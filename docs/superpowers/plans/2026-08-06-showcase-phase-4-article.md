# Showcase — Phase 4: Article & Interstitial

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Article detail with persisted reading progress, an inline native ad in a *second* layout, a collapsible banner, and a frequency-capped interstitial driven by a pure `AdPolicy` that reports **why** it suppressed an ad.

**Architecture:** `AdPolicy` lives in `domain/ad`, is pure, takes an injected `Clock`, and returns `AdDecision.Show` or `AdDecision.Suppress(reason)`. `ArticleViewModel` asks it and emits an `Effect`; a UI-layer `AdEffectHandler` performs the actual `show()` behind a `Mutex`.

**Tech Stack:** Compose Multiplatform 1.11.1, Navigation3, Room 2.8.4.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** Phase 3 complete — feed renders native ads and a banner.

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/`. Record gaps in `docs/showcase-sdk-gaps.md`, work around inside `:showcase`, escalate. **Stop and ask.**
- **Kotlin stays at 2.3.20. No new dependencies.**
- **Testing principle.** Test what a consumer would copy. `AdPolicy` is the highest-value test target in the entire showcase and must be pure — no SDK, no Compose, no coroutines, injected `Clock`.
- **Do not modify** `gradle.properties`, the plugin's `gradle.properties`, or `.github/workflows/release.yml`. **Do not commit** `api/*.klib.api` changes.
- Package root `dev.avinya.admob.showcase`. Branch `feat/showcase-app`. No PR without the owner's confirmation.
- Commits end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

### The governing UX rule

**An ad failure is never a user-facing error.** Slots collapse to zero height; navigation always proceeds. A suppressed or failed interstitial must never block the user leaving an article.

---

## Tasks

### Task 1: Article detail with persisted reading progress

**Files:**
- Create: `showcase/src/commonMain/.../feature/article/ArticleContract.kt`
- Create: `showcase/src/commonMain/.../feature/article/ArticleViewModel.kt`
- Create: `showcase/src/commonMain/.../feature/article/ArticleScreen.kt`
- Create: `showcase/src/commonMain/.../domain/article/Paragraphs.kt`
- Test: `showcase/src/commonTest/.../domain/article/ParagraphsTest.kt`

**Interfaces:**
- Produces: `fun splitParagraphs(body: String): List<String>`, `fun inlineAdSlotIndex(paragraphCount: Int): Int?`, `ArticleState/Intent/Effect`, `@Composable fun ArticleScreen(articleId: String, onBack: () -> Unit)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.avinya.admob.showcase.domain.article

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParagraphsTest {

    @Test
    fun splitsOnBlankLines() {
        assertEquals(listOf("one", "two", "three"), splitParagraphs("one\n\ntwo\n\nthree"))
    }

    @Test
    fun ignoresTrailingWhitespaceAndEmptyParagraphs() {
        assertEquals(listOf("one", "two"), splitParagraphs("one\n\n\n\ntwo\n\n  \n"))
    }

    @Test
    fun placesTheInlineAdAfterTheThirdParagraph() {
        assertEquals(3, inlineAdSlotIndex(paragraphCount = 5))
        assertEquals(3, inlineAdSlotIndex(paragraphCount = 4))
    }

    @Test
    fun omitsTheInlineAdWhenTheArticleIsTooShortToCarryIt() {
        // An ad immediately before the last paragraph reads as an interruption
        // rather than a break, so short articles get none.
        assertNull(inlineAdSlotIndex(paragraphCount = 3))
        assertNull(inlineAdSlotIndex(paragraphCount = 1))
    }
}
```

- [ ] **Step 2: Run to verify it fails**, then implement:

```kotlin
package dev.avinya.admob.showcase.domain.article

/** Article bodies store paragraphs separated by a blank line. */
fun splitParagraphs(body: String): List<String> =
    body.split("\n\n").map(String::trim).filter(String::isNotEmpty)

/**
 * Index at which the inline native ad is inserted, or null for articles too
 * short to carry one.
 *
 * Requires at least one paragraph after the ad: a break needs something on
 * both sides of it, otherwise it reads as an interruption.
 */
fun inlineAdSlotIndex(paragraphCount: Int): Int? =
    if (paragraphCount > INLINE_AD_AFTER_PARAGRAPH) INLINE_AD_AFTER_PARAGRAPH else null

private const val INLINE_AD_AFTER_PARAGRAPH = 3
```

- [ ] **Step 3: Build the screen and ViewModel**

`ArticleScreen` renders the article in a `LazyColumn` keyed by paragraph index. Reading progress is written through `ArticleRepository.setProgress` **debounced** — collect scroll position into a `snapshotFlow`, `debounce(500)`, then persist. Writing on every scroll pixel would hammer Room for no benefit.

Bookmark toggle reads `isBookmarked(articleId)` and writes via `setBookmarked`.

- [ ] **Step 4: Verify and commit**

---

### Task 2: Inline native ad — a second layout from the same DSL

**Files:**
- Create: `showcase/src/commonMain/.../ui/ad/InlineNativeAdLayout.kt`
- Modify: `showcase/src/commonMain/.../domain/ad/ShowcasePlacements.kt`
- Modify: `showcase/src/commonMain/.../feature/article/ArticleScreen.kt`

- [ ] **Step 1: Add the placement**

```kotlin
    val articleNative = AdPlacement(
        id = "article_native",
        format = AdFormat.Native,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_NATIVE, ios = TestAdIds.IOS_NATIVE),
        cachePolicy = AdCachePolicy(maxSize = 2),
        strictTestMode = true,
    )
```

- [ ] **Step 2: Write a deliberately different layout**

The point is to show the DSL composes into more than one shape. Where the feed layout is a card with media on top, the inline one reads as part of the article — a horizontal band, no large media:

```kotlin
val inlineNativeAdLayout: AdLayout = adLayout {
    column(modifier = AdModifier.fillMaxWidth()) {
        row(spacing = 4.dp) { adBadge(); advertiser() }
        row(spacing = 12.dp) {
            icon(modifier = AdModifier.size(56.dp))
            column {
                headline(maxLines = 2)
                body(maxLines = 2)
            }
        }
        callToAction(modifier = AdModifier.fillMaxWidth())
    }
}
```

- [ ] **Step 3: Insert it at `inlineAdSlotIndex`**

`itemKey` is the article id — `"article_native_$articleId"` — stable for the lifetime of the screen and distinct per article.

- [ ] **Step 4: Validate the layout**

Run the SDK's own validator and surface warnings in the log:

```kotlin
AdLayoutValidator.validate(inlineNativeAdLayout)
```

`adBadge()` is policy-required; a missing badge is a validator warning and a policy violation, not a style choice.

- [ ] **Step 5: Verify and commit**

---

### Task 3: Collapsible banner

**Files:**
- Modify: `showcase/src/commonMain/.../domain/ad/ShowcasePlacements.kt`
- Modify: `showcase/src/commonMain/.../feature/article/ArticleScreen.kt`

- [ ] **Step 1: Add the placement**

Visibly different from the feed's anchored banner — that contrast is the demonstration:

```kotlin
    val articleBanner = AdPlacement(
        id = "article_banner",
        format = AdFormat.Banner,
        adUnitIds = AdUnitIds(
            android = TestAdIds.ANDROID_COLLAPSIBLE_BANNER,
            ios = TestAdIds.IOS_COLLAPSIBLE_BANNER,
        ),
        bannerSizePolicy = AdSizePolicy.LargeAnchoredAdaptive(
            collapsible = CollapsiblePlacement.Bottom,
        ),
        bannerRefreshPolicy = BannerRefreshPolicy.AdServerManaged,
        strictTestMode = true,
    )
```

Note the **collapsible** test ad unit ids — the plain banner units do not serve collapsible creatives.

- [ ] **Step 2: Place it, verify the collapse gesture works on device, commit**

---

### Task 4: `AdPolicy` and the frequency-capped interstitial

The highest-value task in the showcase.

**Files:**
- Create: `showcase/src/commonMain/.../domain/ad/AdPolicy.kt`
- Create: `showcase/src/commonMain/.../ui/ad/AdEffectHandler.kt`
- Modify: `showcase/src/commonMain/.../feature/article/ArticleViewModel.kt`
- Test: `showcase/src/commonTest/.../domain/ad/AdPolicyTest.kt`

**Interfaces:**
- Produces: `data class AdPolicySnapshot(...)`, `sealed interface AdDecision`, `enum class SuppressionReason`, `class AdPolicy(clock: Clock)`, `@Composable fun AdEffectHandler(...)`.

- [ ] **Step 1: Write the failing test**

Every boundary, and every suppression reason asserted by name.

```kotlin
package dev.avinya.admob.showcase.domain.ad

import kotlin.test.Test
import kotlin.test.assertEquals

private fun snapshot(
    articlesRead: Int = 3,
    millisSinceLastInterstitial: Long = 120_000,
    millisSinceColdStart: Long = 120_000,
    canRequestAds: Boolean = true,
    wasRewardedUnlock: Boolean = false,
    adsEnabled: Boolean = true,
) = AdPolicySnapshot(
    articlesRead = articlesRead,
    millisSinceLastInterstitial = millisSinceLastInterstitial,
    millisSinceColdStart = millisSinceColdStart,
    canRequestAds = canRequestAds,
    wasRewardedUnlock = wasRewardedUnlock,
    adsEnabled = adsEnabled,
)

class AdPolicyTest {

    private val policy = AdPolicy()

    @Test
    fun showsOnEveryThirdArticle() {
        assertEquals(AdDecision.Show, policy.decideInterstitial(snapshot(articlesRead = 3)))
        assertEquals(AdDecision.Show, policy.decideInterstitial(snapshot(articlesRead = 6)))
    }

    @Test
    fun suppressesOnNonMultiplesWithTheFrequencyCapReason() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.FrequencyCap),
            policy.decideInterstitial(snapshot(articlesRead = 2)),
        )
    }

    @Test
    fun cooldownBoundaryIsExactlySixtySeconds() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.Cooldown),
            policy.decideInterstitial(snapshot(millisSinceLastInterstitial = 59_999)),
        )
        assertEquals(
            AdDecision.Show,
            policy.decideInterstitial(snapshot(millisSinceLastInterstitial = 60_000)),
        )
    }

    @Test
    fun neverInterruptsWithinThirtySecondsOfColdStart() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.ColdStartGrace),
            policy.decideInterstitial(snapshot(millisSinceColdStart = 29_999)),
        )
        assertEquals(
            AdDecision.Show,
            policy.decideInterstitial(snapshot(millisSinceColdStart = 30_000)),
        )
    }

    @Test
    fun suppressesWhenConsentForbidsRequests() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.ConsentMissing),
            policy.decideInterstitial(snapshot(canRequestAds = false)),
        )
    }

    @Test
    fun neverInterruptsAnArticleTheUserJustWatchedAnAdToUnlock() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.RewardedUnlock),
            policy.decideInterstitial(snapshot(wasRewardedUnlock = true)),
        )
    }

    @Test
    fun theLocalKillSwitchWins() {
        assertEquals(
            AdDecision.Suppress(SuppressionReason.AdsDisabled),
            policy.decideInterstitial(snapshot(adsEnabled = false)),
        )
    }

    @Test
    fun consentOutranksFrequencyWhenBothWouldSuppress() {
        // Reason ordering matters: the Inspector shows the FIRST reason, and
        // "consent forbids requests" is more actionable than "not the 3rd article".
        assertEquals(
            AdDecision.Suppress(SuppressionReason.ConsentMissing),
            policy.decideInterstitial(snapshot(articlesRead = 2, canRequestAds = false)),
        )
    }
}
```

- [ ] **Step 2: Run to verify it fails**, then implement:

```kotlin
package dev.avinya.admob.showcase.domain.ad

/** Everything the interstitial rules need, with nothing attached to it. */
data class AdPolicySnapshot(
    val articlesRead: Int,
    val millisSinceLastInterstitial: Long,
    val millisSinceColdStart: Long,
    val canRequestAds: Boolean,
    val wasRewardedUnlock: Boolean,
    val adsEnabled: Boolean,
)

/** Why an ad was not shown. Rendered verbatim by the Inspector. */
enum class SuppressionReason {
    AdsDisabled,
    ConsentMissing,
    ColdStartGrace,
    Cooldown,
    FrequencyCap,
    RewardedUnlock,
    NotReady,
}

sealed interface AdDecision {
    data object Show : AdDecision
    data class Suppress(val reason: SuppressionReason) : AdDecision
}

/**
 * When an interstitial may interrupt the user.
 *
 * Pure — no SDK, no Compose, no coroutines, no clock of its own. Time arrives
 * pre-computed in the snapshot, which is what makes every boundary here
 * testable by comparing two values.
 *
 * Returning a **reason** rather than a boolean is the point. "No ad appeared
 * and I don't know why" is the most common AdMob integration confusion, and
 * making the reason a first-class value is the single most useful thing this
 * showcase teaches.
 */
class AdPolicy {

    fun decideInterstitial(snapshot: AdPolicySnapshot): AdDecision = when {
        !snapshot.adsEnabled -> AdDecision.Suppress(SuppressionReason.AdsDisabled)
        !snapshot.canRequestAds -> AdDecision.Suppress(SuppressionReason.ConsentMissing)
        snapshot.wasRewardedUnlock -> AdDecision.Suppress(SuppressionReason.RewardedUnlock)
        snapshot.millisSinceColdStart < COLD_START_GRACE_MILLIS ->
            AdDecision.Suppress(SuppressionReason.ColdStartGrace)
        snapshot.millisSinceLastInterstitial < COOLDOWN_MILLIS ->
            AdDecision.Suppress(SuppressionReason.Cooldown)
        snapshot.articlesRead % ARTICLES_PER_INTERSTITIAL != 0 ->
            AdDecision.Suppress(SuppressionReason.FrequencyCap)
        else -> AdDecision.Show
    }

    private companion object {
        const val ARTICLES_PER_INTERSTITIAL = 3
        const val COOLDOWN_MILLIS = 60_000L
        const val COLD_START_GRACE_MILLIS = 30_000L
    }
}
```

- [ ] **Step 3: Add the interstitial placement**

```kotlin
    val articleInterstitial = AdPlacement(
        id = "article_interstitial",
        format = AdFormat.Interstitial,
        adUnitIds = AdUnitIds(
            android = TestAdIds.ANDROID_INTERSTITIAL,
            ios = TestAdIds.IOS_INTERSTITIAL,
        ),
        cachePolicy = AdCachePolicy(maxSize = 2, reloadAfterShow = true),
        strictTestMode = true,
    )
```

- [ ] **Step 4: Write `AdEffectHandler`**

Serialises full-screen shows through a `Mutex`. `show()` is **not reentrant per controller**: a second call while one is on screen returns `NotReady` immediately rather than queueing.

```kotlin
@Composable
fun AdEffectHandler(effects: Flow<ArticleEffect>, onSuppressed: (SuppressionReason) -> Unit) {
    val adManager = LocalAdManager.current
    val mutex = remember { Mutex() }
    val interstitial = remember(adManager) {
        adManager.interstitial(ShowcasePlacements.articleInterstitial)
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ArticleEffect.ShowInterstitial -> mutex.withLock {
                    interstitial.load()
                    when (val result = interstitial.show()) {
                        is AdShowResult.Shown -> Unit
                        is AdShowResult.NotReady -> onSuppressed(SuppressionReason.NotReady)
                        is AdShowResult.Failed -> onSuppressed(SuppressionReason.NotReady)
                    }
                }
                is ArticleEffect.AdSuppressed -> onSuppressed(effect.reason)
                is ArticleEffect.NavigateBack -> Unit
            }
        }
    }
}
```

> **Navigation must not wait on this.** `ArticleViewModel` emits `NavigateBack` independently of the ad effect; the screen pops immediately and the interstitial shows over whatever is beneath. An ad never gates a user action.

- [ ] **Step 5: Wire the ViewModel**

On `ArticleIntent.Close`: build the snapshot from repositories and `adManager.consent.canRequestAds.value`, call `policy.decideInterstitial`, emit `ShowInterstitial` or `AdSuppressed(reason)`, then emit `NavigateBack` **unconditionally**.

- [ ] **Step 6: Full verification and commit**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
```

On device: read 3 articles, confirm the interstitial appears on the 3rd close; immediately read another and confirm it is suppressed by cooldown; confirm back navigation is never blocked.

---

## Exit criteria

- [ ] Article renders with persisted, debounced reading progress
- [ ] Inline native ad uses a **visibly different** layout from the feed's
- [ ] Collapsible banner collapses and expands on both platforms
- [ ] Interstitial appears every 3rd article, respecting the 60s cooldown and 30s cold-start grace
- [ ] `AdPolicyTest` passes on Android host and iOS, covering every boundary and reason
- [ ] Navigation is **never** blocked by an ad — suppressed, failed or not-ready
- [ ] `git diff --stat master -- 'admob-cmp*'` is empty

---

## Next plan

**Phase 5 — Store & Library** (`2026-08-06-showcase-phase-5-store-library.md`): the coin economy UI, rewarded ads with callback-based crediting, rewarded-interstitial, unlock transactions with app-open suppression, and the Library screen.
