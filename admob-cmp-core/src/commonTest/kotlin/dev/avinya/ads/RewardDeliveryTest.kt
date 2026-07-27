package dev.avinya.ads

import dev.avinya.ads.internal.RewardDelivery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardDeliveryTest {

    @Test
    fun `reward is delivered once to callback and telemetry`() {
        val callbackRewards = mutableListOf<AdReward>()
        val telemetryRewards = mutableListOf<AdReward>()
        val delivery = RewardDelivery(
            onRewardEarned = callbackRewards::add,
            emitReward = telemetryRewards::add
        )
        val reward = AdReward(1_000_000L, "coin")

        assertTrue(delivery.deliver(reward))
        assertFalse(delivery.deliver(reward))
        assertEquals(listOf(reward), callbackRewards)
        assertEquals(listOf(reward), telemetryRewards)
    }

    @Test
    fun `callback failure does not suppress telemetry`() {
        val telemetryRewards = mutableListOf<AdReward>()
        val delivery = RewardDelivery(
            onRewardEarned = { error("consumer failure") },
            emitReward = telemetryRewards::add
        )
        val reward = AdReward(2_000_000L, "coin")

        assertTrue(delivery.deliver(reward))
        assertEquals(listOf(reward), telemetryRewards)
    }

    @Test
    fun `telemetry only mode still reports reward once`() {
        val telemetryRewards = mutableListOf<AdReward>()
        val delivery = RewardDelivery(
            onRewardEarned = null,
            emitReward = telemetryRewards::add
        )

        delivery.deliver(AdReward(3_000_000L, "coin"))

        assertEquals(1, telemetryRewards.size)
    }
}
