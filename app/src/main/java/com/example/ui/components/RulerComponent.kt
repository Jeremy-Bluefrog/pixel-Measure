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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
 * Modern High-Precision 2D Screen Ruler with Material 3 Motion transitions:
 * - Fluid entrance reveal for graduation scale
 * - Spring-based caliper opening on activation
 * - Reactive drag scale and glowing caliper touch handles
 * - Animated measurement readout transitions
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
    val defaultTargetBottomY = zeroY + 150f * (mmInPx / 10f)

    var caliperTopY by remember { mutableStateOf(zeroY) }
    var targetCaliperBottomY by remember { mutableStateOf(defaultTargetBottomY) }
    var draggedCaliper by remember { mutableStateOf(-1) }

    // Material 3 Launch & Activation Entrance Animations
    val rulerRevealProgress = remember { Animatable(0f) }
    val caliperSpreadProgress = remember { Animatable(0.2f) }

    LaunchedEffect(Unit) {
        // Staggered M3 spring reveals on ruler startup
        rulerRevealProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    LaunchedEffect(Unit) {
        caliperSpreadProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Smoothly animated bottom caliper position taking entrance animation into account
    val effectiveCaliperBottomY = if (caliperSpreadProgress.value < 0.999f && draggedCaliper == -1) {
        zeroY + (targetCaliperBottomY - zeroY) * caliperSpreadProgress.value
    } else {
        targetCaliperBottomY
    }

    val measuredCm = abs(effectiveCaliperBottomY - caliperTopY) / (mmInPx * 10f)

    // Interactive Drag Spring Dynamics
    val topCaliperScale by animateFloatAsState(
        targetValue = if (draggedCaliper == 0) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "topCaliperScale"
    )
    val bottomCaliperScale by animateFloatAsState(
        targetValue = if (draggedCaliper == 1) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bottomCaliperScale"
    )
    val topCaliperGlow by animateFloatAsState(
        targetValue = if (draggedCaliper == 0) 1.0f else 0.0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "topCaliperGlow"
    )
    val bottomCaliperGlow by animateFloatAsState(
        targetValue = if (draggedCaliper == 1) 1.0f else 0.0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "bottomCaliperGlow"
    )

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
        // Main Ruler Canvas with Material 3 Entrance Scale & Offset Transition
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggedCaliper = if (abs(offset.y - caliperTopY) < abs(offset.y - effectiveCaliperBottomY)) 0 else 1
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val prevCm = (abs(targetCaliperBottomY - caliperTopY) / (mmInPx * 10f)).toInt()
                            if (draggedCaliper == 0) {
                                caliperTopY = (caliperTopY + dragAmount.y).coerceIn(zeroY, size.height - 120f)
                            } else {
                                targetCaliperBottomY = (targetCaliperBottomY + dragAmount.y).coerceIn(zeroY, size.height - 120f)
                            }
                            val newCm = (abs(targetCaliperBottomY - caliperTopY) / (mmInPx * 10f)).toInt()
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
            val reveal = rulerRevealProgress.value
            val baseRulerWidth = 160.dp.toPx()
            val animatedRulerWidth = baseRulerWidth * (0.5f + 0.5f * reveal)
            val rulerX = w - animatedRulerWidth

            // Draw Ruler Body background with soft subtle shadow
            drawRect(
                color = Color.Black.copy(alpha = 0.06f * reveal),
                topLeft = Offset(rulerX - 6.dp.toPx(), 0f),
                size = androidx.compose.ui.geometry.Size(animatedRulerWidth + 6.dp.toPx(), h)
            )

            drawRect(
                color = colorSurface,
                topLeft = Offset(rulerX, 0f),
                size = androidx.compose.ui.geometry.Size(animatedRulerWidth, h)
            )

            // Draw Millimeter and Centimeter graduation ticks with stagger reveal
            var curY = zeroY
            var mmCount = 0

            while (curY < h) {
                val isCm = (mmCount % 10 == 0)
                val isHalfCm = (mmCount % 5 == 0)

                val tickLength = when {
                    isCm -> 48.dp.toPx() * reveal
                    isHalfCm -> 32.dp.toPx() * reveal
                    else -> 18.dp.toPx() * reveal
                }

                val tickColor = if (isCm) colorPrimary.copy(alpha = reveal) else colorOnSurface.copy(alpha = 0.35f * reveal)
                val strokeWidth = if (isCm) 2.5.dp.toPx() else 1.dp.toPx()

                drawLine(
                    color = tickColor,
                    start = Offset(rulerX, curY),
                    end = Offset(rulerX + tickLength, curY),
                    strokeWidth = strokeWidth
                )

                if (isCm && reveal > 0.5f) {
                    val cmVal = mmCount / 10
                    textPaint.alpha = (170 * reveal).toInt()
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
            val minCaliperY = minOf(caliperTopY, effectiveCaliperBottomY)
            val maxCaliperY = maxOf(caliperTopY, effectiveCaliperBottomY)

            // Span highlight band with dynamic gradient
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        colorPrimary.copy(alpha = 0.04f * reveal),
                        colorPrimary.copy(alpha = 0.16f * reveal)
                    ),
                    startX = 0f,
                    endX = w
                ),
                topLeft = Offset(0f, minCaliperY),
                size = androidx.compose.ui.geometry.Size(w, maxCaliperY - minCaliperY)
            )

            // Draw top and bottom caliper edge lines with glow
            val isTopActive = draggedCaliper == 0
            val isBottomActive = draggedCaliper == 1
            val baseTabWidth = 32.dp.toPx()

            // Top caliper edge tab & luminous line
            val topTabWidth = baseTabWidth * topCaliperScale
            if (isTopActive) {
                // Glow
                drawRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.4f * topCaliperGlow),
                    topLeft = Offset(rulerX - 16f, caliperTopY - 8f),
                    size = androidx.compose.ui.geometry.Size(topTabWidth + 16f, 16f)
                )
            }
            drawRect(
                color = if (isTopActive) Color(0xFF00E5FF) else colorPrimary,
                topLeft = Offset(rulerX - 10f, caliperTopY - (3f * topCaliperScale)),
                size = androidx.compose.ui.geometry.Size(topTabWidth + 10f, 6f * topCaliperScale)
            )

            // Bottom caliper edge tab & luminous line
            val bottomTabWidth = baseTabWidth * bottomCaliperScale
            if (isBottomActive) {
                // Glow
                drawRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.4f * bottomCaliperGlow),
                    topLeft = Offset(rulerX - 16f, effectiveCaliperBottomY - 8f),
                    size = androidx.compose.ui.geometry.Size(bottomTabWidth + 16f, 16f)
                )
            }
            drawRect(
                color = if (isBottomActive) Color(0xFF00E5FF) else colorPrimary,
                topLeft = Offset(rulerX - 10f, effectiveCaliperBottomY - (3f * bottomCaliperScale)),
                size = androidx.compose.ui.geometry.Size(bottomTabWidth + 10f, 6f * bottomCaliperScale)
            )
        }

        // Live Measurement Floating Readout Card with Refined Glassmorphism & Spring Reveal
        val cardAlpha by animateFloatAsState(
            targetValue = if (rulerRevealProgress.value > 0.4f) 1f else 0f,
            animationSpec = tween(300),
            label = "cardAlpha"
        )
        val cardScale by animateFloatAsState(
            targetValue = if (rulerRevealProgress.value > 0.4f) 1f else 0.9f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "cardScale"
        )

        Surface(
            color = colorSurface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(
                1.5.dp,
                if (draggedCaliper != -1) Color(0xFF00E5FF).copy(alpha = 0.6f) else colorPrimary.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 24.dp)
                .alpha(cardAlpha)
                .scale(cardScale)
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
                        (fadeIn(tween(180)) + slideInVertically(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) { it / 3 }).togetherWith(
                            fadeOut(tween(140)) + slideOutVertically { -it / 3 }
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

                // Quick Fine-Tuning Controls (-1mm / +1mm / Reset) with tactile spring interactions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            targetCaliperBottomY = (targetCaliperBottomY - (mmInPx / 10f)).coerceAtLeast(caliperTopY)
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
                            targetCaliperBottomY = (targetCaliperBottomY + (mmInPx / 10f))
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
                            targetCaliperBottomY = zeroY + 100f * (mmInPx / 10f)
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
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("保存測量記錄", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

