package dev.avinya.admob.cmp.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DemoAdPlacementIdsTest {
    @Test
    fun resolvesRegisteredInterstitialId() {
        assertEquals(
            DemoAdPlacementIds.INTERSTITIAL,
            resolveDemoInterstitialPlacementId(DemoAdPlacementIds.INTERSTITIAL),
        )
    }

    @Test
    fun rejectsUnknownInterstitialId() {
        assertFailsWith<IllegalArgumentException> {
            resolveDemoInterstitialPlacementId("feed_item_42")
        }
    }

    @Test
    fun rejectsBlankInterstitialId() {
        assertFailsWith<IllegalArgumentException> {
            resolveDemoInterstitialPlacementId("   ")
        }
    }
}
