package avinya.tech.yt.ads.debug

import avinya.tech.yt.ads.nativead.layout.AdAssetNode
import avinya.tech.yt.ads.nativead.layout.AdContainerNode
import avinya.tech.yt.ads.nativead.layout.AdLayout
import avinya.tech.yt.ads.nativead.layout.AdLayoutSize
import avinya.tech.yt.ads.nativead.layout.AdModifier
import avinya.tech.yt.ads.nativead.layout.AdNode
import avinya.tech.yt.ads.nativead.layout.AdSpacer
import avinya.tech.yt.ads.nativead.layout.AdStaticText
import avinya.tech.yt.ads.nativead.layout.AdTemplates

/** A friendly name + one-line description for a layout card's header. */
internal data class LayoutMeta(val name: String, val description: String)

/**
 * `AdLayout.identity` is a machine key, not a label — showing it as a title renders a wall of
 * pseudo-code. Match the known templates for a real name, and fall back to a short structural
 * summary for a custom layout.
 */
internal fun AdLayout.displayMeta(): LayoutMeta = when (identity) {
    AdTemplates.compact.identity ->
        LayoutMeta("Compact", "Icon, headline and CTA in one row. Best for tight spaces.")
    AdTemplates.medium.identity ->
        LayoutMeta("Medium", "Media, icon and metadata. Good for feeds.")
    AdTemplates.feedCard.identity ->
        LayoutMeta("Feed card", "Full-width media with rich metadata.")
    else -> LayoutMeta(root.structuralName(), "Custom layout")
}

private fun AdNode.structuralName(): String = when (this) {
    is AdContainerNode.Column -> "Column layout"
    is AdContainerNode.Row -> "Row layout"
    is AdContainerNode.Box -> "Box layout"
    else -> "Layout"
}

/**
 * Pretty-prints the layout tree as an indented, DSL-like source string for the Layouts tab's
 * code block. Deliberately lossy: it surfaces the salient structure (nesting, spacing, sizing,
 * assets) a developer scans for, not every modifier field.
 */
internal fun AdLayout.toSpecSource(): String = buildString { appendNode(root, 0) }.trimEnd()

private fun StringBuilder.appendNode(node: AdNode, depth: Int) {
    val indent = "  ".repeat(depth)
    val call = node.callSignature()
    if (node is AdContainerNode && node.children.isNotEmpty()) {
        append(indent).append(call).append(" {\n")
        node.children.forEach { child -> appendNode(child, depth + 1) }
        append(indent).append("}\n")
    } else {
        append(indent).append(call).append('\n')
    }
}

private fun AdNode.callSignature(): String {
    val args = mutableListOf<String>()
    args += modifierTokens(modifier)
    when (this) {
        is AdContainerNode.Row -> if (spacingDp > 0f) args += "spacing=${fmt(spacingDp)}"
        is AdContainerNode.Column -> if (spacingDp > 0f) args += "spacing=${fmt(spacingDp)}"
        is AdContainerNode.Box -> Unit
        is AdStaticText -> args.add(0, "\"${text.take(24)}\"")
        is AdAssetNode.Headline -> maxLines?.let { args += "maxLines=$it" }
        is AdAssetNode.Body -> maxLines?.let { args += "maxLines=$it" }
        is AdAssetNode.AdBadge -> args.add(0, "\"$text\"")
        else -> Unit
    }
    return "${nodeName()}(${args.joinToString(", ")})"
}

private fun AdNode.nodeName(): String = when (this) {
    is AdContainerNode.Row -> "row"
    is AdContainerNode.Column -> "column"
    is AdContainerNode.Box -> "box"
    is AdSpacer -> "spacer"
    is AdStaticText -> "text"
    is AdAssetNode.Headline -> "headline"
    is AdAssetNode.Body -> "body"
    is AdAssetNode.CallToAction -> "callToAction"
    is AdAssetNode.Icon -> "icon"
    is AdAssetNode.Media -> "media"
    is AdAssetNode.Advertiser -> "advertiser"
    is AdAssetNode.Price -> "price"
    is AdAssetNode.Store -> "store"
    is AdAssetNode.StarRating -> "starRating"
    is AdAssetNode.AdChoices -> "adChoices"
    is AdAssetNode.AdBadge -> "adBadge"
}

private fun modifierTokens(m: AdModifier): List<String> {
    val tokens = mutableListOf<String>()
    val widthMatch = m.width == AdLayoutSize.Match
    val heightMatch = m.height == AdLayoutSize.Match
    when {
        widthMatch && heightMatch -> tokens += "fillMaxSize"
        widthMatch -> tokens += "fillMaxWidth"
        heightMatch -> tokens += "fillMaxHeight"
    }
    (m.width as? AdLayoutSize.Fixed)?.let { w ->
        val h = m.height as? AdLayoutSize.Fixed
        tokens += if (h != null && h.dp == w.dp) "size=${fmt(w.dp)}" else "w=${fmt(w.dp)}"
    }
    (m.height as? AdLayoutSize.Fixed)?.let { h ->
        val w = m.width as? AdLayoutSize.Fixed
        if (!(w != null && w.dp == h.dp)) tokens += "h=${fmt(h.dp)}"
    }
    m.weight?.let { tokens += "weight=${fmt(it)}" }
    m.aspectRatio?.let { tokens += "ratio=${fmt(it)}" }
    paddingToken(m)?.let { tokens += it }
    m.cornerRadiusDp?.let { tokens += "radius=${fmt(it)}" }
    if (m.backgroundArgb != null) tokens += "bg"
    if (m.borderWidthDp != null && m.borderColorArgb != null) tokens += "border"
    return tokens
}

private fun paddingToken(m: AdModifier): String? {
    val p = m.padding
    val l = p.startDp
    val t = p.topDp
    val r = p.endDp
    val b = p.bottomDp
    if (l == 0f && t == 0f && r == 0f && b == 0f) return null
    return if (l == t && t == r && r == b) "pad=${fmt(l)}" else "pad=(${fmt(l)},${fmt(t)},${fmt(r)},${fmt(b)})"
}

private fun fmt(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()
