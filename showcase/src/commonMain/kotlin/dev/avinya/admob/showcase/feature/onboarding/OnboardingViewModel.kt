package dev.avinya.admob.showcase.feature.onboarding

import androidx.lifecycle.viewModelScope
import dev.avinya.admob.showcase.StartupState
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.domain.ad.AdStartupController
import dev.avinya.admob.showcase.domain.ad.AdStartupPhase
import kotlinx.coroutines.launch

/**
 * Observes SDK startup driven by [AdStartupController].
 */
class OnboardingViewModel(
    private val startup: AdStartupController,
    private val settings: SettingsRepository,
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState()) {

    private var finished = false

    init {
        viewModelScope.launch {
            startup.state.collect { snapshot ->
                val step = when (snapshot.phase) {
                    AdStartupPhase.Idle, AdStartupPhase.Consent -> OnboardingStep.Consent
                    AdStartupPhase.Tracking -> OnboardingStep.Tracking
                    AdStartupPhase.Initializing -> OnboardingStep.Initializing
                    AdStartupPhase.Complete -> when (snapshot.startup) {
                        is StartupState.Failed -> OnboardingStep.Failed
                        else -> OnboardingStep.Done
                    }
                }

                updateState {
                    copy(
                        busy = snapshot.running,
                        step = step,
                        startup = snapshot.startup,
                        tracking = trackingStepDisplay(snapshot.tracking),
                    )
                }

                if (snapshot.phase == AdStartupPhase.Complete && snapshot.startup !is StartupState.Failed) {
                    finish()
                }
            }
        }
    }

    override fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Begin -> startup.ensureStarted()
            OnboardingIntent.Retry -> startup.retry()
            OnboardingIntent.ContinueWithoutAds -> finish()
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
        if (finished) return
        finished = true
        viewModelScope.launch {
            settings.setOnboardingComplete(true)
            emitEffect(OnboardingEffect.Finished)
        }
    }
}
