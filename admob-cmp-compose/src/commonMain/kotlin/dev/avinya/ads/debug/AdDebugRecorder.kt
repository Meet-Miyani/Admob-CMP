@file:OptIn(dev.avinya.ads.InternalAdMobCmpApi::class)

package dev.avinya.ads.debug

import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdManager
import dev.avinya.ads.internal.FullScreenStateLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** What the console renders. */
internal data class RecorderState(
    val events: List<RecordedAdEvent> = emptyList(),
    val evictedCount: Long = 0L,
    val isInstalled: Boolean = false,
)

/**
 * Fixed-capacity, oldest-first ring buffer over [RecordedAdEvent].
 *
 * [clock] follows the module's per-class injection convention (see `FullScreenSlotCore`,
 * `NativePoolCore`) so timestamps are testable without a real clock.
 */
internal class EventRingBuffer(
    private val capacity: Int,
    private val clock: () -> Instant = { Clock.System.now() },
) {
    private val lock = FullScreenStateLock()
    private val events = ArrayDeque<RecordedAdEvent>(capacity)
    private var nextSequence = 0L
    private var evicted = 0L

    fun record(event: AdEvent): Unit = lock.withLock {
        events.addLast(
            RecordedAdEvent(
                sequence = nextSequence++,
                timestamp = clock(),
                event = event,
                severity = event.severity(),
            )
        )
        while (events.size > capacity) {
            events.removeFirst()
            evicted++
        }
    }

    fun clear(): Unit = lock.withLock {
        events.clear()
        evicted = 0L
        nextSequence = 0L
    }

    fun snapshot(): RecorderState = lock.withLock {
        RecorderState(events = events.toList(), evictedCount = evicted, isInstalled = true)
    }
}

/**
 * Opt-in recorder for the debug console's event stream.
 *
 * Install it at [AdManager] construction in debug builds so the buffer is live before the
 * first ad request — otherwise SDK initialization, consent gathering and cold-start app-open
 * events have already passed by the time the screen opens.
 *
 * ```kotlin
 * if (isDebugBuild()) AdDebugRecorder.install(adManager, appScope)
 * ```
 *
 * Not installed in release builds, the recorder costs nothing: no collector runs and the
 * buffer stays empty.
 */
public object AdDebugRecorder {

    /** Events retained before oldest-first eviction begins. */
    public const val DEFAULT_CAPACITY: Int = 500

    private val _state = MutableStateFlow(RecorderState())
    internal val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var job: Job? = null
    private var buffer: EventRingBuffer? = null

    /**
     * Subscribes to [manager]'s event stream and retains the most recent [capacity] events.
     * Installing twice replaces the previous subscription and clears the buffer.
     *
     * @param scope owns the collector's lifetime. Required rather than internally created so
     *   the recorder never holds an unscoped coroutine.
     */
    public fun install(
        manager: AdManager,
        scope: CoroutineScope,
        capacity: Int = DEFAULT_CAPACITY,
    ) {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
        uninstall()
        val ring = EventRingBuffer(capacity)
        buffer = ring
        _state.value = RecorderState(isInstalled = true)
        job = scope.launch {
            manager.events.collect { event ->
                ring.record(event)
                _state.value = ring.snapshot()
            }
        }
    }

    /** Cancels the subscription and clears the buffer. */
    public fun uninstall() {
        job?.cancel()
        job = null
        buffer = null
        _state.value = RecorderState(isInstalled = false)
    }

    /** Drops retained events but keeps recording. */
    internal fun clear() {
        buffer?.let {
            it.clear()
            _state.value = it.snapshot()
        }
    }
}
