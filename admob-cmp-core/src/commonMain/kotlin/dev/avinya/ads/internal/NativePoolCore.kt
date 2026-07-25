package dev.avinya.ads.internal

import dev.avinya.ads.AdAttemptResult
import dev.avinya.ads.AdError
import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdLoadState
import dev.avinya.ads.AdLogger
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdRequestOptions
import dev.avinya.ads.AdResponseInfo
import dev.avinya.ads.isRetryableLoadFailure
import dev.avinya.ads.nativead.NativeAdOptions
import dev.avinya.ads.nativead.NativeAdToken
import dev.avinya.ads.nativead.NativeMediaInfo
import dev.avinya.ads.retryAdLoad
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Platform primitives the shared native pool needs. Everything SDK-shaped lives here;
 * everything policy-shaped lives in [NativePoolCore].
 *
 * [A] is the platform's ad handle. Android binds `NativeAd`. iOS binds `LoadedNativeAd`,
 * which carries the strong delegate references the ad needs to outlive it — see CLAUDE.md
 * invariant #4. The core never sees, retains, or reasons about delegates: keeping them
 * inside [A] is what makes that invariant structurally impossible to violate from
 * commonMain.
 */
internal interface NativePoolPlatform<A : Any> {
    /** Deterministic teardown of one ad. Android calls `destroy()`; iOS nulls the ObjC links. */
    fun destroy(ad: A)

    fun responseInfo(ad: A): AdResponseInfo?

    fun mediaInfo(ad: A): NativeMediaInfo?

    /**
     * Runs one batch load of exactly [count] ads and returns the assembled result.
     *
     * The platform owns ALL batch-assembly synchronization (CLAUDE.md invariant #3):
     * the `pending` list, the `cancelled` flag, the multi-ad per-failure protocol, and
     * delegate retention. The core only supplies [count] and consumes the result.
     *
     * [requiredGeneration] is the generation the core captured before queueing this load.
     * Platforms that register in-flight work in their own retention registry (iOS's
     * `activeLoads`) must check it against [NativePoolCore.isCurrentGeneration] before
     * handing anything to the SDK. It is passed explicitly rather than re-read from the
     * core because a `clear()` between the core's read and the platform's would otherwise
     * be invisible — hiding generation from the platform that most needs it is how the
     * Android/iOS drift behind P1-2 happened.
     */
    suspend fun loadBatch(
        count: Int,
        requestOptions: AdRequestOptions,
        nativeOptions: NativeAdOptions,
        requiredGeneration: Long
    ): AdAttemptResult<List<A>>

    /**
     * Runs [block] under the pool's state lock.
     *
     * MUST be reentrant. Android satisfies this with `synchronized` (JVM monitors are
     * reentrant); iOS with `NSRecursiveLock`. Do NOT substitute a plain `NSLock` on iOS —
     * it will deadlock.
     */
    fun <T> withPoolLock(block: () -> T): T
}

/**
 * Shared native-ad pool state machine. Owns generation, cache accounting, TTL eviction,
 * token minting, load-state publication and the availability signal.
 *
 * Extracted from the two hand-written pools, which had drifted: iOS had a generation
 * counter and Android did not, so an Android batch completing after `clear()` repopulated
 * the pool the caller had just emptied (finding P1-2).
 *
 * **Clear contract:** `clear()` drains and destroys *available* inventory only. Ads that
 * are currently leased (acquired but not yet released) survive, because a live view owns
 * them and is still rendering them; they are torn down by [release]. See finding P1-7.
 */
@OptIn(ExperimentalTime::class)
internal class NativePoolCore<A : Any>(
    val placement: AdPlacement,
    private val platform: NativePoolPlatform<A>,
    private val globalEvents: MutableSharedFlow<AdEvent>,
    private val now: () -> Instant = { Clock.System.now() }
) {
    private val loadMutex = Mutex()
    private val ads = ArrayDeque<Entry<A>>()
    private val inUse = mutableMapOf<String, Entry<A>>()
    private var nextToken = 0
    private var generation = 0L
    private var lastResponseInfo: AdResponseInfo? = null

    private val _loadState = MutableStateFlow<AdLoadState>(AdLoadState.Idle)
    private val _events = MutableSharedFlow<AdEvent>(extraBufferCapacity = 32)
    private val _availableAds = MutableStateFlow(0)

    val loadState: StateFlow<AdLoadState> = _loadState
    val events: SharedFlow<AdEvent> = _events
    val availableAds: StateFlow<Int> = _availableAds

    suspend fun preload(
        count: Int,
        requestOptions: AdRequestOptions,
        nativeOptions: NativeAdOptions,
        blockedError: () -> AdError? = { null }
    ): AdLoadState {
        // Captured BEFORE queueing on loadMutex: a clear() while this call waits must
        // invalidate it rather than let it repopulate the cleared pool.
        val requiredGeneration = platform.withPoolLock { generation }
        return loadMutex.withLock {
            blockedError()?.let { return@withLock failIfCurrent(requiredGeneration, it) }

            val target = count.coerceAtLeast(1).coerceAtMost(placement.cachePolicy.maxSize)
            val plan = platform.withPoolLock {
                if (generation != requiredGeneration) null else {
                    val retired = evictExpiredLocked()
                    val loadCount = (target - ads.size - inUse.size).coerceAtLeast(0)
                    if (loadCount > 0) _loadState.value = AdLoadState.Loading
                    LoadPlan(loadCount, retired)
                }
            } ?: return@withLock _loadState.value

            plan.retired.forEach { platform.destroy(it.ad) }
            if (plan.loadCount == 0) {
                AdLogger.d("Native preload skipped; cache already satisfies request. placement=${placement.id}")
                return@withLock _loadState.value
            }

            AdLogger.d("Native preload requested. placement=${placement.id} count=${plan.loadCount}")
            try {
                // Bounds the WHOLE attempt sequence including retry backoff, not each
                // attempt: a listener that never calls back would otherwise restart the
                // clock on every retry and still never finish.
                val result = withTimeoutOrNull(placement.timeoutPolicy.loadTimeout) {
                    retryAdLoad(placement.retryPolicy, { it.isRetryableLoadFailure() }) {
                        if (!isCurrentGeneration(requiredGeneration)) {
                            AdAttemptResult.Failure(AdError.message("Native ad preload was cleared."))
                        } else {
                            platform.loadBatch(plan.loadCount, requestOptions, nativeOptions, requiredGeneration)
                        }
                    }
                } ?: AdAttemptResult.Failure(
                    AdError.message(
                        "Native ad load timed out after ${placement.timeoutPolicy.loadTimeout}. " +
                            "The SDK accepted the request but never reported a result."
                    )
                )
                when (result) {
                    is AdAttemptResult.Success -> admit(requiredGeneration, result.value)
                    // failOrRestore (not failIfCurrent): a returned Failure — including a
                    // timed-out top-up — must not erase usable cached inventory, exactly
                    // like the unexpected-throwable path below.
                    is AdAttemptResult.Failure -> failOrRestore(requiredGeneration, result.error)
                }
            } catch (e: CancellationException) {
                onCancelled(requiredGeneration)
                throw e
            } catch (t: Throwable) {
                // P1-1: only cancellation was handled, so a throw from a beta SDK call, a
                // mapper or a response-info accessor escaped with the state stuck at Loading
                // forever — every later preload then coalesced onto a state with no live
                // operation behind it. Publish a terminal state before rethrowing, derived
                // from what the pool actually holds so a failed top-up does not erase usable
                // cached inventory.
                failOrRestore(
                    requiredGeneration,
                    AdError.message(t.message ?: "Native ad load failed unexpectedly.")
                )
                throw t
            }
        }
    }

    private fun admit(requiredGeneration: Long, loaded: List<A>): AdLoadState {
        if (loaded.isEmpty()) {
            return failIfCurrent(requiredGeneration, AdError.message("No native ads returned."))
        }
        val info = platform.responseInfo(loaded.first())
        val rejected = mutableListOf<A>()
        val published = platform.withPoolLock {
            if (generation != requiredGeneration) {
                rejected += loaded
                false
            } else {
                loaded.forEach { ad ->
                    if (ads.size + inUse.size < placement.cachePolicy.maxSize) {
                        ads += Entry(NativeAdToken(placement.id, "${placement.id}-${nextToken++}"), ad, now())
                    } else {
                        // Native ads hold resources; destroy overflow instead of leaking it.
                        rejected += ad
                    }
                }
                lastResponseInfo = info
                _loadState.value = AdLoadState.Loaded(info)
                publishAvailabilityLocked()
                true
            }
        }
        rejected.forEach { platform.destroy(it) }
        if (published) {
            AdLogger.i("Native preload cached ads. placement=${placement.id} loaded=${loaded.size}")
            emit(AdEvent.Loaded(placement.id, info))
        } else {
            AdLogger.i("Native batch rejected; pool was cleared mid-load. placement=${placement.id}")
        }
        return _loadState.value
    }

    fun acquire(): NativeAdToken? {
        val result = platform.withPoolLock {
            val retired = evictExpiredLocked()
            val entry = ads.removeFirstOrNull()
            if (entry != null) inUse[entry.token.tokenId] = entry
            publishAvailabilityLocked()
            AcquireResult(entry?.token, retired)
        }
        result.retired.forEach { platform.destroy(it.ad) }
        if (result.token == null) {
            AdLogger.w("Native acquire returned null. placement=${placement.id}")
        } else {
            AdLogger.d("Native acquired. placement=${placement.id} token=${result.token.tokenId}")
        }
        return result.token
    }

    fun peek(token: NativeAdToken): A? = platform.withPoolLock { inUse[token.tokenId]?.ad }

    fun release(token: NativeAdToken) {
        val removed = platform.withPoolLock {
            inUse.remove(token.tokenId).also { publishAvailabilityLocked() }
        }
        AdLogger.d("Native release. placement=${placement.id} token=${token.tokenId} found=${removed != null}")
        removed?.let { platform.destroy(it.ad) }
    }

    fun availableCount(): Int {
        val result = platform.withPoolLock {
            val retired = evictExpiredLocked()
            publishAvailabilityLocked()
            AvailableResult(ads.size, retired)
        }
        result.retired.forEach { platform.destroy(it.ad) }
        return result.count
    }

    fun mediaInfo(token: NativeAdToken): NativeMediaInfo? {
        // Resolve the handle under the lock, then call out. Do NOT call platform.mediaInfo
        // while holding the lock: on iOS it touches GMA objects and the lock is also taken
        // from GMA callback threads.
        val ad = platform.withPoolLock { inUse[token.tokenId]?.ad } ?: return null
        return platform.mediaInfo(ad)
    }

    fun clear() {
        AdLogger.i("Native clear. placement=${placement.id}")
        val retired = platform.withPoolLock {
            generation++
            val drained = ads.toList()
            ads.clear()
            lastResponseInfo = null
            // inUse is deliberately NOT touched — see this class's clear contract and
            // finding P1-7. Leased ads stay live until release(token).
            _loadState.value = AdLoadState.Idle
            publishAvailabilityLocked()
            drained
        }
        retired.forEach { platform.destroy(it.ad) }
    }

    /** Current generation, for platforms that must reject late SDK callbacks themselves. */
    fun currentGeneration(): Long = platform.withPoolLock { generation }

    fun isCurrentGeneration(requiredGeneration: Long): Boolean =
        platform.withPoolLock { generation == requiredGeneration }

    private fun onCancelled(requiredGeneration: Long) {
        platform.withPoolLock {
            if (generation != requiredGeneration) return@withPoolLock
            // Derive from what the pool actually holds. Publishing Idle unconditionally
            // (what Android did) erased the fact that usable inventory remained — P1-3.
            _loadState.value = if (ads.isNotEmpty()) {
                AdLoadState.Loaded(lastResponseInfo)
            } else {
                AdLoadState.Idle
            }
        }
    }

    /**
     * Terminal state for an unexpected (non-cancellation) failure: keep reporting [Loaded]
     * when the pool still holds usable inventory, otherwise publish [AdLoadState.Failed].
     *
     * A top-up preload that blows up must not erase the fact that earlier ads are still
     * cached and servable — that is the same inventory-blindness P1-3 fixed for cancellation.
     */
    private fun failOrRestore(requiredGeneration: Long, error: AdError): AdLoadState {
        val hasInventory = platform.withPoolLock { generation == requiredGeneration && ads.isNotEmpty() }
        return if (hasInventory) {
            platform.withPoolLock { _loadState.value = AdLoadState.Loaded(lastResponseInfo) }
            _loadState.value
        } else {
            failIfCurrent(requiredGeneration, error)
        }
    }

    private fun failIfCurrent(requiredGeneration: Long, error: AdError): AdLoadState {
        val published = platform.withPoolLock {
            if (generation != requiredGeneration) false else {
                _loadState.value = AdLoadState.Failed(error)
                true
            }
        }
        if (published) emit(AdEvent.LoadFailed(placement.id, error))
        return _loadState.value
    }

    /** Caller must hold the pool lock. */
    private fun evictExpiredLocked(): List<Entry<A>> {
        val ttl = placement.cachePolicy.expirationPolicy.nativeTtl
        val instant = now()
        val retired = mutableListOf<Entry<A>>()
        // ArrayDeque is oldest-first, so stop at the first fresh entry.
        while (ads.isNotEmpty() && instant - ads.first().loadedAt >= ttl) {
            AdLogger.i("Native cache entry expired. placement=${placement.id}")
            retired += ads.removeFirst()
        }
        if (retired.isNotEmpty()) publishAvailabilityLocked()
        return retired
    }

    /** Caller must hold the pool lock. */
    private fun publishAvailabilityLocked() {
        _availableAds.value = ads.size
    }

    /**
     * Publishes an event originating from a platform SDK callback (impression, click, paid).
     *
     * The core owns the event flows, but only the platform sees these callbacks — they are
     * installed on the ad handle at load time and fire for the ad's whole lifetime. Routing
     * them through here is what keeps a single `events` flow per pool.
     */
    fun emitPlatformEvent(event: AdEvent) = emit(event)

    /**
     * Publishes an event scoped to one specific ad instance, resolving its
     * [NativeAdToken.tokenId] at the moment the event actually fires — NOT at delegate/callback
     * INSTALL time, because a native ad's token doesn't exist yet when its SDK callbacks are
     * wired up (that happens during load, before [admit] has minted a token). [matchesAd] lets
     * each platform identify "its" entry using whatever raw SDK reference it holds at the
     * callback site (see the two platform pools for the exact match key each uses).
     *
     * Resolution runs under the pool lock so it can't race a concurrent admit()/acquire()/
     * release(). Returns null (the event still publishes, just without an id) if the ad's entry
     * is no longer tracked — e.g. the callback fired after release() destroyed it. That is not
     * an error: see the class doc on lease survival across clear().
     */
    fun emitInstanceScopedEvent(matchesAd: (A) -> Boolean, eventFactory: (adInstanceId: String?) -> AdEvent) {
        val instanceId = platform.withPoolLock {
            (ads.asSequence() + inUse.values.asSequence()).firstOrNull { matchesAd(it.ad) }?.token?.tokenId
        }
        emit(eventFactory(instanceId))
    }

    private fun emit(event: AdEvent) {
        _events.emitOrLogDrop(event, "NativePoolCore(${placement.id})")
        globalEvents.emitOrLogDrop(event, "NativePoolCore(${placement.id}) global")
    }

    private data class Entry<A : Any>(val token: NativeAdToken, val ad: A, val loadedAt: Instant)
    private data class LoadPlan<A : Any>(val loadCount: Int, val retired: List<Entry<A>>)
    private data class AcquireResult<A : Any>(val token: NativeAdToken?, val retired: List<Entry<A>>)
    private data class AvailableResult<A : Any>(val count: Int, val retired: List<Entry<A>>)
}
