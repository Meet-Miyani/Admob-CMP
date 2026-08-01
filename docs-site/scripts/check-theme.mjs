#!/usr/bin/env node
import { readFile, stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import path from 'node:path';
import { chromium } from 'playwright';

const DIST = path.resolve('dist');
let BASE = process.env.PREVIEW_URL;
const failures = [];

function check(condition, message) {
  if (condition) {
    console.log(`  ok   ${message}`);
  } else {
    console.error(`  FAIL ${message}`);
    failures.push(message);
  }
}

async function hasDist() {
  try {
    await stat(path.join(DIST, 'index.html'));
    return true;
  } catch {
    return false;
  }
}

const MIME = {
  '.css': 'text/css',
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
};

let server;
if (!(await hasDist())) {
  console.error('dist/ is missing. Run npm run build before npm run check:theme.');
  process.exit(2);
}

if (BASE) {
  try {
    await fetch(BASE);
  } catch (error) {
    console.error(`PREVIEW_URL=${process.env.PREVIEW_URL} is unreachable: ${error.message}`);
    process.exit(2);
  }
} else {
  server = await new Promise((resolve, reject) => {
    const instance = createServer(async (request, response) => {
      const pathname = decodeURIComponent(new URL(request.url, 'http://127.0.0.1').pathname);
      let file = path.resolve(DIST, `.${pathname}`);
      if (file !== DIST && !file.startsWith(`${DIST}${path.sep}`)) {
        response.writeHead(404);
        response.end('Not found');
        return;
      }
      try {
        if ((await stat(file)).isDirectory()) file = path.join(file, 'index.html');
        response.writeHead(200, { 'Content-Type': MIME[path.extname(file)] ?? 'application/octet-stream' });
        response.end(await readFile(file));
      } catch {
        response.writeHead(404);
        response.end('Not found');
      }
    });
    instance.once('error', reject);
    instance.once('listening', () => resolve(instance));
    instance.listen(0, '127.0.0.1');
  });
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('Unable to determine static server address.');
  BASE = `http://127.0.0.1:${address.port}`;
  console.log(`Started isolated static server on ${BASE}`);
}

function luminance(rgb) {
  const channels = rgb.match(/\d+(?:\.\d+)?/g)?.slice(0, 3).map(Number) ?? [];
  const linear = channels.map((channel) => {
    const value = channel / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
}

function contrast(a, b) {
  const [lighter, darker] = [luminance(a), luminance(b)].sort((first, second) => second - first);
  return (lighter + 0.05) / (darker + 0.05);
}

async function inspect({ theme, viewport, reducedMotion = 'no-preference' }) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport, colorScheme: theme, reducedMotion });
  const page = await context.newPage();
  await page.goto(`${BASE}/reference/troubleshooting/`, { waitUntil: 'networkidle' });
  await page.evaluate((nextTheme) => document.documentElement.setAttribute('data-theme', nextTheme), theme);
  const result = await page.evaluate(() => {
    const article = document.querySelector('.sl-markdown-content');
    const h1 = document.querySelector('h1');
    const h2 = document.querySelector('.sl-markdown-content h2');
    const sidebarLink = document.querySelector('.sidebar-content a');
    const searchButton = document.querySelector('site-search button[data-open-modal]');
    const linkCard = document.querySelector('.sl-link-card');
    const frame = document.querySelector('.expressive-code .frame');
    const computed = (element) => (element ? getComputedStyle(element) : null);
    const codeColors = [...document.querySelectorAll('.expressive-code span')]
      .map((element) => getComputedStyle(element).color)
      .filter((color) => color !== 'rgba(0, 0, 0, 0)');
    const wrapper = document.querySelector('.table-scroll');
    const table = wrapper?.querySelector('table');
    const link = document.querySelector('.sl-markdown-content a');
    const main = document.querySelector('main[data-pagefind-body]');
    return {
      article: article && {
        fontFamily: computed(article).fontFamily,
        fontSize: computed(article).fontSize,
        lineHeight: computed(article).lineHeight,
        width: article.getBoundingClientRect().width,
      },
      headings: {
        h1: computed(h1)?.fontSize,
        h1Family: computed(h1)?.fontFamily,
        h2: computed(h2)?.fontSize,
      },
      sidebar: {
        fontFamily: computed(sidebarLink)?.fontFamily,
        fontSize: computed(sidebarLink)?.fontSize,
      },
      link: link && {
        color: getComputedStyle(link).color,
        bodyColor: computed(article)?.color,
        background: getComputedStyle(document.body).backgroundColor,
      },
      code: document.querySelector('.expressive-code pre') && {
        background: computed(document.querySelector('.expressive-code pre'))?.backgroundColor,
        fontSize: computed(document.querySelector('.expressive-code code'))?.fontSize,
        lineHeight: computed(document.querySelector('.expressive-code code'))?.lineHeight,
        radius: computed(frame)?.borderRadius,
        colors: [...new Set(codeColors)],
      },
      components: {
        searchRadius: computed(searchButton)?.borderRadius,
        cardRadius: computed(linkCard)?.borderRadius,
        cardTransform: computed(linkCard)?.transform,
      },
      table: wrapper && table && {
        tabIndex: wrapper.tabIndex,
        role: wrapper.getAttribute('role'),
        label: wrapper.getAttribute('aria-label'),
        overflowX: getComputedStyle(wrapper).overflowX,
        headerBackground: computed(table.querySelector('th'))?.backgroundColor,
        cellBorder: computed(table.querySelector('td'))?.borderBottomWidth,
        scrollWidth: wrapper.scrollWidth,
        clientWidth: wrapper.clientWidth,
      },
      motion: main && {
        name: getComputedStyle(main).animationName,
        duration: getComputedStyle(main).animationDuration,
      },
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: window.innerWidth,
    };
  });
  await context.close();
  await browser.close();
  return result;
}

const syntaxCases = [
  { language: 'kotlin', path: '/start/quickstart/', minimumColors: 5 },
  { language: 'xml', path: '/start/android-setup/', minimumColors: 5 },
  { language: 'toml', path: '/start/installation/', minimumColors: 3 },
  { language: 'bash', path: '/start/installation/', minimumColors: 4 },
];

async function inspectStyle({ theme, path: pathname, selector, property }) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, colorScheme: theme });
  const page = await context.newPage();
  await page.goto(`${BASE}${pathname}`, { waitUntil: 'networkidle' });
  await page.evaluate((nextTheme) => document.documentElement.setAttribute('data-theme', nextTheme), theme);
  const value = await page.evaluate(
    ({ nextSelector, nextProperty }) => {
      const element = document.querySelector(nextSelector);
      return element ? getComputedStyle(element)[nextProperty] : null;
    },
    { nextSelector: selector, nextProperty: property }
  );
  await context.close();
  await browser.close();
  return value;
}

async function inspectSyntax({ theme, language, path: pathname }) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, colorScheme: theme });
  const page = await context.newPage();
  await page.goto(`${BASE}${pathname}`, { waitUntil: 'networkidle' });
  await page.evaluate((nextTheme) => document.documentElement.setAttribute('data-theme', nextTheme), theme);
  const colors = await page.evaluate((nextLanguage) => [
    ...new Set(
      [...document.querySelectorAll(`.expressive-code pre[data-language="${nextLanguage}"] span`)]
        .map((element) => getComputedStyle(element).color)
        .filter((color) => color !== 'rgba(0, 0, 0, 0)')
    ),
  ], language);
  await context.close();
  await browser.close();
  return colors;
}

async function inspectThemeSelector(theme) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, colorScheme: theme });
  const page = await context.newPage();
  await page.goto(`${BASE}/reference/troubleshooting/`, { waitUntil: 'networkidle' });
  await page.evaluate((nextTheme) => document.documentElement.setAttribute('data-theme', nextTheme), theme);

  const selector = page.locator('.right-group starlight-theme-select select');
  const visible = await selector.isVisible();
  await selector.focus();
  const focus = await selector.evaluate((element) => {
    const styles = getComputedStyle(element);
    return {
      visible: element.matches(':focus-visible'),
      outlineStyle: styles.outlineStyle,
      outlineWidth: Number.parseFloat(styles.outlineWidth),
    };
  });

  const opposite = theme === 'light' ? 'dark' : 'light';
  await selector.selectOption(opposite);
  await page.waitForFunction((nextTheme) => document.documentElement.dataset.theme === nextTheme, opposite);
  const selectedTheme = await page.evaluate(() => document.documentElement.dataset.theme);

  await context.close();
  await browser.close();
  return { visible, focus, selectedTheme, opposite };
}

const NATIVELY_FOCUSABLE_TAGS = ['A', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA'];

async function focusAndRead(page, locator) {
  await locator.focus();
  return locator.evaluate((el) => {
    const styles = getComputedStyle(el);
    return {
      visible: el.matches(':focus-visible'),
      outlineStyle: styles.outlineStyle,
      outlineWidth: Number.parseFloat(styles.outlineWidth),
      href: el.getAttribute('href'),
    };
  });
}

async function inspectLanding({ theme, viewport, reducedMotion = 'no-preference' }) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport, colorScheme: theme, reducedMotion });
  const page = await context.newPage();
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await page.evaluate((nextTheme) => document.documentElement.setAttribute('data-theme', nextTheme), theme);

  const data = await page.evaluate(() => {
    const main = document.querySelector('main');
    const h1 = document.querySelector('h1');
    const h2 = document.querySelector('h2');
    const h3 = document.querySelector('.landing-compatibility h3');
    const section = document.querySelector('.landing-section');

    const landingElements = [...document.querySelectorAll('[class*="landing-"]')].map((el) => {
      const s = getComputedStyle(el);
      return {
        tag: el.tagName.toLowerCase(),
        className: el.className,
        borderRadius: Number.parseFloat(s.borderTopLeftRadius),
        boxShadow: s.boxShadow,
        transform: s.transform,
      };
    });

    const tableWrapper = document.querySelector('.table-scroll.table-scroll--wide');
    const table = tableWrapper?.querySelector('table');

    const codeBlocks = [...document.querySelectorAll('.expressive-code pre')].map((block) => ({
      background: getComputedStyle(block).backgroundColor,
    }));

    const metaCode = document.querySelector('.landing-meta code.admob-font-mono');
    const metaCodeFocusable = metaCode
      ? metaCode.tabIndex >= 0 || ['A', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA'].includes(metaCode.tagName)
      : false;

    return {
      main: main
        ? {
            fontFamily: getComputedStyle(main).fontFamily,
            animationName: getComputedStyle(main).animationName,
          }
        : null,
      section: section
        ? { fontFamily: getComputedStyle(section).fontFamily }
        : null,
      headings: {
        h1: h1 ? getComputedStyle(h1).fontSize : null,
        h1Family: h1 ? getComputedStyle(h1).fontFamily : null,
        h2: h2 ? getComputedStyle(h2).fontSize : null,
        h3: h3 ? getComputedStyle(h3).fontSize : null,
      },
      landing: landingElements,
      table: tableWrapper
        ? {
            tabIndex: tableWrapper.tabIndex,
            role: tableWrapper.getAttribute('role'),
            ariaLabel: tableWrapper.getAttribute('aria-label'),
            scrollWidth: tableWrapper.scrollWidth,
            clientWidth: tableWrapper.clientWidth,
            rowCount: table ? table.querySelectorAll('tbody tr').length : 0,
          }
        : null,
      codeBlocks,
      metaCodeFocusable,
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: window.innerWidth,
    };
  });

  const focusChecks = {};

  focusChecks.heroActions = [];
  for (const link of await page.locator('.hero a').all()) {
    focusChecks.heroActions.push(await focusAndRead(page, link));
  }

  const metaCode = page.locator('.landing-meta code.admob-font-mono');
  if (await metaCode.count() > 0) {
    if (data.metaCodeFocusable) {
      focusChecks.metaCode = await focusAndRead(page, metaCode);
    } else {
      focusChecks.metaCode = { focusable: false };
    }
  } else {
    focusChecks.metaCode = { found: false };
  }

  focusChecks.formatLinks = [];
  for (const link of await page.locator('.landing-formats a').all()) {
    focusChecks.formatLinks.push(await focusAndRead(page, link));
  }

  const tableRegion = page.locator('.table-scroll.table-scroll--wide');
  if (await tableRegion.count() > 0) {
    focusChecks.tableRegion = await focusAndRead(page, tableRegion);
  }

  focusChecks.footerLinks = [];
  for (const link of await page.locator('.landing-footer a').all()) {
    focusChecks.footerLinks.push(await focusAndRead(page, link));
  }

  data.focus = focusChecks;

  await context.close();
  await browser.close();
  return data;
}

async function inspectLandingThemeSelector(theme) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, colorScheme: theme });
  const page = await context.newPage();
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await page.evaluate((nextTheme) => document.documentElement.setAttribute('data-theme', nextTheme), theme);

  const selector = page.locator('.right-group starlight-theme-select select');
  const visible = await selector.isVisible();
  await selector.focus();
  const focus = await selector.evaluate((element) => {
    const styles = getComputedStyle(element);
    return {
      visible: element.matches(':focus-visible'),
      outlineStyle: styles.outlineStyle,
      outlineWidth: Number.parseFloat(styles.outlineWidth),
    };
  });

  const opposite = theme === 'light' ? 'dark' : 'light';
  await selector.selectOption(opposite);
  await page.waitForFunction((nextTheme) => document.documentElement.dataset.theme === nextTheme, opposite);
  const selectedTheme = await page.evaluate(() => document.documentElement.dataset.theme);

  await context.close();
  await browser.close();
  return { visible, focus, selectedTheme, opposite };
}

try {
  for (const theme of ['light', 'dark']) {
    const desktop = await inspect({ theme, viewport: { width: 1440, height: 1000 } });
    const roadmapH3 = await inspectStyle({
      theme,
      path: '/project/roadmap/',
      selector: '.sl-markdown-content h3',
      property: 'fontSize',
    });
    const codeBackground = {
      light: 'rgb(246, 248, 250)',
      dark: 'rgb(22, 27, 34)',
    }[theme];
    const usesNativeStack = (fontFamily) =>
      /-apple-system/.test(fontFamily ?? '') && !/Space Grotesk|Inter/.test(fontFamily ?? '');
    check(desktop.article?.fontSize === '16px', `${theme} body is 16px`);
    check(desktop.article?.lineHeight === '25.6px', `${theme} body uses 1.6 rhythm`);
    check(desktop.article?.width <= 720, `${theme} reading measure is at most 45rem`);
    check(desktop.headings?.h1 === '32px', `${theme} desktop H1 is 32px`);
    check(desktop.headings?.h2 === '24px', `${theme} H2 is 24px`);
    check(roadmapH3 === '19px', `${theme} H3 is 19px`);
    check(
      usesNativeStack(desktop.article?.fontFamily) && usesNativeStack(desktop.headings?.h1Family),
      `${theme} prose and headings share the native UI stack`
    );
    check(usesNativeStack(desktop.sidebar?.fontFamily), `${theme} sidebar uses the UI stack`);
    check(desktop.sidebar?.fontSize === '13px', `${theme} sidebar is 13px`);
    check(desktop.link?.color !== desktop.link?.bodyColor && contrast(desktop.link?.color ?? '', desktop.link?.background ?? '') >= 4.5, `${theme} links use a distinct semantic accent`);
    check(desktop.code?.background === codeBackground, `${theme} code follows the site theme`);
    check(desktop.code?.fontSize === '14px', `${theme} code is 14px`);
    check(desktop.code?.radius === '6px', `${theme} code frame radius is 6px`);
    check((desktop.code?.colors.length ?? 0) >= 5, `${theme} code exposes at least five syntax colors`);
    check(desktop.components?.searchRadius === '6px', `${theme} search is not pill-shaped`);
    check(desktop.components?.cardRadius === '6px', `${theme} cards use a quiet radius`);
    check(desktop.components?.cardTransform === 'none', `${theme} cards have no spatial transform`);
    check(desktop.table?.tabIndex === 0 && desktop.table.role === 'region' && desktop.table.label === 'Scrollable data table', `${theme} tables use a focusable labelled scroll region`);
    check(desktop.table?.overflowX === 'auto', `${theme} table wrapper scrolls horizontally when needed`);
    check(desktop.table?.headerBackground !== 'rgba(0, 0, 0, 0)' && desktop.table?.cellBorder === '1px', `${theme} table headers and rows are structured`);
    check(desktop.motion?.name === 'none', `${theme} articles do not animate on entry`);

    const landingDesktop = await inspectLanding({ theme, viewport: { width: 1440, height: 1000 } });
    check(landingDesktop.headings?.h1 === '32px', `${theme} landing H1 is 32px desktop`);
    check(landingDesktop.headings?.h2 === '24px', `${theme} landing H2 is 24px desktop`);
    check(landingDesktop.headings?.h3 === '19px', `${theme} landing H3 is 19px desktop`);
    check(
      usesNativeStack(landingDesktop.headings?.h1Family) && usesNativeStack(landingDesktop.section?.fontFamily),
      `${theme} landing body and H1 share the native UI stack`
    );
    for (const el of landingDesktop.landing ?? []) {
      check(
        el.borderRadius <= 6,
        `${theme} landing ${el.className} radius is at most 6px (got ${el.borderRadius}px)`
      );
      check(el.boxShadow === 'none', `${theme} landing ${el.className} has no shadow`);
      check(el.transform === 'none', `${theme} landing ${el.className} has no transform`);
    }
    check(landingDesktop.table?.tabIndex === 0, `${theme} landing capability table is keyboard-focusable`);
    check(landingDesktop.table?.role === 'region', `${theme} landing capability table has role=region`);
    check(
      typeof landingDesktop.table?.ariaLabel === 'string' && landingDesktop.table.ariaLabel.length > 0,
      `${theme} landing capability table has a non-empty aria-label`
    );
    check(
      (landingDesktop.table?.scrollWidth ?? 0) >= (landingDesktop.table?.clientWidth ?? 0),
      `${theme} landing capability table is structured for horizontal scroll when needed`
    );
    check((landingDesktop.table?.rowCount ?? 0) > 0, `${theme} landing capability table has rows`);
    for (const block of landingDesktop.codeBlocks ?? []) {
      check(block.background === codeBackground, `${theme} landing code block uses ${codeBackground}`);
    }
    check(landingDesktop.main?.animationName === 'none', `${theme} landing main has no entrance animation`);
    check(
      landingDesktop.documentWidth <= landingDesktop.viewportWidth + 1,
      `${theme} landing desktop does not overflow horizontally`
    );
    check(
      landingDesktop.focus?.heroActions?.length === 2,
      `${theme} landing exposes exactly two hero actions (got ${landingDesktop.focus?.heroActions?.length})`
    );
    for (const focus of landingDesktop.focus?.heroActions ?? []) {
      check(
        focus.visible && focus.outlineWidth >= 2,
        `${theme} hero action ${focus.href} has visible focus outline >= 2px`
      );
    }
    if (landingDesktop.focus?.metaCode?.focusable) {
      check(
        landingDesktop.focus.metaCode.visible && landingDesktop.focus.metaCode.outlineWidth >= 2,
        `${theme} landing meta code has visible focus outline >= 2px`
      );
    }
    check(
      landingDesktop.focus?.formatLinks?.length === 6,
      `${theme} landing exposes six format links (got ${landingDesktop.focus?.formatLinks?.length})`
    );
    for (const focus of landingDesktop.focus?.formatLinks ?? []) {
      check(
        focus.visible && focus.outlineWidth >= 2,
        `${theme} format link ${focus.href} has visible focus outline >= 2px`
      );
    }
    check(
      landingDesktop.focus?.tableRegion?.visible && landingDesktop.focus?.tableRegion?.outlineWidth >= 2,
      `${theme} landing capability table region has visible focus outline >= 2px`
    );
    check(
      landingDesktop.focus?.footerLinks?.length === 7,
      `${theme} landing exposes seven footer links (got ${landingDesktop.focus?.footerLinks?.length})`
    );
    for (const focus of landingDesktop.focus?.footerLinks ?? []) {
      check(
        focus.visible && focus.outlineWidth >= 2,
        `${theme} footer link ${focus.href} has visible focus outline >= 2px`
      );
    }

    const themeSelector = await inspectThemeSelector(theme);
    check(themeSelector.visible, `${theme} desktop theme selector is visible`);
    check(
      themeSelector.focus.visible &&
        themeSelector.focus.outlineStyle !== 'none' &&
        themeSelector.focus.outlineWidth >= 2,
      `${theme} theme selector has a visible focus outline of at least 2px`
    );
    check(
      themeSelector.selectedTheme === themeSelector.opposite,
      `${theme} theme selector updates the document theme`
    );

    const landingThemeSelector = await inspectLandingThemeSelector(theme);
    check(landingThemeSelector.visible, `${theme} landing theme selector is visible`);
    check(
      landingThemeSelector.focus.visible &&
        landingThemeSelector.focus.outlineStyle !== 'none' &&
        landingThemeSelector.focus.outlineWidth >= 2,
      `${theme} landing theme selector has a visible focus outline of at least 2px`
    );
    check(
      landingThemeSelector.selectedTheme === landingThemeSelector.opposite,
      `${theme} landing theme selector updates the document theme`
    );

    const mobile = await inspect({ theme, viewport: { width: 390, height: 844 } });
    check(mobile.headings?.h1 === '28px', `${theme} mobile H1 is 28px`);
    check(mobile.documentWidth <= mobile.viewportWidth + 1, `${theme} mobile document does not overflow horizontally`);
    check((mobile.table?.scrollWidth ?? 0) >= (mobile.table?.clientWidth ?? 0), `${theme} mobile table remains contained in its scroll region`);

    const landingMobile = await inspectLanding({ theme, viewport: { width: 390, height: 844 } });
    check(landingMobile.headings?.h1 === '28px', `${theme} landing H1 is 28px mobile`);
    check(landingMobile.headings?.h2 === '24px', `${theme} landing H2 is 24px mobile`);
    check(landingMobile.headings?.h3 === '19px', `${theme} landing H3 is 19px mobile`);
    check(
      landingMobile.documentWidth <= landingMobile.viewportWidth + 1,
      `${theme} landing mobile does not overflow horizontally`
    );

    for (const syntaxCase of syntaxCases) {
      const colors = await inspectSyntax({ theme, ...syntaxCase });
      check(
        colors.length >= syntaxCase.minimumColors,
        `${theme} ${syntaxCase.language} code exposes at least ${syntaxCase.minimumColors} syntax colors`
      );
    }

    const reduced = await inspect({ theme, viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' });
    check(reduced.motion?.name === 'none', `${theme} reduced-motion disables article entrance`);

    const reducedLanding = await inspectLanding({ theme, viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' });
    check(reducedLanding.main?.animationName === 'none', `${theme} landing reduced-motion disables entrance animation`);
  }
} finally {
  if (server) server.close();
}

if (failures.length > 0) {
  console.error(`\n${failures.length} theme check(s) failed.`);
  process.exit(1);
}

console.log('\nTheme and rendering checks passed.');
