package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Top Gradient Blur container providing a true frosted glass gradient blur effect
 * (blur radius transitioning smoothly with translucent glass tint).
 */
@Composable
fun GradientBlurTopBar(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Multi-layer Gradient Blur Stack for smooth frosted glass edge fade
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.verticalGradient(
                        0.0f to baseColor.copy(alpha = 0.85f),
                        0.4f to baseColor.copy(alpha = 0.55f),
                        0.8f to baseColor.copy(alpha = 0.15f),
                        1.0f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius * 0.5f, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.verticalGradient(
                        0.0f to baseColor.copy(alpha = 0.70f),
                        0.5f to baseColor.copy(alpha = 0.30f),
                        1.0f to Color.Transparent
                    )
                )
        )
        // Crisp Foreground Content
        content()
    }
}

/**
 * Bottom Gradient Blur container providing a true frosted glass gradient blur effect
 * fading upward from transparent to blurred glass.
 */
@Composable
fun GradientBlurBottomBar(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 24.dp,
    contentWindowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Multi-layer Gradient Blur Stack
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.2f to baseColor.copy(alpha = 0.15f),
                        0.6f to baseColor.copy(alpha = 0.60f),
                        1.0f to baseColor.copy(alpha = 0.90f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius * 0.5f, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to baseColor.copy(alpha = 0.35f),
                        1.0f to baseColor.copy(alpha = 0.75f)
                    )
                )
        )
        // Foreground Content with WindowInsets padding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(contentWindowInsets)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Standalone decorative gradient blur scrim for overlaying on camera or canvas viewports.
 */
@Composable
fun GradientBlurScrim(
    modifier: Modifier = Modifier,
    isTop: Boolean,
    baseColor: Color = Color.Black,
    blurRadius: Dp = 28.dp
) {
    val gradient = if (isTop) {
        Brush.verticalGradient(
            0.0f to baseColor.copy(alpha = 0.75f),
            0.4f to baseColor.copy(alpha = 0.40f),
            0.8f to baseColor.copy(alpha = 0.10f),
            1.0f to Color.Transparent
        )
    } else {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.2f to baseColor.copy(alpha = 0.10f),
            0.6f to baseColor.copy(alpha = 0.40f),
            1.0f to baseColor.copy(alpha = 0.75f)
        )
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(gradient)
            
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius * 0.5f, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(gradient)
        )
    }
}
