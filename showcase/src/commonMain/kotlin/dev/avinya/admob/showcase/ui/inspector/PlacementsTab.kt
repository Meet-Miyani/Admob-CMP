package dev.avinya.admob.showcase.ui.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdLoadState
import dev.avinya.ads.AdManager
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.BannerRefreshPolicy
import dev.avinya.ads.AdSizePolicy
import dev.avinya.ads.NativeAdPool
import dev.avinya.ads.LocalAdManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Placements tab: per-placement config and live load state for the placements
 * the surrounding screen advertises through [LocalInspectorPlacements].
 *
 * Reads controllers from [LocalAdManager] lazily — a controller created here
 * is the same one the screen's own `BannerAdView` / `NativeAdView` is bound
 * to, so the rendered state is the state on screen, not a freshly built one.
 */
@Composable
fun PlacementsTab(placements: List<AdPlacement>, modifier: Modifier = Modifier) {
    val manager = LocalAdManager.current
    if (placements.isEmpty()) {
        EmptyMessage("This screen has no ad placements.", modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(placements, key = { it.id }) { placement ->
            PlacementsCard(placement = placement, manager = manager)
        }
    }
}

@Composable
private fun PlacementsCard(placement: AdPlacement, manager: AdManager) {
    val loadStateFlow = rememberLoadState(placement, manager)
    val pool = rememberNativePool(placement, manager)
    val loadState by loadStateFlow.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(placement.id, style = MaterialTheme.typography.titleMedium)
            Text(placement.format.name, style = MaterialTheme.typography.labelSmall)

            ConfigSection(placement)
            LiveSection(
                placement = placement,
                loadState = loadState,
                poolDepth = pool?.availableAds?.collectAsState()?.value,
                maxSize = if (placement.format == AdFormat.Native) placement.cachePolicy.maxSize else null,
            )
        }
    }
}

@Composable
private fun ConfigSection(placement: AdPlacement) {
    SectionLabel("Config")
    LabelledValue("Android unit", placement.adUnitIds.android)
    LabelledValue("iOS unit", placement.adUnitIds.ios)
    when (placement.format) {
        AdFormat.Banner -> {
            LabelledValue("Size", placement.bannerSizePolicy.label())
            LabelledValue("Refresh", placement.bannerRefreshPolicy.label())
        }
        AdFormat.Native -> {
            LabelledValue("Cache maxSize", placement.cachePolicy.maxSize.toString())
        }
        AdFormat.Interstitial,
        AdFormat.Rewarded,
        AdFormat.RewardedInterstitial,
        AdFormat.AppOpen,
        -> Unit
    }
}

@Composable
private fun LiveSection(
    placement: AdPlacement,
    loadState: AdLoadState,
    poolDepth: Int?,
    maxSize: Int?,
) {
    SectionLabel("Live")
    LabelledValue("Load state", loadState.label())
    if (placement.format == AdFormat.Native) {
        val depth = poolDepth?.toString() ?: "?"
        val cap = maxSize?.toString() ?: "?"
        LabelledValue("Cache", "$depth / $cap")
    } else {
        LabelledValue("Cache", "n/a")
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun rememberLoadState(placement: AdPlacement, manager: AdManager): StateFlow<AdLoadState> =
    remember(placement.id, manager) {
        when (placement.format) {
            AdFormat.Banner -> manager.banner(placement).loadState
            AdFormat.Native -> manager.nativeAd(placement).loadState
            AdFormat.Interstitial -> manager.interstitial(placement).loadState
            AdFormat.Rewarded -> manager.rewarded(placement).loadState
            AdFormat.RewardedInterstitial -> manager.rewardedInterstitial(placement).loadState
            AdFormat.AppOpen -> manager.appOpen(placement).loadState
        }
    }

@Composable
private fun rememberNativePool(placement: AdPlacement, manager: AdManager): NativeAdPool? =
    remember(placement.id, manager) {
        if (placement.format == AdFormat.Native) manager.nativeAd(placement) else null
    }

private fun AdLoadState.label(): String = when (this) {
    is AdLoadState.Idle -> "Idle"
    is AdLoadState.Loading -> "Loading"
    is AdLoadState.Loaded -> responseInfo?.responseId?.let { "Loaded ($it)" } ?: "Loaded"
    is AdLoadState.Failed -> "Failed ${error.code ?: "—"}: ${error.message}"
}

private fun AdSizePolicy.label(): String = when (this) {
    is AdSizePolicy.LargeAnchoredAdaptive -> "LargeAnchoredAdaptive" +
        (collapsible?.let { " (collapsible=${it.name})" } ?: "")
    is AdSizePolicy.InlineAdaptive -> "InlineAdaptive" +
        (maxHeightDp?.let { " (maxHeight=${it}dp)" } ?: "")
    is AdSizePolicy.Fixed -> "Fixed ${widthDp}x${heightDp}dp"
    is AdSizePolicy.Fluid -> "Fluid"
}

private fun BannerRefreshPolicy.label(): String = when (this) {
    is BannerRefreshPolicy.AdServerManaged -> "AdServerManaged"
    is BannerRefreshPolicy.SdkManaged -> "SdkManaged (${interval.inWholeSeconds}s)"
    is BannerRefreshPolicy.Manual -> "Manual"
}
