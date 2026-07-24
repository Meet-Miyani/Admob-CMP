# App-Open Ads

App-open ads show when the user returns to the app. Use the coordinator — it
implements the entire recommended lifecycle (preload, foreground detection,
minimum-background gating, cooldown, reload after consumption).

## Coordinator

```kotlin
val placement = AdPlacement(
    id = "app_open_main",
    format = AdFormat.AppOpen,
    androidAdUnitId = TestAdIds.ANDROID_APP_OPEN,
    iosAdUnitId = TestAdIds.IOS_APP_OPEN
)

val coordinator = remember(adManager) {
    AppOpenAdCoordinator(
        manager = adManager,
        controller = adManager.appOpen(placement),
        config = AppOpenConfig(
            minBackgroundDuration = 4.seconds,   // ignore quick app switches
            cooldownBetweenShows = 4.hours,      // ZERO disables the cooldown
            preloadOnStart = true,
            showOnColdStart = false              // see KDoc before enabling
        )
    )
}

LaunchedEffect(Unit) { coordinator.start(this) }
```

Behavior:

- Shows only when the SDK is `Ready` (so never over a consent form, and it
  works under `ConsentMode.SkipConsent` too).
- iOS listens to `WillEnterForeground` — system alerts and consent-form
  dismissals do not trigger shows. Android uses `ProcessLifecycleOwner`.
- After a show (or when no fresh ad is cached) it reloads automatically.

## Blocking sensitive flows

```kotlin
coordinator.isBlocked = true   // e.g. during purchase or onboarding
// ...
coordinator.isBlocked = false
```

Set it while another full-screen ad may show, during checkout, etc.

## Manual control

The controller is a normal `FullScreenAdController` if you want to orchestrate
yourself:

```kotlin
val appOpen = adManager.appOpen(placement)
scope.launch {
    appOpen.load()
    appOpen.showIfAvailable()   // show() only if isReady()
}
```

App-open ads expire after 4 hours by default (`AdExpirationPolicy.appOpenTtl`),
matching Google's guidance.
