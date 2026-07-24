package avinya.tech.yt.ads

import androidx.compose.runtime.Stable
import avinya.tech.yt.ads.internal.emitOrLogDrop
import avinya.tech.yt.ads.internal.FullScreenPresentationArbiter
import avinya.tech.yt.ads.nativead.NativeAdOptions
import avinya.tech.yt.ads.nativead.NativeAdToken
import avinya.tech.yt.ads.nativead.NativeMediaInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Entry point to the ads SDK. Provides controllers for every ad format,
 * consent lifecycle management, diagnostics, and a flow of [AdEvent]s.
 * Obtain the process-wide singleton via [rememberAdManager] (Compose) or
 * `AdMob.manager(context)` (Android, outside Compose).
 *
 * Factory functions ([banner], [interstitial], [rewarded], etc.) return
 * cached controllers per placement id. Reusing the same id with a different
 * [AdPlacement] configuration throws an [IllegalStateException].
 *
 * Controllers are cached for the lifetime of the manager and are **not**
 * evicted automatically. Use a small, finite set of static placement ids — do
 * **not** generate per-item ids (e.g. `"feed_item_$index"`), which would
 * accumulate controllers without bound. For repeating UI (feeds, lists), reuse
 * one placement id across items; the native pool handles per-item ads.
 */
@Stable
public interface AdManager {
    /** Current initialization and consent state of the SDK. */
    public val status: StateFlow<AdManagerStatus>
    /**
     * Shared flow of ad lifecycle events ([AdEvent]) for all formats.
     *
     * **Best-effort delivery, not a durable log.** There is no replay and the buffer is
     * finite, so an event emitted with no collector attached — or during a burst that
     * outruns a slow collector — is dropped. Drops are logged (tag `AdMobCMP`) but not
     * retried.
     *
     * Fine for driving UI. **Do not use as the system of record for billing, revenue, or
     * impression accounting**; reconcile against AdMob reporting instead.
     */
    public val events: SharedFlow<AdEvent>
    /** Consent lifecycle controller (UMP integration). */
    public val consent: ConsentController
    /** Diagnostics and debug tools. */
    public val diagnostics: AdDiagnostics
    /** App Tracking Transparency controller (iOS); a no-op reporting NotApplicable on Android. */
    public val tracking: AdTrackingController

    /**
     * Initializes the Google Mobile Ads SDK with the given [config] and [consentMode].
     *
     * @param config SDK configuration including app ids, test mode, and request defaults.
     * @param consentMode Consent-gathering strategy. [ConsentMode.GatherBeforeInitialize]
     *   requests a UMP update and shows the consent form if required, then initializes
     *   ads if [ConsentController.canRequestAds] is true.
     *   [ConsentMode.InitializeOnlyIfAlreadyAllowed] updates UMP but never shows a form.
     *   [ConsentMode.SkipConsent] bypasses UMP entirely.
     * @return The resulting [AdManagerStatus] after initialization completes.
     */
    public suspend fun initialize(
        config: AdConfig,
        consentMode: ConsentMode = ConsentMode.GatherBeforeInitialize
    ): AdManagerStatus

    /** Returns a [BannerAdController] for [placement], cached by id. */
    public fun banner(placement: AdPlacement): BannerAdController
    /** Returns a [NativeAdPool] for [placement], cached by id. */
    public fun nativeAd(placement: AdPlacement): NativeAdPool
    /** Returns an [InterstitialAdController] for [placement], cached by id. */
    public fun interstitial(placement: AdPlacement): InterstitialAdController
    /** Returns a [RewardedAdController] for [placement], cached by id. */
    public fun rewarded(placement: AdPlacement): RewardedAdController
    /** Returns a [RewardedInterstitialAdController] for [placement], cached by id. */
    public fun rewardedInterstitial(placement: AdPlacement): RewardedInterstitialAdController
    /** Returns an [AppOpenAdController] for [placement], cached by id. */
    public fun appOpen(placement: AdPlacement): AppOpenAdController
}

/**
 * Internal capability: process-wide full-screen presentation arbitration.
 *
 * [isFullScreenPresenting] remains a derived observable for hosts that want to react to a
 * full-screen ad being on screen. It is **no longer the admission mechanism** — reading it and
 * then acting on the result is a TOCTOU, which is exactly the defect this interface's
 * [fullScreenArbiter] closes. Anything deciding *whether it may present* must acquire a token
 * from the arbiter instead.
 *
 * Implemented by the real platform managers and consulted by
 * [avinya.tech.yt.ads.appopen.AppOpenAdCoordinator] so it never stacks an app-open ad on top of
 * another full-screen ad (an AdMob policy violation). Not part of the public API.
 */
internal interface FullScreenPresenceAware {
    /** Derived observable. Safe to render; never gate a presentation decision on it. */
    val isFullScreenPresenting: StateFlow<Boolean>

    /**
     * The single process-wide admission gate. Every full-screen slot owned by this manager
     * acquires from this instance, so a token held here blocks all of them.
     */
    val fullScreenArbiter: FullScreenPresentationArbiter
}

/**
 * Controls the consent lifecycle via Google's User Messaging Platform (UMP).
 * Call [requestConsentInfoUpdate] on every app launch and [gatherConsent] to
 * show the consent form when required. Gate ad requests on [canRequestAds].
 */
@Stable
public interface ConsentController {
    /** Current UMP consent status ([ConsentStatus]). */
    public val status: StateFlow<ConsentStatus>
    /** Whether privacy options must be shown to the user. */
    public val privacyOptionsRequirementStatus: StateFlow<PrivacyOptionsRequirementStatus>
    /** True when ads may be requested based on the current consent state. */
    public val canRequestAds: StateFlow<Boolean>
    /** Requests a consent info update from UMP. Call every app launch. */
    public suspend fun requestConsentInfoUpdate(config: AdConfig): ConsentStatus
    /** Requests an update and shows the consent form if required. */
    public suspend fun gatherConsent(config: AdConfig): ConsentStatus
    /** Shows the privacy options form. Should only be called when [privacyOptionsRequirementStatus] is [PrivacyOptionsRequirementStatus.Required]. */
    public suspend fun showPrivacyOptions(): Boolean
    /** Resets consent state for debug/testing purposes only. */
    public suspend fun resetConsentForDebug(): Boolean
}

/**
 * Diagnostics and debug tools for the Google Mobile Ads SDK.
 */
@Stable
public interface AdDiagnostics {
    /** Opens the Ad Inspector UI (debug tool). Returns true if successful. */
    public suspend fun openAdInspector(): Boolean
    /** Opens the debug menu for [adUnitId]. Returns true if successful. */
    public suspend fun openDebugMenu(adUnitId: String): Boolean
    /**
     * Returns the GMA SDK version string, or `null` if not available.
     *
     * This is an **initialization-time snapshot**, captured on the main thread while
     * the SDK initializes. It returns `null` before initialization completes. The GMA
     * SDK requires main-thread access, and this getter is synchronous with nowhere to
     * hop, so a cached snapshot is how the value is obtained safely.
     */
    public fun sdkVersion(): String?

    /**
     * Returns the initialization status of all ad adapters.
     *
     * This is an **initialization-time snapshot**, captured on the main thread while
     * the SDK initializes. It returns an empty list before initialization completes,
     * and an adapter whose status changes afterwards is not reflected until the
     * manager re-initializes. Accepted because adapter statuses are near-static
     * post-init and this is debug/support tooling, not part of the ad-serving path.
     */
    public fun adapterStatuses(): List<AdapterInitializationStatus>
}

/**
 * Controls a single banner ad placement. Handles loading, refresh, and
 * lifecycle. For Compose UI, prefer [BannerAdView] which manages the
 * controller automatically.
 */
@Stable
public interface BannerAdController {
    /** The placement this controller is bound to. */
    public val placement: AdPlacement
    /** Current load state of the banner ad. */
    public val loadState: StateFlow<AdLoadState>
    /** Shared flow of banner lifecycle events. */
    public val events: SharedFlow<AdEvent>
    /**
     * Loads a banner sized for [geometry] using [sizePolicy] and [requestOptions].
     *
     * [geometry] is host-supplied because the controller is process-cached and has no
     * layout context of its own. Pass `null` only for headless use, where the controller
     * falls back to a platform-provided width; that fallback is best-effort and returns
     * a [AdLoadState.Failed] when the platform cannot determine one.
     *
     * Prefer [BannerAdView], which measures its own container and supplies [geometry]
     * automatically.
     */
    public suspend fun load(
        geometry: BannerGeometry? = null,
        sizePolicy: AdSizePolicy = placement.bannerSizePolicy,
        requestOptions: AdRequestOptions = placement.requestOptions
    ): AdLoadState
    /**
     * Reloads the banner, replaying the geometry, size policy AND request options
     * resolved by the most recent [load]. Fails if nothing has been loaded yet.
     */
    public suspend fun refresh(): AdLoadState
    /** Clears the loaded ad and releases resources. */
    public fun clear()
}

/**
 * Controls a full-screen ad format (interstitial, rewarded, rewarded
 * interstitial, or app-open). Full-screen ads are single-use: [show]
 * destroys the ad after dismissal. Cache and TTL are managed by
 * [AdCachePolicy].
 */
@Stable
public interface FullScreenAdController {
    /** The placement this controller is bound to. */
    public val placement: AdPlacement
    /** Current load state. */
    public val loadState: StateFlow<AdLoadState>
    /** Shared flow of full-screen ad lifecycle events. */
    public val events: SharedFlow<AdEvent>
    /** Returns current availability info (cached count, expiry). */
    public fun availability(): AdAvailability
    /** True when at least one cached ad is ready to show. */
    public fun isReady(): Boolean = availability().isReady
    /** Loads an ad. Suspends until the load completes. */
    public suspend fun load(requestOptions: AdRequestOptions = placement.requestOptions): AdLoadState
    /** Alias for [load]. */
    public suspend fun preload(requestOptions: AdRequestOptions = placement.requestOptions): AdLoadState = load(requestOptions)
    /** Shows the ad. Suspends until the ad is dismissed and returns [AdShowResult]. */
    public suspend fun show(options: FullScreenAdOptions = placement.fullScreenOptions): AdShowResult
    /** Clears cached ads and releases resources. */
    public fun clear()
}

/**
 * Typealias for [FullScreenAdController]. Use this type when you need a
 * slot that can hold any full-screen format (interstitial, rewarded, etc.).
 */
public typealias FullScreenAdSlot = FullScreenAdController

/** Full-screen ad controller for interstitial format. */
@Stable
public interface InterstitialAdController : FullScreenAdController

/** Full-screen ad controller for rewarded video format. */
@Stable
public interface RewardedAdController : FullScreenAdController

/** Full-screen ad controller for rewarded interstitial format. */
@Stable
public interface RewardedInterstitialAdController : FullScreenAdController

/**
 * Full-screen ad controller for app-open format. Shows an ad when the app
 * returns to the foreground. For automated lifecycle management use
 * [AppOpenAdCoordinator].
 */
@Stable
public interface AppOpenAdController : FullScreenAdController {
    /** Shows the ad only if [isReady]. Returns [AdShowResult.NotReady] otherwise. */
    public suspend fun showIfAvailable(options: FullScreenAdOptions = placement.fullScreenOptions): AdShowResult =
        if (isReady()) show(options) else AdShowResult.NotReady
}

/**
 * Manages a pool of preloaded native ads for a single placement. Ads are
 * acquired via [acquire] and must be returned via [release]. The composable
 * [NativeAdView] handles acquire/release automatically. Use the pool directly
 * for manual native ad rendering.
 */
@Stable
public interface NativeAdPool {
    /** The placement this pool is bound to. */
    public val placement: AdPlacement
    /** Current load state of the pool. */
    public val loadState: StateFlow<AdLoadState>
    /** Shared flow of native ad lifecycle events. */
    public val events: SharedFlow<AdEvent>
    /**
     * Number of ads currently available for [acquire], as a live signal.
     *
     * Consumers that render one ad per list row should key their acquisition effect on
     * this so a row that got `null` from [acquire] retries when inventory frees up.
     * Without it, `acquire()` returning null was terminal for that composition: with
     * `cachePolicy.maxSize = 1` and one row holding the ad, every other row rendered
     * blank forever.
     *
     * Note this counts only *available* ads. [AdCachePolicy.maxSize] budgets
     * available + in-use, so releasing a lease frees a slot without incrementing this
     * value — the next [preload] is what refills it.
     */
    public val availableAds: StateFlow<Int>
    /** Preloads [count] native ads. */
    public suspend fun preload(
        count: Int = placement.cachePolicy.maxSize,
        requestOptions: AdRequestOptions = placement.requestOptions,
        nativeOptions: NativeAdOptions = placement.nativeOptions
    ): AdLoadState
    /** Acquires a [NativeAdToken] from the pool, or null if none available. Every acquire must be paired with a [release]. */
    public fun acquire(): NativeAdToken?
    /**
     * Releases a previously acquired [NativeAdToken]. The underlying native ad is
     * destroyed/discarded (native ads are single-use and not returned to the pool).
     * Call when the rendering view is disposed.
     */
    public fun release(token: NativeAdToken)
    /** Returns the number of cached ads currently available for acquire. */
    public fun availableCount(): Int
    /** Returns media info (aspect ratio, video duration) for the ad identified by [token]. */
    public fun mediaInfo(token: NativeAdToken): NativeMediaInfo?
    /** Clears all cached ads and resets the pool. */
    public fun clear()
}

/**
 * Default no-op manager used when ads aren't configured. All loads fail
 * with [AdError.sdkNotReady] and the status is always
 * [AdManagerStatus.Disabled].
 */
public object NoOpAdManager : AdManager {
    private val _status = MutableStateFlow<AdManagerStatus>(AdManagerStatus.Disabled("Ads SDK is not configured."))
    private val _events = MutableSharedFlow<AdEvent>(extraBufferCapacity = 32)

    override val status: StateFlow<AdManagerStatus> = _status
    override val events: SharedFlow<AdEvent> = _events
    override val consent: ConsentController = NoOpConsentController
    override val diagnostics: AdDiagnostics = NoOpAdDiagnostics
    override val tracking: AdTrackingController = NoOpTrackingController

    override suspend fun initialize(config: AdConfig, consentMode: ConsentMode): AdManagerStatus = status.value
    override fun banner(placement: AdPlacement): BannerAdController = NoOpBannerAdController(placement)
    override fun nativeAd(placement: AdPlacement): NativeAdPool = NoOpNativeAdPool(placement)
    override fun interstitial(placement: AdPlacement): InterstitialAdController = NoOpInterstitialAdController(placement)
    override fun rewarded(placement: AdPlacement): RewardedAdController = NoOpRewardedAdController(placement)
    override fun rewardedInterstitial(placement: AdPlacement): RewardedInterstitialAdController = NoOpRewardedInterstitialAdController(placement)
    override fun appOpen(placement: AdPlacement): AppOpenAdController = NoOpAppOpenAdController(placement)
}

private object NoOpConsentController : ConsentController {
    private val _status = MutableStateFlow<ConsentStatus>(ConsentStatus.Unknown)
    private val _privacy = MutableStateFlow(PrivacyOptionsRequirementStatus.Unknown)
    private val _canRequestAds = MutableStateFlow(false)
    override val status: StateFlow<ConsentStatus> = _status
    override val privacyOptionsRequirementStatus: StateFlow<PrivacyOptionsRequirementStatus> = _privacy
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds
    override suspend fun requestConsentInfoUpdate(config: AdConfig): ConsentStatus = status.value
    override suspend fun gatherConsent(config: AdConfig): ConsentStatus = status.value
    override suspend fun showPrivacyOptions(): Boolean = false
    override suspend fun resetConsentForDebug(): Boolean = false
}

private object NoOpAdDiagnostics : AdDiagnostics {
    override suspend fun openAdInspector(): Boolean = false
    override suspend fun openDebugMenu(adUnitId: String): Boolean = false
    override fun sdkVersion(): String? = null
    override fun adapterStatuses(): List<AdapterInitializationStatus> = emptyList()
}

internal class NoOpBannerAdController(override val placement: AdPlacement) : BannerAdController {
    private val _loadState = MutableStateFlow<AdLoadState>(AdLoadState.Idle)
    private val _events = MutableSharedFlow<AdEvent>(extraBufferCapacity = 8)
    override val loadState: StateFlow<AdLoadState> = _loadState
    override val events: SharedFlow<AdEvent> = _events
    override suspend fun load(
        geometry: BannerGeometry?,
        sizePolicy: AdSizePolicy,
        requestOptions: AdRequestOptions
    ): AdLoadState = failLoad()
    override suspend fun refresh(): AdLoadState = load()
    override fun clear() = Unit
    private fun failLoad(): AdLoadState.Failed {
        val error = AdError.sdkNotReady()
        return AdLoadState.Failed(error).also {
            _loadState.value = it
            _events.emitOrLogDrop(AdEvent.LoadFailed(placement.id, error), "NoOp(${placement.id})")
        }
    }
}

internal open class NoOpFullScreenAdController(override val placement: AdPlacement) : FullScreenAdController {
    private val _loadState = MutableStateFlow<AdLoadState>(AdLoadState.Idle)
    private val _events = MutableSharedFlow<AdEvent>(extraBufferCapacity = 8)
    override val loadState: StateFlow<AdLoadState> = _loadState
    override val events: SharedFlow<AdEvent> = _events
    override fun availability(): AdAvailability = AdAvailability(isReady = false)
    override suspend fun load(requestOptions: AdRequestOptions): AdLoadState {
        val error = AdError.sdkNotReady()
        return AdLoadState.Failed(error).also {
            _loadState.value = it
            _events.emitOrLogDrop(AdEvent.LoadFailed(placement.id, error), "NoOp(${placement.id})")
        }
    }
    override suspend fun show(options: FullScreenAdOptions): AdShowResult {
        val error = AdError.sdkNotReady()
        _events.emitOrLogDrop(AdEvent.ShowFailed(placement.id, error), "NoOp(${placement.id})")
        return AdShowResult.Failed(error)
    }
    override fun clear() = Unit
}

internal class NoOpInterstitialAdController(placement: AdPlacement) : NoOpFullScreenAdController(placement), InterstitialAdController

internal class NoOpRewardedAdController(placement: AdPlacement) : NoOpFullScreenAdController(placement), RewardedAdController

internal class NoOpRewardedInterstitialAdController(placement: AdPlacement) : NoOpFullScreenAdController(placement), RewardedInterstitialAdController

internal class NoOpAppOpenAdController(placement: AdPlacement) : NoOpFullScreenAdController(placement), AppOpenAdController

internal class NoOpNativeAdPool(override val placement: AdPlacement) : NativeAdPool {
    private val _loadState = MutableStateFlow<AdLoadState>(AdLoadState.Idle)
    private val _events = MutableSharedFlow<AdEvent>(extraBufferCapacity = 8)
    override val loadState: StateFlow<AdLoadState> = _loadState
    override val events: SharedFlow<AdEvent> = _events
    override val availableAds: StateFlow<Int> = MutableStateFlow(0)
    override suspend fun preload(count: Int, requestOptions: AdRequestOptions, nativeOptions: NativeAdOptions): AdLoadState {
        val error = AdError.sdkNotReady()
        return AdLoadState.Failed(error).also {
            _loadState.value = it
            _events.emitOrLogDrop(AdEvent.LoadFailed(placement.id, error), "NoOp(${placement.id})")
        }
    }
    override fun acquire(): NativeAdToken? = null
    override fun release(token: NativeAdToken) = Unit
    override fun availableCount(): Int = 0
    override fun mediaInfo(token: NativeAdToken): NativeMediaInfo? = null
    override fun clear() = Unit
}

/**
 * Convenience extension that calls [initialize] with
 * [ConsentMode.GatherBeforeInitialize] — the standard consent flow that
 * requests a UMP update, shows the form if required, and initializes ads
 * when permitted.
 */
public suspend fun AdManager.gatherConsentAndInitialize(config: AdConfig): AdManagerStatus =
    initialize(config, ConsentMode.GatherBeforeInitialize)

/**
 * Convenience extension that calls [ConsentController.showPrivacyOptions]
 * on this manager's [AdManager.consent] controller.
 */
public suspend fun AdManager.showPrivacyOptions(): Boolean = consent.showPrivacyOptions()
