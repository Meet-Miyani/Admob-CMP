package dev.avinya.ads.debug

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.avinya.ads.AdManager
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.NoOpAdManager
import dev.avinya.ads.debug.console.EventConsole
import dev.avinya.ads.debug.tabs.DiagnosticsTab
import dev.avinya.ads.debug.tabs.FormatsTab
import dev.avinya.ads.debug.tabs.LayoutsTab
import dev.avinya.ads.debug.ui.DebugBackButton
import dev.avinya.ads.debug.ui.DebugChevron
import dev.avinya.ads.debug.ui.DebugDivider
import dev.avinya.ads.debug.ui.DebugLabel
import dev.avinya.ads.debug.ui.DebugTabRail
import dev.avinya.ads.debug.ui.DebugText
import dev.avinya.ads.debug.ui.DebugTokens
import dev.avinya.ads.debug.ui.DebugType
import kotlinx.coroutines.launch

internal enum class ConsoleAnchor { Collapsed, Half, Full }

private val Tabs = listOf("Formats", "Layouts", "Diagnostics")

/**
 * On-device debug console for `admob-cmp`.
 *
 * Drives every ad format against [catalog], previews native ad layouts without a live fill,
 * exposes SDK/adapter/consent diagnostics, and streams [AdManager.events] into a persistent
 * console — provided [AdDebugRecorder.install] was called.
 *
 * **This screen applies no build-type gating.** A library cannot know a consumer's build
 * type; gate the navigation entry point yourself. Ad safety is enforced a layer down by
 * [dev.avinya.ads.AdPlacement.strictTestMode], which throws on a production ad unit id.
 *
 * @param catalog placements to drive. Defaults to Google test ad units.
 * @param manager defaults to [LocalAdManager]; pass explicitly if the CompositionLocal is
 *   not provided, or every request will fail with `sdkNotReady` from `NoOpAdManager`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun AdDebugScreen(
    catalog: AdDebugCatalog = AdDebugCatalog.Test,
    manager: AdManager = LocalAdManager.current,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val recorderState by AdDebugRecorder.state.collectAsState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .fillMaxSize()
            .background(DebugTokens.Bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Header(manager = manager, onBack = onBack)
        DebugDivider()
        DebugTabRail(Tabs, selectedTab, onSelect = { selectedTab = it })
        DebugDivider()

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxPx = with(density) { maxHeight.toPx() }
            val navBarHeight =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val collapsedHeight = DebugTokens.ConsoleCollapsedHeight + navBarHeight
            val collapsedPx = with(density) { collapsedHeight.toPx() }

            val dragState = remember(maxPx, collapsedPx) {
                AnchoredDraggableState(
                    initialValue = ConsoleAnchor.Collapsed,
                    anchors = DraggableAnchors {
                        ConsoleAnchor.Collapsed at maxPx - collapsedPx
                        ConsoleAnchor.Half at maxPx * 0.5f
                        ConsoleAnchor.Full at maxPx * 0.12f
                    },
                )
            }
            val consoleTopPx = dragState.requireOffset()
            val consoleHeight = with(density) { (maxPx - consoleTopPx).toDp() }

            // Content and console tile a Column exactly (no overlap in layout). clipToBounds on
            // the content keeps the native banner AndroidView — hosted in a clipChildren=false
            // FrameLayout — from drawing past its region onto the console below it.
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds(),
                ) {
                    when (selectedTab) {
                        0 -> FormatsTab(catalog, manager)
                        1 -> LayoutsTab(catalog)
                        else -> DiagnosticsTab(manager)
                    }
                }
                ConsolePanel(
                    height = consoleHeight,
                    dragState = dragState,
                    eventCount = recorderState.events.size,
                    onToggle = {
                        val target = if (dragState.currentValue == ConsoleAnchor.Collapsed) {
                            ConsoleAnchor.Half
                        } else {
                            ConsoleAnchor.Collapsed
                        }
                        scope.launch { dragState.animateTo(target) }
                    },
                    navBarHeight = navBarHeight,
                    content = { EventConsole(recorderState) },
                )
            }
        }
    }
}

@Composable
private fun Header(manager: AdManager, onBack: () -> Unit) {
    // Small horizontal padding: the 48dp back-button target supplies its own visual left inset
    // (an 18dp chevron centred in it), so the chevron lands at a natural toolbar margin.
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(DebugTokens.ToolbarHeight)
            .padding(horizontal = DebugTokens.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DebugBackButton(onClick = onBack)
        DebugText("Ad sandbox", style = DebugType.Title)
        Box(Modifier.weight(1f))
        DebugLabel(manager.diagnostics.sdkVersion() ?: "SDK —")
        Box(Modifier.width(DebugTokens.SpaceSm))
    }
}

/**
 * Resizable bottom log panel. The drag gesture lives on the [ConsoleHandle] only — never on the
 * whole panel — so the log list inside [content] scrolls independently instead of fighting the
 * drag. Rounded top + a top hairline read it as a layer above the content.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConsolePanel(
    height: Dp,
    dragState: AnchoredDraggableState<ConsoleAnchor>,
    eventCount: Int,
    onToggle: () -> Unit,
    navBarHeight: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            // clip before background so children are contained by the rounded bounds too.
            .clip(RoundedCornerShape(topStart = DebugTokens.SpaceMd, topEnd = DebugTokens.SpaceMd))
            .background(DebugTokens.Panel),
    ) {
        ConsoleHandle(
            dragState = dragState,
            collapsed = dragState.currentValue == ConsoleAnchor.Collapsed,
            eventCount = eventCount,
            onToggle = onToggle,
            onClear = { AdDebugRecorder.clear() },
            navBarHeight = navBarHeight,
        )
        DebugDivider()
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = navBarHeight),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConsoleHandle(
    dragState: AnchoredDraggableState<ConsoleAnchor>,
    collapsed: Boolean,
    eventCount: Int,
    onToggle: () -> Unit,
    onClear: () -> Unit,
    navBarHeight: Dp = 0.dp,
) {
    val handleHeight = DebugTokens.HandleHeight + navBarHeight
    Box(
        Modifier
            .fillMaxWidth()
            .height(handleHeight)
            .anchoredDraggable(dragState, Orientation.Vertical)
            .clickable(onClick = onToggle)
            .padding(horizontal = DebugTokens.SpaceLg)
            .padding(bottom = navBarHeight),
    ) {
        // Grab indicator, centred at top edge of the handle with SpaceMd top padding.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = DebugTokens.SpaceMd)
                .width(32.dp)
                .height(4.dp)
                .background(DebugTokens.Hairline, RoundedCornerShape(2.dp)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DebugText("Console", style = DebugType.Label)
            DebugLabel(if (eventCount == 1) "1 event" else "$eventCount events")
            Box(Modifier.weight(1f))
            // Clear lives here (not in the filter row) so nothing crowds the chips. Its own
            // clickable consumes the tap, so hitting it clears instead of toggling the sheet.
            // Only shown when the console is open and there is something to clear.
            if (!collapsed && eventCount > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = DebugTokens.SpaceSm, vertical = DebugTokens.SpaceXs),
                ) {
                    DebugText(
                        "Clear",
                        style = DebugType.LabelMuted.copy(color = DebugTokens.Accent)
                    )
                }
            }
            DebugChevron(expanded = !collapsed)
        }
    }
}

@Preview
@Composable
private fun AdDebugScreenPreview() {
    AdDebugScreen(
        catalog = AdDebugCatalog.Test,
        manager = NoOpAdManager,
    )
}

@Preview
@Composable
private fun HeaderPreview() {
    Header(
        manager = NoOpAdManager,
        onBack = {},
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
private fun ConsoleHandlePreview() {
    val density = LocalDensity.current
    val maxPx = with(density) { 400.dp.toPx() }
    val collapsedPx = with(density) { DebugTokens.ConsoleCollapsedHeight.toPx() }
    val dragState = remember(maxPx) {
        AnchoredDraggableState(
            initialValue = ConsoleAnchor.Collapsed,
            anchors = DraggableAnchors {
                ConsoleAnchor.Collapsed at maxPx - collapsedPx
                ConsoleAnchor.Half at maxPx * 0.5f
                ConsoleAnchor.Full at maxPx * 0.12f
            },
        )
    }
    ConsoleHandle(
        dragState = dragState,
        collapsed = true,
        eventCount = 3,
        onToggle = {},
        onClear = {},
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
private fun ConsolePanelPreview() {
    val density = LocalDensity.current
    val maxPx = with(density) { 300.dp.toPx() }
    val collapsedPx = with(density) { DebugTokens.ConsoleCollapsedHeight.toPx() }
    val dragState = remember(maxPx) {
        AnchoredDraggableState(
            initialValue = ConsoleAnchor.Half,
            anchors = DraggableAnchors {
                ConsoleAnchor.Collapsed at maxPx - collapsedPx
                ConsoleAnchor.Half at maxPx * 0.5f
                ConsoleAnchor.Full at maxPx * 0.12f
            },
        )
    }
    ConsolePanel(
        height = 200.dp,
        dragState = dragState,
        eventCount = 1,
        onToggle = {},
        content = {
            Box(Modifier.padding(DebugTokens.SpaceMd)) {
                DebugText("Console content preview", style = DebugType.Label)
            }
        },
    )
}

