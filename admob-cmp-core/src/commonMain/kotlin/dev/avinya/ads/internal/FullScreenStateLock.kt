package dev.avinya.ads.internal

/** Small blocking lock for non-suspending full-screen state transitions, including clear(). */
public expect class FullScreenStateLock() {
    public fun <T> withLock(block: () -> T): T
}
