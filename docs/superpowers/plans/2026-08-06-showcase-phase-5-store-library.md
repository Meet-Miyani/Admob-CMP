# Showcase — Phase 5: Store & Library

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A coin economy where rewarded ads have a real, persisted consequence — plus a rewarded-interstitial offer wall, an unlock transaction that suppresses app-open ads, and the Library screen that deliberately carries no ads at all.

**Architecture:** Coin rules stay pure in `domain/wallet`; `WalletRepository` is the persistence adapter. Rewards are credited from the SDK's `onReward` callback — never from `show()` returning — and guarded by an idempotency key so a replayed callback cannot double-credit.

**Tech Stack:** Compose Multiplatform 1.11.1, Navigation3, Room 2.8.4.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** Phase 4 complete — `AdPolicy` and the interstitial work.

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/`. Record gaps in `docs/showcase-sdk-gaps.md`, work around inside `:showcase`, escalate. **Stop and ask.**
- **Kotlin stays at 2.3.20. No new dependencies.**
- **Testing principle.** Test what a consumer would copy. The coin rules are pure functions over values; do not add a test runtime to observe them through Room.
- **Do not modify** `gradle.properties`, the plugin's `gradle.properties`, or `.github/workflows/release.yml`. **Do not commit** `api/*.klib.api` changes.
- Package root `dev.avinya.admob.showcase`. Branch `feat/showcase-app`. No PR without the owner's confirmation.
- Commits end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

### The rule this phase exists to demonstrate

```
RewardedAdController.show(options, onReward: (AdReward) -> Unit): AdShowResult
```

**Credit from `onReward`. Never from `show()` returning `Shown`.** A user who dismisses the ad early still gets a `Shown` result — crediting on it pays for an ad that was not watched. This is the classic rewarded-ads bug, and the showcase exists partly to demonstrate not committing it.

---

## Tasks

### Task 1: Coin wallet UI and the Store scaffold

**Files:**
- Create: `showcase/src/commonMain/.../feature/store/StoreContract.kt`
- Create: `showcase/src/commonMain/.../feature/store/StoreViewModel.kt`
- Create: `showcase/src/commonMain/.../feature/store/StoreScreen.kt`
- Modify: `showcase/src/commonMain/.../nav/ShowcaseNavHost.kt`

**Interfaces:**
- Consumes: `WalletRepository`, `ArticleRepository`, `domain.wallet` rules, `LocalAdManager`.
- Produces: `StoreState/Intent/Effect`, `@Composable fun StoreScreen()`.

- [ ] **Step 1: Build the state**

`StoreState(balance: Int, premiumArticles: List<PremiumArticle>, rewardedState: RewardedUiState, busy: Boolean)` where `RewardedUiState` is `Idle | Loading | Ready | Showing | Unavailable`. Surfacing the load state is deliberate: consumers need to see that a rewarded ad must be *loaded* before it can be *shown*.

- [ ] **Step 2: Render**

Coin balance, an "Earn 50 coins" card, an offer-wall entry point, and the list of premium articles with their unlock cost and current unlock state. Replace the `PlaceholderScreen("Store")` entry in `ShowcaseNavHost`.

- [ ] **Step 3: Verify and commit**

---

### Task 2: Rewarded ad with callback-based crediting

**Files:**
- Modify: `showcase/src/commonMain/.../domain/ad/ShowcasePlacements.kt`
- Create: `showcase/src/commonMain/.../ui/ad/RewardedAdRunner.kt`
- Modify: `showcase/src/commonMain/.../feature/store/StoreViewModel.kt`
- Test: `showcase/src/commonTest/.../domain/wallet/RewardGrantKeyTest.kt`

**Interfaces:**
- Produces: `fun rewardGrantKey(placementId: String, sessionId: String, sequence: Int): String`, `@Composable fun rememberRewardedAdRunner(): RewardedAdRunner`.

- [ ] **Step 1: Add the placement**

Signatures below are verified against `admob-cmp-core` source — use them exactly.

`AdPlacement` has **no** `serverSideVerificationOptions` parameter. SSV lives inside `FullScreenAdOptions`, reached through the placement's `fullScreenOptions`:

```kotlin
    val storeRewarded = AdPlacement(
        id = "store_rewarded",
        format = AdFormat.Rewarded,
        adUnitIds = AdUnitIds(android = TestAdIds.ANDROID_REWARDED, ios = TestAdIds.IOS_REWARDED),
        fullScreenOptions = FullScreenAdOptions(
            serverSideVerification = ServerSideVerificationOptions(
                userId = "showcase-demo-user",
                customData = "showcase",
            ),
        ),
        strictTestMode = true,
    )
```

Verified shapes: `AdUnitIds(android: String, ios: String)`, `FullScreenAdOptions(immersiveMode: Boolean = false, serverSideVerification: ServerSideVerificationOptions? = null)`, `ServerSideVerificationOptions(userId: String? = null, customData: String? = null)`.

SSV options are set because a real coin economy would verify server-side; standing up the verification endpoint itself is out of scope, and a comment should say so at the call site.

- [ ] **Step 2: Write the failing idempotency-key test**

```kotlin
package dev.avinya.admob.showcase.domain.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RewardGrantKeyTest {

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
    fun differentSessionsDoNotCollide() {
        assertNotEquals(
            rewardGrantKey("store_rewarded", "session-1", 4),
            rewardGrantKey("store_rewarded", "session-2", 4),
        )
    }

    @Test
    fun differentPlacementsDoNotCollide() {
        assertNotEquals(
            rewardGrantKey("store_rewarded", "session-1", 4),
            rewardGrantKey("store_rewarded_interstitial", "session-1", 4),
        )
    }
}
```

- [ ] **Step 3: Run to verify it fails**, then implement:

```kotlin
package dev.avinya.admob.showcase.domain.wallet

/**
 * Identity for one reward grant.
 *
 * The reward callback is not guaranteed to fire exactly once, so every credit
 * carries a key that a replay will reproduce — letting the wallet recognise
 * it and return `AlreadyGranted` instead of paying twice.
 */
fun rewardGrantKey(placementId: String, sessionId: String, sequence: Int): String =
    "$placementId:$sessionId:$sequence"
```

- [ ] **Step 4: Write the runner**

```kotlin
suspend fun RewardedAdController.runAndCredit(
    wallet: WalletRepository,
    grantKey: String,
    onOutcome: (RewardOutcome) -> Unit,
) {
    load()

    var reward: AdReward? = null
    val result = show { earned ->
        // THE correctness point: credit here, from the reward callback — not
        // from `result` below. A user who dismisses early still gets a
        // `Shown` result, and crediting on it pays for an unwatched ad.
        reward = earned
    }

    when {
        reward != null -> onOutcome(
            RewardOutcome.Earned(wallet.credit(reward!!.amount, grantKey)),
        )
        result is AdShowResult.NotReady -> onOutcome(RewardOutcome.NotReady)
        result is AdShowResult.Failed -> onOutcome(RewardOutcome.Failed(result.error))
        // Shown but no reward: the user dismissed before earning it. No credit,
        // no consolation grant. That is correct, not a gap.
        else -> onOutcome(RewardOutcome.Dismissed)
    }
}
```

Call `show()` from a UI-scoped coroutine — never `GlobalScope`; it suspends for the ad's entire on-screen lifetime.

- [ ] **Step 5: Verify on device**

Watch a rewarded ad to completion → balance increases by 50. Dismiss one early → **no credit**, and the UI says "no reward earned". Both behaviours are the demonstration.

- [ ] **Step 6: Commit**

---

### Task 3: Rewarded-interstitial offer wall

**Files:**
- Modify: `showcase/src/commonMain/.../domain/ad/ShowcasePlacements.kt`
- Modify: `showcase/src/commonMain/.../feature/store/StoreScreen.kt`

- [ ] **Step 1: Add the placement**

```kotlin
    val storeRewardedInterstitial = AdPlacement(
        id = "store_rewarded_interstitial",
        format = AdFormat.RewardedInterstitial,
        adUnitIds = AdUnitIds(
            android = TestAdIds.ANDROID_REWARDED_INTERSTITIAL,
            ios = TestAdIds.IOS_REWARDED_INTERSTITIAL,
        ),
        strictTestMode = true,
    )
```

- [ ] **Step 2: Present it as an opt-in**

Rewarded-interstitial policy requires an **intro screen** the user can decline. Show a dialog explaining the offer with explicit Accept/Decline before calling `show()`. Declining is a first-class path, not a dead end.

Credit through the same `runAndCredit` path with its own grant key.

- [ ] **Step 3: Verify and commit**

---

### Task 4: Unlock transaction with app-open suppression, and the Library

**Files:**
- Create: `showcase/src/commonMain/.../feature/library/LibraryScreen.kt` (+ contract, ViewModel)
- Modify: `showcase/src/commonMain/.../feature/store/StoreViewModel.kt`
- Create: `showcase/src/commonMain/.../ui/ad/AppOpenSuppression.kt`

- [ ] **Step 1: Add the suppression seam**

Phase 6 creates the coordinator; this phase defines the seam it will plug into, so the unlock flow does not have to be rewritten later:

```kotlin
package dev.avinya.admob.showcase.ui.ad

/**
 * Lets a flow declare that an app-open ad must not appear over it.
 *
 * Phase 6 backs this with `AppOpenAdCoordinator.isBlocked`. Until then it is
 * a no-op holder, so the call sites that need suppression can be written once
 * and correctly.
 */
class AppOpenSuppressor {
    private var depth = 0
    val isBlocked: Boolean get() = depth > 0

    inline fun <T> suppressing(block: () -> T): T {
        enter()
        try {
            return block()
        } finally {
            exit()
        }
    }

    fun enter() { depth++ }
    fun exit() { depth = (depth - 1).coerceAtLeast(0) }
}
```

Counting depth rather than using a boolean means two overlapping flows cannot un-suppress each other on the way out.

- [ ] **Step 2: Implement unlocking**

```kotlin
appOpenSuppressor.suppressing {
    when (val result = wallet.debit(article.unlockCostCoins)) {
        is DebitResult.Debited -> articles.unlock(article.id, UnlockSource.COINS)
        is DebitResult.InsufficientFunds -> emitEffect(StoreEffect.NeedMoreCoins(result))
    }
}
```

The `finally` inside `suppressing` restores state even if the transaction throws — a suppression that leaks would silently disable app-open ads for the rest of the session.

- [ ] **Step 3: Build the Library screen**

Bookmarks, in-progress articles (from `reading_progress`), and unlocked articles with their `UnlockSource`.

**No ads. At all.** Add a short visible note explaining why: ads belong where the user is browsing, not where they are managing things they already own. Restraint is part of a good integration, and a showcase that puts an ad on every screen teaches the wrong lesson.

- [ ] **Step 4: Full verification and commit**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinJs --no-configuration-cache
```

---

## Exit criteria

- [ ] Rewarded ad watched to completion credits exactly 50 coins
- [ ] Dismissing early credits **nothing** and says so
- [ ] A replayed reward callback returns `AlreadyGranted` and does not double-credit
- [ ] Rewarded-interstitial shows an intro the user can decline
- [ ] Unlocking a premium article spends coins and persists across restart
- [ ] `AppOpenSuppressor` restores state via `finally`, verified by a deliberate mid-transaction failure
- [ ] Library shows bookmarks, progress and unlocks — with **zero** ads
- [ ] `CoinEconomyTest` and `RewardGrantKeyTest` pass on both platforms
- [ ] `git diff --stat master -- 'admob-cmp*'` is empty

---

## Next plan

**Phase 6 — App-open, Inspector, polish** (`2026-08-06-showcase-phase-6-appopen-inspector.md`): `AppOpenAdCoordinator`, the telemetry pipeline, the Inspector's three tabs, and the final documentation and verification pass that closes the branch.
