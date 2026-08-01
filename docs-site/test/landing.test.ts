import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';
import {
  basicAdsRepo,
  capabilities,
  capabilityVerifiedOn,
  formats,
  landingMeta,
  repoUrl,
  roadmapItems,
  trademarkStatement,
} from '../src/data/landing';

const repoRoot = fileURLToPath(new URL('../../', import.meta.url));
const rootGradleProps = join(repoRoot, 'gradle.properties');
const pluginGradleProps = join(repoRoot, 'admob-cmp-gradle-plugin', 'gradle.properties');
const versionsToml = join(repoRoot, 'gradle', 'libs.versions.toml');
const coreBuildGradleKts = join(repoRoot, 'admob-cmp-core', 'build.gradle.kts');
const landingComponentsDir = fileURLToPath(
  new URL('../src/components/landing', import.meta.url)
);
const landingCssPath = fileURLToPath(
  new URL('../src/styles/landing.css', import.meta.url)
);

function readVersionName(file: string): string {
  const contents = readFileSync(file, 'utf8');
  const match = contents.match(/^VERSION_NAME\s*=\s*(.+?)\s*$/m);
  if (!match) throw new Error(`VERSION_NAME not found in ${file}`);
  return match[1];
}

function readTomlString(file: string, key: string): string {
  const contents = readFileSync(file, 'utf8');
  const re = new RegExp(`^${key}\\s*=\\s*"([^"]+)"\\s*$`, 'm');
  const match = contents.match(re);
  if (!match) throw new Error(`${key} not found in ${file}`);
  return match[1];
}

interface CssBlock {
  selector: string;
  body: string;
}

function extractCssBlocks(contents: string): CssBlock[] {
  const stripped = contents.replace(/\/\*[\s\S]*?\*\//g);
  const blocks: CssBlock[] = [];
  const re = /([^{}]+)\{([^{}]*)\}/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(stripped)) !== null) {
    blocks.push({ selector: match[1].trim(), body: match[2] });
  }
  return blocks;
}

function extractStyleBlocks(astroSource: string): CssBlock[] {
  const blocks: CssBlock[] = [];
  const re = /<style[^>]*>([\s\S]*?)<\/style>/gi;
  let match: RegExpExecArray | null;
  while ((match = re.exec(astroSource)) !== null) {
    for (const block of extractCssBlocks(match[1])) {
      blocks.push(block);
    }
  }
  return blocks;
}

const CSS_COMMENT = /\/\*[\s\S]*?\*\//g;

function isCodeCoordinateSelector(selector: string): boolean {
  if (selector.includes('.admob-font-mono')) return true;
  if (selector.includes('.admob-mono')) return true;
  if (/(?:^|\s|,)code(?:\s|,|\.|:|>|\+|~|$)/.test(selector)) return true;
  if (/(?:^|\s|,)pre(?:\s|,|\.|:|>|\+|~|$)/.test(selector)) return true;
  return false;
}

function listFilesRecursive(dir: string): string[] {
  if (!existsSync(dir)) return [];
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      out.push(...listFilesRecursive(full));
    } else {
      out.push(full);
    }
  }
  return out;
}

const STYLING_VIOLATION_CHECKS = {
  color: /#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(|oklch\(|oklab\(/,
  transform: /(?<![\w-])transform\s*:/,
  animation: /\banimation\s*:|@keyframes\b/,
  gradient: /linear-gradient\(|radial-gradient\(|conic-gradient\(|\bgradient\(/,
  textTransformUpper: /\btext-transform\s*:\s*uppercase\b/i,
};

function collectStyleContexts(source: string, isAstro: boolean): string {
  if (!isAstro) return source;
  const contexts: string[] = [];
  const re = /<style[^>]*>([\s\S]*?)<\/style>/gi;
  let match: RegExpExecArray | null;
  while ((match = re.exec(source)) !== null) {
    contexts.push(match[1]);
  }
  const inlineRe = /\sstyle\s*=\s*"([^"]*)"/g;
  while ((match = inlineRe.exec(source)) !== null) {
    contexts.push(match[1]);
  }
  return contexts.join('\n');
}

function findStylingViolations(source: string, isAstro: boolean): string[] {
  const cssOnly = collectStyleContexts(source, isAstro).replace(CSS_COMMENT, '');
  const blocks = isAstro ? extractStyleBlocks(source) : extractCssBlocks(cssOnly);
  const violations: string[] = [];

  const colorHit = cssOnly.match(STYLING_VIOLATION_CHECKS.color);
  if (colorHit) {
    violations.push(`literal color '${colorHit[0]}'`);
  }
  const transformHit = cssOnly.match(STYLING_VIOLATION_CHECKS.transform);
  if (transformHit) {
    violations.push(`transform declaration '${transformHit[0]}'`);
  }
  const animationHit = cssOnly.match(STYLING_VIOLATION_CHECKS.animation);
  if (animationHit) {
    violations.push(`animation declaration '${animationHit[0]}'`);
  }
  const gradientHit = cssOnly.match(STYLING_VIOLATION_CHECKS.gradient);
  if (gradientHit) {
    violations.push(`gradient '${gradientHit[0]}'`);
  }
  const textTransformHit = cssOnly.match(STYLING_VIOLATION_CHECKS.textTransformUpper);
  if (textTransformHit) {
    violations.push(`text-transform: uppercase`);
  }

  const allowedBoxShadow = /^\s*(none|0|initial|unset)\s*$/i;
  for (const block of blocks) {
    for (const declaration of block.body.split(';')) {
      const trimmed = declaration.trim();
      if (!trimmed) continue;
      if (/^box-shadow\s*:/i.test(trimmed)) {
        const value = trimmed.split(':').slice(1).join(':').trim();
        if (!allowedBoxShadow.test(value)) {
          violations.push(`box-shadow '${value}'`);
        }
      }
      if (/^font-family\s*:/i.test(trimmed) && !isCodeCoordinateSelector(block.selector)) {
        violations.push(`font-family outside code/coordinate selector '${block.selector}'`);
      }
    }
  }

  return violations;
}

function checkFileForStylingViolations(filePath: string): string[] {
  const raw = readFileSync(filePath, 'utf8');
  const isAstro = filePath.endsWith('.astro');
  const isCss = filePath.endsWith('.css');
  if (!isAstro && !isCss) return [];
  return findStylingViolations(raw, isAstro);
}

describe('landing.ts data module exports are wired', () => {
  it('exports six format records, capability rows, and roadmap items as defined types', () => {
    expect(formats).toHaveLength(6);
    expect(capabilities.length).toBeGreaterThan(0);
    expect(roadmapItems).toHaveLength(2);
  });
});

describe('gradle version lockstep', () => {
  it('root gradle.properties and plugin gradle.properties share the same VERSION_NAME', () => {
    expect(readVersionName(rootGradleProps)).toBe(readVersionName(pluginGradleProps));
  });

  it('landingMeta version strings match the gradle VERSION_NAME', () => {
    const version = readVersionName(rootGradleProps);
    expect(landingMeta.mavenCoordinate).toContain(version);
    expect(landingMeta.gradlePlugin).toContain(version);
  });
});

describe('landingMeta toolchain and platform facts match build configuration', () => {
  it('kotlin version matches gradle/libs.versions.toml', () => {
    expect(landingMeta.kotlinVersion).toBe(readTomlString(versionsToml, 'kotlin'));
  });

  it('compose multiplatform version matches gradle/libs.versions.toml', () => {
    expect(landingMeta.composeMultiplatformVersion).toBe(
      readTomlString(versionsToml, 'composeMultiplatform')
    );
  });

  it('android minSdk is the integer 26 from gradle/libs.versions.toml', () => {
    expect(landingMeta.androidMinSdk).toBe(26);
    expect(Number(readTomlString(versionsToml, 'android-minSdk'))).toBe(26);
  });

  it('iOS deployment target is 15.0 and the build file pins it', () => {
    expect(landingMeta.iosDeploymentTarget).toBe('15.0');
    const buildFile = readFileSync(coreBuildGradleKts, 'utf8');
    expect(buildFile).toMatch(/osVersionMin\.ios_arm64=15\.0/);
    expect(buildFile).toMatch(/osVersionMin=15\.0/);
  });

  it('license is the Apache License 2.0', () => {
    expect(landingMeta.licenseName).toBe('Apache License 2.0');
  });
});

describe('formats contract', () => {
  const EXPECTED_ORDER = [
    'banner',
    'interstitial',
    'rewarded',
    'rewarded-interstitial',
    'app-open',
    'native',
  ] as const;

  it('contains exactly the six canonical slugs in the canonical order', () => {
    expect(formats.map((f) => f.slug)).toEqual([...EXPECTED_ORDER]);
  });

  it('uses unique slugs', () => {
    const slugs = formats.map((f) => f.slug);
    expect(new Set(slugs).size).toBe(slugs.length);
  });

  it('every internal href ends with a trailing slash or a section fragment', () => {
    for (const f of formats) {
      const isInternal = f.href.startsWith('/');
      expect(isInternal, `${f.slug} href ${f.href} is not internal`).toBe(true);
      const terminal = f.href.endsWith('/') || /\/#[A-Za-z0-9_-]+$/.test(f.href);
      expect(
        terminal,
        `${f.slug} href ${f.href} must end with / or a #fragment`
      ).toBe(true);
    }
  });

  it('screenshot is string-or-null, crop is top/center/bottom', () => {
    for (const f of formats) {
      expect(['top', 'center', 'bottom']).toContain(f.crop);
      const ok = f.screenshot === null || typeof f.screenshot === 'string';
      expect(ok, `${f.slug} screenshot must be string or null`).toBe(true);
    }
  });
});

describe('legal and repository contracts', () => {
  it('trademark statement is verbatim', () => {
    expect(trademarkStatement).toBe(
      'Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.'
    );
  });

  it('basic-ads repo is the canonical URL', () => {
    expect(basicAdsRepo).toBe('https://github.com/LexiLabs-App/basic-ads');
  });

  it('repo URL is the canonical GitHub URL', () => {
    expect(repoUrl).toBe('https://github.com/Meet-Miyani/admob-compose-multiplatform');
  });

  it('capabilityVerifiedOn is the dated 31 July 2026 marker', () => {
    expect(capabilityVerifiedOn).toBe('31 July 2026');
  });
});

describe('capabilities contract', () => {
  it('contains the canonical 14 capability rows', () => {
    expect(capabilities).toHaveLength(14);
  });

  it('every row has a non-empty capability and non-empty admobCmp value', () => {
    for (const row of capabilities) {
      expect(row.capability.length).toBeGreaterThan(0);
      expect(row.admobCmp.length).toBeGreaterThan(0);
    }
  });
});

describe('roadmap contract', () => {
  it('has exactly two items with non-empty title and status', () => {
    expect(roadmapItems).toHaveLength(2);
    for (const item of roadmapItems) {
      expect(item.title.length).toBeGreaterThan(0);
      expect(item.status.length).toBeGreaterThan(0);
    }
  });

  it('titles are the two canonical roadmap items', () => {
    const titles = roadmapItems.map((i) => i.title);
    expect(titles).toContain('Swift Package Manager dependency import');
    expect(titles).toContain('Native video events on Android');
  });
});

describe('landing component styling-boundary rules', () => {
  let componentFiles: string[] = [];
  let cssExists = false;

  beforeAll(() => {
    componentFiles = listFilesRecursive(landingComponentsDir);
    cssExists = existsSync(landingCssPath);
    if (cssExists) componentFiles.push(landingCssPath);
  });

  it('tolerates the missing landing directory and landing.css', () => {
    if (componentFiles.length === 0) {
      expect(componentFiles).toEqual([]);
    }
  });

  it('no landing file uses literal colors, transforms, animations, gradients, or uppercase text-transform', () => {
    for (const file of componentFiles) {
      const violations = checkFileForStylingViolations(file);
      expect(violations, `${file} contains ${violations.join(', ')}`).toEqual([]);
    }
  });

  it('transform scan does not flag text-transform: lowercase (anchor against hyphen lookalike)', () => {
    const violations = findStylingViolations('.x { text-transform: lowercase; }', false);
    expect(violations).toEqual([]);
  });

  it('transform scan still flags a real transform: rotate(...) declaration', () => {
    const violations = findStylingViolations('.x { transform: rotate(5deg); }', false);
    expect(violations.some((v) => v.startsWith('transform declaration'))).toBe(true);
  });
});

describe('landing components do not import PNGs directly', () => {
  let componentFiles: string[] = [];

  beforeAll(() => {
    componentFiles = listFilesRecursive(landingComponentsDir).filter((f) =>
      f.endsWith('.astro') || f.endsWith('.ts') || f.endsWith('.tsx')
    );
  });

  it('no landing source file imports a .png asset directly', () => {
    const offenders: string[] = [];
    for (const file of componentFiles) {
      const source = readFileSync(file, 'utf8');
      if (/\bimport\s+[^;]*\.png\b/.test(source)) {
        offenders.push(file);
      }
    }
    expect(offenders, `landing files import .png directly: ${offenders.join(', ')}`).toEqual([]);
  });
});
