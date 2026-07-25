package dev.avinya.ads.debug

import dev.avinya.ads.AdPlacement
import dev.avinya.ads.TestAdIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdDebugCatalogTest {

    private val catalog = AdDebugCatalog.Test

    private val all: List<AdPlacement> get() = listOf(
        catalog.banner, catalog.collapsibleBanner, catalog.native,
        catalog.interstitial, catalog.rewarded, catalog.rewardedInterstitial, catalog.appOpen,
    )

    @Test
    fun everyTestPlacementEnablesStrictTestMode() {
        all.forEach { assertTrue(it.strictTestMode, "${it.id} must set strictTestMode") }
    }

    @Test
    fun everyTestPlacementUsesGoogleTestAdUnits() {
        val known = setOf(
            TestAdIds.ANDROID_BANNER, TestAdIds.IOS_BANNER,
            TestAdIds.ANDROID_COLLAPSIBLE_BANNER, TestAdIds.IOS_COLLAPSIBLE_BANNER,
            TestAdIds.ANDROID_NATIVE, TestAdIds.IOS_NATIVE,
            TestAdIds.ANDROID_INTERSTITIAL, TestAdIds.IOS_INTERSTITIAL,
            TestAdIds.ANDROID_REWARDED, TestAdIds.IOS_REWARDED,
            TestAdIds.ANDROID_REWARDED_INTERSTITIAL, TestAdIds.IOS_REWARDED_INTERSTITIAL,
            TestAdIds.ANDROID_APP_OPEN, TestAdIds.IOS_APP_OPEN,
        )
        all.forEach { placement ->
            assertTrue(placement.adUnitIds.android in known, "${placement.id} android id is not a test id")
            assertTrue(placement.adUnitIds.ios in known, "${placement.id} ios id is not a test id")
        }
    }

    @Test
    fun placementIdsAreUnique() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun defaultLayoutsAreTheBuiltInTemplates() {
        assertEquals(3, catalog.layouts.size)
    }

    @Test
    fun fullScreenPlacementsAreTheFourFullScreenFormats() {
        assertEquals(
            listOf(catalog.interstitial, catalog.rewarded, catalog.rewardedInterstitial, catalog.appOpen),
            catalog.fullScreenPlacements,
        )
    }
}
