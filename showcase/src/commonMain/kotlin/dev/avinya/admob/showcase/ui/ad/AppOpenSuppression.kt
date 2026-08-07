package dev.avinya.admob.showcase.ui.ad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lets a flow declare that an app-open ad must not appear over it.
 *
 * Depth-counted rather than boolean: two overlapping flows must not
 * un-suppress each other on the way out.
 *
 * Phase 6 binds [isBlocked] to `AppOpenAdCoordinator.isBlocked`.
 */
class AppOpenSuppressor {
    private var depth by mutableStateOf(0)

    val isBlocked: Boolean get() = depth > 0

    fun enter() { depth++ }

    fun exit() { depth = (depth - 1).coerceAtLeast(0) }
}

/** Runs [block] with app-open ads suppressed, restoring state even on failure. */
suspend fun <T> AppOpenSuppressor.suppressing(block: suspend () -> T): T {
    enter()
    return try {
        block()
    } finally {
        // A suppression that leaks silently disables app-open ads for the rest
        // of the session, so the restore must survive an exception.
        exit()
    }
}
