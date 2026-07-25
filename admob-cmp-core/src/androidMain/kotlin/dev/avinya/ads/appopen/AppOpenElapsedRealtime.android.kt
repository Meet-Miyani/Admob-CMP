package dev.avinya.ads.appopen

import android.os.SystemClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal actual fun appOpenElapsedRealtime(): Duration =
    SystemClock.elapsedRealtimeNanos().nanoseconds
