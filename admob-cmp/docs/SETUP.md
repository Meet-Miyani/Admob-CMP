# Setup & Initialization

## 1. Gradle dependency

```toml
# libs.versions.toml
[libraries]
admob-cmp = { group = "dev.avinya.ads", name = "admob-cmp", version = "1.0.2" }
```

```kotlin
// shared module build.gradle.kts
commonMain.dependencies {
    implementation(libs.admob.cmp)
}
```

The Android GMA Next-Gen SDK and UMP arrive as transitive Maven dependencies —
no extra Android dependency needed.

## 2. Android setup

Add your AdMob app id to `AndroidManifest.xml` (GMA crashes at startup without it):

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-3940256099942544~3347511713" /> <!-- sample id; replace -->
</application>
```

### Play Data Safety and the AD_ID permission

The Google Mobile Ads SDK merges `com.google.android.gms.permission.AD_ID` into
your manifest. Apps targeting API 33+ that do not declare it cannot access the
advertising ID. `admob-cmp` itself declares only `INTERNET`; the AD_ID permission
comes from GMA.

When completing the Play Console **Data safety** form, the SDK's default
configuration means your app collects:

- **Device or other IDs** — the advertising ID, used for Advertising or marketing.
- **App activity / app interactions** — ad impressions and clicks.
- **Approximate location** — coarse location derived from IP for ad targeting.

Declare these as collected and shared with third parties. Google publishes a
per-SDK data-disclosure guide for the Mobile Ads SDK; check it against your
mediation adapters, which may collect additional categories.

To opt out of the advertising ID, remove the permission explicitly:

```xml
<uses-permission
    android:name="com.google.android.gms.permission.AD_ID"
    tools:node="remove" />
```

This reduces ad revenue and is not recommended unless your app is child-directed
or you have another compliance reason.

## 3. iOS setup

**Why this step exists:** the published artifact contains cinterop *bindings*
only — never Google's binaries. Your app links the real GMA/UMP frameworks
itself via Swift Package Manager. This keeps mediation adapters working (no
duplicate ObjC classes) and lets you take GMA patch releases without waiting
for this library.

1. In Xcode: **File → Add Package Dependencies**, add both:
   - `https://github.com/googleads/swift-package-manager-google-mobile-ads.git`
     (GoogleMobileAds — use the same major version this library binds: **13.x**)
   - `https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git`
     (GoogleUserMessagingPlatform 3.x)
2. Add to the app target's `Info.plist`:

```xml
<key>GADApplicationIdentifier</key>
<string>ca-app-pub-3940256099942544~1458002511</string> <!-- sample id; replace -->
<key>SKAdNetworkItems</key>
<array>
    <dict>
        <key>SKAdNetworkIdentifier</key>
        <string>cstr6suwn9.skadnetwork</string>
    </dict>
    <!-- ...copy the full current list from the AdMob iOS docs -->
</array>
```

3. If your Kotlin framework is static and no Swift file imports GoogleMobileAds,
   add to `OTHER_LDFLAGS`: `-framework JavaScriptCore` (GMA needs it and nothing
   else triggers autolinking).
4. (Optional but recommended) App Tracking Transparency: add
   `NSUserTrackingUsageDescription` if you want IDFA-personalized ads.

Verify the whole setup with:

```bash
./gradlew :admob-cmp-core:doctorIos     # report-only diagnostic
```

### Troubleshooting: iOS linker errors

| Symptom (at app link time) | Cause | Fix |
|---|---|---|
| `Undefined symbol: _OBJC_CLASS_$_GADMobileAds` | GoogleMobileAds SPM package not added | Add the GMA SPM package (step 1) |
| `Undefined symbol: _OBJC_CLASS_$_UMPConsentInformation` | UMP SPM package not added | Add the UMP SPM package (step 1) |
| `Undefined symbol: _OBJC_CLASS_$_JSContext` | JavaScriptCore not linked | Add `-framework JavaScriptCore` to `OTHER_LDFLAGS` (step 3) |
| `Undefined symbol: GADCurrentOrientation...` / new-API symbols | SPM GMA major version older than the bound headers | Bump the GMA SPM package to 13.x |

## 4. Initialize

```kotlin
val adManager = rememberAdManager()

LaunchedEffect(Unit) {
    adManager.gatherConsentAndInitialize(
        AdConfig(
            androidAppId = TestAdIds.ANDROID_APP_ID, // replace with your app ids
            iosAppId = TestAdIds.IOS_APP_ID,
            initializationHooks = listOf(
                // Runs after the UMP gate and before native GMA initialization
                object : AdInitializationHook {
                    override suspend fun onPhase(phase: AdInitializationPhase, config: AdConfig) {
                        if (phase == AdInitializationPhase.BeforeMobileAdsInitialize) {
                            adManager.tracking.requestAuthorization()
                        }
                    }
                }
            ),
            testMode = true
        )
    )
}
```

> `testMode` defaults to `false`. Set it to `true` only for development on
> registered test devices / debug geography — never ship a production build with
> test mode on.

`gatherConsentAndInitialize(config)` runs the UMP consent flow, invokes any `initializationHooks`, and then starts
Mobile Ads. For manual control over consent and tracking (such as requesting ATT explicitly after UMP), use this exact order:

```kotlin
adManager.consent.gatherConsent(config)
adManager.tracking.requestAuthorization()
adManager.initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)
```

| `ConsentMode` | Behavior |
|---|---|
| `GatherBeforeInitialize` | Request consent info, present the UMP form if required, then init. Recommended. |
| `InitializeOnlyIfAlreadyAllowed` | No form; init only if consent already permits requests, else status `ConsentRequired`. Do not use `SkipConsent` here, as it ignores future UMP revocation. |
| `SkipConsent` | Bypass UMP entirely. |

Every ad request is gated: before initialization succeeds (and consent allows
requests), loads fail fast with `AdErrorCode.SDK_NOT_READY` /
`AdErrorCode.CONSENT_REQUIRED` — nothing reaches the network.

### Child and teen treatment

Consent and ad-request treatment are separate settings:

```kotlin
AdConfig(
    androidAppId = "...",
    iosAppId = "...",
    // UMP consent-flow setting:
    consentTagForUnderAgeOfConsent = true,
    globalRequestConfiguration = GlobalRequestConfiguration(
        // Google Mobile Ads request setting:
        ageRestrictedTreatment = AgeRestrictedTreatment.Child
    )
)
```

Use `Teen` only when your app has determined that treatment is appropriate.
The SDK deliberately does not infer either setting from the other.

**Calling `initialize`/`gatherConsentAndInitialize` more than once is safe but
does not re-configure the SDK.** The underlying Google Mobile Ads singleton
initializes at most once per process. A second call with the same effective
`AdConfig` (app id + request configuration) and `ConsentMode` just replays the
first call's result. A second call with a *different* app id or request
configuration is ignored (with a logged warning) rather than re-applied —
Google Mobile Ads itself has no supported way to re-initialize with new
settings once started. Decide your settings before the first `initialize`
call ever runs.

## 5. Observe status and events

```kotlin
val status by adManager.status.collectAsState()
// AdManagerStatus: Idle, Initializing, ConsentRequired, Ready, Disabled, Failed(error, retryable)

LaunchedEffect(Unit) {
    adManager.events.collect { event ->
        when (event) {
            is AdEvent.Loaded -> println("loaded: ${event.placementId}")
            is AdEvent.LoadFailed -> println("failed: ${event.error}")
            is AdEvent.Paid -> println("revenue: ${event.paidEvent.value.valueMicros}")
            is AdEvent.RewardEarned -> println("reward: ${event.reward}")
            else -> Unit
        }
    }
}
```

## 6. Test ad units

`TestAdIds` carries Google's demo ids for every format on both platforms
(`TestAdIds.ANDROID_BANNER`, `TestAdIds.IOS_REWARDED`,
`TestAdIds.ANDROID_COLLAPSIBLE_BANNER`, ...). `debugAdConfig` and
`debugAdPlacements` give you a ready-made test setup.

## Common pitfalls

- **One manager per process.** `rememberAdManager()` returns a process-wide
  singleton (`AdMob.manager(context)` outside Compose on Android). Don't try to
  construct managers yourself.
- **Controllers are placement-keyed and cached** — `adManager.interstitial(p)`
  returns the same controller every time; wrap in `remember` to avoid the map
  lookup per recomposition, not because it creates anything.
- **All APIs are main-safe.** The SDK hops to the main thread internally; call
  from any dispatcher.
