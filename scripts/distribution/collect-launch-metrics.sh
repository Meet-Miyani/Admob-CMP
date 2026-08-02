#!/usr/bin/env bash
# Collect the automatable rows of spec §12 (success metrics).
#
# Four rows are scriptable. The rest need Google Search Console or a manual
# SERP check and are printed as an explicit TODO block rather than silently
# omitted - a metric that quietly disappears from a report is worse than one
# marked "not collected".
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OWNER="${REPO_OWNER:-Meet-Miyani}"
SLUG="${REPO_SLUG:-admob-compose-multiplatform}"

echo "# Metrics snapshot — $(date -u '+%Y-%m-%d %H:%M UTC')"
echo

echo "## Automated"
echo

printf -- "- GitHub stars: "
curl -s --max-time 25 "https://api.github.com/repos/${OWNER}/${SLUG}" \
  | python3 -c 'import sys,json
try:
    d = json.load(sys.stdin)
    print(d.get("stargazers_count", "ERROR"), "(forks:", str(d.get("forks_count"))+", watchers:", str(d.get("subscribers_count"))+")")
except Exception as e:
    print("ERROR:", e)'

printf -- "- GitHub topics: "
curl -s --max-time 25 "https://api.github.com/repos/${OWNER}/${SLUG}" \
  | python3 -c 'import sys,json
try:
    t = json.load(sys.stdin).get("topics") or []
    print(len(t), "->", ", ".join(t) if t else "(none)")
except Exception as e:
    print("ERROR:", e)'

printf -- "- klibs.io listing: "
if "${HERE}/verify-klibs-listing.sh" >/dev/null 2>&1; then
  echo "INDEXED — verify the metadata checklist in docs/distribution/klibs-io.md"
else
  case $? in
    1) echo "NOT INDEXED" ;;
    *) echo "UNKNOWN (network failure — re-run, do not record as missing)" ;;
  esac
fi

printf -- "- kmp-awesome entry: "
KMP="$(curl -sL --max-time 30 https://raw.githubusercontent.com/terrakok/kmp-awesome/master/README.MD)"
if [ -z "${KMP}" ]; then
  echo "UNKNOWN (fetch failed)"
elif printf '%s' "${KMP}" | grep -q "${SLUG}"; then
  echo "MERGED"
else
  echo "not present"
fi

printf -- "- Maven Central latest version: "
curl -s --max-time 25 "https://repo1.maven.org/maven2/dev/avinya/ads/admob-cmp/maven-metadata.xml" \
  | python3 -c 'import sys,re
s = sys.stdin.read()
m = re.search(r"<latest>([^<]+)</latest>", s) or re.search(r"<version>([^<]+)</version>\s*</versions>", s)
print(m.group(1) if m else "ERROR")'

printf -- "- Docs site reachable: "
curl -s -o /dev/null -w '%{http_code}\n' --max-time 25 -L https://ads.avinya.dev

printf -- "- Docs sitemap URL count: "
curl -sL --max-time 25 https://ads.avinya.dev/sitemap-index.xml https://ads.avinya.dev/sitemap-0.xml 2>/dev/null \
  | grep -c "<loc>" || echo "0 (sitemap not found — check the path Plan 2 configured)"

cat <<'TODO'

## Manual — collect these by hand, they have no free API

- [ ] **Docs pages indexed** — Search Console → Indexing → Pages → "Indexed".
      Cross-check with a `site:ads.avinya.dev` query in a logged-out browser.
      Target: 25+ at both 30 and 90 days.
- [ ] **Search Console impressions** — Performance → Search results → last 28
      days → Total impressions. Target: >0 at 30 days, growing at 90.
- [ ] **Referring domains** — Search Console → Links → Top linking sites.
      Target: 3+ at 30 days, 10+ at 90.
- [ ] **Rankings** — run each query below in a logged-out incognito window with
      a US locale, and record the position of any ads.avinya.dev or
      github.com/Meet-Miyani result, or "unranked" past position 50.
      Personalised results make a signed-in check worthless.

      1. compose multiplatform native ads          (target: top 20 @30d, top 3 @90d)
      2. admob ios kotlin multiplatform undefined symbols
                                                   (target: top 20 @30d, top 10 @90d)
      3. compose multiplatform admob               (target: indexed @30d, page 1-2 @90d)
      4. kotlin multiplatform admob
      5. kmp admob library
      6. compose multiplatform monetization

      Do NOT track `admob cmp`. Spec §7 marks it Hard / Avoid — that SERP
      belongs to Google's Consent Management Platform docs and the intent is
      wrong. A ranking there would be a false positive.
TODO
