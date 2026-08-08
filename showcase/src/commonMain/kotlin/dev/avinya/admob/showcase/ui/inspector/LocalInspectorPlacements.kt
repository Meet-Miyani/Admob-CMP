package dev.avinya.admob.showcase.ui.inspector

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import dev.avinya.ads.AdPlacement

/**
 * The placements the current screen advertises to the Inspector.
 *
 * Each feature screen sets this via `CompositionLocalProvider` to a list of
 * the placements it actually hosts, so the Inspector's Placements tab shows
 * the live state of what is on screen — not the entire catalogue. The
 * [ShowcaseApp] root provides `ShowcasePlacements.allPlacements` as a
 * fallback for any screen that does not override.
 *
 * Accessing it without a provider is a programming error (no default).
 */
val LocalInspectorPlacements: ProvidableCompositionLocal<List<AdPlacement>> = compositionLocalOf {
    error("LocalInspectorPlacements accessed outside ShowcaseApp")
}

/**
 * `true` when running on the Android source set, `false` on iOS.
 *
 * The Inspector needs the platform distinction to surface the GMA Next-Gen
 * gap (no native video events on Android) — `expect`/`actual` keeps that a
 * compile-time fact, not a runtime check that could regress silently.
 */
expect val isAndroid: Boolean
