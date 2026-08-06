package dev.avinya.admob.showcase.domain.feed

/**
 * Decides where native ad slots go in the feed, and what they are keyed by.
 *
 * Pure on purpose. Slot placement and key derivation are the two things a
 * feed integration most often gets wrong, and both are decidable from values
 * alone — no Paging, no Compose, no SDK.
 */
object FeedAdInserter {

    /** One ad per six articles: frequent enough to demonstrate, sparse enough to be plausible. */
    const val AD_INTERVAL: Int = 6

    /** True when an ad slot belongs immediately after the article at [ordinal]. */
    fun shouldInsertAfter(ordinal: Int): Boolean =
        ordinal >= AD_INTERVAL - 1 && (ordinal + 1) % AD_INTERVAL == 0

    /**
     * The `itemKey` for the slot following [articleId].
     *
     * Derived from the article's identity, **never** from its position.
     * Positions shift on prepend and refresh; a changed `itemKey` makes
     * `NativeAdView` release its pooled ad and acquire another, wasting
     * inventory and making ads visibly flicker during scrolling.
     */
    fun slotKeyAfter(articleId: String): String = "feed_native_after_$articleId"
}
