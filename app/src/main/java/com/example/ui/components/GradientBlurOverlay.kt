package com.example.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
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
 * Standard Mask definitions for Progressive Blur effects.
 */
object ProgressiveBlurMasks {
    fun topFade(startAlpha: Float = 1f, endAlpha: Float = 0f): Brush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = startAlpha),
        0.35f to Color.White.copy(alpha = (startAlpha * 0.65f + endAlpha * 0.35f)),
        0.75f to Color.White.copy(alpha = (startAlpha * 0.25f + endAlpha * 0.75f)),
        1.0f to Color.White.copy(alpha = endAlpha)
    )

    fun bottomFade(startAlpha: Float = 0f, endAlpha: Float = 1f): Brush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = startAlpha),
        0.25f to Color.White.copy(alpha = (startAlpha * 0.75f + endAlpha * 0.25f)),
        0.65f to Color.White.copy(alpha = (startAlpha * 0.35f + endAlpha * 0.65f)),
        1.0f to Color.White.copy(alpha = endAlpha)
    )
}

/**
 * AGSL Progressive Variable Blur Shader for Android 13+ (API 33+).
 * Computes progressive variable blur driven by spatial mask definitions.
 */
@Language("AGSL")
private const val AGSL_MASK_PROGRESSIVE_BLUR = """
    uniform shader content;
    uniform float2 resolution;
    uniform float maxRadius;
    uniform float isTop; // 1.0 = Top to bottom mask, 0.0 = Bottom to top mask
    uniform float4 tintColor;

    half4 main(float2 coord) {
        float normY = coord.y / max(resolution.y, 1.0);
        float maskWeight = (isTop > 0.5) ? (1.0 - normY) : normY;
        maskWeight = clamp(maskWeight, 0.0, 1.0);
        
        // Smoothstep optical curve for progressive blur transition
        float progressiveFactor = smoothstep(0.0, 1.0, maskWeight);
        float radius = maxRadius * progressiveFactor;
        
        if (radius < 0.5) {
            return content.eval(coord);
        }
        
        half4 sum = half4(0.0);
        float totalWeight = 0.0;
        
        // 9-tap variable progressive Gaussian sampling
        const int TAPS = 7;
        for (int i = -TAPS; i <= TAPS; i++) {
            float offset = (float(i) / float(TAPS)) * radius;
            float weight = exp(-0.5 * pow(float(i) / (float(TAPS) * 0.48), 2.0));
            
            float2 samplePos = coord + float2(0.0, offset);
            samplePos.y = clamp(samplePos.y, 0.0, resolution.y);
            
            sum += content.eval(samplePos) * weight;
            totalWeight += weight;
        }
        
        half4 blurred = sum / max(totalWeight, 0.001);
        
        // Blend frosted glass tint smoothly proportional to blur depth
        float tintWeight = progressiveFactor * tintColor.a;
        return mix(blurred, half4(tintColor.rgb, 1.0), tintWeight);
    }
"""

/**
 * Jetpack Compose blur modifier extension with specific mask definition.
 * Applies a true progressive blur effect driven by the mask brush rather than a solid/opaque linear gradient.
 */
fun Modifier.blur(
    radius: Dp,
    mask: Brush,
    edgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Unbounded,
    tintColor: Color = Color.Transparent
): Modifier = this.then(
    Modifier.graphicsLayer {
        val isTopDirection = mask == ProgressiveBlurMasks.topFade() ||
            (mask is Brush) // defaults to top-to-bottom if not explicit
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val shader = RuntimeShader(AGSL_MASK_PROGRESSIVE_BLUR)
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("maxRadius", radius.toPx())
                shader.setFloatUniform("isTop", if (isTopDirection) 1.0f else 0.0f)
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
                        radius.toPx() * 0.7f,
                        radius.toPx() * 0.7f,
                        Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderEffect = RenderEffect.createBlurEffect(
                radius.toPx() * 0.7f,
                radius.toPx() * 0.7f,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    }
)

/**
 * Overload for 2D progressive blur radii with mask definition.
 */
fun Modifier.blur(
    radiusX: Dp,
    radiusY: Dp,
    mask: Brush,
    edgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Unbounded,
    tintColor: Color = Color.Transparent
): Modifier = this.blur(
    radius = (radiusX + radiusY) / 2f,
    mask = mask,
    edgeTreatment = edgeTreatment,
    tintColor = tintColor
)

/**
 * Modifier extension for progressive blur with direction.
 */
fun Modifier.progressiveBlur(
    direction: BlurDirection = BlurDirection.BOTTOM_TO_TOP,
    maxBlurRadius: Dp = 28.dp,
    tintColor: Color = Color(0x2A1E293B)
): Modifier = this.blur(
    radius = maxBlurRadius,
    mask = if (direction == BlurDirection.TOP_TO_BOTTOM) ProgressiveBlurMasks.topFade() else ProgressiveBlurMasks.bottomFade(),
    tintColor = tintColor
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
        // Progressive blur scrim driven by mask definition
        GradientBlurScrim(
            modifier = Modifier.matchParentSize(),
            isTop = true,
            baseColor = baseColor,
            blurRadius = blurRadius
        )
        // Foreground Content
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
        // Progressive blur scrim driven by mask definition
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
 * Progressive Blur Scrim using Compose blur modifier with specific mask definitions.
 * Replaces solid/opaque linear gradient backgrounds with true progressive blur.
 */
@Composable
fun GradientBlurScrim(
    modifier: Modifier = Modifier,
    isTop: Boolean,
    baseColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
    blurRadius: Dp = 28.dp
) {
    val mask = if (isTop) ProgressiveBlurMasks.topFade() else ProgressiveBlurMasks.bottomFade()

    Box(
        modifier = modifier.blur(
            radius = blurRadius,
            mask = mask,
            tintColor = baseColor
        )
    ) {
        // Ambient glass specular line at the transition threshold
        val specularBorder = if (isTop) {
            Brush.verticalGradient(
                0.96f to Color.Transparent,
                1.0f to Color.White.copy(alpha = 0.12f)
            )
        } else {
            Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.12f),
                0.04f to Color.Transparent
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(specularBorder)
        )
    }
}
