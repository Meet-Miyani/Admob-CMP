import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    // Kotlin/Compose/AGP plugin versions come from the version catalog via the root
    // settings.gradle.kts pluginManagement block, so they are applied here by alias.
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

// NOTE on group/version/coordinates: do NOT set `group =` / `version =` or call
// `mavenPublishing { coordinates(...) }` here. `admob-cmp/gradle.properties` already
// declares GROUP=tech.avinya.ads, VERSION_NAME=0.1.0, POM_ARTIFACT_ID=admob-cmp plus the
// full POM (name/description/url/license/developer/scm), and the vanniktech plugin reads
// all of that automatically via `pomFromGradleProperties()` (called from its own
// `apply()`) — verified by inspecting the generated
// build/publications/kotlinMultiplatform/pom-default.xml, which already carries the
// correct coordinates. `pomFromGradleProperties()` runs at plugin-apply time (inside the
// `plugins {}` block above) and calls the extension's internal groupId()/version()
// setters, which finalize those properties; a second explicit `coordinates(...)` call
// from script body — even much later — collides with that finalization and throws
// "property 'groupId$plugin' is final and cannot be changed any further". This same
// gradle.properties-driven setup also already configures Maven Central publishing
// (SONATYPE_HOST=CENTRAL_PORTAL), so that repository was already live before this change;
// only the GitHub Packages repository below is new.
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Meet-Miyani/ViewTube")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// iOS XCFramework versions (downloaded at build time — see DownloadIosFramework).
// GMA Next-Gen on Android pulls UMP transitively; gmaUmp is pinned to the resolved
// version in the catalog so it can't drift. SDK levels mirror the catalog.
val gmaIosVersion = libs.versions.gmaIos.get()
val gmaUmpIosVersion = libs.versions.gmaUmpIos.get()
val admobCompileSdk = libs.versions.android.compileSdk.get().toInt()
val admobMinSdk = libs.versions.android.minSdk.get().toInt()
val iosFrameworksDir: File = layout.buildDirectory.dir("ios-frameworks").get().asFile

abstract class DownloadIosFramework : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val baseUrl: Property<String>

    // Supply-chain integrity: the UMP endpoint is unversioned and neither archive was
    // checksummed, so the same commit could build against different headers. Fail closed.
    @get:Input
    abstract val expectedSha256: Property<String>

    @get:OutputFile
    abstract val markerFile: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun download() {
        val mf = markerFile.get().asFile
        // The marker stores the version it was extracted from, so a catalog bump
        // invalidates the cache instead of silently keeping stale frameworks.
        if (mf.exists() && mf.readText().trim() == version.get()) return
        val frameworksBase = mf.parentFile.parentFile
        mf.parentFile.deleteRecursively()
        mf.parentFile.mkdirs()
        val baseName = mf.parentFile.name
        // Note: the UMP zip URL is not version-pinned by Google; the marker records
        // the catalog version that triggered the download.
        val zipPath = when {
            baseName.startsWith("GoogleMobileAds") -> "googlemobileadssdkios-${version.get()}.zip"
            baseName.startsWith("UserMessagingPlatform") -> "googleusermessagingplatform.zip"
            else -> error("Unknown framework: $baseName")
        }
        val zipUrl = URI("${baseUrl.get()}/$zipPath").toURL()
        logger.lifecycle("Downloading from ${zipUrl}...")

        // Read the whole archive into memory first so it can be checksummed BEFORE any of
        // its bytes are extracted — the checksum gate must run on every real download, not
        // just at configuration time, and must never let unverified bytes reach disk.
        val archiveBytes = zipUrl.openStream().use { it.readBytes() }
        val expectedSha = expectedSha256.get()
        val actualSha = MessageDigest.getInstance("SHA-256")
            .digest(archiveBytes)
            .joinToString("") { "%02x".format(it) }
        check(actualSha == expectedSha) {
            "$baseName iOS header archive checksum mismatch.\n  expected: $expectedSha\n  actual:   $actualSha\n" +
                "Refusing to generate bindings from an unverified archive."
        }

        val basePath = frameworksBase.canonicalFile.toPath()
        ByteArrayInputStream(archiveBytes).use { input ->
            ZipInputStream(input).use { zis ->
                val firstEntry = zis.nextEntry ?: throw GradleException("Empty zip from ${zipUrl}")
                val prefix = firstEntry.name.substringBefore('/') + "/"
                fun processEntry(entry: java.util.zip.ZipEntry, base: File) {
                    val rel = entry.name.removePrefix(prefix)
                    val target = File(base, rel)
                    if (!target.canonicalFile.toPath().startsWith(basePath)) {
                        throw GradleException("Zip entry escapes extraction dir: ${entry.name}")
                    }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile.mkdirs()
                        target.outputStream().use { it.write(zis.readAllBytes()) }
                    }
                }
                processEntry(firstEntry, frameworksBase)
                zis.closeEntry()
                var entry = zis.nextEntry
                while (entry != null) {
                    processEntry(entry, frameworksBase)
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        if (!File(mf.parentFile, "ios-arm64").exists()) {
            throw GradleException("Extraction did not produce ${mf.parentFile}/ios-arm64 — zip layout changed?")
        }
        mf.writeText(version.get())
        logger.lifecycle("Extracted to ${mf.parentFile}")
    }
}

val GMA_DOWNLOAD_BASE = "https://dl.google.com/googleadmobadssdk"

val downloadGmaIos by tasks.registering(DownloadIosFramework::class) {
    description = "Download Google Mobile Ads iOS XCFramework"
    group = "ios-setup"
    baseUrl = GMA_DOWNLOAD_BASE
    version = gmaIosVersion
    expectedSha256 = providers.gradleProperty("gmaIosHeadersSha256")
    markerFile.set(layout.buildDirectory.file("ios-frameworks/GoogleMobileAds.xcframework/.gma_downloaded"))
}

val downloadUmpIos by tasks.registering(DownloadIosFramework::class) {
    description = "Download User Messaging Platform iOS XCFramework"
    group = "ios-setup"
    baseUrl = GMA_DOWNLOAD_BASE
    version = gmaUmpIosVersion
    expectedSha256 = providers.gradleProperty("umpIosHeadersSha256")
    markerFile.set(layout.buildDirectory.file("ios-frameworks/UserMessagingPlatform.xcframework/.ump_downloaded"))
}

fun frameworkDir(baseName: String, targetName: String): File {
    val xcframeworkDir = File(iosFrameworksDir, "${baseName}.xcframework")
    val slice = when (targetName) {
        "iosArm64" -> "ios-arm64"
        "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
        else -> error("Unknown iOS target: $targetName")
    }
    return File(xcframeworkDir, "$slice/${baseName}.framework")
}

/**
 * Linker options a final iOS **test** link needs to resolve the GoogleMobileAds/UMP symbols this
 * bindings-only module pulls in. The real Xcode app supplies these via SPM + OTHER_LDFLAGS (see
 * AGENTS.md "iOS setup"); a Kotlin/Native test executable has no Xcode, so it must name them here.
 *
 * This is the single source of truth: admob-cmp applies it to its own test binaries, AND any
 * consumer that compiles admob-cmp into its test executable (e.g. composeApp) reads it back via
 * the `admobCmpTestLinkerOpts` extension below — so consumers never hardcode admob-cmp's paths.
 */
fun admobTestLinkerOpts(targetName: String): List<String> {
    val swiftPlatform = when (targetName) {
        "iosArm64" -> "iphoneos"
        "iosSimulatorArm64" -> "iphonesimulator"
        else -> error("Unknown iOS target: $targetName")
    }
    val developerDir = providers.exec {
        commandLine("xcode-select", "-p")
    }.standardOutput.asText.get().trim()
    val swiftCompatLibDir =
        "$developerDir/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$swiftPlatform"
    return listOf(
        "-F" + frameworkDir("GoogleMobileAds", targetName).parentFile.absolutePath,
        "-F" + frameworkDir("UserMessagingPlatform", targetName).parentFile.absolutePath,
        "-framework", "GoogleMobileAds",
        "-framework", "UserMessagingPlatform",
        // GoogleMobileAds force-loads JavaScriptCore (GADOMIDJSContextPool) and the Swift
        // runtime-compat shims; without these the test link reports undefined _OBJC_CLASS_$_JSContext
        // / __swift_FORCE_LOAD_$_swiftCompatibility56.
        "-framework", "JavaScriptCore",
        "-L$swiftCompatLibDir",
    )
}

// Expose the per-target opts to consuming projects (keyed by Kotlin target name) so a consumer
// can apply them to its own test executables without knowing where AdMob's frameworks live.
extensions.extraProperties["admobCmpTestLinkerOpts"] =
    { targetName: String -> admobTestLinkerOpts(targetName) }

kotlin {
    explicitApi()

    // After any public API change run :admob-cmp:updateKotlinAbi and commit
    // api/admob-cmp.klib.api, or the build fails.
    //
    // `enabled` must be set explicitly: declaring an empty abiValidation {} block
    // does NOT switch validation on (it defaults to false), which left both
    // updateKotlinAbi and checkKotlinAbi silently SKIPPED — the dump went stale
    // and no API break was ever caught.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    android {
        namespace = "avinya.tech.yt.ads"
        compileSdk = admobCompileSdk
        minSdk = admobMinSdk
        withHostTest {
            // Android's stub android.jar throws RuntimeException on any unmocked call by
            // default (e.g. android.util.Log.d) — this JVM-only host-test compilation has
            // no Robolectric to back the framework classes. This is the newer
            // androidLibrary-style KMP DSL's equivalent of classic AGP's
            // `testOptions.unitTests.isReturnDefaultValues = true`: it swaps in a mockable
            // android.jar that returns defaults (false/0/null/no-op) instead of throwing,
            // so plain framework calls like Log.d() no-op rather than crash. It does not
            // change how Mockito-mocked calls behave, so `mockito-core`'s
            // `mockStatic(Log::class.java)` usages in androidHostTest keep working as-is.
            isReturnDefaultValues = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        val targetName = iosTarget.name

        iosTarget.binaries.framework {
            baseName = "AdMobCmp"
            isStatic = true
            freeCompilerArgs += listOf("-Xbinary=bundleId=avinya.tech.yt.ads")
        }

        iosTarget.compilations.getByName("main").cinterops {
            val gma by creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/GoogleMobileAds.def"))
                val fDir = frameworkDir("GoogleMobileAds", targetName)
                compilerOpts("-F", fDir.parentFile.absolutePath)
            }
            val ump by creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/UserMessagingPlatform.def"))
                val fDir = frameworkDir("UserMessagingPlatform", targetName)
                compilerOpts("-F", fDir.parentFile.absolutePath)
            }
        }

        // Wire download tasks to cinterop tasks
        project.tasks.matching { task ->
            task.name.startsWith("cinterop") && task.name.contains(targetName.replaceFirstChar { it.uppercase() })
        }.configureEach {
            dependsOn(downloadGmaIos, downloadUmpIos)
        }

        // Test link tasks also need frameworks downloaded
        project.tasks.matching { task ->
            task.name.startsWith("link") && task.name.contains(targetName.replaceFirstChar { it.uppercase() }) && task.name.contains("Test")
        }.configureEach {
            dependsOn(downloadGmaIos, downloadUmpIos)
        }

        // Test executables perform a real link, so unlike the framework they must
        // resolve GAD*/UMP* symbols against the downloaded slices. Uses the same shared
        // opts that consumers read via the `admobCmpTestLinkerOpts` extension.
        iosTarget.binaries.withType(org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable::class.java).configureEach {
            linkerOpts(admobTestLinkerOpts(targetName))
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The `compose.runtime/foundation/ui` accessors were deprecated; depend
            // on the Compose Multiplatform artifacts directly via the catalog.
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
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.google.ads.mobile.sdk)
            implementation(libs.google.user.messaging.platform)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // Platform-layer tests. The shared state machine is covered by commonTest, but
        // the Android mappers/pool/controllers had no coverage at all, which is where
        // the GMA-facing contracts (e.g. LoadAdError.code being an enum, not an int)
        // are easiest to break silently on an SDK bump.
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.google.ads.mobile.sdk)
            implementation("org.mockito:mockito-core:5.15.2")
        }
    }
}

abstract class DoctorIosTask : DefaultTask() {
    @get:Input
    abstract val xcodeprojPath: Property<String>

    @get:Input
    abstract val frameworksDir: Property<String>

    @get:Input
    abstract val rootDirPath: Property<String>

    @get:Input
    abstract val gmaVersion: Property<String>

    @TaskAction
    fun doctor() {
        // Report-only: the #1 integration failure of the bindings-only model is a
        // forgotten SPM link, surfacing as "Undefined symbol: _OBJC_CLASS_$_GADMobileAds"
        // at app link time. This task diagnoses, it never fails the build.
        val ok = "\u2705"
        val bad = "\u274C"
        val skip = "\u26A0\uFE0F"

        // 1. Binding inputs: the downloaded GMA/UMP XCFrameworks.
        val fwDir = File(frameworksDir.get())
        for (name in listOf("GoogleMobileAds", "UserMessagingPlatform")) {
            val slice = File(fwDir, "$name.xcframework/ios-arm64")
            if (slice.exists()) {
                logger.lifecycle("$ok $name.xcframework download cache present")
            } else {
                logger.lifecycle("$bad $name.xcframework cache missing — run ./gradlew :admob-cmp:downloadGmaIos :admob-cmp:downloadUmpIos")
            }
        }

        // 2. Consumer Xcode project links the SPM products.
        val projDir = File(rootDirPath.get(), xcodeprojPath.get())
        val pbxproj = projDir.walkTopDown().maxDepth(2)
            .firstOrNull { it.name == "project.pbxproj" }
        if (pbxproj == null) {
            logger.lifecycle("$skip skipped SPM check: no project.pbxproj under $projDir (override with -PadmobCmp.xcodeproj=<dir>)")
        } else {
            val content = pbxproj.readText()
            val packages = mapOf(
                "GoogleMobileAds" to "https://github.com/googleads/swift-package-manager-google-mobile-ads.git (from: ${gmaVersion.get()})",
                "GoogleUserMessagingPlatform" to "https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git"
            )
            packages.forEach { (product, url) ->
                if (content.contains(product)) {
                    logger.lifecycle("$ok Xcode project links SPM product '$product'")
                } else {
                    logger.lifecycle("$bad SPM product '$product' not referenced in ${pbxproj.parentFile.name} — add the package: $url")
                    logger.lifecycle("   Without it the app fails to link with: Undefined symbol: _OBJC_CLASS_\$_GADMobileAds")
                }
            }
        }

        // 3. Info.plist requirements.
        val plist = projDir.parentFile?.let { base ->
            base.walkTopDown().maxDepth(3)
                .firstOrNull { it.name == "Info.plist" && !it.path.contains("Tests") }
        }
        if (plist == null) {
            logger.lifecycle("$skip skipped Info.plist check: none found near $projDir")
        } else {
            val content = plist.readText()
            if (content.contains("GADApplicationIdentifier")) {
                logger.lifecycle("$ok Info.plist declares GADApplicationIdentifier")
                if (content.contains("ca-app-pub-3940256099942544~")) {
                    logger.lifecycle("$skip   ...but it is still the Google sample app id — replace before release")
                }
            } else {
                logger.lifecycle("$bad Info.plist is missing GADApplicationIdentifier — GMA crashes at startup without it")
            }
            if (content.contains("SKAdNetworkItems")) {
                logger.lifecycle("$ok Info.plist declares SKAdNetworkItems")
            } else {
                logger.lifecycle("$skip Info.plist has no SKAdNetworkItems — attribution will suffer; copy the list from the AdMob iOS docs")
            }
        }

        logger.lifecycle("doctorIos is diagnostic only; it never fails the build.")
    }

}

tasks.register("doctorIos", DoctorIosTask::class) {
    description = "Diagnose iOS consumer integration (SPM products, Info.plist, framework cache). Report-only."
    group = "ios-setup"
    xcodeprojPath.set(providers.gradleProperty("admobCmp.xcodeproj").orElse("iosApp"))
    frameworksDir.set(iosFrameworksDir.absolutePath)
    rootDirPath.set(rootDir.absolutePath)
    gmaVersion.set(gmaIosVersion)
}
