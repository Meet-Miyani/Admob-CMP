package dev.avinya.ads.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import dev.avinya.ads.AdEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RememberEventCallbackTest {

    @Test
    fun `stable callback invokes latest recomposed lambda`() = runTest {
        val first = mutableListOf<AdEvent>()
        val second = mutableListOf<AdEvent>()

        fun addFirst(e: AdEvent) { first.add(e) }
        fun addSecond(e: AdEvent) { second.add(e) }

        val callback = mutableStateOf<(AdEvent) -> Unit>(::addFirst)
        lateinit var dispatch: (AdEvent) -> Unit

        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        val runner = launch { recomposer.runRecomposeAndApplyChanges() }
        val event = AdEvent.OpenedFullScreen("placement")

        composition.setContent {
            dispatch = rememberCurrentEventCallback(callback.value)
        }
        dispatch(event)

        callback.value = ::addSecond
        composition.setContent {
            dispatch = rememberCurrentEventCallback(callback.value)
        }
        dispatch(event)

        assertEquals<List<AdEvent>>(listOf(event), first)
        assertEquals<List<AdEvent>>(listOf(event), second)
        composition.dispose()
        recomposer.close()
        runner.cancelAndJoin()
    }

    private class UnitApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
