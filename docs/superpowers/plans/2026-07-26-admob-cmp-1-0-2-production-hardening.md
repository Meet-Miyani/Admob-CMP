# AdMob CMP 1.0.2 Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship AdMob CMP 1.0.2 with a mediation-safe reward contract, correct consent recovery, deterministic native ownership, immutable retained configuration, and consistent Android/iOS/Compose behavior.

**Architecture:** Keep policy and lifecycle state in common Kotlin, with Android and iOS files limited to native SDK mapping and callbacks. Reward delivery becomes an explicit at-most-once callback channel independent from presentation results, while `AdEvent.RewardEarned` remains telemetry. Long-lived manager/controller inputs are deep-snapshotted at ownership boundaries, and native full-screen ownership remains callback-owned after SDK handoff.

**Tech Stack:** Kotlin 2.3.20, Kotlin Multiplatform, Compose Multiplatform 1.11.1, kotlinx.coroutines, Google Mobile Ads Next-Gen Android SDK, Google Mobile Ads iOS bindings, UMP, Kotlin ABI validation, Kotlin test.

## Global Constraints

- Target release is `1.0.2`; compatibility with `1.0.1` is not required.
- Do not commit, push, publish remotely, or create a pull request unless the user explicitly requests it.
- Preserve the `admob-cmp-core` / `admob-cmp-compose` / `admob-cmp` artifact split and `dev.avinya.ads` package names.
- Consent order is UMP consent, then iOS ATT, then GMA initialization and the first ad request.
- All GMA, UMP, UIKit, and Android view/SDK calls remain main-thread confined.
- Never force-close or release callback-owned full-screen state after `tryHandOffToCallbacks()` succeeds.
- `AdEvent.RewardEarned` is telemetry, not a durable entitlement ledger.
- Recommend server-side verification for valuable or fraud-sensitive rewards.
- Use official test app/ad-unit IDs only in the debug application.
- Query the Gradle task graph again if build configuration changes alter task names.
- Preserve unrelated working-tree changes; the approved design spec is already untracked and must remain intact.
- Every production behavior change follows red-green-refactor: add a focused failing test, verify the expected failure, implement the minimum fix, then rerun focused and affected tests.

## File and Responsibility Map

**New common core files**

- `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/ConfigurationSnapshots.kt` — deep ownership snapshots for configs, requests, and placements.
- `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/ConsentSessionState.kt` — thread-safe active consent-mode state and admission derivation.
- `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/RewardDelivery.kt` — at-most-once direct reward callback plus telemetry emission.
- `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NoOpControllerRegistry.kt` — collision-checked, locked no-op controller caching.
- `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/InternalAdMobCmpApi.kt` — opt-in marker for inter-artifact implementation bridges.

**New Compose file**

- `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/RememberEventCallback.kt` — stable callback identity that reads the latest lambda.

**New tests**

- `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/ConfigurationSnapshotsTest.kt`
- `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/ConsentSessionStateTest.kt`
- `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/RewardDeliveryTest.kt`
- `admob-cmp-core/src/iosTest/kotlin/dev/avinya/ads/IosFullScreenDelegateStoreTest.kt`
- `admob-cmp-compose/src/commonTest/kotlin/dev/avinya/ads/ui/RememberEventCallbackTest.kt`

**Existing files with focused changes**

- `AdPlacement.kt`, `AdTimeoutPolicy.kt`, `FullScreenAdModels.kt`, `AdShowResult.kt` — public policy and result models.
- `AdManager.kt` — rewarded controller API and no-op manager delegation.
- `FullScreenSlotCore.kt`, `Fakes.kt`, `AppOpenAdCoordinator.kt` — shared presentation/reward lifecycle.
- `AndroidGoogleAdManager.kt`, `IosGoogleAdManager.kt`, `IosConsentController.kt` — consent recovery and initialization state.
- `BannerCore.kt`, `AndroidBannerAdController.kt`, `IosBannerAdController.kt` — per-call banner policy propagation.
- `AndroidFullScreenSlots.kt`, `IosFullScreenSlots.kt` — native reward and terminal delegate callbacks.
- Android/iOS banner and native Compose files — bridge opt-in and current event callbacks.
- `admob-cmp/docs/*`, `admob-cmp/AGENTS.md`, `admob-cmp/CLAUDE.md`, `handoff.md` — consumer and maintainer contract.
- `gradle.properties` and three ABI dump files — 1.0.2 release identity and intentional public API update.

---

### Task 1: Fail Fast on Invalid Policies and Reward Overflow

**Files:**

- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdPlacement.kt:164-216`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdTimeoutPolicy.kt:21-32`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/FullScreenAdModels.kt:35-41`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/AdPlacementTest.kt`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/AdTimeoutTest.kt`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/AdRewardTest.kt`

**Interfaces:**

- Consumes: existing public policy constructors and `AdReward.wholeAmountOrNull()`.
- Produces: the same public signatures with complete constructor invariants and overflow-safe conversion.

- [ ] **Step 1: Add failing policy validation tests**

Add focused cases to `AdPlacementTest.kt`:

```kotlin
@Test
fun `cache policy rejects zero without waiting for placement construction`() {
    assertFailsWith<IllegalArgumentException> { AdCachePolicy(maxSize = 0) }
}

@Test
fun `expiration policy rejects non finite and non positive TTLs`() {
    assertFailsWith<IllegalArgumentException> {
        AdExpirationPolicy(fullScreenTtl = Duration.ZERO)
    }
    assertFailsWith<IllegalArgumentException> {
        AdExpirationPolicy(appOpenTtl = Duration.INFINITE)
    }
    assertFailsWith<IllegalArgumentException> {
        AdExpirationPolicy(nativeTtl = (-1).seconds)
    }
}

@Test
fun `retry policy validates delays ordering and multiplier`() {
    assertFailsWith<IllegalArgumentException> {
        AdRetryPolicy(initialDelay = Duration.ZERO)
    }
    assertFailsWith<IllegalArgumentException> {
        AdRetryPolicy(initialDelay = 5.seconds, maxDelay = 4.seconds)
    }
    assertFailsWith<IllegalArgumentException> {
        AdRetryPolicy(backoffMultiplier = Double.NaN)
    }
    assertFailsWith<IllegalArgumentException> {
        AdRetryPolicy(backoffMultiplier = 0.99)
    }
}

@Test
fun `banner sizes reject invalid dimensions`() {
    assertFailsWith<IllegalArgumentException> { AdSizePolicy.Fixed(0, 50) }
    assertFailsWith<IllegalArgumentException> { AdSizePolicy.Fixed(320, -1) }
    assertFailsWith<IllegalArgumentException> { AdSizePolicy.InlineAdaptive(0) }
}
```

Add `Duration.INFINITE`, `Duration.ZERO`, and `assertFailsWith` imports.

- [ ] **Step 2: Add failing timeout and reward-boundary tests**

In `AdTimeoutTest.kt`, add:

```kotlin
@Test
fun `timeouts must be finite`() {
    assertFailsWith<IllegalArgumentException> {
        AdTimeoutPolicy(loadTimeout = Duration.INFINITE)
    }
    assertFailsWith<IllegalArgumentException> {
        AdTimeoutPolicy(presentationHandOffTimeout = Duration.INFINITE)
    }
}
```

In `AdRewardTest.kt`, add:

```kotlin
@Test
fun wholeAmountReturnsIntBoundaries() {
    assertEquals(
        Int.MAX_VALUE,
        AdReward(Int.MAX_VALUE.toLong() * 1_000_000L, "coins").wholeAmountOrNull()
    )
    assertEquals(
        Int.MIN_VALUE,
        AdReward(Int.MIN_VALUE.toLong() * 1_000_000L, "coins").wholeAmountOrNull()
    )
}

@Test
fun wholeAmountRejectsIntOverflow() {
    assertNull(
        AdReward((Int.MAX_VALUE.toLong() + 1L) * 1_000_000L, "coins")
            .wholeAmountOrNull()
    )
    assertNull(
        AdReward((Int.MIN_VALUE.toLong() - 1L) * 1_000_000L, "coins")
            .wholeAmountOrNull()
    )
}
```

- [ ] **Step 3: Run the focused tests and verify red**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.AdPlacementTest \
  --tests dev.avinya.ads.AdTimeoutTest \
  --tests dev.avinya.ads.AdRewardTest
```

Expected: failures show invalid constructors being accepted and overflow values wrapping to an `Int`.

- [ ] **Step 4: Implement complete validation**

Use one private helper in `AdPlacement.kt`:

```kotlin
private fun requireFinitePositive(name: String, value: Duration) {
    require(value.isFinite() && value.isPositive()) {
        "$name must be finite and positive, was $value"
    }
}
```

Add `init` blocks:

```kotlin
public data class AdCachePolicy(...) {
    init {
        require(maxSize >= 1) { "AdCachePolicy.maxSize must be at least 1." }
    }
}

public data class AdExpirationPolicy(...) {
    init {
        requireFinitePositive("AdExpirationPolicy.fullScreenTtl", fullScreenTtl)
        requireFinitePositive("AdExpirationPolicy.appOpenTtl", appOpenTtl)
        requireFinitePositive("AdExpirationPolicy.nativeTtl", nativeTtl)
    }
}

public data class AdRetryPolicy(...) {
    init {
        require(maxAttempts >= 1) { "AdRetryPolicy.maxAttempts must be at least 1." }
        requireFinitePositive("AdRetryPolicy.initialDelay", initialDelay)
        requireFinitePositive("AdRetryPolicy.maxDelay", maxDelay)
        require(maxDelay >= initialDelay) {
            "AdRetryPolicy.maxDelay must be greater than or equal to initialDelay."
        }
        require(backoffMultiplier.isFinite() && backoffMultiplier >= 1.0) {
            "AdRetryPolicy.backoffMultiplier must be finite and at least 1.0."
        }
    }
}
```

Give `Fixed` and `InlineAdaptive` bodies with their own `init` checks. Update
`AdTimeoutPolicy` to require `isFinite() && isPositive()`.

Implement reward conversion without narrowing before the range check:

```kotlin
public fun wholeAmountOrNull(): Int? {
    if (amountMicros % 1_000_000L != 0L) return null
    val whole = amountMicros / 1_000_000L
    return whole.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
}
```

- [ ] **Step 5: Run focused and shared tests**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.AdPlacementTest \
  --tests dev.avinya.ads.AdTimeoutTest \
  --tests dev.avinya.ads.AdRewardTest
```

Expected: all selected tests pass.

- [ ] **Step 6: Review checkpoint**

Inspect `git diff --check` and the Task 1 diff. Do not commit.

---

### Task 2: Snapshot Configuration at SDK Ownership Boundaries

**Files:**

- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/ConfigurationSnapshots.kt`
- Create: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/ConfigurationSnapshotsTest.kt`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdConfig.kt:137-155`
- Modify later consumers in Tasks 3 and 5.

**Interfaces:**

- Produces:
  - `internal fun AdConfig.ownedSnapshot(): AdConfig`
  - `internal fun GlobalRequestConfiguration.ownedSnapshot(): GlobalRequestConfiguration`
  - `internal fun AdRequestOptions.ownedSnapshot(): AdRequestOptions`
  - `internal fun AdPlacement.ownedSnapshot(): AdPlacement`
- Later tasks must pass only owned snapshots into consent controllers, initialization attempts, collision registries, and controller constructors.

- [ ] **Step 1: Add failing deep-copy tests**

Create `ConfigurationSnapshotsTest.kt`:

```kotlin
package dev.avinya.ads

import dev.avinya.ads.internal.ownedSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigurationSnapshotsTest {

    @Test
    fun `config snapshot does not observe later list mutation`() {
        val gmaIds = mutableListOf("gma-a")
        val umpIds = mutableListOf("ump-a")
        val hooks = mutableListOf<AdInitializationHook>()
        val original = AdConfig(
            appIds = AdAppIds("android", "ios"),
            globalRequestConfiguration = GlobalRequestConfiguration(testDeviceIds = gmaIds),
            debugOptions = AdDebugOptions(consentTestDeviceIds = umpIds),
            initializationHooks = hooks
        )

        val snapshot = original.ownedSnapshot()
        gmaIds += "gma-b"
        umpIds += "ump-b"
        hooks += object : AdInitializationHook {
            override suspend fun onPhase(phase: AdInitializationPhase, config: AdConfig) = Unit
        }

        assertEquals(listOf("gma-a"), snapshot.testDeviceIds)
        assertEquals(listOf("ump-a"), snapshot.debugOptions.consentTestDeviceIds)
        assertEquals(0, snapshot.initializationHooks.size)
    }

    @Test
    fun `placement snapshot deep copies targeting collections`() {
        val keywords = mutableSetOf("sports")
        val values = mutableListOf("one")
        val targeting = mutableMapOf("segment" to values)
        val extras = mutableMapOf("adapter" to "value")
        val original = AdPlacement(
            id = "snapshot-banner",
            format = AdFormat.Banner,
            adUnitIds = AdUnitIds("android", "ios"),
            requestOptions = AdRequestOptions(
                keywords = keywords,
                customTargeting = targeting,
                googleExtras = extras
            )
        )

        val snapshot = original.ownedSnapshot()
        keywords += "news"
        values += "two"
        targeting["new"] = mutableListOf("value")
        extras["late"] = "mutation"

        assertEquals(setOf("sports"), snapshot.requestOptions.keywords)
        assertEquals(mapOf("segment" to listOf("one")), snapshot.requestOptions.customTargeting)
        assertEquals(mapOf("adapter" to "value"), snapshot.requestOptions.googleExtras)
    }
}
```

- [ ] **Step 2: Run the new test and verify red**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.ConfigurationSnapshotsTest
```

Expected: compilation fails because `ownedSnapshot` does not exist.

- [ ] **Step 3: Implement deep ownership snapshots**

Create `ConfigurationSnapshots.kt`:

```kotlin
package dev.avinya.ads.internal

import dev.avinya.ads.AdConfig
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdRequestOptions
import dev.avinya.ads.GlobalRequestConfiguration

internal fun GlobalRequestConfiguration.ownedSnapshot(): GlobalRequestConfiguration =
    copy(testDeviceIds = testDeviceIds.toList())

internal fun AdConfig.ownedSnapshot(): AdConfig = copy(
    globalRequestConfiguration = globalRequestConfiguration.ownedSnapshot(),
    debugOptions = debugOptions.copy(
        consentTestDeviceIds = debugOptions.consentTestDeviceIds.toList()
    ),
    initializationHooks = initializationHooks.toList()
)

internal fun AdRequestOptions.ownedSnapshot(): AdRequestOptions = copy(
    keywords = keywords.toSet(),
    neighboringContentUrls = neighboringContentUrls.toSet(),
    categoryExclusions = categoryExclusions.toSet(),
    customTargeting = customTargeting
        .mapValues { (_, values) -> values.toList() }
        .toMap(),
    googleExtras = googleExtras.toMap()
)

internal fun AdPlacement.ownedSnapshot(): AdPlacement =
    copy(requestOptions = requestOptions.ownedSnapshot())
```

Change `initializationIdentity` so it always retains a snapped global request
configuration:

```kotlin
globalRequestConfiguration = effectiveGlobalRequestConfiguration().ownedSnapshot()
```

- [ ] **Step 4: Run snapshot and config tests**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.ConfigurationSnapshotsTest \
  --tests dev.avinya.ads.AdConfigTest
```

Expected: all selected tests pass.

- [ ] **Step 5: Review checkpoint**

Verify nested custom-targeting lists are copied, not only their outer map. Run
`git diff --check`. Do not commit.

---

### Task 3: Preserve Consent Mode and Remove Racy Readiness State

**Files:**

- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/ConsentSessionState.kt`
- Create: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/ConsentSessionStateTest.kt`
- Modify: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/AndroidGoogleAdManager.kt:40-471`
- Modify: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/IosGoogleAdManager.kt:48-480`
- Verify: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/IosConsentController.kt:89-106`

**Interfaces:**

- Consumes: `AdConfig.ownedSnapshot()` from Task 2 and `deriveAdmission`.
- Produces:
  - `ConsentSessionState.recordCompletedGate(mode)`
  - `ConsentSessionState.modeForPrivacyOptionsResume()`
  - `ConsentSessionState.admission(canRequestAds)`
- Managers must not call `initialize(..., SkipConsent)` from privacy-options recovery.

- [ ] **Step 1: Add failing consent-session tests**

Create `ConsentSessionStateTest.kt`:

```kotlin
package dev.avinya.ads

import dev.avinya.ads.internal.AdRequestAdmission
import dev.avinya.ads.internal.ConsentSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsentSessionStateTest {

    @Test
    fun `privacy recovery cannot invent a mode before initialize`() {
        assertNull(ConsentSessionState().modeForPrivacyOptionsResume())
    }

    @Test
    fun `privacy recovery preserves gather mode`() {
        val state = ConsentSessionState()
        state.recordCompletedGate(ConsentMode.GatherBeforeInitialize)

        assertEquals(
            ConsentMode.GatherBeforeInitialize,
            state.modeForPrivacyOptionsResume()
        )
        assertEquals(AdRequestAdmission.Allowed, state.admission(canRequestAds = true))
        assertEquals(AdRequestAdmission.Revoked, state.admission(canRequestAds = false))
    }

    @Test
    fun `privacy recovery preserves already allowed mode`() {
        val state = ConsentSessionState()
        state.recordCompletedGate(ConsentMode.InitializeOnlyIfAlreadyAllowed)

        assertEquals(
            ConsentMode.InitializeOnlyIfAlreadyAllowed,
            state.modeForPrivacyOptionsResume()
        )
    }

    @Test
    fun `explicit skip mode remains distinguishable`() {
        val state = ConsentSessionState()
        state.recordCompletedGate(ConsentMode.SkipConsent)

        assertEquals(AdRequestAdmission.Skipped, state.admission(canRequestAds = false))
    }
}
```

- [ ] **Step 2: Run the consent-session test and verify red**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.ConsentSessionStateTest
```

Expected: compilation fails because `ConsentSessionState` does not exist.

- [ ] **Step 3: Implement synchronized consent-session state**

Create `ConsentSessionState.kt`:

```kotlin
package dev.avinya.ads.internal

import dev.avinya.ads.ConsentMode
import kotlinx.coroutines.flow.MutableStateFlow

internal class ConsentSessionState {
    private val activeMode = MutableStateFlow<ConsentMode?>(null)

    internal fun recordCompletedGate(mode: ConsentMode) {
        activeMode.value = mode
    }

    internal fun modeForPrivacyOptionsResume(): ConsentMode? = activeMode.value

    internal fun admission(canRequestAds: Boolean): AdRequestAdmission {
        val mode = activeMode.value ?: return AdRequestAdmission.NotGathered
        return deriveAdmission(mode, canRequestAds, consentGathered = true)
    }
}
```

- [ ] **Step 4: Integrate Android without `SkipConsent` recovery**

In `AndroidGoogleAdManager`:

1. Create `private val consentSession = ConsentSessionState()` before the
   `ConsentController`.
2. Replace the privacy callback with:

```kotlin
override val consent: ConsentController =
    AndroidConsentController(activityProvider, appContext, resume@{ config ->
        val mode = consentSession.modeForPrivacyOptionsResume() ?: return@resume
        initialize(config, mode)
    })
```

3. Snapshot `config` at the start of `initialize` and use the snapshot for
   identity, attempts, consent, hooks, and native initialization:

```kotlin
val ownedConfig = config.ownedSnapshot()
val requestedIdentity = ownedConfig.initializationIdentity(ownedConfig.androidAppId)
```

4. After the selected consent step, call:

```kotlin
consentSession.recordCompletedGate(consentMode)
_admission.value = consentSession.admission(canRequest)
```

5. In the `canRequestAds` collector use
   `consentSession.admission(canRequest)`.
6. Remove `activeConsentMode`, `consentGathered`, and `initialized`.
7. Gate requests with `_status.value != AdManagerStatus.Ready`.

- [ ] **Step 5: Mirror the same state machine on iOS**

Apply the same seven changes to `IosGoogleAdManager`. Keep
`appliedConfigIdentity` and `appliedTerminalStatus` behind
`mobileAdsInitializationMutex`; do not add `@Volatile` to them.

Keep `IosConsentController.showPrivacyOptions()` responsible only for refreshing
UMP state and invoking `onCanRequestAds(lastConfig)` when allowed. The manager
selects the preserved mode.

- [ ] **Step 6: Snapshot placements in every production factory**

In all six factory methods on both managers, snapshot before validation or
registration:

```kotlin
val ownedPlacement = placement.ownedSnapshot()
require(ownedPlacement.format == AdFormat.Banner) {
    "AdPlacement '${ownedPlacement.id}' has format ${ownedPlacement.format} but was passed to a Banner factory."
}
checkPlacementCollision(ownedPlacement)
banners.getOrPut(ownedPlacement.id) {
    AndroidBannerAdController(
        ownedPlacement,
        _events,
        ::adRequestBlockedError,
        activityProvider
    )
}
```

Use the platform's existing constructor arguments and registry lock; the sample
shows only the required ownership order. Apply the same order to native,
interstitial, rewarded, rewarded-interstitial, and app-open factories. Collision
maps and controllers must retain `ownedPlacement`, never the caller's mutable
instance.

- [ ] **Step 7: Run common tests and platform compilations**

Run:

```bash
./gradlew \
  :admob-cmp-core:testAndroidHostTest \
  :admob-cmp-core:compileAndroidMain \
  :admob-cmp-core:compileKotlinIosSimulatorArm64
```

Expected: consent tests pass and both managers compile.

- [ ] **Step 8: Review checkpoint**

Search for the forbidden recovery path:

```bash
rg -n 'initialize\\(config, ConsentMode\\.SkipConsent\\)' admob-cmp-core/src
```

Expected: no privacy-options callback uses it. Explicit consumer-selected
`SkipConsent` handling remains in the `when` branches. Run `git diff --check`.
Do not commit.

---

### Task 4: Carry Per-call Banner Size Policy to Native Requests

**Files:**

- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/BannerCore.kt:35-67,195-228`
- Modify: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/AndroidBannerAdController.kt:83-99`
- Modify: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/IosBannerAdController.kt:98-147`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/BannerCoreTest.kt`

**Interfaces:**

- Changes internal `BannerPlatform.loadBanner` to:

```kotlin
suspend fun loadBanner(
    size: S,
    sizePolicy: AdSizePolicy,
    requestOptions: AdRequestOptions,
    requiredGeneration: Long
): AdAttemptResult<V>
```

- Produces exact replay of size, size policy, and request options on initial load and refresh.

- [ ] **Step 1: Make the fake platform capture the policy**

Update the fake `BannerPlatform` in `BannerCoreTest.kt` with:

```kotlin
val loadedPolicies = mutableListOf<AdSizePolicy>()

override suspend fun loadBanner(
    size: FakeSize,
    sizePolicy: AdSizePolicy,
    requestOptions: AdRequestOptions,
    requiredGeneration: Long
): AdAttemptResult<FakeBanner> {
    loadedPolicies += sizePolicy
    loadedOptions += requestOptions
    return nextResult()
}
```

Adapt `FakeSize` to the existing fake size type in that file.

- [ ] **Step 2: Add failing load and refresh tests**

Add:

```kotlin
@Test
fun `per call collapsible policy reaches platform load`() = runTest {
    val policy = AdSizePolicy.LargeAnchoredAdaptive(CollapsiblePlacement.Top)

    core.load(BannerGeometry(320), policy, AdRequestOptions())

    assertEquals(listOf(policy), platform.loadedPolicies)
}

@Test
fun `refresh replays per call collapsible policy`() = runTest {
    val policy = AdSizePolicy.LargeAnchoredAdaptive(CollapsiblePlacement.Bottom)
    core.load(BannerGeometry(320), policy, AdRequestOptions())

    core.refresh()

    assertEquals(listOf(policy, policy), platform.loadedPolicies)
}
```

- [ ] **Step 3: Run the banner test and verify red**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.BannerCoreTest
```

Expected: interface/signature failure or an assertion showing the resolved policy
is not supplied to `loadBanner`.

- [ ] **Step 4: Thread the policy through the internal boundary**

In `BannerCore.loadForGeneration`, replace:

```kotlin
platform.loadBanner(resolved.size, resolved.requestOptions, requiredGeneration)
```

with:

```kotlin
platform.loadBanner(
    resolved.size,
    resolved.sizePolicy,
    resolved.requestOptions,
    requiredGeneration
)
```

Update Android and iOS overrides to accept `sizePolicy`. Use:

```kotlin
requestOptions.withCollapsible(sizePolicy)
```

Do not read `placement.bannerSizePolicy` inside either native `loadBanner`.

- [ ] **Step 5: Run focused tests and platform compilation**

Run:

```bash
./gradlew \
  :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.BannerCoreTest
```

Then run:

```bash
./gradlew \
  :admob-cmp-core:compileAndroidMain \
  :admob-cmp-core:compileKotlinIosSimulatorArm64
```

Expected: tests and both platform compilations pass.

- [ ] **Step 6: Review checkpoint**

Run:

```bash
rg -n 'withCollapsible\\(placement\\.bannerSizePolicy\\)' \
  admob-cmp-core/src/androidMain \
  admob-cmp-core/src/iosMain
```

Expected: no matches. Run `git diff --check`. Do not commit.

---

### Task 5: Make `NoOpAdManager` Match Production Factory Semantics

**Files:**

- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NoOpControllerRegistry.kt`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdManager.kt:296-408`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/NoOpAdManagerTest.kt`

**Interfaces:**

- Consumes: `AdPlacement.ownedSnapshot()` and `FullScreenStateLock`.
- Produces internal factory methods returning stable no-op controller identities with production-equivalent validation.

- [ ] **Step 1: Add failing no-op registry tests**

Add unique placement helpers and tests:

```kotlin
@Test
fun `equivalent factory requests return the same controller`() {
    val placement = AdPlacement(
        id = "noop-cache-banner",
        format = AdFormat.Banner,
        androidAdUnitId = "android",
        iosAdUnitId = "ios"
    )

    assertSame(NoOpAdManager.banner(placement), NoOpAdManager.banner(placement.copy()))
}

@Test
fun `placement id collision is rejected`() {
    val first = AdPlacement(
        id = "noop-collision",
        format = AdFormat.Interstitial,
        androidAdUnitId = "android-a",
        iosAdUnitId = "ios-a"
    )
    val conflicting = first.copy(adUnitIds = AdUnitIds("android-b", "ios-b"))

    NoOpAdManager.interstitial(first)
    assertFailsWith<IllegalStateException> {
        NoOpAdManager.interstitial(conflicting)
    }
}

@Test
fun `factory format mismatch is rejected`() {
    val placement = AdPlacement(
        id = "noop-format",
        format = AdFormat.Native,
        androidAdUnitId = "android",
        iosAdUnitId = "ios"
    )

    assertFailsWith<IllegalArgumentException> {
        NoOpAdManager.banner(placement)
    }
}
```

Import `assertSame` and `assertFailsWith`.

- [ ] **Step 2: Run the no-op tests and verify red**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.NoOpAdManagerTest
```

Expected: identity assertion fails and collision/format calls do not throw.

- [ ] **Step 3: Implement a locked no-op registry**

Create `NoOpControllerRegistry.kt` with:

```kotlin
package dev.avinya.ads.internal

import dev.avinya.ads.*

internal class NoOpControllerRegistry {
    private val lock = FullScreenStateLock()
    private val placements = mutableMapOf<String, AdPlacement>()
    private val banners = mutableMapOf<String, BannerAdController>()
    private val nativePools = mutableMapOf<String, NativeAdPool>()
    private val fullScreen = mutableMapOf<Pair<String, AdFormat>, FullScreenAdController>()

    private fun owned(placement: AdPlacement, expected: AdFormat): AdPlacement {
        require(placement.format == expected) {
            "AdPlacement '${placement.id}' has format ${placement.format} but was passed to a $expected factory."
        }
        val snapshot = placement.ownedSnapshot()
        val existing = placements.getOrPut(snapshot.id) { snapshot }
        check(existing == snapshot) {
            "AdPlacement id '${snapshot.id}' is already registered with different configuration."
        }
        return existing
    }

    internal fun banner(placement: AdPlacement): BannerAdController = lock.withLock {
        val value = owned(placement, AdFormat.Banner)
        banners.getOrPut(value.id) { NoOpBannerAdController(value) }
    }

    internal fun nativeAd(placement: AdPlacement): NativeAdPool = lock.withLock {
        val value = owned(placement, AdFormat.Native)
        nativePools.getOrPut(value.id) { NoOpNativeAdPool(value) }
    }

    // Implement interstitial, rewarded, rewardedInterstitial, and appOpen with
    // the same owned(...) check and (id, format) fullScreen key.
}
```

Write all four full-screen methods explicitly; do not use unchecked generic
casts. If `FullScreenStateLock.withLock` is not accessible from this package,
keep the new registry in `dev.avinya.ads.internal`, where the lock already lives.

- [ ] **Step 4: Delegate `NoOpAdManager` factories**

Add:

```kotlin
private val controllers = NoOpControllerRegistry()
```

Replace all six direct constructors with calls to the registry. Keep existing
load/show failure behavior in the no-op controller classes.

- [ ] **Step 5: Run no-op and full common tests**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.NoOpAdManagerTest
```

Then run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest
```

Expected: selected and full Android host tests pass.

- [ ] **Step 6: Review checkpoint**

Confirm every retained no-op placement is an owned snapshot. Run
`git diff --check`. Do not commit.

---

### Task 6: Introduce the Mediation-safe Common Reward Contract

**Files:**

- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/RewardDelivery.kt`
- Create: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/RewardDeliveryTest.kt`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdShowResult.kt:4-17`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdManager.kt:198-235`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/FullScreenSlotCore.kt:99-378,586-610`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/appopen/AppOpenAdCoordinator.kt:220-235`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/Fakes.kt:20-105`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/FullScreenSlotCoreTest.kt`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/AdEventModelsTest.kt`

**Interfaces:**

- Produces the approved rewarded overloads:

```kotlin
suspend fun show(
    options: FullScreenAdOptions = placement.fullScreenOptions,
    onRewardEarned: (AdReward) -> Unit
): AdShowResult
```

- Produces internal `RewardDelivery.deliver(reward): Boolean`.
- `AdShowResult` contains only `Shown`, `NotReady`, and `Failed`.
- `FullScreenSlotCore.presentAd` receives `RewardDelivery?`.

- [ ] **Step 1: Add failing reward-delivery ordering tests**

Create `RewardDeliveryTest.kt`:

```kotlin
package dev.avinya.ads

import dev.avinya.ads.internal.RewardDelivery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardDeliveryTest {

    @Test
    fun `reward is delivered once to callback and telemetry`() {
        val callbackRewards = mutableListOf<AdReward>()
        val telemetryRewards = mutableListOf<AdReward>()
        val delivery = RewardDelivery(
            onRewardEarned = callbackRewards::add,
            emitReward = telemetryRewards::add
        )
        val reward = AdReward(1_000_000L, "coin")

        assertTrue(delivery.deliver(reward))
        assertFalse(delivery.deliver(reward))
        assertEquals(listOf(reward), callbackRewards)
        assertEquals(listOf(reward), telemetryRewards)
    }

    @Test
    fun `callback failure does not suppress telemetry`() {
        val telemetryRewards = mutableListOf<AdReward>()
        val delivery = RewardDelivery(
            onRewardEarned = { error("consumer failure") },
            emitReward = telemetryRewards::add
        )
        val reward = AdReward(2_000_000L, "coin")

        assertTrue(delivery.deliver(reward))
        assertEquals(listOf(reward), telemetryRewards)
    }

    @Test
    fun `telemetry only mode still reports reward once`() {
        val telemetryRewards = mutableListOf<AdReward>()
        val delivery = RewardDelivery(
            onRewardEarned = null,
            emitReward = telemetryRewards::add
        )

        delivery.deliver(AdReward(3_000_000L, "coin"))

        assertEquals(1, telemetryRewards.size)
    }
}
```

- [ ] **Step 2: Add failing API/result tests**

Update `AdEventModelsTest` so exhaustive result coverage no longer constructs
`AdShowResult.Rewarded`. Add a compile-time usage test:

```kotlin
private suspend fun showRewardedWithCallback(
    controller: RewardedAdController,
    rewards: MutableList<AdReward>
): AdShowResult = controller.show(onRewardEarned = rewards::add)
```

Add two `FullScreenSlotCoreTest` fake presentation cases:

1. reward-before-dismiss: deliver reward, complete dismissal with
   `AdShowResult.Shown`, and assert one callback plus one telemetry event;
2. dismiss-before-reward: return `AdShowResult.Shown`, deliver reward afterward,
   and assert the callback and telemetry still arrive;
3. in both successful presentation orders, assert the consumed rewarded fake is
   absent from `destroyedAds`;
4. add a failed-to-show case and assert that fake is present in `destroyedAds`.

Expose the fake's captured delivery as:

```kotlin
var rewardDelivery: RewardDelivery? = null
```

- [ ] **Step 3: Run selected tests and verify red**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.RewardDeliveryTest \
  --tests dev.avinya.ads.AdEventModelsTest \
  --tests dev.avinya.ads.FullScreenSlotCoreTest
```

Expected: compilation fails because `RewardDelivery` and rewarded overloads do
not exist; existing result coverage still refers to `Rewarded`.

- [ ] **Step 4: Implement at-most-once reward delivery**

Create `RewardDelivery.kt`:

```kotlin
package dev.avinya.ads.internal

import dev.avinya.ads.AdLogger
import dev.avinya.ads.AdReward
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class RewardDelivery(
    private val onRewardEarned: ((AdReward) -> Unit)?,
    private val emitReward: (AdReward) -> Unit
) {
    private val delivered = AtomicBoolean(false)

    internal fun deliver(reward: AdReward): Boolean {
        if (!delivered.compareAndSet(expectedValue = false, newValue = true)) return false
        try {
            onRewardEarned?.invoke(reward)
        } catch (failure: Throwable) {
            AdLogger.e("Reward callback failed.", failure)
        }
        try {
            emitReward(reward)
        } catch (failure: Throwable) {
            AdLogger.e("Reward telemetry emission failed.", failure)
        }
        return true
    }
}
```

- [ ] **Step 5: Change the public result and controller APIs**

Remove `AdShowResult.Rewarded` and rewrite KDoc so `Shown` means presentation
and dismissal only.

Give both rewarded interfaces the approved overload. Their concrete platform
slots will implement it in Task 7.

- [ ] **Step 6: Add reward-aware show internals**

Refactor `FullScreenSlotCore.show` into:

```kotlin
final override suspend fun show(options: FullScreenAdOptions): AdShowResult =
    showInternal(options, onRewardEarned = null)

protected suspend fun showRewarded(
    options: FullScreenAdOptions,
    onRewardEarned: (AdReward) -> Unit
): AdShowResult = showInternal(options, onRewardEarned)
```

`showInternal` creates a `RewardDelivery` only for `Rewarded` and
`RewardedInterstitial` placements:

```kotlin
val rewardDelivery = when (placement.format) {
    AdFormat.Rewarded,
    AdFormat.RewardedInterstitial -> RewardDelivery(onRewardEarned) { reward ->
        emit(AdEvent.RewardEarned(placement.id, reward))
    }
    else -> null
}
```

Pass it to:

```kotlin
protected abstract suspend fun presentAd(
    loaded: AdT,
    options: FullScreenAdOptions,
    presentation: FullScreenPresentationHandle,
    rewardDelivery: RewardDelivery?
): AdShowResult
```

Remove every `result is AdShowResult.Rewarded` branch from core, fakes, and
`AppOpenAdCoordinator`.

- [ ] **Step 7: Preserve late-reward native lifetime**

Add a protected hook:

```kotlin
protected open fun destroyAfterPresentation(wasShown: Boolean): Boolean = true
```

In the presentation close lambda, replace unconditional `safelyDestroyAd(ad)`
with:

```kotlin
if (destroyAfterPresentation(wasShown)) safelyDestroyAd(ad)
```

Rewarded platform slots will override this in Task 7 to return `!wasShown`.
Failed, cleared, expired, and pre-handoff-cancelled ads still use normal
destruction. Successfully presented rewarded ads release Kotlin ownership
without calling a native destroy operation that could suppress a late reward
listener.

- [ ] **Step 8: Update fakes and run common tests**

Update all fake `presentAd` overrides to accept `RewardDelivery?`. Add the
dismiss-before-reward case described in Step 2.

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests dev.avinya.ads.RewardDeliveryTest \
  --tests dev.avinya.ads.AdEventModelsTest \
  --tests dev.avinya.ads.FullScreenSlotCoreTest \
  --tests dev.avinya.ads.AppOpenAdCoordinatorTest
```

Expected: all selected common tests pass. Platform compilation may remain red
until Task 7 updates every native override; do not run the full cross-platform
gate between Tasks 6 and 7.

- [ ] **Step 9: Review checkpoint**

Run:

```bash
rg -n 'AdShowResult\\.Rewarded|is AdShowResult\\.Rewarded' \
  admob-cmp-core/src
```

Expected: no production or test references. Run `git diff --check`. Do not
commit.

---

### Task 7: Wire Reward and Terminal Ownership on Android and iOS

**Files:**

- Modify: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/AndroidFullScreenSlots.kt`
- Modify: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/IosFullScreenSlots.kt`
- Create: `admob-cmp-core/src/iosTest/kotlin/dev/avinya/ads/IosFullScreenDelegateStoreTest.kt`

**Interfaces:**

- Consumes: `RewardDelivery?` and `showRewarded(...)` from Task 6.
- Produces: native callbacks that always return `Shown` at dismissal, deliver
  reward independently, preserve late listeners, and release iOS delegates on
  real terminal callbacks.

- [ ] **Step 1: Add an iOS terminal-release regression harness**

Make `FullScreenDelegateStore` `internal` and add:

```kotlin
internal fun contains(ad: AdT): Boolean = locked {
    entries.any { it.ad === ad }
}

internal fun terminal(ad: AdT, block: () -> Unit) {
    try {
        block()
    } finally {
        release(ad)
    }
}
```

Before adding the methods, create `IosFullScreenDelegateStoreTest.kt`:

```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.avinya.ads

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosFullScreenDelegateStoreTest {

    @Test
    fun `terminal callback releases retained delegate even when callback throws`() {
        val store = FullScreenDelegateStore<Any>()
        val ad = Any()
        val delegate = FullScreenDelegate({}, {}, { _ -> }, {}, {})
        store.retain(ad, delegate)
        assertTrue(store.contains(ad))

        runCatching {
            store.terminal(ad) { error("terminal consumer failed") }
        }

        assertFalse(store.contains(ad))
    }
}
```

- [ ] **Step 2: Run the iOS test and verify red**

Run:

```bash
./gradlew :admob-cmp-core:iosSimulatorArm64Test \
  --tests dev.avinya.ads.IosFullScreenDelegateStoreTest
```

Expected: compilation fails because the store and terminal helper are private or
missing.

- [ ] **Step 3: Update every Android full-screen override**

Add `rewardDelivery: RewardDelivery?` to all four `presentAd` methods.
Interstitial and app-open ignore it after asserting it is null if useful.

In rewarded and rewarded-interstitial slots:

```kotlin
override suspend fun show(
    options: FullScreenAdOptions,
    onRewardEarned: (AdReward) -> Unit
): AdShowResult = showRewarded(options, onRewardEarned)

override fun destroyAfterPresentation(wasShown: Boolean): Boolean = !wasShown
```

Update the native `showRewarded` helper:

- remove the local `reward` variable;
- resume `AdShowResult.Shown` on dismissal;
- convert `RewardItem` to `AdReward`;
- call `rewardDelivery?.deliver(reward)`;
- keep `tryHandOffToCallbacks()` immediately before native `show`.

- [ ] **Step 4: Update every iOS full-screen override**

Add `rewardDelivery: RewardDelivery?` to all four `presentAd` methods.

In rewarded and rewarded-interstitial slots:

```kotlin
override suspend fun show(
    options: FullScreenAdOptions,
    onRewardEarned: (AdReward) -> Unit
): AdShowResult = showRewarded(options, onRewardEarned)

override fun destroyAfterPresentation(wasShown: Boolean): Boolean = !wasShown
```

In each native reward handler:

```kotlin
val reward = AdReward(adReward.amount.toValueMicros(), adReward.type)
rewardDelivery?.deliver(reward)
```

Resume `AdShowResult.Shown` on dismissal regardless of whether reward already
arrived.

- [ ] **Step 5: Release iOS delegates only through terminal callbacks**

Wrap all four `onClosed` and `onFailedToShow` bodies:

```kotlin
onClosed = {
    delegates.terminal(loaded) {
        if (presentation.close(wasShown = true)) {
            emit(AdEvent.ClosedFullScreen(placement.id))
            if (continuation.isActive) continuation.resume(AdShowResult.Shown)
        }
    }
},
onFailedToShow = { error ->
    delegates.terminal(loaded) {
        if (presentation.close(wasShown = false)) {
            emit(AdEvent.ShowFailed(placement.id, error))
            if (continuation.isActive) continuation.resume(AdShowResult.Failed(error))
        }
    }
}
```

Do not add delegate release to `FullScreenSlotCore` cancellation or timeout
catch blocks. Keep handoff before `delegates.retain`.

- [ ] **Step 6: Run iOS tests and both platform compilations**

Run:

```bash
./gradlew \
  :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-core:compileAndroidMain \
  :admob-cmp-core:compileKotlinIosSimulatorArm64
```

Expected: iOS tests and Android/iOS compilation pass.

- [ ] **Step 7: Run all core tests**

Run:

```bash
./gradlew :admob-cmp-core:allTests
```

Expected: all core target tests pass.

- [ ] **Step 8: Review checkpoint**

Verify all four iOS format branches use `delegates.terminal`, and no
post-handoff cancellation path releases delegates. Run `git diff --check`. Do
not commit.

---

### Task 8: Mark Inter-artifact Bridges and Fix Compose Callback Freshness

**Files:**

- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/InternalAdMobCmpApi.kt`
- Modify: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/AndroidBannerAdController.kt:176-196`
- Modify: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/IosBannerAdController.kt:208-225`
- Modify: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdPool.kt:214-215`
- Modify: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdPool.kt:444-445`
- Create: `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/RememberEventCallback.kt`
- Create: `admob-cmp-compose/src/commonTest/kotlin/dev/avinya/ads/ui/RememberEventCallbackTest.kt`
- Modify: four platform files under `admob-cmp-compose/src/{androidMain,iosMain}/kotlin/dev/avinya/ads/ui/*AdView.kt`

**Interfaces:**

- Produces public opt-in marker `@InternalAdMobCmpApi`.
- Produces internal Compose helper
  `rememberCurrentEventCallback(onEvent): (AdEvent) -> Unit`.

- [ ] **Step 1: Add a failing Compose runtime callback test**

Create `RememberEventCallbackTest.kt` using a minimal runtime composition:

```kotlin
package dev.avinya.ads.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import dev.avinya.ads.AdEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RememberEventCallbackTest {

    @Test
    fun `stable callback invokes latest recomposed lambda`() = runTest {
        val first = mutableListOf<AdEvent>()
        val second = mutableListOf<AdEvent>()
        val callback = mutableStateOf<(AdEvent) -> Unit>(first::add)
        lateinit var dispatch: (AdEvent) -> Unit
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        val runner = launch { recomposer.runRecomposeAndApplyChanges() }
        val event = AdEvent.OpenedFullScreen("placement")

        composition.setContent {
            dispatch = rememberCurrentEventCallback(callback.value)
        }
        recomposer.awaitIdle()
        dispatch(event)

        callback.value = second::add
        recomposer.awaitIdle()
        dispatch(event)

        assertEquals(listOf(event), first)
        assertEquals(listOf(event), second)
        composition.dispose()
        recomposer.close()
        runner.cancelAndJoin()
    }

    private class UnitApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
```

- [ ] **Step 2: Run the Compose test and verify red**

Run:

```bash
./gradlew :admob-cmp-compose:testAndroidHostTest \
  --tests dev.avinya.ads.ui.RememberEventCallbackTest
```

Expected: compilation fails because `rememberCurrentEventCallback` does not
exist. If `Recomposer` requires an experimental opt-in in this Compose version,
add the exact compiler-requested opt-in to the test rather than weakening
production visibility.

- [ ] **Step 3: Implement the stable current callback**

Create `RememberEventCallback.kt`:

```kotlin
package dev.avinya.ads.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.avinya.ads.AdEvent

@Composable
internal fun rememberCurrentEventCallback(
    onEvent: (AdEvent) -> Unit
): (AdEvent) -> Unit {
    val current = rememberUpdatedState(onEvent)
    return remember { { event -> current.value(event) } }
}
```

In all four platform composables:

```kotlin
val currentOnEvent = rememberCurrentEventCallback(onEvent)

LaunchedEffect(controllerOrPool) {
    controllerOrPool.events
        // Keep existing native-ad instance filtering here.
        .collect(currentOnEvent)
}
```

- [ ] **Step 4: Add the internal bridge opt-in annotation**

Create `InternalAdMobCmpApi.kt`:

```kotlin
package dev.avinya.ads

@RequiresOptIn(
    message = "This API connects AdMob CMP implementation artifacts and is not a stable consumer API.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
public annotation class InternalAdMobCmpApi
```

Annotate:

- Android current/attach/detach/register banner extensions;
- iOS current/attach/detach/register banner extensions;
- `peekAndroidNativeAd`;
- `peekIosNativeAd`.

Add `@file:OptIn(InternalAdMobCmpApi::class)` to the four Compose platform files
that consume them. Do not mark public sizing helpers such as
`AdSizePolicy.toAndroidAdSize` unless they are implementation-only and have no
documented consumer use.

- [ ] **Step 5: Run Compose tests and cross-platform compilation**

Run:

```bash
./gradlew \
  :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp-compose:compileAndroidMain \
  :admob-cmp-compose:compileKotlinIosSimulatorArm64
```

Expected: test and both platform compilations pass.

- [ ] **Step 6: Run full Compose tests**

Run:

```bash
./gradlew :admob-cmp-compose:allTests
```

Expected: all Compose target tests pass.

- [ ] **Step 7: Review checkpoint**

Search every bridge use and ensure it is annotated or opted in. Run
`git diff --check`. Do not commit.

---

### Task 9: Update Reward, ATT, Banner, Version, and ABI Contracts

**Files:**

- Modify: `admob-cmp/docs/INTERSTITIAL.md`
- Modify: `admob-cmp/docs/SETUP.md`
- Modify: `admob-cmp/docs/BANNER.md`
- Modify: `admob-cmp/AGENTS.md`
- Modify: `admob-cmp/CLAUDE.md`
- Modify: `handoff.md`
- Modify: `gradle.properties:24`
- Update: `admob-cmp-core/api/admob-cmp-core.klib.api`
- Update: `admob-cmp-compose/api/admob-cmp-compose.klib.api`
- Update: `admob-cmp/api/admob-cmp.klib.api`

**Interfaces:**

- Consumes the final public API from Tasks 6 and 8.
- Produces internally consistent 1.0.2 documentation and ABI dumps.

- [ ] **Step 1: Replace reward-result examples**

Replace examples that grant from `AdShowResult.Rewarded` with:

```kotlin
val result = adManager.rewarded(rewardedPlacement).show(
    onRewardEarned = { reward ->
        grantClientRewardOnce(reward.amountMicros, reward.type)
    }
)

when (result) {
    AdShowResult.Shown -> Unit
    AdShowResult.NotReady -> showRetryUi()
    is AdShowResult.Failed -> showAdError(result.error)
}
```

Immediately state:

- the callback may run after `show()` returns;
- do not also grant from `AdEvent.RewardEarned`;
- events are telemetry/observation;
- use server-side verification as the authoritative path for valuable rewards.

- [ ] **Step 2: Unify ATT guidance**

In `admob-cmp/docs/SETUP.md`, remove “request ATT before gathering consent.”
Use this exact order:

```kotlin
adManager.consent.gatherConsent(config)
adManager.tracking.requestAuthorization()
adManager.initialize(config, ConsentMode.InitializeOnlyIfAlreadyAllowed)
```

For the recommended single-call flow, show ATT as an initialization hook that
runs after the manager's UMP gate and before native GMA initialization. Ensure
the prose does not cause UMP to run twice. Do not recommend `SkipConsent` after
manual UMP gathering: that mode intentionally ignores future UMP revocation and
would reopen the production bug this release is fixing.

- [ ] **Step 3: Correct banner fallback and refresh documentation**

Update `BANNER.md`:

- `SdkManaged` waits for an in-flight load to settle; it does not drop the cycle.
- Manual mode registers measured geometry when composed.
- Headless `load` takes `geometry`, `sizePolicy`, and `requestOptions`.
- A null geometry uses best-effort activity/key-window width and fails when no
  width is resolvable.
- Remove unconditional “falls back to screen width” claims.

- [ ] **Step 4: Bump the release version**

Change:

```properties
VERSION_NAME=1.0.2
```

Update the setup dependency example from `1.0.0` to `1.0.2`.

- [ ] **Step 5: Prove the ABI check detects the intentional break**

Run:

```bash
./gradlew \
  :admob-cmp-core:checkKotlinAbi \
  :admob-cmp-compose:checkKotlinAbi \
  :admob-cmp:checkKotlinAbi
```

Expected: at least the core and umbrella checks fail because
`AdShowResult.Rewarded`, rewarded controller methods, and the new opt-in marker
changed the public ABI. A passing core check here means the public dump is not
covering the changed surface and must be investigated before proceeding.

- [ ] **Step 6: Update ABI dumps**

Run:

```bash
./gradlew \
  :admob-cmp-core:updateKotlinAbi \
  :admob-cmp-compose:updateKotlinAbi \
  :admob-cmp:updateKotlinAbi
```

Inspect the dumps and confirm:

- `AdShowResult.Rewarded` is absent;
- both rewarded callback overloads are present;
- `InternalAdMobCmpApi` and annotated bridge metadata are present where ABI
  tooling represents annotations;
- no unrelated API disappeared.

- [ ] **Step 7: Re-run ABI checks**

Run:

```bash
./gradlew \
  :admob-cmp-core:checkKotlinAbi \
  :admob-cmp-compose:checkKotlinAbi \
  :admob-cmp:checkKotlinAbi
```

Expected: all three ABI checks pass.

- [ ] **Step 8: Review checkpoint**

Run:

```bash
rg -n 'AdShowResult\\.Rewarded|request ATT before gathering consent|version = "1\\.0\\.0"' \
  admob-cmp README.md handoff.md
```

Expected: no stale consumer guidance. Historical design/plan documents may
retain old text and must not be rewritten as if they were current docs. Run
`git diff --check`. Do not commit.

---

### Task 10: Cross-task Integration Review and Release Verification

**Files:**

- Review every file changed by Tasks 1-9.
- Do not add new production behavior during this task without a new failing
  regression test.

**Interfaces:**

- Consumes the complete 1.0.2 implementation.
- Produces evidence that source, tests, platform compilation, ABI, and local
  publication agree.

- [ ] **Step 1: Audit the complete diff against the design**

Check each acceptance criterion from
`docs/superpowers/specs/2026-07-26-admob-cmp-1-0-2-production-hardening-design.md`.
Specifically trace:

- privacy options → preserved consent mode → initialization;
- rewarded native callback → direct callback and telemetry, independent of
  dismissal;
- dismissal/failure → presentation token cleanup and iOS delegate release;
- caller collection mutation → owned manager/controller snapshot;
- banner initial load/refresh → resolved per-call size policy;
- core bridges → Compose opt-in;
- recomposition → latest event lambda.

- [ ] **Step 2: Run the complete core gate**

Run:

```bash
./gradlew \
  :admob-cmp-core:allTests \
  :admob-cmp-core:testAndroidHostTest \
  :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-core:compileAndroidMain \
  :admob-cmp-core:compileKotlinIosSimulatorArm64 \
  :admob-cmp-core:checkKotlinAbi
```

Expected: build succeeds with zero failed tests.

- [ ] **Step 3: Run the complete Compose and umbrella gate**

Run:

```bash
./gradlew \
  :admob-cmp-compose:allTests \
  :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp-compose:iosSimulatorArm64Test \
  :admob-cmp-compose:compileAndroidMain \
  :admob-cmp-compose:compileKotlinIosSimulatorArm64 \
  :admob-cmp-compose:checkKotlinAbi \
  :admob-cmp:checkKotlinAbi
```

Expected: build succeeds with zero failed tests.

- [ ] **Step 4: Run repository checks**

Run:

```bash
./gradlew check
```

Expected: all repository checks pass.

- [ ] **Step 5: Verify local Maven publication**

Run:

```bash
./gradlew publishToMavenLocal -PVERSION_NAME=1.0.2
```

Expected: core, Compose, and umbrella 1.0.2 artifacts publish locally with valid
POM dependency scopes. Do not run a remote publishing task.

- [ ] **Step 6: Run final diff and status checks**

Run:

```bash
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors; only the approved implementation, tests,
documentation, version, ABI dumps, design spec, and this plan are changed.

- [ ] **Step 7: Perform a final read-only production review**

Use `superpowers:requesting-code-review` or an equivalent fresh review pass.
Treat as blockers:

- a reward path that can fire twice;
- explicit destruction that can suppress a post-dismissal reward;
- post-handoff delegate/token release from cancellation;
- mutable retained config/placement state;
- platform behavior not covered by the same common contract;
- stale public docs that grant from telemetry or the removed result.

- [ ] **Step 8: Hand off manual smoke coverage**

Report the exact automated commands and results. Request only these manual
release checks:

- Android and iOS consent deny/grant and privacy-options change;
- iOS UMP → ATT → first-request ordering;
- one load/show cycle for every format with official test IDs;
- rewarded and rewarded-interstitial callback grant;
- navigation/cancellation during presentation;
- banner recomposition with a changed `onEvent` lambda.

Do not claim device behavior was verified unless those checks were actually
run.

- [ ] **Step 9: Stop before Git or remote release actions**

Leave changes uncommitted. Offer the user the reviewed diff and verification
evidence. Commit, push, tag, Maven Central publish, and release creation each
require a separate explicit user request.
