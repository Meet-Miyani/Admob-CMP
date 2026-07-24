package avinya.tech.yt.ads.internal

import avinya.tech.yt.ads.ConsentMode

/**
 * Whether ad requests are currently admissible, and why.
 *
 * Replaces the previous sticky `consentGateSatisfied` boolean, which was set once
 * during initialization and never re-closed. A user revoking consent through the
 * privacy options form left the latch open, so every format kept issuing requests
 * after consent had been withdrawn.
 *
 * [Skipped] is kept distinct from [Allowed] so the deliberate
 * [ConsentMode.SkipConsent] override stays visible in logs and diagnostics rather
 * than being indistinguishable from a genuine grant.
 */
internal enum class AdRequestAdmission {
    /** Consent gathered and UMP reports ads may be requested. */
    Allowed,

    /** Consent was gathered but UMP no longer permits requests (revoked). */
    Revoked,

    /** Consent has not been gathered yet; nothing has been granted or denied. */
    NotGathered,

    /** [ConsentMode.SkipConsent] bypasses UMP entirely and forces the gate open. */
    Skipped;

    val permitsRequests: Boolean
        get() = this == Allowed || this == Skipped
}

/**
 * Derives the current admission state.
 *
 * @param consentMode the mode the manager was initialized with.
 * @param canRequestAds the live value of `ConsentController.canRequestAds`.
 * @param consentGathered whether a consent flow has completed at least once. This
 *   distinguishes "not asked yet" from "asked and revoked" — both report
 *   `canRequestAds == false`, but only the latter should purge inventory.
 */
internal fun deriveAdmission(
    consentMode: ConsentMode,
    canRequestAds: Boolean,
    consentGathered: Boolean
): AdRequestAdmission = when {
    consentMode == ConsentMode.SkipConsent -> AdRequestAdmission.Skipped
    !consentGathered -> AdRequestAdmission.NotGathered
    canRequestAds -> AdRequestAdmission.Allowed
    else -> AdRequestAdmission.Revoked
}
