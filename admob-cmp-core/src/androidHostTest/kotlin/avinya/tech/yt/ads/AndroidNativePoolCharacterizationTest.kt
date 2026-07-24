package avinya.tech.yt.ads

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import avinya.tech.yt.ads.nativead.AndroidNativeAdPool
import org.junit.BeforeClass
import org.mockito.Mockito
import org.mockito.stubbing.Answer

/**
 * CHARACTERIZATION of AndroidNativeAdPool as it behaves TODAY, before sub-project D extracts
 * NativePoolCore.
 *
 * LIMITATION: preload() reaches GMA's NativeAdLoader (final class, static load()) and cannot
 * be driven from a host test. Tests that need populated internal state — P1-2 (batch completes
 * after clear), P1-6 (maxSize counts in-use), P1-7 (clear destroys in-use) — are therefore
 * unreachable here. They are covered by:
 *   - The batch-handoff model in NativeAdBatchHandoffTest (for the cancellation/completion race)
 *   - IosNativePoolCharacterizationTest (iOS has the same defects but IS testable through iosTest)
 *
 * See IosNativePoolCharacterizationTest for the testable twin of each P1-x pin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidNativePoolCharacterizationTest {

    companion object {
        @BeforeClass @JvmStatic
        fun mockAndroidLog() {
            Mockito.mockStatic(Log::class.java, Answer { null })
        }
    }

    private val poolPlacement = AdPlacement(
        id = "char_native",
        format = AdFormat.Native,
        androidAdUnitId = "test-android",
        iosAdUnitId = "test-ios"
    )

    private fun pool(blocked: () -> AdError? = { null }) = AndroidNativeAdPool(
        placement = poolPlacement,
        globalEvents = MutableSharedFlow(extraBufferCapacity = 16),
        adRequestBlockedError = blocked
    )

    @Test
    fun `fresh pool has no available ads and acquire returns null`() =
        runTest(StandardTestDispatcher()) {
            val p = pool()
            assertEquals(0, p.availableCount())
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
}
