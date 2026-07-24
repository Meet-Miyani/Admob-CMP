package avinya.tech.yt.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import java.lang.ref.WeakReference

public object AdMob {
    private var manager: AndroidGoogleAdManager? = null
    private var activityTracker: CurrentActivityTracker? = null
    private val lock = Any()

    public fun manager(context: Context): AdManager {
        synchronized(lock) {
            val providedActivity = context as? Activity
            manager?.let {
                if (providedActivity != null) activityTracker?.seed(providedActivity)
                return it
            }
            val appContext = context.applicationContext
            // Always retain a tracker so a later direct Activity call can seed an
            // existing singleton even when lifecycle callbacks cannot be registered.
            val tracker = CurrentActivityTracker().also {
                if (appContext is Application) it.register(appContext)
                if (providedActivity != null) it.seed(providedActivity)
            }
            activityTracker = tracker
            return AndroidGoogleAdManager(appContext) { tracker.currentActivity }.also { manager = it }
        }
    }
}

/**
 * Weakly-held, most-recent-last ordering of foreground entries.
 *
 * Extracted from [CurrentActivityTracker] so the sequencing behind P1-9 is testable without
 * Robolectric — this source set cannot construct a real `Activity`.
 *
 * Entries are weak so a leaked or forgotten remove cannot pin an Activity in memory;
 * [current] prunes cleared references as it scans.
 */
internal class ForegroundStack<T : Any>(private val isUsable: (T) -> Boolean) {
    private val lock = Any()
    private val entries = mutableListOf<WeakReference<T>>()

    /** Adds [value] as the most recent entry, moving it if already tracked. */
    fun push(value: T) {
        synchronized(lock) {
            entries.removeAll { it.get() === value || it.get() == null }
            entries.add(WeakReference(value))
        }
    }

    /** Removes only [value]; anything still in the foreground beneath it survives. */
    fun remove(value: T) {
        synchronized(lock) {
            entries.removeAll { it.get() === value || it.get() == null }
        }
    }

    /** The most recent still-live, usable entry, or null. */
    fun current(): T? = synchronized(lock) {
        for (index in entries.indices.reversed()) {
            val value = entries[index].get()
            if (value == null) {
                entries.removeAt(index)
            } else if (isUsable(value)) {
                return value
            }
        }
        null
    }
}

internal class CurrentActivityTracker : Application.ActivityLifecycleCallbacks {
    private val lock = Any()
    private var registeredApplication: Application? = null

    // P1-9: this was a single WeakReference, so `A started -> B started -> B stopped` cleared
    // it outright and reported no Activity even though A was still in the foreground. Track
    // the whole started set and fall back to the one beneath instead.
    private val started = ForegroundStack<Activity> { !it.isFinishing && !it.isDestroyed }

    val currentActivity: Activity? get() = started.current()

    fun seed(activity: Activity) {
        started.push(activity)
        register(activity.application)
    }

    fun register(application: Application) {
        val shouldRegister = synchronized(lock) {
            if (registeredApplication == null) {
                registeredApplication = application
                true
            } else {
                false
            }
        }
        if (shouldRegister) application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        seed(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        clearIfMatching(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}

    // Resume promotes to most-recent so the resumed Activity wins over one that is merely
    // started (multi-window, or a translucent Activity above a still-started host).
    override fun onActivityResumed(activity: Activity) {
        started.push(activity)
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        clearIfMatching(activity)
    }

    private fun clearIfMatching(activity: Activity) {
        // Remove only this instance. Clearing the whole reference is what lost A when B
        // stopped above it (P1-9).
        started.remove(activity)
    }
}
