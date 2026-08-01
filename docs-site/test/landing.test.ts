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

const formatListPath = fileURLToPath(
  new URL('../src/components/landing/FormatList.astro', import.meta.url)
);

describe('FormatList.astro contracts', () => {
  const source = readFileSync(formatListPath, 'utf8');

  it('exists as an Astro component', () => {
    expect(existsSync(formatListPath)).toBe(true);
  });

  it('resolves the Screenshot component via guarded import.meta.glob, not a static import', () => {
    expect(source).toMatch(/import\.meta\.glob[\s\S]*?['"]\.\.\/Screenshot\.astro['"]/);
    const staticImportRe =
      /^[ \t]*import\s+(?:\*\s+as\s+\w+|\{[^}]*\}|[\w$]+)\s+from\s+['"]\.\.\/Screenshot\.astro['"]/m;
    expect(
      source.match(staticImportRe),
      'FormatList.astro must not static-import Screenshot.astro — use import.meta.glob'
    ).toBeNull();
  });

  it('import.meta.glob is configured with eager: true so the default export is available at build time', () => {
    const globBlock = source.match(/import\.meta\.glob[\s\S]*?\)/);
    expect(globBlock, 'import.meta.glob call must be present').not.toBeNull();
    expect(globBlock![0]).toMatch(/eager\s*:\s*true/);
  });

  it('reads the default export with optional chaining so the absent-component path is graceful', () => {
    expect(source).toMatch(/screenshotModules\[['\"]\.\.\/Screenshot\.astro['\"]\]\?\.default/);
  });

  it('iterates the imported `formats` array directly, without sort or reorder', () => {
    expect(source).toMatch(/formats\.map\(/);
    expect(source).not.toMatch(/\.sort\s*\(/);
  });

  it('does not filter the formats array down to a subset', () => {
    expect(source).not.toMatch(/formats\.(?:filter|slice)\s*\(/);
  });

  it('renders the API identifier inside <code class="admob-font-mono">', () => {
    expect(source).toMatch(/<code[^>]*class="[^"]*\badmob-font-mono\b[^"]*"[^>]*>\s*\{format\.api\}\s*<\/code>/);
  });

  it('renders a <h3> with a link to the format href so the fragment is preserved', () => {
    expect(source).toMatch(/<h3>\s*<a\s+href=\{format\.href\}>/);
  });

  it('renders exactly one <ol class="landing-formats">', () => {
    const matches = source.match(/<ol\s+class="landing-formats"/g) ?? [];
    expect(matches).toHaveLength(1);
  });

  it('section label is sentence case: no uppercase mono eyebrows in the component', () => {
    expect(source).not.toMatch(/\btext-transform\s*:\s*uppercase\b/i);
    const allCapsLabelRe = />\s*SUPPORTED\s+FORMATS\s*</;
    expect(source.match(allCapsLabelRe), 'section label must not be all-caps').toBeNull();
  });

  it('always renders a landing-format__screenshot frame element on every row', () => {
    expect(source).toMatch(/<div\s+class="landing-format__screenshot/);
  });

  it('renders the neutral placeholder div with role="img" and aria-label when the Screenshot is absent', () => {
    expect(source).toMatch(
      /<div\s+class="landing-format__screenshot landing-format__screenshot--placeholder"[^>]*role="img"[^>]*aria-label="[^"]+"/
    );
  });

  it('landing.css defines aspect-ratio on the placeholder selector so the frame shape is stable', () => {
    const css = readFileSync(landingCssPath, 'utf8');
    expect(css).toMatch(
      /\.landing-format__screenshot--placeholder\s*\{[^}]*aspect-ratio\s*:\s*9\s*\/\s*19\.5/
    );
  });
});

const capabilityMatrixPath = fileURLToPath(
  new URL('../src/components/landing/CapabilityMatrix.astro', import.meta.url)
);
const indexMdxPath = fileURLToPath(
  new URL('../src/content/docs/index.mdx', import.meta.url)
);

describe('CapabilityMatrix.astro contracts', () => {
  const source = readFileSync(capabilityMatrixPath, 'utf8');

  it('exists as an Astro component', () => {
    expect(existsSync(capabilityMatrixPath)).toBe(true);
  });

  it('imports capabilities, capabilityVerifiedOn, and basicAdsRepo from the data module', () => {
    expect(source).toMatch(
      /import\s*\{[^}]*\bcapabilities\b[^}]*\}\s*from\s*['"]\.\.\/\.\.\/data\/landing(?:\.ts)?['"]/
    );
    expect(source).toMatch(
      /import\s*\{[^}]*\bcapabilityVerifiedOn\b[^}]*\}\s*from\s*['"]\.\.\/\.\.\/data\/landing(?:\.ts)?['"]/
    );
    expect(source).toMatch(
      /import\s*\{[^}]*\bbasicAdsRepo\b[^}]*\}\s*from\s*['"]\.\.\/\.\.\/data\/landing(?:\.ts)?['"]/
    );
  });

  it('wraps the table in a focusable, labelled, .table-scroll--wide region', () => {
    const wrapperRe =
      /<div[\s\S]*?class="table-scroll table-scroll--wide"[\s\S]*?tabindex="0"[\s\S]*?role="region"[\s\S]*?aria-label=\{ariaLabel\}[\s\S]*?>/;
    expect(source).toMatch(wrapperRe);
  });

  it('aria-label mentions basic-ads and the verified date', () => {
    expect(source).toMatch(/aria-label=\{ariaLabel\}/);
    const labelBinding = source.match(/const\s+ariaLabel\s*=\s*[`'"`]([^`'"`]+)[`'"`]/);
    expect(labelBinding, 'ariaLabel is a literal string template').not.toBeNull();
    const label = labelBinding![1];
    expect(label).toMatch(/basic-ads/i);
    expect(label).toContain('${capabilityVerifiedOn}');
  });

  it('renders a <thead> with three column headers in the canonical order', () => {
    expect(source).toMatch(/<thead>/);
    expect(source).toMatch(/<th\s+scope="col">Capability<\/th>/);
    expect(source).toMatch(/<th\s+scope="col">AdMob CMP<\/th>/);
    expect(source).toMatch(/<th\s+scope="col">basic-ads<\/th>/);
  });

  it('iterates the imported `capabilities` array, not a hard-coded set of rows', () => {
    expect(source).toMatch(/capabilities\.map\(/);
    const firstRowFirstCell = (capabilities[0] as { capability: string }).capability;
    const lastRowFirstCell = (capabilities[capabilities.length - 1] as { capability: string })
      .capability;
    expect(firstRowFirstCell).toBe('Banner ads');
    expect(lastRowFirstCell).toBe('Maven Central publication');
    expect(source).toMatch(/<th\s+scope="row">\{row\.capability\}<\/th>/);
    expect(source).toMatch(/<td>\{row\.admobCmp\}<\/td>/);
    expect(source).toMatch(/<td>\{row\.basicAds\}<\/td>/);
  });

  it('renders exactly 14 rows from the canonical capabilities array', () => {
    expect(capabilities).toHaveLength(14);
    expect(source).not.toMatch(/<\s*tr\s*>[\s\S]{0,200}<\s*td\s*>\s*Yes\s*<\s*\/\s*td\s*>/);
  });

  it('footnotes the comparison with the verified date and a neutral "Not documented" definition', () => {
    expect(source).toMatch(/class="landing-capabilities__footnote"/);
    expect(source).toMatch(/\{capabilityVerifiedOn\}/);
    const footnote = source.match(
      /class="landing-capabilities__footnote"[^>]*>([\s\S]*?)<\/p>/
    );
    expect(footnote, 'footnote paragraph is present').not.toBeNull();
    const text = footnote![1].replace(/\s+/g, ' ');
    expect(text).toMatch(/Not documented/);
    expect(text).toMatch(/public documentation does not describe/i);
    expect(text).toMatch(/older and larger/i);
    expect(text).toMatch(/earlier publisher|API reference/i);
    expect(text).toMatch(/open an issue/i);
    expect(text).toContain('capabilityVerifiedOn');
  });

  it('rejects marketing or evaluative language in the footnote', () => {
    const denylist = [
      'best',
      'leading',
      'superior',
      'only',
      'incredible',
      'powerful',
      'amazing',
      'fastest',
      'easiest',
      'revolutionary',
      'ultimate',
    ];
    const footnote = source.match(
      /class="landing-capabilities__footnote"[^>]*>([\s\S]*?)<\/p>/
    );
    expect(footnote, 'footnote paragraph is present').not.toBeNull();
    const text = footnote![1].replace(/\s+/g, ' ').toLowerCase();
    for (const word of denylist) {
      const re = new RegExp(`\\b${word}\\b`, 'i');
      expect(re.test(text), `footnote must not contain '${word}'`).toBe(false);
    }
  });

  it('uses sentence-case headings and no uppercase mono eyebrows', () => {
    const allCapsHeadingRe = />\s*CAPABILITY\s+COMPARISON\s*</;
    expect(source.match(allCapsHeadingRe), 'component h2 must not be all-caps').toBeNull();
    expect(source).toMatch(/<h2>Capability comparison<\/h2>/);
  });
});

describe('index.mdx quickstart and iOS note', () => {
  const source = readFileSync(indexMdxPath, 'utf8');

  it('imports the CapabilityMatrix component', () => {
    expect(source).toMatch(
      /import\s+CapabilityMatrix\s+from\s+['"]\.\.\/\.\.\/components\/landing\/CapabilityMatrix\.astro['"]/
    );
  });

  it('renders the CapabilityMatrix component', () => {
    expect(source).toMatch(/<CapabilityMatrix\s*\/>/);
  });

  it('keeps the install line on the canonical Maven coordinate and version 1.1.0', () => {
    expect(source).toMatch(/implementation\(["']dev\.avinya\.ads:admob-cmp:1\.1\.0["']\)/);
  });

  it('preserves the gatherConsentAndInitialize(AdConfig(...)) initialization shape', () => {
    expect(source).toMatch(/adManager\.gatherConsentAndInitialize\(/);
    expect(source).toMatch(/AdConfig\(/);
  });

  it('keeps the iOS note mentioning ConsentMode.InitializeOnlyIfAlreadyAllowed and the UMP -> ATT -> initialize ordering', () => {
    expect(source).toMatch(/ConsentMode\.InitializeOnlyIfAlreadyAllowed/);
    expect(source).toMatch(/UMP consent/i);
    expect(source).toMatch(/ATT[^.]*tracking authorization|tracking authorization/i);
    expect(source).toMatch(/then initialize/i);
  });

  it('links the Quickstart example to /start/quickstart/ with a trailing slash', () => {
    const matches = source.match(/\(\/start\/quickstart\/\)/g) ?? [];
    expect(matches.length, 'multiple quickstart links expected (intro + continue)').toBeGreaterThan(
      0
    );
  });

  it('imports the CompatibilityList, RoadmapSummary, and LandingFooter components', () => {
    expect(source).toMatch(
      /import\s+CompatibilityList\s+from\s+['"]\.\.\/\.\.\/components\/landing\/CompatibilityList\.astro['"]/
    );
    expect(source).toMatch(
      /import\s+RoadmapSummary\s+from\s+['"]\.\.\/\.\.\/components\/landing\/RoadmapSummary\.astro['"]/
    );
    expect(source).toMatch(
      /import\s+LandingFooter\s+from\s+['"]\.\.\/\.\.\/components\/landing\/LandingFooter\.astro['"]/
    );
  });

  it('renders CompatibilityList, RoadmapSummary, and LandingFooter in plan order after CapabilityMatrix', () => {
    const order = ['<CapabilityMatrix', '<CompatibilityList', '<RoadmapSummary', '<LandingFooter']
      .map((tag) => ({ tag, index: source.indexOf(tag) }))
      .map(({ tag, index }) => ({ tag, index }));
    for (const { tag, index } of order) {
      expect(index, `${tag} must appear in index.mdx`).toBeGreaterThan(-1);
    }
    for (let i = 1; i < order.length; i += 1) {
      expect(
        order[i].index,
        `${order[i].tag} must appear after ${order[i - 1].tag}`
      ).toBeGreaterThan(order[i - 1].index);
    }
  });
});

const compatibilityListPath = fileURLToPath(
  new URL('../src/components/landing/CompatibilityList.astro', import.meta.url)
);
const roadmapSummaryPath = fileURLToPath(
  new URL('../src/components/landing/RoadmapSummary.astro', import.meta.url)
);
const landingFooterPath = fileURLToPath(
  new URL('../src/components/landing/LandingFooter.astro', import.meta.url)
);

describe('CompatibilityList.astro contracts', () => {
  const source = readFileSync(compatibilityListPath, 'utf8');

  it('exists as an Astro component', () => {
    expect(existsSync(compatibilityListPath)).toBe(true);
  });

  it('imports InitSequence and PlatformMatrix by their fixed names', () => {
    expect(source).toMatch(
      /import\s+InitSequence\s+from\s+['"]\.\.\/diagrams\/InitSequence\.astro['"]/
    );
    expect(source).toMatch(
      /import\s+PlatformMatrix\s+from\s+['"]\.\.\/diagrams\/PlatformMatrix\.astro['"]/
    );
  });

  it('mentions the klib binary compatibility caveat', () => {
    expect(source.toLowerCase()).toMatch(/klib/);
    expect(source.toLowerCase()).toMatch(/binary compatibility/);
  });

  it('renders landingMeta fields in canonical order (Kotlin, Compose Multiplatform, Android, iOS, Maven coordinate)', () => {
    const expectedKeys = [
      'kotlinVersion',
      'composeMultiplatformVersion',
      'androidMinSdk',
      'iosDeploymentTarget',
      'mavenCoordinate',
    ];
    const positions = expectedKeys.map((key) => ({ key, index: source.indexOf(key) }));
    for (const { key, index } of positions) {
      expect(index, `landingMeta.${key} must be referenced in CompatibilityList.astro`).toBeGreaterThan(
        -1
      );
    }
    for (let i = 1; i < positions.length; i += 1) {
      expect(
        positions[i].index,
        `landingMeta.${positions[i].key} must appear after landingMeta.${positions[i - 1].key}`
      ).toBeGreaterThan(positions[i - 1].index);
    }
  });

  it('renders a <dl> with five pairs and the expected labels', () => {
    expect(source).toMatch(/<dl[^>]*class="landing-compatibility__list"/);
    const dlBlock = source.match(
      /<dl[^>]*class="landing-compatibility__list"[^>]*>([\s\S]*?)<\/dl>/
    );
    expect(dlBlock, 'compatibility <dl> is present').not.toBeNull();
    const body = dlBlock![1];
    for (const label of [
      'Kotlin',
      'Compose Multiplatform',
      'Android',
      'iOS',
      'Maven coordinate',
    ]) {
      const dtRe = new RegExp(`<dt>\\s*${label}\\s*</dt>`);
      expect(dtRe.test(body), `expected <dt>${label}</dt> in compatibility <dl>`).toBe(true);
    }
  });

  it('renders the Maven coordinate inside a <code class="admob-font-mono"> tag', () => {
    expect(source).toMatch(
      /<code[^>]*class="[^"]*\badmob-font-mono\b[^"]*"[^>]*>\s*\{landingMeta\.mavenCoordinate\}\s*<\/code>/
    );
  });

  it('renders the InitSequence and PlatformMatrix components once each', () => {
    const initCount = (source.match(/<InitSequence\s*\/>/g) ?? []).length;
    const platformCount = (source.match(/<PlatformMatrix\s*\/>/g) ?? []).length;
    expect(initCount).toBe(1);
    expect(platformCount).toBe(1);
  });

  it('uses sentence-case headings and no uppercase mono eyebrows', () => {
    expect(source).not.toMatch(/\btext-transform\s*:\s*uppercase\b/i);
    expect(source).toMatch(/<h2>[^<]+<\/h2>/);
  });
});

describe('RoadmapSummary.astro contracts', () => {
  const source = readFileSync(roadmapSummaryPath, 'utf8');

  it('exists as an Astro component', () => {
    expect(existsSync(roadmapSummaryPath)).toBe(true);
  });

  it('imports roadmapItems from the data module', () => {
    expect(source).toMatch(
      /import\s*\{[^}]*\broadmapItems\b[^}]*\}\s*from\s*['"]\.\.\/\.\.\/data\/landing(?:\.ts)?['"]/
    );
  });

  it('renders the two canonical roadmap titles via the data module', () => {
    expect(source).toMatch(/\{item\.title\}/);
    const titles = roadmapItems.map((i) => i.title);
    expect(titles).toContain('Swift Package Manager dependency import');
    expect(titles).toContain('Native video events on Android');
  });

  it('does not render roadmap status text in uppercase', () => {
    const denylist = ['GATED', 'BLOCKED'];
    for (const word of denylist) {
      const re = new RegExp(`\\b${word}\\b`);
      expect(re.test(source), `roadmap status must not be the all-caps label '${word}'`).toBe(
        false
      );
    }
    expect(source).not.toMatch(/\btext-transform\s*:\s*uppercase\b/i);
  });

  it('links to /project/roadmap/ with a trailing slash', () => {
    expect(source).toMatch(/href="\/project\/roadmap\/"/);
  });

  it('does not render a status pill, badge, or uppercase chip element', () => {
    expect(source).not.toMatch(/class="[^"]*\b(?:pill|badge|chip)\b/i);
  });
});

describe('LandingFooter.astro contracts', () => {
  const source = readFileSync(landingFooterPath, 'utf8');

  it('exists as an Astro component', () => {
    expect(existsSync(landingFooterPath)).toBe(true);
  });

  it('imports trademarkStatement and repoUrl from the data module', () => {
    expect(source).toMatch(
      /import\s*\{[^}]*\btrademarkStatement\b[^}]*\}\s*from\s*['"]\.\.\/\.\.\/data\/landing(?:\.ts)?['"]/
    );
    expect(source).toMatch(
      /import\s*\{[^}]*\brepoUrl\b[^}]*\}\s*from\s*['"]\.\.\/\.\.\/data\/landing(?:\.ts)?['"]/
    );
  });

  it('renders the trademark statement via the data module (rendered value matches the verbatim contract)', () => {
    expect(source).toMatch(/<p[^>]*class="landing-footer__legal"[^>]*>\s*\{trademarkStatement\}\s*<\/p>/);
    expect(trademarkStatement).toBe(
      'Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.'
    );
  });

  it('renders exactly the seven required links in the compact list', () => {
    const listMatch = source.match(
      /<ul[^>]*class="landing-footer__links"[^>]*>([\s\S]*?)<\/ul>/
    );
    expect(listMatch, 'landing-footer links <ul> is present').not.toBeNull();
    const body = listMatch![1];
    const items = body.match(/<li>[\s\S]*?<\/li>/g) ?? [];
    expect(items, 'seven <li> items expected').toHaveLength(7);
    const labels = items.map((li) => li.replace(/<[^>]+>/g, '').trim());
    expect(labels).toEqual([
      'Quickstart',
      'Installation',
      'Compatibility',
      'Roadmap',
      'API reference',
      'GitHub',
      'Apache-2.0 license',
    ]);
  });

  it('Quickstart, Installation, Compatibility, Roadmap, and API reference all point at internal trailing-slash routes', () => {
    const listMatch = source.match(
      /<ul[^>]*class="landing-footer__links"[^>]*>([\s\S]*?)<\/ul>/
    );
    const body = listMatch![1];
    expect(body).toMatch(/href="\/start\/quickstart\/"/);
    expect(body).toMatch(/href="\/start\/installation\/"/);
    expect(body).toMatch(/href="\/reference\/compatibility\/"/);
    expect(body).toMatch(/href="\/project\/roadmap\/"/);
    expect(body).toMatch(/href="\/api\/"/);
  });

  it('GitHub link uses the canonical repoUrl value', () => {
    const listMatch = source.match(
      /<ul[^>]*class="landing-footer__links"[^>]*>([\s\S]*?)<\/ul>/
    );
    const body = listMatch![1];
    expect(body).toMatch(/<li>\s*<a\s+href=\{repoUrl\}>GitHub<\/a>\s*<\/li>/);
  });

  it('Apache-2.0 license link points at the canonical Apache URL', () => {
    const listMatch = source.match(
      /<ul[^>]*class="landing-footer__links"[^>]*>([\s\S]*?)<\/ul>/
    );
    const body = listMatch![1];
    expect(body).toMatch(
      /href="https:\/\/www\.apache\.org\/licenses\/LICENSE-2\.0\.txt"/
    );
  });

  it('renders the trademark statement in a <p class="landing-footer__legal">', () => {
    expect(source).toMatch(
      /<p[^>]*class="landing-footer__legal"[^>]*>\s*\{trademarkStatement\}\s*<\/p>/
    );
  });

  it('does not duplicate the site footer with a five-column marketing layout', () => {
    expect(source).not.toMatch(/footer-cols-5/);
    expect(source).not.toMatch(/class="[^"]*\bcolumns?\b/i);
    expect((source.match(/<ul\b/g) ?? []).length).toBe(1);
  });
});

describe('landing.css footer and roadmap rules', () => {
  const css = readFileSync(landingCssPath, 'utf8');

  it('defines a border-top rule for the landing-footer class', () => {
    const block = css.match(/\.landing-footer\s*\{([^}]*)\}/);
    expect(block, '.landing-footer rule must be present').not.toBeNull();
    expect(block![1]).toMatch(/border-top\s*:\s*1px\s+solid\s+var\(--admob-hair\)/);
  });

  it('defines a flex layout for the landing-footer__links list', () => {
    const block = css.match(/\.landing-footer__links\s*\{([^}]*)\}/);
    expect(block, '.landing-footer__links rule must be present').not.toBeNull();
    expect(block![1]).toMatch(/display\s*:\s*flex/);
    expect(block![1]).toMatch(/flex-wrap\s*:\s*wrap/);
    expect(block![1]).toMatch(/list-style\s*:\s*none/);
  });

  it('styles the roadmap item title semibold (no uppercase)', () => {
    const block = css.match(/\.landing-roadmap__item-title\s*\{([^}]*)\}/);
    expect(block, '.landing-roadmap__item-title rule must be present').not.toBeNull();
    expect(block![1]).toMatch(/font-weight\s*:\s*600/);
    expect(block![1]).not.toMatch(/text-transform\s*:\s*uppercase/i);
  });

  it('styles the roadmap item status with muted text and 400 weight', () => {
    const block = css.match(/\.landing-roadmap__item-status\s*\{([^}]*)\}/);
    expect(block, '.landing-roadmap__item-status rule must be present').not.toBeNull();
    expect(block![1]).toMatch(/font-weight\s*:\s*400/);
    expect(block![1]).toMatch(/color\s*:\s*var\(--admob-slate\)/);
  });

  it('uses a hairline border-bottom on roadmap items and clears it on the last child', () => {
    const block = css.match(/\.landing-roadmap__item\s*\{([^}]*)\}/);
    expect(block, '.landing-roadmap__item rule must be present').not.toBeNull();
    expect(block![1]).toMatch(/border-bottom\s*:\s*1px\s+solid\s+var\(--admob-hair\)/);
    const lastBlock = css.match(/\.landing-roadmap__item:last-child\s*\{([^}]*)\}/);
    expect(lastBlock, '.landing-roadmap__item:last-child rule must be present').not.toBeNull();
    expect(lastBlock![1]).toMatch(/border-bottom\s*:\s*0/);
  });
});
