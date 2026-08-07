package dev.avinya.admob.showcase.ui.ad

import dev.avinya.ads.AdReward
import dev.avinya.ads.AdShowResult
import dev.avinya.ads.FullScreenAdController
import dev.avinya.admob.showcase.data.repo.WalletRepository
import dev.avinya.admob.showcase.domain.wallet.CreditResult
import dev.avinya.admob.showcase.domain.wallet.coinsFor
import kotlinx.coroutines.CancellationException

sealed interface RewardOutcome {
    data class Earned(val coins: Int, val newBalance: Int) : RewardOutcome
    data object AlreadyGranted : RewardOutcome
    data object DismissedWithoutReward : RewardOutcome
    data object NotReady : RewardOutcome
    data class Failed(val message: String) : RewardOutcome
}

/**
 * Runs a rewarded presentation and credits the wallet.
 *
 * [show] is passed in rather than the controller itself because
 * `RewardedAdController` and `RewardedInterstitialAdController` are unrelated
 * types with identical `show` shapes; a function parameter unifies them
 * without touching the SDK.
 *
 * **The credit comes from [onReward], not from the returned [AdShowResult].**
 * A user who dismisses early still yields `Shown`; crediting on it pays for
 * an ad that was not watched.
 */
suspend fun runRewarded(
    load: suspend () -> Unit,
    show: suspend (onReward: (AdReward) -> Unit) -> AdShowResult,
    wallet: WalletRepository,
    grantKey: String,
): RewardOutcome {
    load()

    var earned: AdReward? = null
    val result = try {
        show { reward -> earned = reward }
    } catch (throwable: Throwable) {
        // A platform that throws instead of returning Failed must not kill the
        // caller's coroutine and freeze the screen.
        if (throwable is CancellationException) throw throwable
        return RewardOutcome.Failed(throwable.message ?: "presentation failed")
    }

    val coins = coinsFor(earned)
    return when {
        coins != null -> when (val credit = wallet.credit(coins, grantKey)) {
            is CreditResult.Credited -> RewardOutcome.Earned(coins, credit.newBalance)
            CreditResult.AlreadyGranted -> RewardOutcome.AlreadyGranted
        }
        result is AdShowResult.NotReady -> RewardOutcome.NotReady
        result is AdShowResult.Failed -> RewardOutcome.Failed(result.error.message)
        // Shown but no reward: dismissed before earning. No consolation grant.
        else -> RewardOutcome.DismissedWithoutReward
    }
}
