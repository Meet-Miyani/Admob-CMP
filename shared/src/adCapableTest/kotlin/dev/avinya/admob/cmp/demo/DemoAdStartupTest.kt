package dev.avinya.admob.cmp.demo

import avinya.tech.yt.ads.AdError
import avinya.tech.yt.ads.AdInitializationPhase
import avinya.tech.yt.ads.AdManagerStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoAdStartupTest {
    @Test
    fun `demo config uses only official sample app ids`() {
        val config = demoTestAdConfig(TrackingAuthorizationHook {})

        assertEquals("ca-app-pub-3940256099942544~3347511713", config.androidAppId)
        assertEquals("ca-app-pub-3940256099942544~1458002511", config.iosAppId)
        assertTrue(config.testMode)
        assertTrue(config.testDeviceIds.isEmpty())
        assertEquals(1, config.initializationHooks.size)
    }

    @Test
    fun `tracking hook runs only immediately before mobile ads initialization`() = runTest {
        var requestCount = 0
        val hook = TrackingAuthorizationHook { requestCount += 1 }
        val config = demoTestAdConfig(hook)

        hook.onPhase(AdInitializationPhase.BeforeConsentRequest, config)
        assertEquals(0, requestCount)

        hook.onPhase(AdInitializationPhase.BeforeMobileAdsInitialize, config)
        assertEquals(1, requestCount)

        hook.onPhase(AdInitializationPhase.AfterMobileAdsInitialize, config)
        assertEquals(1, requestCount)
    }

    @Test
    fun `manager status maps to deterministic startup ui`() {
        assertEquals(
            DemoAdStartupUiState.Starting,
            AdManagerStatus.Idle.toDemoAdStartupUiState(),
        )
        assertEquals(
            DemoAdStartupUiState.Starting,
            AdManagerStatus.Initializing.toDemoAdStartupUiState(),
        )
        assertEquals(
            DemoAdStartupUiState.Ready,
            AdManagerStatus.Ready.toDemoAdStartupUiState(),
        )
        assertEquals(
            DemoAdStartupUiState.ConsentRequired,
            AdManagerStatus.ConsentRequired.toDemoAdStartupUiState(),
        )

        val retryableFailure = AdManagerStatus.Failed(
            error = AdError.message("network unavailable"),
            retryable = true,
        ).toDemoAdStartupUiState()
        assertEquals(
            DemoAdStartupUiState.Failed("network unavailable", retryable = true),
            retryableFailure,
        )
        assertTrue((retryableFailure as DemoAdStartupUiState.Failed).retryable)

        val disabled = AdManagerStatus.Disabled("disabled by host").toDemoAdStartupUiState()
        assertEquals(
            DemoAdStartupUiState.Failed("disabled by host", retryable = false),
            disabled,
        )
        assertFalse((disabled as DemoAdStartupUiState.Failed).retryable)
    }
}
