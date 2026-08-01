# Documentation Native Reference Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current stylized editorial theme with a quiet, theme-aware technical reference design based on measured GitHub Docs and Starlight conventions.

**Architecture:** Keep Starlight, Expressive Code, the unified Markdown processor, and the accessible table wrapper. First update the browser regression contract so the current implementation fails, then simplify Expressive Code to paired GitHub themes and collapse the CSS overrides onto native typography, neutral surfaces, functional borders, and non-spatial interaction feedback.

**Tech Stack:** Astro 7.1.6, Starlight 0.41.5, Expressive Code/Shiki, Playwright 1.62.1, Vitest 4.1.10, CSS custom properties.

## Global Constraints

- Preserve every existing uncommitted change; refine files in place and do not reset them.
- Do not commit, stage, push, open a PR, tag, or release during this plan. Integration remains a separate owner decision.
- Do not change documentation prose, routes, landing-page structure, Mermaid content, SDK code, Kotlin ABI, generated API files, or GitHub workflows.
- Keep `rehypeTableScroll()`, `@astrojs/markdown-remark` 7.2.2, its Vitest coverage, and both local visual checks in release-readiness Section 8.
- Keep all existing `--admob-*` public token names. Values may change; names may not.
- Use the native UI font stack for every non-code surface and retain JetBrains Mono only for code.
- Code must use `github-light` in light mode and `github-dark` in dark mode with Expressive Code contrast correction at 5.5:1.
- Body is 16px/1.6 with a 45rem maximum measure. H1 is 32px desktop and 28px mobile; H2 is 24px; H3 is 19px.
- Use 6px radii, one-pixel neutral borders, no shadows, no spatial hover motion, and no article entrance animation.

---

## File Map

- `docs-site/scripts/check-theme.mjs` — executable rendered-design contract for typography, color schemes, code, tables, components, overflow, and motion.
- `docs-site/astro.config.mjs` — Markdown processor, Starlight configuration, Expressive Code theme selection, Mermaid font stack, and font preloads.
- `docs-site/src/styles/tokens.css` — Avinya/Starlight color mapping and all presentation overrides.
- `docs-site/src/lib/rehype-table-scroll.mjs` — unchanged accessible table transform.
- `docs-site/test/rehype-table-scroll.test.ts` — unchanged table-transform regression coverage.
- `scripts/release-readiness.sh` — unchanged in this pass; verify that Section 8 still runs `check:theme` and `check:overflow`.

### Task 1: Replace the visual regression contract first

**Files:**
- Modify: `docs-site/scripts/check-theme.mjs:85-164`

**Interfaces:**
- Consumes: the built `docs-site/dist/` tree and `PREVIEW_URL` override already supported by the script.
- Produces: failing assertions for the old visual system and executable acceptance criteria for Tasks 2–3.

- [ ] **Step 1: Expand the rendered inspection payload**

Capture typography, component shape, and theme-specific code data in addition to the existing accessibility and overflow data:

```js
const h1 = document.querySelector('h1');
const h2 = document.querySelector('.sl-markdown-content h2');
const h3 = document.querySelector('.sl-markdown-content h3');
const sidebarLink = document.querySelector('.sidebar-content a');
const searchButton = document.querySelector('site-search button[data-open-modal]');
const linkCard = document.querySelector('.sl-link-card');
const frame = document.querySelector('.expressive-code .frame');

return {
  article: {
    fontFamily: getComputedStyle(article).fontFamily,
    fontSize: getComputedStyle(article).fontSize,
    lineHeight: getComputedStyle(article).lineHeight,
    width: article.getBoundingClientRect().width,
  },
  headings: {
    h1: getComputedStyle(h1).fontSize,
    h1Family: getComputedStyle(h1).fontFamily,
    h2: getComputedStyle(h2).fontSize,
    h3: getComputedStyle(h3).fontSize,
  },
  sidebar: {
    fontFamily: getComputedStyle(sidebarLink).fontFamily,
    fontSize: getComputedStyle(sidebarLink).fontSize,
  },
  code: {
    background: getComputedStyle(document.querySelector('.expressive-code pre')).backgroundColor,
    fontSize: getComputedStyle(document.querySelector('.expressive-code code')).fontSize,
    lineHeight: getComputedStyle(document.querySelector('.expressive-code code')).lineHeight,
    radius: getComputedStyle(frame).borderRadius,
    colors: [...new Set(codeColors)],
  },
  components: {
    searchRadius: getComputedStyle(searchButton).borderRadius,
    cardRadius: getComputedStyle(linkCard).borderRadius,
    cardTransform: getComputedStyle(linkCard).transform,
  },
  motion: {
    name: getComputedStyle(main).animationName,
  },
};
```

- [ ] **Step 2: Add exact Native Reference assertions**

Use explicit expected values so future theme drift fails locally:

```js
const codeBackground = {
  light: 'rgb(246, 248, 250)',
  dark: 'rgb(22, 27, 34)',
}[theme];

check(desktop.article?.fontSize === '16px', `${theme} body is 16px`);
check(desktop.article?.lineHeight === '25.6px', `${theme} body uses 1.6 rhythm`);
check(desktop.article?.width <= 720, `${theme} reading measure is at most 45rem`);
check(desktop.headings?.h1 === '32px', `${theme} desktop H1 is 32px`);
check(desktop.headings?.h2 === '24px', `${theme} H2 is 24px`);
check(desktop.headings?.h3 === '19px', `${theme} H3 is 19px`);
check(
  desktop.article?.fontFamily === desktop.headings?.h1Family &&
    !/Space Grotesk|Inter/.test(desktop.article?.fontFamily ?? ''),
  `${theme} prose and headings share the native UI stack`
);
check(desktop.sidebar?.fontFamily === desktop.article?.fontFamily, `${theme} sidebar uses the UI stack`);
check(desktop.sidebar?.fontSize === '13px', `${theme} sidebar is 13px`);
check(desktop.code?.background === codeBackground, `${theme} code follows the site theme`);
check(desktop.code?.fontSize === '14px', `${theme} code is 14px`);
check(desktop.code?.radius === '6px', `${theme} code frame radius is 6px`);
check((desktop.code?.colors.length ?? 0) >= 5, `${theme} code exposes at least five syntax colors`);
check(desktop.components?.searchRadius === '6px', `${theme} search is not pill-shaped`);
check(desktop.components?.cardRadius === '6px', `${theme} cards use a quiet radius`);
check(desktop.components?.cardTransform === 'none', `${theme} cards have no spatial transform`);
check(desktop.motion?.name === 'none', `${theme} articles do not animate on entry`);
check(mobile.headings?.h1 === '28px', `${theme} mobile H1 is 28px`);
```

Retain the accessible link contrast, focusable table region, structured-table, reduced-motion, and mobile containment checks.

- [ ] **Step 3: Check syntax color coverage by documented language**

Add an inspection helper that loads the specified page and selects the matching block:

```js
const syntaxCases = [
  { language: 'kotlin', path: '/start/quickstart/' },
  { language: 'xml', path: '/start/android-setup/' },
  { language: 'toml', path: '/start/installation/' },
  { language: 'bash', path: '/start/installation/' },
];
```

For each case and each theme, count unique computed colors within `.expressive-code[data-language="${language}"] span` and require at least five.

- [ ] **Step 4: Run the new gate against the current build and confirm RED**

Run:

```bash
cd docs-site
npm run check:theme
```

Expected: non-zero exit with failures for 1.6 line height, 32px desktop H1, native shared font, theme-aware light code, 6px shapes, and absent entrance motion. Table accessibility and mobile containment should continue to pass.

- [ ] **Step 5: Review checkpoint**

Inspect only `docs-site/scripts/check-theme.mjs`; do not alter production CSS/config to make the test pass yet, and do not commit.

### Task 2: Move Expressive Code and font loading onto the reference baseline

**Files:**
- Modify: `docs-site/astro.config.mjs:19-88, 96-120, 139-189`

**Interfaces:**
- Consumes: built-in Shiki theme identifiers `github-dark` and `github-light`, plus Starlight’s Expressive Code UI-color bridge.
- Produces: theme-switched token colors and frame surfaces driven by `--sl-color-gray-6`/`--sl-color-gray-7` from Task 3.

- [ ] **Step 1: Remove the permanent-dark theme builder**

Delete `buildCodeTheme(mode)` and its permanent `#15181f` editor colors. Replace the Expressive Code configuration with:

```js
expressiveCode: {
  themes: ['github-dark', 'github-light'],
  minSyntaxHighlightingColorContrast: 5.5,
  useStarlightUiThemeColors: true,
  useStarlightDarkModeSwitch: true,
  styleOverrides: {
    borderRadius: '6px',
    codeFontFamily: 'var(--admob-font-mono)',
    codeFontSize: '0.875rem',
    codeLineHeight: '1.55',
    uiFontFamily: 'var(--admob-font-body)',
    frames: {
      shadowColor: 'transparent',
      tooltipSuccessBackground: 'var(--admob-accent-text)',
      tooltipSuccessForeground: 'var(--admob-accent-contrast)',
    },
  },
},
```

Do not hard-code editor, terminal, title-bar, copy-button, or border colors. Starlight’s UI bridge resolves those per site theme.

- [ ] **Step 2: Remove display/body font preloads**

Replace the Space Grotesk and Inter preload entries with one code-font preload:

```js
{
  tag: 'link',
  attrs: {
    rel: 'preload',
    href: '/fonts/jetbrains-mono-400.woff2',
    as: 'font',
    type: 'font/woff2',
    crossorigin: 'anonymous',
  },
},
```

- [ ] **Step 3: Make Mermaid use the same native UI family**

Set its font stack to:

```js
fontFamily: '-apple-system, BlinkMacSystemFont, Segoe UI, Noto Sans, Helvetica Neue, Arial, sans-serif',
```

Keep all existing Mermaid colors and the `rehypeTableScroll` ordering unchanged. Correct the current indentation of the `rehypePlugins` array while editing this block.

- [ ] **Step 4: Build to verify configuration and theme identifiers**

Run:

```bash
cd docs-site
npm run build
```

Expected: 26 pages build, no Markdown deprecation warning, and no unknown Shiki theme error.

- [ ] **Step 5: Review checkpoint**

Inspect `astro.config.mjs` for leftover `#15181f`, `#0d1015`, `Space Grotesk`, `Inter`, or `buildCodeTheme` references. Expected: none. Do not commit.

### Task 3: Replace the CSS design language

**Files:**
- Modify: `docs-site/src/styles/tokens.css:1-664`

**Interfaces:**
- Consumes: unchanged `--admob-*` token names and Starlight component selectors.
- Produces: the computed typography, component shapes, theme surfaces, and motion behavior asserted by Task 1.

- [ ] **Step 1: Replace font declarations and theme tokens**

Delete all Space Grotesk and Inter `@font-face` rules. Keep both JetBrains Mono faces. Set the theme-independent values exactly:

```css
:root {
  --admob-font-display: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans',
    'Helvetica Neue', Arial, sans-serif;
  --admob-font-body: var(--admob-font-display);
  --admob-font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas,
    'Liberation Mono', monospace;
  --admob-tracking-tight: 0;
  --admob-tracking-tighter: 0;
  --admob-radius: 6px;
  --admob-radius-lg: 6px;
  --admob-border: 1px solid var(--admob-hair);
  --admob-shadow: none;
  --admob-content-max: 72rem;
  --admob-reading: 45rem;
  --admob-ease: ease-out;
}
```

Use these paired surface tokens:

```css
:root,
:root[data-theme='dark'] {
  --admob-paper: #0d1117;
  --admob-surface: #161b22;
  --admob-code: #161b22;
  --admob-ink: #e6edf3;
  --admob-slate: #8b949e;
  --admob-hair: #30363d;
  --admob-accent: #ee3a20;
  --admob-accent-text: #ff9a88;
  --admob-accent-contrast: #ffffff;
  --admob-accent-soft: rgba(238, 58, 32, 0.14);
}

:root[data-theme='light'] {
  --admob-paper: #ffffff;
  --admob-surface: #f6f8fa;
  --admob-code: #f6f8fa;
  --admob-ink: #1f2328;
  --admob-slate: #59636e;
  --admob-hair: #d0d7de;
  --admob-accent: #ee3a20;
  --admob-accent-text: #9e2411;
  --admob-accent-contrast: #ffffff;
  --admob-accent-soft: rgba(238, 58, 32, 0.1);
}
```

Map Starlight’s dark `--sl-color-gray-6` to `#161b22` and light `--sl-color-gray-7` to `#f6f8fa`; these are the code-frame surfaces consumed by Task 2.

- [ ] **Step 2: Apply the exact typography scale**

Replace display typography overrides with:

```css
h1,
h2,
h3,
h4,
h5,
h6,
.site-title {
  font-family: var(--admob-font-body);
  font-weight: 700;
  letter-spacing: normal;
}

.sl-markdown-content h1,
.content-panel h1 {
  font-size: 2rem;
  line-height: 1.2;
  letter-spacing: normal;
  text-wrap: wrap;
}

.sl-markdown-content h2 {
  margin-block: 2.5rem 0.875rem;
  font-size: 1.5rem;
  line-height: 1.3;
}

.sl-markdown-content h3 {
  margin-block: 1.75rem 0.625rem;
  font-size: 1.1875rem;
  line-height: 1.4;
}

.sl-markdown-content {
  color: var(--admob-ink);
  font-size: 1rem;
  line-height: 1.6;
}

@media (max-width: 50rem) {
  .sl-markdown-content h1,
  .content-panel h1 {
    font-size: 1.75rem;
  }
}
```

Delete `.mono-label` display treatment and paragraph `text-wrap: pretty`; allow browser-default wrapping.

- [ ] **Step 3: Simplify prose and navigation**

- Replace border-bottom link simulation with `text-decoration: underline`, `text-underline-offset: 0.15em`, and a 150ms `text-decoration-color`/`color` transition.
- Delete the custom unordered-list reset and pseudo-rule bullets so native Starlight bullets return.
- Set `.sidebar-content a` to the UI stack, 13px/1.5, and a 150ms color transition.
- Keep the current-page 2px inset orange marker, but set `background: transparent`.
- Set group labels and TOC headings to the UI stack, 12px, weight 600, normal tracking, and `text-transform: none`.
- Keep TOC active-marker opacity changes at 150ms with no transform.

Use this active navigation contract:

```css
.sidebar-content a[aria-current='page'],
.sidebar-content a[aria-current='page']:hover {
  color: var(--admob-ink);
  font-weight: 600;
  background: transparent;
  box-shadow: inset 2px 0 var(--admob-accent);
}
```

- [ ] **Step 4: Let Expressive Code follow its themes**

Delete every hard-coded frame/editor color. Keep only structural integration:

```css
.expressive-code .frame {
  overflow: hidden;
  border-radius: var(--admob-radius);
  box-shadow: none;
}

.expressive-code code {
  font-size: 0.875rem;
  line-height: 1.55;
}

.expressive-code .copy button {
  border-radius: 4px;
}
```

- [ ] **Step 5: Flatten asides, cards, pagination, and search**

- Remove the universal orange aside border/title and restore `.starlight-aside__icon { display: block; }`.
- Set aside radius to 6px; title to UI font, 14px, weight 600, normal tracking/case; content to 15px/1.6.
- Set cards and pagination to 6px radius, no shadow, and only a 150ms border/background/color transition.
- Delete `transform: translateY(-1px)` and every transform transition.
- Set card titles and pagination titles to the UI family with normal letter spacing.
- Set the search button to the UI font, 13px, and 6px radius; remove the pill comment and `100px` radius.

- [ ] **Step 6: Remove decorative motion**

Delete `@keyframes admob-article-enter` and the `main[data-pagefind-body]` animation rule. Keep the reduced-motion block, but limit it to disabling smooth scrolling and reducing animation/transition durations.

Allowed transitions are only `color`, `background-color`, `border-color`, `text-decoration-color`, and `opacity`, lasting 120–150ms.

- [ ] **Step 7: Simplify tables without changing structure**

Use:

```css
.table-scroll {
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid var(--admob-hair);
  border-radius: 6px;
  scrollbar-color: var(--admob-slate) transparent;
  scrollbar-width: thin;
}

.sl-markdown-content .table-scroll table {
  width: 100%;
  margin: 0;
  border-collapse: collapse;
  font-size: 0.875rem;
  line-height: 1.45;
}

.sl-markdown-content .table-scroll th {
  padding: 0.625rem 0.75rem;
  border-bottom: 1px solid var(--admob-hair);
  background: var(--admob-surface);
  color: var(--admob-ink);
  font-family: var(--admob-font-body);
  font-size: 0.875rem;
  font-weight: 600;
  letter-spacing: normal;
  text-align: left;
  text-transform: none;
}

.sl-markdown-content .table-scroll td {
  padding: 0.625rem 0.75rem;
  border-bottom: 1px solid var(--admob-hair);
  vertical-align: top;
}

.sl-markdown-content .table-scroll td:first-child {
  font-weight: 500;
}
```

Keep `.table-scroll--wide table { min-width: 42rem; }` and the last-row border removal. Delete table row transition and hover rules.

- [ ] **Step 8: Build and run the new visual gate to reach GREEN**

Run:

```bash
cd docs-site
npm run build
npm run check:theme
```

Expected: the build completes and every light, dark, desktop, mobile, language-color, accessibility, shape, and motion assertion passes.

- [ ] **Step 9: Review checkpoint**

Search the final CSS for rejected patterns:

```bash
rg -n "Space Grotesk|font-family: var\(--admob-font-mono\).*sidebar|text-transform: uppercase|translateY|admob-article-enter|border-radius: 100px|#15181f|#0d1015" docs-site/src/styles/tokens.css docs-site/astro.config.mjs
```

Expected: no matches. Do not commit.

### Task 4: Validate representative rendered pages

**Files:**
- No source changes expected.
- Inspect generated: `docs-site/dist/` (gitignored).

**Interfaces:**
- Consumes: the production build from Task 3.
- Produces: evidence that the CSS contract works across real content shapes, not just one regression page.

- [ ] **Step 1: Capture representative desktop and mobile pages**

Render these pages in light and dark themes at 1440×1000, 768×1024, and 390×844:

```text
/start/quickstart/
/start/installation/
/privacy/consent/
/reference/compatibility/
/reference/troubleshooting/
```

- [ ] **Step 2: Inspect the exact acceptance checklist**

For every viewport/theme combination confirm:

- headings and prose visibly share one native sans family;
- H1 does not dominate the first viewport;
- light code blocks are pale and dark code blocks are charcoal;
- Kotlin, XML, TOML, and Bash syntax remains distinguishable;
- title bars and copy buttons belong to the same code frame;
- tables scroll within their wrapper without widening the page;
- standard bullets, semantic callouts, cards, search, sidebar, and TOC have no ornamental motion or oversized rounding;
- focus outlines remain visible.

- [ ] **Step 3: Fix only evidence-backed visual defects**

If a rendered check fails, first add or tighten the corresponding `check-theme.mjs` assertion, verify RED, make the smallest CSS/config correction, rebuild, and verify GREEN.

- [ ] **Step 4: Review checkpoint**

Record any deviations from the approved spec. Expected: none. Do not commit.

### Task 5: Run the complete local verification protocol

**Files:**
- Verify: `docs-site/package.json`
- Verify: `scripts/release-readiness.sh:173-198`
- Verify: all task-related Git changes

**Interfaces:**
- Consumes: the final implementation tree.
- Produces: exact local evidence required for handoff; no remote actions.

- [ ] **Step 1: Run docs unit and build verification**

```bash
cd docs-site
npm test
npm run build
npm run check:theme
npm run check:overflow
npm run verify
```

Expected:

- 12 Vitest tests pass, including all three table-transform cases;
- 26 pages build without the Markdown deprecation warning;
- theme checks pass in light, dark, mobile, and reduced-motion contexts;
- every authored page is contained at 375px in both themes;
- sitemap, SEO, Pagefind, Mermaid, API reference, and generated artifacts pass.

- [ ] **Step 2: Run whitespace and scope checks**

```bash
git -c core.fsmonitor=false diff --check
git -c core.fsmonitor=false status --short
```

Expected: no whitespace errors. Only the approved docs-theme files, design spec, implementation plan, and the already-approved local readiness integration are modified/untracked.

- [ ] **Step 3: Run the repository’s full eight-section gate**

```bash
./scripts/release-readiness.sh
```

Expected: all eight sections run without `--skip-docs` and finish with `READINESS: PASS`. Section 8 must visibly run both `npm run check:theme` and `npm run check:overflow` after the Astro build.

- [ ] **Step 4: Final handoff without integration actions**

Report the files changed, the measured visual decisions, every verification result, and the absence of commit/push/PR/tag/release actions. Leave the branch and working tree intact for owner review.
