#!/usr/bin/env node
/**
 * docs-site/scripts/audit-content.mjs
 *
 * Enforces the content rules the Astro build cannot: description length,
 * question-shaped H2s, working internal links, no surviving diagram
 * placeholders, and a "Where to next?" section on every guide page.
 *
 * Usage: node scripts/audit-content.mjs
 * Exits 1 on any violation, printing one line per problem.
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { exit } from 'node:process';

const DOCS = 'src/content/docs';
const DIAGRAMS = 'src/components/diagrams';
const problems = [];

/** Every .mdx page under src/content/docs, excluding the Plan 5 landing page. */
function pages(dir = DOCS) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...pages(path));
    else if (entry.name.endsWith('.mdx') && path !== join(DOCS, 'index.mdx')) out.push(path);
  }
  return out;
}

/** Set of every valid canonical URL, derived from the file tree itself. */
const files = pages();
const validUrls = new Set(
  files.map((f) => '/' + f.slice(DOCS.length + 1).replace(/\.mdx$/, '') + '/')
);
validUrls.add('/');
validUrls.add('/api/');

/**
 * Pages exempt from the "every H2 is a question" rule. The generated
 * diagrams-in-words reference intentionally mirrors the diagram titles, which
 * are statements; it is a prose reference, not a guide.
 */
const REFERENCE_PAGES = new Set(['/reference/diagrams-in-words/']);

for (const file of files) {
  const raw = readFileSync(file, 'utf8');
  const fm = raw.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!fm) {
    problems.push(`${file}: no frontmatter`);
    continue;
  }
  const front = fm[1];

  const desc = front.match(/^description:\s*(.+)$/m);
  if (!desc) problems.push(`${file}: no description`);
  else if (desc[1].trim().length > 160)
    problems.push(`${file}: description is ${desc[1].trim().length} chars (max 160)`);

  const body = raw.slice(fm[0].length);
  const pageUrl = '/' + file.slice(DOCS.length + 1).replace(/\.mdx$/, '') + '/';
  const isReference = REFERENCE_PAGES.has(pageUrl);

  if (!isReference) {
    // Every H2 must be a question, except the standard closing section.
    for (const line of body.split('\n')) {
      if (!line.startsWith('## ')) continue;
      const heading = line.slice(3).trim();
      if (heading === 'Where to next?') continue;
      if (!heading.endsWith('?')) problems.push(`${file}: H2 is not a question — "${heading}"`);
    }

    if (!body.includes('## Where to next?')) problems.push(`${file}: no "Where to next?" section`);
  }

  // Internal links must resolve to a real page and end in a slash.
  for (const m of body.matchAll(/href="(\/[^"]*)"|\]\((\/[^)]*)\)/g)) {
    const url = m[1] ?? m[2];
    if (url.startsWith('/llms')) continue;
    if (!url.endsWith('/')) problems.push(`${file}: internal link missing trailing slash — ${url}`);
    else if (!validUrls.has(url)) problems.push(`${file}: internal link has no page — ${url}`);
  }
}

// No diagram may still be a Plan 3 placeholder.
if (existsSync(DIAGRAMS)) {
  for (const entry of readdirSync(DIAGRAMS, { withFileTypes: true })) {
    if (!entry.isFile()) continue;
    const path = join(DIAGRAMS, entry.name);
    if (readFileSync(path, 'utf8').includes('diagram-placeholder'))
      problems.push(`${path}: still a Plan 3 placeholder — Plan 4 must replace it`);
  }
}

if (problems.length === 0) {
  console.log(`AUDIT OK — ${files.length} pages`);
  exit(0);
}
for (const p of problems) console.error(p);
console.error(`\n${problems.length} problem(s)`);
exit(1);
