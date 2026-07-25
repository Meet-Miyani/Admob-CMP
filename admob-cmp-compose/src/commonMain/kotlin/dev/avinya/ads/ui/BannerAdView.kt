package dev.avinya.ads.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdPlacement

/**
 * Composable that renders a banner ad for the given [placement]. Self-loads
 * when the [AdManager] is [AdManagerStatus.Ready] (unless the placement uses
 * [BannerRefreshPolicy.Manual]). Sizes adaptive banners to the measured
 * container width.
 *
 * @param placement The banner placement configuration.
 * @param modifier Modifier for the banner container.
 * @param widthDp Override the measured width for adaptive banner sizing.
 *   Uses the measured container width when null.
 * @param onEvent Callback for banner ad lifecycle events.
 */
@Composable
public expect fun BannerAdView(
    placement: AdPlacement,
    modifier: Modifier = Modifier,
    widthDp: Int? = null,
    onEvent: (AdEvent) -> Unit = {}
)
