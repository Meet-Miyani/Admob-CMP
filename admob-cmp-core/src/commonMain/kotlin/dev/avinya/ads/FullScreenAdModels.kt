package dev.avinya.ads


/**
 * Display options for full-screen ads (interstitial, rewarded, app-open).
 *
 * @param immersiveMode **Android only.** Enable immersive mode for the ad;
 *   ignored on iOS.
 * @param serverSideVerification Server-side verification options for
 *   rewarded / rewarded-interstitial formats.
 */
public data class FullScreenAdOptions(
    val immersiveMode: Boolean = false,
    val serverSideVerification: ServerSideVerificationOptions? = null
)

/**
 * Server-side verification options for rewarded and rewarded-interstitial
 * ads. Provides [userId] and [customData] to the reward server callback
 * for reward validation.
 */
public data class ServerSideVerificationOptions(
    /** User identifier sent to the reward server. */
    val userId: String? = null,
    /** Custom data sent to the reward server. */
    val customData: String? = null
)

/**
 * Reward granted to the user after watching a rewarded ad.
 *
 * @property amountMicros the reward amount in millionths. Mediated rewards can be
 *   fractional (iOS GMA exposes `NSDecimalNumber`); the previous `Int` rounded `0.5` to
 *   `1` and granted double. Micros are exact and match how [AdValue.valueMicros] already
 *   models money.
 * @property type Reward type (e.g., "coins", "lives").
 */
public data class AdReward(val amountMicros: Long, val type: String) {
    /** The amount as a whole number, or null if it is fractional. */
    public fun wholeAmountOrNull(): Int? =
        if (amountMicros % 1_000_000L == 0L) (amountMicros / 1_000_000L).toInt() else null
}
