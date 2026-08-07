package dev.avinya.admob.showcase.domain.telemetry

import dev.avinya.ads.AdEvent

/** A row destined for [dev.avinya.admob.showcase.data.db.entity.AdEventEntity]. */
data class AdEventRow(
    val at: Long,
    val placementId: String,
    val format: String,
    val type: String,
    val detail: String?,
)

/** A row destined for [dev.avinya.admob.showcase.data.db.entity.PaidEventEntity]. */
data class PaidEventRow(
    val placementId: String,
    val valueMicros: Long,
    val currency: String,
)

/** One revenue line in the Inspector's Revenue tab. */
data class PlacementRevenue(
    val placementId: String,
    val totalMicros: Long,
    val impressions: Int,
    val currency: String,
)

/**
 * Stable name for each [AdEvent] subtype, in the order the SDK defines them.
 *
 * The Inspector renders these strings verbatim, so this mapping is a public
 * contract with the UI: renaming an entry silently changes a tab the user
 * reads. Add a new branch when a new [AdEvent] subtype lands.
 */
fun eventTypeName(event: AdEvent): String = when (event) {
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

/**
 * Maps an [AdEvent] into the shape of a single log row.
 *
 * `format` is left blank here — the repository fills it from its placement
 * catalog, because the mapper is pure and must not know which placement ids
 * the showcase ships. [at] is the caller's recorded timestamp.
 */
fun AdEvent.toRow(at: Long): AdEventRow = AdEventRow(
    at = at,
    placementId = placementId.orEmpty(),
    format = "",
    type = eventTypeName(this),
    detail = detailFor(this),
)

/**
 * Maps a [AdEvent.Paid] into the shape of a revenue row.
 *
 * [at] is dropped: the timestamp lives on [AdEvent.toRow] (the matching
 * `ad_events` row) and is not duplicated on the revenue log.
 */
fun AdEvent.Paid.toPaidRow(@Suppress("UNUSED_PARAMETER") at: Long): PaidEventRow =
    PaidEventRow(
        placementId = placementId,
        valueMicros = paidEvent.value.valueMicros,
        currency = paidEvent.value.currencyCode,
    )

/**
 * Groups revenue rows by `(placementId, currency)` and orders the result by
 * total micros descending. Currencies are never summed across each other:
 * adding USD micros to EUR micros produces a meaningless number, so the
 * Inspector shows one row per currency.
 */
fun aggregateRevenue(rows: List<PaidEventRow>): List<PlacementRevenue> =
    rows.groupBy { it.placementId to it.currency }
        .map { (key, group) ->
            val (placementId, currency) = key
            PlacementRevenue(
                placementId = placementId,
                totalMicros = group.sumOf { it.valueMicros },
                impressions = group.size,
                currency = currency,
            )
        }
        .sortedByDescending { it.totalMicros }

private fun detailFor(event: AdEvent): String? = when (event) {
    is AdEvent.Loaded -> event.responseInfo?.responseId
    is AdEvent.LoadFailed -> "${event.error.code ?: "unknown"}: ${event.error.message}"
    is AdEvent.Impression -> event.adInstanceId
    is AdEvent.Clicked -> event.adInstanceId
    is AdEvent.ShowFailed -> "${event.error.code ?: "unknown"}: ${event.error.message}"
    is AdEvent.RewardEarned -> "${event.reward.amountMicros} ${event.reward.type}"
    is AdEvent.Paid -> "${event.paidEvent.value.valueMicros} ${event.paidEvent.value.currencyCode}"
    is AdEvent.VideoMuted -> event.muted.toString()
    is AdEvent.OpenedFullScreen,
    is AdEvent.ClosedFullScreen,
    is AdEvent.VideoStarted,
    is AdEvent.VideoPlayed,
    is AdEvent.VideoPaused,
    is AdEvent.VideoEnded,
    -> null
}
