package dev.avinya.ads.internal

import dev.avinya.ads.AdAttemptResult
import dev.avinya.ads.AdError
import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdLoadState
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdRequestOptions
import dev.avinya.ads.AdResponseInfo
import dev.avinya.ads.AdSizePolicy
import dev.avinya.ads.BannerGeometry
import dev.avinya.ads.isRetryableLoadFailure
import dev.avinya.ads.retryAdLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Platform primitives the shared banner controller needs.
 *
 * [V] is the platform's banner handle. Android binds `BannerAd`. iOS binds a
 * view+delegate pair, because `GADBannerView.delegate` is weak and must be strongly
 * retained for the view's whole lifetime (CLAUDE.md invariant #4). Carrying the
 * delegate inside [V] means "the core retains the banner" already implies "the
 * delegate is alive", and the core never has to know delegates exist.
 *
 * [S] is the platform's resolved ad-size type (`AdSize` / `CValue<GADAdSize>`),
 * which cannot cross into commonMain.
 */
internal interface BannerPlatform<V : Any, S : Any> {
    fun <T> withStateLock(block: () -> T): T

    /**
     * Best-effort width for a headless load with no host geometry, or null when the
     * platform cannot determine one.
     *
     * Nullable on BOTH platforms deliberately: the core, not the platform, owns the
     * failure policy. Android returns null with no current Activity; iOS returns null
     * when it cannot resolve a viewport width.
     */
    fun fallbackWidthDp(): Int?

    fun resolveSize(sizePolicy: AdSizePolicy, widthDp: Int): S

    /** Destroy/tear down a banner. Synchronous on Android; Main-confined async on iOS. */
    fun destroy(banner: V)

    fun responseInfo(banner: V): AdResponseInfo?

    /**
     * Create, configure and load one banner at [size].
     *
     * The platform owns delegate creation, assignment order and retention. On iOS the
     * delegate MUST be assigned before `loadRequest` and strongly held; the core never
     * touches it. [requiredGeneration] lets the platform reject a load the core has
     * already invalidated before handing an ad to the SDK.
     */
    suspend fun loadBanner(
        size: S,
        requestOptions: AdRequestOptions,
        requiredGeneration: Long
    ): AdAttemptResult<V>
}

/**
 * Shared banner controller state machine. Owns the load mutex, generation, attachment
 * refcounting, the resolved request, load-state publication and the
 * keep-the-old-banner-until-the-new-one-lands swap.
 *
 * Restores CLAUDE.md invariant #6: geometry is a host-supplied *input*, never something
 * the controller reaches for. Both controllers previously violated this — Android through
 * `Activity`, iOS through `UIScreen.mainScreen.bounds` (P0-5).
 */
internal class BannerCore<V : Any, S : Any>(
    val placement: AdPlacement,
    private val platform: BannerPlatform<V, S>,
    private val globalEvents: MutableSharedFlow<AdEvent>
) {
    private val loadMutex = Mutex()
    private val _loadState = MutableStateFlow<AdLoadState>(AdLoadState.Idle)
    private val _events = MutableSharedFlow<AdEvent>(extraBufferCapacity = 32)

    private var banner: V? = null
    private var generation = 0L

    // Set by an actual load; this is the P1-4 replay source and always wins.
    private var lastRequest: ResolvedBannerRequest<S>? = null

    // Set by the composable's container measurement WITHOUT loading, so a Manual-policy
    // placement (which performs no automatic load) can still refresh() at the measured
    // width. Kept separate from lastRequest so registering geometry can never overwrite the
    // custom request options a real load resolved — that would silently undo P1-4.
    private var registeredRequest: ResolvedBannerRequest<S>? = null

    // Count of live UI attachments (BannerAdView composables) bound to this controller.
    // The controller is manager-cached for the whole process, so it can't destroy its ad on
    // every composable dispose (a sibling screen briefly mounted during a nav transition may
    // share the same placement). Instead we destroy when the LAST attachment leaves.
    private var attachCount = 0

    val loadState: StateFlow<AdLoadState> = _loadState
    val events: SharedFlow<AdEvent> = _events

    fun currentBanner(): V? = platform.withStateLock { banner }

    fun attach() {
        platform.withStateLock { attachCount++ }
    }

    fun detach() {
        val retired = platform.withStateLock {
            attachCount = (attachCount - 1).coerceAtLeast(0)
            // Invalidate the current load generation even when loadMutex is held. Its eventual
            // callback is allowed to settle the suspended load, but may not reinstall an ad for
            // a UI attachment that no longer exists.
            if (attachCount == 0) clearLocked() else null
        }
        retired?.let { platform.destroy(it) }
    }

    suspend fun load(
        geometry: BannerGeometry?,
        sizePolicy: AdSizePolicy,
        requestOptions: AdRequestOptions,
        blockedError: () -> AdError? = { null }
    ): AdLoadState {
        val requiredGeneration = currentGeneration()
        // Consent is the harder gate, so it is checked before geometry: a consent-blocked
        // caller with no geometry should be told about consent, not sent to fix a width and
        // then hit the gate on the retry. Re-checked inside loadForGeneration, which is the
        // path refresh() also takes (CLAUDE.md invariant #5 — the gate is checked in load()).
        blockedError()?.let { return failIfCurrent(requiredGeneration, it) }
        val widthDp = resolveWidthDp(geometry)
            ?: return failIfCurrent(
                requiredGeneration,
                AdError.message(
                    "Banner load requires a width: no BannerGeometry was supplied and the platform " +
                        "could not determine a fallback. Use BannerAdView, which measures its container."
                )
            )
        val resolved = ResolvedBannerRequest(
            size = platform.resolveSize(sizePolicy, widthDp),
            sizePolicy = sizePolicy,
            requestOptions = requestOptions
        )
        loadForGeneration(resolved, requiredGeneration, blockedError)
        return _loadState.value
    }

    suspend fun refresh(blockedError: () -> AdError? = { null }): AdLoadState {
        val requiredGeneration = currentGeneration()
        // P1-4: replay the WHOLE resolved request, not just the size. Both controllers
        // previously kept only lastResolvedAdSize and rebuilt options from
        // placement.requestOptions, silently dropping custom options from the original
        // load — contradicting the KDoc.
        val resolved = platform.withStateLock { lastRequest ?: registeredRequest }
            ?: return failIfCurrent(
                requiredGeneration,
                AdError.message("Banner refresh requires a prior successful load to replay.")
            )
        loadForGeneration(resolved, requiredGeneration, blockedError)
        return _loadState.value
    }

    /**
     * Records the container geometry the host measured, without triggering a load.
     *
     * Exists for [dev.avinya.ads.BannerRefreshPolicy.Manual], where the composable
     * deliberately performs no automatic load but the consumer still drives [refresh].
     * Without it, such a placement could never refresh: it has no prior load to replay
     * and no way to hand geometry in.
     */
    fun registerGeometry(
        geometry: BannerGeometry,
        sizePolicy: AdSizePolicy,
        requestOptions: AdRequestOptions
    ) {
        platform.withStateLock {
            registeredRequest = ResolvedBannerRequest(
                size = platform.resolveSize(sizePolicy, geometry.widthDp),
                sizePolicy = sizePolicy,
                requestOptions = requestOptions
            )
        }
    }

    private fun resolveWidthDp(geometry: BannerGeometry?): Int? =
        geometry?.widthDp ?: platform.fallbackWidthDp()

    private suspend fun loadForGeneration(
        resolved: ResolvedBannerRequest<S>,
        requiredGeneration: Long,
        blockedError: () -> AdError?
    ): V? = loadMutex.withLock {
        val previousAd = platform.withStateLock {
            if (generation != requiredGeneration) return@withStateLock null
            lastRequest = resolved
            _loadState.value = AdLoadState.Loading
            // Box so a null previous ad is distinguishable from a stale generation.
            Box(banner)
        } ?: return@withLock null

        blockedError()?.let { error ->
            failIfCurrent(requiredGeneration, error)
            return@withLock null
        }

        // Keep the currently displayed ad on screen until the new one returns (no blank
        // flash on refresh). The old ad is destroyed only after the new ad loads
        // successfully; on failure the old ad stays visible.
        val previous = previousAd.value
        try {
            // Bounds the WHOLE attempt sequence including retry backoff, not each
            // attempt: a listener that never calls back would otherwise restart the
            // clock on every retry and still never finish.
            val result = withTimeoutOrNull(placement.timeoutPolicy.loadTimeout) {
                retryAdLoad<V>(placement.retryPolicy, { it.isRetryableLoadFailure() }) {
                    if (!isCurrentGeneration(requiredGeneration)) {
                        AdAttemptResult.Failure(AdError.message("Banner load was cleared."))
                    } else {
                        platform.loadBanner(resolved.size, resolved.requestOptions, requiredGeneration)
                    }
                }
            } ?: AdAttemptResult.Failure(
                AdError.message(
                    "Banner load timed out after ${placement.timeoutPolicy.loadTimeout}. " +
                        "The SDK accepted the request but never reported a result."
                )
            )
            when (result) {
                is AdAttemptResult.Success -> {
                    val responseInfo = platform.responseInfo(result.value)
                    val accepted = platform.withStateLock {
                        if (generation != requiredGeneration) false else {
                            banner = result.value
                            _loadState.value = AdLoadState.Loaded(responseInfo)
                            true
                        }
                    }
                    if (accepted) {
                        emit(AdEvent.Loaded(placement.id, responseInfo))
                        // Keep the old ad until the replacement is fully loaded and admitted.
                        if (previous !== result.value) previous?.let { platform.destroy(it) }
                        result.value
                    } else {
                        platform.destroy(result.value)
                        null
                    }
                }
                is AdAttemptResult.Failure -> {
                    // Leave the previously displayed ad on screen on a current-generation
                    // failure. A detached/cleared generation remains Idle. Use failOrRestore,
                    // not failIfCurrent: when a banner is still displayed, loadState must say
                    // Loaded, not Failed — otherwise a host reacting to Failed by hiding the
                    // slot would hide an ad that is, in fact, still on screen.
                    failOrRestore(requiredGeneration, result.error)
                    null
                }
            }
        } catch (e: CancellationException) {
            // Cancelled mid-load: the previously displayed ad (if any) stays as-is. If the
            // SDK still delivers the in-flight ad, the platform's own isActive guard
            // destroys it there, so nothing leaks.
            platform.withStateLock {
                if (generation == requiredGeneration) _loadState.value = AdLoadState.Idle
            }
            throw e
        } catch (t: Throwable) {
            // P1-1: only cancellation was handled, so a throw from a beta SDK call or a
            // mapper escaped with the state stuck at Loading. That is worse than it looks
            // here: BannerAdView's refresh loop awaits `loadState !is Loading`, which would
            // then never become true again, silently killing refresh for that placement.
            //
            // The displayed banner is deliberately left alone — same reasoning as an
            // ordinary failed refresh (no blank flash) — so the state stays Loaded whenever
            // one is still on screen.
            failOrRestore(
                requiredGeneration,
                AdError.message(t.message ?: "Banner load failed unexpectedly.")
            )
            throw t
        }
    }

    fun clear() {
        val retired = platform.withStateLock { clearLocked() }
        retired?.let { platform.destroy(it) }
    }

    fun currentGeneration(): Long = platform.withStateLock { generation }

    fun isCurrentGeneration(requiredGeneration: Long): Boolean =
        platform.withStateLock { generation == requiredGeneration }

    /**
     * Publishes an event originating from a platform SDK callback (impression, click, paid).
     * Only the platform sees these; the core owns the flows they must reach.
     */
    fun emitPlatformEvent(event: AdEvent) = emit(event)

    /** Caller must hold the state lock. */
    private fun clearLocked(): V? {
        generation++
        val retired = banner
        banner = null
        lastRequest = null
        registeredRequest = null
        _loadState.value = AdLoadState.Idle
        return retired
    }

    /**
     * Terminal state for an unexpected (non-cancellation) failure: keep reporting [Loaded]
     * when a banner is still displayed, otherwise publish [AdLoadState.Failed].
     */
    private fun failOrRestore(requiredGeneration: Long, error: AdError): AdLoadState {
        val displayed = platform.withStateLock {
            if (generation == requiredGeneration) banner else null
        }
        return if (displayed != null) {
            platform.withStateLock {
                _loadState.value = AdLoadState.Loaded(platform.responseInfo(displayed))
            }
            _loadState.value
        } else {
            failIfCurrent(requiredGeneration, error)
        }
    }

    private fun failIfCurrent(requiredGeneration: Long, error: AdError): AdLoadState {
        val published = platform.withStateLock {
            if (generation != requiredGeneration) false else {
                _loadState.value = AdLoadState.Failed(error)
                true
            }
        }
        if (published) emit(AdEvent.LoadFailed(placement.id, error))
        return _loadState.value
    }

    private fun emit(event: AdEvent) {
        _events.emitOrLogDrop(event, "BannerCore(${placement.id})")
        globalEvents.emitOrLogDrop(event, "BannerCore(${placement.id}) global")
    }

    private data class ResolvedBannerRequest<S : Any>(
        val size: S,
        val sizePolicy: AdSizePolicy,
        val requestOptions: AdRequestOptions
    )

    private class Box<T>(val value: T?)
}
