package dev.avinya.ads.gradle

import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

private const val GMA_DOWNLOAD_BASE = "https://dl.google.com/googleadmobadssdk"
private val SUPPORTED_TARGETS = setOf("iosArm64", "iosSimulatorArm64")

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
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val frameworksDir = target.layout.buildDirectory.dir("admob-cmp-ios-frameworks")

            val downloadGma = target.tasks.register("downloadGmaIos", DownloadIosFramework::class.java) {
                description = "Download the Google Mobile Ads iOS XCFramework (test linking only)"
                group = "ios-setup"
                baseUrl.set(GMA_DOWNLOAD_BASE)
                version.set(AdMobCmpNativeDeps.gmaIosVersion)
                expectedSha256.set(AdMobCmpNativeDeps.gmaIosSha256)
                markerFile.set(frameworksDir.map { d ->
                    d.file("GoogleMobileAds.xcframework/.gma_downloaded")
                })
            }
            val downloadUmp = target.tasks.register("downloadUmpIos", DownloadIosFramework::class.java) {
                description = "Download the User Messaging Platform iOS XCFramework (test linking only)"
                group = "ios-setup"
                baseUrl.set(GMA_DOWNLOAD_BASE)
                version.set(AdMobCmpNativeDeps.gmaUmpIosVersion)
                expectedSha256.set(AdMobCmpNativeDeps.umpIosSha256)
                markerFile.set(frameworksDir.map { d ->
                    d.file("UserMessagingPlatform.xcframework/.ump_downloaded")
                })
            }

            val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach {
                val targetName = name
                if (targetName !in SUPPORTED_TARGETS) return@configureEach

                // Test executables perform a real link with no Xcode involved, so they must
                // resolve GAD*/UMP* themselves. Frameworks are NOT touched: Kotlin/Native
                // leaves those symbols undefined there on purpose, for Xcode to bind via SPM.
                binaries.withType(TestExecutable::class.java).configureEach {
                    linkerOpts(testLinkerOpts(target, targetName, frameworksDir.get().asFile))
                }
            }

            target.tasks.matching { task ->
                task.name.startsWith("link") &&
                    task.name.contains("Test") &&
                    SUPPORTED_TARGETS.any { targetName -> task.name.contains(targetName.replaceFirstChar(Char::uppercase)) }
            }.configureEach {
                dependsOn(downloadGma, downloadUmp)
            }

            target.tasks.register("doctorIos", DoctorIosTask::class.java) {
                description = "Diagnose iOS integration (SPM products, Info.plist, framework cache). Report-only."
                group = "ios-setup"
                this.frameworksDir.set(frameworksDir.get().asFile.absolutePath)
                gmaVersion.set(AdMobCmpNativeDeps.gmaIosVersion)
                xcodeprojPath.set(
                    target.providers.gradleProperty("admobCmp.xcodeproj").orElse("iosApp")
                )
                rootDirPath.set(target.rootDir.absolutePath)
            }
        }
    }
}

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
        commandLine("xcode-select", "-p")
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
