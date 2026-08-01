// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import sitemap from '@astrojs/sitemap';
import starlightLlmsTxt from 'starlight-llms-txt';
import rehypeMermaid from 'rehype-mermaid';


/**
 * Canonical origin. `site` MUST be the custom domain, never the *.pages.dev
 * preview host — that mistake is exactly the defect documented in spec §5 for
 * the studio site, and `functions/_middleware.js` is the second line of defence.
 */
const SITE = 'https://ads.avinya.dev';
const REPO = 'https://github.com/Meet-Miyani/admob-compose-multiplatform';
const SITE_TITLE = 'AdMob CMP';
const SITE_DESCRIPTION =
  'Plug-and-play AdMob for Compose Multiplatform: banner, interstitial, rewarded, app-open and native ads with UMP consent on Android and iOS.';

export default defineConfig({
  site: SITE,
  build: { format: 'directory' },
  markdown: {
    /**
     * Mermaid is rendered to static SVG at BUILD time, not in the browser:
     * indexable, no client JS, no layout shift.
     *
     * This costs a headless Chromium at build time (see `npx playwright install
     * chromium`). CI must install that browser before `npm run build` or the
     * build fails with a Playwright "Executable doesn't exist" error.
     *
     * astro-expressive-code appends its own rehype plugin, so this one runs
     * first and consumes the `language-mermaid` fence before EC sees it.
     */
    rehypePlugins: [
      [
        rehypeMermaid,
        {
          strategy: 'inline-svg',
          mermaidConfig: {
            theme: 'base',
            fontFamily: 'Inter, system-ui, sans-serif',
            themeVariables: {
              background: '#f3f4f2',
              primaryColor: '#ebecea',
              primaryTextColor: '#16181a',
              primaryBorderColor: '#d8dad6',
              secondaryColor: '#f3f4f2',
              tertiaryColor: '#ebecea',
              lineColor: '#5a5f63',
              textColor: '#16181a',
            },
          },
        },
      ],
    ],
  },
  integrations: [
    starlight({
      title: SITE_TITLE,
      description: SITE_DESCRIPTION,
      logo: { src: './src/assets/logo.svg', alt: 'AdMob CMP' },
      favicon: '/favicon.svg',
      credits: false,
      pagefind: true,
      lastUpdated: true,
      tableOfContents: { minHeadingLevel: 2, maxHeadingLevel: 3 },
      social: [{ icon: 'github', label: 'GitHub', href: REPO }],
      // Starlight appends the entry path relative to the Astro project root,
      // which is `docs-site/`, so the base URL has to include that segment.
      editLink: { baseUrl: `${REPO}/edit/master/docs-site/` },
      customCss: ['./src/styles/tokens.css', './src/styles/mermaid.css'],
      components: { Head: './src/components/Head.astro' },
      expressiveCode: {
        useStarlightUiThemeColors: true,
        useStarlightDarkModeSwitch: true,
        styleOverrides: {
          borderRadius: 'var(--admob-radius)',
          codeFontFamily: 'var(--admob-font-mono)',
          uiFontFamily: 'var(--admob-font-body)',
        },
      },
      head: [
        {
          tag: 'link',
          attrs: {
            rel: 'preload',
            href: '/fonts/space-grotesk-600.woff2',
            as: 'font',
            type: 'font/woff2',
            crossorigin: 'anonymous',
          },
        },
        {
          tag: 'link',
          attrs: {
            rel: 'preload',
            href: '/fonts/inter-400.woff2',
            as: 'font',
            type: 'font/woff2',
            crossorigin: 'anonymous',
          },
        },
        { tag: 'meta', attrs: { name: 'theme-color', content: '#0e0f10' } },
      ],
      plugins: [
        starlightLlmsTxt({
          projectName: 'AdMob CMP',
          description:
            'A Kotlin Multiplatform / Compose Multiplatform AdMob SDK. Six ad formats (banner, interstitial, rewarded, rewarded interstitial, app-open, native), UMP consent wired into the initialization flow, mediation, paid/revenue events, and a Gradle plugin that fixes Kotlin/Native iOS test linking. Published to Maven Central as dev.avinya.ads:admob-cmp.',
          details: [
            '## Coordinates',
            '',
            '- Maven coordinate: `dev.avinya.ads:admob-cmp`',
            '- Gradle plugin id: `dev.avinya.ads.admob-cmp`',
            '- Modules: `admob-cmp` (facade), `admob-cmp-core`, `admob-cmp-compose`',
            '- Platforms: Android and iOS. There is no desktop or web ad implementation.',
            '- Licence: Apache-2.0',
            '',
            '## Reading order',
            '',
            'Start with `/start/what-is-admob-cmp/`, then `/start/quickstart/`.',
            'iOS integrations should read `/start/ios-setup/` and',
            '`/privacy/app-tracking-transparency/` before writing any ad code:',
            'the required ordering is consent, then ATT, then initialize.',
            '',
            '## Caveats',
            '',
            'The public ABI is frozen and validated in CI. Suggestions that change',
            'a public signature in `admob-cmp-core` or `admob-cmp-compose` will fail',
            '`checkKotlinAbi`.',
            '',
            'Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads',
            'are trademarks of Google LLC.',
          ].join('\n'),
          optionalLinks: [
            {
              label: 'GitHub repository',
              url: 'https://github.com/Meet-Miyani/admob-compose-multiplatform',
              description: 'Source, issues, and the AGENTS.md instructions file.',
            },
            {
              label: 'Maven Central',
              url: 'https://central.sonatype.com/artifact/dev.avinya.ads/admob-cmp',
              description: 'Published artifacts and the current release version.',
            },
            {
              label: 'API reference',
              url: 'https://ads.avinya.dev/api/',
              description: 'Generated Dokka HTML for every public declaration.',
            },
          ],
          promote: ['index*', 'start/what-is-admob-cmp*', 'start/quickstart*', 'start/installation*'],
          demote: ['project/*', 'reference/changelog*'],
          // `exclude` only trims llms-small.txt, the small-context bundle.
          exclude: ['project/contributing*', 'reference/changelog*'],
          customSets: [
            {
              label: 'Ad formats',
              description: 'Every ad format guide: banner, interstitial, rewarded, app-open, native.',
              paths: ['formats/**'],
            },
            {
              label: 'Privacy and consent',
              description: 'UMP consent, App Tracking Transparency ordering, and Play Data safety.',
              paths: ['privacy/**'],
            },
            {
              label: 'Setup',
              description: 'Installation, Android setup, and iOS setup including the Gradle plugin.',
              paths: ['start/**'],
            },
          ],
          pageSeparator: '\n\n---\n\n',
        }),
      ],
      // Information architecture — spec §8. Every path segment is a keyword and
      // there is deliberately no `/docs/` prefix.
      sidebar: [
        {
          label: 'Start here',
          items: [
            { slug: 'start/what-is-admob-cmp' },
            { slug: 'start/quickstart' },
            { slug: 'start/installation' },
            { slug: 'start/android-setup' },
            { slug: 'start/ios-setup' },
          ],
        },
        {
          label: 'Ad formats',
          items: [
            { slug: 'formats/banner' },
            { slug: 'formats/interstitial' },
            { slug: 'formats/rewarded' },
            { slug: 'formats/app-open' },
            { slug: 'formats/native' },
          ],
        },
        {
          label: 'Privacy and consent',
          items: [
            { slug: 'privacy/consent' },
            { slug: 'privacy/app-tracking-transparency' },
            { slug: 'privacy/play-data-safety' },
          ],
        },
        {
          label: 'Advanced',
          items: [
            { slug: 'advanced/mediation' },
            { slug: 'advanced/revenue-events' },
            { slug: 'advanced/caching-retry-timeouts' },
            { slug: 'advanced/test-safety' },
          ],
        },
        {
          label: 'Reference',
          items: [
            { slug: 'reference/architecture' },
            { slug: 'reference/compatibility' },
            { slug: 'reference/troubleshooting' },
            { slug: 'reference/changelog' },
            {
              label: 'API reference (Dokka)',
              link: '/api/',
              attrs: { target: '_blank', rel: 'noopener' },
            },
          ],
        },
        {
          label: 'Project',
          items: [
            { slug: 'project/roadmap' },
            { slug: 'project/contributing' },
            { slug: 'project/ai-agents' },
          ],
        },
      ],
    }),
    sitemap({
      // `/og/*.png` are image endpoints, not pages. `/api/**` is a generated
      // Dokka dump copied in via `public/`; only its entry point is listed.
      // The /api/ entry must be listed, so the filter is "exclude everything under
      // /api/ except the entry point itself" — the plan's filter would have excluded
      // /api/ entirely, which broke the build verifier.
      filter: (page) => !page.includes('/og/') && (page === `${SITE}/api/` || !page.includes('/api/')),
      customPages: [`${SITE}/api/`],
      changefreq: 'weekly',
    }),
  ],
});
