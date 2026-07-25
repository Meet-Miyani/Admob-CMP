package dev.avinya.ads


/**
 * Result of a full-screen ad [show] operation. [Rewarded] only occurs for
 * rewarded and rewarded-interstitial formats.
 */
public sealed interface AdShowResult {
    /** The ad was shown and dismissed successfully. No reward granted. */
    public data object Shown : AdShowResult
    /** The ad was not ready to show (not loaded or consumed). */
    public data object NotReady : AdShowResult
    /** The ad was shown and the user earned a [reward]. */
    public data class Rewarded(val reward: AdReward) : AdShowResult
    /** Show failed with an [error]. */
    public data class Failed(val error: AdError) : AdShowResult
}
