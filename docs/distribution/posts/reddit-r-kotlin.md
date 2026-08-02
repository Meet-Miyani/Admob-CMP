# r/Kotlin — Day 7

**HUMAN STEP — outward-facing publication.** An agent must not post to Reddit.

## MANDATORY GATE: read the rules first

**r/Kotlin's rules could not be verified during planning.** Reddit blocks the
tooling used for this research — `www.reddit.com` is not accessible to the
agent's user agent, `old.reddit.com` and the `.json` endpoints return a bot
challenge instead of content, and Reddit is excluded from the available web
search. So this file makes **no claim** about what r/Kotlin permits.

Before posting, a human must open https://www.reddit.com/r/Kotlin/about/rules/
in a normal browser and read every rule.

Then, in this file, record:

    Rules read on: <DATE>
    Self-promotion rule: <QUOTE IT VERBATIM, or "none">
    Required flair: <NAME, or "none">
    Link posts allowed: <YES/NO>
    Verdict: <POST AS WRITTEN / POST WITH CHANGES: … / DO NOT POST>

If the rules forbid self-promotion, or restrict it to a scheduled thread, or
require a ratio of participation to promotion — **follow the rule**. Post in the
designated thread, or wait, or do not post. A removed post plus a moderator note
is worse for this project than silence, and it is permanent.

Fill the block above before posting. Do not post with it blank.

## Why this is a self-post and not a link post

Self-posts survive stricter rulesets than link posts almost everywhere on Reddit,
because the value is in the post body rather than in the click. The copy below is
written to be worth reading even if nobody follows a link, with authorship
disclosed in the first sentence and links at the end.

It also runs five days after the dev.to article on purpose: linking to a written
explanation reads as sharing, linking to a bare repo reads as advertising.

## Title

```
I built a Compose Multiplatform AdMob SDK covering all six ad formats — including native and app-open
```

## Body — post verbatim, as a text post

```
I maintain [admob-cmp](https://github.com/Meet-Miyani/admob-compose-multiplatform), a Compose Multiplatform wrapper over the Google Mobile Ads SDKs, and 1.1.0 is out. Sharing it here because the two things that took the longest are both things I'd have wanted to read about before starting.

**What it is:** one Kotlin API for banner, interstitial, rewarded, rewarded interstitial, app-open and native ads on Android and iOS. UMP consent is in the initialization flow rather than bolted on. Mediation and paid/revenue events are supported. The API keeps AdMob's vocabulary — `AdValue`, `ResponseInfo`, adaptive banner sizes, native asset names — but replaces the listener surface with suspend functions, `StateFlow` state, and one sealed `AdEvent` stream.

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:1.1.0")
```

**The first hard part — Kotlin/Native test linking.** Google ships the iOS SDK as a Swift package. Your app builds fine because Xcode resolves it. A Kotlin/Native test executable is built by Gradle with no Xcode in the loop, so SPM never resolves and `:module:iosSimulatorArm64Test` dies on `Undefined symbols: _OBJC_CLASS_$_GADBannerView`. The fix is a Gradle plugin that downloads and checksum-verifies the XCFrameworks and applies linker options to test binaries only, leaving the app framework resolving through SPM. One line in your `plugins {}` block. This generalises past ads — any cinterop binding against an SPM-distributed framework has the same problem.

**The second — native ads in Compose.** The platform SDKs want a real view hierarchy with each asset registered against the ad object, which doesn't map onto Composables at all. I ended up with a small layout DSL plus a pool, so a scrolling feed isn't firing one ad load per row.

**The correctness trap that cost me the most time:** on iOS the order is UMP consent → ATT → SDK initialization. Request ATT before consent and you permanently forfeit the IDFA for those requests. Nothing warns you; the ads just earn less. There's a sequence diagram for it in the docs.

Compatibility: Kotlin 2.3.20, Compose Multiplatform 1.11.1, Android minSdk 26, iOS 15+. Apache 2.0. Consumable from KMP/Gradle projects — a pure-Swift app would need a KMP shim.

Docs: https://ads.avinya.dev
Source: https://github.com/Meet-Miyani/admob-compose-multiplatform

Happy to answer anything about the iOS side in particular — it's where all the sharp edges are.

*Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.*
```

## Before posting

- [ ] The gate block above is filled in, with a real date.
- [ ] Required flair applied, if the subreddit has one.
- [ ] Both links return 200.
- [ ] Reddit's own site-wide self-promotion guidance is respected: participate in
      the community, don't only ever post your own work.

## After posting

- [ ] Answer every reply within 24 hours. An unanswered thread is a worse
      outcome than no thread.
- [ ] Never argue with a moderator. If it's removed, accept it and record why
      here so it isn't repeated.
- [ ] **Do not cross-post to r/androiddev, r/iOSProgramming, or
      r/KotlinMultiplatform.** Cross-posting the same content across programming
      subreddits is the most reliable way to get flagged as a spammer across all
      of them at once.
