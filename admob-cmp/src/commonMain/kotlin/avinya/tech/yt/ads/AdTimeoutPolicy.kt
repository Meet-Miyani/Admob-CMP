package avinya.tech.yt.ads

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Bounds on how long a suspending ad operation may take.
 *
 * GMA is a listener SDK: if a terminal callback never arrives, an unbounded `load()` or
 * `show()` suspends forever. For `show()` that is worse than a hang — the presentation
 * token is process-wide, so one wedged presentation blocks every full-screen ad in the app
 * until process death.
 *
 * @property loadTimeout maximum time for one load, INCLUDING retry backoff. On expiry the
 *   state becomes [AdLoadState.Failed]; a late ad is rejected and destroyed by the
 *   generation check, exactly as after a `clear()`.
 * @property presentationHandOffTimeout maximum time between committing an ad to
 *   presentation and the SDK reporting anything. This does NOT bound how long a user
 *   watches an ad.
 */
@Immutable
public data class AdTimeoutPolicy(
    val loadTimeout: Duration = 30.seconds,
    val presentationHandOffTimeout: Duration = 10.seconds
) {
    init {
        require(loadTimeout.isPositive()) {
            "AdTimeoutPolicy.loadTimeout must be positive, was $loadTimeout"
        }
        require(presentationHandOffTimeout.isPositive()) {
            "AdTimeoutPolicy.presentationHandOffTimeout must be positive, was $presentationHandOffTimeout"
        }
    }
}
