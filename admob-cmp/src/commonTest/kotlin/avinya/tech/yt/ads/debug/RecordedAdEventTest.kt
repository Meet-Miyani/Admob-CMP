package avinya.tech.yt.ads.debug

import avinya.tech.yt.ads.AdError
import avinya.tech.yt.ads.AdEvent
import avinya.tech.yt.ads.AdReward
import avinya.tech.yt.ads.AdValue
import avinya.tech.yt.ads.AdValuePrecision
import avinya.tech.yt.ads.PaidEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordedAdEventTest {

    private val error = AdError(code = "3", message = "no fill")
    private val reward = AdReward(amountMicros = 1_000_000L, type = "coins")
    private val paid = PaidEvent(
        placementId = "p",
        value = AdValue(valueMicros = 2_100L, currencyCode = "USD", precision = AdValuePrecision.Precise),
    )

    @Test
    fun everyEventTypeHasTheDocumentedSeverity() {
        val expectations: List<Pair<AdEvent, EventSeverity>> = listOf(
            AdEvent.LoadFailed("p", error) to EventSeverity.Error,
            AdEvent.ShowFailed("p", error) to EventSeverity.Error,
            AdEvent.Paid("p", paid) to EventSeverity.Revenue,
            AdEvent.RewardEarned("p", reward) to EventSeverity.Revenue,
            AdEvent.Impression("p") to EventSeverity.Interaction,
            AdEvent.Clicked("p") to EventSeverity.Interaction,
            AdEvent.Loaded("p") to EventSeverity.Lifecycle,
            AdEvent.OpenedFullScreen("p") to EventSeverity.Lifecycle,
            AdEvent.ClosedFullScreen("p") to EventSeverity.Lifecycle,
            AdEvent.VideoStarted("p") to EventSeverity.Video,
            AdEvent.VideoPlayed("p") to EventSeverity.Video,
            AdEvent.VideoPaused("p") to EventSeverity.Video,
            AdEvent.VideoEnded("p") to EventSeverity.Video,
            AdEvent.VideoMuted("p", muted = true) to EventSeverity.Video,
        )

        assertEquals(14, expectations.size, "AdEvent has 14 subtypes; update this test when one is added")
        expectations.forEach { (event, expected) ->
            assertEquals(expected, event.severity(), "wrong severity for ${event::class.simpleName}")
        }
    }
}
