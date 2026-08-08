package dev.avinya.admob.showcase.core.mvi

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CounterState(val count: Int = 0)

private sealed interface CounterIntent {
    data object Increment : CounterIntent
    data object Announce : CounterIntent
}

private sealed interface CounterEffect {
    data class Announced(val count: Int) : CounterEffect
}

private class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>(CounterState()) {
    override fun onIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> updateState { copy(count = count + 1) }
            CounterIntent.Announce -> emitEffect(CounterEffect.Announced(state.value.count))
        }
    }
}

class MviViewModelTest {

    @Test
    fun startsAtTheInitialState() {
        assertEquals(CounterState(count = 0), CounterViewModel().state.value)
    }

    @Test
    fun intentsReduceIntoState() {
        val vm = CounterViewModel()

        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Increment)

        assertEquals(CounterState(count = 2), vm.state.value)
    }

    @Test
    fun effectsAreBufferedUntilCollected() = runTest {
        val vm = CounterViewModel()

        // Emitted with no collector attached — a buffered Channel must retain them.
        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Announce)
        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Announce)

        val received = mutableListOf<CounterEffect>()
        val job = launch { vm.effects.collect { received += it } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(
            listOf<CounterEffect>(CounterEffect.Announced(1), CounterEffect.Announced(2)),
            received,
        )
    }
}
