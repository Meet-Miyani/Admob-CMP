package dev.avinya.admob.cmp.demo

import avinya.tech.yt.ads.AdConfig
import avinya.tech.yt.ads.AdInitializationHook
import avinya.tech.yt.ads.AdInitializationPhase
import avinya.tech.yt.ads.AdManagerStatus

internal const val DEMO_ANDROID_APP_ID: String =
    "ca-app-pub-3940256099942544~3347511713"
internal const val DEMO_IOS_APP_ID: String =
    "ca-app-pub-3940256099942544~1458002511"

internal fun demoTestAdConfig(
    trackingHook: AdInitializationHook,
): AdConfig = AdConfig(
    androidAppId = DEMO_ANDROID_APP_ID,
    iosAppId = DEMO_IOS_APP_ID,
    testMode = true,
    initializationHooks = listOf(trackingHook),
)

internal class TrackingAuthorizationHook(
    private val requestAuthorization: suspend () -> Unit,
) : AdInitializationHook {
    override suspend fun onPhase(
        phase: AdInitializationPhase,
        config: AdConfig,
    ) {
        if (phase == AdInitializationPhase.BeforeMobileAdsInitialize) {
            requestAuthorization()
        }
    }
}

internal sealed interface DemoAdStartupUiState {
    data object Starting : DemoAdStartupUiState
    data object Ready : DemoAdStartupUiState
    data object ConsentRequired : DemoAdStartupUiState
    data class Failed(
        val message: String,
        val retryable: Boolean,
    ) : DemoAdStartupUiState
}

internal fun AdManagerStatus.toDemoAdStartupUiState(): DemoAdStartupUiState = when {
    this == AdManagerStatus.Idle || this == AdManagerStatus.Initializing ->
        DemoAdStartupUiState.Starting
    this == AdManagerStatus.Ready ->
        DemoAdStartupUiState.Ready
    this == AdManagerStatus.ConsentRequired ->
        DemoAdStartupUiState.ConsentRequired
    this is AdManagerStatus.Failed ->
        DemoAdStartupUiState.Failed(error.message, retryable)
    this is AdManagerStatus.Disabled ->
        DemoAdStartupUiState.Failed(reason, retryable = false)
    else ->
        DemoAdStartupUiState.Failed("Unknown SDK state.", retryable = true)
}
