package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.rememberAdManager

/**
 * Publishes the process-wide [dev.avinya.ads.AdManager] into the composition.
 *
 * Uses the SDK's own `LocalAdManager` rather than adding one to [AppGraph]:
 * `rememberAdManager()` already returns a process-wide singleton, so putting
 * it in the hand-rolled graph would duplicate ownership and imply the
 * showcase controls a lifecycle it does not.
 */
@Composable
fun ProvideAdManager(content: @Composable () -> Unit) {
    val adManager = rememberAdManager()
    CompositionLocalProvider(LocalAdManager provides adManager, content = content)
}
