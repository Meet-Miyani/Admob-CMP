package dev.avinya.ads.internal

import dev.avinya.ads.ConsentMode
import kotlinx.coroutines.flow.MutableStateFlow

internal class ConsentSessionState {
    private val activeMode = MutableStateFlow<ConsentMode?>(null)

    internal fun recordCompletedGate(mode: ConsentMode) {
        activeMode.value = mode
    }

    internal fun modeForPrivacyOptionsResume(): ConsentMode? = activeMode.value

    internal fun admission(canRequestAds: Boolean): AdRequestAdmission {
        val mode = activeMode.value ?: return AdRequestAdmission.NotGathered
        return deriveAdmission(mode, canRequestAds, consentGathered = true)
    }
}
