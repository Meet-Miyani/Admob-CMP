import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import descriptions from '../src/components/diagrams/descriptions.json';
import { renderProsePage } from '../scripts/generate-diagram-prose.mjs';

describe('diagrams-in-words.mdx', () => {
  it('is up to date with descriptions.json', () => {
    const committed = readFileSync(
      new URL('../src/content/docs/reference/diagrams-in-words.mdx', import.meta.url),
      'utf8'
    );
    expect(
      committed,
      'diagrams-in-words.mdx is stale — run `npm run diagrams:prose` and commit the result'
    ).toBe(renderProsePage(descriptions));
  });

  it('carries an anchor for every diagram id', () => {
    const committed = readFileSync(
      new URL('../src/content/docs/reference/diagrams-in-words.mdx', import.meta.url),
      'utf8'
    );
    for (const id of Object.keys(descriptions)) {
      expect(committed).toContain(`<a id="${id}"></a>`);
    }
  });
});
