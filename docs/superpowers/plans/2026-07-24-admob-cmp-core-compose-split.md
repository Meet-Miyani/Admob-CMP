# admob-cmp → `-core` / `-compose` Modularization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the single `:admob-cmp` Compose-Multiplatform library into a Compose-free `:admob-cmp-core` (logic: AdManager, consent, full-screen orchestration, banner/native pools, retry/timeout/telemetry, iOS bindings) plus a `:admob-cmp-compose` (all `@Composable` UI, the native-ad layout DSL, the debug console), keeping `:admob-cmp` as a thin umbrella so existing consumers are unaffected.

**Architecture:** Three published artifacts under `dev.avinya.ads`. `-compose` depends on `-core` via `api(...)`. The umbrella `admob-cmp` does `api(-core)` + `api(-compose)`. **Kotlin package names stay `avinya.tech.yt.ads.*` in both modules** — the split is by Gradle module, not by package — so any consumer importing through the umbrella sees an unchanged API. The Compose-free core is the internal architectural boundary; a public non-Compose surface is a later, demand-gated decision (not a launch commitment).

**Tech Stack:** Kotlin 2.3.20 (ceiling — do not bump), AGP 9.2.1, Gradle 9.4.1, Compose Multiplatform 1.11.1, KMP targets `android` + `iosArm64` + `iosSimulatorArm64`, vanniktech maven-publish 0.30.0, cinterop bindings against downloaded GoogleMobileAds/UMP XCFrameworks, `explicitApi()` + KGP `abiValidation` per module.

## Global Constraints

- **Kotlin ceiling 2.3.20** (KSP 2.3.9). Do NOT bump. The whole repo build shares one Kotlin plugin version.
- **Packages stay `avinya.tech.yt.ads.*`** in every new module. No package renames (ABI is frozen; rename is a separate breaking migration).
- **Bindings-only iOS** — never add `staticLibraries` to the `.def` files. GMA/UMP symbols come from the consumer's Xcode SPM link.
- **`explicitApi()` stays on** in `-core` and `-compose`; after any public API change run `:<module>:updateKotlinAbi` and commit `api/<module>.klib.api`, or the build fails. The umbrella carries **no** ABI validation (it is pure aggregation).
- **Config-cache stays OFF** (`org.gradle.configuration-cache=false`) — cinterop/download/publish tasks call `providers.exec` at configuration time.
- **iOS interop header checksums are pinned** (`gmaIosHeadersSha256`, `umpIosHeadersSha256` in root `gradle.properties`); the download fails closed on mismatch. Do not invent hashes.
- **All GMA/UMP calls on `Dispatchers.Main.immediate`**; iOS ObjC delegates are weak (keep strong Kotlin refs); K/N 2.3.20 miscompiles `is <data object>` — use `==`. (These invariants live inside the moved source; moving files must not alter them.)
- **Maven coordinates:** `dev.avinya.ads:admob-cmp-core`, `dev.avinya.ads:admob-cmp-compose`, `dev.avinya.ads:admob-cmp` (umbrella). Bump `VERSION_NAME` to `0.2.0` for the modular release.
- **Every task must leave the whole repo build green** (all targets compile + existing tests pass + each module's ABI clean). The existing 30 test files are the refactor's safety net — no test is deleted, only relocated.

---

## Target module structure

```
admob-cmp-core/          # NEW — Compose-free. Owns cinterop + iOS framework download.
  build.gradle.kts        # KMP lib, GMA/UMP deps, cinterop, explicitApi, abiValidation. NO Compose UI deps.
  gradle.properties       # POM_ARTIFACT_ID=admob-cmp-core
  api/admob-cmp-core.klib.api
  src/{commonMain,androidMain,iosMain,commonTest,androidHostTest,iosTest}
  src/nativeInterop/cinterop/{GoogleMobileAds,UserMessagingPlatform}.def   # moved here
admob-cmp-compose/       # NEW — Compose UI. api(project(":admob-cmp-core")).
  build.gradle.kts        # KMP lib, Compose MP deps, explicitApi, abiValidation.
  gradle.properties       # POM_ARTIFACT_ID=admob-cmp-compose
  api/admob-cmp-compose.klib.api
  src/{commonMain,androidMain,iosMain,commonTest}
admob-cmp/               # umbrella — api(core) + api(compose), no source, no ABI validation.
  build.gradle.kts
  gradle.properties       # POM_ARTIFACT_ID=admob-cmp
```

## Complete file → module mapping

Derived from a coupling scan (grep for `@Composable`, `import androidx.compose`, `@Immutable`/`@Stable`, and cross-package imports). `[strip]` = remove `@Immutable`/`@Stable` + their `androidx.compose.runtime` imports.

### → `admob-cmp-core` — commonMain
`AdConfig.kt`[strip] · `AdError.kt`[strip] · `AdLogger.kt` · `AdManager.kt`[strip] · `AdPlacement.kt`[strip] · `AdPlatform.kt` · `AdRetry.kt` · `AdShowResult.kt`[strip] · `AdState.kt`[strip] · `AdTelemetry.kt`[strip] · `AdTimeoutPolicy.kt`[strip] · `AdTrackingAuthorization.kt` · `BannerGeometry.kt`[strip] · `FullScreenAdModels.kt`[strip] · `TestAdIds.kt` · `appopen/AppOpenAdCoordinator.kt` · `appopen/ForegroundSignal.kt` · `internal/AdEventEmission.kt` · `internal/AdRequestAdmission.kt` · `internal/BannerCore.kt` · `internal/FullScreenPresentationArbiter.kt` · `internal/FullScreenSlotCore.kt` · `internal/FullScreenStateLock.kt` · `internal/NativePoolCore.kt` · `nativead/NativeAdModels.kt`[strip] · `nativead/NativeAdToken.kt`[strip]

### → `admob-cmp-core` — androidMain
`AdMob.kt` · `AndroidAdMappers.kt` · `AndroidAdPlatformLogger.kt` · `AndroidBannerAdController.kt` · `AndroidFullScreenSlots.kt` · `AndroidGoogleAdManager.kt` **(minus `rememberAdManager()` actual → compose)** · `AndroidTrackingAuthorization.kt` · `appopen/ForegroundSignal.android.kt` · `internal/FullScreenStateLock.android.kt` · `nativead/AndroidNativeAdPool.kt` · **NEW `AndroidBannerSizing.kt`** (holds `screenWidthDp` + `toAndroidAdSize`, extracted from `ui/AndroidBannerAdView.kt`)

### → `admob-cmp-core` — iosMain
`IosAdDiagnostics.kt` · `IosAdMappers.kt` · `IosAdPlatformLogger.kt` · `IosBannerAdController.kt` · `IosConsentController.kt` · `IosFullScreenSlots.kt` · `IosGoogleAdManager.kt` **(minus `rememberAdManager()` actual → compose; add public `IosAdMob` accessor — see Hazard H2)** · `IosTrackingAuthorization.kt` · `RootViewController.kt` · `appopen/ForegroundSignal.ios.kt` · `internal/FullScreenStateLock.ios.kt` · `nativead/IosNativeAdPool.kt`

### → `admob-cmp-core` — nativeInterop
`cinterop/GoogleMobileAds.def` · `cinterop/UserMessagingPlatform.def` (+ the framework-download + cinterop wiring + `admobCmpTestLinkerOpts` extension from the current `build.gradle.kts`)

### → `admob-cmp-compose` — commonMain
`AdComposition.kt` (CompositionLocals, `AdPlacements`, `rememberAdManager()` **expect**) · `ui/BannerAdView.kt` · `ui/BannerVisibility.kt` · `ui/NativeAdView.kt` · `nativead/layout/` **(all 7: `AdLayout.kt`, `AdLayoutDsl.kt`, `AdLayoutPreview.kt`, `AdLayoutValidator.kt`, `AdModifier.kt`, `AdStyle.kt`, `AdTemplates.kt`)** · `debug/` **(all 12: `AdDebugCatalog.kt`, `AdDebugRecorder.kt`, `AdDebugScreen.kt`, `EventFilter.kt`, `LayoutSource.kt`, `RecordedAdEvent.kt`, `console/EventConsole.kt`, `console/EventRow.kt`, `tabs/DiagnosticsTab.kt`, `tabs/FormatsTab.kt`, `tabs/LayoutsTab.kt`, `ui/DebugPrimitives.kt`, `ui/DebugTokens.kt`, `ui/ResponseInfoView.kt`)**

### → `admob-cmp-compose` — androidMain
`ui/AndroidBannerAdView.kt` (minus the two extracted helpers) · `ui/AndroidNativeAdView.kt` · `nativead/rendering/AndroidNativeAdLayoutRenderer.kt` · `nativead/rendering/AndroidNativeAdMeasurement.kt` · `nativead/rendering/AndroidNativeAdStyleMapper.kt` · **NEW `AndroidRememberAdManager.kt`** (the `rememberAdManager()` actual, calls core `AdMob.manager(context)`)

### → `admob-cmp-compose` — iosMain
`ui/IosBannerAdView.kt` · `ui/IosNativeAdView.kt` · `nativead/rendering/IosNativeAdRenderer.kt` · **NEW `IosRememberAdManager.kt`** (the `rememberAdManager()` actual, calls core `IosAdMob.manager`)

### Test relocation
- **→ `-core` commonTest:** `AdConfigTest`, `AdEventModelsTest`, `AdPlacementTest`, `AdRequestAdmissionTest`, `AdRetryTest`, `AdRewardTest`, `AdTimeoutTest`, `AppOpenAdCoordinatorTest`, `BannerCoreTest`, `ConcurrentShowProbeTest`, `FactoryFormatGuardTest`, `Fakes.kt`, `FullScreenPresentationArbiterTest`, `FullScreenSlotCoreTest`, `NativePoolCoreTest`, `NoOpAdManagerTest`, `TestAdSafetyTest`, `TwoSlotArbitrationTest`
- **→ `-core` androidHostTest:** `AndroidAdMappersTest`, `AndroidBannerControllerCharacterizationTest`, `AndroidNativePoolCharacterizationTest`, `ForegroundStackTest`, `NativeAdBatchHandoffTest`
- **→ `-core` iosTest:** `IosAdMappersTest`, `IosBannerControllerCharacterizationTest`, `nativead/IosNativePoolCharacterizationTest`
- **→ `-compose` commonTest:** `AdLayoutValidatorTest`, `BannerVisibilityTest`, `debug/AdDebugCatalogTest`, `debug/AdDebugRecorderTest`, `debug/EventFilterTest`, `debug/RecordedAdEventTest` (`Fakes.kt` is core-side; if a compose test needs a fake, add a minimal local fake — do not duplicate the whole file).

## Known hazards & how this plan handles them

- **H1 — `@Immutable`/`@Stable` require the Compose runtime.** Stripping them from core models is safe: with Kotlin 2.x + Compose 1.11 strong-skipping, `data class`/`val`-only types are inferred stable, so Compose consumers keep recomposition performance without the annotations. Each `[strip]` file: delete `import androidx.compose.runtime.Immutable`/`Stable` and the annotation usages only.
- **H2 — `internal`/`private` becomes inaccessible across the module split.** `NoOpAdManager` (`public object`) and `AdMob.manager()` (`public`) are already reachable. **`IosAdManagerHolder` is `private`** — the iOS `rememberAdManager()` actual (moving to compose) references `IosAdManagerHolder.instance`. Add a public accessor in core (Task 6): `public object IosAdMob { public val manager: AdManager get() = IosAdManagerHolder.instance }`. After each move, a compile failure naming an `internal`/`private` symbol = promote that symbol to `public` in core (and `updateKotlinAbi`).
- **H3 — cinterop klib transitivity to compose iOS UI.** `ui/IosBannerAdView.kt` / `ui/IosNativeAdView.kt` (compose) may reference cinterop types (e.g. `GADBannerView`) directly. cinterop lives in `-core`. Task 6 declares the cinterops with `api`-scoped exposure; Task 7 Step 2 is a **spike** that compiles compose iOS and, only if cinterop symbols are unresolved, re-declares the two `cinterop { }` blocks in `-compose`'s `iosMain` pointing at the *same* `.def` files and the core-downloaded frameworks (shared download, no second fetch). Decision is evidence-driven, with both branches specified in-task.
- **H4 — frozen ABI split.** The single `api/admob-cmp.klib.api` retires. `-core` and `-compose` each get a fresh dump generated by `updateKotlinAbi` at the end of their move tasks. The umbrella disables `abiValidation` (aggregation only). Consumer-facing API is preserved by identical packages + umbrella `api(...)`.
- **H5 — split packages during transition.** While files are mid-move, `avinya.tech.yt.ads.*` exists in two modules at once. This compiles on Android/K/N, but `internal` visibility does not cross the boundary (see H2). Minimize the window: move a whole coherent area per task, verify, commit.

---

## Task 0: Establish and record the green baseline

**Files:** none (verification only).

- [ ] **Step 1: Compile every target of the current single module**

Run:
```bash
./gradlew :admob-cmp:compileCommonMainKotlinMetadata :admob-cmp:compileAndroidMain \
  :admob-cmp:compileKotlinIosSimulatorArm64 --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run the full existing test + ABI suite**

Run:
```bash
./gradlew :admob-cmp:testAndroidHostTest :admob-cmp:iosSimulatorArm64Test :admob-cmp:checkKotlinAbi --console=plain
```
Expected: `BUILD SUCCESSFUL`, 0 failures. **Record the printed test counts** (Android + iOS) — every later task must keep these totals (minus nothing; tests only move between modules).

- [ ] **Step 3: Commit a checkpoint tag**

```bash
git add -A && git commit -m "chore: green baseline before core/compose split"
```

---

## Task 1: Scaffold empty `:admob-cmp-core`

**Files:**
- Create: `admob-cmp-core/build.gradle.kts`
- Create: `admob-cmp-core/gradle.properties`
- Create: `admob-cmp-core/src/commonMain/kotlin/avinya/tech/yt/ads/CoreModulePlaceholder.kt`
- Modify: `settings.gradle.kts` (add `include(":admob-cmp-core")`)

**Interfaces:**
- Produces: a configurable, Compose-free KMP library module with `android` + `iosArm64` + `iosSimulatorArm64` targets, GMA-Next-Gen + UMP Android deps, `explicitApi()`, `abiValidation`. No cinterop yet (added in Task 6 with the iOS code). No Compose deps.

- [ ] **Step 1: Add the module to settings**

In `settings.gradle.kts`, add after `include(":admob-cmp")`:
```kotlin
include(":admob-cmp-core")
```

- [ ] **Step 2: Write `admob-cmp-core/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { enabled.set(true) }

    android {
        namespace = "avinya.tech.yt.ads.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest { isReturnDefaultValues = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.google.ads.mobile.sdk)
            implementation(libs.google.user.messaging.platform)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.google.ads.mobile.sdk)
            implementation("org.mockito:mockito-core:5.15.2")
        }
    }
}
```
> `android.namespace` uses `.core`/`.compose` suffixes to keep the two modules' Android R classes distinct; Kotlin package names of the *source* stay `avinya.tech.yt.ads.*`.
> `androidx.activity.compose` is retained because core's Android entry points touch `activity`/lifecycle, not Compose UI. If Task 5's compile shows it is unused after the move, drop it then.

- [ ] **Step 3: Write `admob-cmp-core/gradle.properties`**

```properties
POM_ARTIFACT_ID=admob-cmp-core
POM_NAME=AdMob CMP — Core
POM_DESCRIPTION=Compose-free Kotlin Multiplatform core for the admob-cmp AdMob SDK (AdManager, consent, full-screen orchestration, banner/native pools, iOS bindings).
```
> `GROUP`, `VERSION_NAME`, license, URL, developer, SCM, `SONATYPE_HOST` are inherited from the root `gradle.properties` set in Task 9 — do not repeat them per module.

- [ ] **Step 4: Write the placeholder so the module has source**

`admob-cmp-core/src/commonMain/kotlin/avinya/tech/yt/ads/CoreModulePlaceholder.kt`:
```kotlin
package avinya.tech.yt.ads

internal const val CORE_MODULE_PLACEHOLDER: String = "admob-cmp-core"
```

- [ ] **Step 5: Verify configuration + empty compile**

Run:
```bash
./gradlew :admob-cmp-core:compileCommonMainKotlinMetadata :admob-cmp-core:compileAndroidMain \
  :admob-cmp-core:compileKotlinIosSimulatorArm64 --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts admob-cmp-core && git commit -m "feat: scaffold empty :admob-cmp-core module"
```

---

## Task 2: Scaffold empty `:admob-cmp-compose`

**Files:**
- Create: `admob-cmp-compose/build.gradle.kts`
- Create: `admob-cmp-compose/gradle.properties`
- Create: `admob-cmp-compose/src/commonMain/kotlin/avinya/tech/yt/ads/ComposeModulePlaceholder.kt`
- Modify: `settings.gradle.kts` (add `include(":admob-cmp-compose")`)

**Interfaces:**
- Consumes: `project(":admob-cmp-core")` via `api(...)`.
- Produces: a configurable Compose-Multiplatform KMP library with the same targets, `explicitApi()`, `abiValidation`.

- [ ] **Step 1: Add to settings**

In `settings.gradle.kts` add:
```kotlin
include(":admob-cmp-compose")
```

- [ ] **Step 2: Write `admob-cmp-compose/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { enabled.set(true) }

    android {
        namespace = "avinya.tech.yt.ads.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest { isReturnDefaultValues = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":admob-cmp-core"))
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.ui)
            implementation(libs.ui.tooling.preview)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.ui.tooling)
            implementation(libs.androidx.uiTooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.google.ads.mobile.sdk)
            implementation(libs.google.user.messaging.platform)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

- [ ] **Step 3: Write `admob-cmp-compose/gradle.properties`**

```properties
POM_ARTIFACT_ID=admob-cmp-compose
POM_NAME=AdMob CMP — Compose
POM_DESCRIPTION=Compose Multiplatform UI for the admob-cmp AdMob SDK (BannerAdView, NativeAdView, native-ad layout DSL, debug console, rememberAdManager).
```

- [ ] **Step 4: Placeholder source**

`admob-cmp-compose/src/commonMain/kotlin/avinya/tech/yt/ads/ComposeModulePlaceholder.kt`:
```kotlin
package avinya.tech.yt.ads

internal const val COMPOSE_MODULE_PLACEHOLDER: String = "admob-cmp-compose"
```

- [ ] **Step 5: Verify configuration + empty compile**

Run:
```bash
./gradlew :admob-cmp-compose:compileCommonMainKotlinMetadata :admob-cmp-compose:compileAndroidMain \
  :admob-cmp-compose:compileKotlinIosSimulatorArm64 --console=plain
```
Expected: `BUILD SUCCESSFUL` (compose sees core's empty API).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts admob-cmp-compose && git commit -m "feat: scaffold empty :admob-cmp-compose module"
```

---

## Task 3: Move Compose-free common models into core

**Files:**
- Move (git mv): the 25 commonMain core files listed in the mapping, from `admob-cmp/src/commonMain/...` to `admob-cmp-core/src/commonMain/...` (identical package path).
- Modify each `[strip]` file: remove `@Immutable`/`@Stable` + their imports.
- Modify: `admob-cmp/build.gradle.kts` — add `implementation(project(":admob-cmp-core"))` to `commonMain.dependencies` so the still-local Compose files resolve the moved types.
- Delete: `admob-cmp-core/src/commonMain/kotlin/avinya/tech/yt/ads/CoreModulePlaceholder.kt`.

**Interfaces:**
- Produces (from core, unchanged signatures): `AdManager`, `NoOpAdManager`, all `*AdController` interfaces, `AdPlacement`, `AdError`, `AdState`, `AdShowResult`, `BannerGeometry`, `NativeAdModels`, `NativeAdToken`, retry/timeout/telemetry types, `internal/*` cores.

- [ ] **Step 1: Move the files**

Run (one `git mv` per file; example for the batch — repeat for every file in "→ core commonMain"):
```bash
cd admob-cmp
for f in AdConfig AdError AdLogger AdManager AdPlacement AdPlatform AdRetry AdShowResult \
         AdState AdTelemetry AdTimeoutPolicy AdTrackingAuthorization BannerGeometry \
         FullScreenAdModels TestAdIds; do
  git mv "src/commonMain/kotlin/avinya/tech/yt/ads/$f.kt" \
         "../admob-cmp-core/src/commonMain/kotlin/avinya/tech/yt/ads/$f.kt"
done
git mv src/commonMain/kotlin/avinya/tech/yt/ads/appopen ../admob-cmp-core/src/commonMain/kotlin/avinya/tech/yt/ads/appopen
git mv src/commonMain/kotlin/avinya/tech/yt/ads/internal ../admob-cmp-core/src/commonMain/kotlin/avinya/tech/yt/ads/internal
git mv src/commonMain/kotlin/avinya/tech/yt/ads/nativead/NativeAdModels.kt ../admob-cmp-core/src/commonMain/kotlin/avinya/tech/yt/ads/nativead/NativeAdModels.kt
git mv src/commonMain/kotlin/avinya/tech/yt/ads/nativead/NativeAdToken.kt ../admob-cmp-core/src/commonMain/kotlin/avinya/tech/yt/ads/nativead/NativeAdToken.kt
rm ../admob-cmp-core/src/commonMain/kotlin/avinya/tech/yt/ads/CoreModulePlaceholder.kt
cd ..
```

- [ ] **Step 2: Strip Compose annotations from the `[strip]` files**

In each of `AdConfig.kt`, `AdError.kt`, `AdManager.kt`, `AdPlacement.kt`, `AdShowResult.kt`, `AdState.kt`, `AdTelemetry.kt`, `AdTimeoutPolicy.kt`, `BannerGeometry.kt`, `FullScreenAdModels.kt`, `nativead/NativeAdModels.kt`, `nativead/NativeAdToken.kt` (now under `admob-cmp-core`): delete every line `import androidx.compose.runtime.Immutable`, `import androidx.compose.runtime.Stable`, and each bare `@Immutable` / `@Stable` annotation line. Change nothing else.

- [ ] **Step 3: Point the umbrella's leftover Compose sources at core**

In `admob-cmp/build.gradle.kts`, inside `commonMain.dependencies { ... }`, add:
```kotlin
implementation(project(":admob-cmp-core"))
```

- [ ] **Step 4: Compile core + the still-mixed umbrella**

Run:
```bash
./gradlew :admob-cmp-core:compileCommonMainKotlinMetadata :admob-cmp:compileCommonMainKotlinMetadata --console=plain
```
Expected: `BUILD SUCCESSFUL`. If it fails with an unresolved `internal` symbol, apply Hazard H2 (promote to `public` in core, re-run).

- [ ] **Step 5: Move the matching commonTest files and run core's common tests**

Move the "→ core commonTest" files (incl. `Fakes.kt`) via `git mv` into `admob-cmp-core/src/commonTest/kotlin/avinya/tech/yt/ads/`. Then run:
```bash
./gradlew :admob-cmp-core:iosSimulatorArm64Test --console=plain
```
Expected: `BUILD SUCCESSFUL`; the moved common tests run under core. (`androidHostTest`/`iosTest` platform tests move with their platform code in Tasks 5–6.)

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor: move Compose-free common models/logic into :admob-cmp-core"
```

---

## Task 4: Move Android platform logic into core

**Files:**
- Move: `AdMob.kt`, `AndroidAdMappers.kt`, `AndroidAdPlatformLogger.kt`, `AndroidBannerAdController.kt`, `AndroidFullScreenSlots.kt`, `AndroidTrackingAuthorization.kt`, `appopen/ForegroundSignal.android.kt`, `internal/FullScreenStateLock.android.kt`, `nativead/AndroidNativeAdPool.kt` → `admob-cmp-core/src/androidMain/...`.
- Split: `AndroidGoogleAdManager.kt` — move the non-`@Composable` body to core; **leave the `@Composable rememberAdManager()` actual behind** (it moves to compose in Task 7). Do this by moving the whole file to core, then cutting the `rememberAdManager()` function into a temporary `admob-cmp/src/androidMain/.../AndroidRememberAdManager.kt` staged for Task 7.
- Create: `admob-cmp-core/src/androidMain/kotlin/avinya/tech/yt/ads/AndroidBannerSizing.kt` (holds `screenWidthDp` + `toAndroidAdSize`, cut from `ui/AndroidBannerAdView.kt`).
- Move: the "→ core androidHostTest" test files.

**Interfaces:**
- Produces: `AdMob.manager(context): AdManager` (already `public`); `screenWidthDp`, `toAndroidAdSize` now in package `avinya.tech.yt.ads` (core), consumed by both core's `AndroidBannerAdController` and compose's `AndroidBannerAdView`.

- [ ] **Step 1: Extract the two banner helpers into core**

Open `admob-cmp/src/androidMain/kotlin/avinya/tech/yt/ads/ui/AndroidBannerAdView.kt`, cut the `screenWidthDp` and `toAndroidAdSize` declarations (and only the imports they need), and paste them into a new `admob-cmp-core/src/androidMain/kotlin/avinya/tech/yt/ads/AndroidBannerSizing.kt` with `package avinya.tech.yt.ads`. Keep them `internal` for now; if Task 7's compose compile can't see them, promote to `public` (H2).

- [ ] **Step 2: Move the Android core files**

```bash
cd admob-cmp
for f in AdMob AndroidAdMappers AndroidAdPlatformLogger AndroidBannerAdController \
         AndroidFullScreenSlots AndroidGoogleAdManager AndroidTrackingAuthorization; do
  git mv "src/androidMain/kotlin/avinya/tech/yt/ads/$f.kt" \
         "../admob-cmp-core/src/androidMain/kotlin/avinya/tech/yt/ads/$f.kt"
done
git mv src/androidMain/kotlin/avinya/tech/yt/ads/appopen/ForegroundSignal.android.kt ../admob-cmp-core/src/androidMain/kotlin/avinya/tech/yt/ads/appopen/ForegroundSignal.android.kt
git mv src/androidMain/kotlin/avinya/tech/yt/ads/internal/FullScreenStateLock.android.kt ../admob-cmp-core/src/androidMain/kotlin/avinya/tech/yt/ads/internal/FullScreenStateLock.android.kt
git mv src/androidMain/kotlin/avinya/tech/yt/ads/nativead/AndroidNativeAdPool.kt ../admob-cmp-core/src/androidMain/kotlin/avinya/tech/yt/ads/nativead/AndroidNativeAdPool.kt
cd ..
```

- [ ] **Step 3: Stage the `rememberAdManager()` Android actual for Task 7**

Cut the `@Composable public actual fun rememberAdManager(): AdManager { ... }` block (and its Compose imports `Composable`, `remember`, `LocalContext`) out of the now-core `admob-cmp-core/src/androidMain/.../AndroidGoogleAdManager.kt` into `admob-cmp/src/androidMain/kotlin/avinya/tech/yt/ads/AndroidRememberAdManager.kt` (package `avinya.tech.yt.ads`, `implementation(project(":admob-cmp-core"))` already gives it `AdMob`). This keeps core Compose-free while the umbrella still supplies the actual until Task 7.

- [ ] **Step 4: Add the core dependency to the umbrella's androidMain (if not already global)**

The `implementation(project(":admob-cmp-core"))` added in Task 3 is in `commonMain`, so androidMain inherits it. No change unless compile says otherwise.

- [ ] **Step 5: Move Android host tests and compile + test**

`git mv` the "→ core androidHostTest" files into `admob-cmp-core/src/androidHostTest/kotlin/avinya/tech/yt/ads/`. Then:
```bash
./gradlew :admob-cmp-core:compileAndroidMain :admob-cmp-core:testAndroidHostTest \
  :admob-cmp:compileAndroidMain --console=plain
```
Expected: `BUILD SUCCESSFUL`; Android host-test count matches the baseline's Android portion for these files.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor: move Android ad logic into :admob-cmp-core; extract banner sizing helpers"
```

---

## Task 5: Move iOS platform logic + cinterop into core

**Files:**
- Move: `IosAdDiagnostics.kt`, `IosAdMappers.kt`, `IosAdPlatformLogger.kt`, `IosBannerAdController.kt`, `IosConsentController.kt`, `IosFullScreenSlots.kt`, `IosGoogleAdManager.kt` (minus `rememberAdManager()` actual), `IosTrackingAuthorization.kt`, `RootViewController.kt`, `appopen/ForegroundSignal.ios.kt`, `internal/FullScreenStateLock.ios.kt`, `nativead/IosNativeAdPool.kt` → `admob-cmp-core/src/iosMain/...`.
- Move: `src/nativeInterop/cinterop/{GoogleMobileAds,UserMessagingPlatform}.def` → `admob-cmp-core/src/nativeInterop/cinterop/`.
- Modify: move the iOS framework-download tasks, cinterop wiring, `admobCmpTestLinkerOpts` extension, and iOS `binaries.framework`/test-linker config out of `admob-cmp/build.gradle.kts` into `admob-cmp-core/build.gradle.kts`.
- Add: public `IosAdMob` accessor (H2). Stage the iOS `rememberAdManager()` actual for Task 7.
- Move: the "→ core iosTest" files.

**Interfaces:**
- Produces: `IosAdMob.manager: AdManager` (public accessor over the previously-private `IosAdManagerHolder`); the cinterop-bound `GoogleMobileAds`/`UserMessagingPlatform` klibs; `AdMobCmp` static iOS framework.

- [ ] **Step 1: Move the cinterop defs + iOS build logic into core**

Move the two `.def` files (git mv). Then cut from `admob-cmp/build.gradle.kts` into `admob-cmp-core/build.gradle.kts`: the `DownloadIosFramework` task class, `downloadGmaIos`/`downloadUmpIos` registrations, `GMA_DOWNLOAD_BASE`, `frameworkDir(...)`, `admobTestLinkerOpts(...)`, the `extensions.extraProperties["admobCmpTestLinkerOpts"]` line, and the `iosArm64()/iosSimulatorArm64()` block that sets `binaries.framework { baseName = "AdMobCmp"; isStatic = true; ... }`, the `cinterops { gma; ump }`, the download→cinterop `dependsOn` wiring, and the `TestExecutable` linkerOpts. Also move the `gmaIosVersion`/`gmaUmpIosVersion`/`iosFrameworksDir` vals and the `doctorIos` task. Replace the plain `iosArm64()`/`iosSimulatorArm64()` added in Task 1 with this full block.

- [ ] **Step 2: Add the public iOS accessor (H2)**

In `admob-cmp-core/src/iosMain/.../IosGoogleAdManager.kt`, keep `private object IosAdManagerHolder` and add:
```kotlin
/** Public entry point for the process-wide iOS [AdManager] singleton. */
public object IosAdMob {
    public val manager: AdManager get() = IosAdManagerHolder.instance
}
```

- [ ] **Step 3: Stage the iOS `rememberAdManager()` actual for Task 7**

Cut the `@Composable public actual fun rememberAdManager(): AdManager = remember { IosAdManagerHolder.instance }` out of the core `IosGoogleAdManager.kt` into `admob-cmp/src/iosMain/kotlin/avinya/tech/yt/ads/IosRememberAdManager.kt`, rewritten to use the public accessor: `... = remember { IosAdMob.manager }`.

- [ ] **Step 4: Move the iOS source files + iOS tests**

`git mv` the "→ core iosMain" files and the "→ core iosTest" files into the corresponding `admob-cmp-core/src/...` paths.

- [ ] **Step 5: Compile iOS + run iOS tests (downloads frameworks once)**

Run:
```bash
./gradlew :admob-cmp-core:compileKotlinIosSimulatorArm64 :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp:compileKotlinIosSimulatorArm64 --console=plain
```
Expected: `BUILD SUCCESSFUL`; iOS test count matches the baseline iOS portion for these files. If unresolved-`internal` errors appear, apply H2.

- [ ] **Step 6: Update core's ABI dump (core surface is now complete)**

Run:
```bash
./gradlew :admob-cmp-core:updateKotlinAbi --console=plain
git add admob-cmp-core/api/admob-cmp-core.klib.api
```

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor: move iOS ad logic + cinterop into :admob-cmp-core; add public IosAdMob accessor"
```

---

## Task 6: Move all Compose UI into `:admob-cmp-compose`

**Files:**
- Move commonMain: `AdComposition.kt`, `ui/BannerAdView.kt`, `ui/BannerVisibility.kt`, `ui/NativeAdView.kt`, all `nativead/layout/*`, all `debug/*` → `admob-cmp-compose/src/commonMain/...`.
- Move androidMain: `ui/AndroidBannerAdView.kt` (now minus the extracted helpers), `ui/AndroidNativeAdView.kt`, `nativead/rendering/Android*` → `admob-cmp-compose/src/androidMain/...`; move the staged `admob-cmp/src/androidMain/.../AndroidRememberAdManager.kt` → `admob-cmp-compose/src/androidMain/...`.
- Move iosMain: `ui/IosBannerAdView.kt`, `ui/IosNativeAdView.kt`, `nativead/rendering/IosNativeAdRenderer.kt` → `admob-cmp-compose/src/iosMain/...`; move staged `IosRememberAdManager.kt` → `admob-cmp-compose/src/iosMain/...`.
- Move commonTest: the "→ compose commonTest" files.
- Delete: `admob-cmp-compose/.../ComposeModulePlaceholder.kt`.

**Interfaces:**
- Consumes from core: `AdManager`, `NoOpAdManager`, `AdPlacement`, `NativeAd*`, `BannerGeometry`, `AdMob.manager`, `IosAdMob.manager`, `screenWidthDp`, `toAndroidAdSize`, cinterop types (see H3).
- Produces: `rememberAdManager()` (expect + both actuals), `LocalAdManager`, `LocalAdPlacements`, `AdPlacements`, `BannerAdView`, `NativeAdView`, the layout DSL, the debug console.

- [ ] **Step 1: Move the compose commonMain files**

```bash
cd admob-cmp
git mv src/commonMain/kotlin/avinya/tech/yt/ads/AdComposition.kt ../admob-cmp-compose/src/commonMain/kotlin/avinya/tech/yt/ads/AdComposition.kt
git mv src/commonMain/kotlin/avinya/tech/yt/ads/ui ../admob-cmp-compose/src/commonMain/kotlin/avinya/tech/yt/ads/ui
git mv src/commonMain/kotlin/avinya/tech/yt/ads/nativead/layout ../admob-cmp-compose/src/commonMain/kotlin/avinya/tech/yt/ads/nativead/layout
git mv src/commonMain/kotlin/avinya/tech/yt/ads/debug ../admob-cmp-compose/src/commonMain/kotlin/avinya/tech/yt/ads/debug
cd ..
rm admob-cmp-compose/src/commonMain/kotlin/avinya/tech/yt/ads/ComposeModulePlaceholder.kt
```

- [ ] **Step 2: SPIKE — resolve cinterop transitivity (H3), then move platform UI**

Move the compose androidMain + iosMain files and the staged `*RememberAdManager.kt` files (git mv). Then compile compose iOS:
```bash
./gradlew :admob-cmp-compose:compileKotlinIosSimulatorArm64 --console=plain
```
- **If `BUILD SUCCESSFUL`:** cinterop types propagate from core; done.
- **If it fails with unresolved `GAD*`/cinterop symbols:** add to `admob-cmp-compose/build.gradle.kts` an `iosArm64()/iosSimulatorArm64()` block that re-declares `compilations.getByName("main").cinterops { val gma by creating { definitionFile.set(project(":admob-cmp-core").file("src/nativeInterop/cinterop/GoogleMobileAds.def")); compilerOpts("-F", <core-downloaded framework dir>) }; val ump by creating { ... } }` and `dependsOn(":admob-cmp-core:downloadGmaIos", ":admob-cmp-core:downloadUmpIos")`. Re-run the compile until `BUILD SUCCESSFUL`. (No second framework download — reuse core's `build/ios-frameworks`.)

- [ ] **Step 3: Compile all compose targets**

Run:
```bash
./gradlew :admob-cmp-compose:compileCommonMainKotlinMetadata :admob-cmp-compose:compileAndroidMain \
  :admob-cmp-compose:compileKotlinIosSimulatorArm64 --console=plain
```
Expected: `BUILD SUCCESSFUL`. Unresolved `internal`/`private` core symbol → promote it to `public` in core + `:admob-cmp-core:updateKotlinAbi`.

- [ ] **Step 4: Move compose tests + run them**

`git mv` the "→ compose commonTest" files into `admob-cmp-compose/src/commonTest/kotlin/avinya/tech/yt/ads/`. If a moved test references a fake from core's `Fakes.kt`, add a minimal local fake in compose test rather than depending on core's test source set. Then:
```bash
./gradlew :admob-cmp-compose:iosSimulatorArm64Test --console=plain
```
Expected: `BUILD SUCCESSFUL`; moved test counts preserved.

- [ ] **Step 5: Generate compose ABI dump**

```bash
./gradlew :admob-cmp-compose:updateKotlinAbi --console=plain
git add admob-cmp-compose/api/admob-cmp-compose.klib.api
```

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor: move all Compose UI + layout DSL + debug console into :admob-cmp-compose"
```

---

## Task 7: Reduce `:admob-cmp` to an umbrella artifact

**Files:**
- Modify: `admob-cmp/build.gradle.kts` — remove all source-set source deps except the two aggregation deps; remove the iOS framework/cinterop logic (moved in Task 5); remove `explicitApi()`/`abiValidation`.
- Delete: `admob-cmp/api/admob-cmp.klib.api` (single-module dump is retired); any now-empty `admob-cmp/src/**` dirs.
- Modify: `admob-cmp/build.gradle.kts` publishing — keep vanniktech; the umbrella publishes `dev.avinya.ads:admob-cmp` re-exporting core + compose.

**Interfaces:**
- Produces: `dev.avinya.ads:admob-cmp` whose transitive `api` graph = core + compose, so a consumer adding only the umbrella gets the full `avinya.tech.yt.ads.*` API exactly as before the split.

- [ ] **Step 1: Rewrite `admob-cmp/build.gradle.kts` as aggregation-only**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    android {
        namespace = "avinya.tech.yt.ads.umbrella"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":admob-cmp-core"))
            api(project(":admob-cmp-compose"))
        }
    }
}
```
> No `explicitApi()` / `abiValidation` here (H4): the umbrella has no source of its own, only re-exports. Remove the GitHub Packages `publishing { repositories { ... } }` block if still present (Central-only).

- [ ] **Step 2: Delete the retired single-module ABI dump + empty source dirs**

```bash
git rm admob-cmp/api/admob-cmp.klib.api
find admob-cmp/src -type d -empty -delete
```

- [ ] **Step 3: Full-repo compile of the three modules**

Run:
```bash
./gradlew :admob-cmp-core:compileKotlinIosSimulatorArm64 :admob-cmp-compose:compileKotlinIosSimulatorArm64 \
  :admob-cmp:compileKotlinIosSimulatorArm64 :admob-cmp:compileAndroidMain --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Full test + ABI matrix — must equal the Task 0 baseline totals**

Run:
```bash
./gradlew :admob-cmp-core:testAndroidHostTest :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-compose:iosSimulatorArm64Test \
  :admob-cmp-core:checkKotlinAbi :admob-cmp-compose:checkKotlinAbi --console=plain
```
Expected: `BUILD SUCCESSFUL`; sum of Android + iOS test counts across the modules == the Task 0 baseline totals (no test lost).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: reduce :admob-cmp to an umbrella that re-exports core + compose"
```

---

## Task 8: Publishing coordinates for the three artifacts

**Files:**
- Modify: root `gradle.properties` — set the shared `GROUP`, `VERSION_NAME`, and the full POM_* block + `SONATYPE_HOST` once (inherited by all three modules).
- Verify: each module's `gradle.properties` sets only `POM_ARTIFACT_ID`/`POM_NAME`/`POM_DESCRIPTION`.

**Interfaces:**
- Produces to Maven local: `dev.avinya.ads:admob-cmp-core:0.2.0`, `dev.avinya.ads:admob-cmp-compose:0.2.0`, `dev.avinya.ads:admob-cmp:0.2.0` (each with KMP `-android`/`-iosarm64`/`-iossimulatorarm64` variants).

- [ ] **Step 1: Move shared POM keys to root `gradle.properties`**

Append to the repo-root `gradle.properties` (values that were in the old `admob-cmp/gradle.properties`, with GROUP/VERSION updated):
```properties
GROUP=dev.avinya.ads
VERSION_NAME=0.2.0
POM_NAME=AdMob CMP
POM_DESCRIPTION=Plug-and-play Compose Multiplatform AdMob SDK for Android GMA Next-Gen and iOS Google Mobile Ads.
POM_URL=https://github.com/<your-gh-user>/<repo>
POM_LICENSE_NAME=Apache License 2.0
POM_LICENSE_URL=https://www.apache.org/licenses/LICENSE-2.0.txt
POM_INCEPTION_YEAR=2025
POM_DEVELOPER_ID=<your-gh-handle>
POM_DEVELOPER_NAME=<Your Name>
POM_SCM_URL=https://github.com/<your-gh-user>/<repo>
POM_SCM_CONNECTION=scm:git:https://github.com/<your-gh-user>/<repo>.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/<your-gh-user>/<repo>.git
SONATYPE_HOST=CENTRAL_PORTAL
RELEASE_SIGNING_ENABLED=true
```
Remove `GROUP`/`VERSION_NAME`/`SONATYPE_HOST`/`POM_URL`/`POM_LICENSE_*`/`POM_DEVELOPER_*`/`POM_SCM_*` from the umbrella's own `gradle.properties`, leaving there only `POM_ARTIFACT_ID=admob-cmp` + `POM_NAME`/`POM_DESCRIPTION`. (`admob-cmp/gradle.properties` currently holds the old `tech.avinya.ads`/`0.1.0` values — delete those lines.)

- [ ] **Step 2: Dry-run publish all three to Maven local**

Run:
```bash
./gradlew publishToMavenLocal -PVERSION_NAME=0.2.0 --no-configuration-cache --console=plain
ls ~/.m2/repository/dev/avinya/ads/
```
Expected: directories `admob-cmp-core`, `admob-cmp-compose`, `admob-cmp` each containing `0.2.0/` with `.pom` + `.module` metadata, coordinates reading `dev.avinya.ads`.

- [ ] **Step 3: Verify the umbrella POM re-exports both**

Run:
```bash
grep -A2 "admob-cmp-core\|admob-cmp-compose" ~/.m2/repository/dev/avinya/ads/admob-cmp/0.2.0/admob-cmp-0.2.0.pom
```
Expected: both appear as `compile`-scope dependencies (from `api(...)`).

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "build: publish dev.avinya.ads:{admob-cmp-core,admob-cmp-compose,admob-cmp}:0.2.0"
```

---

## Task 9: Point the demo app at the compose artifact

**Files:**
- Modify: `shared/build.gradle.kts` — add `implementation(project(":admob-cmp-compose"))` to `androidMain`/`iosMain` (the ad-capable targets), not to `jvmMain`/`jsMain`/`wasmJsMain`.
- Modify: `shared/src/androidMain/.../ads/AdController.android.kt` and `shared/src/iosMain/.../ads/AdController.ios.kt` — replace the `NoOpAdController` placeholders with real `AdController`s backed by the library.

**Interfaces:**
- Consumes: `avinya.tech.yt.ads.AdMob.manager`, `avinya.tech.yt.ads.IosAdMob.manager`, `AdManager.interstitial(placement)`.
- Produces: Android/iOS `getAdController()` actuals that drive real AdMob; web/desktop stay `NoOpAdController`.

- [ ] **Step 1: Depend on the compose artifact from the ad-capable targets only**

In `shared/build.gradle.kts` `sourceSets`:
```kotlin
androidMain.dependencies { implementation(project(":admob-cmp-compose")) }
iosMain.dependencies { implementation(project(":admob-cmp-compose")) }
```

- [ ] **Step 2: Implement the Android real controller**

Rewrite `shared/src/androidMain/kotlin/dev/avinya/admob/cmp/ads/AdController.android.kt`:
```kotlin
package dev.avinya.admob.cmp.ads

import android.content.Context
import avinya.tech.yt.ads.AdMob
import avinya.tech.yt.ads.AdPlacement

class AndroidAdController(private val context: Context) : AdController {
    private val manager = AdMob.manager(context)
    override val adsSupported: Boolean = true
    override fun loadInterstitial(placementId: String) {
        manager.interstitial(AdPlacement(id = placementId)) // adjust AdPlacement construction to its real ctor
    }
    override fun showInterstitial(placementId: String) { /* drive manager.interstitial(...).show() from a coroutine */ }
}
```
> Before writing this, read `admob-cmp-core/src/commonMain/.../AdPlacement.kt` and `AdManager.kt` for the exact `AdPlacement` constructor and the `InterstitialAdController` `suspend` show API, and match them. `getAdController()` here needs a `Context`; thread it through from the Android entry point (or use an app-scoped provider) rather than fabricating one.

- [ ] **Step 3: Implement the iOS real controller**

Rewrite `shared/src/iosMain/kotlin/dev/avinya/admob/cmp/ads/AdController.ios.kt` analogously using `avinya.tech.yt.ads.IosAdMob.manager`.

- [ ] **Step 4: Compile the app against the real library**

Run:
```bash
./gradlew :shared:compileAndroidMain :shared:compileKotlinIosSimulatorArm64 :androidApp:compileDebugKotlin --console=plain
```
Expected: `BUILD SUCCESSFUL`. Web/desktop `NoOpAdController` untouched.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: wire the demo app's Android/iOS AdController to admob-cmp"
```

---

## Self-Review

**1. Spec coverage.**
- `-core`/`-compose`/umbrella three-module split → Tasks 1–7. ✓
- Compose-free core (strip `@Immutable`/`@Stable`) → Task 3 Step 2 + `[strip]` list. ✓
- `rememberAdManager` seam (expect in compose, actuals extracted) → Tasks 4/5 stage, Task 6 lands. ✓
- Native-ad layout DSL + debug console + views to compose → Task 6. ✓
- iOS bindings/cinterop ownership → Task 5 (+ H3 spike in Task 6). ✓
- `dev.avinya.ads:admob-cmp{,-core,-compose}` coordinates, 0.2.0 → Task 8. ✓
- Packages stay `avinya.tech.yt.ads.*`, consumer API preserved via umbrella `api(...)` → Architecture + Task 7. ✓
- Demo app consumes the artifact + real controllers → Task 9. ✓
- Frozen-ABI handling → H4 + per-module `updateKotlinAbi` (Tasks 5/6) + umbrella no-ABI (Task 7). ✓

**2. Placeholder scan.** The `<your-gh-user>`/`<Your Name>` tokens in Task 8 are human-supplied publishing identity (same as the handoff), not code placeholders. Task 9 Step 2's `AdPlacement(...)` is explicitly gated on reading the real constructor first. No "TODO/implement later" steps remain.

**3. Type consistency.** `AdMob.manager(context)` (Android), `IosAdMob.manager` (iOS, new public accessor), `NoOpAdManager` (public), `getAdController()`/`AdController`/`NoOpAdController` (app side) are used identically across tasks. `screenWidthDp`/`toAndroidAdSize` relocate once (Task 4) and are consumed by name in Task 6. `POM_ARTIFACT_ID` values are distinct per module.

**Open item for the executor:** H3 (cinterop transitivity) is the one genuine unknown; Task 6 Step 2 resolves it empirically with both branches specified. If the "re-declare cinterop in compose" branch is taken, note it in the module's README.

---

## Notes for later (out of scope here)

- **Public non-Compose surface.** Positioning: ship `-compose` as the flagship; keep `-core` an internal boundary + demand-gated public surface. Only invest in Swift-interop docs / SKIE examples / a stable non-Compose API if native-UI KMP teams actually ask. No launch commitment.
- **CI/CD + release-please + GPG signing + namespace verification** — see `handoff.md` §6–§9; unchanged by this split except that the publish job now publishes three coordinates (the umbrella pulls the other two transitively, but publish all three).
