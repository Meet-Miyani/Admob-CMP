package dev.avinya.ads.nativead.layout

import androidx.compose.runtime.Immutable

/**
 * Result of validating an [AdLayout] tree. [errors] are structural problems
 * that prevent rendering; [warnings] indicate missing policy-relevant assets
 * (headline, ad badge, AdChoices).
 */
@Immutable
public data class AdLayoutValidationReport(
    /** Errors that prevent the layout from rendering. */
    val errors: List<AdLayoutValidationIssue> = emptyList(),
    /** Warnings about missing policy-relevant assets. */
    val warnings: List<AdLayoutValidationIssue> = emptyList()
) {
    /** True when there are no errors (warnings are acceptable). */
    public val isValid: Boolean get() = errors.isEmpty()
    /** True when there are warnings. */
    public val hasWarnings: Boolean get() = warnings.isNotEmpty()
}

/**
 * A single validation issue found during [AdLayoutValidator.validate].
 */
@Immutable
public data class AdLayoutValidationIssue(
    /** Machine-readable issue code (e.g., "missing_headline", "empty_container"). */
    val code: String,
    /** Human-readable description of the issue. */
    val message: String,
    /** Path to the offending node in the layout tree (e.g., "root/column[0]"). */
    val nodePath: String
)

/**
 * Validates a native [AdLayout] tree.
 *
 * Missing headline / ad badge / AdChoices space are reported as **warnings, by design** —
 * this mirrors the official AdMob SDKs, which log and still render (the SDK draws its own
 * AdChoices overlay regardless of the layout). Only a layout with no renderable asset at
 * all is an error. The library does not throw or refuse to render on warnings; consumers
 * decide how to react to [AdLayoutValidationReport.warnings].
 */
public object AdLayoutValidator {
    /**
     * Validates a native ad [AdNode] tree. Checks for missing required assets
     * (headline, ad badge, AdChoices) and structural problems.
     *
     * @param root The root node of the layout tree to validate.
     * @return A validation report with errors and warnings.
     */
    public fun validate(root: AdNode): AdLayoutValidationReport {
        val warnings = mutableListOf<AdLayoutValidationIssue>()
        val errors = mutableListOf<AdLayoutValidationIssue>()
        val assets = mutableSetOf<String>()

        fun visit(node: AdNode, path: String) {
            when (node) {
                is AdContainerNode.Row -> {
                    if (node.children.isEmpty()) warnings += warning("empty_container", "Row has no children.", path)
                    node.children.forEachIndexed { index, child -> visit(child, "$path/row[$index]") }
                }
                is AdContainerNode.Column -> {
                    if (node.children.isEmpty()) warnings += warning("empty_container", "Column has no children.", path)
                    node.children.forEachIndexed { index, child -> visit(child, "$path/column[$index]") }
                }
                is AdContainerNode.Box -> {
                    if (node.children.isEmpty()) warnings += warning("empty_container", "Box has no children.", path)
                    node.children.forEachIndexed { index, child -> visit(child, "$path/box[$index]") }
                }
                is AdSpacer -> Unit
                is AdStaticText -> {
                    if (node.text.isBlank()) warnings += warning("blank_static_text", "Static text is blank.", path)
                }
                is AdAssetNode.Headline -> assets += "headline"
                is AdAssetNode.Body -> assets += "body"
                is AdAssetNode.CallToAction -> assets += "call_to_action"
                is AdAssetNode.Icon -> assets += "icon"
                is AdAssetNode.Media -> assets += "media"
                is AdAssetNode.Advertiser -> assets += "advertiser"
                is AdAssetNode.Price -> assets += "price"
                is AdAssetNode.Store -> assets += "store"
                is AdAssetNode.StarRating -> assets += "star_rating"
                is AdAssetNode.AdChoices -> assets += "ad_choices"
                is AdAssetNode.AdBadge -> assets += "ad_badge"
            }
        }

        visit(root, "root")

        if ("headline" !in assets) {
            warnings += warning("missing_headline", "Native layout does not include a headline asset.", "root")
        }
        if ("ad_badge" !in assets) {
            warnings += warning("missing_ad_badge", "Native layout should include a visible ad attribution badge.", "root")
        }
        if ("ad_choices" !in assets) {
            warnings += warning("missing_ad_choices_space", "Native layout should reserve space for the SDK-owned AdChoices overlay.", "root")
        }
        if (assets.none { it in contentAssets }) {
            errors += error("missing_renderable_asset", "Native layout has no renderable ad asset.", "root")
        }

        return AdLayoutValidationReport(errors = errors, warnings = warnings)
    }

    private val contentAssets = setOf(
        "headline",
        "body",
        "call_to_action",
        "icon",
        "media",
        "advertiser",
        "price",
        "store",
        "star_rating"
    )

    private fun warning(code: String, message: String, path: String): AdLayoutValidationIssue =
        AdLayoutValidationIssue(code, message, path)

    private fun error(code: String, message: String, path: String): AdLayoutValidationIssue =
        AdLayoutValidationIssue(code, message, path)
}
