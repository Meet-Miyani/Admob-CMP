package dev.avinya.admob.showcase.ui.inspector

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.avinya.ads.AdPlacement
import dev.avinya.admob.showcase.di.LocalAppGraph

/**
 * Three-tab bottom sheet that surfaces live ad state for the current screen.
 *
 * Reads the telemetry flows from the [LocalAppGraph] once, at the top, and
 * passes the snapshots down. Placements are passed in by the parent (the
 * screen) — the sheet does not look them up itself, because per-screen is
 * the point of [LocalInspectorPlacements].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectorSheet(
    placements: List<AdPlacement>,
    onDismiss: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val adEvents by graph.telemetry.adEvents.collectAsState(initial = emptyList())
    val policyDecisions by graph.telemetry.policyDecisions.collectAsState(initial = emptyList())
    val paidEvents by graph.telemetry.paidEvents.collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        SecondaryTabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) },
                )
            }
        }
        when (tab) {
            INDEX_PLACEMENTS -> PlacementsTab(
                placements = placements,
                modifier = tabModifier(),
            )
            INDEX_EVENTS -> EventsTab(
                adEvents = adEvents,
                policyDecisions = policyDecisions,
                isAndroid = isAndroid,
                modifier = tabModifier(),
            )
            INDEX_REVENUE -> RevenueTab(
                paidEvents = paidEvents,
                modifier = tabModifier(),
            )
        }
    }
}

/** Weight inside the sheet's [ColumnScope]; inline so the tabs stay layout-free. */
private fun ColumnScope.tabModifier(): Modifier = Modifier.fillMaxWidth().weight(1f, fill = false)

private const val INDEX_PLACEMENTS = 0
private const val INDEX_EVENTS = 1
private const val INDEX_REVENUE = 2
private val TABS = listOf("Placements", "Events", "Revenue")
