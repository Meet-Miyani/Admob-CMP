import { defineCollection, z } from 'astro:content';
import { docsLoader } from '@astrojs/starlight/loaders';
import { docsSchema } from '@astrojs/starlight/schema';

export const collections = {
  docs: defineCollection({
    loader: docsLoader(),
    schema: docsSchema({
      extend: z.object({
        /**
         * Question/answer pairs rendered as `FAQPage` structured data by
         * `src/components/Head.astro`. Plan 3 populates this on guide pages
         * whose H2s already mirror People-Also-Ask phrasing.
         */
        faq: z
          .array(z.object({ q: z.string(), a: z.string() }))
          .optional(),
      }),
    }),
  }),
};
