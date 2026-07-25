package dev.avinya.ads

import dev.avinya.ads.ui.MIN_VIEWABLE_FRACTION
import dev.avinya.ads.ui.isViewable
import dev.avinya.ads.ui.visibleFractionOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BannerVisibilityTest {

    @Test
    fun `fully on screen is fraction one`() {
        assertEquals(1f, visibleFractionOf(totalArea = 1000f, visibleArea = 1000f))
    }

    @Test
    fun `fully scrolled away is fraction zero`() {
        assertEquals(0f, visibleFractionOf(totalArea = 1000f, visibleArea = 0f))
    }

    @Test
    fun `half on screen is fraction one half`() {
        assertEquals(0.5f, visibleFractionOf(totalArea = 1000f, visibleArea = 500f))
    }

    @Test
    fun `a zero-area node is not viewable rather than dividing by zero`() {
        assertEquals(0f, visibleFractionOf(totalArea = 0f, visibleArea = 0f))
        assertEquals(0f, visibleFractionOf(totalArea = -1f, visibleArea = 100f))
    }

    @Test
    fun `fraction is clamped into zero to one`() {
        assertEquals(1f, visibleFractionOf(totalArea = 100f, visibleArea = 250f))
    }

    @Test
    fun `the threshold is AdMob's fifty percent and is inclusive`() {
        assertEquals(0.5f, MIN_VIEWABLE_FRACTION)
        assertTrue(isViewable(0.5f), "exactly 50% must count as viewable")
        assertTrue(isViewable(0.51f))
        assertFalse(isViewable(0.49f))
        assertFalse(isViewable(0f))
    }
}
