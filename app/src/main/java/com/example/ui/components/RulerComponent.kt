package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
    var draggedCaliper by remember { mutableStateOf(-1) }

    val measuredCm = abs(caliperBottomY - caliperTopY) / (mmInPx * 10f)

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface

    val textPaint = remember(colorOnSurface) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(170, 100, 116, 139)
            textSize = 28f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }

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
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggedCaliper = if (abs(offset.y - caliperTopY) < abs(offset.y - caliperBottomY)) 0 else 1
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val prevCm = (abs(caliperBottomY - caliperTopY) / (mmInPx * 10f)).toInt()
                            if (draggedCaliper == 0) {
                                caliperTopY = (caliperTopY + dragAmount.y).coerceIn(zeroY, size.height - 120f)
                            } else {
                                caliperBottomY = (caliperBottomY + dragAmount.y).coerceIn(zeroY, size.height - 120f)
                            }
                            val newCm = (abs(caliperBottomY - caliperTopY) / (mmInPx * 10f)).toInt()
                            if (newCm != prevCm) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            draggedCaliper = -1
                        },
                        onDragCancel = {
                            draggedCaliper = -1
                        }
                    )
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

            // Draw clean edge indicator tabs on the ruler for top and bottom calipers (no full-screen lines)
            val isTopActive = draggedCaliper == 0
            val isBottomActive = draggedCaliper == 1
            val tabWidth = 28.dp.toPx()

            // Top caliper edge tab
            drawRect(
                color = if (isTopActive) Color(0xFF00E5FF) else colorPrimary,
                topLeft = Offset(rulerX - 10f, caliperTopY - 4f),
                size = androidx.compose.ui.geometry.Size(tabWidth + 10f, 8f)
            )

            // Bottom caliper edge tab
            drawRect(
                color = if (isBottomActive) Color(0xFF00E5FF) else colorPrimary,
                topLeft = Offset(rulerX - 10f, caliperBottomY - 4f),
                size = androidx.compose.ui.geometry.Size(tabWidth + 10f, 8f)
            )
        }

        // Live Measurement Floating Readout Card with Refined Glassmorphism
        Surface(
            color = colorSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, colorPrimary.copy(alpha = 0.25f)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 24.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = colorPrimary.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Straighten,
                                contentDescription = null,
                                tint = colorPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "高精度螢幕游標測量",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorOnSurface
                    )
                }

                val formattedText = viewModel.formatLength(measuredCm / 100.0, selectedUnit)
                AnimatedContent(
                    targetState = formattedText,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically { it / 4 }).togetherWith(
                            fadeOut(tween(140)) + slideOutVertically { -it / 4 }
                        )
                    },
                    label = "rulerReadoutAnim"
                ) { targetVal ->
                    Text(
                        text = targetVal,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = colorPrimary
                    )
                }

                // Quick Fine-Tuning Controls (-1mm / +1mm / Reset)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            caliperBottomY = (caliperBottomY - (mmInPx / 10f)).coerceAtLeast(caliperTopY)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("-1mm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            caliperBottomY = (caliperBottomY + (mmInPx / 10f))
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("+1mm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedIconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            caliperTopY = zeroY
                            caliperBottomY = zeroY + 100f * (mmInPx / 10f)
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        ShareUtility.captureViewSnapshot(localView) { path ->
                            viewModel.saveRulerRecord(cmVal = measuredCm.toDouble(), imagePath = path)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorPrimary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp).fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("保存測量記錄", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
