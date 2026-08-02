# Launch metrics — spec §12

Baseline 2026-07-31. Reviewed 30 and 90 days after Plan 6 lands.

Run `scripts/distribution/collect-launch-metrics.sh` and paste its output under
the relevant checkpoint. Fill the manual rows from Google Search Console and
from SERP checks in a logged-out incognito window.

Spec §12 is explicit that these are targets, not forecasts: ranking outcomes for
a new host with no backlink profile are genuinely unpredictable. **The
controllable rows are indexation and distribution, and those are the ones to
hold the program to.** Do not let a missed ranking target trigger a strategy
change if indexation and distribution are on track.

## Targets

| Metric | Baseline 2026-07-31 | 30-day | 90-day | How |
|---|---|---|---|---|
| Docs pages indexed | 0 | 25+ | 25+ | Search Console → Indexing → Pages |
| klibs.io listing correct | unverified (NOT INDEXED) | verified | verified | `verify-klibs-listing.sh` + the checklist in `klibs-io.md` |
| `kmp-awesome` entry | none | merged | merged | `collect-launch-metrics.sh`. **Gated — see below** |
| Search Console impressions | 0 | >0 | growing | Search Console → Performance |
| Rank: `compose multiplatform native ads` | unranked | top 20 | top 3 | Manual SERP |
| Rank: `admob ios kotlin multiplatform undefined symbols` | unranked | top 20 | top 10 | Manual SERP |
| Rank: `compose multiplatform admob` | unranked | indexed | page 1–2 | Manual SERP |
| Referring domains to ads.avinya.dev | 0 | 3+ | 10+ | Search Console → Links |
| GitHub stars | 0 | — | 50 (directional) | `collect-launch-metrics.sh` |

### The `kmp-awesome` row is not achievable at 30 days, and that is not a miss

`terrakok/kmp-awesome` requires roughly 50 GitHub stars **and** a link to a
third-party project using the library. Both are unmet as of the baseline, and
the star requirement is the same number as the §12 90-day star target. So the
earliest honest submission is the 90-day checkpoint, and only if stars have
actually reached ~50. Recording "not merged" at 30 days is the expected result,
not a failure. See `docs/distribution/kmp-awesome-entry.md`.

## Search Console prerequisites (owned by Plan 2)

Confirm before the first checkpoint, or the impressions, indexed-pages and
referring-domain rows cannot be collected at all:

- [ ] A property exists for `ads.avinya.dev`. Prefer a **Domain** property (DNS
      TXT verification) — it covers `http`/`https` and every subdomain, and it
      is the only type that reports links across all of them.
- [ ] The sitemap is submitted under Indexing → Sitemaps and reports "Success".
- [ ] Under Settings → Ownership verification, at least two verification methods
      are active, so a DNS or hosting change cannot silently drop access.

## Baseline — 2026-07-31

<paste `collect-launch-metrics.sh` output here on the day Plan 6 lands>

Known at planning time:

- GitHub stars: 0. Forks 0, watchers 0.
- GitHub topics: 0.
- klibs.io: NOT INDEXED.
- kmp-awesome: not present. No entry anywhere in the file matches `admob`,
  `basic-ads`, `monetiz` or `advertis`.
- Maven Central latest: 1.1.0.
- Docs pages indexed: 0 — the host does not exist yet.
- Competitor reference point (spec §6): `LexiLabs-App/basic-ads`, 105 stars.
- Existing owned audience to route from: `Meet-Miyani/compose-skill`, 274 stars.

## 30-day checkpoint — <DATE>

<paste output>

Manual rows:

- Docs pages indexed:
- Search Console impressions (28d):
- Referring domains:
- Rank `compose multiplatform native ads`:
- Rank `admob ios kotlin multiplatform undefined symbols`:
- Rank `compose multiplatform admob`:

Actions from this checkpoint:

- [ ] If klibs.io is still NOT INDEXED and 1.1.0 was published more than a month
      ago, file the index request — `docs/distribution/klibs-io.md`, human step.
- [ ] If indexed pages are below 25, check Search Console → Pages for the
      exclusion reason before changing anything. "Crawled – currently not
      indexed" on a new host means wait; "Excluded by 'noindex'" or "Alternate
      page with proper canonical tag" is a real bug in Plan 2's SEO plumbing.
- [ ] If impressions are 0 **and** indexed pages are 0, verify the Search
      Console property is actually verified. A misconfigured property looks
      identical to no traffic.
- [ ] If referring domains are below 3, check that the dev.to and Medium posts
      are live and that Medium's canonical points at dev.to.

## 90-day checkpoint — <DATE>

<paste output>

Manual rows: (same list as above)

Actions from this checkpoint:

- [ ] Re-check the `kmp-awesome` gate — stars ≥ ~50 **and** a third-party usage
      link. Submit only if both hold.
- [ ] Verify the klibs.io metadata checklist in full, not just that the page
      exists. The description is AI-generated and can be wrong.
- [ ] If `compose multiplatform native ads` is still unranked at 90 days despite
      being uncontested, the problem is indexation or thin content, not
      competition. Re-read spec §7's content-depth finding: guides below 300
      words do not rank for informational queries.
- [ ] Spec §13: if an Ahrefs or Similarweb connector has since been authorized,
      re-run §7 against real volume and difficulty data and revise these targets.
      Both were unavailable at planning time and every difficulty rating here is
      qualitative.
