package dev.avinya.ads.internal

public actual class FullScreenStateLock public actual constructor() {
    private val monitor = Any()

    public actual fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
