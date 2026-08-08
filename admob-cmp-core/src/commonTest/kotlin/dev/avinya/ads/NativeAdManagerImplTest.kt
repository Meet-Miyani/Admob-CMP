package dev.avinya.ads

import dev.avinya.ads.internal.NativeAdManagerImpl
import dev.avinya.ads.internal.NativeAdPlatform
import dev.avinya.ads.internal.NativeAdPlatformBatch
import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSlot
import dev.avinya.ads.nativead.NativeAdWindow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class NativeAdManagerImplTest {
    @Test
    fun `same session key shares coordinator state and rejects a conflicting policy`() = runTest {
        val manager = NativeAdManagerImpl(
            policy = NativeAdMemoryPolicy(),
            platform = EmptyNativePlatform,
        )

        val first = manager.session("feed")
        val again = manager.session("feed")

        assertSame(first.state, again.state)
        assertFailsWith<IllegalStateException> {
            manager.session("feed", NativeAdSessionPolicy(maxRetainedAds = 2, retainBehind = 0, prefetchAhead = 0))
        }
    }

    @Test
    fun `deferred manager binds the first accepted memory policy without creating a default coordinator`() {
        val manager = NativeAdManagerImpl<String>(policy = null, platform = EmptyNativePlatform)
        val policy = NativeAdMemoryPolicy(softLimit = 2, hardLimit = 3)

        assertEquals(null, manager.configuredPolicyOrNull())
        assertEquals(0, manager.state.value.loadedAds)
        manager.configure(policy)

        assertEquals(policy, manager.policy)
        manager.configure(policy)
        assertFailsWith<IllegalStateException> {
            manager.configure(NativeAdMemoryPolicy(softLimit = 1, hardLimit = 2))
        }
    }

    @Test
    fun `closed wrapper cannot mutate replacement generation`() = runTest {
        val manager = NativeAdManagerImpl(NativeAdMemoryPolicy(), EmptyNativePlatform)
        val stale = manager.session("feed")
        stale.close()
        val replacement = manager.session("feed")

        assertFailsWith<IllegalStateException> {
            stale.updateWindow(NativeAdWindow(listOf(NativeAdSlot("slot", testNativePlacement()))))
        }
        replacement.deactivate()
        assertFalse(replacement.state.value.active)
    }

    @Test
    fun `clear preserves session definition and permits fresh demand`() = runTest {
        val manager = NativeAdManagerImpl(NativeAdMemoryPolicy(), EmptyNativePlatform)
        val session = manager.session("feed")
        manager.clear()

        session.updateWindow(NativeAdWindow(listOf(NativeAdSlot("slot", testNativePlacement()))))
        assertEquals(true, session.state.value.active)
    }

    @Test
    fun `manager state follows asynchronous reservation admission and retirement`() = runTest {
        val platform = GateNativePlatform()
        val manager = NativeAdManagerImpl(
            policy = NativeAdMemoryPolicy(softLimit = 1, hardLimit = 1),
            platform = platform,
            scope = backgroundScope,
        )
        val session = manager.session("feed")

        session.updateWindow(NativeAdWindow(listOf(NativeAdSlot("slot", testNativePlacement()))))
        runCurrent()
        assertEquals(0, manager.state.value.loadedAds)
        assertEquals(1, manager.state.value.reservedLoads)

        platform.complete("native-ad")
        runCurrent()
        assertEquals(1, manager.state.value.loadedAds)
        assertEquals(0, manager.state.value.reservedLoads)

        manager.clear()
        assertEquals(0, manager.state.value.loadedAds)
        assertEquals(0, manager.state.value.reservedLoads)
    }
}

private class GateNativePlatform : NativeAdPlatform<String> {
    private val result = CompletableDeferred<AdAttemptResult<NativeAdPlatformBatch<String>>>()

    fun complete(ad: String) {
        result.complete(AdAttemptResult.Success(NativeAdPlatformBatch(ads = listOf(ad), unfilledError = null)))
    }

    override suspend fun load(placement: AdPlacement, count: Int, generation: Long): AdAttemptResult<NativeAdPlatformBatch<String>> = result.await()
    override suspend fun bindEvents(ad: String, adInstanceId: String, emit: (AdEvent) -> Unit) = Unit
    override fun destroy(ad: String) = Unit
    override fun responseInfo(ad: String): AdResponseInfo? = null
    override fun mediaInfo(ad: String) = null
}

private object EmptyNativePlatform : NativeAdPlatform<String> {
    override suspend fun load(placement: AdPlacement, count: Int, generation: Long): AdAttemptResult<NativeAdPlatformBatch<String>> =
        AdAttemptResult.Success(NativeAdPlatformBatch(emptyList(), AdError.sdkNotReady()))
    override suspend fun bindEvents(ad: String, adInstanceId: String, emit: (AdEvent) -> Unit) = Unit
    override fun destroy(ad: String) = Unit
    override fun responseInfo(ad: String): AdResponseInfo? = null
    override fun mediaInfo(ad: String) = null
}
