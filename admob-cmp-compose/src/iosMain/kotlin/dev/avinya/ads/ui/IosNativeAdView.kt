@file:OptIn(dev.avinya.ads.InternalAdMobCmpApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.avinya.ads.ui

import GoogleMobileAds.GADNativeAdView
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import dev.avinya.ads.AdError
import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.nativead.IosNativeAdRenderLease
import dev.avinya.ads.nativead.NativeAdSession
import dev.avinya.ads.nativead.NativeAdSlotState
import dev.avinya.ads.nativead.acquireIosNativeAdRenderLease
import dev.avinya.ads.nativead.layout.AdLayout
import dev.avinya.ads.nativead.rendering.IosNativeAdRenderer
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UILayoutPriorityFittingSizeLevel
import platform.UIKit.UILayoutPriorityRequired
import platform.UIKit.UIView
import platform.UIKit.UIViewController

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
    val rendererId = remember(session, slotKey) { "ios-native-renderer-${nextIosRendererId++}" }
    var lease by remember(session, slotKey, placement, rendererId) { mutableStateOf<IosNativeAdRenderLease?>(null) }
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
            if (lease == null) lease = session.acquireIosNativeAdRenderLease(slotKey, placement, rendererId)
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
                var preferredHeight by remember(mountedLease.adInstanceId, layout.identity) { mutableDoubleStateOf(1.0) }
                key(mountedLease.adInstanceId, layout.identity) {
                    UIKitViewController(
                        factory = {
                            val nativeView = GADNativeAdView()
                            nativeView.translatesAutoresizingMaskIntoConstraints = false
                            val content = IosNativeAdRenderer(mountedLease.ad, nativeView).render(layout.root)
                            nativeView.addSubview(content)
                            content.leadingAnchor.constraintEqualToAnchor(nativeView.leadingAnchor).active = true
                            content.trailingAnchor.constraintEqualToAnchor(nativeView.trailingAnchor).active = true
                            content.topAnchor.constraintEqualToAnchor(nativeView.topAnchor).active = true
                            IosNativeAdHostController(nativeView, content, mountedLease.ad) { preferredHeight = it }
                        },
                        onRelease = { it.releaseHost() },
                        modifier = modifier.then(Modifier.height(preferredHeight.dp)),
                        properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
                    )
                }
            }
        }
        else -> loading()
    }
}

private class IosNativeAdHostController(
    private val nativeView: GADNativeAdView,
    private val content: UIView,
    private val nativeAd: GoogleMobileAds.GADNativeAd,
    private val onPreferredHeightChanged: (Double) -> Unit,
) : UIViewController(nibName = null, bundle = null) {
    private var nativeAdRegistered = false
    private val containmentConstraint: NSLayoutConstraint = content.bottomAnchor.constraintLessThanOrEqualToAnchor(nativeView.bottomAnchor)
    private val hostRelease = IosNativeHostRelease(
        detachNativeAd = { nativeView.nativeAd = null },
        clearAssets = {
            nativeView.headlineView = null
            nativeView.bodyView = null
            nativeView.callToActionView = null
            nativeView.iconView = null
            nativeView.mediaView = null
            nativeView.advertiserView = null
            nativeView.priceView = null
            nativeView.storeView = null
            nativeView.starRatingView = null
            nativeView.adChoicesView = null
            nativeView.subviews.forEach { (it as? UIView)?.removeFromSuperview() }
        },
        releaseController = { view.removeFromSuperview() },
    )

    init {
        nativeView.clipsToBounds = true
        view.addSubview(nativeView)
        nativeView.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor).active = true
        nativeView.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor).active = true
        nativeView.topAnchor.constraintEqualToAnchor(view.topAnchor).active = true
        nativeView.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor).active = true
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if (nativeAdRegistered) return
        val (width, currentHeight) = view.bounds.useContents { size.width to size.height }
        if (!width.isFinite() || width <= 0.0) return
        val measuredHeight = content.systemLayoutSizeFittingSize(
            targetSize = CGSizeMake(width, 0.0),
            withHorizontalFittingPriority = UILayoutPriorityRequired,
            verticalFittingPriority = UILayoutPriorityFittingSizeLevel,
        ).useContents { height }
        val sizing = resolveNativeAdLayoutSizing(currentHeight, measuredHeight)
        if (sizing.shouldUpdateHeight) {
            onPreferredHeightChanged(measuredHeight)
        } else if (sizing.shouldRegisterNativeAd) {
            containmentConstraint.active = true
            view.layoutIfNeeded()
            nativeView.layoutIfNeeded()
            if (registeredAssetContainmentIssues().isNotEmpty()) return
            nativeView.nativeAd = nativeAd
            nativeAdRegistered = true
        }
    }

    fun releaseHost() = hostRelease.release()

    private fun registeredAssetContainmentIssues(): List<String> {
        val root = nativeView.bounds.toRectSnapshot()
        val assets = listOfNotNull(
            nativeView.headlineView?.let { "headline" to it },
            nativeView.bodyView?.let { "body" to it },
            nativeView.callToActionView?.let { "callToAction" to it },
            nativeView.iconView?.let { "icon" to it },
            nativeView.mediaView?.let { "media" to it },
            nativeView.advertiserView?.let { "advertiser" to it },
            nativeView.priceView?.let { "price" to it },
            nativeView.storeView?.let { "store" to it },
            nativeView.starRatingView?.let { "starRating" to it },
            nativeView.adChoicesView?.let { "adChoices" to it },
        )
        return assets.mapNotNull { (name, asset) ->
            val bounds = nativeView.convertRect(asset.bounds, fromView = asset).toRectSnapshot()
            "$name=$bounds root=$root".takeUnless { root.contains(bounds) }
        }
    }
}

private data class IosNativeRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun contains(other: IosNativeRect, tolerance: Double = 0.5): Boolean =
        other.x >= x - tolerance && other.y >= y - tolerance &&
            other.x + other.width <= x + width + tolerance &&
            other.y + other.height <= y + height + tolerance
}

private fun kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect>.toRectSnapshot(): IosNativeRect = useContents {
    IosNativeRect(origin.x, origin.y, size.width, size.height)
}

/** Clears only Compose's iOS host; the coordinator retains delegates and destroys GADNativeAd. */
internal class IosNativeHostRelease(
    private val detachNativeAd: () -> Unit,
    private val clearAssets: () -> Unit,
    private val releaseController: () -> Unit,
) {
    private var released = false
    fun release() {
        if (released) return
        released = true
        detachNativeAd()
        clearAssets()
        releaseController()
    }
}

internal data class IosNativeAdLayoutSizing(
    val shouldUpdateHeight: Boolean,
    val shouldRegisterNativeAd: Boolean,
)

internal fun resolveNativeAdLayoutSizing(currentHeight: Double, measuredHeight: Double): IosNativeAdLayoutSizing {
    if (!measuredHeight.isFinite() || measuredHeight <= 0.0 || !currentHeight.isFinite() || currentHeight <= 0.0) {
        return IosNativeAdLayoutSizing(false, false)
    }
    return if (kotlin.math.abs(currentHeight - measuredHeight) > 0.5) {
        IosNativeAdLayoutSizing(true, false)
    } else {
        IosNativeAdLayoutSizing(false, true)
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

private var nextIosRendererId: Long = 0
