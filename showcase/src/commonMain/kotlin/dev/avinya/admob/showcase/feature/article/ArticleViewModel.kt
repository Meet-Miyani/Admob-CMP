package dev.avinya.admob.showcase.feature.article

import androidx.lifecycle.viewModelScope
import dev.avinya.ads.AdManager
import dev.avinya.ads.AdManagerStatus
import dev.avinya.admob.showcase.core.mvi.MviViewModel
import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.data.repo.AdStateRepository
import dev.avinya.admob.showcase.data.repo.AdTelemetryRepository
import dev.avinya.admob.showcase.data.repo.ArticleRepository
import dev.avinya.admob.showcase.domain.ad.AdDecision
import dev.avinya.admob.showcase.domain.ad.AdPolicy
import dev.avinya.admob.showcase.domain.ad.AdPolicySnapshot
import dev.avinya.admob.showcase.domain.ad.ShowcasePlacements
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Loads a single article, observes its bookmark state, and persists the
 * reader's scroll fraction as they read.
 *
 * Task 1 keeps `Close` trivial — it just emits [ArticleEffect.NavigateBack].
 * Task 4 will consult the AdPolicy in `onIntent(ArticleIntent.Close)` and may
 * swap that for a `ShowInterstitial` / `AdSuppressed` effect.
 *
 * Task 2 adds the inline native ad. The ad's placement, the SDK status, and
 * the user's master switch are all observed here so the screen can collapse
 * the slot to the surrounding paragraph when ads are off, without the
 * reading position shifting.
 */
class ArticleViewModel(
    private val articles: ArticleRepository,
    private val settings: SettingsRepository,
    private val adState: AdStateRepository,
    private val telemetry: AdTelemetryRepository,
    private val adManager: AdManager,
    private val clock: Clock,
    private val articleId: String,
    private val adPolicy: AdPolicy = AdPolicy(),
) : MviViewModel<ArticleState, ArticleIntent, ArticleEffect>(ArticleState()) {

    init {
        load()
        observeBookmark()
        observeAdGates()
    }

    private fun load() {
        viewModelScope.launch {
            val entityDeferred = async { articles.article(articleId) }
            val progressDeferred = async { articles.progress(articleId) }
            val entity = entityDeferred.await()
            val fraction = progressDeferred.await()
            updateState {
                copy(
                    article = entity,
                    initialProgress = fraction,
                    loading = false,
                )
            }
        }
    }

    private fun observeBookmark() {
        viewModelScope.launch {
            articles.isBookmarked(articleId).collect { bookmarked ->
                updateState { copy(bookmarked = bookmarked) }
            }
        }
    }

    private fun observeAdGates() {
        combine(settings.adsMasterSwitch, adManager.status) { adsEnabled, status ->
            adsEnabled to status
        }
            .onEach { (adsEnabled, status) ->
                updateState {
                    copy(
                        adsEnabled = adsEnabled,
                        sdkReady = status == AdManagerStatus.Ready,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ArticleIntent) {
        when (intent) {
            ArticleIntent.ToggleBookmark -> viewModelScope.launch {
                // Read the current value from state so the optimistic write
                // does not race with the bookmark flow's next emission.
                articles.setBookmarked(articleId, !state.value.bookmarked)
            }
            ArticleIntent.Close -> viewModelScope.launch {
                handleClose()
            }
            is ArticleIntent.ProgressUpdated -> viewModelScope.launch {
                articles.setProgress(articleId, intent.fraction)
            }
        }
    }

    /**
     * The single thing Task 4 is here to do: ask the policy whether an
     * interstitial may show, dispatch the result, and **always** emit
     * [ArticleEffect.NavigateBack] so a suppressed or not-ready ad never
     * blocks the user from leaving the article.
     *
     * `articlesRead` increments *before* the decision, so the 3rd article
     * close (and the 6th, 9th, …) sees `articlesRead = 3` and gets `Show`.
     * The cooldown write lives in [onInterstitialShown] and is invoked from
     * `AdEffectHandler` only on `AdShowResult.Shown` — recording the timestamp
     * here would burn 60 seconds of cooldown for an ad that never rendered.
     *
     * The decision itself is also forwarded to [telemetry] so the Inspector's
     * Events tab can answer "why did no ad appear" rather than just "what
     * happened". `Show` is recorded the same way as `Suppress(<reason>)` —
     * interleaving the two is the point.
     */
    private suspend fun handleClose() {
        adState.incrementArticlesRead()
        val snapshot = AdPolicySnapshot(
            articlesRead = adState.articlesRead.first(),
            millisSinceLastInterstitial = adState.lastInterstitialAt.first()
                ?.let { clock.nowMillis() - it } ?: Long.MAX_VALUE,
            millisSinceColdStart = clock.nowMillis() - adState.coldStartAt,
            canRequestAds = adManager.consent.canRequestAds.value,
            // TODO(phase 5): wire to the rewarded-unlock flow
            wasRewardedUnlock = false,
            adsEnabled = state.value.adsEnabled,
        )
        val decision = adPolicy.decideInterstitial(snapshot)
        telemetry.recordPolicyDecision(ShowcasePlacements.articleInterstitial.id, decision)
        when (decision) {
            AdDecision.Show -> emitEffect(ArticleEffect.ShowInterstitial)
            is AdDecision.Suppress -> emitEffect(ArticleEffect.AdSuppressed(decision.reason))
        }
        emitEffect(ArticleEffect.NavigateBack)
    }

    /**
     * Records a successful interstitial presentation in [adState].
     *
     * Called from `AdEffectHandler`'s `AdShowResult.Shown` branch — not from
     * the decision site — so a `NotReady` or `Failed` ad does not reset the
     * cooldown.
     */
    fun onInterstitialShown() {
        viewModelScope.launch { adState.recordInterstitialShown(clock.nowMillis()) }
    }
}
