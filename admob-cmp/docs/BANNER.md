# Banner Ads

`BannerAdView` is a composable that loads, sizes, attaches, refreshes, and
disposes the platform banner view itself. Size and refresh behavior come from
the placement, not from composable parameters.

## Usage

```kotlin
val placement = AdPlacement(
    id = "banner_home",
    format = AdFormat.Banner,
    adUnitIds = AdUnitIds(
        android = TestAdIds.ANDROID_BANNER,
        ios = TestAdIds.IOS_BANNER
    )
)

BannerAdView(
    placement = placement,
    modifier = Modifier.fillMaxWidth(),
    onEvent = { event ->
        when (event) {
            is AdEvent.Loaded -> println("banner loaded")
            is AdEvent.LoadFailed -> println("banner failed: ${event.error}")
            is AdEvent.Paid -> println("revenue: ${event.paidEvent.value.valueMicros}")
            else -> Unit
        }
    }
)
```

The composable resolves the width from its constraints (override with
`widthDp`), sizes its height from the returned ad size, and clears the
controller on dispose.

## Ad sizes (`AdSizePolicy` on the placement)

```kotlin
AdPlacement(
    id = "banner_home",
    format = AdFormat.Banner,
    adUnitIds = AdUnitIds(android = "...", ios = "..."),
    bannerSizePolicy = AdSizePolicy.AnchoredAdaptive()   // default is LargeAnchoredAdaptive()
)
```

| Policy | Maps to |
|---|---|
| `AnchoredAdaptive()` | Standard anchored adaptive banner (≤ 90dp) |
| `LargeAnchoredAdaptive()` | Large anchored adaptive (50–150pt) |
| `InlineAdaptive(maxHeightDp)` | Inline adaptive for scrolling content |
| `Fixed(widthDp, heightDp)` | Fixed custom size |
| `Fluid` | Fluid (fills its container) |

## Collapsible banners

Collapsible is a property of the anchored adaptive policies:

```kotlin
bannerSizePolicy = AdSizePolicy.AnchoredAdaptive(collapsible = CollapsiblePlacement.Bottom)
```

Test with the dedicated demo units — regular banner test ids never serve
collapsible fill: `TestAdIds.ANDROID_COLLAPSIBLE_BANNER` /
`TestAdIds.IOS_COLLAPSIBLE_BANNER`.

## Refresh policies

```kotlin
AdPlacement(
    id = "banner_home",
    format = AdFormat.Banner,
    adUnitIds = AdUnitIds(android = "...", ios = "..."),
    bannerRefreshPolicy = BannerRefreshPolicy.SdkManaged(60.seconds)
)
```

| Policy | Behavior |
|---|---|
| `AdServerManaged` (default) | No client timer; configure refresh in the AdMob UI |
| `SdkManaged(interval)` | Client-side reload every `interval` (30s–120s enforced), only while the app is foregrounded/STARTED; skips a cycle if a load is in flight |
| `Manual` | No automatic load at all — call `adManager.banner(placement).refresh()` yourself. `refresh()` reuses the size the composable measured (so compose `BannerAdView` at least once); without a prior composition it falls back to the screen width. |

## Headless use

You can drive banners without the composable via
`adManager.banner(placement)` (`load(sizePolicy, requestOptions)`, `refresh()`,
`loadState`, `events`, `clear()`), but you are then responsible for hosting the
platform view.
