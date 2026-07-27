package dev.avinya.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class NoOpAdManagerTest {

    private val placement = AdPlacement("test", AdFormat.Interstitial, "android", "ios")

    @Test
    fun `NoOpAdManager initialize returns Disabled`() = runTest {
        val result = NoOpAdManager.initialize(AdConfig("android", "ios"))
        assertIs<AdManagerStatus.Disabled>(result)
    }

    @Test
    fun `banner returns sdk_not_ready failures`() = runTest {
        val banner = NoOpAdManager.banner(placement.copy(id = "banner-test", format = AdFormat.Banner))
        assertIs<NoOpBannerAdController>(banner)
        val loadResult = banner.load()
        assertIs<AdLoadState.Failed>(loadResult)
        assertEquals(AdErrorCode.SDK_NOT_READY, (loadResult as AdLoadState.Failed).error.code)
    }

    @Test
    fun `interstitial returns sdk_not_ready failures`() = runTest {
        val interstitial = NoOpAdManager.interstitial(placement)
        assertIs<NoOpInterstitialAdController>(interstitial)
        val loadResult = interstitial.load()
        assertIs<AdLoadState.Failed>(loadResult)
        assertEquals(AdErrorCode.SDK_NOT_READY, (loadResult as AdLoadState.Failed).error.code)
        val showResult = interstitial.show()
        assertIs<AdShowResult.Failed>(showResult)
        assertEquals(AdErrorCode.SDK_NOT_READY, (showResult as AdShowResult.Failed).error.code)
    }

    @Test
    fun `native pool returns null acquire`() {
        val pool = NoOpAdManager.nativeAd(placement.copy(id = "native-test", format = AdFormat.Native))
        assertIs<NoOpNativeAdPool>(pool)
        assertNull(pool.acquire())
        assertEquals(0, pool.availableCount())
    }

    @Test
    fun `appOpen returns sdk_not_ready failures`() = runTest {
        val appOpen = NoOpAdManager.appOpen(placement.copy(id = "appopen-test", format = AdFormat.AppOpen))
        assertIs<NoOpAppOpenAdController>(appOpen)
        val showResult = appOpen.showIfAvailable()
        assertIs<AdShowResult.NotReady>(showResult)
    }

    @Test
    fun `events fire from NoOp controllers`() = runTest {
        val banner = NoOpBannerAdController(placement.copy(id = "banner-events-test", format = AdFormat.Banner))
        val events = mutableListOf<AdEvent>()
        val job = launch { banner.events.collect { events.add(it) } }
        advanceUntilIdle()
        banner.load()
        advanceUntilIdle()
        job.cancel()
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun `equivalent factory requests return the same controller`() {
        val placement = AdPlacement(
            id = "noop-cache-banner",
            format = AdFormat.Banner,
            androidAdUnitId = "android",
            iosAdUnitId = "ios"
        )

        assertSame(NoOpAdManager.banner(placement), NoOpAdManager.banner(placement.copy()))
    }

    @Test
    fun `placement id collision is rejected`() {
        val first = AdPlacement(
            id = "noop-collision",
            format = AdFormat.Interstitial,
            androidAdUnitId = "android-a",
            iosAdUnitId = "ios-a"
        )
        val conflicting = first.copy(adUnitIds = AdUnitIds("android-b", "ios-b"))

        NoOpAdManager.interstitial(first)
        assertFailsWith<IllegalStateException> {
            NoOpAdManager.interstitial(conflicting)
        }
    }

    @Test
    fun `factory format mismatch is rejected`() {
        val placement = AdPlacement(
            id = "noop-format",
            format = AdFormat.Native,
            androidAdUnitId = "android",
            iosAdUnitId = "ios"
        )

        assertFailsWith<IllegalArgumentException> {
            NoOpAdManager.banner(placement)
        }
    }
}
