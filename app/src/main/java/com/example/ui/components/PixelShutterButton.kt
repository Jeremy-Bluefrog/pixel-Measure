package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pixel Camera Style Shutter Button supporting:
 * 1. Tap to capture high-precision measurement photo snapshot.
 * 2. Long-press to initiate AR measurement video recording.
 * 3. Animated recording state morphing (pulsating red ring + rounded square stop icon).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PixelShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isRecording: Boolean = false,
    size: Dp = 58.dp,
    innerPadding: Dp = 6.dp,
    outerRingColor: Color = Color.Black.copy(alpha = 0.35f),
    outerBorderColor: Color = Color.White.copy(alpha = 0.9f),
    innerCircleColor: Color = Color.White,
    recordingColor: Color = Color(0xFFE53935),
    testTag: String = "pixel_shutter_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Base press scale
    val animatedPressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "PixelShutterPressScale"
    )

    // Pulsing animation during recording
    val infiniteTransition = rememberInfiniteTransition(label = "RecordingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRecording) 1.14f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = if (isRecording) 1.0f else 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    // Animated inner shape & color
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isRecording) recordingColor.copy(alpha = pulseAlpha) else outerBorderColor,
        animationSpec = tween(300),
        label = "BorderColor"
    )
    val animatedInnerColor by animateColorAsState(
        targetValue = if (isRecording) recordingColor else innerCircleColor,
        animationSpec = tween(300),
        label = "InnerColor"
    )
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 28.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "CornerRadius"
    )
    val animatedInnerSizeFactor by animateFloatAsState(
        targetValue = if (isRecording) 0.52f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "InnerSize"
    )

    val currentTotalScale = animatedPressScale * (if (isRecording) pulseScale else 1.0f)
    val innerBaseSize = size - (innerPadding * 2)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(currentTotalScale)
            .background(outerRingColor, CircleShape)
            .border(
                width = if (isRecording) 3.dp else 2.5.dp,
                color = animatedBorderColor,
                shape = CircleShape
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag(testTag)
    ) {
        // Inner morphing element (Circle in Photo mode, Rounded Square in Recording mode)
        Box(
            modifier = Modifier
                .size(innerBaseSize * animatedInnerSizeFactor)
                .clip(RoundedCornerShape(animatedCornerRadius))
                .background(animatedInnerColor)
        )
    }
}
