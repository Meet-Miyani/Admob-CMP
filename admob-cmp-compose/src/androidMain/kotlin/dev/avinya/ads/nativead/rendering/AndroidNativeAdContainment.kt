package dev.avinya.ads.nativead.rendering

internal data class NativeAdBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun contains(other: NativeAdBounds): Boolean =
        other.left >= left &&
            other.top >= top &&
            other.right <= right &&
            other.bottom <= bottom
}
