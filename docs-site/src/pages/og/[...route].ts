import { getCollection } from 'astro:content';
import { OGImageRoute } from 'astro-og-canvas';
import { normalizeEntryId } from '../../lib/seo';

const entries = await getCollection('docs');

const pages = Object.fromEntries(
  entries.map((entry) => [
    normalizeEntryId(entry.id),
    {
      title: entry.data.title,
      description: entry.data.description ?? '',
    },
  ])
);

export const { getStaticPaths, GET } = await OGImageRoute({
  pages,
  getImageOptions: (_path, page: { title: string; description: string }) => ({
    title: page.title,
    description: page.description,
    logo: { path: './src/assets/logo.svg', size: [72] },
    // avinya.dev dark paper, warmed towards the accent in the corner.
    bgGradient: [
      [14, 15, 16],
      [30, 18, 15],
    ],
    border: { color: [238, 58, 32], width: 12, side: 'inline-start' },
    padding: 60,
    font: {
      title: {
        families: ['Space Grotesk'],
        weight: 'SemiBold',
        color: [237, 238, 236],
        size: 66,
        lineHeight: 1.1,
      },
      description: {
        families: ['Inter'],
        weight: 'Normal',
        color: [154, 159, 156],
        size: 32,
        lineHeight: 1.4,
      },
    },
    fonts: [
      'https://api.fontsource.org/v1/fonts/space-grotesk/latin-600-normal.ttf',
      'https://api.fontsource.org/v1/fonts/inter/latin-400-normal.ttf',
    ],
    format: 'PNG',
  }),
});
