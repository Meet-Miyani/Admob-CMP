package dev.avinya.ads

import dev.avinya.ads.internal.FullScreenPresentationArbiter
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
 * Process-wide arbitration across two DIFFERENT slots.
 *
 * ConcurrentShowProbeTest covers two show() calls on the SAME slot, which the per-slot
 * activePresentation guard already handled. The defect this file pins is the one nothing
 * covered: two independent slots (e.g. interstitial and app-open) each concluding they may
 * present, because their operationMutex/publicationLock/activePresentation are per-instance.
 *
 * commonTest on Native is single-threaded, so this is an ORDERING proof, not a parallelism
 * proof — see the plan's "testing constraint" note. The beforeShowCommit seam holds slot B at
 * the threshold of its selection transaction until slot A has fully committed, then releases
 * it. That reproduces the observable consequence of the race even though the true interleaving
 * cannot be produced here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TwoSlotArbitrationTest {

    private val interstitialPlacement = AdPlacement(
        id = "arb_interstitial",
        format = AdFormat.Interstitial,
        androidAdUnitId = "test-android-i",
        iosAdUnitId = "test-ios-i"
    )

    private val appOpenPlacement = AdPlacement(
        id = "arb_app_open",
        format = AdFormat.AppOpen,
        androidAdUnitId = "test-android-a",
        iosAdUnitId = "test-ios-a"
    )

    @Test
    fun `second slot does not present while the first holds the token`() =
        runTest(StandardTestDispatcher()) {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val arbiter = FullScreenPresentationArbiter()
                var presenceCount = 0
                var maxPresence = 0
                val countPresence: (Int) -> Unit = { delta ->
                    presenceCount += delta
                    if (presenceCount > maxPresence) maxPresence = presenceCount
                }

                val slotA = FakeFullScreenSlot(
                    interstitialPlacement,
                    testGlobalEvents(),
                    unblockedAdRequestError(),
                    tickClock(),
                    onPresentationChanged = countPresence,
                    onPresentationHandOff = { /* hold the presentation open */ },
                    arbiter = arbiter
                )

                // Slot B parks at the seam until the test releases it, so its selection
                // transaction runs strictly after slot A has committed and taken the token.
                val slotBGate = CompletableDeferred<Unit>()
                val slotB = FakeFullScreenSlot(
                    appOpenPlacement,
                    testGlobalEvents(),
                    unblockedAdRequestError(),
                    tickClock(),
                    onPresentationChanged = countPresence,
                    onPresentationHandOff = { /* hold the presentation open */ },
                    arbiter = arbiter,
                    beforeShowCommit = { slotBGate.await() }
                )

                slotA.enqueueLoadResult(AdAttemptResult.Success("ad-a"))
                slotB.enqueueLoadResult(AdAttemptResult.Success("ad-b"))
                slotA.load()
                slotB.load()
                assertEquals(1, slotA.availability().cachedCount, "precondition: slot A has an ad")
                assertEquals(1, slotB.availability().cachedCount, "precondition: slot B has an ad")

                val showA = launch { slotA.show() }
                val resultB = CompletableDeferred<AdShowResult>()
                val showB = launch { resultB.complete(slotB.show()) }
                advanceUntilIdle()

                assertEquals(1, slotA.presentCallCount, "slot A should have taken the token")
                assertEquals(0, slotB.presentCallCount, "slot B must still be parked at the seam")

                slotBGate.complete(Unit)
                advanceUntilIdle()

                assertEquals(
                    0,
                    slotB.presentCallCount,
                    "slot B must not present while slot A holds the process-wide token"
                )
                assertIs<AdShowResult.NotReady>(
                    resultB.getCompleted(),
                    "the losing slot should report NotReady rather than presenting"
                )
                assertEquals(1, maxPresence, "process-wide presence must never exceed 1")

                showA.cancel()
                showB.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `losing slot keeps its cached ad`() =
        runTest(StandardTestDispatcher()) {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val arbiter = FullScreenPresentationArbiter()
                val slotA = FakeFullScreenSlot(
                    interstitialPlacement,
                    testGlobalEvents(),
                    unblockedAdRequestError(),
                    tickClock(),
                    onPresentationHandOff = { /* hold the presentation open */ },
                    arbiter = arbiter
                )
                val slotBGate = CompletableDeferred<Unit>()
                val slotB = FakeFullScreenSlot(
                    appOpenPlacement,
                    testGlobalEvents(),
                    unblockedAdRequestError(),
                    tickClock(),
                    onPresentationHandOff = { /* hold the presentation open */ },
                    arbiter = arbiter,
                    beforeShowCommit = { slotBGate.await() }
                )

                slotA.enqueueLoadResult(AdAttemptResult.Success("ad-a"))
                slotB.enqueueLoadResult(AdAttemptResult.Success("ad-b"))
                slotA.load()
                slotB.load()

                val showA = launch { slotA.show() }
                val showB = launch { slotB.show() }
                advanceUntilIdle()
                slotBGate.complete(Unit)
                advanceUntilIdle()

                // The whole point of acquiring before the cache CAS: a rejected show must not
                // consume inventory. Slot B's ad must survive for the next attempt.
                assertEquals(
                    1,
                    slotB.availability().cachedCount,
                    "the losing slot's ad must remain cached"
                )
                assertEquals(
                    emptyList(),
                    slotB.destroyedAds,
                    "the losing slot must not destroy its ad"
                )

                showA.cancel()
                showB.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `token is handed to the next slot after the first presentation closes`() =
        runTest(StandardTestDispatcher()) {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val arbiter = FullScreenPresentationArbiter()
                val slotA = FakeFullScreenSlot(
                    interstitialPlacement,
                    testGlobalEvents(),
                    unblockedAdRequestError(),
                    tickClock(),
                    arbiter = arbiter
                )
                val slotB = FakeFullScreenSlot(
                    appOpenPlacement,
                    testGlobalEvents(),
                    unblockedAdRequestError(),
                    tickClock(),
                    arbiter = arbiter
                )

                slotA.enqueueLoadResult(AdAttemptResult.Success("ad-a"))
                slotB.enqueueLoadResult(AdAttemptResult.Success("ad-b"))
                slotA.load()
                slotB.load()

                // No onPresentationHandOff, so FakeFullScreenSlot.presentAd closes the handle
                // itself and slotA.show() returns normally — releasing the token.
                val resultA = slotA.show()
                advanceUntilIdle()
                assertIs<AdShowResult.Shown>(resultA, "slot A should have presented")

                val resultB = slotB.show()
                advanceUntilIdle()
                assertIs<AdShowResult.Shown>(
                    resultB,
                    "slot B must acquire the token once slot A's presentation closed"
                )
                assertEquals(1, slotB.presentCallCount)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
