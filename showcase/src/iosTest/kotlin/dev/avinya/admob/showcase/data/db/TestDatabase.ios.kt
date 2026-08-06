package dev.avinya.admob.showcase.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase> =
    Room.inMemoryDatabaseBuilder<ShowcaseDatabase>(
        factory = { ShowcaseDatabaseConstructor.initialize() },
    ).setDriver(BundledSQLiteDriver())
