# Track 2: Consumer Linker Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the consumer-side test-link failure self-explanatory and documented, so consumers stop hitting a dead end before the Gradle plugin (Track 1) ships.

**Architecture:** Two independent, low-risk changes — a `userSetupHint` in each `.def` file so Kotlin/Native prints an actionable message when the linker fails, and a SETUP.md correction that distinguishes the *app* link from the *Kotlin/Native test* link. No build logic changes, no API changes, no version bump required beyond a patch release.

**Tech Stack:** Kotlin/Native cinterop `.def` files, Markdown docs.

## Global Constraints

- Ship this **before** Track 1. It is independent, cannot break CI, and is valuable even if Track 1 slips.
- Do not change any Kotlin source, `build.gradle.kts`, or public API. `checkKotlinAbi` must stay green without an `updateKotlinAbi` run.
- `userSetupHint` is a documented cinterop property: *"If you're a library author, you can help your users resolve linker errors with custom messages. To do that, add a `userSetupHint=message` property to your `.def` file"* — https://kotlinlang.org/docs/native-definition-file.html
- The message must fit on one line in the `.def` file. `.def` files are Java-properties-like; a literal newline ends the property.
- **Ordering caveat:** the hint text below names the `dev.avinya.ads.admob-cmp` Gradle plugin, which Track 1 creates. If this plan ships **first** (a patch release ahead of the plugin), delete the clause `apply the dev.avinya.ads.admob-cmp Gradle plugin, or ` from both hints and keep only the docs URL — never point users at an artifact that is not on Maven Central. Restore the clause when Track 1 publishes. If both tracks ship together in 1.1.0, use the text exactly as written.
- Verified failure this addresses, reproduced on 2026-07-29 by running `./gradlew :shared:iosSimulatorArm64Test -PadmobCmpConsumePublished=true` in this repo:
  ```
  Undefined symbols for architecture arm64:
    "_OBJC_CLASS_$_GADBannerView", referenced from:
         in libAdmobCMP:admob-cmp-compose-cache.a[2](...)
  ld: symbol(s) not found for architecture arm64
  > Execution failed for task ':shared:linkDebugTestIosSimulatorArm64'.
  ```

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def` | Adds `userSetupHint` for GMA link failures | 1 |
| `admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def` | Adds `userSetupHint` for UMP link failures | 1 |
| `admob-cmp/docs/SETUP.md` | Corrects the troubleshooting table; adds a Kotlin/Native test section | 2 |

---

### Task 1: Emit an actionable hint on linker failure

**Files:**
- Modify: `admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def`
- Modify: `admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def`

**Interfaces:**
- Produces: nothing consumed by later tasks. The hint text should match the wording added to SETUP.md in Task 2.

- [ ] **Step 1: Reproduce the failure and capture the current (unhelpful) output**

Run:

```bash
./gradlew :shared:iosSimulatorArm64Test -PadmobCmpConsumePublished=true
```

Expected: FAILS at `:shared:linkDebugTestIosSimulatorArm64` with `Undefined symbols ... _OBJC_CLASS_$_GADBannerView` and **no guidance whatsoever**. Save the output; you compare against it in Step 4.

If the task instead fails to *resolve* `dev.avinya.ads` artifacts, publish them locally first and re-run:

```bash
./gradlew publishToMavenLocal -PsignAllPublications=false --no-configuration-cache
```

- [ ] **Step 2: Add the hint to the GoogleMobileAds def**

Replace the entire contents of `admob-cmp-core/src/nativeInterop/cinterop/GoogleMobileAds.def` with:

```
language = Objective-C
modules = GoogleMobileAds
userSetupHint = admob-cmp ships Google Mobile Ads BINDINGS only, never Google's binaries. An iOS APP link gets them from the GoogleMobileAds Swift package (Xcode > Add Package Dependencies, 13.7.0+). A Kotlin/Native TEST executable has no Xcode, so it must link them itself - apply the dev.avinya.ads.admob-cmp Gradle plugin, or see https://github.com/Meet-Miyani/Admob-CMP/blob/master/admob-cmp/docs/SETUP.md#kotlinnative-test-executables
```

- [ ] **Step 3: Add the hint to the UserMessagingPlatform def**

Replace the entire contents of `admob-cmp-core/src/nativeInterop/cinterop/UserMessagingPlatform.def` with:

```
language = Objective-C
modules = UserMessagingPlatform
userSetupHint = admob-cmp ships User Messaging Platform BINDINGS only, never Google's binaries. An iOS APP link gets them from the GoogleUserMessagingPlatform Swift package (Xcode > Add Package Dependencies, 3.1.0+). A Kotlin/Native TEST executable has no Xcode, so it must link them itself - apply the dev.avinya.ads.admob-cmp Gradle plugin, or see https://github.com/Meet-Miyani/Admob-CMP/blob/master/admob-cmp/docs/SETUP.md#kotlinnative-test-executables
```

- [ ] **Step 4: Rebuild the bindings and re-run the failing link**

The hint is baked into the klib at cinterop time, so the cinterop must re-run. Force it:

```bash
./gradlew :admob-cmp-core:cinteropGmaIosSimulatorArm64 :admob-cmp-core:cinteropUmpIosSimulatorArm64 --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

Then republish locally and re-run the consumer link:

```bash
./gradlew publishToMavenLocal -PsignAllPublications=false --no-configuration-cache
```

```bash
./gradlew :shared:iosSimulatorArm64Test -PadmobCmpConsumePublished=true --refresh-dependencies
```

Expected: still FAILS (Track 1 fixes that), but the output now carries your hint text alongside the undefined-symbol error.

**If the hint does not appear:** do not fake it or move on. Kotlin/Native may only surface `userSetupHint` for some failure classes. Record exactly what was printed, and report it — the honest outcome is "this mechanism does not cover our case," which changes Track 1's priority rather than being papered over.

- [ ] **Step 5: Verify the public API is untouched**

Run:

```bash
./gradlew :admob-cmp-core:checkKotlinAbi :admob-cmp-compose:checkKotlinAbi
```

Expected: `BUILD SUCCESSFUL`. A `.def` hint is metadata, not API — if this fails, something else changed.

- [ ] **Step 6: Commit**

```bash
git add admob-cmp-core/src/nativeInterop/cinterop
git commit -m "feat(ios): explain the test-link requirement via userSetupHint

A consumer whose Kotlin/Native test executable links admob-cmp gets a bare
'Undefined symbols: _OBJC_CLASS_\$_GADBannerView' with no path forward. cinterop's
userSetupHint attaches an actionable message to exactly that failure."
```

---

### Task 2: Correct the SETUP.md troubleshooting guidance

Today the troubleshooting table maps `Undefined symbol: _OBJC_CLASS_$_GADMobileAds` to "GoogleMobileAds SPM package not added → Add the GMA SPM package". That is right for the app link and **actively wrong** for the test link — adding a package in Xcode does nothing for a Gradle-driven Kotlin/Native link, so a consumer follows the advice, sees the identical error, and is stuck.

**Files:**
- Modify: `admob-cmp/docs/SETUP.md` (the iOS troubleshooting section)

**Interfaces:**
- Consumes: the hint wording from Task 1 — the anchor `#kotlinnative-test-executables` referenced by both `.def` files must exist after this task.

- [ ] **Step 1: Add the missing section**

In `admob-cmp/docs/SETUP.md`, immediately **before** the `### Troubleshooting: iOS linker errors` heading, insert:

```markdown
### Kotlin/Native test executables

Your app links Google's frameworks through SPM (step 1 above). Your **tests** do not.

`./gradlew :yourModule:iosSimulatorArm64Test` makes the Kotlin/Native compiler link a
standalone executable with no Xcode, no `.xcodeproj`, and no SPM anywhere in the
picture. Every symbol must resolve at that link — including the `GAD*`/`UMP*` classes
these bindings reference. This applies even if **none of your tests touch ads**: the
test binary contains your module's whole main compilation, so any production code
calling `rememberAdManager`, `NativeAdView`, or the consent APIs brings those
references along.

The supported fix is the Gradle plugin, which downloads the matching XCFrameworks and
applies the linker options to test binaries only:

```kotlin
plugins {
    id("dev.avinya.ads.admob-cmp") version "<version>"
}
```

A `FakeAdManager` does not help — the requirement comes from the bindings being present
in the link, not from anyone calling them. (For faking ad *behaviour* in tests, the SDK
ships `NoOpAdManager`.)
```

> **Note for the implementer:** if Track 1 has not shipped when this lands, replace the
> `plugins { }` snippet above with the manual `linkerOpts` workaround and a line saying
> the plugin is coming in the next release. Do not document a plugin version that does
> not exist on Maven Central.

- [ ] **Step 2: Split the misleading troubleshooting rows**

In the `### Troubleshooting: iOS linker errors` table, replace these two rows:

```markdown
| `Undefined symbol: _OBJC_CLASS_$_GADMobileAds` | GoogleMobileAds SPM package not added | Add the GMA SPM package (step 1) |
| `Undefined symbol: _OBJC_CLASS_$_UMPConsentInformation` | UMP SPM package not added | Add the UMP SPM package (step 1) |
```

with:

```markdown
| `Undefined symbol: _OBJC_CLASS_$_GAD*` **during an Xcode/app build** | GoogleMobileAds SPM package not added | Add the GMA SPM package (step 1) |
| `Undefined symbol: _OBJC_CLASS_$_UMP*` **during an Xcode/app build** | UMP SPM package not added | Add the UMP SPM package (step 1) |
| `Undefined symbol: _OBJC_CLASS_$_GAD*`/`_UMP*` **during `:linkDebugTestIos…`** | A Kotlin/Native test executable cannot use SPM | Apply the `dev.avinya.ads.admob-cmp` Gradle plugin — see [Kotlin/Native test executables](#kotlinnative-test-executables). Adding an SPM package will **not** fix this. |
```

- [ ] **Step 3: Verify the anchor the `.def` hints point at resolves**

Run:

```bash
grep -n "^### Kotlin/Native test executables" admob-cmp/docs/SETUP.md
```

Expected: exactly one match. GitHub derives the anchor `#kotlinnative-test-executables` from this heading; both `.def` files link to it, so a rename breaks two other files.

- [ ] **Step 4: Commit**

```bash
git add admob-cmp/docs/SETUP.md
git commit -m "docs(setup): distinguish app-link from Kotlin/Native test-link failures

The troubleshooting table gave app-link advice ('add the SPM package') for a symptom
that also occurs at test-link time, where SPM is not involved and that advice cannot
work. Adds the missing section the cinterop userSetupHint points at."
```

---

## Notes

`userSetupHint` is a mitigation, not the fix — it turns a dead end into a signpost. The fix is Track 1. Ship this first precisely because it is worth having even if Track 1 slips, and because it costs one patch release with zero build-logic risk.
