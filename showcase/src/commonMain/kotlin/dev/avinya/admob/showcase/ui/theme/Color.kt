package dev.avinya.admob.showcase.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ============================================================================
// Dark Palette ("Emerald Obsidian")
// ============================================================================
internal val EmeraldBg = Color(0xFF0A120E)
internal val EmeraldOnBg = Color(0xFFF1F5F9)
internal val EmeraldSurface = Color(0xFF14221B)
internal val EmeraldOnSurface = Color(0xFFF1F5F9)
internal val EmeraldSurfaceVariant = Color(0xFF1C2E24)
internal val EmeraldOnSurfaceVariant = Color(0xFF94A3B8)

internal val EmeraldSurfaceBright = Color(0xFF243B2E)
internal val EmeraldSurfaceDim = Color(0xFF0E1A14)
internal val EmeraldSurfaceContainerLowest = Color(0xFF070D0A)
internal val EmeraldSurfaceContainerLow = Color(0xFF0E1A14)
internal val EmeraldSurfaceContainer = Color(0xFF14221B)
internal val EmeraldSurfaceContainerHigh = Color(0xFF1A2C23)
internal val EmeraldSurfaceContainerHighest = Color(0xFF22382C)

internal val EmeraldInverseSurface = Color(0xFFE2E8F0)
internal val EmeraldInverseOnSurface = Color(0xFF0F172A)

internal val EmeraldPrimary = Color(0xFF10B981)
internal val EmeraldOnPrimary = Color(0xFF003822)
internal val EmeraldPrimaryContainer = Color(0xFF064E3B)
internal val EmeraldOnPrimaryContainer = Color(0xFFA7F3D0)
internal val EmeraldInversePrimary = Color(0xFF059669)

internal val MintNeon = Color(0xFF34D399)
internal val MintOnSecondary = Color(0xFF003826)
internal val MintSecondaryContainer = Color(0xFF0D523F)
internal val MintOnSecondaryContainer = Color(0xFF6EE7B7)

internal val AmberAdGold = Color(0xFFF59E0B)
internal val AmberOnTertiary = Color(0xFF452B00)
internal val AmberTertiaryContainer = Color(0xFF78350F)
internal val AmberOnTertiaryContainer = Color(0xFFFDE68A)

internal val DarkError = Color(0xFFEF4444)
internal val DarkOnError = Color(0xFF450A0A)
internal val DarkErrorContainer = Color(0xFF7F1D1D)
internal val DarkOnErrorContainer = Color(0xFFFECACA)

internal val GlassBorderDark = Color(0x1FFFFFFF)
internal val GlassBorderDarkVariant = Color(0x0FFFFFFF)
internal val DarkScrim = Color(0xFF000000)

// ============================================================================
// Light Palette ("Slate Pearl")
// ============================================================================
internal val SlatePearlBg = Color(0xFFF8FAFC)
internal val LightOnBg = Color(0xFF0F172A)
internal val WhiteSurface = Color(0xFFFFFFFF)
internal val LightOnSurface = Color(0xFF0F172A)
internal val LightSurfaceVariant = Color(0xFFF1F5F9)
internal val LightOnSurfaceVariant = Color(0xFF64748B)

internal val LightSurfaceBright = Color(0xFFFFFFFF)
internal val LightSurfaceDim = Color(0xFFE2E8F0)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF8FAFC)
internal val LightSurfaceContainer = Color(0xFFF1F5F9)
internal val LightSurfaceContainerHigh = Color(0xFFE2E8F0)
internal val LightSurfaceContainerHighest = Color(0xFFCBD5E1)

internal val LightInverseSurface = Color(0xFF1E293B)
internal val LightInverseOnSurface = Color(0xFFF8FAFC)

internal val ForestEmerald = Color(0xFF059669)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFD1FAE5)
internal val LightOnPrimaryContainer = Color(0xFF064E3B)
internal val LightInversePrimary = Color(0xFF34D399)

internal val TealSecondary = Color(0xFF0D9488)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFCCFBF1)
internal val LightOnSecondaryContainer = Color(0xFF115E59)

internal val LightAmberTertiary = Color(0xFFD97706)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFFEF3C7)
internal val LightOnTertiaryContainer = Color(0xFF78350F)

internal val LightError = Color(0xFFDC2626)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFEE2E2)
internal val LightOnErrorContainer = Color(0xFF991B1B)

internal val GlassBorderLight = Color(0x0F000000)
internal val GlassBorderLightVariant = Color(0xFFE2E8F0)
internal val LightScrim = Color(0xFF000000)

// ============================================================================
// Material 3 Color Schemes
// ============================================================================
internal val ShowcaseDarkColors = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    inversePrimary = EmeraldInversePrimary,
    secondary = MintNeon,
    onSecondary = MintOnSecondary,
    secondaryContainer = MintSecondaryContainer,
    onSecondaryContainer = MintOnSecondaryContainer,
    tertiary = AmberAdGold,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = AmberOnTertiaryContainer,
    background = EmeraldBg,
    onBackground = EmeraldOnBg,
    surface = EmeraldSurface,
    onSurface = EmeraldOnSurface,
    surfaceVariant = EmeraldSurfaceVariant,
    onSurfaceVariant = EmeraldOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = EmeraldInverseSurface,
    inverseOnSurface = EmeraldInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = GlassBorderDark,
    outlineVariant = GlassBorderDarkVariant,
    scrim = DarkScrim,
    surfaceBright = EmeraldSurfaceBright,
    surfaceDim = EmeraldSurfaceDim,
    surfaceContainer = EmeraldSurfaceContainer,
    surfaceContainerHigh = EmeraldSurfaceContainerHigh,
    surfaceContainerHighest = EmeraldSurfaceContainerHighest,
    surfaceContainerLow = EmeraldSurfaceContainerLow,
    surfaceContainerLowest = EmeraldSurfaceContainerLowest,
)

internal val ShowcaseLightColors = lightColorScheme(
    primary = ForestEmerald,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = TealSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightAmberTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = SlatePearlBg,
    onBackground = LightOnBg,
    surface = WhiteSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = GlassBorderLight,
    outlineVariant = GlassBorderLightVariant,
    scrim = LightScrim,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
)
