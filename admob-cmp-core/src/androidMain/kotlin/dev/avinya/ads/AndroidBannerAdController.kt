package dev.avinya.ads

import dev.avinya.ads.internal.BannerCore
import dev.avinya.ads.internal.BannerPlatform
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Android banner controller. All policy — generation, attachment refcounting, the load
 * mutex, the swap-on-success and the resolved request — lives in [BannerCore]; this class
 * implements only the SDK-touching primitives of [BannerPlatform] plus a thin
 * [BannerAdController] delegation shell.
 */
internal class AndroidBannerAdController internal constructor(
    override val placement: AdPlacement,
    globalEvents: MutableSharedFlow<AdEvent>,
    private val adRequestBlockedError: () -> AdError?,
    private val activityProvider: () -> android.app.Activity? = { null }
) : BannerAdController, BannerPlatform<BannerAd, AdSize> {

    private val stateLock = Any()
    private val core = BannerCore(placement, this, globalEvents)

    override val loadState: StateFlow<AdLoadState> get() = core.loadState
    override val events: SharedFlow<AdEvent> get() = core.events

    internal fun currentAd(): BannerAd? = core.currentBanner()

    internal fun attach() = core.attach()
    internal fun detach() = core.detach()

    /** Records container geometry without loading — see [BannerCore.registerGeometry]. */
    internal fun registerGeometry(
        geometry: BannerGeometry,
        sizePolicy: AdSizePolicy,
        requestOptions: AdRequestOptions
    ) = core.registerGeometry(geometry, sizePolicy, requestOptions)

    override suspend fun load(
        geometry: BannerGeometry?,
        sizePolicy: AdSizePolicy,
        requestOptions: AdRequestOptions
    ): AdLoadState = core.load(geometry, sizePolicy, requestOptions, adRequestBlockedError)

    override suspend fun refresh(): AdLoadState = core.refresh(adRequestBlockedError)

    override fun clear() = core.clear()

    // --- BannerPlatform ---

    override fun <T> withStateLock(block: () -> T): T = synchronized(stateLock) { block() }

    // Nullability is meaningful: with no current Activity there is no width to resolve, and
    // the core fails the load rather than guessing one.
    override fun fallbackWidthDp(): Int? = activityProvider()?.screenWidthDp()?.coerceAtLeast(1)

    override fun resolveSize(sizePolicy: AdSizePolicy, widthDp: Int): AdSize {
        // Reached only after host geometry or fallbackWidthDp already produced a width, so a
        // null Activity here is a genuine edge case rather than the common path. Fall back to
        // the SDK's fixed size instead of throwing.
        val activity = activityProvider() ?: return AdSize(widthDp, 50)
        return sizePolicy.toAndroidAdSize(activity, widthDp)
    }

    override fun destroy(banner: BannerAd) = banner.destroy()

    override fun responseInfo(banner: BannerAd): AdResponseInfo? = banner.getResponseInfo().toCommon()

    override suspend fun loadBanner(
        size: AdSize,
        requestOptions: AdRequestOptions,
        requiredGeneration: Long
    ): AdAttemptResult<BannerAd> = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { }
            val mergedOptions = requestOptions.withCollapsible(placement.bannerSizePolicy)
            val request = BannerAdRequest.Builder(placement.androidAdUnitId, size)
                .applyOptions(mergedOptions)
                .build()
            BannerAd.load(request, object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    if (!continuation.isActive) {
                        ad.destroy()
                        return
                    }
                    ad.adEventCallback = object : BannerAdEventCallback {
                        override fun onAdImpression() {
                            core.emitPlatformEvent(AdEvent.Impression(placement.id))
                        }

                        override fun onAdClicked() {
                            core.emitPlatformEvent(AdEvent.Clicked(placement.id))
                        }

                        override fun onAdPaid(value: com.google.android.libraries.ads.mobile.sdk.common.AdValue) {
                            core.emitPlatformEvent(
                                AdEvent.Paid(
                                    placement.id,
                                    PaidEvent(placement.id, value.toCommon(), ad.getResponseInfo().toCommon())
                                )
                            )
                        }
                    }
                    // The core publishes Loaded/_loadState after the banner is swapped, so the
                    // composable mirrors the NEW ad.
                    continuation.resume(
                        AdAttemptResult.Success(ad),
                        onCancellation = { _, _, _ -> ad.destroy() }
                    )
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    if (continuation.isActive) continuation.resume(AdAttemptResult.Failure(adError.toAdError()))
                }
            })
        }
    }
}

public fun BannerAdController.currentAndroidBannerAd(): BannerAd? =
    (this as? AndroidBannerAdController)?.currentAd()

public fun BannerAdController.attachAndroidBanner(): Unit {
    (this as? AndroidBannerAdController)?.attach()
}

public fun BannerAdController.detachAndroidBanner(): Unit {
    (this as? AndroidBannerAdController)?.detach()
}

public fun BannerAdController.registerAndroidBannerGeometry(
    geometry: BannerGeometry,
    sizePolicy: AdSizePolicy,
    requestOptions: AdRequestOptions
): Unit {
    (this as? AndroidBannerAdController)?.registerGeometry(geometry, sizePolicy, requestOptions)
}

private fun AdRequestOptions.withCollapsible(sizePolicy: AdSizePolicy): AdRequestOptions {
    val collapsible = when (sizePolicy) {
        is AdSizePolicy.LargeAnchoredAdaptive -> sizePolicy.collapsible
        else -> null
    } ?: return this
    val key = "collapsible"
    val value = when (collapsible) {
        CollapsiblePlacement.Top -> "top"
        CollapsiblePlacement.Bottom -> "bottom"
    }
    if (googleExtras.containsKey(key)) return this
    val merged = googleExtras + (key to value)
    return copy(googleExtras = merged)
}
