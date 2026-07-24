package avinya.tech.yt.ads


/**
 * Layout geometry supplied by the host for a banner request.
 *
 * Banner controllers are process-cached and have no layout context of their own
 * (see the invariants in `admob-cmp/CLAUDE.md`). Before this type existed, each
 * controller reached for one anyway — Android through an `Activity`, iOS through
 * `UIScreen.mainScreen.bounds`. The iOS path silently produced full-screen width in
 * iPad split view, Slide Over and popovers, sizing every banner wrong with no error.
 *
 * `BannerAdView` supplies this automatically from its `BoxWithConstraints` measurement.
 * Headless callers must supply it themselves.
 *
 * @property widthDp the container width the ad should be sized to, in dp. Must be positive.
 */
public data class BannerGeometry(public val widthDp: Int) {
    init {
        require(widthDp > 0) { "BannerGeometry.widthDp must be positive, was $widthDp" }
    }
}
