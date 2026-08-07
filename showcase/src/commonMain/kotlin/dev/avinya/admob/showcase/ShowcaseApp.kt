package dev.avinya.admob.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.di.LocalAppOpenSuppressor
import dev.avinya.admob.showcase.di.ProvideAdManager
import dev.avinya.admob.showcase.di.rememberAppGraph
import dev.avinya.admob.showcase.nav.ShowcaseNavHost
import dev.avinya.admob.showcase.nav.ShowcaseNavKey
import dev.avinya.admob.showcase.ui.ad.AppOpenSuppressor
import dev.avinya.admob.showcase.ui.theme.ShowcaseTheme
import dev.avinya.admob.showcase.ui.theme.ThemeMode
import kotlinx.coroutines.flow.map

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
    val onboardingComplete by graph.settings.onboardingComplete
        .map<Boolean, Boolean?> { it }
        .collectAsState(initial = null)

    LaunchedEffect(graph) { graph.articles.seedIfEmpty() }

    val suppressor = remember { AppOpenSuppressor() }

    CompositionLocalProvider(
        LocalAppGraph provides graph,
        LocalAppOpenSuppressor provides suppressor,
    ) {
        ProvideAdManager {
            ShowcaseTheme(themeMode = themeMode) {
                // Hold the first screen until the preference resolves, otherwise
                // a returning user briefly sees onboarding on every cold start.
                when (onboardingComplete) {
                    null -> Box(Modifier.fillMaxSize())
                    else -> {
                        val backStack = remember {
                            mutableStateListOf<ShowcaseNavKey>(
                                if (onboardingComplete == true) {
                                    ShowcaseNavKey.Feed
                                } else {
                                    ShowcaseNavKey.Onboarding
                                },
                            )
                        }
                        ShowcaseNavHost(backStack = backStack)
                    }
                }
            }
        }
    }
}
