package dev.avinya.admob.showcase.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.ui.BannerAdView
import dev.avinya.ads.ui.NativeAdView
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import dev.avinya.admob.showcase.domain.feed.FeedItem
import dev.avinya.admob.showcase.ui.ad.feedNativeAdLayout
import dev.avinya.admob.showcase.ui.inspector.InspectorEntryPoint
import dev.avinya.admob.showcase.ui.inspector.InspectorSheet
import dev.avinya.admob.showcase.ui.inspector.LocalInspectorPlacements

@Composable
fun FeedScreen(onArticleClick: (String) -> Unit) {
    val adManager = LocalAdManager.current
    val graph = LocalAppGraph.current
    val viewModel: FeedViewModel = viewModel {
        FeedViewModel(graph.articles, graph.settings, adManager)
    }
    val items = viewModel.feed.collectAsLazyPagingItems()

    val inspectorEnabled by graph.settings.inspectorEnabled.collectAsState(initial = true)
    var showInspector by remember { mutableStateOf(false) }
    val placements = remember {
        listOf(ShowcasePlacements.feedBanner, ShowcasePlacements.feedNative)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FeedEffect.NavigateToArticle -> onArticleClick(effect.articleId)
            }
        }
    }

    CompositionLocalProvider(LocalInspectorPlacements provides placements) {
        Column(modifier = Modifier.fillMaxSize()) {
            InspectorEntryPoint(
                title = "Feed",
                enabled = inspectorEnabled,
                onOpen = { showInspector = true },
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.key },
                ) { index ->
                    when (val item = items[index]) {
                        is FeedItem.Article -> ArticleCard(
                            item = item,
                            onClick = { viewModel.onIntent(FeedIntent.OpenArticle(item.id)) },
                        )
                        is FeedItem.NativeAdSlot -> NativeAdView(
                            placement = ShowcasePlacements.feedNative,
                            itemKey = item.slotKey,
                            layout = feedNativeAdLayout,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Placeholders are disabled, so null only occurs transiently
                        // while a page loads. Render nothing — never a spinner per row.
                        null -> Unit
                    }
                }
            }

            // Rendered only when it can actually fill. BannerAdView measures its
            // own container and supplies the width — do not build BannerGeometry
            // by hand here; that is only for headless controller callers.
            val state by viewModel.state.collectAsState()
            if (state.adsEnabled && state.sdkReady) {
                BannerAdView(
                    placement = ShowcasePlacements.feedBanner,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showInspector) {
        InspectorSheet(placements = placements, onDismiss = { showInspector = false })
    }
}

@Composable
private fun ArticleCard(item: FeedItem.Article, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.section.uppercase(), style = MaterialTheme.typography.labelSmall)
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(item.author)
                    append(" · ")
                    append(item.readTimeMin)
                    append(" min")
                    if (item.isPremium) append(" · Premium")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
