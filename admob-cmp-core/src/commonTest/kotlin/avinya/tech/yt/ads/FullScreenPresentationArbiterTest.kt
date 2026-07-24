package avinya.tech.yt.ads

import avinya.tech.yt.ads.internal.FullScreenPresentationArbiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arbiter is the single point where process-wide full-screen admission is decided.
 * commonTest on Native is single-threaded, so these tests prove the *contract* — the
 * ordering-level integration proof lives in TwoSlotArbitrationTest.
 */
class FullScreenPresentationArbiterTest {

    @Test
    fun `acquire succeeds when no token is held`() {
        val arbiter = FullScreenPresentationArbiter()
        val token = arbiter.tryAcquire("promo_slot", AdFormat.Interstitial)
        assertNotNull(token, "a free arbiter must hand out the token")
        assertEquals("promo_slot", token.placementId)
        assertEquals(AdFormat.Interstitial, token.format)
        assertTrue(arbiter.isHeld, "arbiter must report the token as held")
    }

    @Test
    fun `acquire fails while a token is held`() {
        val arbiter = FullScreenPresentationArbiter()
        val first = arbiter.tryAcquire("promo_slot", AdFormat.Interstitial)
        assertNotNull(first)

        val second = arbiter.tryAcquire("app_open_slot", AdFormat.AppOpen)
        assertNull(second, "a second acquire must fail while the first token is held")
        assertTrue(arbiter.isHeld)
    }

    @Test
    fun `release frees the arbiter for the next acquirer`() {
        val arbiter = FullScreenPresentationArbiter()
        val first = assertNotNull(arbiter.tryAcquire("promo_slot", AdFormat.Interstitial))
        arbiter.release(first)
        assertFalse(arbiter.isHeld, "release must clear the owner")

        val second = arbiter.tryAcquire("app_open_slot", AdFormat.AppOpen)
        assertNotNull(second, "the arbiter must be acquirable again after release")
        assertEquals("app_open_slot", second.placementId)
    }

    @Test
    fun `release is idempotent and only the first call reports true`() {
        val arbiter = FullScreenPresentationArbiter()
        val token = assertNotNull(arbiter.tryAcquire("promo_slot", AdFormat.Interstitial))

        assertTrue(arbiter.release(token), "first release owns the transition")
        assertFalse(arbiter.release(token), "a repeated release must be a no-op")
        assertFalse(arbiter.isHeld)
    }

    @Test
    fun `a stale token cannot release a token acquired later`() {
        val arbiter = FullScreenPresentationArbiter()
        val stale = assertNotNull(arbiter.tryAcquire("promo_slot", AdFormat.Interstitial))
        arbiter.release(stale)
        val current = assertNotNull(arbiter.tryAcquire("app_open_slot", AdFormat.AppOpen))

        assertFalse(arbiter.release(stale), "a stale token must not release someone else's turn")
        assertTrue(arbiter.isHeld, "the current owner must still hold the token")

        assertTrue(arbiter.release(current))
        assertFalse(arbiter.isHeld)
    }

    @Test
    fun `token remains releasable after the acquirer throws`() {
        val arbiter = FullScreenPresentationArbiter()
        val token = assertNotNull(arbiter.tryAcquire("promo_slot", AdFormat.Interstitial))

        assertFailsWith<IllegalStateException> {
            try {
                error("simulated failure while committing the presentation")
            } finally {
                arbiter.release(token)
            }
        }

        assertFalse(arbiter.isHeld, "a throw on the acquire path must not strand the token")
        assertNotNull(
            arbiter.tryAcquire("app_open_slot", AdFormat.AppOpen),
            "the arbiter must be usable after a failed presentation attempt"
        )
    }

    @Test
    fun `holder describes the current owner for diagnostics`() {
        val arbiter = FullScreenPresentationArbiter()
        assertNull(arbiter.currentHolder(), "a free arbiter has no holder")

        val token = assertNotNull(arbiter.tryAcquire("promo_slot", AdFormat.Interstitial))
        val holder = assertNotNull(arbiter.currentHolder())
        assertTrue(holder.contains("promo_slot"), "holder description must name the placement: $holder")
        assertTrue(holder.contains("Interstitial"), "holder description must name the format: $holder")

        arbiter.release(token)
        assertNull(arbiter.currentHolder())
    }
}
