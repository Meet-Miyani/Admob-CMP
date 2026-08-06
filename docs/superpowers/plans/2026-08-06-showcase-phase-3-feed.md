# Showcase — Phase 3: Feed & First Ads

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A paged article feed with native ads inserted every 6th item and an anchored adaptive banner — the first rendered ads in the showcase, and the hardest integration in the project.

**Architecture:** Paging 3 over a Room `PagingSource`. Ad slots are injected into the `PagingData` stream by a **pure** `FeedAdInserter` driven by each article's own stable ordinal — never by a list position. `NativeAdView` owns pool acquire/release; the feed only supplies a stable `itemKey`.

**Tech Stack:** Paging 3.5.0, Room 2.8.4 (+ `room-paging`), Navigation3, Compose Multiplatform 1.11.1.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** Phase 2 complete — `AdManagerStatus.Ready` is reachable, `LocalAdManager` is provided, Settings exposes the ads master switch.

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/`. Record gaps in `docs/showcase-sdk-gaps.md`, work around inside `:showcase`, escalate. **Stop and ask.**
- **Kotlin stays at 2.3.20.**
- **No new dependencies.** `androidx-paging-common`, `androidx-paging-compose` and `androidx-room-paging` are already in `gradle/libs.versions.toml` and only need referencing. Anything beyond that needs the owner's consent — including test runtimes.
- **Testing principle.** Test what a consumer would copy. Ad-slot placement and key derivation are pure functions and must be tested as such. Do not add a test runtime to observe Paging or Compose.
- **`remember` keys.** Two nav bugs in Phases 1 and 2 came from over-keying `remember`. Key it on what genuinely invalidates the value and nothing else.
- **Do not modify** `gradle.properties`, the plugin's `gradle.properties`, or `.github/workflows/release.yml`. **Do not commit** `api/*.klib.api` changes.
- Package root `dev.avinya.admob.showcase`. Branch `feat/showcase-app`. No PR without the owner's confirmation.
- Commits end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

### The rule this phase exists to demonstrate

Per `admob-cmp/AGENTS.md` hard rule 7, controllers are cached per `AdPlacement.id` for the manager's lifetime and **never evicted**. Generated per-item ids (`"feed_item_$index"`) leak a controller per row, permanently.

So: **one** placement id for the whole feed, and per-item ads come from the native pool via `NativeAdView`'s `itemKey`. That `itemKey` must be stable across page loads — derived from the article, never from a position.

### Design note — why the article carries an ordinal

Paging's `insertSeparators` hands you **adjacent items, not indices**. There is no reliable index available at insertion time, and a stateful counter is wrong because separators are recomputed lazily.

So placement is decided from stable data on the article itself. `ArticleEntity` gains a `feedOrdinal` column, assigned by the seed. This is the same insight as the key rule: **position is not identity.**

---

## Tasks

### Task 1: Ordinal column, paging source, and the feed's data stream

**Files:**
- Modify: `showcase/build.gradle.kts`
- Modify: `showcase/src/commonMain/.../data/db/entity/ContentEntities.kt`
- Modify: `showcase/src/commonMain/.../data/db/ShowcaseDatabase.kt`
- Modify: `showcase/src/commonMain/.../data/db/dao/ArticleDao.kt`
- Modify: `showcase/src/commonMain/.../data/seed/ArticleSeed.kt`
- Modify: `showcase/src/commonMain/.../di/AppGraph.kt`
- Create: `showcase/src/commonMain/.../domain/feed/FeedItem.kt`
- Modify: `showcase/src/commonMain/.../data/repo/ArticleRepository.kt`
- Test: `showcase/src/commonTest/.../data/seed/ArticleSeedTest.kt` (extend)

**Interfaces:**
- Produces: `ArticleEntity.feedOrdinal: Int`, `sealed interface FeedItem` with `Article` and `NativeAdSlot`, `ArticleRepository.feedPager(): Flow<PagingData<FeedItem.Article>>`, `const val FEED_PAGE_SIZE`. Tasks 2–4 consume all of these.

- [ ] **Step 1: Reference the already-approved Paging dependencies**

In `showcase/build.gradle.kts`, add to the `commonMain` dependencies block:

```kotlin
                implementation(libs.androidx.paging.common)
                implementation(libs.androidx.paging.compose)
                implementation(libs.androidx.room.paging)
```

- [ ] **Step 2: Write the failing seed test**

Add to `ArticleSeedTest.kt`:

```kotlin
    @Test
    fun everyArticleCarriesItsFeedOrdinal() {
        val ordinals = ArticleSeed.articles().map { it.feedOrdinal }

        assertEquals(ordinals.indices.toList(), ordinals)
    }

    @Test
    fun ordinalOrderMatchesPublishedAtDescendingSoTheFeedAgrees() {
        val byPublished = ArticleSeed.articles().sortedByDescending { it.publishedAt }

        assertEquals(byPublished.map { it.feedOrdinal }, byPublished.indices.toList())
    }
```

- [ ] **Step 3: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: feedOrdinal`.

- [ ] **Step 4: Add the column**

In `ContentEntities.kt`, add to `ArticleEntity` after `unlockCostCoins`:

```kotlin
    /**
     * Position in the feed's canonical ordering, assigned by the seed.
     *
     * Exists because Paging's `insertSeparators` provides adjacent items but
     * no index, and a stateful counter would be wrong — separators are
     * recomputed lazily. Ad placement therefore reads stable data on the
     * article rather than a list position.
     */
    val feedOrdinal: Int,
```

In `ArticleSeed.kt`, set it inside the builder alongside `id`:

```kotlin
                        feedOrdinal = index,
```

- [ ] **Step 5: Bump the schema version and allow a destructive migration**

In `ShowcaseDatabase.kt`, change `version = 1` to `version = 2`.

In `AppGraph.kt`, add to the database builder chain before `.build()`:

```kotlin
        // Destructive is correct *here* and only here: every row in the
        // database at this point is regenerable seed content, so a real
        // Migration would be ceremony with no user-visible benefit.
        //
        // This stops being acceptable from Phase 5 onward, when the wallet
        // holds coins the user earned by watching ads. Any schema change after
        // that needs a real Migration.
        .fallbackToDestructiveMigration(dropAllTables = true)
```

`fallbackToDestructiveMigration` is confirmed present in Room 2.8.4's KMP runtime (alongside `fallbackToDestructiveMigrationFrom` and `fallbackToDestructiveMigrationOnDowngrade`). Room 2.7 made `dropAllTables` a required parameter; if the compiler disagrees, drop the argument — a wrong arity here is an immediate, self-evident compile error, not a silent bug.

- [ ] **Step 6: Run to verify the seed test passes**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **PASS**, including the two new ordinal cases.

- [ ] **Step 7: Add the PagingSource query**

In `ArticleDao.kt`, add the import `androidx.paging.PagingSource` and:

```kotlin
    /**
     * Feed order. `feedOrdinal` ascending is equivalent to `publishedAt`
     * descending — asserted by `ArticleSeedTest` — and sorting on the integer
     * keeps the ad-slot rule and the query agreeing on one ordering.
     */
    @Query("SELECT * FROM articles ORDER BY feedOrdinal ASC")
    fun pagingSource(): PagingSource<Int, ArticleEntity>
```

- [ ] **Step 8: Define the feed item model**

Create `showcase/src/commonMain/.../domain/feed/FeedItem.kt`:

```kotlin
package dev.avinya.admob.showcase.domain.feed

/** One row in the feed: either real content or a native ad slot. */
sealed interface FeedItem {

    /** Stable identity for Compose's `key` and for Paging's diffing. */
    val key: String

    data class Article(
        val id: String,
        val title: String,
        val author: String,
        val section: String,
        val readTimeMin: Int,
        val isPremium: Boolean,
        val feedOrdinal: Int,
    ) : FeedItem {
        override val key: String get() = "article_$id"
    }

    /**
     * A native ad slot.
     *
     * [slotKey] comes from [FeedAdInserter.slotKeyAfter] and is derived from
     * the article this slot follows — never from a list position. It is passed
     * to `NativeAdView` as `itemKey`, which is what binds a pooled ad to this
     * row and keeps it bound as the list changes around it.
     */
    data class NativeAdSlot(val slotKey: String) : FeedItem {
        override val key: String get() = slotKey
    }
}
```

- [ ] **Step 9: Expose the pager from the repository**

In `ArticleRepository.kt`, add the imports (`androidx.paging.Pager`, `PagingConfig`, `PagingData`, `map`, `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.map`) and:

```kotlin
    /**
     * The feed, 20 articles at a time.
     *
     * Emits only articles; ad slots are inserted downstream by
     * `FeedViewModel`, so the repository stays unaware of advertising.
     */
    fun feedPager(): Flow<PagingData<FeedItem.Article>> = Pager(
        config = PagingConfig(pageSize = FEED_PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { articleDao.pagingSource() },
    ).flow.map { paging -> paging.map(ArticleEntity::toFeedArticle) }
```

and at file scope:

```kotlin
/** 20 keeps the 126-article seed at six real pages. */
const val FEED_PAGE_SIZE: Int = 20

private fun ArticleEntity.toFeedArticle(): FeedItem.Article = FeedItem.Article(
    id = id,
    title = title,
    author = author,
    section = section,
    readTimeMin = readTimeMin,
    isPremium = isPremium,
    feedOrdinal = feedOrdinal,
)
```

- [ ] **Step 10: Verify both platforms compile and all tests pass**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add showcase
git commit -m "$(cat <<'EOF'
feat(showcase): add feed paging source and the feedOrdinal column

Paging's insertSeparators provides adjacent items but no index, and a
stateful counter would be wrong because separators are recomputed
lazily. Ad placement therefore reads a stable ordinal carried by the
article rather than a list position — the same insight as the itemKey
rule: position is not identity.

Schema goes to version 2 with a destructive migration, which is correct
only because every row is regenerable seed content. That stops being
acceptable from Phase 5, when the wallet holds earned coins.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `FeedAdInserter` — the pure rule

**Files:**
- Create: `showcase/src/commonMain/.../domain/feed/FeedAdInserter.kt`
- Test: `showcase/src/commonTest/.../domain/feed/FeedAdInsertionTest.kt`

**Interfaces:**
- Produces: `object FeedAdInserter { const val AD_INTERVAL: Int; fun shouldInsertAfter(ordinal: Int): Boolean; fun slotKeyAfter(articleId: String): String }`. Task 3 applies it.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.avinya.admob.showcase.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedAdInsertionTest {

    @Test
    fun insertsAfterEverySixthArticle() {
        val positions = (0 until 18).filter { FeedAdInserter.shouldInsertAfter(it) }

        assertEquals(listOf(5, 11, 17), positions)
    }

    @Test
    fun doesNotInsertBeforeTheFirstSixArticles() {
        (0..4).forEach { assertFalse(FeedAdInserter.shouldInsertAfter(it), "unexpected slot at $it") }
    }

    @Test
    fun slotKeysDeriveFromTheArticleTheyFollow() {
        assertEquals("feed_native_after_article-005", FeedAdInserter.slotKeyAfter("article-005"))
    }

    @Test
    fun theSameArticleAlwaysYieldsTheSameSlotKey() {
        assertEquals(
            FeedAdInserter.slotKeyAfter("article-011"),
            FeedAdInserter.slotKeyAfter("article-011"),
        )
    }

    @Test
    fun aPrependDoesNotShiftExistingSlotKeys() {
        // THE case index-derived keys get wrong. article-011 sits at list
        // position 11 before a prepend and 14 after it; its slot key must not
        // change, or NativeAdView releases the pooled ad bound to that row and
        // acquires another for no reason — wasted inventory and visible flicker.
        val beforePrepend = FeedAdInserter.slotKeyAfter("article-011")
        val threeNewArticlesArrive = listOf("article-a", "article-b", "article-c")

        assertEquals(beforePrepend, FeedAdInserter.slotKeyAfter("article-011"))
        assertTrue(threeNewArticlesArrive.none { FeedAdInserter.slotKeyAfter(it) == beforePrepend })
    }

    @Test
    fun distinctArticlesNeverCollideOnAKey() {
        val keys = (0 until 200).map { FeedAdInserter.slotKeyAfter("article-$it") }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun theIntervalIsSixSoTheSeedYieldsTwentyOneSlots() {
        // 126 seeded articles / 6 = 21 ad slots across the whole feed.
        assertEquals(6, FeedAdInserter.AD_INTERVAL)
        assertEquals(21, (0 until 126).count { FeedAdInserter.shouldInsertAfter(it) })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: FeedAdInserter`.

- [ ] **Step 3: Write the implementation**

```kotlin
package dev.avinya.admob.showcase.domain.feed

/**
 * Decides where native ad slots go in the feed, and what they are keyed by.
 *
 * Pure on purpose. Slot placement and key derivation are the two things a
 * feed integration most often gets wrong, and both are decidable from values
 * alone — no Paging, no Compose, no SDK.
 */
object FeedAdInserter {

    /** One ad per six articles: frequent enough to demonstrate, sparse enough to be plausible. */
    const val AD_INTERVAL: Int = 6

    /** True when an ad slot belongs immediately after the article at [ordinal]. */
    fun shouldInsertAfter(ordinal: Int): Boolean =
        ordinal >= AD_INTERVAL - 1 && (ordinal + 1) % AD_INTERVAL == 0

    /**
     * The `itemKey` for the slot following [articleId].
     *
     * Derived from the article's identity, **never** from its position.
     * Positions shift on prepend and refresh; a changed `itemKey` makes
     * `NativeAdView` release its pooled ad and acquire another, wasting
     * inventory and making ads visibly flicker during scrolling.
     */
    fun slotKeyAfter(articleId: String): String = "feed_native_after_$articleId"
}
```

- [ ] **Step 4: Run on both platforms**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS**, all seven cases on both.

- [ ] **Step 5: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add the pure feed ad-slot rule

Slot keys derive from the article they follow, never from a list index.
The prepend test is the one that distinguishes the two: a naive
same-input-same-key assertion passes either way and proves nothing.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Native ads in the paged feed

**Files:**
- Create: `showcase/src/commonMain/.../domain/ad/ShowcasePlacements.kt`
- Create: `showcase/src/commonMain/.../ui/ad/FeedNativeAdLayout.kt`
- Create: `showcase/src/commonMain/.../feature/feed/FeedContract.kt`
- Create: `showcase/src/commonMain/.../feature/feed/FeedViewModel.kt`
- Create: `showcase/src/commonMain/.../feature/feed/FeedScreen.kt`
- Modify: `showcase/src/commonMain/.../nav/ShowcaseNavHost.kt`

**Interfaces:**
- Produces: `object ShowcasePlacements` (later phases add to it), `feedNativeAdLayout: AdLayout`, `FeedState/Intent/Effect`, `@Composable fun FeedScreen(onArticleClick: (String) -> Unit)`.

- [ ] **Step 1: Create the placement catalog**

Constructor names below are verified against `admob-cmp-core` source:
`AdPlacement(id, format, adUnitIds, requestOptions, cachePolicy, retryPolicy, timeoutPolicy, bannerSizePolicy, bannerRefreshPolicy, nativeOptions, fullScreenOptions, enabled, strictTestMode)`, `AdUnitIds(android, ios)`, `AdCachePolicy(maxSize, expirationPolicy, reloadAfterShow)`.

```kotlin
package dev.avinya.admob.showcase.domain.ad

import dev.avinya.ads.AdCachePolicy
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdSizePolicy
import dev.avinya.ads.AdUnitIds
import dev.avinya.ads.BannerRefreshPolicy
import dev.avinya.ads.TestAdIds
import kotlin.time.Duration.Companion.seconds

/**
 * Every placement the showcase uses — a **static, finite** catalog.
 *
 * Controllers are cached per `AdPlacement.id` for the manager's lifetime and
 * are never evicted, so generated per-item ids leak permanently. The feed
 * serves per-item ads from the native pool keyed by `itemKey`, rather than
 * minting a placement per row.
 *
 * `strictTestMode = true` throws at construction if any of these ever points
 * at a production ad unit.
 */
object ShowcasePlacements {

    val feedBanner: AdPlacement = AdPlacement(
        id = "feed_banner",
        format = AdFormat.Banner,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_BANNER, ios = TestAdIds.IOS_BANNER),
        bannerSizePolicy = AdSizePolicy.LargeAnchoredAdaptive(),
        bannerRefreshPolicy = BannerRefreshPolicy.SdkManaged(60.seconds),
        strictTestMode = true,
    )

    val feedNative: AdPlacement = AdPlacement(
        id = "feed_native",
        format = AdFormat.Native,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_NATIVE, ios = TestAdIds.IOS_NATIVE),
        // maxSize budgets available + in-use ads together. Five covers the rows
        // visible at once plus prefetch; too low and acquire() returns null for
        // every row beyond the budget.
        cachePolicy = AdCachePolicy(maxSize = 5, reloadAfterShow = true),
        strictTestMode = true,
    )

    // Phases 4-6 add articleNative, articleBanner, articleInterstitial,
    // storeRewarded, storeRewardedInterstitial and appOpen here, so the whole
    // catalog stays in one readable file.
}
```

- [ ] **Step 2: Build the feed's native ad layout**

```kotlin
package dev.avinya.admob.showcase.ui.ad

import androidx.compose.ui.unit.dp
import dev.avinya.ads.nativead.layout.AdLayout
import dev.avinya.ads.nativead.layout.AdModifier
import dev.avinya.ads.nativead.layout.adLayout

/**
 * Card-shaped native ad for the feed: media-led, sized to sit among articles.
 *
 * `adBadge()` is policy-required — the SDK's validator warns without it, and
 * shipping an unlabelled native ad is a policy violation, not a style choice.
 */
val feedNativeAdLayout: AdLayout = adLayout {
    column(modifier = AdModifier.fillMaxWidth()) {
        row(spacing = 8.dp) {
            icon(modifier = AdModifier.size(40.dp))
            column {
                headline(maxLines = 2)
                advertiser()
            }
            adBadge()
        }
        media(modifier = AdModifier.fillMaxWidth().aspectRatio(16f / 9f))
        body(maxLines = 3)
        callToAction(modifier = AdModifier.fillMaxWidth())
    }
}
```

DSL nodes are **functions with named arguments** (`headline(maxLines = 2)`), not property-assignment blocks. The available nodes are verified: `headline`, `body`, `media`, `icon`, `advertiser`, `adBadge`, `callToAction`, `store`, `price`, `starRating`, plus the `row` and `column` containers.

Composable signatures are verified too — use them exactly:

```kotlin
NativeAdView(placement: AdPlacement, itemKey: String,
             layout: AdLayout = AdTemplates.mediaCard,
             modifier: Modifier = Modifier, onEvent: (AdEvent) -> Unit = {})

BannerAdView(placement: AdPlacement, modifier: Modifier = Modifier,
             widthDp: Int? = null, onEvent: (AdEvent) -> Unit = {})
```

`widthDp` stays `null`: `BannerAdView` measures its own container.

- [ ] **Step 3: Write the contract**

```kotlin
package dev.avinya.admob.showcase.feature.feed

data class FeedState(
    val adsEnabled: Boolean = true,
    val sdkReady: Boolean = false,
)

sealed interface FeedIntent {
    data class OpenArticle(val articleId: String) : FeedIntent
}

sealed interface FeedEffect {
    data class NavigateToArticle(val articleId: String) : FeedEffect
}
```

- [ ] **Step 4: Write the ViewModel**

```kotlin
package dev.avinya.admob.showcase.feature.feed

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dev.avinya.ads.AdManager
import dev.avinya.ads.AdManagerStatus
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.data.repo.ArticleRepository
import dev.avinya.admob.showcase.domain.feed.FeedAdInserter
import dev.avinya.admob.showcase.domain.feed.FeedItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class FeedViewModel(
    articles: ArticleRepository,
    settings: SettingsRepository,
    adManager: AdManager,
) : MviViewModel<FeedState, FeedIntent, FeedEffect>(FeedState()) {

    /**
     * The feed, with ad slots already inserted.
     *
     * `cachedIn(viewModelScope)` is load-bearing: without it every
     * recomposition re-collects the flow and refetches pages, which also
     * destroys and re-acquires every pooled native ad on screen.
     */
    val feed: Flow<PagingData<FeedItem>> = combine(
        articles.feedPager(),
        settings.adsMasterSwitch,
        adManager.status,
    ) { paging, adsEnabled, status ->
        val showAds = adsEnabled && status == AdManagerStatus.Ready
        val items = paging.map<FeedItem.Article, FeedItem> { it }
        if (showAds) items.withAdSlots() else items
    }.cachedIn(viewModelScope)

    init {
        combine(settings.adsMasterSwitch, adManager.status) { adsEnabled, status ->
            FeedState(adsEnabled = adsEnabled, sdkReady = status == AdManagerStatus.Ready)
        }.onEach { next -> updateState { next } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: FeedIntent) {
        when (intent) {
            is FeedIntent.OpenArticle -> emitEffect(FeedEffect.NavigateToArticle(intent.articleId))
        }
    }
}

/**
 * Inserts an ad slot after every sixth article.
 *
 * `insertSeparators` supplies adjacent items and no index, which is exactly
 * why placement is decided from the article's own `feedOrdinal` rather than a
 * position — see `FeedAdInserter`.
 */
private fun PagingData<FeedItem>.withAdSlots(): PagingData<FeedItem> =
    insertSeparators { before, _ ->
        val article = before as? FeedItem.Article ?: return@insertSeparators null
        if (FeedAdInserter.shouldInsertAfter(article.feedOrdinal)) {
            FeedItem.NativeAdSlot(FeedAdInserter.slotKeyAfter(article.id))
        } else {
            null
        }
    }
```

- [ ] **Step 5: Write the screen**

```kotlin
package dev.avinya.admob.showcase.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.ui.NativeAdView
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import dev.avinya.admob.showcase.domain.feed.FeedItem
import dev.avinya.admob.showcase.ui.ad.feedNativeAdLayout

@Composable
fun FeedScreen(onArticleClick: (String) -> Unit) {
    val adManager = LocalAdManager.current
    val graph = LocalAppGraph.current
    val viewModel: FeedViewModel = viewModel {
        FeedViewModel(graph.articles, graph.settings, adManager)
    }
    val items = viewModel.feed.collectAsLazyPagingItems()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FeedEffect.NavigateToArticle -> onArticleClick(effect.articleId)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey { it.key },
        ) { index ->
            when (val item = items[index]) {
                is FeedItem.Article -> ArticleCard(
                    item = item,
                    onClick = { viewModel.onIntent(FeedIntent.OpenArticle(item.id)) },
                )
                is FeedItem.NativeAdSlot -> NativeAdView(
                    placement = ShowcasePlacements.feedNative,
                    itemKey = item.slotKey,
                    layout = feedNativeAdLayout,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Placeholders are disabled, so null only occurs transiently
                // while a page loads. Render nothing — never a spinner per row.
                null -> Unit
            }
        }
    }
}

@Composable
private fun ArticleCard(item: FeedItem.Article, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.section.uppercase(), style = MaterialTheme.typography.labelSmall)
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(item.author)
                    append(" · ")
                    append(item.readTimeMin)
                    append(" min")
                    if (item.isPremium) append(" · Premium")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

> When ads are off, `withAdSlots()` is not applied at all, so no empty rows exist to collapse. That is deliberately stronger than rendering a zero-height slot.

- [ ] **Step 6: Wire it into navigation**

In `ShowcaseNavHost.kt`, replace the Feed entry:

```kotlin
                entry<ShowcaseNavKey.Feed> {
                    FeedScreen(
                        onArticleClick = { articleId ->
                            backStack.add(ShowcaseNavKey.ArticleDetail(articleId))
                        },
                    )
                }
```

`ArticleDetail` still renders its placeholder until Phase 4.

- [ ] **Step 7: Verify compilation and tests**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache
```

- [ ] **Step 8: Verify on a device — the real test of this phase**

```bash
./gradlew :androidApp:installDebug
```

Confirm all four:

1. Ads appear after roughly every 6th article.
2. Scrolling down past several ads and back up does **not** reload them — no flicker, no re-request. This is the stable-key rule working.
3. Turning "Show ads" off in Settings removes ad rows entirely, leaving no gaps.
4. No row shows a "failed to load" box; a slot that cannot fill is simply absent.

- [ ] **Step 9: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): render native ads in the paged feed

One placement id for the whole feed; per-item ads come from the pool via
NativeAdView's itemKey, because controllers are cached per placement id
for the manager's lifetime and never evicted.

cachedIn(viewModelScope) is load-bearing — without it every
recomposition refetches pages and destroys every pooled ad on screen.

With ads off the slots are never inserted rather than inserted and
hidden, so there are no empty rows to collapse.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Anchored adaptive banner

**Files:**
- Modify: `showcase/src/commonMain/.../feature/feed/FeedScreen.kt`

- [ ] **Step 1: Add the banner below the list**

Wrap `FeedScreen`'s body in a `Column`, with the `LazyColumn` taking `Modifier.weight(1f)` and the banner beneath it:

```kotlin
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // … items block unchanged …
        }

        // Rendered only when it can actually fill. BannerAdView measures its
        // own container and supplies the width — do not build BannerGeometry
        // by hand here; that is only for headless controller callers.
        val state by viewModel.state.collectAsState()
        if (state.adsEnabled && state.sdkReady) {
            BannerAdView(
                placement = ShowcasePlacements.feedBanner,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
```

Add the imports `androidx.compose.foundation.layout.Column` and `dev.avinya.ads.ui.BannerAdView`.

- [ ] **Step 2: Confirm it occupies zero height when off**

Toggle "Show ads" off in Settings and return to the feed. The bottom navigation bar must sit flush against the list — no reserved strip, no placeholder. Because the composable is not emitted at all, this is structural rather than a styling choice.

- [ ] **Step 3: Confirm refresh behaviour**

Leave the feed open for 60+ seconds and confirm the banner refreshes (`BannerRefreshPolicy.SdkManaged(60.seconds)`). The Inspector in Phase 6 will make this observable; for now watch for the creative changing.

- [ ] **Step 4: Full verification**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
cd docs-site && npm test && cd ..
```

Expected: all green, `docs-site` still at 239 passing.

- [ ] **Step 5: Run on iOS**

Build and run `iosApp` in Xcode against a simulator. Confirm native ads and the banner render, and that scrolling does not reload ads. iOS uses a different native rendering path from Android, so this is not implied by the Android run.

- [ ] **Step 6: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add the anchored adaptive banner to the feed

BannerAdView measures its own container and supplies the width;
BannerGeometry is only for headless controller callers.

The composable is omitted entirely when ads are off, so zero height is
structural rather than styling.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Exit criteria

- [ ] Feed pages through all 126 articles, 20 at a time
- [ ] Native ads appear after every 6th article on **both** platforms
- [ ] Scrolling away and back does not reload a row's ad — stable keys verified by eye
- [ ] `FeedAdInsertionTest` passes on Android host and iOS, including the prepend case
- [ ] Anchored adaptive banner renders and refreshes; absent entirely when ads are off
- [ ] Exactly **one** placement id is used for the whole feed
- [ ] `docs-site` still passes at 239
- [ ] `git diff --stat master..HEAD -- 'admob-cmp*'` is empty

---

## Next plan

**Phase 4 — Article** (`2026-08-06-showcase-phase-4-article.md`): article detail, reading progress, inline native with a second layout, collapsible banner, and `AdPolicy` driving a frequency-capped interstitial. Bring it to execution fidelity before starting it.
