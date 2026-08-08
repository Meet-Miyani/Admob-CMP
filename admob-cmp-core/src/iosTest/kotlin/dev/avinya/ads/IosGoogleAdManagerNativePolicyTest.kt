package dev.avinya.ads

import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosGoogleAdManagerNativePolicyTest {
    @Test
    fun `native facade remains deferred then binds the accepted non-default policy once`() {
        val manager = IosGoogleAdManager()
        val config = AdConfig(
            androidAppId = "android-app",
            iosAppId = "ios-app",
            nativeAdMemoryPolicy = NativeAdMemoryPolicy(softLimit = 2, hardLimit = 3),
        )

        assertEquals(0, manager.nativeAds.state.value.loadedAds)
        assertFailsWith<IllegalStateException> { manager.nativeAds.policy }

        manager.configureNativeAdsAfterAcceptedInitialization(config)
        manager.configureNativeAdsAfterAcceptedInitialization(config)

        assertEquals(config.nativeAdMemoryPolicy, manager.nativeAds.policy)
    }
}
