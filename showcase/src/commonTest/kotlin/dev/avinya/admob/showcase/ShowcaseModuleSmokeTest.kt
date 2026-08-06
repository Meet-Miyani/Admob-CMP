package dev.avinya.admob.showcase

import kotlin.test.Test
import kotlin.test.assertTrue

class ShowcaseModuleSmokeTest {

    @Test
    fun exposesEveryAdFormatTheSdkDefines() {
        val formats = ShowcaseBuildInfo.sdkFormats

        assertTrue(
            "Banner" in formats,
            "expected AdFormat.Banner to be visible from :showcase, got $formats",
        )
        assertTrue(
            formats.size >= 6,
            "expected at least 6 ad formats, got ${formats.size}: $formats",
        )
    }
}
