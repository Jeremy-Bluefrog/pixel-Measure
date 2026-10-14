package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MeasureViewModel

/**
 * Pure visual dynamic guidance pill HUD.
 * Completely silent (no voice/audio) as requested.
 * Provides real-time context cues across measurement steps:
 * 1. Searching plane / motion guidance
 * 2. Pin first point
 * 3. Extend measuring line & distance feedback
 * 4. Magnetic vertex snapping & closing
 */
@Composable
fun MeasurementGuidancePill(
    viewModel: MeasureViewModel,
    subMode: Int,
    autoDetectedType: String,
    planesCount: Int,
    hasCapturedPoints: Boolean,
    isWaitingForSecondPoint: Boolean,
    capturedPointsCount: Int,
    isMeasurementAvailable: Boolean,
    isSnapped: Boolean,
    onOpenTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveDistanceMeters by viewModel.liveDistanceMeters.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()

    // Pulse animation for active scanning radar or magnetic aura
    val infiniteTransition = rememberInfiniteTransition(label = "GuidancePillPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRotation"
    )

    // Compute current guidance step state
    val (icon: ImageVector, title: String, subtitle: String, accentColor: Color) = remember(
        planesCount,
        hasCapturedPoints,
        isWaitingForSecondPoint,
        capturedPointsCount,
        isMeasurementAvailable,
        isSnapped,
        subMode,
        autoDetectedType,
        liveDistanceMeters
    ) {
        when {
            // Snapped state takes priority for vertex magnetic pull
            isSnapped -> {
                Quad(
                    Icons.Rounded.AllInclusive,
                    "已磁吸端點",
                    "已對齊現有端點，點擊「＋」完美閉合",
                    Color(0xFF38BDF8) // Electric Sky Blue
                )
            }
            // Actively drawing line segment (odd number of points: 1, 3, 5...)
            isWaitingForSecondPoint -> {
                val distStr = liveDistanceMeters?.let { viewModel.formatLength(it, selectedUnit) } ?: "量測中..."
                when (subMode) {
                    2 -> Quad(
                        Icons.Rounded.Height,
                        "向頂部延伸垂直高度",
                        "目前高程：$distStr · 對齊後點擊「＋」",
                        Color(0xFFF59E0B) // Amber
                    )
                    1 -> Quad(
                        Icons.Rounded.SquareFoot,
                        "設定多邊形第 ${capturedPointsCount + 1} 個頂點",
                        "當前線段：$distStr · 點擊「＋」新增",
                        Color(0xFF10B981) // Emerald Green
                    )
                    else -> Quad(
                        Icons.Rounded.Straighten,
                        "向終點延伸線段",
                        "即時長度：$distStr · 點擊「＋」確定",
                        Color(0xFF06B6D4) // Cyan
                    )
                }
            }
            // Ready to pin start point (surface is detected and stable)
            isMeasurementAvailable -> {
                val nextSegment = (capturedPointsCount / 2) + 1
                val role = if (capturedPointsCount == 0) "起點" else "線段 $nextSegment 起點"
                Quad(
                    Icons.Rounded.Adjust,
                    "對準 $role",
                    "瞄準目標表面，點擊「＋」按鈕定點",
                    Color(0xFF3B82F6) // Bright Indigo Blue
                )
            }
            // Searching for surface planes in space
            else -> {
                Quad(
                    Icons.Rounded.Radar,
                    "正在辨識空間平面",
                    "請握持手機平緩平移掃描周圍地面或桌面",
                    Color(0xFFA855F7) // Purple
                )
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC0F172A), // Dark Glassmorphism 80% opacity
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .testTag("measurement_guidance_pill")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Animated Icon Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.20f))
                    .then(
                        if (icon == Icons.Rounded.Radar) Modifier.rotate(radarRotation)
                        else if (isSnapped) Modifier.scale(pulseScale)
                        else Modifier
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Step Instructional Texts with smooth AnimatedContent
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith
                                fadeOut(animationSpec = tween(150))
                    },
                    label = "GuidanceTitle"
                ) { targetTitle ->
                    Text(
                        text = targetTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(1.dp))

                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith
                                fadeOut(animationSpec = tween(150))
                    },
                    label = "GuidanceSubtitle"
                ) { targetSubtitle ->
                    Text(
                        text = targetSubtitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1
                    )
                }
            }

            // Quick Tutorial Access Chip Button
            Surface(
                onClick = onOpenTutorial,
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.testTag("guidance_tutorial_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Lightbulb,
                        contentDescription = "教學",
                        tint = Color(0xFFFBBF24), // Warm Amber
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "教學",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)
