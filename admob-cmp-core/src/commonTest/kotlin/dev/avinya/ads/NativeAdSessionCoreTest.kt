package dev.avinya.ads

import dev.avinya.ads.internal.NativeAdDemandClass
import dev.avinya.ads.internal.NativeAdGovernor
import dev.avinya.ads.internal.NativeAdPriority
import dev.avinya.ads.internal.NativeAdRecordId
import dev.avinya.ads.internal.NativeAdSessionCore
import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSlot
import dev.avinya.ads.nativead.NativeAdSlotState
import dev.avinya.ads.nativead.NativeAdWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeAdSessionCoreTest {

    private fun nativePlacement(id: String) = AdPlacement(
        id = id,
        format = AdFormat.Native,
        adUnitIds = AdUnitIds(
            android = "ca-app-pub-3940256099942544/2247696110",
            ios = "ca-app-pub-3940256099942544/3986624511",
        ),
    )

    private fun reserveAndAdmit(
        gov: NativeAdGovernor,
        priority: NativeAdPriority = NativeAdPriority.Speculative,
    ): NativeAdRecordId {
        val decision = gov.reserve(NativeAdDemandClass.Visible, priority, 1, allowPartial = true)
        check(decision.reservations.isNotEmpty()) { "expected a reservation" }
        return gov.admit(decision.reservations.single())
    }

    private fun setup(
        gov: NativeAdGovernor,
        session: NativeAdSessionCore,
        window: NativeAdWindow,
    ): Map<String, NativeAdRecordId> {
        val demand = session.updateWindow(window)
        val recordIds = mutableMapOf<String, NativeAdRecordId>()
        for (entry in demand.slots) {
            val recordId = reserveAndAdmit(gov)
            session.recordAdmitted(entry.key, recordId, entry.generation)
            recordIds[entry.key] = recordId
        }
        return recordIds
    }

    // --- 1 ---------------------------------------------------------------------

    @Test fun `same slot survives deactivate and reactivate`() {
        val policy = NativeAdMemoryPolicy(softLimit = 2, hardLimit = 4, inactiveSessionLimit = 1)
        val gov = NativeAdGovernor(policy)
        val session = NativeAdSessionCore(
            key = "feed-1",
            policy = NativeAdSessionPolicy(),
            memoryPolicy = policy,
            governor = gov,
        )
        val placement = nativePlacement("p")
        val window = NativeAdWindow(visible = listOf(NativeAdSlot("a", placement)))
        val records = setup(gov, session, window)
        val recordId = records.getValue("a")
        session.setMounted("a", true)

        // Visible + has record -> Mounted.
        assertTrue(
            session.state.value.slots["a"] is NativeAdSlotState.Mounted,
            "expected Mounted while visible and loaded, got ${session.state.value.slots["a"]}",
        )

        // Deactivate keeps the record as an anchor but demotes Mounted -> Retained.
        session.deactivate()
        assertTrue(
            session.state.value.slots["a"] is NativeAdSlotState.Retained,
            "expected Retained after deactivate, got ${session.state.value.slots["a"]}",
        )
        assertEquals(recordId, session.recordIdFor("a"), "anchor must keep the same record id")
        assertEquals(1, countInGovernor(gov), "the anchor record is still loaded in the governor")

        // Reactivate: same record; renderer must re-mount via setMounted.
        session.updateWindow(window)
        session.setMounted("a", true)
        assertTrue(
            session.state.value.slots["a"] is NativeAdSlotState.Mounted,
            "expected Mounted after reactivate, got ${session.state.value.slots["a"]}",
        )
        assertEquals(recordId, session.recordIdFor("a"), "the same record id must come back")
    }

    // --- 2 ---------------------------------------------------------------------

    @Test fun `inactive session retains only the last visible anchor`() {
        val policy = NativeAdMemoryPolicy(softLimit = 1, hardLimit = 4, inactiveSessionLimit = 1)
        val gov = NativeAdGovernor(policy)
        val session = NativeAdSessionCore(
            key = "feed-2",
            policy = NativeAdSessionPolicy(maxRetainedAds = 3),
            memoryPolicy = policy,
            governor = gov,
        )
        val placement = nativePlacement("p")
        val window = NativeAdWindow(visible = listOf(
            NativeAdSlot("a", placement),
            NativeAdSlot("b", placement),
            NativeAdSlot("c", placement),
        ))
        val records = setup(gov, session, window)
        val cId = records.getValue("c")

        val retired = session.deactivate()

        // Only "c" (the last visible) survives as an anchor; "a" and "b" are retired.
        assertEquals(setOf(records.getValue("a"), records.getValue("b")), retired.toSet())
        assertEquals(cId, session.recordIdFor("c"))
        assertNull(session.recordIdFor("a"))
        assertNull(session.recordIdFor("b"))
    }

    // --- 3 ---------------------------------------------------------------------

    @Test fun `far behind slots become eviction candidates and are not destroyed inside the session lock`() {
        val policy = NativeAdMemoryPolicy(softLimit = 1, hardLimit = 6, inactiveSessionLimit = 1)
        val gov = NativeAdGovernor(policy)
        val session = NativeAdSessionCore(
            key = "feed-3",
            policy = NativeAdSessionPolicy(maxRetainedAds = 3, retainBehind = 1, prefetchAhead = 1),
            memoryPolicy = policy,
            governor = gov,
        )
        val placement = nativePlacement("p")
        // First window: a, b, c all visible.
        val w1 = NativeAdWindow(visible = listOf(
            NativeAdSlot("a", placement),
            NativeAdSlot("b", placement),
            NativeAdSlot("c", placement),
        ))
        setup(gov, session, w1)
        // Now scroll: only d is visible; a is the retainBehind anchor. b and c are
        // out of the window. We deliberately do NOT admit d in this test — the
        // point is that the far-behind records (b, c) survive even though they
        // are no longer tracked by the session's slot map.
        val w2 = NativeAdWindow(
            visible = listOf(NativeAdSlot("d", placement)),
            retainBehind = listOf(NativeAdSlot("a", placement)),
        )
        val demand = session.updateWindow(w2)
        assertEquals(1, demand.slots.size, "demand only for d (a is already admitted)")
        assertEquals("d", demand.slots.single().key)
        // The far-behind records (b, c) are still loaded in the governor — the
        // session did not destroy them itself. The session is not the owner of
        // platform destruction; the governor (or the coordinator via trim) is.
        assertEquals(3, countInGovernor(gov), "a, b, c still loaded; session did not destroy them")
        // "a" is the retainBehind anchor, still in the map.
        assertNotNull(session.recordIdFor("a"))
    }

    // --- 4 ---------------------------------------------------------------------

    @Test fun `placement change for an existing slot is rejected`() {
        val policy = NativeAdMemoryPolicy(softLimit = 1, hardLimit = 4)
        val gov = NativeAdGovernor(policy)
        val session = NativeAdSessionCore(
            key = "feed-4",
            policy = NativeAdSessionPolicy(),
            memoryPolicy = policy,
            governor = gov,
        )
        val placement1 = nativePlacement("p1")
        val placement2 = nativePlacement("p2")
        val w1 = NativeAdWindow(visible = listOf(NativeAdSlot("a", placement1)))
        setup(gov, session, w1)
        val w2 = NativeAdWindow(visible = listOf(NativeAdSlot("a", placement2)))
        assertFailsWith<IllegalArgumentException> {
            session.updateWindow(w2)
        }
    }

    // --- 5 ---------------------------------------------------------------------

    @Test fun `close is idempotent`() {
        val policy = NativeAdMemoryPolicy(softLimit = 1, hardLimit = 4)
        val gov = NativeAdGovernor(policy)
        val session = NativeAdSessionCore(
            key = "feed-5",
            policy = NativeAdSessionPolicy(),
            memoryPolicy = policy,
            governor = gov,
        )
        val placement = nativePlacement("p")
        val window = NativeAdWindow(visible = listOf(
            NativeAdSlot("a", placement),
            NativeAdSlot("b", placement),
        ))
        val records = setup(gov, session, window)
        val firstRetired = session.close()
        assertEquals(records.values.toSet(), firstRetired.toSet())
        val secondRetired = session.close()
        assertTrue(secondRetired.isEmpty(), "second close must be a no-op")
        // The slot map is cleared.
        assertTrue(session.state.value.slots.isEmpty())
    }

    // --- 6 ---------------------------------------------------------------------

    @Test fun `expired slot never remounts`() {
        val policy = NativeAdMemoryPolicy(softLimit = 1, hardLimit = 4)
        val gov = NativeAdGovernor(policy)
        val session = NativeAdSessionCore(
            key = "feed-6",
            policy = NativeAdSessionPolicy(),
            memoryPolicy = policy,
            governor = gov,
        )
        val placement = nativePlacement("p")
        val window = NativeAdWindow(visible = listOf(NativeAdSlot("a", placement)))
        val records = setup(gov, session, window)
        // TTL expired; the coordinator calls expireSlot on the session.
        val demand = session.expireSlot("a")
        // The slot is now in Loading (new reload demand in flight) and the
        // session emits the demand for the coordinator to act on. The old
        // record is retired — it will never remount.
        val slotAfter = session.state.value.slots["a"]
        assertTrue(
            slotAfter is NativeAdSlotState.Loading,
            "expected Loading after expire (reload demand in flight), got $slotAfter",
        )
        assertEquals(listOf("a"), demand.slots.map { it.key })
        // The original record is gone from the session's view.
        assertNull(session.recordIdFor("a"))
        assertEquals(0, countInGovernor(gov))
        // The reload demand references a fresh generation so the coordinator can
        // race the platform callback without mis-attributing it to the expired
        // record.
        assertTrue(demand.slots.single().generation > 0, "reload generation must be positive")
    }

    // --- 7 ---------------------------------------------------------------------

    @Test fun `walking one thousand slot keys keeps only window and retained state`() {
        val policy = NativeAdMemoryPolicy(softLimit = 1, hardLimit = 8, inactiveSessionLimit = 1)
        val gov = NativeAdGovernor(policy)
        val session = NativeAdSessionCore(
            key = "feed-7",
            policy = NativeAdSessionPolicy(maxRetainedAds = 3, retainBehind = 1, prefetchAhead = 1),
            memoryPolicy = policy,
            governor = gov,
        )
        val placement = nativePlacement("p")
        // First window primes 3 records.
        val first = NativeAdWindow(visible = listOf(
            NativeAdSlot("seed-a", placement),
            NativeAdSlot("seed-b", placement),
            NativeAdSlot("seed-c", placement),
        ))
        setup(gov, session, first)
        // Now walk 1000 unique slot keys through the viewport, three at a time.
        // Each iteration prunes the previous iteration's out-of-window slots,
        // so the slot map cannot grow without bound.
        for (i in 0 until 1000) {
            val window = NativeAdWindow(
                visible = listOf(
                    NativeAdSlot("k-$i", placement),
                    NativeAdSlot("k-${i + 1}", placement),
                    NativeAdSlot("k-${i + 2}", placement),
                ),
                retainBehind = listOf(NativeAdSlot("k-${i - 1}", placement)),
            )
            session.updateWindow(window)
        }
        // The slot map cannot grow without bound: at most the current window's
        // visible + retainBehind keys are mapped. We assert a generous upper
        // bound (well below 1000).
        val mappedSlots = session.state.value.slots.size
        assertTrue(
            mappedSlots <= 20,
            "slot map grew unbounded ($mappedSlots entries after 1000 unique keys)",
        )
    }

    private fun countInGovernor(gov: NativeAdGovernor): Int = gov.state().loadedRecords
}
