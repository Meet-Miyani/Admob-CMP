package dev.avinya.ads.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosNativeAdLayoutSizingTest {

    @Test
    fun `registration waits until the Compose host adopts the native content height`() {
        val result = resolveNativeAdLayoutSizing(
            currentHeight = 1.0,
            measuredHeight = 420.0,
        )

        assertTrue(result.shouldUpdateHeight)
        assertFalse(result.shouldRegisterNativeAd)
    }

    @Test
    fun `registration proceeds once native content is contained by the host`() {
        val result = resolveNativeAdLayoutSizing(
            currentHeight = 420.0,
            measuredHeight = 420.25,
        )

        assertFalse(result.shouldUpdateHeight)
        assertTrue(result.shouldRegisterNativeAd)
    }

    @Test
    fun `invalid native measurements never register the ad`() {
        val result = resolveNativeAdLayoutSizing(
            currentHeight = 0.0,
            measuredHeight = Double.NaN,
        )

        assertFalse(result.shouldUpdateHeight)
        assertFalse(result.shouldRegisterNativeAd)
    }
}
