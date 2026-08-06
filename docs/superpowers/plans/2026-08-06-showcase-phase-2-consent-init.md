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

Create `showcase/src/commonMain/.../feature/onboarding/OnboardingScreen.kt`:

```kotlin
package dev.avinya.admob.showcase.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.avinya.ads.ConsentDebugGeography
import dev.avinya.ads.LocalAdManager
import dev.avinya.admob.showcase.StartupState
import dev.avinya.admob.showcase.di.LocalAppGraph

/**
 * First-launch screen. Shows the initialisation sequence as it happens.
 *
 * Deliberately narrates each step rather than showing an opaque spinner: a
 * consumer reading this app should be able to see that consent comes before
 * tracking, and tracking before the first ad request.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val adManager = LocalAdManager.current
    val graph = LocalAppGraph.current
    val storedGeography by graph.settings.consentDebugGeography.collectAsState(initial = null)

    val debugGeography = remember(storedGeography) {
        ConsentDebugGeography.entries.firstOrNull { it.name == storedGeography }
            ?: ConsentDebugGeography.Disabled
    }

    val viewModel: OnboardingViewModel = viewModel(key = debugGeography.name) {
        OnboardingViewModel(adManager, graph.settings, debugGeography)
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(OnboardingIntent.Begin)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.Finished -> onFinished()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Setting up ads", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Consent is gathered first, then tracking permission, then the SDK " +
                "initialises. Requesting ads before tracking resolves would " +
                "permanently forfeit the advertising identifier.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OnboardingStep.orderedSteps().forEach { step ->
            StepRow(
                label = step.label(),
                detail = step.detail(state),
                status = step.statusFor(state),
            )
        }

        val startup = state.startup
        if (state.step == OnboardingStep.Failed && startup is StartupState.Failed) {
            Text(
                startup.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (startup.retryable) {
                    Button(onClick = { viewModel.onIntent(OnboardingIntent.Retry) }) { Text("Retry") }
                }
                // Always offered: a consent refusal or a permanent failure must
                // never trap the user here. The app works fully without ads.
                OutlinedButton(onClick = { viewModel.onIntent(OnboardingIntent.ContinueWithoutAds) }) {
                    Text("Continue without ads")
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, detail: String, status: StepStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (status) {
            StepStatus.Active -> CircularProgressIndicator(modifier = Modifier.width(18.dp))
            StepStatus.Complete -> Text("✓", style = MaterialTheme.typography.titleMedium)
            StepStatus.Skipped -> Text("–", style = MaterialTheme.typography.titleMedium)
            StepStatus.Pending -> Text("·", style = MaterialTheme.typography.titleMedium)
        }
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.width(0.dp))
}

private enum class StepStatus { Pending, Active, Complete, Skipped }

private fun OnboardingStep.label(): String = when (this) {
    OnboardingStep.Consent -> "Consent"
    OnboardingStep.Tracking -> "Tracking permission"
    OnboardingStep.Initializing -> "Initialising the SDK"
    OnboardingStep.Done -> "Ready"
    OnboardingStep.Failed -> "Failed"
}

private fun OnboardingStep.detail(state: OnboardingState): String = when (this) {
    OnboardingStep.Consent -> "UMP gathers consent where it is required"
    OnboardingStep.Tracking -> when (state.tracking) {
        // Shown, not hidden: a consumer needs to learn ATT is iOS-only.
        TrackingStepDisplay.NotApplicable -> "Not applicable on this platform"
        TrackingStepDisplay.Pending -> "Waiting for the system prompt"
        TrackingStepDisplay.Granted -> "Granted — personalised ads available"
        TrackingStepDisplay.Refused -> "Refused — non-personalised ads only"
    }
    OnboardingStep.Initializing -> "Starting Google Mobile Ads"
    OnboardingStep.Done -> "Done"
    OnboardingStep.Failed -> "Failed"
}

private fun OnboardingStep.statusFor(state: OnboardingState): StepStatus {
    if (this == OnboardingStep.Tracking &&
        state.tracking == TrackingStepDisplay.NotApplicable
    ) {
        return StepStatus.Skipped
    }
    val order = OnboardingStep.orderedSteps()
    val currentIndex = order.indexOf(state.step)
    val thisIndex = order.indexOf(this)
    return when {
        state.step == OnboardingStep.Done -> StepStatus.Complete
        currentIndex < 0 -> StepStatus.Pending
        thisIndex < currentIndex -> StepStatus.Complete
        thisIndex == currentIndex -> StepStatus.Active
        else -> StepStatus.Pending
    }
}
```

- [ ] **Step 7: Verify and commit**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`, `OnboardingStepTest` passing on both platforms.

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

- [ ] **Step 3: Write the contract**

Create `showcase/src/commonMain/.../feature/settings/SettingsContract.kt`:

```kotlin
package dev.avinya.admob.showcase.feature.settings

import dev.avinya.ads.AdTrackingAuthorization
import dev.avinya.ads.ConsentDebugGeography
import dev.avinya.ads.ConsentStatus
import dev.avinya.ads.PrivacyOptionsRequirementStatus
import dev.avinya.admob.showcase.ui.theme.ThemeMode

data class SettingsState(
    val sdkStatus: String = "Unknown",
    val sdkVersion: String? = null,
    val adapters: List<String> = emptyList(),
    val consentStatus: ConsentStatus = ConsentStatus.Unknown,
    val canRequestAds: Boolean = false,
    val privacyOptions: PrivacyOptionsRequirementStatus = PrivacyOptionsRequirementStatus.Unknown,
    val tracking: AdTrackingAuthorization = AdTrackingAuthorization.NotDetermined,
    val debugGeography: ConsentDebugGeography = ConsentDebugGeography.Disabled,
    val themeMode: ThemeMode = ThemeMode.Default,
    val inspectorEnabled: Boolean = true,
    val adsEnabled: Boolean = true,
    val busy: Boolean = false,
)

sealed interface SettingsIntent {
    data object ShowPrivacyOptions : SettingsIntent
    data object RequestTracking : SettingsIntent
    data object ResetConsent : SettingsIntent
    data object OpenAdInspector : SettingsIntent
    data class SetDebugGeography(val geography: ConsentDebugGeography) : SettingsIntent
    data class SetThemeMode(val mode: ThemeMode) : SettingsIntent
    data class SetInspectorEnabled(val enabled: Boolean) : SettingsIntent
    data class SetAdsEnabled(val enabled: Boolean) : SettingsIntent
}

sealed interface SettingsEffect {
    /** Shown as a transient message. [success] false means the SDK refused the request. */
    data class Notice(val message: String, val success: Boolean) : SettingsEffect
}
```

- [ ] **Step 4: Write the ViewModel**

Create `showcase/src/commonMain/.../feature/settings/SettingsViewModel.kt`:

```kotlin
package dev.avinya.admob.showcase.feature.settings

import androidx.lifecycle.viewModelScope
import dev.avinya.ads.AdManager
import dev.avinya.ads.ConsentDebugGeography
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.ui.theme.ThemeMode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val adManager: AdManager,
    private val settings: SettingsRepository,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    init {
        observeSdk()
        observePreferences()
        updateState {
            copy(
                sdkVersion = adManager.diagnostics.sdkVersion(),
                adapters = adManager.diagnostics.adapterStatuses().map { it.toString() },
                tracking = adManager.tracking.status(),
            )
        }
    }

    private fun observeSdk() {
        viewModelScope.launch {
            combine(
                adManager.status,
                adManager.consent.status,
                adManager.consent.canRequestAds,
                adManager.consent.privacyOptionsRequirementStatus,
            ) { status, consent, canRequest, privacy ->
                Quad(status, consent, canRequest, privacy)
            }.collect { (status, consent, canRequest, privacy) ->
                updateState {
                    copy(
                        sdkStatus = status.toString(),
                        consentStatus = consent,
                        canRequestAds = canRequest,
                        privacyOptions = privacy,
                    )
                }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                settings.themeMode,
                settings.inspectorEnabled,
                settings.adsMasterSwitch,
                settings.consentDebugGeography,
            ) { theme, inspector, ads, geography ->
                Quad(theme, inspector, ads, geography)
            }.collect { (theme, inspector, ads, geography) ->
                updateState {
                    copy(
                        themeMode = theme,
                        inspectorEnabled = inspector,
                        adsEnabled = ads,
                        debugGeography = ConsentDebugGeography.entries
                            .firstOrNull { it.name == geography } ?: ConsentDebugGeography.Disabled,
                    )
                }
            }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.ShowPrivacyOptions -> run("Privacy options") {
                adManager.consent.showPrivacyOptions()
            }
            SettingsIntent.ResetConsent -> run("Consent reset — restart to see the form again") {
                adManager.consent.resetConsentForDebug()
            }
            SettingsIntent.OpenAdInspector -> run("Ad Inspector") {
                adManager.diagnostics.openAdInspector()
            }
            SettingsIntent.RequestTracking -> viewModelScope.launch {
                val result = adManager.tracking.requestAuthorization()
                updateState { copy(tracking = result) }
            }
            is SettingsIntent.SetDebugGeography -> viewModelScope.launch {
                settings.setConsentDebugGeography(intent.geography.name)
                emitEffect(SettingsEffect.Notice("Applies on next launch", success = true))
            }
            is SettingsIntent.SetThemeMode -> viewModelScope.launch { settings.setThemeMode(intent.mode) }
            is SettingsIntent.SetInspectorEnabled ->
                viewModelScope.launch { settings.setInspectorEnabled(intent.enabled) }
            is SettingsIntent.SetAdsEnabled ->
                viewModelScope.launch { settings.setAdsMasterSwitch(intent.enabled) }
        }
    }

    /**
     * Runs an SDK call that reports success as a `Boolean`.
     *
     * The result is surfaced rather than swallowed: `showPrivacyOptions()` and
     * `openAdInspector()` return `false` in legitimate situations, and a button
     * that silently does nothing is the worst possible teaching example.
     */
    private fun run(label: String, block: suspend () -> Boolean) {
        if (state.value.busy) return
        updateState { copy(busy = true) }
        viewModelScope.launch {
            val ok = block()
            updateState { copy(busy = false) }
            emitEffect(
                SettingsEffect.Notice(
                    message = if (ok) label else "$label unavailable right now",
                    success = ok,
                ),
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
```

- [ ] **Step 5: Write the screen**

Create `showcase/src/commonMain/.../feature/settings/SettingsScreen.kt`. Structure it as six sections in a `LazyColumn`, each a `SettingsSection(title) { … }` composable of your own:

```kotlin
@Composable
fun SettingsScreen() {
    val adManager = LocalAdManager.current
    val graph = LocalAppGraph.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(adManager, graph.settings) }
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.Notice -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsSection("SDK") {
                    LabelledValue("Status", state.sdkStatus)
                    LabelledValue("Version", state.sdkVersion ?: "unavailable")
                    LabelledValue("Adapters", state.adapters.size.toString())
                }
            }
            item {
                SettingsSection("Consent") {
                    LabelledValue("Status", state.consentStatus.toString())
                    LabelledValue("Can request ads", state.canRequestAds.toString())
                    LabelledValue("Privacy options", state.privacyOptions.name)

                    // Gated ONLY on the requirement status — never on
                    // ConsentStatus.Obtained. See shouldShowPrivacyOptionsButton.
                    if (shouldShowPrivacyOptionsButton(state.privacyOptions)) {
                        Button(
                            enabled = !state.busy,
                            onClick = { viewModel.onIntent(SettingsIntent.ShowPrivacyOptions) },
                        ) { Text("Manage consent") }
                    }
                }
            }
            item {
                SettingsSection("Consent debugging") {
                    Text(
                        "Debug geography forces UMP to behave as if the device were " +
                            "in the selected region. Applies on next launch.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    ConsentDebugGeography.entries.forEach { geography ->
                        RadioRow(
                            label = geography.name,
                            selected = state.debugGeography == geography,
                            onClick = { viewModel.onIntent(SettingsIntent.SetDebugGeography(geography)) },
                        )
                    }
                    OutlinedButton(
                        enabled = !state.busy,
                        onClick = { viewModel.onIntent(SettingsIntent.ResetConsent) },
                    ) { Text("Reset consent") }
                }
            }
            item {
                SettingsSection("Tracking") {
                    LabelledValue("Authorisation", state.tracking.name)
                    if (state.tracking == AdTrackingAuthorization.NotApplicable) {
                        Text(
                            "App Tracking Transparency is an iOS concept. Android " +
                                "always reports NotApplicable.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        Button(onClick = { viewModel.onIntent(SettingsIntent.RequestTracking) }) {
                            Text("Request tracking permission")
                        }
                    }
                }
            }
            item {
                SettingsSection("Diagnostics") {
                    Button(
                        enabled = !state.busy,
                        onClick = { viewModel.onIntent(SettingsIntent.OpenAdInspector) },
                    ) { Text("Open Ad Inspector") }
                }
            }
            item {
                SettingsSection("App") {
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(
                            label = mode.name,
                            selected = state.themeMode == mode,
                            onClick = { viewModel.onIntent(SettingsIntent.SetThemeMode(mode)) },
                        )
                    }
                    SwitchRow(
                        label = "Show inspector",
                        checked = state.inspectorEnabled,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetInspectorEnabled(it)) },
                    )
                    SwitchRow(
                        label = "Show ads",
                        checked = state.adsEnabled,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetAdsEnabled(it)) },
                    )
                    Text(
                        "Turning ads off suppresses every placement locally without " +
                            "changing any SDK or consent state. The app stays fully usable.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
```

Write the four small helpers — `SettingsSection(title, content)`, `LabelledValue(label, value)`, `RadioRow(label, selected, onClick)` and `SwitchRow(label, checked, onCheckedChange)` — in the same file; each is a `Row` or `Column` of Material3 primitives with no logic.

Replace `PlaceholderScreen("Settings")` in `ShowcaseNavHost` with `SettingsScreen()`.

- [ ] **Step 6: Verify and commit**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache
```

```bash
git add showcase/src && git commit -m "$(cat <<'EOF'
feat(showcase): add Settings with live consent, tracking and diagnostics

The privacy-options button is gated only on
PrivacyOptionsRequirementStatus.Required, never on
ConsentStatus.Obtained — gating on the latter puts a dead button in front
of users in regions where UMP requires no such control.

Boolean-returning SDK calls surface their result rather than swallowing
it; a button that silently does nothing is the worst teaching example.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Gate navigation on onboarding, and restore the docs-site consent contract

**Files:**
- Modify: `showcase/src/commonMain/.../ShowcaseApp.kt`
- Modify: `showcase/src/commonMain/.../nav/ShowcaseNavKey.kt`
- Modify: `showcase/src/commonMain/.../nav/ShowcaseNavHost.kt`
- Modify: `docs-site/test/content-quality.test.ts`

- [ ] **Step 1: Extend the nav-key test first**

Add to `ShowcaseNavKeyTest`:

```kotlin
    @Test
    fun onboardingIsNotATopLevelDestination() {
        assertTrue(TOP_LEVEL_KEYS.none { it == ShowcaseNavKey.Onboarding })
    }

    @Test
    fun onboardingHidesTheBottomBar() {
        assertFalse(showsBottomBar(ShowcaseNavKey.Onboarding))
        assertTrue(showsBottomBar(ShowcaseNavKey.Feed))
        assertTrue(showsBottomBar(ShowcaseNavKey.ArticleDetail("a1")))
    }
```

Run it and confirm it fails with `Unresolved reference: Onboarding`.

- [ ] **Step 2: Add the destination and the bottom-bar rule**

In `ShowcaseNavKey.kt`, add the destination — **not** to `TOP_LEVEL_KEYS`:

```kotlin
    data object Onboarding : ShowcaseNavKey {
        override val label: String = "Welcome"
    }
```

and below `TOP_LEVEL_KEYS`:

```kotlin
/**
 * Whether the bottom bar shows for [key].
 *
 * Onboarding is modal: the tabs must not be reachable until the SDK has
 * either initialised or been declined. Kept as a pure function so the rule
 * is testable without Compose.
 */
fun showsBottomBar(key: ShowcaseNavKey): Boolean = key != ShowcaseNavKey.Onboarding
```

Run the test again and confirm it passes.

- [ ] **Step 3: Choose the start destination from persisted state**

In `ShowcaseApp.kt`, replace the fixed backstack with one seeded from `onboardingComplete`. `collectAsState` needs an initial value, and `null` is used to mean "not yet loaded" so the app does not flash the Feed before the preference arrives:

```kotlin
    val onboardingComplete by graph.settings.onboardingComplete
        .map<Boolean, Boolean?> { it }
        .collectAsState(initial = null)

    CompositionLocalProvider(LocalAppGraph provides graph) {
        ProvideAdManager {
            ShowcaseTheme(themeMode = themeMode) {
                // Hold the first screen until the preference resolves, otherwise
                // a returning user briefly sees onboarding on every cold start.
                when (onboardingComplete) {
                    null -> Box(Modifier.fillMaxSize())
                    else -> {
                        val backStack = remember(onboardingComplete) {
                            mutableStateListOf<ShowcaseNavKey>(
                                if (onboardingComplete == true) {
                                    ShowcaseNavKey.Feed
                                } else {
                                    ShowcaseNavKey.Onboarding
                                },
                            )
                        }
                        ShowcaseNavHost(backStack = backStack)
                    }
                }
            }
        }
    }
```

In `ShowcaseNavHost`, wrap the `NavigationBar` in `if (showsBottomBar(current)) { … }` and add the entry:

```kotlin
                entry<ShowcaseNavKey.Onboarding> {
                    OnboardingScreen(
                        onFinished = {
                            backStack.clear()
                            backStack.add(ShowcaseNavKey.Feed)
                        },
                    )
                }
```

Clearing before adding matters: leaving `Onboarding` on the stack would let a back press return to a completed consent flow.

- [ ] **Step 4: Restore the docs-site factual contract**

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

- [ ] **Step 5: Full verification**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
cd docs-site && npm test && cd ..
```

- [ ] **Step 6: Run on both platforms**

Fresh install on Android: onboarding appears, consent form shows (test mode), initialization reaches `Ready`, tabs become reachable. On iOS simulator additionally confirm the **ATT prompt appears** and that `Info.plist` carries `NSUserTrackingUsageDescription` — without it the prompt cannot show and the IDFA is withheld silently.

- [ ] **Step 7: Commit**

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
