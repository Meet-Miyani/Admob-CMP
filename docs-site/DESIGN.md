# Design system — ads.avinya.dev

Internal notes for anyone changing how the docs site looks. This file is not
part of the built site: `docsLoader()` reads only `src/content/docs/`, so
nothing here reaches the sitemap, `llms.txt`, or search.

Read this before editing `src/styles/tokens.css`, `landing.css`, or
`diagrams.css`. The automated gates will stop you breaking the *mechanics* of
the system; they cannot tell you what it is for.

## Why this file exists

An earlier version of the site encoded an anti-design policy directly into its
gates: `scripts/check-theme.mjs` required the `-apple-system` font stack and
explicitly forbade Space Grotesk and Inter, and asserted `border-radius <= 6px`,
`box-shadow: none` and `transform: none` on every landing element.
`test/landing.test.ts` additionally banned transforms, animations, all
gradients, and uppercase text in the landing sources.

Nothing recorded *why*, so the constraints outlived whatever reasoning produced
them, and the site ended up looking like an unstyled GitHub README. Both files
were rewritten in August 2026 to enforce the system described below instead. If
you find yourself reintroducing flat/no-motion rules, that is a regression, not
a restoration.

## Tokens

`src/styles/tokens.css` is the only file allowed to contain literal colours,
font stacks, or raw radius values. Everything else references `--admob-*`. The
header comment in that file lists the public contract; `diagrams.css`,
`landing.css`, `mermaid.css`, and `test/helpers/css-tokens.ts` all consume those
names, so renaming one is a breaking change.

### Colour

Two themes, switched by `data-theme` on `<html>`. Dark is the default and is a
warm near-black; light is near-neutral paper. The accent `#ee3a20` is the brand
constant and is the same in both.

Four pairings are enforced by `test/diagram-contrast.test.ts` **in both themes**:

| Pairing | Minimum | Why |
|---|---|---|
| `--admob-ink` on `--admob-paper` and on `--admob-surface` | 4.5:1 | body text |
| `--admob-slate` on `--admob-paper` and on `--admob-surface` | 4.5:1 | secondary text and every informational boundary |
| `--admob-accent` on both | 3:1 | strokes and large text only |
| `--admob-accent-text` on `--admob-paper` | 4.5:1 | links (checked live by the theme gate) |

`--admob-accent` must never be used for body-size text — it does not clear 4.5:1
on `--admob-surface` in the light theme. `--admob-accent-text` is the text-safe
accent and is a different value per theme.

If you change any of these, run `npx vitest run test/diagram-contrast.test.ts`
before anything else; it fails fast and tells you which pairing broke.

### Type

One variable superfamily plus the mono. `@fontsource-variable/archivo` supplies
both axes in a single self-hosted file at `public/fonts/archivo-wdth.woff2`:
`wght` 100–900 and `wdth` 62–125, exposed to CSS as `font-stretch`.

- **Display** — Archivo at `--admob-stretch-display` (104%), weight 700–750.
  Headings, the site title. Never running text.
- **Body** — the same family at normal width.
- **Utility** — JetBrains Mono, for content that genuinely *is* code or data:
  eyebrows, column headers, API signatures, version strings, dimensions.

The width axis is the thing that makes headings read as headings. Keep it
modest: past roughly 106% the wide letterforms cost more in legibility than they
return in character, and this is a reference site.

Sizes are pinned by the theme gate at fixed viewports — see the checks around
`landing H1 is 56px desktop` and `desktop H1 is 36px`. Changing the scale means
changing those numbers in the same commit.

### Radius, elevation, motion

Scale: `--admob-radius-sm` 4px (inline code, chips) · `--admob-radius` 8px
(controls, search) · `--admob-radius-lg` 12px (panels, cards, code frames,
tables, figures) · `--admob-radius-xl` 18px (the placement plate).

`0`, `50%` and `999px` are shape declarations rather than points on the scale
and are allowed directly. Everything else must be a token — enforced in source
by `landing.test.ts` and at runtime, against the resolved values, by
`check-theme.mjs`.

Depth is layered surfaces plus hairlines first; `--admob-shadow` and
`--admob-shadow-lg` are for genuinely floating things (hover lift, dialogs).
`--admob-scrim` is the modal backdrop, heavier in dark because a dark dialog
over a near-black page needs more separation than the same scrim gives on white.

Motion is allowed. The single rule: **anything that animates must answer
`prefers-reduced-motion`**, in the same file. `landing.test.ts` fails a file that
declares `@keyframes` or `animation:` without a guard, and `check-theme.mjs`
verifies live that every landing element stops under `reduce`.

Two traps, both hit in practice:

1. Do not write `animation-timeline` next to the other animation longhands. The
   minifier folds them into the `animation` shorthand, which cannot carry a
   timeline — the declaration becomes invalid and is silently dropped. Keep the
   timeline in a separate rule with a different selector shape.
2. A scroll-driven animation holds its `from` state whenever the timeline does
   not resolve: printing, full-page screenshots, reader mode. Never start one at
   `opacity: 0`, or that content simply is not there. Translate only.

## Patterns

**Tables.** One treatment everywhere. Column headers (`thead th`) are the mono
eyebrow at 11px; row headers (`tbody th`) are the body face at 14px/600 — they
are content, not labels. Hairline row separators, no vertical rules, 12px/14px
padding, and the border and radius live on the wrapper, never the table.
`.dg-table` in `diagrams.css` deliberately mirrors `.table-scroll` in
`tokens.css`; if you change one, change both.

Starlight sets `display: block; overflow-x: auto` on every `<table>`. Both of
our table families are already inside their own scroll region, so we restore
`display: table` — a block-display table sizes its box like a div while its
cells lay out wider, which renders as text clipped mid-word instead of a
scrollable table.

**Figures.** `DiagramFigure.astro` provides the frame, the keyboard-focusable
scroll region, the caption and the prose link. When a figure contains a table
the frame drops its padding so it hugs, or you get a box inside a box. The
Expand-to-dialog control is created by script and never rendered server-side: a
control that cannot work without JavaScript should not exist in the markup. It
*moves* the scroll region into the dialog rather than cloning it, because the
Mermaid SVGs carry id-scoped styles that a clone would duplicate.

**The placement plate** (`landing/PlacementPlate.astro`) is the landing page's
one bold element. Everything around it stays quiet — that is deliberate, and
adding a second attention-grabbing element is the most likely way to make the
page worse. Its geometry lives in `landing.css` keyed by format slug, because
geometry is presentation; only the wording lives in `data/landing.ts`. It uses
`:has()` with `:hover`/`:focus` and no JavaScript. `:focus`, not
`:focus-visible` — the plate must follow keyboard focus whatever the browser
decides about drawing a ring.

## What enforces this

| Command | Runs where | Covers |
|---|---|---|
| `npm test` | CI and locally | source-level token rules, content contracts, diagram contrast |
| `npm run check:theme` | `scripts/release-readiness.sh` only | computed styles in both themes, type scale, focus outlines, reduced motion |
| `npm run check:overflow` | `scripts/release-readiness.sh` only | every route at 375px in both themes |
| `npm run verify` | CI and locally | canonical URLs, OG images, JSON-LD, sitemap |

Three values are held by hand and nothing checks the pairing — update them
together with the palette:

- the Mermaid `themeVariables` in `astro.config.mjs` (must be the **light**
  values; `mermaid.css` re-tints for dark)
- `<meta name="theme-color">` in `astro.config.mjs` (must equal
  `--admob-paper`; it had already drifted once)
- the OG image colours in `src/pages/og/[...route].ts`

OG cards stay on Noto Sans rather than Archivo on purpose: `astro-og-canvas`
rasterises through canvaskit, which renders a variable font at its default
instance only, so a SemiBold title would silently come back at weight 400.
