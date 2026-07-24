package avinya.tech.yt.ads

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
