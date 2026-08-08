package dev.avinya.admob.showcase.domain.ad

import dev.avinya.ads.AdError
import dev.avinya.ads.AdShowResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdPresentationTest {

    @Test
    fun onlyAShownAdAdvancesTheCooldown() {
        assertTrue(advancesCooldown(AdShowResult.Shown))
    }

    @Test
    fun aNotReadyAdDoesNotBurnTheCooldown() {
        // Regression guard. This shipped broken in Phase 4: the cooldown was
        // written at the decision site, so an interstitial that never rendered
        // still suppressed the next 60 seconds.
        assertFalse(advancesCooldown(AdShowResult.NotReady))
    }

    @Test
    fun aFailedAdDoesNotBurnTheCooldown() {
        assertFalse(
            advancesCooldown(
                AdShowResult.Failed(AdError(code = "internal", message = "boom")),
            ),
        )
    }
}
