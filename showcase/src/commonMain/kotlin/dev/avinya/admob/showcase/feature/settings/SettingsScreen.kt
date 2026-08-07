package dev.avinya.admob.showcase.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import dev.avinya.admob.showcase.ui.inspector.InspectorEntryPoint
import dev.avinya.admob.showcase.ui.inspector.InspectorSheet
import dev.avinya.admob.showcase.ui.inspector.LocalInspectorPlacements
import dev.avinya.admob.showcase.ui.theme.ThemeMode
import dev.avinya.ads.AdTrackingAuthorization
import dev.avinya.ads.ConsentDebugGeography
import dev.avinya.ads.LocalAdManager

@Composable
fun SettingsScreen() {
    val adManager = LocalAdManager.current
    val graph = LocalAppGraph.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(adManager, graph.settings) }
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val inspectorEnabled by graph.settings.inspectorEnabled.collectAsState(initial = true)
    var showInspector by remember { mutableStateOf(false) }
    val placements = remember { listOf(ShowcasePlacements.appOpen) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.Notice -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    CompositionLocalProvider(LocalInspectorPlacements provides placements) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    InspectorEntryPoint(
                        title = "Settings",
                        enabled = inspectorEnabled,
                        onOpen = { showInspector = true },
                    )
                }
                item {
                    SettingsSection("SDK") {
                        LabelledValue("Status", state.sdkStatus)
                        LabelledValue("Version", state.sdkVersion ?: "unavailable")
                        LabelledValue("Adapters", state.adapters.size.toString())
                    }
                }
                item {
                    SettingsSection("Consent") {
                        LabelledValue("Status", state.consentStatus.toString())
                        LabelledValue("Can request ads", state.canRequestAds.toString())
                        LabelledValue("Privacy options", state.privacyOptions.name)

                        // Gated ONLY on the requirement status — never on
                        // ConsentStatus.Obtained. See shouldShowPrivacyOptionsButton.
                        if (shouldShowPrivacyOptionsButton(state.privacyOptions)) {
                            Button(
                                enabled = !state.busy,
                                onClick = { viewModel.onIntent(SettingsIntent.ShowPrivacyOptions) },
                            ) { Text("Manage consent") }
                        }
                    }
                }
                item {
                    SettingsSection("Consent debugging") {
                        Text(
                            "Debug geography forces UMP to behave as if the device were " +
                                "in the selected region. Applies on next launch.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        ConsentDebugGeography.entries.forEach { geography ->
                            RadioRow(
                                label = geography.name,
                                selected = state.debugGeography == geography,
                                onClick = { viewModel.onIntent(SettingsIntent.SetDebugGeography(geography)) },
                            )
                        }
                        OutlinedButton(
                            enabled = !state.busy,
                            onClick = { viewModel.onIntent(SettingsIntent.ResetConsent) },
                        ) { Text("Reset consent") }
                    }
                }
                item {
                    SettingsSection("Tracking") {
                        LabelledValue("Authorisation", state.tracking.name)
                        if (state.tracking == AdTrackingAuthorization.NotApplicable) {
                            Text(
                                "App Tracking Transparency is an iOS concept. Android " +
                                    "always reports NotApplicable.",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            Button(onClick = { viewModel.onIntent(SettingsIntent.RequestTracking) }) {
                                Text("Request tracking permission")
                            }
                        }
                    }
                }
                item {
                    SettingsSection("Diagnostics") {
                        Button(
                            enabled = !state.busy,
                            onClick = { viewModel.onIntent(SettingsIntent.OpenAdInspector) },
                        ) { Text("Open Ad Inspector") }
                    }
                }
                item {
                    SettingsSection("App") {
                        ThemeMode.entries.forEach { mode ->
                            RadioRow(
                                label = mode.name,
                                selected = state.themeMode == mode,
                                onClick = { viewModel.onIntent(SettingsIntent.SetThemeMode(mode)) },
                            )
                        }
                        SwitchRow(
                            label = "Show inspector",
                            checked = state.inspectorEnabled,
                            onCheckedChange = { viewModel.onIntent(SettingsIntent.SetInspectorEnabled(it)) },
                        )
                        SwitchRow(
                            label = "Show ads",
                            checked = state.adsEnabled,
                            onCheckedChange = { viewModel.onIntent(SettingsIntent.SetAdsEnabled(it)) },
                        )
                        Text(
                            "Turning ads off suppresses every placement locally without " +
                                "changing any SDK or consent state. The app stays fully usable.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }

    if (showInspector) {
        InspectorSheet(placements = placements, onDismiss = { showInspector = false })
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LabelledValue(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
