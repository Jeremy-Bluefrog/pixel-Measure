package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.rememberExpressiveConnectedShape
import com.example.ui.theme.rememberExpressivePressCorner
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 全螢幕高精度雙邊螢幕直尺 (Full-Screen Precision Dual-Edge Screen Ruler)
 * Enhanced with Material 3 Expressive Floating Toolbar & Connected Micro-Calibration Controls.
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

    val calibrationFactor by viewModel.rulerCalibration.collectAsStateWithLifecycle()

    val ydpi = remember(displayMetrics) {
        val y = displayMetrics.ydpi
        if (y > 50f && !y.isNaN() && !y.isInfinite()) y else displayMetrics.densityDpi.toFloat()
    }
    val safeFactor = if (calibrationFactor > 0.05f && !calibrationFactor.isNaN() && !calibrationFactor.isInfinite()) calibrationFactor else 1.0f
    val mmInPx = ((ydpi / 25.4f) * safeFactor).coerceIn(1.5f, 50.0f)

    val zeroY = with(density) { 52.dp.toPx() }

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOutline = MaterialTheme.colorScheme.outlineVariant

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

    val fontVerticalOffset = remember(textPaintLeft) {
        (textPaintLeft.descent() + textPaintLeft.ascent()) / 2f
    }

    val isCalibrated = abs(safeFactor - 1.0f) > 0.001f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
            drawContext.canvas.nativeCanvas.drawText("cm", w - pad12Px, zeroY - pad10Px, unitPaintRight)

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
        // TOP-LEFT: M3 EXPRESSIVE FLOATING TOOLBAR CALIBRATION STRIP
        // -------------------------------------------------------------
        ExpressiveFloatingToolbar(
            vibrant = isCalibrated,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 6.dp)
        ) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = "校準",
                tint = if (isCalibrated) MaterialTheme.colorScheme.onPrimaryContainer else colorPrimary,
                modifier = Modifier.size(15.dp)
            )

            Text(
                text = String.format(Locale.US, "%.3fx", safeFactor),
                style = ExpressiveTypography.labelNumeral,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isCalibrated) MaterialTheme.colorScheme.onPrimaryContainer else colorPrimary
            )

            // Connected [-], [+], [Reset] icon buttons
            val btnCount = if (isCalibrated) 3 else 2
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val decInteraction = remember { MutableInteractionSource() }
                val isDecPressed by decInteraction.collectIsPressedAsState()
                val decShape = rememberExpressiveConnectedShape(
                    index = 0,
                    count = btnCount,
                    isActive = false,
                    isPressed = isDecPressed,
                    outerCorner = 14.dp,
                    innerRestingCorner = 4.dp,
                    innerActiveCorner = 12.dp
                )
                Surface(
                    onClick = {
                        val next = ((safeFactor - 0.002f) * 1000f).roundToInt() / 1000f
                        viewModel.updateRulerCalibration(next.coerceIn(0.80f, 1.25f), persistImmediately = true)
                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                    },
                    interactionSource = decInteraction,
                    shape = decShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Remove, contentDescription = "微調減少", modifier = Modifier.size(14.dp), tint = colorOnSurface)
                    }
                }

                val incInteraction = remember { MutableInteractionSource() }
                val isIncPressed by incInteraction.collectIsPressedAsState()
                val incShape = rememberExpressiveConnectedShape(
                    index = 1,
                    count = btnCount,
                    isActive = false,
                    isPressed = isIncPressed,
                    outerCorner = 14.dp,
                    innerRestingCorner = 4.dp,
                    innerActiveCorner = 12.dp
                )
                Surface(
                    onClick = {
                        val next = ((safeFactor + 0.002f) * 1000f).roundToInt() / 1000f
                        viewModel.updateRulerCalibration(next.coerceIn(0.80f, 1.25f), persistImmediately = true)
                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                    },
                    interactionSource = incInteraction,
                    shape = incShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, contentDescription = "微調增加", modifier = Modifier.size(14.dp), tint = colorOnSurface)
                    }
                }

                if (isCalibrated) {
                    val rstInteraction = remember { MutableInteractionSource() }
                    val isRstPressed by rstInteraction.collectIsPressedAsState()
                    val rstShape = rememberExpressiveConnectedShape(
                        index = 2,
                        count = 3,
                        isActive = false,
                        isPressed = isRstPressed,
                        outerCorner = 14.dp,
                        innerRestingCorner = 4.dp,
                        innerActiveCorner = 12.dp
                    )
                    Surface(
                        onClick = {
                            viewModel.updateRulerCalibration(1.0f, persistImmediately = true)
                            viewModel.triggerHapticFeedback(HapticType.CLICK)
                        },
                        interactionSource = rstInteraction,
                        shape = rstShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = "重設校準", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // TOP-RIGHT CORNER: EXPRESSIVE HISTORY SHORTCUT
        // -------------------------------------------------------------
        val histInteraction = remember { MutableInteractionSource() }
        val isHistPressed by histInteraction.collectIsPressedAsState()
        val histCorner by rememberExpressivePressCorner(
            isPressed = isHistPressed,
            restingCorner = 18.dp,
            pressedCorner = 10.dp
        )
        val histScale by animateFloatAsState(
            targetValue = if (isHistPressed) 0.90f else 1.0f,
            animationSpec = ExpressiveMotion.spatialFast(),
            label = "rulerHistScale"
        )
        Surface(
            onClick = onShowHistoryClick,
            interactionSource = histInteraction,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(histCorner),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 6.dp)
                .size(36.dp)
                .graphicsLayer {
                    scaleX = histScale
                    scaleY = histScale
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = "歷史記錄",
                    tint = colorOnSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
