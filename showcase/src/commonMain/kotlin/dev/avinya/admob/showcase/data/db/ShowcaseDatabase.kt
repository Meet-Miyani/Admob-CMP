package dev.avinya.admob.showcase.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import dev.avinya.admob.showcase.data.db.dao.SpikeDao
import dev.avinya.admob.showcase.data.db.entity.SpikeEntity

@Database(
    entities = [SpikeEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ShowcaseDatabaseConstructor::class)
internal abstract class ShowcaseDatabase : RoomDatabase() {
    abstract fun spikeDao(): SpikeDao
}

/**
 * KMP databases cannot be instantiated reflectively, so Room's KSP processor
 * generates the `actual` for this `expect` object per target. There is
 * deliberately no hand-written actual — writing one is an error.
 */
@Suppress("KotlinNoActualForExpect")
internal expect object ShowcaseDatabaseConstructor : RoomDatabaseConstructor<ShowcaseDatabase> {
    override fun initialize(): ShowcaseDatabase
}
