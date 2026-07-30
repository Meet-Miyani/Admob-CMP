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
