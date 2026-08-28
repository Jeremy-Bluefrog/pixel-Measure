package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FloatingPillNavItem(
    val id: Int,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Floating Pill Navigation Bar inspired by modern capsule dock designs:
 * - Fluid spring morphing with animateContentSize
 * - Elevated white/translucent capsule container with smooth outline border
 * - Active item represented as an expanded pill with soft pastel-blue background, icon, and label
 * - Inactive items represented as clean circular icon buttons
 * - Tactile haptic feedback and responsive scale dynamics on tab changes
 */
@Composable
fun FloatingPillNavigationBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    items: List<FloatingPillNavItem>,
    onItemSelected: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Colors matching user reference screenshot
    val containerBg = Color.White.copy(alpha = 0.96f)
    val borderColor = Color(0xFFCAD4DE)
    val activePillBg = Color(0xFFD6E9F7)
    val activeContentColor = Color(0xFF1E3A5F)
    val inactiveIconColor = Color(0xFF2C445E)

    Surface(
        modifier = modifier
            .wrapContentWidth()
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color(0xFF1E293B).copy(alpha = 0.16f)
            ),
        shape = CircleShape,
        color = containerBg,
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val isSelected = item.id == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) activePillBg else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "pillBgColor"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) activeContentColor else inactiveIconColor,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "pillContentColor"
                )

                val pillScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.04f else 0.95f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "pillScale"
                )

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.12f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "iconScale"
                )

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .graphicsLayer {
                            scaleX = pillScale
                            scaleY = pillScale
                        }
                        .clip(CircleShape)
                        .background(backgroundColor, CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onItemSelected(item.id)
                            }
                        )
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .minimumInteractiveComponentSize()
                        .testTag(item.testTag)
                        .padding(horizontal = if (isSelected) 16.dp else 10.dp),
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
                            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                    expandHorizontally(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
                            exit = fadeOut(tween(100)) +
                                    shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = item.label,
                                    color = contentColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
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
