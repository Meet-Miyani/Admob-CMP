# Showcase — Phase 1c: Nav Shell

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Nav3 navigation shell with four working tabs, and gate `:showcase`'s tests in `scripts/release-readiness.sh`.

**Architecture:** A Nav3 `NavDisplay` over a `SnapshotStateList` backstack, with `rememberViewModelStoreNavEntryDecorator` so each entry owns a `ViewModelStore` cleared on pop — which is what will exercise banner and native ad disposal in the ad phases. Tab switches reset the backstack rather than pushing, so back from a tab leaves the app.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.1, AGP 9.2.1, Room 2.8.4 + KSP, androidx.sqlite bundled 2.7.0, DataStore 1.2.1, Navigation3 (runtime 1.1.5 / CMP ui 1.1.1), Paging 3.5.0.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** [Phase 1b — Persistence](2026-08-06-showcase-phase-1b-persistence.md) complete.

**No ads render in this plan.** Each of the four tabs shows a placeholder; the Phase 2–6 plans replace them with real screens. This plan closes Phase 1 and ends at the AGENTS.md pre-PR hard stop.

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/` may be created, modified or deleted. If the showcase needs something the SDK does not expose: record it in `docs/showcase-sdk-gaps.md` using the format in the spec's Invariant 0, work around it inside `:showcase`, and escalate to the owner. **Stop and ask — do not patch the library.**
- **Kotlin stays at 2.3.20.** Do not bump `kotlin` in `gradle/libs.versions.toml`. The whole build applies one Kotlin plugin version and admob-cmp's frozen ABI plus its experimental `abiValidation` DSL require exactly this version.
- **No dependencies beyond the approved list** in the spec's "Approved dependencies" table. Specifically **not** approved: Koin, Hilt, Ktor, Coil, SQLDelight, kotlinx-datetime, kotlinx-serialization. Adding any requires the owner's consent first.
- **Do not modify** `gradle.properties` or `admob-cmp-gradle-plugin/gradle.properties` `VERSION_NAME`. This is not a release.
- **Do not modify** `.github/workflows/release.yml`. No SDK tests go into CI — standing owner decision.
- **Do not create files under `gradle/`** other than editing `gradle/libs.versions.toml`. No new secrets.
- **Do not commit** `api/*.klib.api` changes. `:showcase` is unpublished; the frozen ABI is unaffected.
- `minSdk` is 26, `compileSdk` 37, `targetSdk` 36, JVM target 11 — read from `libs.versions.toml`, never hardcoded.
- Package root for all new code: `dev.avinya.admob.showcase`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Branch is `feat/showcase-app`. Do not open a PR; the pre-PR protocol in AGENTS.md is a hard stop requiring the owner's explicit confirmation.

### Three corrections to the spec, applied by this plan

The spec was written before the build files were read in detail. These three points supersede it; fold them back into the spec if it is ever revised.

1. **Android test task is `testAndroidHostTest`, not `testDebugUnitTest`.** `:showcase` uses `com.android.kotlin.multiplatform.library` (same as `shared` and `admob-cmp-core`), whose host-test task is `testAndroidHostTest`. `testDebugUnitTest` does not exist for that plugin.
2. **No framework `export` is needed.** The spec says `shared` "exports" `:showcase`. It does not need to. `ContentView.swift` only calls `MainViewControllerKt.MainViewController()`; no Swift code references a `:showcase` type. `shared`'s framework is `isStatic = true`, so showcase code is linked in regardless — `export` only controls generated Obj-C headers. Use plain `implementation(project(":showcase"))`. Simpler, and it keeps the framework header surface unchanged.
3. **`:showcase` must apply the `dev.avinya.ads.admob-cmp` Gradle plugin.** The spec does not mention it. Without it, `:showcase:iosSimulatorArm64Test` fails at link with `Undefined symbols: _OBJC_CLASS_$_GADBannerView`. Supplying GMA/UMP frameworks to Kotlin/Native **test executables** is that plugin's entire purpose — an iOS *app* resolves them from Xcode's SPM packages, but a test executable has no Xcode.

### One open decision for the owner (do not resolve unilaterally)

Nav3's `rememberNavBackStack` requires `NavKey`s to be `@Serializable`, which needs the **kotlinx-serialization** plugin — not on the approved dependency list. The Phase 1c plan therefore uses a plain `mutableStateListOf` backstack, which works fully but **does not survive process death**. Raise this with the owner when Phase 1c completes; do not add kotlinx-serialization without consent.

---

---

## File Structure

**Created:** `nav/ShowcaseNavKey.kt`, `nav/ShowcaseNavHost.kt`, `nav/ShowcaseNavKeyTest.kt`.

**Modified:** `gradle/libs.versions.toml` (lifecycle `2.11.0-beta01` → `2.11.0`, Nav3 libraries), `showcase/build.gradle.kts`, `ShowcaseApp.kt`, `scripts/release-readiness.sh`, `README.md`.

---

### Task 1: Nav3 shell with four tabs

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `showcase/build.gradle.kts`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKey.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavHost.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKeyTest.kt`

**Interfaces:**
- Consumes: `LocalAppGraph` from the Phase 1b plan; `ShowcaseTheme` from the Phase 1a plan.
- Produces: `sealed interface ShowcaseNavKey : NavKey` with `Feed`, `Library`, `Store`, `Settings`, `ArticleDetail(articleId)`; `val TOP_LEVEL_KEYS: List<ShowcaseNavKey>`; `@Composable fun ShowcaseNavHost(backStack: SnapshotStateList<ShowcaseNavKey>)`. The Phase 2–6 plans add entries to `entryProvider` and push `ArticleDetail`.

- [ ] **Step 1: Bump lifecycle and add the Nav3 dependencies**

`lifecycle-viewmodel-navigation3` is published at `2.11.0`, while the catalog pins `androidx-lifecycle` at `2.11.0-beta01`. Align them.

In `gradle/libs.versions.toml`, change:

```toml
androidx-lifecycle = "2.11.0"
```

and add to `[libraries]`:

```toml
androidx-lifecycle-viewmodelNavigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "androidx-lifecycle" }
```

- [ ] **Step 2: Verify the bump did not break other consumers**

The bump touches `shared`, and therefore `desktopApp` and `webApp`.

```bash
./gradlew :shared:compileAndroidMain :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`. If any fails, **stop and report** — reverting to `2.11.0-beta01` means dropping `lifecycle-viewmodel-navigation3`, which is a design change needing the owner's decision.

- [ ] **Step 3: Add the dependencies to `:showcase`**

In `showcase/build.gradle.kts`, add to `commonMain` dependencies:

```kotlin
                implementation(libs.androidx.navigation3.runtime)
                implementation(libs.androidx.navigation3.ui)
                implementation(libs.androidx.lifecycle.viewmodelNavigation3)
```

- [ ] **Step 4: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKeyTest.kt`:

```kotlin
package dev.avinya.admob.showcase.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShowcaseNavKeyTest {

    @Test
    fun exposesExactlyFourTopLevelDestinations() {
        assertEquals(
            listOf(
                ShowcaseNavKey.Feed,
                ShowcaseNavKey.Library,
                ShowcaseNavKey.Store,
                ShowcaseNavKey.Settings,
            ),
            TOP_LEVEL_KEYS,
        )
    }

    @Test
    fun articleDetailIsNotATopLevelDestination() {
        assertTrue(TOP_LEVEL_KEYS.none { it is ShowcaseNavKey.ArticleDetail })
    }

    @Test
    fun articleDetailKeysCompareByArticleId() {
        assertEquals(ShowcaseNavKey.ArticleDetail("a1"), ShowcaseNavKey.ArticleDetail("a1"))
        assertTrue(ShowcaseNavKey.ArticleDetail("a1") != ShowcaseNavKey.ArticleDetail("a2"))
    }

    @Test
    fun everyTopLevelKeyHasALabel() {
        assertTrue(TOP_LEVEL_KEYS.all { it.label.isNotBlank() })
    }
}
```

- [ ] **Step 5: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*ShowcaseNavKeyTest*' --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: ShowcaseNavKey`.

- [ ] **Step 6: Write the nav keys**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKey.kt`:

```kotlin
package dev.avinya.admob.showcase.nav

import androidx.navigation3.runtime.NavKey

/**
 * Every destination in the showcase.
 *
 * Keys are plain data, not `@Serializable`: `rememberNavBackStack` would
 * require kotlinx-serialization, which is not an approved dependency. The
 * consequence is that the backstack does not survive process death — raised
 * with the owner as an open decision.
 */
sealed interface ShowcaseNavKey : NavKey {
    val label: String

    data object Feed : ShowcaseNavKey {
        override val label: String = "Feed"
    }

    data object Library : ShowcaseNavKey {
        override val label: String = "Library"
    }

    data object Store : ShowcaseNavKey {
        override val label: String = "Store"
    }

    data object Settings : ShowcaseNavKey {
        override val label: String = "Settings"
    }

    data class ArticleDetail(val articleId: String) : ShowcaseNavKey {
        override val label: String = "Article"
    }
}

/** The bottom bar's destinations, in order. */
val TOP_LEVEL_KEYS: List<ShowcaseNavKey> = listOf(
    ShowcaseNavKey.Feed,
    ShowcaseNavKey.Library,
    ShowcaseNavKey.Store,
    ShowcaseNavKey.Settings,
)
```

- [ ] **Step 7: Run to verify the key test passes**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*ShowcaseNavKeyTest*' --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 8: Write the nav host**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavHost.kt`.

The Phase 2–6 plans replace each placeholder body with the real screen.

```kotlin
package dev.avinya.admob.showcase.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator

/**
 * The app's navigation shell.
 *
 * Real Nav3 entries matter here beyond tidiness: each entry owns a
 * `ViewModelStore` that is cleared on pop, which is what makes banner and
 * native ad disposal actually get exercised as the user moves around.
 */
@Composable
fun ShowcaseNavHost(backStack: SnapshotStateList<ShowcaseNavKey>) {
    val current = backStack.lastOrNull() ?: ShowcaseNavKey.Feed

    Scaffold(
        bottomBar = {
            NavigationBar {
                TOP_LEVEL_KEYS.forEach { key ->
                    NavigationBarItem(
                        selected = current == key,
                        onClick = { switchTopLevel(backStack, key) },
                        // Text initials, not material-icons: that artifact is not on the
                        // approved dependency list. The Phase 6 plan's polish pass revisits this.
                        icon = { Text(key.label.first().toString()) },
                        label = { Text(key.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().padding(padding),
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<ShowcaseNavKey.Feed> { PlaceholderScreen("Feed") }
                entry<ShowcaseNavKey.Library> { PlaceholderScreen("Library") }
                entry<ShowcaseNavKey.Store> { PlaceholderScreen("Store") }
                entry<ShowcaseNavKey.Settings> { PlaceholderScreen("Settings") }
                entry<ShowcaseNavKey.ArticleDetail> { key -> PlaceholderScreen("Article ${key.articleId}") }
            },
        )
    }
}

/**
 * Switching tabs resets to a single-entry backstack rather than pushing.
 * Tabs are peers, so a back press from a tab should leave the app, not walk
 * a history of tab switches.
 */
private fun switchTopLevel(backStack: SnapshotStateList<ShowcaseNavKey>, key: ShowcaseNavKey) {
    if (backStack.size == 1 && backStack.first() == key) return
    backStack.clear()
    backStack.add(key)
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name)
    }
}
```

- [ ] **Step 9: Wire the nav host into ShowcaseApp**

In `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`, replace the `Box { Text(...) }` body inside `ShowcaseTheme` with:

```kotlin
            val backStack = remember { mutableStateListOf<ShowcaseNavKey>(ShowcaseNavKey.Feed) }
            ShowcaseNavHost(backStack = backStack)
```

and add the imports:

```kotlin
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import dev.avinya.admob.showcase.nav.ShowcaseNavHost
import dev.avinya.admob.showcase.nav.ShowcaseNavKey
```

Remove the now-unused `Box`, `fillMaxSize`, `Alignment` and `Text` imports.

- [ ] **Step 10: Verify both platforms build and all tests pass**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Run on both platforms and confirm all four tabs switch**

```bash
./gradlew :androidApp:installDebug
```

Tap each of the four tabs; each shows its placeholder. Then build and run `iosApp` in Xcode against a simulator and confirm the same.

- [ ] **Step 12: Report the process-death decision to the owner**

State plainly: the backstack uses `mutableStateListOf` and does not survive process death; restoring it needs `rememberNavBackStack`, which requires `@Serializable` nav keys and therefore the kotlinx-serialization plugin — not on the approved list. **Ask; do not add it.**

- [ ] **Step 13: Commit**

```bash
git add gradle/libs.versions.toml showcase
git commit -m "$(cat <<'EOF'
feat(showcase): add Nav3 shell with four tabs

NavDisplay with per-entry ViewModel stores, so entries are disposed on
pop — which is what will exercise banner and native ad disposal in later
phases. Tab switches reset the backstack rather than pushing, so back
from a tab leaves the app.

Aligns androidx-lifecycle 2.11.0-beta01 -> 2.11.0 to match
lifecycle-viewmodel-navigation3; shared, desktopApp and webApp verified
still compiling.

Backstack uses mutableStateListOf and does not survive process death;
rememberNavBackStack would need kotlinx-serialization, which is not an
approved dependency.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Wire `:showcase` tests into release-readiness and document the module

**Files:**
- Modify: `scripts/release-readiness.sh`
- Modify: `README.md`

**Interfaces:**
- Consumes: everything.
- Produces: nothing consumed by later phases.

- [ ] **Step 1: Add the Android host test to section 3**

In `scripts/release-readiness.sh`, in `section "3. Android + ABI + publication metadata"`, add `:showcase:testAndroidHostTest` to the Gradle invocation, immediately before `:androidApp:assembleDebug`:

```bash
./gradlew \
  :admob-cmp-core:testAndroidHostTest \
  :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp:verifyKotlinMultiplatformPomDependencyScopes \
  :admob-cmp-compose:verifyKotlinMultiplatformPomDependencyScopes \
  :showcase:testAndroidHostTest \
  :androidApp:assembleDebug \
  --no-configuration-cache
```

- [ ] **Step 2: Add the iOS test to section 5**

In `section "5. iOS + klib ABI"`, add `:showcase:iosSimulatorArm64Test` after the two SDK iOS tests:

```bash
./gradlew \
  :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-compose:iosSimulatorArm64Test \
  :showcase:iosSimulatorArm64Test \
  :admob-cmp-core:checkKotlinAbi \
  :admob-cmp-compose:checkKotlinAbi \
  --no-configuration-cache
```

Do **not** add a `checkKotlinAbi` for `:showcase` — it is unpublished and has no ABI dump.

- [ ] **Step 3: Add the README section**

In `README.md`, add before the licence section:

```markdown
## Showcase app

`showcase/` is a production-grade Compose Multiplatform reference app that
exercises every ad format the SDK supports in realistic placements — a
reading app with a paged feed, article detail, a coin economy and a
per-screen ad Inspector.

It is a **consumer** of `admob-cmp`, never a reason to change it. Design:
[docs/superpowers/specs/2026-08-06-showcase-app-design.md](docs/superpowers/specs/2026-08-06-showcase-app-design.md).

Run it:

```bash
./gradlew :androidApp:installDebug          # Android
open iosApp/iosApp.xcodeproj                # iOS — build and run in Xcode
```

All placements use Google's test ad units with `strictTestMode` on. The app
targets Android and iOS only; `desktopApp` and `webApp` keep rendering the
unsupported-platform screen.

Tests:

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test
```
```

- [ ] **Step 4: Verify the script parses and the new targets exist**

```bash
bash -n scripts/release-readiness.sh && echo "syntax OK"
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --dry-run --no-configuration-cache
```

Expected: `syntax OK` and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add scripts/release-readiness.sh README.md
git commit -m "$(cat <<'EOF'
chore(showcase): run :showcase tests in release-readiness, document module

Adds :showcase:testAndroidHostTest to section 3 and
:showcase:iosSimulatorArm64Test to section 5. No checkKotlinAbi —
:showcase is unpublished and has no ABI dump. release.yml is untouched.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Full verification run — then STOP**

```bash
./scripts/release-readiness.sh
```

Expected: `READINESS: PASS`. `--skip-docs` is **not** acceptable: this branch modifies `gradle/libs.versions.toml`.

**Then stop.** Report to the owner: which sections ran, which were skipped, anything that failed and was fixed, plus the two open decisions (nav backstack process-death persistence, and any entries written into `docs/showcase-sdk-gaps.md`). A `READINESS: PASS` is a prerequisite for *asking* about a PR — it is not authorisation to open one.

---

---

## Exit criteria

- [ ] Four tabs switch correctly on Android **and** iOS
- [ ] `desktopApp` and `webApp` still compile after the lifecycle bump
- [ ] `ShowcaseNavKeyTest` passes on the Android host and iOS
- [ ] `./scripts/release-readiness.sh` reports `READINESS: PASS` (no `--skip-docs`)
- [ ] `git diff --stat master -- 'admob-cmp*'` is empty
- [ ] Owner told about the nav process-death decision and any `docs/showcase-sdk-gaps.md` entries
- [ ] **Stopped** — no PR opened without the owner's explicit confirmation

---

## Next plan

**Phase 2 — Consent & init**, written after this lands: onboarding, consent → ATT → `initialize()`, and the real Settings screen with live consent/privacy/ATT/diagnostics state. This is where `AdManagerStatus.Ready` is first reached.

Then Phase 3 (feed + first ads), Phase 4 (article + interstitial), Phase 5 (store + rewarded), Phase 6 (app-open + Inspector).
