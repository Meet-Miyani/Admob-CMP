package dev.avinya.admob.showcase.core.time

actual object SystemClock : Clock {
    actual override fun nowMillis(): Long = System.currentTimeMillis()
}
