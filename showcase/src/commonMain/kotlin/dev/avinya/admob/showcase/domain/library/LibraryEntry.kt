package dev.avinya.admob.showcase.domain.library

/** One row in the Library, tagged with why it is there. */
data class LibraryEntry(
    val articleId: String,
    val title: String,
    val section: String,
    val readTimeMin: Int,
    val kind: Kind,
) {
    /**
     * Why a row appears in the Library. A single article can qualify under
     * more than one — the union is shown, and the order in which the kinds
     * are listed here is the order in which they appear in the Library list.
     */
    enum class Kind {
        /** The user has bookmarked this article. */
        Bookmarked,

        /** The user opened this article but has not finished it. */
        InProgress,

        /** The user paid to read this premium article (coins or rewarded). */
        Unlocked,
    }
}
