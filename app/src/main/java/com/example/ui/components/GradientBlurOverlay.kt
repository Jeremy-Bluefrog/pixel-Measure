package com.example.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.lang.annotations.Language

/**
 * Direction of progressive blur falloff.
 */
enum class BlurDirection {
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP
}

/**
 * AGSL Progressive Variable Blur Shader for Android 13+ (API 33+).
 * Smoothly varies the blur radius along the Y axis without hard edges or dark bands.
 */
@Language("AGSL")
private const val AGSL_PROGRESSIVE_BLUR = """
    uniform shader content;
    uniform float2 resolution;
    uniform float maxRadius;
    uniform float direction; // 0.0: Bottom to Top, 1.0: Top to Bottom
    uniform float4 tintColor;

    half4 main(float2 coord) {
        float normY = coord.y / max(resolution.y, 1.0);
        float factor = direction > 0.5 ? (1.0 - normY) : normY;
        factor = clamp(factor, 0.0, 1.0);
        
        // Cubic smoothstep for optical progressive falloff
        float curve = factor * factor * (3.0 - 2.0 * factor);
        float radius = maxRadius * curve;
        
        if (radius < 0.8) {
            return content.eval(coord);
        }
        
        half4 sum = half4(0.0);
        float totalWeight = 0.0;
        
        // 9-tap variable progressive Gaussian sampling
        const int TAPS = 7;
        for (int i = -TAPS; i <= TAPS; i++) {
            float offset = (float(i) / float(TAPS)) * radius;
            float dist = float(i) / (float(TAPS) * 0.48);
            float weight = exp(-0.5 * dist * dist);
            
            float2 samplePos = coord + float2(0.0, offset);
            samplePos.y = clamp(samplePos.y, 0.0, resolution.y);
            
            sum += content.eval(samplePos) * weight;
            totalWeight += weight;
        }
        
        half4 blurred = sum / max(totalWeight, 0.001);
        
        // Blend frosted glass tint smoothly proportional to blur depth
        float tintWeight = curve * tintColor.a;
        return mix(blurred, half4(tintColor.rgb, 1.0), tintWeight);
    }
"""

/**
 * Modifier extension applying true Progressive Blur (Variable Blur) to any Composable.
 */
fun Modifier.progressiveBlur(
    direction: BlurDirection = BlurDirection.BOTTOM_TO_TOP,
    maxBlurRadius: Dp = 28.dp,
    tintColor: Color = Color(0x2A1E293B)
): Modifier = this.then(
    Modifier.graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val shader = RuntimeShader(AGSL_PROGRESSIVE_BLUR)
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("maxRadius", maxBlurRadius.toPx())
                shader.setFloatUniform("direction", if (direction == BlurDirection.TOP_TO_BOTTOM) 1.0f else 0.0f)
                shader.setFloatUniform(
                    "tintColor",
                    tintColor.red,
                    tintColor.green,
                    tintColor.blue,
                    tintColor.alpha
                )
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            } catch (e: Throwable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = RenderEffect.createBlurEffect(
                        maxBlurRadius.toPx() * 0.7f,
                        maxBlurRadius.toPx() * 0.7f,
                        Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderEffect = RenderEffect.createBlurEffect(
                maxBlurRadius.toPx() * 0.7f,
                maxBlurRadius.toPx() * 0.7f,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    }
)

/**
 * Top Gradient Blur container providing a true frosted glass progressive blur effect.
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
        // Native Progressive Variable Blur Scrim
        GradientBlurScrim(
            modifier = Modifier.matchParentSize(),
            isTop = true,
            baseColor = baseColor,
            blurRadius = blurRadius
        )
        // Crisp Foreground Content
        content()
    }
}

/**
 * Bottom Gradient Blur container providing a true frosted glass progressive blur effect.
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
        // Native Progressive Variable Blur Scrim
        GradientBlurScrim(
            modifier = Modifier.matchParentSize(),
            isTop = false,
            baseColor = baseColor,
            blurRadius = blurRadius
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
 * Modern Progressive Blur Scrim replacing muddy black gradients with genuine frosted glass variable blur.
 */
@Composable
fun GradientBlurScrim(
    modifier: Modifier = Modifier,
    isTop: Boolean,
    baseColor: Color = Color.White.copy(alpha = 0.08f),
    blurRadius: Dp = 28.dp
) {
    val direction = if (isTop) BlurDirection.TOP_TO_BOTTOM else BlurDirection.BOTTOM_TO_TOP

    Box(
        modifier = modifier
            .progressiveBlur(
                direction = direction,
                maxBlurRadius = blurRadius,
                tintColor = baseColor
            )
    ) {
        // Specular ambient frosted glass border highlight (0.0 to 1px feather)
        val borderHighlight = if (isTop) {
            Brush.verticalGradient(
                0.90f to Color.Transparent,
                1.0f to Color.White.copy(alpha = 0.12f)
            )
        } else {
            Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.12f),
                0.10f to Color.Transparent
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(borderHighlight)
        )
    }
}
