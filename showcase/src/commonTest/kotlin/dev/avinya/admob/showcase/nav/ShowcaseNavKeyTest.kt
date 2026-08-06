package dev.avinya.admob.showcase.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShowcaseNavKeyTest {

    @Test
    fun exposesExactlyFourTopLevelDestinations() {
        assertEquals(
            listOf(
                ShowcaseNavKey.Feed,
                ShowcaseNavKey.Library,
                ShowcaseNavKey.Store,
                ShowcaseNavKey.Settings,
            ),
            TOP_LEVEL_KEYS,
        )
    }

    @Test
    fun articleDetailIsNotATopLevelDestination() {
        assertTrue(TOP_LEVEL_KEYS.none { it is ShowcaseNavKey.ArticleDetail })
    }

    @Test
    fun articleDetailKeysCompareByArticleId() {
        assertEquals(ShowcaseNavKey.ArticleDetail("a1"), ShowcaseNavKey.ArticleDetail("a1"))
        assertTrue(ShowcaseNavKey.ArticleDetail("a1") != ShowcaseNavKey.ArticleDetail("a2"))
    }

    @Test
    fun everyTopLevelKeyHasALabel() {
        assertTrue(TOP_LEVEL_KEYS.all { it.label.isNotBlank() })
    }

    @Test
    fun onboardingIsNotATopLevelDestination() {
        assertTrue(TOP_LEVEL_KEYS.none { it == ShowcaseNavKey.Onboarding })
    }

    @Test
    fun onboardingHidesTheBottomBar() {
        assertFalse(showsBottomBar(ShowcaseNavKey.Onboarding))
        assertTrue(showsBottomBar(ShowcaseNavKey.Feed))
        assertTrue(showsBottomBar(ShowcaseNavKey.ArticleDetail("a1")))
    }
}
