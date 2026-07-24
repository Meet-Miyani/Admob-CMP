package avinya.tech.yt.ads

internal enum class AdLogLevel { Verbose, Debug, Info, Warn, Error }

internal expect object AdPlatformLogger {
    fun log(level: AdLogLevel, tag: String, message: String, throwable: Throwable?)
}

public object AdLogger {
    private const val TAG = "AdMobCMP"

    public fun v(message: String) { AdPlatformLogger.log(AdLogLevel.Verbose, TAG, message, null) }
    public fun d(message: String) { AdPlatformLogger.log(AdLogLevel.Debug, TAG, message, null) }
    public fun i(message: String) { AdPlatformLogger.log(AdLogLevel.Info, TAG, message, null) }
    public fun w(message: String) { AdPlatformLogger.log(AdLogLevel.Warn, TAG, message, null) }
    public fun w(message: String, throwable: Throwable?) { AdPlatformLogger.log(AdLogLevel.Warn, TAG, message, throwable) }
    public fun e(message: String) { AdPlatformLogger.log(AdLogLevel.Error, TAG, message, null) }
    public fun e(message: String, throwable: Throwable?) { AdPlatformLogger.log(AdLogLevel.Error, TAG, message, throwable) }
}
