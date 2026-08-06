package dev.avinya.admob.showcase.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Minimal MVI base: one immutable [state], a stream of one-shot [effects],
 * and a single [onIntent] entry point.
 *
 * Effects use a buffered [Channel] rather than a `SharedFlow` so a one-shot
 * — navigating, showing an ad — is delivered exactly once and cannot replay
 * when the UI re-subscribes after a configuration change.
 *
 * @param S immutable screen state
 * @param I user or system intents
 * @param E one-shot side effects
 */
abstract class MviViewModel<S : Any, I : Any, E : Any>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)

    /** The current screen state. Always non-null, always the latest value. */
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)

    /** One-shot effects. Each is delivered to exactly one collector, exactly once. */
    val effects: Flow<E> = _effects.receiveAsFlow()

    /** Reduce the current state. Safe to call from any thread. */
    protected fun updateState(block: S.() -> S) {
        _state.update(block)
    }

    /** Queue a one-shot effect. Buffered, so it survives having no collector yet. */
    protected fun emitEffect(effect: E) {
        _effects.trySend(effect)
    }

    /** Single entry point for everything the UI or system asks of this screen. */
    abstract fun onIntent(intent: I)
}
