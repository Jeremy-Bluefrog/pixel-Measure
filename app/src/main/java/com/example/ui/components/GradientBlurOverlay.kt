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
 * Top Gradient Blur container that provides a frosted glass gradient backdrop
 * fading downward from opaque/translucent to fully transparent.
 */
@Composable
fun GradientBlurTopBar(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. Frosted Blur Background Layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.verticalGradient(
                        0.0f to baseColor.copy(alpha = 0.92f),
                        0.35f to baseColor.copy(alpha = 0.75f),
                        0.70f to baseColor.copy(alpha = 0.35f),
                        1.0f to Color.Transparent
                    )
                )
        )
        // 2. High-contrast translucent gradient scrim for guaranteed text/icon legibility
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to baseColor.copy(alpha = 0.88f),
                        0.30f to baseColor.copy(alpha = 0.68f),
                        0.65f to baseColor.copy(alpha = 0.28f),
                        1.0f to Color.Transparent
                    )
                )
        )
        // 3. Crisp Foreground Content
        content()
    }
}

/**
 * Bottom Gradient Blur container that provides a frosted glass gradient backdrop
 * fading upward from fully transparent to opaque/translucent at the bottom edge.
 */
@Composable
fun GradientBlurBottomBar(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 20.dp,
    contentWindowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. Frosted Blur Background Layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.25f to baseColor.copy(alpha = 0.35f),
                        0.60f to baseColor.copy(alpha = 0.78f),
                        1.0f to baseColor.copy(alpha = 0.95f)
                    )
                )
        )
        // 2. Translucent gradient scrim layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.20f to baseColor.copy(alpha = 0.25f),
                        0.55f to baseColor.copy(alpha = 0.65f),
                        0.85f to baseColor.copy(alpha = 0.88f),
                        1.0f to baseColor.copy(alpha = 0.95f)
                    )
                )
        )
        // 3. Foreground Content with WindowInsets padding
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
    blurRadius: Dp = 24.dp
) {
    val gradient = if (isTop) {
        Brush.verticalGradient(
            0.0f to baseColor.copy(alpha = 0.80f),
            0.35f to baseColor.copy(alpha = 0.55f),
            0.70f to baseColor.copy(alpha = 0.20f),
            1.0f to Color.Transparent
        )
    } else {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.30f to baseColor.copy(alpha = 0.20f),
            0.65f to baseColor.copy(alpha = 0.55f),
            1.0f to baseColor.copy(alpha = 0.80f)
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
                .background(gradient)
        )
    }
}
