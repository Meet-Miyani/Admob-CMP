@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package avinya.tech.yt.ads.internal

import platform.Foundation.NSRecursiveLock

internal actual class FullScreenStateLock actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
