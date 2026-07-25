package dev.avinya.admob.cmp.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import avinya.tech.yt.ads.ConsentMode
import avinya.tech.yt.ads.LocalAdManager
import avinya.tech.yt.ads.debug.AdDebugCatalog
import avinya.tech.yt.ads.debug.AdDebugScreen
import avinya.tech.yt.ads.rememberAdManager

@Composable
internal actual fun PlatformAdDemo() {
    val manager = rememberAdManager()
    val status by manager.status.collectAsState()
    var retryGeneration by remember { mutableIntStateOf(0) }
    val config = remember(manager) {
        demoTestAdConfig(
            trackingHook = TrackingAuthorizationHook {
                manager.tracking.requestAuthorization()
            },
        )
    }

    LaunchedEffect(manager, config, retryGeneration) {
        manager.initialize(
            config = config,
            consentMode = ConsentMode.GatherBeforeInitialize,
        )
    }

    when (val uiState = status.toDemoAdStartupUiState()) {
        DemoAdStartupUiState.Starting ->
            DemoStartupMessage(
                message = "Preparing consent and Google Mobile Ads…",
                showProgress = true,
            )
        DemoAdStartupUiState.Ready ->
            CompositionLocalProvider(LocalAdManager provides manager) {
                AdDebugScreen(
                    catalog = AdDebugCatalog.Test,
                    manager = manager,
                    onBack = {},
                )
            }
        DemoAdStartupUiState.ConsentRequired ->
            DemoStartupMessage(
                message = "Consent is still required before ads can be requested.",
                actionLabel = "Retry consent",
                onAction = { retryGeneration += 1 },
            )
        is DemoAdStartupUiState.Failed ->
            DemoStartupMessage(
                message = uiState.message,
                actionLabel = if (uiState.retryable) "Retry initialization" else "Try again",
                onAction = { retryGeneration += 1 },
            )
    }
}

@Composable
private fun DemoStartupMessage(
    message: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showProgress) {
                CircularProgressIndicator()
            }
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (actionLabel != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
