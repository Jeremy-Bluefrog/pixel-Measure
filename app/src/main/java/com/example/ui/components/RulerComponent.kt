package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
 * 專業高精度雙邊螢幕游標卡鉗直尺 (Precision Dual-Edge Screen Vernier Caliper)
 *
 * 核心功能：
 * 1. 左右雙邊獨立刻度：左側公制 (mm/cm)，右側可一鍵切換公制 (cm) 或英制 (inch, 1/16" 精準刻度)。
 * 2. 游標卡鉗手勢跟手流暢：單一流水線手勢處理，點擊或拖曳即時響應，無任何延遲與掉幀。
 * 3. 齒輪段落觸感回饋 (Mechanical Tick Haptics)：滑過 1cm、0.5cm 整數刻度時提供輕快段落震動反饋。
 * 4. 游標鎖定 (Lock) 與 ±0.1mm 微調按鈕：支援游標鎖定防止誤碰，並具備 ±0.1mm 微調步進達到游標卡鉗等級精度。
 * 5. 極致流暢校準面板：支援 0.002x 精密步進與連續平滑滑桿（非同步寫入快取，鬆手持久化）。
 * 6. 自適應螢幕密度排版：文字依 sp 向量縮放，垂直基準線以 FontMetrics 完美居中對齊。
 */
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
    val isCalibrationActive by viewModel.isRulerCalibrationActive.collectAsState()

    // Right-edge unit: "cm" or "in"
    var rightUnit by remember { mutableStateOf("cm") }

    // Device physical density and millimeter / inch pixel calculations
    val ydpi = remember(displayMetrics) {
        val y = displayMetrics.ydpi
        if (y > 50f && !y.isNaN() && !y.isInfinite()) y else displayMetrics.densityDpi.toFloat()
    }
    val mmInPx = (ydpi / 25.4f) * calibrationFactor
    val inchInPx = ydpi * calibrationFactor

    // Zero Y baseline: positioned comfortably below top status/controls bar
    val zeroY = with(density) { 86.dp.toPx() }

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

    // Caliper horizontal guide touch state and lock state
    var caliperY by remember { mutableStateOf<Float?>(null) }
    var isCaliperLocked by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Dual-Edge Canvas with High-Responsiveness Gesture Handler
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zeroY, isCaliperLocked, isCalibrationActive) {
                    if (!isCalibrationActive) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (!isCaliperLocked && offset.y >= zeroY) {
                                    caliperY = offset.y
                                    viewModel.triggerHapticFeedback(HapticType.SNAP)
                                }
                            },
                            onDrag = { change, _ ->
                                if (!isCaliperLocked) {
                                    change.consume()
                                    val newY = change.position.y.coerceAtLeast(zeroY)
                                    val prevY = caliperY ?: zeroY
                                    val prevMm = ((prevY - zeroY) / mmInPx).roundToInt()
                                    val newMm = ((newY - zeroY) / mmInPx).roundToInt()
                                    
                                    // Mechanical Gear Tick Haptics
                                    if (prevMm != newMm) {
                                        if (newMm % 10 == 0) {
                                            viewModel.triggerHapticFeedback(HapticType.SNAP)
                                        } else if (newMm % 5 == 0) {
                                            viewModel.triggerHapticFeedback(HapticType.CLICK)
                                        }
                                    }
                                    caliperY = newY
                                }
                            }
                        )
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val rulerWidth = 70.dp.toPx()

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
                strokeWidth = 1.dp.toPx()
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
                strokeWidth = 1.dp.toPx()
            )

            // 3. Zero Reference Baseline connecting Left and Right Rulers
            drawLine(
                color = colorPrimary,
                start = Offset(0f, zeroY),
                end = Offset(w, zeroY),
                strokeWidth = 2.5.dp.toPx()
            )

            // Zero Unit Label Headers
            drawContext.canvas.nativeCanvas.drawText("cm", 12.dp.toPx(), zeroY - 10.dp.toPx(), unitPaintLeft)
            drawContext.canvas.nativeCanvas.drawText(rightUnit, w - 12.dp.toPx(), zeroY - 10.dp.toPx(), unitPaintRight)

            // 4. Left Edge Graduations (Centimeter & Millimeter ticks)
            var leftCurY = zeroY
            var leftMm = 0
            while (leftCurY < h) {
                val isCm = (leftMm % 10 == 0)
                val isHalfCm = (leftMm % 5 == 0)

                val tickLength = when {
                    isCm -> 32.dp.toPx()
                    isHalfCm -> 20.dp.toPx()
                    else -> 12.dp.toPx()
                }

                val tickColor = if (isCm) colorPrimary else colorOnSurface.copy(alpha = 0.35f)
                val strokeWidth = if (isCm) 2.dp.toPx() else 1.dp.toPx()

                drawLine(
                    color = tickColor,
                    start = Offset(0f, leftCurY),
                    end = Offset(tickLength, leftCurY),
                    strokeWidth = strokeWidth
                )

                if (isCm && leftMm > 0) {
                    val cmVal = leftMm / 10
                    drawContext.canvas.nativeCanvas.drawText(
                        "$cmVal",
                        tickLength + 8.dp.toPx(),
                        leftCurY - fontVerticalOffset,
                        textPaintLeft
                    )
                }

                leftCurY += mmInPx
                leftMm++
            }

            // 5. Right Edge Graduations (cm or in)
            if (rightUnit == "cm") {
                // Right side Metric
                var rightCurY = zeroY
                var rightMm = 0
                while (rightCurY < h) {
                    val isCm = (rightMm % 10 == 0)
                    val isHalfCm = (rightMm % 5 == 0)

                    val tickLength = when {
                        isCm -> 32.dp.toPx()
                        isHalfCm -> 20.dp.toPx()
                        else -> 12.dp.toPx()
                    }

                    val tickColor = if (isCm) colorPrimary else colorOnSurface.copy(alpha = 0.35f)
                    val strokeWidth = if (isCm) 2.dp.toPx() else 1.dp.toPx()

                    drawLine(
                        color = tickColor,
                        start = Offset(w, rightCurY),
                        end = Offset(w - tickLength, rightCurY),
                        strokeWidth = strokeWidth
                    )

                    if (isCm && rightMm > 0) {
                        val cmVal = rightMm / 10
                        drawContext.canvas.nativeCanvas.drawText(
                            "$cmVal",
                            w - tickLength - 8.dp.toPx(),
                            rightCurY - fontVerticalOffset,
                            textPaintRight
                        )
                    }

                    rightCurY += mmInPx
                    rightMm++
                }
            } else {
                // Right side Imperial (Inches with 1/16" precision)
                val sixteenthInPx = inchInPx / 16f
                var rightCurY = zeroY
                var step = 0
                while (rightCurY < h) {
                    val isWhole = (step % 16 == 0)
                    val isHalf = (step % 8 == 0)
                    val isQuarter = (step % 4 == 0)
                    val isEighth = (step % 2 == 0)

                    val tickLength = when {
                        isWhole -> 34.dp.toPx()
                        isHalf -> 24.dp.toPx()
                        isQuarter -> 18.dp.toPx()
                        isEighth -> 14.dp.toPx()
                        else -> 10.dp.toPx()
                    }

                    val tickColor = if (isWhole) colorPrimary else colorOnSurface.copy(alpha = 0.35f)
                    val strokeWidth = if (isWhole) 2.dp.toPx() else 1.dp.toPx()

                    drawLine(
                        color = tickColor,
                        start = Offset(w, rightCurY),
                        end = Offset(w - tickLength, rightCurY),
                        strokeWidth = strokeWidth
                    )

                    if (isWhole && step > 0) {
                        val inchVal = step / 16
                        drawContext.canvas.nativeCanvas.drawText(
                            "$inchVal",
                            w - tickLength - 8.dp.toPx(),
                            rightCurY - fontVerticalOffset,
                            textPaintRight
                        )
                    }

                    rightCurY += sixteenthInPx
                    step++
                }
            }

            // 6. Interactive Caliper Guideline & Grips
            caliperY?.let { yPos ->
                // Main Caliper Baseline
                drawLine(
                    color = colorPrimary,
                    start = Offset(0f, yPos),
                    end = Offset(w, yPos),
                    strokeWidth = 2.dp.toPx()
                )

                // High-precision jaw handles
                val gripRadius = 7.dp.toPx()
                drawCircle(color = colorPrimary, radius = gripRadius, center = Offset(rulerWidth, yPos))
                drawCircle(color = colorSurface, radius = gripRadius * 0.45f, center = Offset(rulerWidth, yPos))

                drawCircle(color = colorPrimary, radius = gripRadius, center = Offset(w - rulerWidth, yPos))
                drawCircle(color = colorSurface, radius = gripRadius * 0.45f, center = Offset(w - rulerWidth, yPos))
            }
        }

        // Center Calibration Visual Guide (displayed during calibration)
        if (isCalibrationActive) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 76.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.Straighten,
                        contentDescription = null,
                        tint = colorPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "貼齊實體尺至螢幕邊緣",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorOnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "建議比對 5cm 或 10cm 標示線，微調至完全吻合即可保證 100% 精準度",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Top Controls Bar (Header, Unit switcher, Calibration toggle, History)
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title & Active Mode Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        if (isCalibrationActive) Icons.Rounded.Tune else Icons.Rounded.Straighten,
                        contentDescription = null,
                        tint = colorPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isCalibrationActive) "刻度校準中" else "精密螢幕尺",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorOnSurface
                        )
                        Text(
                            text = if (isCalibrationActive) "即時調節實體比例" else "左右雙邊精準刻度",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Factor Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (abs(calibrationFactor - 1.0f) > 0.001f) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.3fx", calibrationFactor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (abs(calibrationFactor - 1.0f) > 0.001f) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Action Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isCalibrationActive) {
                        // Right Edge Unit Switcher (cm / in)
                        FilterChip(
                            selected = rightUnit == "in",
                            onClick = {
                                rightUnit = if (rightUnit == "cm") "in" else "cm"
                                viewModel.triggerHapticFeedback(HapticType.CLICK)
                            },
                            label = {
                                Text(
                                    text = "右: $rightUnit",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp)
                        )

                        // Calibration Button
                        FilledTonalButton(
                            onClick = {
                                viewModel.setRulerCalibrationActive(true)
                                viewModel.triggerHapticFeedback(HapticType.CLICK)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "校準", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // History Button
                        IconButton(
                            onClick = onShowHistoryClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = "歷史紀錄",
                                tint = colorOnSurface
                            )
                        }
                    } else {
                        // Calibration Mode: Finish Button
                        Button(
                            onClick = {
                                viewModel.persistRulerCalibration()
                                viewModel.setRulerCalibrationActive(false)
                                viewModel.triggerHapticFeedback(HapticType.DOUBLE)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorPrimary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("完成", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // =========================================================================
        // BOTTOM FLOATING PANELS
        // =========================================================================

        // 1. STREAMLINED ULTRA-SMOOTH CALIBRATION DOCK (Active during Calibration)
        AnimatedVisibility(
            visible = isCalibrationActive,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + 8.dp, start = 12.dp, end = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Line 1: Fine-Tuning Steppers & Continuous Smooth Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tactile Decrement Button [-]
                        FilledTonalIconButton(
                            onClick = {
                                val next = ((calibrationFactor - 0.002f) * 1000f).roundToInt() / 1000f
                                viewModel.updateRulerCalibration(next.coerceIn(0.80f, 1.25f), persistImmediately = true)
                                viewModel.triggerHapticFeedback(HapticType.CLICK)
                            },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "微調減少", modifier = Modifier.size(22.dp))
                        }

                        // Center: Live Factor Readout & Smooth Slider
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.3fx", calibrationFactor),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val pctDiff = (calibrationFactor - 1.0f) * 100f
                                Text(
                                    text = if (abs(pctDiff) < 0.05f) "(預設)" else String.format(Locale.US, "(%+0.1f%%)", pctDiff),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Slider(
                                value = calibrationFactor,
                                onValueChange = { newVal ->
                                    val rounded = ((newVal * 1000f).roundToInt() / 1000f).coerceIn(0.80f, 1.25f)
                                    viewModel.updateRulerCalibration(rounded, persistImmediately = false)
                                },
                                onValueChangeFinished = {
                                    viewModel.persistRulerCalibration()
                                },
                                valueRange = 0.82f..1.18f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                        }

                        // Tactile Increment Button [+]
                        FilledTonalIconButton(
                            onClick = {
                                val next = ((calibrationFactor + 0.002f) * 1000f).roundToInt() / 1000f
                                viewModel.updateRulerCalibration(next.coerceIn(0.80f, 1.25f), persistImmediately = true)
                                viewModel.triggerHapticFeedback(HapticType.CLICK)
                            },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "微調增加", modifier = Modifier.size(22.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Line 2: Fast Action Buttons (Reset to 1.000x & Finish Calibration)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.updateRulerCalibration(1.0f, persistImmediately = true)
                                viewModel.triggerHapticFeedback(HapticType.CLICK)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢復預設 (1.000x)", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.persistRulerCalibration()
                                viewModel.setRulerCalibrationActive(false)
                                viewModel.triggerHapticFeedback(HapticType.DOUBLE)
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("完成校準", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. CALIPER REAL-TIME READOUT & VERNIER CONTROLS (Measurement Mode only)
        AnimatedVisibility(
            visible = !isCalibrationActive && caliperY != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + 10.dp, start = 14.dp, end = 14.dp)
        ) {
            caliperY?.let { yPos ->
                val measuredPx = (yPos - zeroY).coerceAtLeast(0f)
                val measuredMm = measuredPx / mmInPx
                val measuredCm = measuredMm / 10.0
                val measuredIn = measuredPx / inchInPx

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // Top Row: Measurement Values & Lock/Clear Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "量測讀數",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    if (isCaliperLocked) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = "已鎖定",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = String.format(Locale.US, "%.2f", measuredCm),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "cm",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorPrimary,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "(${String.format(Locale.US, "%.1f", measuredMm)} mm · ${String.format(Locale.US, "%.2f", measuredIn)} in)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }

                            // Primary Action Buttons (Lock, Clear, Save)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Lock Toggle Button
                                IconButton(
                                    onClick = {
                                        isCaliperLocked = !isCaliperLocked
                                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        if (isCaliperLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                        contentDescription = if (isCaliperLocked) "解除鎖定" else "鎖定刻度",
                                        tint = if (isCaliperLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Clear Marker Button
                                IconButton(
                                    onClick = {
                                        caliperY = null
                                        isCaliperLocked = false
                                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清除游標")
                                }

                                // Save Record Button
                                Button(
                                    onClick = {
                                        viewModel.saveRulerRecord(measuredCm)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("儲存", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Bottom Row: Vernier Fine-Tuning Controls (±0.1 mm steppers)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "卡鉗微調:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // -0.1 mm stepper
                                OutlinedButton(
                                    onClick = {
                                        if (!isCaliperLocked) {
                                            val stepPx = mmInPx * 0.1f
                                            caliperY = ((caliperY ?: zeroY) - stepPx).coerceAtLeast(zeroY)
                                            viewModel.triggerHapticFeedback(HapticType.CLICK)
                                        }
                                    },
                                    enabled = !isCaliperLocked,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("-0.1 mm", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }

                                // +0.1 mm stepper
                                OutlinedButton(
                                    onClick = {
                                        if (!isCaliperLocked) {
                                            val stepPx = mmInPx * 0.1f
                                            caliperY = (caliperY ?: zeroY) + stepPx
                                            viewModel.triggerHapticFeedback(HapticType.CLICK)
                                        }
                                    },
                                    enabled = !isCaliperLocked,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("+0.1 mm", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
