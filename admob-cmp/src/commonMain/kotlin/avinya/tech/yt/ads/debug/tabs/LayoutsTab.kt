package avinya.tech.yt.ads.debug.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import avinya.tech.yt.ads.debug.AdDebugCatalog
import avinya.tech.yt.ads.debug.displayMeta
import avinya.tech.yt.ads.debug.toSpecSource
import avinya.tech.yt.ads.debug.ui.DebugButton
import avinya.tech.yt.ads.debug.ui.DebugCard
import avinya.tech.yt.ads.debug.ui.DebugCodeBlock
import avinya.tech.yt.ads.debug.ui.DebugExpander
import avinya.tech.yt.ads.debug.ui.DebugLabel
import avinya.tech.yt.ads.debug.ui.DebugMono
import avinya.tech.yt.ads.debug.ui.DebugPill
import avinya.tech.yt.ads.debug.ui.DebugText
import avinya.tech.yt.ads.debug.ui.DebugTokens
import avinya.tech.yt.ads.debug.ui.DebugType
import avinya.tech.yt.ads.debug.ui.StatusStyle
import avinya.tech.yt.ads.nativead.layout.AdLayout
import avinya.tech.yt.ads.nativead.layout.AdLayoutPreview
import avinya.tech.yt.ads.nativead.layout.AdLayoutPreviewData

/** Long-form sample data. Truncation and wrapping bugs surface here without waiting for an unlucky live ad. */
private val OverflowSample = AdLayoutPreviewData(
    headline = "An unusually long advertiser headline that will not fit on one line at all",
    body = "Body copy that runs well past the two or three lines a card is likely to allow, " +
        "so clipping, ellipsis and wrapping behaviour are all visible at design time.",
    callToAction = "Install this application now",
    advertiser = "A Very Long Advertiser Name Ltd",
)

@Composable
internal fun LayoutsTab(catalog: AdDebugCatalog, modifier: Modifier = Modifier) {
    var useOverflow by remember { mutableStateOf(false) }
    val data = if (useOverflow) OverflowSample else AdLayoutPreviewData.default

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DebugTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(DebugTokens.SpaceMd),
    ) {
        DebugButton(
            label = if (useOverflow) "Sample data: overflow" else "Sample data: default",
            onClick = { useOverflow = !useOverflow },
            modifier = Modifier.fillMaxWidth(),
        )
        DebugLabel("Previews render without loading an ad — no fill or network required.")

        catalog.layouts.forEach { layout -> LayoutCard(layout, data) }
    }
}

@Composable
private fun LayoutCard(layout: AdLayout, data: AdLayoutPreviewData) {
    val report = layout.validation
    val meta = remember(layout) { layout.displayMeta() }
    var showSource by remember { mutableStateOf(false) }

    DebugCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DebugText(meta.name, style = DebugType.Title, modifier = Modifier.weight(1f))
            DebugPill(
                status = when {
                    !report.isValid -> StatusStyle.Failed
                    report.hasWarnings -> StatusStyle.Loading
                    else -> StatusStyle.Loaded
                },
                overrideLabel = when {
                    !report.isValid -> "${report.errors.size} errors"
                    report.hasWarnings -> "${report.warnings.size} warnings"
                    else -> "valid"
                },
            )
        }
        DebugLabel(meta.description)

        AdLayoutPreview(layout = layout, modifier = Modifier.fillMaxWidth(), data = data)

        report.errors.forEach { DebugMono("✕ ${it.message}", primary = true) }
        report.warnings.forEach { DebugMono("! ${it.message}") }

        // Source is off by default — a tap reveals a proper, indented code block instead of a
        // wall of text always on screen.
        DebugExpander(
            title = if (showSource) "Hide layout source" else "Show layout source",
            expanded = showSource,
            onToggle = { showSource = !showSource },
        )
        if (showSource) {
            DebugCodeBlock(remember(layout) { layout.toSpecSource() })
        }
    }
}
