/**
 * Host guard for the AdMob CMP docs site.
 *
 * Cloudflare Pages answers on three kinds of host:
 *   1. ads.avinya.dev                  — the canonical custom domain. Serve.
 *   2. admob-cmp-docs.pages.dev        — the production preview host. 301 away,
 *                                        so Google never has two crawlable
 *                                        copies of the site to choose between.
 *   3. <hash>.admob-cmp-docs.pages.dev — per-branch previews. Serve them (they
 *                                        exist to be reviewed) but mark them
 *                                        noindex so they cannot be indexed.
 *
 * Spec §5 documents exactly this failure on avinya.dev: every canonical pointed
 * at avinya.pages.dev, which returned HTTP 200, so Google was told to prefer the
 * throwaway host. This file makes that impossible here.
 */

const CANONICAL_HOST = 'ads.avinya.dev';
const PRODUCTION_PREVIEW_HOST = 'admob-cmp-docs.pages.dev';

export async function onRequest(context) {
  const url = new URL(context.request.url);

  if (url.hostname === CANONICAL_HOST) {
    return context.next();
  }

  if (url.hostname === PRODUCTION_PREVIEW_HOST) {
    url.hostname = CANONICAL_HOST;
    url.protocol = 'https:';
    url.port = '';
    return Response.redirect(url.toString(), 301);
  }

  const response = await context.next();
  const headers = new Headers(response.headers);
  headers.set('X-Robots-Tag', 'noindex, nofollow');
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}
