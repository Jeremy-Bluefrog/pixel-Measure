package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.ShareUtility
import com.example.ui.viewmodel.MeasureViewModel
import kotlin.math.abs

/**
 * Modern High-Precision 2D Screen Ruler with dual movable vernier calipers,
 * real hardware DPI scaling, calibration adjustment, and instant save.
 */
@Composable
fun RulerComponent(
    viewModel: MeasureViewModel,
    onShowHistoryClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val haptic = LocalHapticFeedback.current
    val displayMetrics = context.resources.displayMetrics
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val calibrationFactor by viewModel.rulerCalibration.collectAsState()

    val ydpi = remember(displayMetrics) {
        val y = displayMetrics.ydpi
        if (y > 50f && !y.isNaN() && !y.isInfinite()) y else displayMetrics.densityDpi.toFloat()
    }
    val mmInPx = ydpi / 25.4f

    val zeroY = with(LocalDensity.current) { 80.dp.toPx() }
    var caliperTopY by remember { mutableStateOf(zeroY) }
    var caliperBottomY by remember { mutableStateOf(zeroY + 150f * (mmInPx / 10f)) }

    val measuredCm = abs(caliperBottomY - caliperTopY) / (mmInPx * 10f)

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Ruler Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val touchY = change.position.y
                        if (abs(touchY - caliperTopY) < abs(touchY - caliperBottomY)) {
                            caliperTopY = (caliperTopY + dragAmount.y).coerceIn(zeroY, size.height - 120f)
                        } else {
                            caliperBottomY = (caliperBottomY + dragAmount.y).coerceIn(zeroY, size.height - 120f)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val rulerX = w - 160.dp.toPx()

            // Draw Ruler Body background
            drawRect(
                color = colorSurface,
                topLeft = Offset(rulerX, 0f),
                size = androidx.compose.ui.geometry.Size(w - rulerX, h)
            )

            // Draw Millimeter and Centimeter graduation ticks
            var curY = zeroY
            var mmCount = 0

            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 28f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            while (curY < h) {
                val isCm = (mmCount % 10 == 0)
                val isHalfCm = (mmCount % 5 == 0)

                val tickLength = when {
                    isCm -> 48.dp.toPx()
                    isHalfCm -> 32.dp.toPx()
                    else -> 18.dp.toPx()
                }

                val tickColor = if (isCm) colorPrimary else colorOnSurface.copy(alpha = 0.35f)
                val strokeWidth = if (isCm) 2.5.dp.toPx() else 1.dp.toPx()

                drawLine(
                    color = tickColor,
                    start = Offset(rulerX, curY),
                    end = Offset(rulerX + tickLength, curY),
                    strokeWidth = strokeWidth
                )

                if (isCm) {
                    val cmVal = mmCount / 10
                    drawContext.canvas.nativeCanvas.drawText(
                        "$cmVal",
                        rulerX + tickLength + 36f,
                        curY + 10f,
                        textPaint
                    )
                }

                curY += mmInPx
                mmCount++
            }

            // Draw Caliper Guidelines & Span
            val minCaliperY = minOf(caliperTopY, caliperBottomY)
            val maxCaliperY = maxOf(caliperTopY, caliperBottomY)

            // Span highlight band
            drawRect(
                color = colorPrimary.copy(alpha = 0.12f),
                topLeft = Offset(0f, minCaliperY),
                size = androidx.compose.ui.geometry.Size(w, maxCaliperY - minCaliperY)
            )

            // Top Caliper Line
            drawLine(
                color = colorPrimary,
                start = Offset(0f, caliperTopY),
                end = Offset(w, caliperTopY),
                strokeWidth = 3.dp.toPx()
            )

            // Bottom Caliper Line
            drawLine(
                color = colorPrimary,
                start = Offset(0f, caliperBottomY),
                end = Offset(w, caliperBottomY),
                strokeWidth = 3.dp.toPx()
            )
        }

        // Live Measurement Floating Readout Card
        Surface(
            color = colorSurface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "雙游標測量讀數",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = viewModel.formatLength(measuredCm / 100.0, selectedUnit),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = colorPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            ShareUtility.captureViewSnapshot(localView) { path ->
                                viewModel.saveRulerRecord(cmVal = measuredCm.toDouble(), imagePath = path)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("保存記錄")
                    }
                }
            }
        }
    }
}
