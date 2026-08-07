# Fieldnotes Showcase Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Showcase application as a polished editorial culture-and-technology product named **Fieldnotes** that demonstrates the SDK naturally in real product flows, preserves top-level navigation state, and removes the floating/boxed bottom-bar treatment.

**Architecture:** The app has four consumer-facing top-level destinations—Today, Discover, Library, and Profile—each with an independent retained navigation stack. A secondary SDK Lab exposes every supported ad format and diagnostic control without making the primary product feel like a component gallery. Feed and reader native ads use the new session-owned SDK API from the companion native-ad architecture plan.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3 primitives, Navigation 3, Room, Kotlin coroutines and `StateFlow`, the in-repository AdmobCMP SDK.

## Prerequisite and Scope

- Complete `docs/superpowers/plans/2026-08-07-native-ad-session-architecture.md` first, including its characterization gates and `READINESS: PASS`.
- Do not emulate session retention inside Showcase. Showcase consumes the new SDK contract; the SDK owns loading, retention, eviction, expiry, and destruction.
- Preserve existing sample data, Room persistence, bookmarks, wallet/reward behavior, privacy controls, and telemetry unless a task below explicitly relocates the UI.
- Replace the current Feed/Library/Store/Settings top-level structure with Today/Discover/Library/Profile. SDK Lab is entered from Profile and from a small developer action in debug builds.
- The consumer product must remain believable with ads disabled, unavailable, or slow. An unloaded native slot occupies no permanent blank card and does not shift already-visible content unexpectedly.
- The bottom navigation is grounded directly at the window edge. It has no outer floating `Surface`, detached margin, double container, or decorative page padding.
- Compact layouts use a bottom bar. Widths at or above `840.dp` use a navigation rail. Both share the same destinations and retained stacks.
- Do not add a remote font dependency. Use `FontFamily.Serif` for editorial display text and `FontFamily.SansSerif` for interface/body text.
- Do not add gradients, glass effects, emerald branding, oversized rounded-card nesting, or generic AI-dashboard decoration.
- Do not show a sticky bottom banner in an article, and do not trigger interstitials merely because a reader opened or closed an article.
- Do not commit, push, bump the SDK version, or open a PR unless the owner explicitly authorizes it during execution.

## Locked Experience Contract

### Visual system

| Role | Light | Dark |
|---|---:|---:|
| Page | `#F5F0E6` | `#151411` |
| Raised surface | `#FCFAF5` | `#201E1A` |
| Primary ink | `#181713` | `#F3EEE4` |
| Muted ink | `#6D685F` | `#AAA397` |
| Rule/border | `#D8D0C2` | `#3A3630` |
| Editorial accent | `#C6452D` | `#F0785F` |
| Accent container | `#F3D7CF` | `#5A281F` |

- Display typography: serif, `44.sp/48.sp` on expanded layouts and `38.sp/42.sp` on compact layouts.
- Section headline: serif, `30.sp/34.sp`.
- Card title: serif, `22.sp/26.sp`.
- Body: sans serif, `17.sp/27.sp`.
- Metadata/labels: sans serif, `12.sp/16.sp`, medium weight, uppercase only for short taxonomy labels.
- Corner radii: `0.dp` for page structure and navigation, `8.dp` for images, `12.dp` maximum for interactive cards. Never nest rounded cards solely for decoration.
- Spacing scale: `4, 8, 12, 16, 24, 32, 48.dp`. Page gutter is `20.dp` compact and `32.dp` expanded.
- Motion: `180 ms` fade/size transitions for loaded ad insertion; no springy page furniture. Respect reduced-motion accessibility settings where the platform exposes them.

### Product and advertising behavior

- Today is a curated chronological magazine feed. Insert the first native slot after four editorial items, then after every eight editorial items.
- Discover supports category browsing and local article search. Native slots may appear in result feeds using the same stable-slot algorithm, but search result changes get a distinct session key.
- Library contains saved and recently read articles. It contains no ads by default.
- Profile contains preferences, consent/privacy entry points, appearance, reward balance, and the SDK Lab entry.
- Article pages may contain one inline native ad after the first substantial section, never above the headline and never as a sticky footer.
- App-open ads are demonstrated only after consent and initialization, only when a foreground eligibility policy allows them, and never during onboarding, privacy flows, or the first product session.
- Banner, interstitial, rewarded, and rewarded-interstitial formats live in SDK Lab. The Lab uses clear user-triggered actions and exposes readiness/events without leaking platform tokens.
- Stable native slot identity is derived from product context, not list index alone: `today:{feedRevision}:{anchorArticleId}`, `discover:{queryOrCategory}:{anchorArticleId}`, and `article:{articleId}:inline-1`.

---

## File Structure

### Theme and shared presentation

- Modify `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Color.kt` — replace Emerald Glass colors with Fieldnotes tokens.
- Modify `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Type.kt` — define the locked editorial type scale.
- Modify `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Theme.kt` — map semantic colors and remove glass/gradient defaults.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/FieldnotesTokens.kt` — spacing, radius, width breakpoint, and motion constants.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/components/EditorialTopBar.kt` — compact wordmark, section title, and contextual actions.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/components/ArticleCard.kt` — hero, standard, and compact editorial treatments.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/components/SectionHeader.kt` — section title plus optional text action.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/components/EditorialNativeAd.kt` — app-level styling around the SDK `NativeAdView` loading/failure contract.
- Modify existing generic loading, empty, and error components to use flat editorial hierarchy rather than nested cards.

### Navigation and shell

- Modify `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKey.kt` — declare Today, Discover, Library, Profile, SDK Lab, article, and Lab child routes.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavigationState.kt` — selected tab plus one retained `SnapshotStateList<NavKey>` per top-level tab.
- Modify `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavHost.kt` — render the selected retained stack and direct bottom bar/rail.
- Modify `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt` — own navigation state, saveable entry state, and app-wide native session identities.
- Delete the old floating bottom-bar wrapper only after navigation tests pass.

### Consumer destinations

- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/today/TodayContract.kt`.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/today/TodayViewModel.kt`.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/today/TodayScreen.kt`.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/discover/DiscoverContract.kt`.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/discover/DiscoverViewModel.kt`.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/discover/DiscoverScreen.kt`.
- Modify Library presentation files for the new visual system and explicit Saved/History segments.
- Replace Settings presentation with `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/profile/ProfileContract.kt`, `ProfileViewModel.kt`, and `ProfileScreen.kt`.
- Modify Article presentation files for editorial reading, inline native placement, and reader controls.
- Remove obsolete Feed/Store/Settings presentation files after all routes and tests point to their replacements; retain reusable domain/data code.

### SDK Lab

- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/lab/SdkLabScreen.kt` — format index and global SDK health.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/lab/BannerLabScreen.kt`.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/lab/NativeLabScreen.kt`.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/lab/FullScreenLabScreen.kt`.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/lab/PrivacyLabScreen.kt`.
- Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/lab/DiagnosticsLabScreen.kt`.
- Move reusable reward actions from Store into the Lab without changing wallet accounting.
- Modify the Showcase ad-placement catalog so every SDK format has an official test placement and a human-readable Lab description.

### Tests and documentation

- Create navigation-state tests under `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/nav/`.
- Modify `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/domain/feed/FeedAdInserter.kt` and its existing test for the Today cadence and revisioned stable keys.
- Add Discover query and session-key tests.
- Add Lab route-completeness and placement-wiring tests.
- Add theme token and contrast-role tests where pure Kotlin checks are possible.
- Create `showcase/README.md` if the Showcase module has no focused guide; otherwise update the existing sample guide with architecture, ad behavior, and manual QA.

---

### Task 1: Freeze behavior with shell and insertion-policy tests

**Files:**
- Create: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavigationStateTest.kt`
- Modify: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/domain/feed/FeedAdInsertionTest.kt`
- Modify: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKeyTest.kt`

- [ ] **Step 1: Write failing independent-stack tests**

```kotlin
@Test
fun switchingTabs_preservesEachTabsBackStack() {
    val state = ShowcaseNavigationState.initial()

    state.push(ArticleRoute("today-42"))
    state.select(ShowcaseTab.Profile)
    state.push(SdkLabRoute)
    state.select(ShowcaseTab.Today)

    assertEquals(ArticleRoute("today-42"), state.currentRoute)
    state.select(ShowcaseTab.Profile)
    assertEquals(SdkLabRoute, state.currentRoute)
}

@Test
fun reselectingCurrentTab_popsThatTabToRoot() {
    val state = ShowcaseNavigationState.initial()
    state.push(ArticleRoute("today-42"))

    state.select(ShowcaseTab.Today)

    assertEquals(TodayRoute, state.currentRoute)
}
```

- [ ] **Step 2: Write failing stable insertion tests**

```kotlin
@Test
fun insertsFirstAdAfterFourArticles_thenEveryEight() {
    assertEquals(
        listOf(3, 11, 19, 27),
        (0 until 28).filter(FeedAdInserter::shouldInsertAfter),
    )
}

@Test
fun slotKey_isRevisionedAndAnchoredToArticleIdentity() {
    assertEquals(
        "today:seed-v1:article-011",
        FeedAdInserter.slotKeyAfter(articleId = "article-011"),
    )
}
```

- [ ] **Step 3: Run the focused tests and record the expected failures**

Run: `./gradlew :showcase:testAndroidHostTest --tests '*ShowcaseNavigationStateTest' --tests '*FeedAdInsertionTest' --no-configuration-cache`

Expected: FAIL because the new state and insertion policy do not exist.

- [ ] **Step 4: Preserve the tests as the migration safety net; do not alter production code in this task**

**Acceptance:** Failures are missing-symbol/behavior failures, not broken test setup.

---

### Task 2: Replace Emerald Glass with the Fieldnotes design system

**Files:**
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Color.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Type.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/Theme.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/theme/FieldnotesTokens.kt`
- Create: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/ui/theme/FieldnotesThemeTest.kt`

- [ ] **Step 1: Add tests for all semantic light/dark roles and locked constants**

```kotlin
@Test
fun fieldnotesLightColors_matchApprovedPalette() {
    assertEquals(Color(0xFFF5F0E6), FieldnotesLight.page)
    assertEquals(Color(0xFF181713), FieldnotesLight.ink)
    assertEquals(Color(0xFFC6452D), FieldnotesLight.accent)
}

@Test
fun compactNavigationBreakpoint_is840Dp() {
    assertEquals(840.dp, FieldnotesTokens.navigationRailBreakpoint)
}
```

- [ ] **Step 2: Implement tokens and map them into Material semantic roles**

Map `page -> background`, `raised surface -> surface`, `ink -> onBackground/onSurface`, `accent -> primary`, `accent container -> primaryContainer`, and `rule -> outlineVariant`. Define disabled colors from semantic alpha, not new hex values.

- [ ] **Step 3: Define typography and shapes**

Use the locked scale above. Keep buttons and inputs readable with sans serif; reserve serif for editorial titles and quotations.

- [ ] **Step 4: Remove old theme names, glass brushes, and gradient helpers from call sites**

Run: `rg -n 'Emerald|Glass|gradient|Brush\.' showcase/src/commonMain/kotlin/dev/avinya/admob/showcase`

Expected: no remaining design-system references; content images may still use an image-specific scrim when text overlays require contrast.

- [ ] **Step 5: Run focused tests and compile common metadata**

Run: `./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache`

Expected: PASS.

**Acceptance:** Light and dark themes use the locked palette; no remote font or decorative glass dependency is introduced.

---

### Task 3: Build the editorial component grammar

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/components/EditorialTopBar.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/components/ArticleCard.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/components/SectionHeader.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/components/EditorialNativeAd.kt`
- Modify: existing loading, empty, and error components

- [ ] **Step 1: Define small, content-driven component APIs**

```kotlin
@Composable
fun ArticleCard(
    article: ArticleCardModel,
    treatment: ArticleCardTreatment,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier,
)

enum class ArticleCardTreatment { Hero, Standard, Compact }

data class ArticleCardModel(
    val id: String,
    val title: String,
    val author: String,
    val section: String,
    val readTimeMinutes: Int,
    val snippet: String,
    val isPremium: Boolean,
)
```

Avoid a universal card with dozens of visual flags. `ArticleCard` owns only shared article semantics; feed grouping stays with the destination.

- [ ] **Step 2: Implement accessible content hierarchy**

Use one merged click target for the article, a separately labeled bookmark action, minimum `48.dp` touch targets, explicit image descriptions only when the image adds information, and headings that remain meaningful without color.

- [ ] **Step 3: Implement `EditorialNativeAd` as an app skin over the SDK session API**

```kotlin
@Composable
fun EditorialNativeAd(
    session: NativeAdSession,
    slotKey: String,
    placement: AdPlacement.Native,
    modifier: Modifier = Modifier,
) {
    val slotState by session.slotState(slotKey).collectAsState()
    val renderable = slotState is NativeAdSlotState.Ready ||
        slotState is NativeAdSlotState.Retained ||
        slotState is NativeAdSlotState.Mounted
    AnimatedVisibility(
        visible = renderable,
        enter = fadeIn(tween(FieldnotesTokens.adRevealMillis)) +
            expandVertically(tween(FieldnotesTokens.adRevealMillis)),
    ) {
        NativeAdView(
            session = session,
            slotKey = slotKey,
            placement = placement,
            layout = NativeAdLayout.Medium,
        )
    }
}
```

Do not create a permanent skeleton at full ad height. A compact labeled loading line is allowed in SDK Lab only.

- [ ] **Step 4: Add previews or deterministic sample fixtures for light/dark and compact/expanded widths**

- [ ] **Step 5: Compile**

Run: `./gradlew :showcase:compileKotlinIosArm64 --no-configuration-cache`

Expected: PASS.

**Acceptance:** Screens can be assembled from flat sections, typographic hierarchy, rules, imagery, and a small number of purposeful surfaces.

---

### Task 4: Implement retained top-level navigation and grounded navigation chrome

**Files:**
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavKey.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavigationState.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavHost.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/nav/ShowcaseNavigationStateTest.kt`

- [ ] **Step 1: Implement one persistent stack per top-level destination**

```kotlin
enum class ShowcaseTab(val root: NavKey) {
    Today(TodayRoute),
    Discover(DiscoverRoute),
    Library(LibraryRoute),
    Profile(ProfileRoute),
}

class ShowcaseNavigationState internal constructor(
    initialTab: ShowcaseTab,
    private val stacks: Map<ShowcaseTab, SnapshotStateList<NavKey>>,
) {
    var selectedTab by mutableStateOf(initialTab)
        private set

    val currentStack: SnapshotStateList<NavKey> get() = stacks.getValue(selectedTab)
    val currentRoute: NavKey get() = currentStack.last()

    fun select(tab: ShowcaseTab) {
        if (tab == selectedTab) stacks.getValue(tab).retainRoot()
        else selectedTab = tab
    }
}
```

Keep route values and saveable UI values in the root-owned tab state; never attempt to save native ad objects. Process-death restoration is not silently added here: the current project intentionally has no serialization dependency, and a recreated process must create new SDK sessions and ads.

- [ ] **Step 2: Wire Navigation 3 state retention at `ShowcaseApp` scope**

Use saveable-state and ViewModel-store entry decorators already supported by the project. The selected stack leaves composition on tab switch, but its routes, saveable UI state, ViewModels, and SDK native-ad session remain owned above that composition.

- [ ] **Step 3: Replace the floating bar with direct window-edge navigation**

Compact pseudocode:

```kotlin
Scaffold(
    bottomBar = {
        NavigationBar {
            ShowcaseTab.entries.forEach { tab ->
                NavigationBarItem(/* no wrapping Surface or outer padding */)
            }
        }
    },
) { contentPadding ->
    NavigationContent(Modifier.padding(contentPadding))
}
```

Consume `Scaffold` padding exactly once. Do not add a second bottom spacer equal to the navigation bar height. Content does not need to draw behind the opaque bar.

- [ ] **Step 4: Add the `840.dp` rail switch without changing stacks**

Use `BoxWithConstraints` or the project's existing size-class abstraction. Expanded mode places one `NavigationRail` beside content; it does not instantiate a second `NavDisplay`.

- [ ] **Step 5: Run navigation tests and compile**

Run: `./gradlew :showcase:testAndroidHostTest --tests '*ShowcaseNavigationStateTest' :showcase:compileKotlinIosArm64 --no-configuration-cache`

Expected: PASS.

**Acceptance:** Opening a child screen under Profile, switching tabs, and returning restores the same child route and ViewModel state. Reselecting the current tab returns only that tab to its root.

---

### Task 5: Build Today and integrate viewport-aware native sessions

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/today/TodayContract.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/today/TodayViewModel.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/today/TodayScreen.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/domain/feed/FeedAdInserter.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/domain/feed/FeedItem.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/ArticleRepository.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/domain/feed/FeedAdInsertionTest.kt`

- [ ] **Step 1: Keep the existing sealed paging rows and update their contract for SDK sessions**

```kotlin
sealed interface FeedItem {
    val key: String

    data class Article(/* existing editorial fields and feedOrdinal */) : FeedItem

    data class NativeAdSlot(val slotKey: String) : FeedItem {
        override val key = "native:$slotKey"
    }
}
```

The repository continues to return `PagingData<FeedItem.Article>` and the ViewModel continues to use `insertSeparators`; do not replace Paging with a fully materialized list. The row stores a stable slot key, never a platform native-ad instance.

- [ ] **Step 2: Implement the locked insertion policy**

Set `FIRST_AD_AFTER = 4`, `REPEAT_INTERVAL = 8`, and `TODAY_FEED_REVISION = "seed-v1"` in `FeedAdInserter`. `shouldInsertAfter(feedOrdinal)` returns true for ordinals `3, 11, 19, ...`. `slotKeyAfter(articleId)` returns `today:seed-v1:{articleId}`. Bump the revision only when editorial ordering/slot policy intentionally invalidates prior slot identity.

- [ ] **Step 3: Create one Today session above list-item composition**

```kotlin
val nativeSession = rememberNativeAdFeedSession(
    sessionKey = "today:$TODAY_FEED_REVISION",
    listState = listState,
    itemCount = items.itemCount,
    slotAt = { index ->
        (items.peek(index) as? FeedItem.NativeAdSlot)?.let { row ->
            NativeAdSlot(row.slotKey, ShowcasePlacements.feedNative)
        }
    },
)
```

Keep `LazyPagingItems` and key rows with `items.itemKey(FeedItem::key)`. `EditorialNativeAd` renders only loaded slots; scrolling away detaches the platform view but does not independently destroy the session-owned ad. Remove the current feed-bottom `BannerAdView`; banner demonstration moves to SDK Lab.

- [ ] **Step 4: Assemble a recognizably editorial feed**

Use one hero story, a concise date/edition header, ruled section changes, standard story rows, and compact saved-state affordances. Avoid placing every story in an identical rounded card.

- [ ] **Step 5: Delete or migrate old Feed presentation only after route and test coverage move to Today**

- [ ] **Step 6: Run tests**

Run: `./gradlew :showcase:testAndroidHostTest --tests '*FeedAdInsertionTest' --tests '*Today*' :showcase:compileKotlinIosArm64 --no-configuration-cache`

Expected: PASS.

**Acceptance:** Scrolling away from and back to a retained slot shows the same loaded ad identity. Switching Today → Profile → Today does not request replacement ads while the Today session remains retained and within policy.

---

### Task 6: Build Discover with query-scoped feed sessions

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/discover/DiscoverContract.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/discover/DiscoverViewModel.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/discover/DiscoverScreen.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/ArticleDao.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/ArticleRepository.kt`
- Create: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/feature/discover/DiscoverViewModelTest.kt`

- [ ] **Step 1: Add repository queries for categories and normalized local search**

Add a Room `PagingSource` query ordered by `feedOrdinal` that applies an optional exact `section` and a committed search string across `title`, `author`, `section`, and `body` with `LIKE '%' || :query || '%'`. Add `SELECT DISTINCT section FROM articles ORDER BY section`. Trim queries, collapse repeated whitespace, and debounce UI input by `250 ms`. Empty query returns the editorial category landing page rather than querying every column with wildcards.

- [ ] **Step 2: Test query cancellation and deterministic result state**

```kotlin
@Test
fun changingQuery_cancelsOldResults_andChangesSessionIdentity() = runTest {
    viewModel.onQueryChanged("compose")
    advanceTimeBy(250)
    viewModel.onQueryChanged("privacy")
    advanceUntilIdle()

    assertEquals("privacy", viewModel.state.value.normalizedQuery)
    assertEquals("discover:search:privacy", viewModel.state.value.nativeSessionKey)
}
```

- [ ] **Step 3: Build category chips, search, and mixed result layout**

Chips are filters, not ornamental pills. Expose selected state through semantics. Use a two-column story grid only when each column remains at least `280.dp`; otherwise use the standard vertical treatment.

- [ ] **Step 4: Integrate a separate SDK session per normalized query/category context**

Close obsolete sessions through the manager when query history ages out; do not accumulate a session for every keystroke. Only debounced, committed query values receive session identities.

- [ ] **Step 5: Run tests**

Run: `./gradlew :showcase:testAndroidHostTest --tests '*Discover*' :showcase:compileKotlinIosArm64 --no-configuration-cache`

Expected: PASS.

**Acceptance:** Search is stable across tab switches, stale query results cannot overwrite current results, and ad retention for Discover does not alter Today's configured active/inactive window.

---

### Task 7: Redesign Library and replace Settings with Profile

**Files:**
- Modify: existing Library contract, ViewModel, screen, and tests
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/profile/ProfileContract.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/profile/ProfileViewModel.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/profile/ProfileScreen.kt`
- Delete: old Settings presentation files after migration

- [ ] **Step 1: Preserve Library persistence behavior and expose Saved/History as an explicit segment**

Use a flat list, count/context line, strong empty states, and the same article row grammar as Today. Do not insert ads into Library.

- [ ] **Step 2: Port Settings state/actions into Profile before changing presentation**

Keep consent status, privacy options, theme selection, diagnostics, and reset actions behaviorally identical. Add SDK Lab navigation as a route action, not an embedded gallery.

- [ ] **Step 3: Build Profile as grouped text rows with rules**

Use sections for Reading, Appearance, Privacy, Rewards, and Developer. Reserve filled surfaces for destructive confirmation and current consent status, not every row.

- [ ] **Step 4: Verify tab-switch retention**

Add a state test where Profile → SDK Lab → Today → Profile returns to SDK Lab without re-running Profile initialization.

- [ ] **Step 5: Run focused and full shared tests**

Run: `./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache`

Expected: PASS.

**Acceptance:** Existing bookmarks/history/settings remain intact across the UI migration; Profile state and child route survive tab switches.

---

### Task 8: Redesign the article reader and place one inline native ad safely

**Files:**
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/article/ArticleContract.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/article/ArticleViewModel.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/article/ArticleScreen.kt`
- Delete: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/ad/AdEffectHandler.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/domain/ad/ShowcasePlacements.kt`
- Create: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/feature/article/ArticleAdPlacementTest.kt`

- [ ] **Step 1: Model article blocks and a deterministic inline-ad anchor**

The ad follows the first substantial body section after at least two paragraphs or `450` rendered characters, whichever boundary comes later. If the article is too short, omit the ad.

```kotlin
@Test
fun shortArticle_hasNoInlineAd() {
    assertFalse(buildArticleBlocks(shortArticle()).any { it is ArticleBlock.NativeAd })
}

@Test
fun inlineAd_isAfterMeaningfulContent_notHeadline() {
    val blocks = buildArticleBlocks(longArticle("article-9"))
    assertEquals("article:article-9:inline-1", blocks.singleAd().slotKey)
    assertTrue(blocks.indexOf(blocks.singleAd()) > 2)
}
```

- [ ] **Step 2: Build a calm reading layout**

Use headline, deck, author/date metadata, optional hero image, readable measure capped near `720.dp`, serif headline, sans-serif body, and a single persistent bookmark action. Remove decorative containers around paragraphs.

- [ ] **Step 3: Create the article native session at route scope**

The article session is independent of Today and Discover. It uses the same manager-level governor, so opening an article cannot bypass the app-wide limit. Closing the article deactivates/closes its session according to the SDK lifecycle contract.

- [ ] **Step 4: Explicitly prohibit disruptive formats in reader navigation**

Remove `ArticleEffect.ShowInterstitial`, `ArticleEffect.AdSuppressed`, close-time `AdPolicy` evaluation, `AdEffectHandler`, and the article-bottom banner. `ArticleIntent.Close` emits only `NavigateBack`. Keep interstitial/reward policy examples in SDK Lab, where they are explicitly user-triggered. No automatic interstitial on open/back, no sticky banner, and no full-screen format tied to reading progress.

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :showcase:testAndroidHostTest --tests '*Article*' :showcase:compileKotlinIosArm64 --no-configuration-cache`

Expected: PASS.

**Acceptance:** Long articles contain at most one naturally spaced inline native ad; short articles contain none; the reader remains useful with ads unavailable.

---

### Task 9: Build SDK Lab and exercise every SDK format deliberately

**Files:**
- Create: all files listed under **SDK Lab** in File Structure
- Modify: Store/reward files and tests
- Modify: Showcase ad-placement catalog
- Create: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/feature/lab/SdkLabCoverageTest.kt`

- [ ] **Step 1: Define Lab routes by concern, not one endless component screen**

```kotlin
sealed interface SdkLabDestination : NavKey {
    data object Banner : SdkLabDestination
    data object Native : SdkLabDestination
    data object FullScreen : SdkLabDestination
    data object Privacy : SdkLabDestination
    data object Diagnostics : SdkLabDestination
}
```

- [ ] **Step 2: Add a coverage test tying supported formats to a Lab destination and placement**

```kotlin
@Test
fun everySupportedAdFormat_hasALabScenario() {
    assertEquals(
        SupportedAdFormat.entries.toSet(),
        SdkLabScenario.entries.map { it.format }.toSet(),
    )
}
```

- [ ] **Step 3: Implement format-specific experiences**

- Banner: adaptive and fixed-size examples in a bounded preview area, plus load/error events.
- Native: compact/medium layouts, stable slot keys, session retention counter, explicit close/recreate session controls.
- Full-screen: interstitial, rewarded, and rewarded-interstitial with separate Load and Show actions and readiness labels.
- Privacy: consent state, privacy-options entry, ATT status where relevant, and documented initialization order.
- Diagnostics: SDK initialization, test mode, placement mapping, current native session/governor summary, and recent sanitized events.

- [ ] **Step 4: Preserve reward accounting exactly once**

Move Store reward actions and ViewModel logic into Full-screen Lab naming without duplicating reward callbacks. Tests must prove one earned reward produces one wallet mutation even if a screen recomposes.

- [ ] **Step 5: Ensure all examples use official test IDs with `strictTestMode`**

The sample must fail closed if a production ID is supplied in strict test mode.

- [ ] **Step 6: Run Lab tests and compile**

Run: `./gradlew :showcase:testAndroidHostTest --tests '*SdkLab*' --tests '*Reward*' :showcase:compileKotlinIosArm64 --no-configuration-cache`

Expected: PASS.

**Acceptance:** Every public format has one intentional, inspectable Lab scenario; primary consumer tabs remain free of demo controls.

---

### Task 10: Make onboarding and app-open behavior product-safe

**Files:**
- Modify: existing onboarding screen/state files
- Modify: Showcase app-open coordinator integration
- Add/modify: onboarding and app-open eligibility tests

- [ ] **Step 1: Reduce onboarding to product purpose, privacy choice, and one clear continuation path**

Explain that Fieldnotes is an SDK showcase without presenting a wall of feature cards. Keep consent ownership with the SDK flow; do not add a competing consent model in UI state.

- [ ] **Step 2: Extract a pure app-open eligibility policy and test exclusions**

```kotlin
@Test
fun firstSessionAndSensitiveFlows_areNeverEligibleForAppOpen() {
    assertFalse(policy.isEligible(firstSessionContext))
    assertFalse(policy.isEligible(onboardingContext))
    assertFalse(policy.isEligible(privacyOptionsContext))
}
```

Eligibility requires completed initialization/consent, a later foreground transition, minimum background duration, no full-screen ad showing, and no sensitive route.

- [ ] **Step 3: Wire the existing coordinator to that policy**

Do not add arbitrary delayed popups. Record sanitized lifecycle decisions in Diagnostics so SDK behavior is demonstrable.

- [ ] **Step 4: Run tests**

Run: `./gradlew :showcase:testAndroidHostTest --tests '*Onboarding*' --tests '*AppOpen*' --no-configuration-cache`

Expected: PASS.

**Acceptance:** Fresh installs and privacy flows cannot be interrupted by app-open ads; later eligible foregrounds still exercise the SDK.

---

### Task 11: Complete adaptive, inset, accessibility, and failure-state polish

**Files:**
- Modify: destination screens and shared components from Tasks 2–10
- Modify: platform wrappers only if an actual safe-area mismatch is demonstrated
- Update: Showcase sample guide

- [ ] **Step 1: Audit compact, medium, and expanded widths**

Required fixtures: `360x800`, `390x844`, `600x960`, and `1024x768`. At `840.dp`, navigation changes from bottom bar to rail without resetting route or scroll state.

- [ ] **Step 2: Verify insets and bottom navigation geometry**

The navigation component consumes its own system-bar insets. Main content receives and consumes scaffold padding once. Lists use only editorial content spacing after the last row, not an extra floating-bar compensation block.

- [ ] **Step 3: Audit accessibility**

Check minimum touch targets, TalkBack/VoiceOver labels, traversal order, text scaling at `1.3x` and `2.0x`, color contrast, visible focus, selected-tab semantics, and non-color loading/error communication.

- [ ] **Step 4: Audit all ad failure paths**

- Slow/unavailable feed native: editorial rows remain contiguous; no permanent empty ad card.
- Evicted retained slot: SDK may reload only when the slot returns to the active window; UI transition remains controlled.
- Full-screen not ready: button communicates state and never calls show.
- Consent prevents requests: consumer surfaces remain complete; Lab explains the state.
- Offline: locally persisted editorial/library content remains accessible.

- [ ] **Step 5: Capture before/after screenshots for owner review**

Capture Today, Discover, Article, Profile, and SDK Lab in light/dark compact layouts, plus Today and Lab in expanded layout. Do not treat screenshots as automated correctness proof.

- [ ] **Step 6: Update sample documentation**

Document destination ownership, stable slot-key construction, native session identities, ad placement rationale, Lab coverage, and manual QA steps. Link to official vendor guidance rather than copying it.

**Acceptance:** No screen clips at tested widths/text scales; the bottom bar touches the window edge without an outer box; content ends naturally above navigation; ad absence does not break layout.

---

### Task 12: Run the full local release gate and hand off for review

**Files:**
- Modify only files needed to fix failures attributable to this redesign
- Do not modify `.github/workflows/release.yml`

- [ ] **Step 1: Inspect the final diff for accidental scope creep and obsolete UI**

Run:

```bash
git status --short
git diff --stat
rg -n 'Emerald|Glass|FeedRoute|StoreRoute|SettingsRoute' showcase/src
```

Expected: only intentionally retained domain terminology remains; no old top-level routes or visual system references survive.

- [ ] **Step 2: Run focused shared checks**

Run: `./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache`

Expected: PASS.

- [ ] **Step 3: Run the repository's mandatory release-readiness script**

Run: `./scripts/release-readiness.sh`

Expected: `READINESS: PASS`, including SDK host tests, publication metadata/task graph, iOS tests and ABI, Maven Local consumer round trip, Xcode consumer build, Dokka, Astro build, docs tests, and docs verification.

- [ ] **Step 4: Perform manual Android and iOS smoke tests**

Verify:

1. Onboarding and consent complete without app-open interruption.
2. Today native slots reveal smoothly and keep identity across reverse scroll.
3. Today → Profile → Today preserves scroll position and retained ad identity.
4. Profile → SDK Lab → Today → Profile restores the Lab child route.
5. Discover query/category state survives a tab switch and does not disturb Today retention.
6. Article inline native appears only after meaningful content and disappears safely when unavailable.
7. Each SDK Lab format loads/shows or reports an honest unavailable state.
8. Reward callbacks mutate the wallet once.
9. Compact bottom navigation and expanded rail have no outer floating container or duplicate bottom padding.
10. Critical-memory simulation, where available, reduces native retention without destroying mounted ads.

- [ ] **Step 5: Report results and stop for explicit owner confirmation**

Report every release-readiness section that ran, anything skipped, failures fixed, device/simulator coverage, and remaining visual risks. A green gate is not authorization to commit, push, or open a PR.

**Acceptance:** Automated gate passes, Android/iOS smoke behavior matches the locked contract, screenshots are ready for human review, and no repository handoff action occurs without explicit approval.

---

## Implementation Order and Checkpoints

1. Execute the native-ad session architecture plan and obtain owner review.
2. Tasks 1–4 establish contracts, tokens, components, and navigation. Pause for a shell/visual checkpoint.
3. Tasks 5–8 build the consumer product and natural native placements. Pause for feed/reader behavior review.
4. Tasks 9–10 complete SDK breadth without contaminating primary UX. Pause for Lab and ad-policy review.
5. Tasks 11–12 perform adaptive/a11y polish and the mandatory full local gate.

## Explicit Non-Goals

- Rebuilding article ingestion or inventing a remote CMS.
- Adding accounts, cloud sync, social feeds, comments, or subscriptions.
- Keeping every top-level screen permanently composed.
- Retaining an unlimited number of native ads to make every historical slot immortal.
- Hiding attached platform ad views off-screen to preserve them.
- Using production ad IDs in the Showcase app.
- Turning the main navigation into a permanent SDK demo menu.

## Reference Guidance to Re-check During Execution

- Android native ads: <https://developers.google.com/admob/android/next-gen/native>
- iOS native ads: <https://developers.google.com/admob/ios/native>
- Android app-open ads: <https://developers.google.com/admob/android/next-gen/app-open>
- iOS app-open ads: <https://developers.google.com/admob/ios/app-open>
- Compose Material inset handling: <https://developer.android.com/develop/ui/compose/system/material-insets>
- Android adaptive navigation: <https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation>
- Apple tab bars: <https://developer.apple.com/design/human-interface-guidelines/tab-bars>

These references guide platform-safe behavior. The locked Fieldnotes product decisions above remain the implementation contract unless the owner explicitly changes them.
