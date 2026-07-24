@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package avinya.tech.yt.ads.internal

import platform.Foundation.NSRecursiveLock

public actual class FullScreenStateLock public actual constructor() {
    private val lock = NSRecursiveLock()

    public actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
