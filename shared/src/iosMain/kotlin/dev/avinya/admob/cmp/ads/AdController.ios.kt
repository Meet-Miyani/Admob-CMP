package dev.avinya.admob.cmp.ads

import avinya.tech.yt.ads.AdFormat
import avinya.tech.yt.ads.AdLoadState
import avinya.tech.yt.ads.AdManager
import avinya.tech.yt.ads.AdPlacement
import avinya.tech.yt.ads.AdShowResult
import avinya.tech.yt.ads.AdUnitIds
import avinya.tech.yt.ads.InterstitialAdController
import avinya.tech.yt.ads.IosAdMob
import avinya.tech.yt.ads.TestAdIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private object IosAdController : AdController {
    private val manager: AdManager = IosAdMob.manager

    /*
     * IosAdMob.manager is process-wide, so this Main-confined scope deliberately has the same
     * lifetime and is cancelled by process teardown. SupervisorJob isolates independent demo
     * load/show operations. The single controller is initialized only from this scope after the
     * common finite-placement resolver has accepted the caller's ID.
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
                is AdLoadState.Failed -> println("AdmobCMPDemo: Interstitial '$resolvedPlacementId' failed to load: ${result.error}")
                else -> println("AdmobCMPDemo: Interstitial '$resolvedPlacementId' load result: $result")
            }
        }
    }

    override fun showInterstitial(placementId: String) {
        val resolvedPlacementId = resolveDemoInterstitialPlacementId(placementId)
        launchHandled("show interstitial '$resolvedPlacementId'") {
            when (val result = interstitial.show()) {
                is AdShowResult.Failed -> println("AdmobCMPDemo: Interstitial '$resolvedPlacementId' failed to show: ${result.error}")
                AdShowResult.NotReady -> println("AdmobCMPDemo: Interstitial '$resolvedPlacementId' is not ready")
                AdShowResult.Shown -> println("AdmobCMPDemo: Interstitial '$resolvedPlacementId' was shown")
                is AdShowResult.Rewarded -> println("AdmobCMPDemo: Interstitial '$resolvedPlacementId' returned an unexpected reward: ${result.reward}")
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
                println("AdmobCMPDemo: Failed to $operation: $failure")
                throw failure
            }
        }
    }
}

actual fun getAdController(): AdController = IosAdController
