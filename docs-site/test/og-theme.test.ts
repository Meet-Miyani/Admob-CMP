import { access, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const docsRoot = fileURLToPath(new URL('..', import.meta.url));
const routePath = fileURLToPath(new URL('../src/pages/og/[...route].ts', import.meta.url));

describe('Open Graph theme', () => {
  it('renders with bundled Noto Sans rather than legacy or network fonts', async () => {
    const [route, packageJson] = await Promise.all([
      readFile(routePath, 'utf8'),
      readFile(new URL('../package.json', import.meta.url), 'utf8'),
    ]);

    expect(route).not.toMatch(/Space Grotesk|Inter|api\.fontsource/i);
    expect(route).toContain("families: ['Noto Sans']");
    expect(route).toContain('@fontsource/noto-sans/files/noto-sans-latin-400-normal.woff2');
    expect(route).toContain('@fontsource/noto-sans/files/noto-sans-latin-600-normal.woff2');
    expect(JSON.parse(packageJson).dependencies['@fontsource/noto-sans']).toBe('5.3.0');

    await expect(
      access(`${docsRoot}/node_modules/@fontsource/noto-sans/files/noto-sans-latin-400-normal.woff2`)
    ).resolves.toBeUndefined();
    await expect(
      access(`${docsRoot}/node_modules/@fontsource/noto-sans/files/noto-sans-latin-600-normal.woff2`)
    ).resolves.toBeUndefined();
  });
});
