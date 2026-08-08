package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.room.RoomDatabase
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase

/**
 * The one platform-specific thing the showcase needs: where files live.
 *
 * Android needs a `Context`; iOS needs the documents directory. Resolving
 * this composably rather than plumbing a `Context` through `shared` is what
 * keeps `androidApp` and the iOS framework free of structural changes.
 */
interface PlatformStorage {
    fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase>
    fun dataStorePath(): String
}

@Composable
expect fun rememberPlatformStorage(): PlatformStorage

internal const val SHOWCASE_DATABASE_FILE = "showcase.db"
internal const val SHOWCASE_PREFERENCES_FILE = "showcase.preferences_pb"
