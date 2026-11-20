package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SquareFoot
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BlurDirection
import com.example.ui.components.GradientBlurBackdropState
import com.example.ui.components.gradientBlurSource
import com.example.ui.components.hardwareBackdropBlur
import com.example.ui.components.progressiveGlass
import com.example.ui.components.rememberGradientBlurBackdropState
import com.example.ui.i18n.tr
import com.example.ui.theme.ExpressiveCloverShape
import com.example.ui.theme.ExpressiveCookie8Shape
import com.example.ui.theme.ExpressiveSquircle
import com.example.ui.theme.GoogleSansRounded
import com.example.ui.theme.LocalAppDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slide 1 Showcase: Official Material 3 Rounded Iconography (Material Symbols)
 * layered over blurred ambient bokeh and frosted glass pills.
 */
@Composable
internal fun WelcomeArHeroShowcase(
    backdropState: GradientBlurBackdropState? = null,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "welcomeHeroAnim")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reticlePulse"
    )
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    val isDark = LocalAppDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val cardShape = RoundedCornerShape(30.dp)
    val innerGlassState = rememberGradientBlurBackdropState()

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .progressiveGlass(
                backdropState = backdropState,
                direction = BlurDirection.RADIAL_EDGE,
                maxBlurRadius = 26.dp,
                tintColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.52f else 0.68f),
                shape = cardShape,
                borderColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.65f)
            )
            .testTag("welcome_hero_card")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background blurred bokeh & animated measurement rings sampled by pill glass
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .gradientBlurSource(innerGlassState)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .hardwareBackdropBlur(enabled = true, blurRadius = 64f)
                ) {
                    drawCircle(
                        color = primaryColor.copy(alpha = if (isDark) 0.34f else 0.24f),
                        radius = size.minDimension * 0.42f,
                        center = Offset(size.width * 0.28f, size.height * 0.32f)
                    )
                    drawCircle(
                        color = tertiaryColor.copy(alpha = if (isDark) 0.28f else 0.20f),
                        radius = size.minDimension * 0.38f,
                        center = Offset(size.width * 0.76f, size.height * 0.62f)
                    )
                    drawCircle(
                        color = secondaryColor.copy(alpha = if (isDark) 0.22f else 0.16f),
                        radius = size.minDimension * 0.28f,
                        center = Offset(size.width * 0.55f, size.height * 0.20f)
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height * 0.43f)
                    val baseRadius = 58.dp.toPx() * pulse

                    drawCircle(
                        color = primaryColor.copy(alpha = 0.16f),
                        radius = baseRadius * 1.24f,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawArc(
                        color = primaryColor.copy(alpha = 0.70f),
                        startAngle = sweep,
                        sweepAngle = 220f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                        size = Size(baseRadius * 2f, baseRadius * 2f),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                    drawArc(
                        color = tertiaryColor.copy(alpha = 0.58f),
                        startAngle = -sweep * 0.7f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius * 1.16f, center.y - baseRadius * 1.16f),
                        size = Size(baseRadius * 2.32f, baseRadius * 2.32f),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }

                // Central Hero Official Material 3 Icon Emblem (ViewInAr) + Frosted Satellites
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-12).dp)
                ) {
                    Surface(
                        shape = ExpressiveCookie8Shape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ViewInAr,
                                contentDescription = tr("wel_card_ar_icon_desc"),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Surface(
                        shape = ExpressiveSquircle,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.18f else 0.55f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-32).dp, y = (-8).dp)
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Straighten,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        shape = ExpressiveCloverShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.18f else 0.55f)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 32.dp, y = 6.dp)
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SquareFoot,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // Frosted Glass Bottom Badge
            val pillShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .clip(pillShape)
                    .progressiveGlass(
                        backdropState = innerGlassState,
                        direction = BlurDirection.BOTTOM_TO_TOP,
                        maxBlurRadius = 18.dp,
                        tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.72f else 0.80f),
                        shape = pillShape,
                        borderColor = Color.White.copy(alpha = if (isDark) 0.18f else 0.55f)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = ExpressiveCookie8Shape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusStrong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text(
                        text = tr("wel_card_ar_caption"),
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Slide 2 Showcase: Photo, Video Recording & History Organization with Frosted Glass & M3 Icons
 */
@Composable
internal fun WelcomeCaptureShowcase(
    backdropState: GradientBlurBackdropState? = null,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "welcomeCaptureAnim")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lineScanProgress"
    )
    val recAlpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recBlink"
    )

    val isDark = LocalAppDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val cardShape = RoundedCornerShape(30.dp)
    val innerGlassState = rememberGradientBlurBackdropState()

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .progressiveGlass(
                backdropState = backdropState,
                direction = BlurDirection.RADIAL_EDGE,
                maxBlurRadius = 26.dp,
                tintColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.52f else 0.68f),
                shape = cardShape,
                borderColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.65f)
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .gradientBlurSource(innerGlassState)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .hardwareBackdropBlur(enabled = true, blurRadius = 60f)
                ) {
                    drawCircle(
                        color = secondary.copy(alpha = if (isDark) 0.32f else 0.22f),
                        radius = size.minDimension * 0.40f,
                        center = Offset(size.width * 0.72f, size.height * 0.30f)
                    )
                    drawCircle(
                        color = primary.copy(alpha = if (isDark) 0.28f else 0.20f),
                        radius = size.minDimension * 0.36f,
                        center = Offset(size.width * 0.26f, size.height * 0.66f)
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pad = 24.dp.toPx()
                    val frameW = size.width - pad * 2
                    val frameH = size.height - pad * 2
                    val cornerLen = 18.dp.toPx()
                    val strokeW = 3.dp.toPx()

                    val left = pad
                    val top = pad
                    val right = left + frameW
                    val bottom = top + frameH
                    val bracketColor = primary.copy(alpha = 0.72f)

                    drawLine(bracketColor, Offset(left, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(left, top), Offset(left, top + cornerLen), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(right - cornerLen, top), Offset(right, top), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(right, top), Offset(right, top + cornerLen), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(left, bottom - cornerLen), Offset(left, bottom), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(right - cornerLen, bottom), Offset(right, bottom), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(right, bottom - cornerLen), Offset(right, bottom), strokeW, StrokeCap.Round)

                    val boxLeft = size.width * 0.22f
                    val boxTop = size.height * 0.33f
                    val boxW = size.width * 0.56f
                    val boxH = size.height * 0.32f
                    drawRoundRect(
                        color = primary.copy(alpha = 0.12f),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                    drawRoundRect(
                        color = primary.copy(alpha = 0.38f),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    val pStart = Offset(boxLeft + 14.dp.toPx(), boxTop + boxH * 0.5f)
                    val pEnd = Offset(
                        boxLeft + 14.dp.toPx() + (boxW - 28.dp.toPx()) * (0.4f + 0.6f * progress),
                        boxTop + boxH * 0.5f
                    )
                    drawLine(
                        color = primary,
                        start = pStart,
                        end = pEnd,
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = primary, radius = 6.dp.toPx(), center = pStart)
                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = pStart)
                    drawCircle(color = primary, radius = 6.dp.toPx(), center = pEnd)
                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = pEnd)
                }

                // M3 Photo & Video Icon Pair
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = ExpressiveSquircle,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Surface(
                        shape = ExpressiveCloverShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.90f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Frosted REC pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .clip(CircleShape)
                    .progressiveGlass(
                        backdropState = innerGlassState,
                        direction = BlurDirection.TOP_TO_BOTTOM,
                        maxBlurRadius = 14.dp,
                        tintColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.72f else 0.80f),
                        shape = CircleShape,
                        borderColor = Color.White.copy(alpha = if (isDark) 0.16f else 0.55f)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFE53935).copy(alpha = recAlpha), CircleShape)
                    )
                    Text(
                        text = tr("wel_card_capture_badge"),
                        fontFamily = GoogleSansRounded,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val pillShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .clip(pillShape)
                    .progressiveGlass(
                        backdropState = innerGlassState,
                        direction = BlurDirection.BOTTOM_TO_TOP,
                        maxBlurRadius = 18.dp,
                        tintColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.72f else 0.80f),
                        shape = pillShape,
                        borderColor = Color.White.copy(alpha = if (isDark) 0.18f else 0.55f)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = ExpressiveCloverShape,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text(
                        text = tr("wel_card_capture_caption"),
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Slide 3 Showcase: Dual-Edge Screen Ruler & Dual-Line Caliper with Optical Frosted Glass
 */
@Composable
internal fun WelcomeToolsShowcase(
    backdropState: GradientBlurBackdropState? = null,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "welcomeToolsAnim")
    val caliperSweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caliperSweep"
    )

    val isDark = LocalAppDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val cardShape = RoundedCornerShape(30.dp)
    val innerGlassState = rememberGradientBlurBackdropState()

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .progressiveGlass(
                backdropState = backdropState,
                direction = BlurDirection.RADIAL_EDGE,
                maxBlurRadius = 26.dp,
                tintColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.52f else 0.68f),
                shape = cardShape,
                borderColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.65f)
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .gradientBlurSource(innerGlassState)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .hardwareBackdropBlur(enabled = true, blurRadius = 60f)
                ) {
                    drawCircle(
                        color = tertiary.copy(alpha = if (isDark) 0.34f else 0.24f),
                        radius = size.minDimension * 0.42f,
                        center = Offset(size.width * 0.50f, size.height * 0.44f)
                    )
                    drawCircle(
                        color = primary.copy(alpha = if (isDark) 0.24f else 0.16f),
                        radius = size.minDimension * 0.32f,
                        center = Offset(size.width * 0.85f, size.height * 0.28f)
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    var y = 20.dp.toPx()
                    var idx = 0
                    val step = 10.dp.toPx()
                    while (y < h - 54.dp.toPx()) {
                        val tickLen = if (idx % 5 == 0) 24.dp.toPx() else 13.dp.toPx()
                        drawLine(
                            color = onSurface.copy(alpha = if (idx % 5 == 0) 0.7f else 0.35f),
                            start = Offset(0f, y),
                            end = Offset(tickLen, y),
                            strokeWidth = if (idx % 5 == 0) 2.2.dp.toPx() else 1.2.dp.toPx()
                        )
                        y += step
                        idx++
                    }

                    y = 20.dp.toPx()
                    idx = 0
                    val inchStep = 14.dp.toPx()
                    while (y < h - 54.dp.toPx()) {
                        val tickLen = if (idx % 4 == 0) 24.dp.toPx() else 12.dp.toPx()
                        drawLine(
                            color = primary.copy(alpha = if (idx % 4 == 0) 0.78f else 0.35f),
                            start = Offset(w, y),
                            end = Offset(w - tickLen, y),
                            strokeWidth = if (idx % 4 == 0) 2.2.dp.toPx() else 1.2.dp.toPx()
                        )
                        y += inchStep
                        idx++
                    }

                    val bandTop = h * 0.25f
                    val bandBottom = h * (0.45f + caliperSweep * 0.16f)
                    drawRect(
                        color = tertiary.copy(alpha = 0.14f),
                        topLeft = Offset(30.dp.toPx(), bandTop),
                        size = Size(w - 60.dp.toPx(), bandBottom - bandTop)
                    )
                    drawLine(
                        color = tertiary,
                        start = Offset(26.dp.toPx(), bandTop),
                        end = Offset(w - 26.dp.toPx(), bandTop),
                        strokeWidth = 2.2.dp.toPx()
                    )
                    drawLine(
                        color = primary,
                        start = Offset(26.dp.toPx(), bandBottom),
                        end = Offset(w - 26.dp.toPx(), bandBottom),
                        strokeWidth = 2.2.dp.toPx()
                    )
                    drawCircle(
                        color = tertiary,
                        radius = 6.dp.toPx(),
                        center = Offset(w * 0.5f, bandTop)
                    )
                    drawCircle(
                        color = primary,
                        radius = 6.dp.toPx(),
                        center = Offset(w * 0.5f, bandBottom)
                    )
                }
            }

            val pillShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .clip(pillShape)
                    .progressiveGlass(
                        backdropState = innerGlassState,
                        direction = BlurDirection.BOTTOM_TO_TOP,
                        maxBlurRadius = 18.dp,
                        tintColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (isDark) 0.72f else 0.80f),
                        shape = pillShape,
                        borderColor = Color.White.copy(alpha = if (isDark) 0.18f else 0.55f)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = ExpressiveSquircle,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Straighten,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text(
                        text = tr("wel_card_tools_caption"),
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
