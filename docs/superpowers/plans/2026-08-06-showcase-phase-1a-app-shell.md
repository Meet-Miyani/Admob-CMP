# Showcase — Phase 1a: App Shell

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `androidApp` and `iosApp` launch into `ShowcaseApp()`, and add the design system and MVI base every later screen builds on.

**Architecture:** `shared/adCapableMain` depends on `:showcase` and swaps its `PlatformAdDemo` actual to render `ShowcaseApp()`. The jvm/js/wasmJs actuals are untouched, so `desktopApp` and `webApp` keep compiling and keep showing the unsupported-platform screen.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.1, AGP 9.2.1, Room 2.8.4 + KSP, androidx.sqlite bundled 2.7.0, DataStore 1.2.1, Navigation3 (runtime 1.1.5 / CMP ui 1.1.1), Paging 3.5.0.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** [Phase 0 — Toolchain Spike](2026-08-06-showcase-phase-0-toolchain-spike.md) complete, with the decision gate at **outcome A**.

**No ads render in this plan.** The SDK is touched only to port the app-id config across from `shared`.

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/` may be created, modified or deleted. If the showcase needs something the SDK does not expose: record it in `docs/showcase-sdk-gaps.md` using the format in the spec's Invariant 0, work around it inside `:showcase`, and escalate to the owner. **Stop and ask — do not patch the library.**
- **Kotlin stays at 2.3.20.** Do not bump `kotlin` in `gradle/libs.versions.toml`. The whole build applies one Kotlin plugin version and admob-cmp's frozen ABI plus its experimental `abiValidation` DSL require exactly this version.
- **No dependencies beyond the approved list** in the spec's "Approved dependencies" table. Specifically **not** approved: Koin, Hilt, Ktor, Coil, SQLDelight, kotlinx-datetime, kotlinx-serialization. Adding any requires the owner's consent first.
- **Do not modify** `gradle.properties` or `admob-cmp-gradle-plugin/gradle.properties` `VERSION_NAME`. This is not a release.
- **Do not modify** `.github/workflows/release.yml`. No SDK tests go into CI — standing owner decision.
- **Do not create files under `gradle/`** other than editing `gradle/libs.versions.toml`. No new secrets.
- **Do not commit** `api/*.klib.api` changes. `:showcase` is unpublished; the frozen ABI is unaffected.
- `minSdk` is 26, `compileSdk` 37, `targetSdk` 36, JVM target 11 — read from `libs.versions.toml`, never hardcoded.
- Package root for all new code: `dev.avinya.admob.showcase`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Branch is `feat/showcase-app`. Do not open a PR; the pre-PR protocol in AGENTS.md is a hard stop requiring the owner's explicit confirmation.

### Three corrections to the spec, applied by this plan

The spec was written before the build files were read in detail. These three points supersede it; fold them back into the spec if it is ever revised.

1. **Android test task is `testAndroidHostTest`, not `testDebugUnitTest`.** `:showcase` uses `com.android.kotlin.multiplatform.library` (same as `shared` and `admob-cmp-core`), whose host-test task is `testAndroidHostTest`. `testDebugUnitTest` does not exist for that plugin.
2. **No framework `export` is needed.** The spec says `shared` "exports" `:showcase`. It does not need to. `ContentView.swift` only calls `MainViewControllerKt.MainViewController()`; no Swift code references a `:showcase` type. `shared`'s framework is `isStatic = true`, so showcase code is linked in regardless — `export` only controls generated Obj-C headers. Use plain `implementation(project(":showcase"))`. Simpler, and it keeps the framework header surface unchanged.
3. **`:showcase` must apply the `dev.avinya.ads.admob-cmp` Gradle plugin.** The spec does not mention it. Without it, `:showcase:iosSimulatorArm64Test` fails at link with `Undefined symbols: _OBJC_CLASS_$_GADBannerView`. Supplying GMA/UMP frameworks to Kotlin/Native **test executables** is that plugin's entire purpose — an iOS *app* resolves them from Xcode's SPM packages, but a test executable has no Xcode.

### One open decision for the owner (do not resolve unilaterally)

Nav3's `rememberNavBackStack` requires `NavKey`s to be `@Serializable`, which needs the **kotlinx-serialization** plugin — not on the approved dependency list. The Phase 1c plan therefore uses a plain `mutableStateListOf` backstack, which works fully but **does not survive process death**. Raise this with the owner when Phase 1c completes; do not add kotlinx-serialization without consent.

---

---

## File Structure

**Created:** `showcase/src/commonMain/.../ShowcaseApp.kt`, `ShowcaseAdConfig.kt`, `ui/theme/{Color,Type,Theme}.kt`, `core/time/Clock.kt` (+ android/ios actuals), `core/mvi/MviViewModel.kt`, and their tests.

**Modified:** `shared/build.gradle.kts`, `shared/src/adCapableMain/.../PlatformAdDemo.adCapable.kt`.

**Deleted:** `shared/src/adCapableMain/.../DemoAdStartup.kt`, `shared/src/adCapableTest/.../DemoAdStartupTest.kt`.

---

### Task 1: Wire `:showcase` into `:shared` so the app launches on both platforms

First user-visible milestone. After this task the Android and iOS apps launch into showcase code.

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseAdConfig.kt`
- Create: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/ShowcaseAdConfigTest.kt`
- Delete: `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartup.kt`
- Delete: `shared/src/adCapableTest/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartupTest.kt`

**Interfaces:**
- Consumes: the `:showcase` module from the Phase 0 plan.
- Produces: `@Composable fun ShowcaseApp()` — the single public entry point of `:showcase`, called by `shared`. Also `showcaseAdConfig(trackingHook: AdInitializationHook): AdConfig`, `SHOWCASE_ANDROID_APP_ID`, `SHOWCASE_IOS_APP_ID`, `class TrackingAuthorizationHook(requestAuthorization: suspend () -> Unit)`, and `fun AdManagerStatus.toStartupState(): StartupState` — all consumed by the Phase 2 plan.

- [ ] **Step 1: Read the file being moved**

```bash
cat shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartup.kt
cat shared/src/adCapableTest/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartupTest.kt
```

Its contents are ported verbatim below apart from package, names and visibility. Read it first so the port is a move, not a rewrite.

- [ ] **Step 2: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/ShowcaseAdConfigTest.kt`:

```kotlin
package dev.avinya.admob.showcase

import dev.avinya.ads.AdError
import dev.avinya.ads.AdErrorCode
import dev.avinya.ads.AdManagerStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ShowcaseAdConfigTest {

    @Test
    fun usesGoogleSampleAppIdsSoNoProductionInventoryIsEverRequested() {
        assertEquals("ca-app-pub-3940256099942544~3347511713", SHOWCASE_ANDROID_APP_ID)
        assertEquals("ca-app-pub-3940256099942544~1458002511", SHOWCASE_IOS_APP_ID)
    }

    @Test
    fun mapsInitialisingStatusesToStarting() {
        assertEquals(StartupState.Starting, AdManagerStatus.Idle.toStartupState())
        assertEquals(StartupState.Starting, AdManagerStatus.Initializing.toStartupState())
    }

    @Test
    fun mapsReadyAndConsentRequiredToTheirOwnStates() {
        assertEquals(StartupState.Ready, AdManagerStatus.Ready.toStartupState())
        assertEquals(StartupState.ConsentRequired, AdManagerStatus.ConsentRequired.toStartupState())
    }

    @Test
    fun carriesRetryabilityThroughFailure() {
        val status = AdManagerStatus.Failed(
            error = AdError(code = AdErrorCode.SDK_NOT_READY, message = "not ready"),
            retryable = true,
        )

        assertEquals(StartupState.Failed("not ready", retryable = true), status.toStartupState())
    }
}
```

Both signatures above were read from the ABI dump and are exact: `AdError(code: String? = …, message: String, domain: String? = …, responseInfo: AdResponseInfo? = …)` and `AdManagerStatus.Failed(error: AdError, retryable: Boolean)`. `AdErrorCode.SDK_NOT_READY` is a `String` const.

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: SHOWCASE_ANDROID_APP_ID`.

- [ ] **Step 4: Port the startup config into `:showcase`**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseAdConfig.kt`:

```kotlin
package dev.avinya.admob.showcase

import dev.avinya.ads.AdConfig
import dev.avinya.ads.AdInitializationHook
import dev.avinya.ads.AdInitializationPhase
import dev.avinya.ads.AdManagerStatus

/** Google's public sample app id for Android. Never a production unit. */
const val SHOWCASE_ANDROID_APP_ID: String = "ca-app-pub-3940256099942544~3347511713"

/** Google's public sample app id for iOS. Never a production unit. */
const val SHOWCASE_IOS_APP_ID: String = "ca-app-pub-3940256099942544~1458002511"

/**
 * The showcase's [AdConfig].
 *
 * `testMode = true` configures **UMP consent debugging** only — it does not make
 * GMA serve test ads. Test ads come from using [dev.avinya.ads.TestAdIds] units,
 * which every showcase placement does.
 */
fun showcaseAdConfig(trackingHook: AdInitializationHook): AdConfig = AdConfig(
    androidAppId = SHOWCASE_ANDROID_APP_ID,
    iosAppId = SHOWCASE_IOS_APP_ID,
    testMode = true,
    initializationHooks = listOf(trackingHook),
)

/**
 * Runs App Tracking Transparency before GMA initialises.
 *
 * Order is load-bearing: requesting ads before ATT resolves permanently
 * forfeits the IDFA for those requests. Android has no ATT and reports
 * [dev.avinya.ads.AdTrackingAuthorization.NotApplicable].
 */
class TrackingAuthorizationHook(
    private val requestAuthorization: suspend () -> Unit,
) : AdInitializationHook {
    override suspend fun onPhase(phase: AdInitializationPhase, config: AdConfig) {
        if (phase == AdInitializationPhase.BeforeMobileAdsInitialize) {
            requestAuthorization()
        }
    }
}

/** What the UI needs to know about SDK startup, collapsed from [AdManagerStatus]. */
sealed interface StartupState {
    data object Starting : StartupState
    data object Ready : StartupState
    data object ConsentRequired : StartupState
    data class Failed(val message: String, val retryable: Boolean) : StartupState
}

fun AdManagerStatus.toStartupState(): StartupState = when {
    this == AdManagerStatus.Idle || this == AdManagerStatus.Initializing -> StartupState.Starting
    this == AdManagerStatus.Ready -> StartupState.Ready
    this == AdManagerStatus.ConsentRequired -> StartupState.ConsentRequired
    this is AdManagerStatus.Failed -> StartupState.Failed(error.message, retryable)
    this is AdManagerStatus.Disabled -> StartupState.Failed(reason, retryable = false)
    else -> StartupState.Failed("Unknown SDK state.", retryable = true)
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **PASS**, all four tests.

- [ ] **Step 6: Create the app entry point**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`. A placeholder for now; the Phase 1c plan replaces the body with the Nav3 shell.

```kotlin
package dev.avinya.admob.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Root of the showcase app and the only public composable `:showcase` exposes.
 *
 * `shared` calls this from its `PlatformAdDemo` actual on Android and iOS;
 * desktop and web keep rendering `UnsupportedAdPlatform()`.
 */
@Composable
fun ShowcaseApp() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Showcase app — foundation")
        }
    }
}
```

- [ ] **Step 7: Depend on `:showcase` from `shared`**

In `shared/build.gradle.kts`, inside `val adCapableMain by creating { dependencies { ... } }`, add as the first line of the `dependencies` block:

```kotlin
                implementation(project(":showcase"))
```

No `api(...)` and no framework `export(...)` — Swift never references a `:showcase` type, and `shared`'s framework is static, so showcase code links in regardless.

Then **delete** the now-unused `adCapableTest` source set. Remove this whole block:

```kotlin
        val adCapableTest by creating {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
```

and remove the two `dependsOn(adCapableTest)` lines from `androidHostTest` and `iosTest`, leaving:

```kotlin
        val androidHostTest by getting
        val iosTest by getting
```

- [ ] **Step 8: Swap the `PlatformAdDemo` actual**

Replace the whole contents of `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt` with:

```kotlin
package dev.avinya.admob.cmp.demo

import androidx.compose.runtime.Composable
import dev.avinya.admob.showcase.ShowcaseApp

@Composable
internal actual fun PlatformAdDemo() {
    ShowcaseApp()
}
```

- [ ] **Step 9: Delete the moved files**

```bash
git rm shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartup.kt
git rm shared/src/adCapableTest/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartupTest.kt
```

- [ ] **Step 10: Verify every consumer still compiles**

Desktop and web must be unaffected — that is the whole point of keeping the `expect`/`actual` seam.

```bash
./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

```bash
./gradlew :shared:iosSimulatorArm64Test :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Run the app on an Android emulator and confirm the placeholder renders**

```bash
./gradlew :androidApp:installDebug
```

Launch the app. Expected: a centred "Showcase app — foundation".

- [ ] **Step 12: Commit**

```bash
git add -A shared showcase
git commit -m "$(cat <<'EOF'
feat(showcase): render ShowcaseApp from shared on android and iOS

Swaps shared's PlatformAdDemo actual to call ShowcaseApp() and moves the
startup config and its test out of shared into :showcase, where they
belong. shared's now-empty adCapableTest source set is retired.

Uses implementation(), not api()+export(): no Swift code references a
:showcase type and shared's framework is static, so the Obj-C header
surface is unchanged.

desktopApp and webApp are untouched and still compile.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Design system — colour, type, theme

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Color.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Type.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Theme.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/ui/theme/ThemeModeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class ThemeMode { System, Light, Dark }` with `fun ThemeMode.isDark(systemInDark: Boolean): Boolean`, and `@Composable fun ShowcaseTheme(themeMode: ThemeMode, content: @Composable () -> Unit)`. The Phase 1b plan persists `ThemeMode`; the Phase 1c plan wraps the Nav shell in `ShowcaseTheme`.

- [ ] **Step 1: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/ui/theme/ThemeModeTest.kt`:

```kotlin
package dev.avinya.admob.showcase.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {

    @Test
    fun systemFollowsThePlatformSetting() {
        assertTrue(ThemeMode.System.isDark(systemInDark = true))
        assertFalse(ThemeMode.System.isDark(systemInDark = false))
    }

    @Test
    fun explicitModesIgnoreThePlatformSetting() {
        assertTrue(ThemeMode.Dark.isDark(systemInDark = false))
        assertFalse(ThemeMode.Light.isDark(systemInDark = true))
    }

    @Test
    fun defaultsToSystem() {
        assertEquals(ThemeMode.System, ThemeMode.Default)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: ThemeMode`.

- [ ] **Step 3: Write the colour scheme**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Color.kt`:

```kotlin
package dev.avinya.admob.showcase.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF101418)
private val Paper = Color(0xFFFBFAF7)
private val Accent = Color(0xFF2D6A4F)
private val AccentDark = Color(0xFF74C69D)
private val Muted = Color(0xFF6B7280)

internal val ShowcaseLightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Muted,
)

internal val ShowcaseDarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Color(0xFF181D23),
    onSurface = Paper,
    onSurfaceVariant = Color(0xFF9CA3AF),
)
```

- [ ] **Step 4: Write the typography**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Type.kt`:

```kotlin
package dev.avinya.admob.showcase.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Reading-app typography: generous body line height, restrained headings.
 * Uses platform default fonts — bundling a typeface is out of scope.
 */
internal val ShowcaseTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)
```

- [ ] **Step 5: Write the theme**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Theme.kt`:

```kotlin
package dev.avinya.admob.showcase.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** User-selectable theme preference. Persisted by `SettingsRepository`. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        val Default: ThemeMode = System
    }
}

/**
 * Resolves the preference against the platform setting.
 * Pure, so it is testable without Compose.
 */
fun ThemeMode.isDark(systemInDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemInDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun ShowcaseTheme(
    themeMode: ThemeMode = ThemeMode.Default,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark(systemInDark = isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) ShowcaseDarkColors else ShowcaseLightColors,
        typography = ShowcaseTypography,
        content = content,
    )
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 7: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add design system colour, type and theme

ThemeMode.isDark is pure so theme resolution is testable without Compose.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: MVI base and injected Clock

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.kt`
- Create: `showcase/src/androidMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.android.kt`
- Create: `showcase/src/iosMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.ios.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/core/mvi/MviViewModel.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/core/mvi/MviViewModelTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `interface Clock { fun nowMillis(): Long }` and `expect object SystemClock : Clock`. The Phase 1b plan's tests define their own `FixedClock`; there is no shared test clock.
  - `abstract class MviViewModel<S : Any, I : Any, E : Any>(initialState: S) : ViewModel()` exposing `val state: StateFlow<S>`, `val effects: Flow<E>`, `protected fun updateState(block: S.() -> S)`, `protected fun emitEffect(effect: E)`, `abstract fun onIntent(intent: I)`.
  - Every feature ViewModel in Phases 2–6 extends this.

- [ ] **Step 1: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/core/mvi/MviViewModelTest.kt`:

```kotlin
package dev.avinya.admob.showcase.core.mvi

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CounterState(val count: Int = 0)

private sealed interface CounterIntent {
    data object Increment : CounterIntent
    data object Announce : CounterIntent
}

private sealed interface CounterEffect {
    data class Announced(val count: Int) : CounterEffect
}

private class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>(CounterState()) {
    override fun onIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> updateState { copy(count = count + 1) }
            CounterIntent.Announce -> emitEffect(CounterEffect.Announced(state.value.count))
        }
    }
}

class MviViewModelTest {

    @Test
    fun startsAtTheInitialState() {
        assertEquals(CounterState(count = 0), CounterViewModel().state.value)
    }

    @Test
    fun intentsReduceIntoState() {
        val vm = CounterViewModel()

        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Increment)

        assertEquals(CounterState(count = 2), vm.state.value)
    }

    @Test
    fun effectsAreBufferedUntilCollected() = runTest {
        val vm = CounterViewModel()

        // Emitted with no collector attached — a buffered Channel must retain them.
        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Announce)
        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Announce)

        val received = mutableListOf<CounterEffect>()
        val job = launch { vm.effects.collect { received += it } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(
            listOf(CounterEffect.Announced(1), CounterEffect.Announced(2)),
            received,
        )
    }
}
```

This uses plain `kotlinx-coroutines-test` only. Turbine is **not** an approved dependency — do not reach for it even though it would read more nicely here.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: MviViewModel`.

- [ ] **Step 3: Write the Clock**

An `expect`/`actual` rather than `kotlin.time.Clock`, which is still experimental and would need an opt-in. Three tiny files, zero version risk, no dependency.

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.kt`:

```kotlin
package dev.avinya.admob.showcase.core.time

/**
 * Injected time source.
 *
 * Exists so time-dependent rules — notably `AdPolicy`'s frequency caps and
 * cooldowns in Phase 4 — are testable without kotlinx-datetime, which is not
 * an approved dependency.
 */
interface Clock {
    fun nowMillis(): Long
}

/** Wall-clock time since the Unix epoch. The production binding. */
expect object SystemClock : Clock {
    override fun nowMillis(): Long
}
```

Create `showcase/src/androidMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.android.kt`:

```kotlin
package dev.avinya.admob.showcase.core.time

actual object SystemClock : Clock {
    actual override fun nowMillis(): Long = System.currentTimeMillis()
}
```

Create `showcase/src/iosMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.ios.kt`:

```kotlin
package dev.avinya.admob.showcase.core.time

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual object SystemClock : Clock {
    actual override fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
}
```

- [ ] **Step 4: Write the MVI base**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/core/mvi/MviViewModel.kt`:

```kotlin
package dev.avinya.admob.showcase.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Minimal MVI base: one immutable [state], a stream of one-shot [effects],
 * and a single [onIntent] entry point.
 *
 * Effects use a buffered [Channel] rather than a `SharedFlow` so a one-shot
 * — navigating, showing an ad — is delivered exactly once and cannot replay
 * when the UI re-subscribes after a configuration change.
 *
 * @param S immutable screen state
 * @param I user or system intents
 * @param E one-shot side effects
 */
abstract class MviViewModel<S : Any, I : Any, E : Any>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)

    /** The current screen state. Always non-null, always the latest value. */
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)

    /** One-shot effects. Each is delivered to exactly one collector, exactly once. */
    val effects: Flow<E> = _effects.receiveAsFlow()

    /** Reduce the current state. Safe to call from any thread. */
    protected fun updateState(block: S.() -> S) {
        _state.update(block)
    }

    /** Queue a one-shot effect. Buffered, so it survives having no collector yet. */
    protected fun emitEffect(effect: E) {
        _effects.trySend(effect)
    }

    /** Single entry point for everything the UI or system asks of this screen. */
    abstract fun onIntent(intent: I)
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS** on both.

- [ ] **Step 6: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add MVI base and injected Clock

Effects use a buffered Channel, not SharedFlow, so one-shots deliver once
and cannot replay after a configuration change.

Clock is a two-line interface rather than kotlinx-datetime, which is not
an approved dependency.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

---

## Exit criteria

- [ ] `androidApp` and `iosApp` launch showing the themed placeholder
- [ ] `desktopApp` and `webApp` still compile and still show the unsupported-platform screen
- [ ] `ThemeModeTest`, `MviViewModelTest` and `ShowcaseAdConfigTest` pass on the Android host and iOS
- [ ] `shared` no longer has an `adCapableTest` source set
- [ ] `git diff --stat master -- 'admob-cmp*'` is empty

---

## Next plan

**Phase 1b — Persistence** (`2026-08-06-showcase-phase-1b-persistence.md`): the nine-table Room schema, DAOs, deterministic seed, repositories, DataStore settings and the `AppGraph`.
