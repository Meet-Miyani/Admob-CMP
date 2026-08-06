package dev.avinya.admob.showcase.ui.ad

import androidx.compose.ui.unit.dp
import dev.avinya.ads.nativead.layout.AdLayout
import dev.avinya.ads.nativead.layout.AdLayoutValidator
import dev.avinya.ads.nativead.layout.AdModifier
import dev.avinya.ads.nativead.layout.adLayout

/**
 * Horizontal-band native ad that reads as part of the article body rather
 * than a card on its own. No large media — just an icon, a short headline,
 * a 2-line body, and a full-width call to action.
 *
 * `adBadge()` is policy-required — the SDK's validator warns without it, and
 * shipping an unlabelled native ad is a policy violation, not a style choice.
 * It sits in the top row so the validator can confirm the badge is at the top
 * of the ad, not buried beneath the icon.
 */
val inlineNativeAdLayout: AdLayout = adLayout {
    column(modifier = AdModifier.fillMaxWidth()) {
        row(spacing = 4.dp) { adBadge(); advertiser() }
        row(spacing = 12.dp) {
            icon(modifier = AdModifier.size(56.dp))
            column {
                headline(maxLines = 2)
                body(maxLines = 2)
            }
        }
        callToAction(modifier = AdModifier.fillMaxWidth())
    }
}

// Surface validation findings at module load so a bad layout fails to render
// visibly, not silently. Warnings are non-fatal but logged for the same
// reason FeedNativeAdLayout mentions: missing the ad badge is policy-grade.
private object InlineNativeAdLayoutValidation {
    init {
        AdLayoutValidator.validate(inlineNativeAdLayout.root)
            .takeIf { it.warnings.isNotEmpty() }
            ?.let { report ->
                println(
                    "inlineNativeAdLayout validation warnings: " +
                        report.warnings.joinToString { "${it.code}@${it.nodePath}: ${it.message}" }
                )
            }
    }
}
