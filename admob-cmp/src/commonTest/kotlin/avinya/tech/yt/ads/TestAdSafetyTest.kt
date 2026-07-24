package avinya.tech.yt.ads

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestAdSafetyTest {

    @Test
    fun googleTestUnitsAreRecognised() {
        assertTrue(TestAdIds.isTestAdUnitId(TestAdIds.ANDROID_BANNER))
        assertTrue(TestAdIds.isTestAdUnitId(TestAdIds.IOS_REWARDED))
        assertTrue(TestAdIds.isTestAdUnitId(TestAdIds.ANDROID_APP_OPEN))
    }

    @Test
    fun productionUnitsAreNotMistakenForTestUnits() {
        assertFalse(TestAdIds.isTestAdUnitId("ca-app-pub-1234567890123456/1234567890"))
        assertFalse(TestAdIds.isTestAdUnitId(""))
    }

    @Test
    fun strictTestModeRejectsAProductionAdUnit() {
        val error = assertFailsWith<IllegalArgumentException> {
            AdPlacement(
                id = "oops",
                format = AdFormat.Banner,
                androidAdUnitId = "ca-app-pub-1234567890123456/1234567890",
                iosAdUnitId = TestAdIds.IOS_BANNER,
                strictTestMode = true,
            )
        }
        // The message has to name the offending id, or a developer cannot find it.
        assertTrue(error.message!!.contains("ca-app-pub-1234567890123456/1234567890"))
    }

    @Test
    fun strictTestModeAcceptsTestAdUnits() {
        AdPlacement(
            id = "fine",
            format = AdFormat.Banner,
            androidAdUnitId = TestAdIds.ANDROID_BANNER,
            iosAdUnitId = TestAdIds.IOS_BANNER,
            strictTestMode = true,
        )
    }

    @Test
    fun strictTestModeOffAllowsProductionUnits() {
        AdPlacement(
            id = "release",
            format = AdFormat.Banner,
            androidAdUnitId = "ca-app-pub-1234567890123456/1234567890",
            iosAdUnitId = "ca-app-pub-1234567890123456/0987654321",
        )
    }
}
