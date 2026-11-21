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
import androidx.compose.ui.graphics.drawscope.scale
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
import com.example.logic.vulkan.PixelGpuRenderPolicy
import kotlin.math.roundToInt

/**
 * CompositionLocal providing active Google Pixel GPU render policy to optical blur modifiers.
 */
val LocalPixelGpuRenderPolicy = compositionLocalOf { PixelGpuRenderPolicy.DEFAULT }

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
 * Pixel-Optimized AGSL Golden Spiral Poisson-Disc Progressive Blur Shader (Android 13+ / API 33+).
 *
 * Google Pixel Tensor GPU optimizations (ARM Mali / Immortalis & Imagination PowerVR TBDR):
 * 1. Explicit FP16 (`half` / `half2` / `half4`) arithmetic for weights, noise & accumulation
 *    to double ALU throughput and halve register pressure on Valhall & PowerVR execution cores.
 * 2. Incremental 2D Golden-Angle rotation matrix replaces 32 per-pixel trig (`cos`/`sin`) calls
 *    with a single per-pixel trig setup + lightweight `half2` multiply-adds.
 * 3. Adaptive `tapCount` uniform (8–16 taps) driven by [PixelGpuRenderPolicy] & thermal headroom.
 */
private const val PROGRESSIVE_BLUR_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float maxRadius;
    uniform float direction; // 0: T->B, 1: B->T, 2: L->R, 3: R->L, 4: RADIAL_EDGE
    uniform float startFraction;
    uniform float endFraction;
    uniform float tapCount;

    half ign(float2 p) {
        vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
        return half(fract(magic.z * fract(dot(p, magic.xy))));
    }

    half computeRawProgress(float2 fragCoord) {
        if (direction < 0.5) {
            return half(fragCoord.y / max(size.y, 1.0));
        } else if (direction < 1.5) {
            return half(1.0 - (fragCoord.y / max(size.y, 1.0)));
        } else if (direction < 2.5) {
            return half(fragCoord.x / max(size.x, 1.0));
        } else if (direction < 3.5) {
            return half(1.0 - (fragCoord.x / max(size.x, 1.0)));
        } else {
            half2 uv = half2((fragCoord / max(size, float2(1.0))) * 2.0 - 1.0);
            return clamp(half(length(uv)) * half(0.78), half(0.0), half(1.0));
        }
    }

    half4 main(float2 fragCoord) {
        half raw = computeRawProgress(fragCoord);
        half span = max(half(endFraction - startFraction), half(0.001));
        half t = clamp((raw - half(startFraction)) / span, half(0.0), half(1.0));

        // Quintic C2 smootherstep in FP16 for optically invisible blur ramp onset
        half factor = t * t * t * (t * (t * half(6.0) - half(15.0)) + half(10.0));
        float effectiveRadius = maxRadius * float(factor);

        if (effectiveRadius <= 0.35) {
            return content.eval(fragCoord);
        }

        // Single per-pixel trig init; loop advances direction using incremental Golden-Angle rotation
        half noise = ign(fragCoord);
        float phaseOffset = float(noise) * 6.2831853;
        half2 dir = half2(half(cos(phaseOffset)), half(sin(phaseOffset)));
        const half COS_GA = half(-0.73736888);
        const half SIN_GA = half(0.67549030);

        half4 sum = content.eval(fragCoord) * half(0.16);
        half totalWeight = half(0.16);

        float safeTaps = clamp(tapCount, 6.0, 16.0);
        half invTaps = half(1.0 / safeTaps);
        float2 maxBound = max(size - float2(1.0), float2(1.0));

        for (int i = 1; i <= 16; i++) {
            if (float(i) > safeTaps) break;
            dir = half2(dir.x * COS_GA - dir.y * SIN_GA, dir.x * SIN_GA + dir.y * COS_GA);

            // Zero-SFU FP16 FMA minimax radial curve & quadratic Gaussian falloff (replaces 16 sqrt + 16 exp stalls)
            half u = clamp((half(i) - half(0.5) + (noise - half(0.5)) * half(0.35)) * invTaps, half(0.01), half(1.0));
            half radialNorm = clamp(u * (half(1.38) - half(0.38) * u), half(0.05), half(1.0));
            float r = float(radialNorm) * effectiveRadius;
            float2 tapCoord = clamp(fragCoord + float2(dir) * r, float2(0.0), maxBound);

            half sigmaNorm = radialNorm * radialNorm;
            half gaussPoly = half(1.0) - half(0.54) * sigmaNorm;
            half weight = max(gaussPoly * gaussPoly, half(0.08));
            sum += content.eval(tapCoord) * weight;
            totalWeight += weight;
        }

        half4 result = sum / totalWeight;
        half dither = (noise - half(0.5)) * half(1.0 / 255.0);
        result.rgb = clamp(result.rgb + half3(dither * result.a), half3(0.0), half3(result.a));
        return result;
    }
"""

/**
 * Pixel-Optimized FP16 AGSL Quintic Feather & Anti-Banding Dither Mask Shader.
 */
private const val PROGRESSIVE_FEATHER_DITHER_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float direction;
    uniform float startFraction;
    uniform float endFraction;

    half ign(float2 p) {
        vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
        return half(fract(magic.z * fract(dot(p, magic.xy))));
    }

    half computeRawProgress(float2 fragCoord) {
        if (direction < 0.5) {
            return half(fragCoord.y / max(size.y, 1.0));
        } else if (direction < 1.5) {
            return half(1.0 - (fragCoord.y / max(size.y, 1.0)));
        } else if (direction < 2.5) {
            return half(fragCoord.x / max(size.x, 1.0));
        } else if (direction < 3.5) {
            return half(1.0 - (fragCoord.x / max(size.x, 1.0)));
        } else {
            half2 uv = half2((fragCoord / max(size, float2(1.0))) * 2.0 - 1.0);
            return clamp(half(length(uv)) * half(0.78), half(0.0), half(1.0));
        }
    }

    half4 main(float2 fragCoord) {
        half raw = computeRawProgress(fragCoord);
        half span = max(half(endFraction - startFraction), half(0.001));
        half t = clamp((raw - half(startFraction)) / span, half(0.0), half(1.0));
        half mask = t * t * t * (t * (t * half(6.0) - half(15.0)) + half(10.0));

        half4 c = content.eval(fragCoord);
        half dither = (ign(fragCoord) - half(0.5)) * half(1.2 / 255.0);
        half alphaMask = clamp(mask + dither, half(0.0), half(1.0));
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
    endFraction: Float,
    tapCount: Int = 16
): androidx.compose.ui.graphics.RenderEffect? = try {
    shader.setFloatUniform("size", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
    shader.setFloatUniform("maxRadius", maxRadiusPx.coerceAtLeast(0f))
    shader.setFloatUniform("direction", direction.shaderId)
    shader.setFloatUniform("startFraction", startFraction.coerceIn(0f, 0.99f))
    shader.setFloatUniform("endFraction", endFraction.coerceIn(startFraction + 0.01f, 1f))
    shader.setFloatUniform("tapCount", tapCount.coerceIn(6, 16).toFloat())
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
    @Volatile
    internal var isRecording: Boolean = false
    private val activeConsumerCount = java.util.concurrent.atomic.AtomicInteger(0)

    internal val hasActiveConsumers: Boolean
        get() = activeConsumerCount.get() > 0

    internal fun registerConsumer() {
        activeConsumerCount.incrementAndGet()
    }

    internal fun unregisterConsumer() {
        activeConsumerCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
    }
}

@Composable
fun rememberGradientBlurBackdropState(): GradientBlurBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GradientBlurBackdropState(layer) }
}

/**
 * Marks a container as the live backdrop source for downstream [progressiveBackdropBlur] overlays.
 * Zero-cost passthrough on API < 33 or whenever zero active blur consumers are attached.
 */
fun Modifier.gradientBlurSource(state: GradientBlurBackdropState): Modifier = this
    .onGloballyPositioned { coords ->
        state.sourcePositionInRoot = coords.positionInRoot()
        state.sourceSize = coords.size
    }
    .drawWithContent {
        val shouldRecord = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            state.hasActiveConsumers &&
            size.width >= 1f &&
            size.height >= 1f &&
            !state.isRecording

        if (shouldRecord) {
            state.isRecording = true
            var recorded = false
            try {
                state.graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                recorded = true
            } catch (_: Throwable) {
                recorded = false
            } finally {
                state.isRecording = false
            }
            if (recorded) {
                drawLayer(state.graphicsLayer)
            } else {
                drawContent()
            }
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

private object StaticBlurRenderEffectCache {
    private val cache = android.util.SparseArray<androidx.compose.ui.graphics.RenderEffect>(16)

    @RequiresApi(Build.VERSION_CODES.S)
    @Synchronized
    fun getOrCreate(radiusPx: Float): androidx.compose.ui.graphics.RenderEffect {
        val key = (radiusPx * 2f).roundToInt().coerceIn(1, 240)
        cache.get(key)?.let { return it }
        val r = key / 2f
        val effect = RenderEffect
            .createBlurEffect(r, r, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
        if (cache.size() >= 24) {
            cache.clear()
        }
        cache.put(key, effect)
        return effect
    }
}

/**
 * Applies full-surface hardware accelerated RenderEffect Gaussian blur (Android 12+ / API 31+)
 * when modal sheets, dialogs, or welcome overlays cover an active viewport.
 */
fun Modifier.hardwareBackdropBlur(
    enabled: Boolean,
    blurRadius: Float = 32f
): Modifier {
    if (!enabled || blurRadius <= 0.5f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
    }

    val safeRadius = blurRadius.coerceIn(0.5f, 120f)
    val cachedEffect = StaticBlurRenderEffectCache.getOrCreate(safeRadius)
    return this.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        renderEffect = cachedEffect
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

    val gpuPolicy = LocalPixelGpuRenderPolicy.current
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

    val cache = remember { ProgressiveBlurCache() }

    this.graphicsLayer {
        val w = size.width
        val h = size.height
        if (w >= 1f && h >= 1f) {
            val taps = gpuPolicy.agslBlurTapCount
            if (cache.lastW != w || cache.lastH != h || cache.lastRadius != maxRadiusPx ||
                cache.lastDir != direction.shaderId || cache.lastStart != startFraction ||
                cache.lastEnd != endFraction || cache.lastTaps != taps
            ) {
                cache.lastW = w
                cache.lastH = h
                cache.lastRadius = maxRadiusPx
                cache.lastDir = direction.shaderId
                cache.lastStart = startFraction
                cache.lastEnd = endFraction
                cache.lastTaps = taps
                cache.cachedEffect = buildProgressiveBlurEffect(
                    shader = shader,
                    widthPx = w,
                    heightPx = h,
                    maxRadiusPx = maxRadiusPx,
                    direction = direction,
                    startFraction = startFraction,
                    endFraction = endFraction,
                    tapCount = taps
                )
            }
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = cache.cachedEffect
        }
    }
}

private class ProgressiveBlurCache {
    var lastW = -1f
    var lastH = -1f
    var lastRadius = -1f
    var lastDir = -1f
    var lastStart = -1f
    var lastEnd = -1f
    var lastTaps = -1
    var cachedEffect: androidx.compose.ui.graphics.RenderEffect? = null
}

private class BackdropBlurCache {
    var lastW = -1f
    var lastH = -1f
    var lastRadius = -1f
    var lastDir = -1f
    var lastStart = -1f
    var lastEnd = -1f
    var lastScale = -1f
    var lastTier2Mult = -1f
    var lastTaps = -1
    var lastTier2 = false
    var tier1Effect: androidx.compose.ui.graphics.RenderEffect? = null
    var tier2Effect: androidx.compose.ui.graphics.RenderEffect? = null
}

/**
 * True Optical Backdrop Progressive Blur Modifier (Pixel TBDR-Optimized Chained 2-Tier Pyramid):
 * Samples live content captured in [backdropState] into downsampled offscreen buffers tuned by
 * [LocalPixelGpuRenderPolicy] (cutting Mali/PowerVR DRAM bandwidth by ~70%-88%), and renders:
 * - Tier 1: Zero-SFU FP16 AGSL Golden Spiral + IGN progressive blur (8-16 adaptive taps)
 * - Tier 2: Chained half-resolution Gaussian pyramid sampled directly from Tier 1 (75% less pixel fill,
 *           zero duplicate full-scene backdrop traversal; skipped when thermally throttled)
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

    DisposableEffect(backdropState) {
        backdropState.registerConsumer()
        onDispose {
            backdropState.unregisterConsumer()
        }
    }

    val gpuPolicy = LocalPixelGpuRenderPolicy.current
    val density = LocalDensity.current
    val maxRadiusPx = with(density) { maxBlurRadius.toPx() }
    val tier1MaxPx = with(density) { 18.dp.toPx() }.coerceAtMost(maxRadiusPx)
    val needsDeepGaussianTier = gpuPolicy.enableDeepGaussianTier2 && maxRadiusPx > tier1MaxPx + 1f
    val downsampleScale = gpuPolicy.backdropDownsampleScale.coerceIn(0.45f, 1.0f)
    val tier2Mult = gpuPolicy.tier2DownsampleMultiplier.coerceIn(0.40f, 0.75f)

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
    val cache = remember { BackdropBlurCache() }

    clippedBase
        .onGloballyPositioned { coords ->
            targetPositionInRoot = coords.positionInRoot()
        }
        .drawBehind {
            val sourceSize = backdropState.sourceSize
            val w = size.width
            val h = size.height
            if (!backdropState.isRecording && sourceSize.width > 0 && sourceSize.height > 0 && w >= 1f && h >= 1f) {
                // Respect GPU max safe scratch texture dimension (2048px cap on PowerVR TBDR)
                val maxAllowedDim = gpuPolicy.maxScratchTextureDim.toFloat()
                val rawRecW = (w * downsampleScale).coerceAtLeast(1f)
                val rawRecH = (h * downsampleScale).coerceAtLeast(1f)
                val dimClampScale = if (rawRecW > maxAllowedDim || rawRecH > maxAllowedDim) {
                    minOf(maxAllowedDim / rawRecW, maxAllowedDim / rawRecH)
                } else 1f
                val effScale = downsampleScale * dimClampScale
                val recWInt = (w * effScale).roundToInt().coerceAtLeast(1)
                val recHInt = (h * effScale).roundToInt().coerceAtLeast(1)
                val recW = recWInt.toFloat()
                val recH = recHInt.toFloat()

                val t2WInt = (recW * tier2Mult).roundToInt().coerceAtLeast(1)
                val t2HInt = (recH * tier2Mult).roundToInt().coerceAtLeast(1)
                val t2W = t2WInt.toFloat()
                val t2H = t2HInt.toFloat()
                val t2ScaleX = t2W / recW
                val t2ScaleY = t2H / recH

                val taps = gpuPolicy.agslBlurTapCount

                if (cache.lastW != w || cache.lastH != h || cache.lastRadius != maxRadiusPx ||
                    cache.lastDir != direction.shaderId || cache.lastStart != startFraction ||
                    cache.lastEnd != endFraction || cache.lastScale != effScale ||
                    cache.lastTier2Mult != tier2Mult || cache.lastTaps != taps ||
                    cache.lastTier2 != needsDeepGaussianTier
                ) {
                    cache.lastW = w
                    cache.lastH = h
                    cache.lastRadius = maxRadiusPx
                    cache.lastDir = direction.shaderId
                    cache.lastStart = startFraction
                    cache.lastEnd = endFraction
                    cache.lastScale = effScale
                    cache.lastTier2Mult = tier2Mult
                    cache.lastTaps = taps
                    cache.lastTier2 = needsDeepGaussianTier

                    cache.tier1Effect = progressiveShader?.let {
                        buildProgressiveBlurEffect(
                            shader = it,
                            widthPx = recW,
                            heightPx = recH,
                            maxRadiusPx = tier1MaxPx * effScale,
                            direction = direction,
                            startFraction = startFraction,
                            endFraction = endFraction,
                            tapCount = taps
                        )
                    }
                    cache.tier2Effect = if (needsDeepGaussianTier && featherShader != null) {
                        val deepStart = (startFraction + (endFraction - startFraction) * 0.36f).coerceIn(0f, 0.96f)
                        buildDeepGaussianChainEffect(
                            featherShader = featherShader,
                            widthPx = t2W,
                            heightPx = t2H,
                            blurRadiusPx = (maxRadiusPx * effScale * tier2Mult).coerceAtLeast(1f),
                            direction = direction,
                            startFraction = deepStart,
                            endFraction = endFraction
                        )
                    } else null
                }

                val offset = backdropState.sourcePositionInRoot - targetPositionInRoot
                val recordSize = IntSize(recWInt, recHInt)
                val upscaleX = w / recW
                val upscaleY = h / recH

                try {
                    tier1Layer.clip = true
                    tier1Layer.renderEffect = cache.tier1Effect
                    tier1Layer.record(size = recordSize) {
                        scale(scaleX = effScale, scaleY = effScale, pivot = Offset.Zero) {
                            translate(left = offset.x, top = offset.y) {
                                drawLayer(backdropState.graphicsLayer)
                            }
                        }
                    }
                    scale(scaleX = upscaleX, scaleY = upscaleY, pivot = Offset.Zero) {
                        drawLayer(tier1Layer)
                    }

                    val deepEffect = cache.tier2Effect
                    if (deepEffect != null) {
                        // Chained Half-Res Pyramid: downsample directly from Tier 1 instead of re-drawing full scene
                        tier2Layer.clip = true
                        tier2Layer.renderEffect = deepEffect
                        tier2Layer.record(size = IntSize(t2WInt, t2HInt)) {
                            scale(scaleX = t2ScaleX, scaleY = t2ScaleY, pivot = Offset.Zero) {
                                drawLayer(tier1Layer)
                            }
                        }
                        scale(scaleX = w / t2W, scaleY = h / t2H, pivot = Offset.Zero) {
                            drawLayer(tier2Layer)
                        }
                    }
                } catch (_: Throwable) {
                    // Fallback gracefully to tint brush if offscreen layer recording fails transiently
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
    baseColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
    blurRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val tintBrush = remember(baseColor) {
        Brush.verticalGradient(
            0.00f to baseColor,
            0.30f to baseColor.copy(alpha = baseColor.alpha * 0.82f),
            0.55f to baseColor.copy(alpha = baseColor.alpha * 0.45f),
            0.76f to baseColor.copy(alpha = baseColor.alpha * 0.14f),
            0.90f to baseColor.copy(alpha = baseColor.alpha * 0.03f),
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
                startFraction = 0.22f,
                endFraction = 0.96f,
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
    baseColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
    blurRadius: Dp = 16.dp,
    contentWindowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable BoxScope.() -> Unit
) {
    val tintBrush = remember(baseColor) {
        Brush.verticalGradient(
            0.00f to Color.Transparent,
            0.10f to baseColor.copy(alpha = baseColor.alpha * 0.03f),
            0.24f to baseColor.copy(alpha = baseColor.alpha * 0.14f),
            0.45f to baseColor.copy(alpha = baseColor.alpha * 0.45f),
            0.70f to baseColor.copy(alpha = baseColor.alpha * 0.82f),
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
                startFraction = 0.22f,
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
    isDark: Boolean = com.example.ui.theme.LocalAppDarkTheme.current,
    baseColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 16.dp
) {
    val progressiveBrush = remember(isTop, baseColor, isDark, backdropState != null) {
        // Keep optical scrim compact near the edge so central camera view stays unobstructed
        val maxAlpha = if (backdropState != null) {
            if (isDark) 0.42f else 0.38f
        } else {
            if (isDark) 0.66f else 0.60f
        }

        if (isTop) {
            Brush.verticalGradient(
                0.00f to baseColor.copy(alpha = maxAlpha),
                0.18f to baseColor.copy(alpha = maxAlpha * 0.78f),
                0.38f to baseColor.copy(alpha = maxAlpha * 0.46f),
                0.56f to baseColor.copy(alpha = maxAlpha * 0.20f),
                0.72f to baseColor.copy(alpha = maxAlpha * 0.05f),
                0.85f to Color.Transparent,
                1.00f to Color.Transparent
            )
        } else {
            Brush.verticalGradient(
                0.00f to Color.Transparent,
                0.15f to Color.Transparent,
                0.28f to baseColor.copy(alpha = maxAlpha * 0.05f),
                0.44f to baseColor.copy(alpha = maxAlpha * 0.20f),
                0.62f to baseColor.copy(alpha = maxAlpha * 0.46f),
                0.82f to baseColor.copy(alpha = maxAlpha * 0.78f),
                1.00f to baseColor.copy(alpha = maxAlpha)
            )
        }
    }

    val rimHighlight = remember(isTop, isDark) {
        val edgeColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.025f)
        if (isTop) {
            Brush.verticalGradient(
                0.92f to Color.Transparent,
                1.00f to edgeColor
            )
        } else {
            Brush.verticalGradient(
                0.00f to edgeColor,
                0.08f to Color.Transparent
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
                startFraction = 0.28f,
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
