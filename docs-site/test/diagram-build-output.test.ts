import { existsSync, readFileSync } from 'node:fs';
import { beforeAll, describe, expect, it } from 'vitest';
import descriptions from '../src/components/diagrams/descriptions.json';

const GALLERY = new URL('../dist/dev/diagram-gallery/index.html', import.meta.url);
let html = '';

beforeAll(() => {
  if (!existsSync(GALLERY)) {
    throw new Error('dist/dev/diagram-gallery/index.html is missing — run `npm run build` first');
  }
  html = readFileSync(GALLERY, 'utf8');
});

const ids = Object.keys(descriptions);
// PlatformMatrix is a <table>, not an <svg>, so it is exempt from the SVG contract.
const svgIds = ids.filter((id) => id !== 'platform-matrix');

describe('every diagram is present in the static HTML', () => {
  it.each(ids)('%s renders a figure', (id) => {
    expect(html).toContain(`data-diagram="${id}"`);
  });

  it.each(ids)('%s links to its prose equivalent', (id) => {
    expect(html).toContain(`/reference/diagrams-in-words/#${id}`);
  });
});

describe('accessibility contract', () => {
  it.each(svgIds)('%s declares role=img with a title and desc', (id) => {
    expect(html).toContain(`${id}-title ${id}-desc`);
    expect(html).toContain(`id="${id}-title"`);
    expect(html).toContain(`id="${id}-desc"`);
  });

  it('the platform matrix ships as a real table with scoped headers', () => {
    expect(html).toContain('<table class="dg-table">');
    expect(html).toContain('scope="row"');
    expect(html).toContain('scope="col"');
  });
});

describe('build-time static SVG only', () => {
  it('contains inline svg elements, not img references to svg files', () => {
    expect(html).toContain('<svg');
    expect(html).not.toMatch(/<img[^>]+\.svg/);
  });

  it('left no unprocessed mermaid fence behind', () => {
    expect(html).not.toContain('class="mermaid"');
    expect(html).not.toContain('language-mermaid');
  });

  it('ships no client-side renderer', () => {
    expect(html.toLowerCase()).not.toContain('mermaid.esm');
    expect(html.toLowerCase()).not.toContain('mermaid.min.js');
  });

  it('exposes diagram text to crawlers rather than burying it in an image', () => {
    // One representative string per hand-authored diagram.
    for (const needle of [
      'FullScreenSlotCore',
      'maxSize = available + in-use',
      'UIScreen.mainScreen',
      'NETWORK_ERROR',
      'upstream SDK gap',
    ]) {
      expect(html).toContain(needle);
    }
  });
});

describe('overflow contract', () => {
  it('every diagram sits in a scroll region carrying its own min-width', () => {
    expect(html).toContain('class="dg-scroll"');
    expect(html).toContain('--dg-min-w:');
  });
});
