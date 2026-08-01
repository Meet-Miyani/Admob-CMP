import { describe, expect, it } from 'vitest';
import {
  admobPalette,
  contrastRatio,
  diagramColourRoles,
  diagramRule,
  resolveRole,
} from './helpers/css-tokens';

const THEMES = ['light', 'dark'] as const;

/**
 * Every pairing the diagrams actually paint. `min` is the WCAG 2.1 threshold
 * that applies to how the pairing is used: 4.5 for normal-size text (1.4.3),
 * 3 for graphical objects and component boundaries (1.4.11).
 */
const PAIRINGS = [
  { fg: '--dg-ink', bg: '--dg-paper', min: 4.5, use: 'primary text on the diagram background' },
  { fg: '--dg-ink', bg: '--dg-node', min: 4.5, use: 'primary text inside a node' },
  { fg: '--dg-muted', bg: '--dg-paper', min: 4.5, use: 'secondary text on the background' },
  { fg: '--dg-muted', bg: '--dg-node', min: 4.5, use: 'sub-labels inside a node' },
  { fg: '--dg-stroke', bg: '--dg-paper', min: 3, use: 'node borders and edges' },
  { fg: '--dg-stroke', bg: '--dg-node', min: 3, use: 'dividers drawn inside a node' },
  { fg: '--dg-accent', bg: '--dg-paper', min: 3, use: 'emphasised stroke (non-text only)' },
  { fg: '--dg-accent', bg: '--dg-node', min: 3, use: 'emphasised stroke (non-text only)' },
] as const;

/**
 * SVG text classes. `--admob-accent` measures 3.37:1 on `--admob-surface` in the
 * light theme, which fails AA for normal-size text, so no text class may use it.
 */
const TEXT_RULES = ['.dg-title', '.dg-label', '.dg-sub', '.dg-note', '.dg-mono'] as const;
const ALLOWED_TEXT_FILLS = ['var(--dg-ink)', 'var(--dg-muted)'];

describe.each(THEMES)('diagram palette — %s theme', (theme) => {
  const palette = admobPalette(theme);
  const roles = diagramColourRoles();

  it.each(PAIRINGS)('$fg on $bg meets $min:1 ($use)', ({ fg, bg, min }) => {
    const ratio = contrastRatio(resolveRole(fg, roles, palette), resolveRole(bg, roles, palette));
    expect(ratio).toBeGreaterThanOrEqual(min);
  });

  it('defines every colour role as a bare var() reference to an --admob-* token', () => {
    for (const role of Object.keys(roles)) {
      expect(() => resolveRole(role, roles, palette)).not.toThrow();
    }
  });
});

describe('diagram text never uses the accent colour', () => {
  it.each(TEXT_RULES)('%s fills with ink or muted', (selector) => {
    expect(ALLOWED_TEXT_FILLS).toContain(diagramRule(selector).fill);
  });

  it('the HTML caption uses ink or muted, not accent', () => {
    expect(ALLOWED_TEXT_FILLS.map((v) => v.replace('--dg', '--dg'))).toContain(
      diagramRule('.dg-caption').color
    );
  });
});
