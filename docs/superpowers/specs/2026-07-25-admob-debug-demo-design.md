# Production-Pattern Test Ads Demo Design

**Date:** 2026-07-25  
**Status:** Approved design

## Goal

Replace the generated Compose sample UI with a runnable AdMob verification app. Android and iOS must open directly into the existing `AdDebugScreen`, use only Google's sample app and ad-unit IDs, and exercise the SDK through the same consent, initialization, lifecycle, and error-handling boundaries expected from a production host.

The demo is not a production inventory configuration. It is a production-pattern integration that is made safe for development by using official test identifiers and strict test-mode placement validation.

## Scope

The demo will:

- launch directly into the debug screen on Android and iOS;
- initialize the process-wide `AdManager` exactly once;
- gather UMP consent before initializing Google Mobile Ads;
- request iOS tracking authorization after consent and before the first ad request;
- wait for `AdManagerStatus.Ready` before composing ad formats;
- use `AdDebugCatalog.Test`, whose placements use Google test units and `strictTestMode = true`;
- configure Google's sample Android and iOS application IDs;
- expose startup progress, consent-required, and initialization-failure states with an explicit retry action;
- keep JVM, JS, and Wasm compilable without adding unsupported AdMob dependencies.

The demo will not add production ad-unit IDs, mediation, remote configuration, analytics, navigation infrastructure, or a second custom ad gallery.

## Architecture

`shared/commonMain` remains independent of the Android/iOS-only AdMob artifacts. `App()` will render an `expect` platform demo entry point:

```text
App
└── PlatformAdDemo
    ├── Android actual → production-pattern test-ad host → AdDebugScreen
    ├── iOS actual     → production-pattern test-ad host → AdDebugScreen
    └── JVM/JS/Wasm actuals → unsupported-platform explanation
```

The Android and iOS actuals may share source-compatible helpers where the existing source-set hierarchy permits it, but the implementation must not move Google SDK or AdMob Compose types into `commonMain`.

The host obtains the process singleton through `rememberAdManager()`. It supplies that same manager explicitly to `AdDebugScreen` and through `LocalAdManager`, avoiding accidental fallback to `NoOpAdManager`.

## Safe test configuration

A small internal demo configuration owns the official Google sample application IDs:

- Android: `ca-app-pub-3940256099942544~3347511713`
- iOS: `ca-app-pub-3940256099942544~1458002511`

All format placements come from `AdDebugCatalog.Test`. The catalog already uses the platform-specific constants in `TestAdIds` and enables `strictTestMode` on every placement. The demo must not accept arbitrary IDs or introduce an override path.

`AdConfig.testMode` will be enabled for UMP consent debugging. Ad serving safety comes from the sample ad-unit IDs and strict placement validation, not from assuming that the UMP debug flag makes physical devices test devices.

Platform host metadata will use the same sample application IDs. iOS metadata must include the required Google Mobile Ads application identifier and tracking-usage description. Existing Swift Package Manager integration remains the SDK linkage boundary.

## Startup and data flow

The startup sequence is:

1. Compose obtains the stable process-wide manager.
2. A manager-keyed `LaunchedEffect` starts one initialization attempt.
3. UMP consent information is refreshed and the consent form is shown when required.
4. On iOS, tracking authorization is requested after the consent step and before the first ad request. Android's tracking controller remains a no-op.
5. Google Mobile Ads initializes once with the sample application ID and global request configuration.
6. The host observes `manager.status`.
7. Only `Ready` renders `AdDebugScreen(catalog = AdDebugCatalog.Test, manager = manager)`.

The implementation should use the library's initialization API and hooks rather than reproducing platform SDK calls in the app. No ad load may start from a composable body or before readiness.

## UI states

There is no landing page. The root surface shows one of:

- **Starting:** consent/initialization progress;
- **Ready:** the complete existing `AdDebugScreen`;
- **Consent required:** an explanation and retry action;
- **Failed/disabled:** the SDK error and retry action.

Retry creates a new UI initialization attempt without constructing another manager. The manager remains responsible for serializing or reusing native initialization.

The debug screen keeps its existing format tabs, diagnostics, event console, and lifecycle-safe load/show/clear controls. `onBack` is a no-op because it is the root screen.

## Error and lifecycle handling

- Cancellation from the composition scope propagates normally.
- Initialization failures are rendered; they are not silently swallowed.
- Retry calls the same manager with the same immutable configuration.
- Banner and native views remain owned by the library composables and release their platform resources on disposal.
- Full-screen load/show operations remain launched from UI-owned coroutine scopes inside the existing debug screen.
- No app-open coordinator is added; app-open ads are manually loaded and shown by the debug screen, avoiding surprise presentation during verification.

## Testing

Implementation follows red-green-refactor.

Automated tests will cover:

- the demo configuration contains exactly the official sample application IDs;
- no production ad-unit path is introduced;
- startup-state mapping selects starting, ready, consent-required, and failure UI deterministically;
- unsupported targets continue to compile without `admob-cmp-compose`;
- Android and iOS demo sources compile against the new `-compose` artifact;
- existing core/Compose ABI and host tests remain green.

Verification will include Android and iOS simulator compilation, Android host tests, iOS simulator tests, unsupported shared targets, the Android app compile, ABI checks, and `git diff --check`.

## Success criteria

On Android or iOS, launching the app performs the consent-aware initialization sequence and then opens the existing debug console. Banner, native, interstitial, rewarded, rewarded-interstitial, app-open, diagnostics, and Ad Inspector controls operate only against Google's test inventory. Initialization or consent problems are visible and retryable, while no unsupported target gains a Google Ads dependency.
