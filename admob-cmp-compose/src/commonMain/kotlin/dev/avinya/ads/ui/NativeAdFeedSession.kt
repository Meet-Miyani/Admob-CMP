package dev.avinya.ads.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.nativead.NativeAdSession
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSlot
import dev.avinya.ads.nativead.NativeAdWindow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Obtains a named native-ad session and keeps it synchronized with [listState]'s measured
 * viewport. Leaving composition only deactivates the session, allowing its bounded inactive
 * anchor to survive a tab switch; the logical feed owner closes it when it is genuinely done.
 */
@Composable
public fun rememberNativeAdFeedSession(
    sessionKey: String,
    listState: LazyListState,
    itemCount: Int,
    slotAt: (index: Int) -> NativeAdSlot?,
    policy: NativeAdSessionPolicy = NativeAdSessionPolicy(),
): NativeAdSession {
    val manager = LocalAdManager.current
    val session = remember(manager, sessionKey, policy) { manager.nativeAds.session(sessionKey, policy) }
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentSlotAt by rememberUpdatedState(slotAt)

    LaunchedEffect(session, listState) {
        var previousFirstIndex: Int? = null
        var previousFirstOffset: Int? = null
        snapshotFlow {
            val layout = listState.layoutInfo
            val indexes = layout.visibleItemsInfo.map { it.index }
            val firstIndex = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            NativeAdViewportInput(indexes, firstIndex, firstOffset, currentItemCount, currentSlotAt)
        }
            .map { input ->
                val direction = when {
                    previousFirstIndex == null -> NativeAdScrollDirection.Forward
                    input.firstIndex > previousFirstIndex!! ||
                        (input.firstIndex == previousFirstIndex && input.firstOffset > previousFirstOffset!!) ->
                        NativeAdScrollDirection.Forward
                    else -> NativeAdScrollDirection.Reverse
                }
                previousFirstIndex = input.firstIndex
                previousFirstOffset = input.firstOffset
                MeasuredNativeAdViewport(input.indexes, input.itemCount, input.slotAt, direction)
            }
            .distinctUntilChanged()
            .map { viewport ->
                nativeAdWindowForViewport(
                    visibleIndexes = viewport.indexes,
                    itemCount = viewport.itemCount,
                    direction = viewport.direction,
                    policy = policy,
                    slotAt = viewport.slotAt,
                )
            }
            .filterNotNull()
            .collect(session::updateWindow)
    }

    DisposableEffect(session) {
        onDispose(session::deactivate)
    }
    return session
}

/**
 * Creates a one-slot session for an inline native placement that is not part of a lazy list.
 * It uses the same manager and admission governor as feed sessions.
 */
@Composable
public fun rememberNativeAdSlotSession(
    sessionKey: String,
    slot: NativeAdSlot,
    policy: NativeAdSessionPolicy = NativeAdSessionPolicy(
        maxRetainedAds = 1,
        retainBehind = 0,
        prefetchAhead = 0,
    ),
): NativeAdSession {
    val manager = LocalAdManager.current
    val session = remember(manager, sessionKey, policy) { manager.nativeAds.session(sessionKey, policy) }
    val binding = remember(session) { NativeAdSlotSessionBinding(session) }

    LaunchedEffect(binding, slot) { binding.update(slot) }
    DisposableEffect(session) {
        onDispose(binding::deactivate)
    }
    return session
}

/** Testable part of the one-slot effect; production uses it from [rememberNativeAdSlotSession]. */
internal class NativeAdSlotSessionBinding(private val session: NativeAdSession) {
    fun update(slot: NativeAdSlot) {
        session.updateWindow(NativeAdWindow(visible = listOf(slot)))
    }

    fun deactivate() {
        session.deactivate()
    }
}

private data class MeasuredNativeAdViewport(
    val indexes: List<Int>,
    val itemCount: Int,
    val slotAt: (Int) -> NativeAdSlot?,
    val direction: NativeAdScrollDirection,
)

private data class NativeAdViewportInput(
    val indexes: List<Int>,
    val firstIndex: Int,
    val firstOffset: Int,
    val itemCount: Int,
    val slotAt: (Int) -> NativeAdSlot?,
)
