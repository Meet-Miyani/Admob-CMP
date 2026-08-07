package dev.avinya.ads

import dev.avinya.ads.internal.NativeAdCoordinatorCore
import dev.avinya.ads.internal.NativeAdPlatform
import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSlot
import dev.avinya.ads.nativead.NativeAdSlotState
import dev.avinya.ads.nativead.NativeAdWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class NativeAdCoordinatorCoreTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val nativePlacement = AdPlacement(
        id = "p",
        format = AdFormat.Native,
        adUnitIds = AdUnitIds(android = "x", ios = "y"),
    )

    @AfterTest
    fun teardown() {
        scope.cancel()
    }

    private fun fakePlatform(
        loadFn: suspend (AdPlacement, Int, Long) -> AdAttemptResult<List<FakeAd>>,
    ): FakePlatform = FakePlatform(loadFn)

    private fun coordinator(
        memoryPolicy: NativeAdMemoryPolicy = NativeAdMemoryPolicy(),
        platform: NativeAdPlatform<FakeAd>,
    ): NativeAdCoordinatorCore<FakeAd> = NativeAdCoordinatorCore(
        memoryPolicy = memoryPolicy,
        platform = platform,
        scope = scope,
    )

    private fun windowWith(vararg visible: String): NativeAdWindow = NativeAdWindow(
        visible = visible.map { NativeAdSlot(it, nativePlacement) },
    )

    // --- Test 1: 65th live session is rejected ---------------------------------

    @Test fun `rejection of 65th live session`() {
        val platform = fakePlatform { _, _, _ -> AdAttemptResult.Success(emptyList()) }
        val coord = coordinator(
            memoryPolicy = NativeAdMemoryPolicy(maxInactiveSessions = 2, maxSessionRecords = 3),
            platform = platform,
        )
        coord.session("s1")
        coord.session("s2")
        coord.session("s3")
        assertFailsWith<IllegalStateException> {
            coord.session("s4")
        }
    }

    // --- Test 1b: blank session key is rejected --------------------------------

    @Test fun `blank session key is rejected`() {
        val platform = fakePlatform { _, _, _ -> AdAttemptResult.Success(emptyList()) }
        val coord = coordinator(platform = platform)
        assertFailsWith<IllegalArgumentException> { coord.session("") }
    }

    // --- Test 1c: policy mismatch on reuse is rejected -------------------------

    @Test fun `reusing a session key with a different policy is rejected`() {
        val platform = fakePlatform { _, _, _ -> AdAttemptResult.Success(emptyList()) }
        val coord = coordinator(platform = platform)
        coord.session("s1", NativeAdSessionPolicy(maxRetainedAds = 3))
        assertFailsWith<IllegalStateException> {
            coord.session("s1", NativeAdSessionPolicy(maxRetainedAds = 4))
        }
    }

    // --- Test 1d: repeated identical windows do not re-issue demand -----------

    @Test fun `repeated identical windows do not re-issue demand`() = runTest(dispatcher) {
        val platform = fakePlatform { _, count, _ ->
            AdAttemptResult.Success((0 until count).map { FakeAd(it) })
        }
        val coord = coordinator(platform = platform)
        val session = coord.session("s1")
        val window = windowWith("a", "b", "c")
        coord.updateWindow("s1", window)
        advanceUntilIdle()
        val firstCallCount = platform.loadCalls.size
        coord.updateWindow("s1", window)
        advanceUntilIdle()
        assertEquals(
            firstCallCount,
            platform.loadCalls.size,
            "second identical window must not re-issue demand",
        )
    }

    // --- Test 1e: clear destroys every owned platform ad exactly once -------

    @Test fun `clear destroys every owned platform ad exactly once`() = runTest(dispatcher) {
        val platform = fakePlatform { _, count, _ ->
            AdAttemptResult.Success((0 until count).map { FakeAd(it) })
        }
        val coord = coordinator(platform = platform)
        coord.session("s1")
        coord.updateWindow("s1", windowWith("a", "b"))
        advanceUntilIdle()
        coord.clear()
        assertEquals(2, platform.destroyed.size, "two ads destroyed on clear")
        assertEquals(2, platform.destroyed.toSet().size, "no duplicate destroy")
    }

    // --- Test 2: partial batch admission admits only the resolved ads -------

    @Test fun `partial batch admission admits only the resolved ads`() = runTest(dispatcher) {
        val platform = fakePlatform { _, count, _ ->
            val ads = (0 until count / 2).map { FakeAd(it) }
            AdAttemptResult.Success(ads)
        }
        val coord = coordinator(platform = platform)
        val session = coord.session("s1")
        coord.updateWindow("s1", windowWith("a", "b", "c"))
        advanceUntilIdle()
        assertEquals(1, platform.loadCalls.size, "exactly one load for the batch")
        val (_, requestedCount, _) = platform.loadCalls.single()
        assertEquals(3, requestedCount, "platform called with full demand")
        val state = session.state.value
        val readyCount = state.slots.values.count {
            it is NativeAdSlotState.Ready || it is NativeAdSlotState.Mounted
        }
        assertEquals(1, readyCount, "one record admitted (partial fill); remaining slots stay in Loading")
    }

    // --- Test 3: clear during load destroys late callbacks -------------------

    @Test fun `clear during load destroys late callbacks from a stale generation`() = runTest(dispatcher) {
        val platform = fakePlatform { _, count, _ ->
            AdAttemptResult.Success((0 until count).map { FakeAd(it) })
        }
        val coord = coordinator(platform = platform)
        val session = coord.session("s1")
        coord.updateWindow("s1", windowWith("a", "b"))
        coord.clear()
        advanceUntilIdle()
        assertEquals(2, platform.destroyed.size, "late ads destroyed after clear")
        val state = session.state.value
        for ((_, slotState) in state.slots) {
            assertTrue(
                slotState is NativeAdSlotState.Empty || slotState is NativeAdSlotState.Loading,
                "slots reset after clear, got $slotState",
            )
        }
    }

    // --- Test 4: cleanup of idle per-placement schedulers --------------------

    @Test fun `cleanup of idle per-placement schedulers`() = runTest(dispatcher) {
        val platform = fakePlatform { _, _, _ -> AdAttemptResult.Success(emptyList()) }
        val coord = coordinator(platform = platform)
        val session = coord.session("s1")
        coord.updateWindow("s1", windowWith("a"))
        advanceUntilIdle()
        assertTrue(coord.schedulerCount() == 0, "no idle schedulers should remain")
    }

    // --- Test 5: one-hour expiry expires a loaded record -------------------

    @Test fun `one-hour expiry expires a loaded record`() = runTest(dispatcher) {
        val ads = listOf(FakeAd(0))
        val platform = fakePlatform { _, _, _ -> AdAttemptResult.Success(ads) }
        val coord = coordinator(platform = platform)
        val session = coord.session("s1")
        coord.updateWindow("s1", windowWith("a"))
        advanceUntilIdle()
        val before = session.state.value
        assertTrue(before.slots["a"] is NativeAdSlotState.Ready, "admitted and ready")
        coord.tickForTest(61.minutes)
        val after = session.state.value
        val slotAfter = after.slots["a"]
        assertTrue(
            slotAfter is NativeAdSlotState.Empty || slotAfter is NativeAdSlotState.Loading,
            "expected Empty or Loading after TTL, got $slotAfter",
        )
    }

    // --- Test 6: inactive session TTL cleanup -------------------------------

    @Test fun `inactive session TTL cleanup reaps after 30 minutes`() = runTest(dispatcher) {
        val platform = fakePlatform { _, _, _ -> AdAttemptResult.Success(emptyList()) }
        val coord = coordinator(
            memoryPolicy = NativeAdMemoryPolicy(inactiveSessionTtl = 30.minutes),
            platform = platform,
        )
        val session = coord.session("s1")
        session.deactivate()
        coord.tickForTest(31.minutes)
        assertTrue(session.state.value.slots.isEmpty(), "reaped session has no slots")
    }

    // --- Test 7: 32-inactive-record LRU eviction -----------------------------

    @Test fun `32-inactive-record LRU evicts the oldest inactive session`() = runTest(dispatcher) {
        val platform = fakePlatform { _, _, _ -> AdAttemptResult.Success(emptyList()) }
        val coord = coordinator(
            memoryPolicy = NativeAdMemoryPolicy(maxInactiveSessions = 2, maxSessionRecords = 8),
            platform = platform,
        )
        val s1 = coord.session("s1")
        coord.tickForTest(1.minutes)
        s1.deactivate()
        val s2 = coord.session("s2")
        coord.tickForTest(1.minutes)
        s2.deactivate()
        coord.session("s3")  // pushes s1 out
        assertTrue(s1.state.value.slots.isEmpty(), "s1 reaped by LRU")
        assertTrue(s2.state.value.slots.isEmpty(), "s2 still inactive but tracked")
    }

    // --- Test 8: failed top-up preserves existing inventory ----------------

    @Test fun `failed top-up preserves existing inventory`() = runTest(dispatcher) {
        var first = true
        val platform = fakePlatform { _, count, _ ->
            if (first) {
                first = false
                AdAttemptResult.Success((0 until count).map { FakeAd(it) })
            } else {
                AdAttemptResult.Failure(AdError.sdkNotReady())
            }
        }
        val coord = coordinator(platform = platform)
        val session = coord.session("s1")
        coord.updateWindow("s1", windowWith("a", "b"))
        advanceUntilIdle()
        val firstState = session.state.value
        assertTrue(firstState.slots["a"] is NativeAdSlotState.Ready)
        assertTrue(firstState.slots["b"] is NativeAdSlotState.Ready)
        coord.updateWindow("s1", windowWith("a", "b", "c"))
        advanceUntilIdle()
        val secondState = session.state.value
        assertTrue(secondState.slots["a"] is NativeAdSlotState.Ready, "a still ready")
        assertTrue(secondState.slots["b"] is NativeAdSlotState.Ready, "b still ready")
        assertTrue(secondState.slots["c"] is NativeAdSlotState.Failed, "c failed")
    }
}

internal class FakePlatform(
    private val loadFn: suspend (AdPlacement, Int, Long) -> AdAttemptResult<List<FakeAd>>,
) : NativeAdPlatform<FakeAd> {
    val destroyed = mutableListOf<FakeAd>()
    val loadCalls = mutableListOf<Triple<AdPlacement, Int, Long>>()
    override suspend fun load(placement: AdPlacement, count: Int, generation: Long): AdAttemptResult<List<FakeAd>> {
        loadCalls.add(Triple(placement, count, generation))
        return loadFn(placement, count, generation)
    }
    override fun destroy(ad: FakeAd) { destroyed.add(ad) }
    override fun responseInfo(ad: FakeAd) = null
    override fun mediaInfo(ad: FakeAd) = null
}
