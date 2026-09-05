package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.MeasureViewModel

/**
 * Pure, Minimalist 2D Screen Physical Ruler
 * Displays clean physical-accurate millimeter and centimeter ticks along the screen edge.
 */
@Composable
fun RulerComponent(
    viewModel: MeasureViewModel,
    onShowHistoryClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics

    val ydpi = remember(displayMetrics) {
        val y = displayMetrics.ydpi
        if (y > 50f && !y.isNaN() && !y.isInfinite()) y else displayMetrics.densityDpi.toFloat()
    }
    val mmInPx = ydpi / 25.4f

    val zeroY = with(LocalDensity.current) { 48.dp.toPx() }

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface

    val textPaint = remember(colorOnSurface) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(180, 100, 116, 139)
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
        // Pure Ruler Canvas without interactive caliper lines or banners
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val rulerWidth = 120.dp.toPx()
            val rulerX = w - rulerWidth

            // Draw Ruler Body background
            drawRect(
                color = colorSurface,
                topLeft = Offset(rulerX, 0f),
                size = androidx.compose.ui.geometry.Size(rulerWidth, h)
            )

            // Draw Millimeter and Centimeter graduation ticks
            var curY = zeroY
            var mmCount = 0

            while (curY < h) {
                val isCm = (mmCount % 10 == 0)
                val isHalfCm = (mmCount % 5 == 0)

                val tickLength = when {
                    isCm -> 36.dp.toPx()
                    isHalfCm -> 24.dp.toPx()
                    else -> 14.dp.toPx()
                }

                val tickColor = if (isCm) colorPrimary else colorOnSurface.copy(alpha = 0.35f)
                val strokeWidth = if (isCm) 2.dp.toPx() else 1.dp.toPx()

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
                        rulerX + tickLength + 28f,
                        curY + 10f,
                        textPaint
                    )
                }

                curY += mmInPx
                mmCount++
            }
        }
    }
}
