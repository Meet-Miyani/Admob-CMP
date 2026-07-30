# Track 3: Migrate to Official SwiftPM Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **⛔ DO NOT START THIS PLAN YET.** It is gated on tooling that is not released. Verify every entry criterion below before Task 1. If any fails, stop and report — do not "make it work" with beta toolchains on a published library.

**Goal:** Replace the hand-rolled XCFramework download, checksum pinning, and linker wiring with JetBrains' official `swiftPMDependencies {}`, so consumers need neither the Gradle plugin nor a manual Xcode SPM step.

**Architecture:** The Kotlin Gradle plugin gains first-class Swift Package Manager dependencies. A KMP module declares the SPM package; KGP resolves it, generates the cinterop bindings, and — critically — supplies the machine code transitively to consumers, explicitly including Kotlin/Native tests and framework linking. This deletes Track 1's plugin and the last of the consumer-side iOS setup.

**Tech Stack:** Kotlin Gradle plugin `swiftPMDependencies {}` (Alpha), Kotlin ≥ 2.4.20, Swift Package Manager.

## Entry criteria — verify ALL before starting

- [ ] **Kotlin 2.4.20 (or later) is a stable release**, not Beta/RC. As of 2026-07-29, Maven Central's latest were `2.4.10` stable and `2.4.20-Beta2`. Check:
  ```bash
  curl -s https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml | grep -oE "<version>2\.[45][^<]*</version>" | tail -10
  ```
- [ ] **SwiftPM import is no longer Alpha.** Confirm at https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html — a published library must not depend on an Alpha build-tool feature.
- [ ] **KSP has a release for the target Kotlin version.** As of 2026-07-29 KSP topped out at `2.3.10` — no 2.4.x at all, which blocks any consumer using Room or Koin annotations (ViewTube does). Check:
  ```bash
  curl -s https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml | grep -oE "<version>[^<]*</version>" | tail -5
  ```
- [ ] **Compose Multiplatform has a stable release for that Kotlin version.**
- [ ] **`swiftPMDependencies` supports binary-target packages.** GoogleMobileAds' `Package.swift` is a `.binaryTarget` pointing at `googlemobileadsios-spm-<version>.zip`. The official docs' own example (`FirebaseAnalytics`) is also a binary distribution, which is good evidence — but verify against the GMA package specifically in a throwaway project before committing to this migration.

- [ ] **🚩 THE BLOCKING UNKNOWN — a Maven-PUBLISHED library that uses SwiftPM import actually carries its linkage to consumers.** This is *not* documented, and the whole track collapses without it.

  What the docs actually promise is scoped to **projects**: *"For transitive dependencies (projects that depend on those that use SwiftPM import), the Kotlin Gradle plugin automatically provides the necessary machine code from SwiftPM dependencies."* That covers module-to-module dependencies inside one Gradle build. It says nothing about a klib resolved from Maven Central by a stranger.

  The direction of travel is encouraging but unfinished: [KT-84420](https://youtrack.jetbrains.com/issue/KT-84420) ("Figure out the handling of XCFramework when SwiftPM dependencies are involved") concluded that Kotlin should *"start emitting some `Package.swift` structure, which would describe any direct and transitive SwiftPM dependencies necessary for linkage"* — i.e. linkage requirements travel as **metadata the consumer resolves**, not as embedded binaries. Kotlin 2.4.20-Beta2 began emitting those files next to an XCFramework. Whether the equivalent reaches a Maven consumer's `linkDebugTestIosSimulatorArm64` is exactly what must be proven.

  **How to prove it** (do this before anything else in this plan):
  1. In a scratch repo, build a trivial KMP library that declares a `swiftPMDependencies` package and exposes one function touching its API.
  2. `publishToMavenLocal`.
  3. In a *separate* Gradle build with no SwiftPM declaration of its own, depend on it from `commonMain`, add a trivial test, and run `iosSimulatorArm64Test`.
  4. If it links, this track is viable. If it fails with undefined symbols, **this track is dead for a published SDK** — the consumer would have to declare the SwiftPM dependency themselves, which is no better than Track 1's plugin and worse than the status quo, since it also drags every consumer onto a new Kotlin minor.

  Record the result in this file before proceeding either way.

**This is a breaking change for consumers.** Shipping this raises the Kotlin floor for everyone. It must go out as **admob-cmp 2.0.0**, and the 1.x line should keep receiving fixes for as long as the ecosystem lags.

### The klib compatibility rules that govern this decision

From [Kotlin evolution principles](https://kotlinlang.org/docs/kotlin-evolution-principles.html), verbatim:

> "klib binaries are backwards compatible starting with Kotlin 1.9.20. For example, the 2.0.x compiler can read binaries produced by the 1.9.2x compiler."

> "Forward compatibility is not guaranteed. For example, the 2.0.x compiler is not guaranteed to read binaries produced by the 2.1.x compiler."

> "The Kotlin **cinterop** klib binaries are still in Beta. Currently, we cannot give specific compatibility guarantees between different Kotlin versions for cinterop klib binaries."

The third quote is the one that matters most here: **admob-cmp publishes cinterop klibs** (`admob-cmp-core-iosSimulatorArm64Cinterop-gmaMain.klib`, `…-umpMain.klib`), and those are explicitly outside the stable guarantee. So:

- **Consumers on a newer Kotlin than the library** is the supported direction for ordinary klibs — but for our cinterop klibs it is "probably fine, not promised." Verify per Kotlin minor and record the result in the README's compatibility table rather than assuming.
- **Consumers on an older Kotlin than the library** is unsupported in both cases. This is why raising the floor is genuinely breaking, and why publishing from a Beta compiler is a non-starter.

The README's existing conservative wording is therefore correct as-is — do **not** relax it to "2.3.20 or newer" on the strength of the backward-compatibility rule alone, because the cinterop caveat overrides it.

## This is NOT what RevenueCat does

[RevenueCat's purchases-kmp 3.0.0](https://www.revenuecat.com/blog/engineering/kmp-sdk-3/) reaches the same *outcome* (consumers add nothing in Xcode) by a different and, for us, unavailable route: a **home-grown `swiftPackage` DSL in their own `build-logic`** that compiles `purchases-ios` from source — pinned as a git submodule — and **statically links the result into the published artifact**.

That works because `purchases-ios` is open source. Google Mobile Ads is a closed-source prebuilt binary: there is no source to compile, and the XCFramework ships only third-party licenses (TCMalloc, CCTZ, NanoPB, OpenSSL, OpenMeasurement) with no grant to redistribute GMA itself. Embedding Google's binary in a `dev.avinya.ads` artifact would also reintroduce the duplicate-ObjC-class problem that breaks mediation adapters — the exact thing the bindings-only design avoids.

So: same destination, different vehicle. Track 3 rides JetBrains' official mechanism, which resolves SwiftPM packages rather than embedding them — and that is why the publishing question above is load-bearing.

## Why this is the destination

The official docs state the property that makes all of Track 1 unnecessary:

> "For transitive dependencies (projects that depend on those that use SwiftPM import), the Kotlin Gradle plugin automatically provides the necessary machine code from SwiftPM dependencies. For example, you don't need to do any additional configuration when running Kotlin/Native tests or linking a framework."

That is exactly the defect Track 1 works around, solved upstream — and it additionally removes the manual Xcode SPM step that SETUP.md has always required.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `gradle/libs.versions.toml` | Kotlin/CMP/AGP bump | 1 |
| `admob-cmp-core/build.gradle.kts` | `swiftPMDependencies {}` replaces cinterop + downloads | 2 |
| `admob-cmp-core/src/nativeInterop/cinterop/*.def` | Deleted | 2 |
| `admob-cmp-core/src/iosMain/**` | Import statements move to `swiftPMImport.*` | 3 |
| `gradle.properties` | Checksums deleted | 4 |
| `admob-cmp-gradle-plugin/` | Deleted | 4 |
| `shared/build.gradle.kts`, `iosApp/` | Consumer + Xcode simplification | 5 |
| Docs, CI | Rewritten for the new setup | 6 |

---

### Task 1: Bump the toolchain on a spike branch

- [ ] **Step 1: Branch**

```bash
git checkout -b spike/swiftpm-import
```

- [ ] **Step 2: Bump Kotlin and Compose Multiplatform in `gradle/libs.versions.toml`** to the versions that satisfied the entry criteria. Change nothing else.

- [ ] **Step 3: Establish the baseline**

```bash
./gradlew :admob-cmp-core:iosSimulatorArm64Test :admob-cmp-compose:iosSimulatorArm64Test
```

Expected: `BUILD SUCCESSFUL` on the new toolchain *before* any SwiftPM work. If the bump alone breaks the build, fix that first and commit it separately — do not debug two migrations at once.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: bump Kotlin/CMP for SwiftPM import"
```

---

### Task 2: Replace cinterop with `swiftPMDependencies`

- [ ] **Step 1: Declare the SwiftPM dependencies**

In `admob-cmp-core/build.gradle.kts`, inside `kotlin { }`, add (adjusting to the DSL shipped in the stable release — the shape below is from the Alpha docs and **must be re-read before use**):

```kotlin
    swiftPMDependencies {
        swiftPackage(
            url = url("https://github.com/googleads/swift-package-manager-google-mobile-ads.git"),
            version = from("13.7.0"),
            products = listOf(product("GoogleMobileAds")),
        )
        swiftPackage(
            url = url("https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git"),
            version = from("3.1.0"),
            products = listOf(product("GoogleUserMessagingPlatform")),
        )
    }
```

- [ ] **Step 2: Delete the replaced machinery**

From `admob-cmp-core/build.gradle.kts` delete the `cinterops { gma / ump }` blocks and every task/wiring the Track 1 plugin provided. Delete both `.def` files:

```bash
git rm admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def
```

- [ ] **Step 3: Verify bindings are generated**

```bash
./gradlew :admob-cmp-core:compileKotlinIosSimulatorArm64
```

Expected: FAILS with unresolved references to `GADMobileAds`, `UMPConsentInformation`, etc. — the bindings now live under different package names. That failure is the signal for Task 3, not a defect.

---

### Task 3: Repoint iOS imports at the generated packages

- [ ] **Step 1: Find the current import sites**

```bash
grep -rn "^import cocoapods\|^import GoogleMobileAds\|^import UserMessagingPlatform" admob-cmp-core/src/iosMain admob-cmp-compose/src/iosMain
```

- [ ] **Step 2: Rewrite them** to the `swiftPMImport.<group>.<module>.…` namespace the plugin generates. Read the exact generated package from the compiler error in Task 2 Step 3 — do not guess it.

- [ ] **Step 3: Compile, then run the library's own tests**

```bash
./gradlew :admob-cmp-core:iosSimulatorArm64Test :admob-cmp-compose:iosSimulatorArm64Test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Regenerate and review the ABI dump**

```bash
./gradlew :admob-cmp-core:updateKotlinAbi :admob-cmp-compose:updateKotlinAbi
git diff api/
```

Expected: **no change to the public API.** Only iOS-internal imports moved. If public signatures shifted, the migration leaked into the API — investigate before continuing.

---

### Task 4: Delete the workaround

- [ ] **Step 1: Remove the plugin and its pins**

```bash
git rm -r admob-cmp-gradle-plugin
```

Remove `includeBuild("admob-cmp-gradle-plugin")` from the root `settings.gradle.kts`, and delete `gmaIosHeadersSha256` / `umpIosHeadersSha256` from `gradle.properties` — SwiftPM verifies the binary target's checksum itself, from Google's published `Package.swift`.

- [ ] **Step 2: Verify nothing references the removed pieces**

```bash
grep -rn "admob-cmp-gradle-plugin\|HeadersSha256\|DownloadIosFramework\|admobTestLinkerOpts" --include="*.kts" --include="*.kt" --include="*.yml" --include="*.properties" . --exclude-dir=build --exclude-dir=.git
```

Expected: no output.

---

### Task 5: Simplify the consumer and Xcode

- [ ] **Step 1: Drop the plugin from `shared/build.gradle.kts`.**

- [ ] **Step 2: Run the one-time Xcode linkage integration** the docs require:

```bash
XCODEPROJ_PATH="$PWD/iosApp/iosApp.xcodeproj" ./gradlew :shared:integrateLinkagePackage
```

- [ ] **Step 3: Remove the now-redundant SPM packages from `iosApp`** — `GoogleMobileAds` and `GoogleUserMessagingPlatform` come through Gradle now. Remove both `XCRemoteSwiftPackageReference` entries and their build-phase references from `iosApp/iosApp.xcodeproj/project.pbxproj`, then re-resolve:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -resolvePackageDependencies
```

- [ ] **Step 4: The gate — consumer tests and the real app must both build**

```bash
./gradlew :shared:iosSimulatorArm64Test
```

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination "generic/platform=iOS Simulator" CODE_SIGNING_ALLOWED=NO build
```

Expected: both succeed **with no plugin and no manual SPM packages**. That is the whole point of this track.

---

### Task 6: Rewrite docs and CI for 2.0.0

- [ ] **Step 1: Rewrite SETUP.md** — delete the SPM step, the `JavaScriptCore` `OTHER_LDFLAGS` step, the Kotlin/Native test section, and the linker troubleshooting table. Replace with the Gradle dependency, the `integrateLinkagePackage` one-time step, and the Info.plist requirements (`GADApplicationIdentifier`, `SKAdNetworkItems`), which SwiftPM does not cover.
- [ ] **Step 2: Add the Kotlin compatibility row** to the README's version table for 2.0.0, and state plainly that 2.0.0 requires the new Kotlin minor while 1.x remains supported.
- [ ] **Step 3: Write a MIGRATION.md** from 1.x → 2.0.0: remove the plugin, remove the SPM packages, run `integrateLinkagePackage`, bump Kotlin.
- [ ] **Step 4: Update CI** — drop the plugin build/publish steps; keep `:shared:iosSimulatorArm64Test` in the published-facade job. That gate stays valuable regardless of mechanism.
- [ ] **Step 5: Commit and open the PR as a 2.0.0 candidate.**

---

## Acceptance criteria

- [ ] A consumer needs **only** the Maven dependency — no plugin, no Xcode package, no linker flags
- [ ] `:shared:iosSimulatorArm64Test` passes with no ads-specific build configuration anywhere
- [ ] The `iosApp` Xcode project references no GoogleMobileAds/UMP SPM packages
- [ ] `api/*.klib.api` is unchanged apart from intentional 2.0.0 changes
- [ ] `gradle.properties` contains no framework checksums
- [ ] README documents the Kotlin floor for 2.0.0 and the continued 1.x line

## Risks

| Risk | Mitigation |
|---|---|
| Forces every consumer onto a new Kotlin minor | Ship as 2.0.0; keep 1.x alive while KSP/Room/Koin lag |
| `swiftPMDependencies` mishandles GMA's binary target | Entry criteria require proving this in a throwaway project first |
| Mediation adapters expect app-level SPM packages | Test at least one adapter before release; if it breaks, this track does not ship |
| `integrateLinkagePackage` is friction consumers trip on | Document prominently in MIGRATION.md with the exact command |
| Alpha DSL changes shape before stable | Entry criteria forbid starting until it is stable; re-read the docs before writing Task 2 |

## If the entry criteria never clear

Track 1's plugin is a perfectly serviceable permanent answer. It is ~200 lines in one place, it is dogfooded by this repo's own modules, and it is invisible to consumers beyond a single `plugins {}` line. Do not migrate on principle — migrate when the official path is genuinely stable and the ecosystem has caught up.
