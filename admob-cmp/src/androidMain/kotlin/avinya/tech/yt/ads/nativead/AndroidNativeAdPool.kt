package avinya.tech.yt.ads.nativead

import avinya.tech.yt.ads.AdAttemptResult
import avinya.tech.yt.ads.AdError
import avinya.tech.yt.ads.AdEvent
import avinya.tech.yt.ads.AdLoadState
import avinya.tech.yt.ads.AdLogger
import avinya.tech.yt.ads.AdPlacement
import avinya.tech.yt.ads.AdRequestOptions
import avinya.tech.yt.ads.AdResponseInfo
import avinya.tech.yt.ads.INTERNAL_LOAD_ERROR_CODE
import avinya.tech.yt.ads.NativeAdPool
import avinya.tech.yt.ads.PaidEvent
import avinya.tech.yt.ads.internal.NativePoolCore
import avinya.tech.yt.ads.internal.NativePoolPlatform
import avinya.tech.yt.ads.toAdError
import avinya.tech.yt.ads.toAndroidNativeAdRequest
import avinya.tech.yt.ads.toCommon
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Android native pool. All pool policy — generation, cache accounting, TTL eviction, token
 * minting, state publication — lives in [NativePoolCore]; this class implements only the
 * SDK-touching primitives of [NativePoolPlatform] plus a thin [NativeAdPool] delegation
 * shell.
 *
 * Batch assembly and its `pending`/`cancelled` locking stay here by design (CLAUDE.md
 * invariant #3): they are touched from GMA callbacks and from `invokeOnCancellation` on an
 * arbitrary thread, and that protocol is platform-shaped, not policy-shaped.
 */
@OptIn(ExperimentalTime::class)
internal class AndroidNativeAdPool internal constructor(
    override val placement: AdPlacement,
    globalEvents: MutableSharedFlow<AdEvent>,
    private val adRequestBlockedError: () -> AdError?
) : NativeAdPool, NativePoolPlatform<NativeAd> {

    private val stateLock = Any()
    private val core = NativePoolCore(placement, this, globalEvents)

    override val loadState: StateFlow<AdLoadState> get() = core.loadState
    override val events: SharedFlow<AdEvent> get() = core.events
    override val availableAds: StateFlow<Int> get() = core.availableAds

    override suspend fun preload(
        count: Int,
        requestOptions: AdRequestOptions,
        nativeOptions: NativeAdOptions
    ): AdLoadState = core.preload(count, requestOptions, nativeOptions, adRequestBlockedError)

    override fun acquire(): NativeAdToken? = core.acquire()
    override fun release(token: NativeAdToken) = core.release(token)
    override fun availableCount(): Int = core.availableCount()
    override fun mediaInfo(token: NativeAdToken): NativeMediaInfo? = core.mediaInfo(token)
    override fun clear() = core.clear()

    // Retained for AndroidNativeAdView, which needs the SDK handle to build the view tree.
    fun peek(token: NativeAdToken): NativeAd? = core.peek(token)

    fun take(): NativeAd? = acquire()?.let(::peek)

    // --- NativePoolPlatform ---

    override fun <T> withPoolLock(block: () -> T): T = synchronized(stateLock) { block() }

    override fun destroy(ad: NativeAd) = ad.destroy()

    override fun responseInfo(ad: NativeAd): AdResponseInfo? = ad.getResponseInfo().toCommon()

    override fun mediaInfo(ad: NativeAd): NativeMediaInfo? {
        val mediaContent = ad.mediaContent ?: return null
        return NativeMediaInfo(
            aspectRatio = (mediaContent.aspectRatio as? Float)?.takeIf { it > 0f },
            hasVideoContent = mediaContent.hasVideoContent,
            durationSeconds = mediaContent.duration.takeIf { it > 0f }?.toDouble()
        )
    }

    override suspend fun loadBatch(
        count: Int,
        requestOptions: AdRequestOptions,
        nativeOptions: NativeAdOptions,
        requiredGeneration: Long
    ): AdAttemptResult<List<NativeAd>> = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val pending = mutableListOf<NativeAd>()
            // Guarded by synchronized(pending): once cancellation has run its cleanup, this
            // flips so a late onNativeAdLoaded destroys its ad instead of adding it to an
            // already-drained list.
            var cancelled = false
            // Captures the last per-ad failure so onAdLoadingCompleted can report it if the
            // whole batch yields nothing.
            var lastError: AdError? = null
            // If we're cancelled after some ads loaded but before completion, those native
            // ads would otherwise leak.
            continuation.invokeOnCancellation {
                synchronized(pending) {
                    cancelled = true
                    pending.forEach { it.destroy() }
                    pending.clear()
                }
            }
            // The core computed this under its own lock before we were called; recomputing
            // it from a second read of pool internals would be a torn view of the same state.
            AdLogger.i("Android native load started. placement=${placement.id} adUnit=${placement.androidAdUnitId} count=$count")
            val request = requestOptions.toAndroidNativeAdRequest(placement.androidAdUnitId, nativeOptions)
            NativeAdLoader.load(request, count, object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    installCallbacks(nativeAd)
                    // Atomically check cancellation and add: if cancellation already drained
                    // pending, destroy this ad instead of leaking it into a list nothing
                    // will clean up.
                    val accepted = synchronized(pending) {
                        if (cancelled || !continuation.isActive) {
                            false
                        } else {
                            AdLogger.d("Android native ad loaded callback. placement=${placement.id} pendingBefore=${pending.size}")
                            pending += nativeAd
                            true
                        }
                    }
                    if (!accepted) nativeAd.destroy()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    val error = adError.toAdError()
                    AdLogger.e("Android native load failed. placement=${placement.id} code=${error.code} message=${error.message}")
                    // For a multi-ad request GMA fires this per failed ad while the batch is
                    // still in flight; do NOT resume here or later onNativeAdLoaded callbacks
                    // for the same batch would be rejected and their (good) ads destroyed.
                    // Record the error and let onAdLoadingCompleted decide the final result.
                    synchronized(pending) { lastError = error }
                }

                override fun onAdLoadingCompleted() {
                    val loaded: List<NativeAd>
                    val error: AdError?
                    val accepted: Boolean
                    // Claim the batch under the same lock that snapshots it. Checking
                    // cancellation outside the lock left a window where invokeOnCancellation
                    // could drain and destroy an already-empty `pending` while this snapshot
                    // still held the loaded ads, leaking every ad in the batch. Draining here
                    // also stops a later cancellation from double-destroying what we hand back.
                    synchronized(pending) {
                        loaded = pending.toList()
                        error = lastError
                        accepted = !cancelled && continuation.isActive
                        if (accepted) pending.clear()
                    }
                    AdLogger.i("Android native loading completed. placement=${placement.id} loaded=${loaded.size}")
                    if (!accepted) {
                        loaded.forEach { it.destroy() }
                        return
                    }
                    // Resume once, here — the batch-complete signal. Any ad that loaded
                    // counts as success (partial fill is success).
                    if (loaded.isNotEmpty()) {
                        // Two-arg resume: cancellation racing this resume means the caller
                        // never receives the batch, so destroy it here rather than leaking
                        // it into a dropped value.
                        continuation.resume(AdAttemptResult.Success(loaded)) { _, _, _ ->
                            loaded.forEach { it.destroy() }
                        }
                    } else {
                        // Whole batch yielded nothing: report the captured per-ad error, or a
                        // retryable INTERNAL_ERROR if completion arrived with no failure
                        // callback at all (code must match an enum name
                        // isRetryableLoadFailure recognizes, not a legacy integer).
                        continuation.resume(
                            AdAttemptResult.Failure(
                                error ?: AdError(
                                    code = INTERNAL_LOAD_ERROR_CODE,
                                    message = "Native ad loader completed with no ads."
                                )
                            )
                        )
                    }
                }
            })
        }
    }

    private fun installCallbacks(nativeAd: NativeAd) {
        nativeAd.adEventCallback = object : NativeAdEventCallback {
            override fun onAdImpression() =
                core.emitInstanceScopedEvent({ it === nativeAd }) { id -> AdEvent.Impression(placement.id, id) }
            override fun onAdClicked() =
                core.emitInstanceScopedEvent({ it === nativeAd }) { id -> AdEvent.Clicked(placement.id, id) }
            override fun onAdPaid(value: com.google.android.libraries.ads.mobile.sdk.common.AdValue) {
                core.emitInstanceScopedEvent({ it === nativeAd }) { id ->
                    AdEvent.Paid(
                        placement.id,
                        PaidEvent(placement.id, value.toCommon(), nativeAd.getResponseInfo().toCommon()),
                        id
                    )
                }
            }
        }
    }
}
