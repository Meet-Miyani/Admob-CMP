package dev.avinya.admob.showcase.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.domain.library.LibraryEntry
import dev.avinya.admob.showcase.ui.inspector.InspectorEntryPoint
import dev.avinya.admob.showcase.ui.inspector.InspectorSheet
import dev.avinya.admob.showcase.ui.inspector.LocalInspectorPlacements

/**
 * The user's owned-and-engaged content, grouped by why it's here.
 *
 * **No `BannerAdView`, no `NativeAdView`, no interstitial on this screen.** That
 * is the demonstration: a showcase that puts an ad on every screen teaches the
 * wrong lesson. Restraint is part of a good integration, and the screen says so
 * out loud.
 */
@Composable
fun LibraryScreen(onArticleClick: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val entries by graph.articles.library().collectAsState(initial = emptyList())

    val inspectorEnabled by graph.settings.inspectorEnabled.collectAsState(initial = true)
    var showInspector by remember { mutableStateOf(false) }
    // Library is deliberately ad-free; the Inspector still shows, but with an
    // empty Placements tab and just the events/revenue columns populated.
    val placements = remember { emptyList<dev.avinya.ads.AdPlacement>() }

    CompositionLocalProvider(LocalInspectorPlacements provides placements) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "inspector_entry") {
                InspectorEntryPoint(
                    title = "Library",
                    enabled = inspectorEnabled,
                    onOpen = { showInspector = true },
                )
            }
            LibraryEntry.Kind.entries.forEach { kind ->
                val group = entries.filter { it.kind == kind }
                if (group.isNotEmpty()) {
                    item(key = "header_$kind") {
                        Text(kind.label(), style = MaterialTheme.typography.titleMedium)
                    }
                    items(group, key = { "${it.kind}_${it.articleId}" }) { entry ->
                        LibraryRow(entry = entry, onClick = { onArticleClick(entry.articleId) })
                    }
                }
            }

            if (entries.isEmpty()) {
                item { Text("Bookmark or unlock an article and it will appear here.") }
            }

            item {
                // Stated in the UI on purpose. A showcase that puts an ad on every
                // screen teaches the wrong lesson; restraint is part of a good
                // integration, and saying so is more useful than silently omitting.
                Text(
                    "No ads here — ads belong where you are browsing, not where you " +
                        "are managing things you already own.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showInspector) {
        InspectorSheet(placements = placements, onDismiss = { showInspector = false })
    }
}

private fun LibraryEntry.Kind.label(): String = when (this) {
    LibraryEntry.Kind.Bookmarked -> "Bookmarks"
    LibraryEntry.Kind.InProgress -> "In progress"
    LibraryEntry.Kind.Unlocked -> "Unlocked"
}

@Composable
private fun LibraryRow(entry: LibraryEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.section.uppercase(), style = MaterialTheme.typography.labelSmall)
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${entry.readTimeMin} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
