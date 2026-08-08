package dev.avinya.ads

import dev.avinya.ads.nativead.NativeAdBatching
import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import dev.avinya.ads.nativead.NativeAdOptions
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSlot
import dev.avinya.ads.nativead.NativeAdWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class NativeAdPolicyTest {

    private fun nativePlacement(id: String) = AdPlacement(
        id = id,
        format = AdFormat.Native,
        adUnitIds = AdUnitIds(
            android = "ca-app-pub-3940256099942544/2247696110",
            ios = "ca-app-pub-3940256099942544/3986624511",
        ),
    )

    // --- NativeAdMemoryPolicy defaults and validation ----------------------------

    @Test fun `default memory policy is bounded`() {
        val policy = NativeAdMemoryPolicy()
        assertEquals(4, policy.softLimit)
        assertEquals(6, policy.hardLimit)
        assertEquals(1, policy.inactiveSessionLimit)
        assertEquals(32, policy.maxInactiveSessions)
        assertEquals(64, policy.maxSessionRecords)
        assertEquals(30.minutes, policy.inactiveSessionTtl)
    }

    @Test fun `soft limit must be positive`() {
        assertFailsWith<IllegalArgumentException> { NativeAdMemoryPolicy(softLimit = 0) }
        assertFailsWith<IllegalArgumentException> { NativeAdMemoryPolicy(softLimit = -1) }
    }

    @Test fun `hard limit must be positive`() {
        assertFailsWith<IllegalArgumentException> { NativeAdMemoryPolicy(hardLimit = 0) }
    }

    @Test fun `soft limit cannot be higher than hard limit`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(softLimit = 6, hardLimit = 4)
        }
    }

    @Test fun `inactive session limit must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(inactiveSessionLimit = 0)
        }
    }

    @Test fun `inactive session limit cannot exceed hard limit`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(inactiveSessionLimit = 7, hardLimit = 6)
        }
    }

    @Test fun `max inactive sessions must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(maxInactiveSessions = 0)
        }
    }

    @Test fun `max session records must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(maxSessionRecords = 0)
        }
    }

    @Test fun `max inactive sessions cannot exceed max session records`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(maxInactiveSessions = 33, maxSessionRecords = 32)
        }
    }

    @Test fun `inactive session TTL must be finite and positive`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(inactiveSessionTtl = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(inactiveSessionTtl = (-1).minutes)
        }
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(inactiveSessionTtl = Duration.INFINITE)
        }
    }

    // --- NativeAdSessionPolicy defaults and validation --------------------------

    @Test fun `session policy defaults to previous current next`() {
        assertEquals(
            NativeAdSessionPolicy(maxRetainedAds = 3, retainBehind = 1, prefetchAhead = 1),
            NativeAdSessionPolicy(),
        )
    }

    @Test fun `max retained ads must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdSessionPolicy(maxRetainedAds = 0)
        }
    }

    @Test fun `retain behind must be non-negative`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdSessionPolicy(retainBehind = -1)
        }
    }

    @Test fun `prefetch ahead must be non-negative`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdSessionPolicy(prefetchAhead = -1)
        }
    }

    @Test fun `speculative parts must fit in max retained ads`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdSessionPolicy(maxRetainedAds = 2, retainBehind = 1, prefetchAhead = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            NativeAdSessionPolicy(maxRetainedAds = 3, retainBehind = 2, prefetchAhead = 2)
        }
    }

    @Test fun `speculative footprint addition is overflow-safe`() {
        // The data class uses Int. With naive Int addition, Int.MAX_VALUE + Int.MAX_VALUE
        // overflows to -2, which would (incorrectly) pass a < maxRetainedAds check. The
        // validator must promote to Long so the comparison sees the true sum and rejects.
        assertFailsWith<IllegalArgumentException> {
            NativeAdSessionPolicy(
                maxRetainedAds = Int.MAX_VALUE,
                retainBehind = Int.MAX_VALUE,
                prefetchAhead = Int.MAX_VALUE,
            )
        }
    }

    // --- NativeAdBatching defaults ------------------------------------------------

    @Test fun `NativeAdOptions default batching is Sequential`() {
        val options = NativeAdOptions()
        assertEquals(NativeAdBatching.Sequential, options.batching)
    }

    // --- NativeAdSlot validation -------------------------------------------------

    @Test fun `NativeAdSlot with blank key throws`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdSlot(key = "", placement = nativePlacement("p"))
        }
    }

    @Test fun `NativeAdSlot with non-Native placement throws`() {
        val banner = AdPlacement(
            id = "banner",
            format = AdFormat.Banner,
            adUnitIds = AdUnitIds(android = "x", ios = "y"),
        )
        assertFailsWith<IllegalArgumentException> {
            NativeAdSlot(key = "slot", placement = banner)
        }
    }

    // --- NativeAdWindow validation -----------------------------------------------

    @Test fun `NativeAdWindow with blank visible key throws`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdWindow(visible = listOf(NativeAdSlot(key = "", placement = nativePlacement("p"))))
        }
    }

    @Test fun `NativeAdWindow with blank retainBehind key throws`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdWindow(
                visible = listOf(NativeAdSlot(key = "a", placement = nativePlacement("p"))),
                retainBehind = listOf(NativeAdSlot(key = "", placement = nativePlacement("p"))),
            )
        }
    }

    @Test fun `NativeAdWindow with blank prefetchAhead key throws`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdWindow(
                visible = listOf(NativeAdSlot(key = "a", placement = nativePlacement("p"))),
                prefetchAhead = listOf(NativeAdSlot(key = "", placement = nativePlacement("p"))),
            )
        }
    }

    @Test fun `NativeAdWindow with duplicate key and same placement is allowed`() {
        val placement = nativePlacement("p")
        val window = NativeAdWindow(
            visible = listOf(NativeAdSlot(key = "slot-1", placement = placement)),
            prefetchAhead = listOf(NativeAdSlot(key = "slot-1", placement = placement)),
        )
        // The window does not dedup at construction time — the session core does
        // that on first-occurrence while ranking. The constructor's only contract
        // is that no key has a different placement in two bands.
        assertEquals(1, window.visible.size)
        assertEquals(1, window.prefetchAhead.size)
    }

    @Test fun `NativeAdWindow with duplicate key and different placement throws`() {
        val placementA = nativePlacement("placement-a")
        val placementB = nativePlacement("placement-b")
        val ex = assertFailsWith<IllegalArgumentException> {
            NativeAdWindow(
                visible = listOf(NativeAdSlot(key = "slot-1", placement = placementA)),
                prefetchAhead = listOf(NativeAdSlot(key = "slot-1", placement = placementB)),
            )
        }
        assertTrue(
            ex.message!!.contains("slot-1"),
            "exception should name the conflicting slot key, was: ${ex.message}",
        )
    }

    // --- AdConfig wiring ---------------------------------------------------------

    @Test fun `AdConfig secondary constructor forwards nativeAdMemoryPolicy`() {
        val custom = NativeAdMemoryPolicy(softLimit = 2, hardLimit = 3)
        val config = AdConfig(
            androidAppId = "android",
            iosAppId = "ios",
            nativeAdMemoryPolicy = custom,
        )
        assertSame(custom, config.nativeAdMemoryPolicy)
    }

    @Test fun `AdConfig secondary constructor default is the default policy`() {
        val config = AdConfig(androidAppId = "android", iosAppId = "ios")
        assertEquals(NativeAdMemoryPolicy(), config.nativeAdMemoryPolicy)
    }

    @Test fun `changing nativeAdMemoryPolicy does not change platform initialization identity`() {
        val baseline = AdConfig(androidAppId = "android", iosAppId = "ios")
        val raised = baseline.copy(
            nativeAdMemoryPolicy = NativeAdMemoryPolicy(softLimit = 5, hardLimit = 8),
        )
        assertEquals(
            baseline.initializationIdentity("android"),
            raised.initializationIdentity("android"),
            "nativeAdMemoryPolicy must not contribute to GMA initialization identity",
        )
    }

    @Test fun `changing global request configuration does change platform initialization identity`() {
        val baseline = AdConfig(androidAppId = "android", iosAppId = "ios")
        val mutated = baseline.copy(
            globalRequestConfiguration = GlobalRequestConfiguration(testDeviceIds = listOf("HASH")),
        )
        assertNotEquals(
            baseline.initializationIdentity("android"),
            mutated.initializationIdentity("android"),
        )
    }
}
