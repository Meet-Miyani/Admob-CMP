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
 * scrolled past).
 */
internal data class SlotDemandEntry(
    val key: String,
    val placement: AdPlacement,
    val generation: Long,
    val band: NativeAdBand,
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
 * Which viewport band a slot is in. The session tracks this so the
 * published [NativeAdSlotState] can distinguish "visible and loaded"
 * ([NativeAdSlotState.Ready] / [NativeAdSlotState.Mounted]) from
 * "prefetched or retained" ([NativeAdSlotState.Retained]) and so
 * retired records that fall out of the window can be picked up by
 * the next [NativeAdSessionCore.updateWindow] at the right priority.
 */
internal enum class NativeAdBand { Visible, PrefetchAhead, RetainBehind, Out }

/**
 * Per-session slot state machine. The session owns the slot map
 * (slotKey -> [SlotEntry]) and the transitions between the public
 * [NativeAdSlotState] values; the [NativeAdGovernor] owns the
 * process-wide record and reservation counts; the coordinator owns
 * the actual platform ad objects behind the record ids and is
 * responsible for retiring them on close/clear/expiry.
 *
 * Contract:
 * - Each slot has at most one [NativeAdRecordId] at a time.
 * - A demand for a slot carries a generation; recordAdmitted /
 *   recordFailed carry the same generation. A stale generation is
 *   rejected and the callback is dropped.
 * - [updateWindow] is idempotent: emitting demand for a slot whose
 *   current generation is already in flight is a no-op. A new
 *   generation is only emitted when the previous one has resolved
 *   (admitted, failed, or superseded by expiry).
 * - Out-of-window slot entries with no record, no in-flight demand,
 *   and no terminal error are pruned so the map cannot grow
 *   unbounded under scrolling. Entries that own a record or have
 *   an in-flight demand are preserved (the platform ad is still
 *   alive in the coordinator and the next window may pull the slot
 *   back in).
 * - [deactivate] retains at most `min(NativeAdMemoryPolicy.inactiveSessionLimit,
 *   policy.maxRetainedAds)` anchors (mounted first, then last
 *   visible, then most recently admitted). All other detached
 *   records are retired.
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
        var band: NativeAdBand = NativeAdBand.Out,
        var lastError: AdError? = null,
    )

    private val slots = mutableMapOf<String, SlotEntry>()
    private var active: Boolean = true
    private var nextGeneration: Long = 1L
    // In-flight cap. The session refuses to emit new demand for a slot
    // whose band is anything other than the current viewport when the
    // in-flight count is at this cap. Default: hardLimit, so the
    // session's in-flight matches the governor's capacity. A consumer
    // that wants a tighter cap can pass a smaller value.
    private val inFlightCap: Int = memoryPolicy.hardLimit

    private val _state = MutableStateFlow(
        NativeAdSessionState(active = true, slots = emptyMap()),
    )
    val state: StateFlow<NativeAdSessionState> = _state.asStateFlow()

    /**
     * Reports the new viewport window. Returns the slots the
     * coordinator must (re)load to satisfy the new ranking. Marks
     * the session as active.
     *
     * Ranking is visible > prefetchAhead > retainBehind with
     * first-occurrence dedup. Demand is emitted for the first
     * [NativeAdSessionPolicy.maxRetainedAds] ranked slots that do
     * not already have a record or an in-flight demand on a current
     * generation, and only while the session's in-flight count is
     * below [inFlightCap]. Demand is band-aware: a visible slot is
     * preferred over a prefetch slot, a prefetch slot over a retain
     * slot, but every band gets a chance to load once the cap is
     * hit. Out-of-window entries with no record, no in-flight
     * demand, and no terminal error are pruned.
     *
     * @throws IllegalArgumentException if a slot in the window
     *   carries a placement that differs from the one the session
     *   already recorded for the same key.
     */
    fun updateWindow(window: NativeAdWindow): SlotDemand = lock.withLock {
        active = true
        validatePlacementConsistency(window)

        val ranked = rankAndClassify(window)
        val windowKeys = ranked.map { it.entry.key }.toSet()
        val demand = mutableListOf<SlotDemandEntry>()
        val inFlightBefore = currentInFlightCount()

        for (rankedSlot in ranked.take(policy.maxRetainedAds)) {
            val (band, slot) = rankedSlot
            val entry = slots.getOrPut(slot.key) {
                SlotEntry(placement = slot.placement, generation = nextGeneration++)
            }
            // If the placement changed for an existing slot, validatePlacementConsistency
            // would have already thrown. The slot.placement here matches entry.placement.
            entry.band = band
            if (entry.recordId != null) continue
            if (entry.demandInFlight != null) continue  // Idempotent: previous demand still in flight.
            if (inFlightBefore + demand.size >= inFlightCap) continue  // Cap reached.
            // Emit a new demand at a fresh generation.
            val gen = nextGeneration++
            entry.generation = gen
            entry.demandInFlight = gen
            entry.lastError = null
            demand.add(SlotDemandEntry(slot.key, slot.placement, gen, band))
        }

        // Out-of-window entries: update the band, then prune anything that
        // owns nothing. Anchors (records) and in-flight entries stay.
        for ((key, entry) in slots.toMap()) {
            if (key !in windowKeys) {
                entry.band = NativeAdBand.Out
                if (entry.recordId == null && entry.demandInFlight == null && entry.lastError == null) {
                    slots.remove(key)
                }
            }
        }

        publishState()
        SlotDemand(demand)
    }

    /**
     * Coordinator callback: a reservation was admitted to a record.
     * The [generation] must match the entry's current generation;
     * a mismatch means a newer [updateWindow] or [expireSlot] has
     * since superseded this slot and the callback is dropped. The
     * returned [Boolean] is true when the generation was accepted.
     */
    fun recordAdmitted(slotKey: String, recordId: NativeAdRecordId, generation: Long): Boolean = lock.withLock {
        val entry = slots[slotKey] ?: return@withLock false
        if (entry.generation != generation) return@withLock false
        entry.recordId = recordId
        entry.demandInFlight = null
        entry.lastError = null
        publishState()
        true
    }

    /**
     * Coordinator callback: a load attempt failed. Returns true when
     * the generation was accepted.
     */
    fun recordFailed(slotKey: String, error: AdError, generation: Long): Boolean = lock.withLock {
        val entry = slots[slotKey] ?: return@withLock false
        if (entry.generation != generation) return@withLock false
        entry.recordId = null
        entry.demandInFlight = null
        entry.lastError = error
        publishState()
        true
    }

    /**
     * Coordinator callback: the record for this slot expired (the
     * 1-hour native TTL fired, or the platform reported expiry).
     * The session retires the old record (so the coordinator can
     * destroy the platform ad), clears the slot to Empty, and
     * emits a fresh demand at a new generation. The coordinator
     * will call [recordAdmitted] at that generation; any platform
     * callback for the old generation is dropped at the coordinator's
     * generation check.
     */
    fun expireSlot(slotKey: String): SlotDemand = lock.withLock {
        val entry = slots[slotKey] ?: return@withLock SlotDemand(emptyList())
        val recordId = entry.recordId
        if (recordId != null) {
            governor.retire(recordId)
        }
        // Clear the in-flight, bump the generation, leave the band
        // intact so the next platform callback can route the admit
        // back here. The slot returns to Empty until the coordinator
        // submits the reload demand.
        entry.demandInFlight = null
        entry.recordId = null
        entry.mounted = false
        entry.lastError = null
        val gen = nextGeneration++
        entry.generation = gen
        entry.demandInFlight = gen
        publishState()
        SlotDemand(listOf(SlotDemandEntry(slotKey, entry.placement, gen, entry.band)))
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
     * Marks the session inactive. Retains at most
     * `min(NativeAdMemoryPolicy.inactiveSessionLimit, policy.maxRetainedAds)`
     * anchors, picks the last visible slot first, then mounted, then
     * most-recently admitted. All other detached records are
     * retired; the slot entries that lost their record become
     * Empty. The session is marked inactive so the public state
     * reflects it; the next [updateWindow] re-marks it active.
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
                    entry.band = NativeAdBand.Out
                    entry.lastError = null
                }
                rid != null && rid in anchorRecordIds -> {
                    if (entry.mounted) {
                        governor.setMounted(rid, false)
                        entry.mounted = false
                    }
                    entry.band = NativeAdBand.Out
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
     * has no record (Empty, Loading, or Failed).
     */
    fun recordIdFor(slotKey: String): NativeAdRecordId? = lock.withLock {
        slots[slotKey]?.recordId
    }

    private fun currentInFlightCount(): Int = slots.values.count { it.demandInFlight != null }

    private data class RankedSlot(val band: NativeAdBand, val entry: NativeAdSlot)

    private fun rankAndClassify(window: NativeAdWindow): List<RankedSlot> {
        val result = mutableListOf<RankedSlot>()
        val seen = mutableSetOf<String>()
        // Visible first.
        for (slot in window.visible) {
            if (seen.add(slot.key)) result.add(RankedSlot(NativeAdBand.Visible, slot))
        }
        // Then prefetch-ahead.
        for (slot in window.prefetchAhead) {
            if (seen.add(slot.key)) result.add(RankedSlot(NativeAdBand.PrefetchAhead, slot))
        }
        // Then retain-behind.
        for (slot in window.retainBehind) {
            if (seen.add(slot.key)) result.add(RankedSlot(NativeAdBand.RetainBehind, slot))
        }
        return result
    }

    private fun pickAnchors(
        candidates: List<SlotEntry>,
        anchorLimit: Int,
    ): List<SlotEntry> {
        if (anchorLimit <= 0 || candidates.isEmpty()) return emptyList()
        return candidates.sortedWith(
            compareByDescending<SlotEntry> { it.mounted }
                .thenBy { it.band.ordinal }  // Visible first, then PrefetchAhead, then RetainBehind
                .thenByDescending { it.generation },
        ).take(anchorLimit)
    }

    private fun validatePlacementConsistency(window: NativeAdWindow) {
        for (band in listOf(window.visible, window.prefetchAhead, window.retainBehind)) {
            for (slot in band) {
                val existing = slots[slot.key]
                if (existing != null && existing.placement != slot.placement) {
                    throw IllegalArgumentException(
                        "NativeAdSession '$key': slot '${slot.key}' placement changed " +
                            "from '${existing.placement.id}' to '${slot.placement.id}'. " +
                            "Reuse the same AdPlacement instance for the same key across " +
                            "viewport updates."
                    )
                }
            }
        }
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
        entry.band == NativeAdBand.Visible -> NativeAdSlotState.Ready(null)
        else -> NativeAdSlotState.Retained(null)
    }
}
