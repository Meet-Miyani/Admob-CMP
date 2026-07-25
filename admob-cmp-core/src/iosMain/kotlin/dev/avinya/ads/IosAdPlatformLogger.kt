package dev.avinya.ads

import platform.Foundation.NSLog

internal actual object AdPlatformLogger {
    actual fun log(level: AdLogLevel, tag: String, message: String, throwable: Throwable?) {
        val suffix = throwable?.message?.let { " :: $it" }.orEmpty()
        NSLog("[$tag] ${level.label}: $message$suffix")
    }
}

private val AdLogLevel.label: String
    get() = when (this) {
        AdLogLevel.Verbose -> "V"
        AdLogLevel.Debug -> "D"
        AdLogLevel.Info -> "I"
        AdLogLevel.Warn -> "W"
        AdLogLevel.Error -> "E"
    }
