package dev.avinya.ads.internal

import dev.avinya.ads.InternalAdMobCmpApi

/**
 * Small blocking lock for non-suspending full-screen state transitions, including clear().
 *
 * Public only because `admob-cmp-compose` consumes it across the module boundary
 * (`AdDebugRecorder`), which `internal` cannot express — not because it is a consumer API.
 * It carries no ad semantics and its shape may change in any release. The ABI is frozen
 * (invariant #12), so it cannot simply be withdrawn; [InternalAdMobCmpApi] is the additive,
 * non-breaking way to stop it reading as supported surface.
 */
@InternalAdMobCmpApi
public expect class FullScreenStateLock() {
    public fun <T> withLock(block: () -> T): T
}
