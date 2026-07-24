package avinya.tech.yt.ads.nativead


/**
 * Opaque handle to a pooled native ad. Obtained from [NativeAdPool.acquire]
 * and returned via [NativeAdPool.release]. The [tokenId] uniquely identifies
 * the cached ad instance within its pool.
 */
public data class NativeAdToken(
    /** The placement that owns this token's pool. */
    val placementId: String,
    /** Unique identifier for this cached ad instance. */
    val tokenId: String
)
