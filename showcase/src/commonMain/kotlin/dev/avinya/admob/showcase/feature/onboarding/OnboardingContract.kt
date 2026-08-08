package dev.avinya.admob.showcase.feature.onboarding

import dev.avinya.ads.AdTrackingAuthorization
import dev.avinya.admob.showcase.StartupState

/**
 * The initialisation steps, in the only order that is correct.
 *
 * Requesting ads before ATT resolves permanently forfeits the IDFA for those
 * requests, so consent must precede tracking, and tracking must precede the
 * first ad request. This is load-bearing, not cosmetic.
 */
enum class OnboardingStep {
    Consent,
    Tracking,
    Initializing,
    Done,
    Failed,
    ;

    companion object {
        /** The three steps the user actually progresses through. */
        fun orderedSteps(): List<OnboardingStep> = listOf(Consent, Tracking, Initializing)
    }
}

/** How the tracking step renders. Android has no ATT and says so. */
enum class TrackingStepDisplay { Pending, Granted, Refused, NotApplicable }

/**
 * Android reports [AdTrackingAuthorization.NotApplicable]. That is shown
 * explicitly rather than hidden: a consumer reading this app needs to see
 * that ATT is an iOS-only concept, not be left wondering why a step vanished.
 */
fun trackingStepDisplay(status: AdTrackingAuthorization): TrackingStepDisplay = when (status) {
    AdTrackingAuthorization.NotApplicable -> TrackingStepDisplay.NotApplicable
    AdTrackingAuthorization.NotDetermined -> TrackingStepDisplay.Pending
    AdTrackingAuthorization.Authorized -> TrackingStepDisplay.Granted
    AdTrackingAuthorization.Denied, AdTrackingAuthorization.Restricted -> TrackingStepDisplay.Refused
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Consent,
    val tracking: TrackingStepDisplay = TrackingStepDisplay.Pending,
    val startup: StartupState = StartupState.Starting,
    val busy: Boolean = false,
)

sealed interface OnboardingIntent {
    data object Begin : OnboardingIntent
    data object Retry : OnboardingIntent
    data object ContinueWithoutAds : OnboardingIntent
}

sealed interface OnboardingEffect {
    data object Finished : OnboardingEffect
}
