#!/usr/bin/env node
/**
 * docs-site/scripts/wordcount.mjs
 *
 * Counts PROSE words in .mdx pages. Frontmatter, fenced code blocks, MDX
 * import statements and JSX tags are stripped first, because the 800-1,500
 * word target in the docs plan is about explanatory text — a page can hit
 * 1,200 raw words while being three code dumps and a table.
 *
 * Usage: node scripts/wordcount.mjs src/content/docs/formats/banner.mdx [...]
 * Output: one "<words>\t<path>" line per file.
 */
import { readFileSync } from 'node:fs';
import { argv, exit } from 'node:process';

const files = argv.slice(2);
if (files.length === 0) {
  console.error('usage: node scripts/wordcount.mjs <file.mdx> [more.mdx ...]');
  exit(2);
}

for (const file of files) {
  const raw = readFileSync(file, 'utf8');
  const prose = raw
    // frontmatter
    .replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, ' ')
    // fenced code blocks (``` and ~~~), non-greedy
    .replace(/^```[\s\S]*?^```/gm, ' ')
    .replace(/^~~~[\s\S]*?^~~~/gm, ' ')
    // MDX imports and JSX tags
    .replace(/^import .*$/gm, ' ')
    .replace(/<[^>]*>/g, ' ')
    // inline code and Markdown punctuation
    .replace(/`[^`]*`/g, ' ')
    .replace(/[|#>*_[\]()\-=+/\\]/g, ' ');

  const words = prose.split(/\s+/).filter((w) => /[A-Za-z0-9]/.test(w));
  console.log(`${words.length}\t${file}`);
}
