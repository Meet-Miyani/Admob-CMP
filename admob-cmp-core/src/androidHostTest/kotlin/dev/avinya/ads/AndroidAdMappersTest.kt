package dev.avinya.ads

import com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment as GmaAgeRestrictedTreatment
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the contract between the Android error mapper and the shared retry
 * classifier in [isRetryableLoadFailure].
 *
 * `LoadAdError.getCode()` returns a [LoadAdError.ErrorCode] *enum* in GMA
 * Next-Gen, so `code.toString()` yields the enum NAME ("NETWORK_ERROR"), which
 * is what `retryableLoadFailureCodes` matches on. If a future SDK bump changes
 * that getter back to an integer, `toString()` would silently start producing
 * "2" and every Android load failure would become non-retryable without any
 * compile error. These tests fail loudly if that happens.
 */
class AndroidAdMappersTest {

    private fun loadAdError(code: LoadAdError.ErrorCode): LoadAdError =
        LoadAdError(code, "test failure", null)

    @Test
    fun `maps every common age treatment to the matching GMA value`() {
        val expected = mapOf(
            AgeRestrictedTreatment.Unspecified to GmaAgeRestrictedTreatment.UNSPECIFIED,
            AgeRestrictedTreatment.Child to GmaAgeRestrictedTreatment.CHILD,
            AgeRestrictedTreatment.Teen to GmaAgeRestrictedTreatment.TEEN,
        )

        for ((common, gma) in expected) {
            val mapped = GlobalRequestConfiguration(
                ageRestrictedTreatment = common,
            ).toAndroidRequestConfiguration()

            assertEquals(gma, mapped.ageRestrictedTreatment)
        }
    }

    @Test
    fun `maps load error code to enum name not ordinal`() {
        val mapped = loadAdError(LoadAdError.ErrorCode.NETWORK_ERROR).toAdError()
        assertEquals("NETWORK_ERROR", mapped.code)
        assertEquals("test failure", mapped.message)
    }

    @Test
    fun `transient load failures are classified retryable`() {
        val retryable = listOf(
            LoadAdError.ErrorCode.NETWORK_ERROR,
            LoadAdError.ErrorCode.TIMEOUT,
            LoadAdError.ErrorCode.INTERNAL_ERROR
        )
        for (code in retryable) {
            val mapped = loadAdError(code).toAdError()
            assertTrue(
                mapped.isRetryableLoadFailure(),
                "$code mapped to '${mapped.code}', which the retry policy does not accept"
            )
        }
    }

    @Test
    fun `no fill is never retried`() {
        // NO_FILL means the ad server had no inventory for this request. Retrying it
        // burns requests and depresses fill rate rather than recovering anything.
        val mapped = loadAdError(LoadAdError.ErrorCode.NO_FILL).toAdError()
        assertEquals("NO_FILL", mapped.code)
        assertFalse(mapped.isRetryableLoadFailure())
    }

    @Test
    fun `permanent configuration failures are not retried`() {
        val permanent = listOf(
            LoadAdError.ErrorCode.INVALID_REQUEST,
            LoadAdError.ErrorCode.APP_ID_MISSING,
            LoadAdError.ErrorCode.INVALID_AD_RESPONSE
        )
        for (code in permanent) {
            assertFalse(
                loadAdError(code).toAdError().isRetryableLoadFailure(),
                "$code should not be retried"
            )
        }
    }

    @Test
    fun `every error code maps to its own name`() {
        // Guards the whole enum, so a code added by an SDK bump cannot quietly
        // start round-tripping as an integer.
        for (code in LoadAdError.ErrorCode.values()) {
            assertEquals(code.name, loadAdError(code).toAdError().code)
        }
    }
}
