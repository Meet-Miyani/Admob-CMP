package dev.avinya.ads

import dev.avinya.ads.internal.ownedSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigurationSnapshotsTest {

    @Test
    fun `config snapshot does not observe later list mutation`() {
        val gmaIds = mutableListOf("gma-a")
        val umpIds = mutableListOf("ump-a")
        val hooks = mutableListOf<AdInitializationHook>()
        val original = AdConfig(
            appIds = AdAppIds("android", "ios"),
            globalRequestConfiguration = GlobalRequestConfiguration(testDeviceIds = gmaIds),
            debugOptions = AdDebugOptions(consentTestDeviceIds = umpIds),
            initializationHooks = hooks
        )

        val snapshot = original.ownedSnapshot()
        gmaIds += "gma-b"
        umpIds += "ump-b"
        hooks += object : AdInitializationHook {
            override suspend fun onPhase(phase: AdInitializationPhase, config: AdConfig) = Unit
        }

        assertEquals(listOf("gma-a"), snapshot.globalRequestConfiguration.testDeviceIds)
        assertEquals(listOf("ump-a"), snapshot.debugOptions.consentTestDeviceIds)
        assertEquals(0, snapshot.initializationHooks.size)
    }

    @Test
    fun `placement snapshot deep copies targeting collections`() {
        val keywords = mutableSetOf("sports")
        val values = mutableListOf("one")
        val targeting = mutableMapOf("segment" to values)
        val extras = mutableMapOf("adapter" to "value")
        val original = AdPlacement(
            id = "snapshot-banner",
            format = AdFormat.Banner,
            adUnitIds = AdUnitIds("android", "ios"),
            requestOptions = AdRequestOptions(
                keywords = keywords,
                customTargeting = targeting,
                googleExtras = extras
            )
        )

        val snapshot = original.ownedSnapshot()
        keywords += "news"
        values += "two"
        targeting["new"] = mutableListOf("value")
        extras["late"] = "mutation"

        assertEquals(setOf("sports"), snapshot.requestOptions.keywords)
        assertEquals(mapOf("segment" to listOf("one")), snapshot.requestOptions.customTargeting)
        assertEquals(mapOf("adapter" to "value"), snapshot.requestOptions.googleExtras)
    }
}
