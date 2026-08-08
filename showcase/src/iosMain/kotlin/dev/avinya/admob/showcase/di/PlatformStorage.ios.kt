package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private class IosPlatformStorage : PlatformStorage {

    override fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase> =
        Room.databaseBuilder<ShowcaseDatabase>(name = documentsPath(SHOWCASE_DATABASE_FILE))

    override fun dataStorePath(): String = documentsPath(SHOWCASE_PREFERENCES_FILE)

    @OptIn(ExperimentalForeignApi::class)
    private fun documentsPath(fileName: String): String {
        val documents: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(documents?.path) { "iOS documents directory unavailable" } + "/" + fileName
    }
}

@Composable
actual fun rememberPlatformStorage(): PlatformStorage = remember { IosPlatformStorage() }
