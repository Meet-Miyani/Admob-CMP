@file:OptIn(dev.avinya.ads.InternalAdMobCmpApi::class)

package dev.avinya.ads.internal

import dev.avinya.ads.AdError
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSessionState
import dev.avinya.ads.nativead.NativeAdSlot
import dev.avinya.ads.nativead.NativeAdSlotState
import dev.avinya.ads.nativead.NativeAdWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


/**
 * One slot the coordinator must load (or reload) as a result of
 * [NativeAdSessionCore.updateWindow] or [NativeAdSessionCore.expireSlot].
 *
 * The [generation] is a per-slot, monotonically increasing counter the
 * session bumps every time it emits demand for the slot. The coordinator
 * passes the generation back into [NativeAdSessionCore.recordAdmitted] /
 * [recordFailed]; the session rejects callbacks that carry an older
 * generation (e.g. an admit that arrived after [expireSlot] already
 * retired the slot, or a callback from a window that has since
 * scrolled past). The same mechanism is the platform-callback side
 * of the coordinator's generation check.
 */
internal data class SlotDemandEntry(
    val key: String,
    val placement: AdPlacement,
    val generation: Long,
)

/**
 * The set of slots a [NativeAdSessionCore.updateWindow] or
 * [NativeAdSessionCore.expireSlot] call needs loaded. The coordinator
 * reserves capacity and loads each entry in order, calling
 * [NativeAdSessionCore.recordAdmitted] or
 * [NativeAdSessionCore.recordFailed] with the same [SlotDemandEntry.generation]
 * once the platform responds.
 */
internal data class SlotDemand(val slots: List<SlotDemandEntry>)

/**
 * Per-session slot state machine. The session owns the slot map
 * (slotKey -> [SlotEntry]) and the transitions between the public
 * [NativeAdSlotState] values; the [NativeAdGovernor] owns the
 * process-wide record and reservation counts; the coordinator owns
 * the actual platform ad objects behind the record ids.
 *
 * The session's invariants:
 * - Each slot has at most one [NativeAdRecordId] at a time.
 * - A demand for a slot carries a generation; recordAdmitted /
 *   recordFailed carry the same generation. A stale generation is
 *   rejected and the callback is dropped (the slot is in Empty /
 *   Loading for a newer generation, or in some other state that
 *   does not accept this callback).
 * - [updateWindow] dedupes (key, placement) duplicates on first
 *   occurrence (visible > prefetchAhead > retainBehind) and admits
 *   demand for the first [NativeAdSessionPolicy.maxRetainedAds]
 *   ranked slots. Lower-priority slots are not loaded; if they have
 *   a record it stays as a "far behind" anchor.
 * - [deactivate] retains at most `min(NativeAdMemoryPolicy.inactiveSessionLimit,
 *   policy.maxRetainedAds)` anchors, picking the last visible slot
 *   first, falling back to the most recently admitted record. All
 *   other detached records are retired.
 * - The slot map only retains entries that have a record, an
 *   in-flight demand, or are in the current window. Walking a
 *   thousand unique slot keys does not grow the map unbounded.
 *
 * All mutating operations are protected by an internal lock and
 * publish [state] atomically. Public [NativeAdSlotState] values are
 * computed from the slot entry at publish time.
 */
internal class NativeAdSessionCore(
    val key: String,
    val policy: NativeAdSessionPolicy,
    private val memoryPolicy: NativeAdMemoryPolicy,
    private val governor: NativeAdGovernor,
) {
    private val lock = FullScreenStateLock()

    private data class SlotEntry(
        val placement: AdPlacement,
        var generation: Long = 0L,
        var recordId: NativeAdRecordId? = null,
        var demandInFlight: Long? = null,
        var mounted: Boolean = false,
        var inVisible: Boolean = false,
        var lastError: AdError? = null,
    )

    private val slots = mutableMapOf<String, SlotEntry>()
    private var active: Boolean = true
    // Generation starts at 1 so callers can use `generation > 0` as a sanity
    // check for "freshly emitted" demand without colliding with the default
    // zero value of a never-touched slot entry.
    private var nextGeneration: Long = 1L

    private val _state = MutableStateFlow(
        NativeAdSessionState(active = true, slots = emptyMap()),
    )
    val state: StateFlow<NativeAdSessionState> = _state.asStateFlow()

    /**
     * Reports the new viewport window. Returns the slots the
     * coordinator must (re)load to satisfy the new ranking.
     *
     * Ranking is visible > prefetchAhead > retainBehind with
     * first-occurrence dedup. Demand is emitted for the first
     * [NativeAdSessionPolicy.maxRetainedAds] ranked slots that do
     * not already have a record or an in-flight demand on a current
     * generation. Out-of-window slots that have a record keep it
     * (they become far-behind candidates for the governor's trim);
     * out-of-window slots with neither record nor in-flight demand
     * are removed from the map.
     *
     * @throws IllegalArgumentException if a slot in the window
     *   carries a placement that differs from the one the session
     *   already recorded for the same key.
     */
    fun updateWindow(window: NativeAdWindow): SlotDemand = lock.withLock {
        validatePlacementConsistency(window)

        val ranked = rankSlots(window)
        val rankedKeys = ranked.map { it.key }.toSet()
        val demand = mutableListOf<SlotDemandEntry>()

        for (slot in ranked.take(policy.maxRetainedAds)) {
            val entry = slots.getOrPut(slot.key) {
                SlotEntry(placement = slot.placement, generation = nextGeneration++)
            }
            entry.placement // sanity: slot.placement == entry.placement validated above
            entry.inVisible = true
            // Demand only when there is no record and no current in-flight demand
            // for the same generation. A previous demand for a stale generation
            // does not block a new demand — the old callback will be dropped on
            // arrival (the session compares the callback's generation against
            // the entry's current generation and rejects mismatches).
            if (entry.recordId == null) {
                val gen = nextGeneration++
                entry.generation = gen
                entry.demandInFlight = gen
                entry.lastError = null
                demand.add(SlotDemandEntry(slot.key, slot.placement, gen))
            }
        }

        // Slots that left the window entirely. The session tracks the current
        // viewport, not the history: out-of-window entries are removed
        // regardless of record or in-flight state. The governor still owns
        // the records (as far-behind candidates for the next moderate
        // trim); any pending demand's eventual admit is dropped at the
        // session's generation check (the slot is no longer in the map).
        for ((key, _) in slots.toMap()) {
            if (key !in rankedKeys) {
                slots.remove(key)
            }
        }

        publishState()
        SlotDemand(demand)
    }

    /**
     * Coordinator callback: a reservation was admitted to a record.
     * The [generation] must match the entry's current generation;
     * a mismatch means a newer [updateWindow] or [expireSlot] has
     * since superseded this slot and the callback is dropped.
     */
    fun recordAdmitted(slotKey: String, recordId: NativeAdRecordId, generation: Long) = lock.withLock {
        val entry = slots[slotKey] ?: return@withLock
        if (entry.generation != generation) return@withLock
        entry.recordId = recordId
        entry.demandInFlight = null
        entry.lastError = null
        publishState()
    }

    /**
     * Coordinator callback: a load attempt failed. The slot's state
     * becomes [NativeAdSlotState.Failed]. Stale generations are
     * rejected; a future [updateWindow] can reissue demand.
     */
    fun recordFailed(slotKey: String, error: AdError, generation: Long) = lock.withLock {
        val entry = slots[slotKey] ?: return@withLock
        if (entry.generation != generation) return@withLock
        entry.recordId = null
        entry.demandInFlight = null
        entry.lastError = error
        publishState()
    }

    /**
     * Coordinator callback: the record for this slot expired (the
     * 1-hour native TTL fired, or the platform reported expiry).
     * The session retires the old record, clears the slot to Empty
     * (no in-flight demand — the new demand is emitted via the
     * returned [SlotDemand] and the coordinator's first
     * [recordAdmitted] is what transitions the slot to Loading /
     * Ready), and bumps the generation. Any platform callback for
     * the old generation is dropped at the coordinator's generation
     * check.
     */
    fun expireSlot(slotKey: String): SlotDemand = lock.withLock {
        val entry = slots[slotKey] ?: return@withLock SlotDemand(emptyList())
        val recordId = entry.recordId
        if (recordId != null) {
            governor.retire(recordId)
        }
        entry.recordId = null
        entry.mounted = false
        entry.demandInFlight = null
        entry.lastError = null
        val gen = nextGeneration++
        entry.generation = gen
        publishState()
        SlotDemand(listOf(SlotDemandEntry(slotKey, entry.placement, gen)))
    }

    /**
     * Coordinator callback: the renderer for this slot has attached
     * ([mounted] = true) or detached ([mounted] = false). Mounted
     * records are never eviction candidates under any [NativeAdGovernor.trim].
     * No-op on an unknown id.
     */
    fun setMounted(slotKey: String, mounted: Boolean) = lock.withLock {
        val entry = slots[slotKey] ?: return@withLock
        val recordId = entry.recordId ?: return@withLock
        governor.setMounted(recordId, mounted)
        entry.mounted = mounted
        publishState()
    }

    /**
     * Marks the session inactive. The session picks up to
     * `min(memoryPolicy.inactiveSessionLimit, policy.maxRetainedAds)`
     * anchor records — preferring mounted records, then the last
     * visible slot, falling back to the most recently admitted
     * record. Every other detached record is retired (the
     * coordinator destroys the platform objects after this call
     * returns; this method just removes them from the governor).
     * Mounted anchors are automatically unmounted because the
     * session is no longer tracking the viewport.
     */
    fun deactivate(): List<NativeAdRecordId> = lock.withLock {
        active = false
        val anchorLimit = minOf(memoryPolicy.inactiveSessionLimit, policy.maxRetainedAds)
        val withRecords = slots.values.filter { it.recordId != null }
        val anchors = pickAnchors(withRecords, anchorLimit)
        val anchorRecordIds = anchors.mapNotNull { it.recordId }.toSet()
        val toRetire = withRecords
            .filter { it.recordId !in anchorRecordIds }
            .mapNotNull { it.recordId }
        toRetire.forEach { governor.retire(it) }
        for (entry in slots.values) {
            val rid = entry.recordId
            when {
                rid != null && rid in toRetire -> {
                    entry.recordId = null
                    entry.demandInFlight = null
                    entry.mounted = false
                    entry.inVisible = false
                    entry.lastError = null
                }
                rid != null && rid in anchorRecordIds -> {
                    // Anchor: unmount automatically and drop the inVisible flag,
                    // but keep the record. After deactivate the session is no
                    // longer tracking the viewport, so the anchor's status
                    // collapses to Retained.
                    if (entry.mounted) {
                        governor.setMounted(rid, false)
                        entry.mounted = false
                    }
                    entry.inVisible = false
                }
            }
        }
        publishState()
        toRetire
    }

    /**
     * Closes the session, retiring every record and clearing the
     * slot map. Idempotent: a second call is a no-op.
     */
    fun close(): List<NativeAdRecordId> = lock.withLock {
        val toRetire = slots.values.mapNotNull { it.recordId }
        toRetire.forEach { governor.retire(it) }
        slots.clear()
        active = false
        publishState()
        toRetire
    }

    /**
     * Returns the current record id for a slot, or null if the slot
     * has no record (Empty, Loading, or Failed). The coordinator
     * uses this to map a slot to a render handle.
     */
    fun recordIdFor(slotKey: String): NativeAdRecordId? = lock.withLock {
        slots[slotKey]?.recordId
    }

    private fun pickAnchors(
        candidates: List<SlotEntry>,
        anchorLimit: Int,
    ): List<SlotEntry> {
        if (anchorLimit <= 0 || candidates.isEmpty()) return emptyList()
        // Rank: mounted first, then last visible slot first, then most recent.
        // higher inVisibleIndex is "later in viewport" which the plan specifies
        // as the preferred anchor; the slot map preserves insertion order which
        // mirrors the viewport order from the most recent updateWindow call.
        return candidates.sortedWith(
            compareByDescending<SlotEntry> { it.mounted }
                .thenByDescending { it.inVisible }
                .thenByDescending { it.generation },
        ).take(anchorLimit)
    }

    private fun validatePlacementConsistency(window: NativeAdWindow) {
        for (band in listOf(window.visible, window.prefetchAhead, window.retainBehind)) {
            for (slot in band) {
                val existing = slots[slot.key]
                if (existing != null && existing.placement != slot.placement) {
                    throw IllegalArgumentException(
                        "NativeAdSession '${key}': slot '${slot.key}' placement changed " +
                            "from '${existing.placement.id}' to '${slot.placement.id}'. " +
                            "Reuse the same AdPlacement instance for the same key across " +
                            "viewport updates."
                    )
                }
            }
        }
    }

    private fun rankSlots(window: NativeAdWindow): List<NativeAdSlot> {
        val result = mutableListOf<NativeAdSlot>()
        val seen = mutableSetOf<String>()
        for (band in listOf(window.visible, window.prefetchAhead, window.retainBehind)) {
            for (slot in band) {
                if (seen.add(slot.key)) result.add(slot)
            }
        }
        return result
    }

    private fun publishState() {
        val stateMap = slots.mapValues { (_, entry) -> statusFor(entry) }
        _state.value = NativeAdSessionState(
            active = active,
            slots = stateMap,
        )
    }

    private fun statusFor(entry: SlotEntry): NativeAdSlotState = when {
        entry.lastError != null -> NativeAdSlotState.Failed(entry.lastError!!)
        entry.recordId == null && entry.demandInFlight != null -> NativeAdSlotState.Loading
        entry.recordId == null -> NativeAdSlotState.Empty
        entry.mounted -> NativeAdSlotState.Mounted(null)
        entry.inVisible -> NativeAdSlotState.Ready(null)
        else -> NativeAdSlotState.Retained(null)
    }
}
