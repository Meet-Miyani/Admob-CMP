package dev.avinya.admob.showcase.ui.ad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.avinya.ads.AdShowResult
import dev.avinya.ads.LocalAdManager
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import dev.avinya.admob.showcase.domain.ad.SuppressionReason
import dev.avinya.admob.showcase.feature.article.ArticleEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Consumes every [ArticleEffect] for the article screen.
 *
 * Single-consumer: a `Channel.receiveAsFlow()` delivers each effect to exactly
 * one collector, so a second effects collector in the screen would split the
 * stream. The handler owns navigation, suppression reporting, and ad show
 * loading — the screen's only job is to pass the three callbacks in.
 *
 * `show()` is **not reentrant per controller**: a second call while one is on
 * screen returns `NotReady` immediately rather than queueing. The [Mutex]
 * serialises full-screen shows so a near-simultaneous [ArticleEffect.ShowInterstitial]
 * waits for the first to finish instead of racing it. `Shown` is the success
 * path and stays silent; `NotReady` and `Failed` both surface as
 * [SuppressionReason.NotReady] so the Inspector can attribute the absence.
 */
@Composable
fun AdEffectHandler(
    effects: Flow<ArticleEffect>,
    onSuppressed: (SuppressionReason) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val adManager = LocalAdManager.current
    val mutex = remember { Mutex() }
    val interstitial = remember(adManager) {
        adManager.interstitial(ShowcasePlacements.articleInterstitial)
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ArticleEffect.ShowInterstitial -> mutex.withLock {
                    interstitial.load()
                    when (val result = interstitial.show()) {
                        is AdShowResult.Shown -> Unit
                        is AdShowResult.NotReady -> onSuppressed(SuppressionReason.NotReady)
                        is AdShowResult.Failed -> onSuppressed(SuppressionReason.NotReady)
                    }
                }
                is ArticleEffect.AdSuppressed -> onSuppressed(effect.reason)
                is ArticleEffect.NavigateBack -> onNavigateBack()
            }
        }
    }
}
