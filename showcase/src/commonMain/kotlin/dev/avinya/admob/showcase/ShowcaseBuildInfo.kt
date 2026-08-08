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
