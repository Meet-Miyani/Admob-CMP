package dev.avinya.ads.debug

import dev.avinya.ads.AdEvent
import kotlin.time.Instant

/**
 * Severity bucket used by the debug console's filter chips. Not a log level — it groups
 * events by what a developer is looking for, not by how bad they are.
 */
internal enum class EventSeverity { Error, Revenue, Interaction, Lifecycle, Video }

/** One [AdEvent] as retained by [AdDebugRecorder]. */
internal data class RecordedAdEvent(
    /** Monotonic, assigned at record time. Survives ring-buffer eviction. */
    val sequence: Long,
    val timestamp: Instant,
    val event: AdEvent,
    val severity: EventSeverity,
)

/**
 * Classifies an event for the console's filters.
 *
 * The `when` is exhaustive over the sealed interface deliberately — adding a new [AdEvent]
 * subtype must fail to compile here rather than silently defaulting to a bucket.
 */
internal fun AdEvent.severity(): EventSeverity = when (this) {
    is AdEvent.LoadFailed, is AdEvent.ShowFailed -> EventSeverity.Error
    is AdEvent.Paid, is AdEvent.RewardEarned -> EventSeverity.Revenue
    is AdEvent.Impression, is AdEvent.Clicked -> EventSeverity.Interaction
    is AdEvent.Loaded, is AdEvent.OpenedFullScreen, is AdEvent.ClosedFullScreen -> EventSeverity.Lifecycle
    is AdEvent.VideoStarted, is AdEvent.VideoPlayed, is AdEvent.VideoPaused,
    is AdEvent.VideoEnded, is AdEvent.VideoMuted -> EventSeverity.Video
}
