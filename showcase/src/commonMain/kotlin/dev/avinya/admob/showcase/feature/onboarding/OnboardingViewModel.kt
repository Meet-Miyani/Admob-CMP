package dev.avinya.admob.showcase.feature.onboarding

import androidx.lifecycle.viewModelScope
import dev.avinya.ads.AdManager
import dev.avinya.ads.ConsentDebugGeography
import dev.avinya.ads.ConsentMode
import dev.avinya.admob.showcase.TrackingAuthorizationHook
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.showcaseAdConfig
import dev.avinya.admob.showcase.toStartupState
import kotlinx.coroutines.launch

/**
 * Drives SDK startup.
 *
 * The call order below is the canonical one from `admob-cmp/AGENTS.md`:
 * gather consent, then resolve ATT, then initialise with
 * [ConsentMode.InitializeOnlyIfAlreadyAllowed]. ATT is fired from an
 * [dev.avinya.ads.AdInitializationHook] at `BeforeMobileAdsInitialize` rather
 * than called inline, so the SDK controls the exact moment it runs relative
 * to GMA's own startup.
 */
class OnboardingViewModel(
    private val adManager: AdManager,
    private val settings: SettingsRepository,
    private val debugGeography: ConsentDebugGeography,
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState()) {

    override fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Begin, OnboardingIntent.Retry -> start()
            OnboardingIntent.ContinueWithoutAds -> finish()
        }
    }

    private fun start() {
        if (state.value.busy) return
        updateState { copy(busy = true, step = OnboardingStep.Consent) }

        viewModelScope.launch {
            val hook = TrackingAuthorizationHook {
                updateState { copy(step = OnboardingStep.Tracking) }
                val result = adManager.tracking.requestAuthorization()
                updateState { copy(tracking = trackingStepDisplay(result)) }
            }
            val config = showcaseAdConfig(trackingHook = hook, debugGeography = debugGeography)

            adManager.consent.gatherConsent(config)

            updateState { copy(step = OnboardingStep.Initializing) }
            val status = adManager.initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)
            val startup = status.toStartupState()

            updateState {
                copy(
                    busy = false,
                    startup = startup,
                    tracking = trackingStepDisplay(adManager.tracking.status()),
                    step = if (startup is dev.avinya.admob.showcase.StartupState.Failed) {
                        OnboardingStep.Failed
                    } else {
                        OnboardingStep.Done
                    },
                )
            }

            if (startup !is dev.avinya.admob.showcase.StartupState.Failed) finish()
        }
    }

    /**
     * Marks onboarding complete and leaves.
     *
     * Called on success *and* from [OnboardingIntent.ContinueWithoutAds]: a
     * consent refusal or a non-retryable init failure must not trap the user
     * on this screen. The app is fully usable ad-free.
     */
    private fun finish() {
        viewModelScope.launch {
            settings.setOnboardingComplete(true)
            emitEffect(OnboardingEffect.Finished)
        }
    }
}
