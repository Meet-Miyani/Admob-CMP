package dev.avinya.admob.showcase.data.prefs

import dev.avinya.admob.showcase.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    @Test
    fun defaultsBeforeAnythingIsWritten() = runTest {
        val repo = SettingsRepository(inMemoryPreferencesDataStore())

        assertEquals(ThemeMode.System, repo.themeMode.first())
        assertFalse(repo.onboardingComplete.first())
        assertTrue(repo.inspectorEnabled.first())
        assertTrue(repo.adsMasterSwitch.first())
    }

    @Test
    fun persistsThemeMode() = runTest {
        val repo = SettingsRepository(inMemoryPreferencesDataStore())

        repo.setThemeMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, repo.themeMode.first())
    }

    @Test
    fun persistsOnboardingCompletion() = runTest {
        val repo = SettingsRepository(inMemoryPreferencesDataStore())

        repo.setOnboardingComplete(true)

        assertTrue(repo.onboardingComplete.first())
    }

    @Test
    fun anUnrecognisedStoredThemeFallsBackToTheDefault() = runTest {
        val store = inMemoryPreferencesDataStore()
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply { set(SettingsKeys.ThemeMode, "Sepia") }
        }

        assertEquals(ThemeMode.System, SettingsRepository(store).themeMode.first())
    }
}
