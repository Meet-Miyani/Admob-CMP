package dev.avinya.admob.showcase.domain.ad

import dev.avinya.ads.AdCachePolicy
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdSizePolicy
import dev.avinya.ads.AdUnitIds
import dev.avinya.ads.BannerRefreshPolicy
import dev.avinya.ads.TestAdIds
import kotlin.time.Duration.Companion.seconds

/**
 * Every placement the showcase uses — a **static, finite** catalog.
 *
 * Controllers are cached per `AdPlacement.id` for the manager's lifetime and
 * are never evicted, so generated per-item ids leak permanently. The feed
 * serves per-item ads from the native pool keyed by `itemKey`, rather than
 * minting a placement per row.
 *
 * `strictTestMode = true` throws at construction if any of these ever points
 * at a production ad unit.
 */
object ShowcasePlacements {

    val feedBanner: AdPlacement = AdPlacement(
        id = "feed_banner",
        format = AdFormat.Banner,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_BANNER, ios = TestAdIds.IOS_BANNER),
        bannerSizePolicy = AdSizePolicy.LargeAnchoredAdaptive(),
        bannerRefreshPolicy = BannerRefreshPolicy.SdkManaged(60.seconds),
        strictTestMode = true,
    )

    val feedNative: AdPlacement = AdPlacement(
        id = "feed_native",
        format = AdFormat.Native,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_NATIVE, ios = TestAdIds.IOS_NATIVE),
        // maxSize budgets available + in-use ads together. Five covers the rows
        // visible at once plus prefetch; too low and acquire() returns null for
        // every row beyond the budget.
        cachePolicy = AdCachePolicy(maxSize = 5, reloadAfterShow = true),
        strictTestMode = true,
    )

    // Phases 4-6 add articleNative, articleBanner, articleInterstitial,
    // storeRewarded, storeRewardedInterstitial and appOpen here, so the whole
    // catalog stays in one readable file.
}
