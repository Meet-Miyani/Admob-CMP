# Documentation Content Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite and expand every AdMob CMP documentation page into 24 keyword-targeted, diagram-backed guides at `docs-site/src/content/docs/**`, so the library ranks for the queries its audience actually types and the old Markdown files stop competing with them.

**Architecture:** Plan 2 has already scaffolded Starlight, fixed the 24 file paths, written the sidebar, and shipped 24 placeholder stubs. This plan replaces the **body** of each stub — never its path — with an 800–1,500-word guide whose H2s are questions, whose code samples are verified line-by-line against `admob-cmp/AGENTS.md` and the frozen klib ABI dumps, and which embeds one of the eight diagram components Plan 4 produces. The nine legacy files under `admob-cmp/docs/` are then reduced to one-line pointers so no page competes with its own canonical URL.

**Tech Stack:** Astro 7.1.6 · @astrojs/starlight 0.41.5 (MDX + `Aside`/`Tabs`/`Steps`/`LinkCard`/`CardGrid`) · `rehype-mermaid` 3.0.0 (build-time static SVG) · Node 20+ for the word-count script · Kotlin code samples targeting `dev.avinya.ads:admob-cmp:1.1.0`.

---

## Global Constraints

Every task's requirements implicitly include this section. Values are copied verbatim from the approved spec `docs/superpowers/specs/2026-07-31-public-visibility-design.md`, from `admob-cmp/CLAUDE.md`, and from Plan 2.

**Ownership and file paths**

- Docs live at `docs-site/src/content/docs/**` and are authored as `.mdx`.
- **Plan 2 owns the 24 file paths, the `sidebar` array in `docs-site/astro.config.mjs`, and the `title` frontmatter.** This plan rewrites bodies only. **Never move, rename, or delete a page file, and never add a page** — the sidebar, the sitemap, the OG image route, and `llms.txt` all key off those exact slugs.
- **Plan 4 owns `docs-site/src/components/diagrams/*.astro`.** Import them; do not author diagram markup. Task 1 writes a build-safe stub *only for components that do not yet exist*, which Plan 4 later overwrites.
- **Plan 5 owns the landing page** `docs-site/src/content/docs/index.mdx` and `docs-site/src/components/landing/*`. Do not touch either.
- The trademark line lives in the **site footer** (Plan 2/Plan 5). Do **not** repeat it on individual pages.

**URL structure — spec §8, fixed**

- No `/docs/` prefix. Starlight maps `docs-site/src/content/docs/<dir>/<slug>.mdx` → `/<dir>/<slug>/`.
- Example: `docs-site/src/content/docs/formats/banner.mdx` → `https://ads.avinya.dev/formats/banner/`.

**Fixed strings**

- Docs host: `https://ads.avinya.dev`
- Repository: `https://github.com/Meet-Miyani/admob-compose-multiplatform`
- Maven coordinate, unchanged: `dev.avinya.ads:admob-cmp`
- Current version: `1.1.0`
- Gradle plugin id: `dev.avinya.ads.admob-cmp`, version `1.1.0`
- Footer trademark line (Plan 2/5, not repeated per page): `Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.`

**Frontmatter schema**

Plan 2's `docs-site/src/content.config.ts` is Starlight's `docsSchema()` extended with one optional field:

```ts
faq: z.array(z.object({ q: z.string(), a: z.string() })).optional()
```

So every page in this plan uses exactly these keys and no others:

```yaml
---
title: <verbatim from Plan 2 — never change>
description: <≤160 characters>
faq:
  - q: <question, matching an H2 on the page>
    a: <one- or two-sentence answer, plain text, no Markdown>
---
```

- `description` must be **≤160 characters**, must contain the page's primary keyword, and must be a complete sentence.
- Do **not** add `sidebar:` frontmatter. Sidebar position is set explicitly by Plan 2's `sidebar` array in `astro.config.mjs`.
- `faq` entries must be answerable from the page body. Two to four per guide page. Google's `FAQPage` rules mean a question that does not appear on the page is a liability, not an asset.

**Content rules**

- Target **800–1,500 prose words per guide page** (code fences excluded), except where a task states a different target. Several legacy pages are below the 300-word thin-content threshold — `APP_OPEN.md` 229, `CONSENT.md` 240, `MEDIATION.md` 292, `BANNER.md` 361, `INTERSTITIAL.md` 374, `NATIVE.md` 483. **This plan is rewrite-and-expand, not migrate.** Copying a legacy file across is a task failure.
- **Every H2 is a question** using People-Also-Ask phrasing ("How do I …?", "Why does …?", "When should I …?"). H3s may be statements.
- Exactly **one primary keyword per page**, present in the `title` or the first 100 words of body prose, and in the `description`.
- Every page ends with a **"Where to next?"** section containing 2–4 `<LinkCard>`s to sibling pages. Internal links are root-relative and end in a slash: `/formats/native/`, never `formats/native.mdx`.
- **Prose, not a data dump.** Every code sample is preceded by a sentence saying what problem it solves and followed by at least one sentence on what to watch out for.

**API accuracy — non-negotiable**

- Every Kotlin symbol used must exist in `admob-cmp-core/api/admob-cmp-core.klib.api` or `admob-cmp-compose/api/admob-cmp-compose.klib.api`, or be documented in `admob-cmp/AGENTS.md`. The ABI is frozen (`admob-cmp/CLAUDE.md` invariant 12) — if a sample does not compile against 1.1.0, it is wrong, not aspirational.
- **`NativeAdPool.peek()` is NOT public API.** `admob-cmp/AGENTS.md` mentions `peek(token)` when describing internals; it does not appear in the ABI dump. Never write it in a docs code sample.
- `TestAdIds` constants are flat: `TestAdIds.ANDROID_BANNER`. There is no `TestAdIds.Android.*` nesting.
- DSL nodes in `adLayout {}` are **functions with named arguments** (`headline(maxLines = 2)`), not property-assignment blocks.
- Prefer **named arguments** in every sample. Several constructors (`AppOpenConfig`, `NativeAdOptions`) have positional orders that the ABI dump does not disambiguate; named arguments are always correct.
- `AdManager` is an interface with exactly: `status`, `events`, `consent`, `tracking`, `diagnostics`, `initialize(config, mode)`, and the six controller factories `banner`/`interstitial`/`rewarded`/`rewardedInterstitial`/`appOpen`/`nativeAd`. `gatherConsentAndInitialize(config)` is a **suspend extension function** on `AdManager`, not a member.
- Never construct an `AdManager` implementation. Only `rememberAdManager()` (Compose) or `AdMob.manager(context)` (Android, outside Compose) — `admob-cmp/CLAUDE.md` invariant, AGENTS.md hard rule 6.
- Use static, finite `AdPlacement.id`s in every sample. Never generate per-item ids like `"feed_item_$index"` — AGENTS.md hard rule 7.

**Pinned version facts** (from `gradle/libs.versions.toml`, `gradle.properties`, `admob-cmp/README.md`)

| Fact | Value |
|---|---|
| `admob-cmp` | 1.1.0 |
| Kotlin | 2.3.20 |
| Compose Multiplatform | 1.11.1 |
| Android Gradle Plugin | 9.2.1 |
| Android `minSdk` / `compileSdk` / `targetSdk` | 26 / 37 / 36 |
| iOS deployment target | 15.0 |
| Android GMA Next-Gen SDK | 1.3.0 (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk`) |
| Android UMP | 4.0.0 (`com.google.android.ump:user-messaging-platform`) |
| iOS GMA XCFramework / SPM | 13.7.0 / the `13.x` SPM range |
| iOS UMP XCFramework / SPM | 3.1.0 / the `3.x` SPM range |
| Maven group / artifact | `dev.avinya.ads` / `admob-cmp` |
| License | Apache License 2.0 |

**Conservative wording that must not be relaxed**

Quoted verbatim from the Kotlin evolution principles, as recorded in `docs/superpowers/plans/2026-07-29-track3-swiftpm-import-migration.md`:

> "The Kotlin **cinterop** klib binaries are still in Beta. Currently, we cannot give specific compatibility guarantees between different Kotlin versions for cinterop klib binaries."

klib backward compatibility from Kotlin 1.9.20 **explicitly exempts cinterop klibs**, and `admob-cmp` publishes cinterop klibs. `/reference/compatibility/` must therefore say "build with Kotlin 2.3.20; a different Kotlin *minor* may fail to resolve the klib." It must **not** say "2.3.20 or newer".

**Verification commands used throughout**

```bash
# Prose word count for one or more pages (code fences and frontmatter excluded)
node /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site/scripts/wordcount.mjs <files…>

# Full site build — must succeed with no Starlight sidebar warning
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site && npm run build
```

**Commits**

One commit per task, at the end of the task. Message form: `docs(site): write the <page name> guide`.

---

## Page inventory

24 authored pages. `/` (landing) is Plan 5; `/api/` is generated Dokka from Plan 2. Neither is in scope.

| # | URL | File (under `docs-site/src/content/docs/`) | Primary keyword (spec §7) | Diagram | Prose words | Task |
|---|---|---|---|---|---|---|
| 1 | `/start/what-is-admob-cmp/` | `start/what-is-admob-cmp.mdx` | `kmp admob library` | `PlatformMatrix` | 1,100–1,400 | 2 |
| 2 | `/start/quickstart/` | `start/quickstart.mdx` | `compose multiplatform admob` | `InitSequence` | 900–1,200 | 3 |
| 3 | `/start/installation/` | `start/installation.mdx` | `kotlin multiplatform admob` | — | 900–1,200 | 4 |
| 4 | `/start/android-setup/` | `start/android-setup.mdx` | `admob android manifest kotlin multiplatform` | — | 800–1,000 | 5 |
| 5 | `/start/ios-setup/` | `start/ios-setup.mdx` | `kmp ads att idfa` | — | 1,200–1,500 | 6 |
| 6 | `/formats/banner/` | `formats/banner.mdx` | `admob compose multiplatform banner` | `BannerGeometry` | 1,100–1,400 | 7 |
| 7 | `/formats/interstitial/` | `formats/interstitial.mdx` | `compose multiplatform interstitial ads` | `FullScreenLifecycle` | 1,000–1,300 | 8 |
| 8 | `/formats/rewarded/` | `formats/rewarded.mdx` | `kotlin multiplatform rewarded ads` | `FullScreenLifecycle` | 1,000–1,300 | 9 |
| 9 | `/formats/app-open/` | `formats/app-open.mdx` | `kmp app open ads` | `FullScreenLifecycle` | 1,000–1,300 | 10 |
| 10 | `/formats/native/` | `formats/native.mdx` | `compose multiplatform native ads` | `NativePoolLifecycle` | **1,600–2,000** | 11 |
| 11 | `/privacy/consent/` | `privacy/consent.mdx` | `admob consent kotlin multiplatform` | `ConsentDecisionTree` | 1,100–1,400 | 12 |
| 12 | `/privacy/app-tracking-transparency/` | `privacy/app-tracking-transparency.mdx` | `app tracking transparency kotlin multiplatform` | `InitSequence` | 900–1,200 | 13 |
| 13 | `/privacy/play-data-safety/` | `privacy/play-data-safety.mdx` | `admob play data safety ad_id` | — | 800–1,000 | 14 |
| 14 | `/advanced/mediation/` | `advanced/mediation.mdx` | `admob mediation kotlin multiplatform` | — | 900–1,200 | 15 |
| 15 | `/advanced/revenue-events/` | `advanced/revenue-events.mdx` | `admob paid event kotlin multiplatform` | — | 800–1,100 | 16 |
| 16 | `/advanced/caching-retry-timeouts/` | `advanced/caching-retry-timeouts.mdx` | `admob ad caching retry kotlin multiplatform` | `RetryTimeline` + `FullScreenLifecycle` | 1,000–1,300 | 17 |
| 17 | `/advanced/test-safety/` | `advanced/test-safety.mdx` | `admob test ads kotlin multiplatform` | — | 800–1,000 | 18 |
| 18 | `/reference/architecture/` | `reference/architecture.mdx` | `compose multiplatform admob architecture` | `ModuleMap` | 1,200–1,500 | 19 |
| 19 | `/reference/compatibility/` | `reference/compatibility.mdx` | `admob cmp kotlin version compatibility` | `PlatformMatrix` | 800–1,000 | 20 |
| 20 | `/reference/troubleshooting/` | `reference/troubleshooting.mdx` | `admob ios kotlin multiplatform undefined symbols GAD` | — | **1,700–2,100** | 21 |
| 21 | `/reference/changelog/` | `reference/changelog.mdx` | `admob-cmp changelog` | — | 400–700 | 22 |
| 22 | `/project/roadmap/` | `project/roadmap.mdx` | `kotlin multiplatform swiftpm` | — | 1,000–1,300 | 23 |
| 23 | `/project/contributing/` | `project/contributing.mdx` | `admob-cmp contributing` | — | 700–900 | 24 |
| 24 | `/project/ai-agents/` | `project/ai-agents.mdx` | `admob kotlin multiplatform ai agent` | — | 700–900 | 25 |

Plus Task 1 (shared plumbing), Task 26 (legacy redirects), Task 27 (final audit). **27 tasks total.**

Diagram components consumed, all from `docs-site/src/components/diagrams/`:

| Component | Primary page | Reused on |
|---|---|---|
| `ModuleMap.astro` | `/reference/architecture/` | — |
| `InitSequence.astro` | `/privacy/app-tracking-transparency/` | `/start/quickstart/` |
| `FullScreenLifecycle.astro` | `/formats/interstitial/` | `/formats/rewarded/`, `/formats/app-open/`, `/advanced/caching-retry-timeouts/` |
| `NativePoolLifecycle.astro` | `/formats/native/` | — |
| `BannerGeometry.astro` | `/formats/banner/` | — |
| `ConsentDecisionTree.astro` | `/privacy/consent/` | — |
| `RetryTimeline.astro` | `/advanced/caching-retry-timeouts/` | — |
| `PlatformMatrix.astro` | `/reference/compatibility/` | `/start/what-is-admob-cmp/` |

---

### Task 1: Shared plumbing — word-count gate and diagram stubs

Every later task needs two things that do not exist yet: a way to measure prose length against the 800–1,500-word target, and diagram components that may not have been written by Plan 4 yet. Without the stubs, the very first page that imports `BannerGeometry.astro` breaks the build for everyone.

**Files:**
- Create: `docs-site/scripts/wordcount.mjs`
- Create (only if absent): `docs-site/src/components/diagrams/ModuleMap.astro`, `InitSequence.astro`, `FullScreenLifecycle.astro`, `NativePoolLifecycle.astro`, `BannerGeometry.astro`, `ConsentDecisionTree.astro`, `RetryTimeline.astro`, `PlatformMatrix.astro`

**Interfaces:**
- Consumes: `docs-site/` scaffold, `package.json` scripts `build`/`verify`, and the 24 content stubs — all from Plan 2 Tasks 1–3.
- Produces: `node docs-site/scripts/wordcount.mjs <file.mdx…>` printing `<words>\t<path>` per file, exit 0. Used by every page task.
- Produces: eight importable Astro components with **no props**, each usable as `<ComponentName />`. Plan 4 replaces the bodies; the file names and the zero-prop contract are fixed here and must not change.

- [ ] **Step 1: Write the word-count script**

Create `docs-site/scripts/wordcount.mjs`:

```js
#!/usr/bin/env node
/**
 * docs-site/scripts/wordcount.mjs
 *
 * Counts PROSE words in .mdx pages. Frontmatter, fenced code blocks, MDX
 * import statements and JSX tags are stripped first, because the 800-1,500
 * word target in the docs plan is about explanatory text — a page can hit
 * 1,200 raw words while being three code dumps and a table.
 *
 * Usage: node scripts/wordcount.mjs src/content/docs/formats/banner.mdx [...]
 * Output: one "<words>\t<path>" line per file.
 */
import { readFileSync } from 'node:fs';
import { argv, exit } from 'node:process';

const files = argv.slice(2);
if (files.length === 0) {
  console.error('usage: node scripts/wordcount.mjs <file.mdx> [more.mdx ...]');
  exit(2);
}

for (const file of files) {
  const raw = readFileSync(file, 'utf8');
  const prose = raw
    // frontmatter
    .replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, ' ')
    // fenced code blocks (``` and ~~~), non-greedy
    .replace(/^```[\s\S]*?^```/gm, ' ')
    .replace(/^~~~[\s\S]*?^~~~/gm, ' ')
    // MDX imports and JSX tags
    .replace(/^import .*$/gm, ' ')
    .replace(/<[^>]*>/g, ' ')
    // inline code and Markdown punctuation
    .replace(/`[^`]*`/g, ' ')
    .replace(/[|#>*_[\]()\-=+/\\]/g, ' ');

  const words = prose.split(/\s+/).filter((w) => /[A-Za-z0-9]/.test(w));
  console.log(`${words.length}\t${file}`);
}
```

- [ ] **Step 2: Prove the script runs against a Plan 2 stub**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/formats/banner.mdx
```

Expected: a single line like `38	src/content/docs/formats/banner.mdx` — a small number, because the file is still Plan 2's placeholder paragraph. Any number under 100 confirms the script works and confirms the page still needs writing.

- [ ] **Step 3: Create the eight diagram stubs, but only where Plan 4 has not landed**

The `-f` guard is load-bearing: if Plan 4 already wrote a real diagram, this must not clobber it.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
mkdir -p src/components/diagrams

for name in ModuleMap InitSequence FullScreenLifecycle NativePoolLifecycle \
            BannerGeometry ConsentDecisionTree RetryTimeline PlatformMatrix; do
  f="src/components/diagrams/$name.astro"
  if [ -f "$f" ]; then
    echo "KEEP  $f (Plan 4 already wrote it)"
    continue
  fi
  cat > "$f" <<EOF
---
// docs-site/src/components/diagrams/$name.astro
//
// PLACEHOLDER. Plan 4 (visual system) replaces this body with the real
// theme-aware diagram. The file name and the zero-prop contract are fixed by
// Plan 3 and must not change — 24 content pages import it as <$name />.
//
// Plan 3's final audit (Task 27) fails if this placeholder is still present,
// so a stub can never ship to ads.avinya.dev.
---

<figure class="diagram-placeholder" data-diagram="$name">
  <p>Diagram <code>$name</code> is not authored yet.</p>
</figure>

<style>
  .diagram-placeholder {
    margin: 1.5rem 0;
    padding: 1rem;
    border: 2px dashed var(--sl-color-gray-4);
    border-radius: 0.5rem;
    text-align: center;
    color: var(--sl-color-gray-3);
  }
</style>
EOF
  echo "STUB  $f"
done

ls -1 src/components/diagrams/*.astro | wc -l
```

Expected final line: `8`.

- [ ] **Step 4: Build to prove the stubs compile**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm run build
```

Expected: `Complete!` / a successful Astro build with no `[starlight]` sidebar warning.

- [ ] **Step 5: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/scripts/wordcount.mjs docs-site/src/components/diagrams
git commit -m "docs(site): add the prose word-count gate and diagram component stubs"
```

---

### Task 2: `/start/what-is-admob-cmp/` — overview and neutral capability matrix

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/start/what-is-admob-cmp.mdx`
- Read-only sources: `admob-cmp/README.md`, `admob-cmp/AGENTS.md`, spec §6 and §8

**Interfaces:**
- Consumes: `<PlatformMatrix />` from `docs-site/src/components/diagrams/PlatformMatrix.astro` (Task 1 stub / Plan 4 real).
- Produces: canonical `/start/what-is-admob-cmp/`, linked from `/start/quickstart/`, `/reference/compatibility/`, and the Plan 5 landing page.

**Primary keyword:** `kmp admob library`
**Prose target:** 1,100–1,400 words
**Diagram:** `PlatformMatrix`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

Overwrite `docs-site/src/content/docs/start/what-is-admob-cmp.mdx`. `title` is verbatim from Plan 2; `description` supersedes Plan 2's stub value so the primary keyword appears in the SERP snippet.

```mdx
---
title: What is AdMob CMP?
description: AdMob CMP is a KMP AdMob library for Compose Multiplatform — six ad formats, UMP consent and mediation behind one Kotlin API on Android and iOS.
faq:
  - q: What is AdMob CMP?
    a: AdMob CMP is a Kotlin Multiplatform library that wraps the Google Mobile Ads SDKs for Android and iOS behind one Kotlin API, with suspend functions, StateFlow state and a single sealed AdEvent stream.
  - q: Is AdMob CMP a consent management platform?
    a: No. The name predates the ad-tech meaning of CMP. AdMob CMP integrates Google's User Messaging Platform for GDPR consent, but it is an ad SDK wrapper, not a consent management platform vendor.
  - q: Which ad formats does AdMob CMP support?
    a: Banner including collapsible, interstitial, rewarded, rewarded interstitial, app-open and native ads, on both Android and iOS.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import PlatformMatrix from '../../../components/diagrams/PlatformMatrix.astro';

## What is AdMob CMP?

## Which ad formats does it support?

## What does it give you that the raw SDKs do not?

## How does it compare with other Kotlin Multiplatform AdMob libraries?

## Which platforms and versions does it run on?

## What does AdMob CMP deliberately not do?

## Is AdMob CMP a consent management platform?

## Where to next?
```

- [ ] **Step 2: Write "What is AdMob CMP?" and "Which ad formats does it support?"**

The opening two paragraphs must place the primary keyword `kmp admob library` in the first 100 words and state, factually: it is a Compose Multiplatform wrapper over the Google Mobile Ads SDKs — Android GMA Next-Gen SDK 1.3.0 and iOS GMA 13.x; package `dev.avinya.ads`; artifact `dev.avinya.ads:admob-cmp`; Android API 26+, iOS 15+. Say that it keeps AdMob's vocabulary (`AdValue`, response info, adaptive banner sizes, UMP consent states, native asset names) but replaces the listener-style SDK surface with suspend functions, `StateFlow` state, and one sealed `AdEvent` stream.

Then the format table, exactly as verified against `admob-cmp/AGENTS.md`:

```mdx
| Format | Controller | Composable | Guide |
|---|---|---|---|
| Banner (incl. collapsible) | `adManager.banner(placement)` | `BannerAdView` | [Banner ads](/formats/banner/) |
| Interstitial | `adManager.interstitial(placement)` | — | [Interstitial ads](/formats/interstitial/) |
| Rewarded | `adManager.rewarded(placement)` | — | [Rewarded ads](/formats/rewarded/) |
| Rewarded interstitial | `adManager.rewardedInterstitial(placement)` | — | [Rewarded ads](/formats/rewarded/) |
| App-open | `adManager.appOpen(placement)` + `AppOpenAdCoordinator` | — | [App-open ads](/formats/app-open/) |
| Native | `adManager.nativeAd(placement)` (a pool) | `NativeAdView` | [Native ads](/formats/native/) |
```

- [ ] **Step 3: Write "What does it give you that the raw SDKs do not?" with the worked sample**

Explain the four things the library owns so the app does not: the placement-keyed controller cache, the TTL'd FIFO ad cache with retry, the consent gate that makes a pre-consent ad request impossible by construction, and one event stream instead of five callback interfaces. Then show the whole integration — this is the sample that has to convince a reader in ten seconds:

```kotlin
@Composable
fun App() {
    val adManager = rememberAdManager()

    LaunchedEffect(Unit) {
        adManager.gatherConsentAndInitialize(
            AdConfig(
                androidAppId = TestAdIds.ANDROID_APP_ID,
                iosAppId = TestAdIds.IOS_APP_ID,
            )
        )
    }

    val placement = remember {
        AdPlacement(
            id = "main_interstitial",
            format = AdFormat.Interstitial,
            androidAdUnitId = TestAdIds.ANDROID_INTERSTITIAL,
            iosAdUnitId = TestAdIds.IOS_INTERSTITIAL,
        )
    }
    val interstitial = remember(adManager) { adManager.interstitial(placement) }
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            scope.launch {
                interstitial.load()
                interstitial.show()
            }
        }
    ) { Text("Show ad") }
}
```

Follow it with the caveat: `show()` suspends for the ad's whole on-screen lifetime, so it must run in a UI-scoped coroutine and never `GlobalScope`, and a second `show()` on the same controller while one is still on screen returns `AdShowResult.NotReady` rather than queueing.

- [ ] **Step 4: Write the neutral capability matrix**

This section captures `basic-ads alternative` intent. **Rules, and they are not negotiable:** state only verifiable capability facts; use no evaluative adjectives about another project; do not compare code quality, stars, or maintenance; include an honest "when another library fits better" paragraph. Name the alternative exactly once, factually, with a link.

```mdx
| Capability | AdMob CMP | Typical Kotlin Multiplatform AdMob wrapper |
|---|---|---|
| Banner, interstitial, rewarded | Yes | Yes |
| Rewarded interstitial | Yes | Varies |
| App-open ads | Yes | Usually not offered |
| Native ads | Yes, with a layout DSL and an ad pool | Usually not offered |
| UMP consent inside the init flow | Yes, with `ConsentMode` and the privacy options form | Usually a standalone consent call |
| iOS ATT ordering handled explicitly | Yes | Varies |
| Paid/revenue events and mediation response info | Yes | Varies |
| Kotlin/Native test linking on iOS | Solved by a published Gradle plugin | Usually unaddressed |
| iOS distribution model | Bindings-only cinterop; the app links GMA via SPM | Varies |
```

Then a paragraph of the form: the closest alternative in this space is [`LexiLabs-App/basic-ads`](https://github.com/LexiLabs-App/basic-ads). It covers banner, interstitial and rewarded formats. If those three formats are all a project needs and its API shape suits the codebase better, it is a reasonable choice. AdMob CMP exists for projects that also need native ads, app-open ads, consent wired into initialization, or Kotlin/Native tests that link on iOS.

- [ ] **Step 5: Write the remaining sections and embed the diagram**

- **"Which platforms and versions does it run on?"** — one paragraph plus `<PlatformMatrix />` immediately after it, then the sentence that Android is API 26+ with the GMA Next-Gen SDK arriving transitively, and iOS is 15.0+ with GMA and UMP resolved by the app through Swift Package Manager. Link to `/reference/compatibility/` for the version table.
- **"What does AdMob CMP deliberately not do?"** — it ships zero mediation adapters by design (link `/advanced/mediation/`); it never embeds Google's binaries on iOS, only cinterop bindings (link `/reference/architecture/`); it is consumable from KMP/Gradle projects only, so a pure-Swift iOS app cannot adopt it without a KMP shim; it does not support ad networks other than AdMob directly — mediation covers that.
- **"Is AdMob CMP a consent management platform?"** — short and direct. In ad-tech, CMP normally means Consent Management Platform. This library's name is older than that association in the project's own history and is retained for continuity with the published Maven coordinate `dev.avinya.ads:admob-cmp`. It integrates Google's User Messaging Platform to gather GDPR/TCF consent; it is not itself a CMP vendor. Link `/privacy/consent/`.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Quickstart" href="/start/quickstart/" description="Render a test ad in five minutes." />
  <LinkCard title="Installation" href="/start/installation/" description="Gradle, version catalog, and the Gradle plugin." />
  <LinkCard title="Native ads" href="/formats/native/" description="The layout DSL and ad pooling." />
  <LinkCard title="Compatibility" href="/reference/compatibility/" description="Kotlin, Compose, minSdk and iOS versions." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/start/what-is-admob-cmp.mdx
npm run build
```

Expected: a word count between `1100` and `1400`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/start/what-is-admob-cmp.mdx
git commit -m "docs(site): write the What is AdMob CMP overview and capability matrix"
```

---

### Task 3: `/start/quickstart/` — five minutes to a rendering test ad

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/start/quickstart.mdx`
- Read-only sources: `admob-cmp/docs/SETUP.md`, `admob-cmp/AGENTS.md`

**Interfaces:**
- Consumes: `<InitSequence />` from `docs-site/src/components/diagrams/InitSequence.astro`.
- Produces: canonical `/start/quickstart/` — the primary CTA target of the Plan 5 landing page. Every "get started" link on the site points here.

**Primary keyword:** `compose multiplatform admob`
**Prose target:** 900–1,200 words
**Diagram:** `InitSequence`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Quickstart
description: The fastest Compose Multiplatform AdMob setup — render a test ad in five minutes using Google's sample ad units, with no AdMob account required.
faq:
  - q: Do I need an AdMob account to try AdMob CMP?
    a: No. TestAdIds ships Google's official sample app ids and ad-unit ids for every format on both platforms, so you can render a test ad before you have an account.
  - q: Why is my ad not showing yet?
    a: Ad requests are gated on initialization and consent. Until AdManager.status is AdManagerStatus.Ready, loads fail fast with AdErrorCode.SDK_NOT_READY and nothing reaches the network.
---

import { Aside, Steps, CardGrid, LinkCard } from '@astrojs/starlight/components';
import InitSequence from '../../../components/diagrams/InitSequence.astro';

## What will you have at the end?

## How do I add the dependency?

## How do I initialize AdMob in Compose Multiplatform?

## How do I show my first banner?

## Why is nothing rendering yet?

## What changes before you ship?

## Where to next?
```

- [ ] **Step 2: Write the dependency step**

State that the quickstart deliberately uses Google's sample ad units so no AdMob account is needed, and that `/start/installation/` covers version catalogs and the multi-module case. Then:

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.avinya.ads:admob-cmp:1.1.0")
        }
    }
}
```

Add an `<Aside type="note">` saying that if the project runs Kotlin/Native tests (`:shared:iosSimulatorArm64Test`), it also needs the `dev.avinya.ads.admob-cmp` Gradle plugin, and link `/start/installation/` and `/reference/troubleshooting/`.

- [ ] **Step 3: Write the initialization step and embed the sequence diagram**

Introduce the call, then:

```kotlin
@Composable
fun App() {
    val adManager = rememberAdManager()

    LaunchedEffect(Unit) {
        adManager.gatherConsentAndInitialize(
            AdConfig(
                androidAppId = TestAdIds.ANDROID_APP_ID,
                iosAppId = TestAdIds.IOS_APP_ID,
                testMode = true,
            )
        )
    }

    val status by adManager.status.collectAsState()
    if (status is AdManagerStatus.Ready) {
        AdScreen()
    }
}
```

Explain each moving part: `rememberAdManager()` returns a process-wide singleton; `gatherConsentAndInitialize` runs the UMP consent flow, invokes any initialization hooks, then starts Mobile Ads; and `status` is the gate — `AdManagerStatus` is `Idle`, `Initializing`, `ConsentRequired`, `Ready`, `Disabled(reason)`, or `Failed(error, retryable)`.

Immediately after, place `<InitSequence />` and one sentence: on iOS the order matters — UMP consent, then App Tracking Transparency, then the first ad request — and `/privacy/app-tracking-transparency/` explains why requesting ads before ATT resolves permanently forfeits the IDFA for those requests.

Add an `<Aside type="caution">` reproducing the `testMode` trap verbatim in substance: `AdDebugOptions.testMode` configures UMP consent debugging only. It does **not** make GMA serve test ads. Only registering the device in `GlobalRequestConfiguration.testDeviceIds`, or using a `TestAdIds` unit, does that. Link `/advanced/test-safety/`.

- [ ] **Step 4: Write the first-banner step**

```kotlin
val placement = remember {
    AdPlacement(
        id = "home_banner",
        format = AdFormat.Banner,
        androidAdUnitId = TestAdIds.ANDROID_BANNER,
        iosAdUnitId = TestAdIds.IOS_BANNER,
        strictTestMode = true,
    )
}

BannerAdView(
    placement = placement,
    modifier = Modifier.fillMaxWidth(),
    onEvent = { event ->
        when (event) {
            is AdEvent.Loaded -> println("banner loaded")
            is AdEvent.LoadFailed -> println("banner failed: ${event.error}")
            else -> Unit
        }
    },
)
```

Explain that `BannerAdView` measures its own container, supplies the width, loads, attaches, refreshes and disposes the platform view; that `strictTestMode = true` throws at construction if the placement points at a production ad unit, which is exactly what you want in a debug build; and that `remember` is required because controllers are placement-keyed and cached — see AGENTS.md hard rule 1.

- [ ] **Step 5: Write "Why is nothing rendering yet?" and "What changes before you ship?"**

"Why is nothing rendering yet?" is a compact triage list, each item one or two sentences: the manager is not `Ready` yet (gate the UI on `status`); consent has not been granted so loads fail with `AdErrorCode.CONSENT_REQUIRED`; the Android manifest is missing `com.google.android.gms.ads.APPLICATION_ID`, which crashes GMA at startup (link `/start/android-setup/`); the iOS app has not added the two Swift Package Manager packages (link `/start/ios-setup/`); the banner placement uses `BannerRefreshPolicy.Manual` and nothing called `refresh()`. Close with a link to `/reference/troubleshooting/`.

"What changes before you ship?" — replace both sample app ids and every sample ad-unit id with real ones; set `testMode = false`; keep `strictTestMode` on in debug builds only; complete the Play Data safety declaration (link `/privacy/play-data-safety/`); and add `NSUserTrackingUsageDescription` on iOS or every request serves non-personalised ads at materially lower eCPM.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Installation" href="/start/installation/" description="Version catalogs, modules, and the Gradle plugin." />
  <LinkCard title="iOS setup" href="/start/ios-setup/" description="SPM packages, Info.plist, ATT, and doctorIos." />
  <LinkCard title="UMP consent" href="/privacy/consent/" description="Consent modes and the privacy options form." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="Symptom to cause to fix." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/start/quickstart.mdx
npm run build
```

Expected: a word count between `900` and `1200`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/start/quickstart.mdx
git commit -m "docs(site): write the quickstart guide"
```

---

### Task 4: `/start/installation/` — Gradle, version catalog, and the Gradle plugin

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/start/installation.mdx`
- Read-only sources: `admob-cmp/docs/SETUP.md` §1 and "Kotlin/Native test executables", `admob-cmp/README.md`

**Interfaces:**
- Consumes: nothing from Task 1 beyond the word-count script.
- Produces: canonical `/start/installation/`, the target of every "how do I add it" link on the site and in the root README.

**Primary keyword:** `kotlin multiplatform admob`
**Prose target:** 900–1,200 words
**Diagram:** none

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Installation
description: Add the Kotlin Multiplatform AdMob library dev.avinya.ads:admob-cmp with a version catalog, plus the Gradle plugin that fixes iOS test linking.
faq:
  - q: Which Gradle dependency do I add for AdMob on Kotlin Multiplatform?
    a: Add dev.avinya.ads:admob-cmp:1.1.0 to commonMain. The Android Google Mobile Ads Next-Gen SDK and UMP arrive as transitive Maven dependencies.
  - q: Do I need the dev.avinya.ads.admob-cmp Gradle plugin?
    a: Only if your project links Kotlin/Native test executables for iOS. Without it those links fail with undefined _OBJC_CLASS_$_GAD symbols, because a Kotlin/Native test binary cannot use Swift Package Manager.
  - q: Which Kotlin version does admob-cmp 1.1.0 require?
    a: It is compiled with Kotlin 2.3.20. Because it publishes cinterop klibs, consumers on a different Kotlin minor version may fail to resolve the klib.
---

import { Aside, Tabs, TabItem, CardGrid, LinkCard } from '@astrojs/starlight/components';

## How do I add AdMob CMP to a Kotlin Multiplatform project?

## Should I use a version catalog?

## Which modules do I put the dependency in?

## Why do my iOS tests fail to link, and what fixes it?

## What does the Gradle plugin actually do?

## How do I verify the installation?

## Where to next?
```

- [ ] **Step 2: Write the dependency and version-catalog sections**

Open with the primary keyword in the first sentence. State that the artifact is a single umbrella coordinate that brings both the engine and the Compose surface, that the Android GMA Next-Gen SDK and UMP arrive as transitive Maven dependencies so no extra Android dependency is needed, and that iOS deliberately links Google's frameworks in the app, not in the artifact.

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.avinya.ads:admob-cmp:1.1.0")
        }
    }
}
```

Then the version-catalog form, which is what a real multi-module build should use:

```toml
# gradle/libs.versions.toml
[versions]
admob-cmp = "1.1.0"

[libraries]
admob-cmp = { module = "dev.avinya.ads:admob-cmp", version.ref = "admob-cmp" }

[plugins]
admob-cmp = { id = "dev.avinya.ads.admob-cmp", version.ref = "admob-cmp" }
```

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation(libs.admob.cmp)
}
```

Note the reason the catalog pays off here: the library coordinate and the Gradle plugin id must stay on the same version, and a single `version.ref` makes that impossible to get wrong.

- [ ] **Step 3: Write "Which modules do I put the dependency in?"**

Explain the practical rule: put it in the shared module whose `commonMain` calls the ads API, once. Because controllers are placement-keyed and the manager is a process-wide singleton, adding it to several modules does not create several managers — but it does widen the set of modules whose Kotlin/Native test binaries need the Gradle plugin. State that Compose Multiplatform is required only if the composable surface (`BannerAdView`, `NativeAdView`, `rememberAdManager`) is used; the controller API has no Compose dependency.

- [ ] **Step 4: Write the Kotlin/Native test-linking section — the highest-value part of this page**

This is the section that earns the page its inbound links. State the mechanism plainly: the app links Google's frameworks through Swift Package Manager, but tests do not. `./gradlew :yourModule:iosSimulatorArm64Test` makes the Kotlin/Native compiler link a standalone executable with no Xcode, no `.xcodeproj` and no SPM anywhere in the picture, so every symbol must resolve at that link — including the `GAD*` and `UMP*` classes these bindings reference. Emphasise that this applies **even if none of the tests touch ads**, because the test binary contains the module's whole main compilation, so any production code calling `rememberAdManager`, `NativeAdView` or the consent APIs drags those references in. Add that a `FakeAdManager` does not help, because the requirement comes from the bindings being present in the link rather than from anyone calling them — and that for faking ad *behaviour* the SDK ships `NoOpAdManager`.

The fix:

```kotlin
// shared/build.gradle.kts
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}
```

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Close with the `<Aside type="danger">`: adding another Swift Package Manager package will **not** fix a `:linkDebugTestIos…` failure. Only the Gradle plugin does. Link `/reference/troubleshooting/`.

- [ ] **Step 5: Write "What does the Gradle plugin actually do?" and "How do I verify the installation?"**

Describe the plugin's behaviour exactly as implemented: it registers `downloadGmaIos` and `downloadUmpIos`, which fetch the version-stamped GoogleMobileAds and UserMessagingPlatform XCFrameworks into `build/admob-cmp-ios-frameworks/` with a SHA-256 check; it applies linker options **to test executables only** (`-framework GoogleMobileAds`, `-framework UserMessagingPlatform`, `-framework JavaScriptCore`, plus the Swift compatibility library directory); it supports the `iosArm64` and `iosSimulatorArm64` targets; and it deliberately leaves the shipped app framework alone, because Kotlin/Native is supposed to leave those symbols undefined there for Xcode to bind via SPM. Add that applying it also registers `doctorIos`.

Verification:

```bash
./gradlew :admob-cmp-core:doctorIos          # report-only; prints per-check status
./gradlew :shared:iosSimulatorArm64Test      # must link and run
./gradlew :shared:testAndroidHostTest        # JVM-side check
```

Note that `doctorIos` never fails the build — it is diagnostic only — and that it checks the framework download cache, whether the Xcode project references the SPM products, and whether `Info.plist` declares `GADApplicationIdentifier` and `SKAdNetworkItems`.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Android setup" href="/start/android-setup/" description="Manifest, AD_ID, and Play Data safety." />
  <LinkCard title="iOS setup" href="/start/ios-setup/" description="SPM packages, Info.plist, ATT, and JavaScriptCore." />
  <LinkCard title="Compatibility" href="/reference/compatibility/" description="Kotlin, Compose, minSdk and iOS versions." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="Undefined GAD symbols and other link failures." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/start/installation.mdx
npm run build
```

Expected: a word count between `900` and `1200`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/start/installation.mdx
git commit -m "docs(site): write the installation guide"
```

---

### Task 5: `/start/android-setup/` — manifest, AD_ID, and build configuration

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/start/android-setup.mdx`
- Read-only sources: `admob-cmp/docs/SETUP.md` §2, `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: nothing beyond the word-count script.
- Produces: canonical `/start/android-setup/`, linked from `/start/quickstart/`, `/start/installation/`, `/privacy/play-data-safety/`, `/reference/troubleshooting/`.

**Primary keyword:** `admob android manifest kotlin multiplatform`
**Prose target:** 800–1,000 words
**Diagram:** none

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Android setup
description: Android setup for AdMob on Kotlin Multiplatform — the manifest APPLICATION_ID entry, the AD_ID permission, minSdk 26, and how to verify it works.
faq:
  - q: Why does my Android app crash at startup after adding AdMob?
    a: The Google Mobile Ads SDK crashes on launch when the manifest has no com.google.android.gms.ads.APPLICATION_ID meta-data entry. Add it with your AdMob app id.
  - q: Do I need the AD_ID permission for AdMob on Android?
    a: The Google Mobile Ads SDK merges com.google.android.gms.permission.AD_ID into your manifest automatically. Apps targeting API 33 or higher that remove it cannot access the advertising ID, which reduces revenue.
  - q: What is the minimum Android API level for AdMob CMP?
    a: API 26. The library targets Android API 26 and above and depends on the Google Mobile Ads Next-Gen SDK.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';

## What does Android need beyond the Gradle dependency?

## How do I add the AdMob app id to the manifest?

## What is the AD_ID permission and do I need it?

## What are the Android build requirements?

## How do I confirm Android is wired up correctly?

## Where to next?
```

- [ ] **Step 2: Write the manifest section**

Lead with the primary keyword. State that Android needs exactly one thing beyond the dependency — the app id in the manifest — and that omitting it is not a soft failure: the Google Mobile Ads SDK crashes at startup.

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-3940256099942544~3347511713" /> <!-- sample id; replace -->
</application>
```

Make three points after the sample: this is the **app** id (`ca-app-pub-…~…`, tilde separator), not an ad-unit id (`ca-app-pub-…/…`, slash separator); the value shown is Google's public sample and must be replaced before release; and in a Kotlin Multiplatform project this entry belongs to the Android application module's manifest, not the shared module — the shared module is where the Gradle dependency lives, but manifest merging happens in the app.

Note that `admob-cmp` itself declares only `INTERNET`.

- [ ] **Step 3: Write the AD_ID section**

Explain that the Google Mobile Ads SDK merges `com.google.android.gms.permission.AD_ID` into the manifest, that apps targeting API 33+ which do not declare it cannot access the advertising ID, and that the permission comes from GMA rather than from `admob-cmp`. Then give the opt-out and be explicit about its cost:

```xml
<uses-permission
    android:name="com.google.android.gms.permission.AD_ID"
    tools:node="remove" />
```

Follow with: this reduces ad revenue and is not recommended unless the app is child-directed or there is another compliance reason. Removing the permission also changes what must be declared in the Play Data safety form — link `/privacy/play-data-safety/`.

- [ ] **Step 4: Write the build-requirements section**

Give the pinned facts as a table, and say plainly that these are the values `admob-cmp` 1.1.0 is built and tested against:

```mdx
| Setting | Value |
|---|---|
| `minSdk` | 26 |
| `compileSdk` | 37 |
| `targetSdk` | 36 |
| Android Gradle Plugin | 9.2.1 |
| Google Mobile Ads Next-Gen SDK | 1.3.0 (transitive) |
| User Messaging Platform | 4.0.0 (transitive, also pulled by GMA) |
```

Add the repository note that trips real builds: the GMA Next-Gen SDK pulls Cronet from `org.chromium.net`, which is served by Google's Maven repository rather than Maven Central, so `google()` must be present in `dependencyResolutionManagement`. Show it:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

- [ ] **Step 5: Write the verification section and "Where to next?"**

Verification is short and concrete: build and install the Android app, gate the UI on `AdManagerStatus.Ready`, and confirm an ad renders. Mention that on Android, outside Compose, the manager is reachable as `AdMob.manager(context)` and that it is the same process-wide singleton `rememberAdManager()` returns — never construct one. Point at `/reference/troubleshooting/` for the symptom table, and note that Android emits no native video events because the GMA Next-Gen SDK exposes no equivalent callback surface, so cross-platform logic must not depend on them.

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="iOS setup" href="/start/ios-setup/" description="SPM packages, Info.plist, ATT, and doctorIos." />
  <LinkCard title="Play Data safety" href="/privacy/play-data-safety/" description="What to declare in the Play Console form." />
  <LinkCard title="Test safety" href="/advanced/test-safety/" description="testMode versus strictTestMode." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="Symptom to cause to fix." />
</CardGrid>
```

- [ ] **Step 6: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/start/android-setup.mdx
npm run build
```

Expected: a word count between `800` and `1000`, then a successful build.

- [ ] **Step 7: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/start/android-setup.mdx
git commit -m "docs(site): write the Android setup guide"
```

---

### Task 6: `/start/ios-setup/` — SPM, Info.plist, ATT, JavaScriptCore, doctorIos

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/start/ios-setup.mdx`
- Read-only sources: `admob-cmp/docs/SETUP.md` §3, `admob-cmp/AGENTS.md` "iOS setup (executable steps)", `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/DoctorIosTask.kt`

**Interfaces:**
- Consumes: nothing beyond the word-count script.
- Produces: canonical `/start/ios-setup/`, linked from `/start/quickstart/`, `/start/installation/`, `/privacy/app-tracking-transparency/`, `/reference/troubleshooting/`, `/reference/architecture/`.

**Primary keyword:** `kmp ads att idfa`
**Prose target:** 1,200–1,500 words
**Diagram:** none — the sequence diagram lives on `/privacy/app-tracking-transparency/`, which this page links to

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: iOS setup
description: iOS setup for KMP ads, ATT and IDFA — the two Swift Package Manager packages, Info.plist keys, JavaScriptCore linking, and the doctorIos check.
faq:
  - q: Why does AdMob CMP need Swift Package Manager packages on iOS?
    a: The published artifact contains cinterop bindings only, never Google's binaries. Your app links the real GoogleMobileAds and UserMessagingPlatform frameworks itself, which keeps mediation adapters working and lets you take GMA patch releases independently.
  - q: What causes Undefined symbol _OBJC_CLASS_$_JSContext on iOS?
    a: GoogleMobileAds needs JavaScriptCore, and a static Kotlin framework does not autolink it. Add -framework JavaScriptCore to OTHER_LDFLAGS in the app target's build settings.
  - q: Do I need NSUserTrackingUsageDescription for AdMob on iOS?
    a: Yes if you want personalised ads. Without the key the ATT prompt cannot be shown, iOS withholds the IDFA, and every request serves non-personalised ads at materially lower eCPM.
---

import { Aside, Steps, CardGrid, LinkCard } from '@astrojs/starlight/components';

## Why does iOS need extra setup at all?

## Which Swift Package Manager packages do I add?

## What goes in Info.plist?

## How do I enable App Tracking Transparency and keep the IDFA?

## Why do I need to link JavaScriptCore?

## How do I verify the iOS setup?

## What about Kotlin/Native test executables?

## Where to next?
```

- [ ] **Step 2: Write "Why does iOS need extra setup at all?"**

This is the section that turns a chore into a design decision the reader agrees with. State it directly: the published artifact contains cinterop **bindings** only — never Google's binaries. The app links the real GMA and UMP frameworks itself via Swift Package Manager. That buys three things: mediation adapters keep working because there is exactly one copy of the Objective-C classes in the process; GMA patch releases can be taken without waiting for this library; and there is no redistribution question about Google's closed-source binary. The cost is honest and stated: two Swift packages must be added to the Xcode project, and the SPM-resolved GMA major version must match the major this library binds. Link `/reference/architecture/` for the full rationale.

- [ ] **Step 3: Write the SPM section**

```mdx
<Steps>

1. In Xcode, choose **File → Add Package Dependencies**.

2. Add `https://github.com/googleads/swift-package-manager-google-mobile-ads.git` and select the **GoogleMobileAds** product. Use version **13.x** — it must match the major version this library binds.

3. Add `https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git` and select the **GoogleUserMessagingPlatform** product, version **3.x**.

</Steps>
```

Follow with the pin note: `admob-cmp` 1.1.0 binds GMA iOS 13.7.0 and UMP iOS 3.1.0 headers. A GMA SPM package older than the bound headers produces link errors on newly-added API symbols such as `GADCurrentOrientation…`; the fix is to bump the SPM package, not to downgrade the library.

- [ ] **Step 4: Write the Info.plist section**

```xml
<key>GADApplicationIdentifier</key>
<string>ca-app-pub-3940256099942544~1458002511</string><!-- sample id; replace -->

<key>SKAdNetworkItems</key>
<array>
    <dict>
        <key>SKAdNetworkIdentifier</key>
        <string>cstr6suwn9.skadnetwork</string>
    </dict>
    <!-- copy the full current list from the AdMob iOS documentation -->
</array>
```

State that these go in the **app target's** `Info.plist`; that `GADApplicationIdentifier` is the app id with a tilde, not an ad-unit id; that the sample id must be replaced before release; and that an incomplete `SKAdNetworkItems` list degrades attribution rather than breaking the build, which is why it is easy to forget and worth checking with `doctorIos`.

- [ ] **Step 5: Write the ATT section**

This carries the page's primary keyword, `kmp ads att idfa`. Give the key first:

```xml
<key>NSUserTrackingUsageDescription</key>
<string>This identifier will be used to deliver personalised ads to you.</string>
```

Then the hard fact, stated once and unambiguously: **without this key the ATT prompt cannot be shown and iOS withholds the IDFA**, so every request serves non-personalised ads at materially lower eCPM. Then the call order, which is the actual trap:

```kotlin
adManager.consent.gatherConsent(config)
adManager.tracking.requestAuthorization()
adManager.initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)
```

Explain that requesting ads before ATT resolves permanently forfeits the IDFA for those requests, that Android has no ATT so `adManager.tracking` is a no-op there and always reports `AdTrackingAuthorization.NotApplicable`, and that the same ordering can be expressed with a hook instead of three calls:

```kotlin
AdConfig(
    androidAppId = "ca-app-pub-…",
    iosAppId = "ca-app-pub-…",
    initializationHooks = listOf(
        object : AdInitializationHook {
            override suspend fun onPhase(phase: AdInitializationPhase, config: AdConfig) {
                if (phase == AdInitializationPhase.BeforeMobileAdsInitialize) {
                    adManager.tracking.requestAuthorization()
                }
            }
        }
    ),
)
```

Link `/privacy/app-tracking-transparency/` for the sequence diagram and the full explanation.

- [ ] **Step 6: Write the JavaScriptCore, verification, and test-executable sections**

**JavaScriptCore.** If the Kotlin framework is static and no Swift file imports `GoogleMobileAds`, nothing triggers autolinking of JavaScriptCore, which GMA needs. Add `-framework JavaScriptCore` to the app target's `OTHER_LDFLAGS`. The symptom when it is missing is `Undefined symbol: _OBJC_CLASS_$_JSContext`.

**Verification.**

```bash
./gradlew :admob-cmp-core:doctorIos
```

List what it reports, matching the implementation exactly: whether the GoogleMobileAds and UserMessagingPlatform XCFramework download caches are present; whether the Xcode project references each SPM product; whether `Info.plist` declares `GADApplicationIdentifier`, and whether that value is still Google's sample id; and whether `SKAdNetworkItems` is present. Add that it is diagnostic only and never fails the build, and that the Xcode project directory can be overridden with `-PadmobCmp.xcodeproj=<dir>`.

**Kotlin/Native test executables.** Keep this short and route the reader — one paragraph saying a Kotlin/Native test link has no Xcode and therefore cannot use SPM, that the `dev.avinya.ads.admob-cmp` Gradle plugin is the supported fix, and that adding another SPM package will not help. Link `/start/installation/` and `/reference/troubleshooting/`.

- [ ] **Step 7: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="App Tracking Transparency" href="/privacy/app-tracking-transparency/" description="The consent to ATT to initialize sequence." />
  <LinkCard title="UMP consent" href="/privacy/consent/" description="Consent modes and canRequestAds." />
  <LinkCard title="Architecture" href="/reference/architecture/" description="Why iOS ships bindings only." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="Undefined GAD and JSContext symbols." />
</CardGrid>
```

- [ ] **Step 8: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/start/ios-setup.mdx
npm run build
```

Expected: a word count between `1200` and `1500`, then a successful build.

- [ ] **Step 9: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/start/ios-setup.mdx
git commit -m "docs(site): write the iOS setup guide"
```

---

### Task 7: `/formats/banner/` — adaptive sizes, collapsible, refresh, geometry

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/formats/banner.mdx`
- Read-only sources: `admob-cmp/docs/BANNER.md`, `admob-cmp/AGENTS.md` "Banner" and "Banner geometry (headless callers)"

**Interfaces:**
- Consumes: `<BannerGeometry />` from `docs-site/src/components/diagrams/BannerGeometry.astro`.
- Produces: canonical `/formats/banner/`, linked from `/start/quickstart/`, `/start/what-is-admob-cmp/`, `/reference/troubleshooting/`, and the Plan 5 format grid.

**Primary keyword:** `admob compose multiplatform banner`
**Prose target:** 1,100–1,400 words
**Diagram:** `BannerGeometry`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Banner ads
description: AdMob banners in Compose Multiplatform — BannerAdView, adaptive and collapsible size policies, refresh policies, and how banner width is resolved.
faq:
  - q: How do I show an AdMob banner in Compose Multiplatform?
    a: Declare an AdPlacement with AdFormat.Banner and render BannerAdView. The composable measures its container, loads, sizes, attaches, refreshes and disposes the platform banner view for you.
  - q: How do I make a collapsible banner?
    a: Collapsible is a property of the anchored adaptive size policy. Set bannerSizePolicy to AdSizePolicy.LargeAnchoredAdaptive(collapsible = CollapsiblePlacement.Bottom), and test with the dedicated collapsible test ad units.
  - q: Why is my banner blank?
    a: Either the ad manager is not Ready yet, or the placement uses BannerRefreshPolicy.Manual and nothing has called refresh().
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import BannerGeometry from '../../../components/diagrams/BannerGeometry.astro';

## How do I show a banner ad in Compose Multiplatform?

## Which banner size should I use?

## How do collapsible banners work?

## How often does a banner refresh?

## How is the banner width resolved?

## Can I use banners without Compose?

## Why is my banner blank?

## Where to next?
```

- [ ] **Step 2: Write the basic usage section**

Open with the primary keyword. Make the key point up front: size and refresh behaviour come from the **placement**, not from composable parameters, which is why the same banner behaves identically whether it is driven from Compose or headlessly.

```kotlin
val placement = remember {
    AdPlacement(
        id = "banner_home",
        format = AdFormat.Banner,
        adUnitIds = AdUnitIds(
            android = TestAdIds.ANDROID_BANNER,
            ios = TestAdIds.IOS_BANNER,
        ),
    )
}

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
    },
)
```

Then explain what `BannerAdView` does on the reader's behalf: resolves the width from its constraints (overridable with the `widthDp` parameter), sizes its height from the returned ad size, and clears the controller on dispose. Note that `remember` around the placement matters because controllers are cached per placement id for the manager's lifetime and are never auto-evicted, so ids must be static and finite.

- [ ] **Step 3: Write the size-policy section**

```kotlin
AdPlacement(
    id = "banner_home",
    format = AdFormat.Banner,
    adUnitIds = AdUnitIds(android = "…", ios = "…"),
    bannerSizePolicy = AdSizePolicy.LargeAnchoredAdaptive(),   // default
)
```

```mdx
| Policy | What it maps to | Use it when |
|---|---|---|
| `AdSizePolicy.LargeAnchoredAdaptive(collapsible)` | Large anchored adaptive | The banner is pinned to the top or bottom of the screen. This is the default. |
| `AdSizePolicy.InlineAdaptive(maxHeightDp)` | Inline adaptive | The banner sits inside scrolling content and may be taller. |
| `AdSizePolicy.Fixed(widthDp, heightDp)` | A fixed custom size | You need an exact size and accept lower fill. |
| `AdSizePolicy.Fluid` | Fluid | The container dictates the size. |
```

Add the practical guidance: anchored adaptive is the right default because it lets AdMob pick the best height for the device width; inline adaptive is for feeds, where `maxHeightDp` bounds how much of the viewport an ad may take; fixed sizes reduce eligible demand and should be a deliberate choice.

- [ ] **Step 4: Write the collapsible and refresh sections**

Collapsible:

```kotlin
bannerSizePolicy = AdSizePolicy.LargeAnchoredAdaptive(
    collapsible = CollapsiblePlacement.Bottom,
)
```

State that `CollapsiblePlacement` is `Top` or `Bottom`, that collapsible is a property of the anchored adaptive policy rather than a separate format, and — the part that wastes people's afternoons — that **regular banner test ids never serve collapsible fill**. Testing requires `TestAdIds.ANDROID_COLLAPSIBLE_BANNER` and `TestAdIds.IOS_COLLAPSIBLE_BANNER`.

Refresh:

```kotlin
AdPlacement(
    id = "banner_home",
    format = AdFormat.Banner,
    adUnitIds = AdUnitIds(android = "…", ios = "…"),
    bannerRefreshPolicy = BannerRefreshPolicy.SdkManaged(60.seconds),
)
```

```mdx
| Policy | Behaviour |
|---|---|
| `BannerRefreshPolicy.AdServerManaged` | The default. No client timer; configure refresh in the AdMob UI. |
| `BannerRefreshPolicy.SdkManaged(interval)` | Client-side reload every `interval`, enforced to the 30s–120s range, only while the app is foregrounded, and it waits for an in-flight load to settle. |
| `BannerRefreshPolicy.Manual` | No automatic load at all. Call `adManager.banner(placement).refresh()` yourself. |
```

Add an `<Aside type="caution">`: with `Manual`, nothing loads until `refresh()` is called, and `refresh()` fails if nothing has been loaded yet — so a `Manual` banner that has never been composed and loaded renders nothing. This is the single most common "my banner is blank" cause.

- [ ] **Step 5: Write the geometry section and embed the diagram**

This section is why the page exists as more than a size table. Explain that a banner's width is an **input**, not something the SDK can always discover. `BannerAdView` measures its own container and supplies it. A headless caller must supply it:

```kotlin
adManager.banner(placement).load(geometry = BannerGeometry(widthDp = 320))
```

Place `<BannerGeometry />` immediately after that sample, then explain the history honestly, because it changes behaviour readers may have relied on: `load()` previously took `(sizePolicy, requestOptions)` and resolved its own width — from an `Activity` on Android and from `UIScreen.mainScreen` on iOS. The iOS path silently produced full-screen width in iPad split view, Slide Over and popovers, sizing every banner wrong with no error. Width is now a host-supplied input: `load(geometry, sizePolicy, requestOptions)`. Existing no-argument `load()` calls still compile because `geometry` defaults to `null`, but a headless call with no geometry now **fails** rather than guessing when the platform cannot resolve a width.

Add the `refresh()` semantics: `refresh()` replays the **whole** resolved request — geometry, size policy and request options — from the most recent `load()`. It previously kept only the resolved size and rebuilt options from `placement.requestOptions`, silently dropping any custom `AdRequestOptions` the original `load()` was given. It fails if nothing has been loaded yet.

- [ ] **Step 6: Write "Can I use banners without Compose?" and "Why is my banner blank?"**

Headless: `adManager.banner(placement)` returns a `BannerAdController` with `load(geometry, sizePolicy, requestOptions)`, `refresh()`, `clear()`, `loadState: StateFlow<AdLoadState>`, `events: SharedFlow<AdEvent>`, and `placement`. Say plainly that the controller does not host a view — the caller is responsible for that — and that `BannerAdView` is the recommended path for anything Compose-rendered.

Blank-banner triage, as a short list with one line each: the manager is not `AdManagerStatus.Ready`; consent has not been granted, so loads fail with `AdErrorCode.CONSENT_REQUIRED`; the policy is `Manual` and nothing called `refresh()`; the ad unit has no fill right now, which surfaces as GMA code `3` on Android and `1` on iOS and is normal rather than an error to retry aggressively; a headless `load()` was issued with no geometry on a platform that cannot resolve a width. Link `/reference/troubleshooting/`.

- [ ] **Step 7: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Native ads" href="/formats/native/" description="Ads that match your own layout." />
  <LinkCard title="Caching, retry and timeouts" href="/advanced/caching-retry-timeouts/" description="Retry backoff and load bounds." />
  <LinkCard title="Revenue and paid events" href="/advanced/revenue-events/" description="AdValue and the mediation chain." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="Blank banners and no fill." />
</CardGrid>
```

- [ ] **Step 8: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/formats/banner.mdx
npm run build
```

Expected: a word count between `1100` and `1400`, then a successful build.

- [ ] **Step 9: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/formats/banner.mdx
git commit -m "docs(site): write the banner ads guide"
```

---

### Task 8: `/formats/interstitial/` — load, show, cache, retry

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/formats/interstitial.mdx`
- Read-only sources: `admob-cmp/docs/INTERSTITIAL.md`, `admob-cmp/AGENTS.md` "Full-screen pattern"

**Interfaces:**
- Consumes: `<FullScreenLifecycle />` from `docs-site/src/components/diagrams/FullScreenLifecycle.astro`.
- Produces: canonical `/formats/interstitial/`. It is the **reference page for the shared `FullScreenAdController` contract** — `/formats/rewarded/` and `/formats/app-open/` link here for `load`/`show`/`availability` semantics instead of repeating them.

**Primary keyword:** `compose multiplatform interstitial ads`
**Prose target:** 1,000–1,300 words
**Diagram:** `FullScreenLifecycle`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Interstitial ads
description: Compose Multiplatform interstitial ads with suspend load and show, multi-ad caching, TTL eviction, retry, and why a second show returns NotReady.
faq:
  - q: How do I show an interstitial ad in Kotlin Multiplatform?
    a: Get the controller with adManager.interstitial(placement) inside remember, call load() to fill the cache, then call show() from a UI-scoped coroutine. show() suspends until the ad is dismissed.
  - q: Why does my second show() call return NotReady?
    a: show() is not reentrant per controller. A second call while the first presentation is still on screen returns AdShowResult.NotReady immediately instead of queueing. Await the first result before calling again.
  - q: How long does a cached interstitial stay valid?
    a: One hour by default, from AdExpirationPolicy.fullScreenTtl. App-open ads default to four hours. Expired ads are evicted on access.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import FullScreenLifecycle from '../../../components/diagrams/FullScreenLifecycle.astro';

## How do I show an interstitial ad in Compose Multiplatform?

## What does show() actually return?

## What happens between load and show?

## How do I cache more than one interstitial?

## Why does a second show() return NotReady?

## How do I check availability before showing?

## Where to next?
```

- [ ] **Step 2: Write the basic usage section**

Open with the primary keyword and state the shared contract once: interstitial, rewarded, rewarded interstitial and app-open controllers all implement `FullScreenAdController`, whose surface is `load()`, `show()`, `isReady()`, `preload()`, `availability()`, `clear()`, `loadState: StateFlow<AdLoadState>`, `events: SharedFlow<AdEvent>` and `placement`.

```kotlin
val placement = remember {
    AdPlacement(
        id = "interstitial_main",
        format = AdFormat.Interstitial,
        androidAdUnitId = TestAdIds.ANDROID_INTERSTITIAL,
        iosAdUnitId = TestAdIds.IOS_INTERSTITIAL,
    )
}
val interstitial = remember(adManager) { adManager.interstitial(placement) }
val scope = rememberCoroutineScope()

// Preload, well before the moment you want to show
scope.launch {
    when (val state = interstitial.load()) {
        is AdLoadState.Loaded -> println("ready: ${state.responseInfo?.responseId}")
        is AdLoadState.Failed -> println("load failed: ${state.error}")
        else -> Unit
    }
}

// Show — suspends until the ad is dismissed
scope.launch {
    when (val result = interstitial.show()) {
        is AdShowResult.Shown -> println("shown and dismissed")
        is AdShowResult.NotReady -> println("call load() first")
        is AdShowResult.Failed -> println("show failed: ${result.error}")
    }
}
```

Then the two rules that matter: wrap the controller lookup in `remember` because controllers are placement-keyed and cached, and call `show()` from a UI-scoped coroutine and never `GlobalScope`, because it suspends for the ad's full on-screen lifetime.

- [ ] **Step 3: Write "What does show() actually return?" and the lifecycle section**

Describe the three-case sealed result explicitly: `AdShowResult.Shown` means the ad was presented and has now been dismissed — it is not "the ad started"; `AdShowResult.NotReady` means there was nothing to show, or a presentation is already active on this controller; `AdShowResult.Failed(error)` carries an `AdError` with `code`, `message`, `domain` and `responseInfo`.

Then "What happens between load and show?" with `<FullScreenLifecycle />` embedded, followed by prose describing the state machine the diagram shows: `AdLoadState` moves `Idle → Loading → Loaded(responseInfo)` or `Failed(error)`; loaded ads enter a TTL'd FIFO cache; `show()` consumes the oldest first; expired entries are evicted on access; and `clear()` bumps a generation counter so a load or scheduled reload started before the `clear()` can never repopulate a cache the caller just asked to be emptied. Link `/advanced/caching-retry-timeouts/` and `/reference/architecture/`.

- [ ] **Step 4: Write the caching section**

```kotlin
AdPlacement(
    id = "cached_interstitial",
    format = AdFormat.Interstitial,
    adUnitIds = AdUnitIds(android = "…", ios = "…"),
    cachePolicy = AdCachePolicy(
        maxSize = 3,               // load() tops the cache up to 3 ads
        reloadAfterShow = true,    // refill in the background after each show
    ),
)
```

Explain each behaviour: `load()` fills the cache sequentially and a partial fill still reports `Loaded`; `show()` consumes FIFO, oldest first; TTL defaults are one hour for full-screen formats and four hours for app-open, both from `AdExpirationPolicy`; `preload()` is an alias of `load()` — both top up the cache and return the resulting `AdLoadState`. Warn that a large `maxSize` is not free: every cached ad is a live SDK object with its own TTL, and an ad shown near the end of its hour is more likely to be an expired eviction than an impression.

- [ ] **Step 5: Write the reentrancy and availability sections**

Reentrancy, stated flatly because it is a real trap: `show()` is **not reentrant per controller**. Calling it again while a previous `show()` on the *same* controller is still on screen returns `AdShowResult.NotReady` immediately — it does **not** queue and wait for the first presentation to finish. Await the first `show()`'s result, which suspends until dismissal, before issuing another on the same controller. Add the design reason in one sentence: presentation ownership is a one-shot handle, so a queued second show would either double-present or silently swallow the caller's request.

Availability:

```kotlin
if (interstitial.isReady()) {
    interstitial.show()
}

val availability = interstitial.availability()
println(availability.isReady)      // Boolean
println(availability.cachedCount)  // Int
println(availability.expiresIn)    // Duration?
```

Note that `isReady()` is a cheap boolean check while `availability()` returns the full `AdAvailability` record, and that neither is a guarantee — an ad can expire between the check and the show, which is exactly why `show()` returns a result instead of throwing.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Rewarded ads" href="/formats/rewarded/" description="Rewards, and granting them exactly once." />
  <LinkCard title="App-open ads" href="/formats/app-open/" description="AppOpenAdCoordinator and cooldowns." />
  <LinkCard title="Caching, retry and timeouts" href="/advanced/caching-retry-timeouts/" description="TTL, backoff, and load bounds." />
  <LinkCard title="Revenue and paid events" href="/advanced/revenue-events/" description="AdValue and response info." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/formats/interstitial.mdx
npm run build
```

Expected: a word count between `1000` and `1300`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/formats/interstitial.mdx
git commit -m "docs(site): write the interstitial ads guide"
```

---

### Task 9: `/formats/rewarded/` — rewarded and rewarded interstitial

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/formats/rewarded.mdx`
- Read-only sources: `admob-cmp/docs/INTERSTITIAL.md` "Rewarded / rewarded interstitial", `admob-cmp/AGENTS.md`

**Interfaces:**
- Consumes: `<FullScreenLifecycle />` from `docs-site/src/components/diagrams/FullScreenLifecycle.astro`; the shared `FullScreenAdController` contract documented on `/formats/interstitial/`.
- Produces: canonical `/formats/rewarded/`, linked from `/formats/interstitial/` and the Plan 5 format grid.

**Primary keyword:** `kotlin multiplatform rewarded ads`
**Prose target:** 1,000–1,300 words
**Diagram:** `FullScreenLifecycle`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Rewarded ads
description: Kotlin Multiplatform rewarded ads and rewarded interstitials — granting the reward exactly once, server-side verification, and load and show sequencing.
faq:
  - q: How do I grant a reward only once in Kotlin Multiplatform?
    a: Grant from the onRewardEarned callback passed to show(), and do not also grant from AdEvent.RewardEarned. The event stream is telemetry; the callback is the single grant point on the client.
  - q: When should I use server-side verification for rewarded ads?
    a: Whenever the reward has real value. Pass ServerSideVerificationOptions with a userId and customData, and treat your server's callback as the authoritative grant rather than the client callback.
  - q: What is a rewarded interstitial?
    a: A full-screen format that offers a reward without an explicit opt-in prompt from your UI. It uses the same controller interface and the same show(onRewardEarned) signature as a rewarded ad.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import FullScreenLifecycle from '../../../components/diagrams/FullScreenLifecycle.astro';

## How do I show a rewarded ad in Kotlin Multiplatform?

## When exactly is the reward earned?

## How do I make sure I grant the reward only once?

## How do I set up server-side verification?

## How is a rewarded interstitial different?

## What does the load and show cycle look like?

## Where to next?
```

- [ ] **Step 2: Write the basic usage section**

Open with the primary keyword. State that `RewardedAdController` and `RewardedInterstitialAdController` both extend `FullScreenAdController` and add exactly one thing: a `show()` overload that takes a reward callback. Link `/formats/interstitial/` for the shared `load`/`availability`/`clear` semantics so they are not repeated here.

```kotlin
val placement = remember {
    AdPlacement(
        id = "rewarded_extra_life",
        format = AdFormat.Rewarded,
        androidAdUnitId = TestAdIds.ANDROID_REWARDED,
        iosAdUnitId = TestAdIds.IOS_REWARDED,
    )
}
val rewarded = remember(adManager) { adManager.rewarded(placement) }
val scope = rememberCoroutineScope()

scope.launch {
    rewarded.load()

    val result = rewarded.show(
        onRewardEarned = { reward: AdReward ->
            grantClientRewardOnce(reward.amountMicros, reward.type)
        }
    )

    when (result) {
        AdShowResult.Shown -> Unit
        AdShowResult.NotReady -> showRetryUi()
        is AdShowResult.Failed -> showAdError(result.error)
    }
}
```

Point out that `AdReward` carries exactly two fields — `amountMicros: Long` and `type: String` — and that `type` is the reward name configured in the AdMob UI, so client code should treat it as an opaque identifier to match against, not a display string.

- [ ] **Step 3: Write "When exactly is the reward earned?" and the grant-once section**

Be precise, because this is where money is lost. State the four facts as a short list: the `onRewardEarned` callback may run **after** `show()` returns; `AdShowResult.Shown` means presented-and-dismissed, not rewarded; `AdEvent.RewardEarned` is also emitted on the event stream, but events are telemetry and observation; and therefore the client must grant from exactly one place.

```kotlin
// Correct — one grant point
rewarded.show(onRewardEarned = { reward -> grantClientRewardOnce(reward.amountMicros, reward.type) })

// Wrong — this double-grants, because the callback already fired
LaunchedEffect(Unit) {
    adManager.events.collect { event ->
        if (event is AdEvent.RewardEarned) grantClientRewardOnce(event.reward.amountMicros, event.reward.type)
    }
}
```

Follow with an `<Aside type="danger">`: do not grant from both the callback and `AdEvent.RewardEarned`. Use the event stream for analytics only.

- [ ] **Step 4: Write the server-side verification section**

```kotlin
AdPlacement(
    id = "rewarded_extra_life",
    format = AdFormat.Rewarded,
    adUnitIds = AdUnitIds(android = "…", ios = "…"),
    fullScreenOptions = FullScreenAdOptions(
        serverSideVerification = ServerSideVerificationOptions(
            userId = "u123",
            customData = "level-7",
        ),
    ),
)
```

Explain the model in three sentences: `userId` identifies who to credit and `customData` carries whatever context the server needs to validate the grant; AdMob calls the verification URL configured on the ad unit; and for anything of real value, the server callback should be the authoritative grant while the client callback merely updates the UI optimistically. Note that `FullScreenAdOptions` can also be passed per-call to `show()`, which is useful when the same placement serves different users.

- [ ] **Step 5: Write the rewarded-interstitial and lifecycle sections**

Rewarded interstitial: same controller interface, same `show(onRewardEarned)` signature, obtained with `adManager.rewardedInterstitial(placement)` and `AdFormat.RewardedInterstitial`, with `TestAdIds.ANDROID_REWARDED_INTERSTITIAL` and `TestAdIds.IOS_REWARDED_INTERSTITIAL`. The difference is product, not API: a rewarded interstitial can appear at a natural transition without an explicit opt-in prompt from your UI, so AdMob requires an introductory screen and the format has stricter policy expectations. Show the one-line difference:

```kotlin
val rewardedInterstitial = remember(adManager) {
    adManager.rewardedInterstitial(
        AdPlacement(
            id = "rewarded_interstitial_level_end",
            format = AdFormat.RewardedInterstitial,
            androidAdUnitId = TestAdIds.ANDROID_REWARDED_INTERSTITIAL,
            iosAdUnitId = TestAdIds.IOS_REWARDED_INTERSTITIAL,
        )
    )
}
```

"What does the load and show cycle look like?" — embed `<FullScreenLifecycle />` and add two sentences tying it to rewards: the reward callback fires inside the presentation phase the diagram shows, before the terminal dismissal, which is why awaiting `show()` alone is not enough to know whether a reward was earned. Repeat the non-reentrancy rule in one sentence and link `/formats/interstitial/`.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Interstitial ads" href="/formats/interstitial/" description="The shared full-screen controller contract." />
  <LinkCard title="Caching, retry and timeouts" href="/advanced/caching-retry-timeouts/" description="Preloading rewarded inventory." />
  <LinkCard title="Revenue and paid events" href="/advanced/revenue-events/" description="Attributing rewarded revenue." />
  <LinkCard title="Test safety" href="/advanced/test-safety/" description="Never request live rewarded ads in debug." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/formats/rewarded.mdx
npm run build
```

Expected: a word count between `1000` and `1300`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/formats/rewarded.mdx
git commit -m "docs(site): write the rewarded ads guide"
```

---

### Task 10: `/formats/app-open/` — coordinator, cooldowns, blocking

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/formats/app-open.mdx`
- Read-only sources: `admob-cmp/docs/APP_OPEN.md`, `admob-cmp/AGENTS.md` "App-open"

**Interfaces:**
- Consumes: `<FullScreenLifecycle />` from `docs-site/src/components/diagrams/FullScreenLifecycle.astro`.
- Produces: canonical `/formats/app-open/`. This page is the only home of `AppOpenAdCoordinator` documentation; other pages link here rather than describing it.

**Primary keyword:** `kmp app open ads`
**Prose target:** 1,000–1,300 words
**Diagram:** `FullScreenLifecycle`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: App-open ads
description: KMP app-open ads with AppOpenAdCoordinator — foreground detection, minimum background duration, cooldowns, blocking, and the four-hour ad TTL.
faq:
  - q: How do I show app-open ads in Kotlin Multiplatform?
    a: Construct an AppOpenAdCoordinator with the ad manager, the app-open controller and an AppOpenConfig, then call coordinator.start(scope). It handles preloading, foreground detection, cooldowns and reloading.
  - q: How do I stop an app-open ad interrupting a purchase?
    a: Set coordinator.isBlocked to true before the sensitive flow and back to false afterwards. Use it during checkout, onboarding, and whenever another full-screen ad may appear.
  - q: How long does an app-open ad stay valid?
    a: Four hours by default, from AdExpirationPolicy.appOpenTtl, matching Google's guidance. Other full-screen formats default to one hour.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import FullScreenLifecycle from '../../../components/diagrams/FullScreenLifecycle.astro';

## What are app-open ads and when do they show?

## How do I wire up AppOpenAdCoordinator?

## What does each AppOpenConfig setting do?

## How do I stop an app-open ad interrupting checkout?

## Why did my app-open ad not show?

## Can I control app-open ads manually?

## Where to next?
```

- [ ] **Step 2: Write the introduction**

Open with the primary keyword. Explain the format in two sentences: app-open ads show while the app is loading, typically when a user returns to it from the background. Then make the case for the coordinator honestly — the format is unusually easy to get wrong because it depends on process lifecycle, and the coordinator implements the entire recommended lifecycle: preload, foreground detection, minimum-background gating, cooldown, and reload after consumption.

- [ ] **Step 3: Write the coordinator setup section**

```kotlin
val placement = remember {
    AdPlacement(
        id = "app_open_main",
        format = AdFormat.AppOpen,
        androidAdUnitId = TestAdIds.ANDROID_APP_OPEN,
        iosAdUnitId = TestAdIds.IOS_APP_OPEN,
    )
}

val coordinator = remember(adManager) {
    AppOpenAdCoordinator(
        manager = adManager,
        controller = adManager.appOpen(placement),
        config = AppOpenConfig(
            minBackgroundDuration = 4.seconds,
            cooldownBetweenShows = 4.hours,
            preloadOnStart = true,
            showOnColdStart = false,
        ),
    )
}

LaunchedEffect(Unit) { coordinator.start(this) }
```

Explain that `start(scope)` binds the coordinator to a coroutine scope and begins observing foreground transitions, that `stop()` tears it down, and that the coordinator must be created inside `remember` so a recomposition does not build a second one.

- [ ] **Step 4: Write the config table**

```mdx
| Setting | What it does |
|---|---|
| `minBackgroundDuration` | Ignore quick app switches. A return sooner than this does not trigger a show. |
| `cooldownBetweenShows` | Minimum gap between two app-open impressions. `Duration.ZERO` disables the cooldown. |
| `preloadOnStart` | Load an ad as soon as the coordinator starts, so the first eligible return has inventory. |
| `showOnColdStart` | Whether a cold start may show an ad. Read the KDoc before enabling — a cold-start ad competes with your own splash. |
| `coldStartTimeout` | How long a cold start may wait for an ad before giving up and continuing into the app. |
```

Add the judgement call in prose: a short `minBackgroundDuration` maximises impressions and annoys users who switch apps to copy a code; four seconds is a reasonable floor. A `cooldownBetweenShows` of a few hours keeps the format from feeling like an ad wall.

- [ ] **Step 5: Write the blocking, non-show, and manual sections**

Blocking:

```kotlin
coordinator.isBlocked = true    // entering checkout or onboarding
// …
coordinator.isBlocked = false   // flow finished
```

State when to set it: during a purchase, during onboarding, and whenever another full-screen ad may show. Note that `isBlocked` is a plain mutable property, so it is the caller's job to reset it — a `try`/`finally` around the sensitive flow is the safe shape.

"Why did my app-open ad not show?" as a triage list, each item one or two lines: the SDK is not `AdManagerStatus.Ready`, and the coordinator deliberately shows only when it is, so it can never appear over a consent form — this also means it works under `ConsentMode.SkipConsent`; the background interval was shorter than `minBackgroundDuration`; the cooldown has not elapsed; `isBlocked` is still `true` because a flow set it and never reset it; no fresh ad was cached, in which case the coordinator reloads automatically; on iOS the coordinator listens to `WillEnterForeground`, so system alerts and consent-form dismissals do not trigger shows, and on Android it uses `ProcessLifecycleOwner`.

Manual control:

```kotlin
val appOpen = adManager.appOpen(placement)
scope.launch {
    appOpen.load()
    appOpen.showIfAvailable()   // shows only when isReady()
}
```

Explain that `AppOpenAdController` is an ordinary `FullScreenAdController` plus `showIfAvailable()`, and that hand-rolling the lifecycle means reimplementing foreground detection, cooldowns and reload — which is the coordinator's entire reason to exist.

Then embed `<FullScreenLifecycle />` under "What does the load and show cycle look like?" content folded into the manual section, with one sentence noting the only difference from other full-screen formats is the TTL: app-open ads expire after four hours by default (`AdExpirationPolicy.appOpenTtl`), matching Google's guidance, rather than one hour.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Interstitial ads" href="/formats/interstitial/" description="The shared full-screen controller contract." />
  <LinkCard title="UMP consent" href="/privacy/consent/" description="Why app-open never covers a consent form." />
  <LinkCard title="Caching, retry and timeouts" href="/advanced/caching-retry-timeouts/" description="TTLs and the four-hour app-open default." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="When an ad does not appear." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/formats/app-open.mdx
npm run build
```

Expected: a word count between `1000` and `1300`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/formats/app-open.mdx
git commit -m "docs(site): write the app-open ads guide"
```

---

### Task 11: `/formats/native/` — layout DSL, pooling, availableAds, media info  ⭐ PRIORITY

Spec §7 flags this page as pure opportunity capture: `compose multiplatform native ads` has **no competition**, because the incumbent library does not support the format at all. It gets the deepest treatment of any format page.

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/formats/native.mdx`
- Read-only sources: `admob-cmp/docs/NATIVE.md`, `admob-cmp/AGENTS.md` "Native" and "Availability (`pool.availableAds`)", `admob-cmp-compose/api/admob-cmp-compose.klib.api` (`AdLayoutScope`, `AdModifier`, `AdTemplates`)

**Interfaces:**
- Consumes: `<NativePoolLifecycle />` from `docs-site/src/components/diagrams/NativePoolLifecycle.astro`.
- Produces: canonical `/formats/native/`, linked from `/start/what-is-admob-cmp/`, `/formats/banner/`, `/reference/troubleshooting/`, and the Plan 5 landing page.

**Primary keyword:** `compose multiplatform native ads`
**Prose target:** 1,600–2,000 words
**Diagram:** `NativePoolLifecycle`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Native ads
description: Compose Multiplatform native ads — the adLayout DSL, ad pooling, availableAds recovery, maxSize accounting, media info, and the Android video-event gap.
faq:
  - q: Can I show native ads in Compose Multiplatform?
    a: Yes. AdMob CMP renders native ads through a declarative adLayout DSL that becomes real platform-native views, with every asset registered for click and impression tracking on both Android and iOS.
  - q: Why does pool.acquire() return null?
    a: AdCachePolicy.maxSize budgets available plus in-use ads together. Once leases fill the budget, acquire() returns null deterministically. Key your acquisition effect on pool.availableAds and re-run preload, not just acquire.
  - q: Why do native video events only fire on iOS?
    a: iOS emits five video events through GADVideoControllerDelegate. The Android Google Mobile Ads Next-Gen SDK exposes no equivalent callback surface on NativeAd, so Android emits none. It is an upstream SDK gap.
  - q: Do I need an Ad badge in my native layout?
    a: Yes. AdMob policy requires ad attribution, adBadge() is the node that provides it, and AdLayoutValidator warns when it is missing.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import NativePoolLifecycle from '../../../components/diagrams/NativePoolLifecycle.astro';

## What are native ads, and why does AdMob CMP support them?

## How do I render a native ad in Compose Multiplatform?

## How does the adLayout DSL work?

## Which layout nodes are available?

## Do I have to write a custom layout?

## How does the native ad pool work?

## Why does acquire() return null, and how do I recover?

## How do I read media info and detect video?

## Why do native video events only fire on iOS?

## What does AdMob policy require of a native layout?

## Where to next?
```

- [ ] **Step 2: Write the introduction and the basic usage section**

Open with the primary keyword in the first sentence. Explain what native ads are — ad assets (headline, body, icon, media, call to action, advertiser, star rating, price, store) delivered without a fixed creative, so the app composes them into its own design — and why that matters for a feed. Then state the mechanism: `adLayout { … }` is a declarative description that the SDK turns into **platform-native views** with every asset registered for click and impression tracking, rather than Compose surfaces layered over an invisible ad view. That distinction is the reason clicks and impressions are attributed correctly.

```kotlin
val placement = remember {
    AdPlacement(
        id = "feed_native",
        format = AdFormat.Native,
        androidAdUnitId = TestAdIds.ANDROID_NATIVE,
        iosAdUnitId = TestAdIds.IOS_NATIVE,
        maxCacheSize = 5,
    )
}

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
    itemKey = "feed_slot_1",
    layout = feedAdLayout,
    modifier = Modifier.fillMaxWidth(),
    onEvent = { event -> /* Loaded, Impression, Clicked, Paid, Video* */ },
)
```

Then the two rules a reader will otherwise learn the hard way. First, `itemKey` must be **stable per slot** — it is what drives pool acquire and release, so it should identify the position in the list, not the ad. Second, the placement `id` must be static and finite: controllers are cached per id for the manager's lifetime and are never auto-evicted, so generating `"feed_item_$index"` leaks a controller per row. For a feed, reuse one placement id and let the pool serve per-item ads.

- [ ] **Step 3: Write the DSL sections**

"How does the adLayout DSL work?" — explain three properties. Each `adLayout { … }` construction recursively validates the tree and computes a structural identity string, so it is not free; retain custom layouts with `remember { adLayout { … } }`, or `remember(variant) { adLayout { … } }` for dynamic variants, to avoid revalidating and rebuilding platform views on every recomposition. Nodes are **functions with named arguments** (`headline(maxLines = 2)`), not property-assignment blocks — a detail worth stating explicitly because the shape looks like Compose but is not. And every node takes an `AdModifier`, a separate modifier type from Compose's `Modifier`, because the tree is serialised down to native views.

"Which layout nodes are available?":

```mdx
| Node | Asset | Notes |
|---|---|---|
| `headline()` | Headline | The only required asset |
| `body()`, `advertiser()`, `price()`, `store()` | Text assets | Hidden or collapsed per `visibilityPolicy` when missing |
| `starRating()` | Star rating | Text asset; no `maxLines` parameter |
| `icon()` | App icon | |
| `media()` | Image or video media view | Use `mediaInfo()` for the real aspect ratio |
| `callToAction()` | Call-to-action button | |
| `adBadge()` | The "Ad" label | Required by AdMob policy; the validator warns if absent |
| `adChoices()` | AdChoices icon | |
| `row()`, `column()`, `box()`, `spacer()`, `text()` | Containers and statics | `row` and `column` take a `spacing` argument |
```

Then the `AdModifier` paragraph, listing the real surface: sizing (`fillMaxWidth`, `size`, `height`, `width`, `weight`, `sizeIn`, `aspectRatio`), spacing (`padding`, `margin`), decoration (`background`, `border`, `cornerRadius`, `clipRounded`, `clipCircle`, `elevation`, `alpha`), and visibility (`visible`, `invisible`, `gone`).

"Do I have to write a custom layout?" — no. `AdTemplates` ships four ready-made layouts: `AdTemplates.mediaCard` (the `NativeAdView` default), `AdTemplates.feedCard`, `AdTemplates.medium` and `AdTemplates.compact`. Show the shortest possible integration:

```kotlin
NativeAdView(
    placement = placement,
    itemKey = "feed_slot_1",
    layout = AdTemplates.compact,
)
```

- [ ] **Step 4: Write the pooling section and embed the diagram**

Explain the model first: native ads are single-use, so a native placement is served by a **pool** rather than a single controller. `NativeAdView` acquires and releases automatically via `itemKey`. Manual control looks like this:

```kotlin
val pool = adManager.nativeAd(placement)

scope.launch { pool.preload(count = 5) }

val token = pool.acquire() ?: return
try {
    val info = pool.mediaInfo(token)   // aspectRatio, hasVideoContent, durationSeconds
    // render using `token`
} finally {
    pool.release(token)                // every acquired token MUST be released
}
```

Place `<NativePoolLifecycle />` immediately after, then describe the accounting the diagram shows — this is the part that is documented nowhere else:

- `AdCachePolicy.maxSize` budgets **available + in-use** ads together, not just cached ones.
- `release()` **destroys** the ad, because native ads are single-use. It therefore frees a `maxSize` slot **without** incrementing `availableAds`.
- `clear()` drains available inventory only. Ads currently leased stay alive until their `release()`, because a live view is still rendering them. A consequence worth stating plainly: clearing a fully-leased pool frees no capacity until those views dispose.
- Pooled ads expire per `AdExpirationPolicy.nativeTtl`, one hour by default, and are evicted on access.

Add an `<Aside type="note">` listing the public pool surface so nobody guesses: `preload(count, requestOptions, nativeOptions)`, `acquire()`, `release(token)`, `mediaInfo(token)`, `availableCount()`, `clear()`, `availableAds: StateFlow<Int>`, `loadState`, `events`, `placement`.

- [ ] **Step 5: Write the `acquire()` recovery section — the highest-value part of the page**

Set up the failure exactly: with `maxSize = 1` and one row holding the ad, `preload()` returns early and `acquire()` returns `null` for every other row — deterministically, not as a race. `acquire()` returning `null` used to be silent and terminal for that composition, leaving those rows blank forever.

`pool.availableAds` is the signal that lets a view recover. Key the acquisition effect on it:

```kotlin
val availableAds by pool.availableAds.collectAsState()

LaunchedEffect(pool, itemKey, availableAds) {
    pool.preload(count = 1)
    val token = pool.acquire()
    // …render, then release on dispose
}
```

Then the sentence that makes it correct rather than merely plausible: **re-run `preload`, not just `acquire`.** Because `release()` destroys the ad, it frees a `maxSize` slot without incrementing `availableAds`, so an effect that only retries `acquire()` waits for a signal that will never arrive. The built-in `NativeAdView` composables already do this, which is why the manual path is the one that needs the warning.

Close with sizing guidance: size `maxSize` to the number of ad slots that can be **on screen at once**, plus one, not to the number of rows in the feed.

- [ ] **Step 6: Write the media-info, video-gap and policy sections**

Media info:

```kotlin
val info = pool.mediaInfo(token)
if (info?.hasVideoContent == true) {
    println("video, ${info.durationSeconds}s, ratio ${info.aspectRatio}")
}
```

`NativeMediaInfo` has exactly three fields: `aspectRatio: Float?`, `hasVideoContent: Boolean`, `durationSeconds: Double?`. Note the practical use — reserve layout height from `aspectRatio` rather than hardcoding 16:9, so a portrait creative does not letterbox.

The video gap gets an `<Aside type="caution">` and blunt wording, because it is a cross-platform correctness trap:

> **Platform gap — native video events on Android.** iOS emits five video events — `AdEvent.VideoStarted`, `VideoPlayed`, `VideoPaused`, `VideoEnded` and `VideoMuted` — via `GADVideoControllerDelegate`. The Android Google Mobile Ads Next-Gen SDK exposes no equivalent callback surface on `NativeAd`, so Android emits none. Do not rely on native video events for cross-platform logic. This is an upstream SDK gap, not an AdMob CMP omission.

Add that `mediaInfo(token).hasVideoContent` **is** cross-platform, so "does this ad have video" is answerable everywhere even though "has the video started" is not.

Policy:

- `adBadge()` is required by AdMob policy and `AdLayoutValidator` warns when it is missing.
- Every registered asset must stay fully inside the platform native-ad root, and ad attribution must appear at the top. The built-in templates enforce this on both platforms; custom layouts should keep the badge at the top and avoid offsets that push registered assets outside the root bounds.
- On debug builds Google may display its own Native Ad Validator overlay.

Show how to read the validation report, since `AdLayout` exposes it:

```kotlin
val layout = remember { adLayout { /* … */ } }

LaunchedEffect(layout) {
    layout.validation.warnings.forEach { issue ->
        println("${issue.code} at ${issue.nodePath}: ${issue.message}")
    }
}
```

Note that `AdLayout.validation` is an `AdLayoutValidationReport` with `isValid`, `hasWarnings`, `errors` and `warnings`, and each `AdLayoutValidationIssue` carries `code`, `nodePath` and `message`.

- [ ] **Step 7: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Banner ads" href="/formats/banner/" description="When a fixed ad slot is the better fit." />
  <LinkCard title="Caching, retry and timeouts" href="/advanced/caching-retry-timeouts/" description="TTL and cache sizing for pools." />
  <LinkCard title="Revenue and paid events" href="/advanced/revenue-events/" description="Attributing native ad revenue." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="Blank rows and null acquires." />
</CardGrid>
```

- [ ] **Step 8: Verify length, check the forbidden symbol, and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/formats/native.mdx
grep -c 'peek(' src/content/docs/formats/native.mdx || echo "NO peek() OK"
npm run build
```

Expected: a word count between `1600` and `2000`; `NO peek() OK` (`pool.peek()` is not public API and must never appear); then a successful build.

- [ ] **Step 9: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/formats/native.mdx
git commit -m "docs(site): write the native ads guide with pooling and layout DSL"
```

---

### Task 12: `/privacy/consent/` — UMP modes, privacy options form, canRequestAds

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/privacy/consent.mdx`
- Read-only sources: `admob-cmp/docs/CONSENT.md`, `admob-cmp/docs/SETUP.md` §4, `admob-cmp/AGENTS.md` "Consent / privacy options", `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdConfig.kt`

**Interfaces:**
- Consumes: `<ConsentDecisionTree />` from `docs-site/src/components/diagrams/ConsentDecisionTree.astro`.
- Produces: canonical `/privacy/consent/`, linked from `/start/quickstart/`, `/start/ios-setup/`, `/formats/app-open/`, `/privacy/app-tracking-transparency/`, `/reference/troubleshooting/`.

**Primary keyword:** `admob consent kotlin multiplatform`
**Secondary keyword** (spec §7 assigns both to this page): `ump sdk compose multiplatform` — use it once, naturally, in the "How does UMP consent work" section.
**Prose target:** 1,100–1,400 words
**Diagram:** `ConsentDecisionTree`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: UMP consent
description: AdMob consent on Kotlin Multiplatform — UMP consent modes, canRequestAds, the privacy options form, debug geography, and resetting consent in development.
faq:
  - q: How does UMP consent work with AdMob on Kotlin Multiplatform?
    a: Consent is part of initialization. gatherConsentAndInitialize runs the UMP flow, then starts Mobile Ads. Until consent allows requests, every ad load fails fast with AdErrorCode.CONSENT_REQUIRED and nothing reaches the network.
  - q: When should I show a Privacy Settings button?
    a: Only when adManager.consent.privacyOptionsRequirementStatus is PrivacyOptionsRequirementStatus.Required. Do not gate it on ConsentStatus.Obtained, because users who declined must still be able to reopen the form.
  - q: How do I test the EEA consent flow?
    a: Set testMode to true and debugGeography to ConsentDebugGeography.Eea, and register the device hash in AdDebugOptions.consentTestDeviceIds. Debug settings only apply when testMode is true.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import ConsentDecisionTree from '../../../components/diagrams/ConsentDecisionTree.astro';

## How does UMP consent work in AdMob CMP?

## Which ConsentMode should I use?

## What is canRequestAds and why does it gate everything?

## When should I show a Privacy Settings button?

## How do I test the EEA consent flow?

## How do I reset consent during development?

## How do I refresh consent state without showing a form?

## Where to next?
```

- [ ] **Step 2: Write the overview and embed the decision tree**

Open with the primary keyword. State the design decision plainly: consent is integrated into initialization rather than bolted beside it, which makes requesting ads before consent impossible by construction. Mention the UMP SDK by name once here to carry the secondary keyword — the User Messaging Platform SDK is what presents the consent form on both platforms.

```kotlin
adManager.gatherConsentAndInitialize(
    AdConfig(
        androidAppId = "ca-app-pub-…",
        iosAppId = "ca-app-pub-…",
    )
)
```

Place `<ConsentDecisionTree />` after it, then describe the states the diagram shows. `ConsentStatus` is a sealed interface with five cases:

```mdx
| `ConsentStatus` | Meaning |
|---|---|
| `Unknown` | Not determined yet. |
| `Required` | A form must be shown. |
| `Obtained` | The user answered — which does not necessarily mean they consented. |
| `NotRequired` | The regulation does not apply to this user. |
| `Failed(error)` | The consent-info update failed; the error is attached. |
```

- [ ] **Step 3: Write the ConsentMode section**

```kotlin
adManager.initialize(config, ConsentMode.GatherBeforeInitialize)
```

```mdx
| `ConsentMode` | Behaviour |
|---|---|
| `GatherBeforeInitialize` | Request consent info, present the UMP form if required, then initialize. Recommended. |
| `InitializeOnlyIfAlreadyAllowed` | No form. Initialize only if consent already permits requests; otherwise the status becomes `ConsentRequired`. |
| `SkipConsent` | Bypass UMP entirely. |
```

Add the guidance in prose: `gatherConsentAndInitialize(config)` is `GatherBeforeInitialize` with the ergonomics of a single call and is the right default. Use `InitializeOnlyIfAlreadyAllowed` when the app drives the consent flow itself — for instance to sequence App Tracking Transparency between consent and initialization on iOS. Do **not** substitute `SkipConsent` there, because it ignores future UMP revocation. `SkipConsent` is for apps that genuinely have no UMP obligation.

- [ ] **Step 4: Write the canRequestAds section**

```kotlin
val consentStatus by adManager.consent.status.collectAsState()
val canRequestAds by adManager.consent.canRequestAds.collectAsState()
```

State the rule once and clearly: `canRequestAds` is the gate that matters. The SDK refuses ad requests with `AdErrorCode.CONSENT_REQUIRED` until it is `true`, unless the mode is `SkipConsent`. Before initialization succeeds, loads fail fast with `AdErrorCode.SDK_NOT_READY`. In both cases **nothing reaches the network** — a failed load here is the gate working, not a bug. Link `/reference/troubleshooting/`.

Add the point most apps get wrong: `ConsentStatus.Obtained` means the user answered the form, not that they said yes. Branch on `canRequestAds`, never on `Obtained`.

- [ ] **Step 5: Write the privacy-options section**

```kotlin
val privacyRequirement by adManager.consent.privacyOptionsRequirementStatus.collectAsState()

if (privacyRequirement == PrivacyOptionsRequirementStatus.Required) {
    Button(onClick = { scope.launch { adManager.consent.showPrivacyOptions() } }) {
        Text("Privacy Settings")
    }
}
```

Explain that this is the GDPR re-consent affordance, that `PrivacyOptionsRequirementStatus` is `Unknown`, `Required` or `NotRequired`, and that `showPrivacyOptions()` returns a `Boolean` indicating whether the form was presented. Then the `<Aside type="caution">`: do **not** gate this button on `ConsentStatus.Obtained`. Users who declined consent still must be able to reopen the form, and gating on `Obtained` hides the entry point from exactly the users who need it.

- [ ] **Step 6: Write the debug, reset and refresh sections**

This section must correct a real ambiguity in the API surface, and doing so is a differentiator. The convenience `AdConfig(androidAppId = …, iosAppId = …, testDeviceIds = …)` constructor routes `testDeviceIds` into `GlobalRequestConfiguration.testDeviceIds`, which registers **GMA test devices** — the thing that makes Google serve test ads. **UMP consent-form test devices are a different field**: `AdDebugOptions.consentTestDeviceIds`. To force the EEA flow on a physical device, use the primary constructor:

```kotlin
adManager.gatherConsentAndInitialize(
    AdConfig(
        appIds = AdAppIds(android = "ca-app-pub-…", ios = "ca-app-pub-…"),
        debugOptions = AdDebugOptions(
            testMode = true,
            consentDebugGeography = ConsentDebugGeography.Eea,
            consentTestDeviceIds = listOf("YOUR-DEVICE-HASH"),
        ),
        globalRequestConfiguration = GlobalRequestConfiguration(
            testDeviceIds = listOf("YOUR-DEVICE-HASH"),
        ),
    )
)
```

State that `ConsentDebugGeography` is `Disabled`, `Eea` or `NotEea`; that debug settings apply only when `testMode = true`; and that the device hash is printed to logcat or the Xcode console on the first unregistered request. Link `/advanced/test-safety/` for the full `testMode` versus `strictTestMode` explanation.

Reset, for debug builds only:

```kotlin
scope.launch { adManager.consent.resetConsentForDebug() }
```

Re-checking without UI:

```kotlin
scope.launch { adManager.consent.requestConsentInfoUpdate(config) }
```

Explain that `requestConsentInfoUpdate` updates `status`, `canRequestAds` and `privacyOptionsRequirementStatus` without presenting a form, which is what a settings screen should call on open, and that `gatherConsent(config)` is the variant that *may* present the form.

- [ ] **Step 7: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="App Tracking Transparency" href="/privacy/app-tracking-transparency/" description="The iOS consent, ATT, initialize order." />
  <LinkCard title="Play Data safety" href="/privacy/play-data-safety/" description="What to declare in the Play Console." />
  <LinkCard title="Test safety" href="/advanced/test-safety/" description="testMode versus strictTestMode." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="CONSENT_REQUIRED and SDK_NOT_READY." />
</CardGrid>
```

- [ ] **Step 8: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/privacy/consent.mdx
npm run build
```

Expected: a word count between `1100` and `1400`, then a successful build.

- [ ] **Step 9: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/privacy/consent.mdx
git commit -m "docs(site): write the UMP consent guide"
```

---

### Task 13: `/privacy/app-tracking-transparency/` — the consent → ATT → initialize order

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/privacy/app-tracking-transparency.mdx`
- Read-only sources: `admob-cmp/AGENTS.md` "iOS: App Tracking Transparency (required)", `admob-cmp/docs/SETUP.md` §4

**Interfaces:**
- Consumes: `<InitSequence />` from `docs-site/src/components/diagrams/InitSequence.astro` — this page is that diagram's **primary** home.
- Produces: canonical `/privacy/app-tracking-transparency/`, linked from `/start/ios-setup/`, `/start/quickstart/`, `/privacy/consent/`.

**Primary keyword:** `app tracking transparency kotlin multiplatform`
**Prose target:** 900–1,200 words
**Diagram:** `InitSequence`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: App Tracking Transparency
description: App Tracking Transparency for Kotlin Multiplatform AdMob apps — the required Info.plist key, the consent to ATT to initialize order, and what IDFA loss costs.
faq:
  - q: What order should I call consent, ATT and initialize in?
    a: UMP consent first, then ATT, then your first ad request. Requesting ads before ATT resolves permanently forfeits the IDFA for those requests.
  - q: What happens without NSUserTrackingUsageDescription?
    a: The ATT prompt cannot be shown, so iOS withholds the IDFA and every request serves non-personalised ads at materially lower eCPM.
  - q: Does App Tracking Transparency apply on Android?
    a: No. Android has no ATT. adManager.tracking is a no-op there and always reports AdTrackingAuthorization.NotApplicable.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import InitSequence from '../../../components/diagrams/InitSequence.astro';

## What is App Tracking Transparency and why does it matter for ads?

## What is the correct call order?

## What do I need in Info.plist?

## How do I read the tracking authorization state?

## Can I request ATT from an initialization hook?

## What does Android do?

## Where to next?
```

- [ ] **Step 2: Write the introduction**

Open with the primary keyword. Explain ATT in two sentences without editorialising: since iOS 14.5, an app must ask permission before accessing the IDFA, and AdMob uses the IDFA for personalised ads and attribution. Then quantify the consequence honestly: without the prompt, iOS withholds the IDFA and every request serves non-personalised ads at materially lower eCPM. Say that AdMob CMP does not decide this for the app — it exposes `adManager.tracking` and leaves the timing to the caller, because the timing is the whole problem.

- [ ] **Step 3: Write the call-order section and embed the diagram**

This is the page's core. State the order first, then show it, then explain why:

```kotlin
adManager.consent.gatherConsent(config)
adManager.tracking.requestAuthorization()
adManager.initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)
```

Place `<InitSequence />` directly after. Then the three reasons, one sentence each: UMP consent comes first because the GDPR/TCF decision determines whether ads may be requested at all; ATT comes next because the IDFA decision must be resolved before any request goes out; and initialization comes last with `InitializeOnlyIfAlreadyAllowed` so it does not re-present a form the app has already handled. Close with the hard rule in an `<Aside type="danger">`: **requesting ads before ATT resolves permanently forfeits the IDFA for those requests.** There is no retroactive fix — those impressions are non-personalised for good.

- [ ] **Step 4: Write the Info.plist section**

```xml
<key>NSUserTrackingUsageDescription</key>
<string>This identifier will be used to deliver personalised ads to you.</string>
```

State that this goes in the **app target's** `Info.plist`, that the string is shown verbatim in the system prompt so it should say what the user gets rather than what the app wants, and that App Review rejects vague or misleading strings. Note that `./gradlew :admob-cmp-core:doctorIos` checks `GADApplicationIdentifier` and `SKAdNetworkItems` but the tracking description is the developer's responsibility to add. Link `/start/ios-setup/`.

- [ ] **Step 5: Write the state, hook, and Android sections**

Reading state:

```kotlin
when (adManager.tracking.status()) {
    AdTrackingAuthorization.Authorized -> Unit      // IDFA available
    AdTrackingAuthorization.Denied -> Unit          // user said no
    AdTrackingAuthorization.NotDetermined -> Unit   // prompt not shown yet
    AdTrackingAuthorization.Restricted -> Unit      // blocked by policy or parental controls
    AdTrackingAuthorization.NotApplicable -> Unit   // Android, always
}
```

Explain that `status()` is synchronous and cheap, so it is safe to read in a settings screen, while `requestAuthorization()` is suspending and presents the system prompt at most once per install — a second call returns the existing decision rather than re-prompting, which is an OS behaviour rather than a library one.

Hook form, for apps that prefer a single `gatherConsentAndInitialize` call:

```kotlin
AdConfig(
    androidAppId = "ca-app-pub-…",
    iosAppId = "ca-app-pub-…",
    initializationHooks = listOf(
        object : AdInitializationHook {
            override suspend fun onPhase(phase: AdInitializationPhase, config: AdConfig) {
                if (phase == AdInitializationPhase.BeforeMobileAdsInitialize) {
                    adManager.tracking.requestAuthorization()
                }
            }
        }
    ),
)
```

Explain that `AdInitializationPhase` has exactly three values — `BeforeConsentRequest`, `BeforeMobileAdsInitialize` and `AfterMobileAdsInitialize` — that `BeforeMobileAdsInitialize` runs after the UMP gate and before native GMA initialization, which is precisely the ATT slot, and that hooks run exactly once per real native-initialization attempt, so cancelling one `initialize()` caller can never skip or duplicate a hook.

Android: one short paragraph. Android has no ATT. `adManager.tracking` is a no-op there and always reports `AdTrackingAuthorization.NotApplicable`, so the same common-code sequence compiles and runs correctly on both platforms with no `expect`/`actual` needed.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="iOS setup" href="/start/ios-setup/" description="SPM packages, Info.plist, and doctorIos." />
  <LinkCard title="UMP consent" href="/privacy/consent/" description="Consent modes and canRequestAds." />
  <LinkCard title="Play Data safety" href="/privacy/play-data-safety/" description="The Android side of the same disclosure." />
  <LinkCard title="Revenue and paid events" href="/advanced/revenue-events/" description="Measuring what IDFA loss costs you." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/privacy/app-tracking-transparency.mdx
npm run build
```

Expected: a word count between `900` and `1200`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/privacy/app-tracking-transparency.mdx
git commit -m "docs(site): write the App Tracking Transparency guide"
```

---

### Task 14: `/privacy/play-data-safety/` — the Play Console declaration

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/privacy/play-data-safety.mdx`
- Read-only sources: `admob-cmp/docs/SETUP.md` "Play Data Safety and the AD_ID permission"

**Interfaces:**
- Consumes: nothing beyond the word-count script.
- Produces: canonical `/privacy/play-data-safety/`, linked from `/start/android-setup/`, `/privacy/consent/`, `/privacy/app-tracking-transparency/`.

**Primary keyword:** `admob play data safety ad_id`
**Prose target:** 800–1,000 words
**Diagram:** none

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Play Data safety
description: The AdMob Play Data safety and AD_ID declaration — which data categories the Google Mobile Ads SDK collects, and what to tick in the Play Console form.
faq:
  - q: What does the Google Mobile Ads SDK collect for Play Data safety?
    a: In its default configuration it collects device or other IDs (the advertising ID), app activity such as ad impressions and clicks, and approximate location derived from IP for ad targeting.
  - q: Do I have to declare data sharing with third parties?
    a: Yes. Ad serving sends this data to Google and, when mediation is enabled, to the mediated networks, so the collected categories must be declared as shared.
  - q: What happens if I remove the AD_ID permission?
    a: Your app can no longer access the advertising ID on API 33 and above, which reduces ad revenue. It also changes what you declare in the Data safety form.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';

## Why does adding AdMob change my Data safety form?

## Which data categories does the Google Mobile Ads SDK collect?

## What is the AD_ID permission?

## Can I opt out of the advertising ID?

## What changes when I enable mediation?

## Where to next?
```

- [ ] **Step 2: Write the opening and the category list**

Open with the primary keyword. Be honest about scope in the first paragraph: this page explains what the SDK does so the form can be filled in accurately; it is not legal advice, and the app's own data collection must be declared alongside it. Note that `admob-cmp` itself declares only the `INTERNET` permission — everything below comes from the Google Mobile Ads SDK.

Then the categories, as a list with the purpose attached to each, because the Play form asks for purpose:

```mdx
| Data type | Play category | Purpose |
|---|---|---|
| Advertising ID | Device or other IDs | Advertising or marketing |
| Ad impressions and clicks | App activity → App interactions | Advertising or marketing, analytics |
| Coarse location derived from IP | Location → Approximate location | Advertising or marketing |
```

State that all three should be declared as **collected and shared with third parties**, and that Google publishes a per-SDK data-disclosure guide for the Mobile Ads SDK which should be checked against the current SDK version rather than trusted from memory.

- [ ] **Step 3: Write the AD_ID sections**

Explain that the Google Mobile Ads SDK merges `com.google.android.gms.permission.AD_ID` into the app's manifest, and that apps targeting API 33 or above which do not declare it cannot access the advertising ID. Then the opt-out and its cost:

```xml
<uses-permission
    android:name="com.google.android.gms.permission.AD_ID"
    tools:node="remove" />
```

State plainly: this reduces ad revenue and is not recommended unless the app is child-directed or there is another compliance reason. If the permission is removed, the Data safety declaration should reflect that the advertising ID is no longer collected — but ad impressions and coarse location still are.

Add an `<Aside type="note">` connecting this to the request-level settings: a child-directed app should also set `GlobalRequestConfiguration(ageRestrictedTreatment = AgeRestrictedTreatment.Child)`, and `AdConfig.consentTagForUnderAgeOfConsent` is a **separate** UMP-only flag. The library deliberately does not infer either setting from the other. Show it:

```kotlin
AdConfig(
    androidAppId = "ca-app-pub-…",
    iosAppId = "ca-app-pub-…",
    consentTagForUnderAgeOfConsent = true,                 // UMP consent-flow setting
    ageRestrictedTreatment = AgeRestrictedTreatment.Child, // GMA ad-request setting
)
```

Note that `AgeRestrictedTreatment` is `Unspecified`, `Child` or `Teen`, and that `Teen` should be used only when the app has determined that treatment is appropriate.

- [ ] **Step 4: Write the mediation section and "Where to next?"**

Mediation: each mediated network is its own SDK with its own collection behaviour, so enabling mediation can add data categories the base declaration does not cover. Every adapter's data-disclosure documentation must be checked and the form updated. Link `/advanced/mediation/`.

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Android setup" href="/start/android-setup/" description="Manifest and the AD_ID permission." />
  <LinkCard title="UMP consent" href="/privacy/consent/" description="The EU consent obligation." />
  <LinkCard title="Mediation" href="/advanced/mediation/" description="Extra SDKs, extra disclosures." />
</CardGrid>
```

- [ ] **Step 5: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/privacy/play-data-safety.mdx
npm run build
```

Expected: a word count between `800` and `1000`, then a successful build.

- [ ] **Step 6: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/privacy/play-data-safety.mdx
git commit -m "docs(site): write the Play Data safety guide"
```

---

### Task 15: `/advanced/mediation/` — adapters and initialization hooks

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/advanced/mediation.mdx`
- Read-only sources: `admob-cmp/docs/MEDIATION.md`, `admob-cmp/AGENTS.md`

**Interfaces:**
- Consumes: nothing beyond the word-count script.
- Produces: canonical `/advanced/mediation/`, linked from `/start/what-is-admob-cmp/`, `/privacy/play-data-safety/`, `/advanced/revenue-events/`, `/reference/architecture/`.

**Primary keyword:** `admob mediation kotlin multiplatform`
**Prose target:** 900–1,200 words
**Diagram:** none

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Mediation
description: AdMob mediation on Kotlin Multiplatform — adding adapters per platform, setting network privacy flags with initialization hooks, and verifying that fill arrives.
faq:
  - q: Does AdMob CMP bundle mediation adapters?
    a: No, deliberately. Adapters are platform binaries that must match your GMA SDK version and your network contracts. Add them to your Android module and your Xcode project directly.
  - q: How do I set a mediation network's privacy flags before initialization?
    a: Use an AdInitializationHook and act on AdInitializationPhase.BeforeMobileAdsInitialize. Hooks run at well-defined phases on both platforms, exactly once per real native initialization.
  - q: How do I confirm a mediation adapter is serving?
    a: Read adManager.diagnostics.adapterStatuses() after the manager is Ready, and open Google's Ad Inspector with adManager.diagnostics.openAdInspector() on a test device.
---

import { Aside, Tabs, TabItem, CardGrid, LinkCard } from '@astrojs/starlight/components';

## Why does AdMob CMP ship zero mediation adapters?

## How do I add an adapter on each platform?

## How do I set a network's privacy flags before initialization?

## How do I verify an adapter is actually serving?

## How do I see which network won a given impression?

## Where to next?
```

- [ ] **Step 2: Write the design-rationale section**

Open with the primary keyword. Explain the decision as a decision, not an omission: adapters are platform binaries that must match the GMA SDK version and the publisher's network contracts. Bundling them inside a cross-platform wrapper would pin versions for everyone and break the moment two artifacts carry the same Objective-C class. Add the property that makes this work on iOS specifically: because the library links nothing itself and ships bindings only, adapters resolve against the single copy of GMA in the app, so there is no duplicate-class problem. Link `/reference/architecture/`.

- [ ] **Step 3: Write the per-platform adapter section**

```kotlin
// Android — app or shared Android module
dependencies {
    implementation("com.google.ads.mediation:facebook:…")
}
```

```mdx
On iOS, add the adapter's Swift Package or CocoaPod to the Xcode project, alongside the
`GoogleMobileAds` package added during [iOS setup](/start/ios-setup/).
```

Follow with the operational notes: waterfalls and bidding are configured in the AdMob UI as normal; mediated fill flows through every existing API with no special casing; and the adapter version must be compatible with the GMA version in the app on each platform independently, which is the main source of "it works on Android" reports.

- [ ] **Step 4: Write the initialization-hook section**

State the problem first: many networks require privacy flags to be set *before* SDK initialization, and there is no cross-platform place to do that unless the ad library provides one. `AdInitializationHook` is that place.

```kotlin
val metaConsentHook = object : AdInitializationHook {
    override suspend fun onPhase(phase: AdInitializationPhase, config: AdConfig) {
        when (phase) {
            AdInitializationPhase.BeforeConsentRequest -> Unit
            AdInitializationPhase.BeforeMobileAdsInitialize -> {
                // Call into the adapter's platform API behind an expect/actual:
                // Android: AdSettings.setDataProcessingOptions(arrayOf("LDU"))
                // iOS:     FBAdSettings.setDataProcessingOptions(["LDU"])
                applyNetworkPrivacyFlags()
            }
            AdInitializationPhase.AfterMobileAdsInitialize -> Unit
        }
    }
}

AdConfig(
    androidAppId = "ca-app-pub-…",
    iosAppId = "ca-app-pub-…",
    initializationHooks = listOf(metaConsentHook),
)
```

Explain three properties precisely: the hook body is the caller's code, so adapter-specific calls belong in `androidMain`/`iosMain` behind an `expect`/`actual` and are invoked from the hook; the three phases are `BeforeConsentRequest`, `BeforeMobileAdsInitialize` and `AfterMobileAdsInitialize`; and hooks run **exactly once per real native initialization attempt**, inside a detached scope, so cancelling one `initialize()` caller can never skip or duplicate a hook.

Add the `<Aside type="caution">` that saves a debugging session: the Google Mobile Ads singleton initialises at most once per process. A second `initialize` call with the *same* effective config replays the first result; a call with a *different* app id or request configuration is **ignored with a logged warning**, not re-applied. Decide the settings before the first `initialize` ever runs.

- [ ] **Step 5: Write the verification and attribution sections**

```kotlin
// After adManager.status is AdManagerStatus.Ready
adManager.diagnostics.adapterStatuses().forEach { status ->
    println("${status.adapterName}: initialized=${status.initialized} latencyMs=${status.latencyMillis}")
}

scope.launch { adManager.diagnostics.openAdInspector() }
```

Note that `AdapterInitializationStatus` carries `adapterName`, `initialized`, `latencyMillis` and `description`; that `openAdInspector()` launches Google's Ad Inspector overlay and works on test devices, and is the authoritative way to confirm an adapter serves; and that `adManager.diagnostics.sdkVersion()` reports the underlying GMA version, which is the first thing to check against an adapter's compatibility matrix.

Attribution:

```kotlin
LaunchedEffect(Unit) {
    adManager.events.collect { event ->
        if (event is AdEvent.Loaded) {
            val winner = event.responseInfo?.loadedAdNetworkResponseInfo
            println("won by ${winner?.adSourceName} (${winner?.adapterClassName}) in ${winner?.latencyMillis} ms")
        }
    }
}
```

Explain that `AdResponseInfo.loadedAdNetworkResponseInfo` identifies the winning adapter while `adNetworkResponseInfos` is the whole mediation chain including the networks that failed, each with its own `error`; and that `AdEvent.Paid` carries the mediated revenue, which is covered on `/advanced/revenue-events/`.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Revenue and paid events" href="/advanced/revenue-events/" description="AdValue and the mediation chain." />
  <LinkCard title="Play Data safety" href="/privacy/play-data-safety/" description="Adapters add data disclosures." />
  <LinkCard title="Architecture" href="/reference/architecture/" description="Why iOS ships bindings only." />
  <LinkCard title="iOS setup" href="/start/ios-setup/" description="Where adapter packages go in Xcode." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/advanced/mediation.mdx
npm run build
```

Expected: a word count between `900` and `1200`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/advanced/mediation.mdx
git commit -m "docs(site): write the mediation guide"
```

---

### Task 16: `/advanced/revenue-events/` — AdValue, ResponseInfo, paid events

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/advanced/revenue-events.mdx`
- Read-only sources: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdTelemetry.kt`, `admob-cmp/docs/SETUP.md` §5

**Interfaces:**
- Consumes: nothing beyond the word-count script.
- Produces: canonical `/advanced/revenue-events/`, linked from every format page and from `/advanced/mediation/`.

**Primary keyword:** `admob paid event kotlin multiplatform`
**Prose target:** 800–1,100 words
**Diagram:** none

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Revenue and paid events
description: Track the AdMob paid event on Kotlin Multiplatform — AdValue micros, precision, response info, and sending impression-level revenue to your analytics.
faq:
  - q: How do I get impression-level ad revenue in Kotlin Multiplatform?
    a: Collect AdEvent.Paid from adManager.events or from a controller's events flow. Each event carries a PaidEvent with an AdValue holding valueMicros, currencyCode and precision.
  - q: What does AdValuePrecision mean?
    a: It says how trustworthy the number is — Precise, Estimated, PublisherProvided or Unknown. Aggregate estimated values with care and never present them as billed revenue.
  - q: Which ad network won an impression?
    a: Read responseInfo.loadedAdNetworkResponseInfo from the load event or the paid event. The full waterfall, including failures, is in adNetworkResponseInfos.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';

## How do I observe ad events at all?

## How do I read impression-level revenue?

## What is in AdValue, and what does precision mean?

## What does ResponseInfo tell me?

## How should I send this to analytics?

## Where to next?
```

- [ ] **Step 2: Write the event-stream section**

Open with the primary keyword. Explain the two collection points: `adManager.events` is a global `SharedFlow<AdEvent>` covering every placement, while each controller and pool exposes its own `events` for a single placement. Every `AdEvent` carries a `placementId`, which is what makes the global stream usable.

```kotlin
LaunchedEffect(Unit) {
    adManager.events.collect { event ->
        when (event) {
            is AdEvent.Loaded -> println("loaded: ${event.placementId}")
            is AdEvent.LoadFailed -> println("failed: ${event.error}")
            is AdEvent.Impression -> println("impression: ${event.placementId}")
            is AdEvent.Clicked -> println("click: ${event.placementId}")
            is AdEvent.Paid -> println("revenue: ${event.paidEvent.value.valueMicros}")
            is AdEvent.RewardEarned -> println("reward: ${event.reward.type}")
            else -> Unit
        }
    }
}
```

List the full sealed set once so readers stop guessing: `Loaded`, `LoadFailed`, `ShowFailed`, `Impression`, `Clicked`, `OpenedFullScreen`, `ClosedFullScreen`, `RewardEarned`, `Paid`, and the iOS-only `VideoStarted`, `VideoPlayed`, `VideoPaused`, `VideoEnded`, `VideoMuted`. Note that `Impression`, `Clicked` and `Paid` also carry an optional `adInstanceId`, which is what lets a paid event be joined to the impression it belongs to.

- [ ] **Step 3: Write the paid-event and AdValue sections**

```kotlin
LaunchedEffect(Unit) {
    adManager.events.filterIsInstance<AdEvent.Paid>().collect { event ->
        val value = event.paidEvent.value
        analytics.logAdRevenue(
            placementId = event.placementId,
            valueMicros = value.valueMicros,
            currency = value.currencyCode,
            precision = value.precision.name,
            adSource = event.paidEvent.responseInfo?.loadedAdNetworkResponseInfo?.adSourceName,
        )
    }
}
```

Then the data shapes, stated exactly:

- `PaidEvent` has `placementId`, `value: AdValue`, and `responseInfo: AdResponseInfo?`.
- `AdValue` has `valueMicros: Long`, `currencyCode: String`, and `precision: AdValuePrecision`.
- `AdValuePrecision` is `Unknown`, `Estimated`, `PublisherProvided` or `Precise`.

Add the unit warning explicitly, because it is the most common analytics bug in this area: `valueMicros` is **millionths of a currency unit**. Dividing by 1,000,000 gives the value in `currencyCode`. Do not sum micros across currencies.

Add a second note on precision: `Estimated` values are modelled, not billed. Aggregating them is legitimate for pacing and LTV models, but they must never be presented to a user or a finance team as revenue.

- [ ] **Step 4: Write the ResponseInfo and analytics sections**

```kotlin
val info: AdResponseInfo? = event.paidEvent.responseInfo
println(info?.responseId)                                  // unique response identifier
println(info?.adapterClassName)                            // adapter that loaded it
println(info?.loadedAdNetworkResponseInfo?.adSourceName)   // winning network
info?.adNetworkResponseInfos?.forEach { network ->
    println("${network.adSourceName}: ${network.latencyMillis} ms, error=${network.error?.message}")
}
```

Describe the fields: `AdResponseInfo` carries `responseId`, `adapterClassName`, `extras`, `loadedAdNetworkResponseInfo` and `adNetworkResponseInfos`; each `AdNetworkResponseInfo` carries `adapterClassName`, `latencyMillis`, `error`, `adSourceName`, `adSourceId`, `adSourceInstanceName` and `adSourceInstanceId`. Note that the failure entries in the chain are the fastest way to diagnose a mediation waterfall that is not filling — link `/advanced/mediation/`.

Analytics guidance, three short points: collect in one place rather than per-composable so events are not double-counted across recompositions; key on `placementId` plus `adInstanceId` when correlating impressions and revenue; and remember that these events are observation — they are not the mechanism for granting rewards, which is covered on `/formats/rewarded/`.

- [ ] **Step 5: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Mediation" href="/advanced/mediation/" description="Reading the waterfall behind a paid event." />
  <LinkCard title="Rewarded ads" href="/formats/rewarded/" description="Why events are not the grant point." />
  <LinkCard title="Architecture" href="/reference/architecture/" description="How events flow from the platform SDKs." />
</CardGrid>
```

- [ ] **Step 6: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/advanced/revenue-events.mdx
npm run build
```

Expected: a word count between `800` and `1100`, then a successful build.

- [ ] **Step 7: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/advanced/revenue-events.mdx
git commit -m "docs(site): write the revenue and paid events guide"
```

---

### Task 17: `/advanced/caching-retry-timeouts/` — the four policies

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/advanced/caching-retry-timeouts.mdx`
- Read-only sources: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdPlacement.kt`, `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdTimeoutPolicy.kt`, `admob-cmp/docs/ARCHITECTURE.md`

**Interfaces:**
- Consumes: `<RetryTimeline />` (primary home) and `<FullScreenLifecycle />` (reuse) from `docs-site/src/components/diagrams/`.
- Produces: canonical `/advanced/caching-retry-timeouts/`, linked from every format page.

**Primary keyword:** `admob ad caching retry kotlin multiplatform`
**Prose target:** 1,000–1,300 words
**Diagram:** `RetryTimeline` + `FullScreenLifecycle`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Caching, retry and timeouts
description: AdMob ad caching and retry on Kotlin Multiplatform — AdCachePolicy, AdExpirationPolicy, AdRetryPolicy and AdTimeoutPolicy, and how to size each one.
faq:
  - q: How many ads should I cache?
    a: Cache the number of ads you can genuinely show before their TTL expires. Full-screen ads live one hour by default and app-open ads four, so a large cache mostly buys evictions.
  - q: How many times does a failed ad load retry?
    a: AdRetryPolicy.maxAttempts defaults to 2 — the initial attempt plus one retry — with capped exponential backoff. Set maxAttempts to 1 for no retry at all.
  - q: Are no-fill errors retried?
    a: No. Only retryable failures such as network, timeout and internal errors are retried. No fill and consent failures are non-retryable by policy.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import RetryTimeline from '../../../components/diagrams/RetryTimeline.astro';
import FullScreenLifecycle from '../../../components/diagrams/FullScreenLifecycle.astro';

## Which knobs does AdMob CMP actually expose?

## How does the ad cache work?

## How long do cached ads live?

## When and how does a failed load retry?

## What stops a load or a presentation hanging forever?

## How should I size all of this?

## Where to next?
```

- [ ] **Step 2: Write the overview**

Open with the primary keyword and set expectations: everything on this page is per-placement configuration on `AdPlacement`, so different placements can behave differently in the same app. Name the four policies and what each one bounds:

```mdx
| Policy | Bounds | Default |
|---|---|---|
| `AdCachePolicy` | How many ads are held, and whether one reloads after a show | `maxSize = 1`, `reloadAfterShow = false` |
| `AdExpirationPolicy` | How long a cached ad stays usable | 1h full-screen, 4h app-open, 1h native |
| `AdRetryPolicy` | How a failed load is retried | 2 attempts, 2s initial delay, 30s cap, 2.0 multiplier |
| `AdTimeoutPolicy` | How long a load or a presentation hand-off may take | 30s load, 10s presentation hand-off |
```

- [ ] **Step 3: Write the cache and expiration sections**

```kotlin
AdPlacement(
    id = "interstitial_level_end",
    format = AdFormat.Interstitial,
    adUnitIds = AdUnitIds(android = "…", ios = "…"),
    cachePolicy = AdCachePolicy(
        maxSize = 3,
        reloadAfterShow = true,
        expirationPolicy = AdExpirationPolicy(
            fullScreenTtl = 1.hours,
            appOpenTtl = 4.hours,
            nativeTtl = 1.hours,
        ),
    ),
)
```

Embed `<FullScreenLifecycle />` here and describe the mechanics it shows: the cache is a per-slot FIFO deque up to `maxSize`; `load()` fills it sequentially and a partial fill still reports `Loaded`; `show()` consumes the oldest entry first; eviction happens on every touch; and `clear()` bumps a generation counter so any load or scheduled reload still in flight for the old generation is invalidated instead of repopulating a cache the caller just emptied.

State the constraint checks the constructors enforce, because they throw rather than clamp: `AdCachePolicy.maxSize` must be at least 1, and every duration in `AdExpirationPolicy` must be finite and positive.

Add the native-pool cross-reference in one sentence: for native placements the same `maxSize` budgets **available + in-use** ads together — see `/formats/native/`.

- [ ] **Step 4: Write the retry section and embed the timeline**

```kotlin
AdPlacement(
    id = "interstitial_level_end",
    format = AdFormat.Interstitial,
    adUnitIds = AdUnitIds(android = "…", ios = "…"),
    retryPolicy = AdRetryPolicy(
        maxAttempts = 2,          // total attempts, including the first
        initialDelay = 2.seconds,
        maxDelay = 30.seconds,
        backoffMultiplier = 2.0,
    ),
)
```

Place `<RetryTimeline />` after the sample, then explain: `maxAttempts` counts the **initial attempt plus retries**, so the default of 2 means one retry and `maxAttempts = 1` means none; delay grows by `backoffMultiplier` after each attempt and is capped at `maxDelay`; and the constructor requires `maxAttempts >= 1`, finite positive delays, `maxDelay >= initialDelay`, and `backoffMultiplier >= 1.0`.

Then the classification, which is the part that surprises people:

```mdx
| Failure | Retried? |
|---|---|
| Network, timeout, internal error — GMA code `0`/`2` on Android, `2`/`5`/`11` on iOS | Yes, per `AdRetryPolicy` |
| No fill — GMA code `3` on Android, `1` on iOS | No. This is normal; retry later at a natural moment |
| `AdErrorCode.CONSENT_REQUIRED` | No. Resolve consent first |
| `AdErrorCode.SDK_NOT_READY` | No. Wait for `AdManagerStatus.Ready` |
```

- [ ] **Step 5: Write the timeout and sizing sections**

```kotlin
AdPlacement(
    id = "interstitial_level_end",
    format = AdFormat.Interstitial,
    adUnitIds = AdUnitIds(android = "…", ios = "…"),
    timeoutPolicy = AdTimeoutPolicy(
        loadTimeout = 30.seconds,
        presentationHandOffTimeout = 10.seconds,
    ),
)
```

Explain why this policy exists at all, because it is the least obvious of the four: GMA is a listener SDK, so if a terminal callback never arrives an unbounded `load()` or `show()` suspends forever. For `show()` that is worse than a hang — the presentation token is process-wide, so one wedged presentation blocks every full-screen ad in the app until the process dies.

Then the precise semantics: `loadTimeout` is the maximum time for one load **including retry backoff**; on expiry the state becomes `AdLoadState.Failed` and a late ad is rejected and destroyed by the generation check, exactly as after a `clear()`. `presentationHandOffTimeout` bounds the time between committing an ad to presentation and the SDK reporting anything — it does **not** bound how long a user watches an ad.

Sizing guidance, as concrete recommendations rather than platitudes:

- Interstitials at a natural break: `maxSize = 1`, `reloadAfterShow = true`. One ad, always warm.
- Rewarded ads behind a user-initiated button: `maxSize = 1` and preload when the screen opens, so the button is never dead.
- Bursty full-screen moments, such as end-of-level in a game: `maxSize = 2` or `3`, but only if all of them can plausibly be shown within the one-hour TTL.
- Native feeds: size to the number of ad slots that can be on screen at once, plus one.
- Raise `loadTimeout` only for genuinely slow networks; lowering it below about 10 seconds mostly converts fill into failures.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Interstitial ads" href="/formats/interstitial/" description="The shared full-screen controller contract." />
  <LinkCard title="Native ads" href="/formats/native/" description="How maxSize budgets a pool." />
  <LinkCard title="Architecture" href="/reference/architecture/" description="Generation counters and the state machine." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="No fill, timeouts, and error codes." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/advanced/caching-retry-timeouts.mdx
npm run build
```

Expected: a word count between `1000` and `1300`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/advanced/caching-retry-timeouts.mdx
git commit -m "docs(site): write the caching, retry and timeouts guide"
```

---

### Task 18: `/advanced/test-safety/` — testMode versus strictTestMode

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/advanced/test-safety.mdx`
- Read-only sources: `admob-cmp/AGENTS.md` "Config flags: `testMode` and `strictTestMode`", `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdConfig.kt`, `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdPlacement.kt`

**Interfaces:**
- Consumes: nothing beyond the word-count script.
- Produces: canonical `/advanced/test-safety/`, linked from `/start/quickstart/`, `/start/android-setup/`, `/privacy/consent/`, `/formats/rewarded/`, `/reference/troubleshooting/`.

**Primary keyword:** `admob test ads kotlin multiplatform`
**Prose target:** 800–1,000 words
**Diagram:** none

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Test safety
description: AdMob test ads on Kotlin Multiplatform — why testMode does not serve test ads, what strictTestMode guards, and how to keep live ads out of debug builds.
faq:
  - q: Does testMode make AdMob serve test ads?
    a: No. AdDebugOptions.testMode configures UMP consent debugging only. Test ads come from registering the device in GlobalRequestConfiguration.testDeviceIds or from using a TestAdIds ad unit.
  - q: What does strictTestMode do?
    a: AdPlacement.strictTestMode throws at construction if the placement points at a production ad unit. Turn it on in debug builds so a test build can never request live ads.
  - q: Why does requesting live ads in a debug build matter?
    a: It is invalid traffic, and invalid traffic gets AdMob accounts suspended. That is why the check fails closed with an exception rather than a warning.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';

## Why are there two different test flags?

## What does testMode actually configure?

## How do I actually get test ads?

## What does strictTestMode protect me from?

## How should I wire this into debug and release builds?

## Where to next?
```

- [ ] **Step 2: Write the two-flags section**

Open with the primary keyword and lead with the confusion this page exists to remove, stated as an `<Aside type="danger">`:

> **`testMode` and `strictTestMode` are not the same flag, and neither one does what the other does.**
> `AdDebugOptions.testMode` configures **UMP consent debugging** only. It does **not** make Google Mobile Ads serve test ads.
> `AdPlacement.strictTestMode` is the safety guard: it **throws at construction** if the placement points at a production ad unit.

Then a small table so the distinction sticks:

```mdx
| Flag | Lives on | Affects | Default |
|---|---|---|---|
| `testMode` | `AdConfig` / `AdDebugOptions` | UMP consent debugging: debug geography and consent test devices | `false` |
| `strictTestMode` | `AdPlacement` | Construction-time validation of the ad unit ids | `false` |
```

- [ ] **Step 3: Write "What does testMode actually configure?" and "How do I actually get test ads?"**

`testMode` gates the UMP debug settings: `consentDebugGeography` and `consentTestDeviceIds` change nothing unless `testMode` is `true`. Add the guardrail the library ships: if `testMode` is `true` and no test device ids are configured anywhere, the SDK logs a warning at initialization rather than silently doing nothing — because in that state requests may serve **live** ads.

Real test ads come from exactly two places:

```kotlin
// 1. Google's official sample ad units — always test, on any device
AdPlacement(
    id = "banner_home",
    format = AdFormat.Banner,
    androidAdUnitId = TestAdIds.ANDROID_BANNER,
    iosAdUnitId = TestAdIds.IOS_BANNER,
    strictTestMode = true,
)

// 2. Your production ad units, on a registered test device
AdConfig(
    appIds = AdAppIds(android = "ca-app-pub-…", ios = "ca-app-pub-…"),
    globalRequestConfiguration = GlobalRequestConfiguration(
        testDeviceIds = listOf("YOUR-DEVICE-HASH"),
    ),
)
```

Note that emulators and simulators qualify as test devices automatically, that a physical device needs its hashed id — printed to logcat or the Xcode console on the first unregistered request — and that `TestAdIds` also exposes `ANDROID_APP_ID` and `IOS_APP_ID`, plus ready-made `debugAdConfig` and `debugAdPlacements` values for a complete test setup.

- [ ] **Step 4: Write the strictTestMode section**

Explain the failure it prevents and why it fails closed. Because `testMode` only affects UMP, a developer who trusts it requests **real** ads against production ad units from a debug build. That is invalid traffic, and invalid traffic gets AdMob accounts suspended. A warning was judged insufficient; the check throws.

```kotlin
// Throws IllegalArgumentException at construction
AdPlacement(
    id = "banner_home",
    format = AdFormat.Banner,
    androidAdUnitId = "ca-app-pub-1234567890123456/1234567890",  // production unit
    iosAdUnitId = "ca-app-pub-1234567890123456/0987654321",
    strictTestMode = true,
)
```

State that the exception message names the placement and the offending ad unit ids, and that the escape hatch is deliberate and explicit: set `strictTestMode = false` when live ads in that build are genuinely intended.

- [ ] **Step 5: Write the build-wiring section and "Where to next?"**

Give the shape a real app should use — one flag, threaded through:

```kotlin
// A single build-time flag, e.g. from BuildConfig or an expect/actual
val isDebugBuild: Boolean = /* … */

val placements = listOf(
    AdPlacement(
        id = "banner_home",
        format = AdFormat.Banner,
        adUnitIds = if (isDebugBuild) {
            AdUnitIds(android = TestAdIds.ANDROID_BANNER, ios = TestAdIds.IOS_BANNER)
        } else {
            AdUnitIds(android = "ca-app-pub-…/…", ios = "ca-app-pub-…/…")
        },
        strictTestMode = isDebugBuild,
    )
)
```

Close with the release checklist: `testMode = false`, no debug geography, no consent test device ids, real app ids in the Android manifest and the iOS `Info.plist`, and real ad unit ids in every placement.

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="UMP consent" href="/privacy/consent/" description="Where testMode actually applies." />
  <LinkCard title="Quickstart" href="/start/quickstart/" description="The sample-ad-unit path." />
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="When strictTestMode throws." />
</CardGrid>
```

- [ ] **Step 6: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/advanced/test-safety.mdx
npm run build
```

Expected: a word count between `800` and `1000`, then a successful build.

- [ ] **Step 7: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/advanced/test-safety.mdx
git commit -m "docs(site): write the test safety guide"
```

---

### Task 19: `/reference/architecture/` — module map, threading, state machines

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/reference/architecture.mdx`
- Read-only sources: `admob-cmp/docs/ARCHITECTURE.md`, `admob-cmp/AGENTS.md` "Module internals"

**Interfaces:**
- Consumes: `<ModuleMap />` from `docs-site/src/components/diagrams/ModuleMap.astro` (Plan 4).
- Produces: canonical `/reference/architecture/`, linked from `/start/what-is-admob-cmp/`, `/start/ios-setup/`, `/advanced/mediation/`, `/advanced/caching-retry-timeouts/`, `/project/contributing/`.

**Primary keyword:** `compose multiplatform admob architecture`
**Prose target:** 1,200–1,500 words
**Diagram:** `ModuleMap`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Architecture
description: The Compose Multiplatform AdMob architecture behind AdMob CMP — one shared state machine, two thin platform adapters, bindings-only iOS, and the event flow.
faq:
  - q: How is AdMob CMP structured internally?
    a: One shared state machine in common code, with thin Android and iOS adapters that implement only load, present, destroy and canPresent. Caching, retry, consent gating and event emission all live in common code.
  - q: Why does the iOS artifact contain no Google binaries?
    a: It is a deliberate bindings-only design. Embedding the GMA binary would break mediation adapters with duplicate Objective-C classes, pin the GMA version for every consumer, and raise redistribution questions.
  - q: Is AdMob CMP safe to call from any thread?
    a: Yes. Every public API is main-safe. All Google Mobile Ads and UMP calls are wrapped in Dispatchers.Main.immediate internally.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import ModuleMap from '../../../components/diagrams/ModuleMap.astro';

## How is AdMob CMP put together?

## What lives in common code, and what is platform-specific?

## Why does iOS ship bindings instead of binaries?

## How does the full-screen state machine work?

## What is the threading model?

## How do events flow?

## What were the key design decisions?

## Where to next?
```

- [ ] **Step 2: Write the module map section**

Open with the primary keyword, then place `<ModuleMap />` and describe the packages it shows. Do **not** restate the diagram's own caption or alt prose — Plan 4 Task 10 already defines a prose equivalent per diagram for the `llms.txt` bundle, and duplicating it here creates two sources of truth. Write the package table instead:

```mdx
| Package | Contents |
|---|---|
| `dev.avinya.ads` | Public API: `AdManager`, the controllers, `AdPlacement`, `AdConfig`, events, errors, consent, diagnostics |
| `dev.avinya.ads.internal` | `FullScreenSlotCore` — the shared load/show/cache state machine — and `AdRetry`, capped exponential backoff |
| `dev.avinya.ads.appopen` | `AppOpenAdCoordinator` plus the expected foreground signal |
| `dev.avinya.ads.nativead` | The `NativeAdPool` contract, options, and media info |
| `dev.avinya.ads.nativead.layout` | The `adLayout` DSL, `AdLayoutValidator`, and `AdTemplates` |
| `dev.avinya.ads.ui` | The expected composables `BannerAdView` and `NativeAdView` |
```

Then the platform side in prose: `androidMain` provides `AndroidGoogleAdManager` with slots, pool and banner over the GMA Next-Gen SDK, the `AdMob.manager(context)` singleton, and a `ProcessLifecycleOwner` foreground signal. `iosMain` provides `IosGoogleAdManager` over cinterop bindings, a Kotlin UIKit native-ad renderer, and an `NSNotificationCenter` foreground signal.

Note the published module split for readers who look at Maven: `dev.avinya.ads:admob-cmp` is the umbrella artifact; `admob-cmp-core` holds the engine and `admob-cmp-compose` the Compose surface, so the controller API can be used without Compose.

- [ ] **Step 3: Write the shared-vs-platform section**

State the invariant that gives the library its shape: **one state machine, two thin platform adapters.** Every full-screen slot extends `FullScreenSlotCore`, which owns the load mutex, the TTL'd FIFO cache bounded by `AdCachePolicy.maxSize`, retry, consent gating, event emission and `reloadAfterShow`. Platform classes implement only `loadAd`, `presentAd`, `destroyAd` and `canPresent`.

Add why this matters to a consumer rather than only to a maintainer: it is the reason interstitial, rewarded, rewarded interstitial and app-open behave identically across platforms, and the reason a bug fixed in caching is fixed everywhere at once.

- [ ] **Step 4: Write the bindings-only section**

Explain the model: the iOS implementation compiles against the official GMA and UMP XCFrameworks via Kotlin/Native **cinterop**. The `dev.avinya.ads.admob-cmp` Gradle plugin downloads the zips into `build/admob-cmp-ios-frameworks/` — a version-stamped cache — purely for headers. The published klib contains **bindings**, never Google's binaries. The consuming app links GMA and UMP itself via Swift Package Manager.

Then the alternatives and why each was rejected, stated as engineering trade-offs:

- **A CocoaPods plugin** — the ecosystem and this repository are SPM-based.
- **Embedding binaries with `staticLibraries`** — breaks mediation adapters with duplicate Objective-C classes, pins the GMA version for every consumer, and raises redistribution-licence questions. Evaluated and rejected.

And the accepted costs, stated without spin: consumers must add two SPM packages, and the bound GMA major version must match the SPM-resolved one. Link `/start/ios-setup/` and `/project/roadmap/`.

- [ ] **Step 5: Write the state machine, threading and event-flow sections**

**State machine.** The cache carries a generation counter, bumped by `clear()`. A load or scheduled reload started before a `clear()` checks its required generation before publishing results, so it can never repopulate a cache the caller just asked to be emptied. Presentation ownership is a one-shot handle: the core owns it until the platform slot hands it off to the SDK's callbacks immediately before the actual show call; from then on only the SDK's terminal callback, or a cancellation that raced in *before* hand-off, may close it. Two consequences worth stating explicitly because they are observable from the public API: `show()` is not reentrant per controller — a second call while one presentation is active returns `AdShowResult.NotReady` rather than queueing — and cancelling a caller never decrements the process-wide presence signal once hand-off has happened, so `AppOpenAdCoordinator` never sees "not presenting" while an ad the SDK still owns is on screen.

**Threading.** All GMA and UMP calls are wrapped in `Dispatchers.Main.immediate` internally, so every public API is main-safe and callable from any dispatcher. Registries are lock-protected. On iOS, delegate objects are strongly retained by their owners for exactly the Objective-C delegate's lifetime, because Objective-C delegates are weak references.

**Event flow.**

```
platform SDK callback → slot / pool / controller
    → controller.events   (per-placement SharedFlow)
    → AdManager.events    (global SharedFlow)
```

Note that the sealed `AdEvent` set is `Loaded`, `LoadFailed`, `ShowFailed`, `Impression`, `Clicked`, `OpenedFullScreen`, `ClosedFullScreen`, `RewardEarned`, `Paid`, and the iOS-only `Video*` family, and link `/advanced/revenue-events/`.

- [ ] **Step 6: Write the decision log and "Where to next?"**

```mdx
| Decision | Rationale |
|---|---|
| Suspend functions and Flow instead of listener callbacks | One paradigm. `show()` suspending until dismissal collapses five callback interfaces into one call. |
| Placement-keyed, long-lived controllers | The caching, retry and lifecycle layer every app otherwise rebuilds badly. |
| Consent integrated into initialization | Requesting ads before consent becomes impossible by construction. |
| Bindings-only iOS distribution | Mediation safety and version freedom. |
| Caching in the slot layer, not the SDK preload APIs | Those APIs were beta and asymmetric across platforms at design time. |
| No bundled mediation adapters | Adapters are platform binaries tied to your GMA version and your contracts. |
| `explicitApi()` plus a committed ABI dump | The public surface cannot drift silently. |
```

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Caching, retry and timeouts" href="/advanced/caching-retry-timeouts/" description="The policies this machinery exposes." />
  <LinkCard title="Compatibility" href="/reference/compatibility/" description="Versions and the cinterop klib caveat." />
  <LinkCard title="Contributing" href="/project/contributing/" description="Building and testing the library." />
  <LinkCard title="Roadmap" href="/project/roadmap/" description="Where the iOS distribution model goes next." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/reference/architecture.mdx
npm run build
```

Expected: a word count between `1200` and `1500`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/reference/architecture.mdx
git commit -m "docs(site): write the architecture reference"
```

---

### Task 20: `/reference/compatibility/` — the Kotlin/CMP/minSdk/iOS matrix

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/reference/compatibility.mdx`
- Read-only sources: `admob-cmp/README.md` "Version compatibility", `gradle/libs.versions.toml`, `docs/superpowers/plans/2026-07-29-track3-swiftpm-import-migration.md` "The klib compatibility rules that govern this decision"

**Interfaces:**
- Consumes: `<PlatformMatrix />` from `docs-site/src/components/diagrams/PlatformMatrix.astro` (Plan 4).
- Produces: canonical `/reference/compatibility/`, linked from `/start/what-is-admob-cmp/`, `/start/installation/`, `/reference/architecture/`, `/project/roadmap/`, and the root README.

**Primary keyword:** `admob cmp kotlin version compatibility`
**Prose target:** 800–1,000 words
**Diagram:** `PlatformMatrix`

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Compatibility matrix
description: AdMob CMP Kotlin version compatibility — the Kotlin, Compose Multiplatform, Android minSdk and iOS versions each release supports, plus the cinterop klib caveat.
faq:
  - q: Which Kotlin version does admob-cmp require?
    a: Every published release so far is compiled with Kotlin 2.3.20. Consumers on a different Kotlin minor version may fail to resolve the klib.
  - q: Can I use a newer Kotlin than the library was built with?
    a: Ordinary klibs are backwards compatible from Kotlin 1.9.20, but that guarantee explicitly excludes cinterop klibs, and admob-cmp publishes cinterop klibs. Verify per Kotlin minor rather than assuming.
  - q: Do I need Compose Multiplatform to use AdMob CMP?
    a: Only for the composable surface — BannerAdView, NativeAdView and rememberAdManager. The controller API has no Compose dependency.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';
import PlatformMatrix from '../../../components/diagrams/PlatformMatrix.astro';

## Which versions does each release support?

## Which Kotlin version do I need, and why is it strict?

## Do I need Compose Multiplatform?

## Which Google SDK versions are bound?

## Which platforms can consume the library?

## Where to next?
```

- [ ] **Step 2: Write the release matrix**

Open with the primary keyword, then the table. Every row is a fact from `admob-cmp/README.md` and `gradle/libs.versions.toml` — do not invent rows for unreleased versions.

```mdx
| `admob-cmp` | Kotlin | Compose Multiplatform | Android `minSdk` | iOS deployment target |
|---|---|---|---|---|
| 1.1.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |
| 1.0.2 | 2.3.20 | 1.11.1 | 26 | 15.0 |
| 1.0.1 | 2.3.20 | 1.11.1 | 26 | 15.0 |
| 1.0.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |
```

Place `<PlatformMatrix />` after the table with one sentence noting that Android and iOS are the supported ad targets, and that a Kotlin Multiplatform project may target additional platforms — the ads API simply is not available in those source sets.

- [ ] **Step 3: Write the Kotlin section — the conservative wording, unchanged**

This section must not be softened. Reproduce the reasoning:

The library is published as Kotlin/Native klibs **plus cinterop klibs**. Klibs are not binary-compatible across arbitrary Kotlin versions, so consumers must build with a compatible compiler. `admob-cmp` is compiled with **Kotlin 2.3.20**; consumers on a different Kotlin **minor** version may fail to resolve the klib. Patch versions are generally safe.

Then the quote and the exemption, in an `<Aside type="caution">`:

> From the Kotlin evolution principles: "klib binaries are backwards compatible starting with Kotlin 1.9.20." That guarantee **explicitly excludes cinterop klibs**: "The Kotlin **cinterop** klib binaries are still in Beta. Currently, we cannot give specific compatibility guarantees between different Kotlin versions for cinterop klib binaries."
>
> `admob-cmp` publishes cinterop klibs (`admob-cmp-core-iosSimulatorArm64Cinterop-gmaMain.klib` and `…-umpMain.klib`), so the backward-compatibility rule does **not** cover them. Running a newer Kotlin than the library is "probably fine, not promised" — verify it for your Kotlin minor rather than assuming it.

Add the two directions explicitly: consumers on an **older** Kotlin than the library are unsupported in every case. Consumers on a **newer** Kotlin are the supported direction for ordinary klibs but are unverified for the cinterop klibs.

- [ ] **Step 4: Write the Compose, Google SDK, and consumption sections**

**Compose Multiplatform** is required only for the composable surface: `BannerAdView`, `NativeAdView` and `rememberAdManager`. The controller API — `AdManager`, the controllers, `AdPlacement` — has no Compose dependency. On Android outside Compose, `AdMob.manager(context)` returns the same process-wide singleton.

**Bound Google SDK versions:**

```mdx
| Platform | SDK | Version | How it arrives |
|---|---|---|---|
| Android | Google Mobile Ads Next-Gen | 1.3.0 | Transitive Maven dependency |
| Android | User Messaging Platform | 4.0.0 | Transitive, also pulled by GMA |
| iOS | GoogleMobileAds | 13.7.0 headers; add the `13.x` SPM package | You add the Swift package |
| iOS | GoogleUserMessagingPlatform | 3.1.0 headers; add the `3.x` SPM package | You add the Swift package |
```

Add the rule that follows from bindings-only distribution: the SPM-resolved GMA **major** version must match the major these bindings were generated against. A package older than the bound headers fails to link on newly-added API symbols; the fix is to bump the SPM package.

**Consumption model.** The SDK is consumable from Kotlin Multiplatform and Gradle projects only — it compiles into the consumer's umbrella framework. A pure-Swift iOS app cannot adopt it without a Kotlin Multiplatform shim. State this plainly rather than leaving it to be discovered.

- [ ] **Step 5: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Installation" href="/start/installation/" description="Adding the dependency and the plugin." />
  <LinkCard title="Changelog" href="/reference/changelog/" description="What changed in each release." />
  <LinkCard title="Roadmap" href="/project/roadmap/" description="Why the Kotlin floor may rise in 2.0.0." />
  <LinkCard title="Architecture" href="/reference/architecture/" description="Why cinterop klibs exist at all." />
</CardGrid>
```

- [ ] **Step 6: Verify length, check the forbidden phrasing, and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/reference/compatibility.mdx
grep -n '2\.3\.20 or newer\|2\.3\.20+' src/content/docs/reference/compatibility.mdx || echo "CONSERVATIVE WORDING OK"
npm run build
```

Expected: a word count between `800` and `1000`; `CONSERVATIVE WORDING OK` — the page must never claim "2.3.20 or newer", because the cinterop caveat overrides the klib backward-compatibility rule; then a successful build.

- [ ] **Step 7: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/reference/compatibility.mdx
git commit -m "docs(site): write the compatibility matrix"
```

---

### Task 21: `/reference/troubleshooting/` — symptom → cause → fix  ⭐ PRIORITY

Spec §7 flags this page as the single highest-value opportunity in the programme. It must own the query `admob ios kotlin multiplatform undefined symbols GAD` — a painful, uncontested search that the `dev.avinya.ads.admob-cmp` Gradle plugin genuinely fixes. Write it as the page a developer lands on at 1am and leaves with a working build.

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/reference/troubleshooting.mdx`
- Read-only sources: `admob-cmp/AGENTS.md` "Troubleshooting", `admob-cmp/docs/SETUP.md` "Troubleshooting: iOS linker errors", `admob-cmp-gradle-plugin/src/main/kotlin/dev/avinya/ads/gradle/AdMobCmpPlugin.kt`, `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdError.kt`

**Interfaces:**
- Consumes: nothing from Plan 4 — this page is deliberately text-and-table only, so it renders fast and its content is fully indexable.
- Produces: canonical `/reference/troubleshooting/`. Every guide page's "Where to next?" links here, which is what concentrates internal link equity on the page targeting the highest-intent query.

**Primary keyword:** `admob ios kotlin multiplatform undefined symbols GAD`
**Prose target:** 1,700–2,100 words
**Diagram:** none

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

The `description` and the first H2 both carry the primary keyword — that pairing is the whole point of the page.

```mdx
---
title: Troubleshooting
description: Fix AdMob errors on Kotlin Multiplatform — undefined symbols _OBJC_CLASS_$_GAD when linking iOS tests, no fill, blank banners, consent gates and crashes.
faq:
  - q: Why do I get Undefined symbols _OBJC_CLASS_$_GAD when linking Kotlin/Native iOS tests?
    a: A Kotlin/Native test executable links without Xcode, so it cannot use Swift Package Manager. Apply the dev.avinya.ads.admob-cmp Gradle plugin, which downloads the matching XCFrameworks and adds the linker options to test binaries only.
  - q: Will adding another Swift Package Manager package fix a test link failure?
    a: No. SPM is not involved in a Kotlin/Native test link at all. Only the Gradle plugin fixes it. Adding an SPM package fixes the equivalent failure during an Xcode app build.
  - q: What does AdErrorCode.SDK_NOT_READY mean?
    a: Initialization has not finished. Gate ad requests on adManager.status being AdManagerStatus.Ready. Nothing reaches the network until then.
  - q: Why does my ad request fail with GMA code 3 on Android or 1 on iOS?
    a: No fill. There was no ad available for that request. It is normal, it is non-retryable by policy, and the fix is to retry at a later natural moment rather than immediately.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';

## Why do my iOS Kotlin/Native tests fail with undefined GAD symbols?

## Which link failure am I actually looking at?

## What does the Gradle plugin do to fix it?

## What do SDK_NOT_READY and CONSENT_REQUIRED mean?

## What do the numeric Google Mobile Ads error codes mean?

## Why does my Android app crash at startup?

## Why does my banner render nothing?

## Why does acquire() return null and leave feed rows blank?

## Why does my second show() call return NotReady?

## Why did my second initialize() call do nothing?

## Why does creating a placement throw an exception?

## Why do native video events never fire on Android?

## How do I diagnose the iOS setup automatically?

## Where to next?
```

- [ ] **Step 2: Write the headline section — the undefined GAD symbols answer**

This section must be answerable in the first screen. Lead with the exact error text a reader pasted into a search box:

```
Undefined symbols for architecture arm64:
  "_OBJC_CLASS_$_GADMobileAds", referenced from: …
  "_OBJC_CLASS_$_GADBannerView", referenced from: …
ld: symbol(s) not found for architecture arm64
```

Then the cause in two sentences, no preamble: `admob-cmp` ships cinterop **bindings** only, never Google's binaries. An iOS app resolves `GAD*` and `UMP*` at final link from the Swift packages Xcode links — but a Kotlin/Native test executable has **no Xcode, no `.xcodeproj` and no Swift Package Manager anywhere in the picture**, so it must resolve those symbols itself.

Then the fix, immediately:

```kotlin
// shared/build.gradle.kts
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}
```

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```bash
./gradlew :shared:iosSimulatorArm64Test
```

Then the three clarifications that stop the reader trying the wrong things:

- **This happens even if none of your tests touch ads.** The test binary contains the module's whole main compilation, so any production code calling `rememberAdManager`, `NativeAdView` or the consent APIs brings those references along.
- **A `FakeAdManager` does not help.** The requirement comes from the bindings being present in the link, not from anyone calling them. For faking ad *behaviour* in tests, the SDK ships `NoOpAdManager`.
- **Adding another SPM package does not help.** SPM is not consulted during a Kotlin/Native test link.

Close with an `<Aside type="danger">` restating the last point, because it is the single most common wrong turn.

- [ ] **Step 3: Write "Which link failure am I actually looking at?"**

The same symptom text appears in two completely different builds with two completely different fixes. Disambiguate with a table:

```mdx
| Symptom | When it happens | Cause | Fix |
|---|---|---|---|
| `Undefined symbol: _OBJC_CLASS_$_GAD*` | During an **Xcode / app** build | The GoogleMobileAds SPM package is not added | Add the GMA Swift package — see [iOS setup](/start/ios-setup/) |
| `Undefined symbol: _OBJC_CLASS_$_UMP*` | During an **Xcode / app** build | The UMP SPM package is not added | Add the UserMessagingPlatform Swift package |
| `Undefined symbol: _OBJC_CLASS_$_GAD*` or `_UMP*` | During `:linkDebugTestIos…` | A Kotlin/Native test executable cannot use SPM | Apply the `dev.avinya.ads.admob-cmp` Gradle plugin. Adding an SPM package will **not** fix this. |
| `Undefined symbol: _OBJC_CLASS_$_JSContext` | Either build | JavaScriptCore is not linked; a static Kotlin framework does not autolink it | Add `-framework JavaScriptCore` to `OTHER_LDFLAGS` |
| `Undefined symbol: GADCurrentOrientation…` or other new-API symbols | Either build | The GMA SPM package major is older than the bound headers | Bump the GMA Swift package to `13.x` |
| `__swift_FORCE_LOAD_$_swiftCompatibility56` | During a test link | The Swift runtime compatibility shims are not on the link line | Apply the Gradle plugin; it adds the Swift compatibility library directory |
```

Add one sentence on how to tell which build you are in: if the failing Gradle task name starts with `link` and contains `Test`, it is the Kotlin/Native test link.

- [ ] **Step 4: Write "What does the Gradle plugin do to fix it?"**

Readers trust a fix they understand. Describe the plugin exactly as implemented, with no hand-waving:

- It registers `downloadGmaIos` and `downloadUmpIos`, which fetch the version-stamped GoogleMobileAds and UserMessagingPlatform XCFrameworks into `build/admob-cmp-ios-frameworks/` and verify them against a pinned SHA-256.
- It makes every `link…Test…` task for `iosArm64` and `iosSimulatorArm64` depend on those downloads.
- It adds linker options **to test executables only**: `-F` for each framework directory, `-framework GoogleMobileAds`, `-framework UserMessagingPlatform`, `-framework JavaScriptCore` (GMA force-loads it via `GADOMIDJSContextPool`), and `-L` for the Swift compatibility library directory under the active Xcode toolchain.
- It deliberately leaves the **shipped app framework alone**. Kotlin/Native is supposed to leave those symbols undefined there, for Xcode to bind via SPM.
- It registers `doctorIos`.
- Off macOS it contributes no linker options, so Linux CI can still configure the build.

```bash
./gradlew :shared:downloadGmaIos :shared:downloadUmpIos   # populate the cache explicitly
./gradlew :admob-cmp-core:doctorIos                       # report-only diagnostic
```

- [ ] **Step 5: Write the error-code sections**

`AdErrorCode` is deliberately small — two string constants — because everything else is passed through from the platform SDK:

```mdx
| Code | Constant value | Cause | Fix |
|---|---|---|---|
| `AdErrorCode.SDK_NOT_READY` | `"sdk_not_ready"` | `initialize` has not finished | Gate requests on `adManager.status` being `AdManagerStatus.Ready` |
| `AdErrorCode.CONSENT_REQUIRED` | `"consent_required"` | UMP forbids requests | Run `gatherConsentAndInitialize`, or check `canRequestAds` |
```

Add that both are **fail-fast gates**: nothing reaches the network, so seeing them is the design working rather than an outage. Link `/privacy/consent/`.

Then the numeric codes, which come straight from Google:

```mdx
| Platform codes | Meaning | Retried automatically? |
|---|---|---|
| Android `3`, iOS `1` | No fill — there was no ad for this request | No. Non-retryable by policy; retry at a later natural moment |
| Android `0` and `2`, iOS `2`, `5` and `11` | Internal, network or timeout | Yes, per `AdRetryPolicy` — default `maxAttempts = 2`, the initial attempt plus one retry |
```

Note that `AdError` carries `code`, `message`, `domain` and `responseInfo`, that `code` is either one of the two `AdErrorCode` strings or the platform's own numeric code as a string, and that `responseInfo.adNetworkResponseInfos` is the fastest way to see which mediation networks failed and why. Link `/advanced/caching-retry-timeouts/` and `/advanced/mediation/`.

- [ ] **Step 6: Write the remaining symptom sections**

Each is short — two to five sentences — and every one names the exact symptom a reader would search for.

**"Why does my Android app crash at startup?"** The Google Mobile Ads SDK crashes on launch when the manifest has no `com.google.android.gms.ads.APPLICATION_ID` meta-data entry. Add it with the AdMob **app** id (tilde separator), not an ad-unit id. Link `/start/android-setup/`.

**"Why does my banner render nothing?"** Four causes, one line each: the manager is not `AdManagerStatus.Ready`; the placement uses `BannerRefreshPolicy.Manual` and nothing has called `adManager.banner(placement).refresh()`; a headless `load()` was issued with no `BannerGeometry` on a platform that cannot resolve a width, which now fails rather than guessing; or the request returned no fill. Link `/formats/banner/`.

**"Why does acquire() return null and leave feed rows blank?"** `AdCachePolicy.maxSize` budgets **available + in-use** ads together. With `maxSize = 1` and one row holding the ad, `preload()` returns early and `acquire()` returns `null` for every other row — deterministically, not as a race. Recover by keying the acquisition effect on `pool.availableAds` and re-running `preload`, not just `acquire`, because `release()` destroys the ad and so frees a slot without incrementing `availableAds`. Link `/formats/native/`.

**"Why does my second show() call return NotReady?"** `show()` is not reentrant per controller. A second call while the first presentation is still on screen returns `AdShowResult.NotReady` immediately rather than queueing. Await the first `show()`'s result — it suspends until dismissal — before calling again on the same controller. Link `/formats/interstitial/`.

**"Why did my second initialize() call do nothing?"** The native Google Mobile Ads singleton initialises at most once per process. A second call with the *same* effective `AdConfig` identity — app id plus merged `GlobalRequestConfiguration` — and the same `ConsentMode` replays the first result. A call with a *different* identity is **ignored with a logged warning**, not re-applied, because GMA itself has no supported way to re-initialise with new settings. Decide the settings before the first `initialize` ever runs.

**"Why does creating a placement throw an exception?"** `AdPlacement` validates at construction and fails closed. It throws `IllegalArgumentException` when `id` is blank, when `cachePolicy.maxSize` is below 1, or when `strictTestMode` is enabled and any ad unit id is not a Google test unit. The last one is intentional: requesting real ads from a debug build is invalid traffic, and invalid traffic gets AdMob accounts suspended. Link `/advanced/test-safety/`.

**"Why do native video events never fire on Android?"** iOS emits five video events via `GADVideoControllerDelegate`; the Android Google Mobile Ads Next-Gen SDK exposes no equivalent callback surface on `NativeAd`, so Android emits none. This is an upstream SDK gap, not an AdMob CMP omission. `mediaInfo(token).hasVideoContent` **is** cross-platform. Link `/formats/native/`.

**"How do I diagnose the iOS setup automatically?"**

```bash
./gradlew :admob-cmp-core:doctorIos
./gradlew :admob-cmp-core:doctorIos -PadmobCmp.xcodeproj=path/to/dir
```

It reports the XCFramework download cache state, whether the Xcode project references each SPM product, whether `Info.plist` declares `GADApplicationIdentifier` and whether that value is still Google's sample id, and whether `SKAdNetworkItems` is present. It is diagnostic only and never fails the build.

- [ ] **Step 7: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="iOS setup" href="/start/ios-setup/" description="SPM packages, Info.plist, and JavaScriptCore." />
  <LinkCard title="Installation" href="/start/installation/" description="The Gradle plugin and test linking." />
  <LinkCard title="Caching, retry and timeouts" href="/advanced/caching-retry-timeouts/" description="What is retried, and what is not." />
  <LinkCard title="UMP consent" href="/privacy/consent/" description="Why loads fail before consent." />
</CardGrid>
```

- [ ] **Step 8: Verify length, keyword coverage, and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/reference/troubleshooting.mdx
grep -c 'OBJC_CLASS' src/content/docs/reference/troubleshooting.mdx
grep -c 'dev.avinya.ads.admob-cmp' src/content/docs/reference/troubleshooting.mdx
npm run build
```

Expected: a word count between `1700` and `2100`; at least `5` occurrences of `OBJC_CLASS`; at least `3` occurrences of the Gradle plugin id; then a successful build.

- [ ] **Step 9: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/reference/troubleshooting.mdx
git commit -m "docs(site): write the troubleshooting reference"
```

---

### Task 22: `/reference/changelog/` — release history

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/reference/changelog.mdx`
- Read-only sources: `gradle.properties` (`VERSION_NAME`), `admob-cmp/docs/PUBLISHING.md`, the repository's git tags and GitHub releases

**Interfaces:**
- Consumes: nothing beyond the word-count script.
- Produces: canonical `/reference/changelog/`, linked from `/reference/compatibility/` and `/project/roadmap/`. **From 1.2.0 onward this page is updated in the release pull request itself** — record that policy on `/project/contributing/`.

**Primary keyword:** `admob-cmp changelog`
**Prose target:** 400–700 (a reference index, deliberately below the guide target)
**Diagram:** none

- [ ] **Step 1: Gather the verifiable facts before writing a single line**

Do not write release notes from memory. Derive them:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git tag --list --sort=-version:refname
git log --oneline --no-merges v1.0.2..v1.1.0 2>/dev/null | head -40
gh release list --repo Meet-Miyani/admob-compose-multiplatform
curl -s https://repo1.maven.org/maven2/dev/avinya/ads/admob-cmp/maven-metadata.xml
```

**Rule for unverifiable facts:** if a release date or a change cannot be established from a tag, a release, the git log, or Maven Central metadata, **omit that cell** rather than estimating it. An empty cell is honest; a wrong date is a correction waiting to happen.

- [ ] **Step 2: Replace the frontmatter and write the page skeleton**

```mdx
---
title: Changelog
description: The admob-cmp changelog — what changed in each published release of the AdMob CMP Kotlin Multiplatform library, with links to the GitHub releases.
faq:
  - q: Which version of admob-cmp is current?
    a: 1.1.0, published to Maven Central as dev.avinya.ads:admob-cmp along with the dev.avinya.ads.admob-cmp Gradle plugin marker.
  - q: Does a new admob-cmp release change the required Kotlin version?
    a: Not so far. Every 1.x release is built with Kotlin 2.3.20. A change to the Kotlin floor would be a breaking change and would ship as 2.0.0.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';

## Which versions are published?

## What changed in 1.1.0?

## What changed in 1.0.2?

## What changed in 1.0.1?

## What changed in 1.0.0?

## How is this page maintained?

## Where to next?
```

"Which versions are published?" is a short index table with the version, the release date where verifiable, and a link to the GitHub release. Note that all published artifacts live under `dev.avinya.ads` on Maven Central: `admob-cmp`, `admob-cmp-core`, `admob-cmp-compose`, and the `dev.avinya.ads.admob-cmp.gradle.plugin` marker.

- [ ] **Step 3: Write the per-version entries from the gathered facts**

Each version section gets a short paragraph plus a bullet list, in the house form "what changed → what it means for you". Two entries are established by the repository and must appear:

**1.1.0**
- The `dev.avinya.ads.admob-cmp` **Gradle plugin is published alongside the library**, so Kotlin/Native iOS test executables link without a manual workaround. See `/start/installation/`.
- **Banner geometry is now a host-supplied input.** `BannerAdController.load(geometry, sizePolicy, requestOptions)` replaces the previous self-resolving behaviour, which silently produced full-screen width on iPad split view, Slide Over and popovers. Existing no-argument `load()` calls still compile because `geometry` defaults to `null`, but a headless call with no geometry now fails rather than guessing. See `/formats/banner/`.
- **`refresh()` replays the whole resolved request** — geometry, size policy and request options — from the most recent `load()`, instead of rebuilding options from `placement.requestOptions` and silently dropping custom `AdRequestOptions`.

**1.0.2**
- The library was **split into `admob-cmp-core` and `admob-cmp-compose`** behind the existing `admob-cmp` umbrella artifact, so the controller API can be consumed without a Compose Multiplatform dependency. The umbrella coordinate is unchanged, so no consumer action is required.

For **1.0.1** and **1.0.0**, write only what Step 1 established. If the git log and the GitHub release yield nothing beyond the publication itself, the entry is a single line — "First public release of `dev.avinya.ads:admob-cmp`." for 1.0.0, and for 1.0.1 a one-line summary of the commits between the two tags. Do not pad either entry.

- [ ] **Step 4: Write the maintenance policy and "Where to next?"**

State the policy so the page cannot rot: from 1.2.0 onward, this page is updated in the same pull request that bumps `VERSION_NAME` in `gradle.properties`. A release that does not update this page is incomplete. Link `/project/contributing/`.

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Compatibility" href="/reference/compatibility/" description="Which Kotlin and Compose versions each release needs." />
  <LinkCard title="Roadmap" href="/project/roadmap/" description="What is planned, and what is gated." />
  <LinkCard title="Contributing" href="/project/contributing/" description="How a release is cut." />
</CardGrid>
```

- [ ] **Step 5: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/reference/changelog.mdx
npm run build
```

Expected: a word count between `400` and `700`, then a successful build.

- [ ] **Step 6: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/reference/changelog.mdx
git commit -m "docs(site): write the changelog reference"
```

---

### Task 23: `/project/roadmap/` — Track 3, its four gates, and the blocking unknown

Spec §8 is explicit: the roadmap is published **with its real gates**, states what must land upstream first, explains why a published SDK will not depend on an Alpha build-tool feature, and **commits to no date**. Transparency is itself a differentiator here, and the page captures `kotlin multiplatform swiftpm`.

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/project/roadmap.mdx`
- Read-only sources: `docs/superpowers/plans/2026-07-29-track3-swiftpm-import-migration.md` (entry criteria, the blocking unknown, the RevenueCat contrast, the klib rules, "If the entry criteria never clear")

**Interfaces:**
- Consumes: nothing beyond the word-count script.
- Produces: canonical `/project/roadmap/`, linked from `/reference/architecture/`, `/reference/compatibility/`, `/start/ios-setup/`, and the Plan 5 landing page's roadmap teaser.

**Primary keyword:** `kotlin multiplatform swiftpm`
**Prose target:** 1,000–1,300 words
**Diagram:** none

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Roadmap
description: What is next for AdMob CMP — the Kotlin Multiplatform SwiftPM import migration, the four gates it waits on, one unresolved question, and no promised dates.
faq:
  - q: Will AdMob CMP ever remove the Xcode Swift Package Manager step?
    a: That is the intent of the planned SwiftPM import migration, using JetBrains' official swiftPMDependencies. It is gated on four upstream conditions and one unresolved technical question, and no date is promised.
  - q: Why not embed the Google Mobile Ads binary like some other SDKs do?
    a: Google Mobile Ads is a closed-source prebuilt binary with no redistribution grant, and embedding it would reintroduce the duplicate Objective-C class problem that breaks mediation adapters.
  - q: Will the Gradle plugin be removed?
    a: Only if the SwiftPM import migration ships. If the gates never clear, the plugin is a perfectly serviceable permanent answer and stays.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';

## What is on the roadmap, and what is not promised?

## What is the SwiftPM import migration?

## Which four gates does it wait on?

## What is the one question nobody has answered yet?

## Why not do what other SDKs do and embed the binary?

## What happens if the gates never clear?

## Where to next?
```

- [ ] **Step 2: Write the framing section**

Open with an unambiguous statement of policy, because it is what makes the rest of the page credible: this page describes intent, not commitments. **No item here has a date**, and none will get one until its blockers are cleared. Add that the library is production-ready today and that everything below is about removing setup friction, not about missing functionality.

Then a short orientation table:

```mdx
| | |
|---|---|
| **Shipped** | Six ad formats, UMP consent in the init flow, mediation support, paid events, and the `dev.avinya.ads.admob-cmp` Gradle plugin for Kotlin/Native iOS test linking |
| **Planned, gated** | SwiftPM import migration — removes both the Gradle plugin and the manual Xcode package step |
| **Not planned** | Direct support for ad networks other than AdMob (mediation covers this), and renaming the published Maven coordinate |
```

- [ ] **Step 3: Write "What is the SwiftPM import migration?"**

Carry the primary keyword here. Explain the mechanism: the Kotlin Gradle plugin has gained first-class Swift Package Manager dependencies via `swiftPMDependencies {}`. A Kotlin Multiplatform module declares the Swift package, and the Kotlin Gradle plugin resolves it and generates the cinterop bindings. The official documentation states the property that would make the current Gradle plugin unnecessary:

> "For transitive dependencies (projects that depend on those that use SwiftPM import), the Kotlin Gradle plugin automatically provides the necessary machine code from SwiftPM dependencies. For example, you don't need to do any additional configuration when running Kotlin/Native tests or linking a framework."

Then what shipping it would mean for a consumer, concretely: no `dev.avinya.ads.admob-cmp` plugin, no manually-added Swift packages in Xcode, and one added one-time `integrateLinkagePackage` step. Add that it would raise the Kotlin floor for every consumer and would therefore ship as **2.0.0**, with the 1.x line continuing to receive fixes for as long as the ecosystem lags.

- [ ] **Step 4: Write the four gates**

Present them as a checklist with the reason for each, exactly as the Track 3 plan defines them. Do not soften any of them.

```mdx
### Gate 1 — Kotlin 2.4.20 or later is a stable release

Not Beta, not RC. A published library must not be compiled by a pre-release compiler,
because consumers cannot reliably resolve klibs produced by one.

### Gate 2 — SwiftPM import is no longer Alpha

A published SDK must not depend on an Alpha build-tool feature. The DSL shape itself is
still subject to change while it carries that label.

### Gate 3 — KSP has a release for the target Kotlin version

Consumers using Room or Koin annotation processing cannot move to a Kotlin version KSP
does not support, so shipping ahead of KSP would strand them.

### Gate 4 — Compose Multiplatform has a stable release for that Kotlin version

The composable surface is part of the published artifact, so Compose Multiplatform must
support the same Kotlin version.
```

Add one sentence after the list: all four are **upstream** conditions. None of them can be unblocked by work in this repository, which is the honest reason there is no date.

- [ ] **Step 5: Write the blocking unknown**

Give it its own `<Aside type="caution">` and be candid — this is the section that makes the page worth linking to.

State the question: **does a Maven-published library that uses SwiftPM import actually carry its linkage to consumers?** What the documentation promises is scoped to *projects* — module-to-module dependencies inside one Gradle build. It says nothing about a klib resolved from Maven Central by a stranger.

State the direction of travel honestly: [KT-84420](https://youtrack.jetbrains.com/issue/KT-84420) concluded that Kotlin should start emitting a `Package.swift` structure describing the direct and transitive SwiftPM dependencies needed for linkage — that is, linkage requirements travel as **metadata the consumer resolves**, not as embedded binaries. Whether the equivalent reaches a Maven consumer's `linkDebugTestIosSimulatorArm64` is exactly what has to be proven.

State how it will be proven, as four numbered steps: build a trivial Kotlin Multiplatform library declaring a `swiftPMDependencies` package and exposing one function touching its API; publish it to Maven Local; in a *separate* Gradle build with no SwiftPM declaration of its own, depend on it from `commonMain`, add a trivial test, and run `iosSimulatorArm64Test`; if it links, the migration is viable, and if it fails with undefined symbols, **the migration is dead for a published SDK**, because the consumer would then have to declare the SwiftPM dependency themselves — no better than today's Gradle plugin and worse than the status quo, since it would also drag every consumer onto a new Kotlin minor.

Add the secondary technical prerequisite in the same section, since the same experiment settles it: GoogleMobileAds' `Package.swift` is a `.binaryTarget`, so `swiftPMDependencies` must handle binary-target packages. The official documentation's own example is also a binary distribution, which is encouraging evidence but not proof for this package specifically.

- [ ] **Step 6: Write the contrast and the fallback**

**"Why not do what other SDKs do and embed the binary?"** — a factual contrast, no criticism of anyone. RevenueCat's `purchases-kmp` 3.0.0 reaches the same outcome by a different route: a home-grown `swiftPackage` DSL in their own build logic that compiles `purchases-ios` **from source**, pinned as a git submodule, and statically links the result into the published artifact. That works because `purchases-ios` is open source. Google Mobile Ads is a closed-source prebuilt binary: there is no source to compile, and the XCFramework ships only third-party licences with no grant to redistribute GMA itself. Embedding Google's binary in a `dev.avinya.ads` artifact would also reintroduce the duplicate Objective-C class problem that breaks mediation adapters — the exact thing the bindings-only design avoids. Same destination, different vehicle. Link `/reference/architecture/`.

**"What happens if the gates never clear?"** — say it plainly, because it reassures rather than worries: the Gradle plugin is a perfectly serviceable permanent answer. It is a small amount of code in one place, it is dogfooded by this repository's own modules, and it is invisible to consumers beyond a single `plugins {}` line. The migration will not happen on principle; it will happen when the official path is genuinely stable and the ecosystem has caught up.

- [ ] **Step 7: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Architecture" href="/reference/architecture/" description="Why iOS ships bindings, not binaries." />
  <LinkCard title="Compatibility" href="/reference/compatibility/" description="Today's Kotlin floor, and the cinterop caveat." />
  <LinkCard title="Contributing" href="/project/contributing/" description="How to help move a gate." />
  <LinkCard title="Changelog" href="/reference/changelog/" description="What has already shipped." />
</CardGrid>
```

- [ ] **Step 8: Verify length, check for date promises, and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/project/roadmap.mdx
grep -niE 'Q[1-4] 20[0-9]{2}|by (January|February|March|April|May|June|July|August|September|October|November|December)|coming in 20[0-9]{2}|will ship in' src/content/docs/project/roadmap.mdx || echo "NO DATE PROMISES OK"
npm run build
```

Expected: a word count between `1000` and `1300`; `NO DATE PROMISES OK`; then a successful build.

- [ ] **Step 9: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/project/roadmap.mdx
git commit -m "docs(site): write the roadmap with the SwiftPM import gates"
```

---

### Task 24: `/project/contributing/` — build, test, and the frozen ABI

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/project/contributing.mdx`
- Read-only sources: `admob-cmp/README.md` "Building this module", `admob-cmp/AGENTS.md` "Module internals", `admob-cmp/docs/PUBLISHING.md`, `admob-cmp/CLAUDE.md`

**Interfaces:**
- Consumes: nothing beyond the word-count script.
- Produces: canonical `/project/contributing/`, linked from `/reference/architecture/`, `/reference/changelog/`, `/project/roadmap/`.

**Primary keyword:** `admob-cmp contributing`
**Prose target:** 700–900 words
**Diagram:** none

- [ ] **Step 1: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Contributing
description: Contributing to admob-cmp — how to build and test the library, why the public ABI is frozen, and what a mergeable pull request looks like.
faq:
  - q: How do I run the admob-cmp tests?
    a: Run ./gradlew :admob-cmp:iosSimulatorArm64Test for the iOS runner and ./gradlew :admob-cmp:testAndroidHostTest for the JVM runner. Tests live in commonTest and use hand-written fakes.
  - q: Why does my pull request fail the ABI check?
    a: The public API surface is frozen and validated. After any intentional public change, run ./gradlew :admob-cmp:updateKotlinAbi and commit the regenerated api klib.api dump.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';

## How do I build and test the library?

## Why is the public API surface frozen?

## What does a good pull request look like?

## What must never change?

## How is a release cut?

## Where to next?
```

- [ ] **Step 2: Write the build-and-test section**

Open with the primary keyword. Give the commands with what each one is for:

```bash
./gradlew :admob-cmp:iosSimulatorArm64Test   # common tests, iOS runner
./gradlew :admob-cmp:testAndroidHostTest     # common tests, JVM runner
./gradlew :admob-cmp:checkKotlinAbi          # public API surface check
./gradlew :admob-cmp:updateKotlinAbi         # regenerate api/ after an intentional API change
./gradlew :admob-cmp-core:doctorIos          # diagnose iOS consumer integration
```

Describe the test design so contributions match it: tests live in `commonTest` only and use hand-written fakes rather than a mocking framework, with injectable `clock` and `foregroundEvents` seams so time-dependent and lifecycle-dependent behaviour is testable without a device. Note that this is why the same tests run on both the iOS and JVM runners.

- [ ] **Step 3: Write the frozen-ABI section**

Explain the mechanism and the reason together: the module uses `explicitApi()` plus Kotlin Gradle plugin ABI validation, and `api/admob-cmp.klib.api` is committed. Any change to the public surface changes that file, so a reviewer sees the API diff in the pull request rather than discovering it after publication. Four versions are published and consumed, so a breaking change is not free.

```bash
./gradlew :admob-cmp:updateKotlinAbi
git add api/
```

Add the `<Aside type="caution">`: if `checkKotlinAbi` fails and the API change was **not** intentional, the fix is to change the code back, not to regenerate the dump.

- [ ] **Step 4: Write the pull-request and invariants sections**

**A good pull request:**
- Has a test that fails before the change and passes after it.
- Runs both test tasks and `checkKotlinAbi` locally.
- **Ships its documentation in the same pull request.** Docs live in this repository at `docs-site/src/content/docs/`, so an API change and its guide update version together — that is the entire reason the docs site is not a separate repository.
- Updates `/reference/changelog/` when it changes behaviour a consumer can observe.
- Keeps the trademark and neutrality rules intact: nominative use of "AdMob" only, and no comparative claims about other projects beyond verifiable capability facts.

**What must never change** — the hard invariants, stated so a contributor does not propose them in good faith:
- The published Maven coordinate `dev.avinya.ads:admob-cmp`.
- The frozen public ABI, absent a deliberate major version.
- The bindings-only iOS distribution — never add `staticLibraries` to the cinterop `.def` files.
- The rule that `AdManager` implementations are not constructed directly by consumers.

- [ ] **Step 5: Write the release section and "Where to next?"**

Keep it short and route to the maintainer guide rather than duplicating it: releases bump `VERSION_NAME` in `gradle.properties`, publish to Maven Central through the repository's publish workflow, and **must update `/reference/changelog/` in the same pull request**. The full maintainer procedure lives in `admob-cmp/docs/PUBLISHING.md` in the repository.

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Architecture" href="/reference/architecture/" description="Where the code lives and why." />
  <LinkCard title="Using with AI agents" href="/project/ai-agents/" description="AGENTS.md and llms.txt." />
  <LinkCard title="Roadmap" href="/project/roadmap/" description="What is gated, and on what." />
</CardGrid>
```

- [ ] **Step 6: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/project/contributing.mdx
npm run build
```

Expected: a word count between `700` and `900`, then a successful build.

- [ ] **Step 7: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/project/contributing.mdx
git commit -m "docs(site): write the contributing guide"
```

---

### Task 25: `/project/ai-agents/` — AGENTS.md and llms.txt

**Files:**
- Modify (replace body, keep path): `docs-site/src/content/docs/project/ai-agents.mdx`
- Read-only sources: `admob-cmp/AGENTS.md`, Plan 2's `starlight-llms-txt` configuration

**Interfaces:**
- Consumes: the `llms.txt` routes emitted by `starlight-llms-txt`, configured in Plan 2.
- Produces: canonical `/project/ai-agents/`, linked from `/project/contributing/` and the repository README.

**Primary keyword:** `admob kotlin multiplatform ai agent`
**Prose target:** 700–900 words
**Diagram:** none

- [ ] **Step 1: Confirm which llms routes the build actually emits — before writing the page**

Do not list routes from memory; list what the site produces.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm run build
ls -1 dist/llms*.txt
```

Expected: one or more of `dist/llms.txt`, `dist/llms-full.txt`, `dist/llms-small.txt`. **Write into the page only the files this command actually lists.**

- [ ] **Step 2: Replace the frontmatter and lay in the H2 skeleton**

```mdx
---
title: Using with AI agents
description: Point an AI coding agent at AdMob Kotlin Multiplatform integration using AGENTS.md and llms.txt, so it writes code that compiles against the frozen API.
faq:
  - q: How do I make a coding agent write correct AdMob CMP code?
    a: Point it at AGENTS.md in the repository, which is a condensed API guide with the entry points, the format-to-API table, the hard rules and the troubleshooting table. Add the site's llms.txt for the full guide text.
  - q: Does ads.avinya.dev allow AI crawlers?
    a: Yes. The documentation host allows AI crawlers and publishes an llms.txt bundle, because a large share of SDK integration now happens through a coding assistant.
---

import { Aside, CardGrid, LinkCard } from '@astrojs/starlight/components';

## Why does this page exist?

## What is AGENTS.md?

## How do I point my agent at it?

## What is llms.txt, and what does this site publish?

## Which rules do agents get wrong most often?

## Where to next?
```

- [ ] **Step 3: Write the AGENTS.md sections**

Open with the primary keyword and the honest reason: a large share of SDK integration now happens through a coding assistant, and an assistant that guesses at an API produces code that does not compile against a frozen ABI. So the repository ships a file written **for** that reader.

Describe what `AGENTS.md` contains, because that is what makes it worth pointing at: the entry points, the canonical initialization snippet, a format-to-API table mapping each `AdFormat` to its controller, composable and test ad ids, the full-screen load/show pattern, the banner and native sections including geometry and pool accounting, the app-open coordinator, the consent and privacy-options rule, seven hard rules, the `testMode` versus `strictTestMode` distinction, executable iOS setup steps, and a symptom-to-fix troubleshooting table.

How to point an agent at it:

```
Read https://github.com/Meet-Miyani/admob-compose-multiplatform/blob/master/admob-cmp/AGENTS.md
before writing any AdMob CMP code. Follow its hard rules exactly.
```

Add that for agents working inside a repository that depends on the library, the durable form is a project instruction file — a `CLAUDE.md`, an `AGENTS.md`, or the equivalent — containing that same instruction, so it applies to every session rather than one prompt.

- [ ] **Step 4: Write the llms.txt section**

Explain the convention in one sentence: `llms.txt` is a plain-text bundle of a site's documentation, published at a predictable path so a language model can ingest the whole thing without crawling and parsing HTML. Then list **only** the routes Step 1 confirmed, in a table with a one-line description of each and its intended use — the index for orientation, the full bundle for a complete offline context, and the small bundle when a context window is tight.

Add the crawler policy: the documentation host allows AI crawlers deliberately. Link `/reference/troubleshooting/` and note that it is the single most useful page to paste into an agent's context when a build is failing.

Note that Plan 4's diagrams each carry a prose equivalent that is included in the `llms.txt` bundle, so an agent reading the text bundle receives the content of every diagram even though it cannot see the SVG. Do not restate that prose here.

- [ ] **Step 5: Write "Which rules do agents get wrong most often?"**

This is the section that makes the page genuinely useful rather than meta. A short list, each item one line, each drawn from the hard rules:

- Constructing an `AdManager` implementation instead of using `rememberAdManager()` or `AdMob.manager(context)`.
- Generating per-item placement ids such as `"feed_item_$index"`. Controllers are cached per id and never auto-evicted; reuse one placement id and let the native pool serve per-item ads.
- Calling `show()` from `GlobalScope`. It suspends for the ad's full on-screen lifetime and belongs in a UI-scoped coroutine.
- Writing `TestAdIds.Android.BANNER`. The constants are flat: `TestAdIds.ANDROID_BANNER`.
- Writing the `adLayout` DSL as property assignments. The nodes are functions with named arguments: `headline(maxLines = 2)`.
- Assuming `testMode = true` serves test ads. It configures UMP consent debugging only.
- Gating a privacy-settings button on `ConsentStatus.Obtained` instead of `PrivacyOptionsRequirementStatus.Required`.
- Calling `pool.peek(...)`. It is not public API.
- Relying on native video events on Android. Only iOS emits them.

- [ ] **Step 6: Write "Where to next?"**

```mdx
## Where to next?

<CardGrid>
  <LinkCard title="Troubleshooting" href="/reference/troubleshooting/" description="The best page to paste into an agent's context." />
  <LinkCard title="Contributing" href="/project/contributing/" description="The frozen ABI an agent must respect." />
  <LinkCard title="Quickstart" href="/start/quickstart/" description="The canonical integration shape." />
</CardGrid>
```

- [ ] **Step 7: Verify length and build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/wordcount.mjs src/content/docs/project/ai-agents.mdx
npm run build
```

Expected: a word count between `700` and `900`, then a successful build.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/project/ai-agents.mdx
git commit -m "docs(site): write the AI agents guide"
```

---

### Task 26: Redirect the legacy `admob-cmp/docs/*.md` files to their canonical URLs

Eight files under `admob-cmp/docs/` now have a canonical equivalent on `ads.avinya.dev`. GitHub blob pages are indexable, so leaving the old prose in place creates two competing documents for the same query — and the GitHub copy will not carry the expanded content, the diagrams, or the internal links. Replace each body with a pointer. Do **not** delete the files: existing external links to them must keep resolving.

**Files:**
- Modify (replace body, keep path): `admob-cmp/docs/SETUP.md`, `BANNER.md`, `INTERSTITIAL.md`, `NATIVE.md`, `APP_OPEN.md`, `CONSENT.md`, `MEDIATION.md`, `ARCHITECTURE.md`
- Modify: `admob-cmp/README.md` (the "Documentation" link list)
- **Do not touch:** `admob-cmp/docs/PUBLISHING.md` (a maintainer guide with no public equivalent — `/project/contributing/` links to it rather than duplicating it) and `admob-cmp/AGENTS.md` (deliberately retained as the agent-facing condensed guide that `/project/ai-agents/` points at)

**Interfaces:**
- Consumes: all 24 canonical URLs published by Tasks 2–25.
- Produces: eight pointer stubs and an updated README link list. No new files.

- [ ] **Step 1: Replace the eight bodies with pointer stubs**

Each stub is a title, one sentence, the canonical link or links, and nothing else. Run from the repository root:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP

write_pointer() {
  local file="$1"; local title="$2"; local body="$3"
  cat > "admob-cmp/docs/$file" <<EOF
# $title

**This guide has moved to the documentation site.**

$body

The documentation now lives at <https://ads.avinya.dev> and ships from this repository
under \`docs-site/\`, so API changes and their documentation are updated in the same
pull request.
EOF
}

write_pointer SETUP.md "Setup & Initialization" \
"- Gradle, version catalogs and the Gradle plugin — <https://ads.avinya.dev/start/installation/>
- Android manifest, AD_ID and build settings — <https://ads.avinya.dev/start/android-setup/>
- iOS Swift packages, Info.plist, ATT and \`doctorIos\` — <https://ads.avinya.dev/start/ios-setup/>
- Initialization and consent modes — <https://ads.avinya.dev/privacy/consent/>
- Linker and setup failures — <https://ads.avinya.dev/reference/troubleshooting/>"

write_pointer BANNER.md "Banner Ads" \
"Read it at <https://ads.avinya.dev/formats/banner/>."

write_pointer INTERSTITIAL.md "Interstitial & Rewarded Ads" \
"- Interstitials, caching and the full-screen contract — <https://ads.avinya.dev/formats/interstitial/>
- Rewarded and rewarded interstitial ads — <https://ads.avinya.dev/formats/rewarded/>"

write_pointer NATIVE.md "Native Ads" \
"Read it at <https://ads.avinya.dev/formats/native/>."

write_pointer APP_OPEN.md "App-Open Ads" \
"Read it at <https://ads.avinya.dev/formats/app-open/>."

write_pointer CONSENT.md "Consent & Privacy (UMP)" \
"Read it at <https://ads.avinya.dev/privacy/consent/>."

write_pointer MEDIATION.md "Mediation" \
"Read it at <https://ads.avinya.dev/advanced/mediation/>."

write_pointer ARCHITECTURE.md "Architecture" \
"Read it at <https://ads.avinya.dev/reference/architecture/>."

wc -l admob-cmp/docs/*.md
```

Expected: `SETUP.md` around 16 lines and each of the other seven around 10, with `PUBLISHING.md` unchanged at its original length.

- [ ] **Step 2: Repoint the README's documentation list**

In `admob-cmp/README.md`, replace the "## Documentation" list with links to the canonical URLs:

```markdown
## Documentation

Full documentation: <https://ads.avinya.dev>

- [Quickstart](https://ads.avinya.dev/start/quickstart/) — a rendering test ad in five minutes
- [Installation](https://ads.avinya.dev/start/installation/) — Gradle, version catalog, and the Gradle plugin
- [Android setup](https://ads.avinya.dev/start/android-setup/) · [iOS setup](https://ads.avinya.dev/start/ios-setup/)
- [Banner](https://ads.avinya.dev/formats/banner/) · [Interstitial](https://ads.avinya.dev/formats/interstitial/) · [Rewarded](https://ads.avinya.dev/formats/rewarded/) · [App-open](https://ads.avinya.dev/formats/app-open/) · [Native](https://ads.avinya.dev/formats/native/)
- [UMP consent](https://ads.avinya.dev/privacy/consent/) · [App Tracking Transparency](https://ads.avinya.dev/privacy/app-tracking-transparency/) · [Play Data safety](https://ads.avinya.dev/privacy/play-data-safety/)
- [Mediation](https://ads.avinya.dev/advanced/mediation/) · [Revenue events](https://ads.avinya.dev/advanced/revenue-events/) · [Caching, retry and timeouts](https://ads.avinya.dev/advanced/caching-retry-timeouts/) · [Test safety](https://ads.avinya.dev/advanced/test-safety/)
- [Architecture](https://ads.avinya.dev/reference/architecture/) · [Compatibility](https://ads.avinya.dev/reference/compatibility/) · [Troubleshooting](https://ads.avinya.dev/reference/troubleshooting/) · [Changelog](https://ads.avinya.dev/reference/changelog/)
- [Roadmap](https://ads.avinya.dev/project/roadmap/) · [Contributing](https://ads.avinya.dev/project/contributing/) · [Using with AI agents](https://ads.avinya.dev/project/ai-agents/)
- [Publishing](docs/PUBLISHING.md) — maintainer guide, repository only

Integrating with an AI coding agent? Point it at [AGENTS.md](AGENTS.md) and
<https://ads.avinya.dev/llms.txt>.
```

Also update the two inline references earlier in that README — the `docs/SETUP.md` link under **Installation** and the `docs/ARCHITECTURE.md` link in the opening paragraph — to `https://ads.avinya.dev/start/installation/` and `https://ads.avinya.dev/reference/architecture/`.

- [ ] **Step 3: Find every remaining in-repository link to a moved file**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
grep -rn "docs/SETUP.md\|docs/BANNER.md\|docs/INTERSTITIAL.md\|docs/NATIVE.md\|docs/APP_OPEN.md\|docs/CONSENT.md\|docs/MEDIATION.md\|docs/ARCHITECTURE.md\|SETUP.md#" \
  --include="*.md" --include="*.kt" --include="*.kts" . \
  --exclude-dir=build --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=docs-site
```

Repoint every hit at the canonical URL, **except** hits inside `docs/superpowers/` (historical plans and specs, which must stay as written) and inside `admob-cmp/AGENTS.md` (kept intentionally). If a hit is a deep link such as `SETUP.md#kotlinnative-test-executables`, point it at `https://ads.avinya.dev/reference/troubleshooting/`.

- [ ] **Step 4: Verify no duplicated prose survives**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
grep -c 'ads.avinya.dev' admob-cmp/docs/*.md
grep -l 'BannerAdView\|adLayout\|AppOpenAdCoordinator' admob-cmp/docs/*.md || echo "NO DUPLICATED SAMPLES OK"
```

Expected: a non-zero count for each of the eight stubs and `0` for `PUBLISHING.md`; then `NO DUPLICATED SAMPLES OK` — no moved file may still contain guide code samples.

- [ ] **Step 5: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add admob-cmp/docs admob-cmp/README.md
git commit -m "docs: point the legacy markdown guides at their canonical URLs"
```

---

### Task 27: Final content audit — screenshots, links, metadata, and stub removal

The last task is a gate, not a chore. It retrofits the Plan 7 screenshots into the pages written before Plan 7 landed, proves no diagram placeholder survived, and checks every rule this plan asserts but no build step enforces.

**Files:**
- Create: `docs-site/scripts/audit-content.mjs`
- Modify: any page the audit flags, plus the pages that gain a screenshot in Step 2

**Interfaces:**
- Consumes: all 24 pages; `docs-site/src/components/diagrams/*.astro` (Plan 4); `docs-site/src/components/Screenshot.astro` and `docs-site/src/assets/screenshots/screenshots.json` (Plan 7).
- Produces: `node docs-site/scripts/audit-content.mjs`, exiting non-zero on any violation.

- [ ] **Step 1: Write the audit script**

Create `docs-site/scripts/audit-content.mjs`:

```js
#!/usr/bin/env node
/**
 * docs-site/scripts/audit-content.mjs
 *
 * Enforces the content rules the Astro build cannot: description length,
 * question-shaped H2s, working internal links, no surviving diagram
 * placeholders, and a "Where to next?" section on every guide page.
 *
 * Usage: node scripts/audit-content.mjs
 * Exits 1 on any violation, printing one line per problem.
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { exit } from 'node:process';

const DOCS = 'src/content/docs';
const DIAGRAMS = 'src/components/diagrams';
const problems = [];

/** Every .mdx page under src/content/docs, excluding the Plan 5 landing page. */
function pages(dir = DOCS) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...pages(path));
    else if (entry.name.endsWith('.mdx') && path !== join(DOCS, 'index.mdx')) out.push(path);
  }
  return out;
}

/** Set of every valid canonical URL, derived from the file tree itself. */
const files = pages();
const validUrls = new Set(
  files.map((f) => '/' + f.slice(DOCS.length + 1).replace(/\.mdx$/, '') + '/')
);
validUrls.add('/');
validUrls.add('/api/');

for (const file of files) {
  const raw = readFileSync(file, 'utf8');
  const fm = raw.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!fm) {
    problems.push(`${file}: no frontmatter`);
    continue;
  }
  const front = fm[1];

  const desc = front.match(/^description:\s*(.+)$/m);
  if (!desc) problems.push(`${file}: no description`);
  else if (desc[1].trim().length > 160)
    problems.push(`${file}: description is ${desc[1].trim().length} chars (max 160)`);

  const body = raw.slice(fm[0].length);

  // Every H2 must be a question, except the standard closing section.
  for (const line of body.split('\n')) {
    if (!line.startsWith('## ')) continue;
    const heading = line.slice(3).trim();
    if (heading === 'Where to next?') continue;
    if (!heading.endsWith('?')) problems.push(`${file}: H2 is not a question — "${heading}"`);
  }

  if (!body.includes('## Where to next?')) problems.push(`${file}: no "Where to next?" section`);

  // Internal links must resolve to a real page and end in a slash.
  for (const m of body.matchAll(/href="(\/[^"]*)"|\]\((\/[^)]*)\)/g)) {
    const url = m[1] ?? m[2];
    if (url.startsWith('/llms')) continue;
    if (!url.endsWith('/')) problems.push(`${file}: internal link missing trailing slash — ${url}`);
    else if (!validUrls.has(url)) problems.push(`${file}: internal link has no page — ${url}`);
  }
}

// No diagram may still be a Plan 3 placeholder.
if (existsSync(DIAGRAMS)) {
  for (const name of readdirSync(DIAGRAMS)) {
    const path = join(DIAGRAMS, name);
    if (readFileSync(path, 'utf8').includes('diagram-placeholder'))
      problems.push(`${path}: still a Plan 3 placeholder — Plan 4 must replace it`);
  }
}

if (problems.length === 0) {
  console.log(`AUDIT OK — ${files.length} pages`);
  exit(0);
}
for (const p of problems) console.error(p);
console.error(`\n${problems.length} problem(s)`);
exit(1);
```

- [ ] **Step 2: Retrofit the Plan 7 screenshots**

Tasks 2–25 were written before Plan 7 landed. Add screenshots now, using Plan 7's component and its manifest — never a raw `<img>` and never a direct asset path.

**The dark-only constraint is binding.** Plan 7 captures the demo's `DebugTokens`, a theme-fixed dark palette, so **every ad-format screenshot is dark-only**. Only the `consent-*` and `att-*` captures have light/dark pairs. Never write a page, a caption, or an `alt` string that implies a light-theme format screenshot exists, and never place a format screenshot in a context that promises it follows the reader's theme.

First read the manifest to see exactly which captures exist:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
cat src/assets/screenshots/screenshots.json
```

Then add **one** `<Screenshot />` per page, placed after that page's first worked example, using only ids the manifest lists:

| Page | Screenshot family | Theme availability |
|---|---|---|
| `/formats/banner/` | `banner-*` | dark only |
| `/formats/interstitial/` | `interstitial-*` | dark only |
| `/formats/rewarded/` | `rewarded-*` | dark only |
| `/formats/app-open/` | `app-open-*` | dark only |
| `/formats/native/` | `native-*` | dark only |
| `/privacy/consent/` | `consent-*` | light **and** dark |
| `/privacy/app-tracking-transparency/` | `att-*` | light **and** dark |

Import it in each of those seven pages exactly as Plan 7 defines:

```mdx
import Screenshot from '../../../components/Screenshot.astro';
```

Do not add screenshots to the remaining pages. `/reference/troubleshooting/` in particular stays text-and-table only, so it renders fast and stays fully indexable.

- [ ] **Step 3: Run the audit and the word-count sweep**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node scripts/audit-content.mjs
node scripts/wordcount.mjs $(find src/content/docs -name '*.mdx' ! -name 'index.mdx' | sort)
```

Expected: `AUDIT OK — 24 pages`, then 24 word counts. Check each against the page inventory table at the top of this plan. Any page below its floor is under-written and must be expanded — a page under 300 prose words is the exact thin-content failure this plan exists to fix.

- [ ] **Step 4: Confirm the total and the frontmatter coverage**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
find src/content/docs -name '*.mdx' ! -name 'index.mdx' | wc -l
grep -L '^faq:' $(find src/content/docs -name '*.mdx' ! -name 'index.mdx') || echo "ALL PAGES HAVE FAQ OK"
grep -rn 'Plan 3 writes this page' src/content/docs || echo "NO PLAN 2 STUBS LEFT OK"
```

Expected: `24`; then `ALL PAGES HAVE FAQ OK`; then `NO PLAN 2 STUBS LEFT OK`.

- [ ] **Step 5: Full build and Plan 2's own verifier**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm run build
npm run verify
npm run check:overflow
```

Expected: the build succeeds with no `[starlight]` sidebar warning; `verify` passes its sitemap, robots, llms, canonical, OG and JSON-LD assertions; and `check:overflow` reports no horizontal overflow at 375 px — the wide tables on `/reference/troubleshooting/` and `/reference/compatibility/` are the ones most likely to fail this, and the fix is an `overflow-x: auto` wrapper, never a narrower table.

- [ ] **Step 6: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/scripts/audit-content.mjs docs-site/src/content/docs
git commit -m "docs(site): add the content audit gate and retrofit screenshots"
```

---

## Addendum: Plan 4 and Plan 7 integration

Both plans landed after Tasks 2–18 of this plan were drafted. Their contracts are binding on every task above.

**Plan 4 — diagrams.** The eight components exist at `docs-site/src/components/diagrams/`: `ModuleMap`, `InitSequence`, `FullScreenLifecycle`, `NativePoolLifecycle`, `BannerGeometry`, `ConsentDecisionTree`, `RetryTimeline`, `PlatformMatrix`. Consequences:

- Task 1 Step 3's `-f` guard makes the stub generation a **no-op** for every component Plan 4 has written. Run it anyway — it is idempotent, and it prints `KEEP` for each existing file, which is itself the verification that all eight are present.
- Plan 4 Task 10 defines a **prose equivalent per diagram** for the `llms.txt` bundle. Pages must **not** restate that prose. Write the surrounding explanation the page needs, embed the component, and let the diagram carry its own description. Duplicating it creates two sources of truth that will diverge on the first diagram revision.
- Import path from a page two directories deep, which every page in this plan is: `../../../components/diagrams/<Name>.astro`.

**Plan 7 — screenshots.** Screenshots are embedded only through `docs-site/src/components/Screenshot.astro`, which resolves ids from `docs-site/src/assets/screenshots/screenshots.json`. Never a raw `<img>`, never a direct asset path.

- **Every ad-format screenshot is dark-only.** The demo's `DebugTokens` is a theme-fixed dark palette, so `banner-*`, `interstitial-*`, `rewarded-*`, `app-open-*` and `native-*` have no light variant.
- Only `consent-*` and `att-*` have light/dark pairs.
- No page, caption, or `alt` string may imply a light-theme format screenshot exists, and no format screenshot may sit in a context that promises it follows the reader's theme.
- Screenshots are retrofitted in **Task 27 Step 2**, not in the individual page tasks, so the page tasks stay executable even if Plan 7's captures are regenerated.

---

## Self-review

Run against `docs/superpowers/specs/2026-07-31-public-visibility-design.md` with fresh eyes.

**1. Spec coverage**

| Spec requirement | Where it is implemented |
|---|---|
| §8 URL map — all 24 authored pages | Tasks 2–25, one task per page; the inventory table lists file, URL, keyword, diagram and word target |
| §8 — no `/docs/` prefix | Global Constraints; enforced by Plan 2's file paths, which this plan is forbidden to move |
| §7 keyword → page mapping | Every page task names its primary keyword; the inventory table is the single index |
| §7 content-depth finding — rewrite, not migrate | Global Constraints state it explicitly; each task carries a word floor; Task 27 Step 3 sweeps all 24 |
| §7 — troubleshooting owns the undefined-GAD query | Task 21, 1,700–2,100 words, keyword in `description` and the first H2, with a grep gate in Step 8 |
| §7 — native ads as uncontested opportunity | Task 11, 1,600–2,000 words, the deepest format page |
| §8 — roadmap published with real gates, no date | Task 23: four gates, the blocking unknown, the RevenueCat contrast, the fallback, and a grep gate for date promises |
| §8 — neutral capability matrix, not a teardown | Task 2 Step 4, with explicit non-negotiable rules and a "when another library fits better" paragraph |
| §8 — question-based H2s mirroring People-Also-Ask | Global Constraints; every skeleton is written that way; enforced by `audit-content.mjs` |
| §8 — explicit cross-links | Every page ends in "Where to next?"; the audit script fails a page without one and validates every internal link target |
| Duplicate-content risk from the legacy Markdown | Task 26 replaces eight bodies with pointers and repoints the README |
| Track 3 source material | Task 23 draws entirely from `2026-07-29-track3-swiftpm-import-migration.md` |
| Trademark line | Global Constraints: footer only, Plan 2/5 owns it, never repeated per page |
| ABI accuracy | Global Constraints pin the verified surface; `NativeAdPool.peek()` is called out as forbidden and grep-gated in Task 11 |
| Conservative cinterop-klib wording | Task 20 Step 3 quotes the Beta caveat; Step 6 greps for the forbidden "2.3.20 or newer" phrasing |

**Deliberate scope exclusions, each owned elsewhere:** the `/` landing page and its own capability matrix (Plan 5); `/api/` Dokka output (Plan 2); the `compose multiplatform monetization` keyword, which §7 assigns to "Landing / blog" (Plans 5 and 6); diagram authoring (Plan 4); screenshot capture (Plan 7).

**2. Placeholder scan**

No `TBD`, no "add error handling", no "similar to Task N". Every code step carries complete, runnable content. Three places deliberately instruct the executor to *derive* rather than invent, each with the exact command and an explicit rule for what to do when a fact cannot be established:

- Task 22 Step 1 — changelog dates and per-version changes come from git tags, GitHub releases and Maven Central metadata; unverifiable cells are **omitted**, never estimated.
- Task 25 Step 1 — the `llms*.txt` route list is read from `dist/`, not from memory.
- Task 27 Step 2 — screenshot ids are read from `screenshots.json`, not assumed.

**3. Type and name consistency**

Checked across all 27 tasks: `rememberAdManager()`, `AdMob.manager(context)`, `gatherConsentAndInitialize(config)` as a suspend **extension**, `AdManagerStatus.Ready`, `AdShowResult.Shown/NotReady/Failed`, `AdLoadState.Idle/Loading/Loaded/Failed`, `AdErrorCode.SDK_NOT_READY` / `CONSENT_REQUIRED`, `AdCachePolicy(maxSize, expirationPolicy, reloadAfterShow)`, `AdRetryPolicy(maxAttempts, initialDelay, maxDelay, backoffMultiplier)`, `AdTimeoutPolicy(loadTimeout, presentationHandOffTimeout)`, `AdExpirationPolicy(fullScreenTtl, appOpenTtl, nativeTtl)`, `BannerGeometry(widthDp)`, `AdSizePolicy.LargeAnchoredAdaptive/InlineAdaptive/Fixed/Fluid`, `BannerRefreshPolicy.AdServerManaged/SdkManaged/Manual`, `NativeMediaInfo(aspectRatio, hasVideoContent, durationSeconds)`, `PrivacyOptionsRequirementStatus.Required`, `AdTrackingAuthorization.NotApplicable`, `AdInitializationPhase.BeforeMobileAdsInitialize`. Script names are used identically wherever they appear: `scripts/wordcount.mjs` (Task 1, then every page task) and `scripts/audit-content.mjs` (Task 27). Diagram component names match Plan 4's exactly in the inventory table, the addendum, and every import line.

**4. Gaps this review exposed, and how each is resolved**

1. **The ATT keyword collided with iOS setup.** Spec §7 assigns `kmp ads att idfa` to the iOS setup page only, leaving `/privacy/app-tracking-transparency/` without one. Two pages sharing a keyword would cannibalise each other, so the ATT page was given the distinct long-tail `app tracking transparency kotlin multiplatform` and iOS setup keeps the spec-assigned term. The inventory table records the change.
2. **Plan 2 declares `title` and `description` fixed; this plan overrides most descriptions.** The overrides are intentional — several stub descriptions do not contain the page's primary keyword — and they are safe, because Plan 2's sidebar, sitemap and OG route key off the **file path**, not the description text. Only the generated OG card copy changes. `title` is never touched.
3. **Two capability matrices will exist**, one on `/start/what-is-admob-cmp/` (Task 2) and one on the Plan 5 landing page. They must not be identical text, or the two pages compete for `basic-ads alternative`. The landing page should carry a short six-format showcase; the long-form matrix belongs here. Flagged to Plan 5 rather than resolved unilaterally.
4. **`CONSENT.md` contains a genuine API error that a naive migration would have propagated.** It shows `testDeviceIds` on the convenience `AdConfig` constructor as though it registered UMP consent test devices; that parameter routes to `GlobalRequestConfiguration.testDeviceIds`, which registers **GMA** test devices. UMP consent test devices are `AdDebugOptions.consentTestDeviceIds`. Task 12 Step 6 documents the correct form using the primary constructor. This is the clearest evidence that "rewrite, not migrate" was the right instruction.
5. **`AGENTS.md` documents `pool.peek(token)`, which is not in the public ABI.** Recorded as a forbidden symbol in Global Constraints and grep-gated in Task 11 Step 8. Correcting `AGENTS.md` itself is a worthwhile follow-up but is out of scope here.
6. **`/reference/changelog/` targets 400–700 words, below the 800-word floor.** Deliberate: it is a reference index whose value is accuracy, and padding a changelog is worse than a short one. The two priority pages exceed 1,500 for the opposite reason, on the brief's explicit instruction.

---

## Execution

27 tasks. Tasks 2–25 are independent of one another once Task 1 lands — they touch disjoint files and can be executed in any order or in parallel. Task 26 requires all 24 canonical URLs to exist. Task 27 is the gate and runs last.

**REQUIRED SUB-SKILL:** use `superpowers:subagent-driven-development` (a fresh subagent per task, with review between tasks) or `superpowers:executing-plans` (batched inline execution with checkpoints).
