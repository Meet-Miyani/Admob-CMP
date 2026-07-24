package avinya.tech.yt.ads

import avinya.tech.yt.ads.internal.NativePoolCore
import avinya.tech.yt.ads.internal.NativePoolPlatform
import avinya.tech.yt.ads.nativead.NativeAdOptions
import avinya.tech.yt.ads.nativead.NativeMediaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Minimal ad handle: an id plus a destroyed flag, so tests can assert teardown. */
internal class FakeAd(val id: Int) {
    var destroyed: Boolean = false
}

internal class FakeNativePlatform(
    private val batches: MutableList<AdAttemptResult<List<FakeAd>>> = mutableListOf()
) : NativePoolPlatform<FakeAd> {
    var loadCalls: MutableList<Int> = mutableListOf()

    /** Set to run arbitrary code (e.g. core.clear()) between the loader being invoked and it returning. */
    var beforeReturn: (suspend () -> Unit)? = null

    fun enqueue(result: AdAttemptResult<List<FakeAd>>) {
        batches += result
    }

    override fun destroy(ad: FakeAd) {
        ad.destroyed = true
    }

    override fun responseInfo(ad: FakeAd): AdResponseInfo? = null

    override fun mediaInfo(ad: FakeAd): NativeMediaInfo? = null

    override suspend fun loadBatch(
        count: Int,
        requestOptions: AdRequestOptions,
        nativeOptions: NativeAdOptions,
        requiredGeneration: Long
    ): AdAttemptResult<List<FakeAd>> {
        loadCalls += count
        beforeReturn?.invoke()
        return batches.removeFirstOrNull()
            ?: AdAttemptResult.Failure(AdError.message("no batch enqueued"))
    }

    // No real lock: commonTest has no multiplatform `synchronized`, and these tests drive the
    // core from a single runTest coroutine. Invoking directly is also trivially reentrant,
    // which is the property the seam actually requires (see NativePoolPlatform.withPoolLock).
    // The real locking contract is exercised by the platform pools, not here.
    override fun <T> withPoolLock(block: () -> T): T = block()
}

class NativePoolCoreTest {

    private fun core(
        platform: FakeNativePlatform,
        maxSize: Int = 3
    ): NativePoolCore<FakeAd> = NativePoolCore(
        placement = testNativePlacement(maxSize = maxSize),
        platform = platform,
        globalEvents = MutableSharedFlow(extraBufferCapacity = 32)
    )

    @Test
    fun preloadCachesLoadedAdsAndPublishesLoaded() = runTest {
        val platform = FakeNativePlatform()
        platform.enqueue(AdAttemptResult.Success(listOf(FakeAd(1), FakeAd(2))))
        val pool = core(platform)

        pool.preload(2, testRequestOptions(), testNativeOptions())

        assertEquals(2, pool.availableCount())
        assertTrue(pool.loadState.value is AdLoadState.Loaded)
    }

    @Test
    fun acquireMovesAnAdOutOfAvailableAndPeekResolvesIt() = runTest {
        val platform = FakeNativePlatform()
        platform.enqueue(AdAttemptResult.Success(listOf(FakeAd(1))))
        val pool = core(platform)
        pool.preload(1, testRequestOptions(), testNativeOptions())

        val token = pool.acquire()
        assertNotNull(token)
        assertEquals(0, pool.availableCount())
        assertNotNull(pool.peek(token))
    }

    @Test
    fun releaseDestroysTheAdAndFreesCapacity() = runTest {
        val platform = FakeNativePlatform()
        val ad = FakeAd(1)
        platform.enqueue(AdAttemptResult.Success(listOf(ad)))
        val pool = core(platform)
        pool.preload(1, testRequestOptions(), testNativeOptions())

        val token = assertNotNull(pool.acquire())
        pool.release(token)

        assertTrue(ad.destroyed, "release must destroy the underlying ad")
        assertNull(pool.peek(token))
    }

    @Test
    fun overflowBeyondMaxSizeIsDestroyedNotLeaked() = runTest {
        val platform = FakeNativePlatform()
        val ads = listOf(FakeAd(1), FakeAd(2), FakeAd(3), FakeAd(4))
        platform.enqueue(AdAttemptResult.Success(ads))
        val pool = core(platform, maxSize = 2)

        pool.preload(2, testRequestOptions(), testNativeOptions())

        assertEquals(2, pool.availableCount())
        assertEquals(2, ads.count { it.destroyed }, "ads beyond maxSize must be destroyed")
    }

    @Test
    fun batchCompletingAfterClearIsRejectedAndDestroyed() = runTest {
        val platform = FakeNativePlatform()
        val late = listOf(FakeAd(1), FakeAd(2))
        platform.enqueue(AdAttemptResult.Success(late))
        val pool = core(platform)
        // Simulate the real race: clear() lands while the loader is in flight.
        platform.beforeReturn = { pool.clear() }

        pool.preload(2, testRequestOptions(), testNativeOptions())

        assertEquals(0, pool.availableCount(), "a cleared pool must not be repopulated")
        assertTrue(late.all { it.destroyed }, "the late batch must be destroyed, not leaked")
        assertEquals(AdLoadState.Idle, pool.loadState.value)
    }

    @Test
    fun clearWhileQueuedInvalidatesTheQueuedPreload() = runTest {
        val platform = FakeNativePlatform()
        platform.enqueue(AdAttemptResult.Success(listOf(FakeAd(1))))
        val pool = core(platform)
        val generationBefore = pool.currentGeneration()

        pool.clear()

        assertTrue(pool.currentGeneration() != generationBefore, "clear() must bump the generation")
    }

    @Test
    fun cancellationKeepsLoadedWhenInventoryRemains() = runTest {
        val platform = FakeNativePlatform()
        platform.enqueue(AdAttemptResult.Success(listOf(FakeAd(1), FakeAd(2))))
        val pool = core(platform)
        pool.preload(2, testRequestOptions(), testNativeOptions())
        assertTrue(pool.loadState.value is AdLoadState.Loaded)

        // A second preload that is cancelled mid-flight must not erase the fact that
        // the pool still holds usable inventory.
        platform.beforeReturn = { throw CancellationException("cancelled") }
        runCatching { pool.preload(3, testRequestOptions(), testNativeOptions()) }

        assertTrue(
            pool.loadState.value is AdLoadState.Loaded,
            "cancellation must not publish Idle while ${pool.availableCount()} ads remain cached"
        )
    }

    @Test
    fun cancellationPublishesIdleWhenInventoryIsEmpty() = runTest {
        val platform = FakeNativePlatform()
        val pool = core(platform)
        platform.beforeReturn = { throw CancellationException("cancelled") }
        runCatching { pool.preload(1, testRequestOptions(), testNativeOptions()) }

        assertEquals(AdLoadState.Idle, pool.loadState.value)
    }

    @Test
    fun clearDoesNotDestroyLeasedAds() = runTest {
        val platform = FakeNativePlatform()
        val leased = FakeAd(1)
        val cached = FakeAd(2)
        platform.enqueue(AdAttemptResult.Success(listOf(leased, cached)))
        val pool = core(platform)
        pool.preload(2, testRequestOptions(), testNativeOptions())
        val token = assertNotNull(pool.acquire())

        pool.clear()

        assertTrue(cached.destroyed, "cached (unleased) ads must be destroyed by clear()")
        assertTrue(!leased.destroyed, "a leased ad is owned by a live view and must survive clear()")
        assertNotNull(pool.peek(token), "peek must still resolve a live lease after clear()")
    }

    @Test
    fun releasingALeaseAfterClearStillDestroysTheAd() = runTest {
        val platform = FakeNativePlatform()
        val leased = FakeAd(1)
        platform.enqueue(AdAttemptResult.Success(listOf(leased)))
        val pool = core(platform)
        pool.preload(1, testRequestOptions(), testNativeOptions())
        val token = assertNotNull(pool.acquire())
        pool.clear()

        pool.release(token)

        assertTrue(leased.destroyed, "release() after clear() must still tear the ad down")
        assertNull(pool.peek(token))
    }

    @Test
    fun availabilityFlowTracksAcquireAndRelease() = runTest {
        val platform = FakeNativePlatform()
        platform.enqueue(AdAttemptResult.Success(listOf(FakeAd(1), FakeAd(2))))
        val pool = core(platform)
        assertEquals(0, pool.availableAds.value)

        pool.preload(2, testRequestOptions(), testNativeOptions())
        assertEquals(2, pool.availableAds.value)

        val token = assertNotNull(pool.acquire())
        assertEquals(1, pool.availableAds.value, "acquire must decrement availability")

        pool.release(token)
        assertEquals(1, pool.availableAds.value, "release frees capacity but returns no ad to the deque")
    }

    @Test
    fun availabilityFlowDropsToZeroOnClear() = runTest {
        val platform = FakeNativePlatform()
        platform.enqueue(AdAttemptResult.Success(listOf(FakeAd(1), FakeAd(2))))
        val pool = core(platform)
        pool.preload(2, testRequestOptions(), testNativeOptions())

        pool.clear()

        assertEquals(0, pool.availableAds.value)
    }

    @Test
    fun anUnexpectedThrowableDuringLoadDoesNotStrandTheStateInLoading() = runTest {
        val platform = FakeNativePlatform()
        val pool = core(platform)
        platform.beforeReturn = { throw IllegalStateException("beta SDK mapper blew up") }

        runCatching { pool.preload(1, testRequestOptions(), testNativeOptions()) }

        // P1-1: only CancellationException was handled, so an arbitrary Throwable escaped
        // with loadState stuck at Loading forever — later loads coalesce or wait on a state
        // that has no live operation behind it.
        assertTrue(
            pool.loadState.value !is AdLoadState.Loading,
            "an unexpected throwable must not strand the pool in Loading; was ${pool.loadState.value}"
        )
        assertTrue(pool.loadState.value is AdLoadState.Failed)
    }

    @Test
    fun anUnexpectedThrowableKeepsLoadedWhenInventoryRemains() = runTest {
        val platform = FakeNativePlatform()
        platform.enqueue(AdAttemptResult.Success(listOf(FakeAd(1))))
        val pool = core(platform)
        pool.preload(1, testRequestOptions(), testNativeOptions())

        platform.beforeReturn = { throw IllegalStateException("boom") }
        runCatching { pool.preload(3, testRequestOptions(), testNativeOptions()) }

        assertTrue(
            pool.loadState.value is AdLoadState.Loaded,
            "cached inventory must still be reported as Loaded; was ${pool.loadState.value}"
        )
    }

    @Test
    fun aLoadThatNeverCallsBackTimesOutInsteadOfSuspendingForever() = runTest {
        val platform = FakeNativePlatform()
        val pool = core(platform)
        platform.beforeReturn = { kotlinx.coroutines.awaitCancellation() }

        pool.preload(1, testRequestOptions(), testNativeOptions())

        assertTrue(
            pool.loadState.value is AdLoadState.Failed,
            "a load with no callback must fail on timeout; was ${pool.loadState.value}"
        )
    }

    @Test
    fun aTimedOutTopUpKeepsCachedInventoryLoaded() = runTest {
        val platform = FakeNativePlatform()
        platform.enqueue(AdAttemptResult.Success(listOf(FakeAd(1))))
        val pool = core(platform)
        pool.preload(1, testRequestOptions(), testNativeOptions())

        platform.beforeReturn = { kotlinx.coroutines.awaitCancellation() }
        pool.preload(3, testRequestOptions(), testNativeOptions())

        assertTrue(
            pool.loadState.value is AdLoadState.Loaded,
            "a timed-out TOP-UP must not erase usable cached inventory"
        )
        assertEquals(1, pool.availableCount())
    }

    // P1-8: ten feed rows bound to one native placement each collect the pool's whole
    // events flow. Without an ad-instance id on the event, every row's onEvent fires for
    // every other row's impression/click/paid event. emitInstanceScopedEvent resolves the
    // firing ad's token under the pool lock so the composable can filter to its own lease.
    @Test
    fun emitInstanceScopedEventResolvesTheLeasedAdsTokenId() = runTest {
        val platform = FakeNativePlatform()
        val ad1 = FakeAd(1)
        val ad2 = FakeAd(2)
        platform.enqueue(AdAttemptResult.Success(listOf(ad1, ad2)))
        val pool = core(platform)
        pool.preload(2, testRequestOptions(), testNativeOptions())
        val token1 = assertNotNull(pool.acquire())
        val token2 = assertNotNull(pool.acquire())

        val seen = mutableListOf<AdEvent>()
        val collector = launch { pool.events.collect { seen += it } }
        advanceUntilIdle()

        pool.emitInstanceScopedEvent({ it === ad1 }) { id -> AdEvent.Impression(pool.placement.id, id) }
        advanceUntilIdle()

        val impression = seen.single { it is AdEvent.Impression } as AdEvent.Impression
        assertEquals(token1.tokenId, impression.adInstanceId, "the event must name the ad instance that fired it")
        assertTrue(impression.adInstanceId != token2.tokenId, "must not be attributed to a different leased ad")
        collector.cancel()
    }

    @Test
    fun emitInstanceScopedEventStillPublishesWithNullIdWhenTheAdIsNoLongerTracked() = runTest {
        val platform = FakeNativePlatform()
        val ad = FakeAd(1)
        platform.enqueue(AdAttemptResult.Success(listOf(ad)))
        val pool = core(platform)
        pool.preload(1, testRequestOptions(), testNativeOptions())
        val token = assertNotNull(pool.acquire())
        // Simulates the callback firing after release() has already destroyed the entry —
        // that is not an error; the event still publishes, just without an id.
        pool.release(token)

        val seen = mutableListOf<AdEvent>()
        val collector = launch { pool.events.collect { seen += it } }
        advanceUntilIdle()

        pool.emitInstanceScopedEvent({ it === ad }) { id -> AdEvent.Impression(pool.placement.id, id) }
        advanceUntilIdle()

        val impression = seen.single { it is AdEvent.Impression } as AdEvent.Impression
        assertNull(impression.adInstanceId, "an untracked ad must not be attributed a stale token")
        collector.cancel()
    }
}
