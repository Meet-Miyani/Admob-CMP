package dev.avinya.admob.showcase.ui.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.avinya.admob.showcase.data.db.entity.PaidEventEntity
import dev.avinya.admob.showcase.domain.telemetry.PlacementRevenue
import dev.avinya.admob.showcase.domain.telemetry.aggregateRevenue

/**
 * Revenue tab: per-placement aggregate (top earner first) and the raw paid
 * log.
 *
 * The two are kept separate so a reader can confirm an aggregate against its
 * source rows without leaving the tab. Currencies are never summed across
 * each other — see [aggregateRevenue].
 */
@Composable
fun RevenueTab(paidEvents: List<PaidEventEntity>, modifier: Modifier = Modifier) {
    if (paidEvents.isEmpty()) {
        Text(
            "No paid events yet. Impressions with revenue attached will " +
                "appear here as they are recorded.",
            modifier = modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val rows = paidEvents.map { it.toPaidRow() }
    val aggregates = aggregateRevenue(rows)
    val byPlacement = paidEvents.groupBy { it.placementId to it.precision }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "section_aggregate") {
            SectionHeading("Per placement")
        }
        items(aggregates, key = { "${it.placementId}-${it.currency}" }) { line ->
            AggregateRow(
                line = line,
                // The most-recent precision for this placement/currency is a
                // useful stand-in when summing across impressions with
                // different precisions (e.g. one Precise + one Estimated).
                precisionLabel = byPlacement[line.placementId to line.currency]
                    ?.lastOrNull()?.precision
                    ?: UNKNOWN_PRECISION,
            )
        }
        item(key = "section_divider") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item(key = "section_raw") { SectionHeading("Raw paid events") }
        items(paidEvents, key = { it.id }) { event -> RawRow(event) }
    }
}

@Composable
private fun AggregateRow(line: PlacementRevenue, precisionLabel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.placementId, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatMicrosAsCurrency(line.totalMicros, line.currency),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${line.impressions} imp · $precisionLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RawRow(event: PaidEventEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.placementId, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatMicrosAsCurrency(event.valueMicros, event.currency),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    event.precision,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** Trivial mapper: the entity already holds every field the aggregate needs. */
private fun PaidEventEntity.toPaidRow(): dev.avinya.admob.showcase.domain.telemetry.PaidEventRow =
    dev.avinya.admob.showcase.domain.telemetry.PaidEventRow(
        placementId = placementId,
        valueMicros = valueMicros,
        currency = currency,
    )

private fun formatMicrosAsCurrency(micros: Long, currency: String): String {
    val whole = micros / MICROS_PER_UNIT
    val fraction = (micros % MICROS_PER_UNIT).toString().padStart(6, '0').trimEnd('0')
    return if (fraction.isEmpty()) {
        "$whole $currency"
    } else {
        "$whole.$fraction $currency"
    }
}

private const val MICROS_PER_UNIT: Long = 1_000_000L
private const val UNKNOWN_PRECISION: String = "Unknown"
