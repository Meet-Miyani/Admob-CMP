package avinya.tech.yt.ads.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import avinya.tech.yt.ads.AdEvent
import avinya.tech.yt.ads.AdPlacement
import avinya.tech.yt.ads.nativead.layout.AdLayout
import avinya.tech.yt.ads.nativead.layout.AdTemplates

/**
 * Composable that renders a native ad from the pool for the given
 * [placement]. Acquires a [NativeAdToken] from the pool and releases it
 * on dispose automatically. Each [itemKey] gets its own distinct ad,
 * enabling stable list reuse.
 *
 * @param placement The native ad placement configuration.
 * @param itemKey Stable key for list reuse — distinct ads per unique key.
 * @param layout The native ad layout (defaults to [AdTemplates.mediaCard]).
 * @param modifier Modifier for the native ad container.
 * @param onEvent Callback for native ad lifecycle events.
 */
@Composable
public expect fun NativeAdView(
    placement: AdPlacement,
    itemKey: String,
    layout: AdLayout = AdTemplates.mediaCard,
    modifier: Modifier = Modifier,
    onEvent: (AdEvent) -> Unit = {}
)
