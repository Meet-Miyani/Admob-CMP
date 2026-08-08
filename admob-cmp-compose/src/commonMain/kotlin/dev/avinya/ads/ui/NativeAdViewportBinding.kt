package dev.avinya.ads.ui

import dev.avinya.ads.AdPlacement
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSlot
import dev.avinya.ads.nativead.NativeAdWindow

/** Direction of travel used to rank native-ad slots adjacent to a measured viewport. */
internal enum class NativeAdScrollDirection { Forward, Reverse }

private const val viewportScanBudget = 128

/**
 * Translates a measured Lazy list viewport into the bounded native-ad bands consumed by a
 * session. It deliberately knows only list indexes: consumers retain ownership of the feed
 * model through [slotAt], and a long content-only range cannot make this scan walk the feed.
 */
internal fun nativeAdWindowForViewport(
    visibleIndexes: List<Int>,
    itemCount: Int,
    direction: NativeAdScrollDirection,
    policy: NativeAdSessionPolicy,
    slotAt: (Int) -> NativeAdSlot?,
): NativeAdWindow? {
    // An empty measurement is transient while a LazyList first lays out, but an empty feed is
    // authoritative: publish it so the session retires any previous visible demand.
    if (itemCount <= 0) return NativeAdWindow(visible = emptyList())
    if (visibleIndexes.isEmpty()) return null

    val visible = visibleIndexes
        .filter { it in 0 until itemCount }
        .mapNotNull(slotAt)

    // During a Paging shrink Compose can briefly report the prior measured indexes. Clamp the
    // scan anchors to the new range, but never call slotAt for those stale indexes.
    val firstVisible = (visibleIndexes.minOrNull() ?: return null).coerceIn(0, itemCount - 1)
    val lastVisible = (visibleIndexes.maxOrNull() ?: return null).coerceIn(0, itemCount - 1)
    val aheadStep = if (direction == NativeAdScrollDirection.Forward) 1 else -1
    val behindStep = -aheadStep
    val aheadStart = if (aheadStep > 0) lastVisible + 1 else firstVisible - 1
    val behindStart = if (behindStep > 0) lastVisible + 1 else firstVisible - 1

    fun scan(start: Int, step: Int, wanted: Int): List<NativeAdSlot> {
        if (wanted == 0) return emptyList()
        val found = mutableListOf<NativeAdSlot>()
        var index = start
        var inspected = 0
        while (index in 0 until itemCount && inspected < viewportScanBudget && found.size < wanted) {
            slotAt(index)?.let(found::add)
            index += step
            inspected++
        }
        return found
    }

    val ahead = scan(aheadStart, aheadStep, policy.prefetchAhead)
    val behind = scan(behindStart, behindStep, policy.retainBehind)

    val placementsByKey = mutableMapOf<String, AdPlacement>()
    fun unique(slots: List<NativeAdSlot>, remaining: Int): List<NativeAdSlot> = buildList {
        for (slot in slots) {
            val priorPlacement = placementsByKey[slot.key]
            require(priorPlacement == null || priorPlacement == slot.placement) {
                "Native viewport maps slot key '${slot.key}' to conflicting placements " +
                    "'${priorPlacement?.id}' and '${slot.placement.id}'."
            }
            if (priorPlacement == null) {
                placementsByKey[slot.key] = slot.placement
                if (size < remaining) add(slot)
            }
        }
    }

    var remaining = policy.maxRetainedAds
    val uniqueVisible = unique(visible, remaining)
    remaining -= uniqueVisible.size
    val uniqueAhead = unique(ahead, remaining)
    remaining -= uniqueAhead.size
    val uniqueBehind = unique(behind, remaining)

    return NativeAdWindow(
        visible = uniqueVisible,
        prefetchAhead = uniqueAhead,
        retainBehind = uniqueBehind,
    )
}
