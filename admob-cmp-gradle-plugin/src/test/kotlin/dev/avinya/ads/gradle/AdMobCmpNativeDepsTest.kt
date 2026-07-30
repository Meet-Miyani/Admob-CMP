package dev.avinya.ads.gradle

import kotlin.test.Test
import kotlin.test.assertTrue

class AdMobCmpNativeDepsTest {

    @Test
    fun `versions are non-blank and shaped like versions`() {
        assertTrue(AdMobCmpNativeDeps.gmaIosVersion.matches(Regex("""\d+\.\d+\.\d+""")))
        assertTrue(AdMobCmpNativeDeps.gmaUmpIosVersion.matches(Regex("""\d+\.\d+\.\d+""")))
    }

    @Test
    fun `checksums are 64 hex characters`() {
        assertTrue(AdMobCmpNativeDeps.gmaIosSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(AdMobCmpNativeDeps.umpIosSha256.matches(Regex("[0-9a-f]{64}")))
    }
}
