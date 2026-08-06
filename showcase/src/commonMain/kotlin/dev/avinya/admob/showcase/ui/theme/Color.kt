package dev.avinya.admob.showcase.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF101418)
private val Paper = Color(0xFFFBFAF7)
private val Accent = Color(0xFF2D6A4F)
private val AccentDark = Color(0xFF74C69D)
private val Muted = Color(0xFF6B7280)

internal val ShowcaseLightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Muted,
)

internal val ShowcaseDarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Color(0xFF181D23),
    onSurface = Paper,
    onSurfaceVariant = Color(0xFF9CA3AF),
)
