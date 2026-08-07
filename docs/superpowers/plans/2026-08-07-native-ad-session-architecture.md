# Native Ad Session Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the composition-owned native-ad pool with a session-owned, viewport-aware system that preserves nearby ads across scrolling and tab changes while enforcing deterministic application-wide memory limits.

**Architecture:** `AdManager` owns one `NativeAdManager`, which owns named `NativeAdSession` instances and one shared admission governor. Sessions map stable feed-slot identities to native-ad records; Compose reports viewport windows and only attaches/detaches renderers, while the session and governor own loading, retention, eviction, expiry, memory pressure, and destruction.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines and `StateFlow`, Android GMA Next-Gen, iOS Google Mobile Ads 13.x cinterop, Compose Multiplatform, Navigation-independent session keys, KGP ABI validation.

## Global Constraints

- Work only in `admob-cmp-core`, `admob-cmp-compose`, their tests/docs, and the minimum in-repository consumer migration required to restore the build.
- Do not perform the Fieldnotes visual redesign in this plan.
- Remove the public `NativeAdPool`, `NativeAdToken`, `acquire`, `release`, `availableAds`, and platform peek APIs; backward compatibility is explicitly not required.
- Preserve Android/iOS behavior parity except documented upstream capabilities such as Android's missing native-video lifecycle callbacks.
- All GMA/UMP calls remain on `Dispatchers.Main.immediate`, except `MobileAds.initialize()` as documented in `admob-cmp/CLAUDE.md`.
- Android batch callback state remains synchronized because callbacks and cancellation can arrive on different threads.
- iOS `GADAdLoader.delegate` and native delegates remain strongly retained by Kotlin owners until terminal cleanup.
- Default active-session retention is 3 ads; default inactive-session retention is up to `min(NativeAdMemoryPolicy.inactiveSessionLimit, NativeAdSessionPolicy.maxRetainedAds)` ranked anchors (default 1).
- Default application **soft limit** is 4 native ads and **hard limit** is 6, counting loaded ads plus in-flight reservations. Speculative loading (prefetch-ahead, retain-behind warm-ups, backfill) stops at the soft limit; visible demand may exceed it up to the hard limit. Moderate memory pressure trims non-mounted records toward the soft limit; critical pressure retains mounted ads only.
- Native-ad TTL remains 1 hour. Inactive session metadata expires after 30 minutes; the registry keeps at most 32 inactive sessions and 64 total session records.
- A Google multi-ad request is explicitly opt-in, capped at 5, may partially fill, and must never be used for mediated or unknown inventory.
- Mounted ads are never evicted. Critical memory pressure retains mounted ads only.
- No unbounded collection may be keyed by consumer-provided placement, session, or slot identifiers.
- Test safety remains fail-closed through official test IDs and `strictTestMode`.
- Do not change `.github/workflows/release.yml`; verification belongs in `scripts/release-readiness.sh`.
- Do not commit, push, or open a PR unless the owner explicitly authorizes it during execution.

---

## File Structure

### Public core API

- Create `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdSession.kt` — public manager/session interfaces and slot/window state.
- Create `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdPolicies.kt` — memory, session, and batching policies with validated defaults.
- Modify `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdManager.kt` — replace `nativeAd(placement)` with `nativeAds`.
- Modify `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdConfig.kt` — accept the global native memory policy.
- Modify `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdPlacement.kt` — stop describing `AdCachePolicy` as native-ad capacity.
- Modify `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdModels.kt` — add `NativeAdBatching` to native request options.
- Delete `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdToken.kt`.

### Shared implementation

- Create `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdGovernor.kt` — global capacity reservations and eviction priority.
- Create `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdSessionCore.kt` — stable slot state machine and window reconciliation.
- Create `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdCoordinatorCore.kt` — session registry, load scheduling, TTL, consent invalidation, and cleanup.
- Create `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdPlatform.kt` — platform loading/destruction interface and internal handle types.
- Delete `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativePoolCore.kt` after its generation, retry, timeout, event, and exact-destruction invariants are represented in the new cores.
- Modify `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NoOpControllerRegistry.kt` and the no-op types in `AdManager.kt`.

### Platform implementation

- Create `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdCoordinator.kt`.
- Create `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeMemorySignal.kt`.
- Delete `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdPool.kt` after migration.
- Modify `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/AndroidGoogleAdManager.kt`.
- Create `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdCoordinator.kt`.
- Create `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeMemorySignal.kt`.
- Delete `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdPool.kt` after migration.
- Modify `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/IosGoogleAdManager.kt`.

### Compose integration

- Create `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/NativeAdFeedSession.kt` — simple consumer setup and lifecycle binding.
- Create `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/NativeAdViewportBinding.kt` — `LazyListState` to `NativeAdWindow` translation.
- Modify `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/NativeAdView.kt`.
- Modify `admob-cmp-compose/src/androidMain/kotlin/dev/avinya/ads/ui/AndroidNativeAdView.kt`.
- Modify `admob-cmp-compose/src/iosMain/kotlin/dev/avinya/ads/ui/IosNativeAdView.kt`.
- Modify the Android/iOS native layout renderers only where detachment needs explicit unregistering.

### Tests and documentation

- Replace `NativePoolCoreTest.kt` with focused governor, session, and coordinator tests.
- Update Android/iOS native characterization and batch-handoff tests.
- Add Compose viewport-policy tests.
- Update ABI dumps, `admob-cmp/AGENTS.md`, `admob-cmp/CLAUDE.md`, native docs, architecture diagrams, troubleshooting, landing API copy, and AI-agent guidance.

---

### Task 1: Define the replacement public API

**Files:**
- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdSession.kt`
- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdPolicies.kt`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdModels.kt`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdConfig.kt`
- Test: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/NativeAdPolicyTest.kt`

**Interfaces:**
- Consumes: `AdPlacement`, `AdError`, `NativeMediaInfo`, `StateFlow`.
- Produces: the exact public names every later task uses.

- [ ] **Step 1: Write failing policy validation tests**

```kotlin
class NativeAdPolicyTest {
    @Test fun `default memory policy is bounded`() {
        val policy = NativeAdMemoryPolicy()
        assertEquals(4, policy.softLimit)
        assertEquals(6, policy.hardLimit)
        assertEquals(1, policy.inactiveSessionLimit)
        assertEquals(32, policy.maxInactiveSessions)
        assertEquals(64, policy.maxSessionRecords)
    }

    @Test fun `hard limit cannot be lower than soft limit`() {
        assertFailsWith<IllegalArgumentException> {
            NativeAdMemoryPolicy(softLimit = 6, hardLimit = 4)
        }
    }

    @Test fun `session policy defaults to previous current next`() {
        assertEquals(
            NativeAdSessionPolicy(maxRetainedAds = 3, retainBehind = 1, prefetchAhead = 1),
            NativeAdSessionPolicy(),
        )
    }
}
```

- [ ] **Step 2: Run the focused test and confirm the API is absent**

Run: `./gradlew :admob-cmp-core:testAndroidHostTest --tests '*NativeAdPolicyTest*'`

Expected: compilation fails because the policy types do not exist.

- [ ] **Step 3: Add the public policy models**

Define these exact declarations in `NativeAdPolicies.kt`:

```kotlin
public data class NativeAdMemoryPolicy(
    val softLimit: Int = 4,
    val hardLimit: Int = 6,
    val inactiveSessionLimit: Int = 1,
    val maxInactiveSessions: Int = 32,
    val maxSessionRecords: Int = 64,
    val inactiveSessionTtl: Duration = 30.minutes,
)

public data class NativeAdSessionPolicy(
    val maxRetainedAds: Int = 3,
    val retainBehind: Int = 1,
    val prefetchAhead: Int = 1,
)

public enum class NativeAdBatching { Sequential, GoogleOnly }
```

Validation must reject non-positive limits, a soft limit above the hard limit, an inactive limit above the hard limit, `maxInactiveSessions > maxSessionRecords`, non-finite or non-positive TTLs, negative viewport distances, and a session window whose requested speculative parts cannot fit inside `maxRetainedAds` (using `Long` arithmetic so the addition is overflow-safe at the boundary).

- [ ] **Step 4: Add the public session models and interfaces**

Define these exact API shapes in `NativeAdSession.kt`:

```kotlin
public data class NativeAdSlot(val key: String, val placement: AdPlacement)

public data class NativeAdWindow(
    val visible: List<NativeAdSlot>,
    val retainBehind: List<NativeAdSlot> = emptyList(),
    val prefetchAhead: List<NativeAdSlot> = emptyList(),
)

public sealed interface NativeAdSlotState {
    public data object Empty : NativeAdSlotState
    public data object Loading : NativeAdSlotState
    public data class Ready(val mediaInfo: NativeMediaInfo?) : NativeAdSlotState
    public data class Mounted(val mediaInfo: NativeMediaInfo?) : NativeAdSlotState
    public data class Retained(val mediaInfo: NativeMediaInfo?) : NativeAdSlotState
    public data class Failed(val error: AdError) : NativeAdSlotState
}

public data class NativeAdSessionState(
    val active: Boolean,
    val slots: Map<String, NativeAdSlotState>,
)

public data class NativeAdManagerState(
    val loadedAds: Int,
    val reservedLoads: Int,
    val activeSessions: Int,
    val inactiveSessions: Int,
    val hardLimit: Int,
)

public interface NativeAdSession {
    public val key: String
    public val policy: NativeAdSessionPolicy
    public val state: StateFlow<NativeAdSessionState>
    public fun updateWindow(window: NativeAdWindow)
    public fun deactivate()
    public fun slotState(slotKey: String): StateFlow<NativeAdSlotState>
    public fun close()
}

public interface NativeAdManager {
    public val policy: NativeAdMemoryPolicy
    public val state: StateFlow<NativeAdManagerState>
    public fun session(
        key: String,
        policy: NativeAdSessionPolicy = NativeAdSessionPolicy(),
    ): NativeAdSession
    public fun closeSession(key: String)
    public fun clear()
}
```

Require non-blank session/slot keys, native-format placements, deduplication of identical `(key, placement)` duplicates in a window (the session core dedupes on first occurrence, visible > prefetchAhead > retainBehind), and a deterministic throw when the same key is reported with conflicting placements in the same window. `NativeAdManager.session(key, policy)` must throw when a session already exists for `key` with a different `policy`, matching the SDK's existing placement-collision behaviour.

- [ ] **Step 5: Add batching to `NativeAdOptions` and global policy to `AdConfig`**

Add `batching: NativeAdBatching = NativeAdBatching.Sequential` to `NativeAdOptions`. Add `nativeAdMemoryPolicy: NativeAdMemoryPolicy = NativeAdMemoryPolicy()` to `AdConfig`; the effective platform-initialization identity must continue to exclude fields that do not change native GMA initialization.

- [ ] **Step 6: Run the policy tests**

Run: `./gradlew :admob-cmp-core:testAndroidHostTest --tests '*NativeAdPolicyTest*'`

Expected: PASS.

---

### Task 2: Build atomic global admission and eviction

**Files:**
- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdGovernor.kt`
- Test: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/NativeAdGovernorTest.kt`

**Interfaces:**
- Consumes: `NativeAdMemoryPolicy` and the repository's cross-platform
  `FullScreenStateLock` (or an internal non-public generalization of it).
- Produces: demand-aware `reserve`, `admit`, `releaseReservation`, `touch`,
  `reclassify`, `setMounted`, `retire`, and `trim` operations for the coordinator.

Define an internal demand classification at this boundary:

```kotlin
internal enum class NativeAdDemandClass { Visible, Speculative }
```

Every reservation must carry one of these values. `Visible` means the slot is in the
reported visible band. `Speculative` covers prefetch-ahead, retain-behind warm-up,
inactive-anchor backfill, and every other load that is not currently visible. Do not
infer the class from placement or session identity inside the governor.

Use explicit mutation results so capacity retirement and platform cleanup remain
separate:

```kotlin
internal data class NativeAdReservationDecision(
    val reservations: List<NativeAdLoadReservation>,
    val retiredRecordIds: List<NativeAdRecordId> = emptyList(),
)

internal data class NativeAdTrimResult(
    val retiredRecordIds: List<NativeAdRecordId>,
    val cancelledReservations: List<NativeAdLoadReservation>,
)
```

`NativeAdLoadReservation` must be an identity token registered by the governor, not a
forgeable value whose caller-supplied priority is trusted during `admit`. `admit` uses
the governor's stored demand/priority and rejects an unknown, already-resolved, or
different token instance.

- [ ] **Step 1: Write failing governor tests**

Cover these exact cases:

```kotlin
@Test fun `reservations count against the hard limit`() { /* reserve 6; seventh is denied */ }
@Test fun `speculative reservation stops at soft limit`() { /* fifth speculative request is denied at default 4 */ }
@Test fun `visible reservation may exceed soft limit up to hard limit`() { /* visible fifth and sixth granted; seventh denied */ }
@Test fun `visible demand at hard limit retires eligible speculative victim atomically`() { /* victim returned, invariant preserved */ }
@Test fun `mounted ads are never eviction candidates`() { /* mounted survives retained LRU */ }
@Test fun `speculative ads evict before inactive anchors`() { /* assert victim identity */ }
@Test fun `inactive anchors evict before active retained ads`() { /* assert priority */ }
@Test fun `reclassifying a record changes its later eviction order`() { /* prefetched -> active -> inactive */ }
@Test fun `touch uses deterministic monotonic access order for LRU`() { /* no wall-clock dependence or ties */ }
@Test fun `moderate trim returns non-mounted victims until soft limit`() { /* loaded count trends to 4 */ }
@Test fun `critical trim returns only non-mounted records`() { /* mounted excluded */ }
@Test fun `critical trim cancels every in-flight reservation`() { /* late callbacks have no live permit */ }
@Test fun `moderate trim cancels speculative reservations before loaded records`() { /* total loaded plus reserved trends to 4 */ }
@Test fun `releasing a partial batch frees unused reservations`() { /* 3 reserved, 1 admitted */ }
@Test fun `all-or-nothing reservation leaves state unchanged when full count cannot fit`() { /* allowPartial=false */ }
@Test fun `admit rejects a forged or already resolved reservation token`() { /* canonical token only */ }
@Test fun `mixed visible and speculative races never exceed hard limit`() { /* loaded plus reserved always <= 6 */ }
```

- [ ] **Step 2: Run the focused tests**

Run: `./gradlew :admob-cmp-core:testAndroidHostTest --tests '*NativeAdGovernorTest*'`

Expected: compilation fails because `NativeAdGovernor` does not exist.

- [ ] **Step 3: Implement the governor under one lock**

Use an internal `NativeAdRecordId` and identity-based `NativeAdLoadReservation`. Use
`FullScreenStateLock.withLock` for every mutation; raw `synchronized` is unavailable in
Kotlin/Native common code and is prohibited here. Keep an incrementing access ordinal
under that lock for deterministic LRU ordering; do not use `Clock.System` wall time.

The invariant after every mutation must be:

```kotlin
loadedRecordCount + reservedLoadCount <= policy.hardLimit
```

Reservation and trim rules are exact:

- A `Speculative` reservation may consume only the remaining capacity below
  `policy.softLimit`. Deny it when `loadedRecordCount + reservedLoadCount` is already
  at the soft limit, even if hard-limit headroom remains.
- A `Visible` reservation may consume capacity above the soft limit, but never above
  `policy.hardLimit`.
- If visible demand arrives at the hard limit, the governor may atomically retire
  eligible non-mounted records and grant the replacement reservation in the same
  locked mutation. Return those retired record IDs with the reservation decision so
  the coordinator destroys their platform objects after releasing the governor lock.
- If no eligible victim exists because every record is mounted, deny the reservation;
  never violate the hard limit and never evict a mounted record.
- Moderate trim returns non-mounted victims until the count is at or below
  `policy.softLimit`. Count both loaded records and pending reservations. Cancel
  speculative pending reservations first, then retire records by eviction priority;
  cancel visible pending reservations last if that is the only way to approach the
  target. Mounted records may leave the result above the soft limit.
- Critical trim atomically removes every pending reservation and every non-mounted
  record. Return both collections in `NativeAdTrimResult`; late platform callbacks are
  handled by the coordinator's generation check and must never be admitted.

Eviction order is speculative prefetch, inactive retained anchor, active
retain-behind, active ready-ahead. Mounted records are never returned as victims. LRU
breaks ties inside a priority class. Provide `reclassify(recordId, priority)` because a
single record changes class as its slot moves from prefetched to visible, retained
behind, or an inactive anchor; its reservation-time priority is not permanent.

The reservation operation accepts `demandClass`, `priority`, `count`, and
`allowPartial`. Batch reservations may be partially granted only when
`allowPartial = true`; otherwise deny the whole request without mutating records or
reservations so accounting and requested load counts cannot diverge.

- [ ] **Step 4: Run the governor tests**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests '*NativeAdGovernorTest*' \
  --no-configuration-cache
./gradlew :admob-cmp-core:iosSimulatorArm64Test --no-configuration-cache
```

Expected: PASS on both platforms. The iOS command is mandatory for this commonMain
lock implementation; an Android-only pass does not complete Task 2.

---

### Task 3: Implement the per-session slot state machine

**Files:**
- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdSessionCore.kt`
- Test: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/NativeAdSessionCoreTest.kt`

**Interfaces:**
- Consumes: governor callbacks and coordinator load requests.
- Produces: stable `NativeAdSession` behavior and prioritized slot demand.

- [ ] **Step 1: Write failing transition tests**

```kotlin
@Test fun `same slot survives deactivate and reactivate`() { /* Mounted -> Retained -> Mounted */ }
@Test fun `inactive session retains only the last visible anchor`() { /* other detached ads retire */ }
@Test fun `far behind slots become eviction candidates`() { /* not destroyed inside session lock */ }
@Test fun `placement change for an existing slot is rejected`() { /* deterministic exception */ }
@Test fun `close is idempotent`() { /* every record retired once */ }
@Test fun `expired slot never remounts`() { /* state returns Empty and reload demand emitted */ }
@Test fun `walking one thousand slot keys keeps only window and retained state`() { /* bounded state map */ }
```

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew :admob-cmp-core:testAndroidHostTest --tests '*NativeAdSessionCoreTest*'`

- [ ] **Step 3: Implement window reconciliation**

`updateWindow()` must rank visible slots first, then `prefetchAhead`, then `retainBehind`; deduplicate without changing the first occurrence. Admit demand only for the first `maxRetainedAds` ranked slots, so a viewport reporting more native slots than the configured session capacity leaves lower-priority slots `Empty` rather than violating the cap. It publishes state under the session lock, but returns load/retirement actions for the coordinator to execute after unlocking. Once an out-of-window slot has no retained/loaded record and no in-flight generation, remove its state entry; repeated scrolling through unique slot keys must not grow `NativeAdSessionState.slots` without bound.

- [ ] **Step 4: Implement inactivity behavior**

`deactivate()` retains up to `min(NativeAdMemoryPolicy.inactiveSessionLimit, policy.maxRetainedAds)` ranked anchors, preferring mounted and visible slots in viewport order, falling back to the most recently accessed ready record if there is no visible slot. The default policy keeps one anchor; consumers that want a wider inactive ring raise `inactiveSessionLimit` deliberately. Retire all other detached records.

- [ ] **Step 5: Run the session tests**

Expected: PASS.

---

### Task 4: Coordinate sessions, loading, retries, TTL, and exact destruction

**Files:**
- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdPlatform.kt`
- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdCoordinatorCore.kt`
- Test: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/NativeAdCoordinatorCoreTest.kt`

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: the implementation backing `NativeAdManager` and internal render bindings used by Compose.

- [ ] **Step 1: Define the platform boundary**

```kotlin
internal interface NativeAdPlatform<A : Any> {
    suspend fun load(
        placement: AdPlacement,
        count: Int,
        generation: Long,
    ): AdAttemptResult<List<A>>
    fun destroy(ad: A)
    fun responseInfo(ad: A): AdResponseInfo?
    fun mediaInfo(ad: A): NativeMediaInfo?
}
```

The platform implementation reads `placement.nativeOptions.batching`; shared code never guesses whether mediation is configured.

- [ ] **Step 2: Write coordinator race tests**

Test cancellation after one callback, clear during load, consent revocation during backoff, partial batch admission, surplus callback after window removal, failed top-up preserving existing inventory, one-hour expiry, inactive-session TTL cleanup, the 32-inactive-record LRU, rejection of a 65th live session, and cleanup of idle per-placement schedulers.

- [ ] **Step 3: Implement serialized demand scheduling**

Serialize load work per placement, reserve capacity before invoking the platform, wrap the whole retry sequence in `placement.timeoutPolicy.loadTimeout`, and release unused reservations before publishing the terminal state. Remove a placement scheduler once it has no records, reservations, waiters, or retry job. Refuse a new session with a deterministic `IllegalStateException` when 64 live records already exist; never evict an active consumer-owned session behind its back.

- [ ] **Step 4: Implement generation invalidation**

`clear`, consent invalidation, and session closure must bump the relevant generation. A result from an older generation is destroyed immediately and never enters a session.

- [ ] **Step 5: Run the coordinator tests**

Run: `./gradlew :admob-cmp-core:testAndroidHostTest --tests '*NativeAdCoordinatorCoreTest*'`

Expected: PASS.

---

### Task 5: Replace the Android native pool

**Files:**
- Create: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdCoordinator.kt`
- Create: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeMemorySignal.kt`
- Modify: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/AndroidGoogleAdManager.kt`
- Delete: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdPool.kt`
- Delete: `admob-cmp-core/src/androidHostTest/kotlin/dev/avinya/ads/AndroidNativePoolCharacterizationTest.kt` after its cases exist in the coordinator characterization suite
- Test: `admob-cmp-core/src/androidHostTest/kotlin/dev/avinya/ads/NativeAdBatchHandoffTest.kt`
- Test: `admob-cmp-core/src/androidHostTest/kotlin/dev/avinya/ads/AndroidNativeCoordinatorCharacterizationTest.kt`

**Interfaces:**
- Consumes: `NativeAdCoordinatorCore<AndroidLoadedNativeAd>`.
- Produces: Android GMA loading, callbacks, teardown, revenue events, and memory signals.

- [ ] **Step 1: Adapt the batch-handoff tests before production code**

Assert `Sequential` uses the single-ad overload repeatedly and `GoogleOnly` uses `NativeAdLoader.load(request, count, callback)` with `count <= 5`. Assert partial success waits for `onAdLoadingCompleted`, and callbacks after cancellation destroy their ads.

- [ ] **Step 2: Implement Android loading modes**

Keep `pending`, `cancelled`, and `lastError` under `synchronized(pending)`. Snapshot response/media information on Main. `Sequential` must never call the count overload; `GoogleOnly` may use it for `min(reservedCount, 5)`.

- [ ] **Step 3: Add memory pressure mapping**

Register one application-level `ComponentCallbacks2`. Map moderate trim to `NativeMemoryPressure.Moderate` and severe/critical trim to `Critical`; unregister it when the process-wide manager is disposed in tests.

- [ ] **Step 4: Replace the manager registry**

Remove the `nativePools` map. Expose one process-wide coordinator through `override val nativeAds`. Manager clear/consent invalidation delegates to that coordinator.

- [ ] **Step 5: Run Android native tests**

Run: `./gradlew :admob-cmp-core:testAndroidHostTest`

Expected: PASS.

---

### Task 6: Replace the iOS native pool

**Files:**
- Create: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdCoordinator.kt`
- Create: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeMemorySignal.kt`
- Modify: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/IosGoogleAdManager.kt`
- Delete: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdPool.kt`
- Delete: `admob-cmp-core/src/iosTest/kotlin/dev/avinya/ads/nativead/IosNativePoolCharacterizationTest.kt` after its cases exist in the coordinator characterization suite
- Test: `admob-cmp-core/src/iosTest/kotlin/dev/avinya/ads/nativead/IosNativeCoordinatorCharacterizationTest.kt`

**Interfaces:**
- Consumes: `NativeAdCoordinatorCore<LoadedNativeAd>`.
- Produces: iOS GMA loading, weak-delegate retention, teardown, revenue events, and memory warnings.

- [ ] **Step 1: Write iOS loading-mode tests**

Verify `Sequential` omits `GADMultipleAdsAdLoaderOptions`; `GoogleOnly` supplies it with `numberOfAds <= 5`; the active-load registry retains delegates until `adLoaderDidFinishLoading`; and invalidated delegates destroy late arrivals.

- [ ] **Step 2: Implement the iOS adapter**

Retain each loader and delegate through terminal completion. On retirement, clear `GADNativeAd.delegate`, paid handlers, video delegates, and render bindings on Main so ARC can reclaim the object.

- [ ] **Step 3: Add memory-warning observation**

Observe `UIApplicationDidReceiveMemoryWarningNotification`, send `Critical`, and remove the observer during manager teardown in tests.

- [ ] **Step 4: Replace the iOS manager registry and run tests**

Run: `./gradlew :admob-cmp-core:iosSimulatorArm64Test`

Expected: PASS.

---

### Task 7: Replace `AdManager` and no-op wiring

**Files:**
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdManager.kt`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdTelemetry.kt`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NoOpControllerRegistry.kt`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/Fakes.kt`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/NoOpAdManagerTest.kt`
- Delete: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdToken.kt`
- Delete: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativePoolCore.kt`

**Interfaces:**
- Consumes: Tasks 1-6.
- Produces: a compiling core module with no public pool/token API.

- [ ] **Step 1: Change `AdManager`**

Replace:

```kotlin
public fun nativeAd(placement: AdPlacement): NativeAdPool
```

with:

```kotlin
public val nativeAds: NativeAdManager
```

- [ ] **Step 2: Implement the no-op manager/session**

No-op sessions accept windows, publish `Failed(AdError.sdkNotReady(...))` for requested slots, never reserve capacity, and make repeated `close()`/`clear()` safe.

- [ ] **Step 3: Remove token/pool references from fakes and tests**

Change native telemetry documentation so `adInstanceId` is an SDK-generated internal instance identity, not a `NativeAdToken.tokenId`. Preserve the field itself so retained-instance behavior remains inspectable without exposing platform objects.

Run: `rg -n 'NativeAdPool|NativeAdToken|availableAds|nativeAd\(' admob-cmp-core/src`

Expected: no production references and only deliberate migration assertions in tests.

- [ ] **Step 4: Run all core tests**

Run: `./gradlew :admob-cmp-core:testAndroidHostTest :admob-cmp-core:iosSimulatorArm64Test`

Expected: PASS.

---

### Task 8: Add the Compose feed-session and viewport adapter

**Files:**
- Create: `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/NativeAdFeedSession.kt`
- Create: `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/NativeAdViewportBinding.kt`
- Modify: `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/NativeAdView.kt`
- Test: `admob-cmp-compose/src/commonTest/kotlin/dev/avinya/ads/ui/NativeAdViewportBindingTest.kt`

**Interfaces:**
- Consumes: the public session API from Task 1.
- Produces: the simple Compose setup used by Showcase and ViewTube.

- [ ] **Step 1: Write pure viewport calculation tests**

Test one visible ad, no visible ad with the next slot ahead, two visible ads on a large viewport, reverse scrolling, missing Paging items, duplicate slot keys, and the three-ad cap.

- [ ] **Step 2: Define the Compose entry point**

```kotlin
@Composable
public fun rememberNativeAdFeedSession(
    sessionKey: String,
    listState: LazyListState,
    itemCount: Int,
    slotAt: (index: Int) -> NativeAdSlot?,
    policy: NativeAdSessionPolicy = NativeAdSessionPolicy(),
): NativeAdSession
```

Internally obtain `LocalAdManager.current.nativeAds.session(sessionKey, policy)`, use `snapshotFlow` over `LazyListState.layoutInfo`, and call `session.deactivate()` from `DisposableEffect`. Use `rememberUpdatedState` for `itemCount` and `slotAt` so recomposition does not restart the session.

- [ ] **Step 3: Replace the common `NativeAdView` signature**

```kotlin
@Composable
public expect fun NativeAdView(
    session: NativeAdSession,
    slotKey: String,
    placement: AdPlacement,
    layout: AdLayout = AdTemplates.mediaCard,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { NativeAdLoadingPlaceholder() },
    failure: @Composable (AdError) -> Unit = {},
    onEvent: (AdEvent) -> Unit = {},
)
```

- [ ] **Step 4: Run Compose common tests**

Run: `./gradlew :admob-cmp-compose:testAndroidHostTest --tests '*NativeAdViewportBindingTest*'`

Expected: PASS.

---

### Task 9: Implement Android/iOS render attachment without ad destruction

**Files:**
- Modify: `admob-cmp-compose/src/androidMain/kotlin/dev/avinya/ads/ui/AndroidNativeAdView.kt`
- Modify: `admob-cmp-compose/src/iosMain/kotlin/dev/avinya/ads/ui/IosNativeAdView.kt`
- Modify: `admob-cmp-compose/src/androidMain/kotlin/dev/avinya/ads/nativead/rendering/AndroidNativeAdLayoutRenderer.kt`
- Modify: `admob-cmp-compose/src/iosMain/kotlin/dev/avinya/ads/nativead/rendering/IosNativeAdRenderer.kt`
- Test: `admob-cmp-compose/src/androidHostTest/kotlin/dev/avinya/ads/ui/AndroidNativeRenderBindingTest.kt`
- Test: `admob-cmp-compose/src/iosTest/kotlin/dev/avinya/ads/ui/IosNativeRenderBindingTest.kt`

**Interfaces:**
- Consumes: session slot state and internal render-handle access.
- Produces: `Mounted <-> Retained` rendering with no reload.

- [ ] **Step 1: Add render-binding lifecycle tests**

Test attach, detach, reattach, slot eviction, layout-identity change, composition cancellation, and repeated disposal. Assert detachment never destroys the native ad and eviction destroys it once.

- [ ] **Step 2: Implement Android detachment**

Create a new `NativeAdView` for each mount, register the retained ad, and call the view's `destroy()` on Compose release without calling `NativeAd.destroy()`. The coordinator remains the sole owner allowed to destroy the ad object.

- [ ] **Step 3: Implement iOS detachment**

Before releasing the host controller, set `GADNativeAdView.nativeAd = null` and release the view/controller. Do not clear the `GADNativeAd` delegates until coordinator eviction.

- [ ] **Step 4: Characterize real SDK behavior**

On Android and iOS test devices, mount one slot, record its SDK instance id, switch tabs, return, and verify the same id renders and clicks. Confirm no impression occurs while detached and no duplicate platform-registration warning appears.

If either platform cannot safely rebind the same native-ad object after detachment, stop implementation and report the upstream behavior; do not silently retain an attached hidden view or reload the slot.

- [ ] **Step 5: Run Compose platform tests**

Run: `./gradlew :admob-cmp-compose:testAndroidHostTest :admob-cmp-compose:iosSimulatorArm64Test`

Expected: PASS.

---

### Task 10: Migrate in-repository consumers without redesigning them

**Files:**
- Modify: `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/debug/tabs/FormatsTab.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/feed/FeedScreen.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/article/ArticleScreen.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/inspector/PlacementsTab.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/domain/ad/ShowcasePlacements.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/domain/feed/FeedItem.kt`
- Modify: affected Showcase tests only where the public SDK call changed.

**Interfaces:**
- Consumes: `rememberNativeAdFeedSession` and the new `NativeAdView`.
- Produces: a buildable repository before the separate Fieldnotes redesign begins.

- [ ] **Step 1: Migrate the feed**

Keep the current visual layout. Add one `LazyListState`, bind session key `showcase-feed`, map Paging indexes to `FeedItem.NativeAdSlot`, and pass that session into each native view.

- [ ] **Step 2: Migrate article inline ads**

Use a session key derived from the article route, such as `article:$articleId`, and stable slot key `inline-after-paragraph-3`. Deactivate when the article leaves composition.

- [ ] **Step 3: Configure batching safely**

Official Google test placements may set `NativeAdBatching.GoogleOnly`. Every production/unknown placement remains `Sequential` by default.

- [ ] **Step 4: Remove manual pool controls from the debug tab**

Replace preload/acquire/release buttons in both Compose `FormatsTab` and Showcase `PlacementsTab` with session/window/eviction demonstrations that do not expose platform objects. For native rows, read `NativeAdManager.state` and the relevant `NativeAdSession.state`; other formats keep their controller state.

- [ ] **Step 5: Run Showcase tests**

Run: `./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test`

Expected: PASS.

---

### Task 11: Rewrite documentation and ABI snapshots

**Files:**
- Modify: `admob-cmp/AGENTS.md`
- Modify: `admob-cmp/CLAUDE.md`
- Modify: `admob-cmp/README.md`
- Modify: `docs-site/src/content/docs/formats/native.mdx`
- Modify: `docs-site/src/content/docs/reference/architecture.mdx`
- Modify: `docs-site/src/content/docs/reference/troubleshooting.mdx`
- Modify: `docs-site/src/content/docs/reference/diagrams-in-words.mdx`
- Modify: `docs-site/src/content/docs/project/ai-agents.mdx`
- Modify: `docs-site/src/components/diagrams/ModuleMap.astro`
- Replace: `docs-site/src/components/diagrams/NativePoolLifecycle.astro` with `NativeSessionLifecycle.astro`
- Modify: `docs-site/src/components/diagrams/descriptions.json`
- Modify: `docs-site/src/data/landing.ts`
- Update: `admob-cmp-core/api/*.klib.api`
- Update: `admob-cmp-compose/api/*.klib.api`

**Interfaces:**
- Consumes: the final implemented public API.
- Produces: documentation that contains no pool/token instructions.

- [ ] **Step 1: Rewrite the canonical native guide**

Document stable session keys, stable slot keys, one feed setup example, isolated-ad setup, default limits, batch-versus-retention distinction, mediation safety, TTL, memory pressure, and tab restoration.

- [ ] **Step 2: Replace architecture and troubleshooting references**

Remove `NativePoolCore`, `availableAds`, manual acquire/release, and `maxSize` native accounting from every source document and diagram.

- [ ] **Step 3: Regenerate ABI dumps**

Run:

```bash
./gradlew :admob-cmp-core:updateKotlinAbi
./gradlew :admob-cmp-compose:updateKotlinAbi
```

Expected: dumps remove the old pool/token surface and contain the new manager/session surface exactly once.

- [ ] **Step 4: Scan for stale APIs**

Run:

```bash
rg -n 'NativeAdPool|NativeAdToken|availableAds|pool\.acquire|pool\.release|NativePoolCore' \
  admob-cmp admob-cmp-core admob-cmp-compose docs-site/src showcase/src
```

Expected: no stale production or documentation references; migration-history references are allowed only when explicitly labelled historical.

---

### Task 12: Full release gate and manual lifecycle matrix

**Files:** None beyond fixes required by verification.

**Interfaces:**
- Consumes: Tasks 1-11.
- Produces: a release-ready SDK foundation for the Showcase redesign.

- [ ] **Step 1: Run the mandatory repository gate**

Run: `./scripts/release-readiness.sh`

Expected sections: version lockstep, Gradle plugin, Android/ABI/publication metadata, Central task graph, iOS/ABI, published-consumer round trip, Xcode consumer, and docs all pass with final `READINESS: PASS`.

- [ ] **Step 2: Run the device lifecycle matrix**

Verify on Android and iOS:

1. Scroll forward into a preloaded slot without blank flash.
2. Scroll away and back within the three-ad window; instance id remains unchanged.
3. Switch tabs and return; the inactive anchor remains unchanged.
4. Scroll beyond retention and return; a new ad is allowed.
5. Open two independent feed sessions; slot keys do not collide.
6. Trigger memory pressure; detached inventory shrinks and mounted ads remain.
7. Revoke consent; every native object is invalidated and destroyed.
8. Wait or inject the one-hour clock boundary; expired ads never remount.
9. Exercise Google-only partial batch and sequential placement modes.
10. Confirm every platform ad is destroyed exactly once in logs/tests.

- [ ] **Step 3: Report verification before any PR**

Report every section that ran, failures fixed, device matrix results, and any skipped item. Ask the owner for explicit approval before opening a PR.

---

## Acceptance Criteria

- [ ] Public pool/token APIs no longer exist.
- [ ] Consumer setup needs only a session key, stable slot mapping, placement, layout, and `LazyListState`.
- [ ] Same session and slot reuse the same ad across Compose detachment and tab switching while retained.
- [ ] Feed length cannot increase loaded native-ad count.
- [ ] Loaded plus reserved ads never exceed six under races, partial batches, or cancellation.
- [ ] Active sessions load/retain at most three and inactive sessions retain at most one under defaults; consumers may raise both session and global policies deliberately.
- [ ] Mounted ads are never evicted.
- [ ] Google-only loads batch by current deficit up to five; mediated/unknown loads are sequential.
- [ ] Moderate/critical memory pressure follows the locked trim policy.
- [ ] Consent invalidation, expiry, close, clear, and late callbacks destroy exactly once.
- [ ] Android and iOS tests, manual characterization, ABI validation, docs, published-consumer round trip, and Xcode consumer all pass.

## Risks and Rollback

- **Platform reattachment risk:** Official APIs document registration and destruction but do not guarantee every detach/reattach detail. Task 9 is a release blocker; do not substitute hidden attached views or silent reloads.
- **Memory risk:** Count-based limits cannot measure creative-specific media size. Platform memory signals therefore remain mandatory even with the six-ad hard limit.
- **Mediation risk:** The SDK cannot infer server-side mediation configuration. Sequential is the safe default; Google-only batching requires explicit consumer intent.
- **Dirty-worktree risk:** This repository currently contains user-owned uncommitted Showcase work. Execution must begin only after the owner selects a baseline or in an isolated worktree containing the intended changes.
- **Rollback:** Revert the SDK redesign as one coordinated change, including ABI and in-repository consumer migration. Do not attempt a partial rollback that restores `NativeAdPool` while leaving session-based Compose calls.
