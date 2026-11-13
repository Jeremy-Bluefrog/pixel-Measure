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

    // Pulse & radar animations strictly active when required (lazy animation state)
    val infiniteTransition = rememberInfiniteTransition(label = "GuidancePillPulse")
    val pulseScale by if (isSnapped) {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }
    val radarRotation by if (!hasCapturedPoints && planesCount == 0) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "radarRotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

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
        shape = RoundedCornerShape(16.dp),
        color = Color(0xB80F172A), // Dark Glassmorphism 72% opacity
        border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.35f)),
        shadowElevation = 0.dp,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .testTag("measurement_guidance_pill")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Minimal Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.20f))
                    .graphicsLayer {
                        if (icon == Icons.Rounded.Radar) {
                            rotationZ = radarRotation
                        } else if (isSnapped) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Streamlined single-focus message
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (subtitle.contains("：") || subtitle.contains(":")) {
                val valuePart = subtitle.substringAfter("：").substringAfter(":").trim()
                if (valuePart.isNotEmpty()) {
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = valuePart,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0),
                        maxLines = 1
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
