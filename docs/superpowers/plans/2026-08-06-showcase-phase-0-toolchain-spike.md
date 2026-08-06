# Showcase — Phase 0: Toolchain Spike (BLOCKING)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the `:showcase` module and prove its toolchain — that it links GMA/UMP on iOS, and that Room's KSP codegen works against the pinned Kotlin 2.3.20 — before any app code is written.

**Architecture:** A new KMP library module targeting `android`, `iosArm64` and `iosSimulatorArm64`, depending on `:admob-cmp-compose` and applying the `dev.avinya.ads.admob-cmp` Gradle plugin. Nothing is wired into `shared` yet; the app is untouched.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.1, AGP 9.2.1, Room 2.8.4 + KSP, androidx.sqlite bundled 2.7.0, DataStore 1.2.1, Navigation3 (runtime 1.1.5 / CMP ui 1.1.1), Paging 3.5.0.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** none. This is the first plan.

**This plan is a gate.** Task 2 ends in a four-outcome decision. Only outcome A permits the Phase 1a plan to start; the rest stop and report to the owner.

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

**Created:** `showcase/build.gradle.kts`, `showcase/src/commonMain/.../ShowcaseBuildInfo.kt`, a throwaway Room entity/DAO/database under `showcase/src/commonMain/.../data/db/`, and two tests under `showcase/src/commonTest/`.

**Modified:** `settings.gradle.kts` (add `include(":showcase")`), `gradle/libs.versions.toml` (versions, libraries, KSP + Room plugin aliases).

**Untouched:** `shared/`, `androidApp/`, `iosApp/`, `desktopApp/`, `webApp/`, and every `admob-cmp*` module.

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
- Produces: `abstract class ShowcaseDatabase : RoomDatabase()` with `fun spikeDao(): SpikeDao`, and `expect object ShowcaseDatabaseConstructor : RoomDatabaseConstructor<ShowcaseDatabase>`. The Phase 1b plan replaces `SpikeEntity`/`SpikeDao` with the real schema but keeps the class name `ShowcaseDatabase` and the constructor object.

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
 * The Phase 1b plan deletes this and introduces the real schema.
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
| **C** — KSP fails against Kotlin 2.3.20 | Version incompatibility | **Stop. Report.** Fall back to the spec's no-KSP variant: DataStore-only persistence, no Room, no Paging `PagingSource` from Room. This materially changes every later plan — the owner decides, and the spec gets revised before any further code |
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

Throwaway spike entity/DAO; the Phase 1b plan replaces them with the real schema.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

---

## Exit criteria

- [ ] `:showcase` compiles for `android`, `iosArm64`, `iosSimulatorArm64`
- [ ] `:showcase:testAndroidHostTest` passes
- [ ] `:showcase:iosSimulatorArm64Test` passes — proving GMA/UMP link **and** Room codegen
- [ ] `showcase/schemas/` contains an exported `1.json`
- [ ] Task 2's decision gate returned **outcome A**
- [ ] `git diff --stat master -- 'admob-cmp*'` is empty

---

## Next plan

**Phase 1a — App shell** (`2026-08-06-showcase-phase-1a-app-shell.md`): wire `:showcase` into `shared` so the app launches into it, add the design system and the MVI base.

Do not start it unless the gate returned outcome A.
