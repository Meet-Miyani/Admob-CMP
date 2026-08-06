package dev.avinya.admob.showcase.feature.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.ui.BannerAdView
import dev.avinya.ads.ui.NativeAdView
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import dev.avinya.admob.showcase.domain.article.inlineAdSlotIndex
import dev.avinya.admob.showcase.domain.article.splitParagraphs
import dev.avinya.admob.showcase.ui.ad.inlineNativeAdLayout
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

private const val PROGRESS_DEBOUNCE_MS: Long = 500

@Composable
fun ArticleScreen(articleId: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val adManager = LocalAdManager.current
    val viewModel: ArticleViewModel = viewModel {
        ArticleViewModel(graph.articles, graph.settings, adManager, articleId)
    }
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ArticleEffect.NavigateBack -> onBack()
            }
        }
    }

    when {
        state.article != null -> ArticleBody(
            article = state.article!!,
            bookmarked = state.bookmarked,
            initialProgress = state.initialProgress,
            adsEnabled = state.adsEnabled,
            sdkReady = state.sdkReady,
            listState = listState,
            onBack = { viewModel.onIntent(ArticleIntent.Close) },
            onToggleBookmark = { viewModel.onIntent(ArticleIntent.ToggleBookmark) },
            onProgress = { viewModel.onIntent(ArticleIntent.ProgressUpdated(it)) },
        )
        state.loading -> CenteredMessage("Loading…")
        else -> CenteredMessage("Article not found")
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ArticleBody(
    article: ArticleEntity,
    bookmarked: Boolean,
    initialProgress: Float,
    adsEnabled: Boolean,
    sdkReady: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onProgress: (Float) -> Unit,
) {
    val paragraphs = remember(article.body) { splitParagraphs(article.body) }
    val adIndex = remember(paragraphs.size) { inlineAdSlotIndex(paragraphs.size) }
    val showInlineAd = adsEnabled && sdkReady
    // The ad row is only in the list when both the article carries a slot
    // AND the gate is on. When the gate is off, the row is dropped entirely
    // (per the plan: an ad failure is never a user-facing error — no
    // zero-height placeholder, no displaced paragraph, no shift in flow).
    val showAdRow = adIndex != null && showInlineAd
    // Use a sentinel past the end of any LazyColumn row so the paragraph↔row
    // shift below becomes a no-op when no ad row is in the list.
    val effectiveAdIndex = adIndex?.takeIf { showInlineAd } ?: Int.MAX_VALUE

    // Fraction as a derived state of the LazyListState. We approximate the
    // visible-row offset by index alone — coarse but stable across re-layouts,
    // and the persisted value is only ever used to re-scroll on re-entry.
    val fraction by remember(listState, paragraphs.size) {
        derivedStateOf {
            val total = paragraphs.size
            if (total <= 1) 0f
            else {
                val row = listState.firstVisibleItemIndex
                val paragraph = row - (if (row > effectiveAdIndex) 1 else 0)
                (paragraph.toFloat() / (total - 1)).coerceIn(0f, 1f)
            }
        }
    }

    // Restore the saved reading position once the article and the lazy list
    // are both real. Doing it before either is ready is a no-op or a crash.
    LaunchedEffect(article.id) {
        if (initialProgress > 0f && paragraphs.isNotEmpty()) {
            val target = (initialProgress * (paragraphs.size - 1))
                .toInt()
                .coerceIn(0, paragraphs.lastIndex)
            // The ad row shifts every LazyColumn index at and after it by one,
            // so the paragraph-based target needs the matching offset.
            val listTarget = target + (if (target >= effectiveAdIndex) 1 else 0)
            listState.scrollToItem(listTarget)
        }
    }

    // Debounce scroll writes. Without this, every fling updates Room on every
    // frame, which is the exact behaviour `setProgress` exists to prevent.
    LaunchedEffect(listState) {
        snapshotFlow { fraction }.debounce(PROGRESS_DEBOUNCE_MS).collect(onProgress)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ArticleHeader(
            title = article.title,
            author = article.author,
            section = article.section,
            readTimeMin = article.readTimeMin,
            isPremium = article.isPremium,
            bookmarked = bookmarked,
            onBack = onBack,
            onToggleBookmark = onToggleBookmark,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val itemCount = paragraphs.size + if (showAdRow) 1 else 0
            items(
                count = itemCount,
                key = { index ->
                    if (!showAdRow) "p-$index"
                    else when {
                        index < effectiveAdIndex -> "p-$index"
                        index == effectiveAdIndex -> "ad-${article.id}"
                        else -> "p-${index - 1}"
                    }
                },
            ) { index ->
                when {
                    showAdRow && index == adIndex -> {
                        NativeAdView(
                            placement = ShowcasePlacements.articleNative,
                            itemKey = "article_native_${article.id}",
                            layout = inlineNativeAdLayout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                    showAdRow && index > adIndex -> Text(
                        text = paragraphs[index - 1],
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> Text(
                        text = paragraphs[index],
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        if (adsEnabled && sdkReady) {
            BannerAdView(
                placement = ShowcasePlacements.articleBanner,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ArticleHeader(
    title: String,
    author: String,
    section: String,
    readTimeMin: Int,
    isPremium: Boolean,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            // Unicode star — the project bans material-icons as a dependency.
            TextButton(onClick = onToggleBookmark) {
                Text(if (bookmarked) "★ Bookmarked" else "☆ Bookmark")
            }
        }
        Text(section.uppercase(), style = MaterialTheme.typography.labelSmall)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            buildString {
                append(author)
                append(" · ")
                append(readTimeMin)
                append(" min")
                if (isPremium) append(" · Premium")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
