@file:OptIn(InternalAdMobCmpApi::class)
package dev.avinya.ads.ui

import dev.avinya.ads.InternalAdMobCmpApi
import GoogleMobileAds.GADNativeAdView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdLogger
import dev.avinya.ads.AdManagerStatus
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.IosAdMob
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.nativead.NativeAdToken
import dev.avinya.ads.nativead.layout.AdLayout
import dev.avinya.ads.nativead.peekIosNativeAd
import dev.avinya.ads.nativead.rendering.IosNativeAdRenderer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.filter
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UILayoutPriorityFittingSizeLevel
import platform.UIKit.UILayoutPriorityRequired
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import kotlin.math.abs

// See KDoc on the expect declaration in commonMain (ui/NativeAdView.kt).
@OptIn(ExperimentalForeignApi::class)
@Composable
public actual fun NativeAdView(
    placement: AdPlacement,
    itemKey: String,
    layout: AdLayout,
    modifier: Modifier,
    onEvent: (AdEvent) -> Unit
) {
    if (!placement.enabled || placement.format != AdFormat.Native) {
        AdLogger.w("iOS NativeAdView skipped. placement=${placement.id} enabled=${placement.enabled} format=${placement.format}")
        return
    }

    layout.validation.errors.forEach { issue ->
        AdLogger.e("Native layout validation error [${issue.code}] at ${issue.nodePath}: ${issue.message}")
    }
    layout.validation.warnings.forEach { issue ->
        AdLogger.w("Native layout validation warning [${issue.code}] at ${issue.nodePath}: ${issue.message}")
    }
    // P1-17: errors were logged and then rendering continued anyway, so a layout missing
    // every renderable asset still ran preload/acquire and registered an empty view tree with
    // the SDK — consuming inventory while violating the validator's own "errors prevent
    // rendering" contract. Warnings still render; only errors stop here.
    if (layout.validation.errors.isNotEmpty()) {
        AdLogger.e(
            "iOS NativeAdView refusing to render a layout with ${layout.validation.errors.size} " +
                "validation error(s). placement=${placement.id} itemKey=$itemKey"
        )
        return
    }

    val sdk = LocalAdManager.current
    if (sdk !== IosAdMob.manager) {
        AdLogger.w("iOS NativeAdView skipped because LocalAdManager is not IosAdMob.manager. placement=${placement.id}")
        return
    }

    val status by sdk.status.collectAsState()
    val pool = remember(sdk, placement) { sdk.nativeAd(placement) }
    var token by remember(placement.id, itemKey, layout.identity) { mutableStateOf<NativeAdToken?>(null) }
    var nativeContentHeight by remember(placement.id, itemKey, layout.identity) {
        mutableDoubleStateOf(1.0)
    }

    val currentOnEvent = rememberCurrentEventCallback(onEvent)

    LaunchedEffect(pool) {
        // collect, not collectLatest: collectLatest cancels the in-flight onEvent when a
        // new event arrives, so a rapid Impression -> Click silently dropped the impression.
        // onEvent is a plain callback with nothing to cancel.
        //
        // P1-8: every row on a shared native placement collects the SAME pool-wide events
        // flow. Impression/Clicked/Paid now carry the ad-instance id they belong to (see
        // AdEvent.Impression.adInstanceId), so filter out ones that aren't for THIS row's
        // currently-leased ad. token is read fresh on every emission (not captured once),
        // so this stays correct as the row acquires/loses its lease over its lifetime.
        // An event with a null adInstanceId (Loaded/LoadFailed — pool-wide, not
        // per-instance) is never filtered.
        pool.events
            .filter { event ->
                val instanceId = when (event) {
                    is AdEvent.Impression -> event.adInstanceId
                    is AdEvent.Clicked -> event.adInstanceId
                    is AdEvent.Paid -> event.adInstanceId
                    else -> null
                }
                instanceId == null || instanceId == token?.tokenId
            }
            .collect(currentOnEvent)
    }

    val availableAds by pool.availableAds.collectAsState()

    // Keyed on availableAds so a row that lost the race — or was blocked because maxSize was
    // fully leased — retries when inventory frees up, instead of rendering blank forever.
    // maxSize counts available + in-use, so release() frees a SLOT without incrementing
    // availableAds; re-running preload, not just acquire, is what refills it.
    //
    // Every mounted row re-runs this on each availability change, so with N rows and one ad,
    // N-1 will preload (a no-op via the core's cache check) and acquire (null). preload is
    // serialized by the core's loadMutex, so that is a thundering herd on a mutex, not on the
    // network. Judged acceptable: the alternative is a suspending lease queue, which the
    // resolved design decision excludes. If it ever shows up in a profile, the mitigation is a
    // small random stagger before acquire, not a redesign.
    LaunchedEffect(pool, itemKey, layout.identity, status, availableAds) {
        AdLogger.d("iOS NativeAdView effect. placement=${placement.id} itemKey=$itemKey status=$status token=${token?.tokenId} layout=${layout.identity}")
        if (status != AdManagerStatus.Ready) {
            AdLogger.w("iOS NativeAdView waiting for SDK Ready. placement=${placement.id} status=$status")
            return@LaunchedEffect
        }
        if (token == null) {
            val state = pool.preload(placement.cachePolicy.maxSize, placement.requestOptions, placement.nativeOptions)
            AdLogger.i("iOS NativeAdView preload finished. placement=${placement.id} state=$state")
            token = pool.acquire()
            AdLogger.i("iOS NativeAdView acquired. placement=${placement.id} token=${token?.tokenId}")
        }
    }

    val currentToken = token
    if (currentToken != null) {
        key(currentToken.tokenId, layout.identity) {
            UIKitViewController(
                factory = {
                    AdLogger.d("iOS NativeAdView factory render. placement=${placement.id} itemKey=$itemKey token=${currentToken.tokenId} layout=${layout.identity}")
                    val nativeAd = pool.peekIosNativeAd(currentToken)
                        ?: return@UIKitViewController UIViewController()
                    val nativeView = GADNativeAdView()
                    nativeView.translatesAutoresizingMaskIntoConstraints = false
                    val renderer = IosNativeAdRenderer(nativeAd, nativeView)
                    val content = renderer.render(layout.root)
                    nativeView.addSubview(content)
                    content.leadingAnchor.constraintEqualToAnchor(nativeView.leadingAnchor).active = true
                    content.trailingAnchor.constraintEqualToAnchor(nativeView.trailingAnchor).active = true
                    content.topAnchor.constraintEqualToAnchor(nativeView.topAnchor).active = true
                    IosNativeAdHostController(
                        nativeView = nativeView,
                        content = content,
                        registerNativeAd = { nativeView.nativeAd = nativeAd },
                        onPreferredHeightChanged = { measuredHeight ->
                            nativeContentHeight = measuredHeight
                        },
                    )
                },
                modifier = modifier.then(Modifier.height(nativeContentHeight.dp)),
                properties = UIKitInteropProperties(
                    isInteractive = true,
                    isNativeAccessibilityEnabled = true
                )
            )
        }
    }

    DisposableEffect(pool, currentToken?.tokenId) {
        val tokenToRelease = currentToken
        onDispose {
            AdLogger.d("iOS NativeAdView dispose. placement=${placement.id} itemKey=$itemKey token=${tokenToRelease?.tokenId}")
            tokenToRelease?.let(pool::release)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosNativeAdHostController(
    private val nativeView: GADNativeAdView,
    private val content: UIView,
    private val registerNativeAd: () -> Unit,
    private val onPreferredHeightChanged: (Double) -> Unit,
) : UIViewController(nibName = null, bundle = null) {
    private var nativeAdRegistered: Boolean = false
    private val containmentConstraint: NSLayoutConstraint =
        content.bottomAnchor.constraintLessThanOrEqualToAnchor(nativeView.bottomAnchor)

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

        val currentSize = view.bounds.useContents { size.width to size.height }
        val width = currentSize.first
        if (!width.isFinite() || width <= 0.0) return

        val measuredHeight = content.systemLayoutSizeFittingSize(
            targetSize = CGSizeMake(width, 0.0),
            withHorizontalFittingPriority = UILayoutPriorityRequired,
            verticalFittingPriority = UILayoutPriorityFittingSizeLevel,
        ).useContents { height }
        val sizing = resolveNativeAdLayoutSizing(
            currentHeight = currentSize.second,
            measuredHeight = measuredHeight,
        )
        if (sizing.shouldUpdateHeight) {
            AdLogger.d(
                "iOS native host sizing. currentHeight=${currentSize.second} " +
                    "measuredHeight=$measuredHeight"
            )
            onPreferredHeightChanged(measuredHeight)
            return
        }
        if (sizing.shouldRegisterNativeAd) {
            containmentConstraint.active = true
            view.layoutIfNeeded()
            nativeView.layoutIfNeeded()
            val containmentIssues = registeredAssetContainmentIssues()
            if (containmentIssues.isNotEmpty()) {
                AdLogger.e(
                    "iOS native ad registration blocked because asset bounds escape " +
                        "GADNativeAdView: ${containmentIssues.joinToString()}"
                )
                return
            }
            registerNativeAd()
            nativeAdRegistered = true
            AdLogger.d(
                "iOS native ad registered after containment. height=${currentSize.second}"
            )
        }
    }

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
            val converted = nativeView.convertRect(asset.bounds, fromView = asset).toRectSnapshot()
            "$name=$converted root=$root".takeUnless { root.contains(converted) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private data class RectSnapshot(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    fun contains(other: RectSnapshot, tolerance: Double = 0.5): Boolean =
        other.x >= x - tolerance &&
            other.y >= y - tolerance &&
            other.x + other.width <= x + width + tolerance &&
            other.y + other.height <= y + height + tolerance
}

@OptIn(ExperimentalForeignApi::class)
private fun kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect>.toRectSnapshot(): RectSnapshot =
    useContents {
        RectSnapshot(
            x = origin.x,
            y = origin.y,
            width = size.width,
            height = size.height,
        )
    }

internal data class IosNativeAdLayoutSizing(
    val shouldUpdateHeight: Boolean,
    val shouldRegisterNativeAd: Boolean,
)

internal fun resolveNativeAdLayoutSizing(
    currentHeight: Double,
    measuredHeight: Double,
    tolerance: Double = 0.5,
): IosNativeAdLayoutSizing {
    if (!measuredHeight.isFinite() || measuredHeight <= 0.0) {
        return IosNativeAdLayoutSizing(
            shouldUpdateHeight = false,
            shouldRegisterNativeAd = false,
        )
    }
    val heightMatches = currentHeight.isFinite() &&
        currentHeight > 0.0 &&
        abs(currentHeight - measuredHeight) <= tolerance
    return IosNativeAdLayoutSizing(
        shouldUpdateHeight = !heightMatches,
        shouldRegisterNativeAd = heightMatches,
    )
}
