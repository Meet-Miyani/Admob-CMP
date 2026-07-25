package dev.avinya.ads.debug

import dev.avinya.ads.AdEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AdDebugRecorderTest {

    private fun fixedClock(millis: Long): () -> Instant = { Instant.fromEpochMilliseconds(millis) }

    @Test
    fun retainsEventsUpToCapacity() {
        val buffer = EventRingBuffer(capacity = 3, clock = fixedClock(0L))
        repeat(3) { buffer.record(AdEvent.Loaded("p$it")) }

        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.events.size)
        assertEquals(0L, snapshot.evictedCount)
    }

    @Test
    fun evictsOldestFirstBeyondCapacity() {
        val buffer = EventRingBuffer(capacity = 3, clock = fixedClock(0L))
        repeat(5) { buffer.record(AdEvent.Loaded("p$it")) }

        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.events.size)
        assertEquals(2L, snapshot.evictedCount)
        assertEquals(
            listOf("p2", "p3", "p4"),
            snapshot.events.map { it.event.placementId },
        )
    }

    @Test
    fun sequenceStaysMonotonicAcrossEviction() {
        val buffer = EventRingBuffer(capacity = 2, clock = fixedClock(0L))
        repeat(5) { buffer.record(AdEvent.Loaded("p$it")) }

        assertEquals(listOf(3L, 4L), buffer.snapshot().events.map { it.sequence })
    }

    @Test
    fun stampsEventsFromTheInjectedClock() {
        val buffer = EventRingBuffer(capacity = 2, clock = fixedClock(1_700_000_000_000L))
        buffer.record(AdEvent.Loaded("p"))

        assertEquals(
            Instant.fromEpochMilliseconds(1_700_000_000_000L),
            buffer.snapshot().events.single().timestamp,
        )
    }

    @Test
    fun clearResetsEventsAndEvictedCount() {
        val buffer = EventRingBuffer(capacity = 2, clock = fixedClock(0L))
        repeat(5) { buffer.record(AdEvent.Loaded("p$it")) }

        buffer.clear()

        val snapshot = buffer.snapshot()
        assertEquals(0, snapshot.events.size)
        assertEquals(0L, snapshot.evictedCount)
    }
}
