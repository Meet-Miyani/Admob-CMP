package dev.avinya.admob.showcase.feature.settings

import androidx.lifecycle.viewModelScope
import dev.avinya.ads.AdManager
import dev.avinya.ads.ConsentDebugGeography
import dev.avinya.admob.showcase.StartupState
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.domain.ad.AdStartupController
import dev.avinya.admob.showcase.ui.theme.ThemeMode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val adManager: AdManager,
    private val settings: SettingsRepository,
    private val startup: AdStartupController,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    init {
        observeSdk()
        observePreferences()
        observeStartup()
        updateState {
            copy(
                sdkVersion = adManager.diagnostics.sdkVersion(),
                adapters = adManager.diagnostics.adapterStatuses().map { it.toString() },
                tracking = adManager.tracking.status(),
            )
        }
    }

    private fun observeStartup() {
        viewModelScope.launch {
            startup.state.collect { snapshot ->
                updateState { copy(startup = snapshot.startup) }
            }
        }
    }

    private fun observeSdk() {
        viewModelScope.launch {
            combine(
                adManager.status,
                adManager.consent.status,
                adManager.consent.canRequestAds,
                adManager.consent.privacyOptionsRequirementStatus,
            ) { status, consent, canRequest, privacy ->
                Quad(status, consent, canRequest, privacy)
            }.collect { (status, consent, canRequest, privacy) ->
                updateState {
                    copy(
                        sdkStatus = status.toString(),
                        consentStatus = consent,
                        canRequestAds = canRequest,
                        privacyOptions = privacy,
                    )
                }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                settings.themeMode,
                settings.inspectorEnabled,
                settings.adsMasterSwitch,
                settings.consentDebugGeography,
            ) { theme, inspector, ads, geography ->
                Quad(theme, inspector, ads, geography)
            }.collect { (theme, inspector, ads, geography) ->
                updateState {
                    copy(
                        themeMode = theme,
                        inspectorEnabled = inspector,
                        adsEnabled = ads,
                        debugGeography = ConsentDebugGeography.entries
                            .firstOrNull { it.name == geography } ?: ConsentDebugGeography.Disabled,
                    )
                }
            }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.ShowPrivacyOptions -> run("Privacy options") {
                adManager.consent.showPrivacyOptions()
            }
            SettingsIntent.ResetConsent -> run("Consent reset — restart to see the form again") {
                adManager.consent.resetConsentForDebug()
            }
            SettingsIntent.OpenAdInspector -> run("Ad Inspector") {
                adManager.diagnostics.openAdInspector()
            }
            SettingsIntent.RetryStartup -> run("Ads re-initialised") {
                startup.retryAwaiting() is StartupState.Ready
            }
            SettingsIntent.RequestTracking -> viewModelScope.launch {
                val result = adManager.tracking.requestAuthorization()
                updateState { copy(tracking = result) }
            }
            is SettingsIntent.SetDebugGeography -> viewModelScope.launch {
                settings.setConsentDebugGeography(intent.geography.name)
                emitEffect(
                    SettingsEffect.Notice(
                        "Saved — applies on next launch, or now via Retry if ads are not initialised yet.",
                        success = true,
                    ),
                )
            }
            is SettingsIntent.SetThemeMode -> viewModelScope.launch { settings.setThemeMode(intent.mode) }
            is SettingsIntent.SetInspectorEnabled ->
                viewModelScope.launch { settings.setInspectorEnabled(intent.enabled) }
            is SettingsIntent.SetAdsEnabled ->
                viewModelScope.launch { settings.setAdsMasterSwitch(intent.enabled) }
        }
    }

    /**
     * Runs an SDK call that reports success as a `Boolean`.
     *
     * The result is surfaced rather than swallowed: `showPrivacyOptions()` and
     * `openAdInspector()` return `false` in legitimate situations, and a button
     * that silently does nothing is the worst possible teaching example.
     */
    private fun run(label: String, block: suspend () -> Boolean) {
        if (state.value.busy) return
        updateState { copy(busy = true) }
        viewModelScope.launch {
            val ok = block()
            updateState { copy(busy = false) }
            emitEffect(
                SettingsEffect.Notice(
                    message = if (ok) label else "$label unavailable right now",
                    success = ok,
                ),
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
