package dev.avinya.ads.nativead.rendering

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNativeAdContainmentTest {

    @Test
    fun `asset fully inside root is contained`() {
        val root = NativeAdBounds(left = 10, top = 20, right = 210, bottom = 320)
        val asset = NativeAdBounds(left = 10, top = 20, right = 210, bottom = 120)

        assertTrue(root.contains(asset))
    }

    @Test
    fun `translated asset outside root is rejected`() {
        val root = NativeAdBounds(left = 10, top = 20, right = 210, bottom = 320)
        val asset = NativeAdBounds(left = 9, top = 20, right = 210, bottom = 120)

        assertFalse(root.contains(asset))
    }
}
