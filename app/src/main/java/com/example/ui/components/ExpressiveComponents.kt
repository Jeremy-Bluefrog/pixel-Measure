package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.rememberExpressiveConnectedShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Material 3 Expressive Shape-Morphing Loading Indicator.
 * Morphs seamlessly across expressive geometric primitives:
 * Circle -> Rounded Squircle -> 4-Petal Clover -> 8-Sided Scallop Cookie.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "M3ExpressiveLoader")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shapePhase"
    )
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loaderRotation"
    )

    val path = remember { Path() }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotationDeg }
    ) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val maxR = minOf(cx, cy) * 0.92f
        val steps = 72
        path.reset()

        // Determine weights across 4 expressive shapes:
        // 0..1: Circle -> Squircle
        // 1..2: Squircle -> 4-Petal Clover
        // 2..3: 4-Petal Clover -> 8-Sided Cookie
        // 3..4: 8-Sided Cookie -> Circle
        val idx = phase.toInt().coerceIn(0, 3)
        val frac = phase - idx
        // Smoothstep easing per morph segment
        val t = frac * frac * (3f - 2f * frac)

        fun radiusAt(theta: Float, shapeIdx: Int): Float {
            return when (shapeIdx % 4) {
                0 -> maxR // Circle
                1 -> {
                    // Squircle (superellipse n=3.2)
                    val c = abs(cos(theta)).pow(2f / 3.2f)
                    val s = abs(sin(theta)).pow(2f / 3.2f)
                    maxR * 0.94f / maxOf(0.55f, (c + s).pow(0.5f))
                }
                2 -> {
                    // 4-Petal Clover
                    maxR * (0.76f + 0.22f * cos((4f * theta).toDouble()).toFloat())
                }
                else -> {
                    // 8-Sided Scallop Cookie
                    maxR * (0.85f + 0.14f * cos((8f * theta).toDouble()).toFloat())
                }
            }
        }

        for (i in 0..steps) {
            val theta = (2.0 * PI * i / steps).toFloat()
            val r0 = radiusAt(theta, idx)
            val r1 = radiusAt(theta, idx + 1)
            val r = r0 + (r1 - r0) * t
            val x = cx + r * cos(theta)
            val y = cy + r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path = path, color = color)
    }
}

/**
 * Material 3 Expressive Contained Loading Indicator (`ContainedLoadingIndicator` pattern):
 * Morphing polygon centered inside a soft circular tonal container.
 */
@Composable
fun ExpressiveContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerSize: Dp = 28.dp,
    indicatorSize: Dp = 16.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = modifier.size(containerSize)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            ExpressiveLoadingIndicator(
                size = indicatorSize,
                color = indicatorColor
            )
        }
    }
}

@Immutable
data class ExpressiveButtonOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val testTag: String? = null
)

/**
 * Material 3 Expressive Connected Button Group (`ButtonGroup` Connected pattern):
 * - Tight `2.dp` inner spacing between adjacent items.
 * - Fully rounded pill outer corners (`24.dp`).
 * - Asymmetric inner touching corners (`6.dp` resting) that morph smoothly on
 *   selection or press (`18.dp`) using physics-based `ExpressiveMotion.spatialFast()`.
 */
@Composable
fun <T> ExpressiveConnectedButtonGroup(
    options: List<ExpressiveButtonOption<T>>,
    selectedValue: T,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 42.dp,
    outerCorner: Dp = 22.dp,
    innerRestingCorner: Dp = 6.dp,
    innerActiveCorner: Dp = 18.dp,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    unselectedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedBorderColor: Color = Color.Transparent,
    equalWeight: Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selectedValue
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val shape = rememberExpressiveConnectedShape(
                index = index,
                count = options.size,
                isActive = isSelected,
                isPressed = isPressed,
                outerCorner = outerCorner,
                innerRestingCorner = innerRestingCorner,
                innerActiveCorner = innerActiveCorner,
                pressedOuterCorner = 14.dp
            )

            val containerColor by animateColorAsState(
                targetValue = if (isSelected) selectedContainerColor else unselectedContainerColor,
                animationSpec = ExpressiveMotion.effectsDefault(),
                label = "connBtnBg_$index"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) selectedContentColor else unselectedContentColor,
                animationSpec = ExpressiveMotion.effectsDefault(),
                label = "connBtnFg_$index"
            )
            val pressScale by animateFloatAsState(
                targetValue = when {
                    isPressed -> 0.94f
                    isSelected -> 1.0f
                    else -> 0.99f
                },
                animationSpec = ExpressiveMotion.spatialFast(),
                label = "connBtnScale_$index"
            )

            val weightMod = if (equalWeight) Modifier.weight(1f) else Modifier
            val tagMod = option.testTag?.let { Modifier.testTag(it) } ?: Modifier

            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = shape,
                border = if (!isSelected && unselectedBorderColor != Color.Transparent) {
                    BorderStroke(1.dp, unselectedBorderColor)
                } else null,
                modifier = weightMod
                    .then(tagMod)
                    .heightIn(min = itemHeight)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        role = Role.RadioButton
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueSelected(option.value)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (option.icon != null) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        if (option.label.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                    }
                    if (option.label.isNotEmpty()) {
                        Text(
                            text = option.label,
                            color = contentColor,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Split Button (`SplitButtonLayout` pattern):
 * Combines a primary action pill with a connected secondary dropdown/chevron trigger
 * separated by a 2dp gap, featuring asymmetric inner corner morphing and spring chevron rotation.
 */
@Composable
fun ExpressiveSplitButton(
    label: String,
    leadingIcon: ImageVector?,
    onPrimaryClick: () -> Unit,
    onTrailingClick: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    height: Dp = 40.dp,
    leadingTestTag: String? = null,
    trailingTestTag: String? = null
) {
    val haptic = LocalHapticFeedback.current
    val leadingInteraction = remember { MutableInteractionSource() }
    val trailingInteraction = remember { MutableInteractionSource() }
    val isLeadingPressed by leadingInteraction.collectIsPressedAsState()
    val isTrailingPressed by trailingInteraction.collectIsPressedAsState()

    val leadingInnerCorner by animateDpAsState(
        targetValue = if (isLeadingPressed || expanded) 16.dp else 5.dp,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "splitLeadInner"
    )
    val trailingInnerCorner by animateDpAsState(
        targetValue = if (isTrailingPressed || expanded) 18.dp else 5.dp,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "splitTrailInner"
    )
    val trailingOuterCorner by animateDpAsState(
        targetValue = if (expanded) 14.dp else 22.dp,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "splitTrailOuter"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "splitChevronRot"
    )
    val leadingScale by animateFloatAsState(
        targetValue = if (isLeadingPressed) 0.95f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "splitLeadScale"
    )
    val trailingScale by animateFloatAsState(
        targetValue = if (isTrailingPressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "splitTrailScale"
    )

    val leadingShape = RoundedCornerShape(
        topStart = 22.dp,
        bottomStart = 22.dp,
        topEnd = leadingInnerCorner,
        bottomEnd = leadingInnerCorner
    )
    val trailingShape = RoundedCornerShape(
        topStart = trailingInnerCorner,
        bottomStart = trailingInnerCorner,
        topEnd = trailingOuterCorner,
        bottomEnd = trailingOuterCorner
    )

    val trailingBg by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.primaryContainer else containerColor,
        animationSpec = ExpressiveMotion.effectsFast(),
        label = "splitTrailBg"
    )
    val trailingFg by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer else contentColor,
        animationSpec = ExpressiveMotion.effectsFast(),
        label = "splitTrailFg"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = leadingShape,
            border = BorderStroke(0.8.dp, borderColor),
            modifier = Modifier
                .then(leadingTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                .heightIn(min = height)
                .graphicsLayer {
                    scaleX = leadingScale
                    scaleY = leadingScale
                }
                .clip(leadingShape)
                .clickable(
                    interactionSource = leadingInteraction,
                    indication = ripple(bounded = true)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPrimaryClick()
                }
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }

        Surface(
            color = trailingBg,
            contentColor = trailingFg,
            shape = trailingShape,
            border = BorderStroke(0.8.dp, borderColor),
            modifier = Modifier
                .then(trailingTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                .heightIn(min = height)
                .graphicsLayer {
                    scaleX = trailingScale
                    scaleY = trailingScale
                }
                .clip(trailingShape)
                .clickable(
                    interactionSource = trailingInteraction,
                    indication = ripple(bounded = true)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTrailingClick()
                }
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "展開更多選項",
                    tint = trailingFg,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }
    }
}

/**
 * Material 3 Expressive Horizontal Floating Toolbar (`HorizontalFloatingToolbar` pattern):
 * Elevated capsule container with spring-driven layout morphing (`animateContentSize`).
 */
@Composable
fun ExpressiveFloatingToolbar(
    modifier: Modifier = Modifier,
    vibrant: Boolean = false,
    containerColor: Color = if (vibrant) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
    },
    contentColor: Color = if (vibrant) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    },
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier.animateContentSize(animationSpec = ExpressiveMotion.spatialDefault()),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = horizontalArrangement,
            content = content
        )
    }
}
