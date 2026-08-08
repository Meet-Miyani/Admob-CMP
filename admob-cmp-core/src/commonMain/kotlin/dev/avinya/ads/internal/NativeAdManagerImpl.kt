@file:OptIn(dev.avinya.ads.InternalAdMobCmpApi::class)

package dev.avinya.ads.internal

import dev.avinya.ads.AdPlacement
import dev.avinya.ads.nativead.NativeAdManager
import dev.avinya.ads.nativead.NativeAdManagerState
import dev.avinya.ads.nativead.NativeAdMemoryPolicy
import dev.avinya.ads.nativead.NativeAdSession
import dev.avinya.ads.nativead.NativeAdSessionPolicy
import dev.avinya.ads.nativead.NativeAdSessionState
import dev.avinya.ads.nativead.NativeAdWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Thin public facade; coordinator cores remain the sole owners of records and work. */
internal class NativeAdManagerImpl<A : Any>(
    policy: NativeAdMemoryPolicy? = null,
    private val platform: NativeAdPlatform<A>,
    private val canRequestAds: () -> Boolean = { true },
    private val eventSink: (dev.avinya.ads.AdEvent) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : NativeAdManager, NativeAdRenderLeaseProvider<A> {
    private val managerLock = FullScreenStateLock()
    private var configuredPolicy: NativeAdMemoryPolicy? = policy
    private var coordinator: NativeAdCoordinatorCore<A>? = policy?.let(::newCoordinator)
    override val policy: NativeAdMemoryPolicy
        get() = checkNotNull(configuredPolicy) { "Native ads are unavailable until AdManager.initialize succeeds." }
    private val _state = MutableStateFlow(
        NativeAdManagerState(0, 0, 0, 0, policy?.hardLimit ?: NativeAdMemoryPolicy().hardLimit),
    )
    override val state: StateFlow<NativeAdManagerState> = _state

    init {
        coordinator?.setStateListener(::publish)
    }

    /** Binds the coordinator once after the real platform manager accepts configuration. */
    fun configure(policy: NativeAdMemoryPolicy) = managerLock.withLock {
        val existing = configuredPolicy
        if (existing == policy) return@withLock
        check(existing == null) {
            "Native ad memory policy is already configured and cannot be changed for this AdManager instance."
        }
        configuredPolicy = policy
        coordinator = newCoordinator(policy)
        _state.value = coordinator!!.managerState()
    }

    fun configuredPolicyOrNull(): NativeAdMemoryPolicy? = managerLock.withLock { configuredPolicy }

    private fun newCoordinator(policy: NativeAdMemoryPolicy): NativeAdCoordinatorCore<A> =
        NativeAdCoordinatorCore(
            memoryPolicy = policy,
            platform = platform,
            scope = scope,
            canRequestAds = canRequestAds,
            eventSink = eventSink,
        ).also { it.setStateListener(::publish) }

    private fun coordinator(): NativeAdCoordinatorCore<A> = managerLock.withLock {
        checkNotNull(coordinator) { "Native ads are unavailable until AdManager.initialize succeeds." }
    }
    private fun coordinatorOrNull(): NativeAdCoordinatorCore<A>? = managerLock.withLock { coordinator }

    override fun session(key: String, policy: NativeAdSessionPolicy): NativeAdSession {
        val coordinator = coordinator()
        val core = coordinator.session(key, policy)
        val generation = checkNotNull(coordinator.sessionGeneration(key))
        publish()
        return Handle(key, policy, generation, core.state)
    }

    override fun closeSession(key: String) { coordinatorOrNull()?.closeSession(key); publish() }
    override fun clear() { coordinatorOrNull()?.clear(); publish() }
    internal fun onConsentRevoked() { coordinatorOrNull()?.onConsentRevoked(); publish() }
    private fun publish() { _state.value = coordinator?.managerState() ?: _state.value }

    override fun acquireRender(slotKey: String, placement: AdPlacement, rendererId: String, session: NativeAdSession): NativeAdRenderRecord<A>? =
        (session as? NativeAdSessionRenderOwner<A>)?.takeIf { it.owner === this }?.let { handle ->
            coordinator().acquireForRender(handle.sessionKey, handle.generation, slotKey, placement, rendererId)
        }

    override fun releaseRender(slotKey: String, placement: AdPlacement, rendererId: String, recordId: NativeAdRecordId, session: NativeAdSession) {
        (session as? NativeAdSessionRenderOwner<A>)?.takeIf { it.owner === this }?.let { handle ->
            coordinator().releaseRenderer(handle.sessionKey, handle.generation, slotKey, placement, recordId, rendererId)
            publish()
        }
    }

    private inner class Handle(
        override val key: String,
        override val policy: NativeAdSessionPolicy,
        override val generation: Long,
        override val state: StateFlow<NativeAdSessionState>,
    ) : NativeAdSession, NativeAdSessionRenderOwner<A> {
        override val owner: NativeAdRenderLeaseProvider<A> get() = this@NativeAdManagerImpl
        override val sessionKey: String get() = key
        private fun current(): Boolean = coordinator().sessionGeneration(key) == generation
        override fun updateWindow(window: NativeAdWindow) {
            check(current()) { "Native ad session '$key' is no longer active." }
            coordinator().updateWindow(key, generation, window); publish()
        }
        override fun deactivate() { if (current()) { coordinator().deactivateSession(key, generation); publish() } }
        override fun close() { if (current()) { coordinator().closeSession(key, generation); publish() } }
    }
}

internal interface NativeAdRenderLeaseProvider<A : Any> {
    fun acquireRender(slotKey: String, placement: AdPlacement, rendererId: String, session: NativeAdSession): NativeAdRenderRecord<A>?
    fun releaseRender(slotKey: String, placement: AdPlacement, rendererId: String, recordId: NativeAdRecordId, session: NativeAdSession)
}

internal interface NativeAdSessionRenderOwner<A : Any> {
    val owner: NativeAdRenderLeaseProvider<A>
    val sessionKey: String
    val generation: Long
}
