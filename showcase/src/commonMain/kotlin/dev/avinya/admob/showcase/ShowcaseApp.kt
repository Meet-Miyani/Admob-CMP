package dev.avinya.admob.showcase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.di.rememberAppGraph
import dev.avinya.admob.showcase.nav.ShowcaseNavHost
import dev.avinya.admob.showcase.nav.ShowcaseNavKey
import dev.avinya.admob.showcase.ui.theme.ShowcaseTheme
import dev.avinya.admob.showcase.ui.theme.ThemeMode

/**
 * Root of the showcase app and the only public composable `:showcase` exposes.
 *
 * `shared` calls this from its `PlatformAdDemo` actual on Android and iOS;
 * desktop and web keep rendering `UnsupportedAdPlatform()`.
 */
@Composable
fun ShowcaseApp() {
    val graph = rememberAppGraph()
    val themeMode by graph.settings.themeMode.collectAsState(initial = ThemeMode.Default)

    LaunchedEffect(graph) { graph.articles.seedIfEmpty() }

    CompositionLocalProvider(LocalAppGraph provides graph) {
        ShowcaseTheme(themeMode = themeMode) {
            val backStack = remember { mutableStateListOf<ShowcaseNavKey>(ShowcaseNavKey.Feed) }
            ShowcaseNavHost(backStack = backStack)
        }
    }
}
