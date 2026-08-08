@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.avinya.admob.showcase.feature.feed

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dev.avinya.ads.AdManager
import dev.avinya.ads.AdManagerStatus
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.data.repo.ArticleRepository
import dev.avinya.admob.showcase.domain.feed.FeedAdInserter
import dev.avinya.admob.showcase.domain.feed.FeedItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class FeedViewModel(
    articles: ArticleRepository,
    settings: SettingsRepository,
    adManager: AdManager,
) : MviViewModel<FeedState, FeedIntent, FeedEffect>(FeedState()) {

    /**
     * The feed, with ad slots inserted when ads are enabled and the SDK is ready.
     *
     * Uses `flatMapLatest` so that when `showAds` transitions (e.g. upon SDK
     * initialization completing), a fresh `Pager` flow is collected rather than
     * reusing and re-collecting an existing `PagingData`'s single-use `pageEventFlow`.
     *
     * `cachedIn(viewModelScope)` is load-bearing: without it every
     * recomposition re-collects the flow and refetches pages, which also
     * destroys and re-acquires every pooled native ad on screen.
     */
    val feed: Flow<PagingData<FeedItem>> = combine(
        settings.adsMasterSwitch,
        adManager.status,
    ) { adsEnabled, status ->
        adsEnabled && status == AdManagerStatus.Ready
    }.distinctUntilChanged().flatMapLatest { showAds ->
        articles.feedPager().map { paging ->
            val items = paging.map<FeedItem.Article, FeedItem> { it }
            if (showAds) items.withAdSlots() else items
        }
    }.cachedIn(viewModelScope)

    init {
        combine(settings.adsMasterSwitch, adManager.status) { adsEnabled, status ->
            FeedState(adsEnabled = adsEnabled, sdkReady = status == AdManagerStatus.Ready)
        }.distinctUntilChanged().onEach { next -> updateState { next } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: FeedIntent) {
        when (intent) {
            is FeedIntent.OpenArticle -> emitEffect(FeedEffect.NavigateToArticle(intent.articleId))
        }
    }
}

/**
 * Inserts an ad slot after every sixth article.
 *
 * `insertSeparators` supplies adjacent items and no index, which is exactly
 * why placement is decided from the article's own `feedOrdinal` rather than a
 * position — see `FeedAdInserter`.
 */
private fun PagingData<FeedItem>.withAdSlots(): PagingData<FeedItem> =
    insertSeparators { before, _ ->
        val article = before as? FeedItem.Article ?: return@insertSeparators null
        if (FeedAdInserter.shouldInsertAfter(article.feedOrdinal)) {
            FeedItem.NativeAdSlot(FeedAdInserter.slotKeyAfter(article.id))
        } else {
            null
        }
    }
