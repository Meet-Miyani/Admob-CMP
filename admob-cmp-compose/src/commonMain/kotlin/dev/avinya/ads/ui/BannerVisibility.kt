package dev.avinya.ads.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

internal const val MIN_VIEWABLE_FRACTION: Float = 0.5f

internal fun isViewable(fraction: Float): Boolean = fraction >= MIN_VIEWABLE_FRACTION

internal fun visibleFractionOf(totalArea: Float, visibleArea: Float): Float {
    if (totalArea <= 0f) return 0f
    return (visibleArea / totalArea).coerceIn(0f, 1f)
}

internal fun LayoutCoordinates.visibleFraction(): Float {
    if (!isAttached) return 0f
    val visible = boundsInWindow()
    return visibleFractionOf(
        totalArea = size.width.toFloat() * size.height.toFloat(),
        visibleArea = visible.width * visible.height
    )
}

internal fun Modifier.onBannerViewabilityChanged(onChanged: (Boolean) -> Unit): Modifier =
    onGloballyPositioned { coordinates -> onChanged(isViewable(coordinates.visibleFraction())) }
