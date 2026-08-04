#!/usr/bin/env node
/**
 * Generates src/content/docs/reference/diagrams-in-words.mdx from
 * src/components/diagrams/descriptions.json.
 *
 * WHY a generated Starlight PAGE rather than something inside the components:
 * starlight-llms-txt bundles the Markdown source of content-collection entries.
 * It does not execute Astro components, so prose living inside DiagramFigure or
 * a Mermaid .md import would never reach llms-full.txt. A real Markdown page
 * does, verbatim — which is what makes every diagram legible to an LLM.
 *
 * Anchors are emitted as explicit <a id="..."> elements rather than relying on
 * heading slugification, so the caption links in DiagramFigure keep working no
 * matter how a title is later worded.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const SOURCE = new URL('../src/components/diagrams/descriptions.json', import.meta.url);
const TARGET = new URL('../src/content/docs/reference/diagrams-in-words.mdx', import.meta.url);

export function renderProsePage(descriptions) {
  const sections = Object.values(descriptions).map((diagram) => {
    const invariants = diagram.invariants.map((n) => `#${n}`).join(', ');
    return [
      `<a id="${diagram.id}"></a>`,
      '',
      `## ${diagram.title}`,
      '',
      `_Encodes \`admob-cmp/CLAUDE.md\` invariants ${invariants}._`,
      '',
      diagram.prose.join('\n\n'),
    ].join('\n');
  });

  return [
    '---',
    // The title carries the keywords because it drives <title> and the OG card;
    // sidebar.label keeps the navigation entry short. Keep the rendered
    // `<title> | AdMob CMP` under ~60 chars.
    'title: "Architecture diagrams described in words"',
    'description: >-',
    '  Plain-text descriptions of every architecture diagram in the AdMob CMP',
    '  documentation, for screen readers, text-only clients and AI agents.',
    'sidebar:',
    '  label: "Diagrams in words"',
    '---',
    '',
    '{/* GENERATED FILE — do not edit by hand.',
    '    Source: src/components/diagrams/descriptions.json',
    '    Regenerate: npm run diagrams:prose */}',
    '',
    'Every diagram on this site has a text equivalent here, and each one links',
    'back to this page. Nothing on this page is a summary: it is the same',
    'information the diagram carries, written out.',
    '',
    ...sections,
    '',
  ].join('\n');
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const descriptions = JSON.parse(readFileSync(SOURCE, 'utf8'));
  writeFileSync(TARGET, renderProsePage(descriptions), 'utf8');
  console.log(`Wrote ${fileURLToPath(TARGET)}`);
}
