@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.avinya.ads.nativead

import GoogleMobileAds.GADNativeAd
import dev.avinya.ads.AdPlacement
import dev.avinya.ads.InternalAdMobCmpApi
import dev.avinya.ads.internal.NativeAdSessionRenderOwner

@InternalAdMobCmpApi
public interface IosNativeAdRenderLease {
    public val adInstanceId: String
    public val ad: GADNativeAd
    public fun release()
}

@InternalAdMobCmpApi
public fun NativeAdSession.acquireIosNativeAdRenderLease(
    slotKey: String,
    placement: AdPlacement,
    rendererId: String,
): IosNativeAdRenderLease? {
    val owner = (this as? NativeAdSessionRenderOwner<LoadedNativeAd>)?.owner ?: return null
    val record = owner.acquireRender(slotKey, placement, rendererId, this) ?: return null
    return object : IosNativeAdRenderLease {
        private var released = false
        override val adInstanceId: String = record.adInstanceId
        override val ad: GADNativeAd = record.ad.ad
        override fun release() {
            if (released) return
            released = true
            owner.releaseRender(slotKey, placement, rendererId, record.recordId, this@acquireIosNativeAdRenderLease)
        }
    }
}
