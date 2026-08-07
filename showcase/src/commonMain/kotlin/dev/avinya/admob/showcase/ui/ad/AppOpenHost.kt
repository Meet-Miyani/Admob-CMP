package dev.avinya.admob.showcase.ui.ad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.appopen.AppOpenAdCoordinator
import dev.avinya.ads.appopen.AppOpenConfig
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Hosts the process-wide [AppOpenAdCoordinator] and binds its [AppOpenAdCoordinator.isBlocked]
 * to [suppressor], so app-open ads cannot appear over onboarding or a coin unlock.
 *
 * The `coordinator.isBlocked` binding is a re-publish on every change, not a one-shot:
 * a flow that ends while backgrounded must not leave the app permanently suppressed.
 */
@Composable
fun AppOpenHost(suppressor: AppOpenSuppressor, content: @Composable () -> Unit) {
    val adManager = LocalAdManager.current
    val coordinator = remember(adManager) {
        AppOpenAdCoordinator(
            manager = adManager,
            controller = adManager.appOpen(ShowcasePlacements.appOpen),
            config = AppOpenConfig(
                minBackgroundDuration = 4.seconds,
                cooldownBetweenShows = 4.hours,
            ),
        )
    }

    LaunchedEffect(coordinator) { coordinator.start(this) }

    LaunchedEffect(coordinator, suppressor.isBlocked) {
        coordinator.isBlocked = suppressor.isBlocked
    }

    content()
}
