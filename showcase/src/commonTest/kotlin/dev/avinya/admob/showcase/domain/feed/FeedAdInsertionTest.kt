package dev.avinya.admob.showcase.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedAdInsertionTest {

    @Test
    fun insertsAfterEverySixthArticle() {
        val positions = (0 until 18).filter { FeedAdInserter.shouldInsertAfter(it) }

        assertEquals(listOf(5, 11, 17), positions)
    }

    @Test
    fun doesNotInsertBeforeTheFirstSixArticles() {
        (0..4).forEach { assertFalse(FeedAdInserter.shouldInsertAfter(it), "unexpected slot at $it") }
    }

    @Test
    fun slotKeysDeriveFromTheArticleTheyFollow() {
        assertEquals("feed_native_after_article-005", FeedAdInserter.slotKeyAfter("article-005"))
    }

    @Test
    fun theSameArticleAlwaysYieldsTheSameSlotKey() {
        assertEquals(
            FeedAdInserter.slotKeyAfter("article-011"),
            FeedAdInserter.slotKeyAfter("article-011"),
        )
    }

    @Test
    fun aPrependDoesNotShiftExistingSlotKeys() {
        // THE case index-derived keys get wrong. article-011 sits at list
        // position 11 before a prepend and 14 after it; its slot key must not
        // change, or NativeAdView releases the pooled ad bound to that row and
        // acquires another for no reason — wasted inventory and visible flicker.
        val beforePrepend = FeedAdInserter.slotKeyAfter("article-011")
        val threeNewArticlesArrive = listOf("article-a", "article-b", "article-c")

        assertEquals(beforePrepend, FeedAdInserter.slotKeyAfter("article-011"))
        assertTrue(threeNewArticlesArrive.none { FeedAdInserter.slotKeyAfter(it) == beforePrepend })
    }

    @Test
    fun distinctArticlesNeverCollideOnAKey() {
        val keys = (0 until 200).map { FeedAdInserter.slotKeyAfter("article-$it") }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun theIntervalIsSixSoTheSeedYieldsTwentyOneSlots() {
        // 126 seeded articles / 6 = 21 ad slots across the whole feed.
        assertEquals(6, FeedAdInserter.AD_INTERVAL)
        assertEquals(21, (0 until 126).count { FeedAdInserter.shouldInsertAfter(it) })
    }
}
