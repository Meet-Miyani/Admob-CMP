package avinya.tech.yt.ads

import avinya.tech.yt.ads.appopen.AppOpenAdCoordinator
import avinya.tech.yt.ads.appopen.AppOpenConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AppOpenAdCoordinatorTest {

    @Test
    fun `foreground event with ready ad and elapsed thresholds triggers show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        val ticks = mutableListOf(
            Instant.fromEpochSeconds(1000), // background
            Instant.fromEpochSeconds(1005), // foreground: duration check
            Instant.fromEpochSeconds(1005), // showNow: lastShowInstant
        )
        val clock: () -> Instant = { ticks.removeFirstOrNull() ?: error("no tick") }

        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            clock = clock,
        )
        coordinator.start(scope)
        foreground.value = true

        assertTrue(controller.showCalled)
    }

    @Test
    fun `under minBackgroundDuration does not show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        val ticks = mutableListOf(
            Instant.fromEpochSeconds(1000), // background
            Instant.fromEpochSeconds(1001), // foreground: 1s < 4s → no show
        )
        val clock: () -> Instant = { ticks.removeFirstOrNull() ?: error("no tick") }

        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            clock = clock,
        )
        coordinator.start(scope)
        foreground.value = true

        assertFalse(controller.showCalled)
    }

    @Test
    fun `isBlocked suppresses show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        val ticks = mutableListOf(
            Instant.fromEpochSeconds(1000), // background
            Instant.fromEpochSeconds(1005), // foreground: duration check
        )
        val clock: () -> Instant = { ticks.removeFirstOrNull() ?: error("no tick") }

        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            clock = clock,
        )
        coordinator.isBlocked = true
        coordinator.start(scope)
        foreground.value = true

        assertFalse(controller.showCalled)
    }

    @Test
    fun `cooldown suppresses second show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        // Round 1: bg(1000), fg-duration(1005), fg-lastShow(1005) = 3 ticks
        // Round 2: bg(1010), fg-duration(1020), canShowNow-clock(1020) = 3 ticks
        val ticks = mutableListOf(
            Instant.fromEpochSeconds(1000), // bg #1
            Instant.fromEpochSeconds(1005), // fg #1: duration check
            Instant.fromEpochSeconds(1005), // fg #1: lastShowInstant
            Instant.fromEpochSeconds(1010), // bg #2
            Instant.fromEpochSeconds(1020), // fg #2: duration check
            Instant.fromEpochSeconds(1020), // fg #2: canShowNow checks lastShowInstant → sinceLastShow = 1020-1005 = 15s < 30s → suppressed
        )
        val clock: () -> Instant = { ticks.removeFirstOrNull() ?: error("no tick") }

        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(
                preloadOnStart = false,
                cooldownBetweenShows = 30.seconds,
            ),
            foregroundEvents = foreground,
            clock = clock,
        )
        coordinator.start(scope)

        foreground.value = true
        assertTrue(controller.showCalled)
        controller.showCalled = false

        foreground.value = false
        foreground.value = true

        assertFalse(controller.showCalled, "Second show suppressed by cooldown")
    }

    @Test
    fun `foreign full-screen ad presenting suppresses app-open show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        val ticks = mutableListOf(
            Instant.fromEpochSeconds(1000), // background
            Instant.fromEpochSeconds(1005), // foreground: duration check
        )
        val clock: () -> Instant = { ticks.removeFirstOrNull() ?: error("no tick") }

        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            clock = clock,
        )
        coordinator.start(scope)

        // Another full-screen format (e.g. rewarded) holds the process-wide token.
        manager.holdFullScreenToken()
        foreground.value = true

        assertFalse(controller.showCalled, "App-open must not stack on top of another full-screen ad")
    }

    @Test
    fun `app-open show allowed once no full-screen ad is presenting`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        val ticks = mutableListOf(
            Instant.fromEpochSeconds(1000), // background
            Instant.fromEpochSeconds(1005), // foreground: duration check
            Instant.fromEpochSeconds(1005), // showNow: lastShowInstant
        )
        val clock: () -> Instant = { ticks.removeFirstOrNull() ?: error("no tick") }

        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            clock = clock,
        )
        coordinator.start(scope)

        // Foreign format was presenting, then released the token before the foreground transition.
        val otherFormatToken = manager.holdFullScreenToken()
        manager.fullScreenArbiter.release(otherFormatToken)
        foreground.value = true

        assertTrue(controller.showCalled, "App-open should show once no other full-screen ad is presenting")
    }

    @Test
    fun `reload triggered after show when not ready`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        // Controller starts ready (simulatedLoadState=Loaded) → show triggers.
        // The show() sets ready=false (see FakeAppOpenAdController.show()),
        // so onForeground() then calls controller.load().
        val controller = FakeAppOpenAdController(simulatedShowResult = AdShowResult.Shown)
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        val ticks = mutableListOf(
            Instant.fromEpochSeconds(1000), // background
            Instant.fromEpochSeconds(1005), // foreground: duration check
            Instant.fromEpochSeconds(1005), // showNow: lastShowInstant
        )
        val clock: () -> Instant = { ticks.removeFirstOrNull() ?: error("no tick") }

        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            clock = clock,
        )
        coordinator.start(scope)
        foreground.value = true

        assertTrue(controller.showCalled)
        assertTrue(controller.loadCalled, "Controller should load because show() set ready=false")
    }

    @Test
    fun `coordinator declines while another format holds the process-wide token`() =
        runTest(UnconfinedTestDispatcher()) {
            val foreground = MutableStateFlow(false)
            val controller = FakeAppOpenAdController()
            val manager = FakeAdManager()
            val scope = CoroutineScope(UnconfinedTestDispatcher())

            // Another full-screen format is presenting. Note this holds the ARBITER, not the
            // isFullScreenPresenting flow — the coordinator no longer reads that flow, because
            // reading it was the TOCTOU this test pins closed.
            manager.holdFullScreenToken()

            val ticks = mutableListOf(
                Instant.fromEpochSeconds(1000),
                Instant.fromEpochSeconds(1005),
                Instant.fromEpochSeconds(1005),
            )
            val clock: () -> Instant = { ticks.removeFirstOrNull() ?: error("no tick") }

            val coordinator = AppOpenAdCoordinator(
                manager = manager,
                controller = controller,
                config = AppOpenConfig(preloadOnStart = false),
                foregroundEvents = foreground,
                clock = clock,
            )
            coordinator.start(scope)
            foreground.value = true

            assertFalse(
                controller.showCalled,
                "app-open must not stack on top of another full-screen ad"
            )
        }

    @Test
    fun `coordinator shows once the other format releases the token`() =
        runTest(UnconfinedTestDispatcher()) {
            val foreground = MutableStateFlow(false)
            val controller = FakeAppOpenAdController()
            val manager = FakeAdManager()
            val scope = CoroutineScope(UnconfinedTestDispatcher())

            val otherFormatToken = manager.holdFullScreenToken()

            val ticks = mutableListOf(
                Instant.fromEpochSeconds(1000), // initial background
                Instant.fromEpochSeconds(1005), // 1st foreground: duration check (gap=5s, but arbiter held)
                Instant.fromEpochSeconds(1010), // 2nd background
                Instant.fromEpochSeconds(1020), // 2nd foreground: duration check (gap=10s, arbiter free)
                Instant.fromEpochSeconds(1020), // showNow: lastShowInstant
            )
            val clock: () -> Instant = { ticks.removeFirstOrNull() ?: error("no tick") }

            val coordinator = AppOpenAdCoordinator(
                manager = manager,
                controller = controller,
                config = AppOpenConfig(preloadOnStart = false),
                foregroundEvents = foreground,
                clock = clock,
            )
            coordinator.start(scope)
            foreground.value = true
            assertFalse(controller.showCalled, "precondition: blocked while the token is held")

            manager.fullScreenArbiter.release(otherFormatToken)
            foreground.value = false
            foreground.value = true

            assertTrue(
                controller.showCalled,
                "the coordinator must recover once the token is released"
            )
        }
}
