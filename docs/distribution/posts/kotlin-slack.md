# Kotlin Slack — Days 5, 14, 21

**HUMAN STEP — outward-facing publication.** An agent must not post to Slack.

Join at https://surveys.jetbrains.com/s3/kotlin-slack-sign-up if not already a
member.

## The rule that shapes this file

From https://kotlinlang.org/docs/slack-code-of-conduct.html:

> Please refrain from cross-posting the same message on multiple channels. It is
> considered spamming.

This is why there are **three different messages** below rather than one message
posted three times. Do not paste any of them into a second channel. If a channel
is not covered here, it does not get a message.

Also binding, from the same page:

- `@channel` and `@here` are disabled. Do not attempt to work around it.
- "Please do not ping or mention someone directly to get your questions answered,
  especially project owners" — do not @ moderators or JetBrains staff.
- "Don't split messages into multiple ones. Ask it all in a single message."
- Use fenced code blocks, not plain-text code.
- The Slack is not an official support channel and not a bug tracker.

**Before each post: read the channel's topic.** The CoC makes moderators
responsible for keeping channel-specific rules visible, and the topic is where
they live. If a topic forbids project announcements, do not post — note it in
this file and move on. That is a legitimate outcome, not a failure.

---

## Day 5 — `#feed`

`#feed` is the channel for links and project announcements; it is moderated by
Nicola Corti (@gammax) and Youssef Shoaib. This is the only channel that gets a
general announcement.

Post as a single message:

```
Released admob-cmp 1.1.0 — a Compose Multiplatform AdMob SDK for Android and iOS. I'm the author.

One Kotlin API for banner, interstitial, rewarded, rewarded interstitial, app-open and native ads, with UMP consent in the initialization flow, mediation and paid/revenue events. Suspend functions and StateFlow rather than listener callbacks.

implementation("dev.avinya.ads:admob-cmp:1.1.0")

Kotlin 2.3.20 · Compose Multiplatform 1.11.1 · Android minSdk 26 · iOS 15+

Docs: https://ads.avinya.dev
Source: https://github.com/Meet-Miyani/admob-compose-multiplatform

Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

Put the `implementation(...)` line in a code block using the composer's code
formatting.

---

## Day 14 — `#multiplatform`

Moderated by Andrey Mischenko (@gildor). **Different content, not a reworded
announcement** — this message is about a Kotlin/Native tooling problem that
affects any project with cinterop bindings against an SPM-distributed framework,
which is squarely this channel's topic.

Post as a single message:

```
Sharing a Kotlin/Native gotcha and how I ended up solving it, in case it's useful to anyone binding an SPM-distributed framework.

If your cinterop bindings target a framework that ships as a Swift package, `:module:iosSimulatorArm64Test` fails to link:

  Undefined symbols for architecture arm64:
    "_OBJC_CLASS_$_GADBannerView", referenced from: ...

The app builds fine — Xcode resolves the SPM package and hands the binaries to the linker. A Kotlin/Native test executable is built by Gradle with no Xcode in the loop, so SPM never resolves and the binaries are never on the link line.

The fix that stuck was moving the XCFramework download, checksum verification and linker options into a Gradle plugin that applies them to test binaries only, leaving the app framework resolving through SPM as before. I ship it for my own library (I maintain admob-cmp) but the shape generalises to any SPM-bound cinterop.

Write-up: https://ads.avinya.dev/reference/troubleshooting/
```

---

## Day 21 — `#compose`

Moderated by Maryam Alhuthayfi and Zach Klippenstein. **Different content again**
— a Compose API design question, which is what this channel is for.

Post as a single message:

```
A Compose Multiplatform API design question I'd welcome opinions on.

Native ads are awkward in Compose: the platform SDKs need a real view hierarchy with each asset registered to the ad object, so you can't just lay out Composables and hope. I ended up with a small declarative layout DSL that describes the arrangement and handles asset registration underneath, plus a pool so a scrolling feed isn't loading one ad per row.

Docs, with the pool lifecycle diagram: https://ads.avinya.dev/formats/native/

I maintain admob-cmp, so this is my own library — but the interesting bit isn't the ads, it's the general problem of a declarative wrapper over a platform API that demands imperative view registration. If anyone has solved that shape more cleanly elsewhere I'd genuinely like to see it.
```

---

## Channels this plan deliberately does not post to

| Channel | Why |
|---|---|
| `#android` | A fourth message would be pushing the cross-posting rule past its intent. |
| `#compose-ios`, `#compose-desktop`, `#compose-web` | Covered by `#compose`. Posting to all four is exactly the cross-posting the rule prohibits. |
| `#announcements` | JetBrains announcements only. |
| `#meta`, `#reports` | Moderation channels. |
| `#library-development` | About building libraries in general, not announcing them. Fine to participate in; not a launch channel. |
