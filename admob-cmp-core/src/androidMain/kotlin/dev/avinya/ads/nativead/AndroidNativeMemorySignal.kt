package dev.avinya.ads.nativead

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import dev.avinya.ads.internal.NativeMemoryPressure

/**
 * Application-scoped source of native-ad memory pressure.  It deliberately owns no
 * manager reference: callers supply a callback and must close the signal with the
 * manager that created it.
 */
internal class AndroidNativeMemorySignal(
    context: Context,
    private val onPressure: (NativeMemoryPressure) -> Unit,
) : ComponentCallbacks2, AutoCloseable {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private var registered = false
    private var closed = false

    init {
        synchronized(lock) {
            applicationContext.registerComponentCallbacks(this)
            registered = true
        }
    }

    override fun onTrimMemory(level: Int) {
        val pressure = memoryPressureFor(level) ?: return
        synchronized(lock) {
            if (closed) return
        }
        onPressure(pressure)
    }

    @Suppress("DEPRECATION")
    @Deprecated("ComponentCallbacks2.onLowMemory is deprecated by Android.")
    override fun onLowMemory() = onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun close() {
        val unregister = synchronized(lock) {
            if (closed) false else {
                closed = true
                registered.also { registered = false }
            }
        }
        if (unregister) applicationContext.unregisterComponentCallbacks(this)
    }

    internal companion object {
        @Suppress("DEPRECATION")
        fun memoryPressureFor(level: Int): NativeMemoryPressure? = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> NativeMemoryPressure.Moderate

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> NativeMemoryPressure.Critical

            else -> null
        }
    }
}
