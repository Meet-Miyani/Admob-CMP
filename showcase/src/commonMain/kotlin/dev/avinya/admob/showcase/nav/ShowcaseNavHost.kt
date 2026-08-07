package dev.avinya.admob.showcase.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

import dev.avinya.admob.showcase.di.LocalAppOpenSuppressor
import dev.avinya.admob.showcase.feature.onboarding.OnboardingScreen
import dev.avinya.admob.showcase.feature.feed.FeedScreen
import dev.avinya.admob.showcase.feature.article.ArticleScreen
import dev.avinya.admob.showcase.feature.settings.SettingsScreen
import dev.avinya.admob.showcase.feature.store.StoreScreen
import dev.avinya.admob.showcase.feature.library.LibraryScreen

/**
 * The app's navigation shell.
 *
 * Real Nav3 entries matter here beyond tidiness: each entry owns a
 * `ViewModelStore` that is cleared on pop, which is what makes banner and
 * native ad disposal actually get exercised as the user moves around.
 */
@Composable
fun ShowcaseNavHost(backStack: SnapshotStateList<ShowcaseNavKey>) {
    val current = backStack.lastOrNull() ?: ShowcaseNavKey.Feed
    val suppressor = LocalAppOpenSuppressor.current

    Scaffold(
        bottomBar = {
            if (showsBottomBar(current)) {
                NavigationBar {
                    TOP_LEVEL_KEYS.forEach { key ->
                        NavigationBarItem(
                            selected = current == key,
                            onClick = { switchTopLevel(backStack, key) },
                            // Text initials, not material-icons: that artifact is not on the
                            // approved dependency list. The Phase 6 plan's polish pass revisits this.
                            icon = { Text(key.label.first().toString()) },
                            label = { Text(key.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().padding(padding),
            onBack = { if (backStack.size > 1) backStack.removeLast() },
            entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<ShowcaseNavKey.Onboarding> {
                    // Hold suppression for the whole entry lifetime. onFinished
                    // clears the back stack and pushes Feed, which disposes this
                    // entry, so the suppression ends naturally without manual
                    // bookkeeping.
                    LaunchedEffect(Unit) { suppressor.enter() }
                    DisposableEffect(Unit) { onDispose { suppressor.exit() } }
                    OnboardingScreen(
                        onFinished = {
                            backStack.clear()
                            backStack.add(ShowcaseNavKey.Feed)
                        },
                    )
                }
                entry<ShowcaseNavKey.Feed> {
                    FeedScreen(
                        onArticleClick = { articleId ->
                            backStack.add(ShowcaseNavKey.ArticleDetail(articleId))
                        },
                    )
                }
                entry<ShowcaseNavKey.Library> {
                    LibraryScreen(
                        onArticleClick = { articleId ->
                            backStack.add(ShowcaseNavKey.ArticleDetail(articleId))
                        },
                    )
                }
                entry<ShowcaseNavKey.Store> { StoreScreen() }
                entry<ShowcaseNavKey.Settings> { SettingsScreen() }
                entry<ShowcaseNavKey.ArticleDetail> { key -> ArticleScreen(articleId = key.articleId, onBack = { backStack.removeLast() }) }
            },
        )
    }
}

/**
 * Switching tabs resets to a single-entry backstack rather than pushing.
 * Tabs are peers, so a back press from a tab should leave the app, not walk
 * a history of tab switches.
 */
private fun switchTopLevel(backStack: SnapshotStateList<ShowcaseNavKey>, key: ShowcaseNavKey) {
    if (backStack.size == 1 && backStack.first() == key) return
    backStack.clear()
    backStack.add(key)
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name)
    }
}
