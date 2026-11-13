package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.rememberExpressivePressCorner

data class FloatingPillNavItem(
    val id: Int,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Material 3 Expressive Floating Pill Toolbar Navigation Bar:
 * - Uses M3 Expressive Spatial physics-based springs (bouncy overshoot) for expansion & scale
 * - Uses critically damped Effects springs for color transitions
 * - Morphs corner radius dynamically on press (pill <-> squircle) for tactile expression
 * - Preserves 48dp+ accessible touch targets and exact testTags
 */
@Composable
fun FloatingPillNavigationBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    items: List<FloatingPillNavItem>,
    onItemSelected: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val containerBg = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val activePillBg = MaterialTheme.colorScheme.primaryContainer
    val activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveIconColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .wrapContentWidth()
            .animateContentSize(animationSpec = ExpressiveMotion.spatialDefault()),
        shape = CircleShape,
        color = containerBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val isSelected = item.id == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) activePillBg else Color.Transparent,
                    animationSpec = ExpressiveMotion.effectsDefault(),
                    label = "pillBgColor"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) activeContentColor else inactiveIconColor,
                    animationSpec = ExpressiveMotion.effectsDefault(),
                    label = "pillContentColor"
                )

                val pillScale by animateFloatAsState(
                    targetValue = when {
                        isPressed -> 0.93f
                        isSelected -> 1.03f
                        else -> 1.0f
                    },
                    animationSpec = ExpressiveMotion.spatialFast(),
                    label = "pillScale"
                )

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1.0f,
                    animationSpec = ExpressiveMotion.spatialFast(),
                    label = "iconScale"
                )

                val cornerRadius by rememberExpressivePressCorner(
                    isPressed = isPressed,
                    restingCorner = 24.dp,
                    pressedCorner = 12.dp
                )
                val pillShape = RoundedCornerShape(cornerRadius)

                val horizontalPadding = if (isSelected) 16.dp else 10.dp

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .graphicsLayer {
                            scaleX = pillScale
                            scaleY = pillScale
                        }
                        .clip(pillShape)
                        .background(backgroundColor, pillShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onItemSelected(item.id)
                            }
                        )
                        .animateContentSize(animationSpec = ExpressiveMotion.spatialDefault())
                        .minimumInteractiveComponentSize()
                        .testTag(item.testTag)
                        .padding(horizontal = horizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = contentColor,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )

                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(
                                animationSpec = ExpressiveMotion.effectsDefault()
                            ) + expandHorizontally(
                                animationSpec = ExpressiveMotion.spatialDefault(),
                                clip = false
                            ),
                            exit = fadeOut(
                                animationSpec = ExpressiveMotion.effectsFast()
                            ) + shrinkHorizontally(
                                animationSpec = ExpressiveMotion.spatialFast(),
                                clip = false
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = item.label,
                                    color = contentColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
