package dev.avinya.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdConfigTest {

    private fun config(
        consentTagForUnderAgeOfConsent: Boolean = false,
        globalRequestConfiguration: GlobalRequestConfiguration = GlobalRequestConfiguration()
    ) = AdConfig(
        appIds = AdAppIds(android = "ca-app-pub-android", ios = "ca-app-pub-ios"),
        globalRequestConfiguration = globalRequestConfiguration,
        consentTagForUnderAgeOfConsent = consentTagForUnderAgeOfConsent
    )

    @Test
    fun `GMA age treatment remains independent from UMP consent tag`() {
        val config = config(
            consentTagForUnderAgeOfConsent = true,
            globalRequestConfiguration = GlobalRequestConfiguration(
                ageRestrictedTreatment = AgeRestrictedTreatment.Teen
            ),
        )

        assertTrue(config.consentTagForUnderAgeOfConsent)
        assertEquals(
            AgeRestrictedTreatment.Teen,
            config.effectiveGlobalRequestConfiguration().ageRestrictedTreatment,
        )
    }

    @Test
    fun `default age treatment and consent tag are explicit and safe`() {
        val config = config()

        assertFalse(config.consentTagForUnderAgeOfConsent)
        assertEquals(
            AgeRestrictedTreatment.Unspecified,
            config.effectiveGlobalRequestConfiguration().ageRestrictedTreatment,
        )
    }

    @Test
    fun `unrelated global request configuration fields survive effective configuration`() {
        val effective = config(
            globalRequestConfiguration = GlobalRequestConfiguration(
                testDeviceIds = listOf("device-a", "device-b"),
                maxAdContentRating = MaxAdContentRating.Teen,
                ageRestrictedTreatment = AgeRestrictedTreatment.Child,
            ),
        ).effectiveGlobalRequestConfiguration()

        assertEquals(listOf("device-a", "device-b"), effective.testDeviceIds)
        assertEquals(MaxAdContentRating.Teen, effective.maxAdContentRating)
        assertEquals(AgeRestrictedTreatment.Child, effective.ageRestrictedTreatment)
    }

    @Test
    fun `convenience constructor propagates independent age and UMP settings`() {
        val config = AdConfig(
            androidAppId = "ca-app-pub-android",
            iosAppId = "ca-app-pub-ios",
            ageRestrictedTreatment = AgeRestrictedTreatment.Child,
            consentTagForUnderAgeOfConsent = true,
        )

        assertTrue(config.consentTagForUnderAgeOfConsent)
        assertEquals(
            AgeRestrictedTreatment.Child,
            config.effectiveGlobalRequestConfiguration().ageRestrictedTreatment,
        )
    }

    @Test
    fun `testMode with no test device ids anywhere warns`() {
        val warning = config().copy(debugOptions = AdDebugOptions(testMode = true)).testModeWarningOrNull()

        assertNotNull(warning)
        assertTrue(warning.contains("testMode"))
        assertTrue(warning.contains("physical device"))
    }

    @Test
    fun `testMode with global request configuration test device ids does not warn`() {
        val warning = config(
            globalRequestConfiguration = GlobalRequestConfiguration(testDeviceIds = listOf("HASHED_ID"))
        ).copy(debugOptions = AdDebugOptions(testMode = true)).testModeWarningOrNull()

        assertNull(warning)
    }

    @Test
    fun `testMode with only consent debug test device ids still warns`() {
        // consentTestDeviceIds configures UMP consent debugging and NEVER reaches GMA's
        // RequestConfiguration, so it is no evidence the device will be served test ads.
        // This previously asserted the opposite and locked in a warning that failed open.
        val warning = config().copy(
            debugOptions = AdDebugOptions(testMode = true, consentTestDeviceIds = listOf("HASHED_ID"))
        ).testModeWarningOrNull()

        assertNotNull(warning, "UMP consent debug ids must not satisfy the GMA test-device check")
        assertTrue(warning.contains("testDeviceIds"))
    }

    @Test
    fun `testMode false never warns regardless of test device ids`() {
        val warning = config().copy(debugOptions = AdDebugOptions(testMode = false)).testModeWarningOrNull()

        assertNull(warning)
    }
}
