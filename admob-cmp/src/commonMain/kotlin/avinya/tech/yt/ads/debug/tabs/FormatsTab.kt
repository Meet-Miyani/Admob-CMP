package avinya.tech.yt.ads.debug.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import avinya.tech.yt.ads.AdLoadState
import avinya.tech.yt.ads.AdManager
import avinya.tech.yt.ads.AdPlacement
import avinya.tech.yt.ads.FullScreenAdController
import avinya.tech.yt.ads.debug.AdDebugCatalog
import avinya.tech.yt.ads.debug.ui.DebugButton
import avinya.tech.yt.ads.debug.ui.DebugCard
import avinya.tech.yt.ads.debug.ui.DebugMono
import avinya.tech.yt.ads.debug.ui.DebugLabel
import avinya.tech.yt.ads.debug.ui.DebugSectionHeader
import avinya.tech.yt.ads.debug.ui.DebugText
import avinya.tech.yt.ads.debug.ui.DebugPill
import avinya.tech.yt.ads.debug.ui.DebugTokens
import avinya.tech.yt.ads.debug.ui.DebugType
import avinya.tech.yt.ads.debug.ui.ResponseInfoView
import avinya.tech.yt.ads.debug.ui.StatusStyle
import avinya.tech.yt.ads.nativead.layout.AdTemplates
import avinya.tech.yt.ads.ui.BannerAdView
import avinya.tech.yt.ads.ui.NativeAdView
import kotlinx.coroutines.launch

/**
 * GLOBAL CONSTRAINT 1: `AdLoadState.Idle` and `AdLoadState.Loading` are `data object`s.
 * Kotlin/Native 2.3.20 miscompiles `is <data object>` on a `when`-typed local — it folds to
 * `true` on iOS only. Compare data objects with `==`, use `is` only for `Loaded` / `Failed`.
 */
private fun AdLoadState.status(): StatusStyle = when {
    this == AdLoadState.Idle -> StatusStyle.Idle
    this == AdLoadState.Loading -> StatusStyle.Loading
    this is AdLoadState.Loaded -> StatusStyle.Loaded
    else -> StatusStyle.Failed
}

@Composable
internal fun FormatsTab(catalog: AdDebugCatalog, manager: AdManager, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DebugTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(DebugTokens.SpaceMd),
    ) {
        DebugSectionHeader("Banners")
        BannerCard(
            title = "Adaptive banner",
            description = "Anchored adaptive size, no auto-refresh.",
            placement = catalog.banner,
        )
        BannerCard(
            title = "Collapsible banner",
            description = "Collapses to a slim anchor; SDK-managed 30s refresh.",
            placement = catalog.collapsibleBanner,
        )

        DebugSectionHeader("Native")
        NativeCard(catalog, manager)

        DebugSectionHeader("Full-screen")
        FullScreenCard("Interstitial", remember { manager.interstitial(catalog.interstitial) })
        FullScreenCard("Rewarded", remember { manager.rewarded(catalog.rewarded) })
        FullScreenCard("Rewarded interstitial", remember { manager.rewardedInterstitial(catalog.rewardedInterstitial) })
        FullScreenCard("App open", remember { manager.appOpen(catalog.appOpen) })
    }
}

/** Title + status pill, left-aligned title that takes the slack so the pill never wraps. */
@Composable
private fun CardHeader(title: String, pill: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DebugText(title, style = DebugType.Title, modifier = Modifier.weight(1f))
        pill()
    }
}

@Composable
private fun BannerCard(title: String, description: String, placement: AdPlacement) {
    DebugCard {
        CardHeader(title)
        DebugLabel(description)
        DebugMono(placement.id)
        BannerAdView(placement = placement, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NativeCard(catalog: AdDebugCatalog, manager: AdManager) {
    val pool = remember { manager.nativeAd(catalog.native) }
    val available by pool.availableAds.collectAsState()
    val loadState by pool.loadState.collectAsState()
    val scope = rememberCoroutineScope()

    DebugCard {
        CardHeader("Native") { DebugPill(loadState.status()) }
        DebugMono("pool available $available")
        NativeAdView(
            placement = catalog.native,
            itemKey = "debug_native_0",
            layout = AdTemplates.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm)) {
            DebugButton("Preload", onClick = { scope.launch { pool.preload() } }, modifier = Modifier.weight(1f))
            DebugButton("Clear", onClick = { pool.clear() }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FullScreenCard(title: String, controller: FullScreenAdController) {
    val state by controller.loadState.collectAsState()
    val scope = rememberCoroutineScope()
    var showWaterfall by remember { mutableStateOf(false) }
    val status = state.status()

    DebugCard {
        CardHeader(title) { DebugPill(status) }
        DebugMono(controller.placement.id)

        val failure = state as? AdLoadState.Failed
        if (failure != null) {
            DebugMono(
                text = listOfNotNull(failure.error.code?.let { "code $it" }, failure.error.message)
                    .joinToString(" · "),
                primary = true,
            )
            failure.error.responseInfo?.let { info ->
                DebugButton(
                    label = if (showWaterfall) "Hide waterfall" else "${info.adNetworkResponseInfos.size} adapters tried",
                    onClick = { showWaterfall = !showWaterfall },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showWaterfall) ResponseInfoView(info)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm)) {
            DebugButton("Load", onClick = { scope.launch { controller.load() } }, modifier = Modifier.weight(1f))
            // show() suspends for the ad's full lifetime — UI-scoped, never GlobalScope
            // (AGENTS.md hard rule 3).
            DebugButton(
                label = "Show",
                onClick = { scope.launch { controller.show() } },
                modifier = Modifier.weight(1f),
                enabled = state is AdLoadState.Loaded,
            )
            DebugButton("Clear", onClick = { controller.clear() }, modifier = Modifier.weight(1f))
        }
    }
}
