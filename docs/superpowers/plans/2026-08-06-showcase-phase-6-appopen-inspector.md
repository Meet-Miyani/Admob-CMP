# Showcase — Phase 6: App-Open, Inspector & Polish

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the showcase — app-open ads with real suppression, a telemetry pipeline feeding a three-tab Inspector that shows what the SDK is doing and *why* an ad did not appear, then the documentation and verification pass that finishes the branch.

**Architecture:** `AppOpenAdCoordinator` is started once at the app root and driven by `AppOpenSuppressor` from Phase 5. One app-scoped collector drains `adManager.events` into `AdTelemetryRepository`; the Inspector reads Room, so its history survives navigation.

**Tech Stack:** Compose Multiplatform 1.11.1, Navigation3, Room 2.8.4.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** Phase 5 complete. **This is the last plan for the branch.**

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/`. Record gaps in `docs/showcase-sdk-gaps.md`, work around inside `:showcase`, escalate. **Stop and ask.**
- **Kotlin stays at 2.3.20. No new dependencies.**
- **Testing principle.** Test what a consumer would copy. Event→row mapping and revenue aggregation are pure functions; test those. Do not add a test runtime to observe Room or Compose.
- **Do not modify** `gradle.properties`, the plugin's `gradle.properties`, or `.github/workflows/release.yml`. **Do not commit** `api/*.klib.api` changes.
- Package root `dev.avinya.admob.showcase`. Branch `feat/showcase-app`.
- Commits end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- **The branch ends at a hard stop.** Task 5 finishes with a full `release-readiness.sh` run and a report to the owner. A `READINESS: PASS` is a prerequisite for *asking* about a PR — never authorisation to open one.

---

## Tasks

### Task 1: App-open ads with real suppression

**Files:**
- Modify: `showcase/src/commonMain/.../domain/ad/ShowcasePlacements.kt`
- Create: `showcase/src/commonMain/.../ui/ad/AppOpenHost.kt`
- Modify: `showcase/src/commonMain/.../ui/ad/AppOpenSuppression.kt`
- Modify: `showcase/src/commonMain/.../ShowcaseApp.kt`

- [ ] **Step 1: Add the placement**

```kotlin
    val appOpen = AdPlacement(
        id = "app_open",
        format = AdFormat.AppOpen,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_APP_OPEN, ios = TestAdIds.IOS_APP_OPEN),
        strictTestMode = true,
    )
```

- [ ] **Step 2: Host the coordinator**

```kotlin
@Composable
fun AppOpenHost(suppressor: AppOpenSuppressor, content: @Composable () -> Unit) {
    val adManager = LocalAdManager.current
    val coordinator = remember(adManager) {
        AppOpenAdCoordinator(
            manager = adManager,
            controller = adManager.appOpen(ShowcasePlacements.appOpen),
            config = AppOpenConfig(
                minBackgroundDuration = 4.seconds,
                cooldownBetweenShows = 4.hours,
            ),
        )
    }

    LaunchedEffect(coordinator) { coordinator.start(this) }

    // Suppression is state, not an event: re-published whenever it changes so
    // a flow that ends while backgrounded cannot leave the app permanently
    // suppressed.
    LaunchedEffect(coordinator, suppressor.isBlocked) {
        coordinator.isBlocked = suppressor.isBlocked
    }

    content()
}
```

Signatures **verified** against `admob-cmp-core` source:

```
AppOpenAdCoordinator(manager: AdManager, controller: AppOpenAdController,
                     config: AppOpenConfig = …)
    var isBlocked: Boolean            // mutable property, not a function
    fun start(scope: CoroutineScope)

AppOpenConfig(showOnColdStart: Boolean = false,
              minBackgroundDuration: Duration = 4.seconds,
              cooldownBetweenShows: Duration = Duration.ZERO,
              preloadOnStart: Boolean = true,
              coldStartTimeout: Duration = 5.seconds)
```

`showOnColdStart` defaults to `false`; leave it so. A cold-start app-open ad is the most intrusive placement in the catalog and would fire over onboarding on a first run.

- [ ] **Step 3: Suppress during onboarding too**

An app-open ad over the consent flow would be both a bad experience and a policy problem. Wrap onboarding in `suppressor.suppressing { }` for its whole lifetime, alongside the Phase 5 unlock transaction.

- [ ] **Step 4: Verify on device**

Background the app 5+ seconds, return → app-open ad shows. Background during onboarding or an unlock → **no ad**. Return within 4 seconds → no ad (below `minBackgroundDuration`).

- [ ] **Step 5: Commit**

---

### Task 2: Telemetry pipeline

**Files:**
- Create: `showcase/src/commonMain/.../data/repo/AdTelemetryRepository.kt`
- Create: `showcase/src/commonMain/.../domain/telemetry/AdEventMapping.kt`
- Modify: `showcase/src/commonMain/.../di/AppGraph.kt`
- Test: `showcase/src/commonTest/.../domain/telemetry/AdEventMappingTest.kt`

**Interfaces:**
- Produces: `fun AdEvent.toRow(at: Long): AdEventRow`, `fun AdEvent.Paid.toPaidRow(at: Long): PaidEventRow`, `fun aggregateRevenue(List<PaidEventRow>): List<PlacementRevenue>`, `class AdTelemetryRepository`.

- [ ] **Step 1: Write the failing test**

Mapping and aggregation are pure; test them as such.

```kotlin
class AdEventMappingTest {

    @Test
    fun everyEventTypeMapsToAStableRowTypeName() {
        // The Inspector renders these strings, so they are a contract, not
        // an implementation detail — renaming one silently changes the UI.
        assertEquals("Loaded", eventTypeName(AdEvent.Loaded(placementId = "p")))
        assertEquals("Impression", eventTypeName(AdEvent.Impression(placementId = "p")))
        assertEquals("Clicked", eventTypeName(AdEvent.Clicked(placementId = "p")))
    }

    @Test
    fun revenueAggregatesPerPlacementInMicros() {
        val rows = listOf(
            PaidEventRow(placementId = "feed_banner", valueMicros = 1_500, currency = "USD"),
            PaidEventRow(placementId = "feed_banner", valueMicros = 2_500, currency = "USD"),
            PaidEventRow(placementId = "store_rewarded", valueMicros = 9_000, currency = "USD"),
        )

        assertEquals(
            listOf(
                PlacementRevenue("store_rewarded", totalMicros = 9_000, impressions = 1, currency = "USD"),
                PlacementRevenue("feed_banner", totalMicros = 4_000, impressions = 2, currency = "USD"),
            ),
            aggregateRevenue(rows),
        )
    }

    @Test
    fun aggregationIsOrderedByRevenueDescendingSoTheTopEarnerIsFirst() {
        val rows = listOf(
            PaidEventRow("a", valueMicros = 10, currency = "USD"),
            PaidEventRow("b", valueMicros = 99, currency = "USD"),
        )

        assertEquals(listOf("b", "a"), aggregateRevenue(rows).map { it.placementId })
    }

    @Test
    fun mixedCurrenciesAreNotSummedTogether() {
        // Adding USD micros to EUR micros produces a meaningless number.
        val rows = listOf(
            PaidEventRow("a", valueMicros = 100, currency = "USD"),
            PaidEventRow("a", valueMicros = 100, currency = "EUR"),
        )

        assertEquals(2, aggregateRevenue(rows).size)
    }
}
```

- [ ] **Step 2: Run to verify it fails**, then implement the mappers and `aggregateRevenue`, grouping by `(placementId, currency)` and sorting by total descending.

- [ ] **Step 3: Wire the collector**

One app-scoped collector in `AppGraph`, started once:

```kotlin
fun startTelemetry(adManager: AdManager) {
    appScope.launch {
        adManager.events.collect { event ->
            telemetry.record(event, clock.nowMillis())
            if (event is AdEvent.Paid) telemetry.recordPaid(event, clock.nowMillis())
        }
    }
}
```

`TelemetryDao`'s `@Transaction` insert-and-trim keeps every log table at 500 rows.

- [ ] **Step 4: Record policy decisions too**

`AdPolicy` suppressions are recorded alongside SDK events. That interleaving is what makes the Events tab answer "why did no ad appear" rather than just "what happened".

- [ ] **Step 5: Verify and commit**

---

### Task 3: The Inspector

**Files:**
- Create: `showcase/src/commonMain/.../ui/inspector/InspectorSheet.kt`
- Create: `showcase/src/commonMain/.../ui/inspector/{Placements,Events,Revenue}Tab.kt`
- Modify: every feature screen — add the Inspector entry point

**Interfaces:**
- Produces: `@Composable fun InspectorSheet(placements: List<AdPlacement>, onDismiss: () -> Unit)`, `LocalInspectorPlacements`.

- [ ] **Step 1: Build the three tabs**

| Tab | Contents |
|---|---|
| **Placements** | For the current screen: the `AdPlacement` config as rendered fields (id, format, ad unit per platform, size/refresh/cache policy), live `AdLoadState`, cache depth, and `pool.availableAds` against `maxSize` |
| **Events** | Rolling `ad_events` **interleaved with `policy_decisions`**, newest first, each row showing placement, type and reason |
| **Revenue** | `aggregateRevenue` output per placement with `AdValuePrecision`, plus the raw `paid_events` list |

- [ ] **Step 2: Label the Android video-event gap explicitly**

The Events tab must state, on Android, that native video events are unavailable — the GMA Next-Gen SDK exposes no equivalent to iOS's `GADVideoControllerDelegate`, so `VideoStarted`/`VideoPlayed`/`VideoPaused`/`VideoEnded`/`VideoMuted` never arrive.

**This is an upstream SDK gap, not a showcase omission.** An empty section with no explanation reads as a bug in our SDK, which is the opposite of what a showcase should communicate. Say it plainly in the UI.

- [ ] **Step 3: Add the entry point**

An icon in each screen's top bar, shown only when `settings.inspectorEnabled`. Each screen supplies its own placements through `LocalInspectorPlacements`.

- [ ] **Step 4: Verify and commit**

Exercise every format, then confirm the Inspector shows loads, impressions, clicks, paid events **and** at least one policy suppression with its reason.

---

### Task 4: KDoc, README and the gap log

**Files:**
- Modify: KDoc across `:showcase`
- Modify: `README.md`
- Possibly create: `docs/showcase-sdk-gaps.md`

- [ ] **Step 1: KDoc pass**

Every public declaration in `domain/` and `di/` gets KDoc explaining **why**, not what. At genuinely non-obvious SDK call sites — the reward callback, feed key derivation, `isBlocked` restoration, the `show()` mutex — a one-line comment naming the rule being satisfied. Match the surrounding density; do not comment every line.

- [ ] **Step 2: README**

Expand the Showcase section with what each screen demonstrates and the format-coverage table from the spec, so a consumer can find the example they need without reading the whole app.

- [ ] **Step 3: Finalise the gap log**

If any SDK gap was found across Phases 2–6, ensure `docs/showcase-sdk-gaps.md` records each in the spec's format. If none was found, **do not create the file** — and say so explicitly in the Task 5 report, because "we built a full app against the SDK and found no gaps" is itself a useful result.

- [ ] **Step 4: Commit**

---

### Task 5: Full verification — then STOP

- [ ] **Step 1: Run everything**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
cd docs-site && npm ci && npm run build && npm test && npm run verify && cd ..
```

- [ ] **Step 2: Confirm Invariant 0 held across the whole branch**

```bash
git diff --stat master..HEAD -- 'admob-cmp*'
```

Expected: **empty**. If not, stop and report before anything else — this is the branch's defining constraint.

- [ ] **Step 3: Full release-readiness**

```bash
./scripts/release-readiness.sh
```

`--skip-docs` is **not** acceptable: the branch touches `gradle/libs.versions.toml`.

- [ ] **Step 4: Manual pass on both platforms**

Fresh install → onboarding → consent → ATT (iOS) → feed with native ads and banner → article with inline native, collapsible banner and interstitial → store with rewarded and offer wall → unlock → library → settings → inspector → background/foreground for app-open.

- [ ] **Step 5: Report to the owner and STOP**

Report: sections run and skipped; anything that failed and was fixed; SDK gaps found (or none); the nav process-death decision still outstanding; and any deviation from these plans.

**Do not open a PR.** Per `AGENTS.md` and `CLAUDE.md` this is a hard stop: a clean `READINESS: PASS` is a prerequisite for *asking*, and the owner decides.

---

## Exit criteria

- [ ] App-open shows after 4s background, suppressed during onboarding and unlocks
- [ ] Telemetry captures every SDK event plus policy suppressions; log tables stay capped at 500 rows
- [ ] Inspector's three tabs render live data on both platforms
- [ ] Android's native-video gap is explicitly labelled as upstream
- [ ] All six ad formats verified working on Android **and** iOS
- [ ] `./scripts/release-readiness.sh` reports `READINESS: PASS`
- [ ] `git diff --stat master..HEAD -- 'admob-cmp*'` is **empty**
- [ ] Owner has been given the final report — **and no PR has been opened**

---

## After this branch

Each of these is its own spec, not a plan:

| Follow-up | Why it was deferred |
|---|---|
| Store release | Icons, screenshots, signing, R8 keep-rules, privacy policy — a distinct discipline from SDK showcasing |
| docs-site integration | Snippet extraction from showcase sources so docs and app cannot drift |
| Nav process-death persistence | Needs kotlinx-serialization; owner decision outstanding |
| Any SDK additions from the gap log | Additive commits with `updateKotlinAbi`, **after** this branch lands |
