package dev.avinya.ads

/**
 * Android has no ATT. Advertising-id access is governed by the `AD_ID` manifest
 * permission, so there is nothing to prompt for.
 */
internal object AndroidTrackingController : AdTrackingController {
    override fun status(): AdTrackingAuthorization = AdTrackingAuthorization.NotApplicable
    override suspend fun requestAuthorization(): AdTrackingAuthorization =
        AdTrackingAuthorization.NotApplicable
}
