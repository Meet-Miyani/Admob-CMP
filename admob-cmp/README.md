# admob-cmp — Compose Multiplatform AdMob SDK

A Compose Multiplatform wrapper over the Google Mobile Ads SDKs for **Android**
(GMA Next-Gen SDK, released — currently v1.x GA) and **iOS**
(GMA iOS 13.x). One Kotlin API for banner, interstitial, rewarded, rewarded
interstitial, app-open, and native ads, with UMP consent built into the
initialization flow, paid/revenue events, and mediation support.

The API keeps AdMob's vocabulary (`AdValue`, `ResponseInfo`, adaptive banner
sizes, UMP consent states, native asset names) but replaces the listener-style
SDK surface with suspend functions, `StateFlow` state, and one sealed `AdEvent`
stream. See [Architecture](https://ads.avinya.dev/reference/architecture/) for the design.

## Installation

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:1.1.1")
```

If your project runs Kotlin/Native tests (`:yourModule:iosSimulatorArm64Test`), also apply
the Gradle plugin — without it the test link fails on `Undefined symbols … _OBJC_CLASS_$_GAD*`:

```kotlin
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.1"
}
```

See [Installation](https://ads.avinya.dev/start/installation/) and
[Troubleshooting](https://ads.avinya.dev/reference/troubleshooting/).

Platform setup (Android manifest entry, iOS SPM packages + Info.plist) is
required — follow [Android setup](https://ads.avinya.dev/start/android-setup/) and
[iOS setup](https://ads.avinya.dev/start/ios-setup/).

## Version compatibility

`admob-cmp` is published as Kotlin/Native klibs plus cinterop klibs. Klibs are not
binary-compatible across arbitrary Kotlin versions, so consumers must build with a
compatible compiler.

| admob-cmp | Kotlin | Compose Multiplatform | Android minSdk | iOS deployment target |
|---|---|---|---|---|
| 1.1.1 | 2.3.20 | 1.11.1 | 26 | 15.0 |
| 1.1.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |
| 1.0.2 | 2.3.20 | 1.11.1 | 26 | 15.0 |
| 1.0.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |

**Kotlin:** the module is compiled with 2.3.20. Consumers on a different Kotlin
*minor* version may fail to resolve the klib. Patch versions are generally safe.

**Compose Multiplatform:** required only if you use the composable surface
(`BannerAdView`, `NativeAdView`, `rememberAdManager`). The controller API has no
Compose dependency.

**Consumption model:** the SDK is consumable from KMP/Gradle projects only — it
compiles into the consumer's umbrella framework. A pure-Swift iOS app cannot adopt
it without a KMP shim.

## Quick start

```kotlin
@Composable
fun App() {
    val adManager = rememberAdManager()

    LaunchedEffect(Unit) {
        adManager.gatherConsentAndInitialize(
            AdConfig(androidAppId = TestAdIds.ANDROID_APP_ID, iosAppId = TestAdIds.IOS_APP_ID)
        )
    }

    val placement = remember {
        AdPlacement(
            id = "main_interstitial",
            format = AdFormat.Interstitial,
            androidAdUnitId = TestAdIds.ANDROID_INTERSTITIAL,
            iosAdUnitId = TestAdIds.IOS_INTERSTITIAL
        )
    }
    val interstitial = remember(adManager) { adManager.interstitial(placement) }
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            interstitial.load()
            interstitial.show()
        }
    }) { Text("Show ad") }
}
```

## Documentation

Full documentation: <https://ads.avinya.dev>

- [Quickstart](https://ads.avinya.dev/start/quickstart/) — a rendering test ad in five minutes
- [Installation](https://ads.avinya.dev/start/installation/) — Gradle, version catalog, and the Gradle plugin
- [Android setup](https://ads.avinya.dev/start/android-setup/) · [iOS setup](https://ads.avinya.dev/start/ios-setup/)
- [Banner](https://ads.avinya.dev/formats/banner/) · [Interstitial](https://ads.avinya.dev/formats/interstitial/) · [Rewarded](https://ads.avinya.dev/formats/rewarded/) · [App-open](https://ads.avinya.dev/formats/app-open/) · [Native](https://ads.avinya.dev/formats/native/)
- [UMP consent](https://ads.avinya.dev/privacy/consent/) · [App Tracking Transparency](https://ads.avinya.dev/privacy/app-tracking-transparency/) · [Play Data safety](https://ads.avinya.dev/privacy/play-data-safety/)
- [Mediation](https://ads.avinya.dev/advanced/mediation/) · [Revenue events](https://ads.avinya.dev/advanced/revenue-events/) · [Caching, retry and timeouts](https://ads.avinya.dev/advanced/caching-retry-timeouts/) · [Test safety](https://ads.avinya.dev/advanced/test-safety/)
- [Architecture](https://ads.avinya.dev/reference/architecture/) · [Compatibility](https://ads.avinya.dev/reference/compatibility/) · [Troubleshooting](https://ads.avinya.dev/reference/troubleshooting/) · [Changelog](https://ads.avinya.dev/reference/changelog/)
- [Roadmap](https://ads.avinya.dev/project/roadmap/) · [Contributing](https://ads.avinya.dev/project/contributing/) · [Using with AI agents](https://ads.avinya.dev/project/ai-agents/)
- [Publishing](docs/PUBLISHING.md) — maintainer guide, repository only

Integrating with an AI coding agent? Point it at [AGENTS.md](AGENTS.md) and
<https://ads.avinya.dev/llms.txt>.

## Requirements

| Platform | Minimum |
|----------|---------|
| Android  | API 26+, GMA Next-Gen SDK (transitive) |
| iOS      | iOS 15+, GMA + UMP via SPM (see SETUP.md) |

## Building this module

```bash
./gradlew :admob-cmp:iosSimulatorArm64Test   # common tests (iOS runner)
./gradlew :admob-cmp:testAndroidHostTest     # common tests (JVM runner)
./gradlew :admob-cmp:checkKotlinAbi          # public API surface check
./gradlew :admob-cmp:updateKotlinAbi         # regenerate api/ dump after API changes
./gradlew doctorIos          # diagnose iOS consumer integration
```

## License

Apache 2.0
