package com.example.ui.components

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP
}

/**
 * Enables Android 12+ (API 31+ / Android 17+) native hardware Window Blur on the host Window.
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
 * Modifier extension applying View-level hardware blur on Android 12+ (API 31+).
 */
fun Modifier.progressiveBlur(
    direction: BlurDirection = BlurDirection.BOTTOM_TO_TOP,
    maxBlurRadius: Dp = 28.dp,
    tintColor: Color = Color.Transparent
): Modifier = this.then(
    Modifier.graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && maxBlurRadius > 0.dp) {
            try {
                renderEffect = RenderEffect.createBlurEffect(
                    maxBlurRadius.toPx(),
                    maxBlurRadius.toPx(),
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            } catch (e: Throwable) {
                // Graceful fallback
            }
        }
    }
)

/**
 * Top container providing a clean translucent frosted glass surface without muddy dark bands.
 */
@Composable
fun GradientBlurTopBar(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
    blurRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to baseColor,
                    0.80f to baseColor.copy(alpha = baseColor.alpha * 0.85f),
                    1.0f to Color.Transparent
                )
            )
    ) {
        content()
    }
}

/**
 * Bottom container providing a clean translucent frosted glass surface without muddy dark bands.
 */
@Composable
fun GradientBlurBottomBar(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
    blurRadius: Dp = 24.dp,
    contentWindowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.20f to baseColor.copy(alpha = baseColor.alpha * 0.85f),
                    1.0f to baseColor
                )
            )
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
 * Pure optical specular scrim: keeps the camera view 100% crisp and crystal clear
 * while framing the UI with an ultra-subtle light rim, completely removing muddy black gradients.
 */
@Composable
fun GradientBlurScrim(
    modifier: Modifier = Modifier,
    isTop: Boolean,
    baseColor: Color = Color.Transparent,
    blurRadius: Dp = 0.dp
) {
    Box(
        modifier = modifier
    ) {
        // Specular ambient light edge (100% free of dark/black haze)
        val borderHighlight = if (isTop) {
            Brush.verticalGradient(
                0.85f to Color.Transparent,
                1.0f to Color.White.copy(alpha = 0.08f)
            )
        } else {
            Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.08f),
                0.15f to Color.Transparent
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(borderHighlight)
        )
    }
}

