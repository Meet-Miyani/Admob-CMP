package dev.avinya.admob.showcase.ui.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.avinya.admob.showcase.data.db.entity.AdEventEntity
import dev.avinya.admob.showcase.data.db.entity.PolicyDecisionEntity

/**
 * Events tab: the rolling ad-event log interleaved with policy decisions,
 * newest first.
 *
 * The two sources share a single column so a reader can answer "why did this
 * ad show / not show" without flipping tabs. The order is `at DESC` per
 * source and a stable merge by timestamp — see [mergedRows].
 *
 * [isAndroid] triggers an explicit note about the missing native video
 * events. The note is load-bearing: without it, an empty video section
 * reads as a bug in *our* code, when it is actually a GMA Next-Gen SDK gap.
 */
@Composable
fun EventsTab(
    adEvents: List<AdEventEntity>,
    policyDecisions: List<PolicyDecisionEntity>,
    isAndroid: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (isAndroid) {
            AndroidVideoGapBanner()
        }
        if (adEvents.isEmpty() && policyDecisions.isEmpty()) {
            Text(
                "No events yet — exercise the placements on this screen and " +
                    "they will appear here.",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val rows = mergedRows(adEvents, policyDecisions)
                items(rows, key = { it.key() }) { row ->
                    EventRow(row)
                }
            }
        }
    }
}

@Composable
private fun AndroidVideoGapBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            "VideoStarted / VideoPlayed / VideoPaused / VideoEnded / VideoMuted are " +
                "not delivered on Android — the GMA Next-Gen SDK exposes no equivalent " +
                "to iOS's GADVideoControllerDelegate. This is an upstream gap, not a " +
                "showcase omission.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun EventRow(row: EventRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(row.placementId, style = MaterialTheme.typography.labelSmall)
            Text(row.type, style = MaterialTheme.typography.titleSmall)
            row.reason?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class EventRow(
    val id: Long,
    val at: Long,
    val placementId: String,
    val type: String,
    val reason: String?,
) {
    fun key(): String = "evt-$id-$at-$type"
}

private fun mergedRows(
    adEvents: List<AdEventEntity>,
    policyDecisions: List<PolicyDecisionEntity>,
): List<EventRow> {
    val fromEvents = adEvents.map { e ->
        EventRow(
            id = e.id,
            at = e.at,
            placementId = e.placementId,
            type = e.type,
            reason = e.detail,
        )
    }
    val fromDecisions = policyDecisions.map { d ->
        // decision is "Show" or "Suppress:Reason"; reason is the enum name.
        // Show them as "decision · reason" so a reader sees both halves.
        val reasonText = when {
            d.reason.isNullOrBlank() -> null
            d.decision.startsWith("Show") -> null
            else -> d.reason
        }
        EventRow(
            id = -d.id,
            at = d.at,
            placementId = d.placementId,
            type = d.decision,
            reason = reasonText,
        )
    }
    return (fromEvents + fromDecisions).sortedByDescending { it.at }
}
