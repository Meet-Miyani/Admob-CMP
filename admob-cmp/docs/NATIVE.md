# Native Ads

Native ads render through a declarative layout DSL (`adLayout { ... }`) that the
SDK turns into platform-native views with all assets registered for click and
impression tracking.

## Usage

```kotlin
val placement = AdPlacement(
    id = "feed_native",
    format = AdFormat.Native,
    androidAdUnitId = TestAdIds.ANDROID_NATIVE,
    iosAdUnitId = TestAdIds.IOS_NATIVE,
    maxCacheSize = 5
)

val feedAdLayout = remember {
    adLayout {
        column(modifier = AdModifier.fillMaxWidth()) {
            media(
                modifier = AdModifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clipRounded(8.dp)
            )
            spacer(modifier = AdModifier.height(8.dp))
            headline(maxLines = 2)
            body(maxLines = 3)
            row(spacing = 8.dp) {
                icon(modifier = AdModifier.size(24.dp))
                advertiser()
                adBadge()
            }
            callToAction(modifier = AdModifier.fillMaxWidth())
        }
    }
}

NativeAdView(
    placement = placement,
    itemKey = "feed_item_1",      // stable per slot; drives pool acquire/release
    layout = feedAdLayout,
    modifier = Modifier.fillMaxWidth(),
    onEvent = { event -> /* Loaded, Impression, Clicked, Paid, Video*... */ }
)
```

`AdTemplates` ships ready-made layouts (e.g. `AdTemplates.mediaCard`, the
`NativeAdView` default).

## Layout stability

Each `adLayout { ... }` construction recursively validates the layout tree and computes a structural identity string.
In Compose applications, retain custom layouts using `remember { adLayout { ... } }` (or
`remember(layoutVariant) { adLayout { ... } }` for dynamic variants) to prevent unnecessary validation overhead and avoid rebuilding platform native views on recomposition.

## DSL nodes

| Node | Asset | Notes |
|------|-------|-------|
| `headline()` | Headline | The only required asset |
| `body()`, `advertiser()`, `price()`, `store()`, `starRating()` | Text assets | Hidden/collapsed per `visibilityPolicy` when missing |
| `icon()` | App icon | |
| `media()` | Image/video media view | Use `mediaInfo()` for the real aspect ratio |
| `callToAction()` | CTA button | |
| `adBadge()` | "Ad" label | Required by AdMob policy — the validator warns if absent |
| `adChoices()` | AdChoices icon | |
| `row/column/box/spacer/text` | Containers & statics | |

Every node takes an `AdModifier` (padding, size, background, border, clip,
weight, alpha, ...). `AdLayoutValidator` runs in debug and reports issues like a
missing AdBadge.

On debug builds, Google may show its Native Ad Validator. AdMob requires every
registered asset to stay fully inside the platform native-ad root and the ad
attribution to appear at the top. The built-in templates enforce these rules
on Android and iOS; custom layouts should keep `AdBadge` at the top and avoid
offsets that move registered assets outside the root bounds.

## Pooling

`NativeAdView` handles acquire/release automatically via `itemKey`. For manual
control:

```kotlin
val pool = adManager.nativeAd(placement)

scope.launch { pool.preload(count = 5) }

val token = pool.acquire() ?: return
try {
    val info = pool.mediaInfo(token)   // aspectRatio, hasVideoContent, durationSeconds
    // render...
} finally {
    pool.release(token)                // every acquired token MUST be released
}
```

Pooled ads expire per `AdExpirationPolicy` (1 hour default) and are evicted on
access.

## Video

`mediaInfo(token).hasVideoContent` tells you whether the ad has video.
Video lifecycle events (`AdEvent.VideoStarted/VideoPlayed/VideoPaused/
VideoEnded/VideoMuted`) are currently emitted on **iOS only** — the Android
Next-Gen SDK does not expose video callbacks yet.
