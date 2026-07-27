@file:OptIn(InternalAdMobCmpApi::class)
package dev.avinya.ads.ui

import dev.avinya.ads.InternalAdMobCmpApi
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
import androidx.compose.ui.viewinterop.AndroidView
import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdLogger
import dev.avinya.ads.AdManagerStatus
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.nativead.NativeAdToken
import dev.avinya.ads.nativead.peekAndroidNativeAd
import dev.avinya.ads.nativead.layout.AdLayout
import dev.avinya.ads.nativead.rendering.AndroidNativeAdLayoutRenderer
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import kotlinx.coroutines.flow.filter

// See KDoc on the expect declaration in commonMain (ui/NativeAdView.kt).
@Composable
public actual fun NativeAdView(
    placement: AdPlacement,
    itemKey: String,
    layout: AdLayout,
    modifier: Modifier,
    onEvent: (AdEvent) -> Unit
) {
    if (!placement.enabled || placement.format != AdFormat.Native) {
        AdLogger.w("Android NativeAdView skipped. placement=${placement.id} enabled=${placement.enabled} format=${placement.format}")
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
            "Android NativeAdView refusing to render a layout with ${layout.validation.errors.size} " +
                "validation error(s). placement=${placement.id} itemKey=$itemKey"
        )
        return
    }

    val sdk = LocalAdManager.current
    val status by sdk.status.collectAsState()
    val pool = remember(sdk, placement) { sdk.nativeAd(placement) }
    var token by remember(placement.id, itemKey, layout.identity) { mutableStateOf<NativeAdToken?>(null) }
    var nativeAd by remember(placement.id, itemKey, layout.identity) { mutableStateOf<NativeAd?>(null) }

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
        AdLogger.d("Android NativeAdView effect. placement=${placement.id} itemKey=$itemKey status=$status token=${token?.tokenId} layout=${layout.identity}")
        if (status != AdManagerStatus.Ready) {
            AdLogger.w("Android NativeAdView waiting for SDK Ready. placement=${placement.id} status=$status")
            return@LaunchedEffect
        }
        if (token == null) {
            val state = pool.preload(placement.cachePolicy.maxSize, placement.requestOptions, placement.nativeOptions)
            AdLogger.i("Android NativeAdView preload finished. placement=${placement.id} state=$state")
            val acquired = pool.acquire()
            token = acquired
            nativeAd = acquired?.let(pool::peekAndroidNativeAd)
            AdLogger.i("Android NativeAdView acquired. placement=${placement.id} token=${acquired?.tokenId} nativeAdFound=${nativeAd != null}")
        }
    }

    val currentToken = token
    val currentNativeAd = nativeAd
    if (currentToken != null && currentNativeAd != null) {
        key(currentToken.tokenId, layout.identity) {
            AndroidView(
                factory = { context ->
                    AdLogger.d("Android NativeAdView factory render. placement=${placement.id} itemKey=$itemKey token=${currentToken.tokenId} layout=${layout.identity}")
                    AndroidNativeAdLayoutRenderer(context, currentNativeAd).render(layout)
                },
                update = {
                    // Do not rebuild or re-register NativeAdView during normal recomposition.
                    // The SDK-owned view tree is recreated only when token or layout identity changes.
                },
                modifier = modifier
            )
        }
    }

    DisposableEffect(pool, currentToken?.tokenId) {
        val tokenToRelease = currentToken
        onDispose {
            AdLogger.d("Android NativeAdView dispose. placement=${placement.id} itemKey=$itemKey token=${tokenToRelease?.tokenId}")
            tokenToRelease?.let(pool::release)
        }
    }
}
