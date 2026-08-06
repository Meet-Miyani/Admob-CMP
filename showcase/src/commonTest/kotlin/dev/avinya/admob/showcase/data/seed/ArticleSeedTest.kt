package dev.avinya.admob.showcase.data.seed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleSeedTest {

    @Test
    fun producesEnoughArticlesForSixPagesOfTwenty() {
        assertTrue(
            ArticleSeed.articles().size >= 120,
            "need >= 120 articles so paging actually pages; got ${ArticleSeed.articles().size}",
        )
    }

    @Test
    fun isDeterministic() {
        assertEquals(ArticleSeed.articles(), ArticleSeed.articles())
    }

    @Test
    fun idsAreUnique() {
        val ids = ArticleSeed.articles().map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate article ids in seed")
    }

    @Test
    fun everyArticleHasAtLeastFourParagraphsSoTheInlineNativeAdHasSomewhereToSit() {
        val tooShort = ArticleSeed.articles().filter { it.body.split("\n\n").size < 4 }
        assertEquals(emptyList(), tooShort.map { it.id })
    }

    @Test
    fun someArticlesArePremiumAndAllOfThemCostCoins() {
        val premium = ArticleSeed.articles().filter { it.isPremium }
        assertTrue(premium.isNotEmpty(), "expected some premium articles")
        assertTrue(premium.all { it.unlockCostCoins > 0 }, "premium articles must cost coins")
    }

    @Test
    fun freeArticlesCostNothing() {
        assertTrue(ArticleSeed.articles().filterNot { it.isPremium }.all { it.unlockCostCoins == 0 })
    }

    @Test
    fun everyArticleCarriesItsFeedOrdinal() {
        val ordinals = ArticleSeed.articles().map { it.feedOrdinal }

        assertEquals(ordinals.indices.toList(), ordinals)
    }

    @Test
    fun ordinalOrderMatchesPublishedAtDescendingSoTheFeedAgrees() {
        val byPublished = ArticleSeed.articles().sortedByDescending { it.publishedAt }

        assertEquals(byPublished.map { it.feedOrdinal }, byPublished.indices.toList())
    }
}
