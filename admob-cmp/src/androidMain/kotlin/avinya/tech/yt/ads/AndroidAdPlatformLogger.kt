package avinya.tech.yt.ads

import android.util.Log

internal actual object AdPlatformLogger {
    actual fun log(level: AdLogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            AdLogLevel.Verbose -> Log.v(tag, message, throwable)
            AdLogLevel.Debug -> Log.d(tag, message, throwable)
            AdLogLevel.Info -> Log.i(tag, message, throwable)
            AdLogLevel.Warn -> Log.w(tag, message, throwable)
            AdLogLevel.Error -> Log.e(tag, message, throwable)
        }
    }
}
