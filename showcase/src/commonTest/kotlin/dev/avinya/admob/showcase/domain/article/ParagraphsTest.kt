package dev.avinya.admob.showcase.domain.article

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParagraphsTest {

    @Test
    fun splitsOnBlankLines() {
        assertEquals(listOf("one", "two", "three"), splitParagraphs("one\n\ntwo\n\nthree"))
    }

    @Test
    fun ignoresTrailingWhitespaceAndEmptyParagraphs() {
        assertEquals(listOf("one", "two"), splitParagraphs("one\n\n\n\ntwo\n\n  \n"))
    }

    @Test
    fun placesTheInlineAdAfterTheThirdParagraph() {
        assertEquals(3, inlineAdSlotIndex(paragraphCount = 5))
        assertEquals(3, inlineAdSlotIndex(paragraphCount = 4))
    }

    @Test
    fun omitsTheInlineAdWhenTheArticleIsTooShortToCarryIt() {
        // An ad immediately before the last paragraph reads as an interruption
        // rather than a break, so short articles get none.
        assertNull(inlineAdSlotIndex(paragraphCount = 3))
        assertNull(inlineAdSlotIndex(paragraphCount = 1))
    }
}
