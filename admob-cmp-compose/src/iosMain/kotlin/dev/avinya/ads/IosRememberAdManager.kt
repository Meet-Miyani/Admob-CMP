package dev.avinya.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
public actual fun rememberAdManager(): AdManager = remember {
    IosAdMob.manager
}
