package avinya.tech.yt.ads.nativead


/**
 * Options for native ad requests and rendering.
 */
public data class NativeAdOptions(
    /** Preferred media aspect ratio. */
    val mediaAspectRatio: NativeMediaAspectRatio = NativeMediaAspectRatio.Any,
    /** Disable image loading by the SDK (load images yourself). */
    val disableImageLoading: Boolean = false,
    /**
     * Request multiple images per image asset (where the creative provides them).
     *
     * **iOS only.** The Android GMA Next-Gen `NativeAdRequest.Builder` exposes no
     * equivalent option, so this flag has no effect on Android.
     */
    val requestMultipleImages: Boolean = false,
    /** Placement of the AdChoices icon. */
    val adChoicesPlacement: AdChoicesPlacement = AdChoicesPlacement.TopRight,
    /** Options for native video ads. */
    val videoOptions: NativeVideoOptions = NativeVideoOptions(),
    /** Custom click gesture. **Android only.** Ignored on iOS. */
    val customClickGesture: NativeCustomClickGesture? = null
)

/** Preferred aspect ratio for native ad media. */
public enum class NativeMediaAspectRatio { Unknown, Any, Landscape, Portrait, Square }

/** Placement of the AdChoices icon overlay. */
public enum class AdChoicesPlacement { TopLeft, TopRight, BottomRight, BottomLeft }

/**
 * Options for native video ads.
 */
public data class NativeVideoOptions(
    /** Start video with audio muted. */
    val startMuted: Boolean = true,
    /** Request custom video controls instead of SDK defaults. */
    val customControlsRequested: Boolean = false,
    /** Allow click-to-expand on the video. */
    val clickToExpandRequested: Boolean = false
)

/**
 * Custom click gesture for native ads.
 * **Android only.** Ignored on iOS.
 */
public data class NativeCustomClickGesture(
    /** Swipe direction that triggers a click. */
    val direction: SwipeGestureDirection,
    /** Allow taps as well as the swipe gesture. */
    val tapsAllowed: Boolean
)

/** Direction of a swipe gesture. */
public enum class SwipeGestureDirection { Right, Left, Up, Down }

/**
 * Information about a native ad's media asset (image or video).
 */
public data class NativeMediaInfo(
    /** Aspect ratio (width/height) of the media, or null if unknown. */
    val aspectRatio: Float?,
    /** True if the media contains video content. */
    val hasVideoContent: Boolean,
    /** Duration in seconds for video content, or null for images. */
    val durationSeconds: Double?
)
