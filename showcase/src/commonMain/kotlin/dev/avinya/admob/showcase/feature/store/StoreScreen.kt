package dev.avinya.admob.showcase.feature.store

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.avinya.ads.LocalAdManager
import dev.avinya.admob.showcase.data.repo.PremiumArticle
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.di.LocalAppOpenSuppressor
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import dev.avinya.admob.showcase.ui.ad.runRewarded
import dev.avinya.admob.showcase.ui.inspector.InspectorEntryPoint
import dev.avinya.admob.showcase.ui.inspector.InspectorSheet
import dev.avinya.admob.showcase.ui.inspector.LocalInspectorPlacements
import dev.avinya.admob.showcase.ui.theme.EmeraldPrimary
import kotlinx.coroutines.launch

@Composable
fun StoreScreen() {
    val adManager = LocalAdManager.current
    val graph = LocalAppGraph.current
    val suppressor = LocalAppOpenSuppressor.current
    val sessionId = rememberSessionId(graph.clock)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val viewModel: StoreViewModel = viewModel {
        StoreViewModel(
            graph.wallet, graph.articles, suppressor, sessionId, graph.settings, adManager,
        )
    }
    val state by viewModel.state.collectAsState()

    val rewarded = remember(adManager) { adManager.rewarded(ShowcasePlacements.storeRewarded) }
    val offerWall = remember(adManager) {
        adManager.rewardedInterstitial(ShowcasePlacements.storeRewardedInterstitial)
    }

    val inspectorEnabled by graph.settings.inspectorEnabled.collectAsState(initial = true)
    var showInspector by remember { mutableStateOf(false) }
    val placements = remember {
        listOf(ShowcasePlacements.storeRewarded, ShowcasePlacements.storeRewardedInterstitial)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            snackbar.showSnackbar(effect.message())
        }
    }

    CompositionLocalProvider(LocalInspectorPlacements provides placements) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    InspectorEntryPoint(
                        title = "Store",
                        enabled = inspectorEnabled,
                        onOpen = { showInspector = true },
                    )
                }

                // Glass Balance Card
                item {
                    BalanceCard(balance = state.balance)
                }

                // Reward & Suppression Cards
                item {
                    Text(
                        "Coin Boosters & Offers",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                item {
                    AdSuppressionCard(
                        title = "Watch Ad to Earn Coins",
                        description = "Watch a short video ad to earn 25 coins for unlocking premium articles.",
                        icon = Icons.Rounded.Bolt,
                        buttonText = when (state.rewarded) {
                            RewardedUiState.Showing -> "Loading…"
                            else -> "Earn Coins"
                        },
                        enabled = state.adsEnabled && state.sdkReady && state.rewarded == RewardedUiState.Idle,
                        onClick = {
                            viewModel.setRewardedState(RewardedUiState.Showing)
                            scope.launch {
                                val outcome = runRewarded(
                                    load = { rewarded.load() },
                                    show = { onReward -> rewarded.show(onRewardEarned = onReward) },
                                    wallet = graph.wallet,
                                    grantKey = viewModel.nextGrantKey(ShowcasePlacements.storeRewarded.id),
                                )
                                viewModel.onRewardOutcome(outcome)
                            }
                        },
                    )
                }

                item {
                    AdSuppressionCard(
                        title = "Special Offer Pass",
                        description = "View today's special offer wall for bonus coin rewards and ad suppression.",
                        icon = Icons.Rounded.Shield,
                        buttonText = "See Today's Offer",
                        enabled = state.adsEnabled && state.sdkReady,
                        onClick = { viewModel.onIntent(StoreIntent.OpenOfferWall) },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Premium Articles",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                items(state.premium, key = { it.id }) { article ->
                    PremiumRow(
                        article = article,
                        balance = state.balance,
                        onUnlock = { viewModel.onIntent(StoreIntent.Unlock(article)) },
                    )
                }
            }
        }
    }

    if (showInspector) {
        InspectorSheet(placements = placements, onDismiss = { showInspector = false })
    }

    if (state.offerWallVisible) {
        OfferWallDialog(
            onAccept = {
                viewModel.setRewardedState(RewardedUiState.Showing)
                viewModel.onIntent(StoreIntent.AcceptOfferWall)
                scope.launch {
                    val outcome = runRewarded(
                        load = { offerWall.load() },
                        show = { onReward -> offerWall.show(onRewardEarned = onReward) },
                        wallet = graph.wallet,
                        grantKey = viewModel.nextGrantKey(
                            ShowcasePlacements.storeRewardedInterstitial.id,
                        ),
                    )
                    viewModel.onRewardOutcome(outcome)
                }
            },
            onDecline = { viewModel.onIntent(StoreIntent.DeclineOfferWall) },
        )
    }
}

@Composable
private fun rememberSessionId(clock: dev.avinya.admob.showcase.core.time.Clock): String =
    remember { clock.nowMillis().toString() }

@Composable
private fun BalanceCard(balance: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "BALANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$balance Coins",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun AdSuppressionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmeraldPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onClick,
                    enabled = enabled,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(buttonText, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun PremiumRow(
    article: PremiumArticle,
    balance: Int,
    onUnlock: () -> Unit,
) {
    val featureIcon = when {
        article.section.lowercase().contains("sdk") -> Icons.Rounded.Shield
        article.section.lowercase().contains("tech") -> Icons.Rounded.Bolt
        else -> Icons.Rounded.Star
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = featureIcon,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = EmeraldPrimary.copy(alpha = 0.12f),
                ) {
                    Text(
                        article.section.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${article.costCoins} coins required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                article.isUnlocked -> Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldPrimary.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Unlocked",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldPrimary,
                        )
                    }
                }
                balance < article.costCoins -> TextButton(
                    onClick = {},
                    enabled = false,
                ) { Text("Need coins", style = MaterialTheme.typography.labelSmall) }
                else -> Button(
                    onClick = onUnlock,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.Black,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Text("Unlock")
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferWallDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Today's Offer") },
        text = {
            Text(
                "Watch a short video to earn 25 coins. You can decline — there's " +
                    "no penalty, and you can come back any time.",
            )
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
            ) { Text("Accept") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDecline) { Text("Decline") }
        },
    )
}

private fun StoreEffect.message(): String = when (this) {
    is StoreEffect.RewardResult -> when (val outcome = outcome) {
        is dev.avinya.admob.showcase.ui.ad.RewardOutcome.Earned ->
            "You earned ${outcome.coins} coins"
        dev.avinya.admob.showcase.ui.ad.RewardOutcome.AlreadyGranted ->
            "Reward already claimed"
        dev.avinya.admob.showcase.ui.ad.RewardOutcome.DismissedWithoutReward ->
            "You dismissed before earning — no coins awarded"
        dev.avinya.admob.showcase.ui.ad.RewardOutcome.NotReady ->
            "Ad is loading, try again in a moment"
        is dev.avinya.admob.showcase.ui.ad.RewardOutcome.Failed ->
            "Ad failed: ${outcome.message}"
    }
    is StoreEffect.Unlocked -> "Unlocked: $title"
    is StoreEffect.NeedMoreCoins -> "You need $shortfall more coins"
}

