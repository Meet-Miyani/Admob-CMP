package dev.avinya.admob.cmp.ads

import android.content.Context
import android.util.Log
import avinya.tech.yt.ads.AdFormat
import avinya.tech.yt.ads.AdLoadState
import avinya.tech.yt.ads.AdManager
import avinya.tech.yt.ads.AdMob
import avinya.tech.yt.ads.AdPlacement
import avinya.tech.yt.ads.AdShowResult
import avinya.tech.yt.ads.AdUnitIds
import avinya.tech.yt.ads.InterstitialAdController
import avinya.tech.yt.ads.TestAdIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "AdmobCMPDemo"

private object AndroidAdControllerProvider {
    @Volatile
    private var controller: AdController? = null

    fun initialize(context: Context) {
        if (controller != null) return
        synchronized(this) {
            if (controller == null) {
                controller = AndroidAdController(
                    manager = AdMob.manager(context.applicationContext),
                )
            }
        }
    }

    fun get(): AdController = checkNotNull(controller) {
        "Android AdController is not initialized. Call initializeAndroidAdController() " +
            "from the application entry point before requesting it."
    }
}

private class AndroidAdController(
    private val manager: AdManager,
) : AdController {
    /*
     * This controller and AdMob's manager are process-scoped. The scope intentionally shares
     * that lifetime and is cancelled by process teardown; SupervisorJob prevents one failed
     * demo operation from cancelling later operations. Only the application-backed manager is
     * retained, never an Activity.
     *
     * The single controller is initialized only from this Main-confined scope after the common
     * finite-placement resolver has accepted the caller's ID.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val interstitial: InterstitialAdController by lazy {
        manager.interstitial(
            AdPlacement(
                id = DemoAdPlacementIds.INTERSTITIAL,
                format = AdFormat.Interstitial,
                adUnitIds = AdUnitIds(
                    android = TestAdIds.ANDROID_INTERSTITIAL,
                    ios = TestAdIds.IOS_INTERSTITIAL,
                ),
                strictTestMode = true,
            ),
        )
    }

    override val adsSupported: Boolean = true

    override fun loadInterstitial(placementId: String) {
        val resolvedPlacementId = resolveDemoInterstitialPlacementId(placementId)
        launchHandled("load interstitial '$resolvedPlacementId'") {
            when (val result = interstitial.load()) {
                is AdLoadState.Failed -> Log.w(TAG, "Interstitial '$resolvedPlacementId' failed to load: ${result.error}")
                else -> Log.d(TAG, "Interstitial '$resolvedPlacementId' load result: $result")
            }
        }
    }

    override fun showInterstitial(placementId: String) {
        val resolvedPlacementId = resolveDemoInterstitialPlacementId(placementId)
        launchHandled("show interstitial '$resolvedPlacementId'") {
            when (val result = interstitial.show()) {
                is AdShowResult.Failed -> Log.w(TAG, "Interstitial '$resolvedPlacementId' failed to show: ${result.error}")
                AdShowResult.NotReady -> Log.d(TAG, "Interstitial '$resolvedPlacementId' is not ready")
                AdShowResult.Shown -> Log.d(TAG, "Interstitial '$resolvedPlacementId' was shown")
                is AdShowResult.Rewarded -> Log.d(TAG, "Interstitial '$resolvedPlacementId' returned an unexpected reward: ${result.reward}")
            }
        }
    }

    private fun launchHandled(operation: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Log.e(TAG, "Failed to $operation", failure)
                throw failure
            }
        }
    }
}

/**
 * Initializes the Android demo's process-wide [AdController].
 *
 * The entry point supplies an application context so this provider never retains an Activity.
 */
fun initializeAndroidAdController(context: Context) {
    AndroidAdControllerProvider.initialize(context.applicationContext)
}

actual fun getAdController(): AdController = AndroidAdControllerProvider.get()
