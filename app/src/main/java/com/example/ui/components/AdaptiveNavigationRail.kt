package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.tr

/**
 * Adaptive Material 3 Navigation Rail for medium (foldables, landscape)
 * and expanded (tablets, desktops) screen form factors.
 */
@Composable
fun AdaptiveNavigationRail(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    cameraLabel: String,
    rulerLabel: String,
    selectedUnit: String,
    onSelectUnit: (String) -> Unit,
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
    recordCount: Int,
    isHistoryOpen: Boolean,
    onToggleHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showUnitMenu by remember { mutableStateOf(false) }

    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .testTag("adaptive_navigation_rail"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            .union(WindowInsets.displayCutout.only(WindowInsetsSides.Start)),
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SquareFoot,
                            contentDescription = tr("brand_logo_desc"),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tr("brand_name"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Primary Navigation: Camera AR
            NavigationRailItem(
                selected = currentMode == 0,
                onClick = { onModeSelected(0) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = cameraLabel
                    )
                },
                label = {
                    Text(
                        text = cameraLabel,
                        fontSize = 11.sp,
                        fontWeight = if (currentMode == 0) FontWeight.Bold else FontWeight.Normal
                    )
                },
                alwaysShowLabel = true,
                modifier = Modifier.testTag("rail_item_camera")
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Navigation: Screen Ruler
            NavigationRailItem(
                selected = currentMode == 1,
                onClick = { onModeSelected(1) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Straighten,
                        contentDescription = rulerLabel
                    )
                },
                label = {
                    Text(
                        text = rulerLabel,
                        fontSize = 11.sp,
                        fontWeight = if (currentMode == 1) FontWeight.Bold else FontWeight.Normal
                    )
                },
                alwaysShowLabel = true,
                modifier = Modifier.testTag("rail_item_ruler")
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                modifier = Modifier
                    .width(40.dp)
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Unit Switcher
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(width = 56.dp, height = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("rail_unit_button"),
                    onClick = { showUnitMenu = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = selectedUnit.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                DropdownMenu(
                    expanded = showUnitMenu,
                    onDismissRequest = { showUnitMenu = false }
                ) {
                    listOf(
                        "mm" to tr("unit_mm"),
                        "cm" to tr("unit_cm"),
                        "m" to tr("unit_meter"),
                        "in" to tr("unit_inch"),
                        "ft" to tr("unit_ft"),
                        "yd" to tr("unit_yd")
                    ).forEach { (code, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    fontWeight = if (selectedUnit == code) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSelectUnit(code)
                                showUnitMenu = false
                            },
                            leadingIcon = if (selectedUnit == code) {
                                {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("rail_unit_item_$code")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Torch Toggle Action
            IconButton(
                onClick = onToggleTorch,
                modifier = Modifier.size(48.dp).testTag("rail_torch_button")
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    contentDescription = tr("rail_torch"),
                    tint = if (isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // History Action with Badge (AR mode only)
            if (currentMode == 0) {
                BadgedBox(
                    badge = {
                        if (recordCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(if (recordCount > 99) "99+" else recordCount.toString())
                            }
                        }
                    }
                ) {
                    IconButton(
                        onClick = onToggleHistory,
                        modifier = Modifier.size(48.dp).testTag("rail_history_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isHistoryOpen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isHistoryOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = tr("rail_history")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Settings Action at bottom
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(48.dp)
                    .testTag("rail_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = tr("rail_settings"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
