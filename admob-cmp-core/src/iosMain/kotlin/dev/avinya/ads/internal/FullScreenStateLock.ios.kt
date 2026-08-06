@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.avinya.ads.internal

import dev.avinya.ads.InternalAdMobCmpApi
import platform.Foundation.NSRecursiveLock

@InternalAdMobCmpApi
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
