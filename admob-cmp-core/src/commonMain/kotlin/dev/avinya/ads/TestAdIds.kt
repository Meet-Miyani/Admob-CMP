package dev.avinya.ads

/**
 * Google's official sample ad-unit and app IDs for testing.
 * **Never use these constants in production.** Use them in debug builds
 * and test runs only.
 */
public object TestAdIds {
    /**
     * Google's sample-publisher prefix. Every official test ad unit and app id uses it.
     */
    public const val TEST_PUBLISHER_PREFIX: String = "ca-app-pub-3940256099942544"

    /** True when [adUnitId] is one of Google's official test ad units. */
    public fun isTestAdUnitId(adUnitId: String): Boolean =
        adUnitId.startsWith(TEST_PUBLISHER_PREFIX)
    /** Android test app ID. */
    public const val ANDROID_APP_ID: String = "ca-app-pub-3940256099942544~3347511713"
    /** iOS test app ID. */
    public const val IOS_APP_ID: String = "ca-app-pub-3940256099942544~1458002511"
    /** Android test banner ad-unit ID. */
    public const val ANDROID_BANNER: String = "ca-app-pub-3940256099942544/9214589741"
    /** iOS test banner ad-unit ID. */
    public const val IOS_BANNER: String = "ca-app-pub-3940256099942544/2435281174"

    /** Android test collapsible banner ad-unit ID. Regular banner test ids never serve collapsible fill. */
    public const val ANDROID_COLLAPSIBLE_BANNER: String = "ca-app-pub-3940256099942544/2014213617"
    /** iOS test collapsible banner ad-unit ID. */
    public const val IOS_COLLAPSIBLE_BANNER: String = "ca-app-pub-3940256099942544/8388050270"
    /** Android test interstitial ad-unit ID. */
    public const val ANDROID_INTERSTITIAL: String = "ca-app-pub-3940256099942544/1033173712"
    /** iOS test interstitial ad-unit ID. */
    public const val IOS_INTERSTITIAL: String = "ca-app-pub-3940256099942544/4411468910"
    /** Android test native ad-unit ID. */
    public const val ANDROID_NATIVE: String = "ca-app-pub-3940256099942544/2247696110"
    /** iOS test native ad-unit ID. */
    public const val IOS_NATIVE: String = "ca-app-pub-3940256099942544/3986624511"
    /** Android test rewarded ad-unit ID. */
    public const val ANDROID_REWARDED: String = "ca-app-pub-3940256099942544/5224354917"
    /** iOS test rewarded ad-unit ID. */
    public const val IOS_REWARDED: String = "ca-app-pub-3940256099942544/1712485313"
    /** Android test rewarded interstitial ad-unit ID. */
    public const val ANDROID_REWARDED_INTERSTITIAL: String = "ca-app-pub-3940256099942544/5354046379"
    /** iOS test rewarded interstitial ad-unit ID. */
    public const val IOS_REWARDED_INTERSTITIAL: String = "ca-app-pub-3940256099942544/6978759866"
    /** Android test app-open ad-unit ID. */
    public const val ANDROID_APP_OPEN: String = "ca-app-pub-3940256099942544/9257395921"
    /** iOS test app-open ad-unit ID. */
    public const val IOS_APP_OPEN: String = "ca-app-pub-3940256099942544/5575463023"
}

/**
 * Ready-made debug [AdConfig] using [TestAdIds] with test mode enabled.
 */
public val debugAdConfig: AdConfig = AdConfig(TestAdIds.ANDROID_APP_ID, TestAdIds.IOS_APP_ID, testMode = true)

/**
 * Ready-made debug [AdPlacement]s covering every [AdFormat] using
 * [TestAdIds]. Suitable for samples, QA, and testing.
 */
public val debugAdPlacements: List<AdPlacement> = listOf(
    AdPlacement(
        id = "global_banner",
        format = AdFormat.Banner,
        adUnitIds = AdUnitIds(TestAdIds.ANDROID_BANNER, TestAdIds.IOS_BANNER),
        bannerSizePolicy = AdSizePolicy.AnchoredAdaptive()
    ),
    AdPlacement("feed_native", AdFormat.Native, TestAdIds.ANDROID_NATIVE, TestAdIds.IOS_NATIVE, maxCacheSize = 5),
    AdPlacement("transition_interstitial", AdFormat.Interstitial, TestAdIds.ANDROID_INTERSTITIAL, TestAdIds.IOS_INTERSTITIAL),
    AdPlacement("rewarded_action", AdFormat.Rewarded, TestAdIds.ANDROID_REWARDED, TestAdIds.IOS_REWARDED),
    AdPlacement("rewarded_transition", AdFormat.RewardedInterstitial, TestAdIds.ANDROID_REWARDED_INTERSTITIAL, TestAdIds.IOS_REWARDED_INTERSTITIAL),
    AdPlacement("app_open", AdFormat.AppOpen, TestAdIds.ANDROID_APP_OPEN, TestAdIds.IOS_APP_OPEN)
)
