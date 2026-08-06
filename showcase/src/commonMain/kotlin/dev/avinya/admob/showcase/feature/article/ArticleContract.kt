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
 *
 * [adsEnabled] reflects the user-facing master switch in settings; [sdkReady]
 * confirms the AdManager has finished initializing. The inline native ad is
 * only rendered when both are true — when the user has disabled ads, or
 * consent has not yet been resolved, the slot collapses to the paragraph
 * that would have followed so reading flow does not shift.
 */
data class ArticleState(
    val article: ArticleEntity? = null,
    val bookmarked: Boolean = false,
    val initialProgress: Float = 0f,
    val loading: Boolean = true,
    val adsEnabled: Boolean = true,
    val sdkReady: Boolean = false,
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
