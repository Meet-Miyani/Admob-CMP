package avinya.tech.yt.ads.debug.console

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import avinya.tech.yt.ads.AdEvent
import avinya.tech.yt.ads.debug.EventSeverity
import avinya.tech.yt.ads.debug.RecordedAdEvent
import avinya.tech.yt.ads.debug.detailLine
import avinya.tech.yt.ads.debug.typeName
import avinya.tech.yt.ads.debug.ui.DebugDivider
import avinya.tech.yt.ads.debug.ui.DebugMono
import avinya.tech.yt.ads.debug.ui.DebugTokens
import avinya.tech.yt.ads.debug.ui.DebugType
import avinya.tech.yt.ads.debug.ui.ResponseInfoView
import avinya.tech.yt.ads.debug.ui.StatusStyle
import kotlin.time.Instant

internal fun EventSeverity.status(): StatusStyle = when (this) {
    EventSeverity.Error -> StatusStyle.Failed
    EventSeverity.Revenue -> StatusStyle.Revenue
    EventSeverity.Interaction -> StatusStyle.Interaction
    EventSeverity.Lifecycle -> StatusStyle.Loaded
    EventSeverity.Video -> StatusStyle.Video
}

/**
 * `HH:MM:SS.mmm` in UTC, fixed width so the monospace column stays aligned.
 *
 * Floor division is written out rather than using `Math.floorDiv`, which is JVM-only and
 * would fail the iOS compile.
 */
internal fun Instant.formatConsoleTime(): String {
    val totalMillis = toEpochMilliseconds()
    val millis = ((totalMillis % 1000) + 1000) % 1000
    val totalSeconds = if (totalMillis >= 0) totalMillis / 1000L else (totalMillis - 999L) / 1000L
    val secondOfDay = ((totalSeconds % 86_400L) + 86_400L) % 86_400L
    val hours = secondOfDay / 3600L
    val minutes = (secondOfDay % 3600L) / 60L
    val seconds = secondOfDay % 60L
    return buildString {
        append(hours.toString().padStart(2, '0')); append(':')
        append(minutes.toString().padStart(2, '0')); append(':')
        append(seconds.toString().padStart(2, '0')); append('.')
        append(millis.toString().padStart(3, '0'))
    }
}

/**
 * Two lines per row: timestamp + event type above, placement + detail below. Dense in
 * information while clearing the 44dp touch-target minimum, which a single-line row would not.
 */
@Composable
internal fun EventRow(
    record: RecordedAdEvent,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = record.severity.status()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .defaultMinSize(minHeight = DebugTokens.RowHeight)
            .padding(horizontal = DebugTokens.SpaceMd, vertical = DebugTokens.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm)) {
            DebugMono(record.timestamp.formatConsoleTime())
            DebugMono(
                text = "${status.glyph} ${record.event.typeName()}",
                modifier = Modifier,
                primary = false,
            )
        }
        DebugMono("${record.event.placementId ?: "global"} · ${record.event.detailLine()}")

        if (expanded) {
            DebugDivider()
            ExpandedDetail(record.event)
        }
    }
}

@Composable
private fun ExpandedDetail(event: AdEvent) {
    when (event) {
        is AdEvent.LoadFailed -> event.error.responseInfo?.let { ResponseInfoView(it) }
            ?: DebugMono("domain ${event.error.domain ?: "—"} · no response info")
        is AdEvent.ShowFailed -> event.error.responseInfo?.let { ResponseInfoView(it) }
            ?: DebugMono("domain ${event.error.domain ?: "—"} · no response info")
        is AdEvent.Loaded -> event.responseInfo?.let { ResponseInfoView(it) }
            ?: DebugMono("no response info")
        is AdEvent.Paid -> event.paidEvent.responseInfo?.let { ResponseInfoView(it) }
            ?: DebugMono("micros ${event.paidEvent.value.valueMicros}")
        is AdEvent.RewardEarned -> DebugMono(
            "type ${event.reward.type} · micros ${event.reward.amountMicros}"
        )
        else -> DebugMono("no further detail")
    }
}
