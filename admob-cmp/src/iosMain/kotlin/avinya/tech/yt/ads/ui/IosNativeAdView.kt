package avinya.tech.yt.ads.ui

import GoogleMobileAds.GADNativeAdView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import avinya.tech.yt.ads.AdEvent
import avinya.tech.yt.ads.AdFormat
import avinya.tech.yt.ads.AdLogger
import avinya.tech.yt.ads.AdManagerStatus
import avinya.tech.yt.ads.AdPlacement
import avinya.tech.yt.ads.IosAdMob
import avinya.tech.yt.ads.LocalAdManager
import avinya.tech.yt.ads.nativead.NativeAdToken
import avinya.tech.yt.ads.nativead.layout.AdLayout
import avinya.tech.yt.ads.nativead.peekIosNativeAd
import avinya.tech.yt.ads.nativead.rendering.IosNativeAdRenderer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.filter
import platform.UIKit.UIViewController

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
            .collect(onEvent)
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
                    content.bottomAnchor.constraintLessThanOrEqualToAnchor(nativeView.bottomAnchor).active = true
                    nativeView.nativeAd = nativeAd
                    val vc = UIViewController()
                    vc.view.addSubview(nativeView)
                    nativeView.leadingAnchor.constraintEqualToAnchor(vc.view.leadingAnchor).active = true
                    nativeView.trailingAnchor.constraintEqualToAnchor(vc.view.trailingAnchor).active = true
                    nativeView.topAnchor.constraintEqualToAnchor(vc.view.topAnchor).active = true
                    nativeView.bottomAnchor.constraintEqualToAnchor(vc.view.bottomAnchor).active = true
                    vc
                },
                modifier = modifier,
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
