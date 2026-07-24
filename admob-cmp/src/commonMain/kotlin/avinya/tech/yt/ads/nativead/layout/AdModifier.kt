package avinya.tech.yt.ads.nativead.layout

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A serialisable, platform-agnostic analogue of Compose [Modifier] for
 * native ad layout nodes. Immutable value object — chaining methods return
 * new copies with the applied change.
 *
 * @param width Width sizing policy (Wrap, Match, or Fixed).
 * @param height Height sizing policy (Wrap, Match, or Fixed).
 * @param minWidthDp Minimum width in dp.
 * @param minHeightDp Minimum height in dp.
 * @param padding Inner padding insets.
 * @param margin Outer margin insets.
 * @param weight Flex weight for linear layouts.
 * @param aspectRatio Width/height aspect ratio.
 * @param backgroundArgb Background colour as ARGB long.
 * @param borderWidthDp Border stroke width in dp.
 * @param borderColorArgb Border colour as ARGB long.
 * @param borderRadiusDp Border corner radius in dp.
 * @param cornerRadiusDp Corner radius in dp (also sets clip shape).
 * @param alpha Opacity (0f–1f).
 * @param maxWidthDp Maximum width constraint in dp.
 * @param maxHeightDp Maximum height constraint in dp.
 * @param offsetXDp Horizontal offset in dp.
 * @param offsetYDp Vertical offset in dp.
 * @param zIndex Stacking order.
 * @param elevationDp Shadow elevation in dp.
 * @param clipShape Clipping shape (None, Bounds, Circle, RoundedCorner).
 * @param display Visibility mode (Visible, Invisible, Gone).
 */
@Immutable
public data class AdModifier(
    val width: AdLayoutSize = AdLayoutSize.Wrap,
    val height: AdLayoutSize = AdLayoutSize.Wrap,
    val minWidthDp: Float? = null,
    val minHeightDp: Float? = null,
    val padding: AdInsets = AdInsets(),
    val margin: AdInsets = AdInsets(),
    val weight: Float? = null,
    val aspectRatio: Float? = null,
    val backgroundArgb: Long? = null,
    val borderWidthDp: Float? = null,
    val borderColorArgb: Long? = null,
    val borderRadiusDp: Float? = null,
    val cornerRadiusDp: Float? = null,
    val alpha: Float = 1f,
    val maxWidthDp: Float? = null,
    val maxHeightDp: Float? = null,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val zIndex: Float = 0f,
    val elevationDp: Float = 0f,
    val clipShape: AdClip = AdClip.None,
    val display: AdDisplay = AdDisplay.Visible
) {
    /** Companion with factory functions that start from an empty [AdModifier]. */
    public companion object {
        /** An empty [AdModifier] with all defaults (no-op modifier). */
        public val empty: AdModifier = AdModifier()

        /** Factory: fills the available width. */
        public fun fillMaxWidth(): AdModifier = empty.fillMaxWidth()
        /** Factory: fills the available height. */
        public fun fillMaxHeight(): AdModifier = empty.fillMaxHeight()
        /** Factory: fills both available width and height. */
        public fun fillMaxSize(): AdModifier = empty.fillMaxSize()
        /** Factory: fills both available width and height. Alias for [fillMaxSize]. */
        public fun matchParentSize(): AdModifier = empty.matchParentSize()
        /** Factory: wraps content width. */
        public fun wrapContentWidth(): AdModifier = empty.wrapContentWidth()
        /** Factory: wraps content height. */
        public fun wrapContentHeight(): AdModifier = empty.wrapContentHeight()
        /** Factory: wraps content width and height. */
        public fun wrapContentSize(): AdModifier = empty.wrapContentSize()
        /** Factory: sets a fixed width. */
        public fun width(width: Dp): AdModifier = empty.width(width)
        /** Factory: sets a fixed height. */
        public fun height(height: Dp): AdModifier = empty.height(height)
        /** Factory: sets a fixed size (same width and height). */
        public fun size(size: Dp): AdModifier = empty.size(size)
        /** Factory: sets a fixed width and height. */
        public fun size(width: Dp, height: Dp): AdModifier = empty.size(width, height)
        /** Factory: sets a minimum width. */
        public fun minWidth(width: Dp): AdModifier = empty.minWidth(width)
        /** Factory: sets a minimum height. */
        public fun minHeight(height: Dp): AdModifier = empty.minHeight(height)
        /** Factory: sets a maximum width. */
        public fun maxWidth(width: Dp): AdModifier = empty.maxWidth(width)
        /** Factory: sets a maximum height. */
        public fun maxHeight(height: Dp): AdModifier = empty.maxHeight(height)
        /** Factory: sets width min/max constraints. */
        public fun widthIn(min: Dp? = null, max: Dp? = null): AdModifier = empty.widthIn(min, max)
        /** Factory: sets height min/max constraints. */
        public fun heightIn(min: Dp? = null, max: Dp? = null): AdModifier = empty.heightIn(min, max)
        /** Factory: sets width and height min/max constraints. */
        public fun sizeIn(minWidth: Dp? = null, minHeight: Dp? = null, maxWidth: Dp? = null, maxHeight: Dp? = null): AdModifier =
            empty.sizeIn(minWidth, minHeight, maxWidth, maxHeight)
        /** Factory: applies uniform padding on all sides. */
        public fun padding(all: Dp): AdModifier = empty.padding(all)
        /** Factory: applies horizontal and vertical padding. */
        public fun padding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): AdModifier = empty.padding(horizontal, vertical)
        /** Factory: applies per-side padding. */
        public fun padding(start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp): AdModifier =
            empty.padding(start, top, end, bottom)
        /** Factory: applies uniform margin on all sides. */
        public fun margin(all: Dp): AdModifier = empty.margin(all)
        /** Factory: applies horizontal and vertical margin. */
        public fun margin(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): AdModifier = empty.margin(horizontal, vertical)
        /** Factory: applies per-side margin. */
        public fun margin(start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp): AdModifier =
            empty.margin(start, top, end, bottom)
        /** Factory: sets flex weight. */
        public fun weight(weight: Float): AdModifier = empty.weight(weight)
        /** Factory: sets aspect ratio. */
        public fun aspectRatio(ratio: Float): AdModifier = empty.aspectRatio(ratio)
        /** Factory: sets a background colour. */
        public fun background(color: Color): AdModifier = empty.background(color)
        /** Factory: draws a border with optional corner radius. */
        public fun border(width: Dp, color: Color, radius: Dp = 0.dp): AdModifier = empty.border(width, color, radius)
        /** Factory: rounds corners and sets clip shape. */
        public fun cornerRadius(radius: Dp): AdModifier = empty.cornerRadius(radius)
        /** Factory: sets opacity. */
        public fun alpha(alpha: Float): AdModifier = empty.alpha(alpha)
        /** Factory: applies an offset. */
        public fun offset(x: Dp = 0.dp, y: Dp = 0.dp): AdModifier = empty.offset(x, y)
        /** Factory: sets z-index (stacking order). */
        public fun zIndex(zIndex: Float): AdModifier = empty.zIndex(zIndex)
        /** Factory: sets shadow elevation. */
        public fun elevation(elevation: Dp): AdModifier = empty.elevation(elevation)
        /** Factory: sets shadow elevation. Alias for [elevation]. */
        public fun shadow(elevation: Dp): AdModifier = empty.shadow(elevation)
        /** Factory: clips content to bounds. */
        public fun clipToBounds(): AdModifier = empty.clipToBounds()
        /** Factory: clips content with rounded corners. */
        public fun clipRounded(radius: Dp): AdModifier = empty.clipRounded(radius)
        /** Factory: clips content to a circle. */
        public fun clipCircle(): AdModifier = empty.clipCircle()
        /** Factory: makes the node visible. */
        public fun visible(): AdModifier = empty.visible()
        /** Factory: makes the node invisible (still occupies space). */
        public fun invisible(): AdModifier = empty.invisible()
        /** Factory: makes the node gone (removed from layout). */
        public fun gone(): AdModifier = empty.gone()
    }

    /** Fills the available width. */
    public fun fillMaxWidth(): AdModifier = copy(width = AdLayoutSize.Match)
    /** Fills the available height. */
    public fun fillMaxHeight(): AdModifier = copy(height = AdLayoutSize.Match)
    /** Fills both available width and height. */
    public fun fillMaxSize(): AdModifier = copy(width = AdLayoutSize.Match, height = AdLayoutSize.Match)
    /** Fills both available width and height. Alias for [fillMaxSize]. */
    public fun matchParentSize(): AdModifier = fillMaxSize()
    /** Wraps content width. */
    public fun wrapContentWidth(): AdModifier = copy(width = AdLayoutSize.Wrap)
    /** Wraps content height. */
    public fun wrapContentHeight(): AdModifier = copy(height = AdLayoutSize.Wrap)
    /** Wraps content width and height. */
    public fun wrapContentSize(): AdModifier = copy(width = AdLayoutSize.Wrap, height = AdLayoutSize.Wrap)
    /** Sets a fixed width. */
    public fun width(width: Dp): AdModifier = copy(width = AdLayoutSize.Fixed(width.value.coerceAtLeast(0f)))
    /** Sets a fixed height. */
    public fun height(height: Dp): AdModifier = copy(height = AdLayoutSize.Fixed(height.value.coerceAtLeast(0f)))
    /** Sets a fixed size (same width and height). */
    public fun size(size: Dp): AdModifier = copy(
        width = AdLayoutSize.Fixed(size.value.coerceAtLeast(0f)),
        height = AdLayoutSize.Fixed(size.value.coerceAtLeast(0f))
    )
    /** Sets a fixed width and height. */
    public fun size(width: Dp, height: Dp): AdModifier = copy(
        width = AdLayoutSize.Fixed(width.value.coerceAtLeast(0f)),
        height = AdLayoutSize.Fixed(height.value.coerceAtLeast(0f))
    )
    /** Sets a minimum width. */
    public fun minWidth(width: Dp): AdModifier = copy(minWidthDp = width.value.coerceAtLeast(0f))
    /** Sets a minimum height. */
    public fun minHeight(height: Dp): AdModifier = copy(minHeightDp = height.value.coerceAtLeast(0f))
    /** Sets a maximum width. */
    public fun maxWidth(width: Dp): AdModifier = copy(maxWidthDp = width.value.coerceAtLeast(0f))
    /** Sets a maximum height. */
    public fun maxHeight(height: Dp): AdModifier = copy(maxHeightDp = height.value.coerceAtLeast(0f))
    /** Sets width min/max constraints. */
    public fun widthIn(min: Dp? = null, max: Dp? = null): AdModifier = copy(
        minWidthDp = min?.value?.coerceAtLeast(0f) ?: minWidthDp,
        maxWidthDp = max?.value?.coerceAtLeast(0f) ?: maxWidthDp
    )
    /** Sets height min/max constraints. */
    public fun heightIn(min: Dp? = null, max: Dp? = null): AdModifier = copy(
        minHeightDp = min?.value?.coerceAtLeast(0f) ?: minHeightDp,
        maxHeightDp = max?.value?.coerceAtLeast(0f) ?: maxHeightDp
    )
    /** Sets width and height min/max constraints. */
    public fun sizeIn(minWidth: Dp? = null, minHeight: Dp? = null, maxWidth: Dp? = null, maxHeight: Dp? = null): AdModifier = copy(
        minWidthDp = minWidth?.value?.coerceAtLeast(0f) ?: minWidthDp,
        minHeightDp = minHeight?.value?.coerceAtLeast(0f) ?: minHeightDp,
        maxWidthDp = maxWidth?.value?.coerceAtLeast(0f) ?: maxWidthDp,
        maxHeightDp = maxHeight?.value?.coerceAtLeast(0f) ?: maxHeightDp
    )
    /** Applies uniform padding on all sides. */
    public fun padding(all: Dp): AdModifier = copy(padding = AdInsets(all.value, all.value, all.value, all.value))
    /** Applies horizontal and vertical padding. */
    public fun padding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): AdModifier =
        copy(padding = AdInsets(horizontal.value, vertical.value, horizontal.value, vertical.value))
    /** Applies per-side padding. */
    public fun padding(start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp): AdModifier =
        copy(padding = AdInsets(start.value, top.value, end.value, bottom.value))
    /** Applies uniform margin on all sides. */
    public fun margin(all: Dp): AdModifier = copy(margin = AdInsets(all.value, all.value, all.value, all.value))
    /** Applies horizontal and vertical margin. */
    public fun margin(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): AdModifier =
        copy(margin = AdInsets(horizontal.value, vertical.value, horizontal.value, vertical.value))
    /** Applies per-side margin. */
    public fun margin(start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp): AdModifier =
        copy(margin = AdInsets(start.value, top.value, end.value, bottom.value))
    /** Sets flex weight (must be > 0). */
    public fun weight(weight: Float): AdModifier = copy(weight = weight.takeIf { it > 0f })
    /** Sets aspect ratio (must be > 0). */
    public fun aspectRatio(ratio: Float): AdModifier = copy(aspectRatio = ratio.takeIf { it > 0f })
    /** Sets a background colour. */
    public fun background(color: Color): AdModifier = copy(backgroundArgb = color.toArgbLong())
    /** Draws a border with optional corner radius. */
    public fun border(width: Dp, color: Color, radius: Dp = 0.dp): AdModifier = copy(
        borderWidthDp = width.value.coerceAtLeast(0f),
        borderColorArgb = color.toArgbLong(),
        borderRadiusDp = radius.value.coerceAtLeast(0f),
        clipShape = if (radius.value > 0f) AdClip.RoundedCorner(radius.value) else clipShape
    )
    /** Rounds corners and sets clip shape. */
    public fun cornerRadius(radius: Dp): AdModifier = copy(
        cornerRadiusDp = radius.value.coerceAtLeast(0f),
        clipShape = AdClip.RoundedCorner(radius.value.coerceAtLeast(0f))
    )
    /** Sets opacity (0f–1f). */
    public fun alpha(alpha: Float): AdModifier = copy(alpha = alpha.coerceIn(0f, 1f))
    /** Applies an offset. */
    public fun offset(x: Dp = 0.dp, y: Dp = 0.dp): AdModifier = copy(offsetXDp = x.value, offsetYDp = y.value)
    /** Sets z-index (stacking order). */
    public fun zIndex(zIndex: Float): AdModifier = copy(zIndex = zIndex)
    /** Sets shadow elevation. */
    public fun elevation(elevation: Dp): AdModifier = copy(elevationDp = elevation.value.coerceAtLeast(0f))
    /** Sets shadow elevation. Alias for [elevation]. */
    public fun shadow(elevation: Dp): AdModifier = elevation(elevation)
    /** Clips content to bounds. */
    public fun clipToBounds(): AdModifier = copy(clipShape = AdClip.Bounds)
    /** Clips content with rounded corners. */
    public fun clipRounded(radius: Dp): AdModifier = copy(clipShape = AdClip.RoundedCorner(radius.value.coerceAtLeast(0f)))
    /** Clips content to a circle. */
    public fun clipCircle(): AdModifier = copy(clipShape = AdClip.Circle)
    /** Makes the node visible. */
    public fun visible(): AdModifier = copy(display = AdDisplay.Visible)
    /** Makes the node invisible (still occupies space). */
    public fun invisible(): AdModifier = copy(display = AdDisplay.Invisible)
    /** Makes the node gone (removed from layout). */
    public fun gone(): AdModifier = copy(display = AdDisplay.Gone)
    /** If [visible] is true, makes visible; otherwise gone. */
    public fun visible(visible: Boolean): AdModifier = if (visible) visible() else gone()
}

/** Sizing strategy for an [AdModifier] dimension. */
@Immutable
public sealed interface AdLayoutSize {
    /** Size is determined by the content's intrinsic size. */
    public data object Wrap : AdLayoutSize
    /** Size fills the available space in the parent. */
    public data object Match : AdLayoutSize
    /** Fixed size in density-independent pixels. */
    public data class Fixed(val dp: Float) : AdLayoutSize
}

/** Directional insets for padding or margin in an [AdModifier]. */
@Immutable
public data class AdInsets(
    /** Leading inset in dp. */
    val startDp: Float = 0f,
    /** Top inset in dp. */
    val topDp: Float = 0f,
    /** Trailing inset in dp. */
    val endDp: Float = 0f,
    /** Bottom inset in dp. */
    val bottomDp: Float = 0f
)

/** Clipping shape for an [AdModifier]. */
@Immutable
public sealed interface AdClip {
    /** No clipping. */
    public data object None : AdClip
    /** Clip to the node's bounding rectangle. */
    public data object Bounds : AdClip
    /** Clip to a rounded rectangle with the given [radiusDp]. */
    public data class RoundedCorner(val radiusDp: Float) : AdClip
    /** Clip to a circle. */
    public data object Circle : AdClip
}

/** Visibility state for an [AdModifier]. */
public enum class AdDisplay { Visible, Invisible, Gone }

internal fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
