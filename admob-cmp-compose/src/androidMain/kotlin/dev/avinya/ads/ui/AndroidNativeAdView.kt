@file:OptIn(dev.avinya.ads.InternalAdMobCmpApi::class)

package dev.avinya.ads.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import dev.avinya.ads.AdError
import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.nativead.AndroidNativeAdRenderLease
import dev.avinya.ads.nativead.NativeAdSession
import dev.avinya.ads.nativead.NativeAdSlotState
import dev.avinya.ads.nativead.acquireAndroidRenderLease
import dev.avinya.ads.nativead.layout.AdLayout
import dev.avinya.ads.nativead.rendering.AndroidNativeAdLayoutRenderer

@Composable
public actual fun NativeAdView(
    session: NativeAdSession,
    slotKey: String,
    placement: AdPlacement,
    layout: AdLayout,
    modifier: Modifier,
    loading: @Composable () -> Unit,
    failure: @Composable (AdError) -> Unit,
    onEvent: (AdEvent) -> Unit,
) {
    if (!placement.enabled || placement.format != AdFormat.Native || layout.validation.errors.isNotEmpty()) {
        loading()
        return
    }

    val slotState = session.state.collectAsState().value.slots[slotKey]
    val rendererId = remember(session, slotKey) { "android-native-renderer-${nextAndroidRendererId++}" }
    var lease by remember(session, slotKey, placement, rendererId) { mutableStateOf<AndroidNativeAdRenderLease?>(null) }
    val manager = LocalAdManager.current
    val currentLease by rememberUpdatedState(lease)
    val currentOnEvent by rememberUpdatedState(onEvent)

    LaunchedEffect(manager, placement.id) {
        manager.events.collect { event ->
            if (isNativeEventForLease(placement.id, currentLease?.adInstanceId, event)) {
                currentOnEvent(event)
            }
        }
    }

    LaunchedEffect(session, slotKey, placement, rendererId, slotState) {
        if (slotState.canRenderNativeAd()) {
            if (lease == null) lease = session.acquireAndroidRenderLease(slotKey, placement, rendererId)
        } else {
            lease = null
        }
    }

    DisposableEffect(lease) {
        val leaseToRelease = lease
        onDispose { leaseToRelease?.release() }
    }

    when (slotState) {
        is NativeAdSlotState.Failed -> failure(slotState.error)
        is NativeAdSlotState.Ready, is NativeAdSlotState.Retained, is NativeAdSlotState.Mounted -> {
            val mountedLease = lease
            if (mountedLease == null) {
                loading()
            } else {
                key(mountedLease.adInstanceId, layout.identity) {
                    AndroidView(
                        factory = { context ->
                            AndroidNativeAdLayoutRenderer(context, mountedLease.ad).render(layout)
                        },
                        onRelease = ::releaseAndroidNativeAdHost,
                        modifier = modifier,
                    )
                }
            }
        }
        else -> loading()
    }
}

/** Clears Compose-owned Android host state; the coordinator alone destroys NativeAd. */
internal fun releaseAndroidNativeAdHost(view: NativeAdView) {
    AndroidNativeHostRelease(
        clearAssets = {
            view.headlineView = null
            view.bodyView = null
            view.callToActionView = null
            view.iconView = null
            view.advertiserView = null
            view.priceView = null
            view.storeView = null
            view.starRatingView = null
        },
        clearChildren = view::removeAllViews,
        destroyHost = view::destroy,
    ).release()
}

internal class AndroidNativeHostRelease(
    private val clearAssets: () -> Unit,
    private val clearChildren: () -> Unit,
    private val destroyHost: () -> Unit,
) {
    private var released = false
    fun release() {
        if (released) return
        released = true
        clearAssets()
        clearChildren()
        destroyHost()
    }
}

internal fun isNativeEventForLease(
    placementId: String,
    adInstanceId: String?,
    event: AdEvent,
): Boolean = adInstanceId != null && event.placementId == placementId && event.nativeAdInstanceIdOrNull() == adInstanceId

private fun AdEvent.nativeAdInstanceIdOrNull(): String? = when (this) {
    is AdEvent.Impression -> adInstanceId
    is AdEvent.Clicked -> adInstanceId
    is AdEvent.Paid -> adInstanceId
    is AdEvent.VideoStarted -> adInstanceId
    is AdEvent.VideoPlayed -> adInstanceId
    is AdEvent.VideoPaused -> adInstanceId
    is AdEvent.VideoEnded -> adInstanceId
    is AdEvent.VideoMuted -> adInstanceId
    else -> null
}

private fun NativeAdSlotState?.canRenderNativeAd(): Boolean =
    this is NativeAdSlotState.Ready || this is NativeAdSlotState.Retained || this is NativeAdSlotState.Mounted

private var nextAndroidRendererId: Long = 0
