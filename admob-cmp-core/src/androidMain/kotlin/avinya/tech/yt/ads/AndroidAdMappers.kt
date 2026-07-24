package avinya.tech.yt.ads

import android.os.Bundle
import avinya.tech.yt.ads.nativead.AdChoicesPlacement
import avinya.tech.yt.ads.nativead.NativeAdOptions
import avinya.tech.yt.ads.nativead.NativeMediaAspectRatio
import avinya.tech.yt.ads.nativead.SwipeGestureDirection
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement as GmaAdChoicesPlacement
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdSourceResponseInfo as GmaAdSourceResponseInfo
import com.google.android.libraries.ads.mobile.sdk.common.AdValue as GmaAdValue
import com.google.android.libraries.ads.mobile.sdk.common.BaseRequestBuilder
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration as GmaRequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo as GmaResponseInfo
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions as GmaVideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest

internal fun AdRequestOptions.toAndroidAdRequest(adUnitId: String): AdRequest =
    AdRequest.Builder(adUnitId).applyOptions(this).build()

internal fun AdRequestOptions.toAndroidNativeAdRequest(adUnitId: String, nativeOptions: NativeAdOptions): NativeAdRequest {
    val builder = NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE)).applyOptions(this)
    if (nativeOptions.disableImageLoading) builder.disableImageDownloading()
    builder.setMediaAspectRatio(nativeOptions.mediaAspectRatio.toAndroid())
    builder.setAdChoicesPlacement(nativeOptions.adChoicesPlacement.toAndroid())
    builder.setVideoOptions(
        GmaVideoOptions.Builder()
            .setStartMuted(nativeOptions.videoOptions.startMuted)
            .setCustomControlsRequested(nativeOptions.videoOptions.customControlsRequested)
            .setClickToExpandRequested(nativeOptions.videoOptions.clickToExpandRequested)
            .build()
    )
    nativeOptions.customClickGesture?.let {
        builder.enableCustomClickGestureDirection(it.direction.toAndroid(), it.tapsAllowed)
    }
    return builder.build()
}

internal fun <T : BaseRequestBuilder<T>> T.applyOptions(options: AdRequestOptions): T {
    if (options.skipUninitializedAdapters) skipUninitializedAdapters()
    options.keywords.forEach(::addKeyword)
    options.contentUrl?.let(::setContentUrl)
    if (options.neighboringContentUrls.isNotEmpty()) setNeighboringContentUrls(options.neighboringContentUrls)
    options.requestAgent?.let(::setRequestAgent)
    options.categoryExclusions.forEach(::addCategoryExclusion)
    options.customTargeting.forEach { (key, values) ->
        if (values.size == 1) putCustomTargeting(key, values.first()) else putCustomTargeting(key, values)
    }
    if (options.googleExtras.isNotEmpty()) {
        val bundle = Bundle()
        options.googleExtras.forEach { (key, value) -> bundle.putString(key, value) }
        setGoogleExtrasBundle(bundle)
    }
    options.publisherProvidedId?.let(::setPublisherProvidedId)
    options.placementId?.let(::setPlacementId)
    return this
}

internal fun GlobalRequestConfiguration.toAndroidRequestConfiguration(): GmaRequestConfiguration {
    // NOTE: setTagForChildDirectedTreatment / setTagForUnderAgeOfConsent are deprecated
    // in GMA Next-Gen 1.1.0+ in favor of the single setAgeRestrictedTreatment. We keep
    // the two-flag form deliberately: the common GlobalRequestConfiguration exposes both
    // tags as independent tri-states, which does not map 1:1 onto the collapsed
    // age-restricted enum without a public API change. The deprecated setters remain
    // functional in 1.2.0; revisit if/when they are removed upstream.
    val builder = GmaRequestConfiguration.Builder()
        .setTestDeviceIds(testDeviceIds)
        .setTagForChildDirectedTreatment(tagForChildDirectedTreatment.toAndroidChildTag())
        .setTagForUnderAgeOfConsent(tagForUnderAgeOfConsent.toAndroidUnderAgeTag())
        .setMaxAdContentRating(maxAdContentRating.toAndroid())
        .setPublisherPrivacyPersonalizationState(publisherPrivacyPersonalizationState.toAndroid())
    return builder.build()
}

private fun RequestTag.toAndroidChildTag(): GmaRequestConfiguration.TagForChildDirectedTreatment = when (this) {
    RequestTag.Unspecified -> GmaRequestConfiguration.TagForChildDirectedTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED
    RequestTag.True -> GmaRequestConfiguration.TagForChildDirectedTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
    RequestTag.False -> GmaRequestConfiguration.TagForChildDirectedTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
}

private fun RequestTag.toAndroidUnderAgeTag(): GmaRequestConfiguration.TagForUnderAgeOfConsent = when (this) {
    RequestTag.Unspecified -> GmaRequestConfiguration.TagForUnderAgeOfConsent.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED
    RequestTag.True -> GmaRequestConfiguration.TagForUnderAgeOfConsent.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
    RequestTag.False -> GmaRequestConfiguration.TagForUnderAgeOfConsent.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
}

private fun MaxAdContentRating.toAndroid(): GmaRequestConfiguration.MaxAdContentRating = when (this) {
    MaxAdContentRating.Unspecified -> GmaRequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_UNSPECIFIED
    MaxAdContentRating.General -> GmaRequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G
    MaxAdContentRating.ParentalGuidance -> GmaRequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_PG
    MaxAdContentRating.Teen -> GmaRequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_T
    MaxAdContentRating.MatureAudience -> GmaRequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_MA
}

private fun PublisherPrivacyPersonalizationState.toAndroid(): GmaRequestConfiguration.PublisherPrivacyPersonalizationState = when (this) {
    PublisherPrivacyPersonalizationState.Default -> GmaRequestConfiguration.PublisherPrivacyPersonalizationState.DEFAULT
    PublisherPrivacyPersonalizationState.Enabled -> GmaRequestConfiguration.PublisherPrivacyPersonalizationState.ENABLED
    PublisherPrivacyPersonalizationState.Disabled -> GmaRequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED
}

internal fun NativeMediaAspectRatio.toAndroid(): NativeAd.NativeMediaAspectRatio = when (this) {
    NativeMediaAspectRatio.Unknown -> NativeAd.NativeMediaAspectRatio.UNKNOWN
    NativeMediaAspectRatio.Any -> NativeAd.NativeMediaAspectRatio.ANY
    NativeMediaAspectRatio.Landscape -> NativeAd.NativeMediaAspectRatio.LANDSCAPE
    NativeMediaAspectRatio.Portrait -> NativeAd.NativeMediaAspectRatio.PORTRAIT
    NativeMediaAspectRatio.Square -> NativeAd.NativeMediaAspectRatio.SQUARE
}

internal fun AdChoicesPlacement.toAndroid(): GmaAdChoicesPlacement = when (this) {
    AdChoicesPlacement.TopLeft -> GmaAdChoicesPlacement.TOP_LEFT
    AdChoicesPlacement.TopRight -> GmaAdChoicesPlacement.TOP_RIGHT
    AdChoicesPlacement.BottomRight -> GmaAdChoicesPlacement.BOTTOM_RIGHT
    AdChoicesPlacement.BottomLeft -> GmaAdChoicesPlacement.BOTTOM_LEFT
}

internal fun SwipeGestureDirection.toAndroid(): NativeAd.SwipeGestureDirection = when (this) {
    SwipeGestureDirection.Right -> NativeAd.SwipeGestureDirection.RIGHT
    SwipeGestureDirection.Left -> NativeAd.SwipeGestureDirection.LEFT
    SwipeGestureDirection.Up -> NativeAd.SwipeGestureDirection.UP
    SwipeGestureDirection.Down -> NativeAd.SwipeGestureDirection.DOWN
}

internal fun LoadAdError.toAdError(): AdError =
    AdError(code = code.toString(), message = message, responseInfo = responseInfo.toCommon())

internal fun FullScreenContentError.toAdError(): AdError =
    AdError(code = code.toString(), message = message)

internal fun GmaResponseInfo?.toCommon(): AdResponseInfo? = this?.let {
    AdResponseInfo(
        responseId = responseId,
        adapterClassName = adapterClassName,
        extras = responseExtras.toStringMap(),
        loadedAdNetworkResponseInfo = loadedAdSourceResponseInfo.toCommon(),
        adNetworkResponseInfos = adSourceResponses.mapNotNull { source -> source.toCommon() }
    )
}

private fun GmaAdSourceResponseInfo?.toCommon(): AdNetworkResponseInfo? = this?.let {
    AdNetworkResponseInfo(
        adapterClassName = adapterClassName,
        latencyMillis = latencyMillis,
        error = adError?.let { error -> AdError(code = error.code.toString(), message = error.message) },
        adSourceName = name,
        adSourceId = id,
        adSourceInstanceName = instanceName,
        adSourceInstanceId = instanceId
    )
}

internal fun GmaAdValue.toCommon(): AdValue =
    AdValue(valueMicros = valueMicros, currencyCode = currencyCode, precision = precisionType.toCommon())

private fun PrecisionType.toCommon(): AdValuePrecision = when (this) {
    PrecisionType.ESTIMATED -> AdValuePrecision.Estimated
    PrecisionType.PUBLISHER_PROVIDED -> AdValuePrecision.PublisherProvided
    PrecisionType.PRECISE -> AdValuePrecision.Precise
    PrecisionType.UNKNOWN -> AdValuePrecision.Unknown
}

private fun Bundle?.toStringMap(): Map<String, String> {
    if (this == null || isEmpty) return emptyMap()
    return keySet().associateWith { key -> get(key)?.toString().orEmpty() }
}
