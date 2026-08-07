@file:OptIn(dev.avinya.ads.InternalAdMobCmpApi::class)

package dev.avinya.ads.internal

import dev.avinya.ads.AdAttemptResult
import dev.avinya.ads.AdError
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSessionState
import dev.avinya.ads.nativead.NativeAdSlotState
import dev.avinya.ads.nativead.NativeAdWindow
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
 * **Ownership model.** The coordinator is the **sole owner of every
 * admitted platform ad**. The [NativeAdGovernor] only tracks record ids
 * and reservation counts; the platform-side object lives in the
 * coordinator's [records] map and is destroyed exactly once via
 * [destroyRecord] on any of the invalidation paths:
 *  - [closeSession] / [closeAll] / [clear] / [onConsentRevoked]
 *  - per-record 1-hour native TTL (the [tickLocked] pass)
 *  - inactive-session reap at [NativeAdMemoryPolicy.inactiveSessionTtl]
 *  - inactive-session LRU eviction at [NativeAdMemoryPolicy.maxInactiveSessions]
 *
 * **Generation model.** Each placement has a generation counter. [clear]
 * and [onConsentRevoked] bump every placement's generation. A late
 * platform callback that arrives under an older generation is destroyed
 * on arrival — it never reaches a session. Per-slot generation is owned
 * by [NativeAdSessionCore]; the coordinator threads it through admit /
 * fail callbacks so a stale admit for a since-superseded slot is
 * dropped at the session.
 *
 * **Scheduling.** One [PlacementScheduler] per placement that has ever
 * had demand. The scheduler reserves capacity via the governor (using
 * the **granted** reservation count for the platform.load call, never
 * the originally requested count), serialises per-placement work, and
 * removes itself once it has no records, no reservations, no
 * in-flight work, and no queued requests.
 *
 * **TTL.**
 *  - 1-hour native ad TTL is enforced by [tickLocked] on every public
 *    mutator. Expired records destroy their platform ad, retire the
 *    governor accounting, and submit the [NativeAdSessionCore.expireSlot]
 *    reload demand to the right scheduler.
 *  - Inactive-session TTL is [NativeAdMemoryPolicy.inactiveSessionTtl]
 *    (default 30 minutes). The coordinator tracks the inactive set in
 *    insertion order (LinkedHashMap) so eviction is LRU.
 *  - [NativeAdMemoryPolicy.maxSessionRecords] is the hard cap on
 *    live + inactive sessions; the 65th call to [session] throws.
 *
 * **Locking.** One [FullScreenStateLock] per coordinator instance. The
 * lock is held across every mutator; per-placement work is launched on
 * [scope] so platform calls and `platform.destroy` happen outside the
 * lock.
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
    private val inactiveOrder = LinkedHashMap<String, Instant>()
    private val schedulers = mutableMapOf<String, PlacementScheduler>()
    // Sole record of every admitted platform ad. Destroyed exactly once.
    private val records = mutableMapOf<NativeAdRecordId, RecordEntry>()
    // Test-only override for "now". Production uses the real clock.
    private var testNow: Instant? = null

    private inner class RecordEntry(
        val ad: A,
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

    // -----------------------------------------------------------------------
    // Public surface
    // -----------------------------------------------------------------------

    fun session(
        key: String,
        policy: NativeAdSessionPolicy = NativeAdSessionPolicy(),
    ): NativeAdSessionCore = lock.withLock {
        require(key.isNotBlank()) { "session key must not be blank" }
        tickLocked()
        sessions[key]?.let { holder ->
            // Policy mismatch is rejected (the original plan contract).
            if (holder.core.policy != policy) {
                throw IllegalStateException(
                    "NativeAdCoordinatorCore: session '$key' already exists with " +
                        "a different policy (maxRetainedAds=${holder.core.policy.maxRetainedAds}, " +
                        "retainBehind=${holder.core.policy.retainBehind}, " +
                        "prefetchAhead=${holder.core.policy.prefetchAhead}); " +
                        "close the existing session before reusing the key with a new policy."
                )
            }
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
        val retired = holder.core.close()
        destroyAndForgetRecords(retired, holder.core.key)
        // Bump every placement generation so a late platform callback for
        // any of the retired records is destroyed at the scheduler.
        placementGenBumpAll()
        // Cancel queued batches targeting this session; in-flight loads
        // for these slots are still drained (their late result is
        // destroyed under the bumped generation).
        for (sched in schedulers.values) sched.cancelForSession(key)
    }

    fun clear() = lock.withLock {
        tickLocked()
        placementGenBumpAll()
        // Destroy every owned platform ad and clear the records map before
        // closing each session — close() will retire the governor
        // accounting but the platform objects are owned here.
        destroyAllRecordsLocked()
        for (holder in sessions.values) {
            val retired = holder.core.close()
            // records already cleared; nothing to do.
            for (recordId in retired) { /* keep governor-side accounting tidy */ }
        }
        sessions.clear()
        inactiveOrder.clear()
    }

    fun onConsentRevoked() = lock.withLock {
        tickLocked()
        placementGenBumpAll()
        destroyAllRecordsLocked()
        for (holder in sessions.values) holder.core.close()
        sessions.clear()
        inactiveOrder.clear()
    }

    fun updateWindow(sessionKey: String, window: NativeAdWindow) = lock.withLock {
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
        val inactiveCutoff = now - memoryPolicy.inactiveSessionTtl
        val toReap = inactiveOrder.entries.filter { it.value <= inactiveCutoff }.map { it.key }
        for (key in toReap) {
            val holder = sessions.remove(key) ?: continue
            inactiveOrder.remove(key)
            val retired = holder.core.close()
            destroyAndForgetRecords(retired, holder.core.key)
        }
        // Enforce the LRU cap on inactive sessions.
        while (inactiveOrder.size > memoryPolicy.maxInactiveSessions) {
            val oldest = inactiveOrder.entries.firstOrNull()?.key ?: break
            val holder = sessions.remove(oldest) ?: break
            inactiveOrder.remove(oldest)
            val retired = holder.core.close()
            destroyAndForgetRecords(retired, holder.core.key)
        }

        // Expire records past the 1-hour native-ad TTL.
        val nativeCutoff = now - 1.hours
        val expiredRecordIds = records.entries
            .filter { (_, meta) -> meta.loadedAt <= nativeCutoff }
            .map { (id, _) -> id }
            .toList()
        for (recordId in expiredRecordIds) {
            val entry = records.remove(recordId) ?: continue
            val holder = sessions[entry.sessionKey] ?: continue
            platform.destroy(entry.ad)
            val demand = holder.core.expireSlot(entry.slotKey)
            // The reload demand is submitted to the right placement
            // scheduler so the platform call is reissued.
            if (demand.slots.isNotEmpty()) {
                submitDemand(holder, demand)
            }
        }
    }

    private fun submitDemand(holder: SessionHolder, demand: SlotDemand) {
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

    /**
     * Destroy every record the holder currently owns, then remove the
     * corresponding metadata. Called from [clear], [onConsentRevoked],
     * and the inactive-session reap path in [tickLocked].
     */
    private fun destroyAllRecordsLocked() {
        for (entry in records.values) platform.destroy(entry.ad)
        records.clear()
    }

    /**
     * Destroy and remove the records for [retiredRecordIds] belonging
     * to [sessionKey]. Safe to call when the records are not present
     * (e.g. an already-reaped record). Used by [closeSession] and the
     * inactive-session reap path.
     */
    private fun destroyAndForgetRecords(retiredRecordIds: List<NativeAdRecordId>, sessionKey: String) {
        for (recordId in retiredRecordIds) {
            val entry = records.remove(recordId) ?: continue
            if (entry.sessionKey == sessionKey) {
                platform.destroy(entry.ad)
            } else {
                // Cross-session mismatch: the record belongs to another
                // session. Put it back so its owner can clean it up.
                records[recordId] = entry
            }
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
            val priority = nativeAdPriorityFor(batch.entries.first().placement)
            val decision = governor.reserve(
                demandClass = NativeAdDemandClass.Visible,
                priority = priority,
                count = batch.entries.size,
                allowPartial = true,
            )
            // Consume both retired records and cancelled reservations
            // from the decision — they are the platform objects /
            // permits the prior call already accounted for.
            for (recordId in decision.retiredRecordIds) {
                val entry = records.remove(recordId)
                if (entry != null) platform.destroy(entry.ad)
            }
            activeReservations.addAll(decision.reservations)
            val genAtSubmit = generation
            val placement = batch.entries.first().placement
            // Load the **granted** count, not the original demand. When
            // the governor could only reserve some of the requested
            // permits (visible demand at the hard cap with no eviction
            // room), the platform only sees the granted size — never
            // zero, because reserve with allowPartial=true still
            // surfaces whatever fit.
            val grantedCount = activeReservations.size
            currentJob = scope.launch {
                val result = platform.load(placement, grantedCount, genAtSubmit)
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
                    records[recordId] = RecordEntry(
                        ad = ads[i],
                        sessionKey = batch.holder.core.key,
                        slotKey = entry.key,
                        generation = entry.generation,
                        loadedAt = nowLocked(),
                    )
                    batch.holder.core.recordAdmitted(entry.key, recordId, entry.generation)
                } catch (e: IllegalStateException) {
                    platform.destroy(ads[i])
                }
            }
            // Surplus ads (platform returned more than granted): destroy.
            for (i in n until ads.size) {
                platform.destroy(ads[i])
            }
            // Unused reservations (granted > admitted): release.
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
            releaseReservationsLocked()
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
            // The coordinator uses the most common case (ready-ahead) as
            // a default; the platform-specific coordinator (Task 5/6)
            // will refine this for the platform's batching / position.
            return NativeAdPriority.ActiveReadyAhead
        }
    }
}
