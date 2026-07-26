package dev.avinya.ads

import dev.avinya.ads.internal.BannerCore
import dev.avinya.ads.internal.BannerPlatform
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BannerCoreTest {

    private class FakeBanner(val widthDp: Int) {
        var destroyCount = 0
        val destroyed: Boolean get() = destroyCount > 0
    }

    private class FakeBannerPlatform : BannerPlatform<FakeBanner, Int> {
        var fallbackWidth: Int? = 360
        var nextResult: () -> AdAttemptResult<FakeBanner> = {
            AdAttemptResult.Success(FakeBanner(320))
        }
        var lastRequestOptions: AdRequestOptions? = null
        var lastSize: Int? = null
        val loadedPolicies = mutableListOf<AdSizePolicy>()
        val loadedOptions = mutableListOf<AdRequestOptions>()

        /** Runs between loadBanner being entered and it returning — lets a test detach mid-load. */
        var beforeReturn: (suspend () -> Unit)? = null

        // No real lock: commonTest has no multiplatform `synchronized`, and these tests
        // drive the core from a single runTest coroutine. Invoking directly is also
        // trivially reentrant, which is what the seam requires.
        override fun <T> withStateLock(block: () -> T): T = block()
        override fun fallbackWidthDp(): Int? = fallbackWidth
        override fun resolveSize(sizePolicy: AdSizePolicy, widthDp: Int): Int = widthDp
        override fun destroy(banner: FakeBanner) {
            banner.destroyCount++
        }

        override fun responseInfo(banner: FakeBanner): AdResponseInfo? = null
        override suspend fun loadBanner(
            size: Int,
            sizePolicy: AdSizePolicy,
            requestOptions: AdRequestOptions,
            requiredGeneration: Long
        ): AdAttemptResult<FakeBanner> {
            loadedPolicies += sizePolicy
            loadedOptions += requestOptions
            lastSize = size
            lastRequestOptions = requestOptions
            beforeReturn?.invoke()
            return nextResult()
        }
    }

    private fun core(platform: FakeBannerPlatform) = BannerCore(
        placement = testBannerPlacement(),
        platform = platform,
        globalEvents = MutableSharedFlow(extraBufferCapacity = 32)
    )

    @Test
    fun geometryRejectsNonPositiveWidth() {
        assertFailsWith<IllegalArgumentException> { BannerGeometry(0) }
        assertFailsWith<IllegalArgumentException> { BannerGeometry(-1) }
    }

    @Test
    fun geometryKeepsPositiveWidth() {
        assertEquals(320, BannerGeometry(320).widthDp)
    }

    @Test
    fun loadWithoutGeometryFailsWhenThePlatformHasNoFallbackWidth() = runTest {
        val platform = FakeBannerPlatform().apply { fallbackWidth = null }
        val core = core(platform)

        val state = core.load(
            geometry = null,
            sizePolicy = AdSizePolicy.LargeAnchoredAdaptive(),
            requestOptions = testRequestOptions()
        ) { null }

        assertTrue(state is AdLoadState.Failed, "no geometry and no fallback must fail, not guess")
    }

    @Test
    fun hostGeometryWinsOverThePlatformFallback() = runTest {
        val platform = FakeBannerPlatform().apply { fallbackWidth = 999 }
        val core = core(platform)

        core.load(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), testRequestOptions()) { null }

        assertEquals(320, platform.lastSize, "host-supplied width must not be overridden by the fallback")
    }

    @Test
    fun refreshReplaysTheResolvedRequestOptions() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)
        val custom = testRequestOptions().copy(contentUrl = "https://example.com/article")

        core.load(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), custom) { null }
        platform.lastRequestOptions = null
        core.refresh { null }

        assertEquals(
            custom,
            platform.lastRequestOptions,
            "refresh() must replay the options the original load resolved, not placement defaults (P1-4)"
        )
    }

    @Test
    fun mutationAfterLoadOrRegisterDoesNotAffectRetainedRequest() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)
        val keywords = mutableSetOf("one")
        val options = testRequestOptions().copy(keywords = keywords)

        core.load(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), options) { null }
        keywords.add("two")
        
        core.refresh { null }
        assertEquals(
            setOf("one"),
            platform.lastRequestOptions?.keywords,
            "refresh() must use a snapshot of the request options from load(), not the caller's mutated object"
        )

        keywords.clear()
        keywords.add("three")

        val platform2 = FakeBannerPlatform()
        val core2 = core(platform2)
        core2.registerGeometry(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), options)
        keywords.add("four")

        core2.refresh { null }

        assertEquals(
            setOf("three"),
            platform2.lastRequestOptions?.keywords,
            "refresh() must use a snapshot of the request options from registerGeometry(), not the caller's mutated object"
        )
    }

    @Test
    fun refreshBeforeAnyLoadFails() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)

        val state = core.refresh { null }

        assertTrue(state is AdLoadState.Failed)
    }

    @Test
    fun aFailedRefreshKeepsThePreviousBannerAlive() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)
        core.load(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), testRequestOptions()) { null }
        val first = assertNotNull(core.currentBanner())

        platform.nextResult = { AdAttemptResult.Failure(AdError.message("no fill")) }
        core.refresh { null }

        assertTrue(!first.destroyed, "the displayed banner must survive a failed refresh (no blank flash)")
        assertEquals(first, core.currentBanner())
        // A banner is still on screen, so the publicly observable state must say so too —
        // a host reacting to Failed by hiding the slot would otherwise hide a live ad.
        assertTrue(
            core.loadState.value is AdLoadState.Loaded,
            "a banner is still displayed after the failed refresh, so loadState must stay Loaded; " +
                "was ${core.loadState.value}"
        )
    }

    @Test
    fun aFailedLoadWithNoDisplayedBannerPublishesFailed() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)
        platform.nextResult = { AdAttemptResult.Failure(AdError.message("no fill")) }

        val state = core.load(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), testRequestOptions()) { null }

        assertNull(core.currentBanner(), "no banner was ever loaded, so nothing should be displayed")
        assertTrue(
            state is AdLoadState.Failed,
            "a failed load with no displayed banner must publish Failed, not Loaded; was $state"
        )
    }

    /**
     * Pins the retention question flagged as an uncertainty in the plan for Task 11.
     *
     * After the extraction `attach`/`detach` live in the core while iOS's `activeLoad`
     * registry stays in `IosBannerAdController`, so the core's `detach()` bumps the
     * generation without that file knowing. The worry was that an in-flight banner arriving
     * afterwards would either be adopted by a controller nobody is attached to, or be torn
     * down twice (once by the core's stale-generation rejection, once by the platform's own
     * cleanup).
     *
     * Exactly-once destruction is the assertion that distinguishes those outcomes, and it is
     * checked here rather than in `iosTest` because reaching `loadBanner` on iOS needs a real
     * `GADBannerView` load that never completes under a unit test — the same chicken-and-egg
     * the plan records for the native pools (E-4). The behaviour under test is the core's, so
     * this is where it is exercisable.
     */
    @Test
    fun aBannerArrivingAfterTheLastDetachIsNotAdoptedAndIsDestroyedExactlyOnce() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)
        core.attach()
        val late = FakeBanner(320)
        platform.nextResult = { AdAttemptResult.Success(late) }
        // The last attachment leaves while the load is in flight, so the banner that
        // eventually arrives belongs to a generation nobody is attached to.
        platform.beforeReturn = { core.detach() }

        core.load(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), testRequestOptions()) { null }

        assertNull(core.currentBanner(), "a banner arriving after the last detach must not be adopted")
        assertEquals(1, late.destroyCount, "the in-flight banner must be torn down exactly once")
        assertEquals(AdLoadState.Idle, core.loadState.value)
    }

    @Test
    fun aBannerArrivingAfterClearIsNotAdoptedAndIsDestroyedExactlyOnce() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)
        val late = FakeBanner(320)
        platform.nextResult = { AdAttemptResult.Success(late) }
        platform.beforeReturn = { core.clear() }

        core.load(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), testRequestOptions()) { null }

        assertNull(core.currentBanner(), "a cleared controller must not adopt a late banner")
        assertEquals(1, late.destroyCount, "the in-flight banner must be torn down exactly once")
    }

    @Test
    fun anUnexpectedThrowableDuringLoadDoesNotStrandTheStateInLoading() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)
        platform.beforeReturn = { throw IllegalStateException("beta SDK mapper blew up") }

        runCatching {
            core.load(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), testRequestOptions()) { null }
        }

        // P1-1: only CancellationException was handled, so an arbitrary Throwable escaped with
        // loadState stuck at Loading — the refresh loop then waits forever on
        // `loadState !is Loading`, which never becomes true again.
        assertTrue(
            core.loadState.value !is AdLoadState.Loading,
            "an unexpected throwable must not strand the controller in Loading; was ${core.loadState.value}"
        )
    }

    @Test
    fun anUnexpectedThrowableKeepsTheDisplayedBannerAndItsLoadedState() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)
        core.load(BannerGeometry(320), AdSizePolicy.LargeAnchoredAdaptive(), testRequestOptions()) { null }
        val displayed = assertNotNull(core.currentBanner())

        platform.beforeReturn = { throw IllegalStateException("boom") }
        runCatching { core.refresh { null } }

        assertTrue(!displayed.destroyed, "a throwing refresh must not tear down the displayed banner")
        assertTrue(
            core.loadState.value is AdLoadState.Loaded,
            "a banner is still on screen, so the state must stay Loaded; was ${core.loadState.value}"
        )
    }

    @Test
    fun aBannerLoadThatNeverCallsBackTimesOut() = runTest {
        val platform = FakeBannerPlatform()
        val core = core(platform)
        platform.beforeReturn = { kotlinx.coroutines.awaitCancellation() }

        val state = core.load(
            BannerGeometry(320),
            AdSizePolicy.LargeAnchoredAdaptive(),
            testRequestOptions()
        ) { null }

        // Unbounded, this leaves loadState at Loading forever — and BannerAdView's refresh
        // loop awaits `loadState !is Loading`, so refresh dies silently for the placement.
        assertTrue(state is AdLoadState.Failed, "a banner load with no callback must fail on timeout")
    }

    @Test
    fun `per call collapsible policy reaches platform load`() = runTest {
        val policy = AdSizePolicy.LargeAnchoredAdaptive(CollapsiblePlacement.Top)
        val platform = FakeBannerPlatform()
        val core = core(platform)

        core.load(BannerGeometry(320), policy, AdRequestOptions()) { null }

        assertEquals(listOf<AdSizePolicy>(policy), platform.loadedPolicies)
    }

    @Test
    fun `refresh replays per call collapsible policy`() = runTest {
        val policy = AdSizePolicy.LargeAnchoredAdaptive(CollapsiblePlacement.Bottom)
        val platform = FakeBannerPlatform()
        val core = core(platform)

        core.load(BannerGeometry(320), policy, AdRequestOptions()) { null }
        core.refresh { null }

        assertEquals(listOf<AdSizePolicy>(policy, policy), platform.loadedPolicies)
    }
}
