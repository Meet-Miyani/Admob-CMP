package avinya.tech.yt.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import kotlin.math.roundToInt

public fun Activity.screenWidthDp(): Int {
    val metrics = resources.displayMetrics
    return (metrics.widthPixels / metrics.density).roundToInt()
}

public fun AdSizePolicy.toAndroidAdSize(activity: Activity, widthDp: Int): AdSize = when (this) {
    is AdSizePolicy.AnchoredAdaptive -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
    is AdSizePolicy.LargeAnchoredAdaptive -> AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp)
    is AdSizePolicy.InlineAdaptive -> maxHeightDp?.let { AdSize.getInlineAdaptiveBannerAdSize(widthDp, it) }
        ?: AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(activity, widthDp)
    is AdSizePolicy.Fixed -> AdSize(widthDp, heightDp)
    is AdSizePolicy.Fluid -> AdSize.FLUID
}
