package dev.avinya.admob.showcase.feature.feed

data class FeedState(
    val adsEnabled: Boolean = true,
    val sdkReady: Boolean = false,
)

sealed interface FeedIntent {
    data class OpenArticle(val articleId: String) : FeedIntent
}

sealed interface FeedEffect {
    data class NavigateToArticle(val articleId: String) : FeedEffect
}
