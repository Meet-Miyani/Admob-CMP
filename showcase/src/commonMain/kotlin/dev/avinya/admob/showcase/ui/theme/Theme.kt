package dev.avinya.admob.showcase.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** User-selectable theme preference. Persisted by `SettingsRepository`. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        val Default: ThemeMode = System
    }
}

/**
 * Resolves the preference against the platform setting.
 * Pure, so it is testable without Compose.
 */
fun ThemeMode.isDark(systemInDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemInDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun ShowcaseTheme(
    themeMode: ThemeMode = ThemeMode.Default,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark(systemInDark = isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) ShowcaseDarkColors else ShowcaseLightColors,
        typography = ShowcaseTypography,
        content = content,
    )
}
