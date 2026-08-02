# Medium & Product Hunt Listing — Day 4

**HUMAN STEP — outward-facing publication.** An agent must not publish this.

Library / Project metadata:
- Brand/Slug/Coordinate: AdMob CMP (repo admob-compose-multiplatform, coordinate dev.avinya.ads:admob-cmp)
- Target search keywords: compose multiplatform admob, kotlin multiplatform admob, kmp admob library

Medium's rules permit republishing your own content: if you have your own blog
where you publish your content, you may republish it on Medium as long as you
hold the rights. What Medium's spam rules forbid is "repeatedly using responses
or mentions as a method of promotion" — so never comment on other people's posts
to advertise this.

## Medium Import Instructions — Use the Import tool, not copy-paste

    https://medium.com/p/import

Paste the **dev.to** URL from Day 2. The import tool backdates the post to the
original date and sets the canonical link automatically, so Medium's domain
authority does not outrank the source. Copy-pasting the text instead produces
an uncanonicalised duplicate — the exact outcome this whole program is trying
to avoid.

Two days after dev.to is deliberate: it gives Google time to crawl the canonical
before the duplicate exists.

## After importing to Medium

- [ ] Open the imported story's settings and confirm the canonical link points at
      the dev.to URL. If the import did not set it, set it manually. **If it
      cannot be set, unpublish the story** — an uncanonicalised duplicate is
      worse than no Medium presence at all.
- [ ] Tags (Medium allows five): `Kotlin`, `Kotlin Multiplatform`,
      `Compose Multiplatform`, `iOS`, `Android`.
- [ ] Confirm the fenced code blocks survived the import. Medium's importer
      mangles nested backticks; re-check the Gradle snippets specifically.
- [ ] Confirm the trademark line is present at the end.
- [ ] Do not submit to a Medium publication that requires exclusivity — the
      canonical must stay on dev.to.

---

## Product Hunt Listing Copy

**Tagline:**
AdMob CMP — Compose Multiplatform AdMob SDK for Android & iOS

**Short Description:**
AdMob CMP (repo admob-compose-multiplatform, coordinate dev.avinya.ads:admob-cmp) is a compose multiplatform admob, kotlin multiplatform admob, and kmp admob library. One Kotlin API for banner, interstitial, rewarded, rewarded interstitial, app-open, and native ads with UMP consent and zero-config Kotlin/Native test linking.

**Maker Comment:**
```
Hi Product Hunt! 👋

I'm Meet, creator of AdMob CMP (repo admob-compose-multiplatform, coordinate dev.avinya.ads:admob-cmp).

When building Compose Multiplatform apps for Android and iOS, integrating monetization was always fragmented—either requiring verbose platform-specific bridges or hitting Kotlin/Native linker bugs like `Undefined symbols: _OBJC_CLASS_$_GADBannerView` during iOS test execution.

I built AdMob CMP to solve this cleanly:
1. One Kotlin API for all 6 ad formats (banner, interstitial, rewarded, rewarded interstitial, app-open, and native).
2. Built-in UMP consent & ATT privacy flow sequence.
3. Declarative Compose native ad layout DSL with view pooling.
4. A 1-line Gradle plugin (`dev.avinya.ads.admob-cmp`) that automatically links XCFrameworks for iOS simulator tests.

Check out the documentation at https://ads.avinya.dev and GitHub at https://github.com/Meet-Miyani/admob-compose-multiplatform

Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```
