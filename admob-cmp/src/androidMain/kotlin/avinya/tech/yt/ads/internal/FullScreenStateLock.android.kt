package avinya.tech.yt.ads.internal

internal actual class FullScreenStateLock actual constructor() {
    private val monitor = Any()

    actual fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
