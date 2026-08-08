package dev.avinya.admob.showcase.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import dev.avinya.admob.showcase.data.db.dao.ArticleDao
import dev.avinya.admob.showcase.data.db.dao.TelemetryDao
import dev.avinya.admob.showcase.data.db.dao.WalletDao
import dev.avinya.admob.showcase.data.db.entity.AdEventEntity
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.BookmarkEntity
import dev.avinya.admob.showcase.data.db.entity.PaidEventEntity
import dev.avinya.admob.showcase.data.db.entity.PolicyDecisionEntity
import dev.avinya.admob.showcase.data.db.entity.ReadingProgressEntity
import dev.avinya.admob.showcase.data.db.entity.RewardGrantEntity
import dev.avinya.admob.showcase.data.db.entity.UnlockEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity

@Database(
    entities = [
        ArticleEntity::class,
        BookmarkEntity::class,
        ReadingProgressEntity::class,
        UnlockEntity::class,
        WalletEntity::class,
        RewardGrantEntity::class,
        AdEventEntity::class,
        PolicyDecisionEntity::class,
        PaidEventEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(ShowcaseDatabaseConstructor::class)
abstract class ShowcaseDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun walletDao(): WalletDao
    abstract fun telemetryDao(): TelemetryDao
}

/**
 * KMP databases cannot be instantiated reflectively, so Room's KSP processor
 * generates the `actual` per target. Do not hand-write one.
 */
@Suppress("KotlinNoActualForExpect")
expect object ShowcaseDatabaseConstructor : RoomDatabaseConstructor<ShowcaseDatabase> {
    override fun initialize(): ShowcaseDatabase
}
