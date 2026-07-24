# Mediation

## The design

`admob-cmp` deliberately ships **zero mediation adapters**. Adapters are
platform binaries that must match your GMA SDK version and your network
contracts; bundling them inside a cross-platform wrapper would pin versions and
break the moment two artifacts carry the same ObjC class. Instead:

- **Android:** add adapter artifacts to your app module as usual, e.g.
  `implementation("com.google.ads.mediation:facebook:...")`. They ride on the
  same GMA Next-Gen SDK this library depends on transitively.
- **iOS:** add adapter pods/SPM packages to the Xcode project, next to the
  GoogleMobileAds package you already added in [SETUP.md](SETUP.md). Because
  the library links nothing itself (bindings-only), adapters resolve against
  the single GMA copy in your app — no duplicate-class problems.

Configure waterfalls/bidding in the AdMob UI as normal. Mediated fill flows
through every existing API: `AdResponseInfo.loadedAdNetworkResponseInfo`
identifies the winning adapter; `AdEvent.Paid` carries the mediated revenue.

## Adapter consent / privacy APIs

Many networks require privacy flags set *before* SDK initialization. Use
initialization hooks — they run at well-defined phases on both platforms:

```kotlin
val metaConsentHook = object : AdInitializationHook {
    override suspend fun onPhase(phase: AdInitializationPhase, config: AdConfig) {
        when (phase) {
            AdInitializationPhase.BeforeConsentRequest -> Unit
            AdInitializationPhase.BeforeMobileAdsInitialize -> {
                // e.g. expect/actual call into the adapter's platform API:
                // Android: AdSettings.setDataProcessingOptions(arrayOf("LDU"))
                // iOS:     FBAdSettings.setDataProcessingOptions(["LDU"])
            }
            AdInitializationPhase.AfterMobileAdsInitialize -> Unit
        }
    }
}

AdConfig(
    androidAppId = "...",
    iosAppId = "...",
    initializationHooks = listOf(metaConsentHook)
)
```

The hook body is your code: keep adapter-specific calls in `androidMain`/
`iosMain` behind an `expect`/`actual` and invoke them from the hook.

## Verifying adapters

```kotlin
// After status == Ready:
adManager.diagnostics.adapterStatuses().forEach {
    println("${it.adapterName}: initialized=${it.initialized} latencyMs=${it.latencyMillis}")
}

// Visual inspection on a test device:
scope.launch { adManager.diagnostics.openAdInspector() }
```

`openAdInspector()` launches Google's Ad Inspector overlay (works on test
devices) — the authoritative way to confirm an adapter serves.
