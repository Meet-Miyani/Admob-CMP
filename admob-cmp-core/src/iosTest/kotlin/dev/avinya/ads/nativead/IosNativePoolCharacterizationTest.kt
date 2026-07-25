package dev.avinya.ads.nativead

import dev.avinya.ads.AdError
import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdLoadState
import dev.avinya.ads.AdPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosNativePoolCharacterizationTest {

    private val nativePlacement = AdPlacement(
        id = "char_native_ios",
        format = AdFormat.Native,
        androidAdUnitId = "test-android",
        iosAdUnitId = "test-ios"
    )

    private fun pool(blocked: () -> AdError? = { null }) = IosNativeAdPool(
        placement = nativePlacement,
        globalEvents = MutableSharedFlow(extraBufferCapacity = 16),
        adRequestBlockedError = blocked
    )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fresh pool has Idle loadState and zero available ads`() =
        runTest(StandardTestDispatcher()) {
            val p = pool()
            assertEquals(AdLoadState.Idle, p.loadState.value)
            assertEquals(0, p.availableCount())
        }

    @Test
    fun `acquire returns null when pool is empty`() =
        runTest(StandardTestDispatcher()) {
            val p = pool()
            assertNull(p.acquire())
        }

    @Test
    fun `clear on a fresh pool keeps loadState Idle`() =
        runTest(StandardTestDispatcher()) {
            val p = pool()
            assertEquals(AdLoadState.Idle, p.loadState.value)
            p.clear()
            assertEquals(AdLoadState.Idle, p.loadState.value)
        }

    @Test
    fun `release of unknown token is a no-op`() =
        runTest(StandardTestDispatcher()) {
            val p = pool()
            p.release(NativeAdToken(nativePlacement.id, "nonexistent"))
            assertTrue(true)
        }

    @Test
    fun `mediaInfo returns null for unknown token`() =
        runTest(StandardTestDispatcher()) {
            val p = pool()
            assertNull(p.mediaInfo(NativeAdToken(nativePlacement.id, "nonexistent")))
        }
}
