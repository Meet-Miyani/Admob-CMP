package dev.avinya.admob.showcase.feature.article

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.ui.BannerAdView
import dev.avinya.ads.ui.NativeAdView
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import dev.avinya.admob.showcase.domain.ad.SuppressionReason
import dev.avinya.admob.showcase.domain.article.inlineAdSlotIndex
import dev.avinya.admob.showcase.domain.article.splitParagraphs
import dev.avinya.admob.showcase.ui.ad.AdEffectHandler
import dev.avinya.admob.showcase.ui.ad.inlineNativeAdLayout
import dev.avinya.admob.showcase.ui.inspector.InspectorSheet
import dev.avinya.admob.showcase.ui.inspector.LocalInspectorPlacements
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

private const val PROGRESS_DEBOUNCE_MS: Long = 500

@Composable
fun ArticleScreen(articleId: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val adManager = LocalAdManager.current
    val viewModel: ArticleViewModel = viewModel {
        ArticleViewModel(
            articles = graph.articles,
            settings = graph.settings,
            adState = graph.adState,
            telemetry = graph.telemetry,
            adManager = adManager,
            clock = graph.clock,
            articleId = articleId,
        )
    }
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    val inspectorEnabled by graph.settings.inspectorEnabled.collectAsState(initial = true)
    var showInspector by remember { mutableStateOf(false) }
    val placements = remember {
        listOf(
            ShowcasePlacements.articleNative,
            ShowcasePlacements.articleBanner,
            ShowcasePlacements.articleInterstitial,
        )
    }

    AdEffectHandler(
        effects = viewModel.effects,
        onSuppressed = { reason: SuppressionReason ->
            println("Article ad suppressed: $reason")
        },
        onNavigateBack = onBack,
        onShown = { viewModel.onInterstitialShown() },
    )

    CompositionLocalProvider(LocalInspectorPlacements provides placements) {
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
                inspectorEnabled = inspectorEnabled,
                onOpenInspector = { showInspector = true },
            )
            state.loading -> CenteredMessage("Loading…")
            else -> CenteredMessage("Article not found")
        }
    }

    if (showInspector) {
        InspectorSheet(placements = placements, onDismiss = { showInspector = false })
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
    inspectorEnabled: Boolean,
    onOpenInspector: () -> Unit,
) {
    val paragraphs = remember(article.body) { splitParagraphs(article.body) }
    val adIndex = remember(paragraphs.size) { inlineAdSlotIndex(paragraphs.size) }
    val showInlineAd = adsEnabled && sdkReady
    val showAdRow = adIndex != null && showInlineAd
    val effectiveAdIndex = adIndex?.takeIf { showInlineAd } ?: Int.MAX_VALUE

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

    LaunchedEffect(article.id) {
        if (initialProgress > 0f && paragraphs.isNotEmpty()) {
            val target = (initialProgress * (paragraphs.size - 1))
                .toInt()
                .coerceIn(0, paragraphs.lastIndex)
            val listTarget = target + (if (target >= effectiveAdIndex) 1 else 0)
            listState.scrollToItem(listTarget)
        }
    }

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
            inspectorEnabled = inspectorEnabled,
            onOpenInspector = onOpenInspector,
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            NativeAdView(
                                placement = ShowcasePlacements.articleNative,
                                itemKey = "article_native_${article.id}",
                                layout = inlineNativeAdLayout,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            )
                        }
                    }
                    showAdRow && index > adIndex -> {
                        ParagraphCard(text = paragraphs[index - 1])
                    }
                    else -> {
                        ParagraphCard(text = paragraphs[index])
                    }
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
private fun ParagraphCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    inspectorEnabled: Boolean,
    onOpenInspector: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (inspectorEnabled) {
                    IconButton(onClick = onOpenInspector) {
                        Icon(
                            imageVector = Icons.Rounded.Analytics,
                            contentDescription = "Inspect Telemetry",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark article",
                        tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = section.uppercase(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (isPremium) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.tertiary,
                ) {
                    Text(
                        text = "PREMIUM",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = "Author",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = "Read time",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$readTimeMin min read",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}

