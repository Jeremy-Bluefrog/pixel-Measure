package com.example.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt

/**
 * Direction of progressive optical blur falloff.
 */
enum class BlurDirection(internal val shaderId: Float) {
    TOP_TO_BOTTOM(0f),    // Blur increases from top (sharp) to bottom (blurred)
    BOTTOM_TO_TOP(1f),    // Blur increases from bottom (sharp) to top (blurred)
    LEFT_TO_RIGHT(2f),    // Blur increases from left (sharp) to right (blurred)
    RIGHT_TO_LEFT(3f),    // Blur increases from right (sharp) to left (blurred)
    RADIAL_EDGE(4f)       // Optical center stays sharp; blur radiates smoothly toward outer edges
}

/**
 * AGSL 24-Tap Golden Spiral Poisson-Disc Progressive Blur Shader (Android 13+ / API 33+, Vulkan/Skia).
 *
 * Combines:
 * - Normalized progression mapping across [startFraction, endFraction]
 * - Quintic smootherstep falloff curve (6t^5 - 15t^4 + 10t^3) for zero visible transitions
 * - Per-pixel Interleaved Gradient Noise (IGN) phase rotation to eliminate concentric rings & banding
 * - 24-tap Golden Spiral Poisson-Disc Gaussian-weighted sampling for cinema-grade bokeh
 */
private const val PROGRESSIVE_BLUR_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float maxRadius;
    uniform float direction; // 0: T->B, 1: B->T, 2: L->R, 3: R->L, 4: RADIAL_EDGE
    uniform float startFraction;
    uniform float endFraction;

    float ign(float2 p) {
        vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
        return fract(magic.z * fract(dot(p, magic.xy)));
    }

    float computeRawProgress(float2 fragCoord) {
        if (direction < 0.5) {
            return fragCoord.y / max(size.y, 1.0);
        } else if (direction < 1.5) {
            return 1.0 - (fragCoord.y / max(size.y, 1.0));
        } else if (direction < 2.5) {
            return fragCoord.x / max(size.x, 1.0);
        } else if (direction < 3.5) {
            return 1.0 - (fragCoord.x / max(size.x, 1.0));
        } else {
            float2 uv = (fragCoord / max(size, float2(1.0))) * 2.0 - 1.0;
            return clamp(length(uv) * 0.78, 0.0, 1.0);
        }
    }

    half4 main(float2 fragCoord) {
        float raw = computeRawProgress(fragCoord);
        float span = max(endFraction - startFraction, 0.001);
        float t = clamp((raw - startFraction) / span, 0.0, 1.0);

        // Quintic C2 smootherstep for optically invisible blur ramp onset
        float factor = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        float effectiveRadius = maxRadius * factor;

        if (effectiveRadius <= 0.35) {
            return content.eval(fragCoord);
        }

        // Per-pixel IGN rotation eliminates directional banding and spiral moire
        float noise = ign(fragCoord);
        float phaseOffset = noise * 6.2831853;

        half4 sum = content.eval(fragCoord) * 0.16;
        float totalWeight = 0.16;

        const float GOLDEN_ANGLE = 2.39996323;
        const float INV_24 = 1.0 / 24.0;

        for (int i = 1; i <= 24; i++) {
            float fi = float(i);
            float radialNorm = sqrt((fi - 0.5 + (noise - 0.5) * 0.35) * INV_24);
            float r = clamp(radialNorm, 0.05, 1.0) * effectiveRadius;
            float theta = fi * GOLDEN_ANGLE + phaseOffset;
            float2 offset = float2(cos(theta), sin(theta)) * r;
            float2 tapCoord = clamp(fragCoord + offset, float2(0.0), max(size - float2(1.0), float2(1.0)));

            float sigmaNorm = radialNorm * radialNorm;
            float weight = exp(-1.35 * sigmaNorm);
            sum += content.eval(tapCoord) * weight;
            totalWeight += weight;
        }

        half4 result = sum / totalWeight;
        // Subtle sub-LSB dither prevents 8-bit OLED banding on soft gradients
        float dither = (noise - 0.5) * (1.0 / 255.0);
        result.rgb = clamp(result.rgb + half3(dither * result.a), half3(0.0), half3(result.a));
        return result;
    }
"""

/**
 * AGSL Quintic Feather & Anti-Banding Dither Mask Shader.
 * Used as Tier-2 in our hybrid progressive blur pipeline to seamlessly blend
 * deep hardware-native Gaussian RenderEffect blurs into the mid-to-edge zone.
 */
private const val PROGRESSIVE_FEATHER_DITHER_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float direction;
    uniform float startFraction;
    uniform float endFraction;

    float ign(float2 p) {
        vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
        return fract(magic.z * fract(dot(p, magic.xy)));
    }

    float computeRawProgress(float2 fragCoord) {
        if (direction < 0.5) {
            return fragCoord.y / max(size.y, 1.0);
        } else if (direction < 1.5) {
            return 1.0 - (fragCoord.y / max(size.y, 1.0));
        } else if (direction < 2.5) {
            return fragCoord.x / max(size.x, 1.0);
        } else if (direction < 3.5) {
            return 1.0 - (fragCoord.x / max(size.x, 1.0));
        } else {
            float2 uv = (fragCoord / max(size, float2(1.0))) * 2.0 - 1.0;
            return clamp(length(uv) * 0.78, 0.0, 1.0);
        }
    }

    half4 main(float2 fragCoord) {
        float raw = computeRawProgress(fragCoord);
        float span = max(endFraction - startFraction, 0.001);
        float t = clamp((raw - startFraction) / span, 0.0, 1.0);
        float mask = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);

        half4 c = content.eval(fragCoord);
        float dither = (ign(fragCoord) - 0.5) * (1.2 / 255.0);
        half alphaMask = clamp(mask + dither, 0.0, 1.0);
        return c * alphaMask;
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildProgressiveBlurEffect(
    shader: RuntimeShader,
    widthPx: Float,
    heightPx: Float,
    maxRadiusPx: Float,
    direction: BlurDirection,
    startFraction: Float,
    endFraction: Float
): androidx.compose.ui.graphics.RenderEffect? = try {
    shader.setFloatUniform("size", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
    shader.setFloatUniform("maxRadius", maxRadiusPx.coerceAtLeast(0f))
    shader.setFloatUniform("direction", direction.shaderId)
    shader.setFloatUniform("startFraction", startFraction.coerceIn(0f, 0.99f))
    shader.setFloatUniform("endFraction", endFraction.coerceIn(startFraction + 0.01f, 1f))
    RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
} catch (_: Throwable) {
    null
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildDeepGaussianChainEffect(
    featherShader: RuntimeShader,
    widthPx: Float,
    heightPx: Float,
    blurRadiusPx: Float,
    direction: BlurDirection,
    startFraction: Float,
    endFraction: Float
): androidx.compose.ui.graphics.RenderEffect? = try {
    val safeRadius = blurRadiusPx.coerceAtLeast(1f)
    featherShader.setFloatUniform("size", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
    featherShader.setFloatUniform("direction", direction.shaderId)
    featherShader.setFloatUniform("startFraction", startFraction.coerceIn(0f, 0.99f))
    featherShader.setFloatUniform("endFraction", endFraction.coerceIn(startFraction + 0.01f, 1f))

    val gaussianEffect = RenderEffect.createBlurEffect(
        safeRadius,
        safeRadius,
        Shader.TileMode.CLAMP
    )
    val featherEffect = RenderEffect.createRuntimeShaderEffect(featherShader, "content")
    RenderEffect.createChainEffect(featherEffect, gaussianEffect).asComposeRenderEffect()
} catch (_: Throwable) {
    null
}

/**
 * Shared state capturing underlying Compose + Hardware TextureView content into an offscreen
 * GraphicsLayer so floating overlays can sample and apply true progressive backdrop blur
 * without blurring their own foreground text, icons, or controls.
 */
@Stable
class GradientBlurBackdropState internal constructor(
    internal val graphicsLayer: GraphicsLayer
) {
    internal var sourcePositionInRoot by mutableStateOf(Offset.Zero)
    internal var sourceSize by mutableStateOf(IntSize.Zero)
    internal var isRecording by mutableStateOf(false)
}

@Composable
fun rememberGradientBlurBackdropState(): GradientBlurBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GradientBlurBackdropState(layer) }
}

/**
 * Marks a container as the live backdrop source for downstream [progressiveBackdropBlur] overlays.
 */
fun Modifier.gradientBlurSource(state: GradientBlurBackdropState): Modifier = this
    .onGloballyPositioned { coords ->
        state.sourcePositionInRoot = coords.positionInRoot()
        state.sourceSize = coords.size
    }
    .drawWithContent {
        if (size.width >= 1f && size.height >= 1f && !state.isRecording) {
            state.isRecording = true
            try {
                state.graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
            } finally {
                state.isRecording = false
            }
            drawLayer(state.graphicsLayer)
        } else {
            drawContent()
        }
    }

/**
 * Enables native hardware Window Blur on Android 12+ (API 31+) Dialogs & ModalBottomSheets
 * so the live AR camera and measurement overlay blur softly behind modal surfaces.
 */
@Composable
fun EnableWindowBlur(blurRadiusDp: Int = 44) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val density = LocalDensity.current
    val isInspection = LocalInspectionMode.current
    if (isInspection) return

    val blurRadiusPx = remember(blurRadiusDp, density) {
        with(density) { blurRadiusDp.dp.roundToPx().coerceIn(8, 120) }
    }

    SideEffect {
        val window = findDialogWindow(view) ?: return@SideEffect
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = blurRadiusPx
                dimAmount = 0.22f
            }
            window.setBackgroundBlurRadius((blurRadiusPx * 0.75f).roundToInt())
        } catch (_: Throwable) {
        }
    }
}

private tailrec fun findDialogWindow(view: View?): android.view.Window? {
    if (view == null) return null
    val parent = view.parent
    if (parent is DialogWindowProvider) {
        return parent.window
    }
    return findDialogWindow(parent as? View)
}

/**
 * Applies full-surface hardware accelerated RenderEffect Gaussian blur (Android 12+ / API 31+)
 * when modal sheets, dialogs, or welcome overlays cover an active viewport.
 */
fun Modifier.hardwareBackdropBlur(
    enabled: Boolean,
    blurRadius: Float = 32f
): Modifier = composed {
    if (!enabled || blurRadius <= 0.5f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed this
    }

    val safeRadius = blurRadius.coerceIn(0.5f, 120f)
    this.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        renderEffect = RenderEffect
            .createBlurEffect(safeRadius, safeRadius, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}

/**
 * Content-level Progressive Blur Modifier (Android 13+ / API 33+):
 * Uses AGSL 24-tap Golden Spiral Poisson-Disc + IGN RuntimeShader to smoothly vary blur
 * radius across the composable from [startFraction] (0px sharp) to [endFraction] ([maxBlurRadius]).
 */
fun Modifier.progressiveBlur(
    direction: BlurDirection = BlurDirection.BOTTOM_TO_TOP,
    maxBlurRadius: Dp = 24.dp,
    startFraction: Float = 0.0f,
    endFraction: Float = 1.0f
): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || maxBlurRadius <= 0.dp) {
        return@composed this
    }

    val density = LocalDensity.current
    val maxRadiusPx = with(density) { maxBlurRadius.toPx() }
    if (maxRadiusPx <= 0.5f) return@composed this

    val shader = remember {
        try {
            RuntimeShader(PROGRESSIVE_BLUR_AGSL)
        } catch (_: Throwable) {
            null
        }
    } ?: return@composed this

    var lastKey by remember { mutableStateOf<List<Float>?>(null) }
    var cachedEffect by remember { mutableStateOf<androidx.compose.ui.graphics.RenderEffect?>(null) }

    this.graphicsLayer {
        val w = size.width
        val h = size.height
        if (w >= 1f && h >= 1f) {
            val key = listOf(w, h, maxRadiusPx, direction.shaderId, startFraction, endFraction)
            if (lastKey != key) {
                lastKey = key
                cachedEffect = buildProgressiveBlurEffect(
                    shader = shader,
                    widthPx = w,
                    heightPx = h,
                    maxRadiusPx = maxRadiusPx,
                    direction = direction,
                    startFraction = startFraction,
                    endFraction = endFraction
                )
            }
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = cachedEffect
        }
    }
}

/**
 * True Optical Backdrop Progressive Blur Modifier (2-Tier Hybrid Pipeline):
 * Samples live content captured in [backdropState] behind this overlay and renders:
 * - Tier 1: AGSL 24-tap Golden Spiral + IGN true variable-radius progressive blur (0px -> ~18dp)
 * - Tier 2: Hardware-native Gaussian blur seamlessly feathered across the deep half for
 *           zero-stipple, zero-banding glass frosting even at high radii (24dp–32dp+)
 *
 * Foreground children of this composable are drawn AFTER the blurred backdrop and remain 100% sharp.
 */
fun Modifier.progressiveBackdropBlur(
    backdropState: GradientBlurBackdropState?,
    direction: BlurDirection = BlurDirection.BOTTOM_TO_TOP,
    maxBlurRadius: Dp = 24.dp,
    startFraction: Float = 0.0f,
    endFraction: Float = 1.0f,
    tintBrush: Brush? = null,
    shape: Shape? = null
): Modifier = composed {
    val clippedBase = if (shape != null) this.clip(shape) else this

    if (backdropState == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || maxBlurRadius <= 0.dp) {
        return@composed if (tintBrush != null) clippedBase.background(tintBrush) else clippedBase
    }

    val density = LocalDensity.current
    val maxRadiusPx = with(density) { maxBlurRadius.toPx() }
    val tier1MaxPx = with(density) { 18.dp.toPx() }.coerceAtMost(maxRadiusPx)
    val needsDeepGaussianTier = maxRadiusPx > tier1MaxPx + 1f

    val tier1Layer = rememberGraphicsLayer()
    val tier2Layer = rememberGraphicsLayer()

    val progressiveShader = remember {
        try {
            RuntimeShader(PROGRESSIVE_BLUR_AGSL)
        } catch (_: Throwable) {
            null
        }
    }
    val featherShader = remember {
        try {
            RuntimeShader(PROGRESSIVE_FEATHER_DITHER_AGSL)
        } catch (_: Throwable) {
            null
        }
    }

    var targetPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var lastSizeKey by remember { mutableStateOf<List<Float>?>(null) }
    var tier1Effect by remember { mutableStateOf<androidx.compose.ui.graphics.RenderEffect?>(null) }
    var tier2Effect by remember { mutableStateOf<androidx.compose.ui.graphics.RenderEffect?>(null) }

    clippedBase
        .onGloballyPositioned { coords ->
            targetPositionInRoot = coords.positionInRoot()
        }
        .drawBehind {
            val sourceSize = backdropState.sourceSize
            val w = size.width
            val h = size.height
            if (!backdropState.isRecording && sourceSize.width > 0 && sourceSize.height > 0 && w >= 1f && h >= 1f) {
                val key = listOf(w, h, maxRadiusPx, direction.shaderId, startFraction, endFraction)
                if (lastSizeKey != key) {
                    lastSizeKey = key
                    tier1Effect = progressiveShader?.let {
                        buildProgressiveBlurEffect(
                            shader = it,
                            widthPx = w,
                            heightPx = h,
                            maxRadiusPx = tier1MaxPx,
                            direction = direction,
                            startFraction = startFraction,
                            endFraction = endFraction
                        )
                    }
                    tier2Effect = if (needsDeepGaussianTier && featherShader != null) {
                        val deepStart = (startFraction + (endFraction - startFraction) * 0.36f).coerceIn(0f, 0.96f)
                        buildDeepGaussianChainEffect(
                            featherShader = featherShader,
                            widthPx = w,
                            heightPx = h,
                            blurRadiusPx = maxRadiusPx,
                            direction = direction,
                            startFraction = deepStart,
                            endFraction = endFraction
                        )
                    } else null
                }

                val offset = backdropState.sourcePositionInRoot - targetPositionInRoot
                val recordSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))

                tier1Layer.clip = true
                tier1Layer.renderEffect = tier1Effect
                tier1Layer.record(size = recordSize) {
                    translate(left = offset.x, top = offset.y) {
                        drawLayer(backdropState.graphicsLayer)
                    }
                }
                drawLayer(tier1Layer)

                if (tier2Effect != null) {
                    tier2Layer.clip = true
                    tier2Layer.renderEffect = tier2Effect
                    tier2Layer.record(size = recordSize) {
                        translate(left = offset.x, top = offset.y) {
                            drawLayer(backdropState.graphicsLayer)
                        }
                    }
                    drawLayer(tier2Layer)
                }
            }

            if (tintBrush != null) {
                drawRect(brush = tintBrush)
            }
        }
}

/**
 * Modifier applying progressive frosted glass surface treatment:
 * Combines true hardware progressive backdrop blur with a translucent tinted backdrop and specular edge border.
 */
fun Modifier.progressiveGlass(
    backdropState: GradientBlurBackdropState? = null,
    direction: BlurDirection = BlurDirection.BOTTOM_TO_TOP,
    maxBlurRadius: Dp = 24.dp,
    tintColor: Color = Color(0xCC0F172A),
    shape: Shape = RoundedCornerShape(20.dp),
    borderColor: Color = Color.White.copy(alpha = 0.16f)
): Modifier = this
    .progressiveBackdropBlur(
        backdropState = backdropState,
        direction = direction,
        maxBlurRadius = maxBlurRadius,
        tintBrush = Brush.verticalGradient(
            colors = listOf(tintColor, tintColor.copy(alpha = tintColor.alpha * 0.85f))
        ),
        shape = shape
    )
    .border(0.8.dp, borderColor, shape)

/**
 * Top container providing an ultra-smooth progressive blur frosted glass surface.
 */
@Composable
fun GradientBlurTopBar(
    modifier: Modifier = Modifier,
    backdropState: GradientBlurBackdropState? = null,
    baseColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
    blurRadius: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val tintBrush = remember(baseColor) {
        Brush.verticalGradient(
            0.00f to baseColor,
            0.42f to baseColor.copy(alpha = baseColor.alpha * 0.90f),
            0.68f to baseColor.copy(alpha = baseColor.alpha * 0.62f),
            0.84f to baseColor.copy(alpha = baseColor.alpha * 0.28f),
            0.94f to baseColor.copy(alpha = baseColor.alpha * 0.06f),
            1.00f to Color.Transparent
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .progressiveBackdropBlur(
                backdropState = backdropState,
                direction = BlurDirection.BOTTOM_TO_TOP,
                maxBlurRadius = blurRadius,
                startFraction = 0.0f,
                endFraction = 0.95f,
                tintBrush = tintBrush
            )
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
    backdropState: GradientBlurBackdropState? = null,
    baseColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
    blurRadius: Dp = 26.dp,
    contentWindowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable BoxScope.() -> Unit
) {
    val tintBrush = remember(baseColor) {
        Brush.verticalGradient(
            0.00f to Color.Transparent,
            0.06f to baseColor.copy(alpha = baseColor.alpha * 0.06f),
            0.16f to baseColor.copy(alpha = baseColor.alpha * 0.28f),
            0.32f to baseColor.copy(alpha = baseColor.alpha * 0.62f),
            0.58f to baseColor.copy(alpha = baseColor.alpha * 0.90f),
            1.00f to baseColor
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .progressiveBackdropBlur(
                backdropState = backdropState,
                direction = BlurDirection.TOP_TO_BOTTOM,
                maxBlurRadius = blurRadius,
                startFraction = 0.05f,
                endFraction = 1.0f,
                tintBrush = tintBrush
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
 * Dynamic Optical Frosted Glass Scrim:
 * Pairs real AGSL + hardware Gaussian progressive backdrop blur with an 8-stop
 * cubic-Hermite translucent theme tint and subtle specular boundary sheen.
 */
@Composable
fun GradientBlurScrim(
    modifier: Modifier = Modifier,
    isTop: Boolean,
    backdropState: GradientBlurBackdropState? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    baseColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 26.dp
) {
    val progressiveBrush = remember(isTop, baseColor, isDark, backdropState != null) {
        // When live backdrop blur is active, use lighter tint so blurred scene luminance shows through cleanly
        val maxAlpha = if (backdropState != null) {
            if (isDark) 0.56f else 0.52f
        } else {
            if (isDark) 0.82f else 0.74f
        }

        if (isTop) {
            Brush.verticalGradient(
                0.00f to baseColor.copy(alpha = maxAlpha),
                0.15f to baseColor.copy(alpha = maxAlpha * 0.90f),
                0.35f to baseColor.copy(alpha = maxAlpha * 0.66f),
                0.55f to baseColor.copy(alpha = maxAlpha * 0.40f),
                0.72f to baseColor.copy(alpha = maxAlpha * 0.20f),
                0.86f to baseColor.copy(alpha = maxAlpha * 0.07f),
                0.95f to baseColor.copy(alpha = maxAlpha * 0.02f),
                1.00f to Color.Transparent
            )
        } else {
            Brush.verticalGradient(
                0.00f to Color.Transparent,
                0.05f to baseColor.copy(alpha = maxAlpha * 0.02f),
                0.14f to baseColor.copy(alpha = maxAlpha * 0.07f),
                0.28f to baseColor.copy(alpha = maxAlpha * 0.20f),
                0.45f to baseColor.copy(alpha = maxAlpha * 0.40f),
                0.65f to baseColor.copy(alpha = maxAlpha * 0.66f),
                0.85f to baseColor.copy(alpha = maxAlpha * 0.90f),
                1.00f to baseColor.copy(alpha = maxAlpha)
            )
        }
    }

    val rimHighlight = remember(isTop, isDark) {
        val edgeColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.03f)
        if (isTop) {
            Brush.verticalGradient(
                0.90f to Color.Transparent,
                1.00f to edgeColor
            )
        } else {
            Brush.verticalGradient(
                0.00f to edgeColor,
                0.10f to Color.Transparent
            )
        }
    }

    val blurDir = if (isTop) BlurDirection.BOTTOM_TO_TOP else BlurDirection.TOP_TO_BOTTOM

    Box(
        modifier = modifier
            .progressiveBackdropBlur(
                backdropState = backdropState,
                direction = blurDir,
                maxBlurRadius = blurRadius,
                startFraction = 0.02f,
                endFraction = 0.98f,
                tintBrush = progressiveBrush
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(rimHighlight)
        )
    }
}
