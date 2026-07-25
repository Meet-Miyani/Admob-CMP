package dev.avinya.ads.debug

import dev.avinya.ads.AdEvent

/**
 * Applies the console's severity chips and keyword box.
 *
 * An empty [severities] set means "all" — the chips are an opt-in narrowing, not a
 * requirement. [query] matches the placement id and the event type name, case-insensitively.
 * The two conditions combine as AND.
 */
internal fun List<RecordedAdEvent>.filterEvents(
    severities: Set<EventSeverity>,
    query: String,
): List<RecordedAdEvent> {
    val needle = query.trim().lowercase()
    return filter { record ->
        val severityMatches = severities.isEmpty() || record.severity in severities
        val queryMatches = needle.isEmpty() ||
            record.event.placementId?.lowercase()?.contains(needle) == true ||
            record.event.typeName().lowercase().contains(needle)
        severityMatches && queryMatches
    }
}

/** Stable display name for an event type, e.g. `LoadFailed`. */
internal fun AdEvent.typeName(): String = when (this) {
    is AdEvent.Loaded -> "Loaded"
    is AdEvent.LoadFailed -> "LoadFailed"
    is AdEvent.Impression -> "Impression"
    is AdEvent.Clicked -> "Clicked"
    is AdEvent.OpenedFullScreen -> "OpenedFullScreen"
    is AdEvent.ClosedFullScreen -> "ClosedFullScreen"
    is AdEvent.ShowFailed -> "ShowFailed"
    is AdEvent.RewardEarned -> "RewardEarned"
    is AdEvent.Paid -> "Paid"
    is AdEvent.VideoStarted -> "VideoStarted"
    is AdEvent.VideoPlayed -> "VideoPlayed"
    is AdEvent.VideoPaused -> "VideoPaused"
    is AdEvent.VideoEnded -> "VideoEnded"
    is AdEvent.VideoMuted -> "VideoMuted"
}

/** Second line of a console row: the detail that makes the event worth reading. */
internal fun AdEvent.detailLine(): String = when (this) {
    is AdEvent.Loaded -> responseInfo?.loadedAdNetworkResponseInfo?.adSourceName ?: "no response info"
    is AdEvent.LoadFailed -> listOfNotNull(error.code?.let { "code $it" }, error.message).joinToString(" · ")
    is AdEvent.ShowFailed -> listOfNotNull(error.code?.let { "code $it" }, error.message).joinToString(" · ")
    is AdEvent.Impression -> adInstanceId?.let { "inst $it" } ?: "no instance id"
    is AdEvent.Clicked -> adInstanceId?.let { "inst $it" } ?: "no instance id"
    is AdEvent.OpenedFullScreen -> "opened"
    is AdEvent.ClosedFullScreen -> "dismissed"
    is AdEvent.RewardEarned -> "${reward.amountMicros / 1_000_000.0} ${reward.type}"
    is AdEvent.Paid -> with(paidEvent.value) {
        "${valueMicros / 1_000_000.0} $currencyCode · ${precision.name.lowercase()}"
    }
    is AdEvent.VideoMuted -> if (muted) "muted" else "unmuted"
    is AdEvent.VideoStarted, is AdEvent.VideoPlayed,
    is AdEvent.VideoPaused, is AdEvent.VideoEnded -> "native video"
}
