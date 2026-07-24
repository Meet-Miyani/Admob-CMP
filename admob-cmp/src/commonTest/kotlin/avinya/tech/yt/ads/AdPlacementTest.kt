package avinya.tech.yt.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class AdPlacementTest {

    @Test
    fun `blank id throws`() {
        assertFailsWith<IllegalArgumentException> {
            AdPlacement("  ", AdFormat.Banner, "android", "ios")
        }
    }

    @Test
    fun `maxSize less than 1 throws`() {
        assertFailsWith<IllegalArgumentException> {
            AdPlacement("test", AdFormat.Banner, "android", "ios", maxCacheSize = 0)
        }
    }

    @Test
    fun `AdUnitIds forPlatform maps correctly`() {
        val ids = AdUnitIds(android = "android-ad", ios = "ios-ad")
        assertEquals("android-ad", ids.forPlatform(AdPlatform.Android))
        assertEquals("ios-ad", ids.forPlatform(AdPlatform.Ios))
    }

    @Test
    fun `convenience constructor produces equivalent placement`() {
        val full = AdPlacement(
            id = "test",
            format = AdFormat.Native,
            adUnitIds = AdUnitIds("a", "i"),
            cachePolicy = AdCachePolicy(maxSize = 3),
            enabled = true
        )
        val concise = AdPlacement(
            id = "test",
            format = AdFormat.Native,
            androidAdUnitId = "a",
            iosAdUnitId = "i",
            maxCacheSize = 3,
            enabled = true
        )
        assertEquals(full.id, concise.id)
        assertEquals(full.format, concise.format)
        assertEquals(full.adUnitIds, concise.adUnitIds)
        assertEquals(full.cachePolicy, concise.cachePolicy)
    }

    @Test
    fun `SdkManaged interval below 30s throws`() {
        assertFailsWith<IllegalArgumentException> {
            BannerRefreshPolicy.SdkManaged(29.seconds)
        }
    }

    @Test
    fun `SdkManaged interval above 120s throws`() {
        assertFailsWith<IllegalArgumentException> {
            BannerRefreshPolicy.SdkManaged(121.seconds)
        }
    }

    @Test
    fun `SdkManaged interval at boundary is valid`() {
        val lower = BannerRefreshPolicy.SdkManaged(30.seconds)
        val upper = BannerRefreshPolicy.SdkManaged(120.seconds)
        assertEquals(30.seconds, (lower as BannerRefreshPolicy.SdkManaged).interval)
        assertEquals(120.seconds, (upper as BannerRefreshPolicy.SdkManaged).interval)
    }
}
