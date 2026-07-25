package dev.avinya.ads.nativead.layout

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Ready-made, policy-compliant native ad layout templates. Each template
 * includes the required ad attribution badge and AdChoices space.
 */
public object AdTemplates {
    /** Media card template. Alias for [medium]. */
    public val mediaCard: AdLayout get() = medium
    /** Compact horizontal layout with icon, headline, body, and CTA. Best for tight spaces. */
    public val compact: AdLayout = adLayout {
        row(
            modifier = AdModifier.fillMaxWidth().padding(12.dp).background(Color(0xFFFFFFFF)).cornerRadius(12.dp),
            spacing = 12.dp
        ) {
            icon(AdModifier.size(48.dp).cornerRadius(8.dp))
            column(modifier = AdModifier.weight(1f), spacing = 4.dp) {
                row(spacing = 6.dp) {
                    adBadge()
                    headline(modifier = AdModifier.weight(1f))
                }
                body(maxLines = 2)
                advertiser()
            }
            callToAction()
            adChoices(AdModifier.size(20.dp))
        }
    }

    /** Medium card template with media, icon, headline, body, and CTA. Good for feeds. */
    public val medium: AdLayout = adLayout {
        column(
            modifier = AdModifier.fillMaxWidth().padding(12.dp).background(Color(0xFFFFFFFF)).cornerRadius(14.dp),
            spacing = 10.dp
        ) {
            box(modifier = AdModifier.fillMaxWidth()) {
                media()
                row(
                    modifier = AdModifier.fillMaxWidth().padding(8.dp),
                ) {
                    adBadge()
                    spacer(AdModifier.weight(1f))
                    adChoices(
                        modifier = AdModifier.size(24.dp),
                        visibilityPolicy = AdVisibilityPolicy.KeepSpace
                    )
                }
            }
            row(spacing = 10.dp) {
                icon(AdModifier.size(44.dp).cornerRadius(8.dp))
                column(modifier = AdModifier.weight(1f), spacing = 4.dp) {
                    row(spacing = 6.dp) {
                        headline(modifier = AdModifier.weight(1f), maxLines = 2)
                    }
                    body(maxLines = 2)
                    row(spacing = 8.dp) {
                        advertiser()
                        starRating()
                    }
                }
            }
            callToAction(modifier = AdModifier.fillMaxWidth())
        }
    }

    /** Full-width feed card with media, rich metadata, and CTA. Best for content feeds. */
    public val feedCard: AdLayout = adLayout {
        column(
            modifier = AdModifier.fillMaxWidth().padding(16.dp).background(Color(0xFFFFFFFF)).cornerRadius(18.dp),
            spacing = 12.dp
        ) {
            box(modifier = AdModifier.fillMaxWidth()) {
                media()
                row(
                    modifier = AdModifier.fillMaxWidth().padding(8.dp),
                ) {
                    adBadge()
                    spacer(AdModifier.weight(1f))
                    adChoices(AdModifier.size(24.dp))
                }
            }
            row(spacing = 12.dp) {
                icon(AdModifier.size(56.dp).cornerRadius(12.dp))
                column(modifier = AdModifier.weight(1f), spacing = 5.dp) {
                    row(spacing = 8.dp) {
                        headline(modifier = AdModifier.weight(1f), maxLines = 2)
                    }
                    body(maxLines = 3)
                    row(spacing = 8.dp) {
                        advertiser()
                        store()
                        price()
                        starRating()
                    }
                }
            }
            callToAction(modifier = AdModifier.fillMaxWidth())
        }
    }
}
