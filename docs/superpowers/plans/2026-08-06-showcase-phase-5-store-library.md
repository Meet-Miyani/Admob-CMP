# Showcase — Phase 5: Store & Library

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A coin economy where rewarded ads have a real, persisted consequence — plus a rewarded-interstitial offer wall, an unlock transaction that suppresses app-open ads, and a Library screen that deliberately carries no ads at all.

**Architecture:** Coin rules stay pure in `domain/wallet`; `WalletRepository` is the persistence adapter. Rewards are credited from the SDK's reward callback — never from `show()` returning — and guarded by an idempotency key so a replayed callback cannot double-credit.

**Tech Stack:** Compose Multiplatform 1.11.1, Navigation3, Room 2.8.4, Paging 3.5.0.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** Phase 4 complete — `AdPolicy`, the interstitial, and the article screen all work.

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/`. Record gaps in `docs/showcase-sdk-gaps.md`, work around inside `:showcase`, escalate. **Stop and ask.**
- **Kotlin stays at 2.3.20. No new dependencies**, including test runtimes.
- **Testing principle.** Test what a consumer would copy. Coin rules are pure functions over values; do not add a test runtime to observe them through Room.
- **Schema changes now need a real `Migration`.** The wallet holds coins the user earned by watching ads. The destructive fallback added in Phase 3 was justified only while every row was regenerable seed content — **that stops being true in this phase.**
- **An ad never gates a user action.** Phase 4 shipped a bug where `load()` ran inline in the effect collector and blocked navigation. Any `show()` here runs in a child coroutine, never inline in a `collect` body.
- **Only a real presentation may advance state.** Phase 4 shipped a bug where the interstitial cooldown advanced at the *decision* site, so a `NotReady` ad burned 60 seconds for nothing. Rewarded credit has the identical shape — see Task 3.
- **`remember` keys.** Three nav/state bugs so far came from over-keying `remember`. Key on what genuinely invalidates the value.
- **Do not modify** `gradle.properties`, the plugin's `gradle.properties`, or `.github/workflows/release.yml`. **Do not commit** `api/*.klib.api` changes.
- Package root `dev.avinya.admob.showcase`. Branch `feat/showcase-app`. No PR without the owner's confirmation.
- Commits end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

### Verified SDK surface — use exactly these

```
RewardedAdController            : FullScreenAdController
    suspend show(FullScreenAdOptions = …, onReward: (AdReward) -> Unit): AdShowResult
RewardedInterstitialAdController: FullScreenAdController
    suspend show(FullScreenAdOptions = …, onReward: (AdReward) -> Unit): AdShowResult

AdReward(amountMicros: Long, type: String)
    fun wholeAmountOrNull(): Int?    // null when the reward is fractional

AdShowResult = Shown | NotReady | Failed
FullScreenAdOptions(immersiveMode: Boolean = false,
                    serverSideVerification: ServerSideVerificationOptions? = null)
ServerSideVerificationOptions(userId: String? = null, customData: String? = null)
AppOpenAdCoordinator(manager, controller, config = …); var isBlocked: Boolean
```

**`AdReward` has no `amount` property.** It is `amountMicros: Long`; use `wholeAmountOrNull()`.

### The rule this phase exists to demonstrate

**Credit from the reward callback. Never from `show()` returning `Shown`.** A user who dismisses early still yields `Shown` — crediting on it pays for an ad that was not watched. This is the classic rewarded-ads bug, and it is the same mistake shape as Phase 4's cooldown bug.

---

## Tasks

### Task 1: Close the Phase 4 gap — cooldown advances only on a real presentation

Phase 4 fixed this in code but shipped no regression guard, so it can silently come back.

**Files:**
- Create: `showcase/src/commonMain/.../domain/ad/AdPresentation.kt`
- Modify: `showcase/src/commonMain/.../ui/ad/AdEffectHandler.kt`
- Test: `showcase/src/commonTest/.../domain/ad/AdPresentationTest.kt`

**Interfaces:**
- Produces: `fun advancesCooldown(result: AdShowResult): Boolean`. Task 3 reuses the same idea for rewards.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.avinya.admob.showcase.domain.ad

import dev.avinya.ads.AdError
import dev.avinya.ads.AdShowResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdPresentationTest {

    @Test
    fun onlyAShownAdAdvancesTheCooldown() {
        assertTrue(advancesCooldown(AdShowResult.Shown))
    }

    @Test
    fun aNotReadyAdDoesNotBurnTheCooldown() {
        // Regression guard. This shipped broken in Phase 4: the cooldown was
        // written at the decision site, so an interstitial that never rendered
        // still suppressed the next 60 seconds.
        assertFalse(advancesCooldown(AdShowResult.NotReady))
    }

    @Test
    fun aFailedAdDoesNotBurnTheCooldown() {
        assertFalse(
            advancesCooldown(
                AdShowResult.Failed(AdError(code = "internal", message = "boom")),
            ),
        )
    }
}
```

Shapes are **verified**: `AdShowResult.Shown` and `AdShowResult.NotReady` are `object`s; `AdShowResult.Failed(error: AdError)` is a class. `AdError`'s constructor is `(code: String? = null, message: String, domain: String? = null, responseInfo: AdResponseInfo? = null)`, so `message` is the only required argument.

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: advancesCooldown`.

- [ ] **Step 3: Write the rule**

```kotlin
package dev.avinya.admob.showcase.domain.ad

import dev.avinya.ads.AdShowResult

/**
 * Whether a presentation attempt may advance the interstitial cooldown.
 *
 * Only [AdShowResult.Shown] counts. A `NotReady` or `Failed` ad never
 * appeared, so charging the user 60 seconds of suppression for it is a
 * user-facing bug — one this showcase shipped once already.
 *
 * Pure so the rule cannot drift without a test failing.
 */
fun advancesCooldown(result: AdShowResult): Boolean = result is AdShowResult.Shown
```

- [ ] **Step 4: Route the handler through it**

In `AdEffectHandler.kt`, replace the `when (result)` branch that calls `onShown()` so the decision reads from the rule rather than restating it:

```kotlin
                            val result = interstitial.show()
                            if (advancesCooldown(result)) {
                                onShown()
                            } else {
                                onSuppressed(SuppressionReason.NotReady)
                            }
```

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
```

```bash
git add showcase/src && git commit -m "$(cat <<'EOF'
test(showcase): guard the cooldown-only-on-shown rule

Phase 4 fixed this in code but left no regression test, so the bug could
return silently. The rule is now a pure function with its own test, and
AdEffectHandler reads it rather than restating the condition.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Queries and repository methods the Store and Library need

**Files:**
- Modify: `showcase/src/commonMain/.../data/db/dao/ArticleDao.kt`
- Modify: `showcase/src/commonMain/.../data/repo/ArticleRepository.kt`
- Create: `showcase/src/commonMain/.../domain/library/LibraryEntry.kt`

**Interfaces:**
- Produces: `ArticleDao.premiumArticles()`, `bookmarkedArticles()`, `inProgressArticles()`, `unlockedArticles()`; `ArticleRepository.premiumCatalog()`, `unlock(articleId, source)`, `library()`; `data class LibraryEntry`.

- [ ] **Step 1: Add the DAO queries**

```kotlin
    @Query("SELECT * FROM articles WHERE isPremium = 1 ORDER BY feedOrdinal ASC")
    fun premiumArticles(): Flow<List<ArticleEntity>>

    @Query(
        "SELECT a.* FROM articles a " +
            "INNER JOIN bookmarks b ON b.articleId = a.id " +
            "ORDER BY b.createdAt DESC",
    )
    fun bookmarkedArticles(): Flow<List<ArticleEntity>>

    @Query(
        "SELECT a.* FROM articles a " +
            "INNER JOIN reading_progress p ON p.articleId = a.id " +
            "WHERE p.scrollFraction > 0.0 AND p.scrollFraction < 1.0 " +
            "ORDER BY p.updatedAt DESC",
    )
    fun inProgressArticles(): Flow<List<ArticleEntity>>

    @Query(
        "SELECT a.* FROM articles a " +
            "INNER JOIN unlocks u ON u.articleId = a.id " +
            "ORDER BY u.unlockedAt DESC",
    )
    fun unlockedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT articleId FROM unlocks")
    fun unlockedIds(): Flow<List<String>>
```

Room validates this SQL at compile time, so a wrong column or join is a build failure rather than a runtime surprise.

- [ ] **Step 2: Add the library model**

```kotlin
package dev.avinya.admob.showcase.domain.library

/** One row in the Library, tagged with why it is there. */
data class LibraryEntry(
    val articleId: String,
    val title: String,
    val section: String,
    val readTimeMin: Int,
    val kind: Kind,
) {
    enum class Kind { Bookmarked, InProgress, Unlocked }
}
```

- [ ] **Step 3: Add the repository methods**

```kotlin
    /** Premium articles, with their current unlock state. */
    fun premiumCatalog(): Flow<List<PremiumArticle>> = combine(
        articleDao.premiumArticles(),
        articleDao.unlockedIds(),
    ) { articles, unlockedIds ->
        val unlocked = unlockedIds.toSet()
        articles.map { article ->
            PremiumArticle(
                id = article.id,
                title = article.title,
                section = article.section,
                costCoins = article.unlockCostCoins,
                isUnlocked = article.id in unlocked,
            )
        }
    }

    /** Records an unlock. `IGNORE` on the insert makes a repeat a no-op. */
    suspend fun unlock(articleId: String, source: UnlockSource) {
        articleDao.addUnlock(
            UnlockEntity(articleId = articleId, unlockedAt = clock.nowMillis(), source = source),
        )
    }

    fun library(): Flow<List<LibraryEntry>> = combine(
        articleDao.bookmarkedArticles(),
        articleDao.inProgressArticles(),
        articleDao.unlockedArticles(),
    ) { bookmarked, inProgress, unlocked ->
        bookmarked.map { it.toLibraryEntry(LibraryEntry.Kind.Bookmarked) } +
            inProgress.map { it.toLibraryEntry(LibraryEntry.Kind.InProgress) } +
            unlocked.map { it.toLibraryEntry(LibraryEntry.Kind.Unlocked) }
    }
```

with, at file scope:

```kotlin
data class PremiumArticle(
    val id: String,
    val title: String,
    val section: String,
    val costCoins: Int,
    val isUnlocked: Boolean,
)

private fun ArticleEntity.toLibraryEntry(kind: LibraryEntry.Kind) = LibraryEntry(
    articleId = id,
    title = title,
    section = section,
    readTimeMin = readTimeMin,
    kind = kind,
)
```

- [ ] **Step 4: Verify and commit**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test :showcase:compileKotlinIosArm64 --no-configuration-cache
```

```bash
git add showcase/src && git commit -m "$(cat <<'EOF'
feat(showcase): add premium catalog and library queries

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Rewarded ads that credit from the callback

The heart of the phase.

**Files:**
- Modify: `showcase/src/commonMain/.../domain/ad/ShowcasePlacements.kt`
- Create: `showcase/src/commonMain/.../domain/wallet/RewardGrant.kt`
- Create: `showcase/src/commonMain/.../ui/ad/RewardedAdRunner.kt`
- Test: `showcase/src/commonTest/.../domain/wallet/RewardGrantTest.kt`

**Interfaces:**
- Produces: `ShowcasePlacements.storeRewarded`, `storeRewardedInterstitial`; `fun rewardGrantKey(placementId, sessionId, sequence): String`; `fun coinsFor(reward: AdReward?): Int?`; `sealed interface RewardOutcome`; `suspend fun runRewarded(...)`.

- [ ] **Step 1: Add the placements**

`AdPlacement` has **no** `serverSideVerificationOptions` parameter — SSV lives inside `fullScreenOptions`:

```kotlin
    val storeRewarded: AdPlacement = AdPlacement(
        id = "store_rewarded",
        format = AdFormat.Rewarded,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_REWARDED, ios = TestAdIds.IOS_REWARDED),
        fullScreenOptions = FullScreenAdOptions(
            // A real coin economy would verify server-side. Standing up the
            // endpoint is out of scope; setting the options shows where it goes.
            serverSideVerification = ServerSideVerificationOptions(
                userId = "showcase-demo-user",
                customData = "store_rewarded",
            ),
        ),
        strictTestMode = true,
    )

    val storeRewardedInterstitial: AdPlacement = AdPlacement(
        id = "store_rewarded_interstitial",
        format = AdFormat.RewardedInterstitial,
        adUnitIds = AdUnitIds(
            android = TestAdIds.ANDROID_REWARDED_INTERSTITIAL,
            ios = TestAdIds.IOS_REWARDED_INTERSTITIAL,
        ),
        strictTestMode = true,
    )
```

- [ ] **Step 2: Write the failing test**

```kotlin
package dev.avinya.admob.showcase.domain.wallet

import dev.avinya.ads.AdReward
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RewardGrantTest {

    @Test
    fun theSameRewardYieldsTheSameKeySoAReplayCannotDoubleCredit() {
        assertEquals(
            rewardGrantKey("store_rewarded", "session-1", 4),
            rewardGrantKey("store_rewarded", "session-1", 4),
        )
    }

    @Test
    fun successiveRewardsInASessionGetDistinctKeys() {
        assertNotEquals(
            rewardGrantKey("store_rewarded", "session-1", 4),
            rewardGrantKey("store_rewarded", "session-1", 5),
        )
    }

    @Test
    fun differentSessionsAndPlacementsDoNotCollide() {
        assertNotEquals(
            rewardGrantKey("store_rewarded", "session-1", 4),
            rewardGrantKey("store_rewarded", "session-2", 4),
        )
        assertNotEquals(
            rewardGrantKey("store_rewarded", "session-1", 4),
            rewardGrantKey("store_rewarded_interstitial", "session-1", 4),
        )
    }

    @Test
    fun aWholeRewardConvertsToCoins() {
        assertEquals(50, coinsFor(AdReward(amountMicros = 50_000_000L, type = "coins")))
    }

    @Test
    fun aFractionalRewardIsRejectedRatherThanRounded() {
        // AdReward.wholeAmountOrNull() returns null for fractional amounts.
        // Rounding would silently over- or under-pay; refusing is honest.
        assertNull(coinsFor(AdReward(amountMicros = 1_500_000L, type = "coins")))
    }

    @Test
    fun noRewardMeansNoCoins() {
        // The user dismissed before earning. There is no consolation grant.
        assertNull(coinsFor(null))
    }
}
```

- [ ] **Step 3: Run to verify it fails**, then implement:

```kotlin
package dev.avinya.admob.showcase.domain.wallet

import dev.avinya.ads.AdReward

/**
 * Identity for one reward grant.
 *
 * A reward callback is not guaranteed to fire exactly once, so every credit
 * carries a key a replay reproduces — letting the wallet return
 * [CreditResult.AlreadyGranted] instead of paying twice.
 */
fun rewardGrantKey(placementId: String, sessionId: String, sequence: Int): String =
    "$placementId:$sessionId:$sequence"

/**
 * Coins earned from [reward], or null when nothing should be credited.
 *
 * Null means **do not credit**: either no reward callback fired (the user
 * dismissed early) or the reward was fractional and cannot map to whole
 * coins. Rounding a fractional reward would silently over- or under-pay.
 */
fun coinsFor(reward: AdReward?): Int? = reward?.wholeAmountOrNull()
```

- [ ] **Step 4: Write the runner**

```kotlin
package dev.avinya.admob.showcase.ui.ad

import dev.avinya.ads.AdReward
import dev.avinya.ads.AdShowResult
import dev.avinya.ads.FullScreenAdController
import dev.avinya.admob.showcase.data.repo.WalletRepository
import dev.avinya.admob.showcase.domain.wallet.CreditResult
import dev.avinya.admob.showcase.domain.wallet.coinsFor

sealed interface RewardOutcome {
    data class Earned(val coins: Int, val newBalance: Int) : RewardOutcome
    data object AlreadyGranted : RewardOutcome
    data object DismissedWithoutReward : RewardOutcome
    data object NotReady : RewardOutcome
    data class Failed(val message: String) : RewardOutcome
}

/**
 * Runs a rewarded presentation and credits the wallet.
 *
 * [show] is passed in rather than the controller itself because
 * `RewardedAdController` and `RewardedInterstitialAdController` are unrelated
 * types with identical `show` shapes; a function parameter unifies them
 * without touching the SDK.
 *
 * **The credit comes from [onReward], not from the returned [AdShowResult].**
 * A user who dismisses early still yields `Shown`; crediting on it pays for
 * an ad that was not watched.
 */
suspend fun runRewarded(
    load: suspend () -> Unit,
    show: suspend (onReward: (AdReward) -> Unit) -> AdShowResult,
    wallet: WalletRepository,
    grantKey: String,
): RewardOutcome {
    load()

    var earned: AdReward? = null
    val result = try {
        show { reward -> earned = reward }
    } catch (throwable: Throwable) {
        // A platform that throws instead of returning Failed must not kill the
        // caller's coroutine and freeze the screen.
        return RewardOutcome.Failed(throwable.message ?: "presentation failed")
    }

    val coins = coinsFor(earned)
    return when {
        coins != null -> when (val credit = wallet.credit(coins, grantKey)) {
            is CreditResult.Credited -> RewardOutcome.Earned(coins, credit.newBalance)
            CreditResult.AlreadyGranted -> RewardOutcome.AlreadyGranted
        }
        result is AdShowResult.NotReady -> RewardOutcome.NotReady
        result is AdShowResult.Failed -> RewardOutcome.Failed(result.error.message)
        // Shown but no reward: dismissed before earning. No consolation grant.
        else -> RewardOutcome.DismissedWithoutReward
    }
}
```

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
```

---

### Task 4: The Store screen

**Files:**
- Create: `showcase/src/commonMain/.../feature/store/StoreContract.kt`
- Create: `showcase/src/commonMain/.../feature/store/StoreViewModel.kt`
- Create: `showcase/src/commonMain/.../feature/store/StoreScreen.kt`
- Create: `showcase/src/commonMain/.../ui/ad/AppOpenSuppression.kt`
- Modify: `showcase/src/commonMain/.../nav/ShowcaseNavHost.kt`

- [ ] **Step 1: Add the suppression seam**

Phase 6 backs this with the coordinator; defining it now means the unlock flow is written once, correctly.

```kotlin
package dev.avinya.admob.showcase.ui.ad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lets a flow declare that an app-open ad must not appear over it.
 *
 * Depth-counted rather than boolean: two overlapping flows must not
 * un-suppress each other on the way out.
 *
 * Phase 6 binds [isBlocked] to `AppOpenAdCoordinator.isBlocked`.
 */
class AppOpenSuppressor {
    private var depth by mutableStateOf(0)

    val isBlocked: Boolean get() = depth > 0

    fun enter() { depth++ }

    fun exit() { depth = (depth - 1).coerceAtLeast(0) }
}

/** Runs [block] with app-open ads suppressed, restoring state even on failure. */
suspend fun <T> AppOpenSuppressor.suppressing(block: suspend () -> T): T {
    enter()
    return try {
        block()
    } finally {
        // A suppression that leaks silently disables app-open ads for the rest
        // of the session, so the restore must survive an exception.
        exit()
    }
}
```

> `suppressing` is a suspend function taking a suspend lambda — **not** `inline`. An inline function cannot safely wrap suspending work in `try/finally` across suspension points here.

- [ ] **Step 2: Write the contract**

```kotlin
package dev.avinya.admob.showcase.feature.store

import dev.avinya.admob.showcase.data.repo.PremiumArticle
import dev.avinya.admob.showcase.ui.ad.RewardOutcome

enum class RewardedUiState { Idle, Loading, Showing, Unavailable }

data class StoreState(
    val balance: Int = 0,
    val premium: List<PremiumArticle> = emptyList(),
    val rewarded: RewardedUiState = RewardedUiState.Idle,
    val offerWallVisible: Boolean = false,
    val adsEnabled: Boolean = true,
    val sdkReady: Boolean = false,
)

sealed interface StoreIntent {
    data object WatchRewardedAd : StoreIntent
    data object OpenOfferWall : StoreIntent
    data object AcceptOfferWall : StoreIntent
    data object DeclineOfferWall : StoreIntent
    data class Unlock(val article: PremiumArticle) : StoreIntent
}

sealed interface StoreEffect {
    data class RewardResult(val outcome: RewardOutcome) : StoreEffect
    data class Unlocked(val title: String) : StoreEffect
    data class NeedMoreCoins(val shortfall: Int) : StoreEffect
}
```

- [ ] **Step 3: Write the ViewModel**

```kotlin
package dev.avinya.admob.showcase.feature.store

import androidx.lifecycle.viewModelScope
import dev.avinya.ads.AdManager
import dev.avinya.ads.AdManagerStatus
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.data.db.entity.UnlockSource
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.data.repo.ArticleRepository
import dev.avinya.admob.showcase.data.repo.PremiumArticle
import dev.avinya.admob.showcase.data.repo.WalletRepository
import dev.avinya.admob.showcase.domain.wallet.DebitResult
import dev.avinya.admob.showcase.domain.wallet.rewardGrantKey
import dev.avinya.admob.showcase.ui.ad.AppOpenSuppressor
import dev.avinya.admob.showcase.ui.ad.RewardOutcome
import dev.avinya.admob.showcase.ui.ad.suppressing
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class StoreViewModel(
    private val wallet: WalletRepository,
    private val articles: ArticleRepository,
    private val suppressor: AppOpenSuppressor,
    private val sessionId: String,
    settings: SettingsRepository,
    adManager: AdManager,
) : MviViewModel<StoreState, StoreIntent, StoreEffect>(StoreState()) {

    /** Monotonic within a session; combined with [sessionId] into the grant key. */
    private var rewardSequence = 0

    init {
        combine(
            wallet.balance(),
            articles.premiumCatalog(),
            settings.adsMasterSwitch,
            adManager.status,
        ) { balance, premium, adsEnabled, status ->
            StoreState(
                balance = balance,
                premium = premium,
                rewarded = state.value.rewarded,
                offerWallVisible = state.value.offerWallVisible,
                adsEnabled = adsEnabled,
                sdkReady = status == AdManagerStatus.Ready,
            )
        }.onEach { next -> updateState { next } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: StoreIntent) {
        when (intent) {
            StoreIntent.WatchRewardedAd -> Unit // driven by the screen; see nextGrantKey()
            StoreIntent.OpenOfferWall -> updateState { copy(offerWallVisible = true) }
            StoreIntent.AcceptOfferWall, StoreIntent.DeclineOfferWall ->
                updateState { copy(offerWallVisible = false) }
            is StoreIntent.Unlock -> unlock(intent.article)
        }
    }

    /** Allocates the next idempotency key. Called once per presentation attempt. */
    fun nextGrantKey(placementId: String): String =
        rewardGrantKey(placementId, sessionId, ++rewardSequence)

    fun setRewardedState(next: RewardedUiState) {
        updateState { copy(rewarded = next) }
    }

    fun onRewardOutcome(outcome: RewardOutcome) {
        updateState { copy(rewarded = RewardedUiState.Idle) }
        viewModelScope.launch { emitEffect(StoreEffect.RewardResult(outcome)) }
    }

    private fun unlock(article: PremiumArticle) {
        if (article.isUnlocked) return
        viewModelScope.launch {
            // Suppressed for the whole transaction: an app-open ad appearing
            // mid-purchase is both a bad experience and a policy problem.
            suppressor.suppressing {
                when (val result = wallet.debit(article.costCoins)) {
                    is DebitResult.Debited -> {
                        articles.unlock(article.id, UnlockSource.COINS)
                        emitEffect(StoreEffect.Unlocked(article.title))
                    }
                    is DebitResult.InsufficientFunds ->
                        emitEffect(StoreEffect.NeedMoreCoins(result.required - result.balance))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Write the screen**

```kotlin
@Composable
fun StoreScreen() {
    val adManager = LocalAdManager.current
    val graph = LocalAppGraph.current
    val suppressor = LocalAppOpenSuppressor.current
    val sessionId = rememberSessionId()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val viewModel: StoreViewModel = viewModel {
        StoreViewModel(
            graph.wallet, graph.articles, suppressor, sessionId, graph.settings, adManager,
        )
    }
    val state by viewModel.state.collectAsState()

    val rewarded = remember(adManager) { adManager.rewarded(ShowcasePlacements.storeRewarded) }
    val offerWall = remember(adManager) {
        adManager.rewardedInterstitial(ShowcasePlacements.storeRewardedInterstitial)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            snackbar.showSnackbar(effect.message())
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("${state.balance} coins", style = MaterialTheme.typography.headlineLarge) }

            item {
                Button(
                    enabled = state.adsEnabled && state.sdkReady &&
                        state.rewarded == RewardedUiState.Idle,
                    onClick = {
                        // launch, not inline: the presentation suspends for the
                        // ad's whole lifetime and must not gate the UI thread's
                        // effect processing.
                        scope.launch {
                            viewModel.setRewardedState(RewardedUiState.Showing)
                            val outcome = runRewarded(
                                load = { rewarded.load() },
                                show = { onReward -> rewarded.show(onReward = onReward) },
                                wallet = graph.wallet,
                                grantKey = viewModel.nextGrantKey(ShowcasePlacements.storeRewarded.id),
                            )
                            viewModel.onRewardOutcome(outcome)
                        }
                    },
                ) {
                    Text(
                        when (state.rewarded) {
                            RewardedUiState.Showing -> "Loading…"
                            else -> "Watch an ad to earn coins"
                        },
                    )
                }
            }

            item {
                OutlinedButton(
                    enabled = state.adsEnabled && state.sdkReady,
                    onClick = { viewModel.onIntent(StoreIntent.OpenOfferWall) },
                ) { Text("See today's offer") }
            }

            items(state.premium, key = { it.id }) { article ->
                PremiumRow(
                    article = article,
                    balance = state.balance,
                    onUnlock = { viewModel.onIntent(StoreIntent.Unlock(article)) },
                )
            }
        }
    }

    if (state.offerWallVisible) {
        OfferWallDialog(
            onAccept = {
                viewModel.onIntent(StoreIntent.AcceptOfferWall)
                scope.launch {
                    val outcome = runRewarded(
                        load = { offerWall.load() },
                        show = { onReward -> offerWall.show(onReward = onReward) },
                        wallet = graph.wallet,
                        grantKey = viewModel.nextGrantKey(
                            ShowcasePlacements.storeRewardedInterstitial.id,
                        ),
                    )
                    viewModel.onRewardOutcome(outcome)
                }
            },
            onDecline = { viewModel.onIntent(StoreIntent.DeclineOfferWall) },
        )
    }
}
```

Write `PremiumRow`, `OfferWallDialog`, `rememberSessionId()` and `StoreEffect.message()` in the same file. `rememberSessionId()` is `remember { clock.nowMillis().toString() }` — stable for the screen's lifetime, so a reward replay within one session reuses its key.

**`OfferWallDialog` must be declinable.** Rewarded-interstitial policy requires an intro screen the user can refuse; make Decline a real, equal-weight action, not a dismiss-by-tapping-outside.

Add `LocalAppOpenSuppressor` as a `CompositionLocal` provided in `ShowcaseApp`, alongside `LocalAppGraph`.

- [ ] **Step 5: Wire into navigation**

Phase 4 shipped its whole article feature unreachable because this step was left implicit. Do it explicitly.

In `ShowcaseNavHost.kt`, replace the Store entry:

```kotlin
                entry<ShowcaseNavKey.Store> { StoreScreen() }
```

and add `import dev.avinya.admob.showcase.feature.store.StoreScreen`.

In `ShowcaseApp.kt`, provide the suppressor above the nav host so both the Store and Phase 6's coordinator see the same instance:

```kotlin
    val suppressor = remember { AppOpenSuppressor() }

    CompositionLocalProvider(
        LocalAppGraph provides graph,
        LocalAppOpenSuppressor provides suppressor,
    ) {
        // … ProvideAdManager { ShowcaseTheme { … } } unchanged …
    }
```

`remember { }` with **no keys** — the suppressor must outlive every recomposition, and keying it would reset suppression depth mid-transaction.

- [ ] **Step 6: Verify on device**

```bash
./gradlew :androidApp:installDebug
```

Four behaviours, all required:

1. Watch a rewarded ad to completion → balance increases, snackbar confirms.
2. **Dismiss one early → no credit**, and the message says no reward was earned.
3. Offer wall shows an intro that can be declined; declining costs nothing.
4. Unlock a premium article → coins spent, shown as unlocked, and it survives:

```bash
adb shell am force-stop dev.avinya.admob.cmp && ./gradlew :androidApp:installDebug
```

- [ ] **Step 7: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add the Store with rewarded ads and coin unlocks

Credit comes from the reward callback, never from show() returning
Shown — a user who dismisses early still yields Shown, and crediting on
it pays for an ad that was not watched. A replayed callback hits the
idempotency key and returns AlreadyGranted.

A fractional AdReward credits nothing rather than being rounded:
AdReward exposes amountMicros with wholeAmountOrNull(), and rounding
would silently over- or under-pay.

Unlock transactions run inside AppOpenSuppressor.suppressing so an
app-open ad cannot appear mid-purchase; the finally restores depth even
if the transaction throws.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: The Library — deliberately ad-free

**Files:**
- Create: `showcase/src/commonMain/.../feature/library/LibraryScreen.kt`
- Modify: `showcase/src/commonMain/.../nav/ShowcaseNavHost.kt`

- [ ] **Step 1: Write the screen**

```kotlin
@Composable
fun LibraryScreen(onArticleClick: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val entries by graph.articles.library().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LibraryEntry.Kind.entries.forEach { kind ->
            val group = entries.filter { it.kind == kind }
            if (group.isNotEmpty()) {
                item(key = "header_$kind") {
                    Text(kind.label(), style = MaterialTheme.typography.titleMedium)
                }
                items(group, key = { "${it.kind}_${it.articleId}" }) { entry ->
                    LibraryRow(entry = entry, onClick = { onArticleClick(entry.articleId) })
                }
            }
        }

        if (entries.isEmpty()) {
            item { Text("Bookmark or unlock an article and it will appear here.") }
        }

        item {
            // Stated in the UI on purpose. A showcase that puts an ad on every
            // screen teaches the wrong lesson; restraint is part of a good
            // integration, and saying so is more useful than silently omitting.
            Text(
                "No ads here — ads belong where you are browsing, not where you " +
                    "are managing things you already own.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

**No `BannerAdView`, no `NativeAdView`, no interstitial on this screen.** That is the demonstration.

- [ ] **Step 2: Wire into navigation, verify, commit**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
cd docs-site && npm test && cd ..
```

- [ ] **Step 3: Run on iOS**

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination "generic/platform=iOS Simulator" \
  CODE_SIGNING_ALLOWED=NO build
```

Then run it from Xcode against a simulator. Rewarded ads use a different presentation path on iOS, so confirm both crediting **and** early dismissal behave identically to Android — neither is implied by the Android run.

- [ ] **Step 4: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add the ad-free Library screen

Bookmarks, in-progress and unlocked articles, grouped. Carries no ads at
all, and says so in the UI: a showcase that puts an ad on every screen
teaches the wrong lesson, and stating the reasoning is more useful than
silently omitting them.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Exit criteria

- [ ] Rewarded ad watched to completion credits coins; balance persists across restart
- [ ] Dismissing early credits **nothing** and says so
- [ ] A replayed reward callback returns `AlreadyGranted` — no double credit
- [ ] A fractional reward credits nothing rather than rounding
- [ ] Rewarded-interstitial shows a **declinable** intro
- [ ] Unlocking spends coins, persists, and suppresses app-open for the transaction
- [ ] `AppOpenSuppressor` restores depth via `finally`, verified by a deliberate mid-transaction failure
- [ ] Library shows bookmarks, in-progress and unlocks — with **zero** ads
- [ ] `AdPresentationTest`, `RewardGrantTest`, `CoinEconomyTest` pass on both platforms
- [ ] `git diff --stat master..HEAD -- 'admob-cmp*'` is empty

---

## Next plan

**Phase 6 — App-open, Inspector, polish** (`2026-08-06-showcase-phase-6-appopen-inspector.md`): binds `AppOpenSuppressor` to the real coordinator, adds the telemetry pipeline and Inspector, and closes the branch. Bring it to execution fidelity before starting it.
