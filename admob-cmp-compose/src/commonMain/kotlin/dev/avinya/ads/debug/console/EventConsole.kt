package dev.avinya.ads.debug.console

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.avinya.ads.debug.EventSeverity
import dev.avinya.ads.debug.RecorderState
import dev.avinya.ads.debug.filterEvents
import dev.avinya.ads.debug.ui.DebugDivider
import dev.avinya.ads.debug.ui.DebugLabel
import dev.avinya.ads.debug.ui.DebugText
import dev.avinya.ads.debug.ui.DebugTokens
import dev.avinya.ads.debug.ui.DebugType

@Composable
internal fun EventConsole(state: RecorderState, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(emptySet<EventSeverity>()) }
    var query by remember { mutableStateOf("") }
    var expandedSequence by remember { mutableStateOf<Long?>(null) }

    val visible = remember(state.events, selected, query) {
        state.events.filterEvents(selected, query).asReversed()
    }

    Column(
        modifier.fillMaxSize()
            .background(DebugTokens.Panel)
            // systemBars, not just statusBars: the console panel lives at the bottom, so its
            // drag handle must clear the system navigation/gesture bar to stay grabbable. Bg is
            // painted before the inset, so it still fills edge-to-edge behind both bars.
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        ConsoleToolbar(
            state = state,
            selected = selected,
            onToggleSeverity = { severity ->
                selected = if (severity in selected) selected - severity else selected + severity
            },
            query = query,
            onQueryChange = { query = it },
        )
        DebugDivider()

        if (!state.isInstalled) {
            NotInstalledEmptyState()
        } else if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(DebugTokens.SpaceMd), Alignment.Center) {
                DebugLabel(if (state.events.isEmpty()) "no events recorded yet" else "no events match this filter")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.sequence }) { record ->
                    EventRow(
                        record = record,
                        expanded = expandedSequence == record.sequence,
                        onToggle = {
                            expandedSequence =
                                if (expandedSequence == record.sequence) null else record.sequence
                        },
                    )
                    DebugDivider()
                }
            }
        }
    }
}

@Composable
private fun ConsoleToolbar(
    state: RecorderState,
    selected: Set<EventSeverity>,
    onToggleSeverity: (EventSeverity) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val counts = remember(state.events) { state.events.groupingBy { it.severity }.eachCount() }

    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = DebugTokens.SpaceMd, vertical = DebugTokens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(DebugTokens.SpaceMd),
    ) {
        // A single scrollable strip that spans the full width: chips never overflow off-screen
        // or wrap-collapse. Clear lives up in the console header bar, so nothing crowds this row.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip("all ${state.events.size}", active = selected.isEmpty()) {
                EventSeverity.entries.forEach { if (it in selected) onToggleSeverity(it) }
            }
            EventSeverity.entries.forEach { severity ->
                val count = counts[severity] ?: 0
                if (count > 0 || severity == EventSeverity.Error) {
                    FilterChip(
                        label = "${severity.name.lowercase()} $count",
                        active = severity in selected,
                        onClick = { onToggleSeverity(severity) },
                    )
                }
            }
        }

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = DebugType.MonoPrimary,
            cursorBrush = SolidColor(DebugTokens.Accent),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(DebugTokens.RowHeight)
                        .background(DebugTokens.Bg, RoundedCornerShape(6.dp))
                        .padding(horizontal = DebugTokens.SpaceSm),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) DebugLabel("filter by placement or event type")
                    inner()
                }
            },
        )

        if (state.evictedCount > 0L) {
            DebugLabel("${state.evictedCount} older events evicted")
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(DebugTokens.RowHeight)
            .clip(RoundedCornerShape(DebugTokens.RowHeight / 2))
            .background(if (active) DebugTokens.Accent.copy(alpha = 0.20f) else DebugTokens.Bg)
            .clickable(onClick = onClick)
            .padding(horizontal = DebugTokens.SpaceMd),
        contentAlignment = Alignment.Center,
    ) {
        DebugText(
            label,
            style = DebugType.LabelMuted.copy(
                color = if (active) DebugTokens.Accent else DebugTokens.TextSecondary,
            ),
        )
    }
}

/**
 * The recorder is opt-in, so "no events" and "not recording" are different states and must
 * read differently — otherwise a developer debugs the SDK when the real problem is a missing
 * install call.
 */
@Composable
private fun NotInstalledEmptyState() {
    Column(
        Modifier.fillMaxSize().padding(DebugTokens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm),
    ) {
        DebugText("Event recording is not installed")
        DebugLabel("Call this at AdManager construction in debug builds:")
        DebugText("AdDebugRecorder.install(adManager, appScope)", style = DebugType.MonoPrimary)
    }
}
