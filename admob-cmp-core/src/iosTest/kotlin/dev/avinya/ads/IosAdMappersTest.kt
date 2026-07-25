package dev.avinya.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSError

class IosAdMappersTest {

    private fun gadError(code: Long, message: String = "test failure"): NSError =
        NSError.errorWithDomain("com.google.admob", code, null)

    @Test
    fun `maps NSError code to its numeric string not a name`() {
        val mapped = gadError(2L).toAdError()
        assertEquals("2", mapped.code, "iOS must expose GMA numeric codes, not enum names")
        assertEquals("com.google.admob", mapped.domain)
    }

    @Test
    fun `transient iOS load failures are classified retryable`() {
        for (code in listOf(2L, 5L, 11L)) {
            val mapped = gadError(code).toAdError()
            assertTrue(
                mapped.isRetryableLoadFailure(),
                "GAD code $code mapped to '${mapped.code}', which the retry policy does not accept"
            )
        }
    }

    @Test
    fun `no fill is never retryable`() {
        val mapped = gadError(1L).toAdError()
        assertEquals("1", mapped.code)
        assertFalse(mapped.isRetryableLoadFailure(), "GADErrorNoFill (1) must never be retried")
    }

    @Test
    fun `the Android internal error name is not accidentally produced by iOS`() {
        val mapped = gadError(11L).toAdError()
        assertFalse(
            mapped.code == INTERNAL_LOAD_ERROR_CODE,
            "iOS produced the Android enum name; the two error vocabularies have merged"
        )
    }

    @Test
    fun `paid value micros are exact for decimals that Double cannot represent`() {
        // GMA hands back NSDecimalNumber. The old mapping did doubleValue * 1_000_000 and
        // truncated, so any value whose exact decimal is not representable in binary floating
        // point drifted by one or more micros — revenue export then disagreed with the source
        // SDK (P1-14). 0.07 and 8.87 are the classic non-representable cases.
        assertEquals(70_000L, NSDecimalNumber.decimalNumberWithString("0.07").toValueMicros())
        assertEquals(8_870_000L, NSDecimalNumber.decimalNumberWithString("8.87").toValueMicros())
        assertEquals(1_150_000L, NSDecimalNumber.decimalNumberWithString("1.15").toValueMicros())
    }

    @Test
    fun `paid value micros keep sub-micro precision and round half up`() {
        assertEquals(1L, NSDecimalNumber.decimalNumberWithString("0.000001").toValueMicros())
        assertEquals(0L, NSDecimalNumber.decimalNumberWithString("0").toValueMicros())
        // Below one micro the result must round rather than silently truncate toward zero,
        // and the rule must be stated rather than inherited from Double's behaviour.
        assertEquals(1L, NSDecimalNumber.decimalNumberWithString("0.0000005").toValueMicros())
        assertEquals(0L, NSDecimalNumber.decimalNumberWithString("0.0000004").toValueMicros())
    }

    @Test
    fun `paid value micros survive magnitudes beyond exact Double integers`() {
        // 2^53 + 1 micros is the first integer Double cannot represent; the old
        // doubleValue-based path could not round-trip it.
        assertEquals(
            9_007_199_254_740_993L,
            NSDecimalNumber.decimalNumberWithString("9007199254.740993").toValueMicros()
        )
    }
}
