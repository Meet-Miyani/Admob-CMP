package avinya.tech.yt.ads.debug.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
internal fun DebugText(text: String, modifier: Modifier = Modifier, style: TextStyle = DebugType.Label) {
    BasicText(text = text, modifier = modifier, style = style)
}

@Composable
internal fun DebugLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text = text, modifier = modifier, style = DebugType.LabelMuted)
}

/**
 * [color] overrides [primary] when set, so a caller can render arbitrary status colors
 * (e.g. [DebugTokens.Error], [DebugTokens.Revenue]) rather than only the two fixed
 * primary/secondary tones. [primary] stays as the common-case shorthand.
 */
@Composable
internal fun DebugMono(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    color: Color? = null,
) {
    val resolved = color ?: if (primary) DebugTokens.TextPrimary else DebugTokens.TextSecondary
    BasicText(text = text, modifier = modifier, style = DebugType.Mono.copy(color = resolved))
}

@Composable
internal fun DebugDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(DebugTokens.Hairline))
}

/**
 * A proper back chevron with a comfortable square touch target. Drawn with [Canvas] rather than
 * a glyph or Material icon so the debug chrome stays self-contained and never depends on the
 * host app's icon set or theme.
 */
@Composable
internal fun DebugBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(DebugTokens.ToolbarHeight)
            .clip(RoundedCornerShape(DebugTokens.SpaceSm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val strokeWidth = 2.dp.toPx()
            val tipX = size.width * 0.34f
            val backX = size.width * 0.66f
            val midY = size.height / 2f
            drawLine(
                color = DebugTokens.TextPrimary,
                start = Offset(backX, size.height * 0.22f),
                end = Offset(tipX, midY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = DebugTokens.TextPrimary,
                start = Offset(backX, size.height * 0.78f),
                end = Offset(tipX, midY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * A dedicated expand/collapse chevron icon. Drawn with [Canvas] and smoothly animates rotation.
 * Points UP when collapsed (to expand), points DOWN when expanded (to collapse).
 */
@Composable
internal fun DebugChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    color: Color = DebugTokens.TextSecondary,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevronRotation",
    )
    Box(
        modifier = modifier
            .size(24.dp)
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val strokeWidth = 2.dp.toPx()
            val leftX = size.width * 0.15f
            val midX = size.width * 0.5f
            val rightX = size.width * 0.85f
            val topY = size.height * 0.65f
            val tipY = size.height * 0.25f

            drawLine(
                color = color,
                start = Offset(leftX, topY),
                end = Offset(midX, tipY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(rightX, topY),
                end = Offset(midX, tipY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Disabled buttons stay in place and dim rather than disappearing, so the layout never
 * jumps when an ad's load state changes.
 */
@Composable
internal fun DebugButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(DebugTokens.ButtonHeight)
            .border(1.dp, DebugTokens.Hairline, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = DebugType.Label.copy(
                color = if (enabled) DebugTokens.TextPrimary else DebugTokens.TextDisabled,
            ),
        )
    }
}

@Composable
internal fun DebugPill(status: StatusStyle, modifier: Modifier = Modifier, overrideLabel: String? = null) {
    val text = overrideLabel ?: status.label
    Row(
        modifier = modifier
            .background(status.color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = DebugTokens.SpaceSm, vertical = DebugTokens.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = status.glyph, style = DebugType.LabelMuted.copy(color = status.color))
        if (text.isNotEmpty()) {
            BasicText(text = text, style = DebugType.LabelMuted.copy(color = status.color))
        }
    }
}

@Composable
internal fun DebugCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DebugTokens.Panel, RoundedCornerShape(DebugTokens.CardRadius))
            .padding(DebugTokens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm),
        content = content,
    )
}

/**
 * Groups a run of cards under a quiet, uppercase-spaced label so a long tab reads as
 * sections rather than an undifferentiated stack.
 */
@Composable
internal fun DebugSectionHeader(title: String, modifier: Modifier = Modifier) {
    BasicText(
        text = title.uppercase(),
        modifier = modifier.padding(start = DebugTokens.SpaceXs, top = DebugTokens.SpaceXs),
        style = DebugType.Section,
    )
}

/**
 * A tappable disclosure header with a rotating chevron. Progressive disclosure: keep dense
 * or long-form detail (a code block, a waterfall) behind a tap instead of always on screen.
 */
@Composable
internal fun DebugExpander(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DebugTokens.RowHeight)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = DebugTokens.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "▸",
            modifier = Modifier.rotate(rotation),
            style = DebugType.LabelMuted.copy(color = DebugTokens.Accent),
        )
        BasicText(text = title, style = DebugType.LabelMuted.copy(color = DebugTokens.Accent))
    }
}

/**
 * A real code block: monospace on the base surface, rounded, and horizontally scrollable so
 * long lines keep their structure instead of wrapping into a paragraph.
 */
@Composable
internal fun DebugCodeBlock(code: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DebugTokens.SpaceSm))
            .background(DebugTokens.Bg)
            .horizontalScroll(rememberScrollState())
            .padding(DebugTokens.SpaceMd),
    ) {
        BasicText(text = code, style = DebugType.Code)
    }
}

/** Label-value row, the dev-tool properties-panel pattern. */
@Composable
internal fun DebugPropertyRow(label: String, value: String, valueColor: Color = DebugTokens.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = DebugTokens.RowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DebugLabel(label)
        DebugMono(value, color = valueColor)
    }
}

@Composable
internal fun DebugTabRail(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(DebugTokens.ToolbarHeight)) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(DebugTokens.ToolbarHeight)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = title,
                    style = DebugType.Label.copy(
                        color = if (selected) DebugTokens.TextPrimary else DebugTokens.TextSecondary,
                    ),
                )
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(DebugTokens.Accent),
                    )
                }
            }
        }
    }
}
