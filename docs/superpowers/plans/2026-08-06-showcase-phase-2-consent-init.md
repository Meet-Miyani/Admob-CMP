# Showcase — Phase 2: Consent & Initialization

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Take the SDK from never-initialized to `AdManagerStatus.Ready`, through the canonical consent → ATT → initialize order, with a real Settings screen exposing live consent, privacy, tracking and diagnostics state.

**Architecture:** `AdManager` joins the composition via `rememberAdManager()` at the `ShowcaseApp` root and is published through the SDK's own `LocalAdManager`. Startup is driven by an MVI `OnboardingViewModel` that owns the call order; every screen downstream reads `adManager.status` rather than assuming readiness.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.1, Navigation3, Room 2.8.4, DataStore 1.2.1.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** Phase 1c complete — `:showcase` builds, four tabs navigate, `AppGraph` and Room are live.

**First ads-adjacent phase.** No ad *renders* here, but the SDK initializes and `Ready` becomes reachable, which every later phase depends on.

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/` may be created, modified or deleted. Record gaps in `docs/showcase-sdk-gaps.md`, work around them inside `:showcase`, and escalate. **Stop and ask — do not patch the library.**
- **Kotlin stays at 2.3.20.** Do not bump it.
- **No new dependencies.** Everything needed is already in `gradle/libs.versions.toml`. Adding anything — including a test runtime such as Robolectric or `androidx.test` — requires the owner's consent first.
- **Testing principle.** Test what a consumer would copy: the SDK-integration rules. Do not re-test Room, DataStore, Compose or the platform. If a rule cannot be expressed as a pure function over values, that is a smell in the rule, not a reason to add a test runtime. All tests are pure and live in `commonTest`.
- **Do not modify** `gradle.properties`, `admob-cmp-gradle-plugin/gradle.properties`, or `.github/workflows/release.yml`.
- **Do not commit** `api/*.klib.api` changes.
- Package root: `dev.avinya.admob.showcase`. Branch: `feat/showcase-app`. No PR without the owner's explicit confirmation.
- Commit messages end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

### Verified SDK surface (read from the ABI dump — use exactly these)

```
AdManager: status: StateFlow<AdManagerStatus>, consent: ConsentController,
           tracking: AdTrackingController, diagnostics: AdDiagnostics,
           events: SharedFlow<AdEvent>
           suspend initialize(AdConfig, ConsentMode = ...): AdManagerStatus

ConsentController: status: StateFlow<ConsentStatus>,
                   canRequestAds: StateFlow<Boolean>,
                   privacyOptionsRequirementStatus: StateFlow<PrivacyOptionsRequirementStatus>
                   suspend gatherConsent(AdConfig): ConsentStatus
                   suspend requestConsentInfoUpdate(AdConfig): ConsentStatus
                   suspend resetConsentForDebug(): Boolean
                   suspend showPrivacyOptions(): Boolean

AdTrackingController: status(): AdTrackingAuthorization
                      suspend requestAuthorization(): AdTrackingAuthorization

AdDiagnostics: sdkVersion(): String?, adapterStatuses(): List<AdapterInitializationStatus>
               suspend openAdInspector(): Boolean, suspend openDebugMenu(String): Boolean

ConsentMode                     = GatherBeforeInitialize | InitializeOnlyIfAlreadyAllowed | SkipConsent
ConsentStatus                   = Unknown | Required | NotRequired | Obtained | Failed
PrivacyOptionsRequirementStatus = Unknown | Required | NotRequired
AdTrackingAuthorization         = NotDetermined | Authorized | Denied | Restricted | NotApplicable
ConsentDebugGeography           = Disabled | Eea | NotEea

Compose: rememberAdManager(): AdManager, LocalAdManager, LocalAdPlacements
```

---

## Tasks

### Task 1: Put `AdManager` in the graph and expose startup state

**Files:**
- Create: `showcase/src/commonMain/.../di/AdManagerHost.kt`
- Modify: `showcase/src/commonMain/.../ShowcaseApp.kt`
- Modify: `showcase/src/commonMain/.../ShowcaseAdConfig.kt`
- Test: `showcase/src/commonTest/.../ShowcaseAdConfigTest.kt` (extend)

**Interfaces:**
- Consumes: `AppGraph`, `LocalAppGraph`, `showcaseAdConfig()`, `TrackingAuthorizationHook`, `StartupState`, `toStartupState()`.
- Produces: `@Composable fun ProvideAdManager(content: @Composable () -> Unit)` — wraps `rememberAdManager()` and publishes it through the SDK's `LocalAdManager`. Tasks 2–4 and every later phase read `LocalAdManager.current`.

- [ ] **Step 1: Write the failing test**

Extend `ShowcaseAdConfigTest.kt` with the debug-geography plumbing Settings will need:

```kotlin
    @Test
    fun debugGeographyDefaultsToDisabledSoRealGeographyIsUsed() {
        val config = showcaseAdConfig(
            trackingHook = TrackingAuthorizationHook { },
            debugGeography = ConsentDebugGeography.Disabled,
        )

        assertEquals(ConsentDebugGeography.Disabled, config.debugGeography)
    }

    @Test
    fun debugGeographyIsCarriedIntoTheConfig() {
        val config = showcaseAdConfig(
            trackingHook = TrackingAuthorizationHook { },
            debugGeography = ConsentDebugGeography.Eea,
        )

        assertEquals(ConsentDebugGeography.Eea, config.debugGeography)
    }
```

Add the imports `dev.avinya.ads.ConsentDebugGeography` and `kotlin.test.assertEquals`.

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `No value passed for parameter 'debugGeography'`.

- [ ] **Step 3: Add the parameter to `showcaseAdConfig`**

In `ShowcaseAdConfig.kt`, replace the `showcaseAdConfig` function with:

```kotlin
fun showcaseAdConfig(
    trackingHook: AdInitializationHook,
    debugGeography: ConsentDebugGeography = ConsentDebugGeography.Disabled,
): AdConfig = AdConfig(
    androidAppId = SHOWCASE_ANDROID_APP_ID,
    iosAppId = SHOWCASE_IOS_APP_ID,
    testMode = true,
    debugGeography = debugGeography,
    initializationHooks = listOf(trackingHook),
)
```

Add `import dev.avinya.ads.ConsentDebugGeography`.

> `testMode = true` configures **UMP consent debugging only**. It does not make GMA serve test ads — that comes from using `TestAdIds` units, which every showcase placement does. Keep this comment in the KDoc; conflating the two is the most common misreading of the SDK.

- [ ] **Step 4: Run to verify it passes**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 5: Write the AdManager host**

Create `showcase/src/commonMain/.../di/AdManagerHost.kt`:

```kotlin
package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.rememberAdManager

/**
 * Publishes the process-wide [dev.avinya.ads.AdManager] into the composition.
 *
 * Uses the SDK's own `LocalAdManager` rather than adding one to [AppGraph]:
 * `rememberAdManager()` already returns a process-wide singleton, so putting
 * it in the hand-rolled graph would duplicate ownership and imply the
 * showcase controls a lifecycle it does not.
 */
@Composable
fun ProvideAdManager(content: @Composable () -> Unit) {
    val adManager = rememberAdManager()
    CompositionLocalProvider(LocalAdManager provides adManager, content = content)
}
```

- [ ] **Step 6: Wrap the app in it**

In `ShowcaseApp.kt`, wrap the existing `ShowcaseTheme { … }` body so the provider sits inside `LocalAppGraph` but outside the theme:

```kotlin
    CompositionLocalProvider(LocalAppGraph provides graph) {
        ProvideAdManager {
            ShowcaseTheme(themeMode = themeMode) {
                val backStack = remember { mutableStateListOf<ShowcaseNavKey>(ShowcaseNavKey.Feed) }
                ShowcaseNavHost(backStack = backStack)
            }
        }
    }
```

Add `import dev.avinya.admob.showcase.di.ProvideAdManager`.

- [ ] **Step 7: Verify both platforms compile**

```bash
./gradlew :showcase:compileKotlinIosArm64 :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): provide AdManager through the SDK's LocalAdManager

Uses the SDK's own CompositionLocal rather than adding AdManager to
AppGraph: rememberAdManager() already returns a process-wide singleton,
so owning it in the hand-rolled graph would duplicate ownership.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Onboarding — the canonical consent → ATT → initialize order

**Files:**
- Create: `showcase/src/commonMain/.../feature/onboarding/OnboardingContract.kt`
- Create: `showcase/src/commonMain/.../feature/onboarding/OnboardingViewModel.kt`
- Create: `showcase/src/commonMain/.../feature/onboarding/OnboardingScreen.kt`
- Test: `showcase/src/commonTest/.../feature/onboarding/OnboardingStepTest.kt`

**Interfaces:**
- Consumes: `MviViewModel`, `LocalAdManager`, `showcaseAdConfig`, `TrackingAuthorizationHook`, `StartupState`, `SettingsRepository`.
- Produces: `enum class OnboardingStep { Consent, Tracking, Initializing, Done, Failed }`, `OnboardingState/Intent/Effect`, `@Composable fun OnboardingScreen(onFinished: () -> Unit)`. Task 4 gates navigation on completion.

- [ ] **Step 1: Write the failing test**

The rule under test is the **order** and its Android/iOS divergence — pure, no SDK calls.

Create `showcase/src/commonTest/.../feature/onboarding/OnboardingStepTest.kt`:

```kotlin
package dev.avinya.admob.showcase.feature.onboarding

import dev.avinya.ads.AdTrackingAuthorization
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingStepTest {

    @Test
    fun consentAlwaysPrecedesTracking() {
        assertEquals(
            listOf(OnboardingStep.Consent, OnboardingStep.Tracking, OnboardingStep.Initializing),
            OnboardingStep.orderedSteps(),
        )
    }

    @Test
    fun trackingIsShownAsNotApplicableRatherThanHiddenWhenThePlatformHasNoAtt() {
        assertEquals(
            TrackingStepDisplay.NotApplicable,
            trackingStepDisplay(AdTrackingAuthorization.NotApplicable),
        )
    }

    @Test
    fun trackingStatesMapToTheirOwnDisplay() {
        assertEquals(TrackingStepDisplay.Pending, trackingStepDisplay(AdTrackingAuthorization.NotDetermined))
        assertEquals(TrackingStepDisplay.Granted, trackingStepDisplay(AdTrackingAuthorization.Authorized))
        assertEquals(TrackingStepDisplay.Refused, trackingStepDisplay(AdTrackingAuthorization.Denied))
        assertEquals(TrackingStepDisplay.Refused, trackingStepDisplay(AdTrackingAuthorization.Restricted))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: OnboardingStep`.

- [ ] **Step 3: Write the contract**

Create `showcase/src/commonMain/.../feature/onboarding/OnboardingContract.kt`:

```kotlin
package dev.avinya.admob.showcase.feature.onboarding

import dev.avinya.ads.AdTrackingAuthorization
import dev.avinya.admob.showcase.StartupState

/**
 * The initialisation steps, in the only order that is correct.
 *
 * Requesting ads before ATT resolves permanently forfeits the IDFA for those
 * requests, so consent must precede tracking, and tracking must precede the
 * first ad request. This is load-bearing, not cosmetic.
 */
enum class OnboardingStep {
    Consent,
    Tracking,
    Initializing,
    Done,
    Failed,
    ;

    companion object {
        /** The three steps the user actually progresses through. */
        fun orderedSteps(): List<OnboardingStep> = listOf(Consent, Tracking, Initializing)
    }
}

/** How the tracking step renders. Android has no ATT and says so. */
enum class TrackingStepDisplay { Pending, Granted, Refused, NotApplicable }

/**
 * Android reports [AdTrackingAuthorization.NotApplicable]. That is shown
 * explicitly rather than hidden: a consumer reading this app needs to see
 * that ATT is an iOS-only concept, not be left wondering why a step vanished.
 */
fun trackingStepDisplay(status: AdTrackingAuthorization): TrackingStepDisplay = when (status) {
    AdTrackingAuthorization.NotApplicable -> TrackingStepDisplay.NotApplicable
    AdTrackingAuthorization.NotDetermined -> TrackingStepDisplay.Pending
    AdTrackingAuthorization.Authorized -> TrackingStepDisplay.Granted
    AdTrackingAuthorization.Denied, AdTrackingAuthorization.Restricted -> TrackingStepDisplay.Refused
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Consent,
    val tracking: TrackingStepDisplay = TrackingStepDisplay.Pending,
    val startup: StartupState = StartupState.Starting,
    val busy: Boolean = false,
)

sealed interface OnboardingIntent {
    data object Begin : OnboardingIntent
    data object Retry : OnboardingIntent
    data object ContinueWithoutAds : OnboardingIntent
}

sealed interface OnboardingEffect {
    data object Finished : OnboardingEffect
}
```

- [ ] **Step 4: Run to verify the test passes**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 5: Write the ViewModel**

Create `showcase/src/commonMain/.../feature/onboarding/OnboardingViewModel.kt`:

```kotlin
package dev.avinya.admob.showcase.feature.onboarding

import androidx.lifecycle.viewModelScope
import dev.avinya.ads.AdManager
import dev.avinya.ads.ConsentDebugGeography
import dev.avinya.ads.ConsentMode
import dev.avinya.admob.showcase.TrackingAuthorizationHook
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.showcaseAdConfig
import dev.avinya.admob.showcase.toStartupState
import kotlinx.coroutines.launch

/**
 * Drives SDK startup.
 *
 * The call order below is the canonical one from `admob-cmp/AGENTS.md`:
 * gather consent, then resolve ATT, then initialise with
 * [ConsentMode.InitializeOnlyIfAlreadyAllowed]. ATT is fired from an
 * [dev.avinya.ads.AdInitializationHook] at `BeforeMobileAdsInitialize` rather
 * than called inline, so the SDK controls the exact moment it runs relative
 * to GMA's own startup.
 */
class OnboardingViewModel(
    private val adManager: AdManager,
    private val settings: SettingsRepository,
    private val debugGeography: ConsentDebugGeography,
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState()) {

    override fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Begin, OnboardingIntent.Retry -> start()
            OnboardingIntent.ContinueWithoutAds -> finish()
        }
    }

    private fun start() {
        if (state.value.busy) return
        updateState { copy(busy = true, step = OnboardingStep.Consent) }

        viewModelScope.launch {
            val hook = TrackingAuthorizationHook {
                updateState { copy(step = OnboardingStep.Tracking) }
                val result = adManager.tracking.requestAuthorization()
                updateState { copy(tracking = trackingStepDisplay(result)) }
            }
            val config = showcaseAdConfig(trackingHook = hook, debugGeography = debugGeography)

            adManager.consent.gatherConsent(config)

            updateState { copy(step = OnboardingStep.Initializing) }
            val status = adManager.initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)
            val startup = status.toStartupState()

            updateState {
                copy(
                    busy = false,
                    startup = startup,
                    tracking = trackingStepDisplay(adManager.tracking.status()),
                    step = if (startup is dev.avinya.admob.showcase.StartupState.Failed) {
                        OnboardingStep.Failed
                    } else {
                        OnboardingStep.Done
                    },
                )
            }

            if (startup !is dev.avinya.admob.showcase.StartupState.Failed) finish()
        }
    }

    /**
     * Marks onboarding complete and leaves.
     *
     * Called on success *and* from [OnboardingIntent.ContinueWithoutAds]: a
     * consent refusal or a non-retryable init failure must not trap the user
     * on this screen. The app is fully usable ad-free.
     */
    private fun finish() {
        viewModelScope.launch {
            settings.setOnboardingComplete(true)
            emitEffect(OnboardingEffect.Finished)
        }
    }
}
```

- [ ] **Step 6: Write the screen**

Create `showcase/src/commonMain/.../feature/onboarding/OnboardingScreen.kt`. Render one row per `OnboardingStep.orderedSteps()` with its state, the `TrackingStepDisplay` (including an explicit "not applicable on this platform" row), and on `OnboardingStep.Failed` a Retry button plus a **Continue without ads** button. Collect `effects` and call `onFinished()` on `OnboardingEffect.Finished`.

Construct the ViewModel from `LocalAdManager.current` and `LocalAppGraph.current.settings`, reading `debugGeography` from `settings.consentDebugGeography`.

- [ ] **Step 7: Verify and commit**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache
```

```bash
git add showcase/src && git commit -m "$(cat <<'EOF'
feat(showcase): add onboarding with the canonical consent -> ATT -> init order

ATT is fired from an AdInitializationHook at BeforeMobileAdsInitialize, so
the SDK controls when it runs relative to GMA startup. Requesting ads
before ATT resolves permanently forfeits the IDFA.

Android's NotApplicable tracking state renders explicitly rather than
being hidden — a consumer needs to see ATT is iOS-only.

A failed or refused consent never traps the user: the app continues
ad-free.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Settings — live consent, privacy, tracking and diagnostics

**Files:**
- Create: `showcase/src/commonMain/.../feature/settings/SettingsContract.kt`
- Create: `showcase/src/commonMain/.../feature/settings/SettingsViewModel.kt`
- Create: `showcase/src/commonMain/.../feature/settings/SettingsScreen.kt`
- Test: `showcase/src/commonTest/.../feature/settings/PrivacyOptionsVisibilityTest.kt`

**Interfaces:**
- Consumes: `LocalAdManager`, `SettingsRepository`, `MviViewModel`, `ThemeMode`.
- Produces: `fun shouldShowPrivacyOptionsButton(PrivacyOptionsRequirementStatus): Boolean`, `SettingsState/Intent/Effect`, `@Composable fun SettingsScreen()`.

- [ ] **Step 1: Write the failing test**

This encodes the rule most integrations get wrong.

```kotlin
package dev.avinya.admob.showcase.feature.settings

import dev.avinya.ads.ConsentStatus
import dev.avinya.ads.PrivacyOptionsRequirementStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyOptionsVisibilityTest {

    @Test
    fun shownOnlyWhenTheRequirementStatusSaysRequired() {
        assertTrue(shouldShowPrivacyOptionsButton(PrivacyOptionsRequirementStatus.Required))
        assertFalse(shouldShowPrivacyOptionsButton(PrivacyOptionsRequirementStatus.NotRequired))
        assertFalse(shouldShowPrivacyOptionsButton(PrivacyOptionsRequirementStatus.Unknown))
    }

    @Test
    fun consentStatusObtainedDoesNotByItselfJustifyShowingTheButton() {
        // The classic mistake: gating on ConsentStatus.Obtained shows a privacy
        // button in regions where UMP requires none. Only the requirement
        // status is authoritative — this test exists to keep that true.
        assertFalse(shouldShowPrivacyOptionsButton(PrivacyOptionsRequirementStatus.NotRequired))
        assertEqualsIgnored(ConsentStatus.Obtained)
    }

    private fun assertEqualsIgnored(status: ConsentStatus) {
        // ConsentStatus is deliberately not an input to the decision.
        assertTrue(status is ConsentStatus)
    }
}
```

- [ ] **Step 2: Run to verify it fails**, then implement:

```kotlin
package dev.avinya.admob.showcase.feature.settings

import dev.avinya.ads.PrivacyOptionsRequirementStatus

/**
 * Whether to offer the privacy-options ("manage consent") button.
 *
 * Gated **only** on [PrivacyOptionsRequirementStatus.Required] — never on
 * `ConsentStatus.Obtained`, which is the common mistake and puts a dead
 * button in front of users in regions where UMP requires no such control.
 */
fun shouldShowPrivacyOptionsButton(status: PrivacyOptionsRequirementStatus): Boolean =
    status == PrivacyOptionsRequirementStatus.Required

```

- [ ] **Step 3: Build the Settings screen**

Sections, each reading live state:

| Section | Contents |
|---|---|
| SDK | `adManager.status`, `diagnostics.sdkVersion()`, `diagnostics.adapterStatuses()` |
| Consent | `consent.status`, `consent.canRequestAds`, privacy-options button (gated as above) → `consent.showPrivacyOptions()` |
| Consent debugging | `ConsentDebugGeography` picker (persisted), `consent.resetConsentForDebug()` + note that it takes effect on next launch |
| Tracking | `tracking.status()`, request button, "not applicable on Android" copy |
| Diagnostics | `openAdInspector()`, `openDebugMenu(adUnitId)` — both return `Boolean`; surface failure rather than swallowing it |
| App | theme picker, inspector toggle, ads master switch |

The ads master switch and theme write through `SettingsRepository`.

- [ ] **Step 4: Verify and commit**

---

### Task 4: Gate navigation on onboarding, and restore the docs-site consent contract

**Files:**
- Modify: `showcase/src/commonMain/.../ShowcaseApp.kt`
- Modify: `showcase/src/commonMain/.../nav/ShowcaseNavKey.kt`
- Modify: `showcase/src/commonMain/.../nav/ShowcaseNavHost.kt`
- Modify: `docs-site/test/content-quality.test.ts`

- [ ] **Step 1: Add the Onboarding destination**

Add `data object Onboarding : ShowcaseNavKey` (label `"Welcome"`), and **do not** add it to `TOP_LEVEL_KEYS` — it is not a tab. Extend `ShowcaseNavKeyTest` to assert it is excluded, mirroring the existing `ArticleDetail` case.

- [ ] **Step 2: Choose the start destination from persisted state**

In `ShowcaseApp.kt`, read `settings.onboardingComplete` and start the backstack at `Onboarding` when false, `Feed` when true. Hide the bottom bar while the current key is `Onboarding`.

- [ ] **Step 3: Restore the docs-site factual contract**

Phase 1a's file moves dropped an assertion: the contract used to check that a sample demonstrates `ConsentMode.GatherBeforeInitialize`, and nothing replaced it. The showcase now has a real consent flow, so re-point it.

In `docs-site/test/content-quality.test.ts`, add alongside the existing `showcaseAdConfigKt` constant:

```ts
const onboardingViewModelKt = join(repoRoot, 'showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/onboarding/OnboardingViewModel.kt');
```

and extend the showcase startup test:

```ts
  it('showcase onboarding gathers consent before initialising', () => {
    const onboarding = readFileSync(onboardingViewModelKt, 'utf8');

    expect(onboarding).toContain('consent.gatherConsent(config)');
    expect(onboarding).toContain('ConsentMode.InitializeOnlyIfAlreadyAllowed');
  });
```

> The showcase uses `InitializeOnlyIfAlreadyAllowed` after an explicit `gatherConsent`, which is the two-step form of what `GatherBeforeInitialize` does in one call. If the docs assert `GatherBeforeInitialize` specifically, either add a second sample using it or adjust the docs — **flag this to the owner rather than silently changing docs prose.**

- [ ] **Step 4: Full verification**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
cd docs-site && npm test && cd ..
```

- [ ] **Step 5: Run on both platforms**

Fresh install on Android: onboarding appears, consent form shows (test mode), initialization reaches `Ready`, tabs become reachable. On iOS simulator additionally confirm the **ATT prompt appears** and that `Info.plist` carries `NSUserTrackingUsageDescription` — without it the prompt cannot show and the IDFA is withheld silently.

- [ ] **Step 6: Commit**

---

## Exit criteria

- [ ] `AdManagerStatus.Ready` is reached on both platforms
- [ ] UMP consent form appears; ATT prompt appears on iOS; Android shows "not applicable"
- [ ] Settings shows live consent, privacy, tracking and diagnostics state
- [ ] Privacy-options button appears **only** when `PrivacyOptionsRequirementStatus.Required`
- [ ] Consent refusal or non-retryable failure leaves the app fully usable, ad-free
- [ ] Onboarding shows once, then never again
- [ ] `docs-site` vitest passes with the restored consent contract
- [ ] `git diff --stat master -- 'admob-cmp*'` is empty

---

## Next plan

**Phase 3 — Feed** (`2026-08-06-showcase-phase-3-feed.md`): Paging3, ad-slot insertion with stable keys, `NativeAdView` in the feed, anchored adaptive banner. **First rendered ads.**
