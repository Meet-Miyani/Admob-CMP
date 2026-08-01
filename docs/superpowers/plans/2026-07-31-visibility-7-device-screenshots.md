# Plan 7 — Device Screenshots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture, normalise and publish a complete, machine-verified set of device screenshots of every `admob-cmp` ad format on Android and iOS into `docs-site/src/assets/screenshots/`, ready for Plan 5's landing-page showcase and Plan 3's per-format doc pages.

**Architecture:** The demo app already renders every format — `PlatformAdDemo.adCapable.kt` mounts `AdDebugScreen(catalog = AdDebugCatalog.Test)`, and `AdDebugCatalog.Test` defines placements for banner, collapsible banner, native, interstitial, rewarded, rewarded interstitial and app-open. So this plan does **no demo-app feature development**. It adds (a) a re-runnable pre-flight gate, (b) two capture scripts that normalise device chrome before grabbing the framebuffer, (c) a JSON manifest of every screenshot with its dimensions and `alt` text, (d) a Node verifier that fails if the manifest and the files on disk disagree, and (e) one Astro component that is the single integration point for Plans 3 and 5. One stale documentation note and one missing one-line `AdDebugRecorder.install` call are fixed along the way.

**Tech Stack:** Android emulator + `adb`; iOS Simulator + `xcrun simctl` + `xcodebuild`; ImageMagick 7 (`magick`) for normalisation; Node 26 (`node --test`, no dependencies) for the manifest verifier; Astro 7.1.6 `<Picture>` for AVIF/WebP delivery.

---

## Global Constraints

Every task's requirements implicitly include this section.

**Scope**

- **PLAN SCOPE IS CAPTURE, NOT DEVELOPMENT.** The only source change to the demo permitted by this plan is the single `AdDebugRecorder.install` call in Task 3. Do not add screens, placements, navigation or theming to the demo.
- **The public ABI is frozen** (`admob-cmp/CLAUDE.md` invariant 12). No task in this plan changes a public declaration, so `./gradlew :admob-cmp:updateKotlinAbi` must never be needed. If a task makes `checkKotlinAbi` fail, you have exceeded this plan's scope — stop.

**Asset location and naming**

- Screenshots land at `docs-site/src/assets/screenshots/**` — nowhere else. This is an immutable decision from the approved design.
- Filename grammar, **defined by this plan**:

  ```
  <subject>-<platform>[-tablet]-<theme>.png
  ```

  `<platform>` ∈ `android | ios`. `<theme>` ∈ `light | dark`. The `-tablet` segment is present only for tablet-class captures and absent for phone-class captures. Lowercase and `-` only; no underscores, no spaces, no version suffixes.
- **Plan 5 (landing page) and Plan 3 (format pages) MUST consume these exact filenames.** Plan 5 had not been written when this plan was authored, so this plan is the authority on naming, not Plan 5. If Plan 5 already exists and disagrees, Plan 5 changes — the assets do not get renamed.
- Source assets are **PNG only**. AVIF and WebP are produced by Astro's image pipeline at build time via `<Picture formats={['avif','webp']} />`, never committed as files.

**Rendering and normalisation**

- **No device frames.** Justified in Task 5.
- **No cropping.** Every committed PNG is the full device framebuffer. Justified in Task 5.
- Every capture is `-strip`ped of metadata and resized with `-resize '1200x>'` (shrink-only, aspect preserved, never upscaled).
- Every capture has its status bar overridden to a fixed value so re-captures are visually stable.
- Every manifest entry carries a real `width` and `height` read from the PNG header, so consumers can set explicit dimensions and avoid layout shift.

**Theme**

- The debug console chrome is **theme-fixed**. `DebugTokens` (in `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/debug/ui/DebugTokens.kt`) declares a fixed dark palette and its own doc comment states it "Deliberately does NOT inherit the host app's theme". `AdTemplates` (in `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/nativead/layout/AdTemplates.kt`) hardcodes `Color(0xFFFFFFFF)` card backgrounds. **A `-light` variant of any harness surface therefore cannot exist.** All harness captures carry the `dark` theme token and are taken with the device in dark appearance.
- Only two surfaces are rendered by the OS and genuinely respond to device appearance: the **Google UMP consent form** and the **iOS ATT prompt**. Those, and only those, are captured in both `light` and `dark`.

**Ad safety and compliance**

- Only Google test ad units may appear. `AdDebugCatalog.Test` sets `strictTestMode = true` on every placement, which throws on a production ad unit id (`admob-cmp/CLAUDE.md` invariant 10) — so a live creative cannot leak in by accident. Do not disable it.
- App IDs used are Google's public samples: `ca-app-pub-3940256099942544~3347511713` (Android, in `androidApp/src/main/AndroidManifest.xml`) and `ca-app-pub-3940256099942544~1458002511` (iOS, in `iosApp/iosApp/Info.plist`).
- **Never publish a screenshot containing a real (non-test) ad creative, a real publisher/ad-unit id, or a real advertising identifier.** Enforced by Task 14.

**Environment**

- Platform is macOS (darwin). All paths in this plan are absolute or repo-root-relative; run every command from the repository root `/Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP`.
- Verified on this machine on 2026-07-31: Xcode **26.6** (build 17F113) with the **iOS 26.5** SDK — this satisfies the Compose Multiplatform `UIViewLayoutRegion` linkage requirement. `adb` at `/opt/homebrew/bin/adb`. Emulator binary at `~/Library/Android/sdk/emulator/emulator`. The only AVD is `Pixel_10_Pro`. `magick`, `cwebp`, `avifenc` are on PATH. Node v26.0.0.
- `sdkmanager` / `avdmanager` are **not installed** (`~/Library/Android/sdk/cmdline-tools/` does not exist). Do not write steps that need them; the Android tablet variant is produced by `adb shell wm size` / `wm density` on the existing AVD instead.
- **Ad-filtering DNS blocks real test-ad fetches even when the network is up** (`admob-cmp/CLAUDE.md`). Symptom is `ERR_CONNECTION_REFUSED` to `googleads.g.doubleclick.net`. If private DNS is on, every screenshot will show an empty ad slot. Task 1 gates on this.
- Android logcat tag is `AdMobCMP`.

**Version pins (do not bump)**

| Thing | Value |
|---|---|
| Kotlin | 2.3.20 |
| AGP | 9.2.1 |
| Compose Multiplatform | 1.11.1 |
| Android `compileSdk` / `minSdk` / `targetSdk` | 37 / 26 / 36 |
| GMA iOS (SPM) | 13.7.0 |
| UMP iOS (SPM) | 3.1.0 |
| `astro` | 7.1.6 |

**Identifiers**

| Thing | Value |
|---|---|
| Android `applicationId` | `dev.avinya.admob.cmp` |
| Android launch component | `dev.avinya.admob.cmp/.MainActivity` |
| Android debug APK | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| iOS bundle id | `dev.avinya.admob.cmp.AdmobCMP` |
| iOS product name | `AdmobCMP` |
| iOS Xcode project | `iosApp/iosApp.xcodeproj`, single target `iosApp` |
| Android phone device | AVD `Pixel_10_Pro` |
| iOS phone device | Simulator `iPhone 17 Pro` (iOS 26.5) |
| iOS tablet device | Simulator `iPad Pro 11-inch (M5)` (iOS 26.5) |

**Commits**

- Commit at the end of every task. Never commit a `.png` without its manifest entry in the same commit — the verifier will fail and the tree will be broken for the next task.
- Do not commit anything under `build/`, `androidApp/build/`, or `build/ios-derived/`.

---

## File Structure

| Path | Responsibility | Task |
|---|---|---|
| `docs-site/scripts/screenshot-preflight.sh` | Re-runnable environment gate. Exits non-zero if any capture prerequisite is unmet. | 1 |
| `admob-cmp/CLAUDE.md` | Corrected "Demo app & on-device verification" section. | 2 |
| `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt` | Adds the one `AdDebugRecorder.install` call so the event console records. | 3 |
| `docs-site/src/assets/screenshots/screenshots.json` | The manifest. Contract block + one entry per PNG with dimensions, focus hint and `alt`. Single source of truth for Plans 3 and 5. | 4, 6–13 |
| `docs-site/scripts/screenshots.test.mjs` | `node --test` verifier: filename grammar, file/manifest agreement, real PNG dimensions, `alt` quality and uniqueness, required-subject coverage. | 4, 14, 15 |
| `docs-site/scripts/record-screenshot.mjs` | CLI that upserts one manifest entry, reading width/height from the PNG header. | 4 |
| `docs-site/scripts/capture-android.sh` | Normalises Android device chrome, grabs the framebuffer, strips and resizes. | 5 |
| `docs-site/scripts/capture-ios.sh` | Same for the iOS Simulator. | 5 |
| `docs-site/src/assets/screenshots/*.png` | 34 committed source assets. | 6–13 |
| `docs-site/src/components/Screenshot.astro` | The only supported way for Plans 3 and 5 to embed a screenshot. Resolves the manifest entry, emits `<Picture>` with AVIF+WebP and explicit dimensions. | 15 |

`docs-site/` does not exist yet — Plan 2 scaffolds Astro into it. Every step below uses `mkdir -p`, so this plan works whether it runs before or after Plan 2.

---

### Task 1: Pre-flight environment gate

Nothing downstream can be trusted until this passes. In particular, ad-filtering DNS produces screenshots that look like a broken SDK — empty ad slots with no error — so it is checked mechanically, not by memory.

**Files:**
- Create: `docs-site/scripts/screenshot-preflight.sh`

**Interfaces:**
- Consumes: nothing.
- Produces: `docs-site/scripts/screenshot-preflight.sh`, an executable shell script taking no arguments, exiting `0` when every capture prerequisite is met and `1` otherwise. Tasks 6–13 each begin by running it.

- [ ] **Step 1: Create the directory tree**

```bash
cd /Users/meetmiyani/Documents/MeetMiyani/MEET/AdmobCMP
mkdir -p docs-site/scripts docs-site/src/assets/screenshots docs-site/src/components
```

- [ ] **Step 2: Write the pre-flight script**

Create `docs-site/scripts/screenshot-preflight.sh`:

```bash
#!/usr/bin/env bash
# Screenshot capture pre-flight gate for Plan 7.
# Run from the repository root. Exits 1 on the first unmet prerequisite.
set -uo pipefail

FAIL=0
ok()   { printf '  \033[32mOK\033[0m   %s\n' "$1"; }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAIL=1; }
warn() { printf '  \033[33mWARN\033[0m %s\n' "$1"; }

EMULATOR="$HOME/Library/Android/sdk/emulator/emulator"

echo "== Host tools =="
for t in adb xcrun xcodebuild magick node; do
  if command -v "$t" >/dev/null 2>&1; then ok "$t: $(command -v "$t")"; else bad "$t not on PATH"; fi
done
[ -x "$EMULATOR" ] && ok "emulator: $EMULATOR" || bad "emulator missing at $EMULATOR"

echo "== Xcode / iOS SDK =="
XCODE_VER="$(xcodebuild -version 2>/dev/null | head -1 | awk '{print $2}')"
XCODE_MAJOR="${XCODE_VER%%.*}"
if [ -n "$XCODE_MAJOR" ] && [ "$XCODE_MAJOR" -ge 26 ] 2>/dev/null; then
  ok "Xcode $XCODE_VER (>= 26 required for UIViewLayoutRegion linkage)"
else
  bad "Xcode $XCODE_VER is below 26 — Compose Multiplatform will fail to link on iOS"
fi
if xcodebuild -showsdks 2>/dev/null | grep -q 'iphonesimulator26'; then
  ok "iOS 26 simulator SDK present"
else
  bad "no iphonesimulator26.x SDK — install it via Xcode > Settings > Components"
fi

echo "== Simulators =="
for d in "iPhone 17 Pro" "iPad Pro 11-inch (M5)"; do
  if xcrun simctl list devices available | grep -qF "$d ("; then ok "simulator available: $d"; else bad "simulator missing: $d"; fi
done

echo "== Android AVD =="
if "$EMULATOR" -list-avds 2>/dev/null | grep -qx 'Pixel_10_Pro'; then
  ok "AVD available: Pixel_10_Pro"
else
  bad "AVD Pixel_10_Pro not found — create it in Android Studio > Device Manager"
fi

echo "== Ad-filtering DNS (blocks every real test-ad fetch) =="
RESOLVED="$(dig +short +time=3 +tries=1 googleads.g.doubleclick.net 2>/dev/null | grep -E '^[0-9]+\.' | head -1)"
if [ -z "$RESOLVED" ]; then
  bad "googleads.g.doubleclick.net did not resolve — ad-filtering DNS is active. Turn OFF private DNS / AdGuard and re-run."
elif [ "$RESOLVED" = "0.0.0.0" ] || [ "$RESOLVED" = "127.0.0.1" ]; then
  bad "googleads.g.doubleclick.net resolves to $RESOLVED — it is being sinkholed. Turn OFF private DNS / AdGuard and re-run."
else
  ok "googleads.g.doubleclick.net -> $RESOLVED"
fi
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 https://googleads.g.doubleclick.net/ 2>/dev/null)"
if [ "$CODE" = "000" ]; then
  bad "HTTPS to googleads.g.doubleclick.net failed (ERR_CONNECTION_REFUSED class). Ads will not fill."
else
  ok "HTTPS to googleads.g.doubleclick.net -> HTTP $CODE"
fi

echo "== Attached Android device (optional at this stage) =="
if adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
  DNS_MODE="$(adb shell settings get global private_dns_mode 2>/dev/null | tr -d '\r')"
  if [ "$DNS_MODE" = "off" ] || [ "$DNS_MODE" = "null" ]; then
    ok "emulator private_dns_mode=$DNS_MODE"
  else
    bad "emulator private_dns_mode=$DNS_MODE — run: adb shell settings put global private_dns_mode off"
  fi
else
  warn "no Android device attached yet (fine before the emulator is booted)"
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "PRE-FLIGHT PASSED"
else
  echo "PRE-FLIGHT FAILED — fix the FAIL lines above before capturing."
fi
exit "$FAIL"
```

- [ ] **Step 3: Make it executable and run it**

```bash
chmod +x docs-site/scripts/screenshot-preflight.sh
./docs-site/scripts/screenshot-preflight.sh
```

Expected: every line under `Host tools`, `Xcode / iOS SDK`, `Simulators`, `Android AVD` and `Ad-filtering DNS` prints `OK`, a `WARN` for the not-yet-attached Android device is acceptable, and the last line is `PRE-FLIGHT PASSED`.

If the DNS block reports `FAIL`, stop and disable private DNS / AdGuard on the host before continuing. Do not proceed — every ad slot will screenshot empty.

- [ ] **Step 4: Prove the Android app builds**

```bash
./gradlew :androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Then confirm the artifact this plan installs actually exists:

```bash
ls -l androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Expected: one file listed, non-zero size.

- [ ] **Step 5: Ensure the iOS scheme exists**

The project ships no shared scheme (`iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/` is absent), so `xcodebuild -scheme iosApp` will fail until Xcode autocreates it.

```bash
xcodebuild -list -project iosApp/iosApp.xcodeproj 2>/dev/null | sed -n '/Schemes:/,$p'
```

If that prints nothing, open the project once so Xcode generates the scheme, wait for indexing to finish, then quit Xcode and re-run the command:

```bash
open iosApp/iosApp.xcodeproj
```

Expected on re-run: a `Schemes:` heading followed by `iosApp`.

- [ ] **Step 6: Prove the iOS app builds**

```bash
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath build/ios-derived \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Expected: `** BUILD SUCCEEDED **`. Then confirm the bundle:

```bash
ls -d build/ios-derived/Build/Products/Debug-iphonesimulator/AdmobCMP.app
```

Expected: the path is printed.

If linking fails with an undefined symbol mentioning `UIViewLayoutRegion`, the iOS SDK is below 26 — go back to Step 3 and fix the SDK before continuing.

- [ ] **Step 7: Commit**

```bash
git add docs-site/scripts/screenshot-preflight.sh
git commit -m "chore(docs): add screenshot capture pre-flight gate"
```

---

### Task 2: Correct the stale demo note in `admob-cmp/CLAUDE.md`

`admob-cmp/CLAUDE.md:107` describes a `composeApp` demo that no longer exists and asserts that five of the six formats are unreachable. That claim is what caused the original design to defer this work. It is wrong, and it must not mislead the next reader.

**Files:**
- Modify: `admob-cmp/CLAUDE.md:105-124` (the whole `## Demo app & on-device verification` section)

**Interfaces:**
- Consumes: nothing.
- Produces: nothing consumed by code. Downstream tasks rely on the corrected navigation description only as prose.

- [ ] **Step 1: Confirm the note is stale before editing it**

```bash
sed -n '105,124p' admob-cmp/CLAUDE.md
grep -rn "composeApp" --include="*.kt" --include="*.kts" . | grep -v '/build/' | head
grep -n "AdDebugScreen\|AdDebugCatalog" shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt
```

Expected: the section text mentions `composeApp` and `feed_native`; the `composeApp` grep returns **no matches** (the module does not exist — `settings.gradle.kts` includes `:androidApp`, `:desktopApp`, `:shared`, `:webApp`, `:admob-cmp`, `:admob-cmp-core`, `:admob-cmp-compose` and nothing else); and the third grep shows `AdDebugScreen(` and `AdDebugCatalog.Test`. That is the proof the note is stale.

- [ ] **Step 2: Replace the section**

In `admob-cmp/CLAUDE.md`, replace everything from the line `## Demo app & on-device verification` to the end of the file with:

```markdown
## Demo app & on-device verification

- **The demo reaches every format.** `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt`
  renders `AdDebugScreen(catalog = AdDebugCatalog.Test)`, and `AdDebugCatalog.Test`
  defines placements for banner, collapsible banner, native, interstitial,
  rewarded, rewarded interstitial and app-open — all on Google test ad units
  with `strictTestMode = true`. Hosts are `:androidApp` (`dev.avinya.admob.cmp`)
  and `iosApp/iosApp.xcodeproj`.
- The screen has three tabs. **Formats** drives live ads: two banner cards, a
  native card with Preload/Clear, and four full-screen cards each with
  Load / Show / Clear. **Layouts** renders the `AdTemplates` gallery from the
  layout DSL with no fill or network, and each card can reveal its generated
  `adLayout {}` source. **Diagnostics** shows SDK version, mediation adapters,
  consent state (`canRequestAds`, privacy-options requirement, privacy form,
  reset), App Tracking status, and Google's Ad Inspector. A draggable console
  at the bottom streams `AdManager.events`.
- The demo initializes with `ConsentMode.GatherBeforeInitialize`, so the UMP
  consent flow runs on first launch **before** any ad is requested, and on iOS
  the ATT prompt follows it via the `TrackingAuthorizationHook` registered in
  `demoTestAdConfig`. To replay either from scratch, clear app data
  (`adb shell pm clear dev.avinya.admob.cmp`) or reinstall the iOS app
  (`xcrun simctl uninstall booted dev.avinya.admob.cmp.AdmobCMP`). "Reset
  consent" on the Diagnostics tab resets UMP only.
- Test ad units come from `TestAdIds`; the manifest and `Info.plist` use
  Google's sample AdMob App IDs. Real ad fetches are blocked by ad-filtering
  DNS (e.g. AdGuard `private_dns`) even when the network is otherwise up —
  symptom is `ERR_CONNECTION_REFUSED` to `googleads.g.doubleclick.net`. Disable
  private DNS to load real test ads.
- Logcat tag is `AdMobCMP`. The native pipeline logs
  `preload requested → load started → loading completed loaded=N →
  preload finished state=Loaded → acquired token=… nativeAdFound=true`.
  Google's "AdMob native ad validator — No implementation issues found" card
  rendering on screen confirms correct native-ad binding.
```

- [ ] **Step 3: Verify no stale reference survives**

```bash
grep -n "composeApp\|feed_native\|InitializeOnlyIfAlreadyAllowed" admob-cmp/CLAUDE.md
```

Expected: exactly one match — the `InitializeOnlyIfAlreadyAllowed` occurrence inside invariant 11 (the ATT ordering rule), which is correct and must stay. No `composeApp` and no `feed_native` match.

- [ ] **Step 4: Commit**

```bash
git add admob-cmp/CLAUDE.md
git commit -m "docs: correct the stale demo-app note — AdDebugScreen reaches all six formats"
```

---

### Task 3: Make the event console record

`AdDebugScreen`'s console renders "Event recording is not installed" unless `AdDebugRecorder.install` was called, and nothing in the repo calls it (`grep -rn "AdDebugRecorder" --include="*.kt" .` finds only the library's own definitions and its own doc comments). Without this one line, `console-android-dark.png` and `console-ios-dark.png` would be screenshots of an empty-state placeholder.

**Files:**
- Modify: `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt:43-48`

**Interfaces:**
- Consumes: `AdDebugRecorder.install(manager: AdManager, scope: CoroutineScope, capacity: Int = 500)` from `dev.avinya.ads.debug` (public, already on the frozen ABI — this task adds a *call site*, not a declaration).
- Produces: a demo whose console holds a live event stream. Tasks 8 and 12 depend on it.

- [ ] **Step 1: Confirm the recorder is currently uninstalled**

```bash
grep -rn "AdDebugRecorder" --include="*.kt" . | grep -v '/build/' | grep -v 'admob-cmp-compose/src'
```

Expected: **no output** — no call site anywhere outside the library itself.

- [ ] **Step 2: Add the imports**

In `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt`, add these two imports, keeping the existing alphabetical import order:

```kotlin
import dev.avinya.ads.debug.AdDebugRecorder
import kotlinx.coroutines.yield
```

`AdDebugRecorder` sorts immediately before the existing `import dev.avinya.ads.debug.AdDebugScreen`. `kotlinx.coroutines.yield` sorts after the whole `dev.avinya` block.

- [ ] **Step 3: Install the recorder before initialization**

Replace this existing block:

```kotlin
    LaunchedEffect(manager, config, retryGeneration) {
        manager.initialize(
            config = config,
            consentMode = ConsentMode.GatherBeforeInitialize,
        )
    }
```

with:

```kotlin
    LaunchedEffect(manager, config, retryGeneration) {
        // AdManager.events is a SharedFlow with no replay, so the collector has to be
        // subscribed before initialize() emits anything. install() only *launches* the
        // collector; yield() hands the (single-threaded) main dispatcher to that
        // already-queued coroutine so it reaches its collect{} before we continue.
        AdDebugRecorder.install(manager, this)
        yield()
        manager.initialize(
            config = config,
            consentMode = ConsentMode.GatherBeforeInitialize,
        )
    }
```

- [ ] **Step 4: Compile all three targets**

```bash
./gradlew :shared:compileCommonMainKotlinMetadata :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Prove the frozen ABI is untouched**

```bash
./gradlew :admob-cmp:checkKotlinAbi :admob-cmp-core:checkKotlinAbi :admob-cmp-compose:checkKotlinAbi
```

Expected: `BUILD SUCCESSFUL` with no task reporting an ABI diff. This task adds a call site only; if this fails you edited a public declaration and must revert.

- [ ] **Step 6: Commit**

```bash
git add shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt
git commit -m "chore(demo): install AdDebugRecorder so the debug console records events"
```

---

### Task 4: Screenshot manifest and verifier

Build the contract before any pixels exist, so every later task has a failing/passing gate. The verifier is written first and must pass against an empty manifest, then keeps passing as entries are added.

**Files:**
- Create: `docs-site/src/assets/screenshots/screenshots.json`
- Create: `docs-site/scripts/screenshots.test.mjs`
- Create: `docs-site/scripts/record-screenshot.mjs`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `screenshots.json` shape: `{ "$contract": { version: number, namingGrammar: string, platforms: string[], themes: string[], focusValues: string[], requiredSubjects: string[], consumers: string[], licensing: string, themeNote: string }, "screenshots": Entry[] }` where
    `Entry = { file: string, subject: string, platform: "android"|"ios", deviceClass: "phone"|"tablet", theme: "light"|"dark", device: string, width: number, height: number, focus: "top"|"center"|"bottom", alt: string }`.
  - `record-screenshot.mjs` CLI: `node docs-site/scripts/record-screenshot.mjs --file <name.png> --subject <s> --platform <android|ios> --device-class <phone|tablet> --theme <light|dark> --device <string> --focus <top|center|bottom> --alt <string>`. Upserts by `file`, reads `width`/`height` from the PNG header, rewrites the manifest sorted by `file`.
  - `node --test docs-site/scripts/screenshots.test.mjs` is the gate every later task runs.

- [ ] **Step 1: Write the verifier (the failing test)**

Create `docs-site/scripts/screenshots.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const assetDir = join(here, '..', 'src', 'assets', 'screenshots');
const manifestPath = join(assetDir, 'screenshots.json');

function pngSize(path) {
  const buf = readFileSync(path);
  assert.ok(buf.length >= 24, `${path}: file is too short to be a PNG`);
  assert.equal(buf.readUInt32BE(0), 0x89504e47, `${path}: missing PNG signature`);
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
}

const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
const contract = manifest.$contract;
const entries = manifest.screenshots;

test('manifest declares a usable contract', () => {
  assert.equal(contract.version, 1);
  assert.equal(contract.namingGrammar, '<subject>-<platform>[-tablet]-<theme>.png');
  assert.deepEqual(contract.platforms, ['android', 'ios']);
  assert.deepEqual(contract.themes, ['light', 'dark']);
  assert.deepEqual(contract.focusValues, ['top', 'center', 'bottom']);
  assert.ok(contract.licensing.length > 80, 'licensing note must be substantive');
  assert.ok(contract.themeNote.length > 80, 'themeNote must explain the fixed-dark harness');
  assert.ok(Array.isArray(contract.requiredSubjects) && contract.requiredSubjects.length === 7);
  assert.ok(Array.isArray(contract.consumers) && contract.consumers.length >= 2);
});

test('every entry is well formed and matches its file', () => {
  for (const e of entries) {
    const label = e.file;
    assert.ok(contract.platforms.includes(e.platform), `${label}: bad platform ${e.platform}`);
    assert.ok(contract.themes.includes(e.theme), `${label}: bad theme ${e.theme}`);
    assert.ok(['phone', 'tablet'].includes(e.deviceClass), `${label}: bad deviceClass ${e.deviceClass}`);
    assert.ok(contract.focusValues.includes(e.focus), `${label}: bad focus ${e.focus}`);
    assert.match(e.subject, /^[a-z0-9]+(-[a-z0-9]+)*$/, `${label}: subject must be lowercase-kebab`);
    assert.ok(typeof e.device === 'string' && e.device.length > 5, `${label}: device must name the hardware`);

    const tablet = e.deviceClass === 'tablet' ? '-tablet' : '';
    const expected = `${e.subject}-${e.platform}${tablet}-${e.theme}.png`;
    assert.equal(e.file, expected, `${label}: filename does not match its own fields`);

    const path = join(assetDir, e.file);
    assert.ok(existsSync(path), `${label}: manifest entry has no file on disk`);
    const size = pngSize(path);
    assert.equal(e.width, size.width, `${label}: manifest width disagrees with the PNG`);
    assert.equal(e.height, size.height, `${label}: manifest height disagrees with the PNG`);
    assert.ok(size.width <= 1200, `${label}: width ${size.width} exceeds the 1200px normalisation cap`);
  }
});

test('alt text is specific, sized for a screen reader, and unique', () => {
  const seen = new Map();
  for (const e of entries) {
    assert.ok(e.alt.length >= 20, `${e.file}: alt is too short to be useful`);
    assert.ok(e.alt.length <= 125, `${e.file}: alt is ${e.alt.length} chars, over the 125 limit`);
    assert.doesNotMatch(e.alt, /^(image|screenshot|picture|photo|graphic) of/i,
      `${e.file}: alt must describe the content, not restate that it is an image`);
    assert.doesNotMatch(e.alt, /\s{2,}/, `${e.file}: alt has doubled whitespace`);
    assert.equal(e.alt.trim(), e.alt, `${e.file}: alt has leading or trailing whitespace`);
    const prev = seen.get(e.alt);
    assert.equal(prev, undefined, `${e.file}: duplicate alt text, also used by ${prev}`);
    seen.set(e.alt, e.file);
  }
});

test('no orphan PNGs and no duplicate entries', () => {
  const onDisk = readdirSync(assetDir).filter((f) => f.endsWith('.png')).sort();
  const inManifest = entries.map((e) => e.file).sort();
  assert.deepEqual(inManifest, [...new Set(inManifest)], 'manifest lists a file twice');
  assert.deepEqual(onDisk, inManifest, 'files on disk and manifest entries disagree');
});

test('manifest is sorted by filename', () => {
  const files = entries.map((e) => e.file);
  assert.deepEqual(files, [...files].sort(), 'entries must be sorted by file');
});
```

- [ ] **Step 2: Run the verifier and watch it fail**

```bash
node --test docs-site/scripts/screenshots.test.mjs
```

Expected: FAIL — the run aborts before any test with `ENOENT` on `docs-site/src/assets/screenshots/screenshots.json`, because the manifest does not exist yet.

- [ ] **Step 3: Write the empty manifest**

Create `docs-site/src/assets/screenshots/screenshots.json`:

```json
{
  "$contract": {
    "version": 1,
    "namingGrammar": "<subject>-<platform>[-tablet]-<theme>.png",
    "platforms": ["android", "ios"],
    "themes": ["light", "dark"],
    "focusValues": ["top", "center", "bottom"],
    "requiredSubjects": [
      "banner",
      "banner-collapsible",
      "native",
      "interstitial",
      "rewarded",
      "rewarded-interstitial",
      "app-open"
    ],
    "consumers": [
      "docs/superpowers/plans/2026-07-31-visibility-5-landing-page.md",
      "docs/superpowers/plans/2026-07-31-visibility-3-docs-content.md"
    ],
    "themeNote": "The AdDebugScreen harness is theme-fixed: DebugTokens declares a fixed dark palette and explicitly does not inherit the host theme, and AdTemplates hardcodes white native-card backgrounds. A light variant of a harness surface therefore cannot exist, so every harness capture carries the dark theme token. Only the Google UMP consent form and the iOS ATT prompt are OS-rendered and respond to device appearance; those alone are captured in both light and dark.",
    "licensing": "Every image shows a Google TEST ad served from Google's public test ad units (publisher ca-app-pub-3940256099942544) against Google's sample AdMob App IDs, with AdPlacement.strictTestMode enabled so a production ad unit id throws. Google supplies these demonstration creatives so developers can verify an integration, and they are self-labelled as test ads; reproducing them in this SDK's own integration documentation is illustrative, nominative use of the same kind as the AdMob trademark reference in the README. Real, non-test ad creatives must NEVER appear in published material: a live creative is a third party's copyrighted advertisement delivered under a serving licence with no redistribution right, publishing it implies an advertiser endorsement that does not exist, and pairing it with marketing copy risks reading as an ad-performance or revenue claim under AdMob policy. No screenshot may show a real publisher id, a real ad unit id, or a real advertising identifier (IDFA/GAID). This is the project's operating rule, not legal advice.",
    "focusNote": "focus tells a consumer where to anchor object-position when it crops a full-device capture to a card aspect ratio. Images are committed uncropped and unframed on purpose; see Plan 7 Task 5."
  },
  "screenshots": []
}
```

- [ ] **Step 4: Run the verifier and watch it pass**

```bash
node --test docs-site/scripts/screenshots.test.mjs
```

Expected: PASS — `# pass 5`, `# fail 0`. The contract, alt, orphan and sort tests all trivially hold over an empty set.

- [ ] **Step 5: Write the recorder CLI**

Create `docs-site/scripts/record-screenshot.mjs`:

```js
#!/usr/bin/env node
// Upserts one entry into the screenshot manifest, reading real dimensions from the PNG.
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const assetDir = join(here, '..', 'src', 'assets', 'screenshots');
const manifestPath = join(assetDir, 'screenshots.json');

const REQUIRED = ['file', 'subject', 'platform', 'device-class', 'theme', 'device', 'focus', 'alt'];

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i];
    if (!key.startsWith('--')) die(`expected a --flag, got "${key}"`);
    const value = argv[i + 1];
    if (value === undefined) die(`flag ${key} has no value`);
    out[key.slice(2)] = value;
  }
  return out;
}

function die(msg) {
  console.error(`record-screenshot: ${msg}`);
  console.error(`usage: node docs-site/scripts/record-screenshot.mjs ${REQUIRED.map((f) => `--${f} <v>`).join(' ')}`);
  process.exit(1);
}

const args = parseArgs(process.argv.slice(2));
for (const f of REQUIRED) if (!args[f]) die(`missing --${f}`);

const pngPath = join(assetDir, args.file);
if (!existsSync(pngPath)) die(`no such file: ${pngPath}`);
const buf = readFileSync(pngPath);
if (buf.length < 24 || buf.readUInt32BE(0) !== 0x89504e47) die(`${args.file} is not a PNG`);

const entry = {
  file: args.file,
  subject: args.subject,
  platform: args.platform,
  deviceClass: args['device-class'],
  theme: args.theme,
  device: args.device,
  width: buf.readUInt32BE(16),
  height: buf.readUInt32BE(20),
  focus: args.focus,
  alt: args.alt,
};

const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
manifest.screenshots = manifest.screenshots.filter((e) => e.file !== entry.file);
manifest.screenshots.push(entry);
manifest.screenshots.sort((a, b) => a.file.localeCompare(b.file));
writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`recorded ${entry.file} (${entry.width}x${entry.height})`);
```

- [ ] **Step 6: Prove the CLI rejects a missing file**

```bash
node docs-site/scripts/record-screenshot.mjs --file nope.png --subject banner \
  --platform android --device-class phone --theme dark --device "test" \
  --focus center --alt "a description long enough to pass"
```

Expected: exit code 1 and `record-screenshot: no such file: .../docs-site/src/assets/screenshots/nope.png`. Confirm the manifest was not modified:

```bash
node -e "console.log(require('./docs-site/src/assets/screenshots/screenshots.json').screenshots.length)"
```

Expected: `0`.

- [ ] **Step 7: Commit**

```bash
chmod +x docs-site/scripts/record-screenshot.mjs
git add docs-site/scripts/screenshots.test.mjs docs-site/scripts/record-screenshot.mjs \
        docs-site/src/assets/screenshots/screenshots.json
git commit -m "feat(docs): add screenshot manifest, verifier and recorder CLI"
```

---

### Task 5: Capture tooling

Two scripts that make a capture deterministic: normalise the status bar and appearance, grab the framebuffer, strip metadata, cap the width, and write straight into the asset directory.

**Design decisions this task locks in — do not revisit later:**

1. **No device frames.** A bezel adds 30–45% dead pixels that Astro then ships at every responsive breakpoint, which directly hurts the LCP of the landing page Plan 5 owns; a rendered handset also dates the asset to one hardware generation and has to be re-done at the next. CSS on the site can supply a rounded corner and a shadow for free, and can change in one line.
2. **No cropping.** Every committed PNG is the full framebuffer. Any crop geometry hard-coded into a plan rots the moment a `DebugTokens` dp value changes, and a cropped source cannot be re-framed without a re-capture. Instead the operator composes the shot *in the app* — scroll the target card into the content area, collapse the console — and the manifest's `focus` field tells the consumer where to anchor `object-position` when it crops to a card aspect ratio in CSS.
3. **Shrink-only resize to a 1200px cap.** Raw device buffers are 1206–1600px wide; 1200 is a sensible 2x-density source for a card that renders around 400–600 CSS px, and `>` guarantees nothing is ever upscaled.
4. **`-strip` on every image.** Removes any embedded metadata before the asset is published.

**Files:**
- Create: `docs-site/scripts/capture-android.sh`
- Create: `docs-site/scripts/capture-ios.sh`

**Interfaces:**
- Consumes: `docs-site/src/assets/screenshots/` from Task 1.
- Produces:
  - `docs-site/scripts/capture-android.sh <output-name.png>` — writes `docs-site/src/assets/screenshots/<output-name.png>` from the attached Android device.
  - `docs-site/scripts/capture-ios.sh <output-name.png>` — same from the booted iOS Simulator.
  - `docs-site/scripts/capture-android.sh --setup dark|light` and `--teardown` — status-bar demo mode and night mode on/off.
  - `docs-site/scripts/capture-ios.sh --setup dark|light` and `--teardown` — status-bar override and appearance.

- [ ] **Step 1: Write the Android capture script**

Create `docs-site/scripts/capture-android.sh`:

```bash
#!/usr/bin/env bash
# Android capture helper for Plan 7. Run from the repository root.
#   capture-android.sh --setup dark|light   normalise status bar + night mode
#   capture-android.sh --teardown           restore the device
#   capture-android.sh <name.png>           capture into docs-site/src/assets/screenshots/
set -euo pipefail

OUT_DIR="docs-site/src/assets/screenshots"
DEMO='am broadcast -a com.android.systemui.demo'

setup() {
  local mode="$1"
  case "$mode" in dark|light) ;; *) echo "usage: --setup dark|light" >&2; exit 1 ;; esac
  adb shell settings put global private_dns_mode off
  adb shell cmd uimode night "$([ "$mode" = dark ] && echo yes || echo no)"
  adb shell settings put global sysui_demo_allowed 1
  adb shell $DEMO -e command enter
  adb shell $DEMO -e command clock -e hhmm 0941
  adb shell $DEMO -e command battery -e level 100 -e plugged false
  adb shell $DEMO -e command network -e wifi show -e level 4
  adb shell $DEMO -e command network -e mobile show -e datatype none -e level 4
  adb shell $DEMO -e command notifications -e visible false
  echo "android: normalised (appearance=$mode, clock 09:41, battery 100%, notifications hidden)"
}

teardown() {
  adb shell $DEMO -e command exit || true
  adb shell settings put global sysui_demo_allowed 0 || true
  adb shell cmd uimode night no || true
  adb shell wm size reset || true
  adb shell wm density reset || true
  echo "android: restored"
}

capture() {
  local name="$1"
  case "$name" in *.png) ;; *) echo "output must end in .png" >&2; exit 1 ;; esac
  mkdir -p "$OUT_DIR"
  local raw; raw="$(mktemp -t admobcmp-shot).png"
  adb exec-out screencap -p > "$raw"
  magick "$raw" -strip -resize '1200x>' "$OUT_DIR/$name"
  rm -f "$raw"
  echo "wrote $OUT_DIR/$name ($(magick identify -format '%wx%h' "$OUT_DIR/$name"))"
}

case "${1:-}" in
  --setup)    setup "${2:?usage: --setup dark|light}" ;;
  --teardown) teardown ;;
  "")         echo "usage: capture-android.sh [--setup dark|light | --teardown | <name.png>]" >&2; exit 1 ;;
  *)          capture "$1" ;;
esac
```

- [ ] **Step 2: Write the iOS capture script**

Create `docs-site/scripts/capture-ios.sh`:

```bash
#!/usr/bin/env bash
# iOS Simulator capture helper for Plan 7. Run from the repository root.
#   capture-ios.sh --setup dark|light   normalise status bar + appearance on the booted sim
#   capture-ios.sh --teardown           clear the status bar override
#   capture-ios.sh <name.png>           capture into docs-site/src/assets/screenshots/
set -euo pipefail

OUT_DIR="docs-site/src/assets/screenshots"

setup() {
  local mode="$1"
  case "$mode" in dark|light) ;; *) echo "usage: --setup dark|light" >&2; exit 1 ;; esac
  xcrun simctl ui booted appearance "$mode"
  xcrun simctl status_bar booted override \
    --time "9:41" \
    --dataNetwork wifi --wifiMode active --wifiBars 3 \
    --cellularMode active --cellularBars 4 \
    --operatorName "" \
    --batteryState charged --batteryLevel 100
  echo "ios: normalised (appearance=$mode, clock 9:41, battery 100%)"
}

teardown() {
  xcrun simctl status_bar booted clear || true
  xcrun simctl ui booted appearance light || true
  echo "ios: restored"
}

capture() {
  local name="$1"
  case "$name" in *.png) ;; *) echo "output must end in .png" >&2; exit 1 ;; esac
  mkdir -p "$OUT_DIR"
  local raw; raw="$(mktemp -t admobcmp-shot).png"
  xcrun simctl io booted screenshot --type png "$raw"
  magick "$raw" -strip -resize '1200x>' "$OUT_DIR/$name"
  rm -f "$raw"
  echo "wrote $OUT_DIR/$name ($(magick identify -format '%wx%h' "$OUT_DIR/$name"))"
}

case "${1:-}" in
  --setup)    setup "${2:?usage: --setup dark|light}" ;;
  --teardown) teardown ;;
  "")         echo "usage: capture-ios.sh [--setup dark|light | --teardown | <name.png>]" >&2; exit 1 ;;
  *)          capture "$1" ;;
esac
```

- [ ] **Step 3: Make both executable and check their usage output**

```bash
chmod +x docs-site/scripts/capture-android.sh docs-site/scripts/capture-ios.sh
./docs-site/scripts/capture-android.sh; echo "exit=$?"
./docs-site/scripts/capture-ios.sh; echo "exit=$?"
```

Expected: each prints its `usage:` line and `exit=1`.

- [ ] **Step 4: Smoke-test the iOS path end to end**

```bash
xcrun simctl boot "iPhone 17 Pro" 2>/dev/null || true
open -a Simulator
xcrun simctl bootstatus booted -b
./docs-site/scripts/capture-ios.sh --setup dark
./docs-site/scripts/capture-ios.sh smoke-ios-dark.png
```

Expected: `wrote docs-site/src/assets/screenshots/smoke-ios-dark.png (1200x…)` with a width of exactly 1200.

- [ ] **Step 5: Delete the smoke artefact and confirm the verifier still passes**

```bash
rm docs-site/src/assets/screenshots/smoke-ios-dark.png
node --test docs-site/scripts/screenshots.test.mjs
```

Expected: PASS, `# fail 0`. (Had the smoke file been left behind, the orphan-PNG test would fail — that is the guard working.)

- [ ] **Step 6: Commit**

```bash
git add docs-site/scripts/capture-android.sh docs-site/scripts/capture-ios.sh
git commit -m "feat(docs): add deterministic Android and iOS screenshot capture scripts"
```

---

### Task 6: Android — consent flow and Formats tab inline ads

Six assets. The consent form is captured twice because it is one of only two OS-rendered surfaces that respond to device appearance.

**Files:**
- Create: `docs-site/src/assets/screenshots/consent-android-light.png`
- Create: `docs-site/src/assets/screenshots/consent-android-dark.png`
- Create: `docs-site/src/assets/screenshots/banner-android-dark.png`
- Create: `docs-site/src/assets/screenshots/banner-collapsible-android-dark.png`
- Create: `docs-site/src/assets/screenshots/native-android-dark.png`
- Create: `docs-site/src/assets/screenshots/native-validator-android-dark.png`
- Modify: `docs-site/src/assets/screenshots/screenshots.json`

**Interfaces:**
- Consumes: `capture-android.sh` and `record-screenshot.mjs` (Task 5, Task 4); the corrected navigation description (Task 2).
- Produces: six manifest entries whose `subject` values are `consent`, `banner`, `banner-collapsible`, `native`, `native-validator`. `banner`, `banner-collapsible` and `native` are three of the seven `requiredSubjects` Plan 5 depends on.

- [ ] **Step 1: Gate on pre-flight**

```bash
./docs-site/scripts/screenshot-preflight.sh
```

Expected: `PRE-FLIGHT PASSED`. If the DNS lines fail, stop — every ad slot will capture empty.

- [ ] **Step 2: Boot the emulator and install a clean build**

```bash
~/Library/Android/sdk/emulator/emulator -avd Pixel_10_Pro -no-snapshot-load -no-boot-anim &
adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Expected: `Success` from `adb install`.

- [ ] **Step 3: Capture the consent form in light appearance**

```bash
./docs-site/scripts/capture-android.sh --setup light
adb shell pm clear dev.avinya.admob.cmp
adb shell am start -n dev.avinya.admob.cmp/.MainActivity
```

Wait for the Google UMP consent form to appear over the "Preparing consent and Google Mobile Ads…" screen — the demo runs `ConsentMode.GatherBeforeInitialize`, so it always precedes any ad. Then:

```bash
./docs-site/scripts/capture-android.sh consent-android-light.png
```

Expected: `wrote docs-site/src/assets/screenshots/consent-android-light.png (1200x…)`.

- [ ] **Step 4: Capture the consent form in dark appearance**

```bash
./docs-site/scripts/capture-android.sh --setup dark
adb shell pm clear dev.avinya.admob.cmp
adb shell am start -n dev.avinya.admob.cmp/.MainActivity
```

Wait for the consent form again, then:

```bash
./docs-site/scripts/capture-android.sh consent-android-dark.png
```

Expected: the same confirmation line for `consent-android-dark.png`.

- [ ] **Step 5: Record both consent entries**

```bash
node docs-site/scripts/record-screenshot.mjs --file consent-android-light.png \
  --subject consent --platform android --device-class phone --theme light \
  --device "Pixel 10 Pro emulator" --focus center \
  --alt "Google UMP consent form asking about ad personalisation before the Android demo initialises ads"

node docs-site/scripts/record-screenshot.mjs --file consent-android-dark.png \
  --subject consent --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus center \
  --alt "The same Google UMP consent form on Android with the device in dark appearance"
```

Expected: two `recorded …` lines with real pixel dimensions.

- [ ] **Step 6: Consent through and reach the Formats tab**

Accept consent in the form. The demo initialises and `AdDebugScreen` opens on the **Formats** tab (tab index 0 of `Formats | Layouts | Diagnostics`). Confirm the pipeline is alive:

```bash
adb logcat -d -s AdMobCMP | tail -30
```

Expected: load/impression lines for `debug_banner` and `debug_native`. If every line is a failure with `ERR_CONNECTION_REFUSED`, ad-filtering DNS is still on — fix it and restart from Step 1.

- [ ] **Step 7: Capture the adaptive banner**

On the Formats tab, scroll so the **"Adaptive banner"** card (the first card under the `Banners` section header, subtitle "Anchored adaptive size, no auto-refresh.", id `debug_banner`) sits in the upper half of the content area, and leave the console collapsed at the bottom.

```bash
./docs-site/scripts/capture-android.sh banner-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file banner-android-dark.png \
  --subject banner --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus top \
  --alt "Adaptive anchored banner test ad filling the container width in the AdMob CMP sandbox on Android"
```

- [ ] **Step 8: Capture the collapsible banner**

Scroll to the **"Collapsible banner"** card (subtitle "Collapses to a slim anchor; SDK-managed 30s refresh.", id `debug_banner_collapsible`) and tap its expand affordance so the expanded state is on screen — that is the state worth documenting.

```bash
./docs-site/scripts/capture-android.sh banner-collapsible-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file banner-collapsible-android-dark.png \
  --subject banner-collapsible --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus top \
  --alt "Collapsible bottom-anchored banner test ad in its expanded state on Android"
```

- [ ] **Step 9: Capture the native ad**

Scroll to the **Native** card. If the pool is empty, tap **Preload** and wait for the status pill to read `loaded`. The card renders `AdTemplates.medium` through `NativeAdView`.

```bash
./docs-site/scripts/capture-android.sh native-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-android-dark.png \
  --subject native --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus top \
  --alt "Native test ad in the medium template with icon, headline, media and call to action on Android"
```

- [ ] **Step 10: Capture the native ad validator card**

Tap **Clear** then **Preload** on the Native card until the served creative is Google's validator card — the one reading "AdMob native ad validator" with "No implementation issues found". Per `admob-cmp/CLAUDE.md`, that card rendering is the proof of correct native binding, which makes it the single most useful native screenshot in the docs. Cross-check the log:

```bash
adb logcat -d -s AdMobCMP | grep -i "nativeAdFound=true" | tail -3
```

Expected: at least one `nativeAdFound=true` line. Then:

```bash
./docs-site/scripts/capture-android.sh native-validator-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-validator-android-dark.png \
  --subject native-validator --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus top \
  --alt "Google's AdMob native ad validator reporting no implementation issues on Android"
```

- [ ] **Step 11: Verify and commit**

```bash
node --test docs-site/scripts/screenshots.test.mjs
```

Expected: PASS, `# fail 0`, with six entries now present:

```bash
node -e "console.log(require('./docs-site/src/assets/screenshots/screenshots.json').screenshots.length)"
```

Expected: `6`.

```bash
git add docs-site/src/assets/screenshots
git commit -m "docs(screenshots): capture Android consent flow, banners and native ad"
```

---

### Task 7: Android — full-screen formats

Four assets: the four `fullScreenPlacements` from `AdDebugCatalog`. Each is driven from the Formats tab's `Full-screen` section, where every card has **Load**, **Show** and **Clear**; **Show** is disabled until the state pill reads `loaded`.

**Files:**
- Create: `docs-site/src/assets/screenshots/interstitial-android-dark.png`
- Create: `docs-site/src/assets/screenshots/rewarded-android-dark.png`
- Create: `docs-site/src/assets/screenshots/rewarded-interstitial-android-dark.png`
- Create: `docs-site/src/assets/screenshots/app-open-android-dark.png`
- Modify: `docs-site/src/assets/screenshots/screenshots.json`

**Interfaces:**
- Consumes: the running emulator session from Task 6, still in dark appearance with demo-mode status bar.
- Produces: manifest entries for subjects `interstitial`, `rewarded`, `rewarded-interstitial`, `app-open` — the remaining four of the seven `requiredSubjects`.

- [ ] **Step 1: Re-assert normalisation**

If the emulator was restarted since Task 6, re-run setup; it is idempotent.

```bash
./docs-site/scripts/capture-android.sh --setup dark
adb shell am start -n dev.avinya.admob.cmp/.MainActivity
```

Expected: the app returns to the Formats tab.

- [ ] **Step 2: Capture the interstitial**

Scroll to the `Full-screen` section. On the **Interstitial** card (`debug_interstitial`) tap **Load**, wait for the pill to read `loaded`, then tap **Show**. Let the creative finish rendering — including its close affordance — before capturing.

```bash
./docs-site/scripts/capture-android.sh interstitial-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file interstitial-android-dark.png \
  --subject interstitial --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus center \
  --alt "Full-screen interstitial test ad presented over the Android demo app"
```

Dismiss the ad to return to the Formats tab.

- [ ] **Step 3: Capture the rewarded ad**

On the **Rewarded** card (`debug_rewarded`) tap **Load**, wait for `loaded`, tap **Show**. Capture while the video is playing and the reward countdown is visible — that combination is what distinguishes rewarded from interstitial for a reader.

```bash
./docs-site/scripts/capture-android.sh rewarded-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file rewarded-android-dark.png \
  --subject rewarded --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus center \
  --alt "Rewarded video test ad playing with its reward countdown visible on Android"
```

Dismiss the ad.

- [ ] **Step 4: Capture the rewarded interstitial**

On the **Rewarded interstitial** card (`debug_rewarded_interstitial`) tap **Load**, wait for `loaded`, tap **Show**. Capture the **reward intro / opt-out screen** that this format shows before the creative — it is the format's defining screen and the reason it needs its own doc page.

```bash
./docs-site/scripts/capture-android.sh rewarded-interstitial-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file rewarded-interstitial-android-dark.png \
  --subject rewarded-interstitial --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus center \
  --alt "Rewarded interstitial test ad showing its reward intro and opt-out screen on Android"
```

Dismiss the ad.

- [ ] **Step 5: Capture the app-open ad**

On the **App open** card (`debug_app_open`) tap **Load** and wait for `loaded`. This format is defined by where it appears, so background and foreground the app rather than tapping Show:

```bash
adb shell input keyevent KEYCODE_HOME
adb shell am start -n dev.avinya.admob.cmp/.MainActivity
```

If the coordinator does not present it on the foreground transition, fall back to tapping **Show** on the card. Capture once the app-open creative fills the screen.

```bash
./docs-site/scripts/capture-android.sh app-open-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file app-open-android-dark.png \
  --subject app-open --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus center \
  --alt "App-open test ad shown as the Android demo app returns to the foreground"
```

- [ ] **Step 6: Verify and commit**

```bash
node --test docs-site/scripts/screenshots.test.mjs
node -e "console.log(require('./docs-site/src/assets/screenshots/screenshots.json').screenshots.length)"
```

Expected: PASS, `# fail 0`, and `10`.

```bash
git add docs-site/src/assets/screenshots
git commit -m "docs(screenshots): capture Android interstitial, rewarded, rewarded interstitial and app-open"
```

---

### Task 8: Android — Layouts tab, layout DSL source, Diagnostics and Console

Four assets. The Layouts tab is the library's uncontested differentiator (`compose multiplatform native ads` has no competing documentation), and the DSL source view is the only place the `adLayout {}` API is visible as code on a real device.

**Files:**
- Create: `docs-site/src/assets/screenshots/native-layouts-android-dark.png`
- Create: `docs-site/src/assets/screenshots/native-layout-source-android-dark.png`
- Create: `docs-site/src/assets/screenshots/diagnostics-android-dark.png`
- Create: `docs-site/src/assets/screenshots/console-android-dark.png`
- Modify: `docs-site/src/assets/screenshots/screenshots.json`

**Interfaces:**
- Consumes: the emulator session from Task 7; the `AdDebugRecorder.install` call from Task 3 (without it the console renders the "Event recording is not installed" empty state and this task's last capture is worthless).
- Produces: manifest entries for subjects `native-layouts`, `native-layout-source`, `diagnostics`, `console`.

- [ ] **Step 1: Capture the layout gallery**

Select the **Layouts** tab (middle of the three). It renders `catalog.layouts` — `AdTemplates.compact`, `AdTemplates.medium`, `AdTemplates.feedCard` — with preview data and no network. Leave the toggle on **"Sample data: default"**. Scroll so at least two complete template cards and their `valid` status pills are on screen.

```bash
./docs-site/scripts/capture-android.sh native-layouts-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-layouts-android-dark.png \
  --subject native-layouts --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus top \
  --alt "Layouts tab rendering the compact and medium native ad templates with no ad loaded"
```

- [ ] **Step 2: Capture the layout DSL source**

On the **medium** card tap **"Show layout source"**. `LayoutCard` replaces the affordance with a `DebugCodeBlock` holding the generated `adLayout {}` source from `layout.toSpecSource()`. Scroll so the code block dominates the frame.

```bash
./docs-site/scripts/capture-android.sh native-layout-source-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-layout-source-android-dark.png \
  --subject native-layout-source --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus top \
  --alt "Generated adLayout DSL source for the medium native template revealed in the Layouts tab"
```

- [ ] **Step 3: Capture Diagnostics**

Select the **Diagnostics** tab. Scroll so the **SDK** card (version + status pill), the **Adapters** card and the **Consent** card (status, `can request ads`, `privacy options`, and the Privacy form / Reset consent buttons) are all visible in one frame — that triple is what the consent and mediation doc pages need to illustrate.

```bash
./docs-site/scripts/capture-android.sh diagnostics-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file diagnostics-android-dark.png \
  --subject diagnostics --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus top \
  --alt "Diagnostics tab showing SDK version, mediation adapters and live consent state on Android"
```

- [ ] **Step 4: Capture the event console**

Return to the **Formats** tab and drive traffic so the buffer is full: tap **Preload** on Native, then **Load** on Interstitial and Rewarded. Drag the console handle up to its `Half` anchor (or tap the handle to toggle it open).

Confirm the recorder is actually installed before capturing — the panel must show a list of events, **not** the text "Event recording is not installed". If it shows that text, Task 3 was not applied to the build now on the device; rebuild and reinstall.

```bash
./docs-site/scripts/capture-android.sh console-android-dark.png
node docs-site/scripts/record-screenshot.mjs --file console-android-dark.png \
  --subject console --platform android --device-class phone --theme dark \
  --device "Pixel 10 Pro emulator" --focus bottom \
  --alt "Draggable event console streaming AdMob CMP load, impression and paid events on Android"
```

- [ ] **Step 5: Verify and commit**

```bash
node --test docs-site/scripts/screenshots.test.mjs
node -e "console.log(require('./docs-site/src/assets/screenshots/screenshots.json').screenshots.length)"
```

Expected: PASS, `# fail 0`, and `14`.

```bash
git add docs-site/src/assets/screenshots
git commit -m "docs(screenshots): capture Android layouts, layout DSL source, diagnostics and console"
```

---

### Task 9: Android — tablet variants

Two assets. Only banner and native get a tablet variant: banner because adaptive width resolution is the whole point of `BannerGeometry`, and native because the layout DSL reflows. A tablet interstitial or rewarded shows the same full-screen creative and would be a redundant asset to ship.

`sdkmanager` and `avdmanager` are not installed on this machine, so no tablet AVD can be created. Reconfiguring the existing `Pixel_10_Pro` display gives a genuine tablet-width layout with no SDK install: `1600x2560` at density `320` is `800x1280` dp, a classic 10-inch tablet.

**Files:**
- Create: `docs-site/src/assets/screenshots/banner-android-tablet-dark.png`
- Create: `docs-site/src/assets/screenshots/native-android-tablet-dark.png`
- Modify: `docs-site/src/assets/screenshots/screenshots.json`

**Interfaces:**
- Consumes: the emulator session from Task 8.
- Produces: manifest entries with `deviceClass: "tablet"`, exercising the `-tablet` segment of the naming grammar.

- [ ] **Step 1: Switch the emulator to a tablet display**

```bash
adb shell wm size 1600x2560
adb shell wm density 320
adb shell am force-stop dev.avinya.admob.cmp
adb shell am start -n dev.avinya.admob.cmp/.MainActivity
adb shell wm size
adb shell wm density
```

Expected: `Physical size: 1440x3120` (or the AVD's native value) plus `Override size: 1600x2560`, and `Override density: 320`.

- [ ] **Step 2: Capture the tablet banner**

Consent through if prompted, then on the Formats tab scroll to the **"Adaptive banner"** card. The banner should now resolve to a visibly wider adaptive size than the phone capture.

```bash
./docs-site/scripts/capture-android.sh banner-android-tablet-dark.png
node docs-site/scripts/record-screenshot.mjs --file banner-android-tablet-dark.png \
  --subject banner --platform android --device-class tablet --theme dark \
  --device "Pixel 10 Pro emulator at 800x1280dp tablet metrics" --focus top \
  --alt "Adaptive banner resolving to a wider size at 800dp tablet width on Android"
```

- [ ] **Step 3: Capture the tablet native ad**

Scroll to the **Native** card, tap **Preload** if the pill is not `loaded`, and capture once the medium template has reflowed to tablet width.

```bash
./docs-site/scripts/capture-android.sh native-android-tablet-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-android-tablet-dark.png \
  --subject native --platform android --device-class tablet --theme dark \
  --device "Pixel 10 Pro emulator at 800x1280dp tablet metrics" --focus top \
  --alt "Native medium template reflowing across the full width of an 800dp tablet layout on Android"
```

- [ ] **Step 4: Restore the emulator and finish the Android session**

```bash
./docs-site/scripts/capture-android.sh --teardown
adb shell wm size
adb shell wm density
```

Expected: no `Override size` or `Override density` line remains.

- [ ] **Step 5: Verify and commit**

```bash
node --test docs-site/scripts/screenshots.test.mjs
node -e "const m=require('./docs-site/src/assets/screenshots/screenshots.json');console.log(m.screenshots.length, m.screenshots.filter(e=>e.platform==='android').length)"
```

Expected: PASS, `# fail 0`, and `16 16`.

```bash
git add docs-site/src/assets/screenshots
git commit -m "docs(screenshots): capture Android tablet banner and native variants"
```

---

### Task 10: iOS — ATT prompt, consent flow and Formats tab inline ads

Eight assets. iOS is the only platform with an ATT prompt, and its ordering — UMP consent, **then** ATT, **then** `initialize` — is invariant 11 in `admob-cmp/CLAUDE.md` and the single highest-value correctness trap in the library. The demo wires it through `TrackingAuthorizationHook` on `AdInitializationPhase.BeforeMobileAdsInitialize`, so simply launching the app produces the correct sequence to photograph.

**Files:**
- Create: `docs-site/src/assets/screenshots/consent-ios-light.png`
- Create: `docs-site/src/assets/screenshots/consent-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/att-ios-light.png`
- Create: `docs-site/src/assets/screenshots/att-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/banner-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/banner-collapsible-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/native-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/native-validator-ios-dark.png`
- Modify: `docs-site/src/assets/screenshots/screenshots.json`

**Interfaces:**
- Consumes: `capture-ios.sh` (Task 5), the verified Xcode 26.6 / iOS 26.5 toolchain (Task 1).
- Produces: manifest entries for subjects `consent`, `att`, `banner`, `banner-collapsible`, `native`, `native-validator` on `platform: "ios"`. `att` is iOS-only and has no Android counterpart by design.

- [ ] **Step 1: Gate on pre-flight and boot the simulator**

```bash
./docs-site/scripts/screenshot-preflight.sh
xcrun simctl boot "iPhone 17 Pro" 2>/dev/null || true
open -a Simulator
xcrun simctl bootstatus booted -b
```

Expected: `PRE-FLIGHT PASSED`, then the simulator reaching a booted state.

- [ ] **Step 2: Build and install the iOS app**

```bash
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath build/ios-derived \
  CODE_SIGNING_ALLOWED=NO \
  build
xcrun simctl install booted build/ios-derived/Build/Products/Debug-iphonesimulator/AdmobCMP.app
```

Expected: `** BUILD SUCCEEDED **` then a silent successful install.

- [ ] **Step 3: Capture the consent form in light appearance**

Uninstalling is the reliable way to reset both UMP consent and ATT status on the simulator — `simctl privacy` has no tracking service.

```bash
./docs-site/scripts/capture-ios.sh --setup light
xcrun simctl uninstall booted dev.avinya.admob.cmp.AdmobCMP
xcrun simctl install booted build/ios-derived/Build/Products/Debug-iphonesimulator/AdmobCMP.app
xcrun simctl launch booted dev.avinya.admob.cmp.AdmobCMP
```

Wait for the UMP consent form, then:

```bash
./docs-site/scripts/capture-ios.sh consent-ios-light.png
```

- [ ] **Step 4: Capture the ATT prompt in light appearance**

Accept the consent form. `TrackingAuthorizationHook` fires at `BeforeMobileAdsInitialize`, so the system "Allow … to track your activity across other companies' apps and websites?" alert appears next. Capture it before dismissing:

```bash
./docs-site/scripts/capture-ios.sh att-ios-light.png
```

- [ ] **Step 5: Capture both surfaces in dark appearance**

```bash
./docs-site/scripts/capture-ios.sh --setup dark
xcrun simctl uninstall booted dev.avinya.admob.cmp.AdmobCMP
xcrun simctl install booted build/ios-derived/Build/Products/Debug-iphonesimulator/AdmobCMP.app
xcrun simctl launch booted dev.avinya.admob.cmp.AdmobCMP
```

Capture the consent form, accept it, then capture the ATT prompt:

```bash
./docs-site/scripts/capture-ios.sh consent-ios-dark.png
# accept consent, wait for the ATT alert
./docs-site/scripts/capture-ios.sh att-ios-dark.png
```

- [ ] **Step 6: Record the four consent and ATT entries**

```bash
node docs-site/scripts/record-screenshot.mjs --file consent-ios-light.png \
  --subject consent --platform ios --device-class phone --theme light \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus center \
  --alt "Google UMP consent form presented on iPhone before Google Mobile Ads is initialised"

node docs-site/scripts/record-screenshot.mjs --file consent-ios-dark.png \
  --subject consent --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus center \
  --alt "The same Google UMP consent form on iPhone with the device in dark appearance"

node docs-site/scripts/record-screenshot.mjs --file att-ios-light.png \
  --subject att --platform ios --device-class phone --theme light \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus center \
  --alt "iOS App Tracking Transparency prompt shown after UMP consent and before Mobile Ads initialises"

node docs-site/scripts/record-screenshot.mjs --file att-ios-dark.png \
  --subject att --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus center \
  --alt "The iOS App Tracking Transparency prompt with the device in dark appearance"
```

Expected: four `recorded …` lines.

- [ ] **Step 7: Reach the Formats tab and confirm fill**

Answer the ATT prompt (either answer is fine — test ads fill regardless). `AdDebugScreen` opens on the **Formats** tab. Confirm the SDK is live:

```bash
xcrun simctl spawn booted log show --last 2m --predicate 'eventMessage CONTAINS "AdMobCMP"' --style compact | tail -30
```

Expected: load/impression lines for `debug_banner` and `debug_native`. If every entry is a connection failure, ad-filtering DNS is still active on the host — fix it and restart from Step 1.

- [ ] **Step 8: Capture the adaptive banner**

Scroll the **"Adaptive banner"** card (`debug_banner`) into the upper content area with the console collapsed.

```bash
./docs-site/scripts/capture-ios.sh banner-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file banner-ios-dark.png \
  --subject banner --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus top \
  --alt "Adaptive anchored banner test ad filling the container width in the AdMob CMP sandbox on iPhone"
```

- [ ] **Step 9: Capture the collapsible banner**

Scroll to **"Collapsible banner"** (`debug_banner_collapsible`) and expand it.

```bash
./docs-site/scripts/capture-ios.sh banner-collapsible-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file banner-collapsible-ios-dark.png \
  --subject banner-collapsible --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus top \
  --alt "Collapsible bottom-anchored banner test ad in its expanded state on iPhone"
```

- [ ] **Step 10: Capture the native ad and the validator card**

Scroll to the **Native** card, tap **Preload** if needed, and capture once the medium template has rendered:

```bash
./docs-site/scripts/capture-ios.sh native-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-ios-dark.png \
  --subject native --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus top \
  --alt "Native test ad in the medium template with icon, headline, media and call to action on iPhone"
```

Then cycle **Clear** / **Preload** until Google's validator creative is served and capture it:

```bash
./docs-site/scripts/capture-ios.sh native-validator-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-validator-ios-dark.png \
  --subject native-validator --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus top \
  --alt "Google's AdMob native ad validator reporting no implementation issues on iPhone"
```

- [ ] **Step 11: Verify and commit**

```bash
node --test docs-site/scripts/screenshots.test.mjs
node -e "console.log(require('./docs-site/src/assets/screenshots/screenshots.json').screenshots.length)"
```

Expected: PASS, `# fail 0`, and `24`.

```bash
git add docs-site/src/assets/screenshots
git commit -m "docs(screenshots): capture iOS ATT, consent, banners and native ad"
```

---

### Task 11: iOS — full-screen formats

Four assets, mirroring Task 7 on iOS.

**Files:**
- Create: `docs-site/src/assets/screenshots/interstitial-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/rewarded-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/rewarded-interstitial-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/app-open-ios-dark.png`
- Modify: `docs-site/src/assets/screenshots/screenshots.json`

**Interfaces:**
- Consumes: the simulator session from Task 10.
- Produces: manifest entries for subjects `interstitial`, `rewarded`, `rewarded-interstitial`, `app-open` on `platform: "ios"`. With these, all seven `requiredSubjects` exist on both platforms.

- [ ] **Step 1: Re-assert normalisation**

```bash
./docs-site/scripts/capture-ios.sh --setup dark
xcrun simctl launch booted dev.avinya.admob.cmp.AdmobCMP
```

Expected: the app is foreground on the Formats tab.

- [ ] **Step 2: Capture the interstitial**

In the `Full-screen` section, on the **Interstitial** card tap **Load**, wait for `loaded`, tap **Show**, and capture once the creative and its close affordance are rendered.

```bash
./docs-site/scripts/capture-ios.sh interstitial-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file interstitial-ios-dark.png \
  --subject interstitial --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus center \
  --alt "Full-screen interstitial test ad presented over the iOS demo app"
```

Dismiss the ad.

- [ ] **Step 3: Capture the rewarded ad**

On the **Rewarded** card tap **Load**, wait for `loaded`, tap **Show**, and capture with the video playing and the reward countdown visible.

```bash
./docs-site/scripts/capture-ios.sh rewarded-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file rewarded-ios-dark.png \
  --subject rewarded --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus center \
  --alt "Rewarded video test ad playing with its reward countdown visible on iPhone"
```

Dismiss the ad.

- [ ] **Step 4: Capture the rewarded interstitial**

On the **Rewarded interstitial** card tap **Load**, wait for `loaded`, tap **Show**, and capture the reward intro / opt-out screen.

```bash
./docs-site/scripts/capture-ios.sh rewarded-interstitial-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file rewarded-interstitial-ios-dark.png \
  --subject rewarded-interstitial --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus center \
  --alt "Rewarded interstitial test ad showing its reward intro and opt-out screen on iPhone"
```

Dismiss the ad.

- [ ] **Step 5: Capture the app-open ad**

On the **App open** card tap **Load** and wait for `loaded`, then background and foreground the app:

```bash
xcrun simctl launch booted com.apple.springboard 2>/dev/null || true
xcrun simctl launch booted dev.avinya.admob.cmp.AdmobCMP
```

If the coordinator does not present on the foreground transition, tap **Show** on the card instead. Capture once the app-open creative fills the screen.

```bash
./docs-site/scripts/capture-ios.sh app-open-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file app-open-ios-dark.png \
  --subject app-open --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus center \
  --alt "App-open test ad shown as the iOS demo app returns to the foreground"
```

- [ ] **Step 6: Verify all seven required subjects now exist on both platforms**

```bash
node -e "
const m = require('./docs-site/src/assets/screenshots/screenshots.json');
for (const s of m.\$contract.requiredSubjects)
  for (const p of ['android','ios'])
    if (!m.screenshots.some(e => e.subject===s && e.platform===p && e.deviceClass==='phone'))
      { console.error('MISSING', s, p); process.exit(1); }
console.log('all required subjects present on both platforms');
"
node --test docs-site/scripts/screenshots.test.mjs
```

Expected: `all required subjects present on both platforms`, then PASS with `# fail 0` and 28 entries.

- [ ] **Step 7: Commit**

```bash
git add docs-site/src/assets/screenshots
git commit -m "docs(screenshots): capture iOS interstitial, rewarded, rewarded interstitial and app-open"
```

---

### Task 12: iOS — Layouts, layout DSL source, Diagnostics and Console

Four assets, mirroring Task 8 on iOS. The iOS Diagnostics capture is materially different from Android's: its **App tracking** card shows a real ATT status, which is exactly what the `/privacy/app-tracking-transparency/` page needs.

**Files:**
- Create: `docs-site/src/assets/screenshots/native-layouts-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/native-layout-source-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/diagnostics-ios-dark.png`
- Create: `docs-site/src/assets/screenshots/console-ios-dark.png`
- Modify: `docs-site/src/assets/screenshots/screenshots.json`

**Interfaces:**
- Consumes: the simulator session from Task 11; the `AdDebugRecorder.install` call from Task 3.
- Produces: manifest entries for subjects `native-layouts`, `native-layout-source`, `diagnostics`, `console` on `platform: "ios"`.

- [ ] **Step 1: Capture the layout gallery**

Select the **Layouts** tab, leave the toggle on **"Sample data: default"**, and scroll so at least two complete template cards and their `valid` pills are visible.

```bash
./docs-site/scripts/capture-ios.sh native-layouts-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-layouts-ios-dark.png \
  --subject native-layouts --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus top \
  --alt "Layouts tab rendering the compact and medium native ad templates on iPhone with no ad loaded"
```

- [ ] **Step 2: Capture the layout DSL source**

On the **medium** card tap **"Show layout source"** and scroll so the generated `adLayout {}` code block dominates the frame.

```bash
./docs-site/scripts/capture-ios.sh native-layout-source-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-layout-source-ios-dark.png \
  --subject native-layout-source --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus top \
  --alt "Generated adLayout DSL source for the medium native template revealed on iPhone"
```

- [ ] **Step 3: Capture Diagnostics with the ATT card**

Select the **Diagnostics** tab and scroll so the **Consent** card and the **App tracking** card (status plus the "Request authorization" button) are both in frame.

```bash
./docs-site/scripts/capture-ios.sh diagnostics-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file diagnostics-ios-dark.png \
  --subject diagnostics --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus top \
  --alt "Diagnostics tab on iPhone showing live consent state next to the App Tracking authorization status"
```

- [ ] **Step 4: Capture the event console**

Return to **Formats**, tap **Preload** on Native and **Load** on Interstitial and Rewarded, then drag the console handle to its `Half` anchor. Confirm the panel lists events rather than reading "Event recording is not installed".

```bash
./docs-site/scripts/capture-ios.sh console-ios-dark.png
node docs-site/scripts/record-screenshot.mjs --file console-ios-dark.png \
  --subject console --platform ios --device-class phone --theme dark \
  --device "iPhone 17 Pro simulator, iOS 26.5" --focus bottom \
  --alt "Draggable event console streaming AdMob CMP load, impression and paid events on iPhone"
```

- [ ] **Step 5: Verify and commit**

```bash
node --test docs-site/scripts/screenshots.test.mjs
node -e "console.log(require('./docs-site/src/assets/screenshots/screenshots.json').screenshots.length)"
```

Expected: PASS, `# fail 0`, and `32`.

```bash
git add docs-site/src/assets/screenshots
git commit -m "docs(screenshots): capture iOS layouts, layout DSL source, diagnostics and console"
```

---

### Task 13: iOS — tablet variants

The last two assets. Same rationale as Task 9: banner for adaptive width, native for DSL reflow.

**Files:**
- Create: `docs-site/src/assets/screenshots/banner-ios-tablet-dark.png`
- Create: `docs-site/src/assets/screenshots/native-ios-tablet-dark.png`
- Modify: `docs-site/src/assets/screenshots/screenshots.json`

**Interfaces:**
- Consumes: `capture-ios.sh`; the `iPad Pro 11-inch (M5)` simulator verified in Task 1.
- Produces: the final two manifest entries, bringing the set to 34.

- [ ] **Step 1: Boot the iPad simulator and build for it**

`"iPad Pro 11-inch (M5)"` exists only under the iOS 26.5 runtime on this machine, so the name is unambiguous to `simctl`.

```bash
xcrun simctl shutdown booted 2>/dev/null || true
xcrun simctl boot "iPad Pro 11-inch (M5)"
open -a Simulator
xcrun simctl bootstatus booted -b
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M5)' \
  -derivedDataPath build/ios-derived \
  CODE_SIGNING_ALLOWED=NO \
  build
xcrun simctl install booted build/ios-derived/Build/Products/Debug-iphonesimulator/AdmobCMP.app
```

Expected: `** BUILD SUCCEEDED **` and a silent install.

- [ ] **Step 2: Launch and get through consent and ATT**

```bash
./docs-site/scripts/capture-ios.sh --setup dark
xcrun simctl launch booted dev.avinya.admob.cmp.AdmobCMP
```

Accept the UMP consent form and answer the ATT prompt to reach the Formats tab.

- [ ] **Step 3: Capture the tablet banner**

Scroll the **"Adaptive banner"** card into the upper content area. The adaptive size should resolve wider than on iPhone.

```bash
./docs-site/scripts/capture-ios.sh banner-ios-tablet-dark.png
node docs-site/scripts/record-screenshot.mjs --file banner-ios-tablet-dark.png \
  --subject banner --platform ios --device-class tablet --theme dark \
  --device "iPad Pro 11-inch (M5) simulator, iOS 26.5" --focus top \
  --alt "Adaptive banner resolving to a wider size across an iPad Pro 11-inch layout"
```

- [ ] **Step 4: Capture the tablet native ad**

Scroll to the **Native** card, tap **Preload** if the pill is not `loaded`, and capture once the medium template has reflowed.

```bash
./docs-site/scripts/capture-ios.sh native-ios-tablet-dark.png
node docs-site/scripts/record-screenshot.mjs --file native-ios-tablet-dark.png \
  --subject native --platform ios --device-class tablet --theme dark \
  --device "iPad Pro 11-inch (M5) simulator, iOS 26.5" --focus top \
  --alt "Native medium template reflowing across the full width of an iPad Pro 11-inch layout"
```

- [ ] **Step 5: Tear down the simulator session**

```bash
./docs-site/scripts/capture-ios.sh --teardown
xcrun simctl shutdown booted
```

- [ ] **Step 6: Verify the complete set and commit**

```bash
node --test docs-site/scripts/screenshots.test.mjs
node -e "
const m = require('./docs-site/src/assets/screenshots/screenshots.json');
console.log('total', m.screenshots.length);
console.log('android', m.screenshots.filter(e=>e.platform==='android').length);
console.log('ios', m.screenshots.filter(e=>e.platform==='ios').length);
console.log('tablet', m.screenshots.filter(e=>e.deviceClass==='tablet').length);
console.log('light', m.screenshots.filter(e=>e.theme==='light').length);
"
```

Expected: PASS with `# fail 0`, then `total 34`, `android 16`, `ios 18`, `tablet 4`, `light 3`.

(iOS carries two more than Android because `att-ios-light.png` and `att-ios-dark.png` have no Android counterpart. The three `light` entries are `consent-android-light`, `consent-ios-light` and `att-ios-light` — the only OS-rendered surfaces, per the Global Constraints theme rule.)

```bash
git add docs-site/src/assets/screenshots
git commit -m "docs(screenshots): capture iPad banner and native variants — full set complete"
```

---

### Task 14: Licensing and redaction audit

Thirty-four images are about to be published on a public marketing site. This task proves mechanically that none of them can contain a production identifier, records the compliance position in the manifest so it travels with the assets, and adds a regression test so a future contributor cannot quietly add a screenshot of a live creative.

**The position, stated plainly:**

- **What may be published:** these captures. Every ad shown is a Google **test** ad, served from Google's public test publisher `ca-app-pub-3940256099942544` against Google's sample AdMob App IDs, with `AdPlacement.strictTestMode = true` on every placement in `AdDebugCatalog.Test` — which throws on a production ad unit id, so a live creative cannot appear by accident. Google supplies these creatives specifically so developers can verify an integration and they are self-labelled as test ads. Reproducing them in this SDK's own integration documentation is illustrative, nominative use of the same kind as the AdMob trademark reference already agreed in the design's trademark posture. Every page that embeds one still carries the disclaimer: *Not affiliated with or endorsed by Google. AdMob and Google Mobile Ads are trademarks of Google LLC.*
- **What may never be published:** a screenshot containing a real, non-test ad creative. Three independent reasons: (1) a live creative is a third party's copyrighted advertisement delivered to the device under a serving licence that grants no redistribution right; (2) publishing an identifiable advertiser's creative in marketing material implies an endorsement or partnership that does not exist; (3) pairing a real creative with marketing copy can read as an ad-performance or revenue claim, which AdMob's policies prohibit. Equally excluded: any real publisher id, real ad unit id, or real advertising identifier (IDFA/GAID), all of which the Diagnostics tab and the event console are capable of surfacing.
- This is the project's operating rule, not legal advice.

**Files:**
- Modify: `docs-site/scripts/screenshots.test.mjs` (append one test)
- Modify: `docs-site/src/assets/screenshots/screenshots.json` (only if the audit finds a problem)

**Interfaces:**
- Consumes: the complete 34-asset set from Task 13; the `$contract.licensing` string written in Task 4.
- Produces: a `no production ad identifiers anywhere in the captured surface` test in `screenshots.test.mjs`, run by every subsequent task and by CI.

- [ ] **Step 1: Prove no production ad unit id exists anywhere in the demo surface**

Any `ca-app-pub-` string that is not Google's test publisher would be a production identifier that could have reached a screenshot.

```bash
grep -rn "ca-app-pub-" \
  --include="*.kt" --include="*.kts" --include="*.xml" --include="*.plist" --include="*.json" \
  androidApp iosApp shared admob-cmp admob-cmp-core admob-cmp-compose docs-site \
  | grep -v "3940256099942544" \
  | grep -v "/build/"
```

Expected: **no output**. Any line printed is a production identifier that must be removed before publication — stop and resolve it.

- [ ] **Step 2: Add the regression test**

Append to `docs-site/scripts/screenshots.test.mjs`:

```js
test('licensing contract is recorded and no production ad identifiers are claimed', () => {
  const l = contract.licensing;
  assert.match(l, /ca-app-pub-3940256099942544/, 'licensing must name the Google test publisher');
  assert.match(l, /strictTestMode/, 'licensing must cite the strictTestMode guarantee');
  assert.match(l, /never/i, 'licensing must state the prohibition on real creatives');
  assert.match(l, /not legal advice/i, 'licensing must disclaim legal advice');

  const serialised = JSON.stringify(manifest);
  const publishers = [...serialised.matchAll(/ca-app-pub-(\d+)/g)].map((m) => m[1]);
  for (const p of publishers) {
    assert.equal(p, '3940256099942544', `manifest references non-test publisher ca-app-pub-${p}`);
  }

  for (const e of entries) {
    assert.doesNotMatch(e.alt, /\bIDFA\b|\bGAID\b|advertising identifier/i,
      `${e.file}: alt must not reference a real advertising identifier`);
  }
});
```

- [ ] **Step 3: Run the verifier**

```bash
node --test docs-site/scripts/screenshots.test.mjs
```

Expected: PASS, `# pass 6`, `# fail 0`.

- [ ] **Step 4: Visually audit all 34 images**

Open the asset directory and look at every image once:

```bash
open docs-site/src/assets/screenshots
```

For each image confirm all four:

- The ad creative is a Google test creative — it carries a visible "Test Ad" label, or is the "AdMob native ad validator" card, or is one of Google's sample house creatives.
- No real brand's advertisement appears.
- No ad unit id other than a `ca-app-pub-3940256099942544/...` value is legible (check `diagnostics-*`, `console-*` and the banner/native cards, which print `placement.id` and can surface ids).
- No personal data appears — no real account name, email, device name or location in the status bar or any dialog.

If any image fails, delete it, re-capture it per its originating task, and re-run the verifier.

- [ ] **Step 5: Confirm the metadata strip actually worked**

```bash
for f in docs-site/src/assets/screenshots/*.png; do
  printf '%s: ' "$(basename "$f")"
  magick identify -format '%[EXIF:*]%[IPTC:*]' "$f" 2>/dev/null | wc -c
done
```

Expected: every line ends in `0` or `1` (no EXIF/IPTC payload). Any larger number means `-strip` did not run for that file — re-run it through the capture script.

- [ ] **Step 6: Commit**

```bash
git add docs-site/scripts/screenshots.test.mjs
git commit -m "test(docs): assert screenshots carry only Google test ad identifiers"
```

---

### Task 15: Astro integration contract for Plans 5 and 3

One component is the entire supported surface for consuming these assets. Plan 5's landing-page showcase and Plan 3's per-format pages both go through it, so `alt`, dimensions and format negotiation are decided once and cannot drift page to page.

**Files:**
- Create: `docs-site/src/components/Screenshot.astro`
- Modify: `docs-site/scripts/screenshots.test.mjs` (append the consumer-contract test)

**Interfaces:**
- Consumes: `screenshots.json` (Task 4) and the 34 PNGs (Tasks 6–13).
- Produces: `<Screenshot name="banner-android-dark.png" />` — props `name: string` (required, a manifest `file` value), `class?: string`, `loading?: 'lazy' | 'eager'` (default `'lazy'`), `sizes?: string`. Emits an `<picture>` with AVIF and WebP sources plus a PNG fallback, `alt` and `width`/`height` from the manifest, and a `--screenshot-focus` custom property carrying the entry's `focus` value for CSS cropping. **Plans 5 and 3 must not import PNGs directly and must not hand-write `alt` text.**

- [ ] **Step 1: Write the component**

Create `docs-site/src/components/Screenshot.astro`:

```astro
---
import { Picture } from 'astro:assets';
import type { ImageMetadata } from 'astro';
import manifest from '../assets/screenshots/screenshots.json';

interface Props {
  /** A `file` value from screenshots.json, e.g. "banner-android-dark.png". */
  name: string;
  class?: string;
  loading?: 'lazy' | 'eager';
  sizes?: string;
}

const {
  name,
  class: className,
  loading = 'lazy',
  sizes = '(max-width: 640px) 88vw, 420px',
} = Astro.props;

const entry = manifest.screenshots.find((s) => s.file === name);
if (!entry) {
  throw new Error(
    `<Screenshot name="${name}"> is not in screenshots.json. Known: ` +
      manifest.screenshots.map((s) => s.file).join(', '),
  );
}

const files = import.meta.glob<{ default: ImageMetadata }>(
  '../assets/screenshots/*.png',
  { eager: true },
);
const asset = files[`../assets/screenshots/${entry.file}`];
if (!asset) {
  throw new Error(`screenshots.json lists ${entry.file} but the PNG is missing from src/assets/screenshots/`);
}

const widths = [...new Set([400, 800, entry.width])].filter((w) => w <= entry.width);
---

<Picture
  src={asset.default}
  formats={['avif', 'webp']}
  alt={entry.alt}
  width={entry.width}
  height={entry.height}
  widths={widths}
  sizes={sizes}
  loading={loading}
  decoding="async"
  class:list={['screenshot', className]}
  data-platform={entry.platform}
  data-device-class={entry.deviceClass}
  data-theme={entry.theme}
  style={`--screenshot-focus: ${entry.focus};`}
/>

<style>
  .screenshot {
    display: block;
    max-width: 100%;
    height: auto;
    border-radius: 0.75rem;
    /* Sources are committed uncropped and unframed (Plan 7, Task 5). A consumer that
       wants a card-shaped crop sets its own aspect-ratio and object-fit; --screenshot-focus
       carries the manifest's hint for where to anchor. */
    object-position: center var(--screenshot-focus, center);
  }
</style>
```

- [ ] **Step 2: Add the consumer-contract test**

Append to `docs-site/scripts/screenshots.test.mjs`:

```js
test('consumer contract: every subject Plans 3 and 5 need is available on both platforms', () => {
  for (const subject of contract.requiredSubjects) {
    for (const platform of contract.platforms) {
      const hit = entries.find(
        (e) => e.subject === subject && e.platform === platform && e.deviceClass === 'phone' && e.theme === 'dark',
      );
      assert.ok(hit, `Plan 5's showcase needs ${subject}-${platform}-dark.png and it is missing`);
    }
  }
  // The consent flow and the iOS ATT prompt are the two OS-rendered surfaces, so they
  // and only they exist in both appearances.
  for (const platform of contract.platforms) {
    for (const theme of contract.themes) {
      assert.ok(
        entries.some((e) => e.subject === 'consent' && e.platform === platform && e.theme === theme),
        `consent-${platform}-${theme}.png is missing`,
      );
    }
  }
  for (const theme of contract.themes) {
    assert.ok(entries.some((e) => e.subject === 'att' && e.platform === 'ios' && e.theme === theme),
      `att-ios-${theme}.png is missing`);
  }
  assert.equal(entries.filter((e) => e.platform === 'android' && e.theme === 'light').length, 1,
    'Android has exactly one light capture: the UMP consent form');
  assert.equal(entries.length, 34, 'the complete Plan 7 set is 34 assets');
});
```

- [ ] **Step 3: Run the verifier**

```bash
node --test docs-site/scripts/screenshots.test.mjs
```

Expected: PASS, `# pass 7`, `# fail 0`.

- [ ] **Step 4: Build the site if Astro is already scaffolded**

The component needs `astro:assets`, which only resolves once Plan 2 has scaffolded Astro into `docs-site/`.

```bash
if [ -f docs-site/package.json ]; then
  (cd docs-site && npm install && npx astro build)
else
  echo "docs-site/package.json absent — Plan 2 has not run yet; Astro build check deferred to Plan 5"
fi
```

Expected: either a successful `astro build` with AVIF and WebP derivatives emitted under `docs-site/dist/_astro/`, or the deferral message. Both are acceptable outcomes; the Node verifier is the gate this plan owns.

- [ ] **Step 5: Commit**

```bash
git add docs-site/src/components/Screenshot.astro docs-site/scripts/screenshots.test.mjs
git commit -m "feat(docs): add Screenshot.astro as the single consumer surface for device screenshots"
```

---

## Handoff to Plans 5, 3 and 4

**Plan 5 (landing page)** did not exist when this plan was written, so **this plan is the authority on screenshot naming**. Plan 5 must:

- Use `<Screenshot name="…" />`; never import a PNG directly and never hand-write `alt`.
- Build its format showcase grid from exactly these fourteen files — the seven `requiredSubjects`, phone, dark, on both platforms:

  | Subject | Android | iOS |
  |---|---|---|
  | banner | `banner-android-dark.png` | `banner-ios-dark.png` |
  | banner-collapsible | `banner-collapsible-android-dark.png` | `banner-collapsible-ios-dark.png` |
  | native | `native-android-dark.png` | `native-ios-dark.png` |
  | interstitial | `interstitial-android-dark.png` | `interstitial-ios-dark.png` |
  | rewarded | `rewarded-android-dark.png` | `rewarded-ios-dark.png` |
  | rewarded-interstitial | `rewarded-interstitial-android-dark.png` | `rewarded-interstitial-ios-dark.png` |
  | app-open | `app-open-android-dark.png` | `app-open-ios-dark.png` |

  `rewarded` and `rewarded-interstitial` are distinct formats with distinct screenshots — the grid needs both rows, not one standing in for the other.
- Set `loading="eager"` on at most the one above-the-fold hero image and leave the rest lazy.
- Not request a `-light` harness variant. None exists, and Task 5's Global Constraints explain why.

**Plan 3 (per-format doc pages)** maps as follows:

| Page | Screenshots |
|---|---|
| `/formats/banner/` | `banner-{android,ios}-dark.png`, `banner-collapsible-{android,ios}-dark.png`, `banner-{android,ios}-tablet-dark.png` |
| `/formats/interstitial/` | `interstitial-{android,ios}-dark.png` |
| `/formats/rewarded/` | `rewarded-{android,ios}-dark.png`, `rewarded-interstitial-{android,ios}-dark.png` |
| `/formats/app-open/` | `app-open-{android,ios}-dark.png` |
| `/formats/native/` | `native-{android,ios}-dark.png`, `native-validator-{android,ios}-dark.png`, `native-layouts-{android,ios}-dark.png`, `native-layout-source-{android,ios}-dark.png`, `native-{android,ios}-tablet-dark.png` |
| `/privacy/consent/` | `consent-{android,ios}-{light,dark}.png`, `diagnostics-android-dark.png` |
| `/privacy/app-tracking-transparency/` | `att-ios-{light,dark}.png`, `diagnostics-ios-dark.png` |
| `/advanced/mediation/`, `/advanced/revenue-events/` | `diagnostics-{android,ios}-dark.png`, `console-{android,ios}-dark.png` |
| `/reference/troubleshooting/` | `diagnostics-{android,ios}-dark.png`, `console-{android,ios}-dark.png` |

**Plan 4 (diagrams)**: the UMP → ATT → initialize sequence diagram should sit directly above `consent-ios-dark.png` and `att-ios-dark.png` on `/privacy/app-tracking-transparency/` — the diagram states the ordering rule and the two screenshots are the evidence.

---

## Self-Review

**1. Spec coverage.** Every item of Plan 7's brief maps to a task:

| Requirement | Task |
|---|---|
| Pre-flight: Xcode/SDK version, emulator + simulator availability, private-DNS check, build both apps | 1 |
| Fix the stale `admob-cmp/CLAUDE.md:107` note | 2 |
| Banner, incl. collapsible — Android and iOS | 6, 10 (+ tablet 9, 13) |
| Interstitial — Android and iOS | 7, 11 |
| Rewarded — Android and iOS | 7, 11 |
| Rewarded interstitial — Android and iOS | 7, 11 |
| App-open — Android and iOS | 7, 11 |
| Native, incl. layout-DSL example and validator card | 6, 8, 10, 12 (+ tablet 9, 13) |
| Consent / UMP flow | 6, 10 |
| ATT prompt on iOS | 10 |
| Exact navigation through `AdDebugScreen`'s tabs | named per capture step: Formats / Layouts / Diagnostics, with card titles and placement ids |
| Device frames: decide and justify | 5, decision 1 — none |
| Cropping | 5, decision 2 — none; `focus` hint instead |
| Light/dark variants | Global Constraints theme rule + 6, 10 — harness is theme-fixed, so only consent and ATT have both |
| Compression to AVIF+WebP | 15 — Astro `<Picture formats={['avif','webp']}>`; PNG sources only, per the immutable decision |
| Naming convention | Global Constraints, `<subject>-<platform>[-tablet]-<theme>.png`, enforced by the verifier |
| `alt` text for every image | 4 (rules + test), 6–13 (values), 15 (single consumer surface) |
| Integration into Plan 5's showcase and Plan 3's format pages | 15 + Handoff section |
| Licensing / compliance note on test vs real creatives | 14 |
| Assets at `docs-site/src/assets/screenshots/**` | 1, and every capture script writes only there |
| Astro 7.1.6 image pipeline, explicit width/height to avoid layout shift | 4 (dimensions from the PNG header), 15 (`width`/`height` props) |

**2. Placeholder scan.** No `TBD`, `TODO`, "implement later", "add error handling", "similar to Task N", or "write tests for the above" appears. Every script is given in full. Every command is complete and runnable from the repository root. Every expected output is stated.

**3. Type consistency.** Checked across tasks:

- Manifest field names are identical in `screenshots.json` (Task 4), `record-screenshot.mjs` (Task 4), `screenshots.test.mjs` (Tasks 4, 14, 15) and `Screenshot.astro` (Task 15): `file`, `subject`, `platform`, `deviceClass`, `theme`, `device`, `width`, `height`, `focus`, `alt`. The CLI flag is `--device-class` (kebab) and maps to `deviceClass` (camel) exactly once, in `record-screenshot.mjs`.
- Contract keys are identical in the JSON and both test files: `version`, `namingGrammar`, `platforms`, `themes`, `focusValues`, `requiredSubjects`, `consumers`, `themeNote`, `licensing`, `focusNote`.
- `requiredSubjects` has exactly 7 members and the Task 4 test asserts `length === 7`; the Task 11 and Task 15 coverage checks iterate the same list.
- Subject strings are used consistently: `banner`, `banner-collapsible`, `native`, `native-validator`, `native-layouts`, `native-layout-source`, `interstitial`, `rewarded`, `rewarded-interstitial`, `app-open`, `consent`, `att`, `diagnostics`, `console`.
- Running totals reconcile: 6 → 10 → 14 → 16 → 24 → 28 → 32 → 34, and the Task 13 and Task 15 assertions both land on 34 (android 16, ios 18, tablet 4, light 3).
- Kotlin symbols referenced in Task 3 match the source: `AdDebugRecorder.install(manager, scope, capacity)` in `dev.avinya.ads.debug`, and the existing `LaunchedEffect(manager, config, retryGeneration)` block it edits.
- One fix applied during review: the pre-flight script's Android-device section is a `WARN`, not a `FAIL`, so Task 1 can pass before the emulator has ever been booted, while Tasks 6–13 re-run it after boot and get the real `private_dns_mode` check.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-31-visibility-7-device-screenshots.md`.

Two caveats for whoever executes it:

1. **Tasks 6–13 are interactive.** Each requires a human (or a UI-driving agent) to tap Load/Show/Preload and scroll cards into frame between commands. They are not unattended-automatable as written.
2. **Task 1's DNS gate is hard.** If private DNS or AdGuard is active, every ad slot captures empty and the whole set is worthless. Do not skip it.
