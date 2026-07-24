# Interstitial & Rewarded Ads

Interstitial, rewarded, rewarded interstitial, and app-open controllers all
share the `FullScreenAdController` interface: `load()`, `show()`, `isReady()`,
`availability()`, `loadState: StateFlow<AdLoadState>`, `events: SharedFlow<AdEvent>`.

## Interstitial

```kotlin
val placement = AdPlacement(
    id = "interstitial_main",
    format = AdFormat.Interstitial,
    androidAdUnitId = TestAdIds.ANDROID_INTERSTITIAL,
    iosAdUnitId = TestAdIds.IOS_INTERSTITIAL
)
val interstitial = remember(adManager) { adManager.interstitial(placement) }

// Preload
scope.launch {
    when (val state = interstitial.load()) {
        is AdLoadState.Loaded -> println("ready: ${state.responseInfo?.responseId}")
        is AdLoadState.Failed -> println("load failed: ${state.error}")
        else -> Unit
    }
}

// Show (suspends until the ad is dismissed)
scope.launch {
    when (val result = interstitial.show()) {
        is AdShowResult.Shown -> println("shown and dismissed")
        is AdShowResult.Rewarded -> Unit // not produced by interstitials
        is AdShowResult.NotReady -> println("call load() first")
        is AdShowResult.Failed -> println("show failed: ${result.error}")
    }
}
```

## Rewarded / rewarded interstitial

The reward arrives in the `show()` result — no listener needed:

```kotlin
val rewarded = remember(adManager) {
    adManager.rewarded(
        AdPlacement(
            id = "rewarded_main",
            format = AdFormat.Rewarded,
            androidAdUnitId = TestAdIds.ANDROID_REWARDED,
            iosAdUnitId = TestAdIds.IOS_REWARDED
        )
    )
}

scope.launch {
    rewarded.load()
    when (val result = rewarded.show()) {
        is AdShowResult.Rewarded -> grantReward(result.reward.amountMicros, result.reward.type)
        is AdShowResult.Shown -> println("dismissed before earning")
        is AdShowResult.NotReady, is AdShowResult.Failed -> Unit
    }
}
```

`AdEvent.RewardEarned` is also emitted on the event stream at the moment the
reward is earned (before dismissal), if you prefer event-driven handling.

Server-side verification:

```kotlin
fullScreenOptions = FullScreenAdOptions(
    serverSideVerification = ServerSideVerificationOptions(userId = "u123", customData = "level-7")
)
```

## Caching (`maxSize > 1`)

```kotlin
AdPlacement(
    id = "cached_interstitial",
    format = AdFormat.Interstitial,
    adUnitIds = AdUnitIds(android = "...", ios = "..."),
    cachePolicy = AdCachePolicy(
        maxSize = 3,               // load() tops the cache up to 3 ads
        reloadAfterShow = true     // refill in the background after each show
    )
)
```

- `load()` fills the cache sequentially; partial fills still report `Loaded`.
- `show()` consumes ads FIFO (oldest first); expired ads are evicted.
- TTL defaults: 1 hour for full-screen formats, 4 hours for app-open
  (`AdExpirationPolicy`). `availability()` reports `cachedCount` and `expiresIn`.
- `show()` is not reentrant per controller: calling it again while a previous
  `show()` on the *same* controller is still on screen returns
  `AdShowResult.NotReady` immediately — it does **not** queue and wait for the
  first presentation to finish. Await the first `show()`'s result (it suspends
  until dismissal) before issuing another on the same controller.

## Availability

```kotlin
if (interstitial.isReady()) {
    interstitial.show()
}
val a = interstitial.availability() // isReady, cachedCount, expiresIn
```

Note: `preload()` is an alias of `load()` — both top up the cache and return
the resulting `AdLoadState`.
