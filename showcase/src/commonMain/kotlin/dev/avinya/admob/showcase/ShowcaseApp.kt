package dev.avinya.admob.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.di.rememberAppGraph
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Showcase app — foundation")
            }
        }
    }
}
