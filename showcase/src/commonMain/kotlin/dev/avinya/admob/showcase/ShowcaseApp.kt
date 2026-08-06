package dev.avinya.admob.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Root of the showcase app and the only public composable `:showcase` exposes.
 *
 * `shared` calls this from its `PlatformAdDemo` actual on Android and iOS;
 * desktop and web keep rendering `UnsupportedAdPlatform()`.
 */
@Composable
fun ShowcaseApp() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Showcase app — foundation")
        }
    }
}
