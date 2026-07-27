package dev.avinya.ads.internal

import dev.avinya.ads.*

internal class NoOpControllerRegistry {
    private val lock = FullScreenStateLock()
    private val placements = mutableMapOf<String, AdPlacement>()
    private val banners = mutableMapOf<String, BannerAdController>()
    private val nativePools = mutableMapOf<String, NativeAdPool>()
    private val fullScreen = mutableMapOf<Pair<String, AdFormat>, FullScreenAdController>()

    private fun owned(placement: AdPlacement, expected: AdFormat): AdPlacement {
        require(placement.format == expected) {
            "AdPlacement '${placement.id}' has format ${placement.format} but was passed to a $expected factory."
        }
        val snapshot = placement.ownedSnapshot()
        val existing = placements.getOrPut(snapshot.id) { snapshot }
        check(existing == snapshot) {
            "AdPlacement id '${snapshot.id}' is already registered with different configuration."
        }
        return existing
    }

    internal fun banner(placement: AdPlacement): BannerAdController = lock.withLock {
        val value = owned(placement, AdFormat.Banner)
        banners.getOrPut(value.id) { NoOpBannerAdController(value) }
    }

    internal fun nativeAd(placement: AdPlacement): NativeAdPool = lock.withLock {
        val value = owned(placement, AdFormat.Native)
        nativePools.getOrPut(value.id) { NoOpNativeAdPool(value) }
    }

    internal fun interstitial(placement: AdPlacement): InterstitialAdController = lock.withLock {
        val value = owned(placement, AdFormat.Interstitial)
        val key = Pair(value.id, AdFormat.Interstitial)
        val controller = fullScreen.getOrPut(key) { NoOpInterstitialAdController(value) }
        controller as InterstitialAdController
    }

    internal fun rewarded(placement: AdPlacement): RewardedAdController = lock.withLock {
        val value = owned(placement, AdFormat.Rewarded)
        val key = Pair(value.id, AdFormat.Rewarded)
        val controller = fullScreen.getOrPut(key) { NoOpRewardedAdController(value) }
        controller as RewardedAdController
    }

    internal fun rewardedInterstitial(placement: AdPlacement): RewardedInterstitialAdController = lock.withLock {
        val value = owned(placement, AdFormat.RewardedInterstitial)
        val key = Pair(value.id, AdFormat.RewardedInterstitial)
        val controller = fullScreen.getOrPut(key) { NoOpRewardedInterstitialAdController(value) }
        controller as RewardedInterstitialAdController
    }

    internal fun appOpen(placement: AdPlacement): AppOpenAdController = lock.withLock {
        val value = owned(placement, AdFormat.AppOpen)
        val key = Pair(value.id, AdFormat.AppOpen)
        val controller = fullScreen.getOrPut(key) { NoOpAppOpenAdController(value) }
        controller as AppOpenAdController
    }
}
