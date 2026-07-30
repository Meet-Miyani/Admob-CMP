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
