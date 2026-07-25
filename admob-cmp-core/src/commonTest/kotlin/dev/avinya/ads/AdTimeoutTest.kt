package dev.avinya.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class AdTimeoutTest {

    @Test
    fun defaultsBoundBothPhases() {
        val policy = AdTimeoutPolicy()
        assertEquals(30.seconds, policy.loadTimeout)
        assertEquals(10.seconds, policy.presentationHandOffTimeout)
    }

    @Test
    fun nonPositiveTimeoutsAreRejected() {
        assertFailsWith<IllegalArgumentException> { AdTimeoutPolicy(loadTimeout = 0.seconds) }
        assertFailsWith<IllegalArgumentException> {
            AdTimeoutPolicy(presentationHandOffTimeout = (-1).seconds)
        }
    }

    @Test
    fun placementCarriesTheDefaultPolicy() {
        assertEquals(AdTimeoutPolicy(), testPlacement.timeoutPolicy)
    }
}
