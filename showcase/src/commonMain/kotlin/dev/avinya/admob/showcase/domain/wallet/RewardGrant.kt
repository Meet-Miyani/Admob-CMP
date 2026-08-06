package dev.avinya.admob.showcase.domain.wallet

import dev.avinya.ads.AdReward

/**
 * Identity for one reward grant.
 *
 * A reward callback is not guaranteed to fire exactly once, so every credit
 * carries a key a replay reproduces — letting the wallet return
 * [CreditResult.AlreadyGranted] instead of paying twice.
 */
fun rewardGrantKey(placementId: String, sessionId: String, sequence: Int): String =
    "$placementId:$sessionId:$sequence"

/**
 * Coins earned from [reward], or null when nothing should be credited.
 *
 * Null means **do not credit**: either no reward callback fired (the user
 * dismissed early) or the reward was fractional and cannot map to whole
 * coins. Rounding a fractional reward would silently over- or under-pay.
 */
fun coinsFor(reward: AdReward?): Int? = reward?.wholeAmountOrNull()
