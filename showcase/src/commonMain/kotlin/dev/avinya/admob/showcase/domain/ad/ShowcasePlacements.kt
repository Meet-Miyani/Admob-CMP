package dev.avinya.admob.showcase.domain.ad

import dev.avinya.ads.AdCachePolicy
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdSizePolicy
import dev.avinya.ads.AdUnitIds
import dev.avinya.ads.BannerRefreshPolicy
import dev.avinya.ads.CollapsiblePlacement
import dev.avinya.ads.FullScreenAdOptions
import dev.avinya.ads.ServerSideVerificationOptions
import dev.avinya.ads.TestAdIds
import dev.avinya.ads.nativead.NativeAdBatching
import dev.avinya.ads.nativead.NativeAdOptions
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
        cachePolicy = AdCachePolicy(maxSize = 5, reloadAfterShow = true),
        nativeOptions = NativeAdOptions(batching = NativeAdBatching.GoogleOnly),
        strictTestMode = true,
    )

    val articleNative: AdPlacement = AdPlacement(
        id = "article_native",
        format = AdFormat.Native,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_NATIVE, ios = TestAdIds.IOS_NATIVE),
        cachePolicy = AdCachePolicy(maxSize = 2),
        strictTestMode = true,
    )

    val articleBanner: AdPlacement = AdPlacement(
        id = "article_banner",
        format = AdFormat.Banner,
        adUnitIds = AdUnitIds(
            android = TestAdIds.ANDROID_COLLAPSIBLE_BANNER,
            ios = TestAdIds.IOS_COLLAPSIBLE_BANNER,
        ),
        bannerSizePolicy = AdSizePolicy.LargeAnchoredAdaptive(
            collapsible = CollapsiblePlacement.Bottom,
        ),
        bannerRefreshPolicy = BannerRefreshPolicy.AdServerManaged,
        strictTestMode = true,
    )

    val articleInterstitial = AdPlacement(
        id = "article_interstitial",
        format = AdFormat.Interstitial,
        adUnitIds = AdUnitIds(
            android = TestAdIds.ANDROID_INTERSTITIAL,
            ios = TestAdIds.IOS_INTERSTITIAL,
        ),
        cachePolicy = AdCachePolicy(maxSize = 2, reloadAfterShow = true),
        strictTestMode = true,
    )

    val storeRewarded: AdPlacement = AdPlacement(
        id = "store_rewarded",
        format = AdFormat.Rewarded,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_REWARDED, ios = TestAdIds.IOS_REWARDED),
        fullScreenOptions = FullScreenAdOptions(
            // A real coin economy would verify server-side. Standing up the
            // endpoint is out of scope; setting the options shows where it goes.
            serverSideVerification = ServerSideVerificationOptions(
                userId = "showcase-demo-user",
                customData = "store_rewarded",
            ),
        ),
        strictTestMode = true,
    )

    val storeRewardedInterstitial: AdPlacement = AdPlacement(
        id = "store_rewarded_interstitial",
        format = AdFormat.RewardedInterstitial,
        adUnitIds = AdUnitIds(
            android = TestAdIds.ANDROID_REWARDED_INTERSTITIAL,
            ios = TestAdIds.IOS_REWARDED_INTERSTITIAL,
        ),
        strictTestMode = true,
    )

    val appOpen: AdPlacement = AdPlacement(
        id = "app_open",
        format = AdFormat.AppOpen,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_APP_OPEN, ios = TestAdIds.IOS_APP_OPEN),
        strictTestMode = true,
    )

    /**
     * The full catalog, in a stable order. Used by the Inspector / telemetry
     * pipeline to resolve `placementId -> AdFormat` without re-listing the
     * ids; controllers and pools are unaffected.
     */
    val allPlacements: List<AdPlacement> = listOf(
        feedBanner,
        feedNative,
        articleNative,
        articleBanner,
        articleInterstitial,
        storeRewarded,
        storeRewardedInterstitial,
        appOpen,
    )
}
