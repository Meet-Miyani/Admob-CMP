package dev.avinya.admob.cmp.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal expect fun PlatformAdDemo()

@Composable
internal fun UnsupportedAdPlatform() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "The AdMob demo is available on Android and iOS.",
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
