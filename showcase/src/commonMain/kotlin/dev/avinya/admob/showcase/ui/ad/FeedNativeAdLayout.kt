package dev.avinya.admob.showcase.ui.ad

import androidx.compose.ui.unit.dp
import dev.avinya.ads.nativead.layout.AdLayout
import dev.avinya.ads.nativead.layout.AdModifier
import dev.avinya.ads.nativead.layout.adLayout

/**
 * Card-shaped native ad for the feed: media-led, sized to sit among articles.
 *
 * `adBadge()` is policy-required — the SDK's validator warns without it, and
 * shipping an unlabelled native ad is a policy violation, not a style choice.
 */
val feedNativeAdLayout: AdLayout = adLayout {
    column(modifier = AdModifier.fillMaxWidth()) {
        row(spacing = 8.dp) {
            icon(modifier = AdModifier.size(40.dp))
            column {
                headline(maxLines = 2)
                advertiser()
            }
            adBadge()
        }
        media(modifier = AdModifier.fillMaxWidth().aspectRatio(16f / 9f))
        body(maxLines = 3)
        callToAction(modifier = AdModifier.fillMaxWidth())
    }
}
