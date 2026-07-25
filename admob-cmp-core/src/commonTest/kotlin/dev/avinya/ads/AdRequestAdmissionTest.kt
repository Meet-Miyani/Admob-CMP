package dev.avinya.ads

import dev.avinya.ads.internal.AdRequestAdmission
import dev.avinya.ads.internal.deriveAdmission
import kotlin.test.Test
import kotlin.test.assertEquals

class AdRequestAdmissionTest {

    @Test
    fun skipConsentIsAlwaysSkippedRegardlessOfCanRequestAds() {
        assertEquals(
            AdRequestAdmission.Skipped,
            deriveAdmission(ConsentMode.SkipConsent, canRequestAds = false, consentGathered = false)
        )
        assertEquals(
            AdRequestAdmission.Skipped,
            deriveAdmission(ConsentMode.SkipConsent, canRequestAds = true, consentGathered = true)
        )
    }

    @Test
    fun notGatheredBeforeConsentHasBeenCollected() {
        assertEquals(
            AdRequestAdmission.NotGathered,
            deriveAdmission(ConsentMode.GatherBeforeInitialize, canRequestAds = false, consentGathered = false)
        )
    }

    @Test
    fun allowedWhenGatheredAndCanRequest() {
        assertEquals(
            AdRequestAdmission.Allowed,
            deriveAdmission(ConsentMode.GatherBeforeInitialize, canRequestAds = true, consentGathered = true)
        )
    }

    @Test
    fun revokedWhenGatheredButCanRequestIsFalse() {
        assertEquals(
            AdRequestAdmission.Revoked,
            deriveAdmission(ConsentMode.GatherBeforeInitialize, canRequestAds = false, consentGathered = true)
        )
    }

    @Test
    fun onlyAllowedAndSkippedPermitRequests() {
        assertEquals(true, AdRequestAdmission.Allowed.permitsRequests)
        assertEquals(true, AdRequestAdmission.Skipped.permitsRequests)
        assertEquals(false, AdRequestAdmission.Revoked.permitsRequests)
        assertEquals(false, AdRequestAdmission.NotGathered.permitsRequests)
    }
}
