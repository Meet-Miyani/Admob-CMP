package dev.avinya.ads

import dev.avinya.ads.internal.FullScreenPresentationHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * `show()` releases [FullScreenSlotCore]'s operation mutex before presenting, so a second
 * caller can enter selection while an ad is still on screen. Concurrent presentation is
 * instead prevented by the `activePresentation` guard, and these tests pin that behaviour:
 * a stacked interstitial is both an AdMob policy violation and a broken user experience.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrentShowProbeTest {

    private val twoSlotPlacement = AdPlacement(
        id = "probe_slot",
        format = AdFormat.Interstitial,
        androidAdUnitId = "test-android",
        iosAdUnitId = "test-ios",
        maxCacheSize = 2
    )

    @Test
    fun `second show while first presentation in flight does not present concurrently`() =
        runTest(StandardTestDispatcher()) {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var presenceCount = 0
                var maxPresence = 0
                val slot = FakeFullScreenSlot(
                    twoSlotPlacement,
                    testGlobalEvents(),
                    unblockedAdRequestError(),
                    tickClock(),
                    onPresentationChanged = { delta ->
                        presenceCount += delta
                        if (presenceCount > maxPresence) maxPresence = presenceCount
                    },
                    onPresentationHandOff = { /* hold the presentation open */ }
                )
                slot.enqueueLoadResult(AdAttemptResult.Success("ad1"))
                slot.enqueueLoadResult(AdAttemptResult.Success("ad2"))
                slot.load()
                assertEquals(2, slot.availability().cachedCount, "precondition: two ads cached")

                val first = launch { slot.show() }
                advanceUntilIdle()
                assertEquals(1, slot.presentCallCount, "precondition: first ad is presenting")

                val secondResult = CompletableDeferred<AdShowResult>()
                val second = launch { secondResult.complete(slot.show()) }
                advanceUntilIdle()

                assertEquals(
                    1,
                    slot.presentCallCount,
                    "second show must not present while the first ad is on screen"
                )
                assertEquals(1, maxPresence, "process-wide presence must never exceed 1")
                assertIs<AdShowResult.NotReady>(
                    secondResult.getCompleted(),
                    "second show should report NotReady rather than presenting"
                )

                first.cancel()
                second.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `second ad remains cached after a rejected concurrent show`() =
        runTest(StandardTestDispatcher()) {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val slot = FakeFullScreenSlot(
                    twoSlotPlacement,
                    testGlobalEvents(),
                    unblockedAdRequestError(),
                    tickClock(),
                    onPresentationHandOff = { /* hold the presentation open */ }
                )
                slot.enqueueLoadResult(AdAttemptResult.Success("ad1"))
                slot.enqueueLoadResult(AdAttemptResult.Success("ad2"))
                slot.load()

                val first = launch { slot.show() }
                advanceUntilIdle()
                val second = launch { slot.show() }
                advanceUntilIdle()

                // The rejected show must not consume or destroy the queued ad.
                assertEquals(1, slot.availability().cachedCount, "queued ad must survive")
                assertEquals(
                    emptyList(),
                    slot.destroyedAds,
                    "no ad should be destroyed while the first is still on screen"
                )

                first.cancel()
                second.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }
}
