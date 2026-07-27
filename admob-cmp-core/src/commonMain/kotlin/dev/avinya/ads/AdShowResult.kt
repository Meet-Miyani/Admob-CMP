package dev.avinya.ads


/**
 * Result of a full-screen ad [show] operation.
 */
public sealed interface AdShowResult {
    /** The ad was shown and dismissed. */
    public data object Shown : AdShowResult
    /** The ad was not ready to show (not loaded or consumed). */
    public data object NotReady : AdShowResult
    /** Show failed with an [error]. */
    public data class Failed(val error: AdError) : AdShowResult
}
