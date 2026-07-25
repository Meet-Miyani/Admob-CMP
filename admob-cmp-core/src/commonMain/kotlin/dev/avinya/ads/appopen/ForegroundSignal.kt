package dev.avinya.ads.appopen

import kotlinx.coroutines.flow.Flow

internal expect fun appForegroundState(): Flow<Boolean>

/**
 * Point-in-time check of whether the app is currently in the foreground. Unlike
 * [appForegroundState] (which only emits on transitions), this reflects the
 * current state — used to avoid showing a cold-start app-open ad when the app was
 * launched into the background.
 *
 * Suspend because both actuals read a main-thread-only platform API
 * (UIApplication.applicationState / ProcessLifecycleOwner) and hop internally.
 * Callers may invoke it from any dispatcher.
 */
internal expect suspend fun isAppInForeground(): Boolean
