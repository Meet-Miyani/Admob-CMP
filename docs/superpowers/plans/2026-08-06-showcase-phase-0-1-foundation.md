# Showcase App — Phase 0 + Phase 1 (Foundation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a new `:showcase` Kotlin Multiplatform module and prove its toolchain, then build the foundation — persistence, settings, MVI base, theme and a Nav3 shell — so the app builds and runs on Android and iOS with four navigable tabs.

**Architecture:** `:showcase` is a KMP library targeting `android`, `iosArm64` and `iosSimulatorArm64` only. `shared/adCapableMain` depends on it and swaps its `PlatformAdDemo` actual to render `ShowcaseApp()`. `shared`'s jvm/js/wasmJs actuals are untouched, so `desktopApp` and `webApp` keep compiling. Layering inside `:showcase` is by package: `di / core / data / domain / ui / nav / feature`.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.1, AGP 9.2.1, Room 2.8.4 + KSP, androidx.sqlite bundled 2.7.0, DataStore 1.2.1, Navigation3 (runtime 1.1.5 / CMP ui 1.1.1), Paging 3.5.0.

**Spec:** [docs/superpowers/specs/2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Scope of this plan:** Spec Phase 0 and Phase 1 only. Phases 2–6 (consent/init, feed, article, store, inspector) get their own plans, written after this one lands.

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

Nav3's `rememberNavBackStack` requires `NavKey`s to be `@Serializable`, which needs the **kotlinx-serialization** plugin — not on the approved dependency list. Task 11 therefore uses a plain `mutableStateListOf` backstack, which works fully but **does not survive process death**. Raise this with the owner when Task 11 completes; do not add kotlinx-serialization without consent.

---

## File Structure

**Created:**

| Path | Responsibility |
|---|---|
| `showcase/build.gradle.kts` | KMP + Android + Compose + KSP + Room wiring |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt` | Root composable |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.kt` | Injected time source |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/core/mvi/MviViewModel.kt` | MVI base class |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/*.kt` | Room entities |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/*.kt` | Room DAOs |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/ShowcaseDatabase.kt` | Database + KMP constructor |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/seed/ArticleSeed.kt` | Deterministic content seed |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/prefs/SettingsRepository.kt` | DataStore-backed settings |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/*.kt` | Article and Wallet repositories |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/AppGraph.kt` | Manual DI container |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.kt` | Platform seam (interface + expect) |
| `showcase/src/androidMain/.../di/PlatformStorage.android.kt` | Context-based actual |
| `showcase/src/iosMain/.../di/PlatformStorage.ios.kt` | NSFileManager-based actual |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/*.kt` | Colour, type, theme |
| `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/*.kt` | Nav keys + NavDisplay wiring |
| `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/**` | Tests |

**Modified:**

| Path | Change |
|---|---|
| `settings.gradle.kts` | `include(":showcase")` |
| `gradle/libs.versions.toml` | new versions, libraries, plugin aliases |
| `shared/build.gradle.kts` | depend on `:showcase`; retire `adCapableTest` |
| `shared/src/adCapableMain/.../demo/PlatformAdDemo.adCapable.kt` | render `ShowcaseApp()` |
| `scripts/release-readiness.sh` | add `:showcase` tests to sections 3 and 5 |
| `README.md` | "Showcase app" section |

**Deleted:**

| Path | Reason |
|---|---|
| `shared/src/adCapableMain/.../demo/DemoAdStartup.kt` | moves into `:showcase` |
| `shared/src/adCapableTest/.../demo/DemoAdStartupTest.kt` | moves into `:showcase` |

---

# PHASE 0 — Toolchain spike (BLOCKING)

**Nothing in Phase 1 starts until Task 2's decision gate passes.**

---

### Task 1: `:showcase` module skeleton that compiles and tests on both platforms

Proves the module builds, resolves `admob-cmp-compose`, and — critically — that its **iOS test executable links against GMA/UMP**. Deliberately excludes Room and KSP so that a failure here means "module wiring" and never "annotation processing".

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `showcase/build.gradle.kts`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseBuildInfo.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/ShowcaseModuleSmokeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `object ShowcaseBuildInfo { val sdkFormats: List<String> }` — used only by this task's test; later tasks may delete it once real code exists.

- [ ] **Step 1: Add the module to the build**

In `settings.gradle.kts`, append after the existing `include(":admob-cmp-compose")` line:

```kotlin
include(":showcase")
```

- [ ] **Step 2: Add version catalog entries**

In `gradle/libs.versions.toml`, add to `[versions]` (leave every existing entry untouched):

```toml
androidx-datastore = "1.2.1"
androidx-nav3Runtime = "1.1.5"
androidx-nav3Ui = "1.1.1"
androidx-paging = "3.5.0"
androidx-room = "2.8.4"
androidx-sqlite = "2.7.0"
```

Add to `[libraries]`:

```toml
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "androidx-datastore" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "androidx-nav3Runtime" }
androidx-navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "androidx-nav3Ui" }
androidx-paging-common = { module = "androidx.paging:paging-common", version.ref = "androidx-paging" }
androidx-paging-compose = { module = "androidx.paging:paging-compose", version.ref = "androidx-paging" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "androidx-room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "androidx-room" }
androidx-room-paging = { module = "androidx.room:room-paging", version.ref = "androidx-room" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "androidx-sqlite" }
```

Do **not** add the KSP or Room plugin aliases yet — Task 2 does that, after pinning a KSP version.

- [ ] **Step 3: Create `showcase/build.gradle.kts`**

Mirrors `shared/build.gradle.kts`'s Android block and the `admobCmpConsumePublished` conditional, minus the jvm/js/wasmJs targets.

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // REQUIRED: supplies GoogleMobileAds/UMP XCFrameworks to Kotlin/Native TEST
    // executables. Without it, :showcase:iosSimulatorArm64Test fails at link with
    // "Undefined symbols ... _OBJC_CLASS_$_GADBannerView". An iOS app resolves
    // these from Xcode's SPM packages; a test executable has no Xcode.
    id("dev.avinya.ads.admob-cmp")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val consumePublishedAdmobCmp =
    providers.gradleProperty("admobCmpConsumePublished")
        .map(String::toBoolean)
        .getOrElse(false)

kotlin {
    applyDefaultHierarchyTemplate()

    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.avinya.admob.showcase"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.kotlinx.coroutines.core)

                if (consumePublishedAdmobCmp) {
                    implementation("dev.avinya.ads:admob-cmp:${providers.gradleProperty("VERSION_NAME").get()}")
                } else {
                    implementation(project(":admob-cmp-compose"))
                }
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
            }
        }
    }
}
```

- [ ] **Step 4: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/ShowcaseModuleSmokeTest.kt`.

This test exists to force a **real link against the SDK** on iOS. A test that touches no SDK type would pass even if GMA/UMP linking were broken, which is exactly the failure this spike must catch.

```kotlin
package dev.avinya.admob.showcase

import kotlin.test.Test
import kotlin.test.assertTrue

class ShowcaseModuleSmokeTest {

    @Test
    fun exposesEveryAdFormatTheSdkDefines() {
        val formats = ShowcaseBuildInfo.sdkFormats

        assertTrue(
            "Banner" in formats,
            "expected AdFormat.Banner to be visible from :showcase, got $formats",
        )
        assertTrue(
            formats.size >= 6,
            "expected at least 6 ad formats, got ${formats.size}: $formats",
        )
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — compilation error, `Unresolved reference: ShowcaseBuildInfo`.

- [ ] **Step 6: Write the minimal implementation**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseBuildInfo.kt`:

```kotlin
package dev.avinya.admob.showcase

import dev.avinya.ads.AdFormat

/**
 * Smoke-test surface proving `:showcase` can see and link against `admob-cmp`.
 *
 * Touching a real SDK type is deliberate: on iOS it forces the test executable
 * to link GoogleMobileAds/UMP, which is the failure mode the Phase 0 spike
 * exists to catch. Delete once real showcase code references the SDK.
 */
internal object ShowcaseBuildInfo {
    val sdkFormats: List<String> = AdFormat.entries.map { it.name }
}
```

- [ ] **Step 7: Run the Android host test to verify it passes**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 8: Run the iOS test — the real spike**

```bash
./gradlew :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS**.

If it fails at link with `Undefined symbols ... _OBJC_CLASS_$_GAD*` or `_OBJC_CLASS_$_UMP*`, the `dev.avinya.ads.admob-cmp` plugin is not applied or not taking effect — re-check Step 3's `plugins {}` block. **Do not "fix" this by editing the plugin** (Invariant 0); if the plugin genuinely cannot serve `:showcase`, record it in `docs/showcase-sdk-gaps.md` and stop.

- [ ] **Step 9: Verify the iOS device target compiles**

```bash
./gradlew :showcase:compileKotlinIosArm64 --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml showcase/
git commit -m "$(cat <<'EOF'
feat(showcase): add :showcase module skeleton

New KMP module targeting android, iosArm64 and iosSimulatorArm64, wired
to admob-cmp-compose and applying dev.avinya.ads.admob-cmp so iOS test
executables can link GMA/UMP.

Smoke test touches a real SDK type on purpose: that is what forces the
iOS link and makes a linking regression fail loudly.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Room + KSP spike — THE DECISION GATE

The only genuine unknown in the design. KSP's newest release is 2.3.11 and its versioning is decoupled semver, so compatibility with the pinned Kotlin 2.3.20 cannot be inferred from the version string.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `showcase/build.gradle.kts`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/SpikeEntity.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/SpikeDao.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/ShowcaseDatabase.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/db/RoomSpikeTest.kt`

**Interfaces:**
- Consumes: the `:showcase` module from Task 1.
- Produces: `abstract class ShowcaseDatabase : RoomDatabase()` with `fun spikeDao(): SpikeDao`, and `expect object ShowcaseDatabaseConstructor : RoomDatabaseConstructor<ShowcaseDatabase>`. Task 6 replaces `SpikeEntity`/`SpikeDao` with the real schema but keeps the class name `ShowcaseDatabase` and the constructor object.

- [ ] **Step 1: Pin a KSP version**

Find the newest KSP release and record it:

```bash
curl -s "https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml" | grep -o "<version>[^<]*</version>" | tail -3
```

Add to `[versions]` in `gradle/libs.versions.toml`, substituting the version printed above:

```toml
ksp = "2.3.11"
```

Add to `[plugins]`:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "androidx-room" }
```

- [ ] **Step 2: Wire KSP and Room into `showcase/build.gradle.kts`**

Add the two aliases to the `plugins {}` block, after `alias(libs.plugins.composeCompiler)`:

```kotlin
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
```

Add `room {}` and the KSP processor wiring at the **bottom of the file**, outside the `kotlin {}` block:

```kotlin
room {
    schemaDirectory("$projectDir/schemas")
}

// Room's KMP compiler runs per target, so the processor is registered per
// KSP configuration rather than once globally.
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
```

Add the runtime dependencies to `commonMain`, after `implementation(libs.kotlinx.coroutines.core)`:

```kotlin
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
```

- [ ] **Step 3: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/db/RoomSpikeTest.kt`.

Uses Room's KMP `inMemoryDatabaseBuilder`, which takes no `Context` — so the same test body runs unchanged on the Android host and on iOS.

```kotlin
package dev.avinya.admob.showcase.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.avinya.admob.showcase.data.db.entity.SpikeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomSpikeTest {

    private fun database(): ShowcaseDatabase =
        Room.inMemoryDatabaseBuilder<ShowcaseDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    @Test
    fun roundTripsARowThroughRoomOnThisPlatform() = runTest {
        val db = database()
        try {
            db.spikeDao().insert(SpikeEntity(id = 1, label = "hello"))

            assertEquals("hello", db.spikeDao().labelFor(1))
        } finally {
            db.close()
        }
    }

    @Test
    fun returnsNullForAMissingRow() = runTest {
        val db = database()
        try {
            assertEquals(null, db.spikeDao().labelFor(404))
        } finally {
            db.close()
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: ShowcaseDatabase`.

- [ ] **Step 5: Write the entity**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/SpikeEntity.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Throwaway entity proving Room's KSP processor runs for every target.
 * Task 6 deletes this and introduces the real schema.
 */
@Entity(tableName = "spike")
internal data class SpikeEntity(
    @PrimaryKey val id: Long,
    val label: String,
)
```

- [ ] **Step 6: Write the DAO**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/SpikeDao.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.avinya.admob.showcase.data.db.entity.SpikeEntity

@Dao
internal interface SpikeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpikeEntity)

    @Query("SELECT label FROM spike WHERE id = :id")
    suspend fun labelFor(id: Long): String?
}
```

- [ ] **Step 7: Write the database**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/ShowcaseDatabase.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import dev.avinya.admob.showcase.data.db.dao.SpikeDao
import dev.avinya.admob.showcase.data.db.entity.SpikeEntity

@Database(
    entities = [SpikeEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ShowcaseDatabaseConstructor::class)
internal abstract class ShowcaseDatabase : RoomDatabase() {
    abstract fun spikeDao(): SpikeDao
}

/**
 * KMP databases cannot be instantiated reflectively, so Room's KSP processor
 * generates the `actual` for this `expect` object per target. There is
 * deliberately no hand-written actual — writing one is an error.
 */
@Suppress("KotlinNoActualForExpect")
internal expect object ShowcaseDatabaseConstructor : RoomDatabaseConstructor<ShowcaseDatabase> {
    override fun initialize(): ShowcaseDatabase
}
```

- [ ] **Step 8: Run the Android host test**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **PASS**, both tests.

- [ ] **Step 9: Run the iOS test**

```bash
./gradlew :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS**, both tests.

- [ ] **Step 10: DECISION GATE — assess and report before continuing**

Classify the outcome. **Do not proceed to Phase 1 on outcomes B, C or D without the owner's decision.**

| Outcome | Meaning | Action |
|---|---|---|
| **A** — Steps 8 and 9 both pass | KSP and Room work on both targets | Commit, proceed to Phase 1 as written |
| **B** — iOS passes, Android host fails | Likely `BundledSQLiteDriver`'s JVM natives on host tests | **Stop. Report.** Likely resolution: Room tests run on iOS only, Android host tests cover non-Room logic. Owner decides |
| **C** — KSP fails against Kotlin 2.3.20 | Version incompatibility | **Stop. Report.** Fall back to the spec's no-KSP variant: DataStore-only persistence, no Room, no Paging `PagingSource` from Room. This materially changes Phase 1 and later phases — the owner decides, and the spec gets revised before any further code |
| **D** — Room's KMP codegen fails another way | e.g. `@ConstructedBy` unsupported at this version | **Stop. Report.** Record in `docs/showcase-sdk-gaps.md` if the cause is admob-cmp's toolchain pinning rather than Room itself |

Write the outcome into the commit message so the decision is recoverable later.

- [ ] **Step 11: Commit (outcome A only)**

```bash
git add gradle/libs.versions.toml showcase/
git commit -m "$(cat <<'EOF'
feat(showcase): prove Room + KSP work on android and iOS

Phase 0 decision gate. Confirms KSP runs against the pinned Kotlin
2.3.20 and that Room's KMP codegen produces a working database for
androidHostTest and iosSimulatorArm64Test.

Throwaway spike entity/DAO; Task 6 replaces them with the real schema.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

# PHASE 1 — Foundation

---

### Task 3: Wire `:showcase` into `:shared` so the app launches on both platforms

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
- Consumes: `:showcase` from Tasks 1–2.
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

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`. A placeholder for now; Task 11 replaces the body with the Nav3 shell.

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

### Task 4: Design system — colour, type, theme

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Color.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Type.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Theme.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/ui/theme/ThemeModeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class ThemeMode { System, Light, Dark }` with `fun ThemeMode.isDark(systemInDark: Boolean): Boolean`, and `@Composable fun ShowcaseTheme(themeMode: ThemeMode, content: @Composable () -> Unit)`. Task 9 persists `ThemeMode`; Task 11 wraps the Nav shell in `ShowcaseTheme`.

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

### Task 5: MVI base and injected Clock

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.kt`
- Create: `showcase/src/androidMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.android.kt`
- Create: `showcase/src/iosMain/kotlin/dev/avinya/admob/showcase/core/time/Clock.ios.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/core/mvi/MviViewModel.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/core/mvi/MviViewModelTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `interface Clock { fun nowMillis(): Long }` and `expect object SystemClock : Clock`. Task 8's tests define their own `FixedClock`; there is no shared test clock.
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

### Task 6: Real Room schema — entities

Replaces the Task 2 spike entity with the nine tables from the spec.

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/ContentEntities.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/WalletEntities.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/TelemetryEntities.kt`
- Delete: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/SpikeEntity.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/db/SchemaTest.kt`

**Interfaces:**
- Consumes: `ShowcaseDatabase` from Task 2.
- Produces: `ArticleEntity`, `BookmarkEntity`, `ReadingProgressEntity`, `UnlockEntity`, `UnlockSource`, `WalletEntity`, `RewardGrantEntity`, `AdEventEntity`, `PolicyDecisionEntity`, `PaidEventEntity`. Task 7's DAOs and Task 8's repositories consume all of these.

- [ ] **Step 1: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/db/SchemaTest.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaTest {

    private fun database(): ShowcaseDatabase =
        Room.inMemoryDatabaseBuilder<ShowcaseDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    @Test
    fun opensWithEveryTablePresent() = runTest {
        val db = database()
        try {
            // Opening lazily creates the schema; a trivial read per DAO proves
            // each table exists and matches its entity.
            assertEquals(emptyList(), db.articleDao().allIds())
            assertEquals(null, db.walletDao().current())
            assertEquals(0, db.telemetryDao().adEventCount())
        } finally {
            db.close()
        }
    }

    @Test
    fun walletIsASingleRowKeyedAtZero() = runTest {
        val db = database()
        try {
            db.walletDao().upsert(WalletEntity(id = 0, coinBalance = 120, updatedAt = 1L))
            db.walletDao().upsert(WalletEntity(id = 0, coinBalance = 200, updatedAt = 2L))

            assertEquals(200, db.walletDao().current()?.coinBalance)
        } finally {
            db.close()
        }
    }

    @Test
    fun storesAndReadsBackAnArticle() = runTest {
        val db = database()
        try {
            db.articleDao().insertAll(
                listOf(
                    ArticleEntity(
                        id = "a1",
                        title = "Structured concurrency",
                        author = "R. Elder",
                        body = "para one\n\npara two",
                        section = "Kotlin",
                        publishedAt = 1_700_000_000_000L,
                        readTimeMin = 7,
                        isPremium = false,
                        unlockCostCoins = 0,
                    ),
                )
            )

            assertEquals(listOf("a1"), db.articleDao().allIds())
        } finally {
            db.close()
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: ArticleEntity`.

- [ ] **Step 3: Write the content entities**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/ContentEntities.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** An article. Body paragraphs are separated by a blank line. */
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val body: String,
    val section: String,
    val publishedAt: Long,
    val readTimeMin: Int,
    val isPremium: Boolean,
    val unlockCostCoins: Int,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BookmarkEntity(
    @PrimaryKey val articleId: String,
    val createdAt: Long,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey val articleId: String,
    val scrollFraction: Float,
    val updatedAt: Long,
)

/** How a premium article came to be unlocked. Surfaced in the Library. */
enum class UnlockSource { REWARDED, COINS }

@Entity(
    tableName = "unlocks",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("unlockedAt")],
)
data class UnlockEntity(
    @PrimaryKey val articleId: String,
    val unlockedAt: Long,
    val source: UnlockSource,
)
```

- [ ] **Step 4: Write the wallet entities**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/WalletEntities.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row wallet. [id] is always 0. */
@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 0,
    val coinBalance: Int,
    val updatedAt: Long,
)

/**
 * One row per granted reward, keyed by an idempotency key.
 *
 * Guards against a replayed reward callback double-crediting the wallet.
 * `INSERT OR IGNORE` on this table is what makes crediting idempotent.
 */
@Entity(tableName = "reward_grants")
data class RewardGrantEntity(
    @PrimaryKey val idempotencyKey: String,
    val amount: Int,
    val grantedAt: Long,
)
```

- [ ] **Step 5: Write the telemetry entities**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/TelemetryEntities.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per SDK `AdEvent`. Feeds the Inspector's Events tab. Capped at 500 rows. */
@Entity(tableName = "ad_events", indices = [Index("at")])
data class AdEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val placementId: String,
    val format: String,
    val type: String,
    val detail: String?,
)

/**
 * One row per `AdPolicy` decision, including suppressions.
 *
 * Recording *why* no ad appeared is the single most useful thing the
 * Inspector shows, so suppressions are first-class rows, not log noise.
 * Capped at 500 rows.
 */
@Entity(tableName = "policy_decisions", indices = [Index("at")])
data class PolicyDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val placementId: String,
    val decision: String,
    val reason: String?,
)

/** One row per `AdEvent.Paid`. Feeds the Inspector's Revenue tab. Capped at 500 rows. */
@Entity(tableName = "paid_events", indices = [Index("at")])
data class PaidEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val placementId: String,
    val valueMicros: Long,
    val currency: String,
    val precision: String,
)
```

- [ ] **Step 6: Delete the spike entity and update the database**

```bash
git rm showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/SpikeEntity.kt
git rm showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/SpikeDao.kt
git rm showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/db/RoomSpikeTest.kt
```

Replace `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/ShowcaseDatabase.kt` with:

```kotlin
package dev.avinya.admob.showcase.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import dev.avinya.admob.showcase.data.db.dao.ArticleDao
import dev.avinya.admob.showcase.data.db.dao.TelemetryDao
import dev.avinya.admob.showcase.data.db.dao.WalletDao
import dev.avinya.admob.showcase.data.db.entity.AdEventEntity
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.BookmarkEntity
import dev.avinya.admob.showcase.data.db.entity.PaidEventEntity
import dev.avinya.admob.showcase.data.db.entity.PolicyDecisionEntity
import dev.avinya.admob.showcase.data.db.entity.ReadingProgressEntity
import dev.avinya.admob.showcase.data.db.entity.RewardGrantEntity
import dev.avinya.admob.showcase.data.db.entity.UnlockEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity

@Database(
    entities = [
        ArticleEntity::class,
        BookmarkEntity::class,
        ReadingProgressEntity::class,
        UnlockEntity::class,
        WalletEntity::class,
        RewardGrantEntity::class,
        AdEventEntity::class,
        PolicyDecisionEntity::class,
        PaidEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ShowcaseDatabaseConstructor::class)
abstract class ShowcaseDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun walletDao(): WalletDao
    abstract fun telemetryDao(): TelemetryDao
}

/**
 * KMP databases cannot be instantiated reflectively, so Room's KSP processor
 * generates the `actual` per target. Do not hand-write one.
 */
@Suppress("KotlinNoActualForExpect")
expect object ShowcaseDatabaseConstructor : RoomDatabaseConstructor<ShowcaseDatabase> {
    override fun initialize(): ShowcaseDatabase
}
```

The DAOs referenced above are written in Task 7. This task therefore does not compile until Task 7 lands — **complete Tasks 6 and 7 back to back and commit once, at the end of Task 7.**

- [ ] **Step 7: Do not commit yet — continue to Task 7**

---

### Task 7: Room DAOs

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/ArticleDao.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/WalletDao.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/TelemetryDao.kt`

**Interfaces:**
- Consumes: every entity from Task 6.
- Produces: `ArticleDao`, `WalletDao`, `TelemetryDao`. Task 8's repositories consume all three. The Phase 3 plan adds `ArticleDao.pagingSource()`.

- [ ] **Step 1: Write the ArticleDao**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/ArticleDao.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.BookmarkEntity
import dev.avinya.admob.showcase.data.db.entity.ReadingProgressEntity
import dev.avinya.admob.showcase.data.db.entity.UnlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    @Query("SELECT id FROM articles ORDER BY publishedAt DESC")
    suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun count(): Int

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun byId(id: String): ArticleEntity?

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<ArticleEntity>

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE articleId = :articleId")
    suspend fun progressFor(articleId: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE articleId = :articleId")
    suspend fun removeBookmark(articleId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE articleId = :articleId)")
    fun isBookmarked(articleId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addUnlock(unlock: UnlockEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM unlocks WHERE articleId = :articleId)")
    fun isUnlocked(articleId: String): Flow<Boolean>
}
```

- [ ] **Step 2: Write the WalletDao**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/WalletDao.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.avinya.admob.showcase.data.db.entity.RewardGrantEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    @Upsert
    suspend fun upsert(wallet: WalletEntity)

    @Query("SELECT * FROM wallet WHERE id = 0")
    suspend fun current(): WalletEntity?

    @Query("SELECT coinBalance FROM wallet WHERE id = 0")
    fun balance(): Flow<Int?>

    /**
     * IGNORE, not REPLACE: a replayed reward callback must be a no-op.
     * The return value is the inserted row id, or -1 when the key already existed.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordGrant(grant: RewardGrantEntity): Long
}
```

- [ ] **Step 3: Write the TelemetryDao**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/TelemetryDao.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.avinya.admob.showcase.data.db.entity.AdEventEntity
import dev.avinya.admob.showcase.data.db.entity.PaidEventEntity
import dev.avinya.admob.showcase.data.db.entity.PolicyDecisionEntity
import kotlinx.coroutines.flow.Flow

/** Row cap for every log table. A demo left running must not grow unbounded. */
internal const val TELEMETRY_ROW_CAP = 500

@Dao
interface TelemetryDao {

    @Insert
    suspend fun insertAdEvent(event: AdEventEntity)

    @Insert
    suspend fun insertPolicyDecision(decision: PolicyDecisionEntity)

    @Insert
    suspend fun insertPaidEvent(event: PaidEventEntity)

    @Query("SELECT COUNT(*) FROM ad_events")
    suspend fun adEventCount(): Int

    @Query("SELECT * FROM ad_events ORDER BY at DESC LIMIT :limit")
    fun recentAdEvents(limit: Int = TELEMETRY_ROW_CAP): Flow<List<AdEventEntity>>

    @Query("SELECT * FROM policy_decisions ORDER BY at DESC LIMIT :limit")
    fun recentPolicyDecisions(limit: Int = TELEMETRY_ROW_CAP): Flow<List<PolicyDecisionEntity>>

    @Query("SELECT * FROM paid_events ORDER BY at DESC LIMIT :limit")
    fun recentPaidEvents(limit: Int = TELEMETRY_ROW_CAP): Flow<List<PaidEventEntity>>

    @Query("DELETE FROM ad_events WHERE id NOT IN (SELECT id FROM ad_events ORDER BY id DESC LIMIT :cap)")
    suspend fun trimAdEvents(cap: Int = TELEMETRY_ROW_CAP)

    @Query("DELETE FROM policy_decisions WHERE id NOT IN (SELECT id FROM policy_decisions ORDER BY id DESC LIMIT :cap)")
    suspend fun trimPolicyDecisions(cap: Int = TELEMETRY_ROW_CAP)

    @Query("DELETE FROM paid_events WHERE id NOT IN (SELECT id FROM paid_events ORDER BY id DESC LIMIT :cap)")
    suspend fun trimPaidEvents(cap: Int = TELEMETRY_ROW_CAP)

    /** Insert and trim in one transaction, so the cap can never be exceeded between calls. */
    @Transaction
    suspend fun recordAdEvent(event: AdEventEntity) {
        insertAdEvent(event)
        trimAdEvents()
    }

    @Transaction
    suspend fun recordPolicyDecision(decision: PolicyDecisionEntity) {
        insertPolicyDecision(decision)
        trimPolicyDecisions()
    }

    @Transaction
    suspend fun recordPaidEvent(event: PaidEventEntity) {
        insertPaidEvent(event)
        trimPaidEvents()
    }
}
```

- [ ] **Step 4: Run the Task 6 schema test**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **PASS**, all three `SchemaTest` cases.

- [ ] **Step 5: Run the iOS tests**

```bash
./gradlew :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 6: Confirm the exported schema was generated**

```bash
ls showcase/schemas/dev.avinya.admob.showcase.data.db.ShowcaseDatabase/
```

Expected: `1.json`.

- [ ] **Step 7: Commit Tasks 6 and 7 together**

```bash
git add -A showcase
git commit -m "$(cat <<'EOF'
feat(showcase): add the real Room schema and DAOs

Nine tables: articles, bookmarks, reading_progress, unlocks, wallet,
reward_grants, ad_events, policy_decisions, paid_events. Replaces the
Phase 0 spike entity.

Log tables are capped at 500 rows and trimmed inside the same
transaction as the insert, so the cap can never be exceeded between
calls. Reward grants insert with IGNORE so a replayed callback is a
no-op rather than a double credit.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Seed content and repositories

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/seed/ArticleSeed.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/ArticleRepository.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/WalletRepository.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/seed/ArticleSeedTest.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/repo/WalletRepositoryTest.kt`

**Interfaces:**
- Consumes: DAOs from Task 7, `Clock` from Task 5.
- Produces:
  - `object ArticleSeed { fun articles(): List<ArticleEntity> }`
  - `class ArticleRepository(articleDao, clock)` with `suspend fun seedIfEmpty()`, `suspend fun article(id: String): ArticleEntity?`, `suspend fun page(limit: Int, offset: Int): List<ArticleEntity>`, `suspend fun setProgress(articleId: String, fraction: Float)`, `suspend fun progress(articleId: String): Float`, `fun isBookmarked(articleId: String): Flow<Boolean>`, `suspend fun setBookmarked(articleId: String, bookmarked: Boolean)`, `fun isUnlocked(articleId: String): Flow<Boolean>`
  - `class WalletRepository(walletDao, clock)` with `fun balance(): Flow<Int>`, `suspend fun currentBalance(): Int`, `suspend fun credit(amount: Int, idempotencyKey: String): CreditResult`, `suspend fun debit(amount: Int): DebitResult`
  - `sealed interface CreditResult { data class Credited(val newBalance: Int); data object AlreadyGranted }`
  - `sealed interface DebitResult { data class Debited(val newBalance: Int); data class InsufficientFunds(val balance: Int, val required: Int) }`
  - Phase 5's Store screen consumes `WalletRepository` and both result types.

- [ ] **Step 1: Write the failing seed test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/seed/ArticleSeedTest.kt`:

```kotlin
package dev.avinya.admob.showcase.data.seed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleSeedTest {

    @Test
    fun producesEnoughArticlesForSixPagesOfTwenty() {
        assertTrue(
            ArticleSeed.articles().size >= 120,
            "need >= 120 articles so paging actually pages; got ${ArticleSeed.articles().size}",
        )
    }

    @Test
    fun isDeterministic() {
        assertEquals(ArticleSeed.articles(), ArticleSeed.articles())
    }

    @Test
    fun idsAreUnique() {
        val ids = ArticleSeed.articles().map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate article ids in seed")
    }

    @Test
    fun everyArticleHasAtLeastFourParagraphsSoTheInlineNativeAdHasSomewhereToSit() {
        val tooShort = ArticleSeed.articles().filter { it.body.split("\n\n").size < 4 }
        assertEquals(emptyList(), tooShort.map { it.id })
    }

    @Test
    fun someArticlesArePremiumAndAllOfThemCostCoins() {
        val premium = ArticleSeed.articles().filter { it.isPremium }
        assertTrue(premium.isNotEmpty(), "expected some premium articles")
        assertTrue(premium.all { it.unlockCostCoins > 0 }, "premium articles must cost coins")
    }

    @Test
    fun freeArticlesCostNothing() {
        assertTrue(ArticleSeed.articles().filterNot { it.isPremium }.all { it.unlockCostCoins == 0 })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: ArticleSeed`.

- [ ] **Step 3: Write the seed**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/seed/ArticleSeed.kt`:

```kotlin
package dev.avinya.admob.showcase.data.seed

import dev.avinya.admob.showcase.data.db.entity.ArticleEntity

/**
 * Deterministic local content.
 *
 * The showcase has no network layer on purpose: ad behaviour is hard enough
 * to reason about without content loading also being a variable. Same input,
 * same 126 articles, every run, offline.
 */
object ArticleSeed {

    private val sections = listOf("Kotlin", "Compose", "Multiplatform", "Android", "iOS", "Tooling")

    private val topics = listOf(
        "Structured concurrency in practice",
        "What recomposition actually costs",
        "Reading a klib ABI dump",
        "Expect and actual, revisited",
        "Stable types and skippability",
        "Coroutine cancellation you can trust",
        "Paging without the pain",
        "A tour of the memory model",
        "Designing for two platforms at once",
        "When to reach for a state machine",
        "Build times are a feature",
        "Snapshot state, explained",
        "Interop that does not leak",
        "Testing without an emulator",
        "Dependency injection, by hand",
        "Immutability and its discontents",
        "Lifecycles across platforms",
        "Draw, layout, measure",
        "Persistence that survives a refactor",
        "Naming things, still hard",
        "The cost of an abstraction",
    )

    private val authors = listOf("R. Elder", "M. Okonkwo", "S. Lindqvist", "A. Bhatt", "J. Moreau", "T. Nakamura")

    private fun body(topic: String, section: String, index: Int): String = buildString {
        append("$topic is one of those areas where the obvious approach and the correct ")
        append("approach diverge quietly, and the divergence only shows up under load.\n\n")
        append("Most $section code starts simple. A single call site, a single owner, ")
        append("no contention. The trouble begins when the second caller arrives and ")
        append("nobody has decided who owns the state.\n\n")
        append("The rule that has held up best: make the boundary explicit before you ")
        append("make it fast. An explicit boundary can be optimised later. An implicit ")
        append("one has to be discovered first, usually during an incident.\n\n")
        append("There is a version of this argument that goes too far, and it ends in ")
        append("six layers of indirection for a two-line function. Judgement number ")
        append("$index: does the abstraction pay for the reading cost it imposes?\n\n")
        append("If it does not, delete it. That is the whole technique.")
    }

    /** 126 articles: 21 topics across 6 sections. Every 7th is premium. */
    fun articles(): List<ArticleEntity> = buildList {
        var index = 0
        sections.forEach { section ->
            topics.forEach { topic ->
                val premium = index % 7 == 6
                add(
                    ArticleEntity(
                        id = "article-${index.toString().padStart(3, '0')}",
                        title = topic,
                        author = authors[index % authors.size],
                        body = body(topic, section, index),
                        section = section,
                        // Fixed base epoch minus a per-index offset: deterministic and
                        // strictly descending, so feed order is stable across runs.
                        publishedAt = BASE_PUBLISHED_AT - index * ONE_HOUR_MILLIS,
                        readTimeMin = 4 + index % 9,
                        isPremium = premium,
                        unlockCostCoins = if (premium) 50 else 0,
                    )
                )
                index++
            }
        }
    }

    private const val BASE_PUBLISHED_AT = 1_767_225_600_000L // 2026-01-01T00:00:00Z
    private const val ONE_HOUR_MILLIS = 3_600_000L
}
```

- [ ] **Step 4: Run to verify the seed test passes**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*ArticleSeedTest*' --no-configuration-cache
```

Expected: **PASS**, all six.

- [ ] **Step 5: Write the failing wallet test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/repo/WalletRepositoryTest.kt`:

```kotlin
package dev.avinya.admob.showcase.data.repo

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FixedClock(var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
}

class WalletRepositoryTest {

    private fun database(): ShowcaseDatabase =
        Room.inMemoryDatabaseBuilder<ShowcaseDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    @Test
    fun startsAtZero() = runTest {
        val db = database()
        try {
            assertEquals(0, WalletRepository(db.walletDao(), FixedClock()).currentBalance())
        } finally {
            db.close()
        }
    }

    @Test
    fun creditsIncreaseTheBalance() = runTest {
        val db = database()
        try {
            val repo = WalletRepository(db.walletDao(), FixedClock())

            assertEquals(CreditResult.Credited(newBalance = 50), repo.credit(50, "grant-1"))
            assertEquals(CreditResult.Credited(newBalance = 100), repo.credit(50, "grant-2"))
        } finally {
            db.close()
        }
    }

    @Test
    fun aReplayedIdempotencyKeyDoesNotDoubleCredit() = runTest {
        val db = database()
        try {
            val repo = WalletRepository(db.walletDao(), FixedClock())
            repo.credit(50, "grant-1")

            assertEquals(CreditResult.AlreadyGranted, repo.credit(50, "grant-1"))
            assertEquals(50, repo.currentBalance())
        } finally {
            db.close()
        }
    }

    @Test
    fun debitsReduceTheBalance() = runTest {
        val db = database()
        try {
            val repo = WalletRepository(db.walletDao(), FixedClock())
            repo.credit(100, "grant-1")

            assertEquals(DebitResult.Debited(newBalance = 40), repo.debit(60))
        } finally {
            db.close()
        }
    }

    @Test
    fun debitingMoreThanTheBalanceFailsAndChangesNothing() = runTest {
        val db = database()
        try {
            val repo = WalletRepository(db.walletDao(), FixedClock())
            repo.credit(30, "grant-1")

            assertEquals(
                DebitResult.InsufficientFunds(balance = 30, required = 50),
                repo.debit(50),
            )
            assertEquals(30, repo.currentBalance())
        } finally {
            db.close()
        }
    }
}
```

- [ ] **Step 6: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*WalletRepositoryTest*' --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: WalletRepository`.

- [ ] **Step 7: Write WalletRepository**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/WalletRepository.kt`:

```kotlin
package dev.avinya.admob.showcase.data.repo

import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.db.dao.WalletDao
import dev.avinya.admob.showcase.data.db.entity.RewardGrantEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface CreditResult {
    data class Credited(val newBalance: Int) : CreditResult

    /** The idempotency key was already recorded. Nothing changed — this is success, not an error. */
    data object AlreadyGranted : CreditResult
}

sealed interface DebitResult {
    data class Debited(val newBalance: Int) : DebitResult
    data class InsufficientFunds(val balance: Int, val required: Int) : DebitResult
}

/**
 * The coin wallet.
 *
 * [credit] is idempotent by design. A rewarded ad's reward callback can be
 * replayed, and crediting twice for one watched ad is the bug this guards.
 */
class WalletRepository(
    private val walletDao: WalletDao,
    private val clock: Clock,
) {

    fun balance(): Flow<Int> = walletDao.balance().map { it ?: 0 }

    suspend fun currentBalance(): Int = walletDao.current()?.coinBalance ?: 0

    suspend fun credit(amount: Int, idempotencyKey: String): CreditResult {
        val now = clock.nowMillis()
        val inserted = walletDao.recordGrant(
            RewardGrantEntity(idempotencyKey = idempotencyKey, amount = amount, grantedAt = now),
        )
        if (inserted == -1L) return CreditResult.AlreadyGranted

        val newBalance = currentBalance() + amount
        walletDao.upsert(WalletEntity(id = 0, coinBalance = newBalance, updatedAt = now))
        return CreditResult.Credited(newBalance)
    }

    suspend fun debit(amount: Int): DebitResult {
        val balance = currentBalance()
        if (balance < amount) return DebitResult.InsufficientFunds(balance = balance, required = amount)

        val newBalance = balance - amount
        walletDao.upsert(WalletEntity(id = 0, coinBalance = newBalance, updatedAt = clock.nowMillis()))
        return DebitResult.Debited(newBalance)
    }
}
```

- [ ] **Step 8: Write ArticleRepository**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/ArticleRepository.kt`:

```kotlin
package dev.avinya.admob.showcase.data.repo

import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.db.dao.ArticleDao
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.BookmarkEntity
import dev.avinya.admob.showcase.data.db.entity.ReadingProgressEntity
import dev.avinya.admob.showcase.data.seed.ArticleSeed
import kotlinx.coroutines.flow.Flow

/** Reads and writes article content, bookmarks and reading progress. */
class ArticleRepository(
    private val articleDao: ArticleDao,
    private val clock: Clock,
) {

    /** Populates the database on first launch. A no-op afterwards. */
    suspend fun seedIfEmpty() {
        if (articleDao.count() == 0) {
            articleDao.insertAll(ArticleSeed.articles())
        }
    }

    suspend fun article(id: String): ArticleEntity? = articleDao.byId(id)

    suspend fun page(limit: Int, offset: Int): List<ArticleEntity> = articleDao.page(limit, offset)

    suspend fun setProgress(articleId: String, fraction: Float) {
        articleDao.upsertProgress(
            ReadingProgressEntity(
                articleId = articleId,
                scrollFraction = fraction.coerceIn(0f, 1f),
                updatedAt = clock.nowMillis(),
            ),
        )
    }

    suspend fun progress(articleId: String): Float =
        articleDao.progressFor(articleId)?.scrollFraction ?: 0f

    fun isBookmarked(articleId: String): Flow<Boolean> = articleDao.isBookmarked(articleId)

    suspend fun setBookmarked(articleId: String, bookmarked: Boolean) {
        if (bookmarked) {
            articleDao.addBookmark(BookmarkEntity(articleId = articleId, createdAt = clock.nowMillis()))
        } else {
            articleDao.removeBookmark(articleId)
        }
    }

    fun isUnlocked(articleId: String): Flow<Boolean> = articleDao.isUnlocked(articleId)
}
```

- [ ] **Step 9: Run all tests on both platforms**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 10: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add deterministic seed content and repositories

126 offline articles so Paging has six real pages, generated
deterministically — no network layer anywhere in the showcase.

WalletRepository.credit is idempotent: the grant row inserts with
IGNORE, so a replayed rewarded-ad callback returns AlreadyGranted rather
than crediting twice.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: DataStore settings

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/prefs/SettingsRepository.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/prefs/SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `ThemeMode` from Task 4.
- Produces: `class SettingsRepository(dataStore: DataStore<Preferences>)` exposing `themeMode: Flow<ThemeMode>`, `onboardingComplete: Flow<Boolean>`, `inspectorEnabled: Flow<Boolean>`, `adsMasterSwitch: Flow<Boolean>`, and setters for each. Task 10 constructs it; Phases 2 and 6 consume it.

- [ ] **Step 1: Add the dependency**

In `showcase/build.gradle.kts`, add to `commonMain` dependencies:

```kotlin
                implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 2: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/prefs/SettingsRepositoryTest.kt`:

```kotlin
package dev.avinya.admob.showcase.data.prefs

import dev.avinya.admob.showcase.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    @Test
    fun defaultsBeforeAnythingIsWritten() = runTest {
        val repo = SettingsRepository(inMemoryPreferencesDataStore())

        assertEquals(ThemeMode.System, repo.themeMode.first())
        assertFalse(repo.onboardingComplete.first())
        assertTrue(repo.inspectorEnabled.first())
        assertTrue(repo.adsMasterSwitch.first())
    }

    @Test
    fun persistsThemeMode() = runTest {
        val repo = SettingsRepository(inMemoryPreferencesDataStore())

        repo.setThemeMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, repo.themeMode.first())
    }

    @Test
    fun persistsOnboardingCompletion() = runTest {
        val repo = SettingsRepository(inMemoryPreferencesDataStore())

        repo.setOnboardingComplete(true)

        assertTrue(repo.onboardingComplete.first())
    }

    @Test
    fun anUnrecognisedStoredThemeFallsBackToTheDefault() = runTest {
        val store = inMemoryPreferencesDataStore()
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply { set(SettingsKeys.ThemeMode, "Sepia") }
        }

        assertEquals(ThemeMode.System, SettingsRepository(store).themeMode.first())
    }
}
```

The helper `inMemoryPreferencesDataStore()` is written in Step 4.

- [ ] **Step 3: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*SettingsRepositoryTest*' --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: SettingsRepository`.

- [ ] **Step 4: Write the test helper**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/prefs/InMemoryPreferences.kt`:

```kotlin
package dev.avinya.admob.showcase.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A `DataStore<Preferences>` held entirely in memory.
 *
 * Avoids touching the filesystem from tests, which keeps the same test body
 * running unchanged on the Android host and on iOS.
 */
internal fun inMemoryPreferencesDataStore(): DataStore<Preferences> = InMemoryPreferencesDataStore()

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        val updated = transform(state.value)
        state.value = updated
        updated
    }
}
```

- [ ] **Step 5: Write SettingsRepository**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/prefs/SettingsRepository.kt`:

```kotlin
package dev.avinya.admob.showcase.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.avinya.admob.showcase.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal object SettingsKeys {
    val ThemeMode = stringPreferencesKey("theme_mode")
    val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
    val ConsentDebugGeography = stringPreferencesKey("consent_debug_geography")
    val InspectorEnabled = booleanPreferencesKey("inspector_enabled")
    val AdsMasterSwitch = booleanPreferencesKey("ads_master_switch")
}

/**
 * User preferences. Structured data lives in Room; this holds only settings.
 *
 * [adsMasterSwitch] is a local kill switch. Turning it off suppresses every
 * placement in the app without touching any SDK or consent state — useful for
 * demoing the app itself, and for proving the app is fully usable ad-free.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        // An unrecognised stored value must not crash the app on launch.
        ThemeMode.entries.firstOrNull { it.name == prefs[SettingsKeys.ThemeMode] } ?: ThemeMode.Default
    }

    val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[SettingsKeys.OnboardingComplete] ?: false }

    val consentDebugGeography: Flow<String?> =
        dataStore.data.map { it[SettingsKeys.ConsentDebugGeography] }

    val inspectorEnabled: Flow<Boolean> =
        dataStore.data.map { it[SettingsKeys.InspectorEnabled] ?: true }

    val adsMasterSwitch: Flow<Boolean> =
        dataStore.data.map { it[SettingsKeys.AdsMasterSwitch] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[SettingsKeys.ThemeMode] = mode.name }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[SettingsKeys.OnboardingComplete] = complete }
    }

    suspend fun setConsentDebugGeography(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(SettingsKeys.ConsentDebugGeography)
            else prefs[SettingsKeys.ConsentDebugGeography] = value
        }
    }

    suspend fun setInspectorEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.InspectorEnabled] = enabled }
    }

    suspend fun setAdsMasterSwitch(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.AdsMasterSwitch] = enabled }
    }
}
```

- [ ] **Step 6: Run tests on both platforms**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 7: Commit**

```bash
git add showcase
git commit -m "$(cat <<'EOF'
feat(showcase): add DataStore-backed settings

Theme, onboarding, consent debug geography, inspector visibility and a
local ads kill switch. An unrecognised stored theme falls back to the
default rather than crashing on launch.

Tests use an in-memory DataStore so the same bodies run on the Android
host and iOS without touching the filesystem.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: AppGraph and the platform storage seam

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.kt`
- Create: `showcase/src/androidMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.android.kt`
- Create: `showcase/src/iosMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.ios.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/AppGraph.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`

**Interfaces:**
- Consumes: everything from Tasks 4–9.
- Produces:
  - `interface PlatformStorage { fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase>; fun dataStorePath(): String }`
  - `@Composable expect fun rememberPlatformStorage(): PlatformStorage`
  - `class AppGraph(storage: PlatformStorage)` exposing `database`, `settings`, `articles`, `wallet`, `clock`, `appScope`
  - `val LocalAppGraph: ProvidableCompositionLocal<AppGraph>`
  - Every feature ViewModel factory in Phases 2–6 reads its dependencies from `LocalAppGraph.current`.

- [ ] **Step 1: Write the common seam**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.kt`:

```kotlin
package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.room.RoomDatabase
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase

/**
 * The one platform-specific thing the showcase needs: where files live.
 *
 * Android needs a `Context`; iOS needs the documents directory. Resolving
 * this composably rather than plumbing a `Context` through `shared` is what
 * keeps `androidApp` and the iOS framework free of structural changes.
 */
interface PlatformStorage {
    fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase>
    fun dataStorePath(): String
}

@Composable
expect fun rememberPlatformStorage(): PlatformStorage

internal const val SHOWCASE_DATABASE_FILE = "showcase.db"
internal const val SHOWCASE_PREFERENCES_FILE = "showcase.preferences_pb"
```

- [ ] **Step 2: Write the Android actual**

Create `showcase/src/androidMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.android.kt`:

```kotlin
package dev.avinya.admob.showcase.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase

private class AndroidPlatformStorage(private val context: Context) : PlatformStorage {

    override fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase> =
        Room.databaseBuilder<ShowcaseDatabase>(
            context = context,
            name = context.getDatabasePath(SHOWCASE_DATABASE_FILE).absolutePath,
        )

    override fun dataStorePath(): String =
        context.filesDir.resolve(SHOWCASE_PREFERENCES_FILE).absolutePath
}

@Composable
actual fun rememberPlatformStorage(): PlatformStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidPlatformStorage(context) }
}
```

- [ ] **Step 3: Write the iOS actual**

Create `showcase/src/iosMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.ios.kt`:

```kotlin
package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private class IosPlatformStorage : PlatformStorage {

    override fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase> =
        Room.databaseBuilder<ShowcaseDatabase>(name = documentsPath(SHOWCASE_DATABASE_FILE))

    override fun dataStorePath(): String = documentsPath(SHOWCASE_PREFERENCES_FILE)

    @OptIn(ExperimentalForeignApi::class)
    private fun documentsPath(fileName: String): String {
        val documents: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(documents?.path) { "iOS documents directory unavailable" } + "/" + fileName
    }
}

@Composable
actual fun rememberPlatformStorage(): PlatformStorage = remember { IosPlatformStorage() }
```

- [ ] **Step 4: Write the AppGraph**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/AppGraph.kt`:

```kotlin
package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.core.time.SystemClock
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.data.repo.ArticleRepository
import dev.avinya.admob.showcase.data.repo.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.io.files.Path

/**
 * Manual dependency graph, constructed once per process.
 *
 * Hand-rolled rather than Koin or Hilt: the graph is small, and a demo whose
 * point is to be read benefits from wiring you can follow by eye.
 */
class AppGraph(storage: PlatformStorage) {

    val clock: Clock = SystemClock

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: ShowcaseDatabase = storage.databaseBuilder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val preferences: DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath { Path(storage.dataStorePath()) }

    val settings: SettingsRepository = SettingsRepository(preferences)

    val articles: ArticleRepository = ArticleRepository(database.articleDao(), clock)

    val wallet: WalletRepository = WalletRepository(database.walletDao(), clock)
}

/**
 * Set by [dev.avinya.admob.showcase.ShowcaseApp]. Reading it outside that
 * subtree is a programming error, so there is no default.
 */
val LocalAppGraph: ProvidableCompositionLocal<AppGraph> = compositionLocalOf {
    error("LocalAppGraph accessed outside ShowcaseApp")
}

@Composable
internal fun rememberAppGraph(): AppGraph {
    val storage = rememberPlatformStorage()
    return remember(storage) { AppGraph(storage) }
}
```

- [ ] **Step 5: Resolve DataStore's path type before compiling**

DataStore's `createWithPath` has taken an `okio.Path` in some releases and a `kotlinx.io.files.Path` in others. Determine which applies to 1.2.1 rather than guessing — a wrong import here produces a confusing "unresolved reference" that looks like a missing dependency.

```bash
./gradlew :showcase:dependencies --configuration androidMainCompileClasspath --no-configuration-cache | grep -iE "okio|kotlinx-io"
```

- If the output lists **`com.squareup.okio:okio`**, use `okio.Path.Companion.toPath()`:

```kotlin
import okio.Path.Companion.toPath

private val preferences: DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { storage.dataStorePath().toPath() }
```

- If it lists **`org.jetbrains.kotlinx:kotlinx-io-core`**, keep the `kotlinx.io.files.Path` form already written in Step 4.

Either way the transitive dependency is DataStore's own. **Do not add okio or kotlinx-io to the version catalog** — neither is separately approved, and needing an explicit declaration would mean something else is wrong.

- [ ] **Step 6: Wire the graph into ShowcaseApp**

Replace `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt` with:

```kotlin
package dev.avinya.admob.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.di.rememberAppGraph
import dev.avinya.admob.showcase.ui.theme.ShowcaseTheme
import dev.avinya.admob.showcase.ui.theme.ThemeMode

/**
 * Root of the showcase app and the only public composable `:showcase` exposes.
 *
 * `shared` calls this from its `PlatformAdDemo` actual on Android and iOS;
 * desktop and web keep rendering `UnsupportedAdPlatform()`.
 */
@Composable
fun ShowcaseApp() {
    val graph = rememberAppGraph()
    val themeMode by graph.settings.themeMode.collectAsState(initial = ThemeMode.Default)

    LaunchedEffect(graph) { graph.articles.seedIfEmpty() }

    CompositionLocalProvider(LocalAppGraph provides graph) {
        ShowcaseTheme(themeMode = themeMode) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Showcase app — foundation")
            }
        }
    }
}
```

- [ ] **Step 7: Verify both platforms compile**

```bash
./gradlew :showcase:compileKotlinIosArm64 :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Run on an emulator and confirm the database is created**

```bash
./gradlew :androidApp:installDebug
adb shell run-as dev.avinya.admob.cmp ls databases/
```

Expected: `showcase.db` present after launching the app.

- [ ] **Step 9: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add AppGraph and the platform storage seam

rememberPlatformStorage() is the only expect/actual in the module: Android
resolves paths from LocalContext, iOS from NSFileManager. Resolving it
composably rather than plumbing a Context through shared is what keeps
androidApp and the iOS framework structurally unchanged.

Manual DI, no Koin or Hilt — the graph is small and readable by eye,
which is the point of a showcase.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: Nav3 shell with four tabs

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `showcase/build.gradle.kts`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKey.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavHost.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKeyTest.kt`

**Interfaces:**
- Consumes: `LocalAppGraph` (Task 10), `ShowcaseTheme` (Task 4).
- Produces: `sealed interface ShowcaseNavKey : NavKey` with `Feed`, `Library`, `Store`, `Settings`, `ArticleDetail(articleId)`; `val TOP_LEVEL_KEYS: List<ShowcaseNavKey>`; `@Composable fun ShowcaseNavHost(backStack: SnapshotStateList<ShowcaseNavKey>)`. Phases 2–6 add entries to `entryProvider` and push `ArticleDetail`.

- [ ] **Step 1: Bump lifecycle and add the Nav3 dependencies**

`lifecycle-viewmodel-navigation3` is published at `2.11.0`, while the catalog pins `androidx-lifecycle` at `2.11.0-beta01`. Align them.

In `gradle/libs.versions.toml`, change:

```toml
androidx-lifecycle = "2.11.0"
```

and add to `[libraries]`:

```toml
androidx-lifecycle-viewmodelNavigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "androidx-lifecycle" }
```

- [ ] **Step 2: Verify the bump did not break other consumers**

The bump touches `shared`, and therefore `desktopApp` and `webApp`.

```bash
./gradlew :shared:compileAndroidMain :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`. If any fails, **stop and report** — reverting to `2.11.0-beta01` means dropping `lifecycle-viewmodel-navigation3`, which is a design change needing the owner's decision.

- [ ] **Step 3: Add the dependencies to `:showcase`**

In `showcase/build.gradle.kts`, add to `commonMain` dependencies:

```kotlin
                implementation(libs.androidx.navigation3.runtime)
                implementation(libs.androidx.navigation3.ui)
                implementation(libs.androidx.lifecycle.viewmodelNavigation3)
```

- [ ] **Step 4: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKeyTest.kt`:

```kotlin
package dev.avinya.admob.showcase.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShowcaseNavKeyTest {

    @Test
    fun exposesExactlyFourTopLevelDestinations() {
        assertEquals(
            listOf(
                ShowcaseNavKey.Feed,
                ShowcaseNavKey.Library,
                ShowcaseNavKey.Store,
                ShowcaseNavKey.Settings,
            ),
            TOP_LEVEL_KEYS,
        )
    }

    @Test
    fun articleDetailIsNotATopLevelDestination() {
        assertTrue(TOP_LEVEL_KEYS.none { it is ShowcaseNavKey.ArticleDetail })
    }

    @Test
    fun articleDetailKeysCompareByArticleId() {
        assertEquals(ShowcaseNavKey.ArticleDetail("a1"), ShowcaseNavKey.ArticleDetail("a1"))
        assertTrue(ShowcaseNavKey.ArticleDetail("a1") != ShowcaseNavKey.ArticleDetail("a2"))
    }

    @Test
    fun everyTopLevelKeyHasALabel() {
        assertTrue(TOP_LEVEL_KEYS.all { it.label.isNotBlank() })
    }
}
```

- [ ] **Step 5: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*ShowcaseNavKeyTest*' --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: ShowcaseNavKey`.

- [ ] **Step 6: Write the nav keys**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKey.kt`:

```kotlin
package dev.avinya.admob.showcase.nav

import androidx.navigation3.runtime.NavKey

/**
 * Every destination in the showcase.
 *
 * Keys are plain data, not `@Serializable`: `rememberNavBackStack` would
 * require kotlinx-serialization, which is not an approved dependency. The
 * consequence is that the backstack does not survive process death — raised
 * with the owner as an open decision.
 */
sealed interface ShowcaseNavKey : NavKey {
    val label: String

    data object Feed : ShowcaseNavKey {
        override val label: String = "Feed"
    }

    data object Library : ShowcaseNavKey {
        override val label: String = "Library"
    }

    data object Store : ShowcaseNavKey {
        override val label: String = "Store"
    }

    data object Settings : ShowcaseNavKey {
        override val label: String = "Settings"
    }

    data class ArticleDetail(val articleId: String) : ShowcaseNavKey {
        override val label: String = "Article"
    }
}

/** The bottom bar's destinations, in order. */
val TOP_LEVEL_KEYS: List<ShowcaseNavKey> = listOf(
    ShowcaseNavKey.Feed,
    ShowcaseNavKey.Library,
    ShowcaseNavKey.Store,
    ShowcaseNavKey.Settings,
)
```

- [ ] **Step 7: Run to verify the key test passes**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*ShowcaseNavKeyTest*' --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 8: Write the nav host**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavHost.kt`.

Phases 2–6 replace each placeholder body with the real screen.

```kotlin
package dev.avinya.admob.showcase.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator

/**
 * The app's navigation shell.
 *
 * Real Nav3 entries matter here beyond tidiness: each entry owns a
 * `ViewModelStore` that is cleared on pop, which is what makes banner and
 * native ad disposal actually get exercised as the user moves around.
 */
@Composable
fun ShowcaseNavHost(backStack: SnapshotStateList<ShowcaseNavKey>) {
    val current = backStack.lastOrNull() ?: ShowcaseNavKey.Feed

    Scaffold(
        bottomBar = {
            NavigationBar {
                TOP_LEVEL_KEYS.forEach { key ->
                    NavigationBarItem(
                        selected = current == key,
                        onClick = { switchTopLevel(backStack, key) },
                        // Text initials, not material-icons: that artifact is not on the
                        // approved dependency list. Phase 6's polish pass revisits this.
                        icon = { Text(key.label.first().toString()) },
                        label = { Text(key.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().padding(padding),
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<ShowcaseNavKey.Feed> { PlaceholderScreen("Feed") }
                entry<ShowcaseNavKey.Library> { PlaceholderScreen("Library") }
                entry<ShowcaseNavKey.Store> { PlaceholderScreen("Store") }
                entry<ShowcaseNavKey.Settings> { PlaceholderScreen("Settings") }
                entry<ShowcaseNavKey.ArticleDetail> { key -> PlaceholderScreen("Article ${key.articleId}") }
            },
        )
    }
}

/**
 * Switching tabs resets to a single-entry backstack rather than pushing.
 * Tabs are peers, so a back press from a tab should leave the app, not walk
 * a history of tab switches.
 */
private fun switchTopLevel(backStack: SnapshotStateList<ShowcaseNavKey>, key: ShowcaseNavKey) {
    if (backStack.size == 1 && backStack.first() == key) return
    backStack.clear()
    backStack.add(key)
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name)
    }
}
```

- [ ] **Step 9: Wire the nav host into ShowcaseApp**

In `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`, replace the `Box { Text(...) }` body inside `ShowcaseTheme` with:

```kotlin
            val backStack = remember { mutableStateListOf<ShowcaseNavKey>(ShowcaseNavKey.Feed) }
            ShowcaseNavHost(backStack = backStack)
```

and add the imports:

```kotlin
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import dev.avinya.admob.showcase.nav.ShowcaseNavHost
import dev.avinya.admob.showcase.nav.ShowcaseNavKey
```

Remove the now-unused `Box`, `fillMaxSize`, `Alignment` and `Text` imports.

- [ ] **Step 10: Verify both platforms build and all tests pass**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Run on both platforms and confirm all four tabs switch**

```bash
./gradlew :androidApp:installDebug
```

Tap each of the four tabs; each shows its placeholder. Then build and run `iosApp` in Xcode against a simulator and confirm the same.

- [ ] **Step 12: Report the process-death decision to the owner**

State plainly: the backstack uses `mutableStateListOf` and does not survive process death; restoring it needs `rememberNavBackStack`, which requires `@Serializable` nav keys and therefore the kotlinx-serialization plugin — not on the approved list. **Ask; do not add it.**

- [ ] **Step 13: Commit**

```bash
git add gradle/libs.versions.toml showcase
git commit -m "$(cat <<'EOF'
feat(showcase): add Nav3 shell with four tabs

NavDisplay with per-entry ViewModel stores, so entries are disposed on
pop — which is what will exercise banner and native ad disposal in later
phases. Tab switches reset the backstack rather than pushing, so back
from a tab leaves the app.

Aligns androidx-lifecycle 2.11.0-beta01 -> 2.11.0 to match
lifecycle-viewmodel-navigation3; shared, desktopApp and webApp verified
still compiling.

Backstack uses mutableStateListOf and does not survive process death;
rememberNavBackStack would need kotlinx-serialization, which is not an
approved dependency.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: Wire `:showcase` tests into release-readiness and document the module

**Files:**
- Modify: `scripts/release-readiness.sh`
- Modify: `README.md`

**Interfaces:**
- Consumes: everything.
- Produces: nothing consumed by later phases.

- [ ] **Step 1: Add the Android host test to section 3**

In `scripts/release-readiness.sh`, in `section "3. Android + ABI + publication metadata"`, add `:showcase:testAndroidHostTest` to the Gradle invocation, immediately before `:androidApp:assembleDebug`:

```bash
./gradlew \
  :admob-cmp-core:testAndroidHostTest \
  :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp:verifyKotlinMultiplatformPomDependencyScopes \
  :admob-cmp-compose:verifyKotlinMultiplatformPomDependencyScopes \
  :showcase:testAndroidHostTest \
  :androidApp:assembleDebug \
  --no-configuration-cache
```

- [ ] **Step 2: Add the iOS test to section 5**

In `section "5. iOS + klib ABI"`, add `:showcase:iosSimulatorArm64Test` after the two SDK iOS tests:

```bash
./gradlew \
  :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-compose:iosSimulatorArm64Test \
  :showcase:iosSimulatorArm64Test \
  :admob-cmp-core:checkKotlinAbi \
  :admob-cmp-compose:checkKotlinAbi \
  --no-configuration-cache
```

Do **not** add a `checkKotlinAbi` for `:showcase` — it is unpublished and has no ABI dump.

- [ ] **Step 3: Add the README section**

In `README.md`, add before the licence section:

```markdown
## Showcase app

`showcase/` is a production-grade Compose Multiplatform reference app that
exercises every ad format the SDK supports in realistic placements — a
reading app with a paged feed, article detail, a coin economy and a
per-screen ad Inspector.

It is a **consumer** of `admob-cmp`, never a reason to change it. Design:
[docs/superpowers/specs/2026-08-06-showcase-app-design.md](docs/superpowers/specs/2026-08-06-showcase-app-design.md).

Run it:

```bash
./gradlew :androidApp:installDebug          # Android
open iosApp/iosApp.xcodeproj                # iOS — build and run in Xcode
```

All placements use Google's test ad units with `strictTestMode` on. The app
targets Android and iOS only; `desktopApp` and `webApp` keep rendering the
unsupported-platform screen.

Tests:

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test
```
```

- [ ] **Step 4: Verify the script parses and the new targets exist**

```bash
bash -n scripts/release-readiness.sh && echo "syntax OK"
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --dry-run --no-configuration-cache
```

Expected: `syntax OK` and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add scripts/release-readiness.sh README.md
git commit -m "$(cat <<'EOF'
chore(showcase): run :showcase tests in release-readiness, document module

Adds :showcase:testAndroidHostTest to section 3 and
:showcase:iosSimulatorArm64Test to section 5. No checkKotlinAbi —
:showcase is unpublished and has no ABI dump. release.yml is untouched.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Full verification run — then STOP**

```bash
./scripts/release-readiness.sh
```

Expected: `READINESS: PASS`. `--skip-docs` is **not** acceptable: this branch modifies `gradle/libs.versions.toml`.

**Then stop.** Report to the owner: which sections ran, which were skipped, anything that failed and was fixed, plus the two open decisions (nav backstack process-death persistence, and any entries written into `docs/showcase-sdk-gaps.md`). A `READINESS: PASS` is a prerequisite for *asking* about a PR — it is not authorisation to open one.

---

## Phase 1 exit criteria

- [ ] `:showcase` compiles for `android`, `iosArm64`, `iosSimulatorArm64`
- [ ] All `:showcase` tests pass on the Android host and iOS simulator
- [ ] `androidApp` and `iosApp` launch into `ShowcaseApp` with four working tabs
- [ ] `desktopApp` and `webApp` still compile and still show the unsupported-platform screen
- [ ] `showcase.db` is created on first launch and seeded with 126 articles
- [ ] Theme, settings, wallet and article repositories are covered by passing tests
- [ ] No file under any `admob-cmp*` module has been modified (`git diff --stat master -- 'admob-cmp*'` is empty)
- [ ] `./scripts/release-readiness.sh` reports `READINESS: PASS`
- [ ] Owner has been told about the process-death decision and any SDK gaps found

---

## Next plans

Written after this one lands, each against a codebase that exists:

| Plan | Spec phase | Contents |
|---|---|---|
| `…-showcase-phase-2-consent.md` | 2 | Onboarding, consent → ATT → initialize, Settings consent/privacy/ATT/diagnostics |
| `…-showcase-phase-3-feed.md` | 3 | Paging3, ad slot insertion, key stability, `NativeAdView`, anchored adaptive banner |
| `…-showcase-phase-4-article.md` | 4 | Article detail, reading progress, inline native, collapsible banner, `AdPolicy`, interstitial |
| `…-showcase-phase-5-store.md` | 5 | Wallet UI, rewarded, rewarded-interstitial, unlock with `isBlocked`, Library |
| `…-showcase-phase-6-inspector.md` | 6 | `AppOpenAdCoordinator`, Inspector's three tabs, telemetry pipeline, KDoc pass |
