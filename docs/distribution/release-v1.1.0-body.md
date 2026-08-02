# Replacement body for the 1.1.0 GitHub release

Release: https://github.com/Meet-Miyani/admob-compose-multiplatform/releases/tag/1.1.0
Tag: `1.1.0` (no `v` prefix — the *name* carries the `v`, the tag does not).

**New release name** (the current one names the fix but not the product, so it
matches nothing anyone searches for):

```
v1.1.0 — Compose Multiplatform AdMob: zero-config Kotlin/Native test linking
```

What changed and why:

- Added a two-sentence opener. A release page is often the first thing a search
  result surfaces, and the current body never says what the library is.
- Added the Gradle coordinates near the top, so the page answers "how do I use
  this" without a click.
- Added documentation links, which did not exist because the docs site did not
  exist when this was written.
- Replaced the dead `compare/v1.0.2...v1.1.0` link (HTTP 404 — there is no
  `1.0.2` tag) with the changelog page.
- Added the trademark line required by the public-visibility spec.
- Kept the whole technical body verbatim. It is good; it was only missing its
  frame.

---

## Kotlin/Native iOS tests now link without manual setup

**AdMob CMP** is a Compose Multiplatform AdMob SDK for Android and iOS — banner,
interstitial, rewarded, rewarded interstitial, app-open and native ads behind one
Kotlin API, with UMP consent in the initialization flow, mediation, and
paid/revenue events.

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:1.1.0")
```

📖 [Quickstart](https://ads.avinya.dev/start/quickstart/) ·
[Installation](https://ads.avinya.dev/start/installation/) ·
[iOS setup](https://ads.avinya.dev/start/ios-setup/) ·
[All docs](https://ads.avinya.dev)

---

If your project runs `:yourModule:iosSimulatorArm64Test`, admob-cmp used to
fail the link with no explanation:

```
Undefined symbols for architecture arm64:
  "_OBJC_CLASS_$_GADBannerView", referenced from: ...
```

The cause was never obvious: your **app** gets Google's binaries from the
GoogleMobileAds Swift package, but a Kotlin/Native **test** executable links
without Xcode and has no access to SPM. Fixing it meant hand-copying framework
download logic, version pins, checksums and linker flags into your own build
script — and keeping them in sync with this library by hand.

That's now one line.

### New: the `dev.avinya.ads.admob-cmp` Gradle plugin

```kotlin
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}
```

It needs `mavenCentral()` in your settings' plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

The plugin:

- downloads the GMA/UMP XCFrameworks matching the bindings this release was
  generated from — versions and checksums are generated from the library build,
  so they can't drift
- verifies the SHA-256 of every archive **before** extracting a single byte
- applies linker options to **test binaries only**. Your shipped app framework
  is untouched and still resolves GoogleMobileAds through SPM, exactly as before
- adds `./gradlew doctorIos`, a report-only check of your SPM products,
  `Info.plist`, and framework cache

Full walkthrough: [Troubleshooting → undefined GAD symbols](https://ads.avinya.dev/reference/troubleshooting/)

### Clearer failures

Both cinterop definitions now carry a `userSetupHint`, so if the link does fail
you get an explanation and a link to the fix instead of a bare symbol dump.

### Upgrading from 1.0.2

```kotlin
implementation("dev.avinya.ads:admob-cmp:1.1.0")
```

Plus the `plugins {}` block above if you run Kotlin/Native tests. **No source
changes required — the public ABI is byte-for-byte identical to 1.0.2.**

If you previously copied XCFramework download or linker logic into your own
build script as a workaround, you can delete it.

### Compatibility

| admob-cmp | Kotlin | Compose Multiplatform | Android minSdk | iOS deployment target |
|---|---|---|---|---|
| 1.1.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |

Full matrix: https://ads.avinya.dev/reference/compatibility/

### Notes

- The Gradle plugin is published as a separate artifact
  (`dev.avinya.ads:admob-cmp-gradle-plugin`) and is versioned in lockstep with
  the library. Always use matching versions.
- Android integration is unchanged in this release.

**Full changelog:** https://ads.avinya.dev/reference/changelog/

---

*Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
trademarks of Google LLC.*
