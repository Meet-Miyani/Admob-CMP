package dev.avinya.admob.showcase.nav

import androidx.navigation3.runtime.NavKey

/**
 * Every destination in the showcase.
 *
 * Keys are plain data, not `@Serializable`: `rememberNavBackStack` would
 * require kotlinx-serialization, which is not an approved dependency. The
 * consequence is that the backstack does not survive process death — raised
 * with the owner as an open decision.
 */
sealed interface ShowcaseNavKey : NavKey {
    val label: String

    data object Feed : ShowcaseNavKey {
        override val label: String = "Feed"
    }

    data object Library : ShowcaseNavKey {
        override val label: String = "Library"
    }

    data object Store : ShowcaseNavKey {
        override val label: String = "Store"
    }

    data object Settings : ShowcaseNavKey {
        override val label: String = "Settings"
    }

    data class ArticleDetail(val articleId: String) : ShowcaseNavKey {
        override val label: String = "Article"
    }
}

/** The bottom bar's destinations, in order. */
val TOP_LEVEL_KEYS: List<ShowcaseNavKey> = listOf(
    ShowcaseNavKey.Feed,
    ShowcaseNavKey.Library,
    ShowcaseNavKey.Store,
    ShowcaseNavKey.Settings,
)
