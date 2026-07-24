package avinya.tech.yt.ads

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration

internal sealed interface AdAttemptResult<out T> {
    data class Success<T>(val value: T) : AdAttemptResult<T>
    data class Failure(val error: AdError) : AdAttemptResult<Nothing>
}

internal suspend fun <T> retryAdLoad(
    policy: AdRetryPolicy,
    isRetryable: (AdError) -> Boolean,
    attempt: suspend () -> AdAttemptResult<T>
): AdAttemptResult<T> {
    var lastResult: AdAttemptResult<T> = attempt()
    var attemptIndex = 1
    while (lastResult is AdAttemptResult.Failure
        && attemptIndex < policy.maxAttempts
        && isRetryable(lastResult.error)
    ) {
        val delayMs = min(
            (policy.initialDelay.inWholeMilliseconds * policy.backoffMultiplier.pow((attemptIndex - 1).toDouble())),
            policy.maxDelay.inWholeMilliseconds.toDouble()
        ).toLong()
        delay(delayMs)
        attemptIndex++
        lastResult = attempt()
    }
    return lastResult
}
