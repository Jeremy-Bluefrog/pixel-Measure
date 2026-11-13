package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.ar.ArCoreEngineStatus
import com.example.ui.theme.ExpressiveCookie8Shape
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.viewmodel.MeasureViewModel

/**
 * Pure visual dynamic guidance pill HUD enhanced with Material 3 Expressive motion & morphing indicator:
 * - Uses ExpressiveContainedLoadingIndicator (morphing geometric shape) during ARCore plane scanning
 * - Uses ExpressiveCookie8Shape badge when magnetic vertex snap activates
 * - Uses ExpressiveMotion.spatialDefault() physics-based expansion and pop transitions
 * - Uses tabular numerals (`tnum`) for live distance values
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
    modifier: Modifier = Modifier,
    arCoreStatus: ArCoreEngineStatus = ArCoreEngineStatus.ACTIVE,
    arCoreStatusMessage: String = ""
) {
    val liveDistanceMeters by viewModel.liveDistanceMeters.collectAsStateWithLifecycle()
    val selectedUnit by viewModel.selectedUnit.collectAsStateWithLifecycle()

    val isScanningPlanes = arCoreStatus == ArCoreEngineStatus.ACTIVE &&
        !isSnapped &&
        !isWaitingForSecondPoint &&
        !isMeasurementAvailable

    val infiniteTransition = rememberInfiniteTransition(label = "GuidancePillPulse")
    val pulseScale by if (isSnapped) {
        infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }

    val (icon: ImageVector, title: String, subtitle: String, accentColor: Color) = remember(
        planesCount,
        hasCapturedPoints,
        isWaitingForSecondPoint,
        capturedPointsCount,
        isMeasurementAvailable,
        isSnapped,
        subMode,
        autoDetectedType,
        liveDistanceMeters,
        arCoreStatus,
        arCoreStatusMessage
    ) {
        when {
            arCoreStatus == ArCoreEngineStatus.NEEDS_INSTALLATION -> {
                Quad(
                    Icons.Rounded.DownloadForOffline,
                    "需先安裝 Google ARCore",
                    "com.google.ar.core",
                    Color(0xFFF59E0B)
                )
            }
            arCoreStatus == ArCoreEngineStatus.UNSUPPORTED || arCoreStatus == ArCoreEngineStatus.ERROR -> {
                Quad(
                    Icons.Rounded.WarningAmber,
                    arCoreStatusMessage.ifBlank { "ARCore 尚未就緒" },
                    "",
                    Color(0xFFEF4444)
                )
            }
            isSnapped -> {
                Quad(
                    Icons.Rounded.AllInclusive,
                    "已磁吸端點",
                    "已對齊現有端點，點擊「＋」完美閉合",
                    Color(0xFF38BDF8)
                )
            }
            isWaitingForSecondPoint -> {
                val distStr = liveDistanceMeters?.let { viewModel.formatLength(it, selectedUnit) } ?: "量測中..."
                when (subMode) {
                    2 -> Quad(
                        Icons.Rounded.Height,
                        "向頂部延伸垂直高度",
                        "目前高程：$distStr · 對齊後點擊「＋」",
                        Color(0xFFF59E0B)
                    )
                    1 -> Quad(
                        Icons.Rounded.SquareFoot,
                        "設定多邊形第 ${capturedPointsCount + 1} 個頂點",
                        "當前線段：$distStr · 點擊「＋」新增",
                        Color(0xFF10B981)
                    )
                    else -> Quad(
                        Icons.Rounded.Straighten,
                        "向終點延伸線段",
                        "即時長度：$distStr · 點擊「＋」確定",
                        Color(0xFF06B6D4)
                    )
                }
            }
            isMeasurementAvailable -> {
                val nextSegment = (capturedPointsCount / 2) + 1
                val role = if (capturedPointsCount == 0) "起點" else "線段 $nextSegment 起點"
                Quad(
                    Icons.Rounded.Adjust,
                    role,
                    "瞄準目標表面，點擊「＋」按鈕定點",
                    Color(0xFF3B82F6)
                )
            }
            else -> {
                Quad(
                    Icons.Rounded.Radar,
                    "正在辨識空間平面",
                    "請握持手機平緩平移掃描周圍地面或桌面",
                    Color(0xFFA855F7)
                )
            }
        }
    }

    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = ExpressiveMotion.effectsDefault(),
        label = "guidanceAccentColor"
    )
    val pillScale by animateFloatAsState(
        targetValue = if (isSnapped) 1.04f else 1.0f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "guidancePillPopScale"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xBD0F172A),
        border = BorderStroke(1.dp, animatedAccent.copy(alpha = 0.42f)),
        shadowElevation = 0.dp,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                scaleX = pillScale
                scaleY = pillScale
            }
            .animateContentSize(animationSpec = ExpressiveMotion.spatialDefault())
            .testTag("measurement_guidance_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isScanningPlanes) {
                ExpressiveContainedLoadingIndicator(
                    containerSize = 26.dp,
                    indicatorSize = 15.dp,
                    containerColor = animatedAccent.copy(alpha = 0.22f),
                    indicatorColor = animatedAccent
                )
            } else {
                val badgeShape = if (isSnapped) ExpressiveCookie8Shape else CircleShape
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(badgeShape)
                        .background(animatedAccent.copy(alpha = 0.22f))
                        .graphicsLayer {
                            if (isSnapped) {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                        }
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = animatedAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                        style = ExpressiveTypography.labelNumeral,
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
