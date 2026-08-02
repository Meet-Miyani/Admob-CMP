/**
 * Host guard for the AdMob CMP docs site.
 *
 * Cloudflare Pages answers on three kinds of host:
 *   1. ads.avinya.dev                — the canonical custom domain. Serve.
 *   2. <project>.pages.dev           — the production preview host. 301 away, so
 *                                      Google never has two crawlable copies of
 *                                      the site to choose between.
 *   3. <hash>.<project>.pages.dev    — per-deployment previews. Serve them (they
 *                                      exist to be reviewed) but mark them
 *                                      noindex so they cannot be indexed.
 *
 * Spec §5 documents exactly this failure on avinya.dev: every canonical pointed
 * at avinya.pages.dev, which returned HTTP 200, so Google was told to prefer the
 * throwaway host. This file makes that impossible here.
 *
 * The Pages project name is deliberately NOT hardcoded. It used to be, and it
 * had to agree with `--project-name` in .github/workflows/release.yml — two
 * files, no shared source of truth. When they disagreed the guard did not error;
 * it silently fell through to case 3 and *served* the production preview host,
 * which is the duplicate-copy defect this file exists to prevent. Cases 2 and 3
 * are distinguishable by label count alone, so the name is not needed:
 *
 *   <project>.pages.dev          -> 3 labels
 *   <hash>.<project>.pages.dev   -> 4 labels
 *
 * Renaming the Pages project therefore cannot desync this file again.
 */

const CANONICAL_HOST = 'ads.avinya.dev';
const PAGES_SUFFIX = '.pages.dev';
const PRODUCTION_PREVIEW_LABEL_COUNT = 3;

/** True for `<project>.pages.dev`, false for `<hash>.<project>.pages.dev`. */
export function isProductionPreviewHost(hostname) {
  if (!hostname.endsWith(PAGES_SUFFIX)) return false;
  return hostname.split('.').length === PRODUCTION_PREVIEW_LABEL_COUNT;
}

export async function onRequest(context) {
  const url = new URL(context.request.url);

  if (url.hostname === CANONICAL_HOST) {
    return context.next();
  }

  if (isProductionPreviewHost(url.hostname)) {
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
