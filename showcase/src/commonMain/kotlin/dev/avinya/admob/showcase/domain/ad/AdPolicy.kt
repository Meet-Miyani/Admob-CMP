package dev.avinya.admob.showcase.domain.ad

/** Everything the interstitial rules need, with nothing attached to it. */
data class AdPolicySnapshot(
    val articlesRead: Int,
    val millisSinceLastInterstitial: Long,
    val millisSinceColdStart: Long,
    val canRequestAds: Boolean,
    val wasRewardedUnlock: Boolean,
    val adsEnabled: Boolean,
)

/** Why an ad was not shown. Rendered verbatim by the Inspector. */
enum class SuppressionReason {
    AdsDisabled,
    ConsentMissing,
    ColdStartGrace,
    Cooldown,
    FrequencyCap,
    RewardedUnlock,
    NotReady,
}

sealed interface AdDecision {
    data object Show : AdDecision
    data class Suppress(val reason: SuppressionReason) : AdDecision
}

/**
 * When an interstitial may interrupt the user.
 *
 * Pure — no SDK, no Compose, no coroutines, no clock of its own. Time arrives
 * pre-computed in the snapshot, which is what makes every boundary here
 * testable by comparing two values.
 *
 * Returning a **reason** rather than a boolean is the point. "No ad appeared
 * and I don't know why" is the most common AdMob integration confusion, and
 * making the reason a first-class value is the single most useful thing this
 * showcase teaches.
 */
class AdPolicy {

    fun decideInterstitial(snapshot: AdPolicySnapshot): AdDecision = when {
        !snapshot.adsEnabled -> AdDecision.Suppress(SuppressionReason.AdsDisabled)
        !snapshot.canRequestAds -> AdDecision.Suppress(SuppressionReason.ConsentMissing)
        snapshot.wasRewardedUnlock -> AdDecision.Suppress(SuppressionReason.RewardedUnlock)
        snapshot.millisSinceColdStart < COLD_START_GRACE_MILLIS ->
            AdDecision.Suppress(SuppressionReason.ColdStartGrace)
        snapshot.millisSinceLastInterstitial < COOLDOWN_MILLIS ->
            AdDecision.Suppress(SuppressionReason.Cooldown)
        snapshot.articlesRead % ARTICLES_PER_INTERSTITIAL != 0 ->
            AdDecision.Suppress(SuppressionReason.FrequencyCap)
        else -> AdDecision.Show
    }

    private companion object {
        const val ARTICLES_PER_INTERSTITIAL = 3
        const val COOLDOWN_MILLIS = 60_000L
        const val COLD_START_GRACE_MILLIS = 30_000L
    }
}
