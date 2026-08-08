package dev.avinya.ads.ui

import dev.avinya.ads.AdFormat
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.nativead.NativeAdSession
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSessionState
import dev.avinya.ads.nativead.NativeAdSlot
import dev.avinya.ads.nativead.NativeAdWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeAdSessionBindingTest {
    private val placement = AdPlacement("native", AdFormat.Native, "android-native", "ios-native")
    private val replacementPlacement = AdPlacement("replacement", AdFormat.Native, "android-replacement", "ios-replacement")

    @Test
    fun `slot binding updates changed slot and deactivates without closing`() {
        val session = RecordingSession()
        val binding = NativeAdSlotSessionBinding(session)

        binding.update(NativeAdSlot("first", placement))
        binding.update(NativeAdSlot("second", replacementPlacement))
        binding.deactivate()

        assertEquals(listOf("first", "second"), session.windows.map { it.visible.single().key })
        assertEquals(1, session.deactivateCalls)
        assertEquals(0, session.closeCalls)
    }

    @Test
    fun `slot binding is safe to report again during recomposition`() {
        val session = RecordingSession()
        val binding = NativeAdSlotSessionBinding(session)
        val slot = NativeAdSlot("stable", placement)

        binding.update(slot)
        binding.update(slot)

        assertEquals(2, session.windows.size)
        assertEquals(listOf("stable"), session.windows.last().visible.map(NativeAdSlot::key))
    }

    @Test
    fun `two isolated bindings retain independent session ownership`() {
        val first = RecordingSession()
        val second = RecordingSession()

        NativeAdSlotSessionBinding(first).update(NativeAdSlot("one", placement))
        NativeAdSlotSessionBinding(second).update(NativeAdSlot("two", placement))

        assertEquals(listOf("one"), first.windows.single().visible.map(NativeAdSlot::key))
        assertEquals(listOf("two"), second.windows.single().visible.map(NativeAdSlot::key))
    }

    @Test
    fun `explicit close remains the session owners operation`() {
        val session = RecordingSession()
        NativeAdSlotSessionBinding(session).deactivate()

        session.close()

        assertEquals(1, session.deactivateCalls)
        assertEquals(1, session.closeCalls)
    }

    private class RecordingSession : NativeAdSession {
        override val key: String = "recording"
        override val policy: NativeAdSessionPolicy = NativeAdSessionPolicy(
            maxRetainedAds = 1,
            retainBehind = 0,
            prefetchAhead = 0,
        )
        override val state: StateFlow<NativeAdSessionState> = MutableStateFlow(NativeAdSessionState(false, emptyMap()))
        val windows = mutableListOf<NativeAdWindow>()
        var deactivateCalls = 0
        var closeCalls = 0

        override fun updateWindow(window: NativeAdWindow) { windows += window }
        override fun deactivate() { deactivateCalls++ }
        override fun close() { closeCalls++ }
    }
}
