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
- Native-ad TTL remains placement-configurable through
  `placement.cachePolicy.expirationPolicy.nativeTtl` with a 1-hour default. Inactive
  session metadata expires after 30 minutes; the registry keeps at most 32 inactive
  sessions and 64 total session records.
- A Google multi-ad request is explicitly opt-in, capped at 5, may partially fill, and must never be used for mediated or unknown inventory.
- Mounted ads are never evicted. Critical memory pressure retains mounted ads only.
- No unbounded collection may be keyed by consumer-provided placement, session, or slot identifiers.
- `NativeAdCoordinatorCore` is the only owner of admitted platform-ad objects. Session
  state stores record ids and immutable media metadata only; the governor stores counts
  and priorities only. Every platform object leaves coordinator ownership through one
  exact-once retirement path.
- Coordinator/session/scheduler registry state has one lock owner. Never call platform
  loading, platform metadata access, event callbacks, or platform destruction while a
  coordinator, session, scheduler, or governor lock is held. The only permitted nested
  direction is coordinator mutation -> short governor mutation; no callback acquires the
  locks in reverse order.
- `maxRetainedAds` counts a session's admitted records plus accepted in-flight slot
  generations. An out-of-window mounted record may temporarily delay convergence, but
  no new demand is admitted until detachment brings the session back within its limit.
- Never invoke a platform loader with `count <= 0`. One granted reservation maps to one
  slot generation and at most one returned platform ad; partial and surplus results must
  resolve every reservation and slot state deterministically.
- Update the relevant `api/*.klib.api` dump in the same task and commit as every public
  API change. Task 11 verifies the final dumps; it does not defer regeneration.
- Test safety remains fail-closed through official test IDs and `strictTestMode`.
- Do not change `.github/workflows/release.yml`; verification belongs in `scripts/release-readiness.sh`.
- Do not commit, push, or open a PR unless the owner explicitly authorizes it during execution.

### Breaking-change migration map

The repository has no production consumers, so this plan deliberately chooses the smaller,
safer surface instead of compatibility shims. The written migration contract required by the
repository is:

| Old API/ownership | Replacement |
|---|---|
| `AdManager.nativeAd(placement)` | `AdManager.nativeAds.session(sessionKey, policy)` |
| `NativeAdPool.preload(count)` | `NativeAdSession.updateWindow(window)`; the SDK derives demand |
| `NativeAdPool.acquire/release` | Stable `NativeAdSlot.key`; Compose obtains an internal mount lease |
| `NativeAdToken` / platform `peek` | No consumer handle; coordinator owns the platform object |
| `availableAds` / native `maxSize` | Manager/session state plus global/session policies |
| `NativeAdView(placement, itemKey, ...)` | `NativeAdView(session, slotKey, placement, ...)` |

ViewTube is an out-of-repository consumer and must migrate after the SDK is locally published:
create one stable session per logical feed owner, report stable ad-slot keys from its feed model,
and retain that session owner across dashboard tab composition. Do not add a compatibility layer
solely for ViewTube.

### Official SDK facts this plan relies on

- [Android GMA Next-Gen native loading](https://developers.google.com/admob/android/next-gen/native)
  caps a multiple-ad request at five, may return fewer ads than requested, ends the
  request with `onAdLoadingCompleted`, disallows multiple loading for mediated units, and
  warns that native ads are memory-heavy so caches should contain only immediately needed
  inventory and be cleared/reloaded after one hour.
- [The Next-Gen loader callback reference](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/nativead/NativeAdLoaderCallback)
  makes that terminal callback the safe Android batch handoff boundary.
- [The iOS native guide](https://developers.google.com/admob/ios/native) caps multiple-ad
  requests at five, warns that the exact count is not guaranteed, prohibits the multiple-ad
  option for mediated ad units, recommends caching only immediately needed ads, and says to
  clear/reload cached native ads after one hour.
- [Android `NativeAd`](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/nativead/NativeAd)
  and [Android `NativeAdView`](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/nativead/NativeAdView)
  have separate destruction APIs/ownership. The view reference documents registration and
  view destruction, but not detach-and-reattach of one ad across new view instances.
- [iOS `GADNativeAdView`](https://developers.google.com/admob/ios/api/reference/Classes/GADNativeAdView)
  documents the `nativeAd` binding but does not guarantee cross-view reattachment.

The final point is why Task 9 keeps real-device/simulator reattachment characterization as
a release blocker instead of presenting reattachment as an official Google guarantee.

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
- Create `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdManagerImpl.kt` — thin public manager/session wrappers over coordinator-issued generations.
- Delete `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativePoolCore.kt` after its generation, retry, timeout, event, and exact-destruction invariants are represented in the new cores.
- Modify `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NoOpControllerRegistry.kt` and the no-op types in `AdManager.kt`.

### Platform implementation

- Create `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdPlatform.kt`.
- Create `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdRenderLease.kt` — opt-in render-only bridge for Compose.
- Create `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeMemorySignal.kt`.
- Delete `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdPool.kt` after migration.
- Modify `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/AndroidGoogleAdManager.kt`.
- Create `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdPlatform.kt`.
- Create `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdRenderLease.kt` — opt-in render-only bridge for Compose.
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
- Update: `admob-cmp-core/api/admob-cmp-core.klib.api`

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

`retainBehind` and `prefetchAhead` count native-ad slots, not ordinary feed rows. The
Compose adapter performs the bounded row scan needed to find those slots; the session core
receives already-resolved bands and does not know list indexes.

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

Do not add a cached `slotState(slotKey)` API. Arbitrary consumer keys would create an
unbounded per-key `StateFlow` registry or leaking `stateIn` jobs. Consumers and Compose select
the slot from the single bounded `NativeAdSession.state` map; its default size is three.

- [ ] **Step 5: Add batching to `NativeAdOptions` and global policy to `AdConfig`**

Add `batching: NativeAdBatching = NativeAdBatching.Sequential` to `NativeAdOptions`. Add `nativeAdMemoryPolicy: NativeAdMemoryPolicy = NativeAdMemoryPolicy()` to `AdConfig`; the effective platform-initialization identity must continue to exclude fields that do not change native GMA initialization.

Update `AdPlacement`/`AdCachePolicy` documentation precisely: native sessions ignore
`cachePolicy.maxSize` and `reloadAfterShow` because capacity/reload now come from session
and memory policies, but `cachePolicy.expirationPolicy.nativeTtl` remains the authoritative
per-placement TTL (default one hour). Do not silently discard that existing option.

- [ ] **Step 6: Run the policy tests**

Run: `./gradlew :admob-cmp-core:testAndroidHostTest --tests '*NativeAdPolicyTest*'`

Expected: PASS.

- [ ] **Step 7: Regenerate and check the core ABI in the same task**

```bash
./gradlew :admob-cmp-core:updateKotlinAbi
./gradlew :admob-cmp-core:checkKotlinAbi
```

Expected: the policy/session types are present and no per-key `slotState` observer appears.

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
    val cancelledReservations: List<NativeAdLoadReservation> = emptyList(),
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
@Test fun `release rejects a forged reservation without consuming the canonical token`() { /* canonical token only */ }
@Test fun `visible demand cancels speculative pending work before denying at hard limit`() { /* cancelled permit returned atomically */ }
@Test fun `mixed visible and speculative concurrent callers never exceed hard limit`() { /* use genuinely parallel callers; loaded plus reserved always <= 6 */ }
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
  eligible capacity and grant the replacement reservation in the same locked mutation.
  Cancel speculative pending reservations first, oldest first, then retire eligible
  non-mounted records by eviction priority and LRU. Return both cancelled reservation
  tokens and retired record IDs with the reservation decision so the coordinator can
  discard late load callbacks and destroy platform objects after releasing the governor
  lock. Never cancel an existing visible reservation merely to replace it with another
  visible reservation.
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

Both `admit` and `releaseReservation` must compare the supplied reservation by identity
with the governor's canonical stored token. Releasing an unknown/already-resolved token
is idempotent, but a different token instance carrying a live ID is rejected and must not
consume the real reservation. Reject negative reservation counts; `count = 0` is a no-op.

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

### Task 3: Complete the pure per-session state machine

This task replaces the incomplete Task 3 implementation. Do not preserve its internal
shape merely to minimize the diff: later tasks depend on the contracts below.

**Files:**
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdSessionCore.kt`
- Rewrite/extend: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/NativeAdSessionCoreTest.kt`

**Interfaces:**
- Consumes: public session/window/state models from Task 1 and opaque
  `NativeAdRecordId`/priority types from Task 2. The coordinator supplies the already
  validated inactive-retention limit from `NativeAdMemoryPolicy`.
- Produces: a pure, coordinator-owned state machine. It never calls the governor or a
  platform API itself.

Define these internal concepts explicitly; equivalent names are acceptable only if all
call sites and tests use one consistent vocabulary:

```kotlin
internal enum class NativeAdBand { Visible, PrefetchAhead, RetainBehind, Out }

internal data class SlotGeneration(val slotKey: String, val generation: Long)

internal data class SlotDemandEntry(
    val key: String,
    val placement: AdPlacement,
    val generation: Long,
    val band: NativeAdBand,
    val demandClass: NativeAdDemandClass,
    val admittedPriority: NativeAdPriority,
)

internal data class RecordReclassification(
    val recordId: NativeAdRecordId,
    val priority: NativeAdPriority,
)

internal data class NativeAdSessionMutation(
    val demands: List<SlotDemandEntry> = emptyList(),
    val retireRecordIds: List<NativeAdRecordId> = emptyList(),
    val invalidateLoads: List<SlotGeneration> = emptyList(),
    val reclassifications: List<RecordReclassification> = emptyList(),
)
```

Band mapping is fixed:

| Band | Demand class | Loaded-record priority | Published loaded state |
|---|---|---|---|
| Visible | `Visible` | `ActiveReadyAhead` | `Ready` or `Mounted` |
| PrefetchAhead | `Speculative` | `Speculative` | `Retained` |
| RetainBehind | `Speculative` | `ActiveRetainedBehind` | `Retained` |
| Inactive retained anchor | no new demand | `InactiveAnchor` | `Retained` |
| Out | no new demand | retire when detached | `Retained` only while temporarily mounted |

- [ ] **Step 1: Replace the existing tests with failing contract tests**

Add named tests for all of these behaviors:

```kotlin
@Test fun `new session is inactive until its first window`()
@Test fun `window ranking and demand classification are visible then ahead then behind`()
@Test fun `same in-flight generation is not emitted twice`()
@Test fun `existing records are reclassified when their band changes`()
@Test fun `session cap counts existing records plus in-flight demand`()
@Test fun `out-of-window detached records return explicit retirement actions`()
@Test fun `mounted out-of-window record delays replacement demand until detachment`()
@Test fun `out-of-window in-flight loads return explicit invalidations`()
@Test fun `out-of-window failed and empty entries are pruned`()
@Test fun `stale admit is rejected without taking ownership`()
@Test fun `accepted admit publishes the supplied media info`()
@Test fun `deactivate keeps the last visible anchor in current viewport order`()
@Test fun `deactivate invalidates every non-anchor in-flight generation`()
@Test fun `inactive session rejects a late non-anchor admit`()
@Test fun `reactivation preserves the retained anchor record`()
@Test fun `expiry reloads only an active slot still inside its retained window`()
@Test fun `close is idempotent and permanently rejects later mutation`()
@Test fun `walking one thousand successful failed and in-flight keys remains bounded`()
```

Each test must assert the returned mutation actions and final slot map. Tests must not
inspect only an initially-empty state or claim that an orphaned governor record is an
acceptable eviction candidate.

- [ ] **Step 2: Run the tests and confirm RED**

Run:

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests '*NativeAdSessionCoreTest*' \
  --no-configuration-cache
```

Expected: the new ownership/cap/closed-session tests fail against the current core.

- [ ] **Step 3: Make `NativeAdSessionCore` a pure state machine**

Remove its `NativeAdGovernor` dependency and internal state lock. All calls are made by
the coordinator while holding the coordinator mutation lock; the core only mutates its
slot table, publishes `NativeAdSessionState`, and returns `NativeAdSessionMutation`.
Its constructor accepts `(key, policy, inactiveRetentionLimit)`; it does not retain the
governor or whole memory policy. Initialize `active = false` and keep an irreversible
`closed` flag.

Store per slot: placement, band, current viewport rank, generation, in-flight generation,
record id, media info, mounted flag, last access ordinal, and last error. The access
ordinal is monotonic and is refreshed when a slot becomes visible, mounts, or is touched.

- [ ] **Step 4: Implement bounded window reconciliation**

`updateWindow()` validates placement consistency, marks the session active, deduplicates
by first occurrence, and builds the desired set from the first `maxRetainedAds` ranked
keys. Reconcile existing ownership before emitting demand:

1. Desired record: preserve it and return a reclassification if its band changed.
2. Desired in-flight generation: preserve it and emit no duplicate demand.
3. Desired empty/failed slot: emit one fresh demand and mark that generation in flight.
4. Non-desired detached record: remove it from the entry and return its record id for
   retirement.
5. Non-desired in-flight slot: invalidate its generation and clear it.
6. Non-desired mounted record: preserve it temporarily as `Out`; emit no new speculative
   or visible demand while it consumes the session cap.
7. Non-desired entry with no record/in-flight work: publish `Empty` if needed for the
   current call, then prune it from the retained map, including failed entries.

The session's record count plus in-flight generation count must be at most
`maxRetainedAds`, except for temporarily mounted `Out` records that cannot legally be
retired. Such an exception blocks all new demand rather than increasing memory.

- [ ] **Step 5: Implement explicit callback and lifecycle outcomes**

`recordAdmitted(slotKey, recordId, mediaInfo, generation)` returns `true` only when the
session is not closed and the exact generation is still accepted for the desired slot or
retained inactive anchor. On `false`, it does not mutate ownership. `recordDeferred`
clears a generation that received no governor reservation back to `Empty`.
`recordFailed` clears the in-flight marker and publishes `Failed` only for the matching
generation.

`setMounted(slotKey, recordId, mounted)` validates the exact current record. Mounting
refreshes access order. Detaching an `Out` record returns it for retirement, prunes the old
slot, and immediately reconciles any desired empty slot now allowed by the freed session
capacity; this detachment mutation is how a newly visible replacement begins loading.
A stale renderer release is a no-op.

`deactivate()` chooses at most
`min(inactiveRetentionLimit, policy.maxRetainedAds)` records. Rank the last visible key in
the most recent viewport first when it is mounted/loaded, then remaining mounted visible
records in reverse viewport order, then remaining visible records in reverse order, then
most-recently-accessed ready records. This makes the default one-anchor choice exact rather
than map-iteration dependent. It returns retirements for all other records, invalidates
every non-anchor in-flight load, and reclassifies anchors to `InactiveAnchor`. `close()`
returns all records/in-flight generations once, clears the map, and permanently rejects
later updates/admissions.

`expireSlot()` returns the old record for retirement. It emits replacement demand only
when the session is active and the slot remains in the desired retained window; inactive,
out-of-window, and closed slots become/prune to `Empty` without reloading.

- [ ] **Step 6: Run Task 3 on Android and iOS**

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests '*NativeAdSessionCoreTest*' \
  --no-configuration-cache
./gradlew :admob-cmp-core:iosSimulatorArm64Test --no-configuration-cache
```

Expected: PASS. Do not proceed to Task 4 with any orphaned record, duplicate generation,
or unbounded-map test unresolved.

---

### Task 4: Complete coordinator ownership, scheduling, retries, and cleanup

This task replaces the incomplete Task 4 coordinator. It must be reviewed as the
load-bearing boundary for every platform and Compose task that follows.

**Files:**
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdPlatform.kt`
- Rewrite: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdCoordinatorCore.kt`
- Rewrite/extend: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/NativeAdCoordinatorCoreTest.kt`
- Correct: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdSession.kt`
- Update: `admob-cmp-core/api/admob-cmp-core.klib.api`

**Interfaces:**
- Consumes: Tasks 1-3 and `retryAdLoad`/`isRetryableLoadFailure`.
- Produces: one exact owner for platform ads, reservations, session registry state,
  per-placement schedulers, memory pressure, TTL, and internal render lookup.

Use an explicit partial-batch result so unmatched slots receive a real terminal state:

```kotlin
internal data class NativeAdPlatformBatch<A : Any>(
    val ads: List<A>,
    val unfilledError: AdError?,
)

internal interface NativeAdPlatform<A : Any> {
    suspend fun load(
        placement: AdPlacement,
        count: Int,
        generation: Long,
    ): AdAttemptResult<NativeAdPlatformBatch<A>>
    suspend fun bindEvents(
        ad: A,
        adInstanceId: String,
        emit: (AdEvent) -> Unit,
    )
    fun destroy(ad: A)
    fun responseInfo(ad: A): AdResponseInfo?
    fun mediaInfo(ad: A): NativeMediaInfo?
}

internal data class NativeAdRenderRecord<A : Any>(
    val recordId: NativeAdRecordId,
    val adInstanceId: String,
    val ad: A,
    val mediaInfo: NativeMediaInfo?,
)
```

Platform adapters must snapshot response/media metadata on Main, so the accessors above
are pure reads. `bindEvents` installs paid/native/video callbacks on Main and tags all
instance-scoped callbacks with the supplied coordinator identity; it is called outside
locks before a record becomes renderable. `NativeAdCoordinatorCore` stores an owned record containing the platform
ad, generated instance id, placement id, session key, slot key/generation, metadata,
loaded time, and optional renderer owner id.

- [ ] **Step 1: Write the complete failing coordinator test matrix**

Add deterministic tests for:

```kotlin
@Test fun `zero granted reservations never call the platform`()
@Test fun `platform count equals granted reservation count`()
@Test fun `google only demand twelve is scheduled as five five two`()
@Test fun `one reservation maps to one slot generation`()
@Test fun `mixed visible and speculative queue preserves each demand class and soft limit`()
@Test fun `partial result resolves unmatched slots and releases unused permits`()
@Test fun `surplus ads are destroyed exactly once`()
@Test fun `governor cancelled reservations invalidate their owning loads`()
@Test fun `governor retired records destroy their exact platform objects`()
@Test fun `stale session admission retires governor record and destroys ad`()
@Test fun `events bind before readiness and stale instance events are dropped`()
@Test fun `whole retry sequence obeys retry policy and one load timeout`()
@Test fun `non-retryable failure makes one attempt`()
@Test fun `cancellation during backoff releases reservations`()
@Test fun `clear during load destroys every late callback exactly once`()
@Test fun `consent revocation during backoff makes no later platform request`()
@Test fun `close session invalidates queued and active generations`()
@Test fun `loaded ad ttl destroys old object and reloads eligible active slot`()
@Test fun `inactive ttl destroys a real retained anchor`()
@Test fun `inactive lru evicts the oldest real retained session`()
@Test fun `reactivation removes session from inactive accounting`()
@Test fun `different policy for existing session key is rejected`()
@Test fun `sixty fifth live session is rejected without evicting an active session`()
@Test fun `moderate and critical pressure consume governor trim results`()
@Test fun `scheduler disappears after its final job reservation and record are gone`()
@Test fun `render acquisition validates session generation slot placement and record`()
@Test fun `one record rejects a second renderer until the exact lease releases`()
@Test fun `stale renderer release cannot unmount a replacement record`()
@Test fun `concurrent clear close and platform completion preserve exact ownership`()
```

Fakes must suspend at controllable gates, expose attempted counts/generations, and track
destroyed object identities. Every lifecycle test must first load at least one ad or hold
one reservation; an assertion against a session that was empty from construction is not
valid coverage.

- [ ] **Step 2: Run the tests and confirm RED**

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests '*NativeAdCoordinatorCoreTest*' \
  --no-configuration-cache
```

- [ ] **Step 3: Establish one coordinator lock owner**

Keep sessions, scheduler queues/jobs/generations, owned records, reservation owners,
inactive order, and test clock under the coordinator lock. Remove the scheduler-local
lock and duplicate `SessionHolder.active` truth. Scheduler coroutines call the platform
without a state lock, then re-enter one coordinator completion method for an atomic
decision. That method returns destruction/event effects, which run after unlocking.

The coordinator may call the governor during a short locked mutation. No governor method
calls back into the coordinator. No `platform.*` method, consumer callback, coroutine
cancellation, or `StateFlow` collector is invoked while the coordinator/governor lock is
held.

Route every `NativeAdSessionMutation` through one coordinator helper. Under the lock it
must, in order, invalidate/cancel matching generation owners, reclassify current records,
remove requested records through the exact retirement helper, and create reservation
work for new demands. The helper returns platform destruction, job cancellation, and
event effects to execute after unlocking. Do not duplicate partial mutation handling in
`updateWindow`, deactivate, expiry, close, or callback paths.

- [ ] **Step 4: Implement reservation ownership before platform loading**

For every demand, reserve with its explicit demand class and priority. Maintain a global
identity map from each reservation token to its placement scheduler, session key, slot
key, and generation. Process `NativeAdReservationDecision.retiredRecordIds` through the
exact retirement helper and process `cancelledReservations` by removing their owner,
invalidating that slot generation, and ensuring any late platform result is rejected.

Never classify a placement batch from its first entry. Either reserve one slot at a time
in ranked order or reserve only homogeneous `(demandClass, priority)` runs, then combine
the actually granted reservation/slot pairs for the platform call. A speculative slot in
the same placement queue as a visible slot remains speculative and cannot consume
above-soft-limit headroom.

Only granted reservation/slot pairs enter a batch. Before reserving, split a Google-only
placement queue into chunks of at most five; a larger configured global/session limit must
therefore produce sequential chunks such as `5 + 5 + 2`, never a platform request above
five. Sequential placements may pass the current granted chunk to the adapter, which will
perform its single-ad requests serially. Call `recordDeferred` for ungranted entries. If
the granted list is empty, do not launch a coroutine and do not call the platform. Pass
exactly `grantedPairs.size` to `platform.load` and never index ads, reservations, or
entries using the original requested size.

- [ ] **Step 5: Implement retry, timeout, and terminal batch resolution**

Serialize one active job per placement. Wrap the complete retry/backoff sequence—not
each attempt—in `withTimeoutOrNull(placement.timeoutPolicy.loadTimeout)`, using
`retryAdLoad(placement.retryPolicy, AdError::isRetryableLoadFailure)` semantics already
used by `BannerCore`/`NativePoolCore`. Recheck generation/consent before every retry.

On success, match at most one returned ad to each granted pair. Release every unmatched
reservation and publish `Failed(unfilledError)` for its slot; if the adapter supplies no
error, use one deterministic internal partial-fill `AdError`. Destroy surplus ads. On
failure/timeout/cancellation, release every reservation and resolve every matching slot
out of `Loading`. A partial success is terminal for that batch and is not immediately
retried as another multi-ad request.

For each matched ad, generate the record/ad-instance identity and call
`platform.bindEvents` outside locks before publishing readiness. Then re-enter the single
coordinator completion method to admit its canonical governor reservation and ask the
session to accept the exact generation using the already-snapshotted metadata. If binding
fails or the reservation/session is stale on re-entry, release/retire accounting, publish
the matching failure only when still current, and destroy the ad. Only an accepted
admission enters the owned-record map and scheduler accounting. Platform event callbacks
re-enter by record/ad-instance identity, verify current ownership under lock, and emit
after unlocking; late events from retired objects are dropped.

- [ ] **Step 6: Implement exact-once retirement and render lookup**

One helper removes an owned record under the coordinator lock and returns its platform
ad as a destruction effect. It also clears renderer ownership, removes scheduler record
accounting, retires governor accounting when the governor has not already done so, and
updates the session slot when applicable. Call `platform.destroy` after unlocking.

All paths use this helper: session window retirement, governor eviction, moderate/critical
trim, configured native TTL expiry, inactive TTL/LRU, close, clear, consent revocation,
rejected stale admission, and scheduler cancellation. Rendering uses atomic
`acquireForRender(sessionKey, sessionGeneration, slotKey, placement, rendererId)` and
`releaseRenderer(..., recordId, rendererId)` operations. Acquisition validates every
identity, rejects a different existing renderer, marks the record/session/governor mounted,
and returns an immutable `NativeAdRenderRecord<A>` snapshot without transferring ownership
to Compose. Release is idempotent and unmounts only the exact current
record/renderer tuple. A capacity trim cannot return a mounted record; forced lifecycle
invalidation may invalidate its renderer and retire it through the normal exact-once path.

- [ ] **Step 7: Connect session registry lifecycle**

Expose coordinator methods for `updateWindow`, `deactivateSession`, `acquireForRender`,
`releaseRenderer`, `closeSession`, `clear`, `onConsentRevoked`, and `onMemoryPressure`. The public wrapper in
Task 7 must never call `NativeAdSessionCore` directly. Every session-scoped coordinator
method accepts and validates a coordinator-issued session generation in addition to the
key, so stale wrappers cannot affect a replacement. `deactivateSession` applies the session
mutation, timestamps `inactiveOrder`, and enforces inactive TTL/LRU. Reactivation removes
the key from inactive order and publishes active state. Reusing a key validates the
identical policy; blank keys and a 65th live session fail deterministically.

Inactive TTL/LRU eviction is a real session-generation eviction, not merely ad trimming:
it retires the anchor, publishes final inactive/empty state, removes the core from the live
registry, and invalidates handles to that generation. A later `session(sameKey, policy)`
creates a fresh generation; an old handle cannot resurrect or mutate the replacement.

Expiry uses the admitted record's placement snapshot and
`cachePolicy.expirationPolicy.nativeTtl`; it removes/destroys the old record and submits
replacement demand only when the session mutation says the slot is still eligible.
Closing/clearing invalidates queued
and active placement generations, cancels coroutine jobs, releases reservations
immediately, and relies on the platform adapter's retained callback owner to destroy late
SDK arrivals.

- [ ] **Step 8: Correct the already-landed Task 1 ABI drift**

The current feature branch added Task 1's public types without updating the ABI dump and
still declares the unbounded placeholder `NativeAdSession.slotState(slotKey)`. Remove that
method to match the final Task 1 contract, regenerate the current core ABI, and inspect the
diff before any platform work:

```bash
./gradlew :admob-cmp-core:updateKotlinAbi
./gradlew :admob-cmp-core:checkKotlinAbi
```

This is an explicit correction for the already-landed branch. A fresh implementation of
the plan completes the same work in Task 1 Step 7.

- [ ] **Step 9: Run the Task 3-4 correction gate**

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests '*NativeAdGovernorTest*' \
  --tests '*NativeAdSessionCoreTest*' \
  --tests '*NativeAdCoordinatorCoreTest*' \
  --no-configuration-cache
./gradlew :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-core:checkKotlinAbi \
  --no-configuration-cache
```

Expected: PASS with no zero-count load, stuck `Loading`, orphan record, duplicate destroy,
live scheduler leak, or vacuous inactive-session test. Task 5 is blocked until this gate
passes and the Task 3-4 review is clean.

---

### Task 5: Implement the Android platform adapter in isolation

Do not change `AdManager` or delete the old pool in this task. The old path remains the
compiling application path until Task 7 performs the cross-platform public cutover atomically.

**Files:**
- Create: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdPlatform.kt`
- Create: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeMemorySignal.kt`
- Rewrite: `admob-cmp-core/src/androidHostTest/kotlin/dev/avinya/ads/NativeAdBatchHandoffTest.kt`
- Create: `admob-cmp-core/src/androidHostTest/kotlin/dev/avinya/ads/nativead/AndroidNativeAdPlatformTest.kt`

**Interfaces:**
- Consumes: `NativeAdPlatform<AndroidLoadedNativeAd>` and
  `NativeAdPlatformBatch` from Task 4.
- Produces: an independently tested Android GMA adapter and lifecycle-safe memory signal.

- [ ] **Step 1: Write the Android terminal-callback matrix**

Cover these exact cases before changing production code:

```kotlin
@Test fun `sequential count three makes three single ad requests and never count overload`()
@Test fun `sequential stops after terminal failure and returns prior ads plus unfilled error`()
@Test fun `google only accepts exact counts one through five and rejects larger before GMA`()
@Test fun `google only partial batch completes only after onAdLoadingCompleted`()
@Test fun `zero count is rejected before touching GMA`()
@Test fun `cancellation destroys accumulated ads and every later callback ad`()
@Test fun `terminal callback resumes continuation once under cancellation race`()
@Test fun `metadata is snapshotted before the platform result leaves Main`()
@Test fun `destroy dispatches once to Main and clears event callbacks`()
@Test fun `memory callback maps moderate and critical levels and unregisters once`()
```

Use fake loader façades rather than Robolectric shadows for overload selection. Fake ad
identities must record destroy count and the thread/main-dispatch path.

- [ ] **Step 2: Implement `Sequential` as genuinely sequential inventory**

For a granted `count = N`, issue at most N calls to the one-ad GMA overload, one after the
previous request reaches its terminal callback. Never use the multi-ad overload. Stop on
the first terminal failure and return already-loaded ads plus that failure as
`NativeAdPlatformBatch.unfilledError`; do not discard successful earlier ads. Cancellation
between requests stops the loop and routes accumulated/late ads to platform teardown.

This is intentionally not N parallel requests: serialized requests keep the adapter's
callback ownership and cancellation behavior deterministic and avoid a burst against one
placement.

- [ ] **Step 3: Implement opt-in `GoogleOnly` batching**

Require `placement.nativeOptions.batching == NativeAdBatching.GoogleOnly` and require
`count in 1..5`; Task 4 owns `5 + 5 + 2` chunking, so silently coercing a larger count would
strand reservations. Call the Next-Gen count overload once and do not return merely because
one ad arrived. Accumulate callbacks until `onAdLoadingCompleted`; return the accumulated
ads and the final per-ad error when fewer than requested were filled. The coordinator,
not this adapter, matches ads to reservations and destroys any surplus.

Keep `pending`, terminal/cancelled status, last error, and continuation-resumed status in
one `synchronized(callbackState)` block because GMA callbacks and coroutine cancellation
can race. Extract the pending list once at terminal transition, then snapshot metadata,
emit events, resume, or destroy only after leaving that monitor.

- [ ] **Step 4: Preserve platform ownership and metadata rules**

Create `AndroidLoadedNativeAd` only on `Dispatchers.Main.immediate`, with immutable
`AdResponseInfo` and `NativeMediaInfo` snapshots. Implement `bindEvents` on Main so
paid/native/video callbacks report the supplied coordinator-generated `adInstanceId`; do
not derive identity from a slot key. `destroy` must execute on Main, unregister callbacks,
and call `NativeAd.destroy()` exactly once even if cancellation and coordinator retirement
race.

- [ ] **Step 5: Add the Android memory-pressure source**

`AndroidNativeMemorySignal` registers one `ComponentCallbacks2` against application
context and accepts a callback instead of reaching into `AdManager`. Map UI-hidden and
ordinary background trims to `Moderate`; map running-low/critical/complete to `Critical`.
Ignore levels that do not imply memory pressure. Registration and `close()` are idempotent;
tests must verify the application does not retain a disposed manager.

- [ ] **Step 6: Run the Android adapter gate**

```bash
./gradlew :admob-cmp-core:testAndroidHostTest \
  --tests '*NativeAdBatchHandoffTest*' \
  --tests '*AndroidNativeAdPlatformTest*' \
  --no-configuration-cache
```

Expected: PASS with the old manager/pool still compiling and no public ABI change.

---

### Task 6: Implement the iOS platform adapter in isolation

As in Task 5, do not change `IosGoogleAdManager` or delete `IosNativeAdPool` yet.

**Files:**
- Create: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdPlatform.kt`
- Create: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeMemorySignal.kt`
- Create: `admob-cmp-core/src/iosTest/kotlin/dev/avinya/ads/nativead/IosNativeAdPlatformTest.kt`

**Interfaces:**
- Consumes: `NativeAdPlatform<LoadedNativeAd>` and `NativeAdPlatformBatch` from Task 4.
- Produces: an independently tested iOS GMA adapter with correct weak-delegate retention.

- [ ] **Step 1: Write the iOS loader/delegate matrix**

Add tests for:

```kotlin
@Test fun `sequential count three creates three loaders without multiple ads option`()
@Test fun `sequential partial success retains prior ads and terminal error`()
@Test fun `google only accepts exact counts one through five and rejects larger before GMA`()
@Test fun `active registry strongly retains loader and delegate until finish callback`()
@Test fun `cancellation settles coroutine but retains invalidated delegate until finish`()
@Test fun `late invalidated delegate tears down every arriving ad`()
@Test fun `finish and cancellation race resumes continuation once`()
@Test fun `metadata is snapshotted on Main before callback completion`()
@Test fun `teardown clears all ObjC callback links exactly once on Main`()
@Test fun `memory warning emits critical and observer removal is idempotent`()
```

Use the existing Objective-C façade/test seams; do not make tests depend on live ad
inventory.

- [ ] **Step 2: Implement sequential and Google-only modes**

`Sequential` creates one `GADAdLoader` at a time and omits
`GADMultipleAdsAdLoaderOptions`; after each `adLoaderDidFinishLoading`, start the next
single-ad loader until N ads are produced or a request fails. `GoogleOnly` creates one
loader, requires `count in 1..5`, assigns that exact `numberOfAds`, and waits for
`adLoaderDidFinishLoading`. Task 4, not the adapter, splits larger demand into sequential
five-ad chunks. Both modes return successful partial inventory together with one
deterministic unfilled error.

All GMA construction and callbacks run on `Dispatchers.Main.immediate`. Do not infer
Google-only safety from the ad unit id; only the explicit batching option enables it.

- [ ] **Step 3: Make cancellation safe despite weak delegates**

Keep every active loader and delegate in a strong registry until the terminal finish
callback because `GADAdLoader.delegate` is weak and has no cancellation API. Coroutine
cancellation marks the delegate invalid, settles the continuation once, and tears down
already accumulated ads, but it does not drop the registry entry. Any later ad callback
checks invalidation first and immediately tears down that ad; the finish callback finally
removes loader/delegate ownership.

- [ ] **Step 4: Centralize iOS teardown**

Snapshot response/media data before returning `LoadedNativeAd`. Implement `bindEvents` on
Main with a strongly retained native/video delegate and the supplied coordinator-generated
identity. The single Main-thread teardown helper must null the paid handler and `GADNativeAd.delegate`, detach video
delegates, clear any registered native view binding, and release Kotlin callback owners.
ARC is the final deallocator; exact-once here means all SDK/Kotlin links are broken by one
idempotent path.

- [ ] **Step 5: Add the iOS memory-warning source**

Observe `UIApplicationDidReceiveMemoryWarningNotification` once, report
`NativeMemoryPressure.Critical` through an injected callback, and remove the exact observer
token on idempotent `close()`. The signal must not capture the manager strongly after
disposal.

- [ ] **Step 6: Run the iOS adapter gate**

```bash
./gradlew :admob-cmp-core:iosSimulatorArm64Test --no-configuration-cache
```

Expected: PASS, including the new adapter suite, while the old iOS manager/pool path still
compiles.

---

### Task 7: Perform the atomic public and platform-manager cutover

This is the only task that switches application ownership. Do not leave one platform on
the pool API or expose `nativeAds` before both manager implementations are ready.

**Files:**
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdManager.kt`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/AdTelemetry.kt`
- Modify: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NoOpControllerRegistry.kt`
- Create: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativeAdManagerImpl.kt`
- Modify: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/AndroidGoogleAdManager.kt`
- Modify: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/IosGoogleAdManager.kt`
- Create: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdRenderLease.kt`
- Create: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdRenderLease.kt`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/Fakes.kt`
- Modify: `admob-cmp-core/src/commonTest/kotlin/dev/avinya/ads/NoOpAdManagerTest.kt`
- Delete: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/nativead/NativeAdToken.kt`
- Delete: `admob-cmp-core/src/commonMain/kotlin/dev/avinya/ads/internal/NativePoolCore.kt`
- Delete: `admob-cmp-core/src/androidMain/kotlin/dev/avinya/ads/nativead/AndroidNativeAdPool.kt`
- Delete: `admob-cmp-core/src/iosMain/kotlin/dev/avinya/ads/nativead/IosNativeAdPool.kt`
- Replace/delete after porting assertions: both platform `*NativePoolCharacterizationTest.kt` files
- Update: `admob-cmp-core/api/admob-cmp-core.klib.api`

**Interfaces:**
- Consumes: Tasks 1-6.
- Produces: one compiling cross-platform `NativeAdManager`, public session wrappers, and
  opt-in render leases for the Compose module.

- [ ] **Step 1: Add failing public-wrapper and manager-lifecycle tests**

Test that both platform managers create one coordinator, repeated `session(key, policy)`
returns handles to the same logical session generation and `StateFlow`, a conflicting
policy throws, public session methods delegate only through the coordinator, expired/LRU
handles cannot mutate a new generation, no-op sessions remain bounded, consent revocation
clears native ownership, manager clear is reusable, manager disposal is final, and
memory-signal registration is released. Port every still-relevant pool characterization
assertion before deleting those test files.

- [ ] **Step 2: Replace the public manager surface on all platforms together**

Replace `fun nativeAd(placement): NativeAdPool` with `val nativeAds: NativeAdManager` in
`AdManager`, Android, iOS, no-op, and all fakes in one change. Remove `NativeAdPool`,
`NoOpNativeAdPool`, `NativeAdToken`, platform peek extensions, and native pool registries.
Do not add deprecated adapters: the migration table in this plan is the compatibility
contract.

Implement one common internal `NativeAdManagerImpl<A>` plus thin `NativeAdSession` handles
around coordinator methods. Do not cache handles by arbitrary keys; the coordinator owns
the bounded session registry and returns `(sessionKey, generation, state)` identity for a
handle. Repeated manager calls may create small wrapper objects but address the same live
generation and `StateFlow`. Wrappers own no ad, reservation, scheduler, coroutine, or
independent state.

`close()` is idempotent and permanent for that generation; asking the manager for the same
key afterward creates a fresh generation. An expired/evicted/stale handle publishes final
inactive/empty state, throws deterministically on `updateWindow`, and treats `deactivate`
and `close` as idempotent no-ops; it cannot target a replacement generation. `clear()`
destroys inventory and cancels work but leaves live session definitions and the manager
reusable; a later window update may demand fresh ads.

- [ ] **Step 3: Wire process ownership, consent, and memory pressure**

Each real `AdManager` constructs exactly one governor, coordinator core, platform adapter,
and memory signal using `AdConfig.nativeAdMemoryPolicy`. Existing consent loss/invalid
configuration paths call coordinator invalidation; manager clear calls coordinator clear;
final test disposal also closes the memory signal and coroutine scope. Do not call session
cores directly from either manager.

State publication must be derived from coordinator/governor snapshots and count every
loaded record and reservation exactly once. The manager/session registries obey the 32/64
limits even when consumers retain stale wrapper references.

- [ ] **Step 4: Define an explicit single-renderer lease protocol**

Expose platform-specific APIs annotated `@InternalAdMobCmpApi` for the Compose module:

```kotlin
public interface AndroidNativeAdRenderLease {
    public val adInstanceId: String
    public val ad: NativeAd
    public fun release()
}

@InternalAdMobCmpApi
public fun NativeAdSession.acquireAndroidRenderLease(
    slotKey: String,
    placement: AdPlacement,
    rendererId: String,
): AndroidNativeAdRenderLease?
```

Define the iOS equivalent exposing `GADNativeAd`. Acquisition atomically verifies the
current record and exact placement, requires no existing different renderer, marks it
mounted, and returns a lease bound to `(session, slot, placement, recordId, rendererId)`.
`release()` is idempotent and only unmounts when all identities still match; it never
destroys the ad. A stale release must not unmount a replacement. Closing, clearing, consent
loss, or expiry invalidates an outstanding lease and destroys the owned ad through the
coordinator path; ordinary capacity eviction cannot select a mounted record.

- [ ] **Step 5: Remove unbounded per-slot observer APIs**

Do not implement `NativeAdSession.slotState(slotKey)`. Compose selects from the session's
bounded state map. Add a regression test that requesting/updating thousands of historical
slot keys leaves neither wrapper-side flows nor session entries behind.

- [ ] **Step 6: Update telemetry and ABI in this task**

Document `AdEvent.adInstanceId` as the coordinator-created identity. Preserve the field so
tests and consumers can verify stable reuse without seeing platform objects. Regenerate
the core ABI now—not in Task 11:

```bash
./gradlew :admob-cmp-core:updateKotlinAbi
./gradlew :admob-cmp-core:checkKotlinAbi
```

Review the dump manually: old pool/token/peek methods are absent; manager/session/policy
and both opt-in lease surfaces occur exactly once.

- [ ] **Step 7: Run the atomic cutover gate and stale-reference scan**

```bash
rg -n 'NativeAdPool|NativeAdToken|availableAds|nativeAd\(|NativePoolCore|peekAndroidNativeAd|peekIosNativeAd' \
  admob-cmp-core/src
./gradlew :admob-cmp-core:testAndroidHostTest \
  :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-core:checkKotlinAbi \
  --no-configuration-cache
```

Expected: the scan contains only explicit migration-test strings, and all targets pass.
Do not proceed to Compose with either platform still using pool ownership.

---

### Task 8: Add the bounded Compose feed-session and viewport adapter

**Files:**
- Create: `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/NativeAdFeedSession.kt`
- Create: `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/NativeAdViewportBinding.kt`
- Modify: `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/ui/NativeAdView.kt`
- Create/modify: `admob-cmp-compose/src/commonTest/kotlin/dev/avinya/ads/ui/NativeAdViewportBindingTest.kt`
- Create/modify: `admob-cmp-compose/src/commonTest/kotlin/dev/avinya/ads/ui/NativeAdSessionBindingTest.kt`
- Update: `admob-cmp-compose/api/admob-cmp-compose.klib.api`

**Interfaces:**
- Consumes: the final public session API from Task 7.
- Produces: one low-boilerplate feed binding and the common render contract used by Task 9.

- [ ] **Step 1: Specify viewport translation as a pure function**

Introduce an internal calculation input containing visible list indexes, total item count,
scroll direction, policy, and `slotAt(index)`. Policy distances count discovered native-ad
slots, not ordinary rows: scan backward/forward by list index from the visible range until
the requested number of ad slots is found. Call `slotAt` only for indexes in
`[0, itemCount)`, skip null/content/Paging-placeholder results, and cap each directional
scan at an internal 128 indexes per measured viewport so a malformed or ad-free million-row
feed cannot create O(feed length) work. Recalculate only when the visible index range or
direction changes, not for every pixel offset. Deduplicate by first occurrence and preserve
order:

1. Visible native slots in `visibleItemsInfo` order.
2. Prefetch-ahead slots in current scroll direction.
3. Retain-behind slots opposite the direction.
4. Truncate the combined result to `policy.maxRetainedAds` while keeping band membership.

If there is no visible native slot, the ahead scan still selects the next nearby slot.
At the top/bottom boundary or 128-index scan budget, do not wrap or duplicate; a distant ad
will become eligible as the viewport approaches it. A temporary empty layout before the
first measure emits no destructive empty window; wait for a measured viewport.

- [ ] **Step 2: Write the pure viewport test matrix**

Cover forward and reverse scrolling, ads separated by ordinary content rows, one and
multiple visible ads, no visible ad, first measure, top/bottom boundaries, the 128-index
budget, Paging nulls, duplicate keys, conflicting placements, viewport larger than the
default cap, item-count shrink, and rapid alternating windows.
Assert exact `visible`, `prefetchAhead`, and `retainBehind` ordering—not only total size.

- [ ] **Step 3: Implement `rememberNativeAdFeedSession` without restarting work**

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

Obtain `LocalAdManager.current.nativeAds.session(sessionKey, policy)` with keys
`(manager, sessionKey, policy)`. Use `rememberUpdatedState` for item count and mapping,
one `LaunchedEffect(session, listState)` with `snapshotFlow`, structural
`distinctUntilChanged`, and `collectLatest` to report windows. Derive direction from the
last measured first-visible index/offset. Do not close the session on ordinary composition
exit: `DisposableEffect(session)` calls `deactivate()` so tab switches retain the anchor.
Only the owning feature or manager explicitly closes a finished logical session.

Provide the equally simple non-list entry point for one isolated native placement:

```kotlin
@Composable
public fun rememberNativeAdSlotSession(
    sessionKey: String,
    slot: NativeAdSlot,
    policy: NativeAdSessionPolicy = NativeAdSessionPolicy(
        maxRetainedAds = 1,
        retainBehind = 0,
        prefetchAhead = 0,
    ),
): NativeAdSession
```

It reports `NativeAdWindow(visible = listOf(slot))`, uses the same
`(manager, sessionKey, policy)` identity, and deactivates rather than closes on composition
exit. It must not create a separate pool or bypass the global governor. Add lifecycle tests
for slot/placement change, recomposition, ordinary disposal, explicit close, and two
isolated sessions sharing the application limits.

- [ ] **Step 4: Replace the common `NativeAdView` contract**

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

The common contract reads `session.state`; the platform render-lease acquisition validates
that `slotKey` belongs to the exact supplied placement before exposing an ad. Loading must
occupy the same declared layout footprint as the eventual ad (or a consumer-supplied stable
footprint), then crossfade content without inserting/removing a list row. `Empty` and
`Failed` do not start loads from the view; only viewport reporting creates demand.

- [ ] **Step 5: Update Compose ABI and run the common gate**

```bash
./gradlew :admob-cmp-compose:updateKotlinAbi
./gradlew :admob-cmp-compose:checkKotlinAbi
./gradlew :admob-cmp-compose:testAndroidHostTest \
  --tests '*NativeAdViewportBindingTest*' \
  --tests '*NativeAdSessionBindingTest*' \
  --no-configuration-cache
```

Expected: PASS; the ABI contains the new feed/session view API and no old token-based
signature.

---

### Task 9: Bind Android/iOS views through render leases

**Files:**
- Modify: `admob-cmp-compose/src/androidMain/kotlin/dev/avinya/ads/ui/AndroidNativeAdView.kt`
- Modify: `admob-cmp-compose/src/iosMain/kotlin/dev/avinya/ads/ui/IosNativeAdView.kt`
- Modify: `admob-cmp-compose/src/androidMain/kotlin/dev/avinya/ads/nativead/rendering/AndroidNativeAdLayoutRenderer.kt`
- Modify: `admob-cmp-compose/src/iosMain/kotlin/dev/avinya/ads/nativead/rendering/IosNativeAdRenderer.kt`
- Create/modify: `admob-cmp-compose/src/androidHostTest/kotlin/dev/avinya/ads/ui/AndroidNativeRenderBindingTest.kt`
- Create/modify: `admob-cmp-compose/src/iosTest/kotlin/dev/avinya/ads/ui/IosNativeRenderBindingTest.kt`

**Interfaces:**
- Consumes: Task 7's single-renderer leases and Task 8's common view contract.
- Produces: real `Ready/Retained <-> Mounted` transitions without transferring ad
  ownership to composition.

- [ ] **Step 1: Write cross-module lease/render lifecycle tests**

For both platforms, cover first attach, ordinary disposal, reattach of the same record,
duplicate renderer rejection, placement mismatch rejection, stale lease release after
record replacement, capacity-eviction refusal while mounted, forced consent/close teardown
while mounted, layout-identity change, slot-key change, session deactivation, composition
cancellation, and repeated disposal. Assert record id/ad instance id, mounted state,
platform view teardown count, and platform ad teardown count separately.

Detachment must yield `Mounted -> Retained` with zero ad destroys. Coordinator retirement
must invalidate the lease and tear down the platform ad exactly once. No test may use a
mock session that bypasses the coordinator lease protocol.

- [ ] **Step 2: Implement state-driven lease acquisition**

Collect `session.state` and select `slots[slotKey]`. Show the stable loading content for
`Empty`/`Loading`, the failure slot for `Failed`, and attempt a render lease for
`Ready`/`Retained`/`Mounted`. Generate one renderer id with
`remember(session, slotKey)` for that composable instance. A second composition for the
same slot must fail closed to the loading/failure fallback; it must not steal the lease.

Keep the latest `onEvent` with `rememberUpdatedState`. Filter instance-scoped native
events by the lease's `adInstanceId`; placement id or slot key alone is insufficient after
replacement. Release the lease from `DisposableEffect(lease)` exactly once.

- [ ] **Step 3: Implement Android host-view detachment**

Build a fresh Android `NativeAdView` host for a mount and register the leased `NativeAd`.
Key host recreation by `(lease.adInstanceId, layout.identity)`: a layout change destroys
only the old host tree and binds the same leased ad to a new tree. On `AndroidView` release,
remove listeners/assets and call only the documented host-view cleanup; never call
`NativeAd.destroy()` from Compose or the layout renderer. The coordinator is the sole ad
destroyer.

All view/ad registration runs on Main. If a lease becomes invalid during recomposition,
detach the host before showing fallback content so an evicted ad cannot remain registered.

- [ ] **Step 4: Implement iOS host-view detachment**

Create a fresh `GADNativeAdView`/controller per host binding. Before releasing or replacing
it, set `nativeAd = null`, clear asset-view references and view-local delegates, then release
the controller. Keep ad-level paid/native/video delegates intact while the coordinator
retains the record; only Task 6's coordinator teardown clears those. Use the same
`(adInstanceId, layout.identity)` replacement and stale-lease rules as Android.

- [ ] **Step 5: Run automated Compose platform gates**

```bash
./gradlew :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp-compose:iosSimulatorArm64Test \
  :admob-cmp-compose:checkKotlinAbi \
  --no-configuration-cache
```

Expected: PASS with no token/peek imports in Compose.

- [ ] **Step 6: Characterize real GMA reattachment before migration**

On one Android device/emulator with working test ads and one iOS simulator/device, load a
real test ad, mount it, detach via tab navigation, and reattach the same `adInstanceId` to
a newly created host view. Verify assets render, click handling remains valid, no impression
fires while detached, and logs contain no duplicate-registration warning.

If either current GMA SDK cannot safely rebind the same native-ad object, stop here and
amend the architecture with evidence. Do not keep a hidden platform view alive and do not
silently reload; both would violate the approved lifecycle contract.

---

### Task 10: Migrate every in-repository consumer without redesigning UI

**Files:**
- Modify: `admob-cmp-compose/src/commonMain/kotlin/dev/avinya/ads/debug/tabs/FormatsTab.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/feed/FeedScreen.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/feature/article/ArticleScreen.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ui/inspector/PlacementsTab.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/domain/ad/ShowcasePlacements.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/domain/feed/FeedItem.kt`
- Modify/create: affected Showcase and debug-surface tests.

**Interfaces:**
- Consumes: Tasks 7-9.
- Produces: a buildable repository that demonstrates session retention before the separate
  Fieldnotes visual-redesign plan begins.

- [ ] **Step 1: Inventory all old consumers before editing**

Run the stale API scan across every source set, sample, debug surface, and test—not only
the expected file list:

```bash
rg -n 'NativeAdPool|NativeAdToken|availableAds|nativeAd\(|NativeAdView\(' \
  --glob '!**/build/**' --glob '!docs/superpowers/plans/**' .
```

Add every live call site found to this task. Generated API/docs output is handled in Task
11; historical plans remain unchanged.

- [ ] **Step 2: Preserve stable feed-slot identity and row geometry**

Keep `FeedItem.NativeAdSlot(slotKey)` as the source of truth; its key remains derived from
the stable neighboring content identity, never `LazyList` index, page number, or currently
loaded ad. Add the placement when producing `NativeAdSlot` for the viewport mapping.

The ad row must remain in the item model while its ad is empty/loading/failed. Give its
placeholder and loaded native layout a common minimum/aspect footprint and crossfade
inside the row; do not hide the whole row and insert it later, which would move the user's
scroll position.

- [ ] **Step 3: Migrate the Showcase feed with one logical session**

Reuse the screen's one `LazyListState`, create session key `showcase-feed`, and map current
Paging/list indexes to `FeedItem.NativeAdSlot`. Pass that same session into every native
row. The session object is owned above individual rows and deactivates when the feed leaves
composition; returning to the tab reopens the same named session and retained anchor.

Add tests proving list movement does not change slot keys, a tab leave/return causes no
second platform load for the retained anchor, and scrolling beyond the retention window is
allowed to replace an evicted slot.

- [ ] **Step 4: Migrate article inline native ads as an independent session**

Use `sessionKey = "showcase-article:${article.id}"` and one stable slot key such as
`"inline-after-paragraph-3:${article.id}"`. Bind the article's `LazyListState` through the
same viewport adapter; do not manually call preload. This session has its own default
three-record cap but shares the application-wide governor with the feed, so it cannot
steal or collide with a feed slot having the same local name.

Close the article session only when that logical article destination is permanently
discarded; ordinary temporary composition loss calls deactivate. Test feed and article
sessions together so the global soft/hard limits, not two independent pools, control total
inventory.

- [ ] **Step 5: Configure safe batching and rebuild diagnostics**

Only the official Google test native placement may opt into `GoogleOnly`; production and
unknown/mediated placements remain `Sequential`. Replace preload/acquire/release controls
in Compose `FormatsTab` and Showcase `PlacementsTab` with bounded demonstrations:
session activation, exact window contents, slot states, manager loaded/reserved counts,
deactivation, and explicit close/clear. Never expose raw platform ads or render leases.

- [ ] **Step 6: Run consumer and full source scans**

```bash
./gradlew :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp-compose:iosSimulatorArm64Test \
  :showcase:testAndroidHostTest \
  :showcase:iosSimulatorArm64Test \
  --no-configuration-cache
rg -n 'NativeAdPool|NativeAdToken|availableAds|nativeAd\(|NativePoolCore|peekAndroidNativeAd|peekIosNativeAd' \
  --glob '!**/build/**' --glob '!docs/superpowers/plans/**' .
```

Expected: tests pass; remaining scan hits are only docs/ABI scheduled for Task 11.

---

### Task 11: Rewrite documentation, invariants, migration guidance, and verify ABI

**Files:**
- Modify: `README.md`
- Modify: `admob-cmp/README.md`
- Modify: `admob-cmp/AGENTS.md`
- Modify: `admob-cmp/CLAUDE.md`
- Modify: `docs-site/src/content/docs/start/what-is-admob-cmp.mdx`
- Modify: `docs-site/src/content/docs/formats/native.mdx`
- Modify: `docs-site/src/content/docs/reference/architecture.mdx`
- Modify: `docs-site/src/content/docs/reference/troubleshooting.mdx`
- Modify: `docs-site/src/content/docs/reference/diagrams-in-words.mdx`
- Modify: `docs-site/src/content/docs/reference/changelog.mdx` if it enumerates the old API
- Modify: `docs-site/src/content/docs/project/ai-agents.mdx`
- Modify: `docs-site/src/components/diagrams/ModuleMap.astro`
- Replace: `docs-site/src/components/diagrams/NativePoolLifecycle.astro` with
  `docs-site/src/components/diagrams/NativeSessionLifecycle.astro`
- Modify: `docs-site/src/components/diagrams/descriptions.json`
- Modify: `docs-site/src/data/landing.ts`
- Verify only: `admob-cmp-core/api/admob-cmp-core.klib.api`
- Verify only: `admob-cmp-compose/api/admob-cmp-compose.klib.api`

**Interfaces:**
- Consumes: the implemented API and verified behavior, not aspirational plan language.
- Produces: one canonical usage story with no live pool/token instructions.

- [ ] **Step 1: Update the repository's authoritative engineering contracts**

Replace pool invariants in `admob-cmp/AGENTS.md` and `admob-cmp/CLAUDE.md` with the final
rules: coordinator sole ownership, one lock direction, no platform calls under lock,
loaded-plus-reserved limits, reservation-to-generation mapping, exact-once retirement,
Android synchronized callback handoff, iOS strong weak-delegate retention, Main-thread SDK
access, single renderer lease, and ABI update in the same public-change commit.

Do not weaken unrelated frozen ABI, consent, `FullScreenSlotCore`, or dispatcher invariants.

- [ ] **Step 2: Write one canonical native-session guide**

The native format guide must include copy-pasteable examples for:

- A `LazyColumn` feed using a stable feed session and stable model-owned ad slots.
- A separate article feed and `rememberNativeAdSlotSession` isolated placement sharing the
  global governor.
- Tab exit (`deactivate`) versus permanent destination disposal (`close`).
- Default active retention 3, inactive anchor 1, application soft 4/hard 6, configurable
  per-placement native TTL (one-hour default),
  and 30-minute inactive-session metadata TTL.
- Retention count versus Google multi-ad request count; why 5 is a request cap, not a memory
  target; why `Sequential` is default for mediated/unknown inventory.
- Loading/failure row geometry, memory pressure, consent invalidation, and event identity.

Include a migration section matching this plan's table and one explicit ViewTube note:
stable feed/ad-slot models survive recomposition, but the SDK session—not a serializable
ViewModel field—owns native platform objects.

- [ ] **Step 3: Align every overview, diagram, and troubleshooting page**

Update both READMEs, landing copy, “what is” page, module architecture, diagram text/alt
descriptions, and AI-agent guidance. Replace the pool lifecycle diagram with a bounded
flow showing viewport demand -> reservation -> platform load -> retained record -> render
lease -> detachment/retirement. Troubleshooting must distinguish expected reload after
eviction/TTL from incorrect reload after mere row or tab detachment.

State only behavior verified by Tasks 3-10. Do not claim that apps such as YouTube use a
specific internal architecture; describe only this SDK's tested contract.

- [ ] **Step 4: Verify—not postpone—both ABI snapshots**

Tasks 7 and 8 already regenerated their affected dumps. Run checks and inspect the diff;
if docs work reveals an API correction, make that code/API correction in a dedicated
implementation task and regenerate its dump there rather than hiding it inside docs.

```bash
./gradlew :admob-cmp-core:checkKotlinAbi
./gradlew :admob-cmp-compose:checkKotlinAbi
git diff -- admob-cmp-core/api admob-cmp-compose/api
```

- [ ] **Step 5: Scan the whole repository for stale live guidance**

```bash
rg -n 'NativeAdPool|NativeAdToken|availableAds|pool\.acquire|pool\.release|NativePoolCore|nativeAd\(' \
  --glob '!**/build/**' \
  --glob '!docs/superpowers/plans/**' \
  --glob '!docs-site/public/api/**' \
  .
```

Expected: no live source, ABI, root README, module README, or docs-site references. A
changelog may name removal only as explicitly labelled migration history.

- [ ] **Step 6: Build docs before running rendered-output tests**

```bash
./gradlew syncApiDocsToDocsSite
cd docs-site
npm ci
npm run build
npm test
npm run verify
```

Expected: Dokka synchronization succeeds; Astro builds before Vitest; tests and verify
pass without relying on stale `dist/` or generated `public/api/` files. Never commit either
generated directory.

---

### Task 12: Run the complete release gate and real lifecycle matrix

**Files:** None unless verification exposes a defect. Any defect returns to the owning
implementation task and its tests; Task 12 is not a miscellaneous-fixes bucket.

**Interfaces:**
- Consumes: Tasks 1-11.
- Produces: an evidence-backed release-readiness report for owner approval, not a PR.

- [ ] **Step 1: Start from an auditable worktree**

Record `git status --short`, branch, and HEAD. Confirm only intended plan/implementation,
test, ABI, consumer, and docs changes are present. Do not delete or overwrite unrelated
user changes. Do not bump either `VERSION_NAME`; release versioning is a separate owner
decision and commit.

- [ ] **Step 2: Run the mandatory full repository gate**

Run without `--skip-docs` because core, Compose, ABI, and docs all changed:

```bash
./scripts/release-readiness.sh
```

Expected: version lockstep, Gradle plugin, Android tests/ABI/publication metadata, Central
task graph, iOS tests/ABI, Maven Local published-consumer round trip, Xcode consumer, Dokka,
Astro build/test/verify, and final `READINESS: PASS`. If the worktree lacks
`local.properties`, supply the known Android SDK path through `ANDROID_HOME`; do not edit a
machine-specific path into the repository.

A readiness pass proves the scripted gates only. It does not replace the device matrix or
authorize a PR.

- [ ] **Step 3: Execute the Android and iOS lifecycle matrix**

Use official test ad units and `strictTestMode`. Record platform, OS, app build, session
key, slot key, and `adInstanceId` for each case:

1. First load occupies a stable placeholder and crossfades without moving list position.
2. Approaching a slot preloads it; entering the viewport does not start a duplicate load.
3. Scroll away/back within the three-record window; the same instance reattaches.
4. Switch tabs and return; one inactive anchor remains and the same instance reattaches.
5. Scroll beyond retention; the detached old record is destroyed and a later new load is
   allowed.
6. Open feed and article sessions together; local slot names do not collide and global
   loaded-plus-reserved count stays at or below six.
7. Trigger moderate pressure; speculative/detached inventory trims toward four while
   mounted records remain.
8. Trigger critical pressure; only mounted records remain.
9. Revoke consent and clear during active loads; reservations settle, late arrivals tear
   down, and no slot remains `Loading`.
10. Inject/cross the placement's native TTL; an eligible active slot replaces once, while an inactive
    or out-of-window slot does not reload.
11. Exercise `Sequential` and an official Google-only partial batch; requested count,
    filled slots, and unfilled errors match reservations.
12. Navigate/remove/recreate sessions until inactive TTL/LRU applies; real retained ads are
    destroyed and registry counts stay bounded.
13. Rotate/background/foreground and repeat detach/reattach; no hidden platform view,
    duplicate impression, duplicate renderer, or SDK registration warning appears.
14. End with clear/disposal and verify loaded ads, reservations, schedulers, sessions, and
    platform callback owners all converge to zero; every admitted ad retires exactly once.

If any physical/simulator check cannot run, mark it **not verified**. Do not infer it from
unit tests or report the task complete.

- [ ] **Step 4: Report evidence and stop for explicit approval**

Report the exact readiness command/result, every section that ran or was skipped, failures
found and the task where each was corrected, ABI status, Android/iOS matrix evidence, and
final dirty-worktree status. Then ask the owner whether to open a PR. Do not commit, push,
open a PR, tag, create a GitHub release, publish to Central, or change `release.yml` without
the corresponding explicit authorization.

---

## Acceptance Criteria

- [ ] Public pool/token APIs no longer exist.
- [ ] Consumer setup needs only a session key, stable slot mapping, placement, layout, and `LazyListState`.
- [ ] Same session and slot reuse the same ad across Compose detachment and tab switching while retained.
- [ ] Feed length cannot increase loaded native-ad count.
- [ ] Session slot maps, live-session metadata, placement schedulers, reservation ownership, and public wrappers have no consumer-keyed leak path.
- [ ] Loaded plus reserved ads never exceed six under races, partial batches, or cancellation.
- [ ] Active sessions load/retain at most three and inactive sessions retain at most one under defaults; consumers may raise both session and global policies deliberately.
- [ ] Mounted ads are never evicted.
- [ ] One granted reservation maps to one current slot generation and at most one admitted platform ad; zero grants never call GMA.
- [ ] Every partial, failed, timed-out, cancelled, cleared, or stale load resolves its reservations and leaves no slot permanently `Loading`.
- [ ] Google-only loads batch by granted deficit up to five; mediated/unknown loads are serialized single-ad requests.
- [ ] Moderate/critical memory pressure follows the locked trim policy.
- [ ] Coordinator state has one mutation lock, governor nesting has one direction, and no GMA call, callback, event, or destruction occurs while either lock is held.
- [ ] Compose owns only a single-renderer mount lease and platform host view; coordinator remains the sole platform-ad owner and ordinary detachment never destroys an ad.
- [ ] Consent invalidation, expiry, close, clear, and late callbacks destroy exactly once.
- [ ] Feed and article sessions are isolated by session identity while sharing the same global soft/hard limits.
- [ ] Core and Compose ABI dumps are regenerated in the same tasks as their public changes and only verified during documentation/final gates.
- [ ] Android and iOS tests, manual characterization, ABI validation, docs, published-consumer round trip, and Xcode consumer all pass.

## Risks and Rollback

- **Platform reattachment risk:** Official APIs document registration and destruction but do not guarantee every detach/reattach detail. Task 9 is a release blocker; do not substitute hidden attached views or silent reloads.
- **Memory risk:** Count-based limits cannot measure creative-specific media size. Platform memory signals therefore remain mandatory even with the six-ad hard limit.
- **Mediation risk:** The SDK cannot infer server-side mediation configuration. Sequential is the safe default; Google-only batching requires explicit consumer intent.
- **Session-handle risk:** Inactive TTL/LRU removes a logical session generation. Stale handles must fail deterministically and must never target a new generation with the same string key.
- **Execution isolation risk:** Continue implementation in the selected feature worktree and audit `git status` before every task; never absorb unrelated user changes from another checkout.
- **Rollback:** Revert the SDK redesign as one coordinated change, including ABI and in-repository consumer migration. Do not attempt a partial rollback that restores `NativeAdPool` while leaving session-based Compose calls.
