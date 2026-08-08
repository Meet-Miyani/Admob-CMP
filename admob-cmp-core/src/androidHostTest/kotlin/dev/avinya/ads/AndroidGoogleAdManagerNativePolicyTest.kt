package dev.avinya.ads

import android.content.Context
import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidGoogleAdManagerNativePolicyTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun installMainDispatcher() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `native facade remains deferred then binds the accepted non-default policy once`() {
        val manager = AndroidGoogleAdManager(mock(Context::class.java)) { null }
        val config = AdConfig(
            androidAppId = "android-app",
            iosAppId = "ios-app",
            nativeAdMemoryPolicy = NativeAdMemoryPolicy(softLimit = 2, hardLimit = 3),
        )

        assertEquals(0, manager.nativeAds.state.value.loadedAds)
        assertEquals(NativeAdMemoryPolicy(), manager.nativeAds.policy)

        manager.configureNativeAdsAfterAcceptedInitialization(config)
        manager.configureNativeAdsAfterAcceptedInitialization(config)

        assertEquals(config.nativeAdMemoryPolicy, manager.nativeAds.policy)
    }
}
