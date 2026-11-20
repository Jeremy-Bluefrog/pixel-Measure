package com.example.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class AdaptiveWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class AdaptiveHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class FoldPosture {
    FLAT,
    TABLETOP,
    BOOK,
    SEPARATING_HORIZONTAL,
    SEPARATING_VERTICAL
}

enum class HingeOcclusion {
    NONE,
    FULL
}

@Immutable
data class AdaptiveWindowState(
    val widthClass: AdaptiveWidthClass = AdaptiveWidthClass.COMPACT,
    val heightClass: AdaptiveHeightClass = AdaptiveHeightClass.MEDIUM,
    val posture: FoldPosture = FoldPosture.FLAT,
    val hingeBoundsInWindow: Rect? = null,
    val isSeparating: Boolean = false,
    val occlusion: HingeOcclusion = HingeOcclusion.NONE
) {
    val isCompactWidth: Boolean
        get() = widthClass == AdaptiveWidthClass.COMPACT

    val isMediumOrExpandedWidth: Boolean
        get() = widthClass != AdaptiveWidthClass.COMPACT

    val isExpandedWidth: Boolean
        get() = widthClass == AdaptiveWidthClass.EXPANDED

    val isCompactHeight: Boolean
        get() = heightClass == AdaptiveHeightClass.COMPACT

    val isTabletop: Boolean
        get() = posture == FoldPosture.TABLETOP || posture == FoldPosture.SEPARATING_HORIZONTAL

    val isBook: Boolean
        get() = posture == FoldPosture.BOOK || posture == FoldPosture.SEPARATING_VERTICAL

    val hasActiveFoldSplit: Boolean
        get() = isTabletop || isBook

    val useNavigationRail: Boolean
        get() = isMediumOrExpandedWidth || isBook

    val useTwoPaneLayout: Boolean
        get() = isMediumOrExpandedWidth || isBook
}

val LocalAdaptiveWindowState = compositionLocalOf { AdaptiveWindowState() }

/**
 * Resolves real-time Material 3 Adaptive WindowSizeClass + Jetpack WindowManager FoldingFeature state.
 * Falls back safely in Robolectric/preview contexts where Activity or WindowLayoutInfo may be absent.
 */
@Composable
fun rememberAdaptiveWindowState(): AdaptiveWindowState {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val sizeClass = windowAdaptiveInfo.windowSizeClass

    val widthClass = when (sizeClass.windowWidthSizeClass) {
        WindowWidthSizeClass.EXPANDED -> AdaptiveWidthClass.EXPANDED
        WindowWidthSizeClass.MEDIUM -> AdaptiveWidthClass.MEDIUM
        else -> AdaptiveWidthClass.COMPACT
    }

    val heightClass = when (sizeClass.windowHeightSizeClass) {
        WindowHeightSizeClass.EXPANDED -> AdaptiveHeightClass.EXPANDED
        WindowHeightSizeClass.COMPACT -> AdaptiveHeightClass.COMPACT
        else -> AdaptiveHeightClass.MEDIUM
    }

    val windowLayoutInfo by produceState<WindowLayoutInfo?>(initialValue = null, key1 = activity) {
        if (activity != null) {
            WindowInfoTracker.getOrCreate(activity)
                .windowLayoutInfo(activity)
                .map<WindowLayoutInfo, WindowLayoutInfo?> { it }
                .catch { emit(null) }
                .collect { info -> value = info }
        } else {
            value = null
        }
    }

    return remember(widthClass, heightClass, windowLayoutInfo) {
        val foldingFeature = windowLayoutInfo?.displayFeatures
            ?.filterIsInstance<FoldingFeature>()
            ?.firstOrNull()
        resolveAdaptiveWindowState(
            widthClass = widthClass,
            heightClass = heightClass,
            foldingFeature = foldingFeature
        )
    }
}

internal fun resolveAdaptiveWindowState(
    widthClass: AdaptiveWidthClass,
    heightClass: AdaptiveHeightClass,
    foldingFeature: FoldingFeature?
): AdaptiveWindowState {
    if (foldingFeature == null) {
        return AdaptiveWindowState(
            widthClass = widthClass,
            heightClass = heightClass,
            posture = FoldPosture.FLAT
        )
    }

    val posture = when {
        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
            foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL -> FoldPosture.TABLETOP

        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> FoldPosture.BOOK

        foldingFeature.isSeparating &&
            foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL -> FoldPosture.SEPARATING_HORIZONTAL

        foldingFeature.isSeparating &&
            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> FoldPosture.SEPARATING_VERTICAL

        else -> FoldPosture.FLAT
    }

    val occlusion = if (foldingFeature.occlusionType == FoldingFeature.OcclusionType.FULL) {
        HingeOcclusion.FULL
    } else {
        HingeOcclusion.NONE
    }

    return AdaptiveWindowState(
        widthClass = widthClass,
        heightClass = heightClass,
        posture = posture,
        hingeBoundsInWindow = Rect(foldingFeature.bounds),
        isSeparating = foldingFeature.isSeparating,
        occlusion = occlusion
    )
}

/**
 * Container-local hinge geometry computed from windowToLocal coordinate transformation.
 * Never assumes a blind 50/50 split when surrounding NavigationRail / status bars offset the container.
 */
@Immutable
data class LocalHingeMetrics(
    val containerWidthPx: Int = 0,
    val containerHeightPx: Int = 0,
    val localTopPx: Float? = null,
    val localBottomPx: Float? = null,
    val localLeftPx: Float? = null,
    val localRightPx: Float? = null
) {
    fun hasValidHorizontalSplit(): Boolean =
        containerHeightPx > 0 && localTopPx != null && localBottomPx != null && localTopPx > 40f && localBottomPx < containerHeightPx - 40f

    fun hasValidVerticalSplit(): Boolean =
        containerWidthPx > 0 && localLeftPx != null && localRightPx != null && localLeftPx > 40f && localRightPx < containerWidthPx - 40f

    fun topPaneHeightDp(density: Density, fallbackFraction: Float = 0.5f): Dp = with(density) {
        val px = if (hasValidHorizontalSplit()) {
            localTopPx!!
        } else {
            (containerHeightPx * fallbackFraction) - 8.dp.toPx()
        }
        px.coerceAtLeast(120.dp.toPx()).toDp()
    }

    fun horizontalHingeGapDp(density: Density): Dp = with(density) {
        val gapPx = if (hasValidHorizontalSplit()) {
            (localBottomPx!! - localTopPx!!).coerceAtLeast(12.dp.toPx())
        } else {
            16.dp.toPx()
        }
        gapPx.toDp()
    }

    fun leftPaneWidthDp(density: Density, fallbackFraction: Float = 0.5f): Dp = with(density) {
        val px = if (hasValidVerticalSplit()) {
            localLeftPx!!
        } else {
            (containerWidthPx * fallbackFraction) - 8.dp.toPx()
        }
        px.coerceAtLeast(160.dp.toPx()).toDp()
    }

    fun verticalHingeGapDp(density: Density): Dp = with(density) {
        val gapPx = if (hasValidVerticalSplit()) {
            (localRightPx!! - localLeftPx!!).coerceAtLeast(12.dp.toPx())
        } else {
            16.dp.toPx()
        }
        gapPx.toDp()
    }
}

@Composable
fun rememberLocalHingeTracker(
    adaptiveState: AdaptiveWindowState
): Pair<Modifier, LocalHingeMetrics> {
    var metrics by remember { mutableStateOf(LocalHingeMetrics()) }
    var lastCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val computeMetrics: (LayoutCoordinates, Rect?) -> LocalHingeMetrics = remember {
        { coords, bounds ->
            val w = coords.size.width
            val h = coords.size.height
            if (bounds == null || w <= 0 || h <= 0) {
                LocalHingeMetrics(containerWidthPx = w, containerHeightPx = h)
            } else {
                val topLeftLocal = coords.windowToLocal(Offset(bounds.left.toFloat(), bounds.top.toFloat()))
                val bottomRightLocal = coords.windowToLocal(Offset(bounds.right.toFloat(), bounds.bottom.toFloat()))
                LocalHingeMetrics(
                    containerWidthPx = w,
                    containerHeightPx = h,
                    localTopPx = topLeftLocal.y.coerceIn(0f, h.toFloat()),
                    localBottomPx = bottomRightLocal.y.coerceIn(0f, h.toFloat()),
                    localLeftPx = topLeftLocal.x.coerceIn(0f, w.toFloat()),
                    localRightPx = bottomRightLocal.x.coerceIn(0f, w.toFloat())
                )
            }
        }
    }

    // Recompute immediately if hinge bounds change without container re-layout
    val currentBounds = adaptiveState.hingeBoundsInWindow
    remember(currentBounds) {
        lastCoords?.let { coords ->
            if (coords.isAttached) {
                metrics = computeMetrics(coords, currentBounds)
            }
        }
        true
    }

    val trackerModifier = Modifier.onGloballyPositioned { coords ->
        lastCoords = coords
        val updated = computeMetrics(coords, currentBounds)
        if (updated != metrics) {
            metrics = updated
        }
    }

    return trackerModifier to metrics
}

private tailrec fun Context.findHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findHostActivity()
        else -> null
    }
}
