package dev.avinya.admob.showcase.data.db

import androidx.room.RoomDatabase

expect fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase>
