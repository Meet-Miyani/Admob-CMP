# admob-cmp — Compose Multiplatform AdMob SDK

A Compose Multiplatform wrapper over the Google Mobile Ads SDKs for **Android**
(GMA Next-Gen SDK, released — currently v1.x GA) and **iOS**
(GMA iOS 13.x). One Kotlin API for banner, interstitial, rewarded, rewarded
interstitial, app-open, and native ads, with UMP consent built into the
initialization flow, paid/revenue events, and mediation support.

The API keeps AdMob's vocabulary (`AdValue`, `ResponseInfo`, adaptive banner
sizes, UMP consent states, native asset names) but replaces the listener-style
SDK surface with suspend functions, `StateFlow` state, and one sealed `AdEvent`
stream. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design.

## Installation

```kotlin
// commonMain
implementation("tech.avinya.ads:admob-cmp:0.1.0")
```

Platform setup (Android manifest entry, iOS SPM packages + Info.plist) is
required — follow [docs/SETUP.md](docs/SETUP.md).

## Version compatibility

`admob-cmp` is published as Kotlin/Native klibs plus cinterop klibs. Klibs are not
binary-compatible across arbitrary Kotlin versions, so consumers must build with a
compatible compiler.

| admob-cmp | Kotlin | Compose Multiplatform | Android minSdk | iOS deployment target |
|---|---|---|---|---|
| 0.1.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |

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

- [Setup & initialization](docs/SETUP.md) — dependency, platform setup, init, troubleshooting
- [Interstitial & rewarded](docs/INTERSTITIAL.md)
- [Banner ads](docs/BANNER.md) — adaptive sizes, collapsible, refresh policies
- [Native ads](docs/NATIVE.md) — layout DSL, pooling, media info
- [App-open ads](docs/APP_OPEN.md) — `AppOpenAdCoordinator`
- [Consent & privacy](docs/CONSENT.md) — UMP modes, privacy options form
- [Mediation](docs/MEDIATION.md) — adapters, initialization hooks
- [Architecture](docs/ARCHITECTURE.md) — module map, threading, caching, decisions
- [Publishing](docs/PUBLISHING.md) — maintainer guide

Integrating with an AI coding agent? Point it at [AGENTS.md](AGENTS.md).

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
./gradlew :admob-cmp:doctorIos               # diagnose iOS consumer integration
```

## License

Apache 2.0
