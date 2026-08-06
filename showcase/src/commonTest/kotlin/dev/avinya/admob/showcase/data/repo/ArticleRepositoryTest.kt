package dev.avinya.admob.showcase.data.repo

import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.db.BaseRoomTest
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import dev.avinya.admob.showcase.data.db.getInMemoryDatabaseBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ArticleTestClock(var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
}

class ArticleRepositoryTest : BaseRoomTest() {

    private fun database(): ShowcaseDatabase =
        getInMemoryDatabaseBuilder()
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    @Test
    fun seedIfEmptySeedsDatabaseOnlyOnce() = runTest {
        val db = database()
        try {
            val repo = ArticleRepository(db.articleDao(), ArticleTestClock())

            assertEquals(0, db.articleDao().count())
            repo.seedIfEmpty()
            assertEquals(126, db.articleDao().count())

            // Second seed call is a no-op
            repo.seedIfEmpty()
            assertEquals(126, db.articleDao().count())
        } finally {
            db.close()
        }
    }

    @Test
    fun fetchesSingleArticleByIdAndPages() = runTest {
        val db = database()
        try {
            val repo = ArticleRepository(db.articleDao(), ArticleTestClock())
            repo.seedIfEmpty()

            val article = repo.article("article-000")
            assertNotNull(article)
            assertEquals("article-000", article.id)

            val page1 = repo.page(limit = 20, offset = 0)
            assertEquals(20, page1.size)

            val nonexistent = repo.article("nonexistent")
            assertNull(nonexistent)
        } finally {
            db.close()
        }
    }

    @Test
    fun tracksReadingProgress() = runTest {
        val db = database()
        try {
            val repo = ArticleRepository(db.articleDao(), ArticleTestClock())
            repo.seedIfEmpty()

            assertEquals(0f, repo.progress("article-000"))

            repo.setProgress("article-000", 0.75f)
            assertEquals(0.75f, repo.progress("article-000"))

            // Fraction is coerced in 0..1 range
            repo.setProgress("article-000", 1.5f)
            assertEquals(1.0f, repo.progress("article-000"))
        } finally {
            db.close()
        }
    }

    @Test
    fun bookmarkingFlow() = runTest {
        val db = database()
        try {
            val repo = ArticleRepository(db.articleDao(), ArticleTestClock())
            repo.seedIfEmpty()

            assertFalse(repo.isBookmarked("article-000").first())

            repo.setBookmarked("article-000", true)
            assertTrue(repo.isBookmarked("article-000").first())

            repo.setBookmarked("article-000", false)
            assertFalse(repo.isBookmarked("article-000").first())
        } finally {
            db.close()
        }
    }

    @Test
    fun isUnlockedFlow() = runTest {
        val db = database()
        try {
            val repo = ArticleRepository(db.articleDao(), ArticleTestClock())
            repo.seedIfEmpty()

            assertFalse(repo.isUnlocked("article-006").first())

            db.articleDao().addUnlock(dev.avinya.admob.showcase.data.db.entity.UnlockEntity(articleId = "article-006", unlockedAt = 1_000L, source = dev.avinya.admob.showcase.data.db.entity.UnlockSource.COINS))
            assertTrue(repo.isUnlocked("article-006").first())
        } finally {
            db.close()
        }
    }
}
