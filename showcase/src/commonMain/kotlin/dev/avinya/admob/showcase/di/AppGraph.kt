package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.core.time.SystemClock
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.data.repo.AdStateRepository
import dev.avinya.admob.showcase.data.repo.ArticleRepository
import dev.avinya.admob.showcase.data.repo.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/**
 * Manual dependency graph, constructed once per process.
 *
 * Hand-rolled rather than Koin or Hilt: the graph is small, and a demo whose
 * point is to be read benefits from wiring you can follow by eye.
 */
class AppGraph(storage: PlatformStorage) {

    val clock: Clock = SystemClock

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: ShowcaseDatabase = storage.databaseBuilder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // Destructive is correct *here* and only here: every row in the
        // database at this point is regenerable seed content, so a real
        // Migration would be ceremony with no user-visible benefit.
        //
        // This stops being acceptable from Phase 5 onward, when the wallet
        // holds coins the user earned by watching ads. Any schema change after
        // that needs a real Migration.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    private val preferences: DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath { storage.dataStorePath().toPath() }

    val settings: SettingsRepository = SettingsRepository(preferences)

    val articles: ArticleRepository = ArticleRepository(database.articleDao(), clock)

    val wallet: WalletRepository = WalletRepository(database.walletDao(), clock)

    // Captured once at graph construction. Cold-start grace means cold start,
    // not cold launch — the value resets every process, which is the right
    // semantic for the interstitial's first 30s.
    private val coldStartAt: Long = clock.nowMillis()

    val adState: AdStateRepository = AdStateRepository(preferences, clock, coldStartAt)
}

/**
 * Set by [dev.avinya.admob.showcase.ShowcaseApp]. Reading it outside that
 * subtree is a programming error, so there is no default.
 */
val LocalAppGraph: ProvidableCompositionLocal<AppGraph> = compositionLocalOf {
    error("LocalAppGraph accessed outside ShowcaseApp")
}

@Composable
internal fun rememberAppGraph(): AppGraph {
    val storage = rememberPlatformStorage()
    return remember(storage) { AppGraph(storage) }
}
