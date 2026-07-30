# Track 1: Gradle Plugin for Consumer Linking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish `dev.avinya.ads.admob-cmp`, a Gradle plugin that makes a consumer's Kotlin/Native test executables link against Google Mobile Ads with zero configuration, so no consumer ever hand-copies XCFramework download logic, version pins, or linker flags again.

**Architecture:** The GMA version these bindings are generated from is private knowledge of this repo. Today a consumer must duplicate it (version + SHA-256 + xcframework slice names + linker flags) in their own build script, hand-synchronised across a published boundary. The plugin moves that knowledge inside a published artifact versioned in lockstep with the bindings. It lives in an **included build** so this repo's own modules dogfood the exact artifact consumers get, and `admob-cmp-core`'s duplicate copy is deleted.

**Tech Stack:** Gradle `java-gradle-plugin`, Kotlin JVM, Kotlin Multiplatform Gradle plugin APIs (`KotlinMultiplatformExtension`, `TestExecutable`), vanniktech maven-publish, GitHub Actions.

## Global Constraints

- Plugin id: **`dev.avinya.ads.admob-cmp`**. Implementation class: `dev.avinya.ads.gradle.AdMobCmpPlugin`. Artifact: `dev.avinya.ads:admob-cmp-gradle-plugin`.
- Published to **Maven Central**, same as every other artifact here. Consumers therefore need `mavenCentral()` in `pluginManagement { repositories { } }` — document it, do not assume it.
- The plugin's baked-in GMA/UMP versions and checksums must be **generated from `gradle/libs.versions.toml` and `gradle.properties`**, never retyped. Drift between the bindings and the framework the plugin fetches is the exact failure this plan exists to prevent.
- Current values — `gmaIos = "13.7.0"`, `gmaUmpIos = "3.1.0"`, `gmaIosHeadersSha256=cc971f9c5e197bba82262424c7b5abcf8d7b895bfc8e59d474af696f28bebd59`, `umpIosHeadersSha256=02b6b1925be8a6cfc294478c1a6bb1dd4de70cd9e4f31cbbfb789ab4de7b2955`.
- Linker options apply to **`TestExecutable` binaries only**. The shipped app framework must stay untouched — Kotlin/Native deliberately leaves `GAD*` undefined there for Xcode to resolve via SPM. A plugin that alters framework linking is a regression, not a feature.
- `admobTestLinkerOpts` must return an **empty list on non-macOS** so Linux CI can still configure the build. The existing implementation already does this; preserve it.
- Never invent or "fix" a SHA-256 to make a build pass. The checksum gate is a supply-chain control.
- Toolchain is fixed: Kotlin `2.3.20`, AGP `9.2.1`, Gradle `9.4.1`, Java `17`. Do not bump anything.
- The repo has `org.gradle.configuration-cache=true`. Read external state through `providers`, never by touching `File` at configuration time.

## Verified starting state

Reproduced 2026-07-29 in this repo — the library's own sample consumer cannot link its test executable:

```
> Task :shared:linkDebugTestIosSimulatorArm64 FAILED
Undefined symbols for architecture arm64:
  "_OBJC_CLASS_$_GADBannerView", referenced from:
       in libAdmobCMP:admob-cmp-compose-cache.a[2](...)
```

CI does not catch this: the `ios-and-published-consumer` job verifies the consumer only as far as `:shared:compileKotlinIosSimulatorArm64` — a klib compile that never links.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `admob-cmp-gradle-plugin/settings.gradle.kts` | Included-build definition | 1 |
| `admob-cmp-gradle-plugin/build.gradle.kts` | Plugin build, id registration, resource generation, publishing | 1, 2, 5 |
| `admob-cmp-gradle-plugin/gradle.properties` | POM coordinates for the plugin artifact | 1 |
| `settings.gradle.kts` (root) | `pluginManagement { includeBuild(...) }` | 1 |
| `.../gradle/DownloadIosFramework.kt` | Checksum-gated XCFramework download task | 2 |
| `.../gradle/AdMobCmpNativeDeps.kt` | Reads the generated versions/checksums resource | 2 |
| `.../gradle/AdMobCmpPlugin.kt` | Registers tasks, wires test-binary linker options | 3 |
| `.../gradle/DoctorIosTask.kt` | Consumer integration diagnostic, now reachable by consumers | 3 |
| `admob-cmp-core/build.gradle.kts` | Deletes ~150 duplicated lines; applies the plugin | 4 |
| `shared/build.gradle.kts` | Applies the plugin (proves the consumer path) | 4 |
| `.github/workflows/release-readiness.yml` | Adds the test-link gate + plugin publication | 5 |
| `admob-cmp/docs/SETUP.md`, `README.md`, `admob-cmp/docs/PUBLISHING.md` | Consumer + maintainer docs | 6 |

---

### Task 1: Scaffold the plugin as an included build

An included build (not a subproject) so that `admob-cmp-core` and `shared` can *apply* the plugin while it is developed in the same checkout. A subproject cannot be applied by its siblings.

**Files:**
- Create: `admob-cmp-gradle-plugin/settings.gradle.kts`
- Create: `admob-cmp-gradle-plugin/build.gradle.kts`
- Create: `admob-cmp-gradle-plugin/gradle.properties`
- Create: `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/AdMobCmpPlugin.kt`
- Modify: `settings.gradle.kts` (root)

**Interfaces:**
- Produces: applicable plugin id `dev.avinya.ads.admob-cmp`; class `dev.avinya.ads.gradle.AdMobCmpPlugin` (Tasks 2–4 fill it in).

- [ ] **Step 1: Create the included build's settings file**

Create `admob-cmp-gradle-plugin/settings.gradle.kts`:

```kotlin
rootProject.name = "admob-cmp-gradle-plugin"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

- [ ] **Step 2: Create the plugin build script**

Create `admob-cmp-gradle-plugin/build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
    alias(libs.plugins.mavenPublish)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

dependencies {
    // The plugin configures Kotlin Multiplatform test binaries, so it compiles against KGP.
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("admobCmp") {
            id = "dev.avinya.ads.admob-cmp"
            implementationClass = "dev.avinya.ads.gradle.AdMobCmpPlugin"
            displayName = "AdMob CMP"
            description = "Links Google Mobile Ads into Kotlin/Native test executables for admob-cmp consumers."
        }
    }
}
```

> The `libs` accessor is unavailable in an included build unless the catalog is shared. If `alias(libs.plugins.mavenPublish)` fails to resolve, replace that line with `id("com.vanniktech.maven.publish") version "0.37.0"` — matching the version in the root `gradle/libs.versions.toml` — and add a comment that the two must be bumped together.

- [ ] **Step 3: Create the plugin's POM properties**

Create `admob-cmp-gradle-plugin/gradle.properties`:

```properties
GROUP=dev.avinya.ads
VERSION_NAME=1.1.0
POM_ARTIFACT_ID=admob-cmp-gradle-plugin
POM_NAME=AdMob CMP Gradle Plugin
POM_DESCRIPTION=Links Google Mobile Ads/UMP into Kotlin/Native test executables for admob-cmp consumers.
POM_URL=https://github.com/Meet-Miyani/Admob-CMP
POM_LICENSE_NAME=Apache License 2.0
POM_LICENSE_URL=https://www.apache.org/licenses/LICENSE-2.0.txt
POM_INCEPTION_YEAR=2025
POM_DEVELOPER_ID=Meet-Miyani
POM_DEVELOPER_NAME=Meet Miyani
POM_SCM_URL=https://github.com/Meet-Miyani/Admob-CMP
POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/Admob-CMP.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/Admob-CMP.git
mavenCentralPublishing=true
mavenCentralAutomaticPublishing=false
signAllPublications=true
```

- [ ] **Step 4: Write the empty plugin class**

Create `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/AdMobCmpPlugin.kt`:

```kotlin
package dev.avinya.ads.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Supplies the GoogleMobileAds/UMP frameworks that a consumer's Kotlin/Native **test**
 * executables must link against.
 *
 * admob-cmp ships cinterop bindings only — never Google's binaries. An iOS app resolves
 * `GAD*`/`UMP*` at final link from the Swift packages Xcode links. A Kotlin/Native test
 * executable has no Xcode, so it must resolve them itself; without this plugin the link
 * fails with `Undefined symbols ... _OBJC_CLASS_$_GADBannerView`.
 *
 * The shipped app framework is deliberately left alone.
 */
public abstract class AdMobCmpPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Filled in by Tasks 2 and 3.
    }
}
```

- [ ] **Step 5: Wire the included build into the root**

In the root `settings.gradle.kts`, inside the existing `pluginManagement { }` block, add `includeBuild` **above** the `repositories { }` block:

```kotlin
pluginManagement {
    includeBuild("admob-cmp-gradle-plugin")
    repositories {
```

- [ ] **Step 6: Verify the plugin builds and is applicable**

Run:

```bash
./gradlew -p admob-cmp-gradle-plugin build
```

Expected: `BUILD SUCCESSFUL`.

Then confirm the root build still configures with the included build attached:

```bash
./gradlew projects
```

Expected: `BUILD SUCCESSFUL`, listing `admob-cmp`, `admob-cmp-core`, `admob-cmp-compose`, `androidApp`, `desktopApp`, `shared`, `webApp`.

- [ ] **Step 7: Commit**

```bash
git add admob-cmp-gradle-plugin settings.gradle.kts
git commit -m "build: scaffold the dev.avinya.ads.admob-cmp Gradle plugin

Included build so this repo's own modules can apply the same artifact consumers
get. No behaviour yet."
```

---

### Task 2: Move the download logic into the plugin, with versions generated from the catalog

**Files:**
- Create: `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/DownloadIosFramework.kt`
- Create: `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/AdMobCmpNativeDeps.kt`
- Create: `admob-cmp-gradle-plugin/src/test/kotlin/dev/avinya/ads/gradle/AdMobCmpNativeDepsTest.kt`
- Modify: `admob-cmp-gradle-plugin/build.gradle.kts` (resource generation)

**Interfaces:**
- Consumes: plugin scaffold from Task 1.
- Produces:
  - `class DownloadIosFramework : DefaultTask` with properties `version: Property<String>`, `baseUrl: Property<String>`, `expectedSha256: Property<String>`, `markerFile: RegularFileProperty`
  - `object AdMobCmpNativeDeps` exposing `gmaIosVersion: String`, `gmaUmpIosVersion: String`, `gmaIosSha256: String`, `umpIosSha256: String`
  - Task 3 registers `DownloadIosFramework` instances using `AdMobCmpNativeDeps`.

- [ ] **Step 1: Generate the versions resource from the single source of truth**

Append to `admob-cmp-gradle-plugin/build.gradle.kts`:

```kotlin
// The GMA version these bindings were generated from lives in the root catalog, and the
// archive checksums in the root gradle.properties. Read them at build time so the plugin
// can never drift from the bindings it is shipped alongside.
val rootDirOfLibrary = layout.projectDirectory.dir("..")

fun tomlVersion(key: String): String {
    val toml = rootDirOfLibrary.file("gradle/libs.versions.toml").asFile.readText()
    return Regex("""^\s*$key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        .find(toml)?.groupValues?.get(1)
        ?: error("Version '$key' not found in gradle/libs.versions.toml")
}

fun rootProperty(key: String): String {
    val props = java.util.Properties().apply {
        rootDirOfLibrary.file("gradle.properties").asFile.inputStream().use { load(it) }
    }
    return props.getProperty(key) ?: error("Property '$key' not found in gradle.properties")
}

val generateNativeDeps by tasks.registering(WriteProperties::class) {
    destinationFile.set(layout.buildDirectory.file("generated/admobCmp/admob-cmp-native-deps.properties"))
    property("gmaIosVersion", tomlVersion("gmaIos"))
    property("gmaUmpIosVersion", tomlVersion("gmaUmpIos"))
    property("gmaIosSha256", rootProperty("gmaIosHeadersSha256"))
    property("umpIosSha256", rootProperty("umpIosHeadersSha256"))
}

sourceSets.main {
    resources.srcDir(generateNativeDeps.map { it.destinationFile.get().asFile.parentFile })
}
```

- [ ] **Step 2: Write the failing test for the constants accessor**

Create `admob-cmp-gradle-plugin/src/test/kotlin/dev/avinya/ads/gradle/AdMobCmpNativeDepsTest.kt`:

```kotlin
package dev.avinya.ads.gradle

import kotlin.test.Test
import kotlin.test.assertTrue

class AdMobCmpNativeDepsTest {

    @Test
    fun `versions are non-blank and shaped like versions`() {
        assertTrue(AdMobCmpNativeDeps.gmaIosVersion.matches(Regex("""\d+\.\d+\.\d+""")))
        assertTrue(AdMobCmpNativeDeps.gmaUmpIosVersion.matches(Regex("""\d+\.\d+\.\d+""")))
    }

    @Test
    fun `checksums are 64 hex characters`() {
        assertTrue(AdMobCmpNativeDeps.gmaIosSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(AdMobCmpNativeDeps.umpIosSha256.matches(Regex("[0-9a-f]{64}")))
    }
}
```

- [ ] **Step 3: Run the test to confirm it fails**

Run:

```bash
./gradlew -p admob-cmp-gradle-plugin test
```

Expected: FAILS to compile — `Unresolved reference: AdMobCmpNativeDeps`.

- [ ] **Step 4: Implement the constants accessor**

Create `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/AdMobCmpNativeDeps.kt`:

```kotlin
package dev.avinya.ads.gradle

import java.util.Properties

/**
 * GMA/UMP iOS versions and archive checksums this plugin release is pinned to.
 *
 * Generated at build time from the library's `gradle/libs.versions.toml` and
 * `gradle.properties`, so the frameworks this plugin downloads always match the headers
 * the shipped cinterop bindings were generated from. Never hardcode these.
 */
public object AdMobCmpNativeDeps {
    private val props: Properties by lazy {
        val stream = AdMobCmpNativeDeps::class.java
            .getResourceAsStream("/admob-cmp-native-deps.properties")
            ?: error("admob-cmp-native-deps.properties missing from the plugin jar")
        Properties().apply { stream.use { load(it) } }
    }

    private fun require(key: String): String =
        props.getProperty(key) ?: error("Missing '$key' in admob-cmp-native-deps.properties")

    public val gmaIosVersion: String get() = require("gmaIosVersion")
    public val gmaUmpIosVersion: String get() = require("gmaUmpIosVersion")
    public val gmaIosSha256: String get() = require("gmaIosSha256")
    public val umpIosSha256: String get() = require("umpIosSha256")
}
```

- [ ] **Step 5: Run the test to confirm it passes**

Run:

```bash
./gradlew -p admob-cmp-gradle-plugin test
```

Expected: `BUILD SUCCESSFUL`, both tests green.

- [ ] **Step 6: Move the download task into the plugin**

Create `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/DownloadIosFramework.kt` by porting the class currently at `admob-cmp-core/build.gradle.kts:23-105`. Port it **verbatim except** for these changes:

- add `package dev.avinya.ads.gradle` and the imports the build script got implicitly (`org.gradle.api.DefaultTask`, `org.gradle.api.GradleException`, `org.gradle.api.file.RegularFileProperty`, `org.gradle.api.provider.Property`, `org.gradle.api.tasks.Input`, `org.gradle.api.tasks.OutputFile`, `org.gradle.api.tasks.TaskAction`, `java.io.ByteArrayInputStream`, `java.io.File`, `java.net.URI`, `java.security.MessageDigest`, `java.util.zip.ZipInputStream`)
- make the class and its properties `public abstract`

Do **not** change any logic. In particular keep, unchanged:
- the marker-file cache check (`if (mf.exists() && mf.readText().trim() == version.get()) return`)
- reading the whole archive into memory and checksumming **before** any bytes reach disk
- the zip-slip guard (`if (!target.canonicalFile.toPath().startsWith(basePath)) throw GradleException(...)`)
- the post-extraction `ios-arm64` sanity check

- [ ] **Step 7: Verify the ported task compiles**

Run:

```bash
./gradlew -p admob-cmp-gradle-plugin build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add admob-cmp-gradle-plugin
git commit -m "feat(plugin): port the checksum-gated XCFramework download

Versions and checksums are generated from the library's own catalog and
gradle.properties, so the plugin cannot drift from the bindings it ships with."
```

---

### Task 3: Wire test-binary linking (the money task)

This is the task whose gate flips `:shared:iosSimulatorArm64Test` from FAILING to PASSING.

**Files:**
- Modify: `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/AdMobCmpPlugin.kt`
- Create: `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/DoctorIosTask.kt`

**Interfaces:**
- Consumes: `DownloadIosFramework`, `AdMobCmpNativeDeps` from Task 2.
- Produces: applying the plugin registers tasks `downloadGmaIos`, `downloadUmpIos`, `doctorIos` in the consumer project, and configures every Apple `TestExecutable` binary.

- [ ] **Step 1: Implement the plugin body**

Replace the body of `AdMobCmpPlugin.apply` with:

```kotlin
    override fun apply(target: Project) {
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val frameworksDir = target.layout.buildDirectory.dir("admob-cmp-ios-frameworks")

            val downloadGma = target.tasks.register("downloadGmaIos", DownloadIosFramework::class.java) {
                it.description = "Download the Google Mobile Ads iOS XCFramework (test linking only)"
                it.group = "ios-setup"
                it.baseUrl.set(GMA_DOWNLOAD_BASE)
                it.version.set(AdMobCmpNativeDeps.gmaIosVersion)
                it.expectedSha256.set(AdMobCmpNativeDeps.gmaIosSha256)
                it.markerFile.set(frameworksDir.map { d ->
                    d.file("GoogleMobileAds.xcframework/.gma_downloaded")
                })
            }
            val downloadUmp = target.tasks.register("downloadUmpIos", DownloadIosFramework::class.java) {
                it.description = "Download the User Messaging Platform iOS XCFramework (test linking only)"
                it.group = "ios-setup"
                it.baseUrl.set(GMA_DOWNLOAD_BASE)
                it.version.set(AdMobCmpNativeDeps.gmaUmpIosVersion)
                it.expectedSha256.set(AdMobCmpNativeDeps.umpIosSha256)
                it.markerFile.set(frameworksDir.map { d ->
                    d.file("UserMessagingPlatform.xcframework/.ump_downloaded")
                })
            }

            val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach { nativeTarget ->
                val targetName = nativeTarget.name
                if (targetName !in SUPPORTED_TARGETS) return@configureEach

                // Test executables perform a real link with no Xcode involved, so they must
                // resolve GAD*/UMP* themselves. Frameworks are NOT touched: Kotlin/Native
                // leaves those symbols undefined there on purpose, for Xcode to bind via SPM.
                nativeTarget.binaries.withType(TestExecutable::class.java).configureEach { binary ->
                    binary.linkerOpts(testLinkerOpts(target, targetName, frameworksDir.get().asFile))
                }
            }

            target.tasks.matching { task ->
                task.name.startsWith("link") &&
                    task.name.contains("Test") &&
                    SUPPORTED_TARGETS.any { task.name.contains(it.replaceFirstChar(Char::uppercase)) }
            }.configureEach { it.dependsOn(downloadGma, downloadUmp) }

            target.tasks.register("doctorIos", DoctorIosTask::class.java) {
                it.description = "Diagnose iOS integration (SPM products, Info.plist, framework cache). Report-only."
                it.group = "ios-setup"
                it.frameworksDir.set(frameworksDir.get().asFile.absolutePath)
                it.gmaVersion.set(AdMobCmpNativeDeps.gmaIosVersion)
                it.xcodeprojPath.set(
                    target.providers.gradleProperty("admobCmp.xcodeproj").orElse("iosApp")
                )
                it.rootDirPath.set(target.rootDir.absolutePath)
            }
        }
    }
```

Add these to the file, above the class:

```kotlin
private const val GMA_DOWNLOAD_BASE = "https://dl.google.com/googleadmobadssdk"
private val SUPPORTED_TARGETS = setOf("iosArm64", "iosSimulatorArm64")
```

and the imports `org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension`, `org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget`, `org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable`.

- [ ] **Step 2: Port the linker-options helper**

Add to `AdMobCmpPlugin.kt`, as a private top-level function. This is the logic currently at `admob-cmp-core/build.gradle.kts:144-174`, with the **unused `sdkPlatformName` local removed** (it is computed and never read today):

```kotlin
/**
 * Linker options a Kotlin/Native test link needs to resolve the GoogleMobileAds/UMP symbols
 * admob-cmp's cinterop klibs reference.
 *
 * Returns empty off macOS so Linux CI can still configure the build.
 */
private fun testLinkerOpts(project: Project, targetName: String, frameworksDir: File): List<String> {
    if (!org.gradle.internal.os.OperatingSystem.current().isMacOsX) return emptyList()
    val swiftPlatform = when (targetName) {
        "iosArm64" -> "iphoneos"
        "iosSimulatorArm64" -> "iphonesimulator"
        else -> error("Unknown iOS target: $targetName")
    }
    val developerDir = project.providers.exec {
        it.commandLine("xcode-select", "-p")
    }.standardOutput.asText.get().trim()
    val swiftCompatLibDir =
        "$developerDir/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$swiftPlatform"
    return listOf(
        "-F" + frameworkDir(frameworksDir, "GoogleMobileAds", targetName).parentFile.absolutePath,
        "-F" + frameworkDir(frameworksDir, "UserMessagingPlatform", targetName).parentFile.absolutePath,
        "-framework", "GoogleMobileAds",
        "-framework", "UserMessagingPlatform",
        // GoogleMobileAds force-loads JavaScriptCore (GADOMIDJSContextPool) and the Swift
        // runtime-compat shims; without these the link reports undefined
        // _OBJC_CLASS_$_JSContext / __swift_FORCE_LOAD_$_swiftCompatibility56.
        "-framework", "JavaScriptCore",
        "-L$swiftCompatLibDir",
    )
}

private fun frameworkDir(baseDir: File, baseName: String, targetName: String): File {
    val slice = when (targetName) {
        "iosArm64" -> "ios-arm64"
        "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
        else -> error("Unknown iOS target: $targetName")
    }
    return File(File(baseDir, "$baseName.xcframework"), "$slice/$baseName.framework")
}
```

- [ ] **Step 3: Port DoctorIosTask**

Create `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/DoctorIosTask.kt` from `admob-cmp-core/build.gradle.kts:270-351`, verbatim except for the package declaration, explicit imports, `public abstract` visibility, and one message change: the framework-cache-missing line must now read

```
run ./gradlew downloadGmaIos downloadUmpIos
```

(no `:admob-cmp-core:` prefix — it now runs in the consumer's project).

Keep it report-only. It must never fail the build.

- [ ] **Step 4: Build the plugin**

Run:

```bash
./gradlew -p admob-cmp-gradle-plugin build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add admob-cmp-gradle-plugin
git commit -m "feat(plugin): link GMA/UMP into consumer Kotlin/Native test binaries

Applies linker options to TestExecutable binaries only, wires the download tasks
into the test link, and ships doctorIos so consumers can finally run it."
```

---

### Task 4: Dogfood — apply the plugin in this repo and delete the duplicate

**Files:**
- Modify: `admob-cmp-core/build.gradle.kts` (delete lines 1-10 imports as needed, 23-177, and the `TestExecutable` block and download `dependsOn` wiring inside `kotlin { }`, and 270-360)
- Modify: `shared/build.gradle.kts`

**Interfaces:**
- Consumes: the plugin from Task 3.
- Produces: a repo with exactly one implementation of the download/link logic.

- [ ] **Step 1: Apply the plugin to `admob-cmp-core` and delete its copy**

In `admob-cmp-core/build.gradle.kts`:

1. Add `id("dev.avinya.ads.admob-cmp")` to the `plugins { }` block.
2. Delete the `DownloadIosFramework` class, `GMA_DOWNLOAD_BASE`, `downloadGmaIos`, `downloadUmpIos`, `frameworkDir`, `admobTestLinkerOpts`, the `extensions.extraProperties["admobCmpTestLinkerOpts"]` assignment, `DoctorIosTask`, and the `tasks.register("doctorIos", ...)` call.
3. Inside `kotlin { … forEach { iosTarget -> … } }`, delete the `binaries.withType(TestExecutable::class.java) { linkerOpts(...) }` block and both `tasks.matching { … }.configureEach { dependsOn(downloadGmaIos, downloadUmpIos) }` blocks — the plugin now does all of it.
4. Keep the `cinterops { gma / ump }` blocks, but they still need the downloaded headers. Repoint their `compilerOpts` and task dependency at the plugin's tasks:

```kotlin
        iosTarget.compilations.getByName("main").cinterops {
            val gma by creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/GoogleMobileAds.def"))
                compilerOpts("-F", layout.buildDirectory.dir("admob-cmp-ios-frameworks").get().asFile.absolutePath)
            }
            val ump by creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/UserMessagingPlatform.def"))
                compilerOpts("-F", layout.buildDirectory.dir("admob-cmp-ios-frameworks").get().asFile.absolutePath)
            }
        }

        tasks.matching { it.name.startsWith("cinterop") && it.name.contains(targetName.replaceFirstChar { c -> c.uppercase() }) }
            .configureEach { dependsOn("downloadGmaIos", "downloadUmpIos") }
```

> **Careful:** cinterop needs `-F` pointing at the directory *containing* the `.framework`, which differs per slice. If the single `-F` above fails to find the module, restore the per-slice path by reusing the plugin's layout: `…/admob-cmp-ios-frameworks/GoogleMobileAds.xcframework/<slice>`. Do not guess — read the cinterop error, which names the module it could not find.

- [ ] **Step 2: Verify the library's own tests still pass**

Run:

```bash
./gradlew :admob-cmp-core:iosSimulatorArm64Test :admob-cmp-compose:iosSimulatorArm64Test
```

Expected: `BUILD SUCCESSFUL`, all tests green. This proves the plugin is a faithful replacement — same behaviour, one implementation.

- [ ] **Step 3: Verify the public API did not move**

Run:

```bash
./gradlew :admob-cmp-core:checkKotlinAbi :admob-cmp-compose:checkKotlinAbi
```

Expected: `BUILD SUCCESSFUL`. Build-logic refactoring must not shift the ABI. If it does, stop — something real changed.

- [ ] **Step 4: Apply the plugin to the sample consumer**

In `shared/build.gradle.kts`, add to the `plugins { }` block:

```kotlin
    id("dev.avinya.ads.admob-cmp")
```

- [ ] **Step 5: The gate — the consumer test link must now succeed**

Run:

```bash
./gradlew :shared:iosSimulatorArm64Test
```

Expected: `BUILD SUCCESSFUL`. Before this plan, the same command failed with `Undefined symbols ... _OBJC_CLASS_$_GADBannerView`. This single result is the plan's reason for existing.

Then verify it also works against the *published* artifacts, which is what a real consumer resolves:

```bash
./gradlew publishToMavenLocal -PsignAllPublications=false --no-configuration-cache
```

```bash
./gradlew :shared:iosSimulatorArm64Test -PadmobCmpConsumePublished=true --refresh-dependencies
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verify the app framework was NOT altered**

Run:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Expected: `BUILD SUCCESSFUL`. Then confirm the real Xcode consumer still builds, since it must keep resolving GMA from SPM rather than from the plugin:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination "generic/platform=iOS Simulator" CODE_SIGNING_ALLOWED=NO build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 7: Commit**

```bash
git add admob-cmp-core/build.gradle.kts shared/build.gradle.kts
git commit -m "refactor: consume the plugin instead of duplicating its logic

admob-cmp-core drops ~150 lines of download/link logic and applies the published
plugin, so the library dogfoods the exact artifact consumers get. shared/ applying
it takes :shared:iosSimulatorArm64Test from failing to passing."
```

---

### Task 5: Close the CI hole and publish the plugin

**Files:**
- Modify: `.github/workflows/release-readiness.yml`

**Interfaces:**
- Consumes: everything above.
- Produces: a CI gate that would have caught this defect.

- [ ] **Step 1: Replace compile-only consumer verification with a real link**

In `.github/workflows/release-readiness.yml`, in the `ios-and-published-consumer` job, replace the step named `Compile Android and iOS using only the published facade` with:

```yaml
      - name: Build and TEST using only the published facade
        run: |
          ./gradlew \
            :shared:compileAndroidMain \
            :shared:iosSimulatorArm64Test \
            -PadmobCmpConsumePublished=true \
            --refresh-dependencies \
            --no-configuration-cache
```

`:shared:iosSimulatorArm64Test` links a Kotlin/Native test executable; `compileKotlinIosSimulatorArm64` only produced a klib and never linked, which is why this class of defect shipped unnoticed. The test task compiles the same sources, so the old compile step is redundant.

- [ ] **Step 2: Build and test the plugin in CI**

In the `android-and-metadata` job, add as the first Gradle step:

```yaml
      - name: Build and test the Gradle plugin
        run: ./gradlew -p admob-cmp-gradle-plugin build --no-configuration-cache
```

- [ ] **Step 3: Include the plugin in local publication**

The included build does not publish as part of the root build's `publishToMavenLocal`. In the `ios-and-published-consumer` job, change the `Publish all SDK modules locally` step to:

```yaml
      - name: Publish all SDK modules locally
        run: |
          ./gradlew publishToMavenLocal -PsignAllPublications=false --no-configuration-cache
          ./gradlew -p admob-cmp-gradle-plugin publishToMavenLocal -PsignAllPublications=false --no-configuration-cache
```

- [ ] **Step 4: Let consumers resolve the plugin from mavenLocal in the published-facade run**

In the root `settings.gradle.kts`, inside `pluginManagement { repositories { } }`, add `mavenLocal()` as the **first** entry, guarded by the existing flag:

```kotlin
    repositories {
        if (consumePublishedAdmobCmp) {
            exclusiveContent {
                forRepository { mavenLocal() }
                filter { includeGroup("dev.avinya.ads") }
            }
        }
        google {
```

> `consumePublishedAdmobCmp` is already computed at the top of `settings.gradle.kts`, above `pluginManagement`. Confirm it is still in scope there; if Gradle rejects the ordering, read the property inline with `providers.gradleProperty("admobCmpConsumePublished")` instead of hoisting.

- [ ] **Step 5: Verify the CI commands pass locally**

Run each, requiring `BUILD SUCCESSFUL`:

```bash
./gradlew -p admob-cmp-gradle-plugin build --no-configuration-cache
```

```bash
./gradlew :shared:iosSimulatorArm64Test -PadmobCmpConsumePublished=true --refresh-dependencies --no-configuration-cache
```

- [ ] **Step 6: Prove the gate actually guards**

Temporarily comment out `id("dev.avinya.ads.admob-cmp")` in `shared/build.gradle.kts` and run:

```bash
./gradlew :shared:iosSimulatorArm64Test
```

Expected: FAILS with `Undefined symbols ... _OBJC_CLASS_$_GAD*`. **Restore the line and re-run to confirm green.** A gate you have not seen fail is not a gate.

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/release-readiness.yml settings.gradle.kts
git commit -m "ci: link the consumer test executable, not just compile it

The published-facade job stopped at compileKotlinIosSimulatorArm64 — a klib compile
that never links — so a consumer-breaking link defect passed CI. Also builds and
locally publishes the Gradle plugin."
```

---

### Task 6: Document the plugin

**Files:**
- Modify: `admob-cmp/docs/SETUP.md`
- Modify: `admob-cmp/README.md`
- Modify: `admob-cmp/docs/PUBLISHING.md`

- [ ] **Step 1: Make the plugin the documented path in SETUP.md**

In the `### Kotlin/Native test executables` section added by the Track 2 plan, replace the placeholder version with the real one:

```kotlin
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}
```

Immediately below it, add:

```markdown
The plugin needs `mavenCentral()` in your settings' plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

It touches **test binaries only** — your app framework still resolves GoogleMobileAds
from the SPM packages in step 1, exactly as before. Applying it also gives you
`./gradlew doctorIos`, a report-only check of your SPM products, `Info.plist`, and
framework cache.
```

Then fix the stale invocation earlier in the same file. `admob-cmp/docs/SETUP.md` currently ends its `## 3. iOS setup` section with:

```bash
./gradlew :admob-cmp-core:doctorIos     # report-only diagnostic
```

`:admob-cmp-core` is **this repo's** module — a consumer does not have it, so that command has never worked for them. Task 3 moves `DoctorIosTask` into the plugin, which registers `doctorIos` in the consumer's own project. Replace it with:

```bash
./gradlew doctorIos     # report-only diagnostic (requires the plugin, below)
```

Keep the cross-reference honest: the task only exists once the plugin from the next section is applied, so do not present it as available to someone who has only added the Maven dependency.

- [ ] **Step 2: Mention it in the README installation block**

In `admob-cmp/README.md`, under `## Installation`, after the existing `implementation(...)` snippet, add:

```markdown
If your project runs Kotlin/Native tests (`:yourModule:iosSimulatorArm64Test`), also apply
the Gradle plugin — without it the test link fails on `Undefined symbols … _OBJC_CLASS_$_GAD*`:

```kotlin
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}
```

See [docs/SETUP.md](docs/SETUP.md#kotlinnative-test-executables).
```

- [ ] **Step 3: Document the two-command release in PUBLISHING.md**

Add a section stating that the plugin is a **separate included build** and is *not* covered by the root `publishToMavenCentral`. Both commands are required, and the plugin's `VERSION_NAME` in `admob-cmp-gradle-plugin/gradle.properties` must be bumped in lockstep with the root `gradle.properties`:

```bash
./gradlew publishToMavenCentral
```

```bash
./gradlew -p admob-cmp-gradle-plugin publishToMavenCentral
```

- [ ] **Step 4: Verify the docs' commands and anchors are real**

Run:

```bash
grep -n "kotlinnative-test-executables" admob-cmp/README.md admob-cmp/docs/SETUP.md admob-cmp-core/src/nativeInterop/cinterop/*.def
```

Expected: the anchor is referenced from README, the two `.def` files, and defined once in SETUP.md.

- [ ] **Step 5: Commit**

```bash
git add admob-cmp/README.md admob-cmp/docs/SETUP.md admob-cmp/docs/PUBLISHING.md
git commit -m "docs: make the Gradle plugin the documented consumer path"
```

---

## Acceptance criteria

- [ ] `./gradlew :shared:iosSimulatorArm64Test` passes — with and without `-PadmobCmpConsumePublished=true`
- [ ] Removing the plugin from `shared` reproduces the original `Undefined symbols` failure
- [ ] `:admob-cmp-core:iosSimulatorArm64Test` and `:admob-cmp-compose:iosSimulatorArm64Test` still pass
- [ ] `checkKotlinAbi` green for both published modules
- [ ] `xcodebuild` on `iosApp` still succeeds — the app framework path is unchanged
- [ ] `admob-cmp-core/build.gradle.kts` contains no `DownloadIosFramework`, `admobTestLinkerOpts`, or `DoctorIosTask`
- [ ] A consumer needs exactly two things: the Maven dependency and the plugin id

## Risks & rollback

| Risk | Mitigation |
|---|---|
| cinterop `-F` path changes in Task 4 break header resolution | Task 4 Step 2 runs the library's own iOS tests before anything else depends on it; the cinterop error names the missing module |
| Included build complicates the release | Documented in PUBLISHING.md as two commands; CI runs both |
| Consumers without `mavenCentral()` in `pluginManagement` cannot resolve the plugin | Documented explicitly in SETUP.md Step 1 |
| Plugin version drifts from library version | Both read from generated resources; PUBLISHING.md requires lockstep bumps. A stricter guard (a test asserting the two `VERSION_NAME`s match) is a reasonable follow-up |

Rollback is `git revert` per task — every task leaves the build green on its own.
