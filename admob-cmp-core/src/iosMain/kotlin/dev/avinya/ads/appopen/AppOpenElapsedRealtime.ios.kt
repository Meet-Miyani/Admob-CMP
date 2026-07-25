package dev.avinya.ads.appopen

import platform.posix.CLOCK_MONOTONIC_RAW
import platform.posix.clock_gettime_nsec_np
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal actual fun appOpenElapsedRealtime(): Duration =
    clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW.toUInt()).toLong().nanoseconds
