package dev.avinya.admob.showcase.feature.store

import androidx.lifecycle.viewModelScope
import dev.avinya.ads.AdManager
import dev.avinya.ads.AdManagerStatus
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.db.entity.UnlockSource
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.data.repo.ArticleRepository
import dev.avinya.admob.showcase.data.repo.PremiumArticle
import dev.avinya.admob.showcase.data.repo.WalletRepository
import dev.avinya.admob.showcase.domain.wallet.DebitResult
import dev.avinya.admob.showcase.domain.wallet.rewardGrantKey
import dev.avinya.admob.showcase.ui.ad.AppOpenSuppressor
import dev.avinya.admob.showcase.ui.ad.RewardOutcome
import dev.avinya.admob.showcase.ui.ad.suppressing
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class StoreViewModel(
    private val wallet: WalletRepository,
    private val articles: ArticleRepository,
    private val suppressor: AppOpenSuppressor,
    private val sessionId: String,
    settings: SettingsRepository,
    adManager: AdManager,
) : MviViewModel<StoreState, StoreIntent, StoreEffect>(StoreState()) {

    /** Monotonic within a session; combined with [sessionId] into the grant key. */
    private var rewardSequence = 0

    init {
        combine(
            wallet.balance(),
            articles.premiumCatalog(),
            settings.adsMasterSwitch,
            adManager.status,
        ) { balance, premium, adsEnabled, status ->
            StoreState(
                balance = balance,
                premium = premium,
                rewarded = state.value.rewarded,
                offerWallVisible = state.value.offerWallVisible,
                adsEnabled = adsEnabled,
                sdkReady = status == AdManagerStatus.Ready,
            )
        }.onEach { next -> updateState { next } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: StoreIntent) {
        when (intent) {
            StoreIntent.WatchRewardedAd -> Unit // driven by the screen; see nextGrantKey()
            StoreIntent.OpenOfferWall -> updateState { copy(offerWallVisible = true) }
            StoreIntent.AcceptOfferWall, StoreIntent.DeclineOfferWall ->
                updateState { copy(offerWallVisible = false) }
            is StoreIntent.Unlock -> unlock(intent.article)
        }
    }

    /** Allocates the next idempotency key. Called once per presentation attempt. */
    fun nextGrantKey(placementId: String): String =
        rewardGrantKey(placementId, sessionId, ++rewardSequence)

    fun setRewardedState(next: RewardedUiState) {
        updateState { copy(rewarded = next) }
    }

    fun onRewardOutcome(outcome: RewardOutcome) {
        updateState { copy(rewarded = RewardedUiState.Idle) }
        viewModelScope.launch { emitEffect(StoreEffect.RewardResult(outcome)) }
    }

    private fun unlock(article: PremiumArticle) {
        if (article.isUnlocked) return
        viewModelScope.launch {
            // Suppressed for the whole transaction: an app-open ad appearing
            // mid-purchase is both a bad experience and a policy problem.
            suppressor.suppressing {
                when (val result = wallet.debit(article.costCoins)) {
                    is DebitResult.Debited -> {
                        articles.unlock(article.id, UnlockSource.COINS)
                        emitEffect(StoreEffect.Unlocked(article.title))
                    }
                    is DebitResult.InsufficientFunds ->
                        emitEffect(StoreEffect.NeedMoreCoins(result.required - result.balance))
                }
            }
        }
    }
}
