package dev.avinya.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        val banner = NoOpAdManager.banner(placement)
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
        val pool = NoOpAdManager.nativeAd(placement)
        assertIs<NoOpNativeAdPool>(pool)
        assertNull(pool.acquire())
        assertEquals(0, pool.availableCount())
    }

    @Test
    fun `appOpen returns sdk_not_ready failures`() = runTest {
        val appOpen = NoOpAdManager.appOpen(placement)
        assertIs<NoOpAppOpenAdController>(appOpen)
        val showResult = appOpen.showIfAvailable()
        assertIs<AdShowResult.NotReady>(showResult)
    }

    @Test
    fun `events fire from NoOp controllers`() = runTest {
        val banner = NoOpBannerAdController(placement)
        val events = mutableListOf<AdEvent>()
        val job = launch { banner.events.collect { events.add(it) } }
        advanceUntilIdle()
        banner.load()
        advanceUntilIdle()
        job.cancel()
        assertTrue(events.isNotEmpty())
    }

}
