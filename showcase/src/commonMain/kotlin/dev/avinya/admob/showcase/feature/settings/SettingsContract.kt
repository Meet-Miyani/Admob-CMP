package dev.avinya.admob.showcase.feature.settings

import dev.avinya.ads.AdTrackingAuthorization
import dev.avinya.ads.ConsentDebugGeography
import dev.avinya.ads.ConsentStatus
import dev.avinya.ads.PrivacyOptionsRequirementStatus
import dev.avinya.admob.showcase.StartupState
import dev.avinya.admob.showcase.ui.theme.ThemeMode

/**
 * Whether to offer the privacy-options ("manage consent") button.
 *
 * Gated **only** on [PrivacyOptionsRequirementStatus.Required] — never on
 * `ConsentStatus.Obtained`, which is the common mistake and puts a dead
 * button in front of users in regions where UMP requires no such control.
 */
fun shouldShowPrivacyOptionsButton(status: PrivacyOptionsRequirementStatus): Boolean =
    status == PrivacyOptionsRequirementStatus.Required

data class SettingsState(
    val sdkStatus: String = "Unknown",
    val sdkVersion: String? = null,
    val adapters: List<String> = emptyList(),
    val consentStatus: ConsentStatus = ConsentStatus.Unknown,
    val canRequestAds: Boolean = false,
    val privacyOptions: PrivacyOptionsRequirementStatus = PrivacyOptionsRequirementStatus.Unknown,
    val tracking: AdTrackingAuthorization = AdTrackingAuthorization.NotDetermined,
    val debugGeography: ConsentDebugGeography = ConsentDebugGeography.Disabled,
    val themeMode: ThemeMode = ThemeMode.Default,
    val inspectorEnabled: Boolean = true,
    val adsEnabled: Boolean = true,
    val busy: Boolean = false,
    val startup: StartupState = StartupState.Starting,
)

sealed interface SettingsIntent {
    data object ShowPrivacyOptions : SettingsIntent
    data object RequestTracking : SettingsIntent
    data object ResetConsent : SettingsIntent
    data object OpenAdInspector : SettingsIntent
    data object RetryStartup : SettingsIntent
    data class SetDebugGeography(val geography: ConsentDebugGeography) : SettingsIntent
    data class SetThemeMode(val mode: ThemeMode) : SettingsIntent
    data class SetInspectorEnabled(val enabled: Boolean) : SettingsIntent
    data class SetAdsEnabled(val enabled: Boolean) : SettingsIntent
}

sealed interface SettingsEffect {
    /** Shown as a transient message. [success] false means the SDK refused the request. */
    data class Notice(val message: String, val success: Boolean) : SettingsEffect
}
