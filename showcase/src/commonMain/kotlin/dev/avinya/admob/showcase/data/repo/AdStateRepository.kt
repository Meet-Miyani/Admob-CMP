package dev.avinya.admob.showcase.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import dev.avinya.admob.showcase.core.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal object AdStateKeys {
    val ArticlesRead = intPreferencesKey("articles_read")
    val LastInterstitialAt = longPreferencesKey("last_interstitial_at")
}

/**
 * Persistent state the [dev.avinya.admob.showcase.domain.ad.AdPolicy] needs.
 *
 * `articlesRead` increments every time the user closes an article; the policy
 * consults it to decide whether an interstitial may show. `lastInterstitialAt`
 * is the wall-clock millis of the most recent `Show` decision that actually
 * led to a presentation — it is **not** updated on a suppressed decision,
 * because resetting the cooldown because an ad didn't show would be an
 * obvious bug. `coldStartAt` is per-process and not persisted: cold-start
 * grace means cold start, not cold launch.
 */
class AdStateRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    val coldStartAt: Long,
) {

    val articlesRead: Flow<Int> = dataStore.data.map { it[AdStateKeys.ArticlesRead] ?: 0 }

    val lastInterstitialAt: Flow<Long?> =
        dataStore.data.map { it[AdStateKeys.LastInterstitialAt] }

    suspend fun incrementArticlesRead() {
        dataStore.edit { prefs ->
            val current = prefs[AdStateKeys.ArticlesRead] ?: 0
            prefs[AdStateKeys.ArticlesRead] = current + 1
        }
    }

    suspend fun recordInterstitialShown(at: Long = clock.nowMillis()) {
        dataStore.edit { it[AdStateKeys.LastInterstitialAt] = at }
    }
}
