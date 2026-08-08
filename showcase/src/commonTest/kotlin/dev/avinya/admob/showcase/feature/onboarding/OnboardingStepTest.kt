package dev.avinya.admob.showcase.feature.onboarding

import dev.avinya.ads.AdTrackingAuthorization
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingStepTest {

    @Test
    fun consentAlwaysPrecedesTracking() {
        assertEquals(
            listOf(OnboardingStep.Consent, OnboardingStep.Tracking, OnboardingStep.Initializing),
            OnboardingStep.orderedSteps(),
        )
    }

    @Test
    fun trackingIsShownAsNotApplicableRatherThanHiddenWhenThePlatformHasNoAtt() {
        assertEquals(
            TrackingStepDisplay.NotApplicable,
            trackingStepDisplay(AdTrackingAuthorization.NotApplicable),
        )
    }

    @Test
    fun trackingStatesMapToTheirOwnDisplay() {
        assertEquals(TrackingStepDisplay.Pending, trackingStepDisplay(AdTrackingAuthorization.NotDetermined))
        assertEquals(TrackingStepDisplay.Granted, trackingStepDisplay(AdTrackingAuthorization.Authorized))
        assertEquals(TrackingStepDisplay.Refused, trackingStepDisplay(AdTrackingAuthorization.Denied))
        assertEquals(TrackingStepDisplay.Refused, trackingStepDisplay(AdTrackingAuthorization.Restricted))
    }
}
