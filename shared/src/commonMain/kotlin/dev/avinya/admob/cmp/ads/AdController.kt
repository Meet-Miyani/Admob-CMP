package dev.avinya.admob.cmp.ads

/**
 * App-level ad abstraction for the demo.
 *
 * The `admob-cmp` library only ships Android and iOS targets, so the shared module cannot
 * depend on it from common / web / desktop code. This interface is the app's own seam:
 * Android and iOS back it with admob-cmp's `AdManager`, while web and desktop — where AdMob
 * has no SDK — use [NoOpAdController].
 */
interface AdController {
    /** Whether real ads are available on the current platform. */
    val adsSupported: Boolean

    /** Begin loading an interstitial for [placementId]. No-op where ads are unsupported. */
    fun loadInterstitial(placementId: String)

    /** Show a previously loaded interstitial for [placementId]. No-op where unsupported. */
    fun showInterstitial(placementId: String)
}

/**
 * [AdController] for platforms without an AdMob SDK (web, desktop). Every operation is a
 * no-op and [adsSupported] is `false`, so ad-driven UI can degrade gracefully.
 */
open class NoOpAdController : AdController {
    override val adsSupported: Boolean = false
    override fun loadInterstitial(placementId: String) {}
    override fun showInterstitial(placementId: String) {}
}

/** Returns the [AdController] for the current platform. */
expect fun getAdController(): AdController
