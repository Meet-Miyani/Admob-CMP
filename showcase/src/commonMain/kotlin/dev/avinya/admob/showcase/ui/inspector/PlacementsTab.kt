package dev.avinya.admob.showcase.ui.inspector

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.avinya.admob.showcase.ui.theme.AmberAdGold
import dev.avinya.admob.showcase.ui.theme.EmeraldPrimary
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdLoadState
import dev.avinya.ads.AdManager
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.AdSizePolicy
import dev.avinya.ads.BannerRefreshPolicy
import dev.avinya.ads.LocalAdManager
import dev.avinya.ads.NativeAdPool
import kotlinx.coroutines.delay
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
        contentPadding = PaddingValues(16.dp),
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = placement.id,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = placement.format.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = EmeraldPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            ConfigSection(placement)

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 2.dp),
            )

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
    CopyableUnitIdRow("Android unit", placement.adUnitIds.android)
    CopyableUnitIdRow("iOS unit", placement.adUnitIds.ios)
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
    SectionLabel("Live State")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Load status", style = MaterialTheme.typography.bodyMedium)
        AdLoadStateBadge(loadState)
    }

    if (loadState is AdLoadState.Loaded && loadState.responseInfo?.responseId != null) {
        LabelledValue("Response ID", loadState.responseInfo?.responseId ?: "—")
    } else if (loadState is AdLoadState.Failed) {
        LabelledValue("Error Code", loadState.error.code ?: "—")
        Text(
            text = loadState.error.message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (placement.format == AdFormat.Native) {
        val depth = poolDepth?.toString() ?: "?"
        val cap = maxSize?.toString() ?: "?"
        LabelledValue("Native Pool Cache", "$depth / $cap")
    }
}

@Composable
private fun AdLoadStateBadge(loadState: AdLoadState) {
    val (statusLabel, badgeColor) = when (loadState) {
        is AdLoadState.Loaded -> ("READY" to EmeraldPrimary)
        is AdLoadState.Loading -> ("LOADING" to AmberAdGold)
        is AdLoadState.Failed -> ("ERROR" to MaterialTheme.colorScheme.error)
        is AdLoadState.Idle -> ("IDLE" to MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = CircleShape,
        color = badgeColor.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = badgeColor,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CopyableUnitIdRow(label: String, unitId: String) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = unitId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                onClick = {
                    clipboardManager.setText(AnnotatedString(unitId))
                    copied = true
                },
                shape = RoundedCornerShape(6.dp),
                color = if (copied) EmeraldPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    1.dp,
                    if (copied) EmeraldPrimary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ),
                modifier = Modifier.size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(150)) togetherWith
                                fadeOut(animationSpec = tween(150))
                        },
                        label = "copyIcon",
                    ) { isCopied ->
                        if (isCopied) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Copied",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy unit ID",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }
    }
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

