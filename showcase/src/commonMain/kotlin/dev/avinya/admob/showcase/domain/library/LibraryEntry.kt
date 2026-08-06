package dev.avinya.admob.showcase.domain.library

/** One row in the Library, tagged with why it is there. */
data class LibraryEntry(
    val articleId: String,
    val title: String,
    val section: String,
    val readTimeMin: Int,
    val kind: Kind,
) {
    enum class Kind { Bookmarked, InProgress, Unlocked }
}
