package dev.avinya.admob.showcase.feature.store

import dev.avinya.admob.showcase.data.repo.PremiumArticle
import dev.avinya.admob.showcase.ui.ad.RewardOutcome

enum class RewardedUiState { Idle, Loading, Showing, Unavailable }

data class StoreState(
    val balance: Int = 0,
    val premium: List<PremiumArticle> = emptyList(),
    val rewarded: RewardedUiState = RewardedUiState.Idle,
    val offerWallVisible: Boolean = false,
    val adsEnabled: Boolean = true,
    val sdkReady: Boolean = false,
)

sealed interface StoreIntent {
    data object WatchRewardedAd : StoreIntent
    data object OpenOfferWall : StoreIntent
    data object AcceptOfferWall : StoreIntent
    data object DeclineOfferWall : StoreIntent
    data class Unlock(val article: PremiumArticle) : StoreIntent
}

sealed interface StoreEffect {
    data class RewardResult(val outcome: RewardOutcome) : StoreEffect
    data class Unlocked(val title: String) : StoreEffect
    data class NeedMoreCoins(val shortfall: Int) : StoreEffect
}
