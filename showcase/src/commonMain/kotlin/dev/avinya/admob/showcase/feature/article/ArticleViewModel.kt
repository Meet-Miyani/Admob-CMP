package dev.avinya.admob.showcase.feature.article

import androidx.lifecycle.viewModelScope
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.repo.ArticleRepository
import kotlinx.coroutines.launch

/**
 * Loads a single article, observes its bookmark state, and persists the
 * reader's scroll fraction as they read.
 *
 * Task 1 keeps `Close` trivial — it just emits [ArticleEffect.NavigateBack].
 * Task 4 will consult the AdPolicy in `onIntent(ArticleIntent.Close)` and may
 * swap that for a `ShowInterstitial` / `AdSuppressed` effect.
 */
class ArticleViewModel(
    private val articles: ArticleRepository,
    private val articleId: String,
) : MviViewModel<ArticleState, ArticleIntent, ArticleEffect>(ArticleState()) {

    init {
        loadArticle()
        observeBookmark()
        loadInitialProgress()
    }

    private fun loadArticle() {
        viewModelScope.launch {
            val entity = articles.article(articleId)
            updateState { copy(article = entity, loading = false) }
        }
    }

    private fun observeBookmark() {
        viewModelScope.launch {
            articles.isBookmarked(articleId).collect { bookmarked ->
                updateState { copy(bookmarked = bookmarked) }
            }
        }
    }

    private fun loadInitialProgress() {
        viewModelScope.launch {
            val fraction = articles.progress(articleId)
            updateState { copy(initialProgress = fraction) }
        }
    }

    override fun onIntent(intent: ArticleIntent) {
        when (intent) {
            ArticleIntent.ToggleBookmark -> viewModelScope.launch {
                // Read the current value from state so the optimistic write
                // does not race with the bookmark flow's next emission.
                articles.setBookmarked(articleId, !state.value.bookmarked)
            }
            ArticleIntent.Close -> emitEffect(ArticleEffect.NavigateBack)
            is ArticleIntent.ProgressUpdated -> viewModelScope.launch {
                articles.setProgress(articleId, intent.fraction)
            }
        }
    }
}
