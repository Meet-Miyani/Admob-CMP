# Repository Identity and GitHub SEO Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the repository to `admob-compose-multiplatform`, replace the JetBrains-template root README with a keyword-bearing product README, and fix every piece of GitHub and POM metadata that currently makes the library unfindable — plus the cross-repo `avinya.dev` canonical defect that blocks Plan 2.

**Architecture:** This is Plan 1 of the seven-plan program in `docs/superpowers/specs/2026-07-31-public-visibility-design.md` §10. It is almost entirely metadata and prose: a GitHub-side rename plus settings changes (done through `gh` and, for the social preview only, the web UI), and a small set of local file edits (root `README.md`, root `LICENSE`, five `gradle.properties` files, two cinterop `.def` files). One task (Task 8) is in a **different repository** whose path is unknown and must be located first. No production Kotlin source is touched and the public ABI is untouched.

**Tech Stack:** GitHub REST API via `gh` CLI 2.92.0, GitHub web UI, Markdown, Java `.properties` files, Kotlin/Native cinterop `.def` files, headless Google Chrome (PNG rasterisation), ImageMagick `magick` (image verification), Astro (Task 8, cross-repo), Cloudflare Pages Functions (Task 8, cross-repo).

---

## Global Constraints

Every task's requirements implicitly include this section. Values are copied verbatim from the spec and from files verified in the repo on 2026-07-31.

- **Repo rename is `Admob-CMP` → `admob-compose-multiplatform`.** New canonical URL: `https://github.com/Meet-Miyani/admob-compose-multiplatform`.
- **The old repo name `Admob-CMP` must NEVER be reused for another repository.** Reusing it silently breaks every GitHub 301 redirect from the old URL. This prohibition is permanent (spec §9, §11).
- **GitHub does NOT redirect Actions references after a rename.** Any `uses: Meet-Miyani/Admob-CMP/...` reference would break. Task 1 audits for this before the rename.
- **The Maven coordinate does NOT change.** It stays `dev.avinya.ads:admob-cmp`, current version `1.1.0`. Renaming it is an explicit non-goal (spec §2). The same holds for `dev.avinya.ads:admob-cmp-core`, `dev.avinya.ads:admob-cmp-compose`, `dev.avinya.ads:admob-cmp-gradle-plugin`, and the `dev.avinya.ads.admob-cmp` Gradle plugin marker.
- **The brand name stays "AdMob CMP."** The README must reconcile brand, repo slug, and Maven coordinate in one line (spec §3).
- **Docs host is `https://ads.avinya.dev`.** Never `admob.avinya.dev` — a host whose entire label is a Google mark reads as an official Google property (spec §3, trademark posture).
- **This exact trademark line is required on every public surface** (README, site footer, Maven POM description), verbatim:
  > Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
- **`https://ads.avinya.dev` does not exist yet.** It is provisioned by Plan 2. The README written in Task 5 links to it because the spec requires it; those links resolve only after Plan 2 ships. This is a known, accepted forward reference — do not "fix" it by removing the links.
- **Verified library facts, to be used verbatim in the README compatibility matrix:** Kotlin 2.3.20 · Compose Multiplatform 1.11.1 · Android `minSdk` 26 · iOS deployment target 15.0 · GMA Next-Gen (Android) 1.3.0 · GMA iOS 13.7.0 · UMP Android 4.0.0 · UMP iOS 3.1.0. (Sources: `gradle/libs.versions.toml`, `admob-cmp/README.md`.)
- **Verified Maven Central badge URL** (renders `v1.1.0` as of 2026-07-31): `https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp`
- **Exactly these twelve GitHub topics, no more and no fewer:**
  `kotlin-multiplatform` `compose-multiplatform` `admob` `kmp` `kotlin` `ads` `monetization` `native-ads` `android` `ios` `ump` `gdpr`
- **`LICENSE` is Apache License 2.0** and currently lives at `admob-cmp/LICENSE`, **not** at the repo root. This is exactly why the GitHub API reports `license: null`.
- **Do not invent API.** Every Kotlin symbol used in the README was verified against `admob-cmp-core/api/admob-cmp-core.klib.api`, `admob-cmp-compose/api/admob-cmp-compose.klib.api`, and `admob-cmp/AGENTS.md`. Do not add symbols that are not in this plan without re-verifying them the same way.
- **The public ABI is frozen** (`admob-cmp/CLAUDE.md` invariant 12). Nothing in this plan may change it. Task 3 touches two `.def` files and therefore ends with a `checkKotlinAbi` gate.
- **`gh` is installed (2.92.0) but NOT authenticated.** `gh auth login` is a prerequisite and is interactive — a human must run it (Task 1, Step 1).
- **Work happens on the branch `docs/public-visibility-plan`**, which is already checked out. Do not switch branches.

### Step markers used throughout

| Marker | Meaning |
|---|---|
| **[LOCAL]** | Local file edit or local shell command. An agent can do this. |
| **[gh]** | Runs through the `gh` CLI. An agent can do this once Task 1 Step 1 is done. |
| **[HUMAN — interactive]** | Requires a human at a terminal (browser-based OAuth, credential entry). |
| **[HUMAN — web UI]** | Requires a human in a browser. No API exists for this. |

---

## File Structure

Files this plan creates or modifies, and what each is responsible for.

| Path | Action | Responsibility |
|---|---|---|
| `README.md` | Replace entirely | The repo's single highest-value SEO and conversion surface. Keyword-bearing H1, badges, format table, quickstart, compatibility, trademark. |
| `LICENSE` | Create (copy of `admob-cmp/LICENSE`) | Makes GitHub detect and render `Apache-2.0` instead of reporting `license: null`. |
| `admob-cmp/LICENSE` | Leave in place | Keeps the module directory self-contained. Copy, do not move. |
| `gradle.properties` | Modify 5 lines | Root POM fallback metadata: 4 URL properties + trademark in the description. |
| `admob-cmp/gradle.properties` | Modify 1 line | Facade artifact POM description + trademark. |
| `admob-cmp-core/gradle.properties` | Modify 1 line | Core artifact POM description + trademark. |
| `admob-cmp-compose/gradle.properties` | Modify 1 line | Compose artifact POM description + trademark. |
| `admob-cmp-gradle-plugin/gradle.properties` | Modify 5 lines | Plugin artifact POM: 4 URL properties + trademark in the description. Its own included build; it does NOT inherit the root file. |
| `admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def` | Modify 1 URL | `userSetupHint` is printed to consumers on link failure. Must not carry a stale slug. |
| `admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def` | Modify 1 URL | Same. |
| `.github/social-preview.html` | Create | Reproducible source for the 1280x640 social card. Checked in so the card can be regenerated. |
| `.github/social-preview.png` | Create | The 1280x640 PNG a human uploads through the GitHub web UI. |
| `.github/workflows/publish.yml` | **No change** | Audited in Task 1: contains no repo-slug reference. |
| `.github/workflows/release-readiness.yml` | **No change** | Audited in Task 1: contains no repo-slug reference. |
| `handoff.md` | **No change** | Historical session record. Its old-slug hits are a record of a past diff. |
| `docs/superpowers/plans/2026-07-29-track1-*.md`, `2026-07-29-track2-*.md` | **No change** | Archived, executed plans. Historical records. |
| `docs/superpowers/specs/2026-07-31-public-visibility-design.md` | **No change** | Deliberately records the old name as the "before" state. |
| `<studio-repo>/astro.config.mjs` | Modify (Task 8) | **Different repository.** Root cause of the canonical defect. |
| `<studio-repo>/functions/_middleware.ts` | Create (Task 8) | **Different repository.** 301s the `pages.dev` host to `avinya.dev`. |

### Task ordering and why

```
Task 1 (audit + gh auth)
   └─> Task 2 (rename on GitHub, repoint local remote)
          ├─> Task 3 (repoint in-repo URLs, add trademark to POMs)
          ├─> Task 4 (LICENSE at root)
          ├─> Task 5 (root README)
          ├─> Task 6 (topics, homepage, Discussions, Wiki)
          └─> Task 7 (social preview image)

Task 8 (cross-repo canonical fix) — independent of Tasks 1-7, but BLOCKS Plan 2.
```

Task 2 comes before Tasks 3-7 because those tasks write the **new** URL into files and settings. Doing them first would leave the repo pointing at a URL that does not resolve yet.

---

### Task 1: Pre-rename audit and `gh` authentication

Establish that the rename is safe before performing it, and get the CLI authenticated. The audit below was performed on 2026-07-31; this task re-runs it so the executor confirms nothing has drifted.

**Files:**
- Read only: `.github/workflows/publish.yml`, `.github/workflows/release-readiness.yml`
- Read only: every file matching `Admob-CMP` (enumerated below)
- Create/Modify: none

**Interfaces:**
- Consumes: nothing.
- Produces: an authenticated `gh` session with `repo` scope, and a confirmed audit result that later tasks rely on — specifically, the fact that **neither workflow references the repo slug**, so the rename cannot break Actions.

#### Audit result as of 2026-07-31 (what Step 2 must reproduce)

`.github/` contains exactly one subdirectory, `workflows/`, holding two files. Neither contains the string `Admob-CMP`, `Meet-Miyani`, or any `github.com/` URL. Every `uses:` reference is first-party (`actions/checkout@v6`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`). There are no issue templates, no `FUNDING.yml`, no `CODEOWNERS`, no `dependabot.yml`. **Conclusion: the rename breaks no workflow.**

`grep -rn "Admob-CMP"` (excluding `.git`, `build/`, `.gradle*`, `.codegraph`, `.kotlin`, `.idea`) produced exactly **19** hits across **8** files. Ten of them are live files that must change; nine are historical documents that must not.

**Live files — these are the rename's real blast radius (10 hits, 4 files):**

| File | Lines | Disposition |
|---|---|---|
| `gradle.properties` | 27, 33, 34, 35 | **Fix in Task 3** — `POM_URL`, `POM_SCM_URL`, `POM_SCM_CONNECTION`, `POM_SCM_DEV_CONNECTION` |
| `admob-cmp-gradle-plugin/gradle.properties` | 6, 12, 13, 14 | **Fix in Task 3** — same four properties. This is a separate included build with its own copy; it does NOT inherit the root file. The spec's "four POM URL properties" undercounts: there are **eight**. |
| `admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def` | 3 | **Fix in Task 3** — `userSetupHint` URL shown to consumers on iOS link failure |
| `admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def` | 3 | **Fix in Task 3** — same |

**Historical documents — leave every one of them alone (9 hits, 4 files):**

| File | Lines | Why it stays |
|---|---|---|
| `handoff.md` | 358, 361, 363, 365 | Historical record of a past diff (it shows `- old` / `+ new` lines). Rewriting it would falsify history. |
| `docs/superpowers/plans/2026-07-29-track1-gradle-plugin-for-consumer-linking.md` | 127, 133, 134, 135 | Archived, executed plan. |
| `docs/superpowers/plans/2026-07-29-track2-consumer-linker-diagnostics.md` | 69, 79 | Archived, executed plan. |
| `docs/superpowers/specs/2026-07-31-public-visibility-design.md` | 15, 244 | Deliberately records the old name as the "before" state. |

> **The historical count is no longer 9.** This plan document — `docs/superpowers/plans/2026-07-31-visibility-1-repo-identity-github-seo.md` — itself quotes the old slug many times, in the audit tables and in every before/after diff. So an unfiltered `grep -rn "Admob-CMP"` now returns a large and unstable number. **Never assert on the unfiltered total.** Every check in this plan filters `docs/superpowers/` and `handoff.md` out and asserts only on live files.

Also noted, and deliberately **not** changed by this plan:
- `settings.gradle.kts:1` — `rootProject.name = "AdmobCMP"`. This is the Gradle project name, not the GitHub slug. It appears in the klib `Library unique name` fields inside the committed ABI dumps (`admob-cmp-core/api/admob-cmp-core.klib.api:8`, `admob-cmp-compose/api/admob-cmp-compose.klib.api:8`). Changing it would churn the frozen ABI dump for zero SEO benefit. **Out of scope.**
- `iosApp/Configuration/Config.xcconfig` — `PRODUCT_NAME=AdmobCMP`, `PRODUCT_BUNDLE_IDENTIFIER=dev.avinya.admob.cmp.AdmobCMP$(TEAM_ID)`. Demo-app identity. **Out of scope.**
- `admob-cmp-core/build.gradle.kts:39` — `baseName = "AdMobCmp"` (the static framework name). **Out of scope.**

- [ ] **Step 1: Authenticate `gh`** **[HUMAN — interactive]**

A human must run this at a terminal. It opens a browser for OAuth; an agent cannot complete it.

```bash
gh auth login --hostname github.com --git-protocol https --web --scopes repo
```

Then verify:

```bash
gh auth status
```

Expected output contains:
```
github.com
  ✓ Logged in to github.com account Meet-Miyani
  - Token scopes: 'repo'
```

If it still prints `You are not logged into any GitHub hosts`, the login did not complete — stop and retry. Every `[gh]` step in this plan depends on this.

- [ ] **Step 2: Reproduce the old-slug audit over live files** **[LOCAL]**

Run from the repo root. The `--exclude-dir=superpowers` and `--exclude=handoff.md` filters remove the historical documents (including this plan), leaving only files that must actually change:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
grep -rn "Admob-CMP" . \
  --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=.gradle_home \
  --exclude-dir=build --exclude-dir=.codegraph --exclude-dir=.kotlin \
  --exclude-dir=.idea --exclude-dir=superpowers --exclude=handoff.md | sort
```

Expected: exactly **10** lines across **4** files —

```
./admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def:3:userSetupHint = admob-cmp ships Google Mobile Ads BINDINGS only, ...
./admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def:3:userSetupHint = admob-cmp ships User Messaging Platform BINDINGS only, ...
./admob-cmp-gradle-plugin/gradle.properties:12:POM_SCM_URL=https://github.com/Meet-Miyani/Admob-CMP
./admob-cmp-gradle-plugin/gradle.properties:13:POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/Admob-CMP.git
./admob-cmp-gradle-plugin/gradle.properties:14:POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/Admob-CMP.git
./admob-cmp-gradle-plugin/gradle.properties:6:POM_URL=https://github.com/Meet-Miyani/Admob-CMP
./gradle.properties:27:POM_URL=https://github.com/Meet-Miyani/Admob-CMP
./gradle.properties:33:POM_SCM_URL=https://github.com/Meet-Miyani/Admob-CMP
./gradle.properties:34:POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/Admob-CMP.git
./gradle.properties:35:POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/Admob-CMP.git
```

Count it:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
grep -rn "Admob-CMP" . \
  --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=.gradle_home \
  --exclude-dir=build --exclude-dir=.codegraph --exclude-dir=.kotlin \
  --exclude-dir=.idea --exclude-dir=superpowers --exclude=handoff.md | wc -l
```

Expected: `10`.

If a hit appears in a file **not** in the live-files table above, stop and classify it before continuing — a new reference may have landed since 2026-07-31.

- [ ] **Step 3: Confirm the workflows are rename-safe** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
ls -1 .github/workflows/
grep -nE "Admob-CMP|Meet-Miyani|github\.com/" .github/workflows/*.yml; echo "grep exit: $?"
grep -n "uses:" .github/workflows/*.yml
```

Expected:
```
publish.yml
release-readiness.yml
grep exit: 1
.github/workflows/publish.yml:24:      - uses: actions/checkout@v6
.github/workflows/publish.yml:25:      - uses: actions/setup-java@v5
.github/workflows/publish.yml:29:      - uses: gradle/actions/setup-gradle@v6
.github/workflows/release-readiness.yml:24:      - uses: actions/checkout@v6
.github/workflows/release-readiness.yml:25:      - uses: actions/setup-java@v5
.github/workflows/release-readiness.yml:29:      - uses: gradle/actions/setup-gradle@v6
```

`grep exit: 1` means **no matches**, which is the passing result. If it prints `grep exit: 0`, a slug reference exists — read it and fix it before the rename.

- [ ] **Step 4: Record the pre-rename baseline** **[gh]**

```bash
gh api /repos/Meet-Miyani/Admob-CMP \
  --jq '{name, full_name, html_url, homepage, license: .license, has_wiki, has_discussions, topics, stargazers_count}'
```

Expected (matching spec §1): `"name": "Admob-CMP"`, `"homepage": null`, `"license": null`, `"has_wiki": true`, `"has_discussions": false`, `"topics": []`.

Keep this output. Task 6 asserts the inverse of every one of these fields.

- [ ] **Step 5: Confirm no GitHub Actions run is in flight** **[gh]**

A rename during an in-flight publish is asking for trouble.

```bash
gh run list --repo Meet-Miyani/Admob-CMP --limit 10
```

Expected: no run in `in_progress` or `queued` status. If one is running, wait for it to finish before starting Task 2.

---

### Task 2: Rename the repository and repoint the local clone

**Files:**
- Create/Modify: none in the working tree. This task changes GitHub-side state and the local git remote URL only.

**Interfaces:**
- Consumes: the authenticated `gh` session from Task 1 Step 1; the "workflows are rename-safe" conclusion from Task 1 Step 3.
- Produces: the canonical repository URL `https://github.com/Meet-Miyani/admob-compose-multiplatform`, which Tasks 3, 5, 6 and 7 all write into files and settings.

- [ ] **Step 1: Rename the repository** **[gh]**

```bash
gh repo rename admob-compose-multiplatform --repo Meet-Miyani/Admob-CMP --yes
```

Expected output:
```
✓ Renamed repository Meet-Miyani/admob-compose-multiplatform
```

If it fails with `HTTP 403`, the token lacks admin rights on the repo — re-run Task 1 Step 1 with the `repo` scope on the account that owns the repository.

- [ ] **Step 2: Verify the new name and the 301 from the old URL** **[LOCAL]**

```bash
gh api /repos/Meet-Miyani/admob-compose-multiplatform --jq '{name, full_name, html_url}'
curl -sS -o /dev/null -w '%{http_code} -> %{redirect_url}\n' https://github.com/Meet-Miyani/Admob-CMP
```

Expected:
```
{"name":"admob-compose-multiplatform","full_name":"Meet-Miyani/admob-compose-multiplatform","html_url":"https://github.com/Meet-Miyani/admob-compose-multiplatform"}
301 -> https://github.com/Meet-Miyani/admob-compose-multiplatform
```

> **On every `gh api --jq` expectation in this plan:** `gh` prints compact single-line JSON and `jq` preserves the key order of the object you constructed. Whitespace and key order in the real output may still differ from what is shown. **Compare the values, not the formatting.**

A `301` with the new URL confirms the redirect that keeps the already-published 1.0.0-1.1.0 POMs resolvable. Those POMs permanently carry the old URL and will always rely on this redirect — which is why the old name must never be reused.

- [ ] **Step 3: Repoint the local clone's remote** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git remote -v
git remote set-url origin https://github.com/Meet-Miyani/admob-compose-multiplatform.git
git remote -v
```

Expected after the change — both `fetch` and `push` lines read:
```
origin	https://github.com/Meet-Miyani/admob-compose-multiplatform.git (fetch)
origin	https://github.com/Meet-Miyani/admob-compose-multiplatform.git (push)
```

If `git remote -v` showed an SSH URL before the change (`git@github.com:Meet-Miyani/Admob-CMP.git`), use the SSH form instead:

```bash
git remote set-url origin git@github.com:Meet-Miyani/admob-compose-multiplatform.git
```

- [ ] **Step 4: Verify the remote works** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git fetch origin --prune
git status -sb
```

Expected: the fetch completes with no error, and `git status -sb` shows `## docs/public-visibility-plan...origin/docs/public-visibility-plan` (or the branch as untracked-upstream if it has never been pushed — that is fine).

- [ ] **Step 5: Prove GitHub Actions still work after the rename** **[gh]**

`release-readiness.yml` has a `workflow_dispatch` trigger, so it can be fired on demand.

```bash
gh workflow run release-readiness.yml --repo Meet-Miyani/admob-compose-multiplatform --ref master
sleep 20
gh run list --repo Meet-Miyani/admob-compose-multiplatform --workflow=release-readiness.yml --limit 3
```

Expected: a new run appears with status `queued` or `in_progress`. Watch it to completion:

```bash
gh run watch --repo Meet-Miyani/admob-compose-multiplatform $(gh run list --repo Meet-Miyani/admob-compose-multiplatform --workflow=release-readiness.yml --limit 1 --json databaseId --jq '.[0].databaseId')
```

Expected: the run concludes `success`. If it fails on a step unrelated to the rename (a flaky Gradle download, an Xcode SDK assertion), that is not a rename regression — read the log before acting.

- [ ] **Step 6: Note the permanent prohibition** **[LOCAL]**

No command. Confirm you have read and understood this, because it cannot be undone later:

> **`Meet-Miyani/Admob-CMP` must never be created again.** Creating any repository at that path silently kills the 301 established in Step 2, breaking the `POM_URL` and `POM_SCM_URL` in the already-published `1.0.0`, `1.0.1`, `1.0.2`, and `1.1.0` POMs on Maven Central. Those POMs are immutable and cannot be fixed.

---

### Task 3: Repoint every in-repo URL and add the trademark line to the POM descriptions

**Files:**
- Modify: `gradle.properties:27`, `gradle.properties:33-35`, `gradle.properties:26`
- Modify: `admob-cmp-gradle-plugin/gradle.properties:5-6`, `admob-cmp-gradle-plugin/gradle.properties:12-14`
- Modify: `admob-cmp/gradle.properties:3`
- Modify: `admob-cmp-core/gradle.properties:3`
- Modify: `admob-cmp-compose/gradle.properties:3`
- Modify: `admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def:3`
- Modify: `admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def:3`

**Interfaces:**
- Consumes: the new repository URL established in Task 2.
- Produces: POM metadata for the **next** release (1.1.1 / 1.2.0 onward) that carries the correct URL and the required trademark line. Published 1.0.0-1.1.0 POMs are immutable and keep the old URL forever; the Task 2 redirect covers them.

**Why five files and not one:** `admob-cmp-gradle-plugin` is an included build (`settings.gradle.kts:9`, `includeBuild("admob-cmp-gradle-plugin")`) with its own `gradle.properties`. It does **not** inherit the root file. The three module `gradle.properties` files each override `POM_DESCRIPTION` for their own artifact, so the trademark line must be added to each one individually or it will not reach the published POM.

**Note on the `.properties` unicode escapes:** `admob-cmp-core/gradle.properties` and `admob-cmp-compose/gradle.properties` contain `—` (em dash) in `POM_NAME`. Leave those escapes exactly as they are — Java `.properties` files are ISO-8859-1 and the escape is deliberate.

- [ ] **Step 1: Update the four URL properties in the root `gradle.properties`** **[LOCAL]**

Replace lines 27 and 33-35 of `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/gradle.properties`.

Old:
```properties
POM_URL=https://github.com/Meet-Miyani/Admob-CMP
```
New:
```properties
POM_URL=https://github.com/Meet-Miyani/admob-compose-multiplatform
```

Old:
```properties
POM_SCM_URL=https://github.com/Meet-Miyani/Admob-CMP
POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/Admob-CMP.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/Admob-CMP.git
```
New:
```properties
POM_SCM_URL=https://github.com/Meet-Miyani/admob-compose-multiplatform
POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/admob-compose-multiplatform.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/admob-compose-multiplatform.git
```

- [ ] **Step 2: Add the trademark line to the root `POM_DESCRIPTION`** **[LOCAL]**

Replace line 26 of `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/gradle.properties`.

Old:
```properties
POM_DESCRIPTION=Plug-and-play Compose Multiplatform AdMob SDK for Android GMA Next-Gen and iOS Google Mobile Ads.
```
New:
```properties
POM_DESCRIPTION=Plug-and-play Compose Multiplatform AdMob SDK for Android GMA Next-Gen and iOS Google Mobile Ads. Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

- [ ] **Step 3: Update `admob-cmp-gradle-plugin/gradle.properties`** **[LOCAL]**

Replace lines 5-6 and 12-14 of `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-gradle-plugin/gradle.properties`.

Old:
```properties
POM_DESCRIPTION=Links Google Mobile Ads/UMP into Kotlin/Native test executables for admob-cmp consumers.
POM_URL=https://github.com/Meet-Miyani/Admob-CMP
```
New:
```properties
POM_DESCRIPTION=Links Google Mobile Ads/UMP into Kotlin/Native test executables for admob-cmp consumers. Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
POM_URL=https://github.com/Meet-Miyani/admob-compose-multiplatform
```

Old:
```properties
POM_SCM_URL=https://github.com/Meet-Miyani/Admob-CMP
POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/Admob-CMP.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/Admob-CMP.git
```
New:
```properties
POM_SCM_URL=https://github.com/Meet-Miyani/admob-compose-multiplatform
POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/admob-compose-multiplatform.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/admob-compose-multiplatform.git
```

- [ ] **Step 4: Add the trademark line to the three module POM descriptions** **[LOCAL]**

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp/gradle.properties`, replace line 3.

Old:
```properties
POM_DESCRIPTION=Plug-and-play Compose Multiplatform AdMob SDK wrapper for Android GMA Next-Gen and iOS Google Mobile Ads.
```
New:
```properties
POM_DESCRIPTION=Plug-and-play Compose Multiplatform AdMob SDK wrapper for Android GMA Next-Gen and iOS Google Mobile Ads. Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-core/gradle.properties`, replace line 3.

Old:
```properties
POM_DESCRIPTION=Compose-free Kotlin Multiplatform core for the admob-cmp AdMob SDK (AdManager, consent, full-screen orchestration, banner/native pools, iOS bindings).
```
New:
```properties
POM_DESCRIPTION=Compose-free Kotlin Multiplatform core for the admob-cmp AdMob SDK (AdManager, consent, full-screen orchestration, banner/native pools, iOS bindings). Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-compose/gradle.properties`, replace line 3.

Old:
```properties
POM_DESCRIPTION=Compose Multiplatform UI for the admob-cmp AdMob SDK (BannerAdView, NativeAdView, native-ad layout DSL, debug console, rememberAdManager).
```
New:
```properties
POM_DESCRIPTION=Compose Multiplatform UI for the admob-cmp AdMob SDK (BannerAdView, NativeAdView, native-ad layout DSL, debug console, rememberAdManager). Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
```

- [ ] **Step 5: Update the two cinterop `userSetupHint` URLs** **[LOCAL]**

These strings are printed to a consuming developer's console when a Kotlin/Native link fails. They must not point at a slug that only resolves through a redirect.

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def`, replace line 3 in full.

Old:
```
userSetupHint = admob-cmp ships Google Mobile Ads BINDINGS only, never Google's binaries. An iOS APP link gets them from the GoogleMobileAds Swift package (Xcode > Add Package Dependencies, 13.7.0+). A Kotlin/Native TEST executable has no Xcode, so it must link them itself - apply the dev.avinya.ads.admob-cmp Gradle plugin, or see https://github.com/Meet-Miyani/Admob-CMP/blob/master/admob-cmp/docs/SETUP.md#kotlinnative-test-executables
```
New:
```
userSetupHint = admob-cmp ships Google Mobile Ads BINDINGS only, never Google's binaries. An iOS APP link gets them from the GoogleMobileAds Swift package (Xcode > Add Package Dependencies, 13.7.0+). A Kotlin/Native TEST executable has no Xcode, so it must link them itself - apply the dev.avinya.ads.admob-cmp Gradle plugin, or see https://github.com/Meet-Miyani/admob-compose-multiplatform/blob/master/admob-cmp/docs/SETUP.md#kotlinnative-test-executables
```

In `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def`, replace line 3 in full.

Old:
```
userSetupHint = admob-cmp ships User Messaging Platform BINDINGS only, never Google's binaries. An iOS APP link gets them from the GoogleUserMessagingPlatform Swift package (Xcode > Add Package Dependencies, 3.1.0+). A Kotlin/Native TEST executable has no Xcode, so it must link them itself - apply the dev.avinya.ads.admob-cmp Gradle plugin, or see https://github.com/Meet-Miyani/Admob-CMP/blob/master/admob-cmp/docs/SETUP.md#kotlinnative-test-executables
```
New:
```
userSetupHint = admob-cmp ships User Messaging Platform BINDINGS only, never Google's binaries. An iOS APP link gets them from the GoogleUserMessagingPlatform Swift package (Xcode > Add Package Dependencies, 3.1.0+). A Kotlin/Native TEST executable has no Xcode, so it must link them itself - apply the dev.avinya.ads.admob-cmp Gradle plugin, or see https://github.com/Meet-Miyani/admob-compose-multiplatform/blob/master/admob-cmp/docs/SETUP.md#kotlinnative-test-executables
```

Each `userSetupHint` must remain a **single line**. A wrapped line breaks `.def` parsing.

- [ ] **Step 6: Confirm no live-file old-slug reference remains** **[LOCAL]**

This is the same filtered grep as Task 1 Step 2. It must now return nothing.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
grep -rn "Admob-CMP" . \
  --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=.gradle_home \
  --exclude-dir=build --exclude-dir=.codegraph --exclude-dir=.kotlin \
  --exclude-dir=.idea --exclude-dir=superpowers --exclude=handoff.md
echo "exit: $?"
```

Expected: no output, then `exit: 1`. (`grep` exits 1 when it finds nothing — that is the passing result.)

Belt and braces, scoped by file type so it cannot be confused by a documentation hit:

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
grep -rn "Admob-CMP" --include="*.properties" --include="*.def" --include="*.yml" --include="*.kts" --include="*.toml" . \
  --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.gradle_home
echo "exit: $?"
```

Expected: no output, then `exit: 1`. This is the assertion that matters.

The historical documents (`handoff.md`, `docs/superpowers/**`) still contain the old slug on purpose. Do not "clean them up."

- [ ] **Step 7: Verify the generated POMs carry the new URL** **[LOCAL]**

The `.def` edit invalidates the cinterop task, so this run will recompile the iOS interop. It needs macOS with Xcode 26 and may take several minutes.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
./gradlew :admob-cmp:generatePomFileForKotlinMultiplatformPublication --no-configuration-cache
grep -E "<url>|<connection>|<developerConnection>|<description>" admob-cmp/build/publications/kotlinMultiplatform/pom-default.xml
```

Expected: every `<url>` / `<connection>` / `<developerConnection>` contains `admob-compose-multiplatform` and none contains `Admob-CMP`. The `<description>` ends with `AdMob and Google Mobile Ads are trademarks of Google LLC.`

- [ ] **Step 8: Verify the frozen ABI is unchanged** **[LOCAL]**

The `.def` edit touches the cinterop input, so this gate is mandatory. `userSetupHint` is a diagnostic message and is not part of the klib ABI, so this must pass with no dump change.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
./gradlew :admob-cmp-core:checkKotlinAbi :admob-cmp-compose:checkKotlinAbi --no-configuration-cache
git status --porcelain admob-cmp-core/api admob-cmp-compose/api
```

Expected: `BUILD SUCCESSFUL`, and `git status --porcelain` prints **nothing** for the `api/` directories.

**If `checkKotlinAbi` fails or an `api/*.klib.api` file shows as modified, STOP.** Do not run `updateKotlinAbi`. A `userSetupHint` change must not move the ABI; if it did, something else changed and needs investigation (`admob-cmp/CLAUDE.md` invariant 12 — the ABI is frozen).

- [ ] **Step 9: Verify the Gradle plugin build still passes** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
./gradlew -p admob-cmp-gradle-plugin build --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add gradle.properties \
  admob-cmp/gradle.properties \
  admob-cmp-core/gradle.properties \
  admob-cmp-compose/gradle.properties \
  admob-cmp-gradle-plugin/gradle.properties \
  admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def \
  admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def
git commit -m "chore(meta): point POM and cinterop URLs at admob-compose-multiplatform

The repository was renamed from Admob-CMP to admob-compose-multiplatform.
Update all eight POM URL properties (root gradle.properties and the
admob-cmp-gradle-plugin included build, which does not inherit the root
file) and both cinterop userSetupHint links. Add the required trademark
disclaimer to every published POM description.

Published 1.0.0-1.1.0 POMs keep the old URL permanently and rely on
GitHub's 301; the old repo name must never be reused."
```

---

### Task 4: Put `LICENSE` at the repository root

GitHub only detects a license from the repository root. `LICENSE` currently sits at `admob-cmp/LICENSE`, which is exactly why the API reports `license: null` (spec §1).

**Files:**
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/LICENSE` (byte-identical copy of `admob-cmp/LICENSE`)
- Leave in place: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp/LICENSE`

**Interfaces:**
- Consumes: nothing.
- Produces: a root `LICENSE` that Task 5's README badge links to (`[![License](...)](LICENSE)`), and that Task 6 asserts GitHub has detected as `Apache-2.0`.

**Copy, do not move.** The spec §10 wording says "move", but keeping `admob-cmp/LICENSE` in place preserves the module directory as self-contained (it is the artifact's own source root and `admob-cmp/README.md` refers to its license). A duplicated Apache-2.0 text is harmless; a module missing its license is not.

- [ ] **Step 1: Copy the license to the root** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
cp admob-cmp/LICENSE LICENSE
```

- [ ] **Step 2: Verify the copy is byte-identical and is Apache 2.0** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
diff admob-cmp/LICENSE LICENSE && echo "IDENTICAL"
head -3 LICENSE
wc -l LICENSE
```

Expected:
```
IDENTICAL
Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/
      53 LICENSE
```

- [ ] **Step 3: Commit** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add LICENSE
git commit -m "docs: add Apache-2.0 LICENSE at the repository root

GitHub only detects a license from the repo root, so with LICENSE living
only in admob-cmp/ the API reported license: null. Copy (not move) it so
the module directory stays self-contained."
```

- [ ] **Step 4: Verify GitHub detects it** **[gh]**

Push first, then query. GitHub's license detection runs on the default branch, so this reads correctly only after the change reaches `master`.

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git push origin docs/public-visibility-plan
gh api /repos/Meet-Miyani/admob-compose-multiplatform --jq '.license'
```

Expected **while still on the feature branch**: `null` — detection has not run yet, because the file is not on the default branch.

After this branch is merged into `master`, re-run the `gh api` call. Expected then:
```json
{"key":"apache-2.0","name":"Apache License 2.0","spdx_id":"Apache-2.0","url":"https://api.github.com/licenses/apache-2.0","node_id":"MDc6TGljZW5zZTI="}
```

Record this as an open verification for the merge; it is the acceptance criterion for this task.

---

### Task 5: Replace the root `README.md`

The current root README is the JetBrains Compose Multiplatform template with a few local edits. It never mentions AdMob, the Maven coordinate, or the ad formats, so it cannot rank for anything and cannot convert a visitor.

**Files:**
- Modify (replace entirely): `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/README.md`
- Source material (read only, do not modify): `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp/README.md`, `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/admob-cmp/AGENTS.md`, `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/gradle/libs.versions.toml`

**Interfaces:**
- Consumes: the new repository URL (Task 2), the root `LICENSE` the badge links to (Task 4).
- Produces: the H1 `AdMob CMP — Compose Multiplatform AdMob SDK for Android and iOS`, which Plan 5's landing page hero must not duplicate verbatim, and the format/compatibility tables that Plan 3 expands into `/reference/compatibility/`.

**Every Kotlin symbol below was verified against the committed ABI dumps.** Do not add symbols that are not here. Verified sources:
- `rememberAdManager()` — `admob-cmp-compose/api/admob-cmp-compose.klib.api:1086`
- `BannerAdView(placement, modifier, ...)` — `admob-cmp-compose/api/admob-cmp-compose.klib.api:1083`
- `NativeAdView(placement, itemKey, layout, ...)` — `admob-cmp-compose/api/admob-cmp-compose.klib.api:1084`
- `adLayout {}` — `admob-cmp-compose/api/admob-cmp-compose.klib.api:1044`
- `gatherConsentAndInitialize(AdConfig)` — `admob-cmp-core/api/admob-cmp-core.klib.api:1519`
- `AdManager.banner / interstitial / rewarded / rewardedInterstitial / appOpen / nativeAd` — `admob-cmp-core/api/admob-cmp-core.klib.api:226-231`
- `AdPlacement(id, format, androidAdUnitId, iosAdUnitId, maxCacheSize, enabled, strictTestMode)` — `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdPlacement.kt:86-93`
- `AdConfig(androidAppId, iosAppId, testMode, ...)` — `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdConfig.kt:40-47`
- `AdFormat.{Banner, Interstitial, Native, Rewarded, RewardedInterstitial, AppOpen}` — `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdPlatform.kt:13-26`
- `AppOpenAdCoordinator(manager, controller, config)` — `admob-cmp-core/api/admob-cmp-core.klib.api:767-768`
- `AdManagerStatus.Ready` — `admob-cmp-core/api/admob-cmp-core.klib.api:617`
- All `TestAdIds.*` constants — `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/TestAdIds.kt:18-49`

- [ ] **Step 1: Write the new root README** **[LOCAL]**

Write the following content to `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/README.md`, replacing the file completely.

`````markdown
# AdMob CMP — Compose Multiplatform AdMob SDK for Android and iOS

[![Maven Central](https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp?label=Maven%20Central)](https://central.sonatype.com/artifact/dev.avinya.ads/admob-cmp)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS-3DDC84)](#compatibility)

One Kotlin API for **AdMob on Compose Multiplatform**. Write your ad code once in `commonMain` and get banner, interstitial, rewarded, rewarded interstitial, app-open, and native ads on both Android and iOS. AdMob CMP wraps the Google Mobile Ads Next-Gen SDK on Android and the Google Mobile Ads iOS SDK, keeps AdMob's own vocabulary (`AdValue`, `ResponseInfo`, adaptive banner sizes, UMP consent states, native asset names), and replaces the listener-style surface with suspend functions, `StateFlow` state, and one sealed `AdEvent` stream. UMP consent, iOS App Tracking Transparency ordering, paid/revenue events, and mediation are built into the initialization flow rather than bolted on.

> **Brand, repository, coordinate.** The library is branded **AdMob CMP**, the repository is **`admob-compose-multiplatform`**, and the Maven coordinate is **`dev.avinya.ads:admob-cmp`**. The coordinate has not changed across any release and will not change.

**Documentation: [ads.avinya.dev](https://ads.avinya.dev)** · [Quickstart](https://ads.avinya.dev/start/quickstart/) · [Installation](https://ads.avinya.dev/start/installation/) · [iOS setup](https://ads.avinya.dev/start/ios-setup/) · [Troubleshooting](https://ads.avinya.dev/reference/troubleshooting/)

## Install

```kotlin
// commonMain
implementation("dev.avinya.ads:admob-cmp:1.1.0")
```

If your project runs Kotlin/Native tests (`:yourModule:iosSimulatorArm64Test`), also apply the Gradle plugin. Without it the test link fails with `Undefined symbols … _OBJC_CLASS_$_GAD*`, because a Kotlin/Native test executable has no Xcode to resolve the Swift packages for it:

```kotlin
plugins {
    id("dev.avinya.ads.admob-cmp") version "1.1.0"
}
```

Platform setup — the Android manifest entry, and on iOS the two Swift packages plus `Info.plist` keys — is required. Follow [`admob-cmp/docs/SETUP.md`](admob-cmp/docs/SETUP.md), then verify with `./gradlew :admob-cmp-core:doctorIos`.

## Ad formats

All six formats, on both platforms, from one `commonMain` API.

| Format | `AdFormat` | Controller (from `AdManager`) | Composable | Test ad units |
|---|---|---|---|---|
| Banner (incl. collapsible) | `AdFormat.Banner` | `banner(placement)` | `BannerAdView(placement)` | `TestAdIds.ANDROID_BANNER` / `IOS_BANNER` |
| Interstitial | `AdFormat.Interstitial` | `interstitial(placement)` | — | `ANDROID_INTERSTITIAL` / `IOS_INTERSTITIAL` |
| Rewarded | `AdFormat.Rewarded` | `rewarded(placement)` | — | `ANDROID_REWARDED` / `IOS_REWARDED` |
| Rewarded interstitial | `AdFormat.RewardedInterstitial` | `rewardedInterstitial(placement)` | — | `ANDROID_REWARDED_INTERSTITIAL` / `IOS_REWARDED_INTERSTITIAL` |
| App-open | `AdFormat.AppOpen` | `appOpen(placement)` + `AppOpenAdCoordinator` | — | `ANDROID_APP_OPEN` / `IOS_APP_OPEN` |
| Native | `AdFormat.Native` | `nativeAd(placement)` (a pool) | `NativeAdView(placement, itemKey, layout)` | `ANDROID_NATIVE` / `IOS_NATIVE` |

## 30-second quickstart

This runs against Google's official sample ad units, so it is safe to paste as-is.

```kotlin
@Composable
fun App() {
    val adManager = rememberAdManager()

    LaunchedEffect(Unit) {
        adManager.gatherConsentAndInitialize(
            AdConfig(
                androidAppId = TestAdIds.ANDROID_APP_ID,
                iosAppId = TestAdIds.IOS_APP_ID,
                testMode = true
            )
        )
    }

    val placement = remember {
        AdPlacement(
            id = "main_interstitial",
            format = AdFormat.Interstitial,
            androidAdUnitId = TestAdIds.ANDROID_INTERSTITIAL,
            iosAdUnitId = TestAdIds.IOS_INTERSTITIAL,
            strictTestMode = true
        )
    }
    val interstitial = remember(adManager) { adManager.interstitial(placement) }
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            interstitial.load()
            interstitial.show()
        }
    }) { Text("Show ad") }
}
```

`gatherConsentAndInitialize` runs the whole production sequence for you: UMP consent, then App Tracking Transparency on iOS, then the one-time SDK initialization. Gate ad-dependent UI on `adManager.status.collectAsState()` reaching `AdManagerStatus.Ready`.

A banner is one composable — it measures its own container and supplies the width, so adaptive sizing is correct even in iPad split view and Slide Over:

```kotlin
BannerAdView(
    placement = AdPlacement(
        id = "home_banner",
        format = AdFormat.Banner,
        androidAdUnitId = TestAdIds.ANDROID_BANNER,
        iosAdUnitId = TestAdIds.IOS_BANNER
    ),
    modifier = Modifier.fillMaxWidth()
)
```

Native ads are laid out with a declarative DSL and served from a pool, so a feed reuses one placement id across every row and the pool serves a distinct ad per `itemKey`:

```kotlin
val nativePlacement = remember {
    AdPlacement(
        id = "feed_native",
        format = AdFormat.Native,
        androidAdUnitId = TestAdIds.ANDROID_NATIVE,
        iosAdUnitId = TestAdIds.IOS_NATIVE,
        maxCacheSize = 3
    )
}

val layout = remember {
    adLayout {
        column(modifier = AdModifier.fillMaxWidth()) {
            media(modifier = AdModifier.fillMaxWidth().aspectRatio(16f / 9f))
            headline(maxLines = 2)
            body(maxLines = 3)
            row(spacing = 8.dp) { icon(modifier = AdModifier.size(24.dp)); advertiser(); adBadge() }
            callToAction(modifier = AdModifier.fillMaxWidth())
        }
    }
}

NativeAdView(placement = nativePlacement, itemKey = "feed_3", layout = layout)
```

Use a static, finite placement id. Never generate one per row (`"feed_item_$index"`) — controllers are cached per id for the manager's lifetime.

## Why AdMob CMP

- **Six formats, not four.** Native ads and app-open ads are supported on both platforms, with a layout DSL and a real pool.
- **Consent is part of initialization.** UMP modes, the privacy options form, and `canRequestAds` are first-class, and the iOS consent → ATT → initialize ordering is enforced rather than documented and hoped for.
- **The iOS test link actually works.** The `dev.avinya.ads.admob-cmp` Gradle plugin links Google Mobile Ads and UMP into Kotlin/Native test executables, which is the difference between `:iosSimulatorArm64Test` passing and failing with `Undefined symbols … _OBJC_CLASS_$_GAD*`.
- **Revenue and mediation are exposed.** Paid events carry `AdValue` and `ResponseInfo`; mediation adapters get initialization hooks.
- **Test safety fails closed.** `AdPlacement.strictTestMode` throws at construction if a placement points at a production ad unit — turn it on in debug builds.
- **The public ABI is frozen** and enforced in CI by Kotlin ABI validation, so upgrades do not silently break you.

## Compatibility

`admob-cmp` publishes Kotlin/Native klibs plus cinterop klibs. Klibs are not binary-compatible across arbitrary Kotlin versions, so consumers must build with a compatible compiler.

| admob-cmp | Kotlin | Compose Multiplatform | Android `minSdk` | iOS deployment target |
|---|---|---|---|---|
| 1.1.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |
| 1.0.2 | 2.3.20 | 1.11.1 | 26 | 15.0 |
| 1.0.0 | 2.3.20 | 1.11.1 | 26 | 15.0 |

Underlying Google SDKs bound by 1.1.0:

| SDK | Version |
|---|---|
| Google Mobile Ads, Android (Next-Gen) | 1.3.0 |
| Google Mobile Ads, iOS | 13.7.0 |
| User Messaging Platform, Android | 4.0.0 |
| User Messaging Platform, iOS | 3.1.0 |

**Kotlin:** the module is compiled with 2.3.20. Consumers on a different Kotlin *minor* version may fail to resolve the klib. Patch versions are generally safe.

**Compose Multiplatform:** required only if you use the composable surface (`BannerAdView`, `NativeAdView`, `rememberAdManager`). The controller API in `dev.avinya.ads:admob-cmp-core` has no Compose dependency.

**Consumption model:** the SDK is consumable from Kotlin Multiplatform / Gradle projects only — it compiles into the consumer's umbrella framework. A pure-Swift iOS app cannot adopt it without a Kotlin Multiplatform shim.

**Published artifacts:** `dev.avinya.ads:admob-cmp` is the facade and is what you should depend on. It brings in `dev.avinya.ads:admob-cmp-core` (Compose-free) and `dev.avinya.ads:admob-cmp-compose` (the composables). `dev.avinya.ads:admob-cmp-gradle-plugin` is the Kotlin/Native test-linking plugin, applied by its `dev.avinya.ads.admob-cmp` plugin id.

## Documentation

Full guides, diagrams, and the generated API reference live at **[ads.avinya.dev](https://ads.avinya.dev)**. The in-repo sources:

- [Setup & initialization](admob-cmp/docs/SETUP.md) — dependency, Android and iOS platform setup, init, troubleshooting
- [Banner ads](admob-cmp/docs/BANNER.md) — adaptive sizes, collapsible, refresh policies, geometry
- [Interstitial & rewarded](admob-cmp/docs/INTERSTITIAL.md) — load/show, caching, retry
- [Native ads](admob-cmp/docs/NATIVE.md) — layout DSL, pooling, media info
- [App-open ads](admob-cmp/docs/APP_OPEN.md) — `AppOpenAdCoordinator`, cooldowns, blocking
- [Consent & privacy](admob-cmp/docs/CONSENT.md) — UMP modes, privacy options form
- [Mediation](admob-cmp/docs/MEDIATION.md) — adapters, initialization hooks
- [Architecture](admob-cmp/docs/ARCHITECTURE.md) — module map, threading, caching, decisions
- [Publishing](admob-cmp/docs/PUBLISHING.md) — maintainer guide

Integrating with an AI coding agent? Point it at [`admob-cmp/AGENTS.md`](admob-cmp/AGENTS.md) — it is the authoritative, condensed API and usage guide.

## Repository layout

This repository is the SDK plus a Kotlin Multiplatform demo that exercises it.

| Module | What it is |
|---|---|
| `admob-cmp/` | The published facade artifact — depends on core and compose |
| `admob-cmp-core/` | Compose-free Kotlin Multiplatform core: `AdManager`, consent, full-screen orchestration, banner and native pools, iOS cinterop bindings |
| `admob-cmp-compose/` | Compose Multiplatform UI: `BannerAdView`, `NativeAdView`, the native-ad layout DSL, the debug console, `rememberAdManager` |
| `admob-cmp-gradle-plugin/` | Links Google Mobile Ads and UMP into Kotlin/Native test executables |
| `shared/`, `androidApp/`, `iosApp/`, `desktopApp/`, `webApp/` | The demo application. Ads render on the Android and iOS targets; desktop and web build without the ad surface. |

## Running the demo

Android and iOS open directly into the AdMob debug console, which exercises every format against Google's official sample ad units with `strictTestMode` validation on every placement.

```bash
./gradlew :androidApp:assembleDebug          # Android
./gradlew :desktopApp:run                    # Desktop (no ads)
./gradlew :webApp:wasmJsBrowserDevelopmentRun # Web (no ads)
```

For iOS, open [`iosApp/`](iosApp) in Xcode and run. Compose Multiplatform requires **Xcode 26** (and the iOS 26 SDK) because of `UIViewLayoutRegion` linkage.

Tests:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest        # JVM + Android-layer unit tests
./gradlew :admob-cmp-core:iosSimulatorArm64Test      # iOS unit tests
./gradlew :admob-cmp-core:checkKotlinAbi             # public API surface check
./gradlew :admob-cmp-core:doctorIos                  # diagnose iOS consumer integration
```

## Contributing

Issues and pull requests are welcome. Questions, integration help, and feature ideas belong in [Discussions](https://github.com/Meet-Miyani/admob-compose-multiplatform/discussions).

The public ABI is frozen. Additive changes are fine; any breaking change needs a written migration plan. After any public API change, run `./gradlew :admob-cmp-core:updateKotlinAbi` and commit the regenerated `api/*.klib.api` dump, or CI will fail.

## License

[Apache License 2.0](LICENSE).

---

Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
`````

- [ ] **Step 2: Verify the trademark line and the H1** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
head -1 README.md
tail -1 README.md
grep -cE "JetBrains|YouTrack|compose-web|kotl\.in/wasm" README.md
echo "template-remnant grep exit: $?"
```

Use `grep -E`, not `grep` with `\|` — macOS ships BSD grep, whose basic regular expressions do not support `\|` alternation.

Expected:
```
# AdMob CMP — Compose Multiplatform AdMob SDK for Android and iOS
Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.
0
template-remnant grep exit: 1
```

`0` / `exit: 1` confirms every JetBrains-template remnant is gone.

- [ ] **Step 3: Verify the keyword targets are present in the H1 and first paragraph** **[LOCAL]**

The H1 must carry the head term `compose multiplatform admob` and the pitch must carry `kotlin multiplatform` (spec §7).

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
head -1 README.md | grep -qi "compose multiplatform admob" && echo "H1 head-term OK" || echo "H1 head-term MISSING"
sed -n '1,20p' README.md | grep -qi "AdMob on Compose Multiplatform" && echo "pitch OK" || echo "pitch MISSING"
grep -qi "Kotlin Multiplatform" README.md && echo "kmp term OK" || echo "kmp term MISSING"
```

Expected: `H1 head-term OK`, `pitch OK`, `kmp term OK`.

- [ ] **Step 4: Verify every relative link resolves** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
for f in LICENSE admob-cmp/docs/SETUP.md admob-cmp/docs/BANNER.md admob-cmp/docs/INTERSTITIAL.md \
         admob-cmp/docs/NATIVE.md admob-cmp/docs/APP_OPEN.md admob-cmp/docs/CONSENT.md \
         admob-cmp/docs/MEDIATION.md admob-cmp/docs/ARCHITECTURE.md admob-cmp/docs/PUBLISHING.md \
         admob-cmp/AGENTS.md iosApp; do
  [ -e "$f" ] && echo "OK   $f" || echo "MISSING $f"
done
```

Expected: `OK` for all eleven paths. Any `MISSING` is a broken README link — fix the link, do not create the file.

- [ ] **Step 5: Verify the Maven Central badge renders the current version** **[LOCAL]**

```bash
curl -sS "https://img.shields.io/maven-central/v/dev.avinya.ads/admob-cmp" | grep -o "v1\.[0-9]*\.[0-9]*" | head -1
```

Expected: `v1.1.0`. If the badge SVG contains `invalid` or `not found`, the coordinate in the badge URL is wrong — it must be exactly `dev.avinya.ads/admob-cmp`.

- [ ] **Step 6: Commit** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add README.md
git commit -m "docs: replace the JetBrains template README with a product README

The root README was still the Compose Multiplatform template and never
mentioned AdMob, the Maven coordinate, or the six supported ad formats.

Replace it with a keyword-bearing H1 targeting 'compose multiplatform
admob', a badge row (Maven Central, Apache-2.0, Kotlin 2.3.20,
Android/iOS), the one-paragraph pitch, the six-format table, a
30-second quickstart against Google's sample ad units, the
compatibility matrix, links to ads.avinya.dev, and the required
trademark disclaimer.

ads.avinya.dev is provisioned by visibility Plan 2; those links go live
when that plan ships."
```

- [ ] **Step 7: Push and eyeball the rendered result** **[HUMAN — web UI]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git push origin docs/public-visibility-plan
```

Open `https://github.com/Meet-Miyani/admob-compose-multiplatform/blob/docs/public-visibility-plan/README.md` and confirm:
1. All four badges render as images, not broken-image icons.
2. The Maven Central badge reads `v1.1.0`.
3. Both tables render as tables.
4. Every Kotlin block is syntax-highlighted (the language tag parsed).
5. The trademark line is the last visible line.

---

### Task 6: Set topics, homepage, Discussions, and Wiki

**Files:**
- Create/Modify: none. GitHub-side settings only.

**Interfaces:**
- Consumes: the authenticated `gh` session (Task 1 Step 1) and the renamed repository (Task 2).
- Produces: the twelve topics and the `homepage` field the spec's §12 metrics are measured against; the Discussions space the README's Contributing section links to.

- [ ] **Step 1: Set exactly the twelve topics** **[gh]**

The `PUT /topics` endpoint **replaces** the whole set, so this one call is both the add and the prune.

```bash
gh api --method PUT /repos/Meet-Miyani/admob-compose-multiplatform/topics \
  -f "names[]=kotlin-multiplatform" \
  -f "names[]=compose-multiplatform" \
  -f "names[]=admob" \
  -f "names[]=kmp" \
  -f "names[]=kotlin" \
  -f "names[]=ads" \
  -f "names[]=monetization" \
  -f "names[]=native-ads" \
  -f "names[]=android" \
  -f "names[]=ios" \
  -f "names[]=ump" \
  -f "names[]=gdpr"
```

Expected: a JSON object whose `names` array contains all twelve.

- [ ] **Step 2: Set the homepage, enable Discussions, disable the Wiki** **[gh]**

```bash
gh api --method PATCH /repos/Meet-Miyani/admob-compose-multiplatform \
  -f homepage=https://ads.avinya.dev \
  -F has_discussions=true \
  -F has_wiki=false
```

`-f` sends a string, `-F` sends a typed value — the booleans must use `-F` or GitHub receives the strings `"true"` / `"false"` and rejects them.

The wiki is empty (spec §1), so disabling it destroys nothing. Disabling a wiki hides it; it does not delete content.

- [ ] **Step 3: Verify every setting** **[gh]**

```bash
gh api /repos/Meet-Miyani/admob-compose-multiplatform \
  --jq '{name, homepage, has_wiki, has_discussions, topics: (.topics | sort)}'
```

Expected values (compact single-line output; compare values, not formatting):

| Field | Expected |
|---|---|
| `name` | `admob-compose-multiplatform` |
| `homepage` | `https://ads.avinya.dev` |
| `has_wiki` | `false` |
| `has_discussions` | `true` |
| `topics` (sorted) | `["admob","ads","android","compose-multiplatform","gdpr","ios","kmp","kotlin","kotlin-multiplatform","monetization","native-ads","ump"]` |

Assert the count explicitly:

```bash
gh api /repos/Meet-Miyani/admob-compose-multiplatform --jq '.topics | length'
```

Expected: `12`. If it is not 12, re-run Step 1 — a partial `PUT` leaves a partial set.

- [ ] **Step 4: Confirm the Discussions space initialised** **[HUMAN — web UI]**

Open `https://github.com/Meet-Miyani/admob-compose-multiplatform/discussions`.

Expected: the Discussions tab loads. On first visit GitHub may prompt to pick starter categories — accept the defaults (Announcements, General, Ideas, Q&A, Show and tell). The README's Contributing section links here, so it must not 404.

---

### Task 7: Produce and upload the 1280x640 social preview image

GitHub renders the social preview as the Open Graph image for every link to the repository — in Slack, on X, on Reddit, in Discord. Without one, GitHub substitutes a generic card and the link reads as noise.

**Files:**
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.github/social-preview.html` (the reproducible source)
- Create: `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.github/social-preview.png` (the 1280x640 artefact)

**Interfaces:**
- Consumes: nothing beyond the brand facts in Global Constraints.
- Produces: `.github/social-preview.png`, which Plan 5 may reuse as the docs-site OG fallback.

**Specification.** GitHub requires **1280x640 px** and under 1 MB, and crops to roughly 1200x600 in some surfaces — so keep all content inside a 64 px margin. Content, in reading order:
1. Wordmark: `AdMob CMP`
2. Subtitle: `Compose Multiplatform AdMob SDK`
3. Format strip: `Banner · Interstitial · Rewarded · Rewarded Interstitial · App-Open · Native`
4. Coordinate chip: `dev.avinya.ads:admob-cmp`
5. Platform chip: `Android · iOS`
6. Footer: `ads.avinya.dev`

No Google logo, no AdMob logo, no Google brand colours. Nominative text use of "AdMob" only (spec §3, trademark posture). Colours are AdMob CMP's own.

**Production method.** Headless Google Chrome screenshotting a self-contained HTML file. This was verified on this machine on 2026-07-31 and produces an exactly 1280x640 PNG. It needs no new dependencies: Chrome is at `/Applications/Google Chrome.app`, and ImageMagick `magick` (at `/opt/homebrew/bin/magick`) is used only to assert the dimensions. Checking the HTML in makes the card regenerable when the version or format list changes.

- [ ] **Step 1: Write the social preview source** **[LOCAL]**

Write the following to `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.github/social-preview.html`.

```html
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    width: 1280px; height: 640px; overflow: hidden;
    background: radial-gradient(1100px 620px at 12% 0%, #1B2440 0%, #0B0D12 62%);
    color: #F4F6FB;
    font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", Arial, sans-serif;
    -webkit-font-smoothing: antialiased;
  }
  .frame { padding: 72px 80px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; }
  .rule { width: 96px; height: 6px; border-radius: 3px; background: linear-gradient(90deg, #7F52FF 0%, #3DDC84 100%); }
  h1 { font-size: 92px; line-height: 1.02; letter-spacing: -0.035em; font-weight: 700; margin-top: 28px; }
  h2 { font-size: 38px; line-height: 1.25; font-weight: 500; color: #AEB8D0; margin-top: 16px; letter-spacing: -0.01em; }
  .formats { font-size: 24px; color: #8E9AB5; margin-top: 30px; letter-spacing: 0.005em; }
  .chips { display: flex; gap: 14px; align-items: center; }
  .chip {
    font-size: 23px; padding: 12px 22px; border-radius: 999px;
    border: 1px solid #303B57; background: #151A26; color: #C9D3E8;
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, monospace;
  }
  .chip.accent { border-color: #2E5B45; background: #10201A; color: #6FE3A6; font-family: inherit; }
  .footer { display: flex; justify-content: space-between; align-items: center; }
  .host { font-size: 27px; color: #7F8CA8; letter-spacing: 0.01em; }
</style>
</head>
<body>
  <div class="frame">
    <div>
      <div class="rule"></div>
      <h1>AdMob CMP</h1>
      <h2>Compose Multiplatform AdMob SDK</h2>
      <div class="formats">Banner &middot; Interstitial &middot; Rewarded &middot; Rewarded Interstitial &middot; App-Open &middot; Native</div>
    </div>
    <div class="footer">
      <div class="chips">
        <span class="chip">dev.avinya.ads:admob-cmp</span>
        <span class="chip accent">Android &middot; iOS</span>
      </div>
      <div class="host">ads.avinya.dev</div>
    </div>
  </div>
</body>
</html>
```

- [ ] **Step 2: Rasterise to a 1280x640 PNG** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless --disable-gpu --hide-scrollbars \
  --window-size=1280,640 \
  --screenshot="$PWD/.github/social-preview.png" \
  "file://$PWD/.github/social-preview.html"
```

Expected: `18472 bytes written to file ...` (the exact byte count will differ; any non-zero size is fine).

- [ ] **Step 3: Verify the dimensions and file size** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
magick identify .github/social-preview.png
ls -l .github/social-preview.png | awk '{print $5 " bytes"}'
```

Expected: the `identify` line contains `PNG 1280x640 1280x640+0+0`, and the size is well under 1048576 bytes (GitHub's limit).

If the dimensions are not exactly 1280x640, Chrome applied a device pixel ratio — re-run Step 2 adding `--force-device-scale-factor=1`.

- [ ] **Step 4: Look at the image** **[HUMAN]**

```bash
open /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP/.github/social-preview.png
```

Confirm by eye:
1. No text is clipped at any edge.
2. All content sits inside roughly a 64 px margin, so a 1200x600 crop loses nothing.
3. No Google or AdMob logo appears anywhere.
4. The six format names are all legible.

- [ ] **Step 5: Commit** **[LOCAL]**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
git add .github/social-preview.html .github/social-preview.png
git commit -m "chore(meta): add the 1280x640 GitHub social preview card

Checked-in HTML source plus the rasterised PNG, so the card can be
regenerated when the version or format list changes:

  chrome --headless --window-size=1280,640 \\
    --screenshot=.github/social-preview.png .github/social-preview.html

Nominative text use of 'AdMob' only; no Google marks or logos."
git push origin docs/public-visibility-plan
```

- [ ] **Step 6: Upload the image to GitHub** **[HUMAN — web UI]**

**There is no REST API and no `gh` command for the social preview image.** A human must do this in the browser.

1. Open `https://github.com/Meet-Miyani/admob-compose-multiplatform/settings`
2. Scroll to **Social preview**
3. Click **Edit** → **Upload an image...**
4. Choose `.github/social-preview.png` from the repository working tree
5. Confirm the preview thumbnail renders and is not cropped badly

- [ ] **Step 7: Verify GitHub is serving the card** **[LOCAL]**

```bash
curl -sS https://github.com/Meet-Miyani/admob-compose-multiplatform | grep -o '<meta property="og:image" content="[^"]*"'
```

Expected: an `og:image` pointing at `https://repository-images.githubusercontent.com/...`.

If it still points at `https://opengraph.githubassets.com/...`, the upload did not take — repeat Step 6.

---

### Task 8: Cross-repo blocking fix — the `avinya.dev` canonical defect

> ## THIS TASK IS IN A DIFFERENT REPOSITORY
>
> **Not this repo.** The `avinya.dev` studio site lives in a separate repository whose local path is unknown. Step 1 locates it. **Do not create, edit, or commit any of these files inside `AdmobCMP`.**
>
> **This task blocks Plan 2.** Plan 2 makes `ads.avinya.dev` link hard to `avinya.dev/open-source/`. Until this is fixed, every one of those links feeds authority to a hostname that explicitly disavows itself.

**Files:**
- Modify: `<studio-repo>/astro.config.mjs` — the `site:` value
- Create: `<studio-repo>/functions/_middleware.ts` — the 301 from the `pages.dev` host

**Interfaces:**
- Consumes: nothing from Tasks 1-7. This task is independent and can run in parallel.
- Produces: a single canonical host for the studio site, which is the precondition Plan 2 checks before wiring cross-links.

#### Diagnosis (spec §5, verified live 2026-07-31)

Every page on the live studio site declares its canonical as the Cloudflare Pages preview host:

```
https://avinya.dev/              → <link rel="canonical" href="https://avinya.pages.dev/">
https://avinya.dev/about/        → <link rel="canonical" href="https://avinya.pages.dev/about/">
https://avinya.dev/open-source/  → <link rel="canonical" href="https://avinya.pages.dev/open-source/">
```

The same wrong host appears in:
- every `og:url`
- the JSON-LD `url` property
- the RSS feed
- **all 19 sitemap entries**
- the `Sitemap:` directive in `robots.txt`

And `https://avinya.pages.dev` returns **HTTP 200** — it is not redirected.

Google therefore sees two complete, crawlable copies of the site and is explicitly instructed to prefer the throwaway host. `avinya.dev` accumulates no authority at all.

**Root cause:** Astro derives canonical URLs, `og:url`, the sitemap, and the RSS feed from the single `site:` option in `astro.config.mjs`. One wrong value produces exactly this fan-out of symptoms. It is almost certainly still set to the Cloudflare Pages preview URL from initial deployment.

**Fix, in three parts:**
1. Correct `site:` in `astro.config.mjs` to `https://avinya.dev`.
2. Add a 301 from the `pages.dev` host to `avinya.dev`, so the duplicate stops returning 200.
3. Resubmit the corrected sitemap in Google Search Console.

- [ ] **Step 1: Locate the studio site repository** **[LOCAL]**

Try the local filesystem first:

```bash
find "$HOME" -maxdepth 6 -name "astro.config.mjs" -not -path "*/node_modules/*" 2>/dev/null
```

For each result, check whether it is the studio site:

```bash
grep -l "pages.dev\|avinya" $(find "$HOME" -maxdepth 6 -name "astro.config.mjs" -not -path "*/node_modules/*" 2>/dev/null) 2>/dev/null
```

If nothing is found locally, list the account's repositories: **[gh]**

```bash
gh repo list Meet-Miyani --limit 200 --json name,description,url --jq '.[] | [.name, .url] | @tsv'
```

Look for the studio/portfolio site (a name like `avinya`, `avinya-dev`, `avinya-tech`, `portfolio`, or `studio`). Clone it somewhere **outside** the `AdmobCMP` tree:

```bash
gh repo clone Meet-Miyani/<studio-repo-name> "$HOME/Documents/MeetMiyani/MEET/<studio-repo-name>"
```

Record the resolved path and use it for every following step:

```bash
export STUDIO_REPO="$HOME/Documents/MeetMiyani/MEET/<studio-repo-name>"
echo "$STUDIO_REPO"
```

Sanity check before proceeding:

```bash
test -f "$STUDIO_REPO/astro.config.mjs" && echo "FOUND astro.config.mjs" || echo "WRONG REPO"
test "$STUDIO_REPO" != "/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP" && echo "NOT AdmobCMP - good" || echo "STOP: this is AdmobCMP"
```

**If the repository cannot be located, stop and escalate to the maintainer.** Do not guess. The remaining steps have no safe default.

- [ ] **Step 2: Confirm the diagnosis against the live site** **[LOCAL]**

```bash
curl -sS https://avinya.dev/ | grep -oE '<link rel="canonical" href="[^"]*"'
curl -sS -o /dev/null -w 'avinya.pages.dev status: %{http_code}\n' https://avinya.pages.dev/
curl -sS https://avinya.dev/robots.txt | grep -i sitemap
curl -sS https://avinya.dev/sitemap-0.xml | grep -c "avinya.pages.dev"
```

Expected (the defect, still present):
```
<link rel="canonical" href="https://avinya.pages.dev/"
avinya.pages.dev status: 200
Sitemap: https://avinya.pages.dev/sitemap-index.xml
19
```

If the canonical already reads `https://avinya.dev/` and `avinya.pages.dev` returns 301, someone has already fixed this — verify the sitemap too, then skip to Step 7.

If the sitemap file is named differently, find it from the index:

```bash
curl -sS https://avinya.dev/sitemap-index.xml
```

- [ ] **Step 3: Read the current `site:` value** **[LOCAL]**

```bash
grep -n "site:\|base:\|trailingSlash" "$STUDIO_REPO/astro.config.mjs"
```

Expected: a line like `site: 'https://avinya.pages.dev',` — that single value is the root cause.

- [ ] **Step 4: Correct `site:`** **[LOCAL]**

In `$STUDIO_REPO/astro.config.mjs`, change the `site` option.

Old:
```js
  site: 'https://avinya.pages.dev',
```
New:
```js
  site: 'https://avinya.dev',
```

Match whatever quoting style the file already uses (`'` vs `"`) and keep the trailing comma. Do **not** add or change a `base:` option — this site is served from the domain root, and a `base` would break every internal link.

- [ ] **Step 5: Add the 301 from the `pages.dev` host** **[LOCAL]**

A Cloudflare Pages `_redirects` file cannot match on the request host, so the redirect goes in a Pages Function middleware, which runs on every request to the project regardless of which hostname served it.

Create `$STUDIO_REPO/functions/_middleware.ts`:

```ts
/**
 * Canonical-host guard.
 *
 * The Cloudflare Pages preview host (avinya.pages.dev) served HTTP 200 for
 * every page, so Google saw a complete duplicate of the site. Astro's `site:`
 * option now emits https://avinya.dev in canonicals, og:url, JSON-LD, the RSS
 * feed and the sitemap; this middleware makes the duplicate host stop
 * answering with content and 301 to the canonical host instead.
 */
const CANONICAL_HOST = "avinya.dev";

export const onRequest: PagesFunction = async (context) => {
  const url = new URL(context.request.url);

  if (url.hostname.endsWith(".pages.dev")) {
    url.hostname = CANONICAL_HOST;
    url.protocol = "https:";
    url.port = "";
    return Response.redirect(url.toString(), 301);
  }

  return context.next();
};
```

If `$STUDIO_REPO/functions/_middleware.ts` already exists, do not overwrite it — insert the `.pages.dev` check at the top of the existing `onRequest`, before any other logic.

If the project already has a `functions/` directory using `.js` rather than `.ts`, name the file `_middleware.js` and drop the `: PagesFunction` type annotation.

- [ ] **Step 6: Build locally and verify the emitted URLs** **[LOCAL]**

```bash
cd "$STUDIO_REPO"
npm ci
npm run build
grep -rn "pages.dev" dist/ || echo "CLEAN: no pages.dev anywhere in dist/"
grep -o '<link rel="canonical" href="[^"]*"' dist/index.html
grep -c "https://avinya.dev" dist/sitemap-0.xml
```

Do not write `grep ... | head; echo $?` here — `$?` after a pipeline is the *last* command's status, not grep's, so it would always report success.

Expected:
- `CLEAN: no pages.dev anywhere in dist/`
- The canonical reads `<link rel="canonical" href="https://avinya.dev/"`.
- The sitemap count is 19 (or whatever the current page count is), all on `avinya.dev`.

If `pages.dev` still appears, some template hardcodes the host rather than deriving it from `Astro.site`. Find and fix each one:

```bash
cd "$STUDIO_REPO"
grep -rn "pages.dev" src/ astro.config.mjs public/ 2>/dev/null
```

Pay particular attention to `public/robots.txt`, which Astro copies verbatim and which will **not** pick up the `site:` change automatically. Its `Sitemap:` line must be edited by hand:

Old:
```
Sitemap: https://avinya.pages.dev/sitemap-index.xml
```
New:
```
Sitemap: https://avinya.dev/sitemap-index.xml
```

- [ ] **Step 7: Commit and deploy** **[LOCAL]**

```bash
cd "$STUDIO_REPO"
git add astro.config.mjs functions/_middleware.ts
git add public/robots.txt 2>/dev/null || true
git commit -m "fix(seo): set the canonical host to avinya.dev

astro.config.mjs still pointed \`site:\` at the Cloudflare Pages preview
host, so every canonical, og:url, JSON-LD url, RSS entry and all 19
sitemap entries named avinya.pages.dev — which itself served HTTP 200.
Google saw two complete copies of the site and was told to prefer the
throwaway host.

Point \`site:\` at https://avinya.dev and add a Pages Function that 301s
any *.pages.dev request to the canonical host so the duplicate stops
answering with content."
git push
```

Wait for the Cloudflare Pages deployment to finish before Step 8.

- [ ] **Step 8: Verify against the live site** **[LOCAL]**

```bash
curl -sS https://avinya.dev/ | grep -oE '<link rel="canonical" href="[^"]*"'
curl -sS https://avinya.dev/ | grep -oE '<meta property="og:url" content="[^"]*"'
curl -sS -o /dev/null -w 'pages.dev: %{http_code} -> %{redirect_url}\n' https://avinya.pages.dev/
curl -sS -o /dev/null -w 'pages.dev/about: %{http_code} -> %{redirect_url}\n' https://avinya.pages.dev/about/
curl -sS https://avinya.dev/robots.txt | grep -i sitemap
curl -sS https://avinya.dev/sitemap-0.xml | grep -c "avinya.pages.dev"
```

Expected:
```
<link rel="canonical" href="https://avinya.dev/"
<meta property="og:url" content="https://avinya.dev/"
pages.dev: 301 -> https://avinya.dev/
pages.dev/about: 301 -> https://avinya.dev/about/
Sitemap: https://avinya.dev/sitemap-index.xml
0
```

The `0` from the last command is the key assertion: zero sitemap entries still name the preview host. The path-preserving 301 on `/about/` proves the middleware rewrites only the hostname.

- [ ] **Step 9: Resubmit the sitemap in Google Search Console** **[HUMAN — web UI]**

No API path here that is worth automating; a human does this once.

1. Open `https://search.google.com/search-console` and select the `avinya.dev` property. If no property exists for `avinya.dev`, create a **Domain** property and complete DNS verification.
2. **Sitemaps** → submit `https://avinya.dev/sitemap-index.xml`. Confirm it reports `Success` and discovers the expected page count.
3. **URL Inspection** → enter `https://avinya.dev/` → confirm "User-declared canonical" now reads `https://avinya.dev/` → **Request Indexing**.
4. Repeat the URL Inspection for `https://avinya.dev/open-source/`, the page Plan 2 will cross-link to.
5. If a separate `avinya.pages.dev` property exists, leave it alone — the 301 will de-index it on its own. Do **not** use the Removals tool; a removal is temporary and a 301 is permanent.

Reconsolidation typically takes days to a few weeks. **Plan 2 is unblocked as soon as Step 8 passes** — it does not have to wait for Google to reprocess.

---

## Rollout order and human checkpoints

| # | Task | Who | Blocking? |
|---|---|---|---|
| 1 | Pre-rename audit and `gh auth login` | Step 1 **human**, rest agent | Blocks 2-7 |
| 2 | Rename and repoint the local remote | Agent (`gh` + git) | Blocks 3-7 |
| 3 | Repoint in-repo URLs, trademark in POMs | Agent | — |
| 4 | `LICENSE` at repo root | Agent; detection confirmed after merge to `master` | — |
| 5 | Root `README.md` | Agent; Step 7 render check is **human** | — |
| 6 | Topics, homepage, Discussions, Wiki | Agent; Step 4 is **human** | — |
| 7 | Social preview | Agent generates; Steps 4 and 6 are **human** (no API for upload) | — |
| 8 | `avinya.dev` canonical fix (**different repo**) | Agent once located; Step 9 is **human** | **Blocks Plan 2** |

### Definition of done

Every one of these must hold:

```bash
# Repository identity
gh api /repos/Meet-Miyani/admob-compose-multiplatform \
  --jq '{name, homepage, has_wiki, has_discussions, topics_count: (.topics | length)}'
# -> admob-compose-multiplatform / https://ads.avinya.dev / false / true / 12

curl -sS -o /dev/null -w '%{http_code}\n' https://github.com/Meet-Miyani/Admob-CMP        # -> 301
curl -sS https://github.com/Meet-Miyani/admob-compose-multiplatform \
  | grep -c 'repository-images.githubusercontent.com'                                     # -> >= 1

# License (run after this branch merges to master)
gh api /repos/Meet-Miyani/admob-compose-multiplatform --jq '.license.spdx_id'             # -> "Apache-2.0"

# No stale slug in any live file (historical docs keep it on purpose)
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
grep -rn "Admob-CMP" --include="*.properties" --include="*.def" --include="*.yml" \
  --include="*.kts" --include="*.toml" . --exclude-dir=.git --exclude-dir=build \
  --exclude-dir=.gradle --exclude-dir=.gradle_home
echo "exit: $?"                                                                            # -> exit: 1

# README
head -1 README.md   # -> # AdMob CMP — Compose Multiplatform AdMob SDK for Android and iOS
tail -1 README.md   # -> Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.

# ABI still frozen
./gradlew :admob-cmp-core:checkKotlinAbi :admob-cmp-compose:checkKotlinAbi --no-configuration-cache
git status --porcelain admob-cmp-core/api admob-cmp-compose/api                            # -> empty

# Cross-repo canonical (Task 8)
curl -sS https://avinya.dev/ | grep -oE '<link rel="canonical" href="[^"]*"'               # -> https://avinya.dev/
curl -sS -o /dev/null -w '%{http_code}\n' https://avinya.pages.dev/                        # -> 301
curl -sS https://avinya.dev/sitemap-0.xml | grep -c "avinya.pages.dev"                     # -> 0
```

### Known forward references

- The README's `https://ads.avinya.dev/...` links and the repository `homepage` field both point at a host Plan 2 provisions. They 404 until Plan 2 ships. This is deliberate (see Global Constraints) — re-verify them at the end of Plan 2.
- Task 4's license assertion returns `null` until this branch merges to `master`; GitHub only runs license detection on the default branch.
- The POM URL corrections in Task 3 reach Maven Central only with the **next** release. Versions 1.0.0 through 1.1.0 keep the old URL permanently and depend on the Task 2 redirect — which is why the old repo name must never be reused.

---

## Self-review

Run against `docs/superpowers/specs/2026-07-31-public-visibility-design.md` after the plan was complete.

### 1. Spec coverage

Every requirement in spec §10 "Plan 1", plus the §3 and §5 obligations that land in this plan.

| Spec requirement | Source | Task | Status |
|---|---|---|---|
| Rename `Admob-CMP` → `admob-compose-multiplatform` | §10 | Task 2 | Covered |
| Replace the JetBrains-template root README | §10 | Task 5 | Covered |
| — keyword-bearing H1 | §10, §7 | Task 5 Steps 1, 3 | Covered — H1 carries the head term `compose multiplatform admob` |
| — badge row | §10 | Task 5 Step 1 | Covered — four badges; the Maven Central URL is the §9-verified one |
| — format table | §10 | Task 5 Step 1 | Covered — all six formats, with controller and composable per format |
| — 30-second quickstart | §10 | Task 5 Step 1 | Covered — runs against Google's sample ad units |
| — compatibility matrix | §10 | Task 5 Step 1 | Covered — plus a second table for the bound Google SDK versions |
| — trademark disclaimer | §10, §3 | Task 5 Steps 1, 2 | Covered verbatim, asserted by `tail -1` |
| — merged from `admob-cmp/README.md` | §10 | Task 5 | Covered — install, compatibility, quickstart and docs index all derive from it |
| Set `homepage` to `https://ads.avinya.dev` | §10 | Task 6 Step 2 | Covered |
| Move `LICENSE` to repo root | §10 | Task 4 | Covered, with a documented deviation: **copy, not move** |
| Create a 1280×640 social preview | §10 | Task 7 | Covered — dimensions, content, production method, upload path |
| Enable Discussions; disable the empty Wiki | §10 | Task 6 Step 2 | Covered |
| Update the four POM URL properties | §10 | Task 3 | Covered — and corrected upward to **eight** |
| Audit `.github/workflows/` before renaming | §10, §11 | Task 1 Step 3 | Covered — both files verified clean |
| Exactly twelve topics | §10 | Task 6 Step 1 | Covered — `PUT` replaces the whole set; count asserted separately |
| Cross-repo canonical fix | §10, §5 | Task 8 | Covered — diagnosis, three-part fix, repo-location first step |
| Old repo name never reused | §9, §11 | Global Constraints; Task 2 Step 6 | Covered |
| Trademark line on the Maven POM description | §3 | Task 3 Steps 2, 3, 4 | Covered — all five POM descriptions |
| Trademark line in the site footer | §3 | — | **Deliberately out of scope — Plan 2.** The site does not exist yet. |

**Gap found: the GitHub repository `description` field is never set.**

The plan sets `name`, `homepage`, `topics`, `has_discussions` and `has_wiki`, but never touches `description`. That is a real omission for a plan whose stated goal is GitHub SEO: GitHub's own search ranks on name + description + topics, and the description is the single line rendered under the repo title in search results and on each of the twelve topic pages this plan is about to place the repo on. Leaving it at whatever the repository was created with wastes the impression that the topics buy.

The fix is one more flag on the `PATCH` that Task 6 Step 2 already makes, and the copy already exists — it is the POM description minus the trademark sentence. Neither the spec §10 paragraph nor the task brief listed it, so it is recorded here rather than silently added:

```bash
gh api --method PATCH /repos/Meet-Miyani/admob-compose-multiplatform \
  -f description="Compose Multiplatform AdMob SDK for Android and iOS — banner, interstitial, rewarded, app-open and native ads with UMP consent, from one commonMain API."
```

Add this to Task 6 Step 2 and assert `description` alongside the other fields in Task 6 Step 3. Flagging rather than editing, because it is a scope addition the spec did not ask for.

**Two smaller coverage notes, not gaps:**

- Spec §11's risk row "Rename breaks a workflow or **external integration**" is only half-covered. Task 1 Step 3 audits workflows; nothing audits external integrations. The material ones were checked while writing this plan and are unaffected — the Maven Central namespace `dev.avinya.ads` is DNS-verified against the domain, not against GitHub, so the rename cannot touch publishing rights, and `publish.yml` triggers on `release: published`, which survives a rename. Worth stating explicitly in Task 1 rather than leaving implicit.
- Task 3 Step 7 verifies the generated POM for `:admob-cmp` only. The plugin's POM — the other four corrected URL properties — is never generated and inspected; Step 9 runs `build`, which does not necessarily produce `pom-default.xml`. The `.properties` edit is mechanical and Step 6's grep covers it, so this is a belt-and-braces omission, not a correctness hole.

### 2. Placeholder scan

Scanned for every pattern the skill names as a plan failure.

| Pattern | Result |
|---|---|
| `TBD`, `TODO`, `FIXME`, `XXX`, "fill in", "implement later" | None |
| "Add appropriate error handling" / "add validation" / "handle edge cases" | None |
| "Write tests for the above" without test code | N/A — this plan has no test-authoring steps; every verification step carries its exact command and expected output |
| "Similar to Task N" (code not repeated) | None — every before/after block is written out in full, including the two near-identical `.def` `userSetupHint` lines and the five near-identical `POM_DESCRIPTION` edits |
| Steps that describe what to do without showing how | None — all 52 steps carry either a command or literal file content |
| References to types/functions defined in no task | None. `nativePlacement` was the one instance, in the README's native-ads snippet; it is now declared inline in that snippet. |

One deliberate unresolved token remains: `<studio-repo>` / `$STUDIO_REPO` in Task 8. This is not a placeholder in the prohibited sense — the studio site's path is genuinely unknown, Task 8 Step 1 exists solely to resolve it into `$STUDIO_REPO`, and Step 1 ends with a hard stop ("escalate to the maintainer, do not guess") if it cannot be found.

### 3. Type and name consistency

Checked every identifier that crosses a task boundary.

- **New repository URL.** `https://github.com/Meet-Miyani/admob-compose-multiplatform` — identical in Global Constraints, Task 2, all of Task 3's `.properties` and `.def` replacements, the README's Discussions link, Task 6, Task 7 and the Definition of Done. No variant spelling anywhere.
- **Gradle task paths.** `checkKotlinAbi` / `updateKotlinAbi` are addressed as `:admob-cmp-core:` and `:admob-cmp-compose:` — never `:admob-cmp:`, which has no `api/` directory — consistently in Task 3 Step 8, the README's Contributing section, and the Definition of Done. `doctorIos` is always the qualified `:admob-cmp-core:doctorIos`, matching `admob-cmp/AGENTS.md` (note that `admob-cmp/README.md` uses an unqualified `./gradlew doctorIos`; the qualified form is the correct one and is what this plan uses).
- **Kotlin symbols in the README.** `rememberAdManager`, `gatherConsentAndInitialize`, `AdConfig(androidAppId, iosAppId, testMode)`, `AdPlacement(id, format, androidAdUnitId, iosAdUnitId, maxCacheSize, strictTestMode)`, `AdFormat.{Banner, Interstitial, Rewarded, RewardedInterstitial, AppOpen, Native}`, `TestAdIds.*`, `BannerAdView`, `NativeAdView`, `adLayout`, `AdModifier`, `AppOpenAdCoordinator`, `AdManagerStatus.Ready`. Each appears with one spelling and one signature throughout, and each is listed with its verification source in Task 5's preamble.
- **`$STUDIO_REPO`.** Defined in Task 8 Step 1, used in Steps 3–8. No step in Task 8 uses a bare relative path that could resolve inside `AdmobCMP`.
- **Audit grep.** Task 1 Step 2 and Task 3 Step 6 use the *same* filter set (`--exclude-dir=superpowers --exclude=handoff.md`), so "10 hits before" and "0 hits after" are measuring the same population. This was a defect on the first pass — the two greps had different filters and incompatible expected counts — and was corrected.

**One ordering inconsistency, left as-is:** the README written in Task 5 links to `/discussions`, but Discussions is enabled in Task 6. Between those two tasks the link 404s. Both land in the same branch and the same PR, so it never reaches `master` broken. Not worth reordering, but noted so a reviewer running tasks out of order is not surprised.

### 4. Human and web-UI step audit

Confirming that every step a human must perform is marked as such, per the marker vocabulary in Global Constraints.

| Task · Step | Marker | Why a human is required |
|---|---|---|
| Task 1 · Step 1 | `[HUMAN — interactive]` | `gh auth login` is browser-based OAuth. Blocks every `[gh]` step in the plan. |
| Task 5 · Step 7 | `[HUMAN — web UI]` | Visual confirmation that badges, tables and code fences render on GitHub. |
| Task 6 · Step 4 | `[HUMAN — web UI]` | First visit to Discussions prompts for starter categories; no API path. |
| Task 7 · Step 4 | `[HUMAN]` | Visual check that nothing is clipped and no Google mark appears. |
| Task 7 · Step 6 | `[HUMAN — web UI]` | **No REST API and no `gh` command exists for the social preview image.** Browser upload is the only path. |
| Task 8 · Step 9 | `[HUMAN — web UI]` | Google Search Console property selection, sitemap submission, URL inspection. |

All six are also listed in the "Rollout order and human checkpoints" table, and the two hard blockers (Task 1 Step 1 for the whole plan, Task 7 Step 6 for the social card) are called out in prose at their step.

**Two marker defects:**

- Task 7 Step 4 uses a bare `[HUMAN]`, which is not one of the four markers defined in Global Constraints. It should read `[HUMAN — visual check]`, and that marker should be added to the vocabulary table. Cosmetic, but the table claims to be exhaustive.
- Task 4 Step 4 is marked `[gh]`, and correctly so for the command it contains — but the step's actual acceptance criterion ("re-run this after the branch merges to `master`") is a human follow-up that carries no marker at all and no owner. The Definition of Done repeats the check with the same caveat. It should be marked as a deferred human verification so it is not silently dropped at merge time.

Neither defect changes what an executor does; both are recorded rather than edited, so the review's findings stay visible.
