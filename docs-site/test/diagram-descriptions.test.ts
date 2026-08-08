import { existsSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import descriptions from '../src/components/diagrams/descriptions.json';

/** The contract Plans 3 and 5 import against: diagram id → component filename. */
const COMPONENTS: Record<string, string> = {
  'module-map': 'ModuleMap.astro',
  'init-sequence': 'InitSequence.astro',
  'full-screen-lifecycle': 'FullScreenLifecycle.astro',
  'native-session-lifecycle': 'NativeSessionLifecycle.astro',
  'banner-geometry': 'BannerGeometry.astro',
  'consent-decision-tree': 'ConsentDecisionTree.astro',
  'retry-timeline': 'RetryTimeline.astro',
  'platform-matrix': 'PlatformMatrix.astro',
};

const entries = descriptions as Record<string, { id: string; title: string; desc: string; invariants: number[]; prose: string[] }>;

describe('diagram descriptions', () => {
  it('describes exactly the eight diagrams Plans 3 and 5 import', () => {
    expect(Object.keys(entries).sort()).toEqual(Object.keys(COMPONENTS).sort());
  });

  it.each(Object.entries(COMPONENTS))('%s has a component named %s', (id, filename) => {
    const path = new URL(`../src/components/diagrams/${filename}`, import.meta.url);
    expect(existsSync(path), `${filename} is missing`).toBe(true);
  });

  it.each(Object.keys(COMPONENTS))('%s has a complete description', (id) => {
    const entry = entries[id];
    expect(entry.id).toBe(id);
    expect(entry.title.length).toBeGreaterThan(10);
    expect(entry.desc.length).toBeGreaterThan(40);
    // At least one CLAUDE.md invariant, and every number must be a real one (1..12).
    expect(entry.invariants.length).toBeGreaterThan(0);
    for (const n of entry.invariants) {
      expect(n).toBeGreaterThanOrEqual(1);
      expect(n).toBeLessThanOrEqual(12);
    }
    // The text alternative must actually be an alternative, not a caption.
    expect(entry.prose.length).toBeGreaterThanOrEqual(3);
    expect(entry.prose.join(' ').length).toBeGreaterThan(600);
  });
});
