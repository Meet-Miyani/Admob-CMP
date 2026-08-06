# Showcase — Phase 3: Feed & First Ads

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A paged article feed with native ads inserted every 6th item and an anchored adaptive banner — the first rendered ads in the showcase, and the hardest integration in the whole project.

**Architecture:** Paging 3 over a Room `PagingSource`. Ad slots are injected into the `PagingData` stream by a **pure** `FeedAdInserter` whose slot keys derive from the preceding article's id, never from an index. `NativeAdView` handles pool acquire/release; the feed only supplies a stable `itemKey`.

**Tech Stack:** Paging 3.5.0, Room 2.8.4 (+ `room-paging`), Navigation3, Compose Multiplatform 1.11.1.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** Phase 2 complete — `AdManagerStatus.Ready` is reachable and `LocalAdManager` is provided.

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/`. Record gaps in `docs/showcase-sdk-gaps.md`, work around inside `:showcase`, escalate. **Stop and ask.**
- **Kotlin stays at 2.3.20.**
- **No new dependencies.** `androidx-paging-common`, `androidx-paging-compose` and `androidx-room-paging` are already declared in `gradle/libs.versions.toml` and merely need referencing from `showcase/build.gradle.kts`. Anything beyond that needs the owner's consent.
- **Testing principle.** Test what a consumer would copy. Ad-slot index maths and key stability are pure functions and must be tested as such. Do not add a test runtime to observe Paging or Compose.
- **Do not modify** `gradle.properties`, the plugin's `gradle.properties`, or `.github/workflows/release.yml`. **Do not commit** `api/*.klib.api` changes.
- Package root `dev.avinya.admob.showcase`. Branch `feat/showcase-app`. No PR without the owner's confirmation.
- Commits end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

### The rule this phase exists to demonstrate

Per `admob-cmp/AGENTS.md` hard rule 7, controllers are cached per `AdPlacement.id` for the manager's lifetime and are **never evicted**. Generating per-item placement ids (`"feed_item_$index"`) leaks a controller per row, permanently.

So: **one** placement id for the whole feed (`feed_native`), and per-item ads come from the pool via `NativeAdView`'s `itemKey`. The `itemKey` must be **stable across page loads** — derive it from the preceding article's id, never from a list index, because indices shift as pages prepend or refresh and a changed key thrashes the pool.

---

## Tasks

### Task 1: Paging source and the feed's data stream

**Files:**
- Modify: `showcase/build.gradle.kts`
- Modify: `showcase/src/commonMain/.../data/db/dao/ArticleDao.kt`
- Create: `showcase/src/commonMain/.../domain/feed/FeedItem.kt`
- Modify: `showcase/src/commonMain/.../data/repo/ArticleRepository.kt`

**Interfaces:**
- Produces: `sealed interface FeedItem { data class Article(...); data class NativeAdSlot(val slotKey: String) }`, `ArticleRepository.feedPager(): Flow<PagingData<FeedItem.Article>>`. Task 2 consumes both.

- [ ] **Step 1: Reference the already-approved Paging dependencies**

In `showcase/build.gradle.kts`, add to `commonMain`:

```kotlin
                implementation(libs.androidx.paging.common)
                implementation(libs.androidx.paging.compose)
                implementation(libs.androidx.room.paging)
```

- [ ] **Step 2: Add the PagingSource query**

In `ArticleDao.kt`:

```kotlin
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun pagingSource(): PagingSource<Int, ArticleEntity>
```

with `import androidx.paging.PagingSource`.

- [ ] **Step 3: Define the feed item model**

Create `showcase/src/commonMain/.../domain/feed/FeedItem.kt`:

```kotlin
package dev.avinya.admob.showcase.domain.feed

/** One row in the feed: either real content or an ad slot. */
sealed interface FeedItem {

    /** Stable identity for Compose's `key` and for Paging diffing. */
    val key: String

    data class Article(
        val id: String,
        val title: String,
        val author: String,
        val section: String,
        val readTimeMin: Int,
        val isPremium: Boolean,
    ) : FeedItem {
        override val key: String get() = "article_$id"
    }

    /**
     * A native ad slot.
     *
     * [slotKey] is derived from the article it follows, never from a list
     * index — see [FeedAdInserter]. It is passed to `NativeAdView` as
     * `itemKey`, which is what binds a pooled ad to this row.
     */
    data class NativeAdSlot(val slotKey: String) : FeedItem {
        override val key: String get() = slotKey
    }
}
```

- [ ] **Step 4: Expose the pager from the repository**

```kotlin
    fun feedPager(): Flow<PagingData<FeedItem.Article>> = Pager(
        config = PagingConfig(pageSize = FEED_PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { articleDao.pagingSource() },
    ).flow.map { paging -> paging.map(ArticleEntity::toFeedArticle) }
```

with `const val FEED_PAGE_SIZE = 20` and a private `ArticleEntity.toFeedArticle()` mapper.

- [ ] **Step 5: Verify compilation on both platforms, then commit**

---

### Task 2: `FeedAdInserter` — the pure rule, and the reason Paging is here

**Files:**
- Create: `showcase/src/commonMain/.../domain/feed/FeedAdInserter.kt`
- Test: `showcase/src/commonTest/.../domain/feed/FeedAdInsertionTest.kt`

**Interfaces:**
- Produces: `object FeedAdInserter { const val AD_INTERVAL = 6; fun shouldInsertAfter(indexInPage: Int): Boolean; fun slotKeyAfter(articleId: String): String }`. Task 3 applies it via `insertSeparators`.

- [ ] **Step 1: Write the failing test**

These are the assertions that make the whole phase worth doing.

```kotlin
package dev.avinya.admob.showcase.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedAdInsertionTest {

    @Test
    fun insertsAfterEverySixthItem() {
        val positions = (0 until 18).filter { FeedAdInserter.shouldInsertAfter(it) }

        assertEquals(listOf(5, 11, 17), positions)
    }

    @Test
    fun doesNotInsertBeforeTheFirstSixItems() {
        (0..4).forEach { assertFalse(FeedAdInserter.shouldInsertAfter(it), "unexpected slot at $it") }
    }

    @Test
    fun slotKeysDeriveFromTheArticleTheyFollowNotFromAnIndex() {
        assertEquals("feed_native_after_article-005", FeedAdInserter.slotKeyAfter("article-005"))
    }

    @Test
    fun theSameArticleAlwaysYieldsTheSameSlotKeyAcrossPageLoads() {
        // Page 1 loads, then a refresh re-emits the same articles. If keys were
        // index-derived they would be identical here too, so this alone is not
        // enough — see the prepend case below.
        val first = FeedAdInserter.slotKeyAfter("article-011")
        val afterRefresh = FeedAdInserter.slotKeyAfter("article-011")

        assertEquals(first, afterRefresh)
    }

    @Test
    fun aPrependDoesNotShiftExistingSlotKeys() {
        // THE case that index-derived keys get wrong. Article-011 sat at index
        // 11 before the prepend and index 14 after it; its slot key must not
        // change, or the pooled ad bound to that row is discarded and
        // re-acquired for no reason.
        val beforePrepend = FeedAdInserter.slotKeyAfter("article-011")

        val threeNewArticlesArrive = listOf("article-a", "article-b", "article-c")
        val afterPrepend = FeedAdInserter.slotKeyAfter("article-011")

        assertEquals(beforePrepend, afterPrepend)
        assertTrue(threeNewArticlesArrive.none { FeedAdInserter.slotKeyAfter(it) == beforePrepend })
    }

    @Test
    fun distinctArticlesNeverCollideOnAKey() {
        val keys = (0 until 200).map { FeedAdInserter.slotKeyAfter("article-$it") }

        assertEquals(keys.size, keys.toSet().size)
    }
}
```

- [ ] **Step 2: Run to verify it fails**, then implement:

```kotlin
package dev.avinya.admob.showcase.domain.feed

/**
 * Decides where native ad slots go in the feed, and what they are keyed by.
 *
 * Pure on purpose. Slot placement and key derivation are the two things a
 * feed integration most often gets wrong, and both are testable as values —
 * no Paging, no Compose, no SDK.
 */
object FeedAdInserter {

    /** One ad per six articles. Frequent enough to demo, sparse enough to be plausible. */
    const val AD_INTERVAL: Int = 6

    /** True when a slot belongs immediately after the item at [indexInPage]. */
    fun shouldInsertAfter(indexInPage: Int): Boolean =
        indexInPage >= AD_INTERVAL - 1 && (indexInPage + 1) % AD_INTERVAL == 0

    /**
     * The `itemKey` for the slot following [articleId].
     *
     * Derived from the article's identity, **never** from its position.
     * Positions shift on prepend and refresh; a changed `itemKey` makes
     * `NativeAdView` release its pooled ad and acquire another, which both
     * wastes inventory and makes ads visibly flicker as the user scrolls.
     */
    fun slotKeyAfter(articleId: String): String = "feed_native_after_$articleId"
}
```

- [ ] **Step 3: Run on both platforms and commit**

---

### Task 3: Native ads in the paged feed

**Files:**
- Create: `showcase/src/commonMain/.../ui/ad/FeedNativeAdLayout.kt`
- Create: `showcase/src/commonMain/.../feature/feed/FeedContract.kt`
- Create: `showcase/src/commonMain/.../feature/feed/FeedViewModel.kt`
- Create: `showcase/src/commonMain/.../feature/feed/FeedScreen.kt`
- Create: `showcase/src/commonMain/.../domain/ad/ShowcasePlacements.kt`

**Interfaces:**
- Produces: `object ShowcasePlacements` with all eight `AdPlacement` values (later phases consume the rest), `feedNativeAdLayout: AdLayout`, `@Composable fun FeedScreen(onArticleClick: (String) -> Unit)`.

- [ ] **Step 1: Create the placement catalog**

All eight placements defined now so later phases just reference them. Use `TestAdIds` constants and `strictTestMode = true` — the latter **throws at construction** if a placement ever points at a production unit.

```kotlin
package dev.avinya.admob.showcase.domain.ad

import dev.avinya.ads.*
import kotlin.time.Duration.Companion.seconds

/**
 * Every placement the showcase uses — a **static, finite** catalog.
 *
 * Controllers are cached per `AdPlacement.id` for the manager's lifetime and
 * never evicted, so generated per-item ids leak permanently. The feed serves
 * per-item ads from the native pool, keyed by `itemKey`, not by minting a
 * placement per row.
 */
object ShowcasePlacements {

    val feedBanner = AdPlacement(
        id = "feed_banner",
        format = AdFormat.Banner,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_BANNER, ios = TestAdIds.IOS_BANNER),
        bannerSizePolicy = AdSizePolicy.LargeAnchoredAdaptive(),
        bannerRefreshPolicy = BannerRefreshPolicy.SdkManaged(60.seconds),
        strictTestMode = true,
    )

    val feedNative = AdPlacement(
        id = "feed_native",
        format = AdFormat.Native,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_NATIVE, ios = TestAdIds.IOS_NATIVE),
        cachePolicy = AdCachePolicy(maxSize = 5, reloadAfterShow = true),
        strictTestMode = true,
    )

    // article*, store* and appOpen placements follow in Phases 4-6, defined here
    // so the catalog stays in one readable file.
}
```

Constructor names above are **verified** against `admob-cmp-core` source:

```
AdPlacement(id, format, adUnitIds, requestOptions, cachePolicy, retryPolicy,
            timeoutPolicy, bannerSizePolicy, bannerRefreshPolicy, nativeOptions,
            fullScreenOptions, enabled, strictTestMode)
AdUnitIds(android: String, ios: String)
AdCachePolicy(maxSize: Int = 1, expirationPolicy: AdExpirationPolicy = …,
              reloadAfterShow: Boolean = false)
```

Note there is no `serverSideVerificationOptions` parameter — SSV lives inside `fullScreenOptions` (see the Phase 5 plan).

- [ ] **Step 2: Build the native ad layout**

```kotlin
val feedNativeAdLayout: AdLayout = adLayout {
    column(modifier = AdModifier.fillMaxWidth()) {
        row(spacing = 8.dp) {
            icon(modifier = AdModifier.size(40.dp))
            column { headline(maxLines = 2); advertiser() }
            adBadge()
        }
        media(modifier = AdModifier.fillMaxWidth().aspectRatio(16f / 9f))
        body(maxLines = 3)
        callToAction(modifier = AdModifier.fillMaxWidth())
    }
}
```

`adBadge()` is policy-required — the SDK's validator warns without it. DSL nodes are functions taking named arguments, not property-assignment blocks.

- [ ] **Step 3: Assemble the feed**

`FeedScreen` collects `feedPager().collectAsLazyPagingItems()`, applies `insertSeparators` using `FeedAdInserter`, and renders in a `LazyColumn` with `key = { it.key }`:

```kotlin
is FeedItem.NativeAdSlot -> NativeAdView(
    placement = ShowcasePlacements.feedNative,
    itemKey = item.slotKey,
    layout = feedNativeAdLayout,
)
```

Gate ad rows on `adManager.status == Ready` **and** the `adsMasterSwitch` preference; when either is off, render nothing at all — the slot must collapse to zero height, never a "couldn't load" box.

- [ ] **Step 4: Verify on device, then commit**

Scroll the feed and confirm: ads appear roughly every 6th row, they do **not** flicker or re-load when scrolling back up, and no blank gaps remain where an ad failed.

---

### Task 4: Anchored adaptive banner

**Files:**
- Modify: `showcase/src/commonMain/.../feature/feed/FeedScreen.kt`
- Modify: `showcase/src/commonMain/.../nav/ShowcaseNavHost.kt`

- [ ] **Step 1: Place the banner**

`BannerAdView(placement = ShowcasePlacements.feedBanner, modifier = Modifier.fillMaxWidth())`, pinned above the bottom navigation bar. `BannerAdView` measures its own container and supplies the width — do **not** construct `BannerGeometry` by hand here; that is only for headless controller callers.

- [ ] **Step 2: Confirm it collapses cleanly**

With the ads master switch off, or before `Ready`, the banner area must occupy **zero** height — no reserved space, no placeholder.

- [ ] **Step 3: Full verification**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
```

- [ ] **Step 4: Commit**

---

## Exit criteria

- [ ] Feed pages through all 126 articles, 20 at a time
- [ ] Native ads appear after every 6th article on both platforms
- [ ] Scrolling away and back does **not** reload the ad in a given row (stable keys working)
- [ ] `FeedAdInsertionTest` passes on Android host and iOS, including the prepend case
- [ ] Anchored adaptive banner renders and refreshes; collapses to zero height when ads are off
- [ ] Exactly **one** placement id is used for the whole feed
- [ ] `git diff --stat master -- 'admob-cmp*'` is empty

---

## Next plan

**Phase 4 — Article** (`2026-08-06-showcase-phase-4-article.md`): article detail, reading progress, inline native with a second layout, collapsible banner, and `AdPolicy` driving a frequency-capped interstitial.
