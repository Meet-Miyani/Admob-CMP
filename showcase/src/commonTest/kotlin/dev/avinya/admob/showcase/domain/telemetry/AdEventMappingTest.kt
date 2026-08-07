package dev.avinya.admob.showcase.domain.telemetry

import dev.avinya.ads.AdEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class AdEventMappingTest {

    @Test
    fun everyEventTypeMapsToAStableRowTypeName() {
        // The Inspector renders these strings, so they are a contract, not
        // an implementation detail — renaming one silently changes the UI.
        assertEquals("Loaded", eventTypeName(AdEvent.Loaded(placementId = "p")))
        assertEquals("Impression", eventTypeName(AdEvent.Impression(placementId = "p")))
        assertEquals("Clicked", eventTypeName(AdEvent.Clicked(placementId = "p")))
    }

    @Test
    fun revenueAggregatesPerPlacementInMicros() {
        val rows = listOf(
            PaidEventRow(placementId = "feed_banner", valueMicros = 1_500, currency = "USD"),
            PaidEventRow(placementId = "feed_banner", valueMicros = 2_500, currency = "USD"),
            PaidEventRow(placementId = "store_rewarded", valueMicros = 9_000, currency = "USD"),
        )

        assertEquals(
            listOf(
                PlacementRevenue("store_rewarded", totalMicros = 9_000, impressions = 1, currency = "USD"),
                PlacementRevenue("feed_banner", totalMicros = 4_000, impressions = 2, currency = "USD"),
            ),
            aggregateRevenue(rows),
        )
    }

    @Test
    fun aggregationIsOrderedByRevenueDescendingSoTheTopEarnerIsFirst() {
        val rows = listOf(
            PaidEventRow("a", valueMicros = 10, currency = "USD"),
            PaidEventRow("b", valueMicros = 99, currency = "USD"),
        )

        assertEquals(listOf("b", "a"), aggregateRevenue(rows).map { it.placementId })
    }

    @Test
    fun mixedCurrenciesAreNotSummedTogether() {
        // Adding USD micros to EUR micros produces a meaningless number.
        val rows = listOf(
            PaidEventRow("a", valueMicros = 100, currency = "USD"),
            PaidEventRow("a", valueMicros = 100, currency = "EUR"),
        )

        assertEquals(2, aggregateRevenue(rows).size)
    }
}
