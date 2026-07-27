# admob-cmp → Standalone Public SDK Repo — Handoff Book

> **What this is:** the complete context for extracting the `admob-cmp` module out of the
> ViewTube monorepo into its own repository and publishing it to **Maven Central** through a
> fully automated GitHub Actions pipeline. Written so a fresh Claude Code session (or you)
> opening the *new* repo can execute it without the original conversation.
>
> **Source of truth for the module's internals** already travels inside the folder:
> `admob-cmp/AGENTS.md`, `admob-cmp/CLAUDE.md`, `admob-cmp/README.md`, and `admob-cmp/docs/*`
> (`SETUP.md`, `PUBLISHING.md`, `ARCHITECTURE.md`, per-format guides). Read those first in
> the new repo. This book is only about **extraction + publishing + CI/CD**.

---

## 0. How to use this book

1. Read §1 (mission) and §2 (locked decisions) so you don't re-litigate settled choices.
2. §3 answers "will copy-paste work?" — it won't, on its own. §4–§6 are the actual work.
3. §7–§9 are the publishing/CI/CD setup and the one-time human steps (secrets, GPG,
   namespace verification) that only the human can do.
4. §10 is how to verify locally before trusting CI. §11 is the landmine list. §12 is what
   changes back in the ViewTube monorepo afterward. §13 is what still needs a human decision.

---

## 1. Mission & end state

Turn `admob-cmp` into a redistributable, public Compose Multiplatform AdMob SDK that:

- lives in its **own git repository**,
- publishes to **Maven Central** as `dev.avinya.ads:admob-cmp` (anonymous consumers, plug-and-play),
- releases **fully automatically** from CI — no hand-tagging, no local secrets, no machine dependency,
- is consumed by the ViewTube app as a normal Maven dependency (which also finally proves
  the "second app consumes the published artifact" property).

**Definition of done:**
- A tag/release cut by automation results in `dev.avinya.ads:admob-cmp:<version>` appearing on Maven Central, GPG-signed.
- A clean checkout on a fresh macOS runner builds and publishes with **only** GitHub secrets — nothing in `~/.gradle`.
- ViewTube builds against the published coordinate (or a local composite build during development).

---

## 2. Locked decisions (do NOT re-open)

| Topic | Decision | Notes |
|---|---|---|
| Distribution | **Public**, via Maven Central (Central Portal) | Not GitHub Packages — that needs auth even to *read*, which kills "plug-and-play". OSSRH is decommissioned; Central Portal only. |
| Group / namespace | **`dev.avinya.ads`** (verify the `dev.avinya` namespace via the **avinya.dev** domain) | Chosen over `io.github.meet-miyani` (generic) and `tech.avinya.ads` (needs avinya.**tech**, not owned). Verifying `dev.avinya` covers all `dev.avinya.*` artifacts. |
| Artifact | `dev.avinya.ads:admob-cmp` | Kotlin **package names stay `avinya.tech.yt.ads.*`** — Maven coordinates are independent of source packages. No source refactor. |
| Central Portal login | **GitHub identity account** | Two Portal accounts exist (Google-login + GitHub-login, same email — a known Auth0 quirk). The `io.github.meet-miyani` namespace is stranded in the Google account; we ignore it. Verify `dev.avinya` under the **GitHub-login** account. Login provider is irrelevant to CI (CI uses a User Token). |
| Signing | **In-memory GPG via CI secrets** | No keyring files, nothing in `~/.gradle`. |
| Runner | **macOS** (`macos-latest`) | Mandatory: Kotlin/Native iOS targets (`iosArm64`, `iosSimulatorArm64`) can only be compiled on an Apple host. A Linux runner physically cannot build the iOS klibs. |
| Publish plugin | vanniktech `com.vanniktech.maven.publish` `0.30.0` | Already applied. Reads POM from `gradle.properties` via `pomFromGradleProperties()`. |
| Release automation | **release-please** (Conventional Commits) → computes version, writes CHANGELOG, creates tag + GitHub Release → publish job runs | Human gate = merging the release PR. No manual version math. |
| First version | Bump to **`0.1.1`** | `0.1.0` already sits in local `~/.m2` from June; keep public releases clean. Version is ultimately **derived from the git tag** at publish time (see §8). |
| iOS packaging | **Bindings-only** (hard invariant) | Never add `staticLibraries` to the `.def` files. The consumer's Xcode app supplies GoogleMobileAds/UMP via SPM. See `admob-cmp/AGENTS.md`. |

---

## 3. "Will copy-paste the module folder work?" — No, not alone

Copy-pasting `admob-cmp/` gives you the **source**, but the module depends on monorepo
infrastructure that lives *outside* the folder and must be recreated in the new repo:

| What the module references | Where it lives now | Must exist in new repo |
|---|---|---|
| `libs.plugins.*`, `libs.versions.*`, `libs.<lib>` | root `gradle/libs.versions.toml` | a **version catalog** with the trimmed subset (§6.3) |
| `pluginManagement` + repositories | root `settings.gradle.kts` | new `settings.gradle.kts` (§6.1) |
| `gmaIosHeadersSha256`, `umpIosHeadersSha256`, `android.useAndroidX`, cinterop commonization | root + module `gradle.properties` | merged root `gradle.properties` (§6.2) |
| Gradle wrapper `9.4.1` | root `gradle/wrapper/` | copy the wrapper (§5) |

**Also do NOT copy** these (IDE/build cruft): `admob-cmp/.gradle/`, `admob-cmp/build/`,
`admob-cmp/.DS_Store`, `admob-cmp/.classpath`, `admob-cmp/.project`, `admob-cmp/.settings/`.

**Cleanest structure:** keep `admob-cmp` as a **subproject** (`:admob-cmp`) in the new repo,
not the root project. Then its own `gradle.properties` (with all the POM_* keys the
vanniktech plugin reads) stays untouched and the module's `build.gradle.kts` needs only the
small edits in §7. New repo layout in §4.

---

## 4. Target repo layout

```
admob-cmp-sdk/                     # new repo root (name it whatever)
├── .github/
│   └── workflows/
│       └── release.yml            # NEW — release-please + publish (§6.4)
├── gradle/
│   ├── libs.versions.toml         # NEW — trimmed catalog (§6.3)
│   └── wrapper/                    # COPY from monorepo (wrapper 9.4.1)
├── admob-cmp/                      # COPY the module folder (minus build cruft, §5)
│   ├── build.gradle.kts           # COPY then edit (§7)
│   ├── gradle.properties          # COPY then edit GROUP/VERSION/POM (§7)
│   ├── api/admob-cmp.klib.api      # COPY (frozen ABI)
│   ├── AGENTS.md CLAUDE.md README.md LICENSE
│   ├── docs/                       # COPY
│   ├── scripts/                    # COPY
│   └── src/                        # COPY (all source sets)
├── gradlew  gradlew.bat            # COPY from monorepo
├── settings.gradle.kts            # NEW (§6.1)
├── gradle.properties              # NEW — merged root props (§6.2)
├── release-please-config.json     # NEW (§6.4)
├── .release-please-manifest.json  # NEW (§6.4)
├── .gitignore                     # NEW (Gradle/KMP/IDE)
├── CHANGELOG.md                    # release-please will create/maintain
└── RELEASING.md                    # NEW — human runbook (§9)
```

Consider also **renaming the repo/module** to drop the `avinya.tech.yt.ads` app-coupling
eventually, but that's optional and out of scope — the package rename is a breaking change
and the ABI is frozen (§11). Leave packages as-is for the first public release.

---

## 5. Files to COPY verbatim from the monorepo

- `admob-cmp/` **entire folder** EXCEPT `build/`, `.gradle/`, `.DS_Store`, `.classpath`,
  `.project`, `.settings/`.
- `gradle/wrapper/gradle-wrapper.jar` and `gradle-wrapper.properties`
  (`distributionUrl=…gradle-9.4.1-bin.zip`).
- `gradlew`, `gradlew.bat`.
- `LICENSE` (already inside `admob-cmp/` — move to repo root too if you like).

Everything else in this book is **new** or an **edit**.

---

## 6. Files to CREATE new

### 6.1 `settings.gradle.kts`

```kotlin
rootProject.name = "admob-cmp-sdk"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("org.chromium")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("org.chromium")
            }
        }
        mavenCentral()
    }
}

include(":admob-cmp")
```

### 6.2 root `gradle.properties`

```properties
# Kotlin
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx3072M
kotlin.mpp.enableCInteropCommonization=true

# Gradle
org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8
org.gradle.caching=true
# NOTE: configuration-cache is intentionally OFF here. Publishing + cinterop tasks are not
# always CC-compatible; leaving it off avoids flaky publish runs. (Re-enable per-task later
# if you profile it as safe.)
org.gradle.configuration-cache=false

# Android
android.nonTransitiveRClass=true
android.useAndroidX=true

# admob-cmp iOS interop header archive checksums (P1-19). Pinned SHA-256 of the exact zip
# bytes from Google's unauthenticated/unversioned endpoints. Bump ONLY after downloading the
# new archive yourself and recording its real hash — never invent these to make a build pass.
gmaIosHeadersSha256=e29e331ff8e659a514ed1c4944288ef60fd8a877188de92347394ffd3ba87f70
umpIosHeadersSha256=02b6b1925be8a6cfc294478c1a6bb1dd4de70cd9e4f31cbbfb789ab4de7b2955
```

### 6.3 `gradle/libs.versions.toml` (trimmed to exactly what admob-cmp uses)

```toml
[versions]
agp = "9.2.1"
kotlin = "2.3.20"
android-compileSdk = "37"
android-minSdk = "26"

# Compose Multiplatform (these artifacts share the composeMultiplatform version)
composeMultiplatform = "1.11.1"
foundation = "1.11.1"
runtime = "1.11.1"
ui = "1.11.1"
uiToolingPreview = "1.11.1"

# AndroidX
androidx-activity = "1.13.0"
androidx-lifecycle = "2.10.0"   # highest version shared by CMP lifecycle + androidx lifecycle-process

# KotlinX
kotlinxCoroutines = "1.11.0"

# Google Mobile Ads / consent
gmaIos = "13.5.0"       # GMA iOS XCFramework (downloaded by admob-cmp at build time)
gmaUmpIos = "3.1.0"     # UMP iOS XCFramework
gmaNextGen = "1.2.1"    # Android GMA Next-Gen SDK
gmaUmp = "4.0.0"        # Android UMP (also pulled transitively by gmaNextGen)

[libraries]
runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "runtime" }
foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "foundation" }
ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "ui" }
ui-tooling = { module = "org.jetbrains.compose.ui:ui-tooling", version.ref = "uiToolingPreview" }
ui-tooling-preview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "uiToolingPreview" }
androidx-uiTooling = { module = "androidx.compose.ui:ui-tooling", version.ref = "uiToolingPreview" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "androidx-lifecycle" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
google-ads-mobile-sdk = { module = "com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk", version.ref = "gmaNextGen" }
google-user-messaging-platform = { module = "com.google.android.ump:user-messaging-platform", version.ref = "gmaUmp" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidKmpLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
mavenPublish = { id = "com.vanniktech.maven.publish", version = "0.30.0" }
```

> `mockito-core:5.15.2` is referenced with a hardcoded string inside the module's
> `androidHostTest` block, not via the catalog — it travels with `build.gradle.kts`, no
> catalog entry needed.

### 6.4 CI/CD — one workflow, two jobs, plus release-please config

The two-jobs-in-one-workflow design is deliberate: a GitHub Release created by the default
`GITHUB_TOKEN` does **not** trigger a separate `on: release` workflow (a documented Actions
anti-recursion rule). Chaining jobs with `needs` + an `if` on `release_created` sidesteps it
entirely — no PAT required.

**`.github/workflows/release.yml`:**

```yaml
name: release

on:
  push:
    branches: [main]

permissions:
  contents: write
  pull-requests: write

jobs:
  release-please:
    runs-on: ubuntu-latest
    outputs:
      release_created: ${{ steps.rp.outputs.release_created }}
      tag_name: ${{ steps.rp.outputs.tag_name }}
    steps:
      - uses: googleapis/release-please-action@v4
        id: rp
        with:
          token: ${{ secrets.GITHUB_TOKEN }}

  publish:
    needs: release-please
    if: ${{ needs.release-please.outputs.release_created == 'true' }}
    runs-on: macos-latest   # REQUIRED: iOS Kotlin/Native targets need an Apple host
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ needs.release-please.outputs.tag_name }}
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'   # AGP 9.x requires JDK 17+
      - uses: gradle/actions/setup-gradle@v4
      - name: Publish to Maven Central
        run: |
          VERSION="${{ needs.release-please.outputs.tag_name }}"
          ./gradlew :admob-cmp:publishAndReleaseToMavenCentral \
            -PVERSION_NAME="${VERSION#v}" \
            --no-configuration-cache --stacktrace
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.SIGNING_IN_MEMORY_KEY }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: ${{ secrets.SIGNING_IN_MEMORY_KEY_PASSWORD }}
```

- `publishAndReleaseToMavenCentral` uploads **and** auto-releases the Central Portal
  deployment in one task (host is already `CENTRAL_PORTAL` from `gradle.properties`), so no
  manual "publish" click in the portal.
- The published **version is the git tag** (`v0.1.1` → `0.1.1`), overriding `VERSION_NAME`.
  So the value in `gradle.properties` is only a local/dev fallback and never drifts the
  release.

**`release-please-config.json`:**

```json
{
  "$schema": "https://raw.githubusercontent.com/googleapis/release-please/main/schemas/config.json",
  "packages": {
    ".": {
      "release-type": "simple",
      "package-name": "admob-cmp",
      "changelog-path": "CHANGELOG.md"
    }
  }
}
```

**`.release-please-manifest.json`:**

```json
{ ".": "0.1.0" }
```

> `release-type: "simple"` tracks version state in `version.txt` + `CHANGELOG.md` and cuts
> the tag/release. Gradle ignores `version.txt` because the publish job derives the version
> from the tag. If you later want `gradle.properties` kept in lockstep too, add a custom
> release-please updater — but note a trailing `# x-release-please-version` comment does
> **not** work in a `.properties` file (inline `#` becomes part of the value), so that needs
> the JSON-config `extra-files` generic updater with an explicit line match, not the inline
> annotation.

---

## 7. Edits to the copied module

### 7.1 `admob-cmp/gradle.properties`

```diff
- GROUP=tech.avinya.ads
+ GROUP=dev.avinya.ads
- VERSION_NAME=0.1.0
+ VERSION_NAME=0.1.1
  POM_ARTIFACT_ID=admob-cmp
- POM_URL=https://github.com/avinya-tech/admob-cmp
+ POM_URL=https://github.com/Meet-Miyani/Admob-CMP
- POM_LICENSE_NAME=Apache License 2.0
- POM_SCM_URL=https://github.com/avinya-tech/admob-cmp
+ POM_SCM_URL=https://github.com/Meet-Miyani/Admob-CMP
- POM_SCM_CONNECTION=scm:git:https://github.com/avinya-tech/admob-cmp.git
+ POM_SCM_CONNECTION=scm:git:https://github.com/Meet-Miyani/Admob-CMP.git
- POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/avinya-tech/admob-cmp.git
+ POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/Meet-Miyani/Admob-CMP.git
- POM_DEVELOPER_ID=avinya-tech
- POM_DEVELOPER_NAME=Avinya Tech
+ POM_DEVELOPER_ID=Meet-Miyani
+ POM_DEVELOPER_NAME=Meet Miyani

# Add (enables GPG signing of release artifacts — Central rejects unsigned):
+ RELEASE_SIGNING_ENABLED=true
```

`SONATYPE_HOST=CENTRAL_PORTAL` is already present — keep it.

### 7.2 `admob-cmp/build.gradle.kts`

**Remove the GitHub Packages `publishing { repositories { … } }` block** (lines with
`name = "GitHubPackages"` and `maven.pkg.github.com/Meet-Miyani/ViewTube`). It targets the
old monorepo and isn't part of the Central flow. Central publishing is fully driven by the
vanniktech plugin + `gradle.properties`, so no explicit `publishing {}` block is needed.

Everything else in `build.gradle.kts` — the iOS framework download tasks, checksum gate,
cinterop wiring, `admobCmpTestLinkerOpts` extension, `abiValidation`, source sets —
**stays as-is**. It is self-contained once the catalog aliases (§6.3) resolve.

> Do **not** add `mavenPublishing { coordinates(...) }` — the plugin already finalizes
> coordinates from `gradle.properties` at apply-time; a second call throws
> `property 'groupId$plugin' is final`. (This is documented in the big comment already in
> the file — leave it.) `RELEASE_SIGNING_ENABLED=true` is the property-driven way to turn on
> signing without touching the DSL.

---

## 8. Publishing model recap

- **Coordinates:** `dev.avinya.ads:admob-cmp:<tag-version>`. KMP publishes the root module
  plus per-target modules (`-android`, `-iosarm64`, `-iossimulatorarm64`) automatically.
- **Signing:** in-memory, from `ORG_GRADLE_PROJECT_signingInMemoryKey` /
  `…signingInMemoryKeyPassword` env → no keyring on disk.
- **Auth:** `ORG_GRADLE_PROJECT_mavenCentralUsername` / `…Password` = a Central Portal
  **User Token** (not your login password).
- **Release:** `publishAndReleaseToMavenCentral` uploads to the Portal and auto-publishes.
- **Version:** always the git tag; `VERSION_NAME` in `gradle.properties` is a dev fallback.

---

## 9. One-time HUMAN setup (cannot be automated by the agent)

These involve accounts, domains, and secrets — do them yourself; the agent will hand you the
exact commands but must not handle your keys or tokens.

1. **Verify the namespace (GitHub-login account):**
    - Log out of the Central Portal completely, then **Sign in with GitHub** at
      central.sonatype.com. Confirm the account looks empty (if you see
      `io.github.meet-miyani`, you're in the Google account — log out, retry).
    - Register namespace **`dev.avinya`**. Add the DNS **TXT** record it gives you to the
      `avinya.dev` zone. Wait for verification to go green.
2. **Generate the User Token:** Portal → your account → **Generate User Token**. Save the
   username/password halves → these become `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD`.
3. **Create the GPG key (once, any machine):**
   ```bash
   gpg --full-generate-key            # RSA 4096, your email, set a passphrase
   gpg --list-secret-keys --keyid-format=long   # note the KEY_ID
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>   # publish PUBLIC key
   gpg --armor --export-secret-keys <KEY_ID>    # copy this ENTIRE block
   ```
    - `SIGNING_IN_MEMORY_KEY` = the full ASCII-armored **secret** key block (multi-line
      secrets are fine in GitHub).
    - `SIGNING_IN_MEMORY_KEY_PASSWORD` = the key passphrase.
    - **Back up the secret key in a password manager.** Lose it and you can't sign future
      releases with the same identity.
4. **Add the 4 repo secrets** in the new repo → Settings → Secrets and variables → Actions:
   `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY`,
   `SIGNING_IN_MEMORY_KEY_PASSWORD`.
5. **Cutting a release thereafter = zero manual steps:** merge PRs with Conventional Commit
   messages → release-please opens a "release" PR → **merge it** → tag + Release created →
   publish job signs and pushes to Central. Done.

---

## 10. Verify BEFORE trusting CI

On a mac, from the new repo root:

```bash
./gradlew :admob-cmp:compileCommonMainKotlinMetadata
./gradlew :admob-cmp:compileAndroidMain
./gradlew :admob-cmp:compileKotlinIosSimulatorArm64
./gradlew :admob-cmp:iosSimulatorArm64Test
./gradlew :admob-cmp:testAndroidHostTest
./gradlew :admob-cmp:checkKotlinAbi
```

Then a **dry-run of packaging** with no external push, no secrets:

```bash
./gradlew :admob-cmp:publishToMavenLocal -PVERSION_NAME=0.1.1 --no-configuration-cache
ls ~/.m2/repository/dev/avinya/ads/admob-cmp/0.1.1/    # POM + module metadata present?
```

If that resolves and the coordinates read `dev.avinya.ads`, the config is correct and CI
only adds the credentials + signing.

Baseline test counts to expect (from the roadmap): **iOS ~150 / Android ~150, 0 failures**
after the N/O work. Exact numbers will differ; the invariant is **0 failures, ABI clean**.

---

## 11. Landmines / invariants (from `admob-cmp/CLAUDE.md` + hard-won memory)

- **Kotlin ceiling 2.3.20** (KSP 2.3.9). Do not bump. coil ≤ 3.4.0, lifecycle ≤ 2.10.0.
- **`@JvmInline` on value classes** is required for Android — always also run
  `:admob-cmp:compileAndroidMain`, not just the iOS test loop.
- **K/N 2.3.20 miscompiles `is <data object>`** on when-typed locals (iOS only) — use `==`
  for `AdLoadState.Idle` / `AdSizePolicy.Fluid`.
- **`explicitApi()` is on + ABI is FROZEN.** After any public API change run
  `:admob-cmp:updateKotlinAbi` and commit `api/admob-cmp.klib.api`, or the build fails. The
  public ABI was deliberately frozen at the end of the roadmap's sub-project O — do not take
  further breaking changes without a migration plan.
- **Bindings-only iOS** — never add `staticLibraries` to the `.def` files. GMA/UMP symbols
  come from the consumer's Xcode SPM link at final link time.
- **iOS ObjC delegates are weak** — a strong Kotlin ref must outlive the ad.
- **All GMA/UMP calls on `Dispatchers.Main.immediate`.**
- **`GADErrorCode`: `InternalError = 11`; `1 = NoFill` and must never be retried.**
- **Interop header downloads are checksum-pinned** (`gmaIosHeadersSha256`,
  `umpIosHeadersSha256`). The build **fails closed** on mismatch. Bump only after downloading
  and hashing the real archive.
- **ATT precedes the first iOS request** — UMP consent, then `tracking.requestAuthorization()`,
  then `initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)`.
- **Ad-filtering DNS** (e.g. AdGuard `private_dns`) blocks real ad fetches with
  `ERR_CONNECTION_REFUSED` to `googleads.g.doubleclick.net` — irrelevant to CI unit tests,
  relevant only if you run the on-device sandbox.
- **`commonTest` has no multiplatform `synchronized`** — fakes invoke lock blocks directly.

---

## 12. What changes back in the ViewTube monorepo afterward

Once admob-cmp lives elsewhere, this repo's `:admob-cmp` include and `project(":admob-cmp")`
dependency break. Plan:

1. **During development:** use a Gradle **composite build** so you can co-develop without a
   release — in ViewTube `settings.gradle.kts` replace `include(":admob-cmp")` with
   `includeBuild("../admob-cmp-sdk")`. Composite builds preserve access to project
   extensions (see the next point).
2. **⚠️ Real wrinkle — the test linker-opts extension.** ViewTube's `composeApp` currently
   reads iOS test linker flags from the admob-cmp **project** via the
   `admobCmpTestLinkerOpts` extra-property (`composeApp/build.gradle.kts`, plus
   `evaluationDependsOn(":admob-cmp")` and `dependsOn(":admob-cmp:downloadGmaIos", …)`).
    - A **composite build** keeps this working (it's still a project in the build).
    - A **published Maven artifact does NOT** expose that extension. If ViewTube switches to
      the Maven coordinate, `composeApp` must instead **inline** the equivalent linker opts
      (the GoogleMobileAds/UMP `-F`/`-framework` flags + JavaScriptCore + the Swift-compat
      `-L`) into its own iOS test executables, and download the frameworks itself. Budget for
      this — it's the one non-obvious coupling. See memory note "iOS test link needs GAD
      frameworks".
3. **For releases:** after the first Central publish, switch `composeApp` to
   `implementation("dev.avinya.ads:admob-cmp:<version>")` and drop the `includeBuild`. This
   is the step that makes ViewTube the genuine "second app consuming the published artifact".

---

## 13. Open decisions for the human

1. **New repo name** (`admob-cmp`, `admob-cmp-sdk`, `compose-admob`, …) — drives
   `rootProject.name` and the POM URLs.
2. **Carry git history or clean slate?** History for just `admob-cmp/` can be extracted with
   `git filter-repo --path admob-cmp/ --path-rename admob-cmp/:` (or `git subtree split`).
   Clean-slate is faster and fine for a first public cut — the commit history is
   AdMob-internal churn, not consumer-facing.
3. **Package rename?** `avinya.tech.yt.ads` → e.g. `dev.avinya.ads` for brand consistency.
   **Recommended: NOT for v1** — it's a breaking change on a just-frozen ABI and touches
   every file. Do it as a deliberate v0.2/v1.0 migration if ever.
4. **Keep GitHub Packages too?** Decided no (Central only). Re-add the removed `publishing {}`
   block if you later want a private mirror.

---

## Appendix — quick fact sheet

| Fact | Value |
|---|---|
| Current group / artifact | `tech.avinya.ads:admob-cmp:0.1.0` → target `dev.avinya.ads:admob-cmp:0.1.1` |
| Gradle | `9.4.1` |
| AGP | `9.2.1` |
| Kotlin | `2.3.20` (KSP `2.3.9`) — ceiling, do not bump |
| Compose MP | `1.11.1` |
| compileSdk / minSdk | `37` / `26` |
| GMA iOS / UMP iOS | `13.5.0` / `3.1.0` (downloaded, checksum-pinned) |
| GMA Android Next-Gen / UMP | `1.2.1` / `4.0.0` |
| Publish plugin | vanniktech `0.30.0`, host `CENTRAL_PORTAL` |
| iOS targets | `iosArm64`, `iosSimulatorArm64` (static framework `AdMobCmp`, bindings-only) |
| Source sets | `commonMain`, `androidMain`, `iosMain`, `commonTest`, `androidHostTest`, `iosTest` |
| cinterop defs | `src/nativeInterop/cinterop/{GoogleMobileAds,UserMessagingPlatform}.def` |
| ABI dump | `api/admob-cmp.klib.api` (frozen) |
| Runner requirement | macOS (iOS K/N) + JDK 17 |
| CI secrets | `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY`, `SIGNING_IN_MEMORY_KEY_PASSWORD` |
```