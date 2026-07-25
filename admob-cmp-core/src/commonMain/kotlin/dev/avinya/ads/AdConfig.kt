package dev.avinya.ads


/**
 * Per-platform AdMob **app** IDs (not ad-unit IDs). Required for
 * [AdConfig] initialization. Obtain your app IDs from the AdMob dashboard.
 */
public data class AdAppIds(
    /** Android app ID (ca-app-pub-...). */
    val android: String,
    /** iOS app ID (ca-app-pub-...). */
    val ios: String
)

/**
 * Top-level SDK configuration for the Google Mobile Ads SDK. Passed to
 * [AdManager.initialize]. The secondary constructor provides a convenient
 * surface for the most common fields.
 */
public data class AdConfig(
    /** Per-platform AdMob app IDs. */
    val appIds: AdAppIds,
    /** Global request configuration applied to all ad requests. */
    val globalRequestConfiguration: GlobalRequestConfiguration = GlobalRequestConfiguration(),
    /** Debug and test-mode options. */
    val debugOptions: AdDebugOptions = AdDebugOptions(),
    /**
     * UMP-only tag indicating whether the user is under the age of consent.
     *
     * This controls consent-form behavior and is intentionally independent from
     * [GlobalRequestConfiguration.ageRestrictedTreatment], which controls GMA ad requests.
     */
    val consentTagForUnderAgeOfConsent: Boolean = false,
    /** Hooks invoked during initialization phases. */
    val initializationHooks: List<AdInitializationHook> = emptyList()
) {
    /**
     * Convenience secondary constructor that accepts platform app IDs directly
     * and derives [AdAppIds], [GlobalRequestConfiguration], and [AdDebugOptions].
     *
     * @param androidAppId Android AdMob app ID.
     * @param iosAppId iOS AdMob app ID.
     * @param testMode Enables UMP debug consent settings. Does **not** force GMA to serve
     *   test ads on its own — see [AdDebugOptions.testMode] and [testDeviceIds].
     * @param testDeviceIds Device IDs registered as GMA test devices; this is what actually
     *   forces test ads for a physical device (emulators/simulators qualify automatically).
     * @param debugGeography Force UMP debug geography.
     * @param ageRestrictedTreatment Age treatment applied to GMA ad requests.
     * @param consentTagForUnderAgeOfConsent UMP-only under-age-of-consent tag.
     * @param initializationHooks Hooks invoked during initialization phases.
     */
    public constructor(
        androidAppId: String,
        iosAppId: String,
        testMode: Boolean = false,
        testDeviceIds: List<String> = emptyList(),
        debugGeography: ConsentDebugGeography = ConsentDebugGeography.Disabled,
        ageRestrictedTreatment: AgeRestrictedTreatment = AgeRestrictedTreatment.Unspecified,
        consentTagForUnderAgeOfConsent: Boolean = false,
        initializationHooks: List<AdInitializationHook> = emptyList()
    ) : this(
        appIds = AdAppIds(androidAppId, iosAppId),
        globalRequestConfiguration = GlobalRequestConfiguration(
            testDeviceIds = testDeviceIds,
            ageRestrictedTreatment = ageRestrictedTreatment,
        ),
        debugOptions = AdDebugOptions(testMode = testMode, consentDebugGeography = debugGeography),
        consentTagForUnderAgeOfConsent = consentTagForUnderAgeOfConsent,
        initializationHooks = initializationHooks
    )

    /** Convenience accessor for [AdAppIds.android]. */
    public val androidAppId: String get() = appIds.android
    /** Convenience accessor for [AdAppIds.ios]. */
    public val iosAppId: String get() = appIds.ios
    /** Convenience accessor for [AdDebugOptions.testMode]. */
    public val testMode: Boolean get() = debugOptions.testMode
    /** Convenience accessor for [GlobalRequestConfiguration.testDeviceIds]. */
    public val testDeviceIds: List<String> get() = globalRequestConfiguration.testDeviceIds
    /** Convenience accessor for [AdDebugOptions.consentDebugGeography]. */
    public val debugGeography: ConsentDebugGeography get() = debugOptions.consentDebugGeography
}

/**
 * Phases of the SDK initialization sequence during which
 * [AdInitializationHook.onPhase] is called.
 */
public enum class AdInitializationPhase {
    /** Before the UMP consent request. */
    BeforeConsentRequest,
    /** Before `MobileAds.initialize` (Android) / `GADMobileAds.sharedInstance.start` (iOS). */
    BeforeMobileAdsInitialize,
    /** After the GMA SDK initialization completes. */
    AfterMobileAdsInitialize
}

/**
 * Hook invoked at each phase of the SDK initialization lifecycle.
 * Implementations can perform side effects (e.g., server-side config fetch,
 * GDPR consent platform integration) at specific [AdInitializationPhase]s.
 */
public interface AdInitializationHook {
    /** Called during the given [phase] with the [config] used for initialization. */
    public suspend fun onPhase(phase: AdInitializationPhase, config: AdConfig)
}

internal suspend fun AdConfig.dispatchInitializationHooks(phase: AdInitializationPhase) {
    initializationHooks.forEach { hook -> hook.onPhase(phase, this) }
}

/**
 * `testMode` alone cannot force GMA to serve test ads on a physical device: GMA
 * requires that device's specific hashed test-device ID (only emulators and
 * simulators qualify automatically). If `testMode` is true and no test device IDs
 * are configured anywhere, `testMode` is a silent no-op for ad serving — this
 * returns the warning to log in that case, or null when there's nothing to warn
 * about.
 */
internal fun AdConfig.testModeWarningOrNull(): String? {
    if (!testMode) return null
    val hasTestDeviceIds = testDeviceIds.isNotEmpty() || debugOptions.consentTestDeviceIds.isNotEmpty()
    if (hasTestDeviceIds) return null
    return "AdConfig.testMode is true but no test device IDs are configured. testMode alone " +
        "does NOT force GMA to serve test ads on a physical device — only emulators/simulators " +
        "qualify automatically. Populate GlobalRequestConfiguration.testDeviceIds with this " +
        "device's hashed test-device ID (printed to logcat/console on an unregistered ad " +
        "request), or use TestAdIds for a ready-made safe configuration. Without this, " +
        "requests may serve live ads."
}

/**
 * Returns the single request configuration applied to GMA on every platform.
 *
 * UMP configuration is deliberately excluded: [AdConfig.consentTagForUnderAgeOfConsent]
 * controls consent gathering and must not implicitly alter GMA request treatment.
 */
internal fun AdConfig.effectiveGlobalRequestConfiguration(): GlobalRequestConfiguration =
    globalRequestConfiguration

/**
 * Identity of the process-wide GMA configuration that cannot be replaced after
 * the platform singleton has initialized. Consent mode and hooks are deliberately
 * excluded: they control how initialization is reached, not the resulting GMA
 * singleton configuration.
 */
internal data class AdInitializationConfigIdentity(
    val platformAppId: String,
    val globalRequestConfiguration: GlobalRequestConfiguration
)

internal fun AdConfig.initializationIdentity(platformAppId: String): AdInitializationConfigIdentity =
    AdInitializationConfigIdentity(
        platformAppId = platformAppId,
        globalRequestConfiguration = effectiveGlobalRequestConfiguration()
    )

/**
 * Global request configuration applied to every ad request made through
 * the SDK. Fields mirror the GMA SDK request configuration.
 */
public data class GlobalRequestConfiguration(
    /** Test device IDs that receive test ads. */
    val testDeviceIds: List<String> = emptyList(),
    /** Maximum ad content rating filter. */
    val maxAdContentRating: MaxAdContentRating = MaxAdContentRating.Unspecified,
    /** Age treatment applied to all GMA ad requests. */
    val ageRestrictedTreatment: AgeRestrictedTreatment = AgeRestrictedTreatment.Unspecified,
    /** Publisher privacy personalization state. */
    val publisherPrivacyPersonalizationState: PublisherPrivacyPersonalizationState = PublisherPrivacyPersonalizationState.Default,
    /** Enable publisher first-party ID. Null = unspecified. */
    val publisherFirstPartyIdEnabled: Boolean? = null,
    /** Global ad audio muting. Null = unspecified. */
    val appMuted: Boolean? = null,
    /** Global ad audio volume (0.0–1.0). Null = use system volume. */
    val appVolume: Float? = null
)

/** Age treatment applied to Google Mobile Ads requests. */
public enum class AgeRestrictedTreatment {
    /** No specific age-restricted treatment signal. */
    Unspecified,
    /** Apply child-directed treatment. */
    Child,
    /** Apply teen-directed treatment. */
    Teen,
}

/**
 * Maximum ad content rating filter. Corresponds to GMA
 * [MaxAdContentRating](https://developers.google.com/admob/android/targeting#ad_content_filtering).
 */
public enum class MaxAdContentRating { Unspecified, General, ParentalGuidance, Teen, MatureAudience }

/**
 * Publisher privacy personalization state. Controls whether ad personalization
 * is enabled, disabled, or follows the default (user consent) setting.
 */
public enum class PublisherPrivacyPersonalizationState { Default, Enabled, Disabled }

/**
 * Debug and test options for the SDK. [testMode] does **not** force GMA to serve
 * test ads by itself — it only enables UMP's debug consent-gathering settings.
 * Google's SDK determines whether a request is a test request from the device's
 * registration in [GlobalRequestConfiguration.testDeviceIds] (Android/iOS
 * emulators and simulators qualify automatically; physical devices require the
 * device's hashed test-device ID, printed to logcat/console on an unregistered
 * request). If [testMode] is true and no test device IDs are configured, the SDK
 * logs a warning at initialization rather than silently doing nothing — see
 * [dev.avinya.ads.TestAdIds] for a ready-made safe configuration.
 */
public data class AdDebugOptions(
    /**
     * Enables UMP debug consent settings (requires [consentTestDeviceIds] or
     * [consentDebugGeography] to actually change behavior). Does **not** force
     * GMA to serve test ads — populate [GlobalRequestConfiguration.testDeviceIds]
     * for that. Default false — only enable for development/testing.
     */
    val testMode: Boolean = false,
    /** Force UMP debug geography for testing consent flows. */
    val consentDebugGeography: ConsentDebugGeography = ConsentDebugGeography.Disabled,
    /** Test device IDs for UMP consent form testing. */
    val consentTestDeviceIds: List<String> = emptyList(),
    /** Show Ad Inspector entry points in the ad UI. */
    val enableAdInspectorEntryPoints: Boolean = true
)

/**
 * UMP debug geography for testing consent flows. Forces the SDK to
 * behave as if the device is in a specific region for consent purposes.
 * [Disabled] uses real geography; [Eea] forces EEA behavior; [NotEea]
 * forces non-EEA behavior. Never use in production builds.
 */
public enum class ConsentDebugGeography { Disabled, Eea, NotEea }
