# Visibility Plan 5: Native Reference Landing Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Use
> superpowers:test-driven-development for behavior and regression gates.

**Status:** Content and structure complete and verified on 2026-08-02. The
**presentation** requirements below were overridden the same day — see the
notice immediately following. Task 6 is withdrawn.

> ## Presentation requirements are superseded — 2026-08-02
>
> The owner reviewed the built page, rejected its appearance, and commissioned a
> full redesign. The current design system is
> [`docs-site/DESIGN.md`](../../../docs-site/DESIGN.md).
>
> **Ignore the "Native Reference presentation" section below**, and the
> presentation lines of Task 7 and the acceptance checklist. They require the
> native font stack, a 32px H1, no landing-only type, flat surfaces, 6px radii,
> and no entrance animation or transform — every one of which the site now
> deliberately does the opposite of. `check-theme.mjs` and `landing.test.ts`
> were rewritten to enforce the new system, so implementing this section would
> now fail the gates rather than satisfy them.
>
> **Everything else in this plan still stands and still passes**: the canonical
> facts, the exact SEO/hero strings, the six-format contract, the capability
> matrix and its neutrality rules, the roadmap wording, the footer link group,
> the trademark statement, and the Plan 4 diagram integration.
>
> Structural changes made by the redesign:
>
> - `FormatList.astro` was **deleted**. The six formats are now rendered by
>   `landing/PlacementPlate.astro`, which draws a to-scale phone viewport with
>   the ad region as a solid block that moves per format. It replaced six empty
>   screenshot placeholders.
> - `Hero.astro` was added as a Starlight `Hero` component override, so the hero
>   composes the spec strip and the plate. The splash frontmatter, the single
>   H1, and the two hero actions are unchanged.
> - `landing/OriginStory.astro` was added — a short "Why this exists" section
>   between the capability matrix and compatibility.
> - `ProjectMetadata.astro` became the hero spec strip and dropped its Release
>   row; `CompatibilityList.astro` dropped its Maven coordinate row. Both were
>   duplicates of values stated elsewhere on the page.
> - The footer gained a two-link attribution line above the trademark. The seven
>   reference links are unchanged and still asserted as a group.

**Goal:** Replace the placeholder body of
`docs-site/src/content/docs/index.mdx` with a concise technical-reference entry
page at `https://ads.avinya.dev/`. It should answer what the SDK supports, who it
fits, how to install it, and where platform limits apply before directing the
reader to Quickstart.

**Architecture:** Facts and reusable copy live in a typed
`docs-site/src/data/landing.ts` module and are checked against repository build
files. The page remains a Starlight `template: splash` route so Starlight owns the
single H1, primary actions, responsive shell, theme switching, search, and
accessibility behavior. Small Astro components provide semantic structure only;
they inherit the shared Native Reference tokens and do not create a parallel
visual system.

**Visual authority:** The implemented
[`Documentation Native Reference Theme Design`](../specs/2026-08-01-docs-native-reference-theme-design.md)
is binding. This plan may add layout rules needed by the landing page, but it may
not change or bypass shared typography, colors, code themes, tables, component
shape, focus behavior, or motion.

**Tech stack:** Astro 7.1.6, Starlight 0.41.5, MDX, scoped Astro CSS, Vitest
4.1.10, and the existing Playwright theme/overflow gates. Add no dependency.

## Global constraints

### Content and routes

- The page remains `docs-site/src/content/docs/index.mdx`, rendered at `/` with
  `template: splash`.
- Preserve the `og:type: website` head override and exactly one H1.
- Preserve the two hero actions: Quickstart and GitHub.
- Internal links use trailing slashes.
- Do not change documentation routes, SDK code, public ABI, diagrams, raw
  screenshots, generated API files, or GitHub workflows.
- Use the exact trademark statement:

  > Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
  > trademarks of Google LLC.

### Canonical facts

The data module may expose these values, but tests must verify them against the
repository rather than trusting duplicated literals:

- Maven coordinate `dev.avinya.ads:admob-cmp`, version `1.1.0`.
- Gradle plugin `dev.avinya.ads.admob-cmp`, version `1.1.0`.
- Kotlin `2.3.20`; Compose Multiplatform `1.11.1`.
- Android `minSdk 26`; iOS deployment target `15.0`.
- Apache License 2.0.
- Six formats in this order: banner, interstitial, rewarded, rewarded
  interstitial, app-open, native.
- Native video events remain iOS-only.
- On iOS the production ordering remains UMP consent, then ATT, then
  `initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)`.

### SEO and typed data contract

The frontmatter and hero strings are exact:

- `title: Compose Multiplatform AdMob SDK` (31 characters).
- `description: 'Open-source Compose Multiplatform AdMob SDK: six ad formats on Android and iOS from one Kotlin API, with UMP consent built into the init flow.'`
- `hero.title: Compose Multiplatform AdMob SDK for Android and iOS`.
- Hero tagline: `Six AdMob formats on Android and iOS behind one Kotlin API — suspend functions, StateFlow, and UMP consent wired into initialization.`
- Primary action: `Start the 5-minute quickstart` -> `/start/quickstart/`.
- Secondary action: `View on GitHub` -> the canonical repository URL.

Tests must assert the keyword-bearing title/H1, rendered title length at most 60
characters, description length at most 160, exactly one H1, `og:type=website`,
the generated OG URL, and root `SoftwareSourceCode` JSON-LD.

`landing.ts` exports this stable interface:

```ts
// As shipped on 2026-08-02. `screenshot` and `crop` were removed with
// FormatList.astro; `dimension` is the format's on-screen size, shown beside
// the API signature. The plate's geometry lives in landing.css keyed by slug,
// because geometry is presentation.
export interface LandingFormat {
  slug: string;
  name: string;
  href: string;
  blurb: string;
  api: string;
  dimension: string;
}
```

The six records are exact at the identity/link/API level; prose may be tightened
only if every technical claim remains traceable to `admob-cmp/AGENTS.md`:

| slug | name | href | API | crop |
|---|---|---|---|---|
| `banner` | Banner | `/formats/banner/` | `BannerAdView(placement)` | `bottom` |
| `interstitial` | Interstitial | `/formats/interstitial/` | `adManager.interstitial(placement)` | `center` |
| `rewarded` | Rewarded | `/formats/rewarded/` | `adManager.rewarded(placement)` | `center` |
| `rewarded-interstitial` | Rewarded interstitial | `/formats/rewarded/#how-is-a-rewarded-interstitial-different` | `adManager.rewardedInterstitial(placement)` | `center` |
| `app-open` | App-open | `/formats/app-open/` | `AppOpenAdCoordinator(manager, controller, config)` | `top` |
| `native` | Native | `/formats/native/` | `NativeAdView(placement, itemKey, layout)` | `center` |

Each format accepts at most one representative `screenshot`. Plan 7's fourteen
phone/dark captures are availability inventory: the six format records select at
most six of them. `banner-collapsible` remains available for the Banner guide or
may be described under the Banner record without creating a seventh landing
card. Real screenshot integration changes only `screenshot` and `crop` data.

The neutral comparison contract is also exact:

```ts
export interface CapabilityRow {
  capability: string;
  admobCmp: string;
  basicAds: string;
}

export const capabilityVerifiedOn = '31 July 2026';
export const basicAdsRepo = 'https://github.com/LexiLabs-App/basic-ads';
```

| Capability | AdMob CMP | basic-ads |
|---|---|---|
| Banner ads | Yes | Yes |
| Interstitial ads | Yes | Yes |
| Rewarded ads | Yes | Yes |
| Rewarded interstitial ads | Yes | Yes |
| App-open ads | Yes | Not offered |
| Native ads | Yes | Not offered |
| Native ad layout DSL and pooling | `adLayout {}` plus `NativeAdPool` max-size accounting | Not applicable |
| UMP consent inside initialization | `gatherConsentAndInitialize`, three consent strategies, privacy-options form | Consent request |
| iOS ATT ordering | tracking authorization between consent and initialize | Not documented |
| Paid and revenue events | `AdEvent.Paid` with `AdValue` and `ResponseInfo` | Not documented |
| Mediation adapter hooks | `AdInitializationHook` at three initialization points | Not documented |
| Kotlin/Native test linking | Published `dev.avinya.ads.admob-cmp` Gradle plugin | Not addressed |
| Generated API reference | Yes | Yes |
| Maven Central publication | Yes | Yes |

The footnote must define “Not documented” as a statement about public
documentation, date the comparison, credit basic-ads as the older/larger project
and earlier API-reference publisher, and invite corrections. Tests reject
evaluative or attacking language.

The roadmap contract contains exactly two records:

| Title | Status | Required gate language |
|---|---|---|
| Swift Package Manager dependency import | Gated on four unmet upstream conditions | Name `swiftPMDependencies`, Maven-consumer propagation as an open unknown, and the refusal to depend on an Alpha build-tool feature. No date. |
| Native video events on Android | Blocked on the upstream SDK | Name `GADVideoControllerDelegate`, the five iOS events, and the absence of an equivalent Android callback surface. No date. |

The compact footer link group contains Quickstart, Installation, Compatibility,
Roadmap, API reference, GitHub, and Apache-2.0 license links plus the exact
trademark statement. Do not restore the deleted five-column marketing footer.

### Native Reference presentation — SUPERSEDED 2026-08-02, do not implement

> Kept for the record only. See the notice at the top of this plan and
> [`docs-site/DESIGN.md`](../../../docs-site/DESIGN.md). Implementing the rules
> below now fails `check-theme.mjs` and `landing.test.ts`.

- Use the shared native UI stack for every non-code element. Use
  `--admob-font-mono` only for coordinates, API identifiers, and code.
- Inherit the shared 16px/1.6 prose, 32px desktop/28px mobile H1, 24px H2,
  19px H3, and 45rem reading measure. Do not create larger landing-only type.
- Section labels, if needed, are sentence-case UI text with normal tracking.
  Do not use uppercase monospace eyebrows, decorative orange squares, display
  tracking, or balanced marketing headlines.
- Keep backgrounds flat. Groups may use a one-pixel neutral border and 6px
  radius. Do not use shadows, gradients, raised panels, glass effects, or
  spatial hover transforms.
- Brand orange is functional: prose links use `--admob-accent-text`; focus and
  selection use `--admob-accent`. Do not create orange-filled badge segments,
  decorative bullets, or large accent surfaces.
- Prefer semantic lists, definition lists, and row separators over repeated
  cards. Screenshot items may use flat bordered frames because the image needs a
  boundary.
- Use the existing paired Expressive Code theme. Never add landing-specific code
  colors or a permanent-dark code surface.
- Use the canonical focusable `.table-scroll.table-scroll--wide` structure for
  the capability matrix and inherit centralized table styling.
- Interaction transitions are limited to 120–150ms color, background-color,
  border-color, and opacity. No entrance animation, lift, translate, or scale.
- Preserve `prefers-reduced-motion`; do not add a page-local animation system.

### Screenshots and diagrams

- Plan 4 owns diagram components. Import `InitSequence.astro` and
  `PlatformMatrix.astro`; do not duplicate their SVG or table markup.
- Plan 7 owns screenshot capture, filenames, manifest metadata, alt text, and
  platform/theme availability. Consume screenshots only through
  `docs-site/src/components/Screenshot.astro`.
- Dark debug-harness screenshots are faithful source content, not site chrome.
  They remain valid in both site themes and must not be fabricated as light
  captures.
- Screenshot frames use the consumer's neutral border, 6px radius, no shadow,
  and no hover motion.
- The page must render before Plan 7 lands. A missing Screenshot component or
  asset produces a restrained neutral placeholder with the same aspect ratio;
  integrating real assets is a data-only change.
- Tasks 1–4 may proceed against the current documentation foundation. Task 5 is
  blocked until Plan 4 replaces all eight diagram stubs and its verification
  passes; do not hard-import placeholder diagrams into the final landing page.

## File map

| File | Responsibility |
|---|---|
| `docs-site/src/data/landing.ts` | Typed facts, format records, compatibility data, capability rows, links, and legal copy. |
| `docs-site/src/content/docs/index.mdx` | Frontmatter, hero, section order, Quickstart example, and component composition. |
| `docs-site/src/styles/landing.css` | Layout-only spacing, grids, and responsive composition; no color/font/radius tokens. |
| `docs-site/src/components/Hero.astro` | **Added 2026-08-02.** Starlight `Hero` override: renders the H1/tagline/two actions from splash frontmatter and composes the spec strip and the plate. |
| `docs-site/src/components/landing/ProjectMetadata.astro` | Hero spec strip: licence, platforms, Kotlin, and the copyable Maven coordinate. |
| `docs-site/src/components/landing/PlacementPlate.astro` | **Replaced `FormatList.astro` 2026-08-02.** Six-format list plus the to-scale phone viewport whose ad region moves per format. CSS-only, no script. |
| `docs-site/src/components/landing/OriginStory.astro` | **Added 2026-08-02.** Short "Why this exists" section. |
| `docs-site/src/components/landing/CapabilityMatrix.astro` | Neutral, dated semantic comparison table using the canonical table wrapper. |
| `docs-site/src/components/landing/CompatibilityList.astro` | Compact compatibility definition list plus binary-compatibility caveat. |
| `docs-site/src/components/landing/RoadmapSummary.astro` | Two honest gated roadmap items and a link to the complete roadmap. |
| `docs-site/src/components/landing/LandingFooter.astro` | Compact Quickstart/GitHub links, license, and trademark statement. |
| `docs-site/test/landing.test.ts` | Fact consistency, link, structure, styling-boundary, and screenshot-contract tests. |
| `docs-site/scripts/check-theme.mjs` | Adds rendered landing assertions to the existing isolated-server visual gate. |

## Page order

1. Starlight hero: product name, one-sentence fit, Quickstart, GitHub.
2. Quiet project metadata row/definition list.
3. Six supported formats.
4. Minimal installation and initialization example.
5. Neutral capability matrix.
6. Consent/ATT sequence and compatibility limits.
7. Short roadmap summary.
8. Compact reference links and legal footer.

The page should answer the primary developer questions without becoming a second
documentation navigation system. Avoid duplicate link columns, campaign-style
CTA sections, testimonial patterns, statistics, or ornamental product copy.

## Task 1: Establish regression tests and typed facts

**Files:**

- Create `docs-site/src/data/landing.ts`.
- Create `docs-site/test/landing.test.ts`.

**Requirements:**

- Read `VERSION_NAME` from both Gradle property files in the test and require
  lockstep agreement with `landing.ts`.
- Read Kotlin and Compose Multiplatform versions from
  `gradle/libs.versions.toml` and Android/iOS minimums from their authoritative
  build settings.
- Test the six-format order, unique slugs, trailing-slash internal links, legal
  statement, and current repository URL.
- Scan `src/components/landing/**` and `src/styles/landing.css`; reject literal
  hex/rgb/hsl/oklch colors, `box-shadow` other than `none`, transforms,
  animations, gradients, `text-transform: uppercase`, and page-local
  `font-family` declarations outside code/coordinate selectors.
- Test that landing components do not import PNG files directly.
- Run `cd docs-site && npm test`; confirm the new tests fail because the data and
  components do not exist yet.

## Task 2: Build the page shell and project metadata

**Files:**

- Modify `docs-site/src/content/docs/index.mdx`.
- Create `docs-site/src/styles/landing.css`.
- Create `docs-site/src/components/landing/ProjectMetadata.astro`.

**Requirements:**

- Replace the placeholder frontmatter with the exact keyword-bearing SEO and hero
  contract above, keep the splash template, and retain the `og:type` override.
- Remove placeholder prose.
- Render release, license, Kotlin, platforms, and Maven coordinate as a quiet
  inline metadata row that wraps into a definition list on narrow screens.
- Do not use shields-style local pills or accent-filled value segments. A live
  Maven Central image is optional; if retained, it must not become the visual
  anchor of the page or cause layout shift.
- `landing.css` may define only layout/spacing variables and responsive grids.
  All visual values come from `tokens.css`.
- Verify one H1, heading order, keyboard focus, and 390px containment.

## Task 3: Add the supported-format overview

**Files:**

- Create `docs-site/src/components/landing/FormatList.astro`.
- Modify `docs-site/src/data/landing.ts` and `index.mdx`.
- Modify `docs-site/test/landing.test.ts`.

**Requirements:**

- Render all six formats in canonical order with name, one-sentence use case,
  relevant API identifier, and guide link.
- Prefer a two-column list with row separators. Use card framing only where a
  screenshot is present.
- Resolve `Screenshot.astro` without making Plan 7 a build dependency. Use a
  guarded `import.meta.glob('../Screenshot.astro')` lookup rather than a static
  import; select its `default` export only when present and otherwise render the
  same-ratio neutral fallback. The placeholder and real screenshot occupy the
  same aspect-ratio box.
- The image boundary is flat, neutral, 6px, and static.
- Test format count, order, links, the guarded `import.meta.glob` indirection,
  and both absent-component and present-component rendering paths.

## Task 4: Add Quickstart and the capability matrix

**Files:**

- Modify `docs-site/src/content/docs/index.mdx`.
- Create `docs-site/src/components/landing/CapabilityMatrix.astro`.
- Modify `docs-site/src/data/landing.ts` and tests.

**Requirements:**

- Provide one installation snippet and one compact initialization snippet. Link
  to Quickstart for the complete sequence rather than duplicating the guide.
- Ensure the iOS example preserves UMP -> ATT -> initialize ordering.
- Let Expressive Code provide syntax and theme behavior without local overrides.
- Keep the comparison factual and dated. State capabilities without criticizing
  another maintainer or using marketing adjectives.
- Wrap the table in `.table-scroll.table-scroll--wide` with `tabindex="0"`,
  `role="region"`, and an accessible label. Do not implement component-local
  table colors, shadows, hover rows, or zebra striping.

## Task 5: Add compatibility, consent, roadmap, and footer

**Files:**

- Create `CompatibilityList.astro`, `RoadmapSummary.astro`, and
  `LandingFooter.astro`.
- Modify `index.mdx`, `landing.ts`, and tests.

**Requirements:**

- Import the Plan 4 consent sequence and platform matrix by their fixed names.
- Present compatibility as a compact definition list with the Kotlin klib
  binary-compatibility caveat.
- Present roadmap status as sentence-case semibold UI text with neutral borders;
  no uppercase mono status chips.
- End with a small Quickstart/GitHub link group and legal text separated by one
  neutral rule. Do not create a campaign banner or duplicate the site footer's
  navigation hierarchy.
- Test diagram imports, compatibility values, roadmap links, license, and exact
  trademark text.

## Task 6: Integrate screenshots when Plan 7 is available — WITHDRAWN 2026-08-02

**Decision: the landing page does not show device screenshots. Plan 7's
captures go to the per-format guide pages (Plan 3) only.**

Plan 7 §"Handoff" anticipated this case and states that where Plan 5 disagrees,
Plan 5 changes rather than the assets being renamed. This is that change; Plan
7's naming, manifest, alt text and `Screenshot.astro` component are untouched
and its Plan 3 integration is unaffected.

Why the landing page was the wrong consumer:

1. **The plate already answers the question better.** A reader deciding whether
   to adopt the SDK wants to know where each format lands on screen and what it
   costs them in layout. The plate shows that to scale for all six formats in
   one element; six static captures show six screenshots of a debug catalog.
2. **The captures are dark-only, and Plan 7 forbids fabricating light
   variants** — correctly, they are faithful source content. Six dark phone
   images on the light theme would be exactly the foreign-object inconsistency
   the redesign was commissioned to remove.
3. **They are captures of the debug harness, not a shipping app.** That is
   honest, useful evidence inside a format guide ("this is what a collapsible
   banner does"), and weak evidence on the page that has to establish that the
   library is production-grade.
4. **LCP.** This is the SEO entry point. Plan 7 itself flags the cost and tells
   consumers to keep landing screenshots below the hero and lazy-load them; not
   shipping them at all is strictly better for the page's primary job.

The machinery this task depended on — `FormatList.astro`, the guarded
`import.meta.glob('../Screenshot.astro')` lookup, and the `screenshot` / `crop`
fields on `LandingFormat` — was removed on 2026-08-02 along with the six empty
placeholder frames. Reinstating it means reversing that decision deliberately,
not restoring a missing piece.

## Task 7: Extend rendered verification and finish locally

**Files:**

- Modify `docs-site/scripts/check-theme.mjs`.
- Modify `docs-site/test/landing.test.ts` only if the rendered contract exposes a
  missing unit-level guard.

**Rendered assertions at 1440px and 390px, both themes.** Updated 2026-08-02 —
the first and fourth bullets and the last clause were inverted by the redesign;
what `check-theme.mjs` asserts today is:

- Archivo family; landing H1 56px desktop / 36px mobile, H2 30px / 26px, H3
  19px; docs H1 36px, H2 24px, H3 19px; body 16px on a 1.65 rhythm.
- No element exceeds the viewport and no page-level horizontal overflow exists.
- Light code uses the light syntax surface; dark code uses the dark surface.
- Every landing element's radii come from the `--admob-radius*` scale, and every
  shadow is either `none` or a shadow token — resolved from the live tokens
  rather than compared against fixed numbers.
- Capability table is focusable, labelled, row-structured, and contained.
- Focus is visible on hero actions, metadata links, format links, the table
  region, and both footer link groups.
- Theme switching remains functional.
- Motion is permitted; under `prefers-reduced-motion: reduce` every landing
  element must report `animation-name: none`.

**Final verification:**

```bash
cd docs-site
npm test
npm run build
npm run verify
npm run check:theme
npm run check:overflow
cd ..
git diff --check
./scripts/release-readiness.sh
```

Require every pre-existing test plus the new landing tests with zero failures, a
non-decreasing authored/generated Astro page count, both isolated browser gates,
and `READINESS: PASS`. Do not add or modify a GitHub verification job. Do not
commit, push, open a PR, tag, or release until the owner separately approves
integration.

## Acceptance checklist

Rewritten 2026-08-02: the third, fourth and sixth items described the
superseded presentation contract. This repository does not tick checkboxes —
verify each against the repo.

- [ ] The page answers what the SDK supports, who it fits, how to install it,
  and where platform limits apply, then sends the reader to Quickstart. It is
  not a dashboard or a campaign page.
- [ ] Verified project facts cannot silently drift from build configuration.
- [ ] Typography, code, tables, controls, radii, colour, elevation, and motion
  all resolve through `--admob-*` tokens — see `docs-site/DESIGN.md`.
- [ ] No literal colour, hard-coded font stack, or off-scale radius exists
  outside `tokens.css`, and nothing animates without a
  `prefers-reduced-motion` answer in the same file.
- [ ] Six formats, consent ordering, platform limitations, compatibility, and
  legal wording are accurate.
- [ ] Plan 4 diagrams are consumed through their fixed-name components.
  Plan 7 screenshots are **not** consumed here — see the withdrawn Task 6.
- [ ] Desktop/mobile, light/dark, keyboard-focus, reduced-motion, and
  containment gates pass locally.
