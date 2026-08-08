package dev.avinya.admob.showcase.domain.ad

import dev.avinya.ads.AdShowResult

/**
 * Whether a presentation attempt may advance the interstitial cooldown.
 *
 * Only [AdShowResult.Shown] counts. A `NotReady` or `Failed` ad never
 * appeared, so charging the user 60 seconds of suppression for it is a
 * user-facing bug — one this showcase shipped once already.
 *
 * Pure so the rule cannot drift without a test failing.
 */
fun advancesCooldown(result: AdShowResult): Boolean = result is AdShowResult.Shown
