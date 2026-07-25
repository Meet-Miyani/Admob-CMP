@file:OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package dev.avinya.ads

import GoogleMobileAds.GADAppOpenAd
import GoogleMobileAds.GADFullScreenContentDelegateProtocol
import GoogleMobileAds.GADFullScreenPresentingAdProtocol
import GoogleMobileAds.GADInterstitialAd
import GoogleMobileAds.GADRewardedAd
import GoogleMobileAds.GADRewardedInterstitialAd
import GoogleMobileAds.GADResponseInfo
import dev.avinya.ads.internal.FullScreenPresentationArbiter
import dev.avinya.ads.internal.FullScreenPresentationHandle
import dev.avinya.ads.internal.FullScreenSlotCore
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.native.ref.WeakReference
import platform.Foundation.NSError
import platform.Foundation.NSRecursiveLock
import platform.darwin.NSObject

internal class IosInterstitialSlot(
    placement: AdPlacement,
    globalEvents: MutableSharedFlow<AdEvent>,
    adRequestBlockedError: () -> AdError?,
    onPresentationChanged: (Int) -> Unit,
    arbiter: FullScreenPresentationArbiter
) : FullScreenSlotCore<GADInterstitialAd>(
    placement,
    globalEvents,
    adRequestBlockedError,
    onPresentationChanged = onPresentationChanged,
    arbiter = arbiter
), InterstitialAdController {
    private val delegates = FullScreenDelegateStore<GADInterstitialAd>()

    override suspend fun loadAd(requestOptions: AdRequestOptions): AdAttemptResult<GADInterstitialAd> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { }
                GADInterstitialAd.loadWithAdUnitID(placement.iosAdUnitId, requestOptions.toGADRequest()) { ad, error ->
                    if (continuation.isActive) {
                        if (error != null) {
                            continuation.resume(AdAttemptResult.Failure(error.toAdError()))
                        } else if (ad != null) {
                            val weakAd = WeakReference(ad)
                            ad.paidEventHandler = { value ->
                                val strongAd = weakAd.value
                                val adValue = value?.toCommon()
                                if (strongAd != null && adValue != null) {
                                    emit(AdEvent.Paid(placement.id, PaidEvent(placement.id, adValue, strongAd.responseInfo?.toCommon())))
                                }
                            }
                            continuation.resume(AdAttemptResult.Success(ad))
                        } else {
                            continuation.resume(AdAttemptResult.Failure(AdError.message("iOS SDK returned no ad and no error.")))
                        }
                    }
                }
            }
        }

    override suspend fun presentAd(
        loaded: GADInterstitialAd,
        options: FullScreenAdOptions,
        presentation: FullScreenPresentationHandle
    ): AdShowResult = withContext(Dispatchers.Main.immediate) {
        val rootVC = topViewController()
            ?: return@withContext AdShowResult.Failed(AdError.message("No root view controller."))
        suspendCancellableCoroutine<AdShowResult> { continuation ->
            // Cancellation closes only while the core still owns presentation. Once hand-off
            // wins, this delegate stays retained until the SDK terminal callback closes it.
            continuation.invokeOnCancellation { presentation.closeIfCoreOwned() }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            val delegate = FullScreenDelegate(
                onOpened = { emit(AdEvent.OpenedFullScreen(placement.id)) },
                onClosed = {
                    if (presentation.close(wasShown = true)) {
                        emit(AdEvent.ClosedFullScreen(placement.id))
                        if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                    }
                },
                onFailedToShow = { error ->
                    if (presentation.close(wasShown = false)) {
                        emit(AdEvent.ShowFailed(placement.id, error))
                        if (continuation.isActive) continuation.resume(AdShowResult.Failed(error))
                    }
                },
                onImpression = { emit(AdEvent.Impression(placement.id)) },
                onClicked = { emit(AdEvent.Clicked(placement.id)) }
            )
            // Hand off before retaining: if cancellation already closed the handle (raced in
            // via invokeOnCancellation between the isActive check above and here), there will
            // be no terminal SDK callback to release this delegate — retaining it regardless
            // would leak the entry in the delegate store until the slot is next reused.
            if (presentation.tryHandOffToCallbacks()) {
                delegates.retain(loaded, delegate)
                loaded.fullScreenContentDelegate = delegate
                loaded.presentFromRootViewController(rootVC)
            }
        }
    }

    override fun destroyAd(ad: GADInterstitialAd) {
        delegates.release(ad)
    }

    override fun getResponseInfo(ad: GADInterstitialAd): AdResponseInfo? = ad.responseInfo?.toCommon()

    override fun canPresent(): AdError? = if (topViewController() != null) null else AdError.message("No root view controller.")
}

internal class IosRewardedSlot(
    placement: AdPlacement,
    globalEvents: MutableSharedFlow<AdEvent>,
    adRequestBlockedError: () -> AdError?,
    onPresentationChanged: (Int) -> Unit,
    arbiter: FullScreenPresentationArbiter
) : FullScreenSlotCore<GADRewardedAd>(
    placement,
    globalEvents,
    adRequestBlockedError,
    onPresentationChanged = onPresentationChanged,
    arbiter = arbiter
), RewardedAdController {
    private val delegates = FullScreenDelegateStore<GADRewardedAd>()

    override suspend fun loadAd(requestOptions: AdRequestOptions): AdAttemptResult<GADRewardedAd> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { }
                GADRewardedAd.loadWithAdUnitID(placement.iosAdUnitId, requestOptions.toGADRequest()) { ad, error ->
                    if (continuation.isActive) {
                        if (error != null) {
                            continuation.resume(AdAttemptResult.Failure(error.toAdError()))
                        } else if (ad != null) {
                            val weakAd = WeakReference(ad)
                            ad.paidEventHandler = { value ->
                                val strongAd = weakAd.value
                                val adValue = value?.toCommon()
                                if (strongAd != null && adValue != null) {
                                    emit(AdEvent.Paid(placement.id, PaidEvent(placement.id, adValue, strongAd.responseInfo?.toCommon())))
                                }
                            }
                            continuation.resume(AdAttemptResult.Success(ad))
                        } else {
                            continuation.resume(AdAttemptResult.Failure(AdError.message("iOS SDK returned no ad and no error.")))
                        }
                    }
                }
            }
        }

    override suspend fun presentAd(
        loaded: GADRewardedAd,
        options: FullScreenAdOptions,
        presentation: FullScreenPresentationHandle
    ): AdShowResult = withContext(Dispatchers.Main.immediate) {
        val rootVC = topViewController()
            ?: return@withContext AdShowResult.Failed(AdError.message("No root view controller."))
        options.serverSideVerification?.let { loaded.serverSideVerificationOptions = options.serverSideVerificationOptions() }
        suspendCancellableCoroutine<AdShowResult> { continuation ->
            // Cancellation closes only while the core still owns presentation. Once hand-off
            // wins, this delegate stays retained until the SDK terminal callback closes it.
            continuation.invokeOnCancellation { presentation.closeIfCoreOwned() }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            var reward: AdReward? = null
            val delegate = FullScreenDelegate(
                onOpened = { emit(AdEvent.OpenedFullScreen(placement.id)) },
                onClosed = {
                    if (presentation.close(wasShown = true)) {
                        emit(AdEvent.ClosedFullScreen(placement.id))
                        if (continuation.isActive) {
                            continuation.resume(reward?.let(AdShowResult::Rewarded) ?: AdShowResult.Shown)
                        }
                    }
                },
                onFailedToShow = { error ->
                    if (presentation.close(wasShown = false)) {
                        emit(AdEvent.ShowFailed(placement.id, error))
                        if (continuation.isActive) continuation.resume(AdShowResult.Failed(error))
                    }
                },
                onImpression = { emit(AdEvent.Impression(placement.id)) },
                onClicked = { emit(AdEvent.Clicked(placement.id)) }
            )
            // Hand off before retaining: if cancellation already closed the handle (raced in
            // via invokeOnCancellation between the isActive check above and here), there will
            // be no terminal SDK callback to release this delegate — retaining it regardless
            // would leak the entry in the delegate store until the slot is next reused.
            if (presentation.tryHandOffToCallbacks()) {
                delegates.retain(loaded, delegate)
                loaded.fullScreenContentDelegate = delegate
                loaded.presentFromRootViewController(rootVC) {
                    val adReward = loaded.adReward
                    if (adReward != null) {
                        // amount is an NSDecimalNumber and may be fractional (P1-14 established
                        // the exact-decimal pattern for paid values; reuse it here so a mediated
                        // 0.5/2.5 reward is preserved exactly rather than rounded to 1/3).
                        val earned = AdReward(adReward.amount.toValueMicros(), adReward.type)
                        reward = earned
                        emit(AdEvent.RewardEarned(placement.id, earned))
                    }
                }
            }
        }
    }

    override fun destroyAd(ad: GADRewardedAd) {
        delegates.release(ad)
    }

    override fun getResponseInfo(ad: GADRewardedAd): AdResponseInfo? = ad.responseInfo?.toCommon()

    override fun canPresent(): AdError? = if (topViewController() != null) null else AdError.message("No root view controller.")
}

internal class IosRewardedInterstitialSlot(
    placement: AdPlacement,
    globalEvents: MutableSharedFlow<AdEvent>,
    adRequestBlockedError: () -> AdError?,
    onPresentationChanged: (Int) -> Unit,
    arbiter: FullScreenPresentationArbiter
) : FullScreenSlotCore<GADRewardedInterstitialAd>(
    placement,
    globalEvents,
    adRequestBlockedError,
    onPresentationChanged = onPresentationChanged,
    arbiter = arbiter
), RewardedInterstitialAdController {
    private val delegates = FullScreenDelegateStore<GADRewardedInterstitialAd>()

    override suspend fun loadAd(requestOptions: AdRequestOptions): AdAttemptResult<GADRewardedInterstitialAd> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { }
                GADRewardedInterstitialAd.loadWithAdUnitID(placement.iosAdUnitId, requestOptions.toGADRequest()) { ad, error ->
                    if (continuation.isActive) {
                        if (error != null) {
                            continuation.resume(AdAttemptResult.Failure(error.toAdError()))
                        } else if (ad != null) {
                            val weakAd = WeakReference(ad)
                            ad.paidEventHandler = { value ->
                                val strongAd = weakAd.value
                                val adValue = value?.toCommon()
                                if (strongAd != null && adValue != null) {
                                    emit(AdEvent.Paid(placement.id, PaidEvent(placement.id, adValue, strongAd.responseInfo?.toCommon())))
                                }
                            }
                            continuation.resume(AdAttemptResult.Success(ad))
                        } else {
                            continuation.resume(AdAttemptResult.Failure(AdError.message("iOS SDK returned no ad and no error.")))
                        }
                    }
                }
            }
        }

    override suspend fun presentAd(
        loaded: GADRewardedInterstitialAd,
        options: FullScreenAdOptions,
        presentation: FullScreenPresentationHandle
    ): AdShowResult = withContext(Dispatchers.Main.immediate) {
        val rootVC = topViewController()
            ?: return@withContext AdShowResult.Failed(AdError.message("No root view controller."))
        options.serverSideVerification?.let { loaded.serverSideVerificationOptions = options.serverSideVerificationOptions() }
        suspendCancellableCoroutine<AdShowResult> { continuation ->
            // Cancellation closes only while the core still owns presentation. Once hand-off
            // wins, this delegate stays retained until the SDK terminal callback closes it.
            continuation.invokeOnCancellation { presentation.closeIfCoreOwned() }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            var reward: AdReward? = null
            val delegate = FullScreenDelegate(
                onOpened = { emit(AdEvent.OpenedFullScreen(placement.id)) },
                onClosed = {
                    if (presentation.close(wasShown = true)) {
                        emit(AdEvent.ClosedFullScreen(placement.id))
                        if (continuation.isActive) {
                            continuation.resume(reward?.let(AdShowResult::Rewarded) ?: AdShowResult.Shown)
                        }
                    }
                },
                onFailedToShow = { error ->
                    if (presentation.close(wasShown = false)) {
                        emit(AdEvent.ShowFailed(placement.id, error))
                        if (continuation.isActive) continuation.resume(AdShowResult.Failed(error))
                    }
                },
                onImpression = { emit(AdEvent.Impression(placement.id)) },
                onClicked = { emit(AdEvent.Clicked(placement.id)) }
            )
            // Hand off before retaining: if cancellation already closed the handle (raced in
            // via invokeOnCancellation between the isActive check above and here), there will
            // be no terminal SDK callback to release this delegate — retaining it regardless
            // would leak the entry in the delegate store until the slot is next reused.
            if (presentation.tryHandOffToCallbacks()) {
                delegates.retain(loaded, delegate)
                loaded.fullScreenContentDelegate = delegate
                loaded.presentFromRootViewController(rootVC) {
                    val adReward = loaded.adReward
                    if (adReward != null) {
                        // amount is an NSDecimalNumber and may be fractional (P1-14 established
                        // the exact-decimal pattern for paid values; reuse it here so a mediated
                        // 0.5/2.5 reward is preserved exactly rather than rounded to 1/3).
                        val earned = AdReward(adReward.amount.toValueMicros(), adReward.type)
                        reward = earned
                        emit(AdEvent.RewardEarned(placement.id, earned))
                    }
                }
            }
        }
    }

    override fun destroyAd(ad: GADRewardedInterstitialAd) {
        delegates.release(ad)
    }

    override fun getResponseInfo(ad: GADRewardedInterstitialAd): AdResponseInfo? = ad.responseInfo?.toCommon()

    override fun canPresent(): AdError? = if (topViewController() != null) null else AdError.message("No root view controller.")
}

internal class IosAppOpenSlot(
    placement: AdPlacement,
    globalEvents: MutableSharedFlow<AdEvent>,
    adRequestBlockedError: () -> AdError?,
    onPresentationChanged: (Int) -> Unit,
    arbiter: FullScreenPresentationArbiter
) : FullScreenSlotCore<GADAppOpenAd>(
    placement,
    globalEvents,
    adRequestBlockedError,
    onPresentationChanged = onPresentationChanged,
    arbiter = arbiter
), AppOpenAdController {
    private val delegates = FullScreenDelegateStore<GADAppOpenAd>()

    override fun ttl(): Duration = placement.cachePolicy.expirationPolicy.appOpenTtl

    override suspend fun loadAd(requestOptions: AdRequestOptions): AdAttemptResult<GADAppOpenAd> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { }
                GADAppOpenAd.loadWithAdUnitID(placement.iosAdUnitId, requestOptions.toGADRequest()) { ad, error ->
                    if (continuation.isActive) {
                        if (error != null) {
                            continuation.resume(AdAttemptResult.Failure(error.toAdError()))
                        } else if (ad != null) {
                            val weakAd = WeakReference(ad)
                            ad.paidEventHandler = { value ->
                                val strongAd = weakAd.value
                                val adValue = value?.toCommon()
                                if (strongAd != null && adValue != null) {
                                    emit(AdEvent.Paid(placement.id, PaidEvent(placement.id, adValue, strongAd.responseInfo?.toCommon())))
                                }
                            }
                            continuation.resume(AdAttemptResult.Success(ad))
                        } else {
                            continuation.resume(AdAttemptResult.Failure(AdError.message("iOS SDK returned no ad and no error.")))
                        }
                    }
                }
            }
        }

    override suspend fun presentAd(
        loaded: GADAppOpenAd,
        options: FullScreenAdOptions,
        presentation: FullScreenPresentationHandle
    ): AdShowResult = withContext(Dispatchers.Main.immediate) {
        val rootVC = topViewController()
            ?: return@withContext AdShowResult.Failed(AdError.message("No root view controller."))
        suspendCancellableCoroutine<AdShowResult> { continuation ->
            // Cancellation closes only while the core still owns presentation. Once hand-off
            // wins, this delegate stays retained until the SDK terminal callback closes it.
            continuation.invokeOnCancellation { presentation.closeIfCoreOwned() }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            val delegate = FullScreenDelegate(
                onOpened = { emit(AdEvent.OpenedFullScreen(placement.id)) },
                onClosed = {
                    if (presentation.close(wasShown = true)) {
                        emit(AdEvent.ClosedFullScreen(placement.id))
                        if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                    }
                },
                onFailedToShow = { error ->
                    if (presentation.close(wasShown = false)) {
                        emit(AdEvent.ShowFailed(placement.id, error))
                        if (continuation.isActive) continuation.resume(AdShowResult.Failed(error))
                    }
                },
                onImpression = { emit(AdEvent.Impression(placement.id)) },
                onClicked = { emit(AdEvent.Clicked(placement.id)) }
            )
            // Hand off before retaining: if cancellation already closed the handle (raced in
            // via invokeOnCancellation between the isActive check above and here), there will
            // be no terminal SDK callback to release this delegate — retaining it regardless
            // would leak the entry in the delegate store until the slot is next reused.
            if (presentation.tryHandOffToCallbacks()) {
                delegates.retain(loaded, delegate)
                loaded.fullScreenContentDelegate = delegate
                loaded.presentFromRootViewController(rootVC)
            }
        }
    }

    override fun destroyAd(ad: GADAppOpenAd) {
        delegates.release(ad)
    }

    override fun getResponseInfo(ad: GADAppOpenAd): AdResponseInfo? = ad.responseInfo?.toCommon()

    override fun canPresent(): AdError? = if (topViewController() != null) null else AdError.message("No root view controller.")
}

internal class FullScreenDelegate(
    private val onOpened: () -> Unit,
    private val onClosed: () -> Unit,
    private val onFailedToShow: (AdError) -> Unit,
    private val onImpression: () -> Unit,
    private val onClicked: () -> Unit
) : NSObject(), GADFullScreenContentDelegateProtocol {
    override fun adWillPresentFullScreenContent(ad: GADFullScreenPresentingAdProtocol) {
        onOpened()
    }

    override fun adDidDismissFullScreenContent(ad: GADFullScreenPresentingAdProtocol) {
        onClosed()
    }

    override fun ad(ad: GADFullScreenPresentingAdProtocol, didFailToPresentFullScreenContentWithError: NSError) {
        onFailedToShow(didFailToPresentFullScreenContentWithError.toAdError())
    }

    override fun adDidRecordImpression(ad: GADFullScreenPresentingAdProtocol) {
        onImpression()
    }

    override fun adDidRecordClick(ad: GADFullScreenPresentingAdProtocol) {
        onClicked()
    }
}

/** GMA full-screen delegates are weak; retain each delegate with the exact ad it belongs to. */
private class FullScreenDelegateStore<AdT : Any> {
    private val lock = NSRecursiveLock()
    private val entries = mutableListOf<Entry<AdT>>()

    fun retain(ad: AdT, delegate: GADFullScreenContentDelegateProtocol) = locked {
        entries.removeAll { it.ad === ad }
        entries += Entry(ad, delegate)
    }

    fun release(ad: AdT) = locked {
        entries.removeAll { it.ad === ad }
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    private data class Entry<AdT : Any>(
        val ad: AdT,
        val delegate: GADFullScreenContentDelegateProtocol
    )
}
