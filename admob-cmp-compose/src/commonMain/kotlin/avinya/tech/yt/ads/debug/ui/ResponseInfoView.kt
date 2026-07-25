package avinya.tech.yt.ads.debug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import avinya.tech.yt.ads.AdNetworkResponseInfo
import avinya.tech.yt.ads.AdResponseInfo

/**
 * The mediation waterfall behind a load. This is the answer to "why did my ad not fill" —
 * every network that was tried, in order, with its latency and its error.
 *
 * Progressive disclosure: callers show a one-line summary and reveal this on demand.
 */
@Composable
internal fun ResponseInfoView(info: AdResponseInfo, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DebugTokens.SpaceXs)) {
        DebugPropertyRow("response id", info.responseId ?: "—")
        DebugPropertyRow("adapter", info.adapterClassName?.substringAfterLast('.') ?: "—")

        if (info.adNetworkResponseInfos.isEmpty()) {
            DebugLabel("no mediation chain reported")
        } else {
            DebugDivider()
            DebugLabel("waterfall (${info.adNetworkResponseInfos.size})")
            info.adNetworkResponseInfos.forEach { network ->
                WaterfallRow(
                    network = network,
                    isWinner = network == info.loadedAdNetworkResponseInfo,
                )
            }
        }

        if (info.extras.isNotEmpty()) {
            DebugDivider()
            DebugLabel("extras")
            info.extras.forEach { (key, value) -> DebugPropertyRow(key, value) }
        }
    }
}

@Composable
private fun WaterfallRow(network: AdNetworkResponseInfo, isWinner: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = DebugTokens.SpaceXs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DebugTokens.SpaceSm)) {
            DebugPill(if (isWinner) StatusStyle.Loaded else StatusStyle.Failed, overrideLabel = "")
            DebugMono(network.adSourceName ?: network.adapterClassName?.substringAfterLast('.') ?: "unknown", primary = true)
        }
        DebugMono(
            listOfNotNull(
                network.latencyMillis?.let { "${it}ms" },
                network.adSourceInstanceName,
                network.error?.message,
            ).joinToString(" · ").ifEmpty { "no detail" }
        )
    }
}
