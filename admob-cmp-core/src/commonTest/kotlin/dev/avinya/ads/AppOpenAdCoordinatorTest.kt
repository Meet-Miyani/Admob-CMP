package dev.avinya.ads

import dev.avinya.ads.appopen.AppOpenAdCoordinator
import dev.avinya.ads.appopen.AppOpenConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AppOpenAdCoordinatorTest {

    @Test
    fun `foreground event with ready ad and elapsed thresholds triggers show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.start(scope)
        now = 1005.seconds // 5s > 4s minBackgroundDuration
        foreground.value = true

        assertTrue(controller.showCalled)
    }

    @Test
    fun `exact minBackgroundDuration threshold triggers show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false, minBackgroundDuration = 4.seconds),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.start(scope)
        now = 1004.seconds // exact threshold 4s
        foreground.value = true

        assertTrue(controller.showCalled)
    }

    @Test
    fun `under minBackgroundDuration does not show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false, minBackgroundDuration = 4.seconds),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.start(scope)
        now = 1003.99.seconds // 3.99s < 4s → no show
        foreground.value = true

        assertFalse(controller.showCalled)
    }

    @Test
    fun `long background sleep triggers show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.start(scope)
        now = 1000.seconds + 10.hours // 10h sleep
        foreground.value = true

        assertTrue(controller.showCalled)
    }

    @Test
    fun `simulated backward reading is clamped to zero and does not trigger show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false, minBackgroundDuration = 4.seconds),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.start(scope)
        now = 500.seconds // time jumped backward: 500s - 1000s = -500s -> clamped to 0s < 4s
        foreground.value = true

        assertFalse(controller.showCalled)
    }

    @Test
    fun `isBlocked suppresses show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.isBlocked = true
        coordinator.start(scope)
        now = 1005.seconds
        foreground.value = true

        assertFalse(controller.showCalled)
    }

    @Test
    fun `cooldown suppresses second show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(
                preloadOnStart = false,
                minBackgroundDuration = 4.seconds,
                cooldownBetweenShows = 30.seconds,
            ),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.start(scope)

        now = 1005.seconds
        foreground.value = true
        assertTrue(controller.showCalled)
        controller.showCalled = false

        now = 1010.seconds
        foreground.value = false
        now = 1020.seconds // sinceLastShow = 1020-1005 = 15s < 30s -> suppressed by cooldown
        foreground.value = true

        assertFalse(controller.showCalled, "Second show suppressed by cooldown")
    }

    @Test
    fun `foreign full-screen ad presenting suppresses app-open show`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.start(scope)

        manager.holdFullScreenToken()
        now = 1005.seconds
        foreground.value = true

        assertFalse(controller.showCalled, "App-open must not stack on top of another full-screen ad")
    }

    @Test
    fun `app-open show allowed once no full-screen ad is presenting`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController()
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.start(scope)

        val otherFormatToken = manager.holdFullScreenToken()
        manager.fullScreenArbiter.release(otherFormatToken)
        now = 1005.seconds
        foreground.value = true

        assertTrue(controller.showCalled, "App-open should show once no other full-screen ad is presenting")
    }

    @Test
    fun `reload triggered after show when not ready`() = runTest(UnconfinedTestDispatcher()) {
        val foreground = MutableStateFlow(false)
        val controller = FakeAppOpenAdController(simulatedShowResult = AdShowResult.Shown)
        val manager = FakeAdManager()
        val scope = CoroutineScope(UnconfinedTestDispatcher())

        var now = 1000.seconds
        val coordinator = AppOpenAdCoordinator(
            manager = manager,
            controller = controller,
            config = AppOpenConfig(preloadOnStart = false),
            foregroundEvents = foreground,
            elapsedRealtime = { now },
        )
        coordinator.start(scope)
        now = 1005.seconds
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

            manager.holdFullScreenToken()

            var now = 1000.seconds
            val coordinator = AppOpenAdCoordinator(
                manager = manager,
                controller = controller,
                config = AppOpenConfig(preloadOnStart = false),
                foregroundEvents = foreground,
                elapsedRealtime = { now },
            )
            coordinator.start(scope)
            now = 1005.seconds
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

            var now = 1000.seconds
            val coordinator = AppOpenAdCoordinator(
                manager = manager,
                controller = controller,
                config = AppOpenConfig(preloadOnStart = false),
                foregroundEvents = foreground,
                elapsedRealtime = { now },
            )
            coordinator.start(scope)
            now = 1005.seconds
            foreground.value = true
            assertFalse(controller.showCalled, "precondition: blocked while the token is held")

            manager.fullScreenArbiter.release(otherFormatToken)
            now = 1010.seconds
            foreground.value = false
            now = 1020.seconds
            foreground.value = true

            assertTrue(
                controller.showCalled,
                "the coordinator must recover once the token is released"
            )
        }
}
