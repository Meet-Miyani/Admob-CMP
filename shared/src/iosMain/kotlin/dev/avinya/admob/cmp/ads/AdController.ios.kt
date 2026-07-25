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
     * load/show operations. The controller cache is accessed only from this scope.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val interstitials = mutableMapOf<String, InterstitialAdController>()

    override val adsSupported: Boolean = true

    override fun loadInterstitial(placementId: String) {
        launchHandled("load interstitial '$placementId'") {
            when (val result = interstitial(placementId).load()) {
                is AdLoadState.Failed -> println("AdmobCMPDemo: Interstitial '$placementId' failed to load: ${result.error}")
                else -> println("AdmobCMPDemo: Interstitial '$placementId' load result: $result")
            }
        }
    }

    override fun showInterstitial(placementId: String) {
        launchHandled("show interstitial '$placementId'") {
            when (val result = interstitial(placementId).show()) {
                is AdShowResult.Failed -> println("AdmobCMPDemo: Interstitial '$placementId' failed to show: ${result.error}")
                AdShowResult.NotReady -> println("AdmobCMPDemo: Interstitial '$placementId' is not ready")
                AdShowResult.Shown -> println("AdmobCMPDemo: Interstitial '$placementId' was shown")
                is AdShowResult.Rewarded -> println("AdmobCMPDemo: Interstitial '$placementId' returned an unexpected reward: ${result.reward}")
            }
        }
    }

    private fun interstitial(placementId: String): InterstitialAdController =
        interstitials.getOrPut(placementId) {
            manager.interstitial(
                AdPlacement(
                    id = placementId,
                    format = AdFormat.Interstitial,
                    adUnitIds = AdUnitIds(
                        android = TestAdIds.ANDROID_INTERSTITIAL,
                        ios = TestAdIds.IOS_INTERSTITIAL,
                    ),
                    strictTestMode = true,
                ),
            )
        }

    private fun launchHandled(operation: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                println("AdmobCMPDemo: Failed to $operation: $failure")
            }
        }
    }
}

actual fun getAdController(): AdController = IosAdController
