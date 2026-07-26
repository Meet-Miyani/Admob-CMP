# admob-cmp — Agent Guide

Compose Multiplatform AdMob SDK. Package `dev.avinya.ads`; Maven artifact
`dev.avinya.ads:admob-cmp`. Android (GMA Next-Gen, API 26+) + iOS (GMA 13.x,
iOS 15+).

## Entry points

- Compose: `rememberAdManager()` (process-wide singleton), `LocalAdManager`
- Android, outside Compose: `AdMob.manager(context)`
- Placements via `LocalAdPlacements` (provides `AdPlacements`) or your own `AdPlacement` instances

## Canonical initialization

```kotlin
val adManager = rememberAdManager()
LaunchedEffect(Unit) {
    adManager.gatherConsentAndInitialize(
        AdConfig(androidAppId = "ca-app-pub-…", iosAppId = "ca-app-pub-…", testMode = true)
    )
}
// Gate UI on: adManager.status.collectAsState() == AdManagerStatus.Ready
```

Other consent strategies: `adManager.initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)`
or `ConsentMode.SkipConsent`.

## Formats → API

| `AdFormat` | Controller (from `AdManager`) | Composable | Test ids (Android / iOS) |
|---|---|---|---|
| `Banner` | `banner(placement)` | `BannerAdView(placement)` | `TestAdIds.ANDROID_BANNER` / `IOS_BANNER` (collapsible: `ANDROID_COLLAPSIBLE_BANNER` / `IOS_COLLAPSIBLE_BANNER`) |
| `Interstitial` | `interstitial(placement)` | — | `ANDROID_INTERSTITIAL` / `IOS_INTERSTITIAL` |
| `Rewarded` | `rewarded(placement)` | — | `ANDROID_REWARDED` / `IOS_REWARDED` |
| `RewardedInterstitial` | `rewardedInterstitial(placement)` | — | `ANDROID_REWARDED_INTERSTITIAL` / `IOS_REWARDED_INTERSTITIAL` |
| `AppOpen` | `appOpen(placement)` + `AppOpenAdCoordinator` | — | `ANDROID_APP_OPEN` / `IOS_APP_OPEN` |
| `Native` | `nativeAd(placement)` (pool) | `NativeAdView(placement, itemKey, layout)` | `ANDROID_NATIVE` / `IOS_NATIVE` |

`TestAdIds` constants are flat (`TestAdIds.ANDROID_BANNER`) — there is no
`TestAdIds.Android.*` nesting.

## Full-screen pattern

```kotlin
val ad = remember(adManager) { adManager.interstitial(placement) }
scope.launch {
    ad.load()                                  // suspend; AdLoadState back
    when (val r = ad.show()) {                 // suspends until dismissed
        is AdShowResult.Shown -> Unit
        is AdShowResult.NotReady -> Unit       // load() first
        is AdShowResult.Failed -> log(r.error)
    }
}
```

Multi-ad caching: `cachePolicy = AdCachePolicy(maxSize = 3, reloadAfterShow = true)`
on the placement. FIFO show, TTL eviction (1h; app-open 4h).

`show()` is not reentrant per controller: a second `show()` call while the
first is still on screen for the *same* controller returns `NotReady`
immediately rather than queuing behind it. Await one `show()`'s result before
calling it again.

## Banner

```kotlin
AdPlacement(
    id = "home_banner", format = AdFormat.Banner,
    adUnitIds = AdUnitIds(android = "…", ios = "…"),
    bannerSizePolicy = AdSizePolicy.LargeAnchoredAdaptive(collapsible = CollapsiblePlacement.Bottom),
    bannerRefreshPolicy = BannerRefreshPolicy.SdkManaged(60.seconds)  // 30s–120s; or AdServerManaged / Manual
)
BannerAdView(placement = placement, modifier = Modifier.fillMaxWidth())
```

`Manual` policy = no auto-load; call `adManager.banner(placement).refresh()`.

### Banner geometry (headless callers)

`BannerAdView` measures its own container and supplies the width for you — prefer it.
Driving a controller directly means supplying the geometry yourself:

```kotlin
adManager.banner(placement).load(geometry = BannerGeometry(widthDp = 320))
```

> **Breaking change.** `load()` previously took `(sizePolicy, requestOptions)` and resolved
> its own width — from an `Activity` on Android, from `UIScreen.mainScreen` on iOS. The iOS
> path silently produced full-screen width in iPad split view, Slide Over and popovers,
> sizing every banner wrong with no error. Width is now a host-supplied input:
> `load(geometry, sizePolicy, requestOptions)`. Existing no-arg `load()` calls still
> compile — `geometry` defaults to `null` — but a headless call with no geometry now
> **fails** rather than guessing when the platform cannot resolve a width.

`refresh()` replays the **whole** resolved request — geometry, size policy *and* request
options — from the most recent `load()`. It previously kept only the resolved size and
rebuilt options from `placement.requestOptions`, silently dropping any custom
`AdRequestOptions` the original `load()` was given. It fails if nothing has been loaded yet.

## Native

```kotlin
val layout = adLayout {
    column(modifier = AdModifier.fillMaxWidth()) {
        media(modifier = AdModifier.fillMaxWidth().aspectRatio(16f / 9f))
        headline(maxLines = 2)
        body(maxLines = 3)
        row(spacing = 8.dp) { icon(modifier = AdModifier.size(24.dp)); advertiser(); adBadge() }
        callToAction(modifier = AdModifier.fillMaxWidth())
    }
}
NativeAdView(placement = placement, itemKey = "feed_3", layout = layout)
```

DSL nodes are functions with named args (`headline(maxLines = 2)`), NOT
property-assignment blocks. `adBadge()` is policy-required (validator warns).
Manual pooling: `pool.preload(count)`, `pool.acquire()` → render → `pool.release(token)`;
`pool.mediaInfo(token)` gives aspect ratio / video info.

### Availability (`pool.availableAds`)

`AdCachePolicy.maxSize` budgets **available + in-use** ads. So with `maxSize = 1` and one
row holding the ad, `preload()` returns early and `acquire()` returns `null` for every other
row — deterministically, not as a race. `acquire()` returning null used to be silent and
terminal for that composition, leaving those rows blank forever.

`pool.availableAds: StateFlow<Int>` is the signal that lets a view recover. Key your
acquisition effect on it:

```kotlin
val availableAds by pool.availableAds.collectAsState()
LaunchedEffect(pool, itemKey, availableAds) { … pool.preload(…); pool.acquire() … }
```

Re-run `preload`, not just `acquire`: `release()` destroys the ad (native ads are
single-use) so it frees a `maxSize` **slot** without incrementing `availableAds`. The
`NativeAdView` composables already do this.

`clear()` drains available inventory only. Ads currently leased stay alive until their
`release()`, because a live view is still rendering them — so `peek(token)` keeps resolving
after a `clear()`. A consequence: clearing a fully-leased pool frees no capacity until those
views dispose.

> **Platform gap — native video events on Android.** iOS emits five video events
> (`VideoStarted`, `VideoPlayed`, `VideoPaused`, `VideoEnded`, `VideoMuted`) via
> `GADVideoControllerDelegate`. The Android GMA Next-Gen SDK exposes no equivalent
> callback surface on `NativeAd`, so Android emits none. Do not rely on native video
> events for cross-platform logic. This is an upstream SDK gap, not an admob-cmp
> omission.

## App-open

```kotlin
val coordinator = remember(adManager) {
    AppOpenAdCoordinator(
        manager = adManager,
        controller = adManager.appOpen(placement),
        config = AppOpenConfig(minBackgroundDuration = 4.seconds, cooldownBetweenShows = 4.hours)
    )
}
LaunchedEffect(Unit) { coordinator.start(this) }
// coordinator.isBlocked = true during purchases/onboarding/other full-screen ads
```

## Consent / privacy options

Show a privacy-settings button **only** when
`adManager.consent.privacyOptionsRequirementStatus.value == PrivacyOptionsRequirementStatus.Required`,
then call `adManager.consent.showPrivacyOptions()`. Do NOT gate on
`ConsentStatus.Obtained`.

## Hard rules

1. Wrap controller lookups in `remember { adManager.x(placement) }` — never per-recomposition work in composition.
2. Every `pool.acquire()` needs a matching `release()` (the composables do it for you).
3. Call `show()` from a UI-scoped coroutine, never `GlobalScope`; it suspends for the ad's full lifetime.
4. Android-only options that iOS silently ignores: `immersiveMode`, `customClickGesture`, `publisherProvidedId`, `categoryExclusions`, `skipUninitializedAdapters`. (`customTargeting` and `placementId` ARE mapped on both platforms.)
5. Video `AdEvent`s are iOS-only until the Android Next-Gen SDK exposes video callbacks.
6. Don't construct `AdManager` implementations; only `rememberAdManager()` / `AdMob.manager(context)`.
7. Use static, finite `AdPlacement.id`s. Controllers are cached per id for the manager's lifetime and not auto-evicted — never generate per-item ids like `"feed_item_$index"`. For feeds/lists reuse one placement id; the native pool serves per-item ads.

## Config flags: `testMode` and `strictTestMode`

> **`testMode` vs `strictTestMode` — these are not the same flag.**
> `AdDebugOptions.testMode` configures **UMP consent debugging** only. It does **not**
> make GMA serve test ads; only registering a device in
> `GlobalRequestConfiguration.testDeviceIds`, or using a `TestAdIds` unit, does that.
> `AdPlacement.strictTestMode` is the safety guard: it **throws at construction** if the
> placement points at a production ad unit. Turn it on in debug builds.

## iOS setup (executable steps)

1. Add SPM packages to the Xcode project (File → Add Package Dependencies):
   - `https://github.com/googleads/swift-package-manager-google-mobile-ads.git` — version 13.x (must match the bound major)
   - `https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git` — version 3.x
2. Merge into the app target's `Info.plist`:

```xml
<key>GADApplicationIdentifier</key>
<string>ca-app-pub-3940256099942544~1458002511</string><!-- replace sample id -->
<key>SKAdNetworkItems</key>
<array><dict><key>SKAdNetworkIdentifier</key><string>cstr6suwn9.skadnetwork</string></dict></array>
```

3. If no Swift file imports GoogleMobileAds, add `-framework JavaScriptCore` to
   `OTHER_LDFLAGS` (static Kotlin framework does not autolink it).
4. Verify: `./gradlew :admob-cmp-core:doctorIos` (report-only; prints ✅/❌ per check).

### iOS: App Tracking Transparency (required)

Add to the Xcode app target's `Info.plist`:

```xml
<key>NSUserTrackingUsageDescription</key>
<string>This identifier will be used to deliver personalised ads to you.</string>
```

**Without this key the ATT prompt cannot be shown and iOS withholds the IDFA**, so every
request serves non-personalised ads at materially lower eCPM.

Call order matters — UMP consent first, then ATT, then your first ad request:

```kotlin
adManager.consent.gatherConsent(config)
adManager.tracking.requestAuthorization()
adManager.initialize(config)
```

Requesting ads before ATT resolves permanently forfeits the IDFA for those requests.
Android has no ATT; `adManager.tracking` is a no-op there, always reporting
`AdTrackingAuthorization.NotApplicable`.

## Troubleshooting

| Symptom | Cause → fix |
|---|---|
| `AdErrorCode.SDK_NOT_READY` (`sdk_not_ready`) | `initialize` not finished — gate on `status == Ready` |
| `AdErrorCode.CONSENT_REQUIRED` (`consent_required`) | UMP forbids requests — run `gatherConsentAndInitialize` / check `canRequestAds` |
| GMA code `3` (Android) / `1` (iOS) | No fill — normal; retry later (non-retryable by policy) |
| GMA code `0`/`2` (Android), `2`/`5`/`11` (iOS) | Internal/network/timeout — auto-retried per `AdRetryPolicy` (default `maxAttempts = 2`: the initial attempt plus one retry; set `maxAttempts = 1` for no retry) |
| iOS link: `_OBJC_CLASS_$_GADMobileAds` undefined | GMA SPM package missing → step 1 above |
| iOS link: `_OBJC_CLASS_$_JSContext` undefined | Add `-framework JavaScriptCore` → step 3 above |
| Banner composable renders nothing | Manager not `Ready`, or `Manual` refresh policy with no `refresh()` call |

## Module internals (for agents modifying this library)

- `internal/FullScreenSlotCore` — shared load/show state machine: a
  generation-tagged `slotState` (load state + TTL'd FIFO cache, mutated only
  under `publicationLock`), retry via `AdRetry`, consent gate, `canPresent()`
  probe, `onAdLoaded` hook (Android applies `placementId` there). Platform
  slots implement `loadAd`/`presentAd(ad, options, presentation)`/`destroyAd`/
  `canPresent` only. `clear()` bumps the generation so any load/reload still
  in flight for the old generation is invalidated instead of repopulating a
  cleared cache. A slot's `show()` is not reentrant: a second call while a
  presentation is active on that controller returns `NotReady` rather than
  queuing.
- `internal/FullScreenPresentationHandle` — one-shot ownership token for a
  single presentation, passed into `presentAd`. The core owns it until the
  platform slot calls `tryHandOffToCallbacks()` just before invoking the SDK's
  show; after that, only the SDK's terminal callback (`close(wasShown)`) or a
  pre-hand-off cancellation (`closeIfCoreOwned()`) may close it, and closing
  decrements the process-wide presence count exactly once. Never retain a
  platform delegate for an ad before `tryHandOffToCallbacks()` succeeds — if
  it returns false (cancellation raced in first), the SDK show never happens
  and nothing will ever call `destroyAd` to release that delegate.
- `AdAttemptResult.Success/Failure` is the internal load-callback bridge.
- iOS: every ObjC delegate is weak — keep strong Kotlin refs alongside the ad
  (pool `NativeEntry.delegates`, slot `FullScreenDelegateStore`, keyed by ad
  identity so destroying one ad can't sever a different ad's live delegate).
  Paid-event handlers capture ads via `WeakReference` (ARC cycles).
- `AndroidGoogleAdManager`/`IosGoogleAdManager.initialize()`: concurrent
  callers with an *equivalent* request (same effective `AdConfig` identity —
  app id + merged `GlobalRequestConfiguration` — and `ConsentMode`) share one
  in-flight attempt; a distinct request waiting behind it re-registers its own
  attempt once the leader settles. The native GMA/UMP singleton only actually
  initializes once per process: after `appliedConfigIdentity` is set, later
  calls with the *same* identity are no-ops that replay the applied status: a
  call with a *different* identity is ignored with a logged warning, not
  re-applied. `AdInitializationHook`s (`BeforeConsentRequest`,
  `BeforeMobileAdsInitialize`, `AfterMobileAdsInitialize`) run exactly once per
  real native-init attempt, inside the detached `nativeInitializationScope` —
  never inside an individual caller's cancellable coroutine — so cancelling
  one `initialize()` caller can never skip or duplicate a hook.
- Tests: `commonTest` only, hand-written fakes (`Fakes.kt`), injectable
  `clock`/`foregroundEvents` seams. Run `./gradlew :admob-cmp:iosSimulatorArm64Test`
  and `:admob-cmp:testAndroidHostTest`.
- API surface: `explicitApi()` + KGP ABI validation — after any public change
  run `./gradlew :admob-cmp:updateKotlinAbi` and commit `api/admob-cmp.klib.api`.
- iOS bindings: cinterop against downloaded XCFrameworks
  (`build/ios-frameworks/`, version-stamped). Bindings-only distribution —
  NEVER add `staticLibraries` to the `.def` files.
