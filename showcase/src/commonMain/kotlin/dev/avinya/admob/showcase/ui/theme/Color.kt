package dev.avinya.admob.showcase.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Dark Palette (Emerald Obsidian)
internal val EmeraldBg = Color(0xFF0A120E)
internal val EmeraldSurface = Color(0xFF14221B)
internal val EmeraldSurfaceVariant = Color(0xFF1C2E24)
internal val EmeraldPrimary = Color(0xFF10B981)
internal val MintNeon = Color(0xFF34D399)
internal val AmberAdGold = Color(0xFFF59E0B)
internal val GlassBorderDark = Color(0x1FFFFFFF)

// Light Palette (Slate Pearl)
internal val SlatePearlBg = Color(0xFFF8FAFC)
internal val WhiteSurface = Color(0xFFFFFFFF)
internal val ForestEmerald = Color(0xFF059669)
internal val GlassBorderLight = Color(0x0F000000)

internal val ShowcaseDarkColors = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.Black,
    secondary = MintNeon,
    background = EmeraldBg,
    onBackground = Color(0xFFF8FAFC),
    surface = EmeraldSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = EmeraldSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = GlassBorderDark,
    tertiary = AmberAdGold,
)

internal val ShowcaseLightColors = lightColorScheme(
    primary = ForestEmerald,
    onPrimary = Color.White,
    secondary = Color(0xFF0D9488),
    background = SlatePearlBg,
    onBackground = Color(0xFF0F172A),
    surface = WhiteSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = GlassBorderLight,
    tertiary = AmberAdGold,
)

