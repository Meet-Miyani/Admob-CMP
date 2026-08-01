# Documentation Native Reference Theme Design

**Date:** 2026-08-01
**Status:** Implemented, independently reviewed, and verified
**Scope:** Presentation, generated social imagery, and regression gates for `docs-site/`; no content, URL, SDK, or public API changes.

## 1. Problem

The current editorial refresh layers several individually polished treatments—Space Grotesk display headings, Inter body copy, permanent dark code islands, large radii, uppercase monospace labels, tinted active navigation, card lift, and article entrance motion. Together they make the site feel generated and product-marketing-led rather than like a dependable technical reference.

The documentation is code-heavy: 78 fenced examples across 25 authored pages, including 58 Kotlin blocks. Its primary design job is sustained reading, code comparison, navigation, and scanning structured data.

## 2. Research

Rendered desktop styles were measured on 2026-08-01 rather than inferred from screenshots.

| Reference | Body | Headings | Code in light theme | Relevant lesson |
|---|---|---|---|---|
| GitHub Docs | 16px / 24px system UI | 32px H1, 24px H2, same family | `#f6f8fa`, 6px radius, 1px border | Neutral typography and theme-matched code provide the best reference baseline. |
| Starlight Docs | compact sans | same family | `#f5f6f8`; dark becomes `#24272f` | The framework already supports restrained paired code surfaces. |
| Kotlin Docs | 16px / 24px JetBrains Sans | same family, larger hierarchy | pale neutral code surface | Appropriate language-doc density, but headings are larger than this site needs. |
| MDN | 16px / 24px Inter | regular-weight 40px H1, 24px H2 | theme-integrated examples | Simple row-separated tables scan without ornamental containers. |
| Android Developers | 20px / 32px Google Sans | 48px H1 | permanent dark code | Too large and product-led for this site. |
| Stripe / Tailwind | compact body | branded hierarchy | permanent dark code islands | Highly polished, but the strongest source of the visual style the user rejected. |

The selected direction follows GitHub Docs and Starlight for density and color-scheme behavior, with Avinya retained as a small semantic accent.

## 3. Design Principles

1. Content should look authored, not decorated.
2. The same neutral sans family serves navigation, prose, and headings.
3. Light and dark themes are complete environments; code belongs to the active environment.
4. Shape, motion, and color communicate function only.
5. Starlight remains recognizable and maintainable; overrides should remove styling, not create a parallel component system.

## 4. Typography

- Replace Space Grotesk and Inter with a native UI stack: `-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `Noto Sans`, `Helvetica Neue`, `Arial`, sans-serif.
- Keep JetBrains Mono for code because Kotlin punctuation and operators remain clear at small sizes.
- Use the same UI stack for headings, body, sidebar, table of contents, labels, cards, pagination, and search.
- Body: 16px, 1.6 line height, maximum reading measure 45rem.
- H1: 32px / 1.2 desktop and 28px mobile, weight 650–700, normal tracking.
- H2: 24px / 1.3, weight 650–700.
- H3: 19px / 1.4, weight 650–700.
- Sidebar and table of contents: 13px / 1.45–1.5.
- Remove display-font tracking, balanced heading wrapping, uppercase monospace labels, and decorative letter spacing.

## 5. Color and Surfaces

- Light page: white or near-white with a single pale neutral secondary surface.
- Dark page: charcoal, not absolute black, with one slightly raised neutral surface.
- Retain `#ee3a20` as the brand source color, but use accessible darker/lighter text variants for prose links.
- Orange is limited to links, focus rings, the active table-of-contents marker, and selection. Callouts retain their semantic note/tip/caution/danger colors.
- Remove tinted active-row fills and universal orange callout titles.
- Use one-pixel neutral borders. No shadows.

## 6. Code Blocks

- Use paired GitHub-style syntax themes through Expressive Code: light syntax and a pale surface in light mode; dark syntax and a charcoal surface in dark mode.
- Code is 14px with 1.55 line height.
- Use a 6px outer radius and one border around the complete frame.
- Title bar, editor, copy control, and terminal variants share the active theme instead of hard-coded colors.
- Keep Expressive Code contrast correction at 5.5:1 and preserve copy feedback.
- Remove hard-coded permanent-dark frame colors from both Astro configuration and CSS.
- Generated Open Graph cards use bundled Noto Sans assets so social imagery follows the same neutral sans direction without build-time font downloads.

## 7. Navigation and Components

- Sidebar and table-of-contents labels use the regular UI font; group labels are 12px semibold without uppercase tracking.
- Current sidebar navigation uses stronger neutral text only, without an orange marker, tinted rectangle, or nested hierarchy rail.
- The table of contents retains a thin coral active marker because it communicates reading position without competing with the sidebar.
- Header utilities are compact and quiet: the GitHub icon is neutral, the social divider is removed, and the theme selector is borderless while preserving visible keyboard focus.
- Search uses a 6px rectangle rather than a pill.
- Restore conventional unordered-list bullets.
- Cards and pagination use a 6px radius, flat background, and quiet border. Remove lift and spatial hover motion.
- Callouts use subtle semantic surfaces, normal sans titles, and their icons. Remove the universal orange left-rule treatment.

## 8. Tables

- Preserve the accessible `.table-scroll` wrapper and wide-table classification.
- Use 14px text, left-aligned semibold headers, a subtle header surface, and one-pixel row separators.
- Set body cells in the first column to 500 weight.
- Remove row hover tint, shadow, zebra striping, uppercase header typography, and oversized rounding.
- The scroll container receives at most a 6px radius and must remain keyboard focusable.

## 9. Motion

- Remove article entrance animation and all translate/lift effects.
- Permit only 120–150ms color, background-color, border-color, and opacity transitions on interactive controls.
- Preserve `prefers-reduced-motion` as a regression requirement.

## 10. Implementation Boundaries

- Refine the current uncommitted files in place; do not reset them.
- Keep the Markdown table transform, its tests, and the explicit `@astrojs/markdown-remark` processor migration.
- Update `check-theme.mjs` to assert the new typography, paired code backgrounds, reduced radii, absence of entrance animation, table structure, overflow containment, theme-selector focus and switching, and reduced-motion behavior.
- Make both browser gates serve the freshly built `dist/` directory on isolated ephemeral localhost ports unless an explicit `PREVIEW_URL` is supplied.
- Cover the bundled Open Graph typography contract with a focused unit test.
- Keep both visual checks in Section 8 of `scripts/release-readiness.sh`.
- Do not change documentation prose, route structure, Mermaid content, generated API files, SDK code, or GitHub workflows.

## 11. Acceptance

- The light theme contains no permanent dark code islands.
- Headings and prose clearly use the same native sans family.
- No article entrance, card lift, pill search, uppercase mono navigation labels, or decorative list rules remain.
- Header utilities remain compact, and the sidebar uses a text-only current-page state with no nested rail.
- Quickstart, Installation, Consent, Compatibility, and Troubleshooting remain readable at desktop, tablet, and mobile widths.
- Computed syntax-color minimums match the documented samples in both themes: Kotlin 5, XML 5, TOML 3, and Bash 4.
- Tables remain accessible, focusable, and horizontally contained.
- `npm test` passes 13 tests, the Astro build produces 26 pages, and `npm run verify`, `npm run check:theme`, `npm run check:overflow`, `git diff --check`, and the full eight-section `./scripts/release-readiness.sh` finish cleanly with `READINESS: PASS`.
