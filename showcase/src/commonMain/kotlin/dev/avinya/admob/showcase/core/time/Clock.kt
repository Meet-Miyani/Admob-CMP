package dev.avinya.admob.showcase.core.time

/**
 * Injected time source.
 *
 * Exists so time-dependent rules — notably `AdPolicy`'s frequency caps and
 * cooldowns in Phase 4 — are testable without kotlinx-datetime, which is not
 * an approved dependency.
 */
interface Clock {
    fun nowMillis(): Long
}

/** Wall-clock time since the Unix epoch. The production binding. */
expect object SystemClock : Clock {
    override fun nowMillis(): Long
}
