package avinya.tech.yt.ads.debug

import androidx.compose.runtime.Immutable
import avinya.tech.yt.ads.AdFormat
import avinya.tech.yt.ads.AdPlacement
import avinya.tech.yt.ads.AdSizePolicy
import avinya.tech.yt.ads.AdUnitIds
import avinya.tech.yt.ads.BannerRefreshPolicy
import avinya.tech.yt.ads.CollapsiblePlacement
import avinya.tech.yt.ads.TestAdIds
import avinya.tech.yt.ads.nativead.layout.AdLayout
import avinya.tech.yt.ads.nativead.layout.AdTemplates
import kotlin.time.Duration.Companion.seconds

/**
 * The placement set a debug ad screen drives, plus the native layouts its gallery renders.
 *
 * Defaults to [Test] — Google's test ad units with [AdPlacement.strictTestMode] on. Supply
 * your own to debug your own inventory and mediation waterfall; the screen applies no extra
 * safety of its own, so a catalog built from production ad unit ids will request production
 * ads.
 */
@Immutable
public class AdDebugCatalog(
    public val banner: AdPlacement,
    public val collapsibleBanner: AdPlacement,
    public val native: AdPlacement,
    public val interstitial: AdPlacement,
    public val rewarded: AdPlacement,
    public val rewardedInterstitial: AdPlacement,
    public val appOpen: AdPlacement,
    public val layouts: List<AdLayout> =
        listOf(AdTemplates.compact, AdTemplates.medium, AdTemplates.feedCard),
) {
    public companion object {
        /** Google test ad units. Every placement sets `strictTestMode = true`. */
        public val Test: AdDebugCatalog = AdDebugCatalog(
            banner = AdPlacement(
                id = "debug_banner",
                format = AdFormat.Banner,
                androidAdUnitId = TestAdIds.ANDROID_BANNER,
                iosAdUnitId = TestAdIds.IOS_BANNER,
                strictTestMode = true,
            ),
            // Regular banner test ids never serve collapsible fill — this needs the
            // dedicated collapsible test units.
            collapsibleBanner = AdPlacement(
                id = "debug_banner_collapsible",
                format = AdFormat.Banner,
                adUnitIds = AdUnitIds(
                    android = TestAdIds.ANDROID_COLLAPSIBLE_BANNER,
                    ios = TestAdIds.IOS_COLLAPSIBLE_BANNER,
                ),
                bannerSizePolicy = AdSizePolicy.AnchoredAdaptive(
                    collapsible = CollapsiblePlacement.Bottom,
                ),
                bannerRefreshPolicy = BannerRefreshPolicy.SdkManaged(30.seconds),
                strictTestMode = true,
            ),
            native = AdPlacement(
                id = "debug_native",
                format = AdFormat.Native,
                androidAdUnitId = TestAdIds.ANDROID_NATIVE,
                iosAdUnitId = TestAdIds.IOS_NATIVE,
                maxCacheSize = 2,
                strictTestMode = true,
            ),
            interstitial = AdPlacement(
                id = "debug_interstitial",
                format = AdFormat.Interstitial,
                androidAdUnitId = TestAdIds.ANDROID_INTERSTITIAL,
                iosAdUnitId = TestAdIds.IOS_INTERSTITIAL,
                strictTestMode = true,
            ),
            rewarded = AdPlacement(
                id = "debug_rewarded",
                format = AdFormat.Rewarded,
                androidAdUnitId = TestAdIds.ANDROID_REWARDED,
                iosAdUnitId = TestAdIds.IOS_REWARDED,
                strictTestMode = true,
            ),
            rewardedInterstitial = AdPlacement(
                id = "debug_rewarded_interstitial",
                format = AdFormat.RewardedInterstitial,
                androidAdUnitId = TestAdIds.ANDROID_REWARDED_INTERSTITIAL,
                iosAdUnitId = TestAdIds.IOS_REWARDED_INTERSTITIAL,
                strictTestMode = true,
            ),
            appOpen = AdPlacement(
                id = "debug_app_open",
                format = AdFormat.AppOpen,
                androidAdUnitId = TestAdIds.ANDROID_APP_OPEN,
                iosAdUnitId = TestAdIds.IOS_APP_OPEN,
                strictTestMode = true,
            ),
        )
    }
}

/** The four full-screen ad formats. */
internal val AdDebugCatalog.fullScreenPlacements: List<AdPlacement>
    get() = listOf(interstitial, rewarded, rewardedInterstitial, appOpen)
