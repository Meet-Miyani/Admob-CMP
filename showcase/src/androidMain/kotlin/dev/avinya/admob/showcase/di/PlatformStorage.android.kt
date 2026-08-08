package dev.avinya.admob.showcase.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase

private class AndroidPlatformStorage(private val context: Context) : PlatformStorage {

    override fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase> =
        Room.databaseBuilder<ShowcaseDatabase>(
            context = context,
            name = context.getDatabasePath(SHOWCASE_DATABASE_FILE).absolutePath,
        )

    override fun dataStorePath(): String =
        context.filesDir.resolve(SHOWCASE_PREFERENCES_FILE).absolutePath
}

@Composable
actual fun rememberPlatformStorage(): PlatformStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidPlatformStorage(context) }
}
