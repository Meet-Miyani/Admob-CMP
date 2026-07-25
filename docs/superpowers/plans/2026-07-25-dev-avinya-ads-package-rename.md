# `dev.avinya.ads` Package Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the ads library's public Kotlin packages and platform namespaces from `avinya.tech.yt.ads` to `dev.avinya.ads` without changing library branding, Maven coordinates, artifact IDs, module names, or the demo app identity.

**Architecture:** Migrate the Compose-free core first, then the Compose layer, then the public bundle and demo consumer. Each module retains its existing package suffixes; the aggregation artifact keeps its `admob-cmp` coordinate while its Android namespace becomes `dev.avinya.ads.bundle`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android KMP Library plugin, Kotlin/Native iOS frameworks, Kotlin ABI validation, Gradle.

## Global Constraints

- Public package root: `dev.avinya.ads`.
- Android namespaces: `dev.avinya.ads.core`, `dev.avinya.ads.compose`, and `dev.avinya.ads.bundle`.
- iOS framework bundle ID: `dev.avinya.ads`.
- Demo application packages, namespace, and application ID remain `dev.avinya.admob.cmp`.
- Maven group remains `dev.avinya.ads`.
- Maven artifacts remain `admob-cmp`, `admob-cmp-core`, and `admob-cmp-compose`.
- Gradle modules remain `:admob-cmp`, `:admob-cmp-core`, and `:admob-cmp-compose`.
- Do not add compatibility aliases in `avinya.tech.yt.ads`.
- Do not rewrite historical files under `docs/superpowers/` or `handoff.md`.
- Do not rename the project, repository, library brand, classes, or APIs.
- Do not commit; leave the completed migration for the user to review.

---

### Task 1: Rename the Compose-free core package

**Files:**
- Move: `admob-cmp-core/src/{commonMain,commonTest,androidMain,androidHostTest,iosMain,iosTest}/kotlin/avinya/tech/yt/ads/**`
- To: `admob-cmp-core/src/{commonMain,commonTest,androidMain,androidHostTest,iosMain,iosTest}/kotlin/dev/avinya/ads/**`
- Modify: `admob-cmp-core/build.gradle.kts`
- Regenerate: `admob-cmp-core/api/admob-cmp-core.klib.api`

**Interfaces:**
- Consumes: Existing public core declarations in `avinya.tech.yt.ads.*`.
- Produces: The same declarations and signatures in `dev.avinya.ads.*`, Android namespace `dev.avinya.ads.core`, and iOS bundle ID `dev.avinya.ads`.

- [ ] **Step 1: Record the core migration baseline**

Run:

```bash
rg -n 'avinya\.tech\.yt\.ads' \
  admob-cmp-core/src \
  admob-cmp-core/build.gradle.kts \
  admob-cmp-core/api/admob-cmp-core.klib.api
```

Expected: matches in Kotlin package/import declarations, the Android namespace, the iOS bundle ID, and the ABI dump.

- [ ] **Step 2: Move every core package tree**

Run:

```bash
for source_set in commonMain commonTest androidMain androidHostTest iosMain iosTest; do
  mkdir -p "admob-cmp-core/src/$source_set/kotlin/dev/avinya"
  git mv \
    "admob-cmp-core/src/$source_set/kotlin/avinya/tech/yt/ads" \
    "admob-cmp-core/src/$source_set/kotlin/dev/avinya/ads"
done
```

Expected: all core sources and tests now live below `kotlin/dev/avinya/ads`.

- [ ] **Step 3: Rewrite core Kotlin packages and imports**

Run:

```bash
rg -l 'avinya\.tech\.yt\.ads' admob-cmp-core/src --glob '*.kt' |
  xargs perl -pi -e 's/avinya\.tech\.yt\.ads/dev.avinya.ads/g'
```

Expected:

```bash
rg -n 'avinya\.tech\.yt\.ads' admob-cmp-core/src
```

returns no matches.

- [ ] **Step 4: Change the core Android namespace and iOS bundle ID**

Edit `admob-cmp-core/build.gradle.kts`:

```kotlin
android {
    namespace = "dev.avinya.ads.core"
    // Existing configuration remains unchanged.
}
```

and change the framework binary option to:

```kotlin
freeCompilerArgs += listOf("-Xbinary=bundleId=dev.avinya.ads")
```

Expected:

```bash
rg -n 'avinya\.tech\.yt\.ads' admob-cmp-core/build.gradle.kts
```

returns no matches.

- [ ] **Step 5: Compile and test the migrated core**

Run:

```bash
./gradlew \
  :admob-cmp-core:compileCommonMainKotlinMetadata \
  :admob-cmp-core:compileAndroidMain \
  :admob-cmp-core:compileKotlinIosSimulatorArm64 \
  :admob-cmp-core:testAndroidHostTest \
  :admob-cmp-core:iosSimulatorArm64Test \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Regenerate and verify the core ABI**

Run:

```bash
./gradlew :admob-cmp-core:updateKotlinAbi :admob-cmp-core:checkKotlinAbi --console=plain
```

Expected: `BUILD SUCCESSFUL`, and `admob-cmp-core/api/admob-cmp-core.klib.api` exposes `dev.avinya.ads.*` with no `avinya.tech.yt.ads` entries.

- [ ] **Step 7: Review the core checkpoint without committing**

Run:

```bash
git diff --check
git status --short admob-cmp-core
```

Expected: no whitespace errors; moved sources, Gradle identity changes, and the regenerated ABI dump are visible for review.

---

### Task 2: Rename the Compose package

**Files:**
- Move: `admob-cmp-compose/src/{commonMain,commonTest,androidMain,iosMain}/kotlin/avinya/tech/yt/ads/**`
- To: `admob-cmp-compose/src/{commonMain,commonTest,androidMain,iosMain}/kotlin/dev/avinya/ads/**`
- Modify: `admob-cmp-compose/build.gradle.kts`
- Regenerate: `admob-cmp-compose/api/admob-cmp-compose.klib.api`

**Interfaces:**
- Consumes: Core APIs from `dev.avinya.ads.*`.
- Produces: Existing Compose UI, native-layout DSL, debug console, and `rememberAdManager` APIs under `dev.avinya.ads.*`, with Android namespace `dev.avinya.ads.compose`.

- [ ] **Step 1: Move every Compose package tree**

Run:

```bash
for source_set in commonMain commonTest androidMain iosMain; do
  mkdir -p "admob-cmp-compose/src/$source_set/kotlin/dev/avinya"
  git mv \
    "admob-cmp-compose/src/$source_set/kotlin/avinya/tech/yt/ads" \
    "admob-cmp-compose/src/$source_set/kotlin/dev/avinya/ads"
done
```

Expected: all Compose sources and tests now live below `kotlin/dev/avinya/ads`.

- [ ] **Step 2: Rewrite Compose packages and imports**

Run:

```bash
rg -l 'avinya\.tech\.yt\.ads' admob-cmp-compose/src --glob '*.kt' |
  xargs perl -pi -e 's/avinya\.tech\.yt\.ads/dev.avinya.ads/g'
```

Expected:

```bash
rg -n 'avinya\.tech\.yt\.ads' admob-cmp-compose/src
```

returns no matches.

- [ ] **Step 3: Change the Compose Android namespace**

Edit `admob-cmp-compose/build.gradle.kts`:

```kotlin
android {
    namespace = "dev.avinya.ads.compose"
    // Existing configuration remains unchanged.
}
```

Expected:

```bash
rg -n 'avinya\.tech\.yt\.ads' admob-cmp-compose/build.gradle.kts
```

returns no matches.

- [ ] **Step 4: Compile and test the migrated Compose module**

Run:

```bash
./gradlew \
  :admob-cmp-compose:compileCommonMainKotlinMetadata \
  :admob-cmp-compose:compileAndroidMain \
  :admob-cmp-compose:compileKotlinIosSimulatorArm64 \
  :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp-compose:iosSimulatorArm64Test \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Regenerate and verify the Compose ABI**

Run:

```bash
./gradlew :admob-cmp-compose:updateKotlinAbi :admob-cmp-compose:checkKotlinAbi --console=plain
```

Expected: `BUILD SUCCESSFUL`, and `admob-cmp-compose/api/admob-cmp-compose.klib.api` exposes `dev.avinya.ads.*` with no `avinya.tech.yt.ads` entries.

- [ ] **Step 6: Review the Compose checkpoint without committing**

Run:

```bash
git diff --check
git status --short admob-cmp-compose
```

Expected: no whitespace errors; moved sources, the Android namespace change, and regenerated ABI dump are visible for review.

---

### Task 3: Rename the bundle marker and migrate the demo consumer

**Files:**
- Move: `admob-cmp/src/commonMain/kotlin/avinya/tech/yt/ads/UmbrellaModuleMarker.kt`
- To: `admob-cmp/src/commonMain/kotlin/dev/avinya/ads/BundleModuleMarker.kt`
- Modify: `admob-cmp/build.gradle.kts`
- Modify imports: `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartup.kt`
- Modify imports: `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt`
- Modify imports: `shared/src/adCapableTest/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartupTest.kt`
- Modify current documentation: `admob-cmp/AGENTS.md`
- Modify current documentation: `admob-cmp/CLAUDE.md`
- Modify current documentation: `admob-cmp/docs/ARCHITECTURE.md`
- Modify current documentation: `admob-cmp/docs/PUBLISHING.md`

**Interfaces:**
- Consumes: Core and Compose APIs from `dev.avinya.ads.*`.
- Produces: The unchanged `:admob-cmp` bundle artifact, Android namespace `dev.avinya.ads.bundle`, and a compiling demo app whose own package remains `dev.avinya.admob.cmp.*`.

- [ ] **Step 1: Move and rename the private bundle marker**

Run:

```bash
mkdir -p admob-cmp/src/commonMain/kotlin/dev/avinya/ads
git mv \
  admob-cmp/src/commonMain/kotlin/avinya/tech/yt/ads/UmbrellaModuleMarker.kt \
  admob-cmp/src/commonMain/kotlin/dev/avinya/ads/BundleModuleMarker.kt
```

Replace the file content with:

```kotlin
package dev.avinya.ads

private class BundleModuleMarker private constructor()
```

- [ ] **Step 2: Change the bundle Android namespace**

Edit `admob-cmp/build.gradle.kts`:

```kotlin
android {
    namespace = "dev.avinya.ads.bundle"
    // Existing configuration remains unchanged.
}
```

Do not change `api(project(":admob-cmp-core"))`, `api(project(":admob-cmp-compose"))`, publishing coordinates, or POM re-export behavior.

- [ ] **Step 3: Update demo imports without changing demo packages**

Run:

```bash
rg -l 'avinya\.tech\.yt\.ads' \
  shared/src/adCapableMain \
  shared/src/adCapableTest \
  --glob '*.kt' |
  xargs perl -pi -e 's/avinya\.tech\.yt\.ads/dev.avinya.ads/g'
```

Expected:

```bash
rg -n '^package ' shared/src/adCapableMain shared/src/adCapableTest
```

continues to show only `dev.avinya.admob.cmp.*` package declarations.

- [ ] **Step 4: Update current library documentation**

In the following files, replace consumer and contributor references to
`avinya.tech.yt.ads.*` with `dev.avinya.ads.*`:

```text
admob-cmp/AGENTS.md
admob-cmp/CLAUDE.md
admob-cmp/docs/ARCHITECTURE.md
admob-cmp/docs/PUBLISHING.md
```

Use “bundle artifact” instead of “umbrella artifact” when describing
`:admob-cmp`. Do not rewrite historical plans, specs, or `handoff.md`.

- [ ] **Step 5: Compile the bundle and demo consumers**

Run:

```bash
./gradlew \
  :admob-cmp:compileCommonMainKotlinMetadata \
  :admob-cmp:compileAndroidMain \
  :admob-cmp:compileKotlinIosSimulatorArm64 \
  :shared:compileAndroidMain \
  :shared:compileKotlinIosSimulatorArm64 \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run demo tests**

Run:

```bash
./gradlew \
  :shared:testAndroidHostTest \
  :shared:iosSimulatorArm64Test \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Prove the old namespace is absent from active code**

Run:

```bash
rg -n 'avinya\.tech\.yt\.ads' \
  admob-cmp-core \
  admob-cmp-compose \
  admob-cmp \
  shared \
  --glob '!build/**'
```

Expected: no matches.

Historical references under `docs/superpowers/` and `handoff.md` are intentionally excluded from this gate.

- [ ] **Step 8: Run the complete library verification gate**

Run:

```bash
./gradlew \
  :admob-cmp-core:testAndroidHostTest \
  :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp-compose:iosSimulatorArm64Test \
  :admob-cmp-core:checkKotlinAbi \
  :admob-cmp-compose:checkKotlinAbi \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Review the full migration without committing**

Run:

```bash
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors; only the package migration, namespace changes, ABI dumps, current documentation, approved spec, and this plan are part of the intended delta. Existing unrelated untracked files remain untouched.
