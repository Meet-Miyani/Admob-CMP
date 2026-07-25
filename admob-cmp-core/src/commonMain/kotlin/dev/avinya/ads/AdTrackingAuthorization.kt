package dev.avinya.ads

/**
 * App Tracking Transparency authorisation state (iOS 14.5+).
 *
 * Android has no equivalent and always reports [NotApplicable]; advertising-id access
 * there is governed by the `AD_ID` permission, declared in the manifest.
 */
public enum class AdTrackingAuthorization {
    /** Not an iOS 14.5+ device, or Android. */
    NotApplicable,
    /** The user has not been asked yet. Requesting ads now permanently forfeits the IDFA. */
    NotDetermined,
    /** Restricted by device policy; cannot be changed by prompting. */
    Restricted,
    /** The user declined. Ads serve non-personalised. */
    Denied,
    /** The user allowed tracking. The IDFA is available. */
    Authorized,
}

/**
 * Reads and requests App Tracking Transparency authorisation.
 *
 * Obtain via [AdManager.tracking]. Call [requestAuthorization] once, **after** UMP consent
 * and **before** the first ad request — Google's documented order — otherwise that first
 * request permanently serves without the IDFA.
 */
public interface AdTrackingController {
    /** Current authorisation without prompting. */
    public fun status(): AdTrackingAuthorization

    /**
     * Shows the system ATT prompt if status is [AdTrackingAuthorization.NotDetermined],
     * and returns the resolved status. Safe to call repeatedly: iOS shows the prompt at
     * most once per install.
     */
    public suspend fun requestAuthorization(): AdTrackingAuthorization
}

/**
 * Common no-op used by [NoOpAdManager]. Always reports [AdTrackingAuthorization.NotApplicable] —
 * there is no ads SDK configured, so there is nothing to prompt for.
 */
internal object NoOpTrackingController : AdTrackingController {
    override fun status(): AdTrackingAuthorization = AdTrackingAuthorization.NotApplicable
    override suspend fun requestAuthorization(): AdTrackingAuthorization =
        AdTrackingAuthorization.NotApplicable
}
