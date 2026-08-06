package dev.avinya.admob.showcase.domain.feed

/** One row in the feed: either real content or a native ad slot. */
sealed interface FeedItem {

    /** Stable identity for Compose's `key` and for Paging's diffing. */
    val key: String

    data class Article(
        val id: String,
        val title: String,
        val author: String,
        val section: String,
        val readTimeMin: Int,
        val isPremium: Boolean,
        val feedOrdinal: Int,
    ) : FeedItem {
        override val key: String get() = "article_$id"
    }

    /**
     * A native ad slot.
     *
     * [slotKey] comes from [FeedAdInserter.slotKeyAfter] and is derived from
     * the article this slot follows — never from a list position. It is passed
     * to `NativeAdView` as `itemKey`, which is what binds a pooled ad to this
     * row and keeps it bound as the list changes around it.
     */
    data class NativeAdSlot(val slotKey: String) : FeedItem {
        override val key: String get() = slotKey
    }
}
