package dev.avinya.ads.debug

import dev.avinya.ads.AdError
import dev.avinya.ads.AdEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class EventFilterTest {

    private fun record(event: AdEvent, sequence: Long = 0L) = RecordedAdEvent(
        sequence = sequence,
        timestamp = Instant.fromEpochMilliseconds(0L),
        event = event,
        severity = event.severity(),
    )

    private val events = listOf(
        record(AdEvent.Loaded("banner"), 0L),
        record(AdEvent.LoadFailed("interstitial", AdError(code = "3", message = "no fill")), 1L),
        record(AdEvent.Clicked("banner"), 2L),
    )

    @Test
    fun emptySeveritySetMeansNoFilter() {
        assertEquals(3, events.filterEvents(emptySet(), "").size)
    }

    @Test
    fun filtersToSelectedSeverities() {
        val result = events.filterEvents(setOf(EventSeverity.Error), "")
        assertEquals(1, result.size)
        assertEquals("interstitial", result.single().event.placementId)
    }

    @Test
    fun queryMatchesPlacementIdCaseInsensitively() {
        val result = events.filterEvents(emptySet(), "BANNER")
        assertEquals(2, result.size)
        assertTrue(result.all { it.event.placementId == "banner" })
    }

    @Test
    fun queryMatchesEventTypeName() {
        val result = events.filterEvents(emptySet(), "clicked")
        assertEquals(1, result.size)
    }

    @Test
    fun severityAndQueryCombineAsAnd() {
        val result = events.filterEvents(setOf(EventSeverity.Error), "banner")
        assertEquals(0, result.size)
    }
}
