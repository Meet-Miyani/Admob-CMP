# terrakok/kmp-awesome — entry (GATED, do not submit yet)

Repository: https://github.com/terrakok/kmp-awesome
Branch: `master`. File: `README.MD` — **uppercase extension**; `README.md` 404s.
Contribution mechanism: pull request. There is no CONTRIBUTING.md; the rules live
in the `## Contribution guide` section at the bottom of `README.MD`.

## The gate — both conditions must be true before opening a PR

Verbatim from the README's Contribution guide:

```
 1) every suggesion should contain link to any project uses the library (not an author's sample)
 2) the library is supposed to support Android + iOS targets
 3) the library must be published to the MavenCentral
 4) the library should be a popular (github stars ~ 50, at least)
```

| Rule | Status 2026-07-31 |
|---|---|
| 1 — a link to a project that *uses* the library, not the author's own sample | **UNMET** |
| 2 — Android + iOS targets | Met |
| 3 — published to Maven Central | Met |
| 4 — roughly 50 GitHub stars or more | **UNMET (0 stars)** |

**Do not open the PR until rules 1 and 4 are both satisfied.** Rule 4 is the
§12 90-day star target (50, directional), so this entry is naturally a
post-90-day action, not a launch-week action. Rule 1 needs a real third-party
app or library that depends on `dev.avinya.ads:admob-cmp`; the demo modules in
this repo do not count.

Two honest ways rule 1 gets satisfied, neither of which is gameable:

- A consumer opens an issue, a discussion, or a "we ship this" note. Watch for
  it, then ask permission to cite them.
- klibs.io reports a non-zero **Dependents** count on the project page once the
  listing exists (Task 1). A dependent found there is a genuine third-party
  usage link.

Re-check the gate at the 90-day metrics checkpoint (Task 7). If stars are still
below ~50, do not submit; a rejected PR on a curated list is worse than no PR,
because the rejection is public and permanent.

## Where the entry goes

Section `### 🧩 Service SDK`, appended **after** the last existing entry
(`ConfigCat`) and **before** the `### 🧮 Arithmetic` heading.

Why Service SDK: that section holds third-party service wrappers — Firebase,
Supabase, OpenAI, Sentry, GrowthBook, ConfigCat. An AdMob wrapper is the same
shape. `### 🔍 Analytics` is the wrong section; it holds crash reporting and
event tracking (CrashKiOS, NSExceptionKt, trckr), not ad serving.

There is no ads or monetization category in the list, and no existing entry
matches `admob`, `basic-ads`, `monetiz` or `advertis`. The category is
uncontested.

Do **not** add a new `### Ads` heading. Adding a section for one entry also
requires editing the `## Contents` HTML table above, which is a much larger diff
and a much easier PR to decline.

## The entry, verbatim — copy these four lines exactly

```
[admob-cmp](https://github.com/Meet-Miyani/admob-compose-multiplatform) - AdMob SDK for Compose Multiplatform
[![GitHub Repo stars](https://img.shields.io/github/stars/Meet-Miyani/admob-compose-multiplatform?style=flat)](https://github.com/Meet-Miyani/admob-compose-multiplatform)
[![Maven Central](https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp)](https://central.sonatype.com/artifact/dev.avinya.ads/admob-cmp)
> Banner, interstitial, rewarded, rewarded interstitial, app-open and native ads for Android and iOS behind one Kotlin API, with UMP consent in the initialization flow, mediation and paid/revenue events. Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

Followed by exactly one blank line, matching every other entry in the file.

Format notes, derived from the file itself:

- Entries are **not** bulleted and **not** alphabetised. They are plain
  paragraphs appended in the order they were merged. Append at the end of the
  section; do not sort.
- Line 1 is `[Name](repo-url) - short description`. The separator is a plain
  hyphen surrounded by single spaces, not an en dash.
- Line 2 is the `shields.io` GitHub-stars badge, linking to the repo.
- Line 3 is the `shields.io` Maven Central badge, linking to the
  `central.sonatype.com/artifact/<group>/<artifact>` page.
- Line 4 is a `>` blockquote holding the long description.
- Six of the ten Service SDK entries have a trailing space at the end of line 1
  and four do not. A single trailing space is not a Markdown hard break, so all
  ten render identically. The entry above matches `ConfigCat`, the entry it
  follows, and has no trailing space.
- The link text is `admob-cmp`, the Maven artifact id, not the repo slug — the
  section mixes both conventions (`supabase-kt` and `bitcoin-kmp` use the
  artifact name) and the artifact id is what a reader copies into Gradle.

## PR metadata

Title:

```
Add admob-cmp to Service SDK
```

Body:

```
Adds `dev.avinya.ads:admob-cmp` — an AdMob SDK for Compose Multiplatform
covering banner, interstitial, rewarded, rewarded interstitial, app-open and
native ads on Android and iOS from one Kotlin API.

Against the contribution rules:

1. Used by: <LINK TO A THIRD-PARTY PROJECT — REQUIRED, NOT AN AUTHOR SAMPLE>
2. Targets: Android (`androidJvm`) and iOS (`iosArm64`, `iosSimulatorArm64`,
   `iosX64`), per the published `kotlin-tooling-metadata.json`.
3. Maven Central: https://central.sonatype.com/artifact/dev.avinya.ads/admob-cmp
   — 1.0.0, 1.0.1, 1.0.2, 1.1.0.
4. Stars: <CURRENT COUNT>

Docs: https://ads.avinya.dev

Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
trademarks of Google LLC.
```

The two angle-bracket fields are deliberately unfilled: they cannot be written
until the gate opens, and inventing either of them would be dishonest to the
maintainer. If either is still empty, the PR is not ready.

## Fallback if the gate stays shut

`Heapy/awesome-kotlin` is the other curated list linked from
`kotlinlang.org/community`. It states **no** star threshold and no
third-party-usage requirement. Its contributing guide
(`.github/contributing.md`) says:

```
1. Checkout `main` branch;
2. Edit  [src/main/resources/links/]${category}.awesome.kts;
3. Create PR.
```

The relevant category file is `src/main/resources/links/Libraries.awesome.kts`
(alternatives: `Android.awesome.kts`, `Native.awesome.kts`). Entries are Kotlin
script, not Markdown, so the four-line Markdown entry above cannot be reused —
read a neighbouring entry in that file and match its DSL.

This is a genuine alternative, not a consolation prize: it is linked from
kotlinlang.org and it accepts the project today.
