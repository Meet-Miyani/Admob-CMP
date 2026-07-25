package dev.avinya.ads.appopen

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import android.os.Handler
import android.os.Looper

internal actual fun appForegroundState(): Flow<Boolean> = callbackFlow {
    val owner = ProcessLifecycleOwner.get()
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> trySend(true)
            Lifecycle.Event.ON_STOP -> trySend(false)
            else -> Unit
        }
    }
    // Lifecycle observers must be (un)registered on the main thread regardless of
    // which dispatcher collects this flow.
    withContext(Dispatchers.Main.immediate) { owner.lifecycle.addObserver(observer) }
    awaitClose {
        Handler(Looper.getMainLooper()).post { owner.lifecycle.removeObserver(observer) }
    }
}

internal actual suspend fun isAppInForeground(): Boolean =
    withContext(Dispatchers.Main.immediate) {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
