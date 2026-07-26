@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.avinya.ads

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosFullScreenDelegateStoreTest {

    @Test
    fun `terminal callback releases retained delegate even when callback throws`() {
        val store = FullScreenDelegateStore<Any>()
        val ad = Any()
        val delegate = FullScreenDelegate({}, {}, { _ -> }, {}, {})
        store.retain(ad, delegate)
        assertTrue(store.contains(ad))

        runCatching {
            store.terminal(ad) { error("terminal consumer failed") }
        }

        assertFalse(store.contains(ad))
    }
}
