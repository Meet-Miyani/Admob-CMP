package dev.avinya.ads.nativead

import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.InternalAdMobCmpApi
import dev.avinya.ads.internal.NativeAdSessionRenderOwner

@InternalAdMobCmpApi
public interface AndroidNativeAdRenderLease {
    public val adInstanceId: String
    public val ad: NativeAd
    public fun release()
}

@InternalAdMobCmpApi
public fun NativeAdSession.acquireAndroidRenderLease(
    slotKey: String,
    placement: AdPlacement,
    rendererId: String,
): AndroidNativeAdRenderLease? {
    val owner = (this as? NativeAdSessionRenderOwner<AndroidLoadedNativeAd>)?.owner ?: return null
    val record = owner.acquireRender(slotKey, placement, rendererId, this) ?: return null
    return object : AndroidNativeAdRenderLease {
        private var released = false
        override val adInstanceId: String = record.adInstanceId
        override val ad: NativeAd = record.ad.ad
        override fun release() {
            if (released) return
            released = true
            owner.releaseRender(slotKey, placement, rendererId, record.recordId, this@acquireAndroidRenderLease)
        }
    }
}
