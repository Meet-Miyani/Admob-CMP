package dev.avinya.admob.showcase.domain.feed

import dev.avinya.ads.AdPlacement
import dev.avinya.ads.nativead.NativeAdSlot as SessionSlot

/** One row in the feed: either real content or a native ad slot. */
sealed interface FeedItem {

    /** Stable identity for Compose's `key` and for Paging's diffing. */
    val key: String

    /**
     * A paged article as it appears in the feed.
     *
     * [feedOrdinal] is the article's position in the **un-inserted** feed — its
     * index before [FeedAdInserter] interleaves ad slots. The inserter reads
     * this to decide where to put an ad; nothing in the inserter or the screen
     * should compute it from the slot's list position, which is a different
     * number and shifts on every page.
     */
    data class Article(
        val id: String,
        val title: String,
        val author: String,
        val section: String,
        val readTimeMin: Int,
        val isPremium: Boolean,
        val feedOrdinal: Int,
        val snippet: String = "",
        val publishedAt: Long = 0L,
    ) : FeedItem {
        override val key: String get() = "article_$id"
    }

    /**
     * A native ad slot.
     *
     * [slotKey] comes from [FeedAdInserter.slotKeyAfter] and is derived from
     * the article this slot follows — never from a list position. The screen
     * maps it to a session slot with its static placement when reporting the
     * measured viewport.
     */
    data class NativeAdSlot(val slotKey: String) : FeedItem {
        override val key: String get() = slotKey

        fun sessionSlot(placement: AdPlacement): SessionSlot = SessionSlot(slotKey, placement)
    }
}
