package dev.avinya.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * An immutable, Compose-stable holder for a list of [AdPlacement]s.
 * Provides lookup by id via [AdPlacements.placement].
 */
@Immutable
public class AdPlacements(public val items: List<AdPlacement>)

/**
 * CompositionLocal providing the active [AdManager] to the composable tree.
 * Defaults to [NoOpAdManager] when not provided (all loads fail with
 * [AdError.sdkNotReady]).
 */
public val LocalAdManager: ProvidableCompositionLocal<AdManager> = staticCompositionLocalOf { NoOpAdManager }
/**
 * CompositionLocal providing the configured [AdPlacements] list.
 * Defaults to an empty list when not provided.
 */
public val LocalAdPlacements: ProvidableCompositionLocal<AdPlacements> = staticCompositionLocalOf { AdPlacements(emptyList()) }

/**
 * Looks up an enabled placement by [id] from this list.
 * @return The matching [AdPlacement] if found and enabled, or null.
 */
public fun List<AdPlacement>.placement(id: String): AdPlacement? = firstOrNull { it.id == id && it.enabled }

/**
 * Looks up an enabled placement by [id] from this [AdPlacements] wrapper.
 * @return The matching [AdPlacement] if found and enabled, or null.
 */
public fun AdPlacements.placement(id: String): AdPlacement? = items.placement(id)

/**
 * Returns the process-wide singleton [AdManager] for use in Compose.
 * On Android, resolves the instance from the application context.
 */
@Composable
public expect fun rememberAdManager(): AdManager
