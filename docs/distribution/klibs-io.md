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
