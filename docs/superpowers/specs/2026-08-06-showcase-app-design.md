# Showcase app — design

**Date:** 2026-08-06
**Branch:** `feat/showcase-app`
**Status:** approved design, pending implementation plan

A production-grade Compose Multiplatform demo app that exercises every
`admob-cmp` ad format in placements a real product would actually use.
It replaces the ad-hoc demo body currently rendered by
`shared/src/adCapableMain/.../PlatformAdDemo.adCapable.kt`.

---

## Invariant 0 — the SDK does not change

**No change to `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/`, or
`admob-cmp-gradle-plugin/` may be made on this branch without the owner's
explicit prior consent.** This is a showcase; the library is the subject,
not the material.

Building a real app against an SDK is how API gaps get found, so gaps are
expected. When one appears:

1. Record it — what was needed, which screen needed it, what the workaround
   was.
2. Work around it inside `:showcase` if a reasonable workaround exists.
3. Bring it to the owner. Do not patch the library mid-branch.

Any SDK change the owner subsequently approves lands as its own additive
commit, with the regenerated `api/*.klib.api` dump in that same commit, per
[AGENTS.md](../../../AGENTS.md).

The frozen ABI is untouched by this work: `:showcase` is not a published
module.

---

## Goals

A realistic host app, not a control panel. Ads sit in placements a real
product would use, and every screen carries an **Inspector** revealing the
exact SDK surface in play — placement config, live state flows, event log,
and revenue.

The two audiences are served by one artifact: the app reads as a plausible
product, and the Inspector turns it into a reference.

### Non-goals

Out of scope for this spec. Each may become its own spec later.

- Store publishing: listings, icons, screenshots, signing, R8/proguard.
- Real (non-test) ad unit IDs.
- `docs-site` integration or snippet extraction.
- Compose UI tests, screenshot tests, or emulator-based tests.
- Any network layer. Content is seeded locally.
- Desktop and web showcase surfaces. Those targets keep today's
  `UnsupportedAdPlatform()` screen.

### Success criteria

1. Builds and runs from source on an Android device/emulator and an iOS
   simulator.
2. All six ad formats render and function, each in a placement justified by
   the product rather than by the demo.
3. `./scripts/release-readiness.sh` reports `READINESS: PASS` including the
   new `:showcase` test invocations.
4. `desktopApp` and `webApp` still compile, unchanged.
5. No file under any `admob-cmp*` module is modified.

---

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | Realistic app + per-screen Inspector | A button that shows an interstitial proves nothing. Contextual placement is the hard part. |
| D2 | New `:showcase` KMP module (android + iosArm64 + iosSimulatorArm64) | Room/Nav3 have no js/wasm support; building in `shared/commonMain` would constrain library choice. |
| D3 | Nav3 + Paging3 + Room + DataStore; manual DI | Each earns its place by exercising something hard. No Koin, Hilt, Ktor, Coil, SQLDelight. |
| D4 | Product concept: reading app with a coin economy | Makes all six formats land naturally, including native-in-a-paged-feed. |
| D5 | MVI with a hand-rolled ~60-line base | Explicit enough to teach, light enough not to bury the SDK calls. |
| D6 | Ad **policy** in domain, ad **presentation** in UI | Makes policy host-testable with zero SDK or Compose involvement. |
| D7 | Domain unit tests wired into `scripts/release-readiness.sh` | Honours the standing no-tests-in-CI decision; nothing goes near `release.yml`. |
| D8 | In-repo reference app is the finish line | Keeps the branch reviewable and landable. |

---

## Architecture

### Gradle topology

```
:showcase (android, iosArm64, iosSimulatorArm64) ──> :admob-cmp-compose
:shared   (+ jvm, js, wasmJs)
   ├── adCapableMain  api(:showcase); PlatformAdDemo.adCapable = { ShowcaseApp() }
   └── jvm/js/wasmJs  unchanged → UnsupportedAdPlatform()
:androidApp  → :shared           (no structural change)
:iosApp      → Shared.framework  (no structural change)
:desktopApp / :webApp             (no change)
```

`:showcase` resolves `admob-cmp` the same way `shared/adCapableMain` does
today: `project(":admob-cmp-compose")` normally, or the published
`dev.avinya.ads:admob-cmp:$VERSION_NAME` coordinate when
`admobCmpConsumePublished=true`.

`shared`'s iOS framework exports `:showcase` so the existing
`MainViewController` and `ContentView.swift` keep working untouched. No
Xcode project edits.

`shared` keeps its `expect fun PlatformAdDemo()` seam. Only the adCapable
actual changes.

### Internal layering

By package, not by Gradle module.

```
dev.avinya.admob.showcase
  ShowcaseApp.kt      root: theme + AppGraph + NavDisplay + AppOpen + AdEffectHandler
  di/                 AppGraph (manual DI); rememberAppGraph() expect/actual
  core/mvi/           MviViewModel<S, I, E>
  core/time/          Clock (two-line interface, injected for testability)
  data/db/            Room database, DAOs, entities
  data/prefs/         DataStore settings
  data/seed/          deterministic article seed
  data/repo/          ArticleRepository, WalletRepository,
                      AdTelemetryRepository, SettingsRepository
  domain/model/       Article, Wallet, FeedItem
  domain/ad/          AdPolicy, AdDecision, ShowcasePlacements, FeedAdInserter
  ui/theme/           colour, type, shape, dark mode
  ui/component/       design-system primitives
  ui/ad/              AdEffectHandler, native ad layouts, ad slot wrappers
  ui/inspector/       Inspector sheet + tabs
  nav/                ShowcaseNavKey, NavDisplay wiring
  feature/onboarding|feed|article|library|store|settings
                      each: XxxContract.kt, XxxViewModel.kt, XxxScreen.kt
```

### The platform seam

Room and DataStore both need platform paths. Rather than plumb a `Context`
through `shared` — which would force an `androidApp` change — the seam is a
single composable:

```kotlin
@Composable expect fun rememberAppGraph(): AppGraph
```

Android resolves it from `LocalContext.current.applicationContext`; iOS from
`NSFileManager` document directory. The result is published through a
`CompositionLocal`. One seam, two small actuals.

### Placement catalog

SDK hard rule 7 forbids generated per-item placement IDs — controllers are
cached per ID for the manager's lifetime and never evicted, so
`"feed_item_$index"` leaks permanently. `ShowcasePlacements` is therefore a
single object of eight static `AdPlacement` values, all pointing at
`TestAdIds` constants with `strictTestMode = true`. Per-item native ads come
from the pool, not from per-item placements.

| Placement ID | Format | Notable config |
|---|---|---|
| `feed_banner` | Banner | `LargeAnchoredAdaptive`, `SdkManaged(60s)` |
| `feed_native` | Native | `AdCachePolicy(maxSize = 5, reloadAfterShow = true)` |
| `article_banner` | Banner | `collapsible = CollapsiblePlacement.Bottom`, `AdServerManaged` |
| `article_native` | Native | `AdCachePolicy(maxSize = 2)` |
| `article_interstitial` | Interstitial | `AdCachePolicy(maxSize = 2, reloadAfterShow = true)` |
| `store_rewarded` | Rewarded | `ServerSideVerificationOptions` demo |
| `store_rewarded_interstitial` | RewardedInterstitial | — |
| `app_open` | AppOpen | `AppOpenConfig(minBackgroundDuration = 4s, cooldownBetweenShows = 4h)` |

### Approved dependencies

| Artifact | Version | Notes |
|---|---|---|
| `androidx.navigation3:navigation3-runtime` | 1.1.5 | Google Maven |
| `org.jetbrains.androidx.navigation3:navigation3-ui` | 1.1.1 | Maven Central; has iosArm64 + iosSimulatorArm64 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0 | aligns `androidx-lifecycle` from `2.11.0-beta01` → `2.11.0` |
| `androidx.paging:paging-common`, `paging-compose` | 3.5.0 | stable, KMP with iOS targets |
| `androidx.room:room-runtime`, `room-compiler`, `room-gradle-plugin` | 2.8.4 | needs KSP |
| `androidx.sqlite:sqlite-bundled` | 2.7.0 | |
| `androidx.datastore:datastore-preferences` | 1.2.1 | latest stable |
| `com.google.devtools.ksp` | pinned in Phase 0 | latest is 2.3.11; Kotlin 2.3.20 compatibility is unverified — **see Phase 0** |

Explicitly not adopted: Koin, Hilt, Ktor, Coil, SQLDelight, kotlinx-datetime.

Kotlin stays pinned at **2.3.20**. The whole build applies one Kotlin plugin
version, and `admob-cmp`'s frozen ABI plus its experimental `abiValidation`
DSL require it. Nothing in this spec bumps it.

---

## Screens

Navigation is a Nav3 `NavBackStack<ShowcaseNavKey>` rendered by `NavDisplay`,
with `rememberViewModelStoreNavEntryDecorator` scoping a ViewModel per entry
and disposing it on pop. Four top-level tabs; `ArticleDetail` pushes on top.
Real entry lifecycles are what exercise banner and native ad disposal.

### 0 · Onboarding (first launch only)

The canonical initialisation order from
[admob-cmp/AGENTS.md](../../../admob-cmp/AGENTS.md), made visible:

```
consent.gatherConsent(config)
  → tracking.requestAuthorization()      (via AdInitializationHook at
                                          BeforeMobileAdsInitialize)
  → initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)
```

Requesting ads before ATT resolves permanently forfeits the IDFA for those
requests, so the order is load-bearing, not stylistic.

Reuses the existing `TrackingAuthorizationHook` and test app IDs from
`DemoAdStartup.kt` rather than rewriting them. Renders `Starting`,
`ConsentRequired`, `Ready`, `Failed(retryable)`.

On Android the ATT step renders as **"not applicable"** rather than being
hidden. `AdTrackingAuthorization.NotApplicable` is a real state; hiding it
teaches the wrong thing.

### 1 · Feed

The hardest integration in the app, and the reason Paging3 is in the
dependency set.

- Paging3 `Pager` over a Room `PagingSource`; page size 20.
- `PagingData<Article>` mapped to `FeedItem`, then `insertSeparators`
  injecting a `FeedItem.NativeAd` slot after every 6th article.
- **Ad slot keys derive from the preceding article's ID, never the index**:
  `"feed_native_${beforeId}"`. Index-derived keys mutate as pages load and
  thrash the native pool. This is the detail feed integrations most often get
  wrong.
- `NativeAdView(placement = FEED_NATIVE, itemKey = slotKey, layout = feedNativeLayout)`.
- Anchored adaptive `BannerAdView` pinned above the bottom bar.

### 2 · Article detail

- Inline native after the 3rd paragraph, using a **different** `AdLayout`
  than the feed's — same DSL, two shapes, proving it composes.
- Collapsible banner (`CollapsiblePlacement.Bottom`), visibly distinct from
  the feed's anchored banner.
- Reading progress debounced into Room on scroll.
- On close, `ArticleViewModel` consults `AdPolicy`. Caps: every 3rd article,
  at least 60s since the last interstitial, never within 30s of cold start,
  never on a rewarded-unlocked article. Emits `Effect.ShowInterstitial` or
  `Effect.AdSuppressed(reason)`. Suppression is never silent — it surfaces in
  the Inspector.

### 3 · Library

Bookmarks, in-progress articles, unlocked articles. **Zero ads, by design.**
Restraint is part of a good integration; Settings explains the reasoning.

### 4 · Store

- Coin wallet in Room, single row.
- "Watch an ad → +50 coins" runs the full `RewardedAdController` lifecycle
  with `load()` state rendered.
- **The reward is credited from the `onReward: (AdReward) -> Unit` callback
  passed to `show()`, not from `show()` returning `AdShowResult.Shown`.**
  Crediting on the return value is the classic rewarded-ads bug: a user who
  dismisses early still gets paid. An idempotency key in `reward_grants`
  additionally prevents a replayed callback from double-crediting.
- `RewardedInterstitialAdController` as an offer wall on entering the premium
  section.
- Unlocking a premium article spends coins with
  `appOpenCoordinator.isBlocked = true` for the duration of the transaction,
  restored in a `finally`. A genuine reason to suppress, not a synthetic one.

### 5 · Settings

Consent status, `canRequestAds`, `resetConsentForDebug()`,
`ConsentDebugGeography` picker, ATT status and `requestAuthorization()`,
`diagnostics.sdkVersion()`, `adapterStatuses()`, `openAdInspector()`,
`openDebugMenu()`, and a theme toggle.

The privacy-options button renders **only** when
`consent.privacyOptionsRequirementStatus.value ==
PrivacyOptionsRequirementStatus.Required` — per admob-cmp/AGENTS.md,
explicitly *not* gated on `ConsentStatus.Obtained`.

### 6 · Inspector

A modal bottom sheet reachable from every screen.

| Tab | Contents |
|---|---|
| Placements | The current screen's `AdPlacement` config, live `AdLoadState`, cache depth, `pool.availableAds` against `maxSize` |
| Events | Rolling `adManager.events` log interleaved with `AdPolicy` suppression decisions |
| Revenue | `AdEvent.Paid` / `AdValue` log with `AdValuePrecision`, aggregated per placement |

**Platform caveat, labelled in the UI:** the five native video events
(`VideoStarted`, `VideoPlayed`, `VideoPaused`, `VideoEnded`, `VideoMuted`)
populate on iOS and stay empty on Android. The Android GMA Next-Gen SDK
exposes no equivalent callback surface. The Inspector states this rather than
appearing broken.

### Format coverage

| Format | Where | What it proves |
|---|---|---|
| Banner | Feed, anchored adaptive | sizing, `SdkManaged` refresh |
| Banner | Article, collapsible | `CollapsiblePlacement`, `AdServerManaged` |
| Native | Feed, paged | pool, key stability, `availableAds` recovery |
| Native | Article, inline | layout DSL reuse in a different shape |
| Interstitial | Article close | frequency capping, cache, suppression reasons |
| Rewarded | Store | reward-callback correctness, persisted consequence |
| RewardedInterstitial | Store premium entry | offer-wall pattern |
| AppOpen | app-wide | `AppOpenAdCoordinator`, `isBlocked` during transactions |

---

## Data layer

### Room schema

```
articles(id PK, title, author, body, section, publishedAt, readTimeMin,
         isPremium, unlockCostCoins)
bookmarks(articleId PK → articles)
reading_progress(articleId PK → articles, scrollFraction, updatedAt)
unlocks(articleId PK → articles, unlockedAt, source: REWARDED | COINS)
wallet(id = 0 PK, coinBalance, updatedAt)               -- single row
reward_grants(idempotencyKey PK, amount, grantedAt)     -- double-credit guard
ad_events(id PK, at, placementId, format, type, detail)        -- Inspector · Events
policy_decisions(id PK, at, placementId, decision, reason)     -- Inspector · Events
paid_events(id PK, at, placementId, valueMicros, currency, precision)
                                                               -- Inspector · Revenue
```

The three log tables are **capped at 500 rows, trimmed on insert within the
same transaction.** A demo left running for an hour otherwise grows
unbounded.

Content is deterministically seeded at first launch: roughly 120 articles, so
a page size of 20 yields six real pages. No network anywhere in the app; the
only network traffic is GMA's own.

### DataStore

Preferences only:

| Key | Purpose |
|---|---|
| `themeMode` | system / light / dark |
| `onboardingComplete` | skip the consent flow after first launch |
| `consentDebugGeography` | drives `ConsentDebugGeography` for UMP testing |
| `inspectorEnabled` | show or hide the Inspector entry point on every screen |
| `adsMasterSwitch` | a local kill switch that suppresses every placement, so the app can be viewed ad-free without changing any SDK state |

### Telemetry pipeline

One app-scoped collector, started once in `AppGraph`:

```
adManager.events    ──> AdTelemetryRepository.record(event)
                          └─ AdEvent.Paid also forks into paid_events
AdPolicy decisions  ──> AdTelemetryRepository.record(decision)
```

---

## State flow

`MviViewModel<S, I, E>` exposes `state: StateFlow<S>`, `effects: Flow<E>`
backed by a buffered `Channel` so one-shots cannot replay on rotation,
`protected updateState { }`, `protected emit(effect)`, and
`abstract fun onIntent(intent: I)`. Each feature owns an `XxxContract.kt`
holding its `State` / `Intent` / `Effect` triple.

One complete round trip, showing where each layer's responsibility ends:

```
User taps Close
 → ArticleIntent.CloseArticle
 → ArticleViewModel.onIntent
      builds AdPolicySnapshot(articlesRead, lastInterstitialAt,
                              canRequestAds, sinceColdStart, wasUnlocked)
      → AdPolicy.decideInterstitial(snapshot)   ← pure: no SDK import,
                                                  no Compose, no coroutines,
                                                  injected Clock
      → AdDecision.Show(placementId) | AdDecision.Suppress(reason)
 → Effect.ShowInterstitial(id) | Effect.AdSuppressed(reason)
 → AdEffectHandler (ui/ad/)
      Show     → remember { adManager.interstitial(p) }; load(); show(); record
      Suppress → telemetry.record(decision)      ← surfaces in Inspector
 → backStack.removeLastOrNull()
```

`AdDecision.Suppress` carries a reason: `FrequencyCap`, `Cooldown`,
`ConsentMissing`, `NotReady`, `HostBusy`, `UserIsPremium`. "No ad appeared and
I don't know why" is the most common AdMob integration confusion, and making
the reason a first-class value is the highest-leverage thing this app
teaches.

`AdEffectHandler` serialises full-screen shows through a `Mutex`. `show()` is
not reentrant per controller: a second call while one is on screen returns
`NotReady` immediately rather than queuing behind it.

---

## Error handling

**Governing rule: an ad failure is never a user-facing error.** Slots collapse
to zero height. Navigation always proceeds.

| Failure | Handling |
|---|---|
| `AdManagerStatus.Failed(retryable = true)` | Onboarding shows the reason and a Retry action |
| `AdManagerStatus.Failed(retryable = false)`, `Disabled` | App runs fully usable and ad-free; persistent notice in Settings |
| `AdManagerStatus.ConsentRequired` | Route to consent; ads gated off, app still usable |
| No-fill (Android code `3` / iOS code `1`) | Slot collapses silently; logged to the Inspector as expected-normal |
| Other load errors | SDK's `AdRetryPolicy` retries; Inspector shows the attempt count |
| `AdShowResult.NotReady`, `AdShowResult.Failed` | Recorded as a suppression; navigation proceeds regardless |
| `pool.acquire()` returns null | `NativeAdView` recovers via `availableAds`; neutral placeholder meanwhile |
| Room or DataStore failure | Repositories return `Result<T>`; UI shows a retriable error state |
| Reward callback never fires | No credit. The user sees "no reward earned"; there is no consolation grant |

---

## Testing

In `showcase/src/commonTest/`, run via `:showcase:testDebugUnitTest`
(Android host) and `:showcase:iosSimulatorArm64Test`.

| Test | Covers |
|---|---|
| `AdPolicyTest` | cap boundaries (2nd vs 3rd article), cooldown boundaries (59s vs 61s), cold-start grace, consent gating, premium-unlock exemption — each asserting the specific `SuppressionReason` |
| `CoinEconomyTest` | credit, debit, insufficient funds, and a replayed idempotency key not double-crediting |
| `FeedAdInsertionTest` | every-6th index math; key derivation stability across simulated page loads, and that a prepend does not shift existing keys |
| `AdStatusMappingTest` | `AdManagerStatus` → UI state; carries over today's `DemoAdStartupTest` coverage |

`AdPolicy` takes an injected `Clock` — a two-line
`interface Clock { fun nowMillis(): Long }` in `core/time/` — so time-dependent
rules are testable without adding kotlinx-datetime.

No Compose UI tests, no screenshot tests, no emulator tests.

Related cleanup: `DemoAdStartup.kt` and `DemoAdStartupTest.kt` move from
`shared` into `:showcase`, since they are showcase startup logic rather than
shared-module logic. `shared/adCapableMain` is then left holding only the
one-line `PlatformAdDemo` actual, and `shared`'s now-empty `adCapableTest`
source set and its `dependsOn` wiring are retired.

---

## Implementation phases

Each phase ends with the app compiling and running on both platforms. No
phase leaves it broken.

### Phase 0 — toolchain spike (blocking)

Create `:showcase` with Room + KSP, one entity, one DAO. Compile and test on
`android` and `iosSimulatorArm64`.

KSP's alignment with Kotlin 2.3.20 is the only genuine unknown in this
design: KSP's newest release is 2.3.11 and its versioning is now decoupled
semver, so compatibility cannot be inferred from the version string. **No
other phase starts until this is green.** If it fails, the fallback is the
no-KSP variant — DataStore-only persistence, no Room — and that is brought
back to the owner for a decision before any app code is written.

| Phase | Contents |
|---|---|
| 1 · Foundation | module wiring, `shared` seam swap, `rememberAppGraph()` actuals, theme and design system, Nav3 shell with four empty tabs, MVI base, Room schema and seed, DataStore, repositories |
| 2 · Init and consent | consent → ATT → initialize, status gating, Settings consent/privacy/ATT/diagnostics section. First `AdManagerStatus.Ready` |
| 3 · Feed | Paging3, ad slot insertion, key stability, `NativeAdView`, anchored adaptive banner. Adds `FeedAdInsertionTest` |
| 4 · Article | detail screen, reading progress, inline native with a second layout, collapsible banner, `AdPolicy` and interstitial on close. Adds `AdPolicyTest` |
| 5 · Store and Library | wallet, rewarded with callback-based credit and idempotency, rewarded-interstitial, unlock transaction with `isBlocked`, Library screen. Adds `CoinEconomyTest` |
| 6 · App-open, Inspector, polish | `AppOpenAdCoordinator`, Inspector's three tabs, telemetry pipeline, KDoc pass, README section, release-readiness wiring |

---

## Repo impact

Files this branch is expected to touch:

- `settings.gradle.kts` — add `include(":showcase")`
- `gradle/libs.versions.toml` — approved versions plus `ksp` and `room`
  plugin aliases
- `showcase/**` — new
- `shared/src/adCapableMain/**` — swap the `PlatformAdDemo` actual body;
  remove `DemoAdStartup.kt`
- `shared/src/adCapableTest/**` — remove `DemoAdStartupTest.kt`; the source
  set is then deleted
- `shared/build.gradle.kts` — depend on and export `:showcase`; retire the
  `adCapableTest` source set
- `scripts/release-readiness.sh` — add `:showcase` tests to the existing
  Android-host and iOS test sections
- `README.md` — a short "Showcase app" section

Files this branch must **not** touch:

- Anything under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/`,
  `admob-cmp-gradle-plugin/` — see Invariant 0
- `api/*.klib.api` — the frozen ABI is unaffected by an unpublished module
- `gradle.properties` and `admob-cmp-gradle-plugin/gradle.properties`
  `VERSION_NAME` — this is not a release, and that decision is the owner's
- `.github/workflows/release.yml` — the no-tests-in-CI decision stands
- `gradle/` — no new files, no new secrets

### Pre-PR protocol

Per [AGENTS.md](../../../AGENTS.md) and [CLAUDE.md](../../../CLAUDE.md):

1. Run the **full** `./scripts/release-readiness.sh`. `--skip-docs` is not
   acceptable here, because this branch touches
   `gradle/libs.versions.toml`.
2. Report the result to the owner — sections run, sections skipped, anything
   that failed and was fixed.
3. Wait for explicit confirmation. A `READINESS: PASS` is a prerequisite for
   *asking*, not authorisation to open the PR.

---

## Open risks

| Risk | Mitigation |
|---|---|
| KSP may not align with the pinned Kotlin 2.3.20 | Phase 0 is a blocking spike; documented no-KSP fallback |
| Nav3 `navigation3-ui` for CMP is at 1.1.1 with a 1.2.0-alpha line | Pin the stable pair (`runtime` 1.1.5 + `ui` 1.1.1); the alpha line is not adopted |
| Aligning `androidx-lifecycle` `2.11.0-beta01` → `2.11.0` touches `shared`, `desktopApp`, `webApp` | Verified during Phase 1; those modules must still compile before the phase closes |
| Exporting `:showcase` from `shared`'s iOS framework could enlarge or break the framework link | Verified by the existing Xcode consumer build inside `release-readiness.sh` |
| Building a real app may reveal SDK gaps | Invariant 0: record, work around, escalate. Never patch the library on this branch |
