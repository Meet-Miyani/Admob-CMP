package avinya.tech.yt.ads

/**
 * Supported mobile ad platforms. Used to resolve platform-specific IDs
 * and behavior.
 */
public enum class AdPlatform { Android, Ios }

/**
 * Supported ad formats in the SDK. Each format has a corresponding
 * controller type on [AdManager].
 */
public enum class AdFormat {
    /** Banner ad (anchored adaptive, inline, or fixed size). */
    Banner,
    /** Interstitial full-screen ad. */
    Interstitial,
    /** Native ad (rendered with a custom [AdLayout]). */
    Native,
    /** Rewarded video ad. */
    Rewarded,
    /** Rewarded interstitial ad. */
    RewardedInterstitial,
    /** App-open ad shown on app foreground. */
    AppOpen
}
