package dev.avinya.ads

import dev.avinya.ads.internal.FullScreenPresentationArbiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class FullScreenSlotCoreOrphanDrainTest {

    private val rewardedPlacement = testPlacement.copy(
        format = AdFormat.Rewarded,
        cachePolicy = AdCachePolicy(maxSize = 2)
    )

    @Test
    fun `shown rewarded ad is retained until next load then destroyed`() = runTest(StandardTestDispatcher()) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val globalEvents = testGlobalEvents()
            var currentTime = Instant.fromEpochMilliseconds(1_000_000L)
            val slot = FakeFullScreenSlot(
                placement = rewardedPlacement,
                globalEvents = globalEvents,
                adRequestBlockedError = unblockedAdRequestError(),
                clock = { currentTime }
            )

            slot.enqueueLoadResult(AdAttemptResult.Success("rewarded_ad_1"))
            slot.enqueueShowResult(AdShowResult.Shown)

            // 1. Load the rewarded ad
            val loadResult1 = slot.load()
            assertIs<AdLoadState.Loaded>(loadResult1)

            // 2. Show the rewarded ad (destroyAfterPresentation returns false for shown rewarded ads)
            val showResult = slot.showRewardedForTest(testPlacement.fullScreenOptions) { /* reward callback */ }
            assertIs<AdShowResult.Shown>(showResult)

            // The ad should not be destroyed immediately after show because destroyAfterPresentation(wasShown=true) is false
            assertTrue(
                "rewarded_ad_1" !in slot.destroyedAds,
                "Shown rewarded ad must not be destroyed immediately to allow late reward callbacks"
            )

            // 3. Next load triggers prepareLoad, which drains orphanedAds
            slot.enqueueLoadResult(AdAttemptResult.Success("rewarded_ad_2"))
            val loadResult2 = slot.load()
            assertIs<AdLoadState.Loaded>(loadResult2)

            // Now rewarded_ad_1 should have been drained and destroyed during prepareLoad
            assertTrue(
                "rewarded_ad_1" in slot.destroyedAds,
                "Orphaned rewarded ad must be destroyed on subsequent load"
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `orphaned rewarded ad is destroyed when slot is cleared`() = runTest(StandardTestDispatcher()) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val globalEvents = testGlobalEvents()
            var currentTime = Instant.fromEpochMilliseconds(1_000_000L)
            val slot = FakeFullScreenSlot(
                placement = rewardedPlacement,
                globalEvents = globalEvents,
                adRequestBlockedError = unblockedAdRequestError(),
                clock = { currentTime }
            )

            slot.enqueueLoadResult(AdAttemptResult.Success("rewarded_ad_1"))
            slot.enqueueShowResult(AdShowResult.Shown)

            slot.load()
            slot.showRewardedForTest(testPlacement.fullScreenOptions) {}

            assertTrue("rewarded_ad_1" !in slot.destroyedAds)

            // Clear should drain orphanedAds
            slot.clear()

            assertTrue(
                "rewarded_ad_1" in slot.destroyedAds,
                "Orphaned rewarded ad must be destroyed when clear() is called"
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `arbiter loss during prepareShow removes expired ads from cache and destroys them`() = runTest(StandardTestDispatcher()) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val globalEvents = testGlobalEvents()
            var currentTime = Instant.fromEpochMilliseconds(1_000_000L)
            val sharedArbiter = FullScreenPresentationArbiter()

            // Pre-acquire the arbiter so slot loses arbitration
            val blockingToken = sharedArbiter.tryAcquire("other_slot", AdFormat.Interstitial)
            requireNotNull(blockingToken)

            val slot = FakeFullScreenSlot(
                placement = rewardedPlacement,
                globalEvents = globalEvents,
                adRequestBlockedError = unblockedAdRequestError(),
                clock = { currentTime },
                arbiter = sharedArbiter
            )

            // Load 2 ads: ad_old at t=0, ad_new at t=30m
            slot.enqueueLoadResult(AdAttemptResult.Success("ad_old"))
            slot.enqueueLoadResult(AdAttemptResult.Failure(AdError.message("stop")))
            slot.load()

            currentTime += 30.minutes
            slot.enqueueLoadResult(AdAttemptResult.Success("ad_new"))
            slot.enqueueLoadResult(AdAttemptResult.Failure(AdError.message("stop")))
            slot.load()

            // Advance time to t=65m (past 1 hour TTL for ad_old at age 65m, but ad_new is only 35m old)
            currentTime += 35.minutes

            // Attempt show — arbiter will reject because blockingToken is held
            val showResult = slot.show()
            assertEquals(AdShowResult.NotReady, showResult)

            // The expired ad (ad_old) must have been removed from cache and destroyed
            assertTrue(
                "ad_old" in slot.destroyedAds,
                "Expired ad should be destroyed when prepareShow handles arbiter loss"
            )
            assertTrue(
                "ad_new" !in slot.destroyedAds,
                "Fresh ad must remain in cache and not be destroyed"
            )

            // Release arbiter and verify ad_new can still be presented
            sharedArbiter.release(blockingToken)
            slot.enqueueShowResult(AdShowResult.Shown)
            val showResult2 = slot.show()
            assertEquals(AdShowResult.Shown, showResult2)
            assertEquals(listOf("ad_new"), slot.presentedAds)

            // The actual defect was destroying an ad that was never removed from the cache, so
            // a later partition destroyed it a second time. Membership alone cannot see that —
            // this counts. clear() re-partitions whatever the cache still holds.
            slot.clear()
            assertEquals(
                1,
                slot.destroyedAds.count { it == "ad_old" },
                "ad_old must be destroyed exactly once: the arbiter-loss path has to remove it " +
                    "from the cache in the same CAS that retires it, or a later partition " +
                    "destroys it again. Destroys were: ${slot.destroyedAds}"
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
