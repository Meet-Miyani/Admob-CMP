@file:OptIn(dev.avinya.ads.InternalAdMobCmpApi::class)

package dev.avinya.ads.internal

import dev.avinya.ads.AdAttemptResult
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdResponseInfo
import dev.avinya.ads.nativead.NativeMediaInfo


/**
 * Platform boundary for the native-ad coordinator. The shared coordinator
 * drives loads through this interface; the actual GMA / UMP integration
 * lives in the platform implementation (Android: `GMA Next-Gen`,
 * iOS: `GMA 13.x`).
 *
 * The platform implementation reads
 * [dev.avinya.ads.nativead.NativeAdOptions.batching] to decide between
 * the single-ad and multi-ad GMA overloads; the shared coordinator never
 * infers that choice from placement or session identity because it cannot
 * see the server-side mediation configuration.
 *
 * Contract:
 * - [load] is called once per platform call by the per-placement
 *   scheduler. It must return all resolved ads, the failure, or a
 *   partial result. The [generation] is the placement-level generation
 *   in force at the time the call was issued; the platform passes it
 *   through to its callback path so the coordinator can match a late
 *   callback to the right generation.
 * - [destroy] is called for every ad the coordinator decides to drop
 *   (stale generation, partial-batch remainder, top-up failure, expiry,
 *   clear, consent revocation). The platform implementation must
 *   release all native-side resources for [ad] and never let it be
 *   re-used.
 * - [responseInfo] and [mediaInfo] are read-only accessors; the
 *   coordinator captures them at admit time and stores them on the
 *   session's slot state so Compose can render without re-querying
 *   the platform.
 */
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
