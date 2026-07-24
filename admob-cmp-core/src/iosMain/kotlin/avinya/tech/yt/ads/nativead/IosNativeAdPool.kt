@file:OptIn(
    ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlin.time.ExperimentalTime::class
)

package avinya.tech.yt.ads.nativead

import GoogleMobileAds.GADAdChoicesPosition
import GoogleMobileAds.GADAdLoader
import GoogleMobileAds.GADAdLoaderAdTypeNative
import GoogleMobileAds.GADMultipleAdsAdLoaderOptions
import GoogleMobileAds.GADNativeAd
import GoogleMobileAds.GADNativeAdDelegateProtocol
import GoogleMobileAds.GADNativeAdLoaderDelegateProtocol
import GoogleMobileAds.GADNativeAdMediaAdLoaderOptions
import GoogleMobileAds.GADNativeAdViewAdOptions
import GoogleMobileAds.GADNativeAdImageAdLoaderOptions
import GoogleMobileAds.GADVideoOptions
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
import avinya.tech.yt.ads.toCommon
import avinya.tech.yt.ads.toGADRequest
import avinya.tech.yt.ads.topViewController
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSRecursiveLock
import platform.darwin.NSObject

internal class IosNativeAdPool(
    override val placement: AdPlacement,
    globalEvents: MutableSharedFlow<AdEvent>,
    private val adRequestBlockedError: () -> AdError?
) : NativeAdPool, NativePoolPlatform<LoadedNativeAd> {
    private val stateLock = NSRecursiveLock()
    private val core = NativePoolCore(placement, this, globalEvents)

    // GADAdLoader.delegate is weak. Cleared/cancelled batches remain here until GMA sends
    // adLoaderDidFinishLoading, allowing late native ads to be received and torn down.
    // This is an iOS-only in-flight retention registry — the core has no such concept, and
    // must not gain one (CLAUDE.md invariants #3 and #4).
    private val activeLoads = mutableListOf<NativeLoadBatch>()

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

    // IosNativeAdView's UIKit factory consumes the GADNativeAd directly.
    fun peek(token: NativeAdToken): GADNativeAd? = core.peek(token)?.ad

    override fun clear() {
        // Ordering matters: core.clear() bumps the generation first, so a delegate resuming
        // from invalidate() below already fails its generation check.
        core.clear()
        // Settles each suspended preload. The corresponding NativeLoadBatch stays strongly
        // retained in activeLoads until GMA's terminal callback drains it — GADAdLoader has
        // no cancellation API, so late callbacks must still find a live delegate.
        val delegates = withPoolLock { activeLoads.map { it.delegate } }
        delegates.forEach { it.invalidate() }
    }

    // --- NativePoolPlatform ---

    override fun <T> withPoolLock(block: () -> T): T = locked(block)

    // iOS has no GADNativeAd.destroy(), so break the ObjC-side links eagerly instead of
    // waiting for ARC + Kotlin/Native GC: null the delegate and paid-event handler so the
    // ad stops retaining (and being retained by) its delegates and closure. Mirrors the
    // deterministic ad.destroy() the Android pool performs on the same paths.
    override fun destroy(ad: LoadedNativeAd) = teardownNativeAdOnMain(ad.ad)

    override fun responseInfo(ad: LoadedNativeAd): AdResponseInfo? = ad.ad.responseInfo?.toCommon()

    // The core resolves the handle under the pool lock and calls this outside it. The
    // previous shape returned non-locally from inside the inline locked{} lambda — correct
    // only because locked is inline, and silently broken if that ever changed.
    override fun mediaInfo(ad: LoadedNativeAd): NativeMediaInfo? {
        val mediaContent = ad.ad.mediaContent ?: return null
        return NativeMediaInfo(
            aspectRatio = mediaContent.aspectRatio.takeIf { it > 0.0 }?.toFloat(),
            hasVideoContent = mediaContent.hasVideoContent,
            durationSeconds = if (mediaContent.duration > 0f) mediaContent.duration.toDouble() else null
        )
    }

    override suspend fun loadBatch(
        count: Int,
        requestOptions: AdRequestOptions,
        nativeOptions: NativeAdOptions,
        requiredGeneration: Long
    ): AdAttemptResult<List<LoadedNativeAd>> = withContext(Dispatchers.Main.immediate) {
        if (!core.isCurrentGeneration(requiredGeneration)) {
            return@withContext AdAttemptResult.Failure(AdError.message("Native ad preload was cleared."))
        }
        suspendCancellableCoroutine<AdAttemptResult<List<LoadedNativeAd>>> { continuation ->
            lateinit var delegate: NativeAdLoaderDelegate
            delegate = NativeAdLoaderDelegate(
                placementId = placement.id,
                emit = { core.emitPlatformEvent(it) },
                emitInstanceScoped = core::emitInstanceScopedEvent,
                continuation = continuation,
                onTerminal = ::releaseActiveLoad
            )
            val adLoader = createAdLoader(count, requestOptions, nativeOptions)
            val batch = NativeLoadBatch(adLoader, delegate)
            continuation.invokeOnCancellation {
                // Cancellation settles the caller independently from SDK completion. Keep the
                // batch retained so late callbacks still reach delegate.cancel()'s rejection path.
                delegate.cancel()
            }
            adLoader.delegate = delegate
            val registered = locked {
                if (!core.isCurrentGeneration(requiredGeneration)) false else {
                    activeLoads += batch
                    true
                }
            }
            if (registered) {
                // If clear races after registration, it invalidates the delegate but intentionally
                // leaves this batch retained; starting the request still gives GMA a terminal
                // callback on which ownership can be released.
                adLoader.loadRequest(requestOptions.toGADRequest())
            } else {
                delegate.invalidate()
            }
        }
    }

    private fun releaseActiveLoad(delegate: NativeAdLoaderDelegate) {
        locked {
            val index = activeLoads.indexOfFirst { it.delegate === delegate }
            if (index >= 0) activeLoads.removeAt(index)
        }
    }

    private inline fun <T> locked(block: () -> T): T {
        stateLock.lock()
        try {
            return block()
        } finally {
            stateLock.unlock()
        }
    }

    private fun createAdLoader(loadCount: Int, requestOptions: AdRequestOptions, nativeOptions: NativeAdOptions): GADAdLoader {
        val rootViewController = topViewController()
        val options = mutableListOf<Any>().apply {
            add(GADMultipleAdsAdLoaderOptions().apply { numberOfAds = loadCount.toLong() })
            add(GADNativeAdMediaAdLoaderOptions().apply {
                mediaAspectRatio = nativeOptions.mediaAspectRatio.toGADMediaAspectRatio()
            })
            add(GADNativeAdImageAdLoaderOptions().apply {
                disableImageLoading = nativeOptions.disableImageLoading
                shouldRequestMultipleImages = nativeOptions.requestMultipleImages
            })
            add(GADVideoOptions().apply {
                startMuted = nativeOptions.videoOptions.startMuted
                customControlsRequested = nativeOptions.videoOptions.customControlsRequested
                clickToExpandRequested = nativeOptions.videoOptions.clickToExpandRequested
            })
            add(GADNativeAdViewAdOptions().apply {
                preferredAdChoicesPosition = nativeOptions.adChoicesPlacement.toGADAdChoicesPosition()
            })
        }
        return GADAdLoader(
            adUnitID = placement.iosAdUnitId,
            rootViewController = rootViewController,
            adTypes = listOf(GADAdLoaderAdTypeNative),
            options = options
        )
    }

    private data class NativeLoadBatch(val loader: GADAdLoader, val delegate: NativeAdLoaderDelegate)
}

internal class LoadedNativeAd(val ad: GADNativeAd, val delegates: List<NSObject>)

internal class NativeAdLoaderDelegate(
    private val placementId: String,
    private val emit: (AdEvent) -> Unit,
    // P1-8: Impression/Clicked/Paid must name the specific ad instance they came from so a
    // NativeAdView can filter out events meant for a different row on a shared placement. The
    // delegate only has access to whatever raw SDK reference the callback naturally carries
    // (didReceiveNativeAd below), not the pool/core itself, so the core's resolver is threaded
    // through as this lambda — see NativePoolCore.emitInstanceScopedEvent.
    private val emitInstanceScoped: (matchesAd: (LoadedNativeAd) -> Boolean, eventFactory: (adInstanceId: String?) -> AdEvent) -> Unit,
    private val continuation: kotlinx.coroutines.CancellableContinuation<AdAttemptResult<List<LoadedNativeAd>>>,
    private val onTerminal: (NativeAdLoaderDelegate) -> Unit
) : NSObject(), GADNativeAdLoaderDelegateProtocol {
    // `pending` and `cancelled` are touched from the GMA delegate callbacks (Main.immediate)
    // AND from invokeOnCancellation (any thread), mirroring the Android pool. All access goes
    // through this lock so a late didReceiveNativeAd can't add to an already-drained list and
    // a cancellation can't race a receive (CLAUDE.md invariant #3).
    private val lock = NSRecursiveLock()
    private val pending = mutableListOf<LoadedNativeAd>()
    private var invalidated = false
    private var continuationSettled = false
    private var terminalCallbackReceived = false
    // Captures the last per-ad failure so adLoaderDidFinishLoading can report it if
    // the whole batch yields nothing.
    private var lastError: AdError? = null

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    // Called when the preload coroutine itself is cancelled. The caller is already settled,
    // but the delegate stays strongly retained by the pool until GMA's terminal callback.
    fun cancel() {
        val retired = locked {
            invalidated = true
            continuationSettled = true
            pending.toList().also {
                pending.clear()
            }
        }
        retired.forEach(::teardownLoaded)
    }

    // clear() must settle preload() even though GADAdLoader has no cancellation API. Mark the
    // batch invalid, drain already-delivered ads, and resume with a non-retryable internal result;
    // activeLoads continues retaining this delegate/loader for safe late-callback handling.
    fun invalidate() {
        val invalidation = locked {
            invalidated = true
            val retired = pending.toList()
            pending.clear()
            val shouldResume = !continuationSettled
            continuationSettled = true
            NativeLoadInvalidation(retired, shouldResume)
        }
        invalidation.retired.forEach(::teardownLoaded)
        if (invalidation.shouldResume && continuation.isActive) {
            continuation.resume(
                AdAttemptResult.Failure(AdError.message("Native ad preload was cleared.")),
                null
            )
        }
    }

    override fun adLoader(adLoader: GADAdLoader, didReceiveNativeAd: GADNativeAd) {
        val nativeDelegate = NativeAdDelegate(
            onImpression = {
                emitInstanceScoped({ it.ad === didReceiveNativeAd }) { id -> AdEvent.Impression(placementId, id) }
            },
            onClicked = {
                emitInstanceScoped({ it.ad === didReceiveNativeAd }) { id -> AdEvent.Clicked(placementId, id) }
            }
        )
        val delegates = mutableListOf<NSObject>()
        didReceiveNativeAd.delegate = nativeDelegate
        delegates.add(nativeDelegate)
        val weakAd = WeakReference(didReceiveNativeAd)
        didReceiveNativeAd.paidEventHandler = { value ->
            val ad = weakAd.value
            val adValue = value?.toCommon()
            if (ad != null && adValue != null) {
                emitInstanceScoped({ it.ad === didReceiveNativeAd }) { id ->
                    AdEvent.Paid(placementId, PaidEvent(placementId, adValue, ad.responseInfo?.toCommon()), id)
                }
            }
        }
        val mc = didReceiveNativeAd.mediaContent
        if (mc != null && mc.hasVideoContent) {
            val videoDelegate = NativeVideoControllerDelegate(
                placementId = placementId,
                emit = emit
            )
            mc.videoController.delegate = videoDelegate
            delegates.add(videoDelegate)
        }
        val loaded = LoadedNativeAd(didReceiveNativeAd, delegates)
        // Atomically check cancellation and add: if already cancelled/inactive, tear down
        // this ad instead of leaking it into a list nothing will drain.
        val accepted = locked {
            if (invalidated || continuationSettled || terminalCallbackReceived || !continuation.isActive) {
                false
            } else {
                pending.add(loaded)
                true
            }
        }
        if (!accepted) teardownLoaded(loaded)
    }
    override fun adLoaderDidFinishLoading(adLoader: GADAdLoader) {
        // Batch-complete signal: resume once here. Partial fill counts as success;
        // an empty batch reports the captured per-ad error or a stable shared
        // internal-error code, so it follows the same retry policy as Android.
        val completion = locked {
            if (terminalCallbackReceived) return@locked null
            terminalCallbackReceived = true
            if (continuationSettled) return@locked null
            continuationSettled = true
            val loaded = pending.toList()
            pending.clear()
            if (loaded.isNotEmpty()) {
                AdAttemptResult.Success(loaded)
            } else {
                AdAttemptResult.Failure(
                    lastError ?: AdError(
                        code = INTERNAL_LOAD_ERROR_CODE,
                        message = "Native ad loader completed with no ads."
                    )
                )
            }
        }
        try {
            if (completion != null) {
                if (continuation.isActive) {
                    continuation.resume(
                        completion,
                        onCancellation = { _, value, _ ->
                            if (value is AdAttemptResult.Success) {
                                value.value.forEach(::teardownLoaded)
                            }
                        }
                    )
                } else if (completion is AdAttemptResult.Success) {
                    completion.value.forEach(::teardownLoaded)
                }
            }
        } finally {
            // This is the SDK ownership boundary: only now may the pool drop the strong loader
            // and weak-delegate companion references retained for this batch.
            onTerminal(this)
        }
    }
    override fun adLoader(adLoader: GADAdLoader, didFailToReceiveAdWithError: NSError) {
        // For a multi-ad request this fires per failed ad while the batch is still in
        // flight; record the error and let adLoaderDidFinishLoading resume once, so a
        // partial fill is not discarded by an early resume.
        locked {
            if (!invalidated && !continuationSettled && !terminalCallbackReceived) {
                lastError = didFailToReceiveAdWithError.toAdError()
            }
        }
    }

    // iOS has no GADNativeAd.destroy(); null the ObjC-side links so the ad and its delegates
    // stop retaining each other (same as IosNativeAdPool.teardownAd, duplicated here because
    // the delegate has no access to the pool's private helper).
    private fun teardownLoaded(loaded: LoadedNativeAd) {
        teardownNativeAdOnMain(loaded.ad)
    }

    private data class NativeLoadInvalidation(
        val retired: List<LoadedNativeAd>,
        val shouldResume: Boolean
    )
}

private val nativeAdTeardownScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

private fun teardownNativeAdOnMain(ad: GADNativeAd) {
    // Delegate cancellation can run on any thread. Logical ownership is always drained under the
    // owning lock first; this independent scope performs only the physical GMA teardown on Main.
    nativeAdTeardownScope.launch {
        ad.delegate = null
        ad.paidEventHandler = null
        ad.mediaContent?.videoController?.delegate = null
    }
}

internal class NativeVideoControllerDelegate(
    private val placementId: String,
    private val emit: (AdEvent) -> Unit
) : NSObject(), GoogleMobileAds.GADVideoControllerDelegateProtocol {
    // The protocol only has DidPlayVideo (both first play and resume); distinguish
    // Started/Played ourselves.
    private var started = false

    override fun videoControllerDidPlayVideo(videoController: GoogleMobileAds.GADVideoController) {
        if (!started) {
            started = true
            emit(AdEvent.VideoStarted(placementId))
        } else {
            emit(AdEvent.VideoPlayed(placementId))
        }
    }
    override fun videoControllerDidPauseVideo(videoController: GoogleMobileAds.GADVideoController) {
        emit(AdEvent.VideoPaused(placementId))
    }
    override fun videoControllerDidEndVideoPlayback(videoController: GoogleMobileAds.GADVideoController) {
        emit(AdEvent.VideoEnded(placementId))
    }
    override fun videoControllerDidMuteVideo(videoController: GoogleMobileAds.GADVideoController) {
        emit(AdEvent.VideoMuted(placementId, muted = true))
    }
    override fun videoControllerDidUnmuteVideo(videoController: GoogleMobileAds.GADVideoController) {
        emit(AdEvent.VideoMuted(placementId, muted = false))
    }
}

internal class NativeAdDelegate(
    private val onImpression: () -> Unit,
    private val onClicked: () -> Unit
) : NSObject(), GADNativeAdDelegateProtocol {
    override fun nativeAdDidRecordImpression(nativeAd: GADNativeAd) { onImpression() }
    override fun nativeAdDidRecordClick(nativeAd: GADNativeAd) { onClicked() }
    override fun nativeAdWillPresentScreen(nativeAd: GADNativeAd) {}
    override fun nativeAdWillDismissScreen(nativeAd: GADNativeAd) {}
    override fun nativeAdDidDismissScreen(nativeAd: GADNativeAd) {}
}

public fun NativeAdPool.peekIosNativeAd(token: NativeAdToken): GADNativeAd? =
    (this as? IosNativeAdPool)?.peek(token)

internal fun NativeMediaAspectRatio.toGADMediaAspectRatio(): GoogleMobileAds.GADMediaAspectRatio = when (this) {
    NativeMediaAspectRatio.Unknown -> GoogleMobileAds.GADMediaAspectRatioUnknown
    NativeMediaAspectRatio.Any -> GoogleMobileAds.GADMediaAspectRatioAny
    NativeMediaAspectRatio.Landscape -> GoogleMobileAds.GADMediaAspectRatioLandscape
    NativeMediaAspectRatio.Portrait -> GoogleMobileAds.GADMediaAspectRatioPortrait
    NativeMediaAspectRatio.Square -> GoogleMobileAds.GADMediaAspectRatioSquare
}

internal fun AdChoicesPlacement.toGADAdChoicesPosition(): GoogleMobileAds.GADAdChoicesPosition = when (this) {
    AdChoicesPlacement.TopLeft -> GADAdChoicesPosition.GADAdChoicesPositionTopLeftCorner
    AdChoicesPlacement.TopRight -> GADAdChoicesPosition.GADAdChoicesPositionTopRightCorner
    AdChoicesPlacement.BottomRight -> GADAdChoicesPosition.GADAdChoicesPositionBottomRightCorner
    AdChoicesPlacement.BottomLeft -> GADAdChoicesPosition.GADAdChoicesPositionBottomLeftCorner
}
