package avinya.tech.yt.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AdRetryTest {

    private val retryPolicy = AdRetryPolicy(maxAttempts = 3, initialDelay = 10.milliseconds, maxDelay = 100.milliseconds, backoffMultiplier = 2.0)

    @Test
    fun `respects maxAttempts`() = runTest {
        var attemptCount = 0
        val result = retryAdLoad(retryPolicy, { true }) {
            attemptCount++
            AdAttemptResult.Failure(AdError.message("fail"))
        }
        assertIs<AdAttemptResult.Failure>(result)
        assertEquals(3, attemptCount)
    }

    @Test
    fun `non-retryable error short-circuits`() = runTest {
        var attemptCount = 0
        val result = retryAdLoad(
            policy = retryPolicy,
            isRetryable = { error -> error.code != "fatal" },
            attempt = {
                attemptCount++
                AdAttemptResult.Failure(AdError(code = "fatal", message = "fatal error"))
            }
        )
        assertIs<AdAttemptResult.Failure>(result)
        assertEquals(1, attemptCount)
    }

    @Test
    fun `success on attempt 2 returns success and stops`() = runTest {
        var attemptCount = 0
        val result = retryAdLoad(retryPolicy, { true }) {
            attemptCount++
            if (attemptCount == 2) AdAttemptResult.Success("ok")
            else AdAttemptResult.Failure(AdError.message("fail"))
        }
        assertIs<AdAttemptResult.Success<String>>(result)
        assertEquals("ok", (result as AdAttemptResult.Success).value)
        assertEquals(2, attemptCount)
    }

    @Test
    fun `exponential backoff is capped at maxDelay`() = runTest {
        val capPolicy = AdRetryPolicy(maxAttempts = 5, initialDelay = 50.milliseconds, maxDelay = 100.milliseconds, backoffMultiplier = 4.0)
        var attemptCount = 0
        val result = retryAdLoad(capPolicy, { true }) {
            attemptCount++
            AdAttemptResult.Failure(AdError.message("fail"))
        }
        assertIs<AdAttemptResult.Failure>(result)
        assertEquals(5, attemptCount)
    }
}
