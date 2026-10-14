package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.*

/**
 * Interactive step-by-step tutorial dialog with rich native Canvas animations.
 * Completely silent (no voice/audio) as requested.
 * Visually illustrates:
 * 1. Panning & plane surface detection
 * 2. Pinning point & stretching distance line
 * 3. Magnetic snapping & polygon area closing
 */
@Composable
fun MeasurementTutorialDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(0) } // 0, 1, 2

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF00F172A), // Dark Glassmorphism 94% opacity
            border = BorderStroke(1.2.dp, Color(0xFF334155)),
            shadowElevation = 16.dp,
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
                .testTag("measurement_tutorial_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.40f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "AR 測量步驟教學",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "動態示範 · 純視覺引導",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "關閉",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Interactive Animated Stage Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF030712))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally { width -> width / 2 } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> -width / 2 } + fadeOut()
                            } else {
                                slideInHorizontally { width -> -width / 2 } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> width / 2 } + fadeOut()
                            }
                        },
                        label = "TutorialAnimationStage"
                    ) { step ->
                        when (step) {
                            0 -> TutorialStep1PlaneScanAnimation()
                            1 -> TutorialStep2LineMeasureAnimation()
                            else -> TutorialStep3MagneticSnapAnimation()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Step Description Text
                val stepData = when (currentStep) {
                    0 -> Triple(
                        "步驟 1：環視空間與平面辨識",
                        "手持相機平緩左右平移掃描周圍地面或牆面，直到畫面鎖定青色網格與光點基準面。",
                        Color(0xFFA855F7)
                    )
                    1 -> Triple(
                        "步驟 2：定點起點與延伸測量",
                        "將中心準心對齊目標起點，按下「＋」定點；平緩移動手機延伸線段，每滿整公尺均有細膩震動提示。",
                        Color(0xFF06B6D4)
                    )
                    else -> Triple(
                        "步驟 3：端點磁吸與面積閉合",
                        "靠近既有頂點時自動啟動磁吸對齊並微震提示，按下「＋」即可完美閉合多邊形或計算高程。",
                        Color(0xFF10B981)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = stepData.first,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = stepData.third,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stepData.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0),
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Step Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0..2) {
                        val isSelected = i == currentStep
                        val widthAnim by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(widthAnim)
                                .clip(CircleShape)
                                .background(if (isSelected) stepData.third else Color(0xFF334155))
                                .clickable { currentStep = i }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Bottom Navigation Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text("上一步", color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            if (currentStep < 2) {
                                currentStep++
                            } else {
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = stepData.third,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(if (currentStep > 0) 1.4f else 1f).height(46.dp)
                    ) {
                        Text(
                            text = if (currentStep < 2) "下一步" else "開始測量",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            if (currentStep < 2) Icons.Rounded.ArrowForward else Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Step 1 Animation: Phone panning over 3D perspective plane with scanning waves and detected grid
 */
@Composable
private fun TutorialStep1PlaneScanAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "Step1Anim")
    // Phone oscillating left & right
    val panOffsetNorm by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "panOffset"
    )
    // Radar beam wave expanding
    val radarPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarPhase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Perspective Grid Floor
        val horizonY = h * 0.50f
        val vp = Offset(w * 0.5f, horizonY)

        val gridLines = 7
        for (i in 0..gridLines) {
            val startX = w * (i.toFloat() / gridLines)
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(startX, h),
                end = vp,
                strokeWidth = 1.2f
            )
        }
        for (j in 1..4) {
            val y = horizonY + (h - horizonY) * (j.toFloat() / 4f).pow(1.6f)
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.2f
            )
        }

        // 2. Detected Surface Plane (Glowing Cyan polygon)
        val planeY1 = horizonY + 25f
        val planeY2 = h - 18f
        val planePath = Path().apply {
            moveTo(w * 0.28f, planeY1)
            lineTo(w * 0.72f, planeY1)
            lineTo(w * 0.85f, planeY2)
            lineTo(w * 0.15f, planeY2)
            close()
        }
        drawPath(
            path = planePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x33A855F7), Color(0x6606B6D4)),
                startY = planeY1,
                endY = planeY2
            ),
            style = Fill
        )
        drawPath(
            path = planePath,
            color = Color(0xFF38BDF8),
            style = Stroke(
                width = 1.8f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), radarPhase * 20f)
            )
        )

        // 3. Feature Points Sparkles
        val sparkles = listOf(
            Offset(w * 0.35f, h * 0.65f),
            Offset(w * 0.50f, h * 0.75f),
            Offset(w * 0.68f, h * 0.62f),
            Offset(w * 0.42f, h * 0.82f),
            Offset(w * 0.58f, h * 0.85f)
        )
        sparkles.forEachIndexed { idx, pt ->
            val pPhase = ((radarPhase + idx * 0.2f) % 1f)
            val pAlpha = (1f - pPhase).coerceIn(0f, 1f)
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = pAlpha),
                radius = 3.5f + pPhase * 4f,
                center = pt
            )
            drawCircle(
                color = Color.White.copy(alpha = pAlpha),
                radius = 2f,
                center = pt
            )
        }

        // 4. Oscillating Phone Silhouette
        val phoneCenterX = w * 0.5f + panOffsetNorm * (w * 0.22f)
        val phoneCenterY = h * 0.26f
        val phoneW = 54f
        val phoneH = 80f
        val phoneRect = Rect(
            phoneCenterX - phoneW / 2,
            phoneCenterY - phoneH / 2,
            phoneCenterX + phoneW / 2,
            phoneCenterY + phoneH / 2
        )

        // Scanning projection cone from phone to ground
        val conePath = Path().apply {
            moveTo(phoneCenterX, phoneCenterY + phoneH / 2)
            lineTo(phoneCenterX - 65f - radarPhase * 15f, h * 0.88f)
            lineTo(phoneCenterX + 65f + radarPhase * 15f, h * 0.88f)
            close()
        }
        drawPath(
            path = conePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x5506B6D4), Color(0x0506B6D4)),
                startY = phoneCenterY,
                endY = h * 0.88f
            )
        )

        // Draw phone frame
        drawRoundRect(
            color = Color(0xFF334155),
            topLeft = phoneRect.topLeft,
            size = phoneRect.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            style = Fill
        )
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(phoneRect.left + 3f, phoneRect.top + 3f),
            size = Size(phoneW - 6f, phoneH - 6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            style = Fill
        )
        // Camera lens dot
        drawCircle(
            color = Color(0xFF06B6D4),
            radius = 3f,
            center = Offset(phoneCenterX, phoneCenterY - phoneH / 2 + 10f)
        )
        // Home bar
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(phoneCenterX - 10f, phoneCenterY + phoneH / 2 - 6f),
            end = Offset(phoneCenterX + 10f, phoneCenterY + phoneH / 2 - 6f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Step 2 Animation: Pinning Point A and stretching measuring line to Point B with live distance badge
 */
@Composable
private fun TutorialStep2LineMeasureAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "Step2Anim")
    // Stretch progression from 0 (Point A) to 1 (Point B)
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lineProgress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val pA = Offset(w * 0.22f, h * 0.62f)
        val pBTarget = Offset(w * 0.78f, h * 0.40f)
        val currentEnd = Offset(
            pA.x + (pBTarget.x - pA.x) * progress,
            pA.y + (pBTarget.y - pA.y) * progress
        )

        // 1. Measuring Line (Cyan glowing line with animated dashes)
        drawLine(
            color = Color(0xFF06B6D4),
            start = pA,
            end = currentEnd,
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        // Laser dash accent
        drawLine(
            color = Color.White,
            start = pA,
            end = currentEnd,
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -progress * 40f),
            cap = StrokeCap.Round
        )

        // 2. Point A (Anchor with pulse)
        drawCircle(
            color = Color(0x443B82F6),
            radius = 18f,
            center = pA
        )
        drawCircle(
            color = Color(0xFF3B82F6),
            radius = 8f,
            center = pA
        )
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = pA
        )

        // 3. Current Live Crosshair Reticle at currentEnd
        val reticleRadius = 22f
        drawCircle(
            color = Color(0x3306B6D4),
            radius = reticleRadius,
            center = currentEnd
        )
        drawCircle(
            color = Color(0xFF06B6D4),
            radius = reticleRadius,
            center = currentEnd,
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
        )
        // Crosshair ticks
        val tick = 6f
        drawLine(Color.White, Offset(currentEnd.x - tick, currentEnd.y), Offset(currentEnd.x + tick, currentEnd.y), 2f)
        drawLine(Color.White, Offset(currentEnd.x, currentEnd.y - tick), Offset(currentEnd.x, currentEnd.y + tick), 2f)

        // 4. Point B target indicator ring
        drawCircle(
            color = Color(0x22F59E0B),
            radius = 12f,
            center = pBTarget
        )
        drawCircle(
            color = Color(0xFFF59E0B).copy(alpha = if (progress >= 0.95f) 1f else 0.4f),
            radius = 6f,
            center = pBTarget,
            style = Stroke(width = 2f)
        )

        // 5. Distance Badge Box
        val midX = (pA.x + currentEnd.x) / 2
        val midY = (pA.y + currentEnd.y) / 2 - 24f
        val badgeW = 76f
        val badgeH = 26f
        drawRoundRect(
            color = Color(0xEE0F172A),
            topLeft = Offset(midX - badgeW / 2, midY - badgeH / 2),
            size = Size(badgeW, badgeH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            style = Fill
        )
        drawRoundRect(
            color = Color(0xFF06B6D4),
            topLeft = Offset(midX - badgeW / 2, midY - badgeH / 2),
            size = Size(badgeW, badgeH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            style = Stroke(width = 1.2f)
        )
    }

    // Distance label overlaid
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val distM = (progress * 1.85f)
        val distText = String.format(java.util.Locale.US, "%.2f m", distM)
        Text(
            text = distText,
            color = Color(0xFF38BDF8),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.offset(y = (-6).dp)
        )
    }
}

/**
 * Step 3 Animation: Magnetic vertex snapping and polygon area calculation
 */
@Composable
private fun TutorialStep3MagneticSnapAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "Step3Anim")
    val snapPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snapPhase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val p1 = Offset(w * 0.25f, h * 0.35f)
        val p2 = Offset(w * 0.75f, h * 0.35f)
        val p3 = Offset(w * 0.70f, h * 0.75f)

        // Cursor moving toward p1 (start vertex) to trigger snap
        val isSnapped = snapPhase > 0.65f
        val cursorRaw = if (!isSnapped) {
            val t = (snapPhase / 0.65f)
            Offset(w * 0.32f - t * (w * 0.05f), h * 0.65f - t * (h * 0.25f))
        } else {
            p1 // Snapped into exact vertex position
        }

        // Polygon area fill (illuminates brightly once closed)
        val polyPath = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(cursorRaw.x, cursorRaw.y)
            close()
        }
        val fillAlpha = if (isSnapped) 0.35f else 0.10f
        drawPath(
            path = polyPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF10B981).copy(alpha = fillAlpha), Color(0xFF06B6D4).copy(alpha = fillAlpha * 0.4f)),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = w * 0.4f
            )
        )

        // Draw polygon perimeter edges
        drawLine(Color(0xFF10B981), p1, p2, strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Color(0xFF10B981), p2, p3, strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Color(0xFF10B981), p3, cursorRaw, strokeWidth = 3f, cap = StrokeCap.Round)
        if (isSnapped) {
            drawLine(Color(0xFF10B981), cursorRaw, p1, strokeWidth = 3f, cap = StrokeCap.Round)
        } else {
            drawLine(
                Color(0xFF06B6D4).copy(alpha = 0.6f),
                cursorRaw,
                p1,
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                cap = StrokeCap.Round
            )
        }

        // Existing vertices
        listOf(p2, p3).forEach { pt ->
            drawCircle(Color(0xFF10B981), radius = 6f, center = pt)
            drawCircle(Color.White, radius = 3f, center = pt)
        }

        // Snap Target Aura at p1
        val auraPulse = ((snapPhase * 3) % 1f)
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = (1f - auraPulse) * 0.5f),
            radius = 16f + auraPulse * 18f,
            center = p1,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = 8f,
            center = p1
        )
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = p1
        )

        // Magnetic Attraction Ripple if nearing p1
        if (isSnapped) {
            drawCircle(
                color = Color(0xFFFBBF24),
                radius = 24f,
                center = p1,
                style = Stroke(width = 2.5f)
            )
        }
    }

    // Centered Area Result Badge
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (snapPhase > 0.65f) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xF0064E3B), // Emerald Dark Glass
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "面積 1.45 m²",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
