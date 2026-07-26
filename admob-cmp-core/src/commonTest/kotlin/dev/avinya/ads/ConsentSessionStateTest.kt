package dev.avinya.ads

import dev.avinya.ads.internal.AdRequestAdmission
import dev.avinya.ads.internal.ConsentSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsentSessionStateTest {

    @Test
    fun `privacy recovery cannot invent a mode before initialize`() {
        assertNull(ConsentSessionState().modeForPrivacyOptionsResume())
    }

    @Test
    fun `privacy recovery preserves gather mode`() {
        val state = ConsentSessionState()
        state.recordCompletedGate(ConsentMode.GatherBeforeInitialize)

        assertEquals(
            ConsentMode.GatherBeforeInitialize,
            state.modeForPrivacyOptionsResume()
        )
        assertEquals(AdRequestAdmission.Allowed, state.admission(canRequestAds = true))
        assertEquals(AdRequestAdmission.Revoked, state.admission(canRequestAds = false))
    }

    @Test
    fun `privacy recovery preserves already allowed mode`() {
        val state = ConsentSessionState()
        state.recordCompletedGate(ConsentMode.InitializeOnlyIfAlreadyAllowed)

        assertEquals(
            ConsentMode.InitializeOnlyIfAlreadyAllowed,
            state.modeForPrivacyOptionsResume()
        )
    }

    @Test
    fun `explicit skip mode remains distinguishable`() {
        val state = ConsentSessionState()
        state.recordCompletedGate(ConsentMode.SkipConsent)

        assertEquals(AdRequestAdmission.Skipped, state.admission(canRequestAds = false))
    }
}
