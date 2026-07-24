package avinya.tech.yt.ads

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The guards live in the platform managers, which commonTest cannot construct.
 * This test pins the error-message contract that both platform implementations
 * must produce, using the same require() shape they use.
 */
class FactoryFormatGuardTest {

    private fun requireFormat(actual: AdFormat, expected: AdFormat, placementId: String) {
        require(actual == expected) {
            "AdPlacement '$placementId' has format $actual but was passed to a $expected factory. " +
                "The factory function and placement.format must agree."
        }
    }

    @Test
    fun mismatchedFormatFailsWithBothFormatsNamed() {
        val failure = assertFailsWith<IllegalArgumentException> {
            requireFormat(AdFormat.Rewarded, AdFormat.Interstitial, "promo_slot")
        }
        val message = failure.message ?: ""
        assertTrue(message.contains("Rewarded"), "message must name the actual format: $message")
        assertTrue(message.contains("Interstitial"), "message must name the expected format: $message")
        assertTrue(message.contains("promo_slot"), "message must name the placement: $message")
    }

    @Test
    fun matchingFormatPasses() {
        requireFormat(AdFormat.Interstitial, AdFormat.Interstitial, "promo_slot")
    }
}
