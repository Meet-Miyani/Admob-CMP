package dev.avinya.admob.showcase.data.db

import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaTest : BaseRoomTest() {

    private fun database(): ShowcaseDatabase =
        getInMemoryDatabaseBuilder()
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    @Test
    fun opensWithEveryTablePresent() = runTest {
        val db = database()
        try {
            // Opening lazily creates the schema; a trivial read per DAO proves
            // each table exists and matches its entity.
            assertEquals(emptyList(), db.articleDao().allIds())
            assertEquals(null, db.walletDao().current())
            assertEquals(0, db.telemetryDao().adEventCount())
        } finally {
            db.close()
        }
    }

    @Test
    fun walletIsASingleRowKeyedAtZero() = runTest {
        val db = database()
        try {
            db.walletDao().upsert(WalletEntity(id = 0, coinBalance = 120, updatedAt = 1L))
            db.walletDao().upsert(WalletEntity(id = 0, coinBalance = 200, updatedAt = 2L))

            assertEquals(200, db.walletDao().current()?.coinBalance)
        } finally {
            db.close()
        }
    }

    @Test
    fun storesAndReadsBackAnArticle() = runTest {
        val db = database()
        try {
            db.articleDao().insertAll(
                listOf(
                    ArticleEntity(
                        id = "a1",
                        title = "Structured concurrency",
                        author = "R. Elder",
                        body = "para one\n\npara two",
                        section = "Kotlin",
                        publishedAt = 1_700_000_000_000L,
                        readTimeMin = 7,
                        isPremium = false,
                        unlockCostCoins = 0,
                    ),
                )
            )

            assertEquals(listOf("a1"), db.articleDao().allIds())
        } finally {
            db.close()
        }
    }
}
