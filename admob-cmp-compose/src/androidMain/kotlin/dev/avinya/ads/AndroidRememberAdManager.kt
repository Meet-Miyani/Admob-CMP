package dev.avinya.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
public actual fun rememberAdManager(): AdManager {
    val context = LocalContext.current
    return remember { AdMob.manager(context) }
}
