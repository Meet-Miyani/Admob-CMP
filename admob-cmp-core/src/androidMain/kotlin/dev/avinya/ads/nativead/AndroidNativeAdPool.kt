package dev.avinya.ads.nativead

import android.os.Handler
import android.os.Looper
import dev.avinya.ads.InternalAdMobCmpApi
import dev.avinya.ads.AdAttemptResult
import dev.avinya.ads.AdError
import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdLoadState
import dev.avinya.ads.AdLogger
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdRequestOptions
import dev.avinya.ads.AdResponseInfo
import dev.avinya.ads.INTERNAL_LOAD_ERROR_CODE
import dev.avinya.ads.NativeAdPool
import dev.avinya.ads.PaidEvent
import dev.avinya.ads.internal.NativePoolCore
import dev.avinya.ads.internal.NativePoolPlatform
import dev.avinya.ads.toAdError
import dev.avinya.ads.toAndroidNativeAdRequest
import dev.avinya.ads.toCommon
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
) : NativeAdPool, NativePoolPlatform<AndroidLoadedNativeAd> {

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
    // Unwraps the handle exactly as IosNativeAdPool.peek does.
    fun peek(token: NativeAdToken): NativeAd? = core.peek(token)?.ad

    fun take(): NativeAd? = acquire()?.let(::peek)

    // --- NativePoolPlatform ---

    override fun <T> withPoolLock(block: () -> T): T = synchronized(stateLock) { block() }

    override fun destroy(ad: AndroidLoadedNativeAd) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ad.ad.destroy()
        } else {
            Handler(Looper.getMainLooper()).post { ad.ad.destroy() }
        }
    }

    // Both accessors are pure field reads of values snapshotted on Main at load time, so they
    // are safe from whatever dispatcher the caller uses. mediaInfo() in particular is public,
    // non-suspend API a consumer can call from any thread.
    override fun responseInfo(ad: AndroidLoadedNativeAd): AdResponseInfo? = ad.responseInfo

    override fun mediaInfo(ad: AndroidLoadedNativeAd): NativeMediaInfo? = ad.mediaInfo

    override suspend fun loadBatch(
        count: Int,
        requestOptions: AdRequestOptions,
        nativeOptions: NativeAdOptions,
        requiredGeneration: Long
    ): AdAttemptResult<List<AndroidLoadedNativeAd>> = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val pending = mutableListOf<AndroidLoadedNativeAd>()
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
                    pending.forEach { it.ad.destroy() }
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
                    // Snapshot response/media info HERE, on the Main-confined loader callback.
                    // NativePoolCore reads them back from an arbitrary dispatcher (mediaInfo() is
                    // even public, non-suspend API), which put GMA accesses off Main — CLAUDE.md
                    // invariant #5. Both are fixed once the ad is loaded.
                    val loaded = AndroidLoadedNativeAd(
                        ad = nativeAd,
                        responseInfo = nativeAd.getResponseInfo().toCommon(),
                        mediaInfo = nativeAd.readMediaInfo()
                    )
                    val accepted = synchronized(pending) {
                        if (cancelled || !continuation.isActive) {
                            false
                        } else {
                            AdLogger.d("Android native ad loaded callback. placement=${placement.id} pendingBefore=${pending.size}")
                            pending += loaded
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
                    val loaded: List<AndroidLoadedNativeAd>
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
                        loaded.forEach { it.ad.destroy() }
                        return
                    }
                    // Resume once, here — the batch-complete signal. Any ad that loaded
                    // counts as success (partial fill is success).
                    if (loaded.isNotEmpty()) {
                        // Two-arg resume: cancellation racing this resume means the caller
                        // never receives the batch, so destroy it here rather than leaking
                        // it into a dropped value.
                        continuation.resume(AdAttemptResult.Success(loaded)) { _, _, _ ->
                            loaded.forEach { it.ad.destroy() }
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
                core.emitInstanceScopedEvent({ it.ad === nativeAd }) { id -> AdEvent.Impression(placement.id, id) }
            override fun onAdClicked() =
                core.emitInstanceScopedEvent({ it.ad === nativeAd }) { id -> AdEvent.Clicked(placement.id, id) }
            override fun onAdPaid(value: com.google.android.libraries.ads.mobile.sdk.common.AdValue) {
                core.emitInstanceScopedEvent({ it.ad === nativeAd }) { id ->
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

/**
 * Android's native-ad handle: the SDK ad plus the metadata snapshotted on Main at load time.
 *
 * Mirrors iOS's [dev.avinya.ads.nativead.LoadedNativeAd]. The raw `NativeAd` used to be the
 * pool's handle type directly, which left no place to cache metadata and forced the core to
 * call back into GMA from arbitrary dispatchers.
 */
internal data class AndroidLoadedNativeAd(
    val ad: NativeAd,
    val responseInfo: AdResponseInfo?,
    val mediaInfo: NativeMediaInfo?,
)

private fun NativeAd.readMediaInfo(): NativeMediaInfo? {
    val mediaContent = mediaContent ?: return null
    return NativeMediaInfo(
        aspectRatio = (mediaContent.aspectRatio as? Float)?.takeIf { it > 0f },
        hasVideoContent = mediaContent.hasVideoContent,
        durationSeconds = mediaContent.duration.takeIf { it > 0f }?.toDouble()
    )
}

@InternalAdMobCmpApi
public fun NativeAdPool.peekAndroidNativeAd(token: NativeAdToken): NativeAd? =
    (this as? AndroidNativeAdPool)?.peek(token)
