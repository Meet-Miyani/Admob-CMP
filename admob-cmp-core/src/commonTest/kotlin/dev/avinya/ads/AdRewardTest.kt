package dev.avinya.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdRewardTest {

    @Test
    fun fractionalRewardsSurviveExactly() {
        // The bug: 0.5 was rounded to 1, granting double. Micros are exact.
        assertEquals(500_000L, AdReward(amountMicros = 500_000L, type = "coins").amountMicros)
        assertEquals(2_500_000L, AdReward(amountMicros = 2_500_000L, type = "coins").amountMicros)
    }

    @Test
    fun wholeAmountsReadNaturally() {
        assertEquals(3, AdReward(amountMicros = 3_000_000L, type = "coins").wholeAmountOrNull())
    }

    @Test
    fun fractionalAmountsHaveNoWholeRepresentation() {
        assertEquals(null, AdReward(amountMicros = 500_000L, type = "coins").wholeAmountOrNull())
    }

    @Test
    fun wholeAmountReturnsIntBoundaries() {
        assertEquals(
            Int.MAX_VALUE,
            AdReward(Int.MAX_VALUE.toLong() * 1_000_000L, "coins").wholeAmountOrNull()
        )
        assertEquals(
            Int.MIN_VALUE,
            AdReward(Int.MIN_VALUE.toLong() * 1_000_000L, "coins").wholeAmountOrNull()
        )
    }

    @Test
    fun wholeAmountRejectsIntOverflow() {
        assertNull(
            AdReward((Int.MAX_VALUE.toLong() + 1L) * 1_000_000L, "coins")
                .wholeAmountOrNull()
        )
        assertNull(
            AdReward((Int.MIN_VALUE.toLong() - 1L) * 1_000_000L, "coins")
                .wholeAmountOrNull()
        )
    }
}
