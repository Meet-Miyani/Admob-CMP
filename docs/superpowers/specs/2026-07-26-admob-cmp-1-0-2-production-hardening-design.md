# AdMob CMP 1.0.2 Production Hardening Design

**Date:** 2026-07-26

**Status:** Approved design; implementation not started

**Target release:** 1.0.2

**Compatibility:** Source and binary compatibility with 1.0.1 is not required

## Objective

Harden the standalone AdMob Compose Multiplatform SDK for production use on
Android and iOS. The work addresses the validated findings from the deep SDK
review without applying suggestions that weaken native lifecycle ownership,
misrepresent telemetry as a durable entitlement mechanism, or add tests that
do not exercise meaningful behavior.

The implementation remains split into:

- `admob-cmp-core`: platform SDK integration, consent, configuration,
  controllers, lifecycle state, and public non-Compose APIs.
- `admob-cmp-compose`: lifecycle-aware Compose rendering and event forwarding.
- `admob-cmp`: umbrella artifact and consumer documentation.

## Decision Summary

| Review finding | Decision |
|---|---|
| Privacy-options recovery resumes with `SkipConsent` | Fix using the active caller-selected consent mode |
| `AdShowResult.Rewarded` depends on callback ordering | Remove it and introduce a rewarded-specific callback API |
| iOS full-screen delegates survive terminal callbacks | Release on dismissal and failure-to-present |
| Release iOS delegates from cancellation/timeout catch blocks | Reject because post-handoff release is unsafe and pre-handoff delegates are not retained |
| Add `@Volatile` to all iOS initialization fields | Replace cross-dispatcher mutable pairs with synchronized state; retain mutex ownership for applied configuration |
| Collapsible banner options use the placement default | Carry the resolved per-call size policy to platform loaders |
| Mutable configuration collections can alias SDK state | Snapshot collections when the SDK assumes ownership |
| Policy value objects accept invalid values | Add complete constructor validation |
| `AdReward.wholeAmountOrNull()` can overflow | Return `null` outside the `Int` range |
| `NoOpAdManager` creates inconsistent controller instances | Cache by placement and validate collisions/formats |
| Core-to-Compose bridge APIs are public without warning | Require opt-in with `@InternalAdMobCmpApi` |
| Compose event collectors retain old callbacks | Use `rememberUpdatedState` and read it per emission |
| Add a token Android instrumentation test | Reject; add deterministic host/core regressions and define a separate real-device smoke boundary |
| ATT guidance is contradictory | Standardize on UMP consent, then ATT, then GMA initialization/request |
| Banner fallback documentation is stale | Align all documentation with the current measured-geometry/fallback behavior |

## 1. Reward Contract

### Problem

`AdShowResult.Rewarded` is currently assembled when the dismissal callback
resumes `show()`. Google-served ads report reward before dismissal, but mediated
networks may determine a different order. A valid reward callback that arrives
after dismissal therefore cannot be represented by the result already returned
from `show()`.

`AdEvent.RewardEarned` must remain useful for observation and telemetry, but the
existing `SharedFlow` is deliberately buffered and non-durable. It is not an
entitlement ledger and must not be documented as the only safe way to grant
currency or paid benefits.

### Public API

Remove `AdShowResult.Rewarded`. `AdShowResult` will represent presentation
outcome only:

- `Shown`: the ad was presented and dismissed.
- `NotReady`: no ad could be presented.
- `Failed`: presentation failed.

Add a rewarded-specific overload to both rewarded controller interfaces:

```kotlin
public interface RewardedAdController : FullScreenAdController {
    public suspend fun show(
        options: FullScreenAdOptions = placement.fullScreenOptions,
        onRewardEarned: (AdReward) -> Unit
    ): AdShowResult
}

public interface RewardedInterstitialAdController : FullScreenAdController {
    public suspend fun show(
        options: FullScreenAdOptions = placement.fullScreenOptions,
        onRewardEarned: (AdReward) -> Unit
    ): AdShowResult
}
```

The inherited `show(options)` remains available and returns only presentation
outcome. It still emits `AdEvent.RewardEarned`, but consumers that grant a
client-side entitlement must use the rewarded-specific overload and install
the callback before presentation.

The reward callback:

- is invoked directly from the native reward callback;
- may run before or after the suspending `show()` call returns;
- is delivered at most once for one presentation;
- does not determine the returned `AdShowResult`;
- is isolated so a consumer exception cannot corrupt SDK cleanup or suppress
  telemetry;
- is invoked on the platform main thread, matching GMA callback delivery.

`AdEvent.RewardEarned` is emitted independently from the same native callback.
Documentation must warn consumers not to grant the same reward from both the
direct callback and the event stream.

For valuable or fraud-sensitive entitlements, documentation will recommend
AdMob server-side verification as the authoritative grant path. Client
callbacks remain appropriate for immediate UI feedback and lower-risk rewards.

### Post-dismissal Native Lifetime

A rewarded ad must remain capable of delivering a late native reward callback
after dismissal. The core will distinguish cache/load destruction from
post-presentation cleanup:

- cached, cleared, expired, cancelled-before-handoff, and failed-to-show ads are
  destroyed normally;
- a successfully presented rewarded or rewarded-interstitial ad is not
  explicitly destroyed at dismissal in a way that can cancel a pending reward
  listener;
- the SDK drops its strong ownership after the native SDK has accepted
  presentation and terminal lifecycle state has been published;
- iOS releases the full-screen-content delegate on dismissal, while the
  separately supplied native reward handler remains owned by GMA until it
  finishes.

This avoids an arbitrary reward grace timeout. The library will not guess how
long a mediation adapter may delay a callback.

## 2. Consent Recovery and Initialization Concurrency

### Consent-mode preservation

The privacy-options controllers currently invoke the manager callback with only
the last configuration. The manager callback then calls
`initialize(config, ConsentMode.SkipConsent)`. When initialization previously
stopped at `ConsentRequired`, this replaces the active consent policy with
`SkipConsent`.

The callback will instead resume initialization with the mode selected by the
original caller:

1. `initialize(config, mode)` records the active mode after its consent step.
2. Privacy-options dismissal refreshes UMP state.
3. If `canRequestAds` becomes true, the controller asks the manager to resume.
4. The manager uses the active mode; it never manufactures `SkipConsent`.
5. If no active mode exists, privacy-options recovery does not initialize GMA
   implicitly.

The fallback deliberately fails closed. A privacy UI action must not invent an
initialization policy for an app that never called `initialize`.

### Cross-dispatcher state

Do not add `@Volatile` indiscriminately.

- `appliedConfigIdentity` and `appliedTerminalStatus` remain owned by
  `mobileAdsInitializationMutex`; volatile reads would not protect their
  relationship.
- The active consent-mode/admission context becomes one thread-safe snapshot,
  rather than independently visible mutable fields.
- Request admission continues to be published through `MutableStateFlow`.
- Readiness gates use the manager's published terminal status instead of a
  second loosely synchronized `initialized` flag.
- Platform GMA and UMP calls remain main-thread confined.

The Android and iOS managers must implement the same state transitions and
tests must pin the shared decision logic where native SDK construction is not
available.

## 3. iOS Full-screen Delegate Ownership

The full-screen delegate store exists because GMA delegate properties are weak.
Ownership remains:

- core-owned before `tryHandOffToCallbacks()`;
- callback-owned after a successful handoff;
- released only by a native terminal callback once callback-owned.

Every iOS full-screen format will release its retained delegate on:

- `adDidDismissFullScreenContent`;
- failure to present full-screen content.

Release must occur in a `finally`-style terminal path so continuation
cancellation or duplicate lifecycle signals cannot retain the entry.

The implementation will not release the delegate from
`FullScreenSlotCore`'s cancellation catch:

- before handoff, the current code has not retained a delegate;
- after handoff, GMA may still display the ad and requires the delegate;
- releasing it early can suppress cleanup callbacks, strand the process-wide
  presentation token, or allow another ad to present concurrently.

If a native SDK never sends a terminal callback, retaining callback ownership
is intentional. The SDK cannot safely infer that a visible presentation has
ended.

## 4. Banner Request Fidelity

`BannerCore` already stores a `ResolvedBannerRequest` containing:

- resolved platform size;
- the caller's `AdSizePolicy`;
- request options.

The platform `loadBanner` boundary currently receives only the resolved size
and request options, causing both platforms to reconstruct collapsible extras
from `placement.bannerSizePolicy`.

Extend the internal `BannerPlatform.loadBanner` contract to also receive the
resolved `sizePolicy`. Android and iOS will call
`requestOptions.withCollapsible(sizePolicy)`.

This applies equally to:

- direct `load(geometry, sizePolicy, requestOptions)`;
- geometry registered for manual refresh;
- later `refresh()` calls replaying `ResolvedBannerRequest`.

No public banner API shape changes are required.

## 5. Configuration Ownership and Validation

### Immutable SDK snapshots

Public configuration types remain ergonomic Kotlin data classes. Replacing all
of them with custom immutable classes would create a large API shape change and
would still leave nested maps and lists to solve.

Instead, the SDK creates deep snapshots when it assumes long-lived ownership:

- initialization snapshots global test-device IDs, consent debug IDs, and
  initialization hooks;
- initialization identity stores the snapped global request configuration;
- placement registration snapshots request sets/maps and nested custom-target
  value lists before collision checks and controller construction;
- initialization and placement snapshots are the values retained by managers
  and controllers.

Mutation of caller-owned collections after `initialize()` or controller
creation must not change native configuration, equality/collision checks,
refresh behavior, or initialization identity.

### Constructor validation

Validation will fail fast at the policy object that owns the invariant:

- `AdCachePolicy.maxSize >= 1`;
- expiration TTLs are finite and strictly positive;
- `AdRetryPolicy.maxAttempts >= 1`;
- retry delays are finite and strictly positive;
- `maxDelay >= initialDelay`;
- `backoffMultiplier` is finite and at least `1.0`;
- `AdSizePolicy.Fixed.widthDp` and `heightDp` are positive;
- `AdSizePolicy.InlineAdaptive.maxHeightDp`, when present, is positive;
- existing timeout validations remain and are expanded to reject infinite
  values if necessary.

`AdPlacement` may retain defensive validation, but it is no longer the only
place an invalid `AdCachePolicy` is rejected.

### Reward conversion

`AdReward.wholeAmountOrNull()` returns an `Int` only when:

1. `amountMicros` is exactly divisible by `1_000_000`; and
2. the quotient is inside `Int.MIN_VALUE..Int.MAX_VALUE`.

Otherwise it returns `null`.

## 6. No-op Manager Consistency

`NoOpAdManager` will mirror production manager factory semantics:

- validate that the factory matches `placement.format`;
- reject reuse of one placement ID with different configuration;
- cache banner and native controllers by placement ID;
- cache full-screen controllers by placement ID and format;
- return the same controller instance for repeated equivalent requests;
- protect its process-wide registries with the existing multiplatform state
  lock abstraction.

This makes fallback behavior predictable in previews, unsupported targets, and
tests.

## 7. Core-to-Compose Bridge Boundary

The platform bridge functions must be callable from `admob-cmp-compose`, which
is a separate artifact, so Kotlin `internal` visibility is not viable.

Add a public `@InternalAdMobCmpApi` annotation using `@RequiresOptIn`. Apply it
to the Android/iOS banner and native bridge functions that expose platform
objects or attachment/geometry operations solely for the Compose artifact.

`admob-cmp-compose` explicitly opts in at its platform source-set boundary.
Normal SDK consumers receive a clear warning or error if they call bridge APIs
directly.

## 8. Compose Callback Freshness

Each Android/iOS banner and native composable will use:

```kotlin
val currentOnEvent by rememberUpdatedState(onEvent)

LaunchedEffect(controllerOrPool) {
    events.collect { event -> currentOnEvent(event) }
}
```

The collector is keyed only by the stable controller/pool, so recomposition
does not restart it. Reading `currentOnEvent` inside the emission lambda is
required; passing its current function value directly to `collect` would still
capture one value when the effect starts.

Native-ad instance filtering remains before callback invocation.

## 9. Documentation

Documentation changes include:

- remove examples that grant rewards from `AdShowResult.Rewarded`;
- document the new direct callback and its possible post-return delivery;
- classify `AdEvent.RewardEarned` as observation/telemetry;
- recommend SSV for authoritative high-value grants;
- standardize iOS ordering as UMP consent, then ATT, then GMA
  initialization/first request;
- remove the contradictory instruction to request ATT before UMP consent;
- align banner manual-refresh and fallback text with measured geometry,
  key-window/activity fallback, and failure when width cannot be resolved;
- update umbrella documentation and API examples, not only core KDoc.

## 10. Verification Strategy

### Test-first regression coverage

Add deterministic tests for:

- consent recovery preserving the active mode and refusing to invent one;
- per-call collapsible size policy reaching initial load and refresh;
- initialization and placement snapshots resisting later caller mutation;
- every new validation boundary, including `NaN`, infinity, zero, negatives,
  and retry delay ordering;
- whole reward amounts at both `Int` boundaries and beyond them;
- `NoOpAdManager` instance reuse, format validation, and placement collision;
- rewarded callback ordering for reward-before-dismiss and
  dismiss-before-reward;
- at-most-once reward delivery;
- presentation cleanup remaining correct after caller cancellation;
- iOS delegate-store release on both terminal callbacks.

Tests that can run in common code will use shared state-machine fakes. Native
characterization tests will cover platform mapping and delegate ownership
without making network requests.

### Automated gates

The implementation plan will discover the current Gradle task graph before
choosing exact tasks. The final gate must include, at minimum:

- common/core tests;
- Android host tests;
- Android main compilation;
- iOS simulator tests;
- iOS simulator compilation;
- Compose module tests/compilation;
- ABI validation/update for the intentional public API break;
- `git diff --check`.

### Instrumentation and device testing

Do not add an instrumentation test merely to satisfy a finding. Real AdMob
lifecycle tests require a device/emulator, network, test inventory, lifecycle
coordination, and non-flaky CI policy.

The existing production-pattern debug app remains the manual integration
surface. A future instrumented suite is valuable only when it has:

- dedicated managed-device CI;
- official test app/ad-unit IDs;
- explicit UMP reset/setup;
- bounded network retries;
- lifecycle assertions that cannot be covered by host tests.

Manual release smoke testing for 1.0.2 should exercise consent denial/grant,
privacy-options changes, ATT ordering on iOS, each ad format, late reward
delivery where a mediation test source supports it, cancellation/navigation,
and banner recomposition.

## Non-goals

- No arbitrary timeout that force-closes a callback-owned full-screen ad.
- No durable on-device reward ledger inside this SDK.
- No replacement for server-side verification.
- No unrelated Compose UI redesign.
- No network-dependent test added solely for test-count optics.
- No automatic Git commit.

## Acceptance Criteria

The 1.0.2 hardening is complete when:

1. privacy-options recovery cannot switch the manager into `SkipConsent`;
2. rewarded entitlements no longer depend on dismissal ordering;
3. telemetry is not documented as durable entitlement delivery;
4. iOS delegates release on real terminal callbacks and remain retained while
   callback-owned;
5. per-call banner collapsible policy is honored on both platforms and refresh;
6. retained configurations and placements cannot be changed by caller
   collection mutation;
7. invalid policies fail at construction;
8. no-op and production factories share collision/caching semantics;
9. Compose event callbacks always use the latest lambda;
10. ATT, reward, and banner documentation is internally consistent;
11. all selected Android, iOS, Compose, ABI, and diff gates pass.
