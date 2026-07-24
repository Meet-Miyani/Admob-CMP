package avinya.tech.yt.ads.internal

/** Small blocking lock for non-suspending full-screen state transitions, including clear(). */
internal expect class FullScreenStateLock() {
    fun <T> withLock(block: () -> T): T
}
