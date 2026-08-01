# AdMob CMP — End-to-End Public Visibility Design

**Date:** 2026-07-31
**Status:** Approved (design), pending spec review
**Scope:** Repository identity, SEO, developer documentation site, and off-site distribution for `dev.avinya.ads:admob-cmp`.

## 1. Problem

The library is production-ready and published — four versions on Maven Central, a frozen public ABI, six ad formats, UMP consent, mediation, and a Gradle plugin that solves a real consumer linking failure. Its public surface does not reflect any of that.

Verified 2026-07-31:

| Signal | Value |
|---|---|
| Repo | `Meet-Miyani/Admob-CMP`, created 2026-07-25 |
| Stars / forks / watchers | 0 / 0 / 0 |
| Topics | `[]` |
| `homepage` | `null` |
| `license` (GitHub-detected) | `null` — `LICENSE` sits in `admob-cmp/`, not repo root |
| Root `README.md` | Still the JetBrains Compose Multiplatform template |
| Releases | One: `v1.1.0`, 2026-07-30 |
| Wiki | Enabled, empty |
| Discussions | Disabled |
| Docs site | None |
| API reference | None — Dokka is not configured anywhere in the build |

Maven Central is healthy: `admob-cmp`, `admob-cmp-core`, and `admob-cmp-compose` all publish `1.0.0`, `1.0.1`, `1.0.2`, `1.1.0`, plus the `dev.avinya.ads.admob-cmp.gradle.plugin` marker at `1.1.0`.

So: the artifact works, and nobody can find it.

## 2. Goals

1. Make the library findable for the queries its actual audience types.
2. Ship a documentation site that beats the incumbent on the axis where the incumbent is weakest — explanatory, task-oriented, visual content.
3. Establish off-site presence in the channels KMP developers actually browse.
4. Publish the roadmap, including the gated Track 3 work, honestly.

### Non-goals

- Renaming the Maven coordinate. Four versions are published; breaking consumers buys nothing.
- Taking any breaking API change. Invariant 12 in `admob-cmp/CLAUDE.md` holds — the ABI is frozen.
- Supporting ad networks other than AdMob. Mediation already covers that need.
- Paid acquisition of any kind.

## 3. The naming problem, and the decision

**"AdMob CMP" already means something else.** In ad-tech, CMP is *Consent Management Platform* — the GDPR/TCF requirement Google mandated in January 2024. That SERP is owned by `support.google.com/admob` and `developers.google.com/admob`, and the intent behind it is "how do I show a consent form," not "give me a Kotlin library."

Worse, the queries the real audience types — `compose multiplatform admob`, `kotlin multiplatform admob`, `kmp admob library` — contain **no token** the repo name matches. GitHub tokenizes `admob-cmp` into `admob` + `cmp`; it can never match `compose`, `multiplatform`, or `kotlin`.

### Decisions

| Decision | Value | Rationale |
|---|---|---|
| GitHub repo name | `admob-compose-multiplatform` | Carries all three high-value tokens. GitHub 301s the old URL. Repo name is the strongest GitHub search ranking factor. |
| Maven coordinate | unchanged: `dev.avinya.ads:admob-cmp` | Published and consumed. Renaming breaks users for a marginal gain. |
| Brand name | "AdMob CMP" | Retained. The README reconciles brand, repo slug, and coordinate in one line. |
| Docs host | `ads.avinya.dev` | See §4. |
| Site shape | Marketing landing page + `/docs/` + `/api/` | Landing targets commercial intent; docs target informational long-tail; they do not cannibalize. |
| Docs framework | Astro + Starlight | `avinya.dev` is already Astro; tokens and toolchain carry over. |
| Docs location | Inside the SDK repo | API changes and doc updates ship in the same PR. |
| Visuals | Diagrams first, device screenshots close behind | Originally deferred on the belief that 5 of 6 formats were unreachable in the demo. That was based on a stale `CLAUDE.md` note — see §10, Plan 7. Screenshots need capture work only, no app development. |
| AI crawlers | Allowed on the docs host, with `llms.txt` | A large share of SDK integration now happens through a coding assistant. |

### Trademark posture

Nominative use of "AdMob" to describe compatibility is standard practice and low-risk — `admob-plus`, `react-native-admob`, and many others do exactly this. What Google's brand permissions specifically discourage is using their marks *in a domain name*, because a host whose entire label is `admob` reads as an official Google property. This is why `admob.avinya.dev` was rejected in favour of `ads.avinya.dev`, which carries no mark at all.

Every public surface (README, site footer, Maven POM description) carries:

> Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.

## 4. Docs hosting: why a subdomain

Textbook SEO prefers a subfolder (`avinya.dev/docs/`) over a subdomain, because subfolders consolidate domain authority. That argument is weak in this specific case:

- `avinya.dev` has ~19 indexed URLs and is young. There is very little authority to donate.
- Because of the canonical defect in §5, nothing has consolidated on `avinya.dev` at all yet.
- A docs site for a developer tool generates its *own* inbound links from blogs, Stack Overflow answers, and GitHub.

Against that, a subdomain lets the docs live **in the SDK repo**, so a public API change and its documentation ship in one PR and version together. For a library with a frozen ABI and a published compatibility matrix, that operational property is worth more than the marginal link equity.

**Decision: `ads.avinya.dev`**, a second Cloudflare Pages project with a custom domain, built from `docs-site/` in the SDK repo, cross-linked hard with `avinya.dev/open-source/`.

## 5. Blocking prerequisite: the `avinya.dev` canonical defect

Every page on the live studio site declares its canonical as the Cloudflare Pages preview host:

```
https://avinya.dev/              → <link rel="canonical" href="https://avinya.pages.dev/">
https://avinya.dev/about/        → <link rel="canonical" href="https://avinya.pages.dev/about/">
https://avinya.dev/open-source/  → <link rel="canonical" href="https://avinya.pages.dev/open-source/">
```

The same wrong host appears in every `og:url`, the JSON-LD `url`, the RSS feed, **all 19 sitemap entries**, and the `Sitemap:` directive in `robots.txt`. `https://avinya.pages.dev` returns **HTTP 200** — it is not redirected.

Google therefore sees two complete, crawlable copies of the studio site, and is explicitly instructed to prefer the throwaway host. Root cause is almost certainly `site:` in the studio site's `astro.config.mjs` still set to the Pages preview URL.

This must be fixed **before** the docs site starts linking to `avinya.dev`, or those links feed authority to a hostname that disavows itself. It is a one-line config change plus a redirect rule, but it lives in a **different repository** and is tracked as a distinct task in Plan 1.

## 6. Competitive analysis

`LexiLabs-App/basic-ads` — 105 stars, topics `admob, android, google, ios, java, kmm, kmp, kotlin, multiplatform`, docs at `ads.basic.lexilabs.app`.

| Dimension | admob-cmp | basic-ads |
|---|---|---|
| Ad formats | 6 — banner, interstitial, rewarded, rewarded interstitial, **app-open**, **native** | 4 — no native, no app-open |
| Native ad layout DSL | `adLayout {}` + pooling + media info | — |
| Consent | UMP in the init flow, ATT ordering, privacy options form | Basic consent request |
| Paid/revenue events, mediation | Yes | Not documented |
| Kotlin/Native test linking | Solved via a published Gradle plugin | Not addressed |
| Docs | None (yet) | **Generated Dokka API dump only** — no guides, no landing page, no diagrams, no screenshots |
| Topics | None (yet) | 9 |
| Stars | 0 | 105 |

The incumbent is ahead on social proof and metadata hygiene, and behind on everything that takes engineering effort. Their documentation is a generated API reference with no prose. **That gap is the entire strategy**: a guide-first, diagram-rich, task-oriented site wins on dwell time, internal linking, question-based headings, and freshness — and it is exactly what a Dokka dump structurally cannot provide.

Symmetrically, they have an API reference and we do not. Shipping Dokka alongside guides closes our only content deficit.

## 7. Keyword strategy

No SEO tool was connected for this work (Ahrefs and Similarweb MCP servers require authorization). Difficulty and opportunity below are qualitative, inferred from SERP composition, GitHub topic density, and Maven/GitHub signals — not tool-sourced volume.

| Keyword | Difficulty | Opportunity | Intent | Target page |
|---|---|---|---|---|
| `compose multiplatform admob` | Moderate | High | Commercial | Landing (H1) |
| `kotlin multiplatform admob` | Moderate | High | Commercial | Landing + repo name |
| `kmp admob library` | Easy | High | Commercial | Landing |
| `admob compose multiplatform banner` | Easy | High | Informational | Banner guide |
| `compose multiplatform native ads` | Easy | High (uncontested) | Informational | Native guide |
| `kmp app open ads` | Easy | High (uncontested) | Informational | App-open guide |
| `admob ios kotlin multiplatform undefined symbols GAD` | Easy | High (pure pain) | Troubleshooting | Troubleshooting |
| `admob consent kotlin multiplatform` | Moderate | Medium | Informational | Consent guide |
| `ump sdk compose multiplatform` | Moderate | Medium | Informational | Consent guide |
| `kotlin multiplatform rewarded ads` | Easy | Medium | Informational | Rewarded guide |
| `compose multiplatform monetization` | Moderate | Medium | Awareness | Landing / blog |
| `admob mediation kotlin multiplatform` | Easy | Medium | Informational | Mediation guide |
| `kmp ads att idfa` | Easy | Medium | Compliance | iOS setup |
| `basic-ads alternative` | Easy | Medium | Commercial | Comparison section |
| `kotlin multiplatform swiftpm` | Easy | Low–Medium | Informational | Roadmap |
| `admob cmp` | Hard | **Avoid** | Wrong intent | — |

Two pages are pure opportunity capture:

- **Troubleshooting** owns `admob ios kotlin multiplatform undefined symbols GAD` — a painful, uncontested query that the Gradle plugin genuinely fixes.
- **Native ads** has no competition at all, because the incumbent does not support the format.

### Content-depth finding

Current documentation totals ~6,750 words across eleven files. Several pages fall **below the 300-word thin-content threshold** for informational queries:

| File | Words |
|---|---|
| `APP_OPEN.md` | 229 |
| `CONSENT.md` | 240 |
| `MEDIATION.md` | 292 |
| `BANNER.md` | 361 |
| `INTERSTITIAL.md` | 374 |
| `NATIVE.md` | 483 |
| `README.md` | 527 |
| `PUBLISHING.md` | 560 |
| `ARCHITECTURE.md` | 604 |
| `SETUP.md` | 1,296 |
| `AGENTS.md` | 1,787 |

The content plan is therefore **rewrite and expand**, not migrate. Each guide page targets 800–1,500 words with worked examples, diagrams, and question-based H2s.

## 8. Information architecture

URLs carry no redundant `/docs/` segment — every path segment is a keyword. Starlight maps `docs-site/src/content/docs/<dir>/<slug>.mdx` to `/<dir>/<slug>/`.

```
/                                Landing page (commercial intent)

/start/what-is-admob-cmp/        Overview, when to use it, what it is not
/start/quickstart/               5-minute path to a rendering test ad
/start/installation/             Gradle, version catalog, the Gradle plugin
/start/android-setup/            Manifest, AD_ID, Play Data safety
/start/ios-setup/                SPM, Info.plist, ATT, JavaScriptCore, doctorIos

/formats/banner/                 Adaptive sizes, collapsible, refresh, geometry
/formats/interstitial/           Load/show, caching, retry
/formats/rewarded/               Rewarded + rewarded interstitial
/formats/app-open/               AppOpenAdCoordinator, cooldowns, blocking
/formats/native/                 Layout DSL, pooling, availableAds, media info

/privacy/consent/                UMP modes, privacy options form, canRequestAds
/privacy/app-tracking-transparency/   iOS ordering: consent → ATT → initialize
/privacy/play-data-safety/       Declaration guidance

/advanced/mediation/             Adapters, initialization hooks
/advanced/revenue-events/        AdValue, ResponseInfo, paid events
/advanced/caching-retry-timeouts/
/advanced/test-safety/           testMode vs strictTestMode

/reference/architecture/         Module map, threading, state machines
/reference/compatibility/        Kotlin/CMP/minSdk/iOS matrix
/reference/troubleshooting/      Symptom → cause → fix
/reference/changelog/

/project/roadmap/                Including Track 3 and its real gates
/project/contributing/
/project/ai-agents/              AGENTS.md, llms.txt

/api/                            Dokka HTML (generated, not authored)
```

Every page carries exactly one primary keyword, question-based H2s mirroring People-Also-Ask phrasing, and explicit cross-links.

### Two framing decisions

**Roadmap is published with its real gates.** Track 3 (JetBrains `swiftPMDependencies` import) is blocked on four unmet conditions, one of which — whether a *Maven-published* library propagates SwiftPM linkage to consumers — is an undocumented unknown. The roadmap page states this plainly: what is coming, what must land upstream first, and why a published SDK will not depend on an Alpha build-tool feature. Transparency is itself a differentiator, and the page captures `kotlin multiplatform swiftpm`.

**Comparison content is a neutral capability matrix**, not a named teardown. It captures `basic-ads alternative` intent without reading as an attack on another maintainer.

## 9. Verified technical facts

Confirmed live on 2026-07-31; plans should pin these.

| Component | Version / value |
|---|---|
| `astro` | 7.1.6 |
| `@astrojs/starlight` | 0.41.5 |
| `@astrojs/sitemap` | 3.7.3 |
| `starlight-llms-txt` | 0.11.0 |
| `rehype-mermaid` | 3.0.0 |
| Dokka Gradle plugin | 2.2.0 (`dokkaGenerate`, output `build/dokka/html`) |
| Maven Central badge | `https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp` → renders `v1.1.0` ✅ |
| Starlight scaffold | `npm create astro@latest -- --template starlight` |

Notes carried into the plans:

- **Mermaid is not built into Starlight.** Use `rehype-mermaid` for **build-time static SVG** (indexable, no client JS, no layout shift) rather than a client-side renderer. Signature diagrams are hand-authored Astro SVG components; sequence and flow diagrams use Mermaid.
- **Dokka must be applied to each documented subproject**, with aggregation in the root build.
- **GitHub does not redirect Actions references** after a rename, and the old repo name must never be reused or redirects break.
- `gradle.properties` currently hardcodes `POM_URL`, `POM_SCM_URL`, `POM_SCM_CONNECTION`, and `POM_SCM_DEV_CONNECTION` to the old repo URL. Published POMs for 1.0.0–1.1.0 keep the old URL permanently (redirects cover them); new releases must carry the new one.

## 10. Plan decomposition

Seven plans. Plan 1 is the highest impact-per-hour in the program and unblocks the rest.

### Plan 1 — Repository identity and GitHub SEO foundation

Rename `Admob-CMP` → `admob-compose-multiplatform`. Replace the JetBrains-template root README with a real product README merged from `admob-cmp/README.md`: keyword-bearing H1, badge row, format table, 30-second quickstart, compatibility matrix, trademark disclaimer. Set `homepage` to `https://ads.avinya.dev`. Move `LICENSE` (verified Apache License 2.0) to repo root so GitHub detects and renders it. Create a 1280×640 social preview. Enable Discussions; disable the empty Wiki. Update the four POM URL properties in `gradle.properties`. Audit `.github/workflows/` for any reference that will break on rename.

Topics — exactly these twelve, chosen to cover every token the repo name cannot:

```
kotlin-multiplatform  compose-multiplatform  admob        kmp
kotlin                ads                    monetization native-ads
android               ios                    ump          gdpr
```

**Also includes the cross-repo blocking fix from §5** — correct `site:` in the studio site's Astro config and 301 `avinya.pages.dev` → `avinya.dev`.

### Plan 2 — Documentation site foundation

Scaffold Starlight in `docs-site/`. Lift design tokens from `avinya.dev` (Space Grotesk / Inter, existing theme system). SEO plumbing: `@astrojs/sitemap`, per-page canonical and OG, JSON-LD (`SoftwareSourceCode`, `TechArticle`, `FAQPage`, `BreadcrumbList`), permissive `robots.txt` for the docs host, `starlight-llms-txt`, generated OG cards, Pagefind search. Wire Dokka 2.2.0 into the Gradle build and publish HTML to `/api/`. Cloudflare Pages project, `ads.avinya.dev` custom domain, GitHub Actions deploy job. Provision **Google Search Console** for the new host and submit the sitemap — without it the §12 metrics cannot be measured.

### Plan 3 — Documentation content architecture

Build the IA in §8. Rewrite and expand all existing docs to 800–1,500 words per guide with worked examples and question-based H2s. Author the net-new pages: What is AdMob CMP, Quickstart, Play Data safety, Revenue & paid events, Caching/retry/timeouts, Compatibility, Troubleshooting, Changelog, Roadmap, Contributing, Using with AI agents.

### Plan 4 — Visual system

Eight theme-aware diagrams:

1. Module map — one shared core, two platform adapters
2. **UMP → ATT → initialize sequence** — the highest-value correctness trap in the library
3. Full-screen ad lifecycle state machine, including the TTL'd FIFO cache and generation counter
4. Native pool lifecycle with `maxSize` accounting (available + in-use)
5. Banner geometry resolution — host-supplied width to adaptive size
6. Consent decision tree — `canRequestAds`, privacy-options requirement
7. Retry / backoff timeline
8. Platform support matrix

### Plan 5 — Landing page

Hero targeting `compose multiplatform admob`. Six-format showcase grid. Neutral capability matrix. Badge and compatibility strip. Real code sample. Roadmap teaser. CTA into Quickstart.

### Plan 6 — Launch and off-site distribution

Verify the klibs.io listing (auto-indexes within ~1 month given OSS + GitHub + `kotlin-tooling-metadata.json` in a Maven artifact — all satisfied). PR to `terrakok/kmp-awesome`. Confirm Maven Central POM metadata. Featured card and new slug on `avinya.dev/open-source/`. Launch content calendar: r/Kotlin, Kotlin Slack `#multiplatform`, dev.to, Medium, Compose Multiplatform community channels.

### Plan 7 — Demo app and device screenshots (deferred)

**Corrected 2026-07-31 — this plan is far cheaper than first scoped.** `admob-cmp/CLAUDE.md:107` claims the demo wires only a native ad (`feed_native`) and that banner/interstitial/rewarded/rewarded-interstitial/app-open are unreachable. That note is **stale**: it describes a `composeApp` demo that no longer exists.

The current demo (`shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt`) renders `AdDebugScreen(catalog = AdDebugCatalog.Test)`, and `AdDebugCatalog.Test` already defines placements for **all six formats** — banner, collapsible banner, native, interstitial, rewarded, rewarded interstitial, and app-open — surfaced through `FormatsTab`, `LayoutsTab`, and `DiagnosticsTab`.

So no demo-app development is required. This plan is: run the existing demo on an Android emulator and an iOS simulator, capture every format, produce light/dark and phone/tablet variants, optimise to AVIF/WebP with correct `alt` text, and retrofit into Plans 4 and 5. **Fix the stale `CLAUDE.md:107` note as part of this plan.**

Because the cost collapsed, this plan may be resequenced ahead of Plan 5 so the landing page ships with real screenshots rather than retrofitting them.

### Sequencing

```
Plan 1 ──┬─> Plan 2 ──┬─> Plan 3 ──┬─> Plan 5 ──> Plan 6
         │            │            │
         │            └─> Plan 4 ──┘
         │
         └─> (Plan 7, deferred; feeds back into 4 and 5)
```

## 11. Risks

| Risk | Mitigation |
|---|---|
| Rename breaks a workflow or external integration | Audit `.github/workflows/` before renaming; GitHub does not redirect Actions references |
| Old repo name reused later, breaking redirects | Documented prohibition in Plan 1 |
| Docs drift from the frozen ABI | Docs live in the SDK repo; API changes and doc updates ship in one PR |
| Diagrams contradict the code | Diagrams derive from `ARCHITECTURE.md` and the invariants in `CLAUDE.md`, and are reviewed against them |
| Publishing the Track 3 roadmap reads as a promise | Page states gates explicitly and commits to no date |
| Studio site canonical fix not landed before docs launch | Plan 1 blocks Plan 2 |
| No keyword tool connected | Targets are qualitative; revisit if an Ahrefs/Similarweb connector is authorized |

## 12. Success metrics

Reviewed 30 and 90 days after Plan 6 lands. Measured via Google Search Console (which Plan 2 must provision for `ads.avinya.dev`), the GitHub API, and manual SERP checks.

These are targets, not forecasts. Ranking outcomes for a new host with no backlink profile are genuinely unpredictable; the *controllable* metrics are the indexation and distribution rows, and those are the ones to hold the program to.

| Metric | Baseline (2026-07-31) | 30-day | 90-day |
|---|---|---|---|
| Docs pages indexed | 0 | 25+ | 25+ |
| klibs.io listing correct | unverified | verified | verified |
| `kmp-awesome` entry | none | merged | merged |
| Search Console impressions | 0 | >0 | growing |
| Ranking: `compose multiplatform native ads` (uncontested) | unranked | top 20 | top 3 |
| Ranking: `admob ios kotlin multiplatform undefined symbols` | unranked | top 20 | top 10 |
| Ranking: `compose multiplatform admob` (head term) | unranked | indexed | page 1–2 |
| Referring domains to `ads.avinya.dev` | 0 | 3+ | 10+ |
| GitHub stars | 0 | — | 50 (directional only) |

## 13. Open questions

None blocking. If an Ahrefs or Similarweb connector is later authorized, re-run §7 against real volume and difficulty data and revise the targets in §12.
