package com.example.ui.components

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Direction of progressive blur falloff.
 */
enum class BlurDirection {
    TOP_TO_BOTTOM,    // Blur increases from top (sharp) to bottom (blurred)
    BOTTOM_TO_TOP,    // Blur increases from bottom (sharp) to top (blurred)
    LEFT_TO_RIGHT,    // Blur increases from left (sharp) to right (blurred)
    RIGHT_TO_LEFT     // Blur increases from right (sharp) to left (blurred)
}

/**
 * AGSL Progressive Blur Shader (Android 13+ / API 33+)
 *
 * Employs a multi-tap 2D Gaussian kernel with progressive radius modulation
 * governed by a non-linear cubic optical falloff curve.
 */
private const val PROGRESSIVE_BLUR_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float maxRadius;
    uniform float direction; // 0: TOP_TO_BOTTOM, 1: BOTTOM_TO_TOP, 2: LEFT_TO_RIGHT, 3: RIGHT_TO_LEFT

    half4 main(float2 fragCoord) {
        float progress = 0.0;
        if (direction < 0.5) {
            progress = clamp(fragCoord.y / size.y, 0.0, 1.0);
        } else if (direction < 1.5) {
            progress = clamp(1.0 - (fragCoord.y / size.y), 0.0, 1.0);
        } else if (direction < 2.5) {
            progress = clamp(fragCoord.x / size.x, 0.0, 1.0);
        } else {
            progress = clamp(1.0 - (fragCoord.x / size.x), 0.0, 1.0);
        }

        // Cubic optical smoothstep easing for smooth organic blur transition
        float factor = progress * progress * (3.0 - 2.0 * progress);
        float effectiveRadius = maxRadius * factor;

        if (effectiveRadius <= 0.5) {
            return content.eval(fragCoord);
        }

        half4 sum = content.eval(fragCoord) * 0.227027;
        float total = 0.227027;

        // 4-ring distributed kernel with variable progressive radius
        float d1 = effectiveRadius * 0.35;
        half4 tap1 = content.eval(fragCoord + float2(0.0, d1)) + content.eval(fragCoord - float2(0.0, d1)) +
                     content.eval(fragCoord + float2(d1, 0.0)) + content.eval(fragCoord - float2(d1, 0.0));
        sum += tap1 * 0.1945946;
        total += 0.1945946 * 4.0;

        float d2 = effectiveRadius * 0.70;
        half4 tap2 = content.eval(fragCoord + float2(0.0, d2)) + content.eval(fragCoord - float2(0.0, d2)) +
                     content.eval(fragCoord + float2(d2, 0.0)) + content.eval(fragCoord - float2(d2, 0.0));
        sum += tap2 * 0.1216216;
        total += 0.1216216 * 4.0;

        float d3 = effectiveRadius * 1.05;
        half4 tap3 = content.eval(fragCoord + float2(0.0, d3)) + content.eval(fragCoord - float2(0.0, d3)) +
                     content.eval(fragCoord + float2(d3, 0.0)) + content.eval(fragCoord - float2(d3, 0.0));
        sum += tap3 * 0.054054;
        total += 0.054054 * 4.0;

        float d4 = effectiveRadius * 1.40;
        half4 tap4 = content.eval(fragCoord + float2(0.0, d4)) + content.eval(fragCoord - float2(0.0, d4)) +
                     content.eval(fragCoord + float2(d4, 0.0)) + content.eval(fragCoord - float2(d4, 0.0));
        sum += tap4 * 0.016216;
        total += 0.016216 * 4.0;

        return sum / total;
    }
"""

/**
 * Enables Android 12+ (API 31+) native hardware Window Blur on the host Window.
 * When applied to a Dialog, AlertDialog, or ModalBottomSheet, the surface behind the window
 * (the camera, 3D measurements, live viewport) is blurred in real-time by SurfaceFlinger.
 */
@Composable
fun EnableWindowBlur(blurRadiusDp: Int = 40) {
    val view = LocalView.current
    val density = LocalDensity.current
    val radiusPx = with(density) { blurRadiusDp.dp.roundToPx() }

    DisposableEffect(view) {
        var parent = view.parent
        var targetWindow: Window? = null
        while (parent != null) {
            if (parent is DialogWindowProvider) {
                targetWindow = parent.window
                break
            }
            parent = parent.parent
        }
        if (targetWindow == null) {
            targetWindow = (view.context as? Activity)?.window
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetWindow != null) {
            try {
                targetWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                targetWindow.attributes = targetWindow.attributes.apply {
                    blurBehindRadius = radiusPx
                }
            } catch (e: Throwable) {
                android.util.Log.w("WindowBlur", "Hardware Window Blur failed: ${e.message}")
            }
        }
        onDispose { }
    }
}

/**
 * Applies View-level hardware accelerated RenderEffect blur on Android 12+ (API 31+).
 * Blurs the actual Composable container (e.g. the AR camera viewport) when sheets or dialogs are opened.
 */
fun Modifier.hardwareBackdropBlur(
    enabled: Boolean,
    blurRadius: Float = 32f
): Modifier = this.then(
    Modifier.graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderEffect = if (enabled && blurRadius > 0.5f) {
                RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            } else null
        }
    }
)

/**
 * Progressive Blur Modifier:
 * Uses AGSL RuntimeShader on Android 13+ (API 33+) to dynamically vary blur radius
 * across the specified direction (e.g. 0dp sharp to maxBlurRadius blurred).
 * Gracefully falls back to hardware RenderEffect blur on Android 12 (API 31+).
 */
fun Modifier.progressiveBlur(
    direction: BlurDirection = BlurDirection.BOTTOM_TO_TOP,
    maxBlurRadius: Dp = 24.dp
): Modifier = composed {
    val density = LocalDensity.current
    val maxRadiusPx = with(density) { maxBlurRadius.toPx() }

    val runtimeShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(PROGRESSIVE_BLUR_AGSL)
            } catch (e: Throwable) {
                null
            }
        } else null
    }

    this.graphicsLayer {
        if (maxRadiusPx <= 0.5f) return@graphicsLayer

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null && size.width > 0f && size.height > 0f) {
            try {
                val dirCode = when (direction) {
                    BlurDirection.TOP_TO_BOTTOM -> 0f
                    BlurDirection.BOTTOM_TO_TOP -> 1f
                    BlurDirection.LEFT_TO_RIGHT -> 2f
                    BlurDirection.RIGHT_TO_LEFT -> 3f
                }
                runtimeShader.setFloatUniform("size", size.width, size.height)
                runtimeShader.setFloatUniform("maxRadius", maxRadiusPx)
                runtimeShader.setFloatUniform("direction", dirCode)
                renderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "content").asComposeRenderEffect()
                return@graphicsLayer
            } catch (e: Throwable) {
                // Fall through to standard RenderEffect
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                renderEffect = RenderEffect.createBlurEffect(
                    maxRadiusPx,
                    maxRadiusPx,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            } catch (e: Throwable) {
                // Graceful fallback
            }
        }
    }
}

/**
 * Modifier applying progressive frosted glass surface treatment:
 * Combines hardware progressive blur with a translucent tinted backdrop and specular edge border.
 */
fun Modifier.progressiveGlass(
    direction: BlurDirection = BlurDirection.BOTTOM_TO_TOP,
    maxBlurRadius: Dp = 24.dp,
    tintColor: Color = Color(0xDD0F172A),
    shape: Shape = RoundedCornerShape(20.dp),
    borderColor: Color = Color.White.copy(alpha = 0.15f)
): Modifier = this
    .progressiveBlur(direction = direction, maxBlurRadius = maxBlurRadius)
    .background(tintColor, shape)
    .border(0.8.dp, borderColor, shape)

/**
 * Top container providing an ultra-smooth progressive blur frosted glass surface.
 */
@Composable
fun GradientBlurTopBar(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    blurRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val stops = remember(baseColor) {
        listOf(
            0.00f to baseColor,
            0.60f to baseColor.copy(alpha = baseColor.alpha * 0.88f),
            0.85f to baseColor.copy(alpha = baseColor.alpha * 0.40f),
            1.00f to Color.Transparent
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .progressiveBlur(direction = BlurDirection.BOTTOM_TO_TOP, maxBlurRadius = blurRadius)
            .background(Brush.verticalGradient(colorStops = stops.toTypedArray()))
    ) {
        content()
    }
}

/**
 * Bottom container providing an ultra-smooth progressive blur frosted glass surface.
 */
@Composable
fun GradientBlurBottomBar(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    blurRadius: Dp = 24.dp,
    contentWindowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable BoxScope.() -> Unit
) {
    val stops = remember(baseColor) {
        listOf(
            0.00f to Color.Transparent,
            0.15f to baseColor.copy(alpha = baseColor.alpha * 0.40f),
            0.40f to baseColor.copy(alpha = baseColor.alpha * 0.88f),
            1.00f to baseColor
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .progressiveBlur(direction = BlurDirection.TOP_TO_BOTTOM, maxBlurRadius = blurRadius)
            .background(Brush.verticalGradient(colorStops = stops.toTypedArray()))
    ) {
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
 * Elegant Frosted Glass Camera Scrim:
 * Provides a modern translucent HUD backdrop (deep slate glass gradient)
 * that ensures 100% legibility for the status bar, buttons, and badges over any camera scene.
 * Uses progressive blur (AGSL/RenderEffect) coupled with an optical 7-stop cubic easing
 * gradient and a specular light boundary edge to achieve authentic progressive blur glassmorphism.
 */
@Composable
fun GradientBlurScrim(
    modifier: Modifier = Modifier,
    isTop: Boolean,
    baseColor: Color = Color(0xFF0F172A),
    blurRadius: Dp = 24.dp
) {
    // 7-stop non-linear progressive easing gradient (cubic optical curve)
    val progressiveStops = remember(isTop, baseColor) {
        if (isTop) {
            listOf(
                0.00f to baseColor.copy(alpha = 0.90f),
                0.20f to baseColor.copy(alpha = 0.82f),
                0.40f to baseColor.copy(alpha = 0.62f),
                0.60f to baseColor.copy(alpha = 0.40f),
                0.78f to baseColor.copy(alpha = 0.20f),
                0.90f to baseColor.copy(alpha = 0.08f),
                1.00f to Color.Transparent
            )
        } else {
            listOf(
                0.00f to Color.Transparent,
                0.10f to baseColor.copy(alpha = 0.08f),
                0.22f to baseColor.copy(alpha = 0.20f),
                0.40f to baseColor.copy(alpha = 0.40f),
                0.60f to baseColor.copy(alpha = 0.62f),
                0.80f to baseColor.copy(alpha = 0.82f),
                1.00f to baseColor.copy(alpha = 0.92f)
            )
        }
    }

    val rimHighlight = remember(isTop) {
        if (isTop) {
            Brush.verticalGradient(
                0.88f to Color.Transparent,
                1.00f to Color.White.copy(alpha = 0.14f)
            )
        } else {
            Brush.verticalGradient(
                0.00f to Color.White.copy(alpha = 0.14f),
                0.12f to Color.Transparent
            )
        }
    }

    val blurDir = if (isTop) BlurDirection.BOTTOM_TO_TOP else BlurDirection.TOP_TO_BOTTOM

    Box(
        modifier = modifier
            .progressiveBlur(direction = blurDir, maxBlurRadius = blurRadius)
            .background(Brush.verticalGradient(colorStops = progressiveStops.toTypedArray()))
    ) {
        // Subtle specular light edge separating the blurred HUD from the sharp camera viewport
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(rimHighlight)
        )
    }
}


