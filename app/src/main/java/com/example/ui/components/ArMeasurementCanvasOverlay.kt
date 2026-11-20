package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.TranslationManager
import com.example.logic.ar.ArMath
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
import com.example.ui.theme.loadRoundedNativeTypeface
import com.example.ui.viewmodel.MeasureViewModel
import com.example.ui.viewmodel.Point3D
import kotlin.math.*

// Pre-allocated static dash arrays, quantized PathEffect lookup tables, and box edges to eliminate per-frame GC allocations
private val DASH_10_6 = floatArrayOf(10f, 6f)
private val DASH_10_8 = floatArrayOf(10f, 8f)
private val DASH_14_10 = floatArrayOf(14f, 10f)
private val DASH_10_6_CACHE = Array(32) { i -> PathEffect.dashPathEffect(DASH_10_6, i * 0.5f) }
private val DASH_10_8_CACHE = Array(36) { i -> PathEffect.dashPathEffect(DASH_10_8, i * 0.5f) }
private val DASH_14_10_CACHE = Array(48) { i -> PathEffect.dashPathEffect(DASH_14_10, i * 0.5f) }

private fun getDash10_6(phase: Float): PathEffect {
    val p = ((phase % 16f) + 16f) % 16f
    return DASH_10_6_CACHE[((p * 2f).toInt()).coerceIn(0, 31)]
}

private fun getDash10_8(phase: Float): PathEffect {
    val p = ((phase % 18f) + 18f) % 18f
    return DASH_10_8_CACHE[((p * 2f).toInt()).coerceIn(0, 35)]
}

private fun getDash14_10(phase: Float): PathEffect {
    val p = ((phase % 24f) + 24f) % 24f
    return DASH_14_10_CACHE[((p * 2f).toInt()).coerceIn(0, 47)]
}

private val BOX_EDGE_A = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3)
private val BOX_EDGE_B = intArrayOf(1, 2, 3, 0, 5, 6, 7, 4, 4, 5, 6, 7)

/**
 * 繪製專業 AR 空間測量準心 (Professional Multi-Style AR HUD Targeting Reticle)
 * 完美支援四大風格：DOUBLE_RING (經典雙環), PRECISION_CROSSHAIR (高精十字), MINIMAL_DOT (極簡圓點), HOLOGRAPHIC_RADAR (全息雷達)
 */
private fun DrawScope.drawArReticle(
    center: Offset,
    style: String,
    primaryColor: Color,
    accentColor: Color,
    isSnapped: Boolean,
    pulseScale: Float,
    snapScale: Float,
    dashPhase: Float
) {
    if (center.x.isNaN() || center.y.isNaN() || center.x < -2000f || center.y < -2000f) return

    val activeColor = if (isSnapped) accentColor else primaryColor
    val basePulse = pulseScale.coerceIn(0.8f, 1.4f)
    val snapFactor = snapScale.coerceIn(0.85f, 1.35f)

    when (style) {
        "PRECISION_CROSSHAIR" -> {
            // 1. 高精度十字準星：中央精準定位光標 + 軸向刻度 + 吸附時鎖定邊角
            val armLen = 22.dp.toPx() * snapFactor
            val gap = 7.dp.toPx()
            val strokeW = if (isSnapped) 2.2.dp.toPx() else 1.8.dp.toPx()

            // 核心十字線條
            drawLine(activeColor, Offset(center.x - armLen, center.y), Offset(center.x - gap, center.y), strokeW, cap = StrokeCap.Round)
            drawLine(activeColor, Offset(center.x + gap, center.y), Offset(center.x + armLen, center.y), strokeW, cap = StrokeCap.Round)
            drawLine(activeColor, Offset(center.x, center.y - armLen), Offset(center.x, center.y - gap), strokeW, cap = StrokeCap.Round)
            drawLine(activeColor, Offset(center.x, center.y + gap), Offset(center.x, center.y + armLen), strokeW, cap = StrokeCap.Round)

            // 刻度微標 (Sub-millimeter Ticks)
            val tickHalf = 3.2.dp.toPx()
            val t1 = 13.dp.toPx()
            drawLine(activeColor.copy(alpha = 0.85f), Offset(center.x - t1, center.y - tickHalf), Offset(center.x - t1, center.y + tickHalf), 1.2.dp.toPx())
            drawLine(activeColor.copy(alpha = 0.85f), Offset(center.x + t1, center.y - tickHalf), Offset(center.x + t1, center.y + tickHalf), 1.2.dp.toPx())
            drawLine(activeColor.copy(alpha = 0.85f), Offset(center.x - tickHalf, center.y - t1), Offset(center.x + tickHalf, center.y - t1), 1.2.dp.toPx())
            drawLine(activeColor.copy(alpha = 0.85f), Offset(center.x - tickHalf, center.y + t1), Offset(center.x + tickHalf, center.y + t1), 1.2.dp.toPx())

            // 磁吸鎖定時呈現 45度 戰術防護角
            if (isSnapped) {
                val bracketDist = 18.dp.toPx()
                val bracketLen = 6.dp.toPx()
                drawLine(activeColor, Offset(center.x - bracketDist, center.y - bracketDist), Offset(center.x - bracketDist + bracketLen, center.y - bracketDist), 2.dp.toPx())
                drawLine(activeColor, Offset(center.x - bracketDist, center.y - bracketDist), Offset(center.x - bracketDist, center.y - bracketDist + bracketLen), 2.dp.toPx())

                drawLine(activeColor, Offset(center.x + bracketDist, center.y - bracketDist), Offset(center.x + bracketDist - bracketLen, center.y - bracketDist), 2.dp.toPx())
                drawLine(activeColor, Offset(center.x + bracketDist, center.y - bracketDist), Offset(center.x + bracketDist, center.y - bracketDist + bracketLen), 2.dp.toPx())

                drawLine(activeColor, Offset(center.x - bracketDist, center.y + bracketDist), Offset(center.x - bracketDist + bracketLen, center.y + bracketDist), 2.dp.toPx())
                drawLine(activeColor, Offset(center.x - bracketDist, center.y + bracketDist), Offset(center.x - bracketDist, center.y + bracketDist - bracketLen), 2.dp.toPx())

                drawLine(activeColor, Offset(center.x + bracketDist, center.y + bracketDist), Offset(center.x + bracketDist - bracketLen, center.y + bracketDist), 2.dp.toPx())
                drawLine(activeColor, Offset(center.x + bracketDist, center.y + bracketDist), Offset(center.x + bracketDist, center.y + bracketDist - bracketLen), 2.dp.toPx())
            }

            // 核心精準實心白點
            drawCircle(activeColor, center = center, radius = 3.8.dp.toPx(), style = Stroke(width = 1.4.dp.toPx()))
            drawCircle(Color.White, center = center, radius = 2.8.dp.toPx())
        }
        "MINIMAL_DOT" -> {
            // 2. 極簡精準圓點
            val dotRadius = if (isSnapped) 5.5.dp.toPx() else 4.6.dp.toPx()
            val haloRadius = (16.dp.toPx() * basePulse).coerceIn(12.dp.toPx(), 26.dp.toPx())
            drawCircle(
                color = activeColor.copy(alpha = if (isSnapped) 0.55f else 0.22f),
                center = center,
                radius = haloRadius,
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawCircle(Color.White, center = center, radius = dotRadius)
            drawCircle(activeColor, center = center, radius = dotRadius * 0.45f)
        }
        "HOLOGRAPHIC_RADAR" -> {
            // 3. 全息雷達準星
            val radarRadius = (20.dp.toPx() * basePulse * snapFactor).coerceAtLeast(14.dp.toPx())
            val sweepStart = ((dashPhase * 160f) % 360f)
            drawArc(
                color = activeColor,
                startAngle = sweepStart,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(center.x - radarRadius, center.y - radarRadius),
                size = Size(radarRadius * 2f, radarRadius * 2f),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = activeColor.copy(alpha = 0.35f),
                startAngle = sweepStart + 180f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(center.x - radarRadius, center.y - radarRadius),
                size = Size(radarRadius * 2f, radarRadius * 2f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
            val bSize = 6.dp.toPx()
            val bDist = radarRadius + 5.dp.toPx()
            drawLine(activeColor, Offset(center.x - bDist, center.y - bDist), Offset(center.x - bDist + bSize, center.y - bDist), 1.8.dp.toPx())
            drawLine(activeColor, Offset(center.x - bDist, center.y - bDist), Offset(center.x - bDist, center.y - bDist + bSize), 1.8.dp.toPx())
            drawLine(activeColor, Offset(center.x + bDist, center.y - bDist), Offset(center.x + bDist - bSize, center.y - bDist), 1.8.dp.toPx())
            drawLine(activeColor, Offset(center.x + bDist, center.y - bDist), Offset(center.x + bDist, center.y - bDist + bSize), 1.8.dp.toPx())
            drawLine(activeColor, Offset(center.x - bDist, center.y + bDist), Offset(center.x - bDist + bSize, center.y + bDist), 1.8.dp.toPx())
            drawLine(activeColor, Offset(center.x - bDist, center.y + bDist), Offset(center.x - bDist, center.y + bDist - bSize), 1.8.dp.toPx())
            drawLine(activeColor, Offset(center.x + bDist, center.y + bDist), Offset(center.x + bDist - bSize, center.y + bDist), 1.8.dp.toPx())
            drawLine(activeColor, Offset(center.x + bDist, center.y + bDist), Offset(center.x + bDist, center.y + bDist - bSize), 1.8.dp.toPx())

            drawCircle(Color.White, center = center, radius = 3.2.dp.toPx())
        }
        else -> { // "DOUBLE_RING" (預設雙同心環)
            val outerRadius = (22.dp.toPx() * basePulse * snapFactor).coerceAtLeast(14.dp.toPx())
            val innerRadius = (12.dp.toPx() * snapFactor).coerceAtLeast(8.dp.toPx())

            // 外環
            drawCircle(
                color = activeColor,
                center = center,
                radius = outerRadius,
                style = Stroke(width = 2.4.dp.toPx())
            )
            // 內環 (半透明輔助對齊)
            drawCircle(
                color = activeColor.copy(alpha = if (isSnapped) 0.85f else 0.45f),
                center = center,
                radius = innerRadius,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 核心實心白點
            val dotRadius = if (isSnapped) 5.2.dp.toPx() else 4.6.dp.toPx()
            drawCircle(
                color = activeColor.copy(alpha = 0.75f),
                center = center,
                radius = dotRadius + 1.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                center = center,
                radius = dotRadius
            )
        }
    }
}

/**
 * 繪製已鎖定測量錨點標記 (Confirmed 3D Spatial Anchor Marker)
 */
private fun DrawScope.drawArMeasurementAnchor(
    center: Offset,
    index: Int,
    label: String?,
    originLabel: String,
    primaryColor: Color,
    isOrigin: Boolean,
    nodeLabelPaint: android.graphics.Paint,
    badgeBgPaint: android.graphics.Paint
) {
    if (center.x.isNaN() || center.y.isNaN() || center.x < -2000f || center.y < -2000f) return

    val anchorColor = if (isOrigin) Color(0xFFFFB300) else primaryColor
    val outerRadius = if (isOrigin) 14.dp.toPx() else 11.dp.toPx()
    val dotRadius = if (isOrigin) 5.6.dp.toPx() else 4.8.dp.toPx()

    // 1. 外圍錨點環
    drawCircle(
        color = anchorColor,
        center = center,
        radius = outerRadius,
        style = Stroke(width = 2.6.dp.toPx())
    )

    // 2. 柔光實心底層
    drawCircle(
        color = anchorColor.copy(alpha = 0.22f),
        center = center,
        radius = outerRadius
    )

    // 3. 核心白色定位點
    drawCircle(
        color = Color.White,
        center = center,
        radius = dotRadius
    )

    // 4. 錨點序號小徽章
    val badgeChar = if (index == 0) originLabel else "${index + 1}"
    drawContext.canvas.nativeCanvas.apply {
        val tw = nodeLabelPaint.measureText(badgeChar)
        val th = nodeLabelPaint.textSize
        val bx = center.x
        val by = center.y - outerRadius - 12.dp.toPx()
        val padH = 10f
        val padV = 5f

        badgeBgPaint.color = anchorColor.toArgb()
        drawRoundRect(bx - tw / 2f - padH, by - th - padV, bx + tw / 2f + padH, by + 2f + padV, 14f, 14f, badgeBgPaint)

        nodeLabelPaint.color = if (isOrigin) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        drawText(badgeChar, bx, by, nodeLabelPaint)
    }
}

/**
 * 專業硬體加速 3D AR 空間測量繪製畫布 (Hardware-Accelerated AR Measurement Canvas Overlay)
 *
 * 核心渲染能力：
 * 1. 支援多元測量模式幾何繪製：直線距離、封閉多邊形面積、空間垂準高度、圓形直徑與空間夾角弧形。
 * 2. 支援四大準心風格 (DOUBLE_RING, PRECISION_CROSSHAIR, MINIMAL_DOT, HOLOGRAPHIC_RADAR) 與磁吸鎖定回饋。
 * 3. 支援 3D 透視輔助網格 (PERSPECTIVE_GRID, DOT_MATRIX, OFF) 與特徵點雲空間視覺化。
 * 4. 支援字體大小 (COMPACT, STANDARD, LARGE)、標籤透明度 (SOLID, GLASS, CLEAR) 與線寬自適應。
 * 5. 全向 3D 空間坐標投影與 2D 低通濾波，徹底消滅抖動與重組卡頓。
 */
@Composable
fun ArMeasurementCanvasOverlay(
    viewModel: MeasureViewModel,
    dashPhase: State<Float>,
    reticlePulseScale: State<Float>,
    snapScaleAnimated: State<Float>,
    modifier: Modifier = Modifier
) {
    val themePrimary = MaterialTheme.colorScheme.primary
    val colorSecondary = MaterialTheme.colorScheme.secondary
    val themeTertiary = MaterialTheme.colorScheme.tertiary
    val colorPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val colorOnPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val colorOnPrimary = MaterialTheme.colorScheme.onPrimary
    val currentLang = LocalAppLanguage.current
    val originLabel = tr("canvas_origin")

    // High-frequency AR states collected as State<T> objects (read inside Canvas {} only)
    // so 30/60fps telemetry updates invalidate only the Draw phase and avoid Compose recomposition.
    val viewMatrixState = viewModel.viewMatrix.collectAsStateWithLifecycle()
    val projectionMatrixState = viewModel.projectionMatrix.collectAsStateWithLifecycle()
    val liveTargetPointState = viewModel.liveTargetPoint.collectAsStateWithLifecycle()
    val liveTargetScreenPosState = viewModel.liveTargetScreenPos.collectAsStateWithLifecycle()
    val liveDistanceMetersState = viewModel.liveDistanceMeters.collectAsStateWithLifecycle()
    val isSnappedState = viewModel.isSnapped.collectAsStateWithLifecycle()
    val multiSampleProgressState = viewModel.multiSampleProgress.collectAsStateWithLifecycle()
    val isMultiSampleLockedState = viewModel.isMultiSampleLocked.collectAsStateWithLifecycle()
    val autoDetectedTypeState = viewModel.autoDetectedType.collectAsStateWithLifecycle()
    val liveSurfaceNormalState = viewModel.liveSurfaceNormal.collectAsStateWithLifecycle()
    val isSurfaceNormalLockedState = viewModel.isSurfaceNormalLocked.collectAsStateWithLifecycle()

    // Low-frequency configuration states
    val isHorizontallyMirrored by viewModel.isHorizontallyMirrored.collectAsStateWithLifecycle()
    val subMode by viewModel.cameraSubMode.collectAsStateWithLifecycle()
    val selectedUnit by viewModel.selectedUnit.collectAsStateWithLifecycle()
    val lineThickness by viewModel.lineThickness.collectAsStateWithLifecycle()
    val reticleStyle by viewModel.reticleStyle.collectAsStateWithLifecycle()
    val arAccentPalette by viewModel.arAccentPalette.collectAsStateWithLifecycle()
    val arFontSize by viewModel.arFontSize.collectAsStateWithLifecycle()
    val badgeOpacity by viewModel.badgeOpacity.collectAsStateWithLifecycle()
    val gridOverlayStyle by viewModel.gridOverlayStyle.collectAsStateWithLifecycle()

    val colorPrimary = remember(arAccentPalette, themePrimary) {
        when (arAccentPalette) {
            "PIXEL_BLUE" -> Color(0xFF4C8DF6)
            "NEON_LIME" -> Color(0xFF22C55E)
            "AMBER_GOLD" -> Color(0xFFF59E0B)
            "CORAL_ROSE" -> Color(0xFFF43F5E)
            else -> themePrimary
        }
    }
    val colorTertiary = remember(arAccentPalette, themeTertiary) {
        when (arAccentPalette) {
            "PIXEL_BLUE" -> Color(0xFF38BDF8)
            "NEON_LIME" -> Color(0xFFA3E635)
            "AMBER_GOLD" -> Color(0xFFFDE047)
            "CORAL_ROSE" -> Color(0xFFFB7185)
            else -> themeTertiary
        }
    }

    val badgeAlpha = when (badgeOpacity) {
        "SOLID" -> 0.95f
        "CLEAR" -> 0.45f
        else -> 0.75f
    }

    val arTextSizePx = when (arFontSize) {
        "COMPACT" -> 28f
        "LARGE" -> 44f
        else -> 36f
    }

    val capturedPoints = viewModel.capturedPoints

    val context = LocalContext.current
    val roundedBoldTypeface = remember(context) {
        loadRoundedNativeTypeface(context, isBold = true)
    }

    // Cached paints for hardware canvas badge rendering
    val badgeBgPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }
    }
    val badgeBorderPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f
        }
    }
    val badgeTextPaint = remember(arTextSizePx, roundedBoldTypeface) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = arTextSizePx
            typeface = roundedBoldTypeface
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val nodeLabelPaint = remember(roundedBoldTypeface) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            typeface = roundedBoldTypeface
            textAlign = android.graphics.Paint.Align.CENTER
            color = android.graphics.Color.WHITE
        }
    }

    // Reusable Path & primitive caches (Zero per-frame allocations)
    val areaCachedPath = remember { Path() }
    val canvasCirclePath = remember { Path() }
    val canvasCircleBloomPath = remember { Path() }
    val canvasCircleBasis = remember { DoubleArray(9) }
    val canvasCircleScratchX = remember { FloatArray(48) }
    val canvasCircleScratchY = remember { FloatArray(48) }
    val reticleBasis = remember { DoubleArray(9) }
    val reticleOuterX = remember { FloatArray(36) }
    val reticleOuterY = remember { FloatArray(36) }
    val reticleInnerX = remember { FloatArray(36) }
    val reticleInnerY = remember { FloatArray(36) }
    val reticleOuterPath = remember { Path() }
    val reticleInnerPath = remember { Path() }
    val reticleSweepPath = remember { Path() }
    val reticleSmoothedNormal = remember { floatArrayOf(0f, 1f, 0f) }
    val hasReticleNormal = remember { booleanArrayOf(false) }
    val reticleSmoothedRadius = remember { floatArrayOf(-1f) }
    val smoothedCircleCenter = remember { doubleArrayOf(0.0, 0.0, 0.0) }
    val smoothedCircleRadius = remember { doubleArrayOf(-1.0) }
    val hasSmoothedCircle = remember { booleanArrayOf(false) }
    val tempProjHolder = remember { FloatArray(2) }
    val tempProjHolder2 = remember { FloatArray(2) }
    val reticleHolderProj = remember { ScreenPoint() }
    val validPtsBuffer = remember { Array(128) { ScreenPoint() } }
    val box3dX = remember { DoubleArray(8) }
    val box3dY = remember { DoubleArray(8) }
    val box3dZ = remember { DoubleArray(8) }
    val box2dX = remember { FloatArray(8) }
    val box2dY = remember { FloatArray(8) }
    val box2dValid = remember { BooleanArray(8) }
    val cachedAngleFmt = remember {
        object {
            var lastAngle = Double.NaN
            var text = ""
        }
    }
    val cachedVolFmt = remember {
        object {
            var lastVol = Double.NaN
            var lastLen = Double.NaN
            var lastDepth = Double.NaN
            var lastHeight = Double.NaN
            var lastLang = ""
            var text = ""
        }
    }

    val circleCompletionPulse = remember { Animatable(0f) }

    // Zero-allocation reusable coordinate buffer pool for 60fps canvas drawing (expanded to 128 elements)
    val projectedPool = remember { ProjectedPointPool(128) }

    val isCircleSubMode = subMode == 4

    LaunchedEffect(isCircleSubMode, capturedPoints.size) {
        if (isCircleSubMode && capturedPoints.size >= 3) {
            circleCompletionPulse.snapTo(0f)
            circleCompletionPulse.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        } else if (!isCircleSubMode || capturedPoints.isEmpty()) {
            circleCompletionPulse.snapTo(0f)
            hasSmoothedCircle[0] = false
            smoothedCircleRadius[0] = -1.0
        }
    }

    // Reset cached circle/normal smoothing on measurement clear
    LaunchedEffect(capturedPoints.size) {
        if (capturedPoints.isEmpty()) {
            hasSmoothedCircle[0] = false
            smoothedCircleRadius[0] = -1.0
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
        val viewMatrix = viewMatrixState.value
        val projectionMatrix = projectionMatrixState.value
        val liveTargetPoint = liveTargetPointState.value
        val liveTargetScreenPos = liveTargetScreenPosState.value
        val liveDistanceMeters = liveDistanceMetersState.value
        val isSnapped = isSnappedState.value
        val multiSampleProgress = multiSampleProgressState.value
        val isMultiSampleLocked = isMultiSampleLockedState.value
        val autoDetectedType = autoDetectedTypeState.value
        val liveSurfaceNormal = liveSurfaceNormalState.value
        val isSurfaceNormalLocked = isSurfaceNormalLockedState.value
        val currentDashPhase = dashPhase.value

        val primaryContainerBgArgb = Color(colorPrimaryContainer.red, colorPrimaryContainer.green, colorPrimaryContainer.blue, badgeAlpha).toArgb()
        val primaryBadgeBgArgb = Color(colorPrimary.red, colorPrimary.green, colorPrimary.blue, badgeAlpha).toArgb()
        val tertiaryBadgeBgArgb = Color(colorTertiary.red, colorTertiary.green, colorTertiary.blue, badgeAlpha).toArgb()
        val primaryBorderArgb = colorPrimary.copy(alpha = 0.8f).toArgb()
        val whiteBorderArgb = Color.White.copy(alpha = 0.85f).toArgb()
        val onPrimaryContainerArgb = colorOnPrimaryContainer.toArgb()
        val onPrimaryArgb = colorOnPrimary.toArgb()

        val screenW = size.width.toInt()
        val screenH = size.height.toInt()
        val screenCenter = Offset(size.width / 2f, size.height / 2f)

        val hasValidMatrices = viewMatrix.size >= 16 && projectionMatrix.size >= 16 &&
            ArMath.isMatrixValid(viewMatrix) && ArMath.isMatrixValid(projectionMatrix)

        // Project live 3D target point directly with current frame matrices so rubber-band line,
        // 3D reticle ring, and crosshair dot remain 1:1 pixel-aligned with zero 2D EMA trailing lag.
        val currentReticlePos = if (liveTargetPoint != null && hasValidMatrices) {
            val ok = ArMath.projectCoordsToScreen(
                liveTargetPoint.x,
                liveTargetPoint.y,
                liveTargetPoint.z,
                viewMatrix,
                projectionMatrix,
                screenW,
                screenH,
                tempProjHolder,
                isHorizontallyMirrored
            )
            if (ok && tempProjHolder[0] in (-120f)..(size.width + 120f) &&
                tempProjHolder[1] in (-120f)..(size.height + 120f)
            ) {
                Offset(tempProjHolder[0], tempProjHolder[1])
            } else {
                screenCenter
            }
        } else if (liveTargetScreenPos != null &&
            liveTargetScreenPos.x in (-120f)..(size.width + 120f) &&
            liveTargetScreenPos.y in (-120f)..(size.height + 120f)
        ) {
            liveTargetScreenPos
        } else {
            screenCenter
        }

        // Optical Center & Magnetic Tether Guidance Line
        if (isSnapped) {
            val tetherDx = currentReticlePos.x - screenCenter.x
            val tetherDy = currentReticlePos.y - screenCenter.y
            val tetherDist = sqrt(tetherDx * tetherDx + tetherDy * tetherDy)
            if (tetherDist > 14f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.40f),
                    center = screenCenter,
                    radius = 3.5.dp.toPx(),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                drawLine(
                    color = colorPrimary.copy(alpha = 0.75f),
                    start = screenCenter,
                    end = currentReticlePos,
                    strokeWidth = 2.0.dp.toPx(),
                    pathEffect = getDash10_6(currentDashPhase)
                )
            }
        }

        // Project confirmed 3D world points directly to 2D screen coordinates with zero lag.
        // Anchored points live in world space and are driven smoothly by current viewMatrix * projectionMatrix;
        // skipping 2D EMA eliminates visual sliding during camera panning and prevents phantom off-screen freezes.
        val pointsSnapshot = capturedPoints
        val pointsCount = pointsSnapshot.size
        val safePointsCount = minOf(pointsCount, projectedPool.capacity)
        projectedPool.reset(safePointsCount)
        for (i in 0 until safePointsCount) {
            val rawPt = pointsSnapshot[i]
            val ok = ArMath.projectCoordsToScreen(
                rawPt.x,
                rawPt.y,
                rawPt.z,
                viewMatrix,
                projectionMatrix,
                screenW,
                screenH,
                tempProjHolder,
                isHorizontallyMirrored
            )
            if (ok) {
                projectedPool.set(i, tempProjHolder[0], tempProjHolder[1], true)
            } else {
                projectedPool.set(i, 0f, 0f, false)
            }
        }
        val projectedPoints = projectedPool

        // Mode Detection
        val isAreaMode = subMode == 1 || (subMode == 0 && autoDetectedType == "AREA")
        val isHeightMode = subMode == 2 || (subMode == 0 && autoDetectedType == "HEIGHT")
        val isAngleMode = subMode == 5 || (subMode == 0 && autoDetectedType == "ANGLE")
        val isCircleMode = subMode == 4 || (subMode == 0 && autoDetectedType == "CIRCLE")
        val isBoxMode = subMode == 3 || (subMode == 0 && autoDetectedType == "VOLUME")
        val isDistanceMode = !isAreaMode && !isHeightMode && !isAngleMode && !isCircleMode && !isBoxMode

        val effectiveLineThickness = lineThickness.coerceIn(1.5f, 8.0f)

        // -------------------------------------------------------------
        // 1. DISTANCE & AREA MODE: Confirmed Connecting 3D Virtual Lines
        // -------------------------------------------------------------
        if (isDistanceMode || isAreaMode) {
            val stepVal = if (isAreaMode) 1 else 2
            if (projectedPoints.size >= 2) {
                for (i in 0 until projectedPoints.size - 1 step stepVal) {
                    val p1 = projectedPoints[i]
                    val p2 = projectedPoints[i + 1]
                    if (p1 != null && p2 != null) {
                        val startOffset = Offset(p1.first, p1.second)
                        val endOffset = Offset(p2.first, p2.second)
                        val dx = endOffset.x - startOffset.x
                        val dy = endOffset.y - startOffset.y
                        val segLen = sqrt(dx * dx + dy * dy)

                        if (!segLen.isNaN() && segLen > 0.5f) {
                            // 1. Crisp outer halo (no drop shadow)
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.28f),
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = (effectiveLineThickness + 4.0f).dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 2. Core solid laser line
                            drawLine(
                                color = colorPrimary,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = effectiveLineThickness.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 4. Perpendicular dimension ticks
                            if (segLen > 20f && segLen < 4000f) {
                                val nx = -dy / segLen
                                val ny = dx / segLen
                                val tickHalfLen = 8.dp.toPx()

                                drawLine(
                                    color = Color.White,
                                    start = Offset(startOffset.x - nx * tickHalfLen, startOffset.y - ny * tickHalfLen),
                                    end = Offset(startOffset.x + nx * tickHalfLen, startOffset.y + ny * tickHalfLen),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                drawLine(
                                    color = Color.White,
                                    start = Offset(endOffset.x - nx * tickHalfLen, endOffset.y - ny * tickHalfLen),
                                    end = Offset(endOffset.x + nx * tickHalfLen, endOffset.y + ny * tickHalfLen),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // Ruler scale hash marks
                                val step = 28f
                                var d = step
                                var tickCount = 0
                                while (d < segLen - step && tickCount++ < 35) {
                                    val px = startOffset.x + (dx / segLen) * d
                                    val py = startOffset.y + (dy / segLen) * d
                                    val subTickLen = 4.dp.toPx()
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.7f),
                                        start = Offset(px - nx * subTickLen, py - ny * subTickLen),
                                        end = Offset(px + nx * subTickLen, py + ny * subTickLen),
                                        strokeWidth = 1.6.dp.toPx()
                                    )
                                    d += step
                                }

                                // 3D Floating Segment Dimension Capsule
                                val midX = (startOffset.x + endOffset.x) / 2f
                                val midY = (startOffset.y + endOffset.y) / 2f
                                val segDist = if (i + 1 < pointsSnapshot.size) {
                                    ArMath.distance(pointsSnapshot[i], pointsSnapshot[i + 1])
                                } else 0.0
                                val distText = viewModel.formatLength(segDist, selectedUnit)

                                drawContext.canvas.nativeCanvas.apply {
                                    val textWidth = badgeTextPaint.measureText(distText)
                                    val textHeight = badgeTextPaint.textSize
                                    val padH = 30f
                                    val padV = 16f
                                    val left = midX - textWidth / 2f - padH
                                    val top = midY - textHeight / 2f - padV
                                    val right = midX + textWidth / 2f + padH
                                    val bottom = midY + textHeight / 2f + padV
                                    val radius = 34f

                                    // Capsule background with badgeAlpha (flat border, no drop shadow)
                                    badgeBgPaint.color = primaryContainerBgArgb
                                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)

                                    // Border
                                    badgeBorderPaint.color = primaryBorderArgb
                                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)

                                    // Text
                                    badgeTextPaint.color = onPrimaryContainerArgb
                                    drawText(distText, midX, midY + textHeight * 0.35f, badgeTextPaint)
                                }
                            }
                        }
                    }
                }
            }

            // Closed Polygon in Area mode (Zero-allocation reusable buffers)
            if (isAreaMode && projectedPoints.size >= 3) {
                var validPtsCount = 0
                for (k in 0 until projectedPoints.size) {
                    if (validPtsCount >= validPtsBuffer.size) break
                    val pt = projectedPoints.getOrNull(k)
                    if (pt != null) {
                        val buf = validPtsBuffer[validPtsCount]
                        buf.x = pt.x
                        buf.y = pt.y
                        buf.isValid = true
                        validPtsCount++
                    }
                }
                if (validPtsCount >= 3) {
                    val first = validPtsBuffer[0]
                    val last = validPtsBuffer[validPtsCount - 1]

                    val startOffset = Offset(last.first, last.second)
                    val endOffset = Offset(first.first, first.second)
                    val dx = endOffset.x - startOffset.x
                    val dy = endOffset.y - startOffset.y
                    val segLen = sqrt(dx * dx + dy * dy)

                    if (!segLen.isNaN() && segLen > 0.5f) {
                        drawLine(
                            color = colorPrimary.copy(alpha = 0.28f),
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = (effectiveLineThickness + 4.0f).dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        drawLine(
                            color = colorPrimary,
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = effectiveLineThickness.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        if (segLen > 20f && segLen < 4000f) {
                            val midX = (startOffset.x + endOffset.x) / 2f
                            val midY = (startOffset.y + endOffset.y) / 2f
                            val segDist = ArMath.distance(pointsSnapshot.last(), pointsSnapshot.first())
                            val distText = viewModel.formatLength(segDist, selectedUnit)

                            drawContext.canvas.nativeCanvas.apply {
                                val textWidth = badgeTextPaint.measureText(distText)
                                val textHeight = badgeTextPaint.textSize
                                val padH = 30f
                                val padV = 16f
                                val left = midX - textWidth / 2f - padH
                                val top = midY - textHeight / 2f - padV
                                val right = midX + textWidth / 2f + padH
                                val bottom = midY + textHeight / 2f + padV
                                val radius = 34f

                                badgeBgPaint.color = primaryContainerBgArgb
                                drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                                badgeBorderPaint.color = primaryBorderArgb
                                drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                                badgeTextPaint.color = onPrimaryContainerArgb
                                drawText(distText, midX, midY + textHeight * 0.35f, badgeTextPaint)
                            }
                        }
                    }

                    // Semi-transparent polygon interior mesh fill (Zero heap allocations)
                    areaCachedPath.reset()
                    areaCachedPath.moveTo(validPtsBuffer[0].first, validPtsBuffer[0].second)
                    for (k in 1 until validPtsCount) {
                        areaCachedPath.lineTo(validPtsBuffer[k].first, validPtsBuffer[k].second)
                    }
                    areaCachedPath.close()
                    drawPath(
                        path = areaCachedPath,
                        color = colorPrimary.copy(alpha = 0.18f)
                    )

                    // Visual Centroid & Floating Area Badge (Zero heap allocations)
                    var sumX = 0f
                    var sumY = 0f
                    for (k in 0 until validPtsCount) {
                        sumX += validPtsBuffer[k].first
                        sumY += validPtsBuffer[k].second
                    }
                    val centroidX = sumX / validPtsCount
                    val centroidY = sumY / validPtsCount
                    val areaVal = viewModel.calculatePolygonArea()
                    val areaText = TranslationManager.formatString(
                        "canvas_area_fmt",
                        currentLang,
                        viewModel.formatArea(areaVal, selectedUnit)
                    )

                    drawContext.canvas.nativeCanvas.apply {
                        val textWidth = badgeTextPaint.measureText(areaText)
                        val textHeight = badgeTextPaint.textSize
                        val padH = 34f
                        val padV = 18f
                        val left = centroidX - textWidth / 2f - padH
                        val top = centroidY - textHeight / 2f - padV
                        val right = centroidX + textWidth / 2f + padH
                        val bottom = centroidY + textHeight / 2f + padV
                        val radius = 38f

                        badgeBgPaint.color = primaryBadgeBgArgb
                        drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                        badgeBorderPaint.color = whiteBorderArgb
                        drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                        badgeTextPaint.color = onPrimaryArgb
                        drawText(areaText, centroidX, centroidY + textHeight * 0.35f, badgeTextPaint)
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. ANGLE MODE (3D Corner Angle & Vertex Arc Sector)
        // -------------------------------------------------------------
        if (isAngleMode) {
            val p0 = projectedPoints.getOrNull(0)
            val p1 = projectedPoints.getOrNull(1) // Vertex
            val p2 = projectedPoints.getOrNull(2)

            // Arm 1: P0 -> P1 (Vertex)
            if (p0 != null && p1 != null) {
                drawLine(
                    color = colorPrimary,
                    start = Offset(p0.first, p0.second),
                    end = Offset(p1.first, p1.second),
                    strokeWidth = effectiveLineThickness.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Arm 2: P1 (Vertex) -> P2
            if (p1 != null && p2 != null) {
                drawLine(
                    color = colorSecondary,
                    start = Offset(p1.first, p1.second),
                    end = Offset(p2.first, p2.second),
                    strokeWidth = effectiveLineThickness.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Confirmed Angle: 3 Points captured
            if (p0 != null && p1 != null && p2 != null) {
                val vOffset = Offset(p1.first, p1.second)
                val angleDeg = viewModel.calculateAngle()

                // Draw Angle Arc Sector
                val a1 = Math.toDegrees(atan2((p0.second - p1.second).toDouble(), (p0.first - p1.first).toDouble())).toFloat()
                val a2 = Math.toDegrees(atan2((p2.second - p1.second).toDouble(), (p2.first - p1.first).toDouble())).toFloat()
                var sweep = a2 - a1
                while (sweep < -180f) sweep += 360f
                while (sweep > 180f) sweep -= 360f

                val arcRadius = 36.dp.toPx()
                drawArc(
                    color = colorTertiary,
                    startAngle = a1,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(vOffset.x - arcRadius, vOffset.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Floating Angle Badge along Bisector
                val bisectorRad = Math.toRadians((a1 + sweep / 2f).toDouble())
                val badgeDist = 58.dp.toPx()
                val badgeX = vOffset.x + (cos(bisectorRad) * badgeDist).toFloat()
                val badgeY = vOffset.y + (sin(bisectorRad) * badgeDist).toFloat()
                val angleText = if (angleDeg == cachedAngleFmt.lastAngle) {
                    cachedAngleFmt.text
                } else {
                    cachedAngleFmt.lastAngle = angleDeg
                    "∠ %.1f°".format(java.util.Locale.US, angleDeg).also { cachedAngleFmt.text = it }
                }

                drawContext.canvas.nativeCanvas.apply {
                    val textWidth = badgeTextPaint.measureText(angleText)
                    val textHeight = badgeTextPaint.textSize
                    val padH = 28f
                    val padV = 16f
                    val left = badgeX - textWidth / 2f - padH
                    val top = badgeY - textHeight / 2f - padV
                    val right = badgeX + textWidth / 2f + padH
                    val bottom = badgeY + textHeight / 2f + padV
                    val radius = 34f

                    badgeBgPaint.color = tertiaryBadgeBgArgb
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                    badgeBorderPaint.color = whiteBorderArgb
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                    badgeTextPaint.color = android.graphics.Color.BLACK
                    drawText(angleText, badgeX, badgeY + textHeight * 0.35f, badgeTextPaint)
                }
            }
        }

        // -------------------------------------------------------------
        // 3. HEIGHT MODE (Vertical Plumb & Ground Horizon Alignment)
        // -------------------------------------------------------------
        if (isHeightMode && pointsSnapshot.isNotEmpty()) {
            val base3D = pointsSnapshot[0]
            val top3D = if (pointsSnapshot.size >= 2) pointsSnapshot[1] else (liveTargetPoint ?: base3D)

            val baseProj = projectedPoints.getOrNull(0)
            val plumbOk = ArMath.projectCoordsToScreen(
                base3D.x,
                top3D.y,
                base3D.z,
                viewMatrix,
                projectionMatrix,
                screenW,
                screenH,
                tempProjHolder,
                isHorizontallyMirrored
            )
            val topProj = if (projectedPoints.size >= 2) projectedPoints[1] else reticleHolderProj.apply { x = currentReticlePos.x; y = currentReticlePos.y; isValid = true }

            if (baseProj != null && plumbOk && topProj != null) {
                val pBase = Offset(baseProj.first, baseProj.second)
                val pPlumb = Offset(tempProjHolder[0], tempProjHolder[1])
                val pTop = Offset(topProj.first, topProj.second)

                // Vertical Plumb Laser Line (Base -> Plumb Foot)
                drawLine(
                    color = colorTertiary,
                    start = pBase,
                    end = pPlumb,
                    strokeWidth = (effectiveLineThickness + 1.5f).dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Horizontal Guide Line (Plumb Foot -> Top Target)
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = pPlumb,
                    end = pTop,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = getDash10_6(currentDashPhase)
                )

                // Right-Angle Indicator at Plumb Corner
                val cornerSize = 14.dp.toPx()
                val vPlumb = Offset(pBase.x - pPlumb.x, pBase.y - pPlumb.y)
                val lenPlumb = sqrt(vPlumb.x * vPlumb.x + vPlumb.y * vPlumb.y)
                val vHoriz = Offset(pTop.x - pPlumb.x, pTop.y - pPlumb.y)
                val lenHoriz = sqrt(vHoriz.x * vHoriz.x + vHoriz.y * vHoriz.y)

                if (lenPlumb > 18f && lenHoriz > 18f) {
                    val uPlumb = Offset(vPlumb.x / lenPlumb, vPlumb.y / lenPlumb)
                    val uHoriz = Offset(vHoriz.x / lenHoriz, vHoriz.y / lenHoriz)
                    val cP1 = Offset(pPlumb.x + uPlumb.x * cornerSize, pPlumb.y + uPlumb.y * cornerSize)
                    val cP2 = Offset(pPlumb.x + (uPlumb.x + uHoriz.x) * cornerSize, pPlumb.y + (uPlumb.y + uHoriz.y) * cornerSize)
                    val cP3 = Offset(pPlumb.x + uHoriz.x * cornerSize, pPlumb.y + uHoriz.y * cornerSize)
                    drawLine(colorTertiary, cP1, cP2, 2.dp.toPx())
                    drawLine(colorTertiary, cP2, cP3, 2.dp.toPx())
                }

                // Floating Vertical Height Badge
                val midHeightX = (pBase.x + pPlumb.x) / 2f
                val midHeightY = (pBase.y + pPlumb.y) / 2f
                val hMeters = if (pointsSnapshot.size >= 2) {
                    viewModel.calculateVerticalHeight()
                } else {
                    abs(top3D.y - base3D.y)
                }
                val hText = TranslationManager.formatString(
                    "canvas_height_fmt",
                    currentLang,
                    viewModel.formatLength(hMeters, selectedUnit)
                )

                drawContext.canvas.nativeCanvas.apply {
                    val textWidth = badgeTextPaint.measureText(hText)
                    val textHeight = badgeTextPaint.textSize
                    val padH = 30f
                    val padV = 16f
                    val left = midHeightX - textWidth / 2f - padH
                    val top = midHeightY - textHeight / 2f - padV
                    val right = midHeightX + textWidth / 2f + padH
                    val bottom = midHeightY + textHeight / 2f + padV
                    val radius = 34f

                    badgeBgPaint.color = tertiaryBadgeBgArgb
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                    badgeBorderPaint.color = whiteBorderArgb
                    drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                    badgeTextPaint.color = android.graphics.Color.WHITE
                    drawText(hText, midHeightX, midHeightY + textHeight * 0.35f, badgeTextPaint)
                }
            }
        }

        // -------------------------------------------------------------
        // 4. CIRCLE / DIAMETER MODE (SubMode 4 — True 3D Surface-Aligned Perspective Ring + Smooth Preview)
        // -------------------------------------------------------------
        if (isCircleMode && hasValidMatrices && pointsSnapshot.isNotEmpty()) {
            val liveTarget = liveTargetPoint
            val p0 = pointsSnapshot[0]
            val p1 = if (pointsSnapshot.size >= 2) pointsSnapshot[1] else null
            val p2 = if (pointsSnapshot.size >= 3) pointsSnapshot[2] else null

            // Compute live target circle (1-pt diameter preview, 2-pt live 3-pt circumcircle, or 3-pt locked circle)
            val liveFitCircle = when {
                p2 != null -> viewModel.calculateCircle()
                p1 != null && liveTarget != null -> ArMath.fitCircle3Points(p0, p1, liveTarget) ?: ArMath.fitCircle2Points(p0, p1)
                p1 != null -> ArMath.fitCircle2Points(p0, p1)
                liveTarget != null -> ArMath.fitCircle2Points(p0, liveTarget)
                else -> null
            }

            if (liveFitCircle != null && liveFitCircle.radius > 0.005) {
                val targetCx = liveFitCircle.center.x
                val targetCy = liveFitCircle.center.y
                val targetCz = liveFitCircle.center.z
                val targetRadius = liveFitCircle.radius

                // Smooth 3D circle center & radius during live preview; snap smoothly when locked (3+ pts)
                val smoothAlpha = if (p2 != null) 0.45 else 0.24
                if (!hasSmoothedCircle[0] || smoothedCircleRadius[0] <= 0.0) {
                    smoothedCircleCenter[0] = targetCx
                    smoothedCircleCenter[1] = targetCy
                    smoothedCircleCenter[2] = targetCz
                    smoothedCircleRadius[0] = targetRadius
                    hasSmoothedCircle[0] = true
                } else {
                    smoothedCircleCenter[0] += (targetCx - smoothedCircleCenter[0]) * smoothAlpha
                    smoothedCircleCenter[1] += (targetCy - smoothedCircleCenter[1]) * smoothAlpha
                    smoothedCircleCenter[2] += (targetCz - smoothedCircleCenter[2]) * smoothAlpha
                    smoothedCircleRadius[0] += (targetRadius - smoothedCircleRadius[0]) * smoothAlpha
                }

                val drawCx = smoothedCircleCenter[0]
                val drawCy = smoothedCircleCenter[1]
                val drawCz = smoothedCircleCenter[2]
                val drawRadius = smoothedCircleRadius[0]

                val fallbackNorm = if (hasReticleNormal[0]) reticleSmoothedNormal else liveSurfaceNormal
                val hasBasis = when {
                    p1 != null && p2 != null -> {
                        ArMath.computePlaneNormal3Points(p0, p1, p2, canvasCircleBasis) ||
                            ArMath.buildOrthonormalBasis(fallbackNorm[0].toDouble(), fallbackNorm[1].toDouble(), fallbackNorm[2].toDouble(), canvasCircleBasis)
                    }
                    p1 != null && liveTarget != null -> {
                        ArMath.computePlaneNormal3Points(p0, p1, liveTarget, canvasCircleBasis) ||
                            ArMath.buildOrthonormalBasis(fallbackNorm[0].toDouble(), fallbackNorm[1].toDouble(), fallbackNorm[2].toDouble(), canvasCircleBasis)
                    }
                    else -> ArMath.buildOrthonormalBasis(fallbackNorm[0].toDouble(), fallbackNorm[1].toDouble(), fallbackNorm[2].toDouble(), canvasCircleBasis)
                }

                if (hasBasis && drawRadius > 0.005) {
                    val segCount = 48
                    val isComplete = p2 != null
                    val strokePx = effectiveLineThickness.dp.toPx()
                    val projectedCount = ArMath.projectCircle3DToScreen(
                        cx = drawCx,
                        cy = drawCy,
                        cz = drawCz,
                        radiusMeters = drawRadius,
                        basis = canvasCircleBasis,
                        segments = segCount,
                        viewMatrix = viewMatrix,
                        projectionMatrix = projectionMatrix,
                        screenWidth = screenW,
                        screenHeight = screenH,
                        outScreenX = canvasCircleScratchX,
                        outScreenY = canvasCircleScratchY,
                        scratchXY = tempProjHolder,
                        isHorizontallyMirrored = isHorizontallyMirrored,
                        phaseOffsetRad = if (isComplete) 0.0 else (currentDashPhase * 0.04).toDouble()
                    )

                    if (projectedCount >= 3) {
                        canvasCirclePath.reset()
                        canvasCirclePath.moveTo(canvasCircleScratchX[0], canvasCircleScratchY[0])
                        for (i in 1 until projectedCount) {
                            canvasCirclePath.lineTo(canvasCircleScratchX[i], canvasCircleScratchY[i])
                        }
                        canvasCirclePath.close()

                        // Subtle 3D completion ripple ring when 3rd point locks
                        val pulseProgress = circleCompletionPulse.value
                        if (isComplete && pulseProgress in 0.01f..0.98f) {
                            val rippleRadius = drawRadius * (1.0 + 0.16 * pulseProgress)
                            val rippleCount = ArMath.projectCircle3DToScreen(
                                cx = drawCx,
                                cy = drawCy,
                                cz = drawCz,
                                radiusMeters = rippleRadius,
                                basis = canvasCircleBasis,
                                segments = segCount,
                                viewMatrix = viewMatrix,
                                projectionMatrix = projectionMatrix,
                                screenWidth = screenW,
                                screenHeight = screenH,
                                outScreenX = canvasCircleScratchX,
                                outScreenY = canvasCircleScratchY,
                                scratchXY = tempProjHolder,
                                isHorizontallyMirrored = isHorizontallyMirrored
                            )
                            if (rippleCount >= 3) {
                                canvasCircleBloomPath.reset()
                                canvasCircleBloomPath.moveTo(canvasCircleScratchX[0], canvasCircleScratchY[0])
                                for (i in 1 until rippleCount) {
                                    canvasCircleBloomPath.lineTo(canvasCircleScratchX[i], canvasCircleScratchY[i])
                                }
                                canvasCircleBloomPath.close()
                                drawPath(
                                    path = canvasCircleBloomPath,
                                    color = colorPrimary.copy(alpha = (1f - pulseProgress) * 0.65f),
                                    style = Stroke(width = (strokePx * (1.6f - 0.6f * pulseProgress)), cap = StrokeCap.Round)
                                )
                            }
                        }

                        // Semi-transparent 3D disc fill
                        drawPath(
                            path = canvasCirclePath,
                            color = colorPrimary.copy(alpha = if (isComplete) 0.18f else 0.11f)
                        )

                        // Outer 3D stroke (solid when complete, flowing dash during preview)
                        drawPath(
                            path = canvasCirclePath,
                            color = colorPrimary,
                            style = Stroke(
                                width = strokePx,
                                cap = StrokeCap.Round,
                                pathEffect = if (isComplete) null else getDash14_10(currentDashPhase)
                            )
                        )

                        // Project 3D center
                        val centerProjOk = ArMath.projectCoordsToScreen(drawCx, drawCy, drawCz, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored)
                        val centerOffset = if (centerProjOk) Offset(tempProjHolder[0], tempProjHolder[1]) else screenCenter

                        // Orthogonal 3D diameter crossbars along tangent basis (U and V)
                        val ux = canvasCircleBasis[3]; val uy = canvasCircleBasis[4]; val uz = canvasCircleBasis[5]
                        val vx = canvasCircleBasis[6]; val vy = canvasCircleBasis[7]; val vz = canvasCircleBasis[8]
                        if (ArMath.projectCoordsToScreen(drawCx - ux * drawRadius, drawCy - uy * drawRadius, drawCz - uz * drawRadius, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored) &&
                            ArMath.projectCoordsToScreen(drawCx + ux * drawRadius, drawCy + uy * drawRadius, drawCz + uz * drawRadius, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder2, isHorizontallyMirrored)
                        ) {
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.42f),
                                start = Offset(tempProjHolder[0], tempProjHolder[1]),
                                end = Offset(tempProjHolder2[0], tempProjHolder2[1]),
                                strokeWidth = 1.4.dp.toPx(),
                                pathEffect = getDash10_6(currentDashPhase)
                            )
                        }
                        if (ArMath.projectCoordsToScreen(drawCx - vx * drawRadius, drawCy - vy * drawRadius, drawCz - vz * drawRadius, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored) &&
                            ArMath.projectCoordsToScreen(drawCx + vx * drawRadius, drawCy + vy * drawRadius, drawCz + vz * drawRadius, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder2, isHorizontallyMirrored)
                        ) {
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.28f),
                                start = Offset(tempProjHolder[0], tempProjHolder[1]),
                                end = Offset(tempProjHolder2[0], tempProjHolder2[1]),
                                strokeWidth = 1.1.dp.toPx(),
                                pathEffect = getDash10_6(currentDashPhase)
                            )
                        }

                        // Center marker dot
                        drawCircle(Color.White, center = centerOffset, radius = 3.8.dp.toPx())
                        drawCircle(colorPrimary, center = centerOffset, radius = 6.dp.toPx(), style = Stroke(width = 1.6.dp.toPx()))

                        // Live diameter chord between P0 and P1/liveTarget during preview
                        if (!isComplete) {
                            val endPt = p1 ?: liveTarget
                            if (endPt != null &&
                                ArMath.projectCoordsToScreen(p0.x, p0.y, p0.z, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored) &&
                                ArMath.projectCoordsToScreen(endPt.x, endPt.y, endPt.z, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder2, isHorizontallyMirrored)
                            ) {
                                drawLine(
                                    color = colorTertiary,
                                    start = Offset(tempProjHolder[0], tempProjHolder[1]),
                                    end = Offset(tempProjHolder2[0], tempProjHolder2[1]),
                                    strokeWidth = strokePx * 0.85f,
                                    pathEffect = getDash14_10(currentDashPhase),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        val displayDiam = liveFitCircle.diameter
                        val displayCircum = liveFitCircle.circumference
                        val circleText = TranslationManager.formatString(
                            "canvas_circle_fmt",
                            currentLang,
                            viewModel.formatLength(displayDiam, selectedUnit),
                            viewModel.formatLength(displayCircum, selectedUnit)
                        )

                        drawContext.canvas.nativeCanvas.apply {
                            val textWidth = badgeTextPaint.measureText(circleText)
                            val textHeight = badgeTextPaint.textSize
                            val padH = 30f
                            val padV = 15f
                            val midX = centerOffset.x
                            val midY = centerOffset.y - 28.dp.toPx()
                            val left = midX - textWidth / 2f - padH
                            val top = midY - textHeight / 2f - padV
                            val right = midX + textWidth / 2f + padH
                            val bottom = midY + textHeight / 2f + padV
                            val radius = 34f

                            badgeBgPaint.color = primaryBadgeBgArgb
                            drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                            badgeBorderPaint.color = whiteBorderArgb
                            drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                            badgeTextPaint.color = onPrimaryArgb
                            drawText(circleText, midX, midY + textHeight * 0.35f, badgeTextPaint)
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 5. 3D BOX VOLUME MODE (SubMode 3 — Zero Heap Allocation)
        // -------------------------------------------------------------
        if (isBoxMode && pointsSnapshot.isNotEmpty()) {
            val p0 = pointsSnapshot[0]
            val pTarget = if (pointsSnapshot.size >= 2) pointsSnapshot[1] else (liveTargetPoint ?: p0)

            val minX = min(p0.x, pTarget.x)
            val maxX = max(p0.x, pTarget.x)
            val minY = min(p0.y, pTarget.y)
            val maxY = max(p0.y, pTarget.y)
            val minZ = min(p0.z, pTarget.z)
            val maxZ = max(p0.z, pTarget.z)

            val lenX = abs(maxX - minX).coerceAtLeast(0.01)
            val heightY = abs(maxY - minY).coerceAtLeast(0.01)
            val depthZ = abs(maxZ - minZ).coerceAtLeast(0.01)
            val volumeM3 = lenX * heightY * depthZ

            box3dX[0] = minX; box3dY[0] = minY; box3dZ[0] = minZ
            box3dX[1] = maxX; box3dY[1] = minY; box3dZ[1] = minZ
            box3dX[2] = maxX; box3dY[2] = minY; box3dZ[2] = maxZ
            box3dX[3] = minX; box3dY[3] = minY; box3dZ[3] = maxZ
            box3dX[4] = minX; box3dY[4] = maxY; box3dZ[4] = minZ
            box3dX[5] = maxX; box3dY[5] = maxY; box3dZ[5] = minZ
            box3dX[6] = maxX; box3dY[6] = maxY; box3dZ[6] = maxZ
            box3dX[7] = minX; box3dY[7] = maxY; box3dZ[7] = maxZ

            var validSumX = 0f
            var validSumY = 0f
            var validCount = 0
            for (v in 0 until 8) {
                val ok = ArMath.projectCoordsToScreen(
                    box3dX[v], box3dY[v], box3dZ[v],
                    viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored
                )
                box2dValid[v] = ok
                if (ok) {
                    box2dX[v] = tempProjHolder[0]
                    box2dY[v] = tempProjHolder[1]
                    validSumX += box2dX[v]
                    validSumY += box2dY[v]
                    validCount++
                }
            }

            val boxStrokePx = effectiveLineThickness.dp.toPx()
            val boxDashEffect = getDash10_8(currentDashPhase)
            for (e in 0 until 12) {
                val a = BOX_EDGE_A[e]
                val b = BOX_EDGE_B[e]
                if (box2dValid[a] && box2dValid[b]) {
                    drawLine(
                        color = colorTertiary,
                        start = Offset(box2dX[a], box2dY[a]),
                        end = Offset(box2dX[b], box2dY[b]),
                        strokeWidth = boxStrokePx,
                        pathEffect = boxDashEffect
                    )
                }
            }

            val center2D = if (validCount > 0) {
                Offset(validSumX / validCount, validSumY / validCount)
            } else screenCenter

            val volText = if (
                volumeM3 == cachedVolFmt.lastVol &&
                lenX == cachedVolFmt.lastLen &&
                depthZ == cachedVolFmt.lastDepth &&
                heightY == cachedVolFmt.lastHeight &&
                currentLang == cachedVolFmt.lastLang
            ) {
                cachedVolFmt.text
            } else {
                cachedVolFmt.lastVol = volumeM3
                cachedVolFmt.lastLen = lenX
                cachedVolFmt.lastDepth = depthZ
                cachedVolFmt.lastHeight = heightY
                cachedVolFmt.lastLang = currentLang
                TranslationManager.formatString(
                    "canvas_volume_fmt",
                    currentLang,
                    "%.3f".format(java.util.Locale.US, volumeM3),
                    "%.2f".format(java.util.Locale.US, lenX),
                    "%.2f".format(java.util.Locale.US, depthZ),
                    "%.2f".format(java.util.Locale.US, heightY)
                ).also { cachedVolFmt.text = it }
            }

            drawContext.canvas.nativeCanvas.apply {
                val textWidth = badgeTextPaint.measureText(volText)
                val textHeight = badgeTextPaint.textSize
                val padH = 30f
                val padV = 16f
                val left = center2D.x - textWidth / 2f - padH
                val top = center2D.y - textHeight / 2f - padV
                val right = center2D.x + textWidth / 2f + padH
                val bottom = center2D.y + textHeight / 2f + padV
                val radius = 34f

                badgeBgPaint.color = tertiaryBadgeBgArgb
                drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                badgeBorderPaint.color = whiteBorderArgb
                drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                badgeTextPaint.color = android.graphics.Color.BLACK
                drawText(volText, center2D.x, center2D.y + textHeight * 0.35f, badgeTextPaint)
            }
        }

        // -------------------------------------------------------------
        // 6. 3D SPATIAL GROUND GRID OVERLAY (PERSPECTIVE_GRID / DOT_MATRIX)
        // -------------------------------------------------------------
        if (gridOverlayStyle == "PERSPECTIVE_GRID" && hasValidMatrices) {
            val groundY = pointsSnapshot.firstOrNull()?.y ?: -0.45
            val gridSpan = 2.0
            val gridStep = 0.5
            var gx = -gridSpan
            while (gx <= gridSpan + 0.01) {
                val ok1 = ArMath.projectCoordsToScreen(gx, groundY, -gridSpan, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored)
                val p1 = if (ok1) Offset(tempProjHolder[0], tempProjHolder[1]) else null
                val ok2 = ArMath.projectCoordsToScreen(gx, groundY, gridSpan, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored)
                val p2 = if (ok2) Offset(tempProjHolder[0], tempProjHolder[1]) else null
                if (p1 != null && p2 != null) {
                    drawLine(
                        color = colorPrimary.copy(alpha = 0.22f),
                        start = p1,
                        end = p2,
                        strokeWidth = 1.0.dp.toPx()
                    )
                }
                gx += gridStep
            }
            var gz = -gridSpan
            while (gz <= gridSpan + 0.01) {
                val ok1 = ArMath.projectCoordsToScreen(-gridSpan, groundY, gz, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored)
                val p1 = if (ok1) Offset(tempProjHolder[0], tempProjHolder[1]) else null
                val ok2 = ArMath.projectCoordsToScreen(gridSpan, groundY, gz, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored)
                val p2 = if (ok2) Offset(tempProjHolder[0], tempProjHolder[1]) else null
                if (p1 != null && p2 != null) {
                    drawLine(
                        color = colorPrimary.copy(alpha = 0.22f),
                        start = p1,
                        end = p2,
                        strokeWidth = 1.0.dp.toPx()
                    )
                }
                gz += gridStep
            }
        } else if (gridOverlayStyle == "DOT_MATRIX" && hasValidMatrices) {
            val groundY = pointsSnapshot.firstOrNull()?.y ?: -0.45
            val gridSpan = 1.8
            val gridStep = 0.30
            val dotRadius = 2.2.dp.toPx()
            var gx = -gridSpan
            while (gx <= gridSpan + 0.01) {
                var gz = -gridSpan
                while (gz <= gridSpan + 0.01) {
                    if (ArMath.projectCoordsToScreen(gx, groundY, gz, viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored)) {
                        drawCircle(
                            color = colorPrimary.copy(alpha = 0.34f),
                            radius = dotRadius,
                            center = Offset(tempProjHolder[0], tempProjHolder[1])
                        )
                    }
                    gz += gridStep
                }
                gx += gridStep
            }
        }

        // -------------------------------------------------------------
        // 7. ACTIVE DYNAMIC VIRTUAL LINE & RUBBER-BAND PREVIEW
        // -------------------------------------------------------------
        val isActivelyDrawingLine = when {
            isAreaMode -> if (subMode == 0 && projectedPoints.size >= 3) false else projectedPoints.isNotEmpty()
            isAngleMode -> projectedPoints.size == 1 || projectedPoints.size == 2
            isHeightMode -> projectedPoints.size == 1
            isCircleMode -> projectedPoints.size == 1
            isBoxMode -> projectedPoints.size == 1
            else -> projectedPoints.size % 2 == 1
        }

        if (isActivelyDrawingLine && projectedPoints.isNotEmpty() && !isCircleMode && !isBoxMode) {
            val anchorIndex = if (isAngleMode && projectedPoints.size == 2) 1 else projectedPoints.size - 1
            val lastPt = projectedPoints.getOrNull(anchorIndex)
            if (lastPt != null) {
                val startOffset = Offset(lastPt.first, lastPt.second)
                val dx = currentReticlePos.x - startOffset.x
                val dy = currentReticlePos.y - startOffset.y
                val liveLen = sqrt(dx * dx + dy * dy)

                if (!liveLen.isNaN() && liveLen > 0.5f) {
                    // Dynamic dashed measurement line
                    drawLine(
                        color = colorPrimary,
                        start = startOffset,
                        end = currentReticlePos,
                        strokeWidth = effectiveLineThickness.dp.toPx(),
                        pathEffect = getDash14_10(currentDashPhase),
                        cap = StrokeCap.Round
                    )

                    // Area Mode: Live rubber-band closing dashed line from reticle to first point
                    if (isAreaMode && projectedPoints.size >= 2) {
                        val firstPt = projectedPoints.first()
                        if (firstPt != null) {
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.65f),
                                start = currentReticlePos,
                                end = Offset(firstPt.first, firstPt.second),
                                strokeWidth = 2.5.dp.toPx(),
                                pathEffect = getDash10_8(currentDashPhase)
                            )
                        }
                    }

                    // Angle Mode Live Preview: Arc at Vertex (P1) between P0 and currentReticlePos
                    if (isAngleMode && projectedPoints.size == 2) {
                        val p0 = projectedPoints[0]
                        val p1 = projectedPoints[1]
                        if (p0 != null && p1 != null) {
                            val vOffset = Offset(p1.first, p1.second)
                            val a0 = Math.toDegrees(atan2((p0.second - p1.second).toDouble(), (p0.first - p1.first).toDouble())).toFloat()
                            val aRet = Math.toDegrees(atan2((currentReticlePos.y - p1.second).toDouble(), (currentReticlePos.x - p1.first).toDouble())).toFloat()
                            var liveSweep = aRet - a0
                            while (liveSweep < -180f) liveSweep += 360f
                            while (liveSweep > 180f) liveSweep -= 360f

                            val liveArcR = 30.dp.toPx()
                            drawArc(
                                color = colorTertiary.copy(alpha = 0.85f),
                                startAngle = a0,
                                sweepAngle = liveSweep,
                                useCenter = false,
                                topLeft = Offset(vOffset.x - liveArcR, vOffset.y - liveArcR),
                                size = Size(liveArcR * 2f, liveArcR * 2f),
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // 3D In-Canvas Hardware Accelerated Live Distance Badge
                    val liveDist = liveDistanceMeters
                    if (liveDist != null && liveDist > 0.0) {
                        val midX = (startOffset.x + currentReticlePos.x) / 2f
                        val midY = (startOffset.y + currentReticlePos.y) / 2f
                        val rawDistText = viewModel.formatLength(liveDist, selectedUnit)
                        val distText = if (isSnapped) {
                            TranslationManager.formatString("canvas_snap_fmt", currentLang, rawDistText)
                        } else {
                            rawDistText
                        }

                        drawContext.canvas.nativeCanvas.apply {
                            val textWidth = badgeTextPaint.measureText(distText)
                            val textHeight = badgeTextPaint.textSize
                            val padH = 32f
                            val padV = 16f
                            val left = midX - textWidth / 2f - padH
                            val top = midY - textHeight / 2f - padV
                            val right = midX + textWidth / 2f + padH
                            val bottom = midY + textHeight / 2f + padV
                            val radius = 34f

                            badgeBgPaint.color = primaryBadgeBgArgb
                            drawRoundRect(left, top, right, bottom, radius, radius, badgeBgPaint)
                            badgeBorderPaint.color = whiteBorderArgb
                            drawRoundRect(left, top, right, bottom, radius, radius, badgeBorderPaint)
                            badgeTextPaint.color = onPrimaryArgb
                            drawText(distText, midX, midY + textHeight * 0.35f, badgeTextPaint)
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 8. Confirmed Measurement Anchors
        // -------------------------------------------------------------
        for (index in pointsSnapshot.indices) {
            val proj = projectedPoints.getOrNull(index)
            if (proj != null) {
                val pt3d = pointsSnapshot[index]
                drawArMeasurementAnchor(
                    center = Offset(proj.first, proj.second),
                    index = index,
                    label = pt3d.label,
                    originLabel = originLabel,
                    primaryColor = colorPrimary,
                    isOrigin = (index == 0),
                    nodeLabelPaint = nodeLabelPaint,
                    badgeBgPaint = badgeBgPaint
                )
            }
        }

        // -------------------------------------------------------------
        // 9. Active Target HUD Reticle (3D 平面貼合圓圈準心 + 流暢姿態插值 + 四大風格支援)
        // -------------------------------------------------------------
        var drew3DReticle = false
        if (reticleStyle == "DOUBLE_RING" &&
            isSurfaceNormalLocked &&
            hasValidMatrices &&
            liveTargetPoint != null &&
            liveSurfaceNormal.size >= 3
        ) {
            // Smooth surface normal orientation continuously inside 60fps Canvas loop
            var tnx = liveSurfaceNormal[0]
            var tny = liveSurfaceNormal[1]
            var tnz = liveSurfaceNormal[2]
            val targetLen = sqrt(tnx * tnx + tny * tny + tnz * tnz)
            if (targetLen > 1e-4f) {
                tnx /= targetLen; tny /= targetLen; tnz /= targetLen
                if (!hasReticleNormal[0]) {
                    reticleSmoothedNormal[0] = tnx
                    reticleSmoothedNormal[1] = tny
                    reticleSmoothedNormal[2] = tnz
                    hasReticleNormal[0] = true
                } else {
                    val cnx = reticleSmoothedNormal[0]
                    val cny = reticleSmoothedNormal[1]
                    val cnz = reticleSmoothedNormal[2]
                    val dot = (cnx * tnx + cny * tny + cnz * tnz).coerceIn(-1f, 1f)
                    if (dot < 0f) {
                        tnx = -tnx; tny = -tny; tnz = -tnz
                    }
                    val absDot = abs(dot)
                    val normalLerp = (0.24f + (1f - absDot) * 0.36f).coerceIn(0.24f, 0.56f)
                    var sx = cnx + (tnx - cnx) * normalLerp
                    var sy = cny + (tny - cny) * normalLerp
                    var sz = cnz + (tnz - cnz) * normalLerp
                    val sLen = sqrt(sx * sx + sy * sy + sz * sz)
                    if (sLen > 1e-4f) {
                        reticleSmoothedNormal[0] = sx / sLen
                        reticleSmoothedNormal[1] = sy / sLen
                        reticleSmoothedNormal[2] = sz / sLen
                    }
                }
            }

            val baseReticleDist = (liveDistanceMeters ?: 1.0).coerceIn(0.25, 6.0)
            val snapF = snapScaleAnimated.value.coerceIn(0.85f, 1.35f)
            val pulseF = reticlePulseScale.value.coerceIn(0.85f, 1.35f)
            val targetRadiusM = (baseReticleDist * 0.036 * snapF * pulseF).coerceIn(0.016, 0.22).toFloat()
            val outerRadiusM = if (reticleSmoothedRadius[0] < 0f) {
                reticleSmoothedRadius[0] = targetRadiusM
                targetRadiusM.toDouble()
            } else {
                val sr = reticleSmoothedRadius[0] + (targetRadiusM - reticleSmoothedRadius[0]) * 0.28f
                reticleSmoothedRadius[0] = sr
                sr.toDouble()
            }
            val innerRadiusM = outerRadiusM * 0.56

            if (ArMath.buildOrthonormalBasis(
                    reticleSmoothedNormal[0].toDouble(),
                    reticleSmoothedNormal[1].toDouble(),
                    reticleSmoothedNormal[2].toDouble(),
                    reticleBasis
                )
            ) {
                val segs = 36
                val outerCnt = ArMath.projectCircle3DToScreen(
                    cx = liveTargetPoint.x,
                    cy = liveTargetPoint.y,
                    cz = liveTargetPoint.z,
                    radiusMeters = outerRadiusM,
                    basis = reticleBasis,
                    segments = segs,
                    viewMatrix = viewMatrix,
                    projectionMatrix = projectionMatrix,
                    screenWidth = screenW,
                    screenHeight = screenH,
                    outScreenX = reticleOuterX,
                    outScreenY = reticleOuterY,
                    scratchXY = tempProjHolder,
                    isHorizontallyMirrored = isHorizontallyMirrored
                )
                val innerCnt = ArMath.projectCircle3DToScreen(
                    cx = liveTargetPoint.x,
                    cy = liveTargetPoint.y,
                    cz = liveTargetPoint.z,
                    radiusMeters = innerRadiusM,
                    basis = reticleBasis,
                    segments = segs,
                    viewMatrix = viewMatrix,
                    projectionMatrix = projectionMatrix,
                    screenWidth = screenW,
                    screenHeight = screenH,
                    outScreenX = reticleInnerX,
                    outScreenY = reticleInnerY,
                    scratchXY = tempProjHolder,
                    isHorizontallyMirrored = isHorizontallyMirrored
                )
                if (outerCnt >= 3 && innerCnt >= 3) {
                    val activeReticleColor = if (isSnapped) colorTertiary else colorPrimary
                    reticleOuterPath.reset()
                    reticleOuterPath.moveTo(reticleOuterX[0], reticleOuterY[0])
                    for (i in 1 until outerCnt) {
                        reticleOuterPath.lineTo(reticleOuterX[i], reticleOuterY[i])
                    }
                    reticleOuterPath.close()

                    reticleInnerPath.reset()
                    reticleInnerPath.moveTo(reticleInnerX[0], reticleInnerY[0])
                    for (i in 1 until innerCnt) {
                        reticleInnerPath.lineTo(reticleInnerX[i], reticleInnerY[i])
                    }
                    reticleInnerPath.close()

                    // Soft 3D bloom fill & outer magnetic aura
                    drawPath(
                        path = reticleOuterPath,
                        color = activeReticleColor.copy(alpha = if (isSnapped) 0.20f else 0.09f)
                    )
                    drawPath(
                        path = reticleOuterPath,
                        color = activeReticleColor.copy(alpha = if (isSnapped) 0.40f else 0.20f),
                        style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = reticleOuterPath,
                        color = activeReticleColor,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Inner ring
                    drawPath(
                        path = reticleInnerPath,
                        color = activeReticleColor.copy(alpha = if (isSnapped) 0.90f else 0.55f),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Smooth rotating 3D tangent-space scan sweep arc (~70 deg)
                    val sweepSpan = 7
                    val sweepStepRad = (2.0 * Math.PI) / segs
                    val sweepPhaseRad = (currentDashPhase * 0.10).toDouble()
                    val ux = reticleBasis[3]; val uy = reticleBasis[4]; val uz = reticleBasis[5]
                    val vx = reticleBasis[6]; val vy = reticleBasis[7]; val vz = reticleBasis[8]
                    val sweepRadiusM = outerRadiusM * 1.14
                    var sweepValid = true
                    reticleSweepPath.reset()
                    for (si in 0..sweepSpan) {
                        val theta = sweepPhaseRad + si * sweepStepRad
                        val c = cos(theta) * sweepRadiusM
                        val s = sin(theta) * sweepRadiusM
                        val ok = ArMath.projectCoordsToScreen(
                            liveTargetPoint.x + c * ux + s * vx,
                            liveTargetPoint.y + c * uy + s * vy,
                            liveTargetPoint.z + c * uz + s * vz,
                            viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored
                        )
                        if (!ok) {
                            sweepValid = false
                            break
                        }
                        if (si == 0) reticleSweepPath.moveTo(tempProjHolder[0], tempProjHolder[1])
                        else reticleSweepPath.lineTo(tempProjHolder[0], tempProjHolder[1])
                    }
                    if (sweepValid) {
                        drawPath(
                            path = reticleSweepPath,
                            color = Color.White.copy(alpha = if (isSnapped) 0.92f else 0.65f),
                            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 4 cardinal 3D (U, V) tangent ticks on the outer plane ring
                    val tickInnerM = outerRadiusM * 0.88
                    val tickOuterM = outerRadiusM * 1.12
                    for (cardinal in 0 until 4) {
                        val ax = if (cardinal < 2) ux else vx
                        val ay = if (cardinal < 2) uy else vy
                        val az = if (cardinal < 2) uz else vz
                        val sign = if (cardinal % 2 == 0) 1.0 else -1.0
                        if (ArMath.projectCoordsToScreen(
                                liveTargetPoint.x + sign * ax * tickInnerM,
                                liveTargetPoint.y + sign * ay * tickInnerM,
                                liveTargetPoint.z + sign * az * tickInnerM,
                                viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored
                            ) &&
                            ArMath.projectCoordsToScreen(
                                liveTargetPoint.x + sign * ax * tickOuterM,
                                liveTargetPoint.y + sign * ay * tickOuterM,
                                liveTargetPoint.z + sign * az * tickOuterM,
                                viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder2, isHorizontallyMirrored
                            )
                        ) {
                            drawLine(
                                color = activeReticleColor.copy(alpha = 0.85f),
                                start = Offset(tempProjHolder[0], tempProjHolder[1]),
                                end = Offset(tempProjHolder2[0], tempProjHolder2[1]),
                                strokeWidth = 1.8.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Core center anchor dot projected cleanly at 3D hit center
                    val centerDotPos = if (ArMath.projectCoordsToScreen(
                            liveTargetPoint.x, liveTargetPoint.y, liveTargetPoint.z,
                            viewMatrix, projectionMatrix, screenW, screenH, tempProjHolder, isHorizontallyMirrored
                        )
                    ) Offset(tempProjHolder[0], tempProjHolder[1]) else currentReticlePos

                    val dotRadius = if (isSnapped) 5.2.dp.toPx() else 4.6.dp.toPx()
                    drawCircle(
                        color = activeReticleColor.copy(alpha = 0.80f),
                        center = centerDotPos,
                        radius = dotRadius + 1.2.dp.toPx(),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        center = centerDotPos,
                        radius = dotRadius
                    )
                    drew3DReticle = true
                }
            }
        }
        if (!drew3DReticle) {
            drawArReticle(
                center = currentReticlePos,
                style = reticleStyle,
                primaryColor = colorPrimary,
                accentColor = colorTertiary,
                isSnapped = isSnapped,
                pulseScale = reticlePulseScale.value,
                snapScale = snapScaleAnimated.value,
                dashPhase = currentDashPhase
            )
        }

        // Multi-Sample Burst Averaging Dynamic Progress Arc (Only visible during active burst measurement)
        if (multiSampleProgress > 0f) {
            val reticleCenter = currentReticlePos
            val reticleRadiusPx = (24f * snapScaleAnimated.value * reticlePulseScale.value).dp.toPx()
            val arcRadius = reticleRadiusPx + 4.dp.toPx()
            drawArc(
                color = if (isMultiSampleLocked) colorTertiary else colorPrimary,
                startAngle = -90f,
                sweepAngle = multiSampleProgress * 360f,
                useCenter = false,
                topLeft = Offset(reticleCenter.x - arcRadius, reticleCenter.y - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Reusable zero-allocation 2D screen coordinate holder.
 * Provides .first and .second getters for backwards compatibility with Pair<Float, Float>.
 */
class ScreenPoint(
    var x: Float = 0f,
    var y: Float = 0f,
    var isValid: Boolean = false
) {
    val first: Float get() = x
    val second: Float get() = y
}

/**
 * Pre-allocated pool of ScreenPoints to eliminate GC churn inside the 60fps canvas draw loop.
 */
class ProjectedPointPool(val capacity: Int = 128) {
    val pool = Array(capacity) { ScreenPoint() }
    var size: Int = 0

    fun reset(count: Int) {
        size = count.coerceAtMost(capacity)
        for (i in 0 until capacity) {
            pool[i].isValid = false
        }
    }

    fun set(index: Int, x: Float, y: Float, isValid: Boolean) {
        if (index in 0 until capacity) {
            val p = pool[index]
            p.x = x
            p.y = y
            p.isValid = isValid
        }
    }

    fun getOrNull(index: Int): ScreenPoint? {
        if (index in 0 until size) {
            val p = pool[index]
            return if (p.isValid) p else null
        }
        return null
    }

    operator fun get(index: Int): ScreenPoint? = getOrNull(index)

    fun isNotEmpty(): Boolean = size > 0

    fun first(): ScreenPoint? = getOrNull(0)
}

