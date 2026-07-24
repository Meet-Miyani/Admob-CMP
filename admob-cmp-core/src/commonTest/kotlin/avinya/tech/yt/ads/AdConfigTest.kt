package avinya.tech.yt.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdConfigTest {

    private fun config(
        requestUnderAgeOfConsent: Boolean? = null,
        tagForChildDirectedTreatment: Boolean? = null,
        globalRequestConfiguration: GlobalRequestConfiguration = GlobalRequestConfiguration()
    ) = AdConfig(
        appIds = AdAppIds(android = "ca-app-pub-android", ios = "ca-app-pub-ios"),
        globalRequestConfiguration = globalRequestConfiguration,
        requestUnderAgeOfConsent = requestUnderAgeOfConsent,
        tagForChildDirectedTreatment = tagForChildDirectedTreatment
    )

    @Test
    fun `explicit COPPA flags win over nested request configuration`() {
        val effective = config(
            requestUnderAgeOfConsent = true,
            tagForChildDirectedTreatment = true,
            globalRequestConfiguration = GlobalRequestConfiguration(
                tagForUnderAgeOfConsent = RequestTag.False,
                tagForChildDirectedTreatment = RequestTag.False
            )
        ).effectiveGlobalRequestConfiguration()

        assertEquals(RequestTag.True, effective.tagForUnderAgeOfConsent)
        assertEquals(RequestTag.True, effective.tagForChildDirectedTreatment)
    }

    @Test
    fun `explicit false COPPA flags win over nested true configuration`() {
        val effective = config(
            requestUnderAgeOfConsent = false,
            tagForChildDirectedTreatment = false,
            globalRequestConfiguration = GlobalRequestConfiguration(
                tagForUnderAgeOfConsent = RequestTag.True,
                tagForChildDirectedTreatment = RequestTag.True
            )
        ).effectiveGlobalRequestConfiguration()

        assertEquals(RequestTag.False, effective.tagForUnderAgeOfConsent)
        assertEquals(RequestTag.False, effective.tagForChildDirectedTreatment)
    }

    @Test
    fun `null COPPA flags preserve nested request configuration`() {
        val effective = config(
            requestUnderAgeOfConsent = null,
            tagForChildDirectedTreatment = null,
            globalRequestConfiguration = GlobalRequestConfiguration(
                tagForUnderAgeOfConsent = RequestTag.True,
                tagForChildDirectedTreatment = RequestTag.False
            )
        ).effectiveGlobalRequestConfiguration()

        assertEquals(RequestTag.True, effective.tagForUnderAgeOfConsent)
        assertEquals(RequestTag.False, effective.tagForChildDirectedTreatment)
    }

    @Test
    fun `unrelated global request configuration fields survive the merge`() {
        val effective = config(
            tagForChildDirectedTreatment = true,
            globalRequestConfiguration = GlobalRequestConfiguration(
                testDeviceIds = listOf("device-a", "device-b"),
                maxAdContentRating = MaxAdContentRating.Teen
            )
        ).effectiveGlobalRequestConfiguration()

        assertEquals(listOf("device-a", "device-b"), effective.testDeviceIds)
        assertEquals(MaxAdContentRating.Teen, effective.maxAdContentRating)
        assertEquals(RequestTag.True, effective.tagForChildDirectedTreatment)
    }

    @Test
    fun `convenience constructor propagates COPPA flags into the effective configuration`() {
        val effective = AdConfig(
            androidAppId = "ca-app-pub-android",
            iosAppId = "ca-app-pub-ios",
            requestUnderAgeOfConsent = true,
            tagForChildDirectedTreatment = true
        ).effectiveGlobalRequestConfiguration()

        assertEquals(RequestTag.True, effective.tagForUnderAgeOfConsent)
        assertEquals(RequestTag.True, effective.tagForChildDirectedTreatment)
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
    fun `testMode with only consent debug test device ids does not warn`() {
        val warning = config().copy(
            debugOptions = AdDebugOptions(testMode = true, consentTestDeviceIds = listOf("HASHED_ID"))
        ).testModeWarningOrNull()

        assertNull(warning)
    }

    @Test
    fun `testMode false never warns regardless of test device ids`() {
        val warning = config().copy(debugOptions = AdDebugOptions(testMode = false)).testModeWarningOrNull()

        assertNull(warning)
    }
}
