# Kotlin Weekly — Day 1

**HUMAN STEP — outward-facing publication.** An agent must not send this email.

Library / Project details:
- Brand/Slug/Coordinate: AdMob CMP (repo admob-compose-multiplatform, coordinate dev.avinya.ads:admob-cmp)
- Head term targets: compose multiplatform admob, kotlin multiplatform admob, kmp admob library

Submission mechanism, from https://kotlinweekly.net/ — the site's own `mailto:` link:

    To:      mailinglist@kotlinweekly.net
    Subject: Link for submission - Kotlin Weekly

Kotlin Weekly is editorially curated and explicitly invites link submissions, so
there is no self-promotion risk here. It is the single highest value-per-risk
channel in the calendar. The editors decide whether to run it; that is the whole
moderation model.

There is a separate `Sponsoring for Kotlin Weekly` subject for paid placement.
**Do not use it** — spec §2 rules out paid acquisition of any kind.

## Body — send verbatim

Hi,

I'd like to submit a link for a future issue.

**admob-cmp — a Compose Multiplatform AdMob SDK for Android and iOS**
https://ads.avinya.dev

I'm the author. AdMob CMP (repo admob-compose-multiplatform, coordinate dev.avinya.ads:admob-cmp) is a compose multiplatform admob, kotlin multiplatform admob, and kmp admob library. It covers banner, interstitial, rewarded, rewarded interstitial, app-open and native ads behind one Kotlin API, with UMP consent in the initialization flow, mediation, and paid/revenue events. Published on Maven Central as `dev.avinya.ads:admob-cmp` (Kotlin 2.3.20, Compose Multiplatform 1.11.1, Android minSdk 26, iOS 15+).

The piece your readers may find most useful is the iOS one: Kotlin/Native test executables link without Xcode, so they can't see the GoogleMobileAds Swift package, and `:module:iosSimulatorArm64Test` fails with `Undefined symbols: _OBJC_CLASS_$_GADBannerView`. 1.1.0 ships a Gradle plugin that fixes it in one line — https://ads.avinya.dev/reference/troubleshooting/

Source: https://github.com/Meet-Miyani/admob-compose-multiplatform

Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.

Thanks either way,
Meet Miyani
