# Launch and Off-Site Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish `dev.avinya.ads:admob-cmp` in the off-site channels Kotlin Multiplatform developers actually browse — klibs.io, `kmp-awesome`, Maven Central metadata, `avinya.dev/open-source/`, GitHub releases, and a sequenced launch calendar — and instrument the §12 success metrics with 30-day and 90-day review checkpoints.

**Architecture:** This is Plan 6 of the seven-plan program in `docs/superpowers/specs/2026-07-31-public-visibility-design.md` §10. It produces three kinds of artifact: (1) **repeatable verification scripts** under `scripts/distribution/` that a human or CI can re-run at each metrics checkpoint; (2) **committed records and verbatim copy** under `docs/distribution/` so nothing is improvised at posting time; and (3) **small source changes** in two repositories — POM metadata in this repo, and a featured-card treatment in the separate `avinya.dev` studio site repo. Every step that publishes something outward — a forum post, a PR to a third party's repo, an issue on JetBrains' tracker, a GitHub release edit, a deploy of the studio site — is marked as a human step and is never performed by an agent.

**Tech Stack:** Bash + `curl` + `python3` (verification scripts, no new dependencies); Gradle `gradle.properties` POM properties; GitHub Actions (`release-readiness.yml`); Astro 7 + TypeScript + Vitest (studio site repo); Markdown (records and copy).

## Global Constraints

- **Plan 6 runs last.** Sequencing per spec §10: `Plan 1 → Plan 2 → Plan 3 → Plan 5 → Plan 6`, with Plan 4 feeding Plan 5. Do not start Task 6 (launch calendar) until `https://ads.avinya.dev` is live and Google Search Console is provisioned (Plan 2), and the guide pages named in the copy exist (Plan 3).
- **CRITICAL — no agent may publish outward.** Every step labelled `**HUMAN STEP — outward-facing publication.**` must be performed and approved by a human being. An agent executing this plan writes the copy into a file, then stops and hands the file to the human. An agent must never post to a forum, Slack, subreddit, blogging platform, mailing list, third-party issue tracker, or third-party pull request, and must never edit a GitHub release or push a deploy of the studio site. Committing files inside this repository is not an outward-facing publication and needs no such gate.
- **GitHub repo (new):** `https://github.com/Meet-Miyani/admob-compose-multiplatform` — renamed from `Admob-CMP` in Plan 1.
- **GitHub repo (old, permanently reserved):** `https://github.com/Meet-Miyani/Admob-CMP`. GitHub 301-redirects it. The old name must never be reused, or every redirect breaks.
- **Docs site:** `https://ads.avinya.dev`
- **Maven coordinate:** `dev.avinya.ads:admob-cmp`, current version `1.1.0`. Never renamed (spec §2 non-goals).
- **Gradle plugin marker:** `dev.avinya.ads.admob-cmp` at `1.1.0`.
- **Trademark line — required verbatim wherever the project is described:**
  > Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
- **Brand/slug/coordinate reconciliation** — use this phrasing whenever all three must appear together: *"AdMob CMP (repo `admob-compose-multiplatform`, coordinate `dev.avinya.ads:admob-cmp`)"*.
- **Keyword targets are binding (spec §7).** Copy must carry `compose multiplatform admob`, `kotlin multiplatform admob`, `kmp admob library`. Never optimise for `admob cmp` — that SERP belongs to Google's Consent Management Platform documentation and the intent is wrong.
- **Competitive posture is binding (spec §6).** Comparison content is a neutral capability matrix. Never name `LexiLabs-App/basic-ads` in an attacking frame, in any post, PR, or release note.
- **Success metrics are binding (spec §12).** Baseline is 2026-07-31. Reviews at 30 and 90 days after this plan lands.
- **Ad formats — always six, in this order:** banner, interstitial, rewarded, rewarded interstitial, app-open, native.
- **Compatibility row for 1.1.0:** Kotlin 2.3.20, Compose Multiplatform 1.11.1, Android minSdk 26, iOS deployment target 15.0.
- **Do not bump Kotlin.** The whole build is pinned to 2.3.20 (frozen ABI + `abiValidation` DSL).
- **The public ABI is frozen** (`admob-cmp/CLAUDE.md` invariant 12). Nothing in this plan touches library source.

---

## Verified facts this plan is built on (all checked 2026-07-31)

These were confirmed live. Where a later task says "verify", it means re-confirm before acting, because these can drift.

| Fact | Verified value |
|---|---|
| klibs.io listing for the project | **Does not exist.** `https://klibs.io/project/Meet-Miyani/Admob-CMP` renders `data-testid="not-found-page-message"` → "Page not found" |
| klibs.io indexing criteria | 4 criteria (see Task 1); all 4 currently satisfied by 1.1.0 |
| `kotlin-tooling-metadata.json` on Central | Present: `admob-cmp-1.1.0-kotlin-tooling-metadata.json` |
| Published targets in that metadata | `androidJvm` + two `native` targets + `common` metadata. **No JVM, JS, or Wasm.** |
| `terrakok/kmp-awesome` README filename | `README.MD` (uppercase extension), branch `master` |
| `kmp-awesome` correct category | `### 🧩 Service SDK`. There is **no** ads/monetization category, and `### 🔍 Analytics` is for crash/telemetry, not ads |
| `kmp-awesome` existing ads entries | **None.** No match for `admob`, `basic-ads`, `monetiz`, or `advertis` anywhere in the file |
| `kmp-awesome` contribution rules | 4 rules; **rules 1 and 4 are currently unmet** (see Task 2) |
| Published POM `<url>` and `<scm>` for 1.1.0 | `https://github.com/Meet-Miyani/Admob-CMP` — the old slug, permanent |
| POM URL properties live in **two** files | `gradle.properties` **and** `admob-cmp-gradle-plugin/gradle.properties` (spec §9 mentions only the first) |
| Git tags in the repo | **Only `1.1.0`.** No `1.0.0`, `1.0.1`, `1.0.2`, and no `v`-prefixed tags |
| `v1.1.0` release "Full changelog" link | **Broken — HTTP 404.** Both `compare/v1.0.2...v1.1.0` and `compare/1.0.2...1.1.0` 404, because no `1.0.2` tag exists |
| Studio site repo | `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA`, remote `https://github.com/Meet-Miyani/AVINYA.git` (private) |
| Studio site `/open-source/` data source | `src/lib/repos.ts` → GitHub API at build time. **No hardcoded slug list**, so the rename propagates automatically. Sorted by stars desc; **no featured treatment exists** |
| Kotlin Slack rule that constrains this plan | *"Please refrain from cross-posting the same message on multiple channels. It is considered spamming."* |
| Kotlin Slack sign-up | `https://surveys.jetbrains.com/s3/kotlin-slack-sign-up` |
| `JetBrains/compose-multiplatform` Discussions | **Disabled** (`has_discussions: false`, `/discussions` → 404). There is no official Compose Multiplatform forum |
| `discuss.kotlinlang.org` → Libraries category | Scoped to *"the Kotlin standard library and other **kotlinx** libraries"* — not third-party announcements |
| Kotlin Weekly submission | `mailto:mailinglist@kotlinweekly.net` with subject `Link for submission - Kotlin Weekly` |
| Reddit access from tooling | **Blocked.** `www.reddit.com` is not accessible to the agent's user agent; r/Kotlin's rules could not be read programmatically and **must** be read by a human |

---

### Task 1: klibs.io listing verification

klibs.io is JetBrains' KMP library index. Listing is automatic — this is a *verify-and-correct* task, not a submission task. The listing does not exist yet; the point of this task is to build the repeatable check, record today's state, and encode the decision rule for what to do when the check keeps failing.

**Files:**
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-klibs-listing.sh`
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/klibs-io.md`

**Interfaces:**
- Consumes: nothing from earlier tasks. Depends on Plan 1 having renamed the repo (the script's `REPO_SLUG` default is the new slug).
- Produces: `scripts/distribution/verify-klibs-listing.sh` — exits `0` when the project page renders, `1` when it 404s, `2` on a network/tooling failure. Honours env overrides `REPO_OWNER` (default `Meet-Miyani`) and `REPO_SLUG` (default `admob-compose-multiplatform`). Task 7's `collect-launch-metrics.sh` invokes it and reads its exit code.

- [ ] **Step 1: Create the scripts directory and write the failing verification script**

```bash
mkdir -p /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution
mkdir -p /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution
```

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-klibs-listing.sh`:

```bash
#!/usr/bin/env bash
# Verify the klibs.io listing for admob-cmp.
#
# klibs.io is a Next.js app that returns HTTP 200 for every path, including
# unknown ones, so status codes prove nothing. A missing project renders a
# not-found component carrying data-testid="not-found-page-message"; that
# marker is the only reliable signal.
#
# Exit codes:
#   0  listing exists
#   1  listing missing (not-found marker present)
#   2  network or tooling failure — result is unknown, not negative
set -uo pipefail

REPO_OWNER="${REPO_OWNER:-Meet-Miyani}"
REPO_SLUG="${REPO_SLUG:-admob-compose-multiplatform}"
URL="https://klibs.io/project/${REPO_OWNER}/${REPO_SLUG}"

echo "klibs.io listing check"
echo "  url: ${URL}"

BODY="$(curl -sL --max-time 30 "${URL}")" || {
  echo "  RESULT: UNKNOWN (curl failed)"
  exit 2
}

if [ -z "${BODY}" ]; then
  echo "  RESULT: UNKNOWN (empty response)"
  exit 2
fi

if printf '%s' "${BODY}" | grep -q 'not-found-page-message'; then
  echo "  RESULT: NOT INDEXED"
  echo "  klibs.io has no project page for ${REPO_OWNER}/${REPO_SLUG}."
  exit 1
fi

echo "  RESULT: INDEXED"
echo
echo "  Metadata rendered on the page (verify each against docs/distribution/klibs-io.md):"
printf '%s' "${BODY}" | python3 -c '
import sys, re, html
s = sys.stdin.read()
s = re.sub(r"<script.*?</script>", " ", s, flags=re.S)
s = re.sub(r"<style.*?</style>", " ", s, flags=re.S)
t = html.unescape(re.sub(r"<[^>]+>", " ", s))
print("   ", re.sub(r"\s+", " ", t).strip()[:900])
'
exit 0
```

- [ ] **Step 2: Make it executable and run it — it must report NOT INDEXED**

```bash
chmod +x /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-klibs-listing.sh
/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-klibs-listing.sh; echo "exit=$?"
```

Expected output:

```
klibs.io listing check
  url: https://klibs.io/project/Meet-Miyani/admob-compose-multiplatform
  RESULT: NOT INDEXED
  klibs.io has no project page for Meet-Miyani/admob-compose-multiplatform.
exit=1
```

If it prints `INDEXED`, the listing appeared early — that is fine. Skip to Step 4 and fill the record with the real values.

- [ ] **Step 3: Prove the script detects a real listing (guard against a false negative)**

The marker-based check would report NOT INDEXED for every project if the marker string were wrong. Run it against a project known to be indexed:

```bash
REPO_OWNER=GitLiveApp REPO_SLUG=firebase-kotlin-sdk \
  /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-klibs-listing.sh; echo "exit=$?"
```

Expected: `RESULT: INDEXED`, `exit=0`, followed by a metadata line containing `firebase-kotlin-sdk`, `GitHub stars`, and `License`. If this prints NOT INDEXED, the marker is wrong — stop and re-derive it before continuing.

- [ ] **Step 4: Write the verification record with the criteria, the expected metadata, and the correction paths**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/klibs-io.md`:

```markdown
# klibs.io listing — admob-cmp

klibs.io is JetBrains' index of Kotlin Multiplatform libraries. Listing is
automatic. There is no submit-your-library form for the normal path.

Run `scripts/distribution/verify-klibs-listing.sh` to re-check. Exit 0 =
indexed, 1 = missing, 2 = unknown (network failure — never record a 2 as
"missing").

## Indexing criteria and our status

Quoted from https://klibs.io/faq:

| # | Criterion | Status 2026-07-31 |
|---|---|---|
| 1 | "The project is open source and is available on GitHub." | Met |
| 2 | "At least one artifact is published to Maven Central." | Met — 1.0.0, 1.0.1, 1.0.2, 1.1.0 |
| 3 | "At least one artifact is multiplatform – must have kotlin-tooling-metadata.json" | Met — `admob-cmp-1.1.0-kotlin-tooling-metadata.json` |
| 4 | "At least one artifact's POM contains a valid link to the GitHub repository, either under 'url' or 'scm.url'" | Met **via a 301 redirect only** — see the risk below |

Timing, quoted from the FAQ: "All projects fulfilling the criteria are added
automatically within one month (it is a frequency of maven central public index
update). If your project is already presented in the klibs.io, then new versions
should appear the next day after they are published to Maven Central."

## Status

- **2026-07-31 — NOT INDEXED.** `https://klibs.io/project/Meet-Miyani/Admob-CMP`
  returns the not-found page. 1.1.0 was published 2026-07-30, one day earlier, so
  this is expected: the one-month Maven Central index cycle has not run yet.

## Open risk: criterion 4 depends on a redirect

Every published POM for 1.0.0–1.1.0 carries the **old** repo URL:

    <url>https://github.com/Meet-Miyani/Admob-CMP</url>
    <scm><url>https://github.com/Meet-Miyani/Admob-CMP</url></scm>

After the Plan 1 rename that URL 301-redirects to
`https://github.com/Meet-Miyani/admob-compose-multiplatform`. Whether the klibs.io
indexer follows the redirect is **not documented**. If it does not, criterion 4
fails and the project is never picked up automatically.

Decision rule:

1. Re-run the script 30 days after 1.1.0 was published (i.e. on or after
   **2026-08-30**). If INDEXED, no action.
2. If still missing on 2026-08-30, file an index request (human step, below).
   This is the cheap probe and costs nothing if the delay was only timing.
3. If the index request is answered with "the POM URL does not resolve", ship
   `1.1.1` from Task 3's corrected metadata. That release carries the new URL
   directly and removes the dependency on the redirect entirely.

Do not skip to step 3. Publishing a version purely to satisfy an indexer is a
real cost to consumers and is only justified once the cheaper probes fail.

## Correction paths (all are human steps — they post publicly)

| Situation | Where | URL |
|---|---|---|
| Criteria met but still not listed after a month | JetBrains index-request form | https://github.com/JetBrains/klibs-io-issue-management/issues/new?assignees=&labels=index-request&projects=&template=index_request.yml |
| Listed, but the metadata is wrong | "Suggest an edit" on the project page, which opens | https://github.com/JetBrains/klibs-io-issue-management/issues/new/choose (template `suggest_an_edit.yml`) |
| A question about why it is missing | Question template | https://github.com/JetBrains/klibs-io-issue-management/issues/new?assignees=&labels=question&projects=&template=question.md&title= |
| A klibs.io site bug | https://github.com/JetBrains/klibs-io/issues/new/choose |

## Metadata to verify once the listing appears

klibs.io generates some fields with AI when a project's own metadata is thin:
"When library metadata is incomplete, klibs.io may generate additional metadata
for libraries using AI to improve search and their discoverability." So the
generated description **must** be read, not assumed.

Check each of these on the project page and open a "Suggest an edit" issue for
any that is wrong:

- [ ] **Project name** reads `admob-compose-multiplatform` under author `Meet-Miyani`.
- [ ] **Description** names Compose Multiplatform and AdMob, and lists the six ad
      formats. Reject any AI-generated description that calls this a Consent
      Management Platform — that is the wrong meaning of "CMP" and the exact
      confusion spec §3 exists to avoid.
- [ ] **Targets** show **Android** and **Kotlin/Native** only. If JVM, JS, or Wasm
      appear, the metadata is wrong — the published
      `kotlin-tooling-metadata.json` declares `androidJvm`, two `native` targets,
      and `common` metadata, nothing else.
- [ ] **License** shows `Apache License 2.0`. If it is blank, GitHub is not
      detecting `LICENSE` at the repo root — that is a Plan 1 regression, fix it
      there, not here.
- [ ] **Homepage** points at `https://ads.avinya.dev`.
- [ ] **GitHub repository** points at the new slug, not a redirect from the old one.
- [ ] **Tag** is `service-sdk` or an ads-appropriate equivalent.
- [ ] **Latest release** is the current version.

Record the outcome of each check as a dated line under Status above.
```

- [ ] **Step 5: HUMAN STEP — outward-facing publication. File the index request, but only if the gate is open**

Do **not** perform this step before 2026-08-30, and only if Step 2's script reports NOT INDEXED on or after that date. Filing early wastes a maintainer's time on a project that is simply inside its normal one-month window.

An agent must stop here and hand this to a human. The human:

1. Re-runs `scripts/distribution/verify-klibs-listing.sh` and confirms exit code `1`.
2. Opens https://github.com/JetBrains/klibs-io-issue-management/issues/new?assignees=&labels=index-request&projects=&template=index_request.yml
3. Fills the template with:
   - Repository: `https://github.com/Meet-Miyani/admob-compose-multiplatform`
   - Maven coordinates: `dev.avinya.ads:admob-cmp`
   - Notes: *"Published to Maven Central since 1.0.0 (currently 1.1.0). `kotlin-tooling-metadata.json` is present on every release. The repository was renamed from `Admob-CMP` to `admob-compose-multiplatform` on <DATE>; POMs for 1.0.0–1.1.0 carry the pre-rename URL, which now 301-redirects to the current one. Please let me know if the indexer needs a POM with the post-rename URL and I will cut a patch release."*
4. Reads the whole issue body once more before submitting, then submits.
5. Records the issue URL and date under **Status** in `docs/distribution/klibs-io.md`.

- [ ] **Step 6: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add scripts/distribution/verify-klibs-listing.sh docs/distribution/klibs-io.md
git commit -m "docs(distribution): add klibs.io listing verification script and record"
```

---

### Task 2: `terrakok/kmp-awesome` entry

`terrakok/kmp-awesome` is the high-authority curated KMP list. Its contribution rules were read from the live README and **two of the four are currently unmet**. This task writes the exact entry so it is ready the moment the gates open, and records the gate status honestly. It does **not** open a PR now — a PR that violates the stated rules wastes a maintainer's time and makes the project look like it did not read the contributing guide.

**Files:**
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/kmp-awesome-entry.md`

**Interfaces:**
- Consumes: the repo slug from the Global Constraints (`admob-compose-multiplatform`) and the Maven coordinate `dev.avinya.ads:admob-cmp`.
- Produces: `docs/distribution/kmp-awesome-entry.md`, containing the verbatim four-line entry. Task 7's `collect-launch-metrics.sh` greps the live `kmp-awesome` `README.MD` for the string `admob-compose-multiplatform` to score the §12 row "`kmp-awesome` entry".

- [ ] **Step 1: Re-confirm the contribution rules and the target category against the live file**

The rules and section names below were read on 2026-07-31. Re-read them — an awesome-list's rules change without notice, and submitting against stale rules is the failure mode this step exists to prevent.

```bash
cd /private/tmp/claude-501/-Users-meetmiyani-Documents-MeetMiyani-MEET-AdmobCMP/5f08656e-b6d0-4a0b-844b-887151d66afd/scratchpad
curl -sL --max-time 30 https://raw.githubusercontent.com/terrakok/kmp-awesome/master/README.MD -o kmp-awesome.md
echo "--- contribution guide ---"
sed -n '/^## Contribution guide/,/^## License/p' kmp-awesome.md
echo "--- category headings ---"
grep -n '^### ' kmp-awesome.md
echo "--- any existing ads entry? ---"
grep -in 'admob\|basic-ads\|monetiz\|advertis' kmp-awesome.md || echo "(none — the category is uncontested)"
```

Note the filename: `README.MD` with an **uppercase** extension. `README.md` returns HTTP 404.

Expected contribution guide, verbatim:

```
## Contribution guide
Feel free to contribute. Follow common style and welcome!
Few rules:
 1) every suggesion should contain link to any project uses the library (not an author's sample)
 2) the library is supposed to support Android + iOS targets
 3) the library must be published to the MavenCentral
 4) the library should be a popular (github stars ~ 50, at least)
```

Expected: no ads/monetization heading exists. The closest correct home is `### 🧩 Service SDK` — that section holds Firebase, Supabase, OpenAI, Sentry, ConfigCat and GrowthBook, i.e. third-party service wrappers, which is exactly what an AdMob wrapper is. `### 🔍 Analytics` is **wrong**: it holds CrashKiOS, NSExceptionKt and trckr — crash reporting and event tracking, not ad serving.

- [ ] **Step 2: Score the four rules against the project's real state**

Run:

```bash
curl -s --max-time 25 https://api.github.com/repos/Meet-Miyani/admob-compose-multiplatform \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print("stars:", d.get("stargazers_count"))'
```

Score:

| Rule | Requirement | Status | Blocking? |
|---|---|---|---|
| 1 | "every suggesion should contain link to any project uses the library (not an author's sample)" | **UNMET.** The only known consumers are this repo's own `androidApp`/`iosApp`/`shared` demo — explicitly excluded by the rule as "an author's sample" | **Yes** |
| 2 | "the library is supposed to support Android + iOS targets" | Met — published `kotlin-tooling-metadata.json` declares `androidJvm` + two `native` targets | No |
| 3 | "the library must be published to the MavenCentral" | Met — 1.0.0 through 1.1.0 | No |
| 4 | "the library should be a popular (github stars ~ 50, at least)" | **UNMET** at 0 stars on 2026-07-31 | **Yes** |

- [ ] **Step 3: Write the entry record, including the exact entry and the gate**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/kmp-awesome-entry.md`:

````markdown
# terrakok/kmp-awesome — entry (GATED, do not submit yet)

Repository: https://github.com/terrakok/kmp-awesome
Branch: `master`. File: `README.MD` — **uppercase extension**; `README.md` 404s.
Contribution mechanism: pull request. There is no CONTRIBUTING.md; the rules live
in the `## Contribution guide` section at the bottom of `README.MD`.

## The gate — both conditions must be true before opening a PR

Verbatim from the README's Contribution guide:

```
 1) every suggesion should contain link to any project uses the library (not an author's sample)
 2) the library is supposed to support Android + iOS targets
 3) the library must be published to the MavenCentral
 4) the library should be a popular (github stars ~ 50, at least)
```

| Rule | Status 2026-07-31 |
|---|---|
| 1 — a link to a project that *uses* the library, not the author's own sample | **UNMET** |
| 2 — Android + iOS targets | Met |
| 3 — published to Maven Central | Met |
| 4 — roughly 50 GitHub stars or more | **UNMET (0 stars)** |

**Do not open the PR until rules 1 and 4 are both satisfied.** Rule 4 is the
§12 90-day star target (50, directional), so this entry is naturally a
post-90-day action, not a launch-week action. Rule 1 needs a real third-party
app or library that depends on `dev.avinya.ads:admob-cmp`; the demo modules in
this repo do not count.

Two honest ways rule 1 gets satisfied, neither of which is gameable:

- A consumer opens an issue, a discussion, or a "we ship this" note. Watch for
  it, then ask permission to cite them.
- klibs.io reports a non-zero **Dependents** count on the project page once the
  listing exists (Task 1). A dependent found there is a genuine third-party
  usage link.

Re-check the gate at the 90-day metrics checkpoint (Task 7). If stars are still
below ~50, do not submit; a rejected PR on a curated list is worse than no PR,
because the rejection is public and permanent.

## Where the entry goes

Section `### 🧩 Service SDK`, appended **after** the last existing entry
(`ConfigCat`) and **before** the `### 🧮 Arithmetic` heading.

Why Service SDK: that section holds third-party service wrappers — Firebase,
Supabase, OpenAI, Sentry, GrowthBook, ConfigCat. An AdMob wrapper is the same
shape. `### 🔍 Analytics` is the wrong section; it holds crash reporting and
event tracking (CrashKiOS, NSExceptionKt, trckr), not ad serving.

There is no ads or monetization category in the list, and no existing entry
matches `admob`, `basic-ads`, `monetiz` or `advertis`. The category is
uncontested.

Do **not** add a new `### Ads` heading. Adding a section for one entry also
requires editing the `## Contents` HTML table above, which is a much larger diff
and a much easier PR to decline.

## The entry, verbatim — copy these four lines exactly

```
[admob-cmp](https://github.com/Meet-Miyani/admob-compose-multiplatform) - AdMob SDK for Compose Multiplatform
[![GitHub Repo stars](https://img.shields.io/github/stars/Meet-Miyani/admob-compose-multiplatform?style=flat)](https://github.com/Meet-Miyani/admob-compose-multiplatform)
[![Maven Central](https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp)](https://central.sonatype.com/artifact/dev.avinya.ads/admob-cmp)
> Banner, interstitial, rewarded, rewarded interstitial, app-open and native ads for Android and iOS behind one Kotlin API, with UMP consent in the initialization flow, mediation and paid/revenue events. Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

Followed by exactly one blank line, matching every other entry in the file.

Format notes, derived from the file itself:

- Entries are **not** bulleted and **not** alphabetised. They are plain
  paragraphs appended in the order they were merged. Append at the end of the
  section; do not sort.
- Line 1 is `[Name](repo-url) - short description`. The separator is a plain
  hyphen surrounded by single spaces, not an en dash.
- Line 2 is the `shields.io` GitHub-stars badge, linking to the repo.
- Line 3 is the `shields.io` Maven Central badge, linking to the
  `central.sonatype.com/artifact/<group>/<artifact>` page.
- Line 4 is a `>` blockquote holding the long description.
- Six of the ten Service SDK entries have a trailing space at the end of line 1
  and four do not. A single trailing space is not a Markdown hard break, so all
  ten render identically. The entry above matches `ConfigCat`, the entry it
  follows, and has no trailing space.
- The link text is `admob-cmp`, the Maven artifact id, not the repo slug — the
  section mixes both conventions (`supabase-kt` and `bitcoin-kmp` use the
  artifact name) and the artifact id is what a reader copies into Gradle.

## PR metadata

Title:

```
Add admob-cmp to Service SDK
```

Body:

```
Adds `dev.avinya.ads:admob-cmp` — an AdMob SDK for Compose Multiplatform
covering banner, interstitial, rewarded, rewarded interstitial, app-open and
native ads on Android and iOS from one Kotlin API.

Against the contribution rules:

1. Used by: <LINK TO A THIRD-PARTY PROJECT — REQUIRED, NOT AN AUTHOR SAMPLE>
2. Targets: Android (`androidJvm`) and iOS (`iosArm64`, `iosSimulatorArm64`,
   `iosX64`), per the published `kotlin-tooling-metadata.json`.
3. Maven Central: https://central.sonatype.com/artifact/dev.avinya.ads/admob-cmp
   — 1.0.0, 1.0.1, 1.0.2, 1.1.0.
4. Stars: <CURRENT COUNT>

Docs: https://ads.avinya.dev

Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
trademarks of Google LLC.
```

The two angle-bracket fields are deliberately unfilled: they cannot be written
until the gate opens, and inventing either of them would be dishonest to the
maintainer. If either is still empty, the PR is not ready.

## Fallback if the gate stays shut

`Heapy/awesome-kotlin` is the other curated list linked from
`kotlinlang.org/community`. It states **no** star threshold and no
third-party-usage requirement. Its contributing guide
(`.github/contributing.md`) says:

```
1. Checkout `main` branch;
2. Edit  [src/main/resources/links/]${category}.awesome.kts;
3. Create PR.
```

The relevant category file is `src/main/resources/links/Libraries.awesome.kts`
(alternatives: `Android.awesome.kts`, `Native.awesome.kts`). Entries are Kotlin
script, not Markdown, so the four-line Markdown entry above cannot be reused —
read a neighbouring entry in that file and match its DSL.

This is a genuine alternative, not a consolation prize: it is linked from
kotlinlang.org and it accepts the project today.
````

- [ ] **Step 4: HUMAN STEP — outward-facing publication. Open the PR, but only once the gate is open**

An agent must never fork `terrakok/kmp-awesome`, never push a branch to it, and never open a pull request against it. Stop here and hand the record to a human.

The human, at the 90-day checkpoint or later:

1. Re-runs Step 1 to confirm the rules have not changed.
2. Confirms stars ≥ ~50 and that a real third-party usage link exists. If either is missing, **stop** — do not submit.
3. Forks the repo, edits `README.MD`, and appends the four-line entry after the `ConfigCat` entry in `### 🧩 Service SDK`.
4. Confirms the diff is exactly five lines added (four content lines plus one blank) and touches nothing else.
5. Opens the PR with the title and body above, both angle-bracket fields filled.
6. Records the PR URL and date in `docs/distribution/kmp-awesome-entry.md`.

- [ ] **Step 5: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs/distribution/kmp-awesome-entry.md
git commit -m "docs(distribution): record the kmp-awesome entry and its contribution gate"
```

---

### Task 3: Maven Central POM metadata

Published POMs for 1.0.0–1.1.0 are immutable and keep the old repo URL forever; GitHub's 301 covers those readers. This task makes every *future* release carry the new URL, gets the trademark line into the POM description as spec §3 requires, aligns the names and descriptions with the §7 keywords, and adds a CI check so the URLs can never silently drift back.

**Files:**
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/gradle.properties:25-35`
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-gradle-plugin/gradle.properties:1-18`
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp/gradle.properties`
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-core/gradle.properties`
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-compose/gradle.properties`
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-pom-metadata.sh`
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.github/workflows/release-readiness.yml:34-42`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `scripts/distribution/verify-pom-metadata.sh` — exits `0` when all POM properties across all five `gradle.properties` files are correct, `1` with a per-failure report otherwise. Wired into the `android-and-metadata` job of `release-readiness.yml`.

- [ ] **Step 1: Write the failing verification script**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-pom-metadata.sh`:

```bash
#!/usr/bin/env bash
# Verify the POM metadata that ships in every published artifact.
#
# Published POMs are immutable: 1.0.0-1.1.0 carry the pre-rename repo URL
# permanently, and GitHub's 301 covers those readers. This check exists so that
# every FUTURE release carries the new URL, the trademark line, and the search
# keywords from the public-visibility spec.
#
# Two files hold the full URL/SCM property set, because the Gradle plugin is a
# separate included build with its own publishing config. Both must agree.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NEW_REPO="https://github.com/Meet-Miyani/admob-compose-multiplatform"
OLD_REPO="https://github.com/Meet-Miyani/Admob-CMP"
TRADEMARK="Not affiliated with or endorsed by Google."
FAIL=0

fail() { echo "  FAIL: $*"; FAIL=1; }
ok()   { echo "  ok:   $*"; }

prop() { # prop <file> <key> -> value, or empty
  sed -n "s/^$2=//p" "$1" | head -1
}

check_eq() { # check_eq <file> <key> <expected>
  local got; got="$(prop "$1" "$2")"
  if [ "${got}" = "$3" ]; then ok "$1 :: $2"; else
    fail "$1 :: $2"; echo "        expected: $3"; echo "        actual:   ${got:-<missing>}"
  fi
}

check_contains() { # check_contains <file> <key> <substring>
  local got; got="$(prop "$1" "$2")"
  case "${got}" in
    *"$3"*) ok "$1 :: $2 contains '$3'" ;;
    *) fail "$1 :: $2 must contain '$3'"; echo "        actual: ${got:-<missing>}" ;;
  esac
}

echo "== URL and SCM properties (both files that define them) =="
for f in "${ROOT}/gradle.properties" "${ROOT}/admob-cmp-gradle-plugin/gradle.properties"; do
  check_eq "$f" POM_URL                 "${NEW_REPO}"
  check_eq "$f" POM_SCM_URL             "${NEW_REPO}"
  check_eq "$f" POM_SCM_CONNECTION      "scm:git:${NEW_REPO}.git"
  check_eq "$f" POM_SCM_DEV_CONNECTION  "scm:git:ssh://git@github.com/Meet-Miyani/admob-compose-multiplatform.git"
done

echo "== No file anywhere still references the old repo slug =="
if grep -rn "${OLD_REPO}" "${ROOT}"/gradle.properties "${ROOT}"/*/gradle.properties 2>/dev/null; then
  fail "the pre-rename repo URL is still present in a gradle.properties file"
else
  ok "no gradle.properties references ${OLD_REPO}"
fi

echo "== Descriptions carry the trademark line (spec §3) =="
for f in "${ROOT}/admob-cmp/gradle.properties" \
         "${ROOT}/admob-cmp-core/gradle.properties" \
         "${ROOT}/admob-cmp-compose/gradle.properties" \
         "${ROOT}/admob-cmp-gradle-plugin/gradle.properties"; do
  check_contains "$f" POM_DESCRIPTION "${TRADEMARK}"
done

echo "== Names and descriptions carry the search keywords (spec §7) =="
check_contains "${ROOT}/admob-cmp/gradle.properties"                POM_NAME        "Compose Multiplatform"
check_contains "${ROOT}/admob-cmp-core/gradle.properties"           POM_DESCRIPTION "Kotlin Multiplatform"
check_contains "${ROOT}/admob-cmp-compose/gradle.properties"        POM_DESCRIPTION "Compose Multiplatform"
check_contains "${ROOT}/admob-cmp-gradle-plugin/gradle.properties"  POM_DESCRIPTION "Kotlin Multiplatform"
check_contains "${ROOT}/admob-cmp/gradle.properties"                POM_DESCRIPTION "AdMob"

echo
if [ "${FAIL}" -ne 0 ]; then
  echo "POM metadata verification FAILED"
  exit 1
fi
echo "POM metadata verification passed"
exit 0
```

- [ ] **Step 2: Run it and confirm it fails on the real current state**

```bash
chmod +x /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-pom-metadata.sh
/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-pom-metadata.sh; echo "exit=$?"
```

Expected: `exit=1`, with failures including `POM_URL`, `POM_SCM_URL`, `POM_SCM_CONNECTION` and `POM_SCM_DEV_CONNECTION` in **both** `gradle.properties` and `admob-cmp-gradle-plugin/gradle.properties`, plus four `POM_DESCRIPTION must contain 'Not affiliated with or endorsed by Google.'` failures.

If Plan 1 has already landed its URL fix, the URL failures will be absent from the root file but **must still be present for `admob-cmp-gradle-plugin/gradle.properties`** — spec §9 names only `gradle.properties`, so the plugin's copy is the one Plan 1 is likely to have missed. If both files already pass the URL checks, that is fine; the description failures are the ones this task must fix regardless.

- [ ] **Step 3: Fix the URL and SCM properties in the root `gradle.properties`**

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/gradle.properties`, replace lines 25-35 with:

```properties
POM_NAME=AdMob CMP
POM_DESCRIPTION=Plug-and-play Compose Multiplatform AdMob SDK for Android GMA Next-Gen and iOS Google Mobile Ads. Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
POM_URL=https://github.com/Meet-Miyani/admob-compose-multiplatform
POM_LICENSE_NAME=Apache License 2.0
POM_LICENSE_URL=https://www.apache.org/licenses/LICENSE-2.0.txt
POM_INCEPTION_YEAR=2025
POM_DEVELOPER_ID=Meet-Miyani
POM_DEVELOPER_NAME=Meet Miyani
POM_SCM_URL=https://github.com/Meet-Miyani/admob-compose-multiplatform
POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/admob-compose-multiplatform.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/admob-compose-multiplatform.git
```

These root `POM_NAME`/`POM_DESCRIPTION` values are defaults that every module overrides; they are kept correct so a future module added without its own overrides still publishes sane metadata.

- [ ] **Step 4: Fix the Gradle plugin's own `gradle.properties` — the second, easily missed copy**

The plugin is a separate included build with its own complete publishing configuration. Replace the whole of `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-gradle-plugin/gradle.properties` with:

```properties
GROUP=dev.avinya.ads
VERSION_NAME=1.1.0
POM_ARTIFACT_ID=admob-cmp-gradle-plugin
POM_NAME=AdMob CMP Gradle Plugin
POM_DESCRIPTION=Links Google Mobile Ads and UMP into Kotlin/Native test executables for Kotlin Multiplatform consumers of admob-cmp, fixing "Undefined symbols: _OBJC_CLASS_$_GAD*" at iOS test link time. Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
POM_URL=https://github.com/Meet-Miyani/admob-compose-multiplatform
POM_LICENSE_NAME=Apache License 2.0
POM_LICENSE_URL=https://www.apache.org/licenses/LICENSE-2.0.txt
POM_INCEPTION_YEAR=2025
POM_DEVELOPER_ID=Meet-Miyani
POM_DEVELOPER_NAME=Meet Miyani
POM_SCM_URL=https://github.com/Meet-Miyani/admob-compose-multiplatform
POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/admob-compose-multiplatform.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/admob-compose-multiplatform.git
mavenCentralPublishing=true
mavenCentralAutomaticPublishing=false
signAllPublications=true
```

The `$_GAD*` in the description is a literal string inside a `.properties` value, not shell or Gradle interpolation — `java.util.Properties` does not expand `$`. Step 6 asserts it survives into the generated POM.

- [ ] **Step 5: Add the trademark line to the three library module descriptions**

Replace the whole of `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp/gradle.properties`:

```properties
POM_ARTIFACT_ID=admob-cmp
POM_NAME=AdMob Compose Multiplatform SDK
POM_DESCRIPTION=Plug-and-play Compose Multiplatform AdMob SDK wrapper for Android GMA Next-Gen and iOS Google Mobile Ads. Banner, interstitial, rewarded, rewarded interstitial, app-open and native ads with UMP consent, mediation and paid/revenue events. Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

Replace the whole of `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-core/gradle.properties`:

```properties
POM_ARTIFACT_ID=admob-cmp-core
POM_NAME=AdMob CMP — Core
POM_DESCRIPTION=Compose-free Kotlin Multiplatform core for the admob-cmp AdMob SDK (AdManager, consent, full-screen orchestration, banner/native pools, iOS bindings). Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

Replace the whole of `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-compose/gradle.properties`:

```properties
POM_ARTIFACT_ID=admob-cmp-compose
POM_NAME=AdMob CMP — Compose
POM_DESCRIPTION=Compose Multiplatform UI for the admob-cmp AdMob SDK (BannerAdView, NativeAdView, native-ad layout DSL, debug console, rememberAdManager). Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

`—` is the existing escaped em dash; `java.util.Properties` decodes it. Leave it escaped — writing a raw `—` risks an encoding change in the published POM.

`POM_NAME` for the umbrella artifact stays `AdMob Compose Multiplatform SDK`. That is what is already published for 1.1.0, it carries two of the three §7 head-term tokens, and changing it would make the artifact's display name inconsistent across versions for no gain.

- [ ] **Step 6: Run the verification script — it must now pass**

```bash
/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/verify-pom-metadata.sh; echo "exit=$?"
```

Expected: every line prefixed `ok:`, final line `POM metadata verification passed`, `exit=0`.

- [ ] **Step 7: Generate a real POM and confirm the properties reach the XML**

The properties check proves the inputs. This proves the output.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
./gradlew :admob-cmp:generatePomFileForKotlinMultiplatformPublication --no-configuration-cache
python3 - <<'PY'
import glob, re, sys
paths = glob.glob('admob-cmp/build/publications/**/pom-default.xml', recursive=True)
assert paths, 'no generated POM found'
xml = open(paths[0], encoding='utf-8').read()
checks = {
    'new repo url':  'https://github.com/Meet-Miyani/admob-compose-multiplatform',
    'trademark':     'Not affiliated with or endorsed by Google.',
    'no old slug':   None,
}
ok = True
for label, needle in checks.items():
    if needle is None:
        bad = 'Meet-Miyani/Admob-CMP' in xml
        print(f'{"ok  " if not bad else "FAIL"} {label}')
        ok &= not bad
    else:
        hit = needle in xml
        print(f'{"ok  " if hit else "FAIL"} {label}')
        ok &= hit
print(re.search(r'<description>.*?</description>', xml, re.S).group(0))
sys.exit(0 if ok else 1)
PY
```

Expected: three `ok` lines, the `<description>` element printed with the trademark sentence at the end, exit 0.

If the publication name differs, list the available tasks with `./gradlew :admob-cmp:tasks --all --no-configuration-cache | grep generatePomFile` and run the one that matches.

- [ ] **Step 8: Wire the check into CI so the URLs cannot silently drift back**

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.github/workflows/release-readiness.yml`, insert this step in the `android-and-metadata` job immediately after `- uses: gradle/actions/setup-gradle@v6` (line 29) and before `- name: Build and test the Gradle plugin`:

```yaml
      - name: Verify POM metadata (URLs, trademark line, keywords)
        run: ./scripts/distribution/verify-pom-metadata.sh
```

It runs before the Gradle steps deliberately: it takes under a second and fails the job immediately on a metadata regression rather than after a multi-minute build.

- [ ] **Step 9: Record that published POMs are immutable, so nobody tries to "fix" them**

Append to `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/klibs-io.md`, under the `## Open risk` section:

```markdown
### Why the old URL is not a defect to fix

Maven Central artifacts are immutable by policy. The POMs for 1.0.0, 1.0.1,
1.0.2 and 1.1.0 will carry `https://github.com/Meet-Miyani/Admob-CMP` forever
and there is no republish path. This is fine for humans and for most tooling:
GitHub 301-redirects the old URL, and the redirect holds as long as the old
repository name is never reused.

`scripts/distribution/verify-pom-metadata.sh` guarantees only that *future*
releases carry the new URL. The only consumer that might not follow the
redirect is the klibs.io indexer — see the decision rule above.
```

- [ ] **Step 10: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add gradle.properties admob-cmp/gradle.properties admob-cmp-core/gradle.properties \
        admob-cmp-compose/gradle.properties admob-cmp-gradle-plugin/gradle.properties \
        scripts/distribution/verify-pom-metadata.sh .github/workflows/release-readiness.yml \
        docs/distribution/klibs-io.md
git commit -m "fix(publishing): point POM metadata at the new repo and add the trademark line

Both gradle.properties files that define POM_URL/POM_SCM_* are updated - the
root one and the Gradle plugin's included build, which is easy to miss. Every
module description now carries the trademark disclaimer required by the
public-visibility spec, and release-readiness CI enforces all of it."
```

---

### Task 4: `avinya.dev/open-source/` featured card

The studio site already lists the SDK, but as one undifferentiated card sorted sixth by star count, linking only to GitHub. This task adds a featured treatment and a direct link to `https://ads.avinya.dev`, so the studio site's existing audience — `compose-skill` has 274 stars — has a path into the docs.

**Files:**
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/lib/repos.ts`
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/lib/repos.test.ts`
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/config.ts`
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/pages/open-source.astro`

**Interfaces:**
- Consumes: the new repo slug `admob-compose-multiplatform` (Plan 1) and the docs origin `https://ads.avinya.dev` (Plan 2).
- Produces, in the studio site repo:
  - `export interface FeaturedRepo { docsUrl: string; tagline: string }`
  - `export const FEATURED_REPOS: Record<string, FeaturedRepo>` in `src/config.ts`
  - `Repo` gains three fields: `featured: boolean`, `docsUrl: string | null`, `tagline: string | null`
  - `mapRepos(raw: any[], featured?: Record<string, FeaturedRepo>): Repo[]`
  - `fetchRepos(username: string, token?: string, fetcher?: JsonFetcher, featured?: Record<string, FeaturedRepo>): Promise<Repo[]>`

- [ ] **Step 1: Locate the studio site repository — do not assume the path**

This is a different repository from the SDK. Find it rather than hardcoding:

```bash
find ~/Documents ~/Developer ~/Projects ~/Sites -maxdepth 5 -name "open-source.astro" -not -path "*/node_modules/*" 2>/dev/null
```

Expected: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/pages/open-source.astro`

Confirm it is the right repository — the remote must be the studio site, and the page must be the one that renders the cards:

```bash
git -C /Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA remote get-url origin
grep -n "fetchRepos\|githubUsername" /Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/pages/open-source.astro \
                                     /Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/config.ts
```

Expected: remote `https://github.com/Meet-Miyani/AVINYA.git`; `open-source.astro` calls `fetchRepos(DATA.githubUsername, import.meta.env.GITHUB_TOKEN)`; `config.ts` defines `githubUsername`.

If `find` returns nothing, widen it: `find ~ -maxdepth 7 -name "open-source.astro" -not -path "*/node_modules/*" 2>/dev/null`. If it still returns nothing, the repo is not cloned locally — stop and ask the user to clone `https://github.com/Meet-Miyani/AVINYA.git`. Do not proceed by guessing.

**Note for the rest of this task:** the repo slug list is *not* hardcoded anywhere. `fetchRepos` pulls every public repo from the GitHub API at build time and `mapRepos` sorts by stars. So the Plan 1 rename propagates to this page automatically on the next build — no slug edit is needed. What is missing is a featured treatment and a docs link, and that is what the remaining steps add.

- [ ] **Step 2: Write the failing tests**

Replace the whole of `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/lib/repos.test.ts`:

```ts
import { describe, it, expect, vi } from 'vitest';
import { mapRepos, fetchRepos } from './repos';

const API = [
  { name: 'alpha', description: 'A tool', html_url: 'https://github.com/u/alpha',
    stargazers_count: 120, language: 'Kotlin', fork: false, private: false, archived: false },
  { name: 'beta', description: null, html_url: 'https://github.com/u/beta',
    stargazers_count: 300, language: 'TypeScript', fork: false, private: false, archived: false },
  { name: 'a-fork', description: 'x', html_url: 'https://github.com/u/a-fork',
    stargazers_count: 999, language: 'Go', fork: true, private: false, archived: false },
  { name: 'admob-compose-multiplatform', description: 'AdMob SDK',
    html_url: 'https://github.com/u/admob-compose-multiplatform',
    stargazers_count: 0, language: 'Kotlin', fork: false, private: false, archived: false },
];

const FEATURED = {
  'admob-compose-multiplatform': {
    docsUrl: 'https://ads.avinya.dev',
    tagline: 'Ads for Android and iOS from one Kotlin API.',
  },
};

describe('mapRepos', () => {
  it('drops forks, sorts by stars desc, and normalizes missing description', () => {
    const r = mapRepos(API);
    expect(r.map((x) => x.name)).toEqual(['beta', 'alpha', 'admob-compose-multiplatform']);
    expect(r[1].description).toBe('A tool');
    expect(r[0].description).toBe('');
    expect(r[0].stars).toBe(300);
  });

  it('marks nothing featured when no featured map is supplied', () => {
    const r = mapRepos(API);
    expect(r.every((x) => x.featured === false)).toBe(true);
    expect(r.every((x) => x.docsUrl === null && x.tagline === null)).toBe(true);
  });

  it('floats a featured repo above higher-starred repos', () => {
    const r = mapRepos(API, FEATURED);
    expect(r[0].name).toBe('admob-compose-multiplatform');
    expect(r.map((x) => x.name)).toEqual(['admob-compose-multiplatform', 'beta', 'alpha']);
  });

  it('attaches the docs url and tagline to the featured repo only', () => {
    const r = mapRepos(API, FEATURED);
    expect(r[0].featured).toBe(true);
    expect(r[0].docsUrl).toBe('https://ads.avinya.dev');
    expect(r[0].tagline).toBe('Ads for Android and iOS from one Kotlin API.');
    expect(r[1].featured).toBe(false);
    expect(r[1].docsUrl).toBeNull();
  });

  it('ignores a featured entry whose repo the API did not return', () => {
    const r = mapRepos(API, { 'renamed-away': { docsUrl: 'https://x.dev', tagline: 't' } });
    expect(r.some((x) => x.featured)).toBe(false);
    expect(r.map((x) => x.name)).toEqual(['beta', 'alpha', 'admob-compose-multiplatform']);
  });
});

describe('fetchRepos', () => {
  it('returns [] and does not throw on fetch failure', async () => {
    const bad = vi.fn().mockRejectedValue(new Error('rate limit'));
    await expect(fetchRepos('user', undefined, bad)).resolves.toEqual([]);
  });
  it('returns [] on non-array JSON', async () => {
    const ok = vi.fn().mockResolvedValue({ message: 'Not Found' });
    await expect(fetchRepos('nobody', undefined, ok)).resolves.toEqual([]);
  });
  it('passes the featured map through to mapRepos', async () => {
    const ok = vi.fn().mockResolvedValue(API);
    const r = await fetchRepos('user', undefined, ok, FEATURED);
    expect(r[0].name).toBe('admob-compose-multiplatform');
    expect(r[0].docsUrl).toBe('https://ads.avinya.dev');
  });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA && npm test
```

Expected: FAIL. The three new `mapRepos` featured tests and `passes the featured map through to mapRepos` fail, because `Repo` has no `featured`, `docsUrl`, or `tagline` and neither function accepts a featured map. The two pre-existing `fetchRepos` failure tests still pass.

- [ ] **Step 4: Implement the featured support in `repos.ts`**

Replace the whole of `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/lib/repos.ts`:

```ts
export interface FeaturedRepo {
  docsUrl: string;
  tagline: string;
}

export interface Repo {
  name: string;
  description: string;
  url: string;
  stars: number;
  language: string | null;
  featured: boolean;
  docsUrl: string | null;
  tagline: string | null;
}

export type JsonFetcher = (url: string, token?: string) => Promise<unknown>;

const defaultFetcher: JsonFetcher = async (url, token) => {
  const res = await fetch(url, {
    headers: {
      accept: 'application/vnd.github+json',
      'user-agent': 'avinya-build',
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
};

// Featured repos float to the top regardless of star count; everything else
// stays sorted by stars desc. A featured key naming a repo the API did not
// return is ignored, so a rename can never blank the page.
export function mapRepos(raw: any[], featured: Record<string, FeaturedRepo> = {}): Repo[] {
  return raw
    .filter((r) => r && !r.private && !r.fork && !r.archived)
    .map((r) => {
      const name = String(r.name);
      const f = featured[name];
      return {
        name,
        description: r.description ? String(r.description) : '',
        url: String(r.html_url),
        stars: Number(r.stargazers_count) || 0,
        language: r.language ? String(r.language) : null,
        featured: Boolean(f),
        docsUrl: f ? f.docsUrl : null,
        tagline: f ? f.tagline : null,
      };
    })
    .sort((a, b) => {
      if (a.featured !== b.featured) return a.featured ? -1 : 1;
      return b.stars - a.stars;
    });
}

// NEVER throws. [] -> the page renders the "GitHub feed unavailable" empty-state.
export async function fetchRepos(
  username: string,
  token?: string,
  fetcher: JsonFetcher = defaultFetcher,
  featured: Record<string, FeaturedRepo> = {}
): Promise<Repo[]> {
  try {
    const data = await fetcher(
      `https://api.github.com/users/${encodeURIComponent(username)}/repos?per_page=100&sort=updated`,
      token
    );
    if (!Array.isArray(data)) return [];
    return mapRepos(data, featured);
  } catch {
    return [];
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA && npm test
```

Expected: PASS — all tests in `repos.test.ts` green, and the other suites (`articles`, `seo`, `store-stats`) unchanged.

- [ ] **Step 6: Declare the featured repo in `config.ts`**

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/config.ts`, append after the `DATA` block:

```ts
// Repos given a featured card on /open-source/. Keyed by GitHub repo name.
// A key that no longer matches a repo is ignored, so a rename degrades to an
// ordinary card rather than an error.
import type { FeaturedRepo } from './lib/repos';

export const FEATURED_REPOS: Record<string, FeaturedRepo> = {
  'admob-compose-multiplatform': {
    docsUrl: 'https://ads.avinya.dev',
    tagline:
      'Banner, interstitial, rewarded, rewarded interstitial, app-open and native ads for Android and iOS from one Kotlin API. UMP consent, mediation and paid/revenue events included.',
  },
} as const;
```

- [ ] **Step 7: Render the featured card in `open-source.astro`**

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA/src/pages/open-source.astro`, replace the frontmatter import and `fetchRepos` call (lines 1-7) with:

```astro
---
import BaseLayout from '../layouts/BaseLayout.astro';
import EmptyState from '../components/EmptyState.astro';
import { fetchRepos } from '../lib/repos';
import { DATA, FEATURED_REPOS } from '../config';
const repos = await fetchRepos(DATA.githubUsername, import.meta.env.GITHUB_TOKEN, undefined, FEATURED_REPOS);
---
```

Replace the `repos.map(...)` block (lines 20-29) with:

```astro
        {repos.map((r) => (
          <article class:list={['repo', r.featured && 'repo--featured']} data-reveal>
            <div class="repo__top">
              <a class="repo__name" href={r.url}>{r.name}</a>
              <span class="repo__stars"><span aria-hidden="true" class="repo__stardot"></span>★ {r.stars}</span>
            </div>
            <p class="repo__desc">{r.tagline ?? r.description}</p>
            <div class="repo__foot">
              {r.language && <span class="repo__lang"><span aria-hidden="true" class="repo__langdot"></span>{r.language}</span>}
              <span class="repo__links">
                {r.docsUrl && <a class="repo__docs" href={r.docsUrl}>Docs ↗</a>}
                <span class="repo__gh">GitHub ↗</span>
              </span>
            </div>
          </article>
        ))}
```

The card changes from `<a>` to `<article>` because a featured card holds two links, and an `<a>` inside an `<a>` is invalid HTML that browsers silently un-nest. Whole-card clickability is preserved by the stretched-link pattern in the next step, so nothing regresses for the non-featured cards.

- [ ] **Step 8: Add the styles for the stretched link and the featured treatment**

In the same file, replace the `.repo{ … }` and `.repo:hover{ … }` rules with:

```css
  .repo{ position:relative; display:block; text-decoration:none; color:inherit; padding:18px;
    border:1px solid var(--hair); border-radius:16px; transition:border-color .2s ease; }
  .repo:hover{ border-color:var(--ink); }
  .repo__name{ font-weight:500; color:var(--ink); text-decoration:none; }
  /* Stretched link: the repo name covers the whole card, so the card stays
     clickable while the Docs link below remains a separate, reachable target. */
  .repo__name::after{ content:''; position:absolute; inset:0; border-radius:16px; }
  .repo--featured{ grid-column:1 / -1; border-color:var(--ink); }
  .repo--featured .repo__desc{ font-size:15px; }
  .repo__links{ position:relative; z-index:1; display:inline-flex; gap:14px; }
  .repo__docs{ color:var(--ink); text-decoration:none;
    border-bottom:1.5px solid var(--hair); padding-bottom:1px; }
  .repo__docs:hover{ border-color:var(--ink); }
```

`grid-column:1 / -1` makes the featured card span the full width of the existing `auto-fit` grid. `z-index:1` on `.repo__links` lifts the Docs anchor above the stretched pseudo-element so it is clickable and keyboard-reachable.

- [ ] **Step 9: Build the site and verify the card renders**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA
npm run build
grep -o 'repo--featured' dist/open-source/index.html | head -1
grep -o 'https://ads.avinya.dev' dist/open-source/index.html | head -1
grep -o 'admob-compose-multiplatform' dist/open-source/index.html | head -1
```

Expected: `repo--featured`, `https://ads.avinya.dev`, and `admob-compose-multiplatform` each printed once.

If `admob-compose-multiplatform` is missing but `Admob-CMP` is present, Plan 1's rename has not landed yet. The card falls back to an ordinary card and the build still succeeds — that is the designed degradation. Land Plan 1, rebuild, and re-run this step before continuing.

- [ ] **Step 10: Run the repo's own guards**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA
npm test
npm run check:links
npm run check:scroll
```

Expected: all pass. `check:scroll` matters here — the full-width featured card is the one change that could introduce horizontal overflow on a narrow viewport.

- [ ] **Step 11: Commit in the studio site repo**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AVINYA
git add src/lib/repos.ts src/lib/repos.test.ts src/config.ts src/pages/open-source.astro
git commit -m "feat(open-source): featured card for admob-compose-multiplatform

Featured repos float above star order, span the grid, and carry a direct link
to their docs site. Cards become <article> with a stretched link so a card can
hold two anchors without nesting them."
```

- [ ] **Step 12: HUMAN STEP — outward-facing publication. Deploy the studio site**

Pushing this branch publishes a change to a live public website via Cloudflare Pages. An agent must not push or deploy. Stop here.

The human:

1. Confirms `https://avinya.dev/` is *not* still declaring `https://avinya.pages.dev` as its canonical. This is spec §5's blocking defect, owned by Plan 1: `src/config.ts` sets `SITE_URL` from `import.meta.env.SITE_URL` and falls back to `https://avinya.pages.dev`. Check with:
   `curl -s https://avinya.dev/open-source/ | grep -o '<link rel="canonical"[^>]*>'`
   Expected after Plan 1: `href="https://avinya.dev/open-source/"`. **If it still says `avinya.pages.dev`, do not deploy** — shipping a link to `ads.avinya.dev` from a page that disavows its own hostname feeds authority to a throwaway host.
2. Pushes the branch and opens a PR, or pushes to the deploy branch per the repo's normal process.
3. After the deploy, loads `https://avinya.dev/open-source/` and confirms the featured card appears first, spans the full width, shows the tagline rather than the raw GitHub description, and that the **Docs ↗** link navigates to `https://ads.avinya.dev`.

---

### Task 5: GitHub release notes

The `v1.1.0` body is already strong technical writing, but it is written for someone who already knows what the library is, and its "Full changelog" link is broken. This task fixes the live release and creates a template so every future release is discoverable by construction.

**Files:**
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/release-v1.1.0-body.md`
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.github/RELEASE_NOTES_TEMPLATE.md`
- Modify: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp/docs/PUBLISHING.md` (the `## Release checklist` section)

**Interfaces:**
- Consumes: the docs URLs from spec §8 (`/start/quickstart/`, `/reference/troubleshooting/`, `/reference/compatibility/`, `/reference/changelog/`), created by Plan 3.
- Produces: `.github/RELEASE_NOTES_TEMPLATE.md`, referenced by a new step in the `PUBLISHING.md` release checklist.

- [ ] **Step 1: Confirm the broken changelog link and the tag situation**

```bash
curl -s --max-time 25 https://api.github.com/repos/Meet-Miyani/admob-compose-multiplatform/tags \
  | python3 -c 'import sys,json; print("tags:", [t["name"] for t in json.load(sys.stdin)])'
for u in v1.0.2...v1.1.0 1.0.2...1.1.0; do
  printf '%s -> ' "$u"
  curl -s -o /dev/null -w '%{http_code}\n' --max-time 20 \
    "https://github.com/Meet-Miyani/admob-compose-multiplatform/compare/$u"
done
```

Expected: `tags: ['1.1.0']`, and **both** compare URLs return `404`. There is exactly one tag, so no compare link between releases can resolve. The current body's `**Full changelog:** …/compare/v1.0.2...v1.1.0` is dead and must be replaced, not merely re-pointed.

- [ ] **Step 2: Write the replacement release body**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/release-v1.1.0-body.md`. Everything below the first `---` is what gets pasted into GitHub.

````markdown
# Replacement body for the 1.1.0 GitHub release

Release: https://github.com/Meet-Miyani/admob-compose-multiplatform/releases/tag/1.1.0
Tag: `1.1.0` (no `v` prefix — the *name* carries the `v`, the tag does not).

**New release name** (the current one names the fix but not the product, so it
matches nothing anyone searches for):

```
v1.1.0 — Compose Multiplatform AdMob: zero-config Kotlin/Native test linking
```

What changed and why:

- Added a two-sentence opener. A release page is often the first thing a search
  result surfaces, and the current body never says what the library is.
- Added the Gradle coordinates near the top, so the page answers "how do I use
  this" without a click.
- Added documentation links, which did not exist because the docs site did not
  exist when this was written.
- Replaced the dead `compare/v1.0.2...v1.1.0` link (HTTP 404 — there is no
  `1.0.2` tag) with the changelog page.
- Added the trademark line required by the public-visibility spec.
- Kept the whole technical body verbatim. It is good; it was only missing its
  frame.

---

## Kotlin/Native iOS tests now link without manual setup

**AdMob CMP** is a Compose Multiplatform AdMob SDK for Android and iOS — banner,
interstitial, rewarded, rewarded interstitial, app-open and native ads behind one
Kotlin API, with UMP consent in the initialization flow, mediation, and
paid/revenue events.

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:1.1.0")
```

📖 [Quickstart](https://ads.avinya.dev/start/quickstart/) ·
[Installation](https://ads.avinya.dev/start/installation/) ·
[iOS setup](https://ads.avinya.dev/start/ios-setup/) ·
[All docs](https://ads.avinya.dev)

---

If your project runs `:yourModule:iosSimulatorArm64Test`, admob-cmp used to
fail the link with no explanation:

```
Undefined symbols for architecture arm64:
  "_OBJC_CLASS_$_GADBannerView", referenced from: ...
```

The cause was never obvious: your **app** gets Google's binaries from the
GoogleMobileAds Swift package, but a Kotlin/Native **test** executable links
without Xcode and has no access to SPM. Fixing it meant hand-copying framework
download logic, version pins, checksums and linker flags into your own build
script — and keeping them in sync with this library by hand.

That's now one line.

### New: the `dev.avinya.ads.admob-cmp` Gradle plugin

```kotlin
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}
```

It needs `mavenCentral()` in your settings' plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

The plugin:

- downloads the GMA/UMP XCFrameworks matching the bindings this release was
  generated from — versions and checksums are generated from the library build,
  so they can't drift
- verifies the SHA-256 of every archive **before** extracting a single byte
- applies linker options to **test binaries only**. Your shipped app framework
  is untouched and still resolves GoogleMobileAds through SPM, exactly as before
- adds `./gradlew doctorIos`, a report-only check of your SPM products,
  `Info.plist`, and framework cache

Full walkthrough: [Troubleshooting → undefined GAD symbols](https://ads.avinya.dev/reference/troubleshooting/)

### Clearer failures

Both cinterop definitions now carry a `userSetupHint`, so if the link does fail
you get an explanation and a link to the fix instead of a bare symbol dump.

### Upgrading from 1.0.2

```kotlin
implementation("dev.avinya.ads:admob-cmp:1.1.0")
```

Plus the `plugins {}` block above if you run Kotlin/Native tests. **No source
changes required — the public ABI is byte-for-byte identical to 1.0.2.**

If you previously copied XCFramework download or linker logic into your own
build script as a workaround, you can delete it.

### Compatibility

| admob-cmp | Kotlin | Compose Multiplatform | Android minSdk | iOS deployment target |
|---|---|---|---|---|
| 1.1.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |

Full matrix: https://ads.avinya.dev/reference/compatibility/

### Notes

- The Gradle plugin is published as a separate artifact
  (`dev.avinya.ads:admob-cmp-gradle-plugin`) and is versioned in lockstep with
  the library. Always use matching versions.
- Android integration is unchanged in this release.

**Full changelog:** https://ads.avinya.dev/reference/changelog/

---

*Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
trademarks of Google LLC.*
````

- [ ] **Step 3: Write the reusable template**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.github/RELEASE_NOTES_TEMPLATE.md`:

````markdown
<!--
Release notes template for dev.avinya.ads:admob-cmp.

Copy everything below the marker into the GitHub release body and fill the
<ANGLE BRACKET> fields. Delete any section that does not apply — an empty
heading is worse than a missing one.

Release name format (the tag is bare `X.Y.Z`; the *name* carries the `v`):

    vX.Y.Z — <what a user gains, in five words or fewer>

Keep "Compose Multiplatform" or "Kotlin Multiplatform" in the name. A release
page is often the first search result for this project, and a name like
"v1.2.0 — bug fixes" matches nothing anyone types.

Rules that are not negotiable:

1. The opener states what the library IS. Assume the reader arrived from a
   search engine and has never heard of it.
2. Gradle coordinates appear above the fold.
3. Docs links appear above the fold.
4. Never write `compare/<old>...<new>` unless BOTH tags exist. Verify with
   `git tag --list` before publishing; only `1.1.0` existed as of 2026-07-31,
   which is why the changelog page is the default target.
5. The trademark line is the last line, every time.
6. If the public ABI changed, say so explicitly and link the migration notes.
   The ABI is frozen (admob-cmp/CLAUDE.md invariant 12), so in practice this
   line reads "the public ABI is unchanged".
-->

--- COPY BELOW THIS LINE ---

## <One-line headline: the change, phrased as what the user gains>

**AdMob CMP** is a Compose Multiplatform AdMob SDK for Android and iOS — banner,
interstitial, rewarded, rewarded interstitial, app-open and native ads behind one
Kotlin API, with UMP consent in the initialization flow, mediation, and
paid/revenue events.

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:<VERSION>")
```

📖 [Quickstart](https://ads.avinya.dev/start/quickstart/) ·
[Installation](https://ads.avinya.dev/start/installation/) ·
[iOS setup](https://ads.avinya.dev/start/ios-setup/) ·
[All docs](https://ads.avinya.dev)

---

### What's new

<Lead with the problem in the reader's words, then the fix. If there is a
diagnostic string users paste into a search engine — a linker error, a stack
trace, an exception message — quote it verbatim in a fenced block. That string
is the query this page should rank for.>

### <Feature or fix heading>

<Worked example in Kotlin. Show the smallest complete snippet that works, not a
fragment.>

Full guide: <https://ads.avinya.dev/... link the specific page>

### Upgrading from <PREVIOUS VERSION>

```kotlin
implementation("dev.avinya.ads:admob-cmp:<VERSION>")
```

<State whether any source change is required. If the public ABI is unchanged,
say so in bold — it is the single most useful sentence in the release for
someone deciding whether to upgrade today.>

### Compatibility

| admob-cmp | Kotlin | Compose Multiplatform | Android minSdk | iOS deployment target |
|---|---|---|---|---|
| <VERSION> | <KOTLIN> | <CMP> | <MINSDK> | <IOS> |

Full matrix: https://ads.avinya.dev/reference/compatibility/

### Notes

- The Gradle plugin (`dev.avinya.ads:admob-cmp-gradle-plugin`) is versioned in
  lockstep with the library. Always use matching versions.
- <Anything platform-specific that did or did not change.>

**Full changelog:** https://ads.avinya.dev/reference/changelog/

---

*Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
trademarks of Google LLC.*
````

- [ ] **Step 4: Hook the template into the release checklist**

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp/docs/PUBLISHING.md`, find the `## Release checklist` section (around line 94) and insert a new item immediately before the existing item 8 (`Run ./scripts/publish-maven-central.sh, inspect both staging deployments …`), renumbering the items after it:

```markdown
8. Draft the GitHub release body from
   [`.github/RELEASE_NOTES_TEMPLATE.md`](../../.github/RELEASE_NOTES_TEMPLATE.md).
   Name the release `vX.Y.Z — <what the user gains>` and keep "Compose
   Multiplatform" or "Kotlin Multiplatform" in the name. Before publishing, run
   `git tag --list` and confirm every tag you reference in a `compare/` link
   actually exists — a broken changelog link shipped in 1.1.0 exactly this way.
```

- [ ] **Step 5: Verify every link in both files resolves**

The template's whole value is its links, and a template that ships 404s is worse than none.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
grep -oh 'https://ads\.avinya\.dev[^ )]*' \
  docs/distribution/release-v1.1.0-body.md .github/RELEASE_NOTES_TEMPLATE.md \
  | sed 's/[.,]$//' | sort -u | while read -r u; do
    printf '%s -> ' "$u"
    curl -s -o /dev/null -w '%{http_code}\n' --max-time 20 -L "$u"
  done
```

Expected: every URL returns `200`.

If any returns `404`, the corresponding page has not been authored yet by Plan 3. **Do not publish the release edit** (Step 6) until they all resolve — replace the offending link with `https://ads.avinya.dev` or wait for Plan 3. If the whole host fails to resolve, Plan 2 has not landed; stop and finish Plan 2 first.

- [ ] **Step 6: HUMAN STEP — outward-facing publication. Update the live release**

Editing a GitHub release modifies public content. An agent must not run `gh release edit`, must not call the GitHub releases API, and must not open the release editor. Stop here and hand `docs/distribution/release-v1.1.0-body.md` to a human.

The human:

1. Re-runs Step 5 and confirms every docs link returns 200.
2. Opens https://github.com/Meet-Miyani/admob-compose-multiplatform/releases/tag/1.1.0 and clicks Edit.
3. Sets the release name to `v1.1.0 — Compose Multiplatform AdMob: zero-config Kotlin/Native test linking`.
4. Replaces the body with everything below the `---` marker in `release-v1.1.0-body.md`.
5. Previews it, confirms the tables render and no link is dead, then saves.
6. **Does not** re-publish or re-tag. `.github/workflows/publish.yml` fires on `release: [published]`; editing an already-published release does not re-trigger it, and re-publishing would create duplicate Central Portal staging deployments (see the warning in `PUBLISHING.md`).

- [ ] **Step 7: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs/distribution/release-v1.1.0-body.md .github/RELEASE_NOTES_TEMPLATE.md admob-cmp/docs/PUBLISHING.md
git commit -m "docs(release): rewrite the 1.1.0 notes for discoverability and add a template

The 1.1.0 body never said what the library is and its changelog link 404s -
it points at a compare between two tags, only one of which exists. The
template makes the frame (what it is, coordinates, docs links, trademark)
structural rather than something to remember."
```

---

### Task 6: Launch content calendar

Every step in this task that posts anything is a human step. Several of these communities have rules that materially change what can be posted, and two of them constrain this plan directly. Those constraints are honoured in the sequencing and the copy, not worked around.

**Files:**
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/launch-calendar.md`
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/kotlin-weekly.md`
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/devto-article.md`
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/medium.md`
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/kotlin-slack.md`
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/reddit-r-kotlin.md`

**Interfaces:**
- Consumes: `https://ads.avinya.dev` and its `/start/quickstart/`, `/formats/native/`, `/reference/troubleshooting/`, `/reference/compatibility/`, `/project/roadmap/` pages (Plans 2 and 3); the rewritten release body (Task 5).
- Produces: `docs/distribution/launch-calendar.md` — the sequencing and per-channel rules, referenced by Task 7's checkpoints.

- [ ] **Step 1: Write the calendar, with each community's researched rules**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/launch-calendar.md`:

```markdown
# Launch calendar

**Every posting step below is performed by a human.** No agent posts to a
community forum, Slack workspace, subreddit, blogging platform, or mailing list
on this project's behalf, under any circumstance. Agents write the copy into
`docs/distribution/posts/` and stop.

Day 0 is the day `https://ads.avinya.dev` is live, its sitemap is submitted to
Google Search Console (Plan 2), and the guide pages named in the copy exist
(Plan 3). Do not start before all three are true. Posting a link to a docs site
that 404s in places is the one mistake none of these communities forgive.

## Per-community rules — researched 2026-07-31

| Channel | Rule as found | What this plan does |
|---|---|---|
| Kotlin Slack, all channels | *"Please refrain from cross-posting the same message on multiple channels. It is considered spamming."* — https://kotlinlang.org/docs/slack-code-of-conduct.html | **Three different messages in three channels, staggered over three weeks. Never the same text twice.** |
| Kotlin Slack `#feed` | The announcements/links channel. Moderated by Nicola Corti (@gammax) and Youssef Shoaib. | The release announcement goes here, and only here. |
| Kotlin Slack `#multiplatform` | Moderated by Andrey Mischenko (@gildor). No published channel-specific rules; the CoC says moderators keep channel rules in the channel topic. | Read the topic first. Post a tooling-specific message about Kotlin/Native test linking — genuinely different content, not a copy. |
| Kotlin Slack `#compose` | Moderated by Maryam Alhuthayfi and Zach Klippenstein. | Read the topic first. Post a Compose-specific message about the native-ad layout DSL — again, different content. |
| Kotlin Slack, general | `@channel` and `@here` are disabled. Do not ping maintainers to get attention. Use code blocks, one message, no thread-splitting. | Copy is a single message with one fenced block. |
| r/Kotlin | **Rules could not be read programmatically — Reddit blocks the tooling used for this research.** They must be read by a human before posting. | Hard gate: read the rules, then post. Copy is written as a self-post so it survives the strictest plausible ruleset. |
| dev.to | The Code of Conduct contains no self-promotion prohibition. Cross-posting is supported first-class via the `canonical_url` front-matter field. | Full article, `canonical_url` pointing at the docs page. |
| Medium | Cross-posting your own content is allowed if you hold the rights. The Import tool backdates the post and sets the canonical link automatically. Spam rules forbid "repeatedly using responses or mentions as a method of promotion". | Import the dev.to article. Never promote via comments on other people's posts. |
| Kotlin Weekly | Submissions explicitly invited: `mailto:mailinglist@kotlinweekly.net` with subject `Link for submission - Kotlin Weekly`. Roughly 23,000 subscribers. Editorially curated, so there is no spam risk in submitting. | Submit on Day 1. Highest value-per-risk channel in this list. |
| "Compose Multiplatform community channels" | **There is no official Compose Multiplatform forum.** `JetBrains/compose-multiplatform` has GitHub Discussions **disabled** and `/discussions` returns 404. | The CMP community is Kotlin Slack `#compose`, `#compose-ios`, `#compose-desktop`. Covered by the Slack row. Do not invent a channel. |
| discuss.kotlinlang.org → **Libraries** | Category description scopes it to *"the Kotlin standard library and other **kotlinx** libraries"*. A third-party library announcement is off-topic there. | **Excluded.** Do not post in Libraries. |
| discuss.kotlinlang.org → **Multiplatform** | A general discussion category (359 topics), not an announcement board. | Optional, low priority, after Day 30 and only if there is a question to answer rather than a thing to announce. |

## Sequencing, and why it is this order

The ordering is not arbitrary. It moves from channels this project controls, to
editorially-mediated channels, to community channels where a post is judged by
strangers — so that by the time a stranger clicks through, there is a docs site,
a real article, and a release page waiting rather than a bare repo.

| Day | Channel | Action | Who |
|---|---|---|---|
| 0 | Owned | The rewritten 1.1.0 release body is live (Task 5) | Human |
| 0 | Owned | `avinya.dev/open-source/` featured card is deployed (Task 4) | Human |
| 1 | Kotlin Weekly | Submit the docs-site link by email | Human |
| 2 | dev.to | Publish the article with `canonical_url` set | Human |
| 4 | Medium | Import the dev.to article (auto-canonical) | Human |
| 5 | Kotlin Slack `#feed` | Single release announcement | Human |
| 7 | r/Kotlin | Self-post — **only after reading the subreddit rules** | Human |
| 14 | Kotlin Slack `#multiplatform` | Kotlin/Native test-linking message (different content) | Human |
| 21 | Kotlin Slack `#compose` | Native-ad layout DSL message (different content) | Human |
| 30 | — | 30-day metrics checkpoint (Task 7) | Human |
| 90 | — | 90-day metrics checkpoint + `kmp-awesome` gate re-check (Tasks 2, 7) | Human |

Medium trails dev.to by two days on purpose: dev.to is the canonical, and giving
Google a couple of days to crawl the canonical before the duplicate appears is
the cheapest possible insurance on a brand-new host.

r/Kotlin trails the article by five days on purpose: a Reddit post that links to
a written explanation reads as sharing something, and a post that links to a bare
repo reads as advertising. The former survives moderation; the latter is what
gets removed.

## Rules that apply everywhere

- Every post carries the trademark line: *Not affiliated with or endorsed by
  Google. AdMob and Google Mobile Ads are trademarks of Google LLC.*
- Never name a competing library in a post. Spec §6 makes comparison content a
  neutral capability matrix on our own site, not a talking point in someone
  else's community.
- Never claim ranking, download, or adoption numbers. There are none yet.
- Disclose authorship in the first or second sentence, everywhere. Every channel
  below is fine with an author sharing their own work and hostile to an author
  pretending not to be one.
- If a moderator asks for a change, make it and do not argue.
```

- [ ] **Step 2: Write the Kotlin Weekly submission**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/kotlin-weekly.md`:

```markdown
# Kotlin Weekly — Day 1

**HUMAN STEP — outward-facing publication.** An agent must not send this email.

Submission mechanism, from https://kotlinweekly.net/ — the site's own
`mailto:` link:

    To:      mailinglist@kotlinweekly.net
    Subject: Link for submission - Kotlin Weekly

Kotlin Weekly is editorially curated and explicitly invites link submissions, so
there is no self-promotion risk here. It is the single highest value-per-risk
channel in the calendar. The editors decide whether to run it; that is the whole
moderation model.

There is a separate `Sponsoring for Kotlin Weekly` subject for paid placement.
**Do not use it** — spec §2 rules out paid acquisition of any kind.

## Body — send verbatim

Hi,

I'd like to submit a link for a future issue.

**admob-cmp — a Compose Multiplatform AdMob SDK for Android and iOS**
https://ads.avinya.dev

I'm the author. It covers banner, interstitial, rewarded, rewarded interstitial,
app-open and native ads behind one Kotlin API, with UMP consent in the
initialization flow, mediation, and paid/revenue events. Published on Maven
Central as `dev.avinya.ads:admob-cmp` (Kotlin 2.3.20, Compose Multiplatform
1.11.1, Android minSdk 26, iOS 15+).

The piece your readers may find most useful is the iOS one: Kotlin/Native test
executables link without Xcode, so they can't see the GoogleMobileAds Swift
package, and `:module:iosSimulatorArm64Test` fails with
`Undefined symbols: _OBJC_CLASS_$_GADBannerView`. 1.1.0 ships a Gradle plugin
that fixes it in one line —
https://ads.avinya.dev/reference/troubleshooting/

Source: https://github.com/Meet-Miyani/admob-compose-multiplatform

Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
trademarks of Google LLC.

Thanks either way,
Meet Miyani
```

- [ ] **Step 3: Write the dev.to article**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/devto-article.md`:

````markdown
# dev.to — Day 2

**HUMAN STEP — outward-facing publication.** An agent must not publish this.

dev.to's Code of Conduct contains no self-promotion prohibition, and dev.to
supports cross-posting first-class through the `canonical_url` front-matter
field. Setting it is not optional here: without it, dev.to's much stronger
domain outranks `ads.avinya.dev` for our own content, which is the exact
opposite of what this program is for.

Publish via **Create Post → the three-dot menu → "Switch to Markdown editor"**,
then paste the whole thing including the front matter.

The title is the literal linker error, because that is the string people paste
into a search engine. Spec §7 marks
`admob ios kotlin multiplatform undefined symbols GAD` as easy difficulty, high
opportunity, "pure pain" — and uncontested.

## Copy — publish verbatim

```markdown
---
title: "Undefined symbols: _OBJC_CLASS_$_GADBannerView — fixing AdMob linking in Kotlin Multiplatform iOS tests"
published: true
description: Why Kotlin/Native test executables can't see the GoogleMobileAds Swift package, and the one-line fix.
tags: kotlin, ios, android, testing
canonical_url: https://ads.avinya.dev/reference/troubleshooting/
---

If you've added AdMob to a Kotlin Multiplatform project and then run your iOS
tests, you've probably seen this:

```
Undefined symbols for architecture arm64:
  "_OBJC_CLASS_$_GADBannerView", referenced from: ...
ld: symbol(s) not found for architecture arm64
```

Your app builds. Your Android tests pass. Only `iosSimulatorArm64Test` fails,
and the error names a class you never wrote.

I maintain [admob-cmp](https://github.com/Meet-Miyani/admob-compose-multiplatform),
a Compose Multiplatform AdMob SDK, and this was the single most confusing thing
about integrating it. Here's what's actually happening and how to fix it.

## Why it happens

Google ships the iOS Mobile Ads SDK as a Swift package. When you build your app,
Xcode resolves that package and hands the binaries to the linker. Everything
works.

A Kotlin/Native **test** executable is built by Gradle, not Xcode. There is no
Xcode build, therefore no SPM resolution, therefore no `GoogleMobileAds`
binaries on the link line. The cinterop bindings still declare
`GADBannerView`, so the compile succeeds and the link fails.

This is not specific to any one library. Any Kotlin Multiplatform project with
cinterop bindings against an SPM-distributed framework hits it. It's a structural
gap between how Kotlin/Native tests link and how SPM distributes binaries.

## The manual fix, and why you don't want it

You can fix it yourself. You need to:

1. Download the `GoogleMobileAds` and `UserMessagingPlatform` XCFrameworks at
   exactly the versions the bindings were generated against
2. Verify their checksums, because you are downloading binaries into a build
3. Unpack them somewhere stable and cache them
4. Add `-framework` and `-F` linker options — to the **test** binaries only,
   because your app framework must keep resolving through SPM
5. Keep all of the above in sync with the library, by hand, forever

That's 60-odd lines of `build.gradle.kts` that has nothing to do with your app,
and it silently rots the moment the library bumps its bindings.

## The one-line fix

As of 1.1.0 this is a Gradle plugin:

```kotlin
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}
```

It needs `mavenCentral()` in your plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

That's the whole change. The plugin:

- downloads the XCFrameworks at the versions the bindings were generated from —
  the versions and checksums are generated from the library build, so they
  can't drift out of sync
- verifies the SHA-256 of every archive **before** extracting a byte
- applies linker options to **test binaries only** — your shipped app framework
  is untouched and still resolves GoogleMobileAds through SPM
- adds `./gradlew doctorIos`, a report-only check of your SPM products,
  `Info.plist`, and framework cache

If you previously hand-rolled the workaround, delete it.

## Checking your setup

```bash
./gradlew doctorIos
```

It reports on your SPM products, your `Info.plist` entries (`GADApplicationIdentifier`,
`NSUserTrackingUsageDescription`), and the framework cache. It changes nothing —
it only tells you what's wrong.

Then:

```bash
./gradlew :yourModule:iosSimulatorArm64Test
```

## While you're here

`admob-cmp` is a Compose Multiplatform AdMob SDK for Android and iOS. One Kotlin
API for banner, interstitial, rewarded, rewarded interstitial, app-open and
native ads, with UMP consent in the initialization flow, mediation, and
paid/revenue events. Suspend functions and `StateFlow` instead of listeners.

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:1.1.0")
```

| admob-cmp | Kotlin | Compose Multiplatform | Android minSdk | iOS deployment target |
|---|---|---|---|---|
| 1.1.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |

Docs: <https://ads.avinya.dev> · Source:
<https://github.com/Meet-Miyani/admob-compose-multiplatform>

One thing worth flagging if you're doing this on iOS: the order is UMP consent,
then ATT, then SDK initialization. Requesting ATT before consent permanently
forfeits the IDFA for those requests, and it's not recoverable in that session.
There's a [diagram of the sequence in the consent docs](https://ads.avinya.dev/privacy/app-tracking-transparency/).

*Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are
trademarks of Google LLC.*
```

## Before publishing

- [ ] `canonical_url` is `https://ads.avinya.dev/reference/troubleshooting/` and
      that page returns 200.
- [ ] Every link in the article returns 200.
- [ ] Tags are four or fewer — dev.to's limit.
- [ ] The authorship disclosure ("I maintain…") is in the third paragraph.
- [ ] The trademark line is present.
````

- [ ] **Step 4: Write the Medium instructions**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/medium.md`:

```markdown
# Medium — Day 4

**HUMAN STEP — outward-facing publication.** An agent must not publish this.

Medium's rules permit republishing your own content: if you have your own blog
where you publish your content, you may republish it on Medium as long as you
hold the rights. What Medium's spam rules forbid is "repeatedly using responses
or mentions as a method of promotion" — so never comment on other people's posts
to advertise this.

## Use the Import tool, not copy-paste

    https://medium.com/p/import

Paste the **dev.to** URL from Day 2. The import tool backdates the post to the
original date and sets the canonical link automatically, so Medium's domain
authority does not outrank the source. Copy-pasting the text instead produces
an uncanonicalised duplicate — the exact outcome this whole program is trying
to avoid.

Two days after dev.to is deliberate: it gives Google time to crawl the canonical
before the duplicate exists.

## After importing

- [ ] Open the imported story's settings and confirm the canonical link points at
      the dev.to URL. If the import did not set it, set it manually. **If it
      cannot be set, unpublish the story** — an uncanonicalised duplicate is
      worse than no Medium presence at all.
- [ ] Tags (Medium allows five): `Kotlin`, `Kotlin Multiplatform`,
      `Compose Multiplatform`, `iOS`, `Android`.
- [ ] Confirm the fenced code blocks survived the import. Medium's importer
      mangles nested backticks; re-check the Gradle snippets specifically.
- [ ] Confirm the trademark line is present at the end.
- [ ] Do not submit to a Medium publication that requires exclusivity — the
      canonical must stay on dev.to.

The author already has a Medium presence at https://meet-miyani.medium.com,
which the studio site's article feed reads. This post appearing there is a
secondary benefit and is not a reason to skip the canonical.
```

- [ ] **Step 5: Write the three Kotlin Slack messages**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/kotlin-slack.md`:

````markdown
# Kotlin Slack — Days 5, 14, 21

**HUMAN STEP — outward-facing publication.** An agent must not post to Slack.

Join at https://surveys.jetbrains.com/s3/kotlin-slack-sign-up if not already a
member.

## The rule that shapes this file

From https://kotlinlang.org/docs/slack-code-of-conduct.html:

> Please refrain from cross-posting the same message on multiple channels. It is
> considered spamming.

This is why there are **three different messages** below rather than one message
posted three times. Do not paste any of them into a second channel. If a channel
is not covered here, it does not get a message.

Also binding, from the same page:

- `@channel` and `@here` are disabled. Do not attempt to work around it.
- "Please do not ping or mention someone directly to get your questions answered,
  especially project owners" — do not @ moderators or JetBrains staff.
- "Don't split messages into multiple ones. Ask it all in a single message."
- Use fenced code blocks, not plain-text code.
- The Slack is not an official support channel and not a bug tracker.

**Before each post: read the channel's topic.** The CoC makes moderators
responsible for keeping channel-specific rules visible, and the topic is where
they live. If a topic forbids project announcements, do not post — note it in
this file and move on. That is a legitimate outcome, not a failure.

---

## Day 5 — `#feed`

`#feed` is the channel for links and project announcements; it is moderated by
Nicola Corti (@gammax) and Youssef Shoaib. This is the only channel that gets a
general announcement.

Post as a single message:

```
Released admob-cmp 1.1.0 — a Compose Multiplatform AdMob SDK for Android and iOS. I'm the author.

One Kotlin API for banner, interstitial, rewarded, rewarded interstitial, app-open and native ads, with UMP consent in the initialization flow, mediation and paid/revenue events. Suspend functions and StateFlow rather than listener callbacks.

implementation("dev.avinya.ads:admob-cmp:1.1.0")

Kotlin 2.3.20 · Compose Multiplatform 1.11.1 · Android minSdk 26 · iOS 15+

Docs: https://ads.avinya.dev
Source: https://github.com/Meet-Miyani/admob-compose-multiplatform

Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

Put the `implementation(...)` line in a code block using the composer's code
formatting.

---

## Day 14 — `#multiplatform`

Moderated by Andrey Mischenko (@gildor). **Different content, not a reworded
announcement** — this message is about a Kotlin/Native tooling problem that
affects any project with cinterop bindings against an SPM-distributed framework,
which is squarely this channel's topic.

Post as a single message:

```
Sharing a Kotlin/Native gotcha and how I ended up solving it, in case it's useful to anyone binding an SPM-distributed framework.

If your cinterop bindings target a framework that ships as a Swift package, `:module:iosSimulatorArm64Test` fails to link:

  Undefined symbols for architecture arm64:
    "_OBJC_CLASS_$_GADBannerView", referenced from: ...

The app builds fine — Xcode resolves the SPM package and hands the binaries to the linker. A Kotlin/Native test executable is built by Gradle with no Xcode in the loop, so SPM never resolves and the binaries are never on the link line.

The fix that stuck was moving the XCFramework download, checksum verification and linker options into a Gradle plugin that applies them to test binaries only, leaving the app framework resolving through SPM as before. I ship it for my own library (I maintain admob-cmp) but the shape generalises to any SPM-bound cinterop.

Write-up: https://ads.avinya.dev/reference/troubleshooting/
```

---

## Day 21 — `#compose`

Moderated by Maryam Alhuthayfi and Zach Klippenstein. **Different content again**
— a Compose API design question, which is what this channel is for.

Post as a single message:

```
A Compose Multiplatform API design question I'd welcome opinions on.

Native ads are awkward in Compose: the platform SDKs need a real view hierarchy with each asset registered to the ad object, so you can't just lay out Composables and hope. I ended up with a small declarative layout DSL that describes the arrangement and handles asset registration underneath, plus a pool so a scrolling feed isn't loading one ad per row.

Docs, with the pool lifecycle diagram: https://ads.avinya.dev/formats/native/

I maintain admob-cmp, so this is my own library — but the interesting bit isn't the ads, it's the general problem of a declarative wrapper over a platform API that demands imperative view registration. If anyone has solved that shape more cleanly elsewhere I'd genuinely like to see it.
```

---

## Channels this plan deliberately does not post to

| Channel | Why |
|---|---|
| `#android` | A fourth message would be pushing the cross-posting rule past its intent. |
| `#compose-ios`, `#compose-desktop`, `#compose-web` | Covered by `#compose`. Posting to all four is exactly the cross-posting the rule prohibits. |
| `#announcements` | JetBrains announcements only. |
| `#meta`, `#reports` | Moderation channels. |
| `#library-development` | About building libraries in general, not announcing them. Fine to participate in; not a launch channel. |
````

- [ ] **Step 6: Write the r/Kotlin post, gated on a human reading the rules**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/posts/reddit-r-kotlin.md`:

````markdown
# r/Kotlin — Day 7

**HUMAN STEP — outward-facing publication.** An agent must not post to Reddit.

## MANDATORY GATE: read the rules first

**r/Kotlin's rules could not be verified during planning.** Reddit blocks the
tooling used for this research — `www.reddit.com` is not accessible to the
agent's user agent, `old.reddit.com` and the `.json` endpoints return a bot
challenge instead of content, and Reddit is excluded from the available web
search. So this file makes **no claim** about what r/Kotlin permits.

Before posting, a human must open https://www.reddit.com/r/Kotlin/about/rules/
in a normal browser and read every rule.

Then, in this file, record:

    Rules read on: <DATE>
    Self-promotion rule: <QUOTE IT VERBATIM, or "none">
    Required flair: <NAME, or "none">
    Link posts allowed: <YES/NO>
    Verdict: <POST AS WRITTEN / POST WITH CHANGES: … / DO NOT POST>

If the rules forbid self-promotion, or restrict it to a scheduled thread, or
require a ratio of participation to promotion — **follow the rule**. Post in the
designated thread, or wait, or do not post. A removed post plus a moderator note
is worse for this project than silence, and it is permanent.

Fill the block above before posting. Do not post with it blank.

## Why this is a self-post and not a link post

Self-posts survive stricter rulesets than link posts almost everywhere on Reddit,
because the value is in the post body rather than in the click. The copy below is
written to be worth reading even if nobody follows a link, with authorship
disclosed in the first sentence and links at the end.

It also runs five days after the dev.to article on purpose: linking to a written
explanation reads as sharing, linking to a bare repo reads as advertising.

## Title

```
I built a Compose Multiplatform AdMob SDK covering all six ad formats — including native and app-open
```

## Body — post verbatim, as a text post

```
I maintain [admob-cmp](https://github.com/Meet-Miyani/admob-compose-multiplatform), a Compose Multiplatform wrapper over the Google Mobile Ads SDKs, and 1.1.0 is out. Sharing it here because the two things that took the longest are both things I'd have wanted to read about before starting.

**What it is:** one Kotlin API for banner, interstitial, rewarded, rewarded interstitial, app-open and native ads on Android and iOS. UMP consent is in the initialization flow rather than bolted on. Mediation and paid/revenue events are supported. The API keeps AdMob's vocabulary — `AdValue`, `ResponseInfo`, adaptive banner sizes, native asset names — but replaces the listener surface with suspend functions, `StateFlow` state, and one sealed `AdEvent` stream.

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:1.1.0")
```

**The first hard part — Kotlin/Native test linking.** Google ships the iOS SDK as a Swift package. Your app builds fine because Xcode resolves it. A Kotlin/Native test executable is built by Gradle with no Xcode in the loop, so SPM never resolves and `:module:iosSimulatorArm64Test` dies on `Undefined symbols: _OBJC_CLASS_$_GADBannerView`. The fix is a Gradle plugin that downloads and checksum-verifies the XCFrameworks and applies linker options to test binaries only, leaving the app framework resolving through SPM. One line in your `plugins {}` block. This generalises past ads — any cinterop binding against an SPM-distributed framework has the same problem.

**The second — native ads in Compose.** The platform SDKs want a real view hierarchy with each asset registered against the ad object, which doesn't map onto Composables at all. I ended up with a small layout DSL plus a pool, so a scrolling feed isn't firing one ad load per row.

**The correctness trap that cost me the most time:** on iOS the order is UMP consent → ATT → SDK initialization. Request ATT before consent and you permanently forfeit the IDFA for those requests. Nothing warns you; the ads just earn less. There's a sequence diagram for it in the docs.

Compatibility: Kotlin 2.3.20, Compose Multiplatform 1.11.1, Android minSdk 26, iOS 15+. Apache 2.0. Consumable from KMP/Gradle projects — a pure-Swift app would need a KMP shim.

Docs: https://ads.avinya.dev
Source: https://github.com/Meet-Miyani/admob-compose-multiplatform

Happy to answer anything about the iOS side in particular — it's where all the sharp edges are.

*Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.*
```

## Before posting

- [ ] The gate block above is filled in, with a real date.
- [ ] Required flair applied, if the subreddit has one.
- [ ] Both links return 200.
- [ ] Reddit's own site-wide self-promotion guidance is respected: participate in
      the community, don't only ever post your own work.

## After posting

- [ ] Answer every reply within 24 hours. An unanswered thread is a worse
      outcome than no thread.
- [ ] Never argue with a moderator. If it's removed, accept it and record why
      here so it isn't repeated.
- [ ] **Do not cross-post to r/androiddev, r/iOSProgramming, or
      r/KotlinMultiplatform.** Cross-posting the same content across programming
      subreddits is the most reliable way to get flagged as a spammer across all
      of them at once.
````

- [ ] **Step 7: Verify every URL used in the copy resolves**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
grep -roh 'https://[a-zA-Z0-9./_#?=-]*' docs/distribution/posts/ docs/distribution/launch-calendar.md \
  | sed 's/[.,)]*$//' | sort -u | while read -r u; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 -L "$u")
    [ "$code" = "200" ] || echo "NOT 200 ($code): $u"
  done
echo "--- url check done ---"
```

Expected: only `--- url check done ---`, with no `NOT 200` lines.

Known acceptable exceptions, which must be checked by hand rather than ignored wholesale: `https://www.reddit.com/r/Kotlin/about/rules/` (Reddit blocks this tooling — a human confirms it in a browser) and `https://medium.com/p/import` (requires a signed-in session). Any `ads.avinya.dev` URL failing means Plan 3 has not authored that page — fix the link or wait, and do not post.

- [ ] **Step 8: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add docs/distribution/launch-calendar.md docs/distribution/posts/
git commit -m "docs(distribution): launch calendar and verbatim post copy

Every posting step is human-performed. Copy respects each community's rules as
researched: Kotlin Slack forbids cross-posting the same message, so the three
channels get three different messages two weeks apart; r/Kotlin's rules are
unreadable from tooling and are gated on a human reading them; the kotlinlang
forum's Libraries category is scoped to kotlinx and is excluded."
```

---

### Task 7: Metrics instrumentation and review checkpoints

Spec §12 defines nine metrics with a 2026-07-31 baseline and 30/90-day targets. Four can be collected by script; five cannot, because Google Search Console has no free unauthenticated API, Google blocks scripted SERP queries, and there is no free referring-domains API. This task automates what is automatable and gives the rest an exact manual procedure, so a checkpoint is a 15-minute task rather than an improvisation.

**Files:**
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/collect-launch-metrics.sh`
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/launch-metrics.md`

**Interfaces:**
- Consumes: `scripts/distribution/verify-klibs-listing.sh` (Task 1, invoked for its exit code); `docs/distribution/kmp-awesome-entry.md` (Task 2); the Search Console property provisioned by Plan 2.
- Produces: `scripts/distribution/collect-launch-metrics.sh`, printing a fixed-format block that is pasted into `docs/distribution/launch-metrics.md` at each checkpoint.

- [ ] **Step 1: Write the collection script**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/collect-launch-metrics.sh`:

```bash
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
```

- [ ] **Step 2: Make it executable and run it**

```bash
chmod +x /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/collect-launch-metrics.sh
/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/scripts/distribution/collect-launch-metrics.sh
```

Expected at baseline: a `# Metrics snapshot` header; stars `0`; topics either `0 -> (none)` or the twelve from Plan 1; klibs `NOT INDEXED`; kmp-awesome `not present`; Maven Central `1.1.0`; the manual TODO block. Docs-site rows depend on Plan 2 — `000` or a non-200 code before it lands is expected and correct.

Every line must print something. A blank value means a parse bug — fix it now, not at the checkpoint when the baseline is gone.

- [ ] **Step 3: Write the metrics record with the baseline and both checkpoints**

Write `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/docs/distribution/launch-metrics.md`:

```markdown
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
```

- [ ] **Step 4: Commit**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add scripts/distribution/collect-launch-metrics.sh docs/distribution/launch-metrics.md
git commit -m "docs(distribution): instrument the spec §12 success metrics

Four rows are scriptable (stars, topics, klibs listing, kmp-awesome entry);
Search Console, SERP positions and referring domains have no free API and are
printed as an explicit manual TODO rather than dropped. 30-day and 90-day
checkpoints carry their own action lists."
```

- [ ] **Step 5: Schedule the two checkpoints**

**HUMAN STEP.** Create two calendar reminders, because a checkpoint with no owner and no date does not happen:

- **+30 days from the day Plan 6 lands** — "admob-cmp launch: 30-day metrics checkpoint". Body: run `scripts/distribution/collect-launch-metrics.sh`, fill the manual rows in `docs/distribution/launch-metrics.md`, work the 30-day action list.
- **+90 days** — "admob-cmp launch: 90-day metrics checkpoint + kmp-awesome gate". Body: same, plus re-check the `kmp-awesome` star and third-party-usage gate in `docs/distribution/kmp-awesome-entry.md`.

---

## Self-review

Run after all seven tasks are complete.

- [ ] **Spec coverage.** §10 Plan 6 names five deliverables plus the §12 metrics: klibs.io verification → Task 1; `kmp-awesome` PR → Task 2; Maven Central POM metadata → Task 3; `avinya.dev/open-source/` → Task 4; launch content calendar → Task 6; §12 metrics → Task 7. GitHub release notes (from the task brief, beyond §10's summary) → Task 5. §3's trademark line appears in Tasks 2, 3, 5, 6. §6's neutral-comparison rule is a Global Constraint and is enforced in Task 6's "rules that apply everywhere". §7's keywords drive the Task 3 POM check, the Task 5 release name, and the Task 6 dev.to title. §9's note that `gradle.properties` hardcodes the four POM URL properties is covered by Task 3 — **and extended**, because the properties live in two files, not one.
- [ ] **No agent publishes outward.** Confirm every one of these is marked `HUMAN STEP`: Task 1 Step 5 (JetBrains issue), Task 2 Step 4 (third-party PR), Task 4 Step 12 (site deploy), Task 5 Step 6 (release edit), Task 6 Steps 2–6 (all five channels), Task 7 Step 5 (calendar). Confirm no step anywhere instructs an agent to run `gh release edit`, `gh pr create` against a third-party repo, or any posting API.
- [ ] **Placeholder scan.** The only angle-bracket fields that survive are ones that *cannot* be known at planning time and whose absence blocks the step: the `kmp-awesome` PR's third-party usage link and star count (Task 2), the r/Kotlin rules block (Task 6), and the checkpoint dates and pasted outputs (Task 7). Each is explicitly labelled as blocking. Every other step contains complete, literal content.
- [ ] **Type consistency.** Task 4 defines `FeaturedRepo { docsUrl, tagline }`, `Repo` with `featured`/`docsUrl`/`tagline`, `mapRepos(raw, featured?)` and `fetchRepos(username, token?, fetcher?, featured?)`. The tests in Step 2, the implementation in Step 4, the config in Step 6 and the template in Step 7 all use exactly these names. `FEATURED_REPOS` is the config export name in Steps 6 and 7 alike.
- [ ] **Script contracts.** `verify-klibs-listing.sh` exits 0/1/2 and is consumed by `collect-launch-metrics.sh` on exactly those codes. `verify-pom-metadata.sh` exits 0/1 and is consumed by `release-readiness.yml`. Both honour `REPO_OWNER`/`REPO_SLUG`.
- [ ] **Every URL in every committed file returns 200**, except the two documented exceptions in Task 6 Step 7.
