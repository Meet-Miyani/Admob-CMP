package dev.avinya.admob.showcase.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {

    @Test
    fun systemFollowsThePlatformSetting() {
        assertTrue(ThemeMode.System.isDark(systemInDark = true))
        assertFalse(ThemeMode.System.isDark(systemInDark = false))
    }

    @Test
    fun explicitModesIgnoreThePlatformSetting() {
        assertTrue(ThemeMode.Dark.isDark(systemInDark = false))
        assertFalse(ThemeMode.Light.isDark(systemInDark = true))
    }

    @Test
    fun defaultsToSystem() {
        assertEquals(ThemeMode.System, ThemeMode.Default)
    }
}
