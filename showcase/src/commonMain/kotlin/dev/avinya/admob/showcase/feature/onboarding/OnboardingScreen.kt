package dev.avinya.admob.showcase.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.avinya.admob.showcase.StartupState
import dev.avinya.admob.showcase.di.LocalAppGraph

/**
 * First-launch screen. Shows the initialisation sequence as it happens.
 *
 * Deliberately narrates each step rather than showing an opaque spinner: a
 * consumer reading this app should be able to see that consent comes before
 * tracking, and tracking before the first ad request.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val graph = LocalAppGraph.current
    val viewModel: OnboardingViewModel = viewModel {
        OnboardingViewModel(graph.startup, graph.settings)
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(OnboardingIntent.Begin)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.Finished -> onFinished()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Setting up ads", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Consent is gathered first, then tracking permission, then the SDK " +
                "initialises. Requesting ads before tracking resolves would " +
                "permanently forfeit the advertising identifier.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OnboardingStep.orderedSteps().forEach { step ->
            StepRow(
                label = step.label(),
                detail = step.detail(state),
                status = step.statusFor(state),
            )
        }

        val startup = state.startup
        if (state.step == OnboardingStep.Failed && startup is StartupState.Failed) {
            Text(
                startup.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (startup.retryable) {
                    Button(onClick = { viewModel.onIntent(OnboardingIntent.Retry) }) { Text("Retry") }
                }
                // Always offered: a consent refusal or a permanent failure must
                // never trap the user here. The app works fully without ads.
                OutlinedButton(onClick = { viewModel.onIntent(OnboardingIntent.ContinueWithoutAds) }) {
                    Text("Continue without ads")
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, detail: String, status: StepStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (status) {
            StepStatus.Active -> CircularProgressIndicator(modifier = Modifier.width(18.dp))
            StepStatus.Complete -> Text("✓", style = MaterialTheme.typography.titleMedium)
            StepStatus.Skipped -> Text("–", style = MaterialTheme.typography.titleMedium)
            StepStatus.Pending -> Text("·", style = MaterialTheme.typography.titleMedium)
        }
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.width(0.dp))
}

private enum class StepStatus { Pending, Active, Complete, Skipped }

private fun OnboardingStep.label(): String = when (this) {
    OnboardingStep.Consent -> "Consent"
    OnboardingStep.Tracking -> "Tracking permission"
    OnboardingStep.Initializing -> "Initialising the SDK"
    OnboardingStep.Done -> "Ready"
    OnboardingStep.Failed -> "Failed"
}

private fun OnboardingStep.detail(state: OnboardingState): String = when (this) {
    OnboardingStep.Consent -> "UMP gathers consent where it is required"
    OnboardingStep.Tracking -> when (state.tracking) {
        // Shown, not hidden: a consumer needs to learn ATT is iOS-only.
        TrackingStepDisplay.NotApplicable -> "Not applicable on this platform"
        TrackingStepDisplay.Pending -> "Waiting for the system prompt"
        TrackingStepDisplay.Granted -> "Granted — personalised ads available"
        TrackingStepDisplay.Refused -> "Refused — non-personalised ads only"
    }
    OnboardingStep.Initializing -> "Starting Google Mobile Ads"
    OnboardingStep.Done -> "Done"
    OnboardingStep.Failed -> "Failed"
}

private fun OnboardingStep.statusFor(state: OnboardingState): StepStatus {
    if (this == OnboardingStep.Tracking &&
        state.tracking == TrackingStepDisplay.NotApplicable
    ) {
        return StepStatus.Skipped
    }
    val order = OnboardingStep.orderedSteps()
    val currentIndex = order.indexOf(state.step)
    val thisIndex = order.indexOf(this)
    return when {
        state.step == OnboardingStep.Done -> StepStatus.Complete
        currentIndex < 0 -> StepStatus.Pending
        thisIndex < currentIndex -> StepStatus.Complete
        thisIndex == currentIndex -> StepStatus.Active
        else -> StepStatus.Pending
    }
}
