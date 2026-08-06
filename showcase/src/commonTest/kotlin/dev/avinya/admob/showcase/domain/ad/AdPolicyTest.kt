package dev.avinya.admob.showcase.domain.ad

import kotlin.test.Test
import kotlin.test.assertEquals

private fun snapshot(
    articlesRead: Int = 3,
    millisSinceLastInterstitial: Long = 120_000,
    millisSinceColdStart: Long = 120_000,
    canRequestAds: Boolean = true,
    wasRewardedUnlock: Boolean = false,
    adsEnabled: Boolean = true,
) = AdPolicySnapshot(
    articlesRead = articlesRead,
    millisSinceLastInterstitial = millisSinceLastInterstitial,
    millisSinceColdStart = millisSinceColdStart,
    canRequestAds = canRequestAds,
    wasRewardedUnlock = wasRewardedUnlock,
    adsEnabled = adsEnabled,
)

class AdPolicyTest {

    private val policy = AdPolicy()

    @Test
    fun showsOnEveryThirdArticle() {
        assertEquals(AdDecision.Show, policy.decideInterstitial(snapshot(articlesRead = 3)))
        assertEquals(AdDecision.Show, policy.decideInterstitial(snapshot(articlesRead = 6)))
    }

    @Test
    fun suppressesOnNonMultiplesWithTheFrequencyCapReason() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.FrequencyCap),
            policy.decideInterstitial(snapshot(articlesRead = 2)),
        )
    }

    @Test
    fun cooldownBoundaryIsExactlySixtySeconds() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.Cooldown),
            policy.decideInterstitial(snapshot(millisSinceLastInterstitial = 59_999)),
        )
        assertEquals(
            AdDecision.Show,
            policy.decideInterstitial(snapshot(millisSinceLastInterstitial = 60_000)),
        )
    }

    @Test
    fun neverInterruptsWithinThirtySecondsOfColdStart() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.ColdStartGrace),
            policy.decideInterstitial(snapshot(millisSinceColdStart = 29_999)),
        )
        assertEquals(
            AdDecision.Show,
            policy.decideInterstitial(snapshot(millisSinceColdStart = 30_000)),
        )
    }

    @Test
    fun suppressesWhenConsentForbidsRequests() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.ConsentMissing),
            policy.decideInterstitial(snapshot(canRequestAds = false)),
        )
    }

    @Test
    fun neverInterruptsAnArticleTheUserJustWatchedAnAdToUnlock() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.RewardedUnlock),
            policy.decideInterstitial(snapshot(wasRewardedUnlock = true)),
        )
    }

    @Test
    fun theLocalKillSwitchWins() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.AdsDisabled),
            policy.decideInterstitial(snapshot(adsEnabled = false)),
        )
    }

    @Test
    fun consentOutranksFrequencyWhenBothWouldSuppress() {
        // Reason ordering matters: the Inspector shows the FIRST reason, and
        // "consent forbids requests" is more actionable than "not the 3rd article".
        assertEquals(
            AdDecision.Suppress(SuppressionReason.ConsentMissing),
            policy.decideInterstitial(snapshot(articlesRead = 2, canRequestAds = false)),
        )
    }
}
