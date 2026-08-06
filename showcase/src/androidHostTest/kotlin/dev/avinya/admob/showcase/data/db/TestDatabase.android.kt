package dev.avinya.admob.showcase.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider

actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase> =
    Room.inMemoryDatabaseBuilder<ShowcaseDatabase>(
        context = ApplicationProvider.getApplicationContext(),
        factory = { ShowcaseDatabaseConstructor.initialize() },
    )
