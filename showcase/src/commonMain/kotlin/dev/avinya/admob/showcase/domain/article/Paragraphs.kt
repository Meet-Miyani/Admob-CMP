package dev.avinya.admob.showcase.domain.article

/** Article bodies store paragraphs separated by a blank line. */
fun splitParagraphs(body: String): List<String> =
    body.split("\n\n").map(String::trim).filter(String::isNotEmpty)

/**
 * Index at which the inline native ad is inserted, or null for articles too
 * short to carry one.
 *
 * Requires at least one paragraph after the ad: a break needs something on
 * both sides of it, otherwise it reads as an interruption.
 */
fun inlineAdSlotIndex(paragraphCount: Int): Int? =
    if (paragraphCount > INLINE_AD_AFTER_PARAGRAPH) INLINE_AD_AFTER_PARAGRAPH else null

private const val INLINE_AD_AFTER_PARAGRAPH = 3
