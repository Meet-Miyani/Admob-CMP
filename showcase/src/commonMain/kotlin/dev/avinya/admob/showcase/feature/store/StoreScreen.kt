package dev.avinya.admob.showcase.feature.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.avinya.ads.LocalAdManager
import dev.avinya.admob.showcase.data.repo.PremiumArticle
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.di.LocalAppOpenSuppressor
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import dev.avinya.admob.showcase.ui.ad.runRewarded
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

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            snackbar.showSnackbar(effect.message())
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("${state.balance} coins", style = MaterialTheme.typography.headlineLarge) }

            item {
                Button(
                    enabled = state.adsEnabled && state.sdkReady &&
                        state.rewarded == RewardedUiState.Idle,
                    onClick = {
                        // launch, not inline: the presentation suspends for the
                        // ad's whole lifetime and must not gate the UI thread's
                        // effect processing.
                        scope.launch {
                            viewModel.setRewardedState(RewardedUiState.Showing)
                            val outcome = runRewarded(
                                load = { rewarded.load() },
                                show = { onReward -> rewarded.show(onRewardEarned = onReward) },
                                wallet = graph.wallet,
                                grantKey = viewModel.nextGrantKey(ShowcasePlacements.storeRewarded.id),
                            )
                            viewModel.onRewardOutcome(outcome)
                        }
                    },
                ) {
                    Text(
                        when (state.rewarded) {
                            RewardedUiState.Showing -> "Loading…"
                            else -> "Watch an ad to earn coins"
                        },
                    )
                }
            }

            item {
                OutlinedButton(
                    enabled = state.adsEnabled && state.sdkReady,
                    onClick = { viewModel.onIntent(StoreIntent.OpenOfferWall) },
                ) { Text("See today's offer") }
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

    if (state.offerWallVisible) {
        OfferWallDialog(
            onAccept = {
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
private fun PremiumRow(
    article: PremiumArticle,
    balance: Int,
    onUnlock: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    article.section.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(article.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${article.costCoins} coins",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                article.isUnlocked -> Text(
                    "Unlocked",
                    style = MaterialTheme.typography.labelLarge,
                )
                balance < article.costCoins -> TextButton(
                    onClick = {},
                    enabled = false,
                ) { Text("Not enough coins") }
                else -> Button(onClick = onUnlock) { Text("Unlock") }
            }
        }
    }
}

/**
 * Reward-interstitial intro: the user must be able to refuse before any ad
 * loads. Equal-weight buttons, not dismiss-by-tap-outside.
 */
@Composable
private fun OfferWallDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Today's offer") },
        text = {
            Text(
                "Watch a short video to earn 25 coins. You can decline — there's " +
                    "no penalty, and you can come back any time.",
            )
        },
        confirmButton = {
            Button(onClick = onAccept) { Text("Accept") }
        },
        dismissButton = {
            // Outlined so "Decline" reads as a real choice, not a cancel
            // hidden behind the X. Both buttons are equal weight.
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
