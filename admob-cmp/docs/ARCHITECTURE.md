# Architecture

## Module map

```
commonMain
  avinya.tech.yt.ads            public API: AdManager, controllers, AdPlacement,
                                AdConfig, events, errors, consent, diagnostics
  avinya.tech.yt.ads.internal   FullScreenSlotCore (shared load/show/cache state
                                machine), AdRetry (capped exponential backoff)
  avinya.tech.yt.ads.appopen    AppOpenAdCoordinator + expect foreground signal
  avinya.tech.yt.ads.nativead   NativeAdPool contract, options, media info
  avinya.tech.yt.ads.nativead.layout   AdLayout DSL + validator + templates
  avinya.tech.yt.ads.ui         expect composables: BannerAdView, NativeAdView

androidMain                     AndroidGoogleAdManager + slots/pool/banner over the
                                GMA Next-Gen SDK; AdMob.manager(context) singleton;
                                ProcessLifecycleOwner foreground signal
iosMain                         IosGoogleAdManager + slots/pool/banner/consent over
                                cinterop bindings; Kotlin native-ad renderer
                                (UIKit); NSNotificationCenter foreground signal
```

One state machine, two thin platform adapters: every full-screen slot extends
`FullScreenSlotCore`, which owns the load mutex, the TTL'd FIFO cache
(`AdCachePolicy.maxSize`), retry, consent gating, event emission, and
`reloadAfterShow`. Platform classes implement only `loadAd` / `presentAd` /
`destroyAd` / `canPresent`.

The cache carries a generation counter, bumped by `clear()`. A load or
scheduled reload started before a `clear()` checks its required generation
before publishing results, so it can never repopulate a cache the caller just
asked to be emptied. Presentation ownership is a one-shot
`FullScreenPresentationHandle`: the core owns it until the platform slot hands
it off to the SDK's callbacks right before the actual show call; from then on
only the SDK's terminal callback (or a cancellation that raced in *before*
hand-off) may close it. `show()` is therefore not reentrant per controller — a
second call while one presentation is active returns `NotReady` rather than
queuing — and cancelling a caller never itself decrements the process-wide
presence signal once hand-off has happened, so `AppOpenAdCoordinator` never
sees "not presenting" while an ad the SDK still owns is actually on screen.

## iOS binding model (bindings-only — locked decision)

The iOS implementation compiles against the official GMA/UMP XCFrameworks via
Kotlin/Native **cinterop**. Gradle downloads the zips
(`build/ios-frameworks/`, version-stamped cache) purely for headers; the
published klib contains *bindings*, never Google's binaries. The consuming app
links GMA/UMP itself via SPM.

Why not the alternatives:
- **CocoaPods plugin**: the ecosystem (and this repo) is SPM-based.
- **Embedding binaries** (RevenueCat-style `staticLibraries`): breaks mediation
  adapters with duplicate ObjC classes, pins the GMA version, and raises
  redistribution-license questions. Evaluated and rejected.

Costs we accept: consumers must add two SPM packages (the `doctorIos` task and
SETUP.md own that story), and the bound GMA major version must match the
SPM-resolved one.

## Threading model

All GMA/UMP calls are wrapped in `Dispatchers.Main.immediate` internally —
every public API is main-safe and callable from any dispatcher. Registries are
lock-protected; iOS delegate objects are strongly retained by their owners for
exactly the ObjC delegate's lifetime (ObjC delegates are weak).

## Caching & TTL

- Full-screen: per-slot deque up to `maxSize`, FIFO show, eviction on every
  touch; TTLs from `AdExpirationPolicy` (1h full-screen, 4h app-open).
- Native: token-based pool (`acquire`/`release`), same TTL discipline.
- Caching lives in our slot layer, not the SDKs' preload APIs — those were
  beta/asymmetric across platforms at design time. Revisit when both are GA.

## Event flow

```
platform SDK callback → slot/pool/controller
    → controller.events (per-placement SharedFlow)
    → AdManager.events  (global SharedFlow)
```

Sealed `AdEvent`: Loaded, LoadFailed, ShowFailed, Impression, Clicked,
OpenedFullScreen, ClosedFullScreen, RewardEarned, Paid, Video* (iOS).

## Decision log

| Decision | Rationale |
|---|---|
| Suspend + Flow instead of listener callbacks | One paradigm; show() suspending until dismissal collapses 5 callback interfaces |
| Placement-keyed long-lived controllers | The caching/retry/lifecycle layer every app rebuilds badly |
| Consent integrated into init | Impossible to request ads pre-consent by construction |
| Bindings-only iOS distribution | Mediation safety, version freedom (above) |
| Slot-layer caching | SDK preload APIs beta/asymmetric |
| No bundled mediation adapters | See docs/MEDIATION.md |
| explicitApi + ABI dump | Public surface can't drift silently |
