# Visibility Plan 5: Landing Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder body of `docs-site/src/content/docs/index.mdx` with a complete, keyword-targeted marketing landing page at `https://ads.avinya.dev/` that converts `compose multiplatform admob` search intent into a Quickstart click.

**Architecture:** All landing copy and facts live in one typed data module (`src/data/landing.ts`) that vitest checks against the repository's own build files, so the page can never claim a version, Kotlin level or minSdk the repo does not actually publish. Presentation lives in eight small `.astro` components under `src/components/landing/`, each styled exclusively against the nineteen `--admob-*` custom properties Plan 2 installs. The page is a Starlight `template: splash` page: Starlight's own `hero` frontmatter renders the single `<h1>` and the two CTAs (which guarantees exactly one H1 and correct heading order), and every section below it is a custom component wrapped in a shared `Section.astro` shell.

**Tech Stack:** Astro 7.1.6, `@astrojs/starlight` 0.41.5, MDX, plain `.astro` components with scoped `<style>`, vitest 4.1.10 (already a devDependency from Plan 2), Playwright via Plan 2's `npm run check:overflow`.

## Global Constraints

- **Do not upgrade anything.** `astro` 7.1.6 and `@astrojs/starlight` 0.41.5 are pinned by Plan 2. This plan adds **no** new npm dependency, runtime or dev.
- **Landing page path is fixed:** `docs-site/src/content/docs/index.mdx`, rendered at `/`, `template: splash`. Custom components go in `docs-site/src/components/landing/*.astro` and nowhere else.
- **Never hardcode a colour, font or radius.** Style only against the nineteen tokens Plan 2 defines in `docs-site/src/styles/tokens.css`:
  `--admob-ink` `--admob-paper` `--admob-surface` `--admob-slate` `--admob-hair` `--admob-code` `--admob-accent` `--admob-accent-soft` `--admob-accent-contrast` `--admob-font-display` `--admob-font-body` `--admob-font-mono` `--admob-tracking-tight` `--admob-tracking-tighter` `--admob-radius` `--admob-radius-lg` `--admob-border` `--admob-shadow` `--admob-content-max`.
  Note `--admob-border` is a **border shorthand** (`1px solid var(--admob-hair)`), used as `border: var(--admob-border)`. Use `--admob-hair` when you need the colour alone. Task 1 adds a vitest guard that fails the build on any hex, `rgb()`, `hsl()`, `oklch()` or bare colour keyword inside `src/components/landing/**` or `src/styles/landing.css`.
- **Do not introduce a parallel token set.** `src/styles/landing.css` may define **layout-only** variables (spacing, grid gaps). It must not define a single colour or font.
- **Frontmatter contract from Plan 2 must survive:** keep `template: splash` and the `head` entry that sets `og:type: website`. Plan 2's `Head.astro` switches to `SoftwareSourceCode` JSON-LD purely on the `/` pathname and its verifier asserts it — do not add a second JSON-LD block.
- **Canonical facts** (do not restate them differently anywhere on the page):
  - Host `https://ads.avinya.dev` · Repo `https://github.com/Meet-Miyani/admob-compose-multiplatform`
  - Maven coordinate `dev.avinya.ads:admob-cmp` version `1.1.0` · Gradle plugin id `dev.avinya.ads.admob-cmp` version `1.1.0`
  - Kotlin `2.3.20` · Compose Multiplatform `1.11.1` · Android `minSdk 26` · iOS deployment target `15.0` · Licence Apache 2.0
  - Maven Central badge URL `https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp` (renders `v1.1.0`)
- **Trademark line, verbatim, in the page footer:**
  > Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
- **Every claim must be verifiable** against `admob-cmp/README.md` or `admob-cmp/AGENTS.md`. Do not overclaim. In particular: the SDK is consumable from KMP/Gradle projects only, Compose Multiplatform is required only for the composable surface, and native video events are iOS-only. These caveats appear on the page, not just in the docs.
- **ATT ordering invariant** (`admob-cmp/AGENTS.md`, "iOS: App Tracking Transparency"): UMP consent → ATT → initialize. Requesting ads before ATT resolves permanently forfeits the IDFA for those requests. The landing code sample must show this order and must not show `gatherConsentAndInitialize` as the iOS-complete path.
- **Comparison content is a neutral capability matrix, not a teardown** (spec §8). State capabilities and nothing else. No adjectives about another project's quality, no "unlike X", no implied criticism of a maintainer. Include a dated verification note, name the other project once with a link, and credit where it leads.
- **Screenshots are consumed only through `Screenshot.astro`** (`docs-site/src/components/Screenshot.astro`, produced by Plan 7 Task 15). Never reference a path under `src/assets/screenshots/` directly from a landing component. The format grid must render correctly with inline placeholder art before Plan 7 runs, and swap to real screenshots by changing **data only** — no markup restructuring.
- **Plan 7's harness is theme-fixed dark** (`screenshots.json` `$contract.themeNote`): `AdDebugScreen` declares a fixed dark palette and does not inherit the host theme, so **no light variant of an ad-format capture can exist**. The landing grid therefore consumes `-dark.png` captures only, in both site themes, framed by a card whose own surface follows the theme. Do not design a light/dark screenshot swap.
- **Ordering:** this plan runs after Plan 2 (tokens, `astro.config.mjs`, `Head.astro`, vitest, `npm run verify`, `npm run check:overflow`), after Plan 3 (the `/start/`, `/formats/`, `/privacy/`, `/reference/`, `/project/` pages this page links to), and after Plan 4 — Tasks 6 and 7 import `InitSequence.astro` and `PlatformMatrix.astro` from `docs-site/src/components/diagrams/` by name, and a bare import of a component Plan 4 has not created fails the build.
- **Plan 7 is not a dependency.** Task 3 ships inline placeholder art, resolves `Screenshot.astro` through `import.meta.glob` so a missing file degrades instead of failing the build, and the retrofit in Task 9, Step 5 is a data-only change to `src/data/landing.ts`.
- **Every internal link must end in a trailing slash** (`/start/quickstart/`, not `/start/quickstart`). Starlight emits directory-style URLs; a missing slash costs a redirect.
- Run every command from `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site` unless a step says otherwise.

## File Structure

| Path | Responsibility | Task |
|---|---|---|
| `docs-site/src/data/landing.ts` | Single source of truth for every fact and every string on the page: library constants, six format records, capability rows, differentiators, compatibility rows, roadmap items, footer copy. | 1, 3, 5, 6, 7, 8 |
| `docs-site/src/styles/landing.css` | Layout-only custom properties and the `body[data-has-hero]` content-width override. No colours. | 1 |
| `docs-site/src/components/landing/Section.astro` | Shared section shell: anchor id, eyebrow, `<h2>`, lead paragraph, optional surface panel. | 1 |
| `docs-site/src/content/docs/index.mdx` | The page. Frontmatter (title, description, `template: splash`, `hero`) plus the section composition. | 1–8 |
| `docs-site/test/landing.test.ts` | Vitest guards: version/Kotlin/CMP/minSdk agreement with the repo build files, no hardcoded colours, copy-length limits, link hygiene, screenshot-manifest agreement. | 1, 3, 5, 7 |
| `docs-site/src/components/landing/BadgeStrip.astro` | Maven Central badge, licence, Kotlin and platform pills, and the copyable coordinate, directly under the hero. | 2 |
| `docs-site/src/components/landing/FormatGrid.astro` | Responsive grid of the six format cards. | 3 |
| `docs-site/src/components/landing/FormatCard.astro` | One card. The **only** call site of `Screenshot.astro` in this plan. | 3, 9 |
| `docs-site/src/components/landing/FormatArt.astro` | Inline SVG wireframe placeholder, one variant per format, shown until Plan 7 lands. | 3 |
| `docs-site/src/components/landing/CapabilityMatrix.astro` | Neutral, dated capability table with a horizontal-scroll container. | 5 |
| `docs-site/src/components/landing/DifferentiatorGrid.astro` | "Why this exists" — five cards. | 6 |
| `docs-site/src/components/landing/CompatibilityStrip.astro` | Version strip plus the klib binary-compatibility caveat. | 7 |
| `docs-site/src/components/landing/RoadmapTeaser.astro` | Two gated roadmap items and the link to `/project/roadmap/`. | 8 |
| `docs-site/src/components/landing/FinalCta.astro` | Closing CTA, link columns, licence line and the trademark line. | 8 |

**Section order on the page** (each is one task):

1. Hero — Starlight `hero` frontmatter (H1, value proposition, two CTAs) + `BadgeStrip` — Tasks 1–2
2. Six-format showcase grid — Task 3
3. Quickstart code sample — Task 4
4. Neutral capability matrix — Task 5
5. Why this exists / differentiators — Task 6
6. Compatibility strip — Task 7
7. Roadmap teaser — Task 8
8. Final CTA + footer with the trademark line — Task 8

Then: responsive and theming verification (Task 9), Lighthouse verification (Task 10).

---

### Task 1: Page skeleton, fact module, section shell and page SEO

Establishes everything the other seven sections sit on: the typed fact module and the tests that keep it honest, the layout-only stylesheet, the shared `Section` shell, and the final frontmatter (title ≤60 chars once Starlight appends the site title, description ≤160, `hero` H1 and CTAs).

**Files:**
- Create: `docs-site/test/landing.test.ts`
- Create: `docs-site/src/data/landing.ts`
- Create: `docs-site/src/styles/landing.css`
- Create: `docs-site/src/components/landing/Section.astro`
- Modify: `docs-site/src/content/docs/index.mdx` (replaces the Plan 2 placeholder body; keeps `template: splash` and the `og:type` head entry)

**Interfaces:**
- Consumes from Plan 2: `docs-site/src/styles/tokens.css` (the nineteen `--admob-*` tokens), `docs-site/package.json` scripts `test`, `build`, `preview`, `verify`, `check:overflow`, and `Head.astro`'s `/`-only `SoftwareSourceCode` JSON-LD.
- Produces, used by every later task:
  - `docs-site/src/data/landing.ts` exporting `library` (a `const` object with `brand`, `version`, `groupId`, `artifactId`, `gradlePluginId`, `kotlin`, `composeMultiplatform`, `androidMinSdk`, `iosDeploymentTarget`, `licence`, `licenceUrl`, `site`, `repo`, `mavenCentral`, `mavenBadge`, `trademark`, all `string`) and `coordinate: string` = `` `${groupId}:${artifactId}:${version}` ``.
  - `Section.astro` with `Props { id: string; eyebrow?: string; heading: string; lead?: string; panel?: boolean }`. Renders `<section class="landing-section" id={id} aria-labelledby={id + '-heading'}>` containing an `<h2 id={id + '-heading'}>`, then `<slot />`. `panel` draws a `--admob-surface` card around the content.
  - `docs-site/src/styles/landing.css` defining `--landing-section-gap`, `--landing-section-gap-sm`, `--landing-gutter`, `--landing-gutter-sm`, `--landing-head-max`, `--landing-card-gap`, and widening `--sl-content-width` to `--admob-content-max` on `body[data-has-hero]`.
  - `docs-site/test/landing.test.ts` — the gate every later task re-runs with `npm test`.

- [ ] **Step 1: Write the failing tests**

Create `docs-site/test/landing.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { library, coordinate } from '../src/data/landing';

const here = dirname(fileURLToPath(import.meta.url));
const docsSite = join(here, '..');
const repoRoot = join(docsSite, '..');

const SITE_TITLE = 'AdMob CMP'; // astro.config.mjs SITE_TITLE, set by Plan 2.

function gradleProperty(key: string): string {
  const text = readFileSync(join(repoRoot, 'gradle.properties'), 'utf8');
  const match = new RegExp(`^\\s*${key}\\s*=\\s*(.+)$`, 'm').exec(text);
  if (!match) throw new Error(`gradle.properties has no ${key}`);
  return match[1].trim();
}

function catalogVersion(key: string): string {
  const text = readFileSync(join(repoRoot, 'gradle', 'libs.versions.toml'), 'utf8');
  const match = new RegExp(`^\\s*${key.replace(/-/g, '\\-')}\\s*=\\s*"([^"]+)"`, 'm').exec(text);
  if (!match) throw new Error(`libs.versions.toml has no ${key}`);
  return match[1];
}

function landingSources(): Array<{ path: string; text: string }> {
  const out: Array<{ path: string; text: string }> = [];
  const cssPath = join(docsSite, 'src', 'styles', 'landing.css');
  if (existsSync(cssPath)) out.push({ path: cssPath, text: readFileSync(cssPath, 'utf8') });
  const dir = join(docsSite, 'src', 'components', 'landing');
  if (existsSync(dir)) {
    for (const name of readdirSync(dir).filter((f) => f.endsWith('.astro'))) {
      const path = join(dir, name);
      out.push({ path, text: readFileSync(path, 'utf8') });
    }
  }
  return out;
}

/** Only the CSS parts of a source file — `<style>` blocks, or the whole file for .css. */
function styleBlocks(file: { path: string; text: string }): string {
  if (file.path.endsWith('.css')) return file.text;
  return [...file.text.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((m) => m[1]).join('\n');
}

const indexMdx = readFileSync(join(docsSite, 'src', 'content', 'docs', 'index.mdx'), 'utf8');
const frontmatter = indexMdx.split(/^---$/m)[1] ?? '';

describe('landing facts agree with the repository build files', () => {
  it('publishes the version gradle.properties actually publishes', () => {
    expect(library.version).toBe(gradleProperty('VERSION_NAME'));
    expect(library.groupId).toBe(gradleProperty('GROUP'));
  });

  it('quotes the Kotlin, Compose Multiplatform and minSdk levels the build uses', () => {
    expect(library.kotlin).toBe(catalogVersion('kotlin'));
    expect(library.composeMultiplatform).toBe(catalogVersion('composeMultiplatform'));
    expect(library.androidMinSdk).toBe(catalogVersion('android-minSdk'));
  });

  it('builds the Maven coordinate from its own parts', () => {
    expect(coordinate).toBe('dev.avinya.ads:admob-cmp:1.1.0');
  });

  it('carries the trademark line verbatim', () => {
    expect(library.trademark).toBe(
      'Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.'
    );
  });

  it('points at the renamed repository and the docs host', () => {
    expect(library.repo).toBe('https://github.com/Meet-Miyani/admob-compose-multiplatform');
    expect(library.site).toBe('https://ads.avinya.dev');
    expect(library.mavenBadge).toBe('https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp');
  });
});

describe('landing components never hardcode a colour', () => {
  const forbidden: Array<[string, RegExp]> = [
    ['hex colour', /#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{1,5})?\b/],
    ['rgb()/rgba()', /\brgba?\s*\(/],
    ['hsl()/hsla()', /\bhsla?\s*\(/],
    ['oklch()/oklab()', /\bokl(?:ch|ab)\s*\(/],
    ['colour keyword', /(?<![-\w])(?:white|black|red|blue|green|grey|gray|silver|navy|teal|orange|purple)(?![-\w])/],
  ];

  it('has at least one landing stylesheet to check', () => {
    expect(landingSources().length).toBeGreaterThan(0);
  });

  for (const file of landingSources()) {
    it(`${file.path.split('/').slice(-2).join('/')} uses only --admob-* tokens`, () => {
      const css = styleBlocks(file);
      for (const [label, pattern] of forbidden) {
        const hit = pattern.exec(css);
        expect(hit, `${label} found: ${hit?.[0]}`).toBeNull();
      }
    });
  }
});

describe('landing page SEO', () => {
  it('keeps the rendered <title> at 60 characters or fewer', () => {
    const title = /^title:\s*(.+)$/m.exec(frontmatter)?.[1].trim();
    expect(title).toBeTruthy();
    expect(`${title} | ${SITE_TITLE}`.length).toBeLessThanOrEqual(60);
  });

  it('targets the head keyword in the title', () => {
    const title = /^title:\s*(.+)$/m.exec(frontmatter)![1].trim();
    expect(title).toContain('Compose Multiplatform');
    expect(title).toContain('AdMob');
  });

  it('keeps the meta description between 120 and 160 characters', () => {
    const description = /^description:\s*(.+)$/m.exec(frontmatter)?.[1].trim();
    expect(description).toBeTruthy();
    expect(description!.length).toBeGreaterThanOrEqual(120);
    expect(description!.length).toBeLessThanOrEqual(160);
  });

  it('renders exactly one H1, and it carries the head keyword', () => {
    expect(frontmatter).toContain('template: splash');
    expect(frontmatter).toContain('Compose Multiplatform AdMob SDK for Android and iOS');
    // No markdown H1 anywhere in the body — Starlight's hero owns the only one.
    const body = indexMdx.split(/^---$/m).slice(2).join('---');
    expect(body).not.toMatch(/^#\s+/m);
  });

  it('keeps the Plan 2 frontmatter contract', () => {
    expect(frontmatter).toContain('og:type');
    expect(frontmatter).toContain('content: website');
  });
});

describe('internal links are directory-style', () => {
  const sources = [{ path: 'index.mdx', text: indexMdx }, ...landingSources()];
  for (const file of sources) {
    it(`${file.path.split('/').slice(-1)[0]} ends every internal link with a slash`, () => {
      const links = [
        ...[...file.text.matchAll(/href=["'](\/[^"'#]*)["']/g)].map((m) => m[1]),
        ...[...file.text.matchAll(/^\s*link:\s*(\/\S*)$/gm)].map((m) => m[1]),
      ];
      for (const link of links) {
        expect(link.endsWith('/'), `${link} must end with a trailing slash`).toBe(true);
      }
    });
  }
});
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test
```

Expected: FAIL. Vitest cannot resolve `../src/data/landing` — `Failed to resolve import "../src/data/landing"`. Nothing else runs.

- [ ] **Step 3: Create the fact module**

Create `docs-site/src/data/landing.ts`:

```ts
/**
 * Single source of truth for every fact and every string on the landing page.
 *
 * The numbers here are asserted against gradle.properties and
 * gradle/libs.versions.toml by docs-site/test/landing.test.ts, so a release that
 * bumps the library fails the docs build until this file is updated. Never
 * inline one of these values into a component.
 */
export const library = {
  brand: 'AdMob CMP',
  version: '1.1.0',
  groupId: 'dev.avinya.ads',
  artifactId: 'admob-cmp',
  gradlePluginId: 'dev.avinya.ads.admob-cmp',
  kotlin: '2.3.20',
  composeMultiplatform: '1.11.1',
  androidMinSdk: '26',
  iosDeploymentTarget: '15.0',
  licence: 'Apache 2.0',
  licenceUrl: 'https://www.apache.org/licenses/LICENSE-2.0.txt',
  site: 'https://ads.avinya.dev',
  repo: 'https://github.com/Meet-Miyani/admob-compose-multiplatform',
  mavenCentral: 'https://central.sonatype.com/artifact/dev.avinya.ads/admob-cmp',
  mavenBadge: 'https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp',
  trademark:
    'Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.',
} as const;

export const coordinate = `${library.groupId}:${library.artifactId}:${library.version}`;
```

- [ ] **Step 4: Create the layout-only stylesheet**

Create `docs-site/src/styles/landing.css`:

```css
/*
 * Landing-page LAYOUT variables only.
 *
 * Colours, fonts, radii and shadows come from the nineteen --admob-* tokens in
 * src/styles/tokens.css (Plan 2). Nothing in this file, and nothing in
 * src/components/landing/**, may declare a literal colour — docs-site/test/
 * landing.test.ts fails the build if one appears.
 */

:root {
  --landing-section-gap: 5.5rem;
  --landing-section-gap-sm: 3.5rem;
  --landing-gutter: 1.5rem;
  --landing-gutter-sm: 1.125rem;
  --landing-head-max: 46rem;
  --landing-card-gap: 1.25rem;
}

/*
 * Starlight ships --sl-content-width: 45rem, which is right for prose and far
 * too narrow for a landing grid. Starlight sets data-has-hero on <body> for any
 * page with `hero` frontmatter, so this widens the splash page and only that.
 */
body[data-has-hero] {
  --sl-content-width: var(--admob-content-max);
}

/* Starlight's markdown vertical rhythm fights the section shells' own padding. */
body[data-has-hero] .sl-markdown-content > .landing-section {
  margin-top: 0;
}

@media (prefers-reduced-motion: reduce) {
  .landing-section * {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
  }
}
```

- [ ] **Step 5: Create the section shell**

Create `docs-site/src/components/landing/Section.astro`:

```astro
---
import '../../styles/landing.css';

interface Props {
  /** Anchor id. Also derives the aria-labelledby target. */
  id: string;
  /** Small monospace kicker above the heading. Optional. */
  eyebrow?: string;
  /** The section H2. Required — every landing section is labelled. */
  heading: string;
  /** One or two sentences under the heading. Optional. */
  lead?: string;
  /** Draw a raised surface card around the section body. */
  panel?: boolean;
}

const { id, eyebrow, heading, lead, panel = false } = Astro.props;
---

<section class="landing-section" id={id} aria-labelledby={`${id}-heading`}>
  <div class:list={['landing-inner', panel && 'is-panel']}>
    <header class="landing-head">
      {eyebrow && (
        <p class="landing-eyebrow">
          <span class="landing-eyebrow-mark" aria-hidden="true"></span>{eyebrow}
        </p>
      )}
      <h2 id={`${id}-heading`} class="landing-heading">{heading}</h2>
      {lead && <p class="landing-lead">{lead}</p>}
    </header>
    <slot />
  </div>
</section>

<style>
  .landing-section {
    padding-block: var(--landing-section-gap);
  }

  .landing-inner.is-panel {
    background: var(--admob-surface);
    border: var(--admob-border);
    border-radius: var(--admob-radius-lg);
    padding: clamp(1.5rem, 4vw, 3rem);
  }

  .landing-head {
    max-width: var(--landing-head-max);
    margin-block-end: 2.5rem;
  }

  .landing-eyebrow {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin: 0 0 0.625rem;
    font-family: var(--admob-font-mono);
    font-size: 0.75rem;
    line-height: 1.2;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    /* --admob-slate, not --admob-accent: the accent on paper measures 4.27:1 in
       the light theme, under the 4.5:1 AA floor for text this size. The accent
       appears as the decorative mark instead, where contrast rules do not bite. */
    color: var(--admob-slate);
  }

  .landing-eyebrow-mark {
    inline-size: 0.5rem;
    block-size: 0.5rem;
    border-radius: 2px;
    background: var(--admob-accent);
    flex: none;
  }

  .landing-heading {
    margin: 0;
    font-family: var(--admob-font-display);
    font-size: clamp(1.6rem, 1.1rem + 2vw, 2.4rem);
    line-height: 1.12;
    letter-spacing: var(--admob-tracking-tight);
    color: var(--admob-ink);
  }

  .landing-lead {
    margin: 0.875rem 0 0;
    font-family: var(--admob-font-body);
    font-size: 1.0625rem;
    line-height: 1.65;
    color: var(--admob-slate);
  }

  @media (max-width: 48rem) {
    .landing-section {
      padding-block: var(--landing-section-gap-sm);
    }
    .landing-head {
      margin-block-end: 1.75rem;
    }
  }
</style>
```

- [ ] **Step 6: Replace `index.mdx` with the final frontmatter and the first section**

Overwrite `docs-site/src/content/docs/index.mdx`. `title` is 31 characters, so the rendered `<title>` is `Compose Multiplatform AdMob SDK | AdMob CMP` — 43 characters. `description` is 142. Tasks 2–8 add sections to the body; this frontmatter is final.

```mdx
---
title: Compose Multiplatform AdMob SDK
description: 'Open-source Compose Multiplatform AdMob SDK: six ad formats on Android and iOS from one Kotlin API, with UMP consent built into the init flow.'
template: splash
hero:
  title: Compose Multiplatform AdMob SDK for Android and iOS
  tagline: Six AdMob formats on Android and iOS behind one Kotlin API — suspend functions, StateFlow, and UMP consent wired into initialization.
  actions:
    - text: Start the 5-minute quickstart
      link: /start/quickstart/
      icon: right-arrow
      variant: primary
    - text: View on GitHub
      link: https://github.com/Meet-Miyani/admob-compose-multiplatform
      icon: external
      variant: secondary
head:
  - tag: meta
    attrs:
      property: og:type
      content: website
---

import Section from '../../components/landing/Section.astro';

<Section
  id="formats"
  eyebrow="Ad formats"
  heading="Six ad formats, one Kotlin API"
  lead="Banner, interstitial, rewarded, rewarded interstitial, app-open and native — the same placement type, the same controllers and the same suspend load-and-show contract on Android and iOS."
>
  <p>The format grid lands in Task 3.</p>
</Section>
```

- [ ] **Step 7: Run the tests and the build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build
```

Expected: every test in `test/landing.test.ts` PASSES, Plan 2's `test/seo.test.ts` still passes, and `npm run build` finishes with `Complete!`.

If `npm test` reports the `<title>` over 60 characters, the site title in `astro.config.mjs` changed — shorten the frontmatter `title`, never the assertion.

- [ ] **Step 8: Confirm the rendered head, then commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node -e "
const h = require('fs').readFileSync('dist/index.html','utf8');
const t = /<title>([^<]*)<\/title>/g;
const titles = [...h.matchAll(t)].map(m => m[1]);
console.log('titles:', titles.length, JSON.stringify(titles));
console.log('h1 count:', (h.match(/<h1[ >]/g) || []).length);
console.log('ld+json blocks:', (h.match(/application\/ld\+json/g) || []).length);
console.log('og:image:', /property=\"og:image\" content=\"([^\"]+)\"/.exec(h)?.[1]);
"
```

Expected: `titles: 1 ["Compose Multiplatform AdMob SDK | AdMob CMP"]`, `h1 count: 1`, at least one `ld+json` block (Plan 2's `SoftwareSourceCode`), and an absolute `og:image` under `https://ads.avinya.dev/og/`.

If `h1 count` is 2, a markdown `#` heading crept into the body — remove it; Starlight's hero owns the only H1.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/data/landing.ts docs-site/src/styles/landing.css \
        docs-site/src/components/landing/Section.astro \
        docs-site/src/content/docs/index.mdx docs-site/test/landing.test.ts
git commit -m "feat(docs-site): landing page skeleton, fact module and section shell"
```

---

### Task 2: Hero badge strip

Starlight's `hero` frontmatter (Task 1) already renders the H1, the value proposition and both CTAs. This task adds the badge strip and the copyable Maven coordinate directly beneath them.

**Files:**
- Create: `docs-site/src/components/landing/BadgeStrip.astro`
- Modify: `docs-site/src/content/docs/index.mdx` (import and render `BadgeStrip` above the `formats` section)

**Interfaces:**
- Consumes: `library`, `coordinate` from `src/data/landing.ts` (Task 1).
- Produces: `BadgeStrip.astro`, no props. Emits `<div class="badge-strip">` containing four badges and a `<p class="badge-coordinate">`.

**Badge rendering decision — read before writing the component.** The Maven Central version badge is the live shields.io image named in the design's immutable decisions, because keeping it live is the whole point of that badge: it reads the real Maven Central metadata, so the page cannot claim `1.1.0` after `1.2.0` ships. The other three badges are static repository facts that already live in `src/data/landing.ts` and are asserted against `gradle.properties` and `libs.versions.toml` by `test/landing.test.ts`; shipping three more third-party image requests to render three constants buys nothing, so they are local pills styled to shields' `flat-square` geometry (20 px tall, 3 px radius, muted label segment, accent value segment). The strip therefore looks uniform and makes exactly one external request.

- [ ] **Step 1: Write the component**

Create `docs-site/src/components/landing/BadgeStrip.astro`:

```astro
---
import '../../styles/landing.css';
import { library, coordinate } from '../../data/landing';

/**
 * One live shields.io image (the version badge, which must stay accurate on its
 * own) plus three local pills for facts that test/landing.test.ts already keeps
 * honest. The version badge sits in a fixed-width slot so a late-arriving SVG
 * cannot reflow its siblings and score CLS.
 */
const pills = [
  { label: 'license', value: library.licence, href: library.licenceUrl },
  { label: 'kotlin', value: library.kotlin, href: 'https://kotlinlang.org/' },
  { label: 'platforms', value: 'Android · iOS', href: '/reference/compatibility/' },
];
---

<div class="badge-strip">
  <ul class="badges" aria-label="Project status">
    <li class="badge-slot">
      <a href={library.mavenCentral} rel="noopener">
        <img
          src={library.mavenBadge}
          alt={`admob-cmp on Maven Central, current version ${library.version}`}
          height="20"
          loading="eager"
          decoding="async"
        />
      </a>
    </li>
    {pills.map((pill) => (
      <li>
        <a class="pill" href={pill.href} rel="noopener">
          <span class="pill-label">{pill.label}</span>
          <span class="pill-value">{pill.value}</span>
        </a>
      </li>
    ))}
  </ul>

  <p class="badge-coordinate">
    <span class="coordinate-label">Add to <code>commonMain</code>:</span>
    <code class="coordinate">{coordinate}</code>
  </p>
</div>

<style>
  .badge-strip {
    display: flex;
    flex-direction: column;
    gap: 1rem;
    align-items: center;
    margin-block: 0 1rem;
  }

  .badges {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 0.5rem;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .badges li {
    display: flex;
    block-size: 20px;
  }

  /* Reserve the badge's rendered width so its late load shifts nothing.
     Step 3 replaces 140px with the measured intrinsic width. */
  .badge-slot {
    inline-size: 140px;
    overflow: hidden;
  }

  .badge-slot img {
    display: block;
    block-size: 20px;
    inline-size: auto;
  }

  .pill {
    display: inline-flex;
    align-items: stretch;
    block-size: 20px;
    border-radius: 3px;
    overflow: hidden;
    font-family: var(--admob-font-mono);
    font-size: 0.6875rem;
    line-height: 20px;
    text-decoration: none;
  }

  .pill-label,
  .pill-value {
    padding-inline: 0.5rem;
    white-space: nowrap;
  }

  .pill-label {
    background: var(--admob-surface);
    color: var(--admob-ink);
    border: var(--admob-border);
    border-inline-end: 0;
  }

  .pill-value {
    background: var(--admob-accent);
    color: var(--admob-accent-contrast);
  }

  .pill:hover .pill-value,
  .pill:focus-visible .pill-value {
    filter: brightness(1.1);
  }

  .badge-coordinate {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    align-items: center;
    gap: 0.5rem;
    margin: 0;
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
    color: var(--admob-slate);
  }

  .coordinate {
    font-family: var(--admob-font-mono);
    font-size: 0.875rem;
    padding: 0.35rem 0.7rem;
    border-radius: var(--admob-radius);
    background: var(--admob-code);
    border: var(--admob-border);
    color: var(--admob-ink);
    user-select: all;
  }

  @media (max-width: 30rem) {
    .coordinate-label {
      display: none;
    }
    .coordinate {
      font-size: 0.8125rem;
      overflow-wrap: anywhere;
    }
  }
</style>
```

- [ ] **Step 2: Render it under the hero**

In `docs-site/src/content/docs/index.mdx`, add the import and place `<BadgeStrip />` as the first thing in the body, above the `formats` section:

```mdx
import BadgeStrip from '../../components/landing/BadgeStrip.astro';
import Section from '../../components/landing/Section.astro';

<BadgeStrip />

<Section
  id="formats"
```

- [ ] **Step 3: Build, measure the badge, and fix the reserved width**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm run build && npm run preview
```

In a second terminal:

```bash
curl -sI "https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp" | head -1
curl -s "https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp" | head -c 200
```

Expected: `HTTP/2 200`, and the SVG opening tag carries `width="..."` — read that number. It renders `v1.1.0`.

Set `.badge-slot { inline-size: <that width>px; }` in `BadgeStrip.astro`. If shields is unreachable, leave `140px`: it over-reserves by a few pixels, which costs a small gap but never a layout shift.

- [ ] **Step 4: Run the tests and the build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build
```

Expected: PASS, including the `BadgeStrip.astro uses only --admob-* tokens` case added automatically by the file scan, and `Complete!` from the build.

- [ ] **Step 5: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/components/landing/BadgeStrip.astro docs-site/src/content/docs/index.mdx
git commit -m "feat(docs-site): landing hero badge strip and Maven coordinate"
```

---

### Task 3: Six-format showcase grid

The grid is the section that later hosts Plan 7's device screenshots. It ships now with inline SVG placeholder art and swaps to real captures by changing **one field per record** in `src/data/landing.ts` — no markup change (Task 9, Step 5).

**Files:**
- Modify: `docs-site/src/data/landing.ts` (append the `LandingFormat` type and `landingFormats`)
- Create: `docs-site/src/components/landing/FormatArt.astro`
- Create: `docs-site/src/components/landing/FormatCard.astro`
- Create: `docs-site/src/components/landing/FormatGrid.astro`
- Modify: `docs-site/src/content/docs/index.mdx` (replace the `formats` section placeholder with `<FormatGrid />`)
- Modify: `docs-site/test/landing.test.ts` (append the format-record suite)

**Interfaces:**
- Consumes: `Section.astro` (Task 1); optionally `docs-site/src/components/Screenshot.astro` (Plan 7 Task 15) with props `name: string` (a `file` value from `screenshots.json`), `class?: string`, `loading?: 'lazy' | 'eager'`, `sizes?: string`. `Screenshot.astro` emits a `<Picture>` carrying class `screenshot` and `style="--screenshot-focus: <top|center|bottom>"`, uncropped and unframed — the consumer supplies `aspect-ratio` and `object-fit`.
- Produces:
  - `export interface LandingFormat { slug: string; name: string; href: string; blurb: string; api: string; screenshot: string | null; crop: 'top' | 'center' | 'bottom'; }`
  - `export const landingFormats: readonly LandingFormat[]` — exactly six records, in the order banner, interstitial, rewarded, rewarded-interstitial, app-open, native.
  - `FormatArt.astro` with `Props { kind: string }` — one wireframe per `slug`.
  - `FormatCard.astro` with `Props { format: LandingFormat }` — the **only** call site of `Screenshot.astro` in this plan.
  - `FormatGrid.astro`, no props.

**Screenshot subjects this landing page requires from Plan 7.** Plan 7's `screenshots.test.mjs` asserts "Plan 5 required-subject coverage" against exactly this list. All are `deviceClass: phone`, `platform: android`, `theme: dark`:

| Card | Plan 7 `subject` | Manifest `file` | Manifest `focus` |
|---|---|---|---|
| Banner | `banner` | `banner-android-dark.png` | `bottom` |
| Interstitial | `interstitial` | `interstitial-android-dark.png` | `center` |
| Rewarded | `rewarded` | `rewarded-android-dark.png` | `center` |
| Rewarded interstitial | `rewarded-interstitial` | `rewarded-interstitial-android-dark.png` | `center` |
| App-open | `app-open` | `app-open-android-dark.png` | `top` |
| Native | `native` | `native-android-dark.png` | `center` |

The seventh required subject, `banner-collapsible`, is consumed by Plan 3's `/formats/banner/` page, not by this grid.

**Why dark-only screenshots on a page that themes both ways.** `DebugTokens` in `admob-cmp-compose` declares a fixed dark palette and deliberately does not call `isSystemInDarkTheme()`, and `AdTemplates` hardcodes white native-card backgrounds, so a light-theme capture of a harness surface cannot be produced at all. Two alternatives were considered and rejected: forcing a permanently dark showcase band is not expressible from Plan 2's token set, because that ramp inverts between themes and `color-mix()` cannot select "whichever of ink and paper is darker"; and adding a twentieth `--admob-*` token would break Plan 2's "do not introduce a parallel token set" contract. The design instead treats each capture as **content, not chrome** — the way an app-store listing does. Each screenshot sits on a `--admob-code` stage with a `--admob-border` hairline and `--admob-radius`, and every card carries a caption naming it a Google test ad in the bundled debug harness. The stage, border, caption and card all follow the site theme; the screenshot inside is a picture of an app, and reads as one in both themes.

- [ ] **Step 1: Write the failing test**

Append to `docs-site/test/landing.test.ts`:

```ts
import { landingFormats } from '../src/data/landing';

describe('the six-format showcase', () => {
  const expectedOrder = [
    'banner',
    'interstitial',
    'rewarded',
    'rewarded-interstitial',
    'app-open',
    'native',
  ];

  it('lists exactly the six formats, in the documented order', () => {
    expect(landingFormats.map((f) => f.slug)).toEqual(expectedOrder);
  });

  it('links every card into the /formats/ tree with a directory-style URL', () => {
    for (const format of landingFormats) {
      expect(format.href.startsWith('/formats/'), format.slug).toBe(true);
      expect(
        format.href.endsWith('/') || format.href.includes('/#'),
        `${format.slug}: ${format.href} must end in a slash or be a slash-anchored fragment`
      ).toBe(true);
    }
  });

  it('names a real API entry point and a substantive blurb for each card', () => {
    for (const format of landingFormats) {
      expect(format.api.length, format.slug).toBeGreaterThan(8);
      expect(format.blurb.length, format.slug).toBeGreaterThan(60);
      expect(format.blurb.length, format.slug).toBeLessThanOrEqual(200);
      expect(['top', 'center', 'bottom']).toContain(format.crop);
    }
  });

  it('only ever names dark-theme Android phone captures', () => {
    for (const format of landingFormats) {
      if (format.screenshot === null) continue;
      expect(format.screenshot).toBe(`${format.slug}-android-dark.png`);
    }
  });

  it('agrees with the Plan 7 manifest once Plan 7 has run', () => {
    const manifestPath = join(docsSite, 'src', 'assets', 'screenshots', 'screenshots.json');
    if (!existsSync(manifestPath)) {
      expect(landingFormats.every((f) => f.screenshot === null)).toBe(true);
      return;
    }
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
    for (const format of landingFormats) {
      expect(format.screenshot, `${format.slug} must be wired up once the manifest exists`).not.toBeNull();
      const entry = manifest.screenshots.find((s: { file: string }) => s.file === format.screenshot);
      expect(entry, `${format.screenshot} is not in screenshots.json`).toBeTruthy();
      expect(entry.focus, `${format.slug}: crop must match the manifest focus hint`).toBe(format.crop);
      expect(entry.theme).toBe('dark');
      expect(entry.deviceClass).toBe('phone');
    }
  });
});

describe('landing components go through Screenshot.astro', () => {
  it('never references an image path under src/assets/screenshots directly', () => {
    for (const file of landingSources()) {
      expect(file.text, file.path).not.toMatch(/assets\/screenshots\//);
    }
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test
```

Expected: FAIL — `"landingFormats" is not exported by "src/data/landing.ts"`.

- [ ] **Step 3: Append the format records**

Append to `docs-site/src/data/landing.ts`:

```ts
export interface LandingFormat {
  /** Stable key. Doubles as the FormatArt wireframe variant and the Plan 7 subject. */
  slug: string;
  name: string;
  /** Destination in the /formats/ tree. Directory-style, or slash-anchored. */
  href: string;
  /** One or two sentences. Every claim traceable to admob-cmp/AGENTS.md. */
  blurb: string;
  /** The API entry point, shown as inline code on the card. */
  api: string;
  /**
   * A `file` value from docs-site/src/assets/screenshots/screenshots.json, or
   * null until Plan 7 has run. This is the ONLY field that changes when the real
   * screenshots land — see Task 9, Step 5.
   */
  screenshot: string | null;
  /** Must equal the manifest entry's `focus`. Asserted by test/landing.test.ts. */
  crop: 'top' | 'center' | 'bottom';
}

export const landingFormats: readonly LandingFormat[] = [
  {
    slug: 'banner',
    name: 'Banner',
    href: '/formats/banner/',
    blurb:
      'Anchored adaptive sizes, collapsible placements, and SDK-, ad-server- or manually-managed refresh. The composable measures its own container and supplies the width.',
    api: 'BannerAdView(placement)',
    screenshot: null,
    crop: 'bottom',
  },
  {
    slug: 'interstitial',
    name: 'Interstitial',
    href: '/formats/interstitial/',
    blurb:
      'Suspend load(), then show() returns when the ad is dismissed. An optional FIFO cache holds several ads with a one-hour TTL and reloads after each show.',
    api: 'adManager.interstitial(placement)',
    screenshot: null,
    crop: 'center',
  },
  {
    slug: 'rewarded',
    name: 'Rewarded',
    href: '/formats/rewarded/',
    blurb:
      'The same load-and-show contract, with the reward delivered through an onReward callback and an AdEvent.RewardEarned on the shared event stream.',
    api: 'adManager.rewarded(placement)',
    screenshot: null,
    crop: 'center',
  },
  {
    slug: 'rewarded-interstitial',
    name: 'Rewarded interstitial',
    href: '/formats/rewarded/#rewarded-interstitial',
    blurb:
      'A rewarded ad offered at a transition point rather than behind a button, with the same controller interface and the same reward callback.',
    api: 'adManager.rewardedInterstitial(placement)',
    screenshot: null,
    crop: 'center',
  },
  {
    slug: 'app-open',
    name: 'App-open',
    href: '/formats/app-open/',
    blurb:
      'AppOpenAdCoordinator watches foreground transitions with a minimum background duration and a cooldown between shows, and can be blocked during purchases or onboarding.',
    api: 'AppOpenAdCoordinator(manager, controller, config)',
    screenshot: null,
    crop: 'top',
  },
  {
    slug: 'native',
    name: 'Native',
    href: '/formats/native/',
    blurb:
      'Compose the ad from headline, body, icon, media and call-to-action nodes with the adLayout {} DSL. A pool with maxSize accounting serves per-item ads in a feed.',
    api: 'NativeAdView(placement, itemKey, layout)',
    screenshot: null,
    crop: 'center',
  },
];
```

- [ ] **Step 4: Write the placeholder art**

Create `docs-site/src/components/landing/FormatArt.astro`:

```astro
---
/**
 * Wireframe stand-in for a device capture, one variant per format slug. Drawn to
 * the same 4:5 stage the real screenshots crop to, so replacing art with a photo
 * changes nothing about the layout. Purely decorative — the card's own text is
 * the accessible label.
 */
interface Props {
  kind: string;
}

const { kind } = Astro.props;
---

<svg
  class="format-art"
  viewBox="0 0 320 400"
  preserveAspectRatio="xMidYMid slice"
  role="presentation"
  aria-hidden="true"
  focusable="false"
>
  <rect class="app" x="0" y="0" width="320" height="400" />
  <rect class="chrome" x="20" y="18" width="60" height="8" rx="4" />
  <rect class="chrome" x="272" y="18" width="28" height="8" rx="4" />

  {kind === 'banner' && (
    <Fragment>
      <rect class="title" x="20" y="44" width="150" height="16" rx="6" />
      <rect class="line" x="20" y="82" width="280" height="110" rx="10" />
      <rect class="line" x="20" y="206" width="280" height="9" rx="4" />
      <rect class="line" x="20" y="223" width="230" height="9" rx="4" />
      <rect class="line" x="20" y="240" width="262" height="9" rx="4" />
      <rect class="line" x="20" y="257" width="200" height="9" rx="4" />
      <rect class="ad" x="20" y="316" width="280" height="60" rx="8" />
      <rect class="adchip" x="30" y="326" width="22" height="10" rx="3" />
      <rect class="adline" x="30" y="346" width="150" height="10" rx="5" />
    </Fragment>
  )}

  {kind === 'interstitial' && (
    <Fragment>
      <rect class="ad" x="12" y="12" width="296" height="376" rx="12" />
      <rect class="adchip" x="26" y="26" width="22" height="10" rx="3" />
      <path class="glyph" d="M272 24 l16 16 M288 24 l-16 16" />
      <rect class="admedia" x="44" y="72" width="232" height="130" rx="8" />
      <rect class="adline" x="60" y="226" width="200" height="14" rx="6" />
      <rect class="adline" x="90" y="252" width="140" height="10" rx="5" />
      <rect class="cta" x="100" y="304" width="120" height="34" rx="8" />
    </Fragment>
  )}

  {kind === 'rewarded' && (
    <Fragment>
      <rect class="ad" x="12" y="12" width="296" height="376" rx="12" />
      <rect class="adchip" x="26" y="26" width="22" height="10" rx="3" />
      <rect class="pill" x="212" y="22" width="84" height="18" rx="9" />
      <path class="glyph star" d="M160 108 l14 30 33 5 -24 23 6 33 -29 -16 -29 16 6 -33 -24 -23 33 -5 z" />
      <rect class="adline" x="80" y="228" width="160" height="12" rx="6" />
      <rect class="adline" x="104" y="252" width="112" height="9" rx="4" />
      <rect class="cta" x="100" y="304" width="120" height="34" rx="8" />
    </Fragment>
  )}

  {kind === 'rewarded-interstitial' && (
    <Fragment>
      <rect class="ad" x="12" y="12" width="296" height="376" rx="12" />
      <rect class="adchip" x="26" y="26" width="22" height="10" rx="3" />
      <rect class="pill" x="196" y="22" width="100" height="18" rx="9" />
      <rect class="admedia" x="44" y="72" width="232" height="112" rx="8" />
      <path class="glyph star" d="M160 214 l10 21 23 3 -17 16 4 23 -20 -11 -20 11 4 -23 -17 -16 23 -3 z" />
      <rect class="adline" x="80" y="266" width="160" height="10" rx="5" />
      <rect class="cta" x="100" y="304" width="120" height="34" rx="8" />
    </Fragment>
  )}

  {kind === 'app-open' && (
    <Fragment>
      <rect class="ad" x="12" y="12" width="296" height="376" rx="12" />
      <rect class="adchip" x="26" y="26" width="22" height="10" rx="3" />
      <rect class="icon" x="128" y="112" width="64" height="64" rx="16" />
      <rect class="adline" x="100" y="200" width="120" height="12" rx="6" />
      <rect class="adline" x="76" y="222" width="168" height="9" rx="4" />
      <rect class="cta" x="100" y="304" width="120" height="34" rx="8" />
    </Fragment>
  )}

  {kind === 'native' && (
    <Fragment>
      <rect class="title" x="20" y="44" width="150" height="16" rx="6" />
      <rect class="line" x="20" y="82" width="280" height="9" rx="4" />
      <rect class="line" x="20" y="99" width="240" height="9" rx="4" />
      <rect class="ad" x="12" y="126" width="296" height="182" rx="10" />
      <rect class="admedia" x="24" y="138" width="272" height="86" rx="6" />
      <rect class="adchip" x="24" y="232" width="22" height="10" rx="3" />
      <rect class="adline" x="54" y="232" width="150" height="10" rx="5" />
      <rect class="adline" x="24" y="252" width="200" height="8" rx="4" />
      <rect class="cta" x="24" y="272" width="104" height="24" rx="6" />
      <rect class="line" x="20" y="330" width="280" height="9" rx="4" />
      <rect class="line" x="20" y="347" width="210" height="9" rx="4" />
    </Fragment>
  )}
</svg>

<style>
  .format-art {
    display: block;
    inline-size: 100%;
    block-size: 100%;
  }
  .app {
    fill: var(--admob-code);
  }
  .chrome,
  .line,
  .admedia,
  .adline,
  .pill,
  .icon {
    fill: var(--admob-hair);
  }
  .title {
    fill: var(--admob-slate);
    opacity: 0.45;
  }
  .ad {
    fill: var(--admob-accent-soft);
    stroke: var(--admob-accent);
    stroke-width: 1.5;
  }
  .adchip,
  .cta {
    fill: var(--admob-accent);
  }
  .glyph {
    fill: none;
    stroke: var(--admob-slate);
    stroke-width: 2.5;
    stroke-linecap: round;
  }
  .glyph.star {
    fill: var(--admob-accent);
    stroke: none;
  }
</style>
```

- [ ] **Step 5: Write the card**

Create `docs-site/src/components/landing/FormatCard.astro`. This is the only file in the plan that may reference `Screenshot.astro`.

`Screenshot.astro` is resolved through `import.meta.glob` rather than a bare `import`. A bare import of a file Plan 7 has not created yet fails the build, which would make this whole plan depend on Plan 7; `import.meta.glob` returns `{}` when nothing matches, so the card falls back to the wireframe and the build stays green. When Plan 7 lands, the same glob picks the component up with no edit here — the retrofit in Task 9 changes data only.

```astro
---
import type { LandingFormat } from '../../data/landing';
import FormatArt from './FormatArt.astro';

interface Props {
  format: LandingFormat;
}

const { format } = Astro.props;

/**
 * Plan 7 Task 15 creates ../Screenshot.astro. Until it does, this glob is empty
 * and every card renders FormatArt. A bare `import` here would hard-fail the
 * build before Plan 7 runs; a glob degrades instead. The capitalised binding is
 * required for Astro to treat the value as a component.
 */
const screenshotModules = import.meta.glob<{ default: unknown }>('../Screenshot*.astro', {
  eager: true,
});
const Screenshot = (screenshotModules['../Screenshot.astro']?.default ?? null) as any;
---

<li class="format-card">
  <a class="format-link" href={format.href}>
    <span class="format-stage" data-crop={format.crop}>
      {format.screenshot && Screenshot
        ? <Screenshot name={format.screenshot} sizes="(max-width: 40rem) 88vw, (max-width: 64rem) 42vw, 20rem" />
        : <FormatArt kind={format.slug} />}
    </span>
    <span class="format-body">
      <span class="format-name">{format.name}</span>
      <span class="format-blurb">{format.blurb}</span>
      <code class="format-api">{format.api}</code>
    </span>
  </a>
  <p class="format-caption">Google test ad in the bundled debug harness.</p>
</li>

<style>
  .format-card {
    display: flex;
    flex-direction: column;
    background: var(--admob-surface);
    border: var(--admob-border);
    border-radius: var(--admob-radius-lg);
    overflow: hidden;
  }

  .format-link {
    display: flex;
    flex-direction: column;
    flex: 1;
    text-decoration: none;
    color: inherit;
  }

  .format-link:hover,
  .format-link:focus-visible {
    text-decoration: none;
  }

  .format-card:hover,
  .format-card:focus-within {
    box-shadow: var(--admob-shadow);
  }

  /*
   * The stage is the swap point. Plan 7 ships uncropped full framebuffers, so the
   * consumer supplies the crop: 4:5, cover, anchored by the manifest's focus hint,
   * which Screenshot.astro exposes as --screenshot-focus. The placeholder SVG is
   * drawn to the same box, so swapping data changes nothing about the layout.
   */
  .format-stage {
    display: block;
    aspect-ratio: 4 / 5;
    background: var(--admob-code);
    border-block-end: var(--admob-border);
    overflow: hidden;
  }

  .format-stage :global(.screenshot) {
    inline-size: 100%;
    block-size: 100%;
    object-fit: cover;
    border-radius: 0;
  }

  .format-body {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    padding: 1.125rem 1.25rem 1.25rem;
  }

  .format-name {
    font-family: var(--admob-font-display);
    font-size: 1.125rem;
    line-height: 1.2;
    letter-spacing: var(--admob-tracking-tight);
    color: var(--admob-ink);
  }

  .format-blurb {
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
    line-height: 1.55;
    color: var(--admob-slate);
  }

  .format-api {
    align-self: flex-start;
    margin-block-start: 0.25rem;
    padding: 0.3rem 0.55rem;
    border-radius: var(--admob-radius);
    background: var(--admob-code);
    border: var(--admob-border);
    font-family: var(--admob-font-mono);
    font-size: 0.75rem;
    line-height: 1.4;
    color: var(--admob-ink);
    overflow-wrap: anywhere;
  }

  .format-caption {
    margin: 0;
    padding: 0 1.25rem 1rem;
    font-family: var(--admob-font-body);
    font-size: 0.75rem;
    line-height: 1.4;
    color: var(--admob-slate);
  }
</style>
```

- [ ] **Step 6: Write the grid and render it**

Create `docs-site/src/components/landing/FormatGrid.astro`:

```astro
---
import { landingFormats } from '../../data/landing';
import FormatCard from './FormatCard.astro';
---

<ul class="format-grid">
  {landingFormats.map((format) => <FormatCard format={format} />)}
</ul>

<style>
  .format-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: var(--landing-card-gap);
    margin: 0;
    padding: 0;
    list-style: none;
  }

  @media (min-width: 40rem) {
    .format-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (min-width: 64rem) {
    .format-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }
</style>
```

Then in `docs-site/src/content/docs/index.mdx`, add the import and replace the placeholder paragraph inside the `formats` section:

```mdx
import FormatGrid from '../../components/landing/FormatGrid.astro';
```

```mdx
  lead="Banner, interstitial, rewarded, rewarded interstitial, app-open and native — the same placement type, the same controllers and the same suspend load-and-show contract on Android and iOS."
>
  <FormatGrid />
</Section>
```

- [ ] **Step 7: Run the tests and the build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build
```

Expected: all `six-format showcase` cases PASS (the manifest case takes the "Plan 7 has not run" branch and asserts every `screenshot` is `null`), and the build finishes with `Complete!`.

Then confirm the grid actually rendered six placeholder wireframes and no `<img>`:

```bash
node -e "
const h = require('fs').readFileSync('dist/index.html','utf8');
console.log('cards:', (h.match(/class=\"[^\"]*format-card/g) || []).length);
console.log('wireframes:', (h.match(/format-art/g) || []).length);
console.log('screenshot imgs:', (h.match(/class=\"[^\"]*screenshot/g) || []).length);
"
```

Expected: `cards: 6`, `wireframes: 6` or more (the class appears on both the element and its scoped rule), `screenshot imgs: 0`. Task 9, Step 5 inverts the last two.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/data/landing.ts docs-site/test/landing.test.ts \
        docs-site/src/components/landing/FormatArt.astro \
        docs-site/src/components/landing/FormatCard.astro \
        docs-site/src/components/landing/FormatGrid.astro \
        docs-site/src/content/docs/index.mdx
git commit -m "feat(docs-site): six-format showcase grid with screenshot-ready stages"
```

---

### Task 4: Quickstart code sample

A real, correct, copy-pasteable integration. Every identifier is checked against `admob-cmp/AGENTS.md` and the committed ABI dump `admob-cmp-core/api/admob-cmp-core.klib.api`. The sample uses the explicit three-call sequence rather than `gatherConsentAndInitialize`, because the shorthand omits the ATT step and the ATT ordering is the single most expensive mistake an integrator can make on iOS.

The code lives as fenced blocks in `index.mdx`, not inside an `.astro` component, so Starlight's bundled Expressive Code gives it syntax highlighting, a copy button, a titled frame and theme-aware colours with no work here.

**Files:**
- Modify: `docs-site/src/content/docs/index.mdx`

**Interfaces:**
- Consumes: `Section.astro` (Task 1); `Aside` from `@astrojs/starlight/components` (shipped with Starlight 0.41.5).
- Produces: the `#quickstart` section anchor. No new exports.

**API facts this sample depends on** (all verified against the repository, do not change them):

| Symbol | Verified signature |
|---|---|
| `rememberAdManager()` | `dev.avinya.ads`, `@Composable`, returns `AdManager` |
| `AdManager.status` | `StateFlow<AdManagerStatus>`; `AdManagerStatus.Ready` is an `object`, so compare with `==` |
| `AdConfig(...)` | convenience constructor `(androidAppId: String, iosAppId: String, testMode: Boolean = false, …)` |
| `AdManager.consent.gatherConsent(config)` | `suspend`, returns `ConsentStatus` |
| `AdManager.tracking.requestAuthorization()` | `suspend`, returns `AdTrackingAuthorization` |
| `AdManager.initialize(config, mode)` | `suspend`, returns `AdManagerStatus`; `ConsentMode.InitializeOnlyIfAlreadyAllowed` is a real enum entry |
| `AdPlacement(...)` | convenience constructor `(id, format, androidAdUnitId, iosAdUnitId, maxCacheSize = 1, enabled = true, strictTestMode = false)` |
| `BannerAdView(...)` | `dev.avinya.ads.ui`, params `(placement, modifier, …)` |
| `TestAdIds.*` | flat constants — `TestAdIds.ANDROID_BANNER`, never `TestAdIds.Android.BANNER` |

- [ ] **Step 1: Add the section to `index.mdx`**

Add the `Aside` import alongside the existing imports at the top of the body:

```mdx
import { Aside } from '@astrojs/starlight/components';
```

Then append after the `formats` section:

````mdx
<Section
  id="quickstart"
  eyebrow="Quickstart"
  heading="Add the dependency, gather consent, show an ad"
  lead="This is the whole Kotlin integration. Platform setup — the Android manifest entry, and the iOS Swift Package Manager packages plus Info.plist keys — is a separate required step that the installation guides walk through."
>

```kotlin title="build.gradle.kts"
plugins {
    // Only needed if your project runs Kotlin/Native tests. Without it the test
    // link fails on: Undefined symbols … _OBJC_CLASS_$_GAD*
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation("dev.avinya.ads:admob-cmp:1.1.0")
    }
}
```

```kotlin title="App.kt"
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.avinya.ads.AdConfig
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdManagerStatus
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.ConsentMode
import dev.avinya.ads.TestAdIds
import dev.avinya.ads.rememberAdManager
import dev.avinya.ads.ui.BannerAdView

@Composable
fun App() {
    val adManager = rememberAdManager()
    val status by adManager.status.collectAsState()

    // Order matters on iOS: UMP consent, then ATT, then initialize. Requesting
    // ads before ATT resolves permanently forfeits the IDFA for those requests.
    // Android has no ATT — `tracking` is a no-op reporting NotApplicable.
    LaunchedEffect(Unit) {
        val config = AdConfig(
            androidAppId = TestAdIds.ANDROID_APP_ID,
            iosAppId = TestAdIds.IOS_APP_ID,
            testMode = true
        )
        adManager.consent.gatherConsent(config)
        adManager.tracking.requestAuthorization()
        adManager.initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)
    }

    val placement = remember {
        AdPlacement(
            id = "home_banner",
            format = AdFormat.Banner,
            androidAdUnitId = TestAdIds.ANDROID_BANNER,
            iosAdUnitId = TestAdIds.IOS_BANNER,
            strictTestMode = true
        )
    }

    if (status == AdManagerStatus.Ready) {
        BannerAdView(placement = placement, modifier = Modifier.fillMaxWidth())
    }
}
```

<Aside type="caution" title="testMode does not serve test ads">
  `testMode` configures UMP consent debugging only — it does not make Google Mobile
  Ads return test creatives. This sample serves test ads because it points at
  `TestAdIds` ad units. `strictTestMode = true` is the guard in the other direction:
  the placement throws at construction if a production ad unit id ever reaches it, so
  leave it on in debug builds.
</Aside>

<p class="quickstart-next">
  Next: <a href="/start/installation/">Installation</a> ·
  <a href="/start/android-setup/">Android setup</a> ·
  <a href="/start/ios-setup/">iOS setup</a> ·
  <a href="/privacy/app-tracking-transparency/">Consent, ATT and initialization order</a>
</p>

</Section>
````

- [ ] **Step 2: Style the "next" line**

Append to `docs-site/src/styles/landing.css`:

```css
.quickstart-next {
  margin-block: 1.5rem 0;
  font-family: var(--admob-font-body);
  font-size: 0.9375rem;
  line-height: 1.7;
  color: var(--admob-slate);
}
```

- [ ] **Step 3: Verify the sample compiles against the real API**

Do not trust the sample by eye. Check every symbol against the committed ABI dump:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
for s in rememberAdManager 'ui/BannerAdView' 'AdManagerStatus.Ready' \
         'ConsentController.gatherConsent' 'AdTrackingController.requestAuthorization' \
         'ConsentMode.InitializeOnlyIfAlreadyAllowed'; do
  printf '%-46s ' "$s"
  grep -qF "$s" admob-cmp-core/api/admob-cmp-core.klib.api admob-cmp-compose/api/admob-cmp-compose.klib.api \
    && echo OK || echo MISSING
done
grep -c 'ANDROID_BANNER\|IOS_BANNER\|ANDROID_APP_ID\|IOS_APP_ID' \
  admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/TestAdIds.kt
```

Expected: `OK` on all six lines, and `4` from the final count. Any `MISSING` means the ABI moved — fix the sample, never the assertion.

- [ ] **Step 4: Build and check the rendering**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build
node -e "
const h = require('fs').readFileSync('dist/index.html','utf8');
console.log('code frames:', (h.match(/expressive-code/g) || []).length > 0);
console.log('build.gradle.kts titled:', h.includes('build.gradle.kts'));
console.log('App.kt titled:', h.includes('App.kt'));
console.log('ATT before initialize:', h.indexOf('requestAuthorization') < h.indexOf('InitializeOnlyIfAlreadyAllowed'));
"
```

Expected: all four lines `true`. The last one is the ordering invariant asserted against the rendered page.

- [ ] **Step 5: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/content/docs/index.mdx docs-site/src/styles/landing.css
git commit -m "feat(docs-site): landing quickstart sample with the UMP/ATT/initialize order"
```

---

### Task 5: Neutral capability matrix

Captures `basic-ads alternative` intent with a factual, dated capability table. Per spec §8 this is a capability comparison and **not** a teardown: cells state what each project supports and nothing else, the other project is named once with a link, the rows where it leads are stated plainly, and the whole table carries a verification date and an invitation to correct it. Task 5 adds a test that fails on evaluative language, so the neutrality is enforced rather than merely intended.

**Files:**
- Modify: `docs-site/src/data/landing.ts`
- Create: `docs-site/src/components/landing/CapabilityMatrix.astro`
- Modify: `docs-site/src/content/docs/index.mdx`
- Modify: `docs-site/test/landing.test.ts`

**Interfaces:**
- Consumes: `Section.astro` (Task 1).
- Produces:
  - `export interface CapabilityRow { capability: string; admobCmp: string; basicAds: string }`
  - `export const capabilityRows: readonly CapabilityRow[]` — fourteen rows.
  - `export const capabilityVerifiedOn = '31 July 2026'`
  - `export const capabilityNote: string` — the fairness footnote.
  - `export const basicAdsRepo = 'https://github.com/LexiLabs-App/basic-ads'`
  - `CapabilityMatrix.astro`, no props.

- [ ] **Step 1: Write the failing neutrality test**

Append to `docs-site/test/landing.test.ts`:

```ts
import {
  capabilityRows,
  capabilityNote,
  capabilityVerifiedOn,
  basicAdsRepo,
} from '../src/data/landing';

describe('the capability matrix stays neutral', () => {
  const cells = capabilityRows.flatMap((r) => [r.capability, r.admobCmp, r.basicAds]);

  const evaluative =
    /\b(better|best|worse|worst|superior|inferior|lacking|inadequate|primitive|outdated|abandoned|unmaintained|incomplete|crippled|limited to|merely|unlike|fails? to|can'?t even|no real)\b/i;

  it('has a filled-in comparison for every row', () => {
    expect(capabilityRows.length).toBe(14);
    for (const row of capabilityRows) {
      expect(row.capability.length, JSON.stringify(row)).toBeGreaterThan(3);
      expect(row.admobCmp.length, row.capability).toBeGreaterThan(0);
      expect(row.basicAds.length, row.capability).toBeGreaterThan(0);
    }
  });

  it('uses no evaluative language in any cell', () => {
    for (const cell of cells) {
      expect(evaluative.exec(cell), `evaluative wording: "${cell}"`).toBeNull();
    }
  });

  it('uses no evaluative language in the footnote either', () => {
    expect(evaluative.exec(capabilityNote), capabilityNote).toBeNull();
  });

  it('dates the comparison and credits where the other project leads', () => {
    expect(capabilityVerifiedOn).toBe('31 July 2026');
    expect(capabilityNote).toContain(capabilityVerifiedOn);
    expect(capabilityNote).toContain('105');
    expect(capabilityNote).toMatch(/open an issue/i);
    expect(basicAdsRepo).toBe('https://github.com/LexiLabs-App/basic-ads');
  });

  it('names the other project exactly once outside the link', () => {
    const mentions = capabilityNote.match(/basic-ads/g) ?? [];
    expect(mentions.length).toBe(1);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test
```

Expected: FAIL — `"capabilityRows" is not exported by "src/data/landing.ts"`.

- [ ] **Step 3: Append the matrix data**

Append to `docs-site/src/data/landing.ts`:

```ts
export interface CapabilityRow {
  capability: string;
  admobCmp: string;
  basicAds: string;
}

export const capabilityVerifiedOn = '31 July 2026';
export const basicAdsRepo = 'https://github.com/LexiLabs-App/basic-ads';

/**
 * Capability facts only. No adjectives, no comparisons of quality, no claims about
 * which project suits a given team. Sourced from each project's public repository
 * and published documentation on the date above. test/landing.test.ts fails the
 * build if evaluative language appears in any cell.
 */
export const capabilityRows: readonly CapabilityRow[] = [
  { capability: 'Banner ads', admobCmp: 'Yes', basicAds: 'Yes' },
  { capability: 'Interstitial ads', admobCmp: 'Yes', basicAds: 'Yes' },
  { capability: 'Rewarded ads', admobCmp: 'Yes', basicAds: 'Yes' },
  { capability: 'Rewarded interstitial ads', admobCmp: 'Yes', basicAds: 'Yes' },
  { capability: 'App-open ads', admobCmp: 'Yes', basicAds: 'Not offered' },
  { capability: 'Native ads', admobCmp: 'Yes', basicAds: 'Not offered' },
  {
    capability: 'Native ad layout DSL and pooling',
    admobCmp: 'adLayout {} plus a NativeAdPool with maxSize accounting',
    basicAds: 'Not applicable',
  },
  {
    capability: 'UMP consent inside the initialization flow',
    admobCmp: 'gatherConsentAndInitialize, three ConsentMode strategies, privacy options form',
    basicAds: 'Consent request',
  },
  {
    capability: 'iOS App Tracking Transparency ordering',
    admobCmp: 'adManager.tracking.requestAuthorization() between consent and initialize',
    basicAds: 'Not documented',
  },
  {
    capability: 'Paid and revenue events',
    admobCmp: 'AdEvent.Paid carrying AdValue and ResponseInfo',
    basicAds: 'Not documented',
  },
  {
    capability: 'Mediation adapter hooks',
    admobCmp: 'AdInitializationHook at three initialization points',
    basicAds: 'Not documented',
  },
  {
    capability: 'Kotlin/Native test-executable linking',
    admobCmp: 'Published Gradle plugin (dev.avinya.ads.admob-cmp)',
    basicAds: 'Not addressed',
  },
  { capability: 'Generated API reference', admobCmp: 'Yes', basicAds: 'Yes' },
  { capability: 'Published on Maven Central', admobCmp: 'Yes', basicAds: 'Yes' },
];

export const capabilityNote =
  'Compiled from each project’s public repository and published documentation on 31 July 2026. ' +
  '“Not documented” means a capability is not described in that project’s published documentation, ' +
  'which is a statement about the documentation and not about the code. ' +
  'basic-ads is the older project and the larger one by community size — 105 GitHub stars on that date — ' +
  'and published a generated API reference before this project did. ' +
  'If a row here is out of date, open an issue and it will be corrected.';
```

- [ ] **Step 4: Write the component**

Create `docs-site/src/components/landing/CapabilityMatrix.astro`:

```astro
---
import '../../styles/landing.css';
import { capabilityRows, capabilityNote, basicAdsRepo, library } from '../../data/landing';
---

<div class="matrix-scroll" tabindex="0" role="region" aria-label="Capability comparison table">
  <table class="matrix">
    <thead>
      <tr>
        <th scope="col">Capability</th>
        <th scope="col">admob-cmp {library.version}</th>
        <th scope="col">
          <a href={basicAdsRepo} rel="noopener">basic-ads</a>
        </th>
      </tr>
    </thead>
    <tbody>
      {capabilityRows.map((row) => (
        <tr>
          <th scope="row">{row.capability}</th>
          <td>{row.admobCmp}</td>
          <td>{row.basicAds}</td>
        </tr>
      ))}
    </tbody>
  </table>
</div>

<p class="matrix-note">{capabilityNote}</p>

<style>
  /* Plan 2's `npm run check:overflow` fails the build if anything overflows the
     document at 375 px, so the table scrolls inside its own region. tabindex="0"
     makes that scroll container keyboard-reachable, which the a11y audit requires. */
  .matrix-scroll {
    overflow-x: auto;
    border: var(--admob-border);
    border-radius: var(--admob-radius-lg);
    background: var(--admob-surface);
  }

  .matrix-scroll:focus-visible {
    outline: 2px solid var(--admob-accent);
    outline-offset: 2px;
  }

  .matrix {
    inline-size: 100%;
    min-inline-size: 40rem;
    border-collapse: collapse;
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
  }

  .matrix th,
  .matrix td {
    padding: 0.75rem 1rem;
    text-align: start;
    vertical-align: top;
    border-block-end: var(--admob-border);
  }

  .matrix thead th {
    font-family: var(--admob-font-display);
    font-size: 0.875rem;
    letter-spacing: var(--admob-tracking-tight);
    color: var(--admob-ink);
    white-space: nowrap;
  }

  .matrix tbody th {
    font-weight: 600;
    color: var(--admob-ink);
    inline-size: 32%;
  }

  .matrix tbody td {
    color: var(--admob-slate);
  }

  .matrix tbody tr:last-child th,
  .matrix tbody tr:last-child td {
    border-block-end: 0;
  }

  .matrix-note {
    margin-block: 1.25rem 0;
    max-inline-size: var(--landing-head-max);
    font-family: var(--admob-font-body);
    font-size: 0.875rem;
    line-height: 1.65;
    color: var(--admob-slate);
  }
</style>
```

- [ ] **Step 5: Render the section**

Add the import to `index.mdx` and append the section after `quickstart`:

```mdx
import CapabilityMatrix from '../../components/landing/CapabilityMatrix.astro';
```

```mdx
<Section
  id="compare"
  eyebrow="Capability matrix"
  heading="What each Compose Multiplatform AdMob library supports"
  lead="Two Kotlin Multiplatform libraries wrap the Google Mobile Ads SDKs for Compose Multiplatform. This is a capability comparison and nothing more — no benchmarks, and no claim about which one suits your project."
>
  <CapabilityMatrix />
</Section>
```

- [ ] **Step 6: Run the tests and the build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build && npm run check:overflow
```

Expected: every capability case PASSES, the build finishes with `Complete!`, and `check:overflow` reports no horizontal overflow at 375 px — the table scrolls inside `.matrix-scroll` rather than widening the document.

- [ ] **Step 7: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/data/landing.ts docs-site/test/landing.test.ts \
        docs-site/src/components/landing/CapabilityMatrix.astro \
        docs-site/src/content/docs/index.mdx
git commit -m "feat(docs-site): neutral, dated capability matrix on the landing page"
```

---

### Task 6: "Why this exists" differentiators

Five cards, each naming a real API and linking to the guide that explains it. Every sentence is traceable to `admob-cmp/README.md` or `admob-cmp/AGENTS.md`. The framing is self-descriptive, not comparative — the comparison already happened in Task 5 and does not need repeating. The section closes with Plan 4's `InitSequence` diagram, because the consent card describes an ordering that is far easier to see than to read.

**Files:**
- Modify: `docs-site/src/data/landing.ts`
- Create: `docs-site/src/components/landing/DifferentiatorGrid.astro`
- Modify: `docs-site/src/styles/landing.css`
- Modify: `docs-site/src/content/docs/index.mdx`
- Modify: `docs-site/test/landing.test.ts`

**Interfaces:**
- Consumes: `Section.astro` (Task 1); `InitSequence.astro` from `docs-site/src/components/diagrams/` (Plan 4, Task 3) — no props, self-captioned, emits build-time static SVG with its own `role="img"` / `aria-labelledby` contract.
- Produces:
  - `export interface Differentiator { title: string; body: string; code: string; href: string; linkText: string }`
  - `export const differentiators: readonly Differentiator[]` — exactly five.
  - `DifferentiatorGrid.astro`, no props.

- [ ] **Step 1: Write the failing test**

Append to `docs-site/test/landing.test.ts`:

```ts
import { differentiators } from '../src/data/landing';

describe('the differentiator cards', () => {
  it('covers the five points the design calls out', () => {
    expect(differentiators).toHaveLength(5);
    const titles = differentiators.map((d) => d.title.toLowerCase()).join(' | ');
    for (const topic of ['native', 'app-open', 'consent', 'gradle plugin', 'abi']) {
      expect(titles, `no card covers ${topic}`).toContain(topic);
    }
  });

  it('links each card into the docs with a directory-style URL and shows real code', () => {
    for (const d of differentiators) {
      expect(d.href.startsWith('/'), d.title).toBe(true);
      expect(d.href.endsWith('/'), `${d.title}: ${d.href}`).toBe(true);
      expect(d.linkText.length, d.title).toBeGreaterThan(4);
      expect(d.code.length, d.title).toBeGreaterThan(10);
      expect(d.body.length, d.title).toBeGreaterThan(120);
    }
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test
```

Expected: FAIL — `"differentiators" is not exported by "src/data/landing.ts"`.

- [ ] **Step 3: Append the data**

Append to `docs-site/src/data/landing.ts`:

```ts
export interface Differentiator {
  title: string;
  body: string;
  /** One line of real API, shown as inline code. */
  code: string;
  href: string;
  linkText: string;
}

export const differentiators: readonly Differentiator[] = [
  {
    title: 'Native ads, composed rather than templated',
    body:
      'The adLayout {} DSL builds the ad out of headline, body, icon, media, advertiser, ad-badge and call-to-action nodes, so a native ad can match your own design system. A pool budgets available and in-use ads against maxSize, and pool.availableAds is the signal that lets a feed row recover when the pool is momentarily empty instead of staying blank forever.',
    code: 'adLayout { column { media(); headline(maxLines = 2); callToAction() } }',
    href: '/formats/native/',
    linkText: 'Native ads guide',
  },
  {
    title: 'App-open ads that know when to stay quiet',
    body:
      'AppOpenAdCoordinator watches foreground transitions, enforces a minimum background duration and a cooldown between shows, and exposes isBlocked so you can suppress it during a purchase, during onboarding, or while another full-screen ad is up. The lifecycle work that makes this format behave sits outside the ad SDK itself.',
    code: 'AppOpenConfig(minBackgroundDuration = 4.seconds, cooldownBetweenShows = 4.hours)',
    href: '/formats/app-open/',
    linkText: 'App-open guide',
  },
  {
    title: 'UMP consent inside the initialization flow',
    body:
      'Consent is part of initialization, with three strategies: gather first, initialize only if consent already allows it, or skip. On iOS the ATT prompt belongs between consent and initialize, because requesting ads before ATT resolves permanently forfeits the IDFA for those requests. The privacy-options button is gated on PrivacyOptionsRequirementStatus.Required, not on consent being obtained.',
    code: 'adManager.initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)',
    href: '/privacy/consent/',
    linkText: 'Consent guide',
  },
  {
    title: 'A Gradle plugin that fixes Kotlin/Native test linking',
    body:
      'Run :yourModule:iosSimulatorArm64Test against an AdMob integration and the link fails with Undefined symbols … _OBJC_CLASS_$_GAD*, because the Google Mobile Ads frameworks arrive through Swift Package Manager inside Xcode and a Kotlin/Native test executable never sees them. Applying the published plugin downloads the version-stamped XCFrameworks and wires them into that link.',
    code: 'id("dev.avinya.ads.admob-cmp") version "1.1.0"',
    href: '/reference/troubleshooting/',
    linkText: 'Troubleshooting',
  },
  {
    title: 'A public ABI that is frozen on purpose',
    body:
      'The module is built with explicit API mode and Kotlin ABI validation. The API dumps under api/ are committed to the repository, and checkKotlinAbi fails the build on any unintended change to the public surface, so upgrading inside 1.x cannot quietly move something you depend on. Changes to the public API are additive.',
    code: './gradlew :admob-cmp:checkKotlinAbi',
    href: '/reference/architecture/',
    linkText: 'Architecture',
  },
];
```

- [ ] **Step 4: Write the component**

Create `docs-site/src/components/landing/DifferentiatorGrid.astro`:

```astro
---
import '../../styles/landing.css';
import { differentiators } from '../../data/landing';
---

<ul class="why-grid">
  {differentiators.map((d) => (
    <li class="why-card">
      <h3 class="why-title">{d.title}</h3>
      <p class="why-body">{d.body}</p>
      <code class="why-code">{d.code}</code>
      <a class="why-link" href={d.href}>{d.linkText}<span aria-hidden="true"> &rarr;</span></a>
    </li>
  ))}
</ul>

<style>
  .why-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: var(--landing-card-gap);
    margin: 0;
    padding: 0;
    list-style: none;
  }

  @media (min-width: 48rem) {
    .why-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    /* Five cards into two columns: the last one spans both rather than leaving a hole. */
    .why-card:last-child {
      grid-column: 1 / -1;
    }
  }

  .why-card {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
    padding: 1.5rem;
    background: var(--admob-surface);
    border: var(--admob-border);
    border-radius: var(--admob-radius-lg);
  }

  .why-title {
    margin: 0;
    font-family: var(--admob-font-display);
    font-size: 1.125rem;
    line-height: 1.25;
    letter-spacing: var(--admob-tracking-tight);
    color: var(--admob-ink);
  }

  .why-body {
    margin: 0;
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
    line-height: 1.6;
    color: var(--admob-slate);
  }

  .why-code {
    max-inline-size: 100%;
    padding: 0.4rem 0.6rem;
    border-radius: var(--admob-radius);
    background: var(--admob-code);
    border: var(--admob-border);
    font-family: var(--admob-font-mono);
    font-size: 0.75rem;
    line-height: 1.5;
    color: var(--admob-ink);
    overflow-wrap: anywhere;
  }

  .why-link {
    display: inline-flex;
    align-items: center;
    min-block-size: 1.5rem; /* 24px — WCAG 2.2 Target Size (Minimum) */
    margin-block-start: auto;
    padding-block-start: 0.25rem;
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
    color: var(--admob-ink);
    text-decoration-color: var(--admob-accent);
    text-underline-offset: 0.25em;
  }
</style>
```

- [ ] **Step 5: Render the section with the init-sequence diagram**

Add both imports to `index.mdx`:

```mdx
import DifferentiatorGrid from '../../components/landing/DifferentiatorGrid.astro';
import InitSequence from '../../components/diagrams/InitSequence.astro';
```

Append the section after `compare`:

```mdx
<Section
  id="why"
  eyebrow="Why this exists"
  heading="Where reconciling two ad SDKs took the real work"
  lead="AdMob's Android and iOS SDKs have different shapes, different lifecycles and different gaps. These are the five places where closing that distance was the hard part."
>
  <DifferentiatorGrid />

  <div class="why-diagram">
    <InitSequence />
  </div>
</Section>
```

Append to `docs-site/src/styles/landing.css`:

```css
.why-diagram {
  margin-block-start: 2.5rem;
}
```

- [ ] **Step 6: Run the tests and the build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build && npm run check:overflow
```

Expected: PASS, `Complete!`, and no overflow at 375 px. The heading order on the page is now H1 → H2 → H3 with no level skipped, which Task 10's accessibility audit checks.

If the build fails with `Cannot find module '../../components/diagrams/InitSequence.astro'`, Plan 4 has not been merged. Merge it — do not substitute a hand-drawn diagram here, because Plan 4 owns the diagram accessibility contract (`role="img"`, `aria-labelledby` pointing at an in-SVG `<title>` and `<desc>`, a visible caption, and a link to the prose equivalent on `/reference/diagrams-in-words/`).

- [ ] **Step 7: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/data/landing.ts docs-site/test/landing.test.ts \
        docs-site/src/components/landing/DifferentiatorGrid.astro \
        docs-site/src/styles/landing.css docs-site/src/content/docs/index.mdx
git commit -m "feat(docs-site): landing differentiator cards and init-sequence diagram"
```

---

### Task 7: Compatibility strip

Four version facts read straight out of `library`, Plan 4's `PlatformMatrix` for per-platform support, and the three caveats that stop this page overselling: the klib binary-compatibility limit, the fact that Compose Multiplatform is optional, and the fact that a pure-Swift app cannot consume the SDK at all. Putting the limits on the landing page rather than three clicks deep costs a little conversion and saves every wrong-fit integrator a wasted afternoon.

**Files:**
- Modify: `docs-site/src/data/landing.ts`
- Create: `docs-site/src/components/landing/CompatibilityStrip.astro`
- Modify: `docs-site/src/styles/landing.css`
- Modify: `docs-site/src/content/docs/index.mdx`
- Modify: `docs-site/test/landing.test.ts`

**Interfaces:**
- Consumes: `library` and `Section.astro` (Task 1); `PlatformMatrix.astro` from `docs-site/src/components/diagrams/` (Plan 4, Task 9) — no props, ships as a semantic `<table>` rather than SVG, and documents the Android native-video-events upstream gap.
- Produces:
  - `export interface CompatibilityFact { label: string; value: string }`
  - `export const compatibilityFacts: readonly CompatibilityFact[]` — four, each derived from `library`, never retyped.
  - `export const compatibilityCaveats: readonly string[]` — three.
  - `CompatibilityStrip.astro`, no props.

- [ ] **Step 1: Write the failing test**

Append to `docs-site/test/landing.test.ts`:

```ts
import { compatibilityFacts, compatibilityCaveats } from '../src/data/landing';

describe('the compatibility strip', () => {
  it('states the four version facts, sourced from the library constants', () => {
    const values = Object.fromEntries(compatibilityFacts.map((f) => [f.label, f.value]));
    expect(values['Kotlin']).toBe(library.kotlin);
    expect(values['Compose Multiplatform']).toBe(library.composeMultiplatform);
    expect(values['Android minSdk']).toBe(library.androidMinSdk);
    expect(values['iOS deployment target']).toBe(library.iosDeploymentTarget);
    expect(compatibilityFacts).toHaveLength(4);
  });

  it('carries the klib compatibility caveat honestly', () => {
    expect(compatibilityCaveats).toHaveLength(3);
    const klib = compatibilityCaveats.find((c) => c.includes('klib'));
    expect(klib, 'no caveat mentions klibs').toBeTruthy();
    expect(klib).toContain('not binary-compatible');
    expect(klib).toMatch(/cinterop/);
    expect(klib).toMatch(/outside Kotlin's stable binary-compatibility guarantee/);
    expect(klib).toContain(library.kotlin);
  });

  it('says Compose Multiplatform is optional and that Swift-only apps cannot consume it', () => {
    const joined = compatibilityCaveats.join(' ');
    expect(joined).toMatch(/controller API has no Compose dependency/i);
    expect(joined).toMatch(/pure-Swift/i);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test
```

Expected: FAIL — `"compatibilityFacts" is not exported by "src/data/landing.ts"`.

- [ ] **Step 3: Append the data**

Append to `docs-site/src/data/landing.ts`:

```ts
export interface CompatibilityFact {
  label: string;
  value: string;
}

/** Derived from `library`, never retyped — test/landing.test.ts asserts the link. */
export const compatibilityFacts: readonly CompatibilityFact[] = [
  { label: 'Kotlin', value: library.kotlin },
  { label: 'Compose Multiplatform', value: library.composeMultiplatform },
  { label: 'Android minSdk', value: library.androidMinSdk },
  { label: 'iOS deployment target', value: library.iosDeploymentTarget },
];

export const compatibilityCaveats: readonly string[] = [
  `admob-cmp ships Kotlin/Native klibs, including cinterop klibs generated against the Google Mobile Ads and UMP headers. Klibs are not binary-compatible across arbitrary Kotlin versions, and cinterop klibs in particular sit outside Kotlin's stable binary-compatibility guarantee. The module is compiled with Kotlin ${library.kotlin}; a different Kotlin minor version may fail to resolve the klib, while patch versions are generally safe.`,
  'Compose Multiplatform is required only for the composable surface — BannerAdView, NativeAdView and rememberAdManager. The controller API has no Compose dependency.',
  'The SDK is consumable from Kotlin Multiplatform Gradle projects only: it compiles into your umbrella framework. A pure-Swift iOS app cannot adopt it without a Kotlin Multiplatform shim.',
];
```

- [ ] **Step 4: Write the component**

Create `docs-site/src/components/landing/CompatibilityStrip.astro`:

```astro
---
import '../../styles/landing.css';
import { compatibilityFacts, compatibilityCaveats, library } from '../../data/landing';
---

<dl class="compat-strip">
  {compatibilityFacts.map((fact) => (
    <div class="compat-item">
      <dt>{fact.label}</dt>
      <dd>{fact.value}</dd>
    </div>
  ))}
</dl>

<ul class="compat-caveats">
  {compatibilityCaveats.map((caveat) => <li>{caveat}</li>)}
</ul>

<p class="compat-more">
  Full matrix, including every published version:
  <a href="/reference/compatibility/">Compatibility reference</a>. Current release
  <code>{library.version}</code>.
</p>

<style>
  .compat-strip {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
    /* 1px of hairline showing through the gap draws the dividers without borders
       that would double up at the grid edges. */
    gap: 1px;
    margin: 0;
    padding: 0;
    background: var(--admob-hair);
    border: var(--admob-border);
    border-radius: var(--admob-radius-lg);
    overflow: hidden;
  }

  .compat-item {
    padding: 1.25rem;
    background: var(--admob-surface);
  }

  .compat-item dt {
    font-family: var(--admob-font-body);
    font-size: 0.8125rem;
    line-height: 1.3;
    color: var(--admob-slate);
  }

  .compat-item dd {
    margin: 0.35rem 0 0;
    font-family: var(--admob-font-display);
    font-size: 1.375rem;
    line-height: 1.15;
    letter-spacing: var(--admob-tracking-tight);
    color: var(--admob-ink);
  }

  .compat-caveats {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    margin-block: 1.5rem 0;
    padding-inline-start: 1.1rem;
    max-inline-size: var(--landing-head-max);
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
    line-height: 1.65;
    color: var(--admob-slate);
  }

  .compat-caveats li::marker {
    color: var(--admob-accent);
  }

  .compat-more {
    margin-block: 1.25rem 0;
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
    color: var(--admob-slate);
  }

  .compat-more code {
    font-family: var(--admob-font-mono);
    font-size: 0.875rem;
    color: var(--admob-ink);
  }
</style>
```

- [ ] **Step 5: Render the section with the platform matrix**

Add both imports to `index.mdx`:

```mdx
import CompatibilityStrip from '../../components/landing/CompatibilityStrip.astro';
import PlatformMatrix from '../../components/diagrams/PlatformMatrix.astro';
```

Append the section after `why`:

```mdx
<Section
  id="compatibility"
  eyebrow="Compatibility"
  heading="What you need to be running"
  lead="And the three limits worth knowing before you start, rather than after."
  panel
>
  <CompatibilityStrip />

  <div class="compat-diagram">
    <PlatformMatrix />
  </div>
</Section>
```

Append to `docs-site/src/styles/landing.css`:

```css
.compat-diagram {
  margin-block-start: 2.5rem;
}
```

- [ ] **Step 6: Run the tests and the build**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build && npm run check:overflow
```

Expected: PASS, `Complete!`, and no overflow at 375 px — `auto-fit` collapses the strip to a single column, and Plan 4's `PlatformMatrix` supplies its own scroll container.

- [ ] **Step 7: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/data/landing.ts docs-site/test/landing.test.ts \
        docs-site/src/components/landing/CompatibilityStrip.astro \
        docs-site/src/styles/landing.css docs-site/src/content/docs/index.mdx
git commit -m "feat(docs-site): landing compatibility strip with the klib caveat"
```

---

### Task 8: Roadmap teaser, final CTA and footer

Two sections and the page's closing furniture. The roadmap teaser publishes the two open items **with their real gates and no dates**, per spec §8 — transparency is itself a differentiator here, and the section captures `kotlin multiplatform swiftpm`. The footer carries the trademark line verbatim.

**Files:**
- Modify: `docs-site/src/data/landing.ts`
- Create: `docs-site/src/components/landing/RoadmapTeaser.astro`
- Create: `docs-site/src/components/landing/FinalCta.astro`
- Modify: `docs-site/src/content/docs/index.mdx`
- Modify: `docs-site/test/landing.test.ts`

**Interfaces:**
- Consumes: `Section.astro`, `library` and `coordinate` (Task 1).
- Produces:
  - `export interface RoadmapItem { title: string; status: string; body: string }`
  - `export const roadmapItems: readonly RoadmapItem[]` — exactly two.
  - `export interface FooterLink { text: string; href: string }`
  - `export interface FooterColumn { heading: string; links: readonly FooterLink[] }`
  - `export const footerColumns: readonly FooterColumn[]` — five.
  - `RoadmapTeaser.astro` and `FinalCta.astro`, neither taking props. `FinalCta.astro` renders its own `<Section>` plus a sibling `<footer class="landing-footer">`.

- [ ] **Step 1: Write the failing test**

Append to `docs-site/test/landing.test.ts`:

```ts
import { roadmapItems, footerColumns } from '../src/data/landing';

describe('the roadmap teaser', () => {
  it('publishes both open items with their gates', () => {
    expect(roadmapItems).toHaveLength(2);
    for (const item of roadmapItems) {
      expect(item.title.length, item.title).toBeGreaterThan(8);
      expect(item.status.length, item.title).toBeGreaterThan(5);
      expect(item.body.length, item.title).toBeGreaterThan(120);
    }
  });

  it('covers SwiftPM import and the Android native-video-events gap', () => {
    const joined = roadmapItems.map((i) => `${i.title} ${i.body}`).join(' ');
    expect(joined).toMatch(/swiftPMDependencies/);
    expect(joined).toMatch(/GADVideoControllerDelegate/);
  });

  it('promises no date and no release', () => {
    const promissory =
      /\b(coming soon|Q[1-4]\s*20\d{2}|by (?:the end of )?20\d{2}|next (?:month|quarter|release)|will ship|shipping in|ETA)\b/i;
    for (const item of roadmapItems) {
      const text = `${item.title} ${item.status} ${item.body}`;
      expect(promissory.exec(text), `promissory language: "${text}"`).toBeNull();
    }
  });
});

describe('the footer', () => {
  it('groups the site into five columns of working links', () => {
    expect(footerColumns).toHaveLength(5);
    for (const column of footerColumns) {
      expect(column.heading.length, column.heading).toBeGreaterThan(3);
      expect(column.links.length, column.heading).toBeGreaterThan(2);
      for (const link of column.links) {
        if (link.href.startsWith('/')) {
          expect(link.href.endsWith('/'), `${link.text}: ${link.href}`).toBe(true);
        } else {
          expect(link.href.startsWith('https://'), `${link.text}: ${link.href}`).toBe(true);
        }
      }
    }
  });

  it('links the roadmap, the generated API reference and the repository', () => {
    const hrefs = footerColumns.flatMap((c) => c.links.map((l) => l.href));
    expect(hrefs).toContain('/project/roadmap/');
    expect(hrefs).toContain('/api/');
    expect(hrefs).toContain(library.repo);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test
```

Expected: FAIL — `"roadmapItems" is not exported by "src/data/landing.ts"`.

- [ ] **Step 3: Append the data**

Append to `docs-site/src/data/landing.ts`:

```ts
export interface RoadmapItem {
  title: string;
  /** Where the item actually stands. Never a date. */
  status: string;
  body: string;
}

/**
 * Published with the real gates, per the design's framing decision. A roadmap that
 * reads as a promise is worse than no roadmap, so no item carries a date and every
 * item names what has to land first.
 */
export const roadmapItems: readonly RoadmapItem[] = [
  {
    title: 'Swift Package Manager dependency import',
    status: 'Gated on four unmet upstream conditions',
    body:
      "JetBrains' swiftPMDependencies import would let the SDK declare its own Google Mobile Ads and UMP dependencies, instead of asking you to add two Swift packages to the Xcode project by hand. Four conditions are unmet, and one of them is an open unknown: whether a Maven-published library propagates SwiftPM linkage to its own consumers at all. A published SDK is not going to depend on an Alpha build-tool feature, so this carries no date.",
  },
  {
    title: 'Native video events on Android',
    status: 'Blocked on the upstream SDK',
    body:
      'iOS emits five native video events — VideoStarted, VideoPlayed, VideoPaused, VideoEnded and VideoMuted — through GADVideoControllerDelegate. The Android Google Mobile Ads Next-Gen SDK exposes no equivalent callback surface on NativeAd, so Android emits none. This is a gap in the upstream SDK rather than an omission here, and it is documented instead of papered over: do not rely on native video events for cross-platform logic.',
  },
];

export interface FooterLink {
  text: string;
  href: string;
}

export interface FooterColumn {
  heading: string;
  links: readonly FooterLink[];
}

export const footerColumns: readonly FooterColumn[] = [
  {
    heading: 'Get started',
    links: [
      { text: 'What is AdMob CMP', href: '/start/what-is-admob-cmp/' },
      { text: 'Quickstart', href: '/start/quickstart/' },
      { text: 'Installation', href: '/start/installation/' },
      { text: 'Android setup', href: '/start/android-setup/' },
      { text: 'iOS setup', href: '/start/ios-setup/' },
    ],
  },
  {
    heading: 'Ad formats',
    links: [
      { text: 'Banner', href: '/formats/banner/' },
      { text: 'Interstitial', href: '/formats/interstitial/' },
      { text: 'Rewarded', href: '/formats/rewarded/' },
      { text: 'App-open', href: '/formats/app-open/' },
      { text: 'Native', href: '/formats/native/' },
    ],
  },
  {
    heading: 'Privacy',
    links: [
      { text: 'UMP consent', href: '/privacy/consent/' },
      { text: 'App Tracking Transparency', href: '/privacy/app-tracking-transparency/' },
      { text: 'Play Data safety', href: '/privacy/play-data-safety/' },
    ],
  },
  {
    heading: 'Reference',
    links: [
      { text: 'Architecture', href: '/reference/architecture/' },
      { text: 'Compatibility', href: '/reference/compatibility/' },
      { text: 'Troubleshooting', href: '/reference/troubleshooting/' },
      { text: 'Changelog', href: '/reference/changelog/' },
      { text: 'API reference', href: '/api/' },
    ],
  },
  {
    heading: 'Project',
    links: [
      { text: 'Roadmap', href: '/project/roadmap/' },
      { text: 'Contributing', href: '/project/contributing/' },
      { text: 'Using with AI agents', href: '/project/ai-agents/' },
      { text: 'GitHub', href: library.repo },
      { text: 'Maven Central', href: library.mavenCentral },
    ],
  },
];
```

- [ ] **Step 4: Write the roadmap teaser**

Create `docs-site/src/components/landing/RoadmapTeaser.astro`:

```astro
---
import '../../styles/landing.css';
import { roadmapItems } from '../../data/landing';
---

<ul class="roadmap-list">
  {roadmapItems.map((item) => (
    <li class="roadmap-item">
      <div class="roadmap-head">
        <h3 class="roadmap-title">{item.title}</h3>
        <span class="roadmap-status">{item.status}</span>
      </div>
      <p class="roadmap-body">{item.body}</p>
    </li>
  ))}
</ul>

<p class="roadmap-more">
  <a href="/project/roadmap/">Full roadmap, with every gate spelled out<span aria-hidden="true"> &rarr;</span></a>
</p>

<style>
  .roadmap-list {
    display: flex;
    flex-direction: column;
    gap: var(--landing-card-gap);
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .roadmap-item {
    padding: 1.5rem;
    background: var(--admob-surface);
    border: var(--admob-border);
    border-radius: var(--admob-radius-lg);
  }

  .roadmap-head {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 0.5rem 0.875rem;
    margin-block-end: 0.75rem;
  }

  .roadmap-title {
    margin: 0;
    font-family: var(--admob-font-display);
    font-size: 1.125rem;
    line-height: 1.25;
    letter-spacing: var(--admob-tracking-tight);
    color: var(--admob-ink);
  }

  .roadmap-status {
    padding: 0.2rem 0.55rem;
    border-radius: var(--admob-radius);
    background: var(--admob-accent-soft);
    border: var(--admob-border);
    font-family: var(--admob-font-mono);
    font-size: 0.6875rem;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: var(--admob-ink);
    white-space: nowrap;
  }

  .roadmap-body {
    margin: 0;
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
    line-height: 1.65;
    color: var(--admob-slate);
  }

  .roadmap-more {
    margin-block: 1.5rem 0;
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
  }

  .roadmap-more a {
    display: inline-flex;
    align-items: center;
    min-block-size: 1.5rem; /* 24px — WCAG 2.2 Target Size (Minimum) */
    color: var(--admob-ink);
    text-decoration-color: var(--admob-accent);
    text-underline-offset: 0.25em;
  }

  @media (max-width: 30rem) {
    .roadmap-status {
      white-space: normal;
    }
  }
</style>
```

- [ ] **Step 5: Write the final CTA and footer**

Create `docs-site/src/components/landing/FinalCta.astro`:

```astro
---
import Section from './Section.astro';
import { library, coordinate, footerColumns } from '../../data/landing';
---

<Section
  id="get-started"
  eyebrow="Get started"
  heading="Ship ads in your Compose Multiplatform app"
  lead="Five minutes from an empty build file to a rendering test ad. Google's sample app IDs and test ad units work without an AdMob account, so you can try the whole flow before signing up for anything."
  panel
>
  <div class="cta-actions">
    <a class="cta cta-primary" href="/start/quickstart/">Start the quickstart</a>
    <a class="cta cta-secondary" href="/start/what-is-admob-cmp/">What it is, and what it is not</a>
    <a class="cta cta-secondary" href={library.repo} rel="noopener">GitHub</a>
  </div>
  <p class="cta-coordinate"><code>{coordinate}</code></p>
</Section>

<footer class="landing-footer">
  <nav class="footer-columns" aria-label="Site">
    {footerColumns.map((column) => (
      <div class="footer-column">
        <h2 class="footer-heading">{column.heading}</h2>
        <ul>
          {column.links.map((link) => (
            <li>
              <a href={link.href} rel={link.href.startsWith('/') ? undefined : 'noopener'}>
                {link.text}
              </a>
            </li>
          ))}
        </ul>
      </div>
    ))}
  </nav>

  <div class="footer-legal">
    <p class="footer-trademark">{library.trademark}</p>
    <p class="footer-licence">
      <a href={library.licenceUrl} rel="noopener">{library.licence} licensed</a>.
      &copy; 2025&ndash;2026 Meet Miyani. Brand <strong>{library.brand}</strong>,
      repository <code>admob-compose-multiplatform</code>, Maven coordinate
      <code>{library.groupId}:{library.artifactId}</code>.
    </p>
  </div>
</footer>

<style>
  .cta-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .cta {
    display: inline-flex;
    align-items: center;
    min-block-size: 2.75rem; /* 44px touch target */
    padding: 0.65rem 1.15rem;
    border-radius: var(--admob-radius);
    font-family: var(--admob-font-body);
    font-size: 0.9375rem;
    line-height: 1.2;
    text-decoration: none;
  }

  .cta-primary {
    background: var(--admob-accent);
    color: var(--admob-accent-contrast);
    border: 1px solid transparent;
  }

  .cta-secondary {
    background: var(--admob-paper);
    color: var(--admob-ink);
    border: var(--admob-border);
  }

  .cta:focus-visible {
    outline: 2px solid var(--admob-accent);
    outline-offset: 2px;
  }

  .cta-coordinate {
    margin-block: 1.25rem 0;
  }

  .cta-coordinate code {
    padding: 0.35rem 0.7rem;
    border-radius: var(--admob-radius);
    background: var(--admob-code);
    border: var(--admob-border);
    font-family: var(--admob-font-mono);
    font-size: 0.875rem;
    color: var(--admob-ink);
    user-select: all;
    overflow-wrap: anywhere;
  }

  .landing-footer {
    margin-block-start: var(--landing-section-gap-sm);
    padding-block-start: 2.5rem;
    border-block-start: var(--admob-border);
  }

  .footer-columns {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
    gap: 2rem 1.5rem;
  }

  .footer-heading {
    margin: 0 0 0.75rem;
    font-family: var(--admob-font-display);
    font-size: 0.875rem;
    letter-spacing: var(--admob-tracking-tight);
    color: var(--admob-ink);
  }

  .footer-column ul {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .footer-column a {
    font-family: var(--admob-font-body);
    font-size: 0.875rem;
    line-height: 1.4;
    color: var(--admob-slate);
    text-decoration: none;
  }

  .footer-column a:hover,
  .footer-column a:focus-visible {
    color: var(--admob-ink);
    text-decoration: underline;
    text-underline-offset: 0.25em;
  }

  .footer-legal {
    display: flex;
    flex-direction: column;
    gap: 0.6rem;
    margin-block-start: 2.5rem;
    padding-block: 1.5rem 0;
    border-block-start: var(--admob-border);
    font-family: var(--admob-font-body);
    font-size: 0.8125rem;
    line-height: 1.6;
    color: var(--admob-slate);
  }

  .footer-legal p {
    margin: 0;
    max-inline-size: 52rem;
  }

  .footer-trademark {
    color: var(--admob-ink);
  }

  .footer-legal code {
    font-family: var(--admob-font-mono);
    font-size: 0.75rem;
  }
</style>
```

> **Two deliberate choices in this file.** The footer column headings are `<h2>`, not `<h3>`: they sit inside `<footer>`, which is a landmark rather than a subsection of the CTA's `<h2>`, so `<h3>` would imply nesting that does not exist — and `h2` after `h2` never skips a level. Separately, `.footer-column a` is left at its natural ~20 px height here **on purpose**: Task 9 measures it, fails on it, and fixes it, so the WCAG 2.2 target-size rule gets a real red-to-green cycle instead of an unverified assertion.

- [ ] **Step 6: Render both sections**

Add the imports to `index.mdx`:

```mdx
import RoadmapTeaser from '../../components/landing/RoadmapTeaser.astro';
import FinalCta from '../../components/landing/FinalCta.astro';
```

Append after the `compatibility` section. `FinalCta` renders its own `<Section>`, so it is not wrapped:

```mdx
<Section
  id="roadmap"
  eyebrow="Roadmap"
  heading="What is next, and what is blocking it"
  lead="Published with its real gates. Two items are open, both are waiting on something upstream, and neither has a date."
>
  <RoadmapTeaser />
</Section>

<FinalCta />
```

- [ ] **Step 7: Run the tests, build, and assert the trademark line**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build && npm run check:overflow
node -e "
const h = require('fs').readFileSync('dist/index.html','utf8');
const tm = 'Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.';
console.log('trademark verbatim:', h.includes(tm));
console.log('sections:', ['formats','quickstart','compare','why','compatibility','roadmap','get-started']
  .map(id => id + '=' + h.includes('id=\"' + id + '\"')).join(' '));
console.log('h1:', (h.match(/<h1[ >]/g) || []).length, 'h2:', (h.match(/<h2[ >]/g) || []).length);
"
```

Expected: `trademark verbatim: true`, every section id `true`, `h1: 1`, and `h2` at least 12 — seven section headings plus five footer column headings.

If `trademark verbatim: false`, something smart-quoted the string. `library.trademark` deliberately contains no apostrophes or dashes that a typographic transform would touch, so the cause is an edit rather than the renderer. Restore it character for character from the Global Constraints block at the top of this plan.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/data/landing.ts docs-site/test/landing.test.ts \
        docs-site/src/components/landing/RoadmapTeaser.astro \
        docs-site/src/components/landing/FinalCta.astro \
        docs-site/src/content/docs/index.mdx
git commit -m "feat(docs-site): landing roadmap teaser, final CTA and footer"
```

---

### Task 9: Responsive behaviour, theming, and the Plan 7 screenshot retrofit

Plan 2's `npm run check:overflow` already covers 375 px in both themes for every page. This task adds a landing-specific check at all three target widths that also asserts what overflow cannot see: that the grid really reflows 1 → 2 → 3 columns, that targets are big enough to hit, and that the token layer genuinely switches between themes rather than baking one palette into the build. Step 5 then performs the Plan 7 retrofit and re-runs the whole check with real images in the stages.

**Files:**
- Create: `docs-site/scripts/check-landing-responsive.mjs`
- Modify: `docs-site/package.json` (add the `check:landing` script)
- Modify: `docs-site/src/components/landing/FinalCta.astro` (target-size fix, Step 3)
- Modify: `docs-site/src/data/landing.ts` (screenshot retrofit, Step 5)

**Interfaces:**
- Consumes: `playwright` 1.62.1 and the Chromium binary, both already present from Plan 2 Task 7; the preview server on `http://localhost:4321`.
- Produces: `npm run check:landing` — exits non-zero with a per-viewport, per-theme report. This is a standing gate, not a one-off.

**Target viewports and expected column counts:**

| Width | Class | `.format-grid` | `.why-grid` | `.footer-columns` |
|---|---|---|---|---|
| 375 px | phone | 1 | 1 | 1 (auto-fit) |
| 768 px | tablet | 2 | 2 | 2–3 (auto-fit) |
| 1280 px | desktop | 3 | 2 | 5 (auto-fit) |

Only `.format-grid` is asserted mechanically — it has fixed `repeat(N, …)` breakpoints, so its column count is a hard expectation. The `auto-fit` grids are checked by the overflow assertion instead, because their column count legitimately depends on the rendered font metrics.

- [ ] **Step 1: Write the responsive checker**

Create `docs-site/scripts/check-landing-responsive.mjs`:

```js
#!/usr/bin/env node
/**
 * Landing-page responsive and theming gate. Requires `npm run preview` to be
 * serving dist/ on http://localhost:4321 (override with PREVIEW_URL).
 *
 * At 375 / 768 / 1280 px in both themes it asserts:
 *   - the document never scrolls horizontally
 *   - .format-grid reflows 1 -> 2 -> 3 columns
 *   - exactly one <h1>, and six format cards
 *   - no clickable target shorter than 24 CSS px (WCAG 2.2 Target Size (Minimum))
 * and across themes:
 *   - --admob-ink resolves to different values, proving the tokens really switch
 */
import { chromium } from 'playwright';

const BASE = process.env.PREVIEW_URL ?? 'http://localhost:4321';

const VIEWPORTS = [
  { label: '375 phone', width: 375, height: 812, columns: 1 },
  { label: '768 tablet', width: 768, height: 1024, columns: 2 },
  { label: '1280 desktop', width: 1280, height: 800, columns: 3 },
];

const failures = [];
const inkByTheme = {};

const browser = await chromium.launch();

for (const theme of ['light', 'dark']) {
  for (const vp of VIEWPORTS) {
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      colorScheme: theme,
    });
    const page = await context.newPage();
    await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
    // Starlight's toggle stamps data-theme on <html>. Set it explicitly so this
    // exercises the manual switch, not just the OS preference.
    await page.evaluate((t) => document.documentElement.setAttribute('data-theme', t), theme);
    await page.waitForTimeout(120);

    const r = await page.evaluate(() => {
      const grid = document.querySelector('.format-grid');
      const columns = grid
        ? getComputedStyle(grid).gridTemplateColumns.trim().split(/\s+/).filter(Boolean).length
        : 0;
      const smallTargets = [
        ...document.querySelectorAll(
          '.cta-actions a, .format-link, .footer-column a, .badge-strip a, .why-link, .roadmap-more a'
        ),
      ]
        .map((el) => ({
          label: (el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 44),
          height: Math.round(el.getBoundingClientRect().height),
        }))
        .filter((t) => t.height > 0 && t.height < 24);
      return {
        scrollWidth: document.documentElement.scrollWidth,
        innerWidth: window.innerWidth,
        columns,
        h1: document.querySelectorAll('h1').length,
        cards: document.querySelectorAll('.format-card').length,
        ink: getComputedStyle(document.documentElement).getPropertyValue('--admob-ink').trim(),
        smallTargets,
        widest: [...document.querySelectorAll('body *')]
          .filter((el) => el.getBoundingClientRect().right > window.innerWidth + 1)
          .slice(0, 3)
          .map((el) => `${el.tagName.toLowerCase()}.${el.className || '(no class)'}`),
      };
    });

    const where = `${theme} @ ${vp.label}`;
    const before = failures.length;

    if (r.scrollWidth > r.innerWidth + 1) {
      failures.push(
        `${where}: document scrolls horizontally (${r.scrollWidth} > ${r.innerWidth}) — ${r.widest.join(', ')}`
      );
    }
    if (r.columns !== vp.columns) {
      failures.push(`${where}: .format-grid has ${r.columns} columns, expected ${vp.columns}`);
    }
    if (r.h1 !== 1) failures.push(`${where}: ${r.h1} <h1> elements, expected exactly 1`);
    if (r.cards !== 6) failures.push(`${where}: ${r.cards} format cards, expected 6`);
    for (const t of r.smallTargets) {
      failures.push(`${where}: target "${t.label}" is only ${t.height}px tall (minimum 24)`);
    }

    inkByTheme[theme] = r.ink;
    const verdict = failures.length === before ? 'ok  ' : 'FAIL';
    console.log(`  ${verdict} ${where}: ${r.columns} cols, ${r.cards} cards, ink ${r.ink}`);
    await context.close();
  }
}

await browser.close();

if (inkByTheme.light && inkByTheme.light === inkByTheme.dark) {
  failures.push(`--admob-ink is ${inkByTheme.light} in both themes — the token layer is not switching`);
}

if (failures.length) {
  console.error(`\n${failures.length} landing check failure(s):`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log('\nLanding responsive and theming checks passed.');
```

- [ ] **Step 2: Register the script and run it — expect a failure**

In `docs-site/package.json`, add to `"scripts"`:

```json
    "check:landing": "node scripts/check-landing-responsive.mjs"
```

Then:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm run build
npm run preview &
sleep 3
npm run check:landing
```

Expected: FAIL, with six target-size failures — one per viewport per theme — naming footer links such as `target "Quickstart" is only 20px tall (minimum 24)`. Task 8 left `.footer-column a` at its natural height precisely so this cycle is real: `0.875rem` at `line-height: 1.4` computes to about 20 px, under the WCAG 2.2 Target Size (Minimum) floor of 24 px, and the 0.5 rem list gap is too tight for the spacing exception to apply.

Column counts (1 / 2 / 3), the card count and the H1 count should all pass on this first run.

- [ ] **Step 3: Fix the footer target size and re-run**

In `docs-site/src/components/landing/FinalCta.astro`, replace the `.footer-column a` rule with:

```css
  .footer-column a {
    display: inline-flex;
    align-items: center;
    min-block-size: 1.5rem; /* 24px — WCAG 2.2 Target Size (Minimum) */
    font-family: var(--admob-font-body);
    font-size: 0.875rem;
    line-height: 1.4;
    color: var(--admob-slate);
    text-decoration: none;
  }
```

Rebuild and re-check:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm run build && npm run check:landing
```

Expected: `Landing responsive and theming checks passed.`, with `ok` on all six viewport/theme combinations and two different `--admob-ink` values across the themes.

- [ ] **Step 4: Confirm the full gate, then commit the checker**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run verify && npm run check:overflow && npm run check:landing
```

Expected: all four green. `check:overflow` covers every page at 375 px; `check:landing` covers `/` at all three widths in both themes.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/scripts/check-landing-responsive.mjs docs-site/package.json \
        docs-site/src/components/landing/FinalCta.astro
git commit -m "test(docs-site): responsive and theming gate for the landing page"
```

- [ ] **Step 5: Retrofit the Plan 7 screenshots — data only**

Run this **only once Plan 7 has been merged** and `docs-site/src/assets/screenshots/screenshots.json` exists. Confirm first:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
node --test docs-site/scripts/screenshots.test.mjs
node -e "
const m = require('./docs-site/src/assets/screenshots/screenshots.json');
for (const s of ['banner','interstitial','rewarded','rewarded-interstitial','app-open','native']) {
  const e = m.screenshots.find(x => x.file === s + '-android-dark.png');
  console.log((e ? 'ok  ' : 'MISS') + ' ' + s + '-android-dark.png' + (e ? '  focus=' + e.focus : ''));
}
"
```

Expected: Plan 7's verifier passes, and all six lines report `ok` with `focus` values `bottom, center, center, center, top, center` in that order.

Now change **six fields and nothing else** in `docs-site/src/data/landing.ts` — every `screenshot: null` becomes the matching manifest filename, and every `crop` stays exactly as it is:

```ts
  { slug: 'banner',                /* … */ screenshot: 'banner-android-dark.png',                crop: 'bottom' },
  { slug: 'interstitial',          /* … */ screenshot: 'interstitial-android-dark.png',          crop: 'center' },
  { slug: 'rewarded',              /* … */ screenshot: 'rewarded-android-dark.png',              crop: 'center' },
  { slug: 'rewarded-interstitial', /* … */ screenshot: 'rewarded-interstitial-android-dark.png', crop: 'center' },
  { slug: 'app-open',              /* … */ screenshot: 'app-open-android-dark.png',              crop: 'top' },
  { slug: 'native',                /* … */ screenshot: 'native-android-dark.png',                crop: 'center' },
```

Do not touch `FormatCard.astro`, `FormatGrid.astro`, `FormatArt.astro` or any CSS. `FormatCard` already resolves `Screenshot.astro` through `import.meta.glob`, already renders the ternary, and its `.format-stage` already supplies the 4:5 box and `object-fit: cover`; `Screenshot.astro` supplies `--screenshot-focus` from the manifest, which the crop anchors to. If a layout change *seems* necessary here, something in Task 3 was built wrong — fix it there rather than special-casing the retrofit.

Keep `FormatArt.astro` in the tree. It is the fallback whenever a manifest entry goes missing, and it is what the grid renders in a fresh clone before the image assets are pulled.

- [ ] **Step 6: Verify the retrofit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build && npm run check:overflow && npm run check:landing
node -e "
const h = require('fs').readFileSync('dist/index.html','utf8');
console.log('picture elements:', (h.match(/<picture/g) || []).length);
console.log('avif sources:', (h.match(/type=\"image\/avif\"/g) || []).length);
console.log('wireframes left:', (h.match(/class=\"[^\"]*format-art/g) || []).length);
console.log('lazy images:', (h.match(/loading=\"lazy\"/g) || []).length >= 6);
"
```

Expected: the `agrees with the Plan 7 manifest once Plan 7 has run` test from Task 3 now takes its real branch and asserts every `crop` equals the manifest `focus`; `picture elements: 6`; `avif sources: 6`; `wireframes left: 0`; `lazy images: true`. Column counts and overflow are unchanged, because the stage geometry never moved.

- [ ] **Step 7: Commit the retrofit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/src/data/landing.ts
git commit -m "feat(docs-site): show real device captures in the landing format grid"
```

---

### Task 10: Lighthouse verification

The landing page is the entry point for every commercial-intent query in spec §7, so its Core Web Vitals and its SEO audit are load-bearing. This task pins target scores, records the run, and fixes anything that misses.

**Files:**
- Create: `docs-site/scripts/check-lighthouse.mjs`
- Modify: `docs-site/package.json` (add the `check:lighthouse` script)
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.gitignore`

**Interfaces:**
- Consumes: the preview server on `http://localhost:4321`, and `lighthouse` fetched through `npx` — deliberately **not** added to `package.json` dependencies, because it is a periodic audit tool rather than a build input, and pinning a headless-Chrome driver into the docs build is more maintenance than it repays.
- Produces: `npm run check:lighthouse`, plus JSON reports under `docs-site/.lighthouse/`.

**Target scores.** Desktop and mobile are scored on different curves, so they get different floors:

| Category | Desktop | Mobile | Why this floor |
|---|---|---|---|
| Performance | ≥ 98 | ≥ 92 | The page ships no JavaScript of its own — Starlight's theme toggle and Pagefind are the only scripts. CSS is bundled and inlined by Astro, fonts are self-hosted woff2 (Plan 2), and every screenshot is lazy and below the fold. |
| Accessibility | 100 | 100 | Non-negotiable. One H1, no skipped heading levels, `scope`-ed table headers, a keyboard-reachable table scroll region, `alt` text sourced from the Plan 7 manifest, and 24 px minimum targets are all already in place. |
| Best practices | ≥ 96 | ≥ 96 | Not 100: the badge strip makes one third-party request to `img.shields.io`, which is the deliberate trade recorded in Task 2. |
| SEO | 100 | 100 | Title, meta description, canonical, `lang`, crawlability and descriptive link text are all handled — Plan 2 for the head, this plan for the copy. |

CLS is asserted separately at ≤ 0.02, because a good Performance rollup can still hide a badge-strip reflow.

- [ ] **Step 1: Write the assertion script**

Create `docs-site/scripts/check-lighthouse.mjs`:

```js
#!/usr/bin/env node
/**
 * Asserts the landing page's Lighthouse scores against the floors recorded in
 * Plan 5, Task 10. Run `npx lighthouse` first (Step 2) — this script only reads
 * the reports, so a flaky network cannot turn into a flaky assertion.
 */
import { readFileSync, existsSync } from 'node:fs';

const FLOORS = {
  desktop: { performance: 98, accessibility: 100, 'best-practices': 96, seo: 100 },
  mobile: { performance: 92, accessibility: 100, 'best-practices': 96, seo: 100 },
};

/** Audits that must be a clean pass regardless of the category rollup. */
const MUST_PASS = [
  'document-title',
  'meta-description',
  'html-has-lang',
  'heading-order',
  'color-contrast',
  'image-alt',
  'link-name',
  'is-crawlable',
  'canonical',
  'viewport',
  'target-size',
];

const failures = [];

for (const [profile, floors] of Object.entries(FLOORS)) {
  const path = `.lighthouse/${profile}.json`;
  if (!existsSync(path)) {
    failures.push(`${profile}: ${path} is missing — run Step 2 first`);
    continue;
  }
  const report = JSON.parse(readFileSync(path, 'utf8'));

  for (const [category, floor] of Object.entries(floors)) {
    const score = Math.round((report.categories[category]?.score ?? 0) * 100);
    console.log(`  ${score >= floor ? 'ok  ' : 'FAIL'} ${profile} ${category}: ${score} (floor ${floor})`);
    if (score < floor) failures.push(`${profile} ${category}: ${score} < ${floor}`);
  }

  for (const id of MUST_PASS) {
    const audit = report.audits[id];
    if (!audit) continue; // not applicable on this profile
    if (audit.score !== null && audit.score < 1) {
      failures.push(`${profile} audit "${id}": ${audit.title} — ${audit.displayValue ?? 'failing'}`);
    }
  }

  const cls = report.audits['cumulative-layout-shift']?.numericValue ?? 0;
  console.log(`  ${cls <= 0.02 ? 'ok  ' : 'FAIL'} ${profile} CLS: ${cls.toFixed(4)} (max 0.02)`);
  if (cls > 0.02) failures.push(`${profile} CLS ${cls.toFixed(4)} > 0.02`);
}

if (failures.length) {
  console.error(`\n${failures.length} Lighthouse failure(s):`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log('\nLighthouse targets met.');
```

- [ ] **Step 2: Register the script, ignore the reports, and run the audits**

In `docs-site/package.json`, add to `"scripts"`:

```json
    "check:lighthouse": "node scripts/check-lighthouse.mjs"
```

Append to `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.gitignore`:

```gitignore
docs-site/.lighthouse/
```

Then run both profiles against a production build:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm run build
npm run preview &
sleep 3
mkdir -p .lighthouse
npx --yes lighthouse@12 http://localhost:4321/ --preset=desktop --quiet \
  --output=json --output-path=.lighthouse/desktop.json --chrome-flags="--headless=new"
npx --yes lighthouse@12 http://localhost:4321/ --quiet \
  --output=json --output-path=.lighthouse/mobile.json --chrome-flags="--headless=new"
```

Expected: two JSON reports written. Audit the preview build, never `astro dev` — the dev server serves unbundled modules and unminified CSS and will read 20 to 30 points lower for reasons that do not exist in production.

- [ ] **Step 3: Assert the scores**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm run check:lighthouse
```

Expected: `Lighthouse targets met.`

The three misses most likely on a first run, each with its fix:

- **`target-size` fails.** A link is under 24 px. Task 9's `check:landing` should already have caught it; if a new one appeared, apply the same `display: inline-flex; align-items: center; min-block-size: 1.5rem` treatment.
- **CLS above 0.02.** The shields.io badge is loading into a slot narrower than its intrinsic width and its siblings reflow. Re-measure it and widen `.badge-slot` (Task 2, Step 3).
- **Performance below the mobile floor.** Check `total-byte-weight` in the report. If the six screenshots dominate, narrow the `sizes` attribute in `FormatCard.astro` — the stage is never wider than 20 rem at any breakpoint, so `sizes` should never let the browser pick a 1200 px candidate. Do **not** solve this by dropping cards from the grid.

- [ ] **Step 4: Record the baseline and commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
node -e "
for (const p of ['desktop','mobile']) {
  const r = JSON.parse(require('fs').readFileSync('.lighthouse/'+p+'.json','utf8'));
  const s = Object.entries(r.categories).map(([k,v]) => k+'='+Math.round(v.score*100)).join(' ');
  console.log(p.padEnd(8), s);
}
"
```

Expected: two lines at or above the floors in the table above. Paste them into the pull-request description — the design's §12 success metrics are reviewed at 30 and 90 days, and this is the launch baseline.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs-site/scripts/check-lighthouse.mjs docs-site/package.json .gitignore
git commit -m "test(docs-site): Lighthouse gate for the landing page"
```

- [ ] **Step 5: Final whole-page gate**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs-site
npm test && npm run build && npm run verify && npm run check:overflow && npm run check:landing && npm run check:lighthouse
```

Expected: six green runs. This is the merge gate for the plan.

---

## Self-Review

**1. Spec coverage.** Every item in the brief maps to a task.

| Requirement | Task |
|---|---|
| Hero: H1 targeting `compose multiplatform admob`, one-sentence value proposition, primary CTA to `/start/quickstart/`, secondary CTA to GitHub | 1 (Starlight `hero` frontmatter) |
| Badge strip: Maven Central version, licence, Kotlin 2.3.20, Android/iOS platforms | 2 |
| Six-format showcase grid, each card linking to its `/formats/*` page, screenshot-ready | 3 |
| Real, copy-pasteable quickstart verified against `AGENTS.md`, UMP → ATT → initialize | 4 |
| Neutral capability matrix capturing `basic-ads alternative`, no named teardown | 5 |
| Differentiators: native ads, app-open, UMP consent in the init flow, the Gradle plugin, the frozen ABI | 6 |
| Compatibility strip: Kotlin 2.3.20, CMP 1.11.1, minSdk 26, iOS 15.0, plus the klib caveat | 7 |
| Roadmap teaser linking to `/project/roadmap/` | 8 |
| Final CTA and footer with the trademark line verbatim | 8 |
| Page SEO: title ≤ 60, meta description ≤ 160, OG image, `SoftwareSourceCode` JSON-LD | 1 |
| Responsive at 375 / 768 / 1280 | 9 |
| Light and dark theming | 9 |
| Lighthouse with target scores | 10 |
| Exact screenshot filenames declared for Plan 7 | 3 |

Two SEO requirements are satisfied by delegation rather than by new code, and both are verified here rather than assumed: the OG image comes from Plan 2's `astro-og-canvas` route, and the `SoftwareSourceCode` JSON-LD from Plan 2's `Head.astro`, which switches on the `/` pathname. Task 1, Step 8 asserts both appear in `dist/index.html`. Emitting a second JSON-LD block from this plan would have put two conflicting `SoftwareSourceCode` nodes on one page, so it deliberately does not.

**2. Placeholder scan.** No `TBD`, no "write compelling copy here", no "similar to Task N", no "add appropriate error handling". Every string a reader will see is written out verbatim: the hero title and tagline, six format names with blurbs and API lines, both code samples, fourteen capability rows and the fairness footnote, five differentiator bodies, three compatibility caveats, two roadmap items, twenty-three footer links, and the trademark line. Every code step carries complete, runnable content, including all six SVG wireframe variants and all three verification scripts in full.

**3. Type consistency.** `library`, `coordinate`, `LandingFormat`, `landingFormats`, `CapabilityRow`, `capabilityRows`, `capabilityNote`, `capabilityVerifiedOn`, `basicAdsRepo`, `Differentiator`, `differentiators`, `CompatibilityFact`, `compatibilityFacts`, `compatibilityCaveats`, `RoadmapItem`, `roadmapItems`, `FooterLink`, `FooterColumn` and `footerColumns` are each declared once in a task's Interfaces block and used under exactly that name in every later task and in `test/landing.test.ts`. `Section.astro`'s prop is `heading`, never `title`, at all seven call sites. `FormatCard`'s field is `crop` while Plan 7's manifest field is `focus` — different names on purpose, and Task 3's test asserts they hold the same value. `Screenshot.astro`'s prop is `name`, matching Plan 7 Task 15, not `file` or `src`.

**One gap found during review, and closed inside the plan.** Task 8's footer links, as first specified, computed to roughly 20 px tall — `0.875rem` at `line-height: 1.4` — which is under the WCAG 2.2 Target Size (Minimum) floor of 24 px, with a 0.5 rem list gap too tight for the spacing exception to apply. Rather than silently correcting Task 8 and asserting a fix nobody had seen fail, Task 8 now states that the natural height is left in place on purpose, Task 9 Step 2 predicts the exact failure text, and Task 9 Step 3 supplies the fix. The rule gets a real red-to-green cycle. The same `min-block-size: 1.5rem` treatment is applied up front to `.why-link` and `.roadmap-more a`, which are single links rather than a dense list.

**Three risks worth flagging to the executor.**

- **Plan 4 is a hard dependency.** Tasks 6 and 7 use bare imports of `InitSequence.astro` and `PlatformMatrix.astro`, which fail the build if Plan 4 has not merged. This is deliberate — Plan 4 owns the diagram accessibility contract, and redrawing those two here would fork it — but it means Plan 5 cannot start before Plan 4 lands.
- **`import.meta.glob` in `FormatCard.astro` is load-bearing.** Replacing it with a bare `import Screenshot from '../Screenshot.astro'` compiles fine once Plan 7 has merged, and then silently makes the landing page unbuildable in any checkout without the screenshot assets. The comment in the file says so; keep it.
- **The `<title>` strategy is deliberately conservative.** Frontmatter `title` is 31 characters, so even with Starlight appending ` | AdMob CMP` the rendered title is 43 — comfortably inside 60 without a `head` override that would risk a duplicate `<title>` tag. If the site title in `astro.config.mjs` ever grows, `test/landing.test.ts` fails on the composed length rather than silently shipping a truncated SERP entry.

**One judgement call worth restating, because it will look like an omission.** Every ad-format screenshot from Plan 7 is dark-only: `DebugTokens` declares a fixed dark palette and does not inherit the host theme, and `AdTemplates` hardcodes white native-card backgrounds, so a light capture of a harness surface cannot exist. Only `consent-*` and `att-*` have light/dark pairs, and neither appears on this page. The landing page themes both ways regardless, because each capture is framed as content rather than chrome — an `--admob-code` stage with a hairline border, a themed card around it, and a caption naming it a Google test ad in the bundled debug harness. Forcing a permanently dark showcase band was rejected: Plan 2's ramp inverts between themes and `color-mix()` cannot express "whichever of ink and paper is darker", and adding a twentieth `--admob-*` token would break Plan 2's "do not introduce a parallel token set" contract.
