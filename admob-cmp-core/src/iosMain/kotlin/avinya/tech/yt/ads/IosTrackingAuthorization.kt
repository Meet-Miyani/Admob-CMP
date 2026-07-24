package avinya.tech.yt.ads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import platform.AppTrackingTransparency.ATTrackingManager
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusAuthorized
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusDenied
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusNotDetermined
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusRestricted

internal object IosTrackingController : AdTrackingController {

    override fun status(): AdTrackingAuthorization =
        when (ATTrackingManager.trackingAuthorizationStatus) {
            ATTrackingManagerAuthorizationStatusAuthorized -> AdTrackingAuthorization.Authorized
            ATTrackingManagerAuthorizationStatusDenied -> AdTrackingAuthorization.Denied
            ATTrackingManagerAuthorizationStatusRestricted -> AdTrackingAuthorization.Restricted
            ATTrackingManagerAuthorizationStatusNotDetermined -> AdTrackingAuthorization.NotDetermined
            else -> AdTrackingAuthorization.NotApplicable
        }

    override suspend fun requestAuthorization(): AdTrackingAuthorization =
        // UIKit/ATT prompt presentation is main-thread only (CLAUDE.md invariant #5).
        withContext(Dispatchers.Main.immediate) {
            if (status() != AdTrackingAuthorization.NotDetermined) return@withContext status()
            suspendCancellableCoroutine { continuation ->
                ATTrackingManager.requestTrackingAuthorizationWithCompletionHandler { _ ->
                    if (continuation.isActive) continuation.resume(status())
                }
            }
        }
}
