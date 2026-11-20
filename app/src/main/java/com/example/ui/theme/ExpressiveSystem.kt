package com.example.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Material 3 Expressive Physics-Based Spring Motion Scheme.
 *
 * Separates animations into:
 * - Spatial specs (bounds, scale, corner morphing, translation) with lively overshoot bounce.
 * - Effects specs (color, alpha, elevation) critically damped with dampingRatio = 1.0f so
 *   non-spatial visual properties never flicker or bounce unnaturally.
 */
@Immutable
object ExpressiveMotion {
    fun <T> spatialFast(): SpringSpec<T> = spring(
        dampingRatio = 0.58f,
        stiffness = 750f
    )

    fun <T> spatialDefault(): SpringSpec<T> = spring(
        dampingRatio = 0.65f,
        stiffness = 460f
    )

    fun <T> spatialSlow(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 300f
    )

    fun <T> effectsFast(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1400f
    )

    fun <T> effectsDefault(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 650f
    )

    fun <T> effectsSlow(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 320f
    )
}

val LocalExpressiveMotion = staticCompositionLocalOf { ExpressiveMotion }

/**
 * Material 3 Expressive Shape Library:
 * Curated geometric shapes inspired by the M3 Expressive 35-shape system:
 * - Cookie (8-sided, 9-sided, 12-sided scalloped emblems)
 * - Clover4 (4-petal organic quatrefoil)
 * - Squircle (smooth superellipse)
 * - Morphable polygon-to-scallop shape for ExpressiveLoadingIndicator
 */
class ExpressiveCookieShape(
    private val sides: Int = 8,
    private val scallopDepth: Float = 0.11f,
    private val rotationOffsetRad: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy)
        val path = Path()
        val steps = sides * 18

        for (i in 0..steps) {
            val theta = (2.0 * PI * i / steps).toFloat() + rotationOffsetRad
            val harmonic = cos((sides * (theta - rotationOffsetRad)).toDouble()).toFloat()
            val radius = r * (1f - scallopDepth + scallopDepth * harmonic)
            val x = cx + radius * cos(theta)
            val y = cy + radius * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

class ExpressiveClover4Shape(
    private val rotationOffsetRad: Float = (PI / 4.0).toFloat()
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy)
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val theta = (2.0 * PI * i / steps).toFloat() + rotationOffsetRad
            val lobe = cos((4f * (theta - rotationOffsetRad)).toDouble()).toFloat()
            val radius = r * (0.83f + 0.17f * lobe)
            val x = cx + radius * cos(theta)
            val y = cy + radius * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

class ExpressiveSquircleShape(
    private val exponent: Float = 2.8f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = cx
        val ry = cy
        val path = Path()
        val steps = 72
        val invExp = 2f / exponent

        for (i in 0..steps) {
            val theta = (2.0 * PI * i / steps).toFloat()
            val cosT = cos(theta)
            val sinT = sin(theta)
            val x = cx + rx * sign(cosT) * kotlin.math.abs(cosT).pow(invExp)
            val y = cy + ry * sign(sinT) * kotlin.math.abs(sinT).pow(invExp)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val ExpressiveCookie8Shape: Shape = ExpressiveCookieShape(sides = 8, scallopDepth = 0.10f)
val ExpressiveCookie9Shape: Shape = ExpressiveCookieShape(sides = 9, scallopDepth = 0.11f)
val ExpressiveCookie12Shape: Shape = ExpressiveCookieShape(sides = 12, scallopDepth = 0.08f)
val ExpressiveCloverShape: Shape = ExpressiveClover4Shape()
val ExpressiveSquircle: Shape = ExpressiveSquircleShape(exponent = 3.0f)

/**
 * Animated Material 3 Expressive asymmetric connected corner shape:
 * Outer edge remains generously rounded (`outerCorner`), while inner touching edges
 * morph dynamically between tight resting corners (`innerRestingCorner`) and
 * expressive rounded corners (`innerActiveCorner`) when selected or pressed.
 */
@Composable
fun rememberExpressiveConnectedShape(
    index: Int,
    count: Int,
    isActive: Boolean,
    isPressed: Boolean,
    outerCorner: Dp = 24.dp,
    innerRestingCorner: Dp = 6.dp,
    innerActiveCorner: Dp = 18.dp,
    pressedOuterCorner: Dp = 14.dp
): RoundedCornerShape {
    val targetOuter = if (isPressed) pressedOuterCorner else outerCorner
    val targetInner = when {
        isPressed -> innerActiveCorner
        isActive -> innerActiveCorner
        else -> innerRestingCorner
    }

    val animatedOuter: Dp = animateDpAsState(
        targetValue = targetOuter,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "exprConnOuter_$index"
    ).value

    val animatedInner: Dp = animateDpAsState(
        targetValue = targetInner,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "exprConnInner_$index"
    ).value

    return when {
        count <= 1 -> RoundedCornerShape(animatedOuter)
        index == 0 -> RoundedCornerShape(
            topStart = animatedOuter,
            bottomStart = animatedOuter,
            topEnd = animatedInner,
            bottomEnd = animatedInner
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = animatedInner,
            bottomStart = animatedInner,
            topEnd = animatedOuter,
            bottomEnd = animatedOuter
        )
        else -> RoundedCornerShape(animatedInner)
    }
}

@Composable
fun rememberExpressivePressCorner(
    isPressed: Boolean,
    restingCorner: Dp = 24.dp,
    pressedCorner: Dp = 12.dp
): State<Dp> = animateDpAsState(
    targetValue = if (isPressed) pressedCorner else restingCorner,
    animationSpec = ExpressiveMotion.spatialFast(),
    label = "exprPressCorner"
)
