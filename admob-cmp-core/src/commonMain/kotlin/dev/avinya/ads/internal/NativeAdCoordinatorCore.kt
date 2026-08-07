@file:OptIn(dev.avinya.ads.InternalAdMobCmpApi::class)

package dev.avinya.ads.internal

import dev.avinya.ads.AdAttemptResult
import dev.avinya.ads.AdError
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant


/**
 * Process-wide coordinator that drives native-ad loads across every active
 * session, owns placement-level load scheduling, and bridges the
 * platform-specific [NativeAdPlatform] to the public
 * [dev.avinya.ads.nativead.NativeAdManager] / [dev.avinya.ads.nativead.NativeAdSession] surface.
 *
 * Architecture:
 * - The coordinator owns a [NativeAdGovernor] (created from the same
 *   [NativeAdMemoryPolicy] every session uses) and one
 *   [PlacementScheduler] per placement that has ever had demand.
 * - Every [session] / [updateWindow] / [setMounted] / [closeSession] /
 *   [clear] / [onConsentRevoked] call routes through the coordinator's
 *   per-instance lock; per-placement scheduling and per-session slot
 *   transitions are atomic from the caller's perspective.
 * - Platform load work is launched on [scope]. The coordinator never
 *   blocks on a platform call.
 *
 * Generation model:
 * - Each placement has a generation counter. [clear] and
 *   [onConsentRevoked] bump every placement's generation. Late
 *   platform callbacks that arrive under an older generation are
 *   destroyed on arrival — they never reach a session.
 * - Per-slot generation is owned by [NativeAdSessionCore]; the
 *   coordinator threads it through admit/fail callbacks so a stale
 *   admit for a since-superseded slot is dropped at the session.
 *
 * TTL:
 * - Native ad TTL is 1 hour. On every public mutator the coordinator
 *   walks the record timestamp map and expires records past the TTL.
 * - Inactive session TTL is [NativeAdMemoryPolicy.inactiveSessionTtl]
 *   (default 30 minutes). The coordinator tracks the inactive set in
 *   insertion order (LinkedHashMap) so eviction is LRU.
 * - [NativeAdMemoryPolicy.maxSessionRecords] is the hard cap on live
 *   + inactive sessions; the 65th call to [session] throws.
 *
 * Idle scheduler cleanup: a [PlacementScheduler] removes itself from
 * the coordinator once it has no records, no reservations, no
 * in-flight load, and no queued requests.
 */
internal class NativeAdCoordinatorCore<A : Any>(
    private val memoryPolicy: NativeAdMemoryPolicy,
    private val platform: NativeAdPlatform<A>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Instant = { Clock.System.now() },
) {
    private val lock = FullScreenStateLock()
    private val governor = NativeAdGovernor(memoryPolicy)
    private val sessions = mutableMapOf<String, SessionHolder>()
    // Inactive session order, oldest first. LinkedHashMap preserves insertion
    // order so iteration gives LRU eviction order.
    private val inactiveOrder = LinkedHashMap<String, Instant>()
    private val schedulers = mutableMapOf<String, PlacementScheduler>()
    // recordId -> (sessionKey, slotKey, generation, loadedAt)
    private val records = mutableMapOf<NativeAdRecordId, RecordMeta>()
    // Test-only override for "now". Production uses the real clock.
    private var testNow: Instant? = null

    private data class RecordMeta(
        val sessionKey: String,
        val slotKey: String,
        val generation: Long,
        val loadedAt: Instant,
    )

    private inner class SessionHolder(
        val core: NativeAdSessionCore,
        var lastActive: Instant,
        var active: Boolean = true,
    )

    init {
        // Validate policy up-front. The constructor fails if the policy
        // is inconsistent (e.g. softLimit > hardLimit).
        memoryPolicy.toString() // touch the field; explicit construction is the validation
    }

    // -----------------------------------------------------------------------
    // Public surface
    // -----------------------------------------------------------------------

    /**
     * Returns the session for [key], creating it on first access. The
     * [policy] is honoured only on creation; re-using a key returns the
     * existing session.
     *
     * @throws IllegalStateException if the session registry already holds
     *   [NativeAdMemoryPolicy.maxSessionRecords] live sessions.
     */
    fun session(
        key: String,
        policy: NativeAdSessionPolicy = NativeAdSessionPolicy(),
    ): NativeAdSessionCore = lock.withLock {
        tickLocked()
        sessions[key]?.let { holder ->
            holder.lastActive = nowLocked()
            if (!holder.active) {
                holder.active = true
                inactiveOrder.remove(key)
            }
            return@withLock holder.core
        }
        if (sessions.size >= memoryPolicy.maxSessionRecords) {
            throw IllegalStateException(
                "NativeAdCoordinatorCore: maxSessionRecords (${memoryPolicy.maxSessionRecords}) " +
                    "reached; cannot create session '$key'."
            )
        }
        val holder = SessionHolder(
            core = NativeAdSessionCore(key, policy, memoryPolicy, governor),
            lastActive = nowLocked(),
        )
        sessions[key] = holder
        holder.core
    }

    fun closeSession(key: String) = lock.withLock {
        tickLocked()
        val holder = sessions.remove(key) ?: return@withLock
        inactiveOrder.remove(key)
        // Retiring the session closes the underlying slot map and retires
        // every record; we still need to drop the in-flight loads for the
        // retired records.
        val retired = holder.core.close()
        for (recordId in retired) records.remove(recordId)
        // Bump the placement generations for any placements that the
        // session had live records for, so a late platform callback for
        // any of those records is destroyed.
        for (recordId in retired) {
            val meta = records.remove(recordId) ?: continue
            // Bump the placement generation for the slot's placement.
            // The slot key is not tied to a single placement, but the
            // session's slot map still maps to placements — for now we
            // bump all placements whose current generation could be
            // affected. A more precise accounting (per-slot placement
            // tracking) is left for the platform-specific coordinator.
            placementGenBumpAll()
            break
        }
        // Cancel any in-flight placement work targeting this session's keys.
        for (sched in schedulers.values) sched.cancelForSession(key)
    }

    fun clear() = lock.withLock {
        tickLocked()
        // Bump placement generations so any in-flight load that completes
        // after this call is recognized as stale and destroyed at the
        // scheduler. We deliberately do NOT cancel the in-flight coroutines
        // — their results still need to be drained so the platform
        // can clean up its own state (destroyed ads, etc.).
        placementGenBumpAll()
        for (holder in sessions.values) {
            val retired = holder.core.close()
            for (recordId in retired) records.remove(recordId)
        }
        sessions.clear()
        inactiveOrder.clear()
        records.clear()
    }

    fun onConsentRevoked() = lock.withLock {
        tickLocked()
        placementGenBumpAll()
        for (holder in sessions.values) {
            val retired = holder.core.close()
            for (recordId in retired) records.remove(recordId)
        }
        sessions.clear()
        inactiveOrder.clear()
        records.clear()
    }

    /**
     * Routes a viewport update from Compose into the session, then
     * forwards the resulting slot demand to the right per-placement
     * schedulers.
     */
    fun updateWindow(sessionKey: String, window: dev.avinya.ads.nativead.NativeAdWindow) = lock.withLock {
        tickLocked()
        val holder = sessions[sessionKey] ?: return@withLock
        holder.lastActive = nowLocked()
        if (!holder.active) {
            holder.active = true
            inactiveOrder.remove(sessionKey)
        }
        val demand = holder.core.updateWindow(window)
        submitDemand(holder, demand)
    }

    fun setMounted(sessionKey: String, slotKey: String, mounted: Boolean) = lock.withLock {
        tickLocked()
        sessions[sessionKey]?.let { holder ->
            holder.core.setMounted(slotKey, mounted)
            holder.lastActive = nowLocked()
        }
    }

    fun schedulerCount(): Int = lock.withLock { schedulers.size }

    /**
     * Test-only: advance the coordinator's internal clock by [duration] and
     * run the TTL + inactive-session reaper. Production code never calls
     * this; it exists so race tests can fast-forward without `runBlocking`.
     */
    fun tickForTest(duration: Duration) = lock.withLock {
        testNow = nowLocked() + duration
        tickLocked()
    }

    // -----------------------------------------------------------------------
    // Internal helpers (must be called under `lock`)
    // -----------------------------------------------------------------------

    private fun nowLocked(): Instant = testNow ?: clock()

    private fun tickLocked() {
        val now = nowLocked()

        // Reap inactive sessions past the TTL.
        val cutoff = now - memoryPolicy.inactiveSessionTtl
        val toReap = inactiveOrder.entries.filter { it.value <= cutoff }.map { it.key }
        for (key in toReap) {
            val holder = sessions.remove(key) ?: continue
            inactiveOrder.remove(key)
            val retired = holder.core.close()
            for (recordId in retired) records.remove(recordId)
        }
        // Enforce the LRU cap on inactive sessions.
        while (inactiveOrder.size > memoryPolicy.maxInactiveSessions) {
            val oldest = inactiveOrder.entries.firstOrNull()?.key ?: break
            val holder = sessions.remove(oldest) ?: break
            inactiveOrder.remove(oldest)
            val retired = holder.core.close()
            for (recordId in retired) records.remove(recordId)
        }

        // Expire records past the 1-hour native-ad TTL.
        val nativeCutoff = now - 1.hours
        val expiredRecordIds = records.entries
            .filter { (_, meta) -> meta.loadedAt <= nativeCutoff }
            .map { (id, _) -> id }
            .toList()
        for (recordId in expiredRecordIds) {
            val meta = records.remove(recordId) ?: continue
            val holder = sessions[meta.sessionKey] ?: continue
            holder.core.expireSlot(meta.slotKey)
            // expireSlot retires the record with the governor; nothing else
            // to do for the placement scheduler.
        }
    }

    private fun submitDemand(holder: SessionHolder, demand: SlotDemand) {
        // Group entries by placement so each per-placement scheduler sees a
        // single batch.
        if (demand.slots.isEmpty()) return
        val byPlacement = demand.slots.groupBy { it.placement.id }
        for ((placementId, entries) in byPlacement) {
            val scheduler = schedulers.getOrPut(placementId) { PlacementScheduler(placementId) }
            scheduler.submit(holder, entries)
        }
    }

    private fun placementGenBumpAll() {
        if (schedulers.isEmpty()) return
        for (placementId in schedulers.keys.toList()) {
            schedulers[placementId]?.bumpGeneration()
        }
    }

    private inner class PlacementScheduler(val placementId: String) {
        private val lock = FullScreenStateLock()
        private val queue = mutableListOf<Batch>()
        private var currentJob: Job? = null
        private val activeRecordIds = mutableSetOf<NativeAdRecordId>()
        private val activeReservations = mutableListOf<NativeAdLoadReservation>()
        private var generation: Long = 0L

        private inner class Batch(val holder: SessionHolder, val entries: List<SlotDemandEntry>)

        fun submit(holder: SessionHolder, entries: List<SlotDemandEntry>) = lock.withLock {
            queue.add(Batch(holder, entries))
            if (currentJob == null) startNextLocked()
        }

        fun cancel() = lock.withLock {
            currentJob?.cancel()
            currentJob = null
            queue.clear()
            releaseReservationsLocked()
            // We do not touch activeRecordIds here — close()/clear() on
            // the coordinator retires them through the session.
        }

        fun cancelForSession(sessionKey: String) = lock.withLock {
            val it = queue.iterator()
            while (it.hasNext()) {
                if (it.next().holder.core.key == sessionKey) it.remove()
            }
            // We do not cancel an in-flight load here: the late result is
            // still subject to the generation check.
            processNextOrCleanupLocked()
        }

        fun bumpGeneration() = lock.withLock {
            generation++
        }

        private fun startNextLocked() {
            val batch = queue.removeAt(0)
            val count = batch.entries.size
            val priority = nativeAdPriorityFor(batch.entries.first().placement)
            val decision = governor.reserve(
                demandClass = NativeAdDemandClass.Visible,
                priority = priority,
                count = count,
                allowPartial = true,
            )
            activeReservations.addAll(decision.reservations)
            val genAtSubmit = generation
            val placement = batch.entries.first().placement
            currentJob = scope.launch {
                val result = platform.load(placement, count, genAtSubmit)
                handleResult(batch, genAtSubmit, result)
            }
        }

        private fun handleResult(
            batch: Batch,
            submittedGen: Long,
            result: AdAttemptResult<List<A>>,
        ) = lock.withLock {
            currentJob = null
            val currentGen = generation
            val stale = submittedGen != currentGen
            if (stale) {
                // Stale: drop reservations and destroy any returned ads.
                if (result is AdAttemptResult.Success) {
                    result.value.forEach { platform.destroy(it) }
                }
                releaseReservationsLocked()
                processNextOrCleanupLocked()
                return@withLock
            }
            when (result) {
                is AdAttemptResult.Success -> handleSuccess(batch, result.value)
                is AdAttemptResult.Failure -> handleFailure(batch, result.error)
            }
            processNextOrCleanupLocked()
        }

        private fun handleSuccess(batch: Batch, ads: List<A>) {
            val n = minOf(ads.size, batch.entries.size)
            for (i in 0 until n) {
                val reservation = activeReservations[i]
                val entry = batch.entries[i]
                try {
                    val recordId = governor.admit(reservation)
                    activeRecordIds.add(recordId)
                    records[recordId] = RecordMeta(
                        sessionKey = batch.holder.core.key,
                        slotKey = entry.key,
                        generation = entry.generation,
                        loadedAt = nowLocked(),
                    )
                    batch.holder.core.recordAdmitted(entry.key, recordId, entry.generation)
                } catch (e: IllegalStateException) {
                    // Reservation already resolved or stale; destroy the
                    // returned ad so it does not leak.
                    platform.destroy(ads[i])
                }
            }
            // Surplus ads (platform returned more than the demand) are destroyed.
            for (i in n until ads.size) {
                platform.destroy(ads[i])
            }
            // Unused reservations (partial fill) are released.
            for (i in n until activeReservations.size) {
                try {
                    governor.releaseReservation(activeReservations[i])
                } catch (_: IllegalStateException) {
                    // Already gone; ignore.
                }
            }
            activeReservations.clear()
        }

        private fun handleFailure(batch: Batch, error: AdError) {
            // Release every reservation for the failed batch.
            releaseReservationsLocked()
            // Mark every requested slot as Failed.
            for (entry in batch.entries) {
                batch.holder.core.recordFailed(entry.key, error, entry.generation)
            }
        }

        private fun releaseReservationsLocked() {
            for (res in activeReservations) {
                try {
                    governor.releaseReservation(res)
                } catch (_: IllegalStateException) {
                    // Already gone.
                }
            }
            activeReservations.clear()
        }

        private fun processNextOrCleanupLocked() {
            if (queue.isNotEmpty()) {
                startNextLocked()
            } else if (activeRecordIds.isEmpty() && activeReservations.isEmpty() && currentJob == null) {
                schedulers.remove(placementId)
            }
        }

        private fun nativeAdPriorityFor(placement: AdPlacement): NativeAdPriority {
            // The coordinator uses the most common case (ready-ahead) as a
            // default; the platform-specific coordinator (Task 5/6) will
            // refine this for the platform's batching / position. The
            // record-eviction priority is set by the session core as the
            // slot moves between bands.
            return NativeAdPriority.ActiveReadyAhead
        }
    }
}
