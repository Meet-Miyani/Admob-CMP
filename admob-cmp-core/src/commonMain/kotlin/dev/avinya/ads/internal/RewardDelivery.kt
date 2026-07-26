package dev.avinya.ads.internal

import dev.avinya.ads.AdLogger
import dev.avinya.ads.AdReward
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class RewardDelivery(
    private val onRewardEarned: ((AdReward) -> Unit)?,
    private val emitReward: (AdReward) -> Unit
) {
    private val delivered = AtomicBoolean(false)

    internal fun deliver(reward: AdReward): Boolean {
        if (!delivered.compareAndSet(expectedValue = false, newValue = true)) return false
        try {
            onRewardEarned?.invoke(reward)
        } catch (failure: Throwable) {
            AdLogger.e("Reward callback failed.", failure)
        }
        try {
            emitReward(reward)
        } catch (failure: Throwable) {
            AdLogger.e("Reward telemetry emission failed.", failure)
        }
        return true
    }
}
