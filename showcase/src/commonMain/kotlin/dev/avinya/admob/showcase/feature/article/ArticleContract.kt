package dev.avinya.admob.showcase.feature.article

import dev.avinya.admob.showcase.data.db.entity.ArticleEntity

/**
 * Immutable UI state for the article detail screen.
 *
 * `article == null` with [loading] == true means the load is in flight.
 * `article == null` with [loading] == false means the load completed and the
 * row was missing — the screen renders "Article not found" rather than
 * "Loading…" forever.
 *
 * [initialProgress] is the persisted scroll fraction, loaded once on
 * entry so the screen can restore the user's last reading position.
 */
data class ArticleState(
    val article: ArticleEntity? = null,
    val bookmarked: Boolean = false,
    val initialProgress: Float = 0f,
    val loading: Boolean = true,
)

sealed interface ArticleIntent {
    data object ToggleBookmark : ArticleIntent
    data object Close : ArticleIntent
    data class ProgressUpdated(val fraction: Float) : ArticleIntent
}

sealed interface ArticleEffect {
    /** Ask the host to pop this entry. */
    data object NavigateBack : ArticleEffect
}
