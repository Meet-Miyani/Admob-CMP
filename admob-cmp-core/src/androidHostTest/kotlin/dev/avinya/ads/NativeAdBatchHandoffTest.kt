package dev.avinya.ads

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Models the batch-handoff invariant in `AndroidNativeAdPool`'s loader callbacks.
 *
 * The real pool calls into GMA's static `NativeAdLoader`, so the callback closure
 * cannot be driven from a host test without reshaping production code purely for
 * testability. What is worth pinning is the concurrency rule the fix established:
 * the completion callback claims the batch under the SAME lock that snapshots it,
 * so a cancellation racing completion cannot leave an ad owned by nobody.
 *
 * Previously the `isActive` check sat outside `synchronized(pending)`, so
 * cancellation could drain and destroy an already-empty list while the snapshot
 * still held the loaded ads — every ad in that batch leaked. This reproduces the
 * ordering under contention and asserts the accounting stays exact.
 */
class NativeAdBatchHandoffTest {

    /** Stand-in for `NativeAd`, which is final and cannot be instantiated here. */
    private class FakeAd {
        val destroyCount = AtomicInteger(0)
        fun destroy() {
            destroyCount.incrementAndGet()
        }
    }

    /**
     * Mirrors the fixed callback structure: cancellation and completion contend for
     * the same `pending` list, and exactly one of them takes ownership of the batch.
     */
    private class BatchHandoff {
        private val pending = mutableListOf<FakeAd>()
        private var cancelled = false
        var deliveredBatch: List<FakeAd>? = null
            private set

        fun add(ad: FakeAd) {
            val accepted = synchronized(pending) {
                if (cancelled) false else { pending += ad; true }
            }
            if (!accepted) ad.destroy()
        }

        /** The `invokeOnCancellation` path. */
        fun cancel() {
            synchronized(pending) {
                cancelled = true
                pending.forEach { it.destroy() }
                pending.clear()
            }
        }

        /** The `onAdLoadingCompleted` path, claiming the batch under the lock. */
        fun complete() {
            val loaded: List<FakeAd>
            val accepted: Boolean
            synchronized(pending) {
                loaded = pending.toList()
                accepted = !cancelled
                if (accepted) pending.clear()
            }
            if (!accepted) {
                loaded.forEach { it.destroy() }
                return
            }
            deliveredBatch = loaded
        }
    }

    @Test
    fun `every ad is either delivered or destroyed exactly once under cancellation race`() {
        // Many trials so the two threads interleave across the whole window rather
        // than settling into one scheduling pattern.
        repeat(2_000) {
            val handoff = BatchHandoff()
            val ads = List(4) { FakeAd() }
            ads.forEach(handoff::add)

            val start = CountDownLatch(1)
            val cancelThread = Thread {
                start.await()
                handoff.cancel()
            }
            val completeThread = Thread {
                start.await()
                handoff.complete()
            }
            cancelThread.start()
            completeThread.start()
            start.countDown()
            cancelThread.join(TimeUnit.SECONDS.toMillis(5))
            completeThread.join(TimeUnit.SECONDS.toMillis(5))

            val delivered = handoff.deliveredBatch.orEmpty().toSet()
            for (ad in ads) {
                if (ad in delivered) {
                    // Handed to the caller: the pool owns it now, so nothing destroyed it.
                    assertEquals(0, ad.destroyCount.get(), "delivered ad was also destroyed")
                } else {
                    // Not handed over: it must have been cleaned up, exactly once.
                    assertEquals(1, ad.destroyCount.get(), "orphaned or double-destroyed ad")
                }
            }
        }
    }

    @Test
    fun `ads arriving after cancellation are destroyed immediately`() {
        val handoff = BatchHandoff()
        handoff.cancel()

        val late = FakeAd()
        handoff.add(late)

        assertEquals(1, late.destroyCount.get(), "late ad leaked after cancellation")
    }

    @Test
    fun `completion without cancellation delivers the whole batch intact`() {
        val handoff = BatchHandoff()
        val ads = List(3) { FakeAd() }
        ads.forEach(handoff::add)

        handoff.complete()

        assertEquals(ads, handoff.deliveredBatch)
        assertTrue(ads.all { it.destroyCount.get() == 0 }, "delivered batch was destroyed")
    }
}
