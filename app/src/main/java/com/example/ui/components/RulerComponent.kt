package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.TranslationManager
import com.example.ui.adaptive.LocalAdaptiveWindowState
import com.example.ui.adaptive.rememberLocalHingeTracker
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.GoogleSansRounded
import com.example.ui.theme.loadRoundedNativeTypeface
import com.example.ui.theme.rememberExpressiveConnectedShape
import com.example.ui.theme.rememberExpressivePressCorner
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 全螢幕高精度雙邊螢幕直尺 (Full-Screen Precision Dual-Edge Screen Ruler)
 * - 系統狀態列於螢幕尺模式中自動隱藏
 * - 頂部為單一 M3 Expressive「編輯」按鈕，可開啟視窗調整刻度數字大小與實體比例校正
 * - 已移除不必要的歷史紀錄功能，介面更純粹專注
 */
private val PRECALCULATED_INT_STRINGS = Array(301) { "$it" }

internal fun computeCaliperDeltaMm(lineAMm: Double, lineBMm: Double): Double =
    abs(lineBMm - lineAMm)

internal fun formatCaliperDeltaSummary(deltaMm: Double): String {
    val safeMm = deltaMm.coerceAtLeast(0.0)
    val cm = safeMm / 10.0
    val inches = safeMm / 25.4
    return String.format(Locale.US, "%.1f mm · %.2f cm · %.2f in", safeMm, cm, inches)
}

@Composable
fun RulerComponent(
    viewModel: MeasureViewModel,
    bottomPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val displayMetrics = context.resources.displayMetrics
    val adaptiveState = LocalAdaptiveWindowState.current
    val (hingeTrackerModifier, hingeMetrics) = rememberLocalHingeTracker(adaptiveState)

    val calibrationFactor by viewModel.rulerCalibration.collectAsStateWithLifecycle()
    val fontScale by viewModel.rulerFontScale.collectAsStateWithLifecycle()
    val rulerTheme by viewModel.rulerTheme.collectAsStateWithLifecycle()
    val rulerDualUnit by viewModel.rulerDualUnit.collectAsStateWithLifecycle()
    val isCalibrationDialogFromSettings by viewModel.isRulerCalibrationActive.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isCalibrationDialogFromSettings) {
        if (isCalibrationDialogFromSettings) {
            showEditDialog = true
            viewModel.setRulerCalibrationActive(false)
        }
    }

    val ydpi = remember(displayMetrics) {
        val y = displayMetrics.ydpi
        if (y > 50f && !y.isNaN() && !y.isInfinite()) y else displayMetrics.densityDpi.toFloat()
    }
    val xdpi = remember(displayMetrics) {
        val x = displayMetrics.xdpi
        if (x > 50f && !x.isNaN() && !x.isInfinite()) x else displayMetrics.densityDpi.toFloat()
    }
    val safeFactor = if (calibrationFactor > 0.05f && !calibrationFactor.isNaN() && !calibrationFactor.isInfinite()) calibrationFactor else 1.0f
    val safeFontScale = fontScale.coerceIn(0.8f, 1.8f)
    val mmInPxD = ((ydpi.toDouble() / 25.4) * safeFactor.toDouble()).coerceIn(1.5, 50.0)
    val xMmInPxD = ((xdpi.toDouble() / 25.4) * safeFactor.toDouble()).coerceIn(1.5, 50.0)

    // Leaves clean headroom below top centered Edit button & display cutout
    val zeroY = with(density) { 74.dp.toPx() }

    val themePrimary = MaterialTheme.colorScheme.primary
    val themeSurface = MaterialTheme.colorScheme.surface
    val themeBackground = MaterialTheme.colorScheme.background
    val themeOnSurface = MaterialTheme.colorScheme.onSurface
    val themeOutline = MaterialTheme.colorScheme.outlineVariant

    val (colorPrimary, colorSurface, colorBackground, colorOnSurface, colorOutline) = remember(
        rulerTheme, themePrimary, themeSurface, themeBackground, themeOnSurface, themeOutline
    ) {
        when (rulerTheme) {
            "STEEL" -> listOf(
                androidx.compose.ui.graphics.Color(0xFF94A3B8),
                androidx.compose.ui.graphics.Color(0xFF1E293B),
                androidx.compose.ui.graphics.Color(0xFF0F172A),
                androidx.compose.ui.graphics.Color(0xFFF1F5F9),
                androidx.compose.ui.graphics.Color(0xFF475569)
            )
            "NEON_CYAN" -> listOf(
                androidx.compose.ui.graphics.Color(0xFF22D3EE),
                androidx.compose.ui.graphics.Color(0xFF091522),
                androidx.compose.ui.graphics.Color(0xFF030712),
                androidx.compose.ui.graphics.Color(0xFFCFFAFE),
                androidx.compose.ui.graphics.Color(0xFF155E75)
            )
            "HIGH_CONTRAST" -> listOf(
                androidx.compose.ui.graphics.Color(0xFFFACC15),
                androidx.compose.ui.graphics.Color(0xFF18181B),
                androidx.compose.ui.graphics.Color(0xFF09090B),
                androidx.compose.ui.graphics.Color(0xFFFEF08A),
                androidx.compose.ui.graphics.Color(0xFF52525B)
            )
            else -> listOf(themePrimary, themeSurface, themeBackground, themeOnSurface, themeOutline)
        }
    }
    val roundedBoldTypeface = remember(context) {
        loadRoundedNativeTypeface(context, isBold = true)
    }

    val textPaintLeft = remember(colorOnSurface, density, safeFontScale, roundedBoldTypeface) {
        android.graphics.Paint().apply {
            color = colorOnSurface.copy(alpha = 0.88f).toArgb()
            textSize = with(density) { (11f * safeFontScale).sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
            typeface = roundedBoldTypeface
        }
    }

    val textPaintRight = remember(colorOnSurface, density, safeFontScale, roundedBoldTypeface) {
        android.graphics.Paint().apply {
            color = colorOnSurface.copy(alpha = 0.88f).toArgb()
            textSize = with(density) { (11f * safeFontScale).sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = roundedBoldTypeface
        }
    }

    val unitPaintLeft = remember(colorPrimary, density, safeFontScale, roundedBoldTypeface) {
        android.graphics.Paint().apply {
            color = colorPrimary.toArgb()
            textSize = with(density) { (11.5f * safeFontScale).sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
            typeface = roundedBoldTypeface
        }
    }

    val unitPaintRight = remember(colorPrimary, density, safeFontScale, roundedBoldTypeface) {
        android.graphics.Paint().apply {
            color = colorPrimary.toArgb()
            textSize = with(density) { (11.5f * safeFontScale).sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = roundedBoldTypeface
        }
    }

    val fontVerticalOffset = remember(textPaintLeft) {
        (textPaintLeft.descent() + textPaintLeft.ascent()) / 2f
    }

    val isCustomConfigured = abs(safeFactor - 1.0f) > 0.001f || abs(safeFontScale - 1.0f) > 0.05f || rulerTheme != "MATERIAL_YOU" || rulerDualUnit

    // Interactive Dual-Line Caliper State (preserved across fold/unfold configuration changes)
    val clipboardManager = LocalClipboardManager.current
    var isCaliperEnabled by rememberSaveable { mutableStateOf(false) }
    var caliperLineAMm by rememberSaveable { mutableDoubleStateOf(10.0) }
    var caliperLineBMm by rememberSaveable { mutableDoubleStateOf(50.0) }
    var draggingLine by remember { mutableIntStateOf(0) } // 0 = none, 1 = A, 2 = B
    var lastDetentMm by remember { mutableIntStateOf(-1) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val caliperAccentColor = MaterialTheme.colorScheme.tertiary
    val caliperDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f) }

    val maxVisibleMm = remember(viewportSize, zeroY, mmInPxD) {
        if (viewportSize.height > 0) {
            ((viewportSize.height - zeroY) / mmInPxD).coerceIn(20.0, 500.0)
        } else {
            160.0
        }
    }

    // Resolve container-local hinge bands (Tabletop horizontal or Book vertical)
    val hingeBandPaddingPx = with(density) { 4.dp.toPx() }
    val horizontalHingeBand: Pair<Float, Float>? = remember(adaptiveState.isTabletop, hingeMetrics, viewportSize) {
        if (!adaptiveState.isTabletop || viewportSize.height <= 0) {
            null
        } else if (hingeMetrics.hasValidHorizontalSplit()) {
            (hingeMetrics.localTopPx!! - hingeBandPaddingPx) to (hingeMetrics.localBottomPx!! + hingeBandPaddingPx)
        } else {
            val mid = viewportSize.height * 0.5f
            (mid - with(density) { 10.dp.toPx() }) to (mid + with(density) { 10.dp.toPx() })
        }
    }
    val verticalHingeBand: Pair<Float, Float>? = remember(adaptiveState.isBook, hingeMetrics, viewportSize) {
        if (!adaptiveState.isBook || viewportSize.width <= 0) {
            null
        } else if (hingeMetrics.hasValidVerticalSplit()) {
            (hingeMetrics.localLeftPx!! - hingeBandPaddingPx) to (hingeMetrics.localRightPx!! + hingeBandPaddingPx)
        } else {
            val mid = viewportSize.width * 0.5f
            (mid - with(density) { 10.dp.toPx() }) to (mid + with(density) { 10.dp.toPx() })
        }
    }
    val showExtendedHorizontalScale = remember(viewportSize, adaptiveState.isMediumOrExpandedWidth) {
        val minWidePx = with(density) { 540.dp.toPx() }
        adaptiveState.isMediumOrExpandedWidth || viewportSize.width >= minWidePx
    }

    CompositionLocalProvider(LocalAppLanguage provides currentLanguage) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBackground)
            .then(hingeTrackerModifier)
            .onSizeChanged { viewportSize = it }
            .testTag("ruler_screen")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isCaliperEnabled) {
                        Modifier.pointerInput(mmInPxD, zeroY, maxVisibleMm) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val yA = (zeroY + caliperLineAMm * mmInPxD).toFloat()
                                    val yB = (zeroY + caliperLineBMm * mmInPxD).toFloat()
                                    draggingLine = if (abs(startOffset.y - yA) <= abs(startOffset.y - yB)) 1 else 2
                                    val activeMm = if (draggingLine == 1) caliperLineAMm else caliperLineBMm
                                    lastDetentMm = activeMm.roundToInt()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onDragEnd = { draggingLine = 0 },
                                onDragCancel = { draggingLine = 0 },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val deltaMm = dragAmount.y / mmInPxD
                                    if (draggingLine == 1) {
                                        val nextMm = (caliperLineAMm + deltaMm).coerceIn(0.0, maxVisibleMm)
                                        caliperLineAMm = nextMm
                                        val detent = nextMm.roundToInt()
                                        if (detent != lastDetentMm) {
                                            lastDetentMm = detent
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    } else if (draggingLine == 2) {
                                        val nextMm = (caliperLineBMm + deltaMm).coerceIn(0.0, maxVisibleMm)
                                        caliperLineBMm = nextMm
                                        val detent = nextMm.roundToInt()
                                        if (detent != lastDetentMm) {
                                            lastDetentMm = detent
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            val w = size.width
            val h = size.height
            val rulerWidth = (70f + (safeFontScale - 1.0f).coerceAtLeast(0f) * 14f).dp.toPx()
            val borderStrokeWidth = 1.dp.toPx()
            val zeroStrokeWidth = 2.5.dp.toPx()
            val pad8Px = 8.dp.toPx()
            val pad10Px = 10.dp.toPx()
            val pad12Px = 12.dp.toPx()

            val tickCmLen = 32.dp.toPx()
            val tickHalfCmLen = 20.dp.toPx()
            val tickMmLlen = 12.dp.toPx()
            val strokeCmWidth = 2.dp.toPx()
            val strokeMmWidth = 1.dp.toPx()

            drawRect(
                color = colorSurface,
                topLeft = Offset(0f, 0f),
                size = Size(rulerWidth, h)
            )
            drawLine(
                color = colorOutline.copy(alpha = 0.5f),
                start = Offset(rulerWidth, 0f),
                end = Offset(rulerWidth, h),
                strokeWidth = borderStrokeWidth
            )

            val rightRulerX = w - rulerWidth
            drawRect(
                color = colorSurface,
                topLeft = Offset(rightRulerX, 0f),
                size = Size(rulerWidth, h)
            )
            drawLine(
                color = colorOutline.copy(alpha = 0.5f),
                start = Offset(rightRulerX, 0f),
                end = Offset(rightRulerX, h),
                strokeWidth = borderStrokeWidth
            )

            drawLine(
                color = colorPrimary,
                start = Offset(0f, zeroY),
                end = Offset(w, zeroY),
                strokeWidth = zeroStrokeWidth
            )

            drawContext.canvas.nativeCanvas.drawText("cm", pad12Px, zeroY - pad10Px, unitPaintLeft)
            drawContext.canvas.nativeCanvas.drawText(if (rulerDualUnit) "in" else "cm", w - pad12Px, zeroY - pad10Px, unitPaintRight)

            var leftMm = 0
            while (leftMm < 5000) {
                val leftCurY = (zeroY + leftMm * mmInPxD).toFloat()
                if (leftCurY >= h) break
                val inHinge = horizontalHingeBand != null &&
                    leftCurY >= horizontalHingeBand.first &&
                    leftCurY <= horizontalHingeBand.second
                if (!inHinge) {
                    val isCm = (leftMm % 10 == 0)
                    val isHalfCm = (leftMm % 5 == 0)

                    val tickLength = when {
                        isCm -> tickCmLen
                        isHalfCm -> tickHalfCmLen
                        else -> tickMmLlen
                    }

                    val tickColor = if (isCm) colorPrimary else colorOnSurface.copy(alpha = 0.35f)
                    val strokeWidth = if (isCm) strokeCmWidth else strokeMmWidth

                    drawLine(
                        color = tickColor,
                        start = Offset(0f, leftCurY),
                        end = Offset(tickLength, leftCurY),
                        strokeWidth = strokeWidth
                    )

                    if (isCm && leftMm > 0) {
                        val cmVal = leftMm / 10
                        drawContext.canvas.nativeCanvas.drawText(
                            PRECALCULATED_INT_STRINGS.getOrElse(cmVal) { "$cmVal" },
                            tickLength + pad8Px,
                            leftCurY - fontVerticalOffset,
                            textPaintLeft
                        )
                    }
                }
                leftMm++
            }

            if (rulerDualUnit) {
                // Imperial inch scale (1/16 inch subdivisions) on right edge
                val stepSixteenthInPxD = (mmInPxD * 25.4) / 16.0
                var tick16th = 0
                while (tick16th < 1600) {
                    val rightCurY = (zeroY + tick16th * stepSixteenthInPxD).toFloat()
                    if (rightCurY >= h) break
                    val inHinge = horizontalHingeBand != null &&
                        rightCurY >= horizontalHingeBand.first &&
                        rightCurY <= horizontalHingeBand.second
                    if (!inHinge) {
                        val isInch = (tick16th % 16 == 0)
                        val isHalfInch = (tick16th % 8 == 0)
                        val isQuarterInch = (tick16th % 4 == 0)

                        val tickLength = when {
                            isInch -> tickCmLen
                            isHalfInch -> tickHalfCmLen + 4.dp.toPx()
                            isQuarterInch -> tickHalfCmLen - 2.dp.toPx()
                            else -> tickMmLlen
                        }

                        val tickColor = if (isInch) colorPrimary else colorOnSurface.copy(alpha = 0.35f)
                        val strokeWidth = if (isInch) strokeCmWidth else strokeMmWidth

                        drawLine(
                            color = tickColor,
                            start = Offset(w, rightCurY),
                            end = Offset(w - tickLength, rightCurY),
                            strokeWidth = strokeWidth
                        )

                        if (isInch && tick16th > 0) {
                            val inVal = tick16th / 16
                            drawContext.canvas.nativeCanvas.drawText(
                                PRECALCULATED_INT_STRINGS.getOrElse(inVal) { "$inVal" },
                                w - tickLength - pad8Px,
                                rightCurY - fontVerticalOffset,
                                textPaintRight
                            )
                        }
                    }
                    tick16th++
                }
            } else {
                var rightMm = 0
                while (rightMm < 5000) {
                    val rightCurY = (zeroY + rightMm * mmInPxD).toFloat()
                    if (rightCurY >= h) break
                    val inHinge = horizontalHingeBand != null &&
                        rightCurY >= horizontalHingeBand.first &&
                        rightCurY <= horizontalHingeBand.second
                    if (!inHinge) {
                        val isCm = (rightMm % 10 == 0)
                        val isHalfCm = (rightMm % 5 == 0)

                        val tickLength = when {
                            isCm -> tickCmLen
                            isHalfCm -> tickHalfCmLen
                            else -> tickMmLlen
                        }

                        val tickColor = if (isCm) colorPrimary else colorOnSurface.copy(alpha = 0.35f)
                        val strokeWidth = if (isCm) strokeCmWidth else strokeMmWidth

                        drawLine(
                            color = tickColor,
                            start = Offset(w, rightCurY),
                            end = Offset(w - tickLength, rightCurY),
                            strokeWidth = strokeWidth
                        )

                        if (isCm && rightMm > 0) {
                            val cmVal = rightMm / 10
                            drawContext.canvas.nativeCanvas.drawText(
                                PRECALCULATED_INT_STRINGS.getOrElse(cmVal) { "$cmVal" },
                                w - tickLength - pad8Px,
                                rightCurY - fontVerticalOffset,
                                textPaintRight
                            )
                        }
                    }
                    rightMm++
                }
            }

            // Extended Top Horizontal Precision Ruler on Tablets & Unfolded Foldables
            if (showExtendedHorizontalScale && w > rulerWidth * 2.5f) {
                val horizStartX = rulerWidth
                val horizEndX = w - rulerWidth
                val topBandHeight = 52.dp.toPx()
                drawRect(
                    color = colorSurface.copy(alpha = 0.92f),
                    topLeft = Offset(horizStartX, 0f),
                    size = Size(horizEndX - horizStartX, topBandHeight)
                )
                drawLine(
                    color = colorOutline.copy(alpha = 0.5f),
                    start = Offset(horizStartX, topBandHeight),
                    end = Offset(horizEndX, topBandHeight),
                    strokeWidth = borderStrokeWidth
                )

                val zeroX = horizStartX + 18.dp.toPx()
                var horizMm = 0
                while (horizMm < 5000) {
                    val curX = (zeroX + horizMm * xMmInPxD).toFloat()
                    if (curX >= horizEndX - 10.dp.toPx()) break
                    val inVerticalHinge = verticalHingeBand != null &&
                        curX >= verticalHingeBand.first &&
                        curX <= verticalHingeBand.second
                    if (!inVerticalHinge) {
                        val isCm = (horizMm % 10 == 0)
                        val isHalfCm = (horizMm % 5 == 0)
                        val tickH = when {
                            isCm -> 24.dp.toPx()
                            isHalfCm -> 16.dp.toPx()
                            else -> 9.dp.toPx()
                        }
                        val tickColor = if (isCm) colorPrimary else colorOnSurface.copy(alpha = 0.35f)
                        val strokeWidth = if (isCm) strokeCmWidth else strokeMmWidth
                        drawLine(
                            color = tickColor,
                            start = Offset(curX, 0f),
                            end = Offset(curX, tickH),
                            strokeWidth = strokeWidth
                        )
                        if (isCm) {
                            val cmVal = horizMm / 10
                            val labelStr = if (cmVal == 0) "0 cm" else PRECALCULATED_INT_STRINGS.getOrElse(cmVal) { "$cmVal" }
                            drawContext.canvas.nativeCanvas.drawText(
                                labelStr,
                                curX + 4.dp.toPx(),
                                tickH + 13.dp.toPx(),
                                textPaintLeft
                            )
                        }
                    }
                    horizMm++
                }
            }

            // Hinge Exclusion Band Visual Hint (so user never aligns objects across physical crease)
            horizontalHingeBand?.let { (hTop, hBot) ->
                val bandH = (hBot - hTop).coerceAtLeast(8.dp.toPx())
                drawRect(
                    color = colorOutline.copy(alpha = 0.16f),
                    topLeft = Offset(0f, hTop),
                    size = Size(w, bandH)
                )
                drawLine(
                    color = colorPrimary.copy(alpha = 0.4f),
                    start = Offset(0f, hTop),
                    end = Offset(w, hTop),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = caliperDashEffect
                )
                drawLine(
                    color = colorPrimary.copy(alpha = 0.4f),
                    start = Offset(0f, hTop + bandH),
                    end = Offset(w, hTop + bandH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = caliperDashEffect
                )
            }
            verticalHingeBand?.let { (vLeft, vRight) ->
                val bandW = (vRight - vLeft).coerceAtLeast(8.dp.toPx())
                drawRect(
                    color = colorOutline.copy(alpha = 0.16f),
                    topLeft = Offset(vLeft, 0f),
                    size = Size(bandW, h)
                )
            }

            // Interactive Dual-Line Caliper Overlay Canvas
            if (isCaliperEnabled) {
                val yA = (zeroY + caliperLineAMm * mmInPxD).toFloat().coerceIn(zeroY, h)
                val yB = (zeroY + caliperLineBMm * mmInPxD).toFloat().coerceIn(zeroY, h)
                val bandTop = min(yA, yB)
                val bandBottom = max(yA, yB)

                drawRect(
                    color = colorPrimary.copy(alpha = 0.14f),
                    topLeft = Offset(0f, bandTop),
                    size = Size(w, (bandBottom - bandTop).coerceAtLeast(1f))
                )

                // Hairline A
                drawLine(
                    color = colorPrimary,
                    start = Offset(0f, yA),
                    end = Offset(w, yA),
                    strokeWidth = 2.2.dp.toPx()
                )
                // Hairline B
                drawLine(
                    color = caliperAccentColor,
                    start = Offset(0f, yB),
                    end = Offset(w, yB),
                    strokeWidth = 2.2.dp.toPx(),
                    pathEffect = if (draggingLine == 2) null else caliperDashEffect
                )

                // Center Vertical Dimension Arrow between A & B (avoids vertical hinge in Book posture)
                if (bandBottom - bandTop > 18.dp.toPx()) {
                    val midX = if (verticalHingeBand != null && w > 0f) {
                        val rightPaneMid = (verticalHingeBand.second + w - rulerWidth) / 2f
                        rightPaneMid.coerceIn(rulerWidth + 24.dp.toPx(), w - rulerWidth - 24.dp.toPx())
                    } else {
                        w / 2f
                    }
                    drawLine(
                        color = colorPrimary.copy(alpha = 0.7f),
                        start = Offset(midX, bandTop + 4.dp.toPx()),
                        end = Offset(midX, bandBottom - 4.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // Draggable Grip Caps (A on Left-Center, B on Right-Center)
                val gripRadius = 14.dp.toPx()
                val gripAX = rulerWidth + 28.dp.toPx()
                val gripBX = w - rulerWidth - 28.dp.toPx()
                drawCircle(color = colorPrimary, radius = gripRadius, center = Offset(gripAX, yA))
                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 4.dp.toPx(), center = Offset(gripAX, yA))
                drawCircle(color = caliperAccentColor, radius = gripRadius, center = Offset(gripBX, yB))
                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 4.dp.toPx(), center = Offset(gripBX, yB))
            }
        }

        // Top Center: M3 Expressive "編輯" Floating Pill Button
        val editInteraction = remember { MutableInteractionSource() }
        val isEditPressed by editInteraction.collectIsPressedAsState()
        val editCorner by rememberExpressivePressCorner(
            isPressed = isEditPressed,
            restingCorner = 24.dp,
            pressedCorner = 14.dp
        )
        val editBg by animateColorAsState(
            targetValue = if (isCustomConfigured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            animationSpec = ExpressiveMotion.effectsDefault(),
            label = "rulerEditBg"
        )
        val editFg by animateColorAsState(
            targetValue = if (isCustomConfigured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            animationSpec = ExpressiveMotion.effectsDefault(),
            label = "rulerEditFg"
        )

        // Hinge-aware repositioning for top Edit & bottom Caliper controls:
        // In Book posture, place controls inside the unobstructed right pane instead of straddling the vertical crease.
        val rightPaneStartDp = if (adaptiveState.isBook && hingeMetrics.containerWidthPx > 0) {
            hingeMetrics.leftPaneWidthDp(density) + hingeMetrics.verticalHingeGapDp(density)
        } else {
            0.dp
        }
        val editTopOffset = if (showExtendedHorizontalScale) 62.dp else 10.dp

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showEditDialog = true
            },
            interactionSource = editInteraction,
            shape = RoundedCornerShape(editCorner),
            color = editBg,
            border = BorderStroke(
                width = 1.dp,
                color = if (isCustomConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier
                .align(if (adaptiveState.isBook) Alignment.TopEnd else Alignment.TopCenter)
                .displayCutoutPadding()
                .padding(
                    top = editTopOffset,
                    end = if (adaptiveState.isBook) 92.dp else 0.dp
                )
                .heightIn(min = 48.dp)
                .testTag("ruler_edit_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = tr("ruler_edit_desc"),
                    tint = editFg,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tr("ruler_edit_btn"),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = editFg
                )
            }
        }

        // Bottom Caliper Toggle Pill OR Active Dual-Line Caliper Readout Card
        Box(
            modifier = Modifier
                .align(if (adaptiveState.isBook) Alignment.BottomEnd else Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = if (adaptiveState.isBook) rightPaneStartDp + 12.dp else 20.dp,
                    end = if (adaptiveState.isBook) 92.dp else 20.dp,
                    bottom = bottomPadding + 14.dp
                )
                .widthIn(max = 460.dp)
        ) {
            if (!isCaliperEnabled) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        caliperLineAMm = 10.0
                        caliperLineBMm = 50.0.coerceAtMost(maxVisibleMm - 5.0)
                        isCaliperEnabled = true
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ruler_caliper_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Straighten,
                            contentDescription = tr("ruler_caliper_desc"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = tr("ruler_caliper_title"),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                val deltaMm = computeCaliperDeltaMm(caliperLineAMm, caliperLineBMm)
                val deltaCmText = String.format(Locale.US, "%.2f cm", deltaMm / 10.0)
                val deltaSummaryText = formatCaliperDeltaSummary(deltaMm)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ruler_caliper_card")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("ruler_caliper_delta_hint"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = deltaCmText,
                                    style = ExpressiveTypography.titleNumeral,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = deltaSummaryText,
                                    style = ExpressiveTypography.labelNumeral,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isCaliperEnabled = false
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("ruler_caliper_close_button")
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = tr("ruler_caliper_close"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val span = computeCaliperDeltaMm(caliperLineAMm, caliperLineBMm).coerceAtLeast(10.0)
                                    caliperLineAMm = 0.0
                                    caliperLineBMm = span.coerceIn(5.0, maxVisibleMm)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("ruler_caliper_zero_button")
                            ) {
                                Icon(Icons.Rounded.VerticalAlignTop, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("ruler_zero_origin"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(deltaSummaryText))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.showToast(
                                        TranslationManager.formatString("ruler_copied_toast_fmt", currentLanguage, deltaSummaryText)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("ruler_caliper_copy_button")
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("ruler_copy_reading"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        RulerEditDialog(
            calibrationFactor = safeFactor,
            fontScale = safeFontScale,
            onFontScaleChange = { scale ->
                viewModel.updateRulerFontScale(scale, persistImmediately = true)
            },
            onCalibrationChange = { factor ->
                viewModel.updateRulerCalibration(factor, persistImmediately = true)
            },
            onResetAll = {
                viewModel.updateRulerFontScale(1.0f, persistImmediately = true)
                viewModel.updateRulerCalibration(1.0f, persistImmediately = true)
                viewModel.triggerHapticFeedback(HapticType.CLICK)
            },
            onDismiss = { showEditDialog = false }
        )
    }
    }
}

/**
 * 螢幕尺編輯視窗：
 * 1. 調整數字大小 (0.8x ~ 1.8x 與快速預設群組)
 * 2. 實體比例校正 (信用卡 85.6 mm 參照與 ±0.002x 微調)
 */
@Composable
fun RulerEditDialog(
    calibrationFactor: Float,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onCalibrationChange: (Float) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 520.dp)
            .testTag("ruler_edit_dialog"),
        shape = RoundedCornerShape(28.dp),
        icon = {
            EnableWindowBlur(blurRadiusDp = 44)
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = tr("ruler_edit_dialog_title"),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RulerEditBodyContent(
                    calibrationFactor = calibrationFactor,
                    fontScale = fontScale,
                    onFontScaleChange = onFontScaleChange,
                    onCalibrationChange = onCalibrationChange
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(tr("common_done"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onResetAll,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(tr("common_reset_all"))
            }
        }
    )
}

@Composable
fun RulerEditBodyContent(
    calibrationFactor: Float,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onCalibrationChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val currentLang = LocalAppLanguage.current
    val displayMetrics = context.resources.displayMetrics
    val ydpi = remember(displayMetrics) {
        val y = displayMetrics.ydpi
        if (y > 50f && !y.isNaN() && !y.isInfinite()) y else displayMetrics.densityDpi.toFloat()
    }
    val safeFactor = calibrationFactor.coerceIn(0.80f, 1.25f)
    val safeFontScale = fontScale.coerceIn(0.8f, 1.8f)

    val fontPresets = remember(currentLang) {
        listOf(
            ExpressiveButtonOption("1.0", TranslationManager.getString("ruler_font_std", currentLang), testTag = "ruler_font_preset_std"),
            ExpressiveButtonOption("1.3", TranslationManager.getString("ruler_font_large", currentLang), testTag = "ruler_font_preset_large"),
            ExpressiveButtonOption("1.6", TranslationManager.getString("ruler_font_xl", currentLang), testTag = "ruler_font_preset_xl")
        )
    }
    val activeFontPreset = remember(safeFontScale) {
        when {
            abs(safeFontScale - 1.0f) < 0.08f -> "1.0"
            abs(safeFontScale - 1.3f) < 0.08f -> "1.3"
            abs(safeFontScale - 1.6f) < 0.12f -> "1.6"
            else -> ""
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature 1: 調整數字大小
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FormatSize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = tr("ruler_step_font_size"),
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${(safeFontScale * 100).roundToInt()}%",
                        style = ExpressiveTypography.labelNumeral,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                ExpressiveConnectedButtonGroup(
                    options = fontPresets,
                    selectedValue = activeFontPreset,
                    onValueSelected = { key ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFontScaleChange(key.toFloatOrNull() ?: 1.0f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Slider(
                    value = safeFontScale,
                    onValueChange = { raw ->
                        val stepped = (raw * 20f).roundToInt() / 20f
                        onFontScaleChange(stepped.coerceIn(0.8f, 1.8f))
                    },
                    valueRange = 0.8f..1.8f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ruler_font_scale_slider")
                )

                // Live preview strip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("ruler_preview_numbers"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "0   1   2   3  cm",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            fontSize = (11f * safeFontScale).sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Feature 2: 校正
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = tr("ruler_step_calibration"),
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.3fx", safeFactor),
                        style = ExpressiveTypography.labelNumeral,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = tr("ruler_calibration_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Connected [-] / [+] / [1.000x] fine calibration buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val decInt = remember { MutableInteractionSource() }
                    val decPressed by decInt.collectIsPressedAsState()
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val next = ((safeFactor - 0.002f) * 1000f).roundToInt() / 1000f
                            onCalibrationChange(next.coerceIn(0.80f, 1.25f))
                        },
                        interactionSource = decInt,
                        shape = rememberExpressiveConnectedShape(0, 3, false, decPressed),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ruler_cal_dec_btn")
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = tr("ruler_scale_dec_desc"), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("ruler_scale_dec"), fontWeight = FontWeight.Bold)
                    }

                    val rstInt = remember { MutableInteractionSource() }
                    val rstPressed by rstInt.collectIsPressedAsState()
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCalibrationChange(1.0f)
                        },
                        interactionSource = rstInt,
                        shape = rememberExpressiveConnectedShape(1, 3, abs(safeFactor - 1.0f) <= 0.001f, rstPressed),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ruler_cal_reset_btn")
                    ) {
                        Text("1.000x", style = ExpressiveTypography.labelNumeral, fontWeight = FontWeight.Bold)
                    }

                    val incInt = remember { MutableInteractionSource() }
                    val incPressed by incInt.collectIsPressedAsState()
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val next = ((safeFactor + 0.002f) * 1000f).roundToInt() / 1000f
                            onCalibrationChange(next.coerceIn(0.80f, 1.25f))
                        },
                        interactionSource = incInt,
                        shape = rememberExpressiveConnectedShape(2, 3, false, incPressed),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ruler_cal_inc_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = tr("ruler_scale_inc_desc"), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("ruler_scale_inc"), fontWeight = FontWeight.Bold)
                    }
                }

                Slider(
                    value = safeFactor,
                    onValueChange = { raw ->
                        val stepped = ((raw * 500f).roundToInt()) / 500f
                        onCalibrationChange(stepped.coerceIn(0.80f, 1.25f))
                    },
                    valueRange = 0.80f..1.25f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ruler_calibration_slider")
                )

                // Visual 50 mm reference gauge bar
                val mmInDp = with(LocalDensity.current) { ((ydpi / 25.4f) * safeFactor).toDp() }
                val refWidthDp = (mmInDp * 50f).coerceIn(80.dp, 260.dp)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(refWidthDp)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tr("ruler_ref_bar_caption"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
