package dev.avinya.ads.debug.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The debug console's chrome. Deliberately does NOT inherit the host app's theme, so a
 * developer can always tell what the SDK rendered from what the harness rendered.
 *
 * Values are accessibility-derived, not taste:
 * - [Bg] sits above the near-black halation threshold; pure black makes text appear to glow,
 *   worst for readers with astigmatism.
 * - [TextPrimary] is off-white rather than #FFFFFF for the same reason.
 * - Status hues are desaturated ~30%; saturated colour on dark backgrounds visually vibrates.
 * - Blue and amber carry the primary distinction (protan/deutan safe). Green and red only
 *   ever decorate a glyph that already carries the meaning — see [StatusStyle].
 *
 * This is the ONLY file allowed to declare colour literals.
 */
internal object DebugTokens {
    val Bg: Color = Color(0xFF16181C)
    val Panel: Color = Color(0xFF1E2126)
    val Hairline: Color = Color(0xFF2A2F36)
    val TextPrimary: Color = Color(0xFFE6E8EB)
    val TextSecondary: Color = Color(0xFF9BA3AE)
    val TextDisabled: Color = Color(0xFF5A6169)
    val Accent: Color = Color(0xFF6CA8F0)

    val Ok: Color = Color(0xFF7DBF8A)
    val Error: Color = Color(0xFFE08A7A)
    val Revenue: Color = Color(0xFFD4A960)
    val Video: Color = Color(0xFFA98BD1)

    /** 4/8dp scale, extended upward so the screen can breathe. */
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 20.dp

    /** Outer padding for a scrollable tab's content. Generous on purpose. */
    val ScreenPadding = 16.dp

    /** Corner radius shared by cards, code blocks and pills' container. */
    val CardRadius = 12.dp

    /** WCAG 2.5.8 wants 24dp minimum; Android guidance is 48dp. Rows carry two lines. */
    val RowHeight = 44.dp
    val ButtonHeight = 48.dp
    val ToolbarHeight = 48.dp

    /** The console's grab bar. Tall enough to be a comfortable drag + tap target. */
    val HandleHeight = 48.dp

    /** Collapsed console anchor: just the header/handle bar peeking above the bottom edge. */
    val ConsoleCollapsedHeight = HandleHeight
}

internal object DebugType {
    val Label = TextStyle(fontSize = 13.sp, color = DebugTokens.TextPrimary)
    val LabelMuted = TextStyle(fontSize = 12.sp, color = DebugTokens.TextSecondary)
    val Title = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DebugTokens.TextPrimary)
    val Mono = TextStyle(
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = DebugTokens.TextSecondary,
    )
    val MonoPrimary = Mono.copy(color = DebugTokens.TextPrimary)

    /** Section label above a group of cards: small, uppercase-spaced, muted. */
    val Section = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        color = DebugTokens.TextSecondary,
    )

    /** Readable monospace for a real code block — primary tone, roomier line height. */
    val Code = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFamily = FontFamily.Monospace,
        color = DebugTokens.TextPrimary,
    )
}

/**
 * Redundant encoding: glyph + word + colour. Colour is never the sole carrier of meaning,
 * so the console stays readable in greyscale and for colour-vision-deficient viewers.
 */
internal data class StatusStyle(val glyph: String, val label: String, val color: Color) {
    companion object {
        val Loaded = StatusStyle("✓", "loaded", DebugTokens.Ok)
        val Failed = StatusStyle("✕", "failed", DebugTokens.Error)
        val Loading = StatusStyle("◌", "loading", DebugTokens.TextSecondary)
        val Idle = StatusStyle("·", "idle", DebugTokens.TextSecondary)
        val Interaction = StatusStyle("●", "", DebugTokens.Accent)
        val Revenue = StatusStyle("$", "", DebugTokens.Revenue)
        val Video = StatusStyle("▶", "", DebugTokens.Video)
    }
}
