# dev.to Article & X Announcement Thread — Day 2

**HUMAN STEP — outward-facing publication.** An agent must not publish this.

Library / Project metadata:
- Brand/Slug/Coordinate: AdMob CMP (repo admob-compose-multiplatform, coordinate dev.avinya.ads:admob-cmp)
- Target search keywords: compose multiplatform admob, kotlin multiplatform admob, kmp admob library

dev.to's Code of Conduct contains no self-promotion prohibition, and dev.to
supports cross-posting first-class through the `canonical_url` front-matter
field. Setting it is not optional here: without it, dev.to's much stronger
domain outranks `ads.avinya.dev` for our own content, which is the exact
opposite of what this program is for.

Publish via **Create Post → the three-dot menu → "Switch to Markdown editor"**,
then paste the whole thing including the front matter.

The title is the literal linker error, because that is the string people paste
into a search engine. Spec §7 marks
`admob ios kotlin multiplatform undefined symbols GAD` as easy difficulty, high
opportunity, "pure pain" — and uncontested.

## dev.to Article Copy — publish verbatim

```markdown
---
title: "Undefined symbols: _OBJC_CLASS_$_GADBannerView — fixing AdMob linking in Kotlin Multiplatform iOS tests"
published: true
description: Why Kotlin/Native test executables can't see the GoogleMobileAds Swift package, and the one-line fix.
tags: kotlin, ios, android, testing
canonical_url: https://ads.avinya.dev/reference/troubleshooting/
---

If you've added AdMob to a Kotlin Multiplatform project and then run your iOS
tests, you've probably seen this:

```
Undefined symbols for architecture arm64:
  "_OBJC_CLASS_$_GADBannerView", referenced from: ...
ld: symbol(s) not found for architecture arm64
```

Your app builds. Your Android tests pass. Only `iosSimulatorArm64Test` fails,
and the error names a class you never wrote.

I maintain [AdMob CMP](https://github.com/Meet-Miyani/admob-compose-multiplatform) (repo admob-compose-multiplatform, coordinate dev.avinya.ads:admob-cmp), a compose multiplatform admob / kotlin multiplatform admob / kmp admob library solution for Android & iOS, and this was the single most confusing thing about integrating it. Here's what's actually happening and how to fix it.

## Why it happens

Google ships the iOS Mobile Ads SDK as a Swift package. When you build your app,
Xcode resolves that package and hands the binaries to the linker. Everything
works.

A Kotlin/Native **test** executable is built by Gradle, not Xcode. There is no
Xcode build, therefore no SPM resolution, therefore no `GoogleMobileAds`
binaries on the link line. The cinterop bindings still declare
`GADBannerView`, so the compile succeeds and the link fails.

This is not specific to any one library. Any Kotlin Multiplatform project with
cinterop bindings against an SPM-distributed framework hits it. It's a structural
gap between how Kotlin/Native tests link and how SPM distributes binaries.

## The manual fix, and why you don't want it

You can fix it yourself. You need to:

1. Download the `GoogleMobileAds` and `UserMessagingPlatform` XCFrameworks at
   exactly the versions the bindings were generated against
2. Verify their checksums, because you are downloading binaries into a build
3. Unpack them somewhere stable and cache them
4. Add `-framework` and `-F` linker options — to the **test** binaries only,
   because your app framework must keep resolving through SPM
5. Keep all of the above in sync with the library, by hand, forever

That's 60-odd lines of `build.gradle.kts` that has nothing to do with your app,
and it silently rots the moment the library bumps its bindings.

## The one-line fix

As of 1.1.0 this is a Gradle plugin:

```kotlin
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}
```

It needs `mavenCentral()` in your plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

That's the whole change. The plugin:

- downloads the XCFrameworks at the versions the bindings were generated from —
  the versions and checksums are generated from the library build, so they
  can't drift out of sync
- verifies the SHA-256 of every archive **before** extracting a byte
- applies linker options to **test binaries only** — your shipped app framework
  is untouched and still resolves GoogleMobileAds through SPM
- adds `./gradlew doctorIos`, a report-only check of your SPM products,
  `Info.plist`, and framework cache

If you previously hand-rolled the workaround, delete it.

## Checking your setup

```bash
./gradlew doctorIos
```

It reports on your SPM products, your `Info.plist` entries (`GADApplicationIdentifier`,
`NSUserTrackingUsageDescription`), and the framework cache. It changes nothing —
it only tells you what's wrong.

Then:

```bash
./gradlew :yourModule:iosSimulatorArm64Test
```

## While you're here

AdMob CMP (repo admob-compose-multiplatform, coordinate dev.avinya.ads:admob-cmp) is a compose multiplatform admob / kotlin multiplatform admob / kmp admob library for Android and iOS. One Kotlin API for banner, interstitial, rewarded, rewarded interstitial, app-open and native ads, with UMP consent in the initialization flow, mediation, and paid/revenue events. Suspend functions and `StateFlow` instead of listeners.

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:1.1.0")
```

| admob-cmp | Kotlin | Compose Multiplatform | Android minSdk | iOS deployment target |
|---|---|---|---|---|
| 1.1.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |

Docs: <https://ads.avinya.dev> · Source:
<https://github.com/Meet-Miyani/admob-compose-multiplatform>

One thing worth flagging if you're doing this on iOS: the order is UMP consent,
then ATT, then SDK initialization. Requesting ATT before consent permanently
forfeits the IDFA for those requests, and it's not recoverable in that session.
There's a [diagram of the sequence in the consent docs](https://ads.avinya.dev/privacy/app-tracking-transparency/).

*Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
trademarks of Google LLC.*
```

## X (Twitter) Announcement Thread Copy — post as thread

```
1/5 🚀 Released 1.1.0 of AdMob CMP (repo admob-compose-multiplatform, coordinate dev.avinya.ads:admob-cmp)!
A lightweight compose multiplatform admob & kotlin multiplatform admob library for Android & iOS.
One Kotlin API for banner, interstitial, rewarded, rewarded interstitial, app-open & native ads.
https://ads.avinya.dev

2/5 Hit "Undefined symbols: _OBJC_CLASS_$_GADBannerView" when running Kotlin/Native iOS tests?
It happens because test executables link via Gradle without Xcode/SPM.
1.1.0 introduces `dev.avinya.ads.admob-cmp` Gradle plugin to solve test linking in 1 line!

3/5 AdMob CMP is a pure kmp admob library designed with Kotlin-first idioms:
- StateFlow & suspend functions over callback listeners
- Automatic UMP consent flow & ATT integration
- Native ad layout DSL + view pooling

4/5 Read the full deep-dive on fixing Kotlin/Native iOS test linking:
https://ads.avinya.dev/reference/troubleshooting/

5/5 Docs: https://ads.avinya.dev
Source: https://github.com/Meet-Miyani/admob-compose-multiplatform

Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

## Before publishing

- [ ] `canonical_url` is `https://ads.avinya.dev/reference/troubleshooting/` and that page returns 200.
- [ ] Every link in the article returns 200.
- [ ] Tags are four or fewer — dev.to's limit.
- [ ] The authorship disclosure ("I maintain…") is in the third paragraph.
- [ ] The trademark line is present.
