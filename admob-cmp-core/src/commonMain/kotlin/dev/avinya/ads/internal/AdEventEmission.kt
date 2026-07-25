package dev.avinya.ads.internal

import dev.avinya.ads.AdEvent
import dev.avinya.ads.AdLogger
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Publishes [event], logging when the buffer refuses it.
 *
 * P1-16: every emitter called `tryEmit` and discarded the Boolean. `AdEvent` flows have no
 * replay and a finite extra buffer, so a burst against a slow collector — or an emission with
 * no collector attached — dropped impressions, closes, show failures and paid events with no
 * trace anywhere. The stream is presented as a unified event stream but has best-effort
 * datagram semantics; this at least makes the loss observable instead of invisible.
 *
 * This is deliberately the minimum. Durable analytics/revenue delivery needs a separate
 * contract — a synchronous sink or bounded channel with explicit overflow behaviour and event
 * IDs for deduplication — which is an API change, not a logging change.
 */
internal fun MutableSharedFlow<AdEvent>.emitOrLogDrop(event: AdEvent, source: String) {
    if (!tryEmit(event)) {
        AdLogger.w(
            "Ad event DROPPED by $source — the event buffer was full and this event is gone. " +
                "event=${event::class.simpleName} placement=${event.placementId}. " +
                "Do not rely on this stream for billing-grade accounting."
        )
    }
}
