package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 全螢幕高精度雙邊螢幕直尺 (Full-Screen Precision Dual-Edge Screen Ruler)
 *
 * 核心特色：
 * 1. 自動全螢幕沉浸式量測，無頂部標題列與手電筒干擾，極致利用螢幕全高。
 * 2. 移除切換單位干擾按鈕，雙邊均勻清晰刻度，左側標註公制 (cm)，右側零點基準對齊。
 * 3. 矯正調整面板精簡移至左上角 (Top-Left)，以超小型 Mini-Strip 呈現微調步進 (0.002x) 與即時比例。
 */
private val PRECALCULATED_INT_STRINGS = Array(301) { "$it" }

@Composable
fun RulerComponent(
    viewModel: MeasureViewModel,
    onShowHistoryClick: () -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics

    // Calibration factor from ViewModel state (persisted in SharedPreferences)
    val calibrationFactor by viewModel.rulerCalibration.collectAsState()
    val rulerTheme by viewModel.rulerTheme.collectAsState()

    val rulerAccentColor = when (rulerTheme) {
        "NEON_CYAN" -> androidx.compose.ui.graphics.Color(0xFF00E5FF)
        "HIGH_CONTRAST" -> androidx.compose.ui.graphics.Color(0xFFFACC15)
        else -> MaterialTheme.colorScheme.primary
    }

    // Device physical density and millimeter / inch pixel calculations
    val ydpi = remember(displayMetrics) {
        val y = displayMetrics.ydpi
        if (y > 50f && !y.isNaN() && !y.isInfinite()) y else displayMetrics.densityDpi.toFloat()
    }
    val safeFactor = if (calibrationFactor > 0.05f && !calibrationFactor.isNaN() && !calibrationFactor.isInfinite()) calibrationFactor else 1.0f
    val mmInPx = ((ydpi / 25.4f) * safeFactor).coerceIn(1.5f, 50.0f)

    // Full screen Zero Y baseline: positioned comfortably below top status bar / notch
    val zeroY = with(density) { 52.dp.toPx() }

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOutline = MaterialTheme.colorScheme.outlineVariant

    // Native text paints with sp-scaled text sizes and font metrics
    val textPaintLeft = remember(colorOnSurface, density) {
        android.graphics.Paint().apply {
            color = colorOnSurface.copy(alpha = 0.85f).toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    val textPaintRight = remember(colorOnSurface, density) {
        android.graphics.Paint().apply {
            color = colorOnSurface.copy(alpha = 0.85f).toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    val unitPaintLeft = remember(colorPrimary, density) {
        android.graphics.Paint().apply {
            color = colorPrimary.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    val unitPaintRight = remember(colorPrimary, density) {
        android.graphics.Paint().apply {
            color = colorPrimary.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    // Font vertical centering offset for tick alignment
    val fontVerticalOffset = remember(textPaintLeft) {
        (textPaintLeft.descent() + textPaintLeft.ascent()) / 2f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // -------------------------------------------------------------
        // Dual-Edge Full-Screen Ruler Canvas
        // -------------------------------------------------------------
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val rulerWidth = 70.dp.toPx()
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

            // 1. Left Ruler Body Background
            drawRect(
                color = colorSurface,
                topLeft = Offset(0f, 0f),
                size = Size(rulerWidth, h)
            )
            // Left Ruler Boundary Line
            drawLine(
                color = colorOutline.copy(alpha = 0.5f),
                start = Offset(rulerWidth, 0f),
                end = Offset(rulerWidth, h),
                strokeWidth = borderStrokeWidth
            )

            // 2. Right Ruler Body Background
            val rightRulerX = w - rulerWidth
            drawRect(
                color = colorSurface,
                topLeft = Offset(rightRulerX, 0f),
                size = Size(rulerWidth, h)
            )
            // Right Ruler Boundary Line
            drawLine(
                color = colorOutline.copy(alpha = 0.5f),
                start = Offset(rightRulerX, 0f),
                end = Offset(rightRulerX, h),
                strokeWidth = borderStrokeWidth
            )

            // 3. Zero Reference Baseline connecting Left and Right Rulers across the full screen
            drawLine(
                color = colorPrimary,
                start = Offset(0f, zeroY),
                end = Offset(w, zeroY),
                strokeWidth = zeroStrokeWidth
            )

            // Zero Unit Label Headers
            drawContext.canvas.nativeCanvas.drawText("cm", pad12Px, zeroY - pad10Px, unitPaintLeft)
            drawContext.canvas.nativeCanvas.drawText("cm", w - pad12Px, zeroY - pad10Px, unitPaintRight)

            // 4. Left Edge Graduations (Centimeter & Millimeter ticks)
            var leftCurY = zeroY
            var leftMm = 0
            while (leftCurY < h && leftMm < 3000) {
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

                leftCurY += mmInPx
                leftMm++
            }

            // 5. Right Edge Graduations (Matching Metric Centimeter & Millimeter ticks)
            var rightCurY = zeroY
            var rightMm = 0
            while (rightCurY < h && rightMm < 3000) {
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

                rightCurY += mmInPx
                rightMm++
            }
        }

        // -------------------------------------------------------------
        // TOP-LEFT CORNER: SHRUNK COMPACT CALIBRATION STRIP (左上角微縮校準條)
        // -------------------------------------------------------------
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = "校準",
                    tint = colorPrimary,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(2.dp))

                Text(
                    text = String.format(Locale.US, "%.3fx", safeFactor),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorPrimary
                )

                // Micro Decrement [-] Button
                IconButton(
                    onClick = {
                        val next = ((safeFactor - 0.002f) * 1000f).roundToInt() / 1000f
                        viewModel.updateRulerCalibration(next.coerceIn(0.80f, 1.25f), persistImmediately = true)
                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Rounded.Remove,
                        contentDescription = "微調減少",
                        modifier = Modifier.size(14.dp),
                        tint = colorOnSurface
                    )
                }

                // Micro Increment [+] Button
                IconButton(
                    onClick = {
                        val next = ((safeFactor + 0.002f) * 1000f).roundToInt() / 1000f
                        viewModel.updateRulerCalibration(next.coerceIn(0.80f, 1.25f), persistImmediately = true)
                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "微調增加",
                        modifier = Modifier.size(14.dp),
                        tint = colorOnSurface
                    )
                }

                // Reset to Default (1.000x) if calibrated
                if (abs(safeFactor - 1.0f) > 0.001f) {
                    IconButton(
                        onClick = {
                            viewModel.updateRulerCalibration(1.0f, persistImmediately = true)
                            viewModel.triggerHapticFeedback(HapticType.CLICK)
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "重設校準",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // TOP-RIGHT CORNER: MINIMAL HISTORY SHORTCUT
        // -------------------------------------------------------------
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 6.dp)
        ) {
            IconButton(
                onClick = onShowHistoryClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = "歷史記錄",
                    tint = colorOnSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
