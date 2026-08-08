package dev.avinya.admob.showcase.core.time

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual object SystemClock : Clock {
    actual override fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
}
