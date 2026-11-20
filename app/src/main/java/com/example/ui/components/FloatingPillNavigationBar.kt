package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.GoogleSansRounded

data class FloatingPillNavItem(
    val id: Int,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Material 3 Expressive Floating Pill Toolbar Navigation Bar:
 * - Prevents border/label clipping with unconstrained min-height (48.dp+),
 *   safe internal padding, and narrow-screen horizontal scroll guard.
 * - Uses GoogleSansRounded typography and M3 Expressive spatial/effects springs.
 * - Preserves 48dp+ accessible touch targets and exact testTags.
 */
@Composable
fun FloatingPillNavigationBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    items: List<FloatingPillNavItem>,
    onItemSelected: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val containerBg = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val activePillBg = MaterialTheme.colorScheme.primaryContainer
    val activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pillShape = remember { RoundedCornerShape(24.dp) }

    Surface(
        modifier = modifier
            .wrapContentWidth()
            .widthIn(max = 520.dp),
        shape = CircleShape,
        color = containerBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                key(item.id) {
                val isSelected = item.id == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }

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

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.025f else 1.0f,
                    animationSpec = ExpressiveMotion.spatialFast(),
                    label = "iconScale"
                )

                val horizontalPadding = if (isSelected) 16.dp else 12.dp

                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
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
                        .testTag(item.testTag)
                        .padding(horizontal = horizontalPadding, vertical = 10.dp),
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
                                    fontFamily = GoogleSansRounded,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
