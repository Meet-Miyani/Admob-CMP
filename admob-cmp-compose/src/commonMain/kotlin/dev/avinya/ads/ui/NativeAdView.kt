package dev.avinya.ads.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.avinya.ads.AdError
import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.nativead.NativeAdSession
import dev.avinya.ads.nativead.layout.AdLayout
import dev.avinya.ads.nativead.layout.AdTemplates

/**
 * Renders one session-owned native-ad slot. The session, not composition, creates demand and
 * owns the platform ad. A platform implementation may acquire a renderer lease only when the
 * exact [slotKey]/[placement] pair is ready; empty and failed states never load from this view.
 */
@Composable
public expect fun NativeAdView(
    session: NativeAdSession,
    slotKey: String,
    placement: AdPlacement,
    layout: AdLayout = AdTemplates.mediaCard,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { NativeAdLoadingPlaceholder() },
    failure: @Composable (AdError) -> Unit = {},
    onEvent: (AdEvent) -> Unit = {},
)

/** Stable empty content used until a platform renderer is available for a session slot. */
@Composable
public fun NativeAdLoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}
