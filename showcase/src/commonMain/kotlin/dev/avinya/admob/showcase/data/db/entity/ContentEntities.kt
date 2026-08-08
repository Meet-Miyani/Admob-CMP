package dev.avinya.admob.showcase.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** An article. Body paragraphs are separated by a blank line. */
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val body: String,
    val section: String,
    val publishedAt: Long,
    val readTimeMin: Int,
    val isPremium: Boolean,
    val unlockCostCoins: Int,
    /**
     * Position in the feed's canonical ordering, assigned by the seed.
     *
     * Exists because Paging's `insertSeparators` provides adjacent items but
     * no index, and a stateful counter would be wrong — separators are
     * recomputed lazily. Ad placement therefore reads stable data on the
     * article rather than a list position.
     */
    val feedOrdinal: Int,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BookmarkEntity(
    @PrimaryKey val articleId: String,
    val createdAt: Long,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey val articleId: String,
    val scrollFraction: Float,
    val updatedAt: Long,
)

/** How a premium article came to be unlocked. Surfaced in the Library. */
enum class UnlockSource { REWARDED, COINS }

@Entity(
    tableName = "unlocks",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("unlockedAt")],
)
data class UnlockEntity(
    @PrimaryKey val articleId: String,
    val unlockedAt: Long,
    val source: UnlockSource,
)
