package dev.avinya.ads.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.avinya.ads.AdEvent

@Composable
internal fun rememberCurrentEventCallback(
    onEvent: (AdEvent) -> Unit
): (AdEvent) -> Unit {
    val current = rememberUpdatedState(onEvent)
    return remember { { event -> current.value(event) } }
}
