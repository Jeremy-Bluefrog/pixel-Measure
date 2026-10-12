package com.example.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.sensor.SensorCorrectionTelemetry
import com.example.ui.viewmodel.MeasureViewModel
import java.util.Locale

@Composable
fun ModernArTopBar(
    viewModel: MeasureViewModel,
    context: Context,
    subMode: Int,
    autoDetectedType: String,
    selectedUnit: String,
    isTorchOn: Boolean,
    torchBrightness: Float,
    cameraAspectRatio: String,
    planesCount: Int,
    isRecordingVideo: Boolean,
    recordingSeconds: Int,
    onShowHelp: () -> Unit,
    onShowHistoryClick: () -> Unit,
    onShowSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnPrimary = MaterialTheme.colorScheme.onPrimary
    val colorSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val colorSurfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val colorOutlineVariant = MaterialTheme.colorScheme.outlineVariant
    val colorError = MaterialTheme.colorScheme.error
    val colorOnError = MaterialTheme.colorScheme.onError
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var showTorchBrightnessMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        // 1. Top Bar Items
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Quick SubMode Selector & Unit Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // SubMode Selector Pill
                var showSubModeMenu by remember { mutableStateOf(false) }
                val subModeLabel = when (subMode) {
                    1 -> "面積"
                    2 -> "高度"
                    5 -> "角度"
                    else -> if (autoDetectedType == "HEIGHT") "自動 (高度)" else if (autoDetectedType == "AREA") "自動 (面積)" else "自動"
                }

                Box {
                    Surface(
                        onClick = { showSubModeMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.88f),
                        contentColor = colorOnSurface,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.testTag("submode_selector_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                when (subMode) {
                                    1 -> Icons.Rounded.SquareFoot
                                    2 -> Icons.Rounded.Height
                                    5 -> Icons.Rounded.Architecture
                                    else -> Icons.Rounded.AutoMode
                                },
                                contentDescription = null,
                                tint = colorPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = subModeLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorOnSurface
                            )
                            Icon(
                                Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = colorOnSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSubModeMenu,
                        onDismissRequest = { showSubModeMenu = false },
                        modifier = Modifier.background(colorSurfaceContainer)
                    ) {
                        listOf(
                            0 to ("智慧自動 (Auto)" to Icons.Rounded.AutoMode),
                            -1 to ("直線距離 (Distance)" to Icons.Rounded.Straighten),
                            1 to ("封閉面積 (Area)" to Icons.Rounded.SquareFoot),
                            2 to ("垂直高度 (Height)" to Icons.Rounded.Height),
                            5 to ("空間夾角 (Angle)" to Icons.Rounded.Architecture)
                        ).forEach { (modeId, info) ->
                            val (label, icon) = info
                            val isSelected = (modeId == 0 && subMode == 0) || (modeId == subMode) || (modeId == -1 && subMode == 0 && autoDetectedType == "DISTANCE")
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colorPrimary else colorOnSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(icon, null, tint = if (isSelected) colorPrimary else colorOnSurfaceVariant)
                                },
                                onClick = {
                                    if (modeId == -1) {
                                        viewModel.setCameraSubMode(0)
                                    } else {
                                        viewModel.setCameraSubMode(modeId)
                                    }
                                    showSubModeMenu = false
                                }
                            )
                        }
                    }
                }

                // Unit Quick Selector Pill
                var showUnitMenu by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        onClick = { showUnitMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.88f),
                        contentColor = colorPrimary,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.testTag("unit_selector_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = selectedUnit.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorPrimary
                            )
                            Icon(
                                Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = colorPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showUnitMenu,
                        onDismissRequest = { showUnitMenu = false },
                        modifier = Modifier.background(colorSurfaceContainer)
                    ) {
                        listOf("cm" to "公分 (cm)", "m" to "公尺 (m)", "in" to "英吋 (in)", "ft" to "英呎 (ft)", "yd" to "碼 (yd)").forEach { (unitCode, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        fontWeight = if (selectedUnit == unitCode) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedUnit == unitCode) colorPrimary else colorOnSurface
                                    )
                                },
                                onClick = {
                                    viewModel.setSelectedUnit(unitCode)
                                    showUnitMenu = false
                                },
                                leadingIcon = if (selectedUnit == unitCode) {
                                    { Icon(Icons.Rounded.Check, null, tint = colorPrimary) }
                                } else null
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Right: Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Help Button
                IconButton(
                    onClick = onShowHelp,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.88f),
                            CircleShape
                        )
                        .border(
                            width = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f),
                            shape = CircleShape
                        )
                        .shadow(2.dp, CircleShape)
                        .testTag("help_guide_button")
                ) {
                    Icon(
                        Icons.Rounded.HelpOutline,
                        contentDescription = "測量指南",
                        tint = colorOnSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Torch Button
                Box {
                    IconButton(
                        onClick = {
                            if (!isTorchOn) {
                                viewModel.toggleTorch(context)
                            } else {
                                showTorchBrightnessMenu = !showTorchBrightnessMenu
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isTorchOn) colorPrimary else MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.88f),
                                CircleShape
                            )
                            .border(
                                width = 0.8.dp,
                                color = if (isTorchOn) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f),
                                shape = CircleShape
                            )
                            .shadow(if (isTorchOn) 5.dp else 2.dp, CircleShape)
                            .testTag("flashlight_toggle_button")
                    ) {
                        Icon(
                            if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = "手電筒補光",
                            tint = if (isTorchOn) colorOnPrimary else colorOnSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Torch Brightness Popup Panel
                    DropdownMenu(
                        expanded = showTorchBrightnessMenu,
                        onDismissRequest = { showTorchBrightnessMenu = false },
                        modifier = Modifier
                            .background(colorSurfaceContainer)
                            .widthIn(min = 220.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.LightMode,
                                        contentDescription = null,
                                        tint = colorPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "手電筒亮度",
                                        color = colorOnSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "${(torchBrightness * 100).toInt()}%",
                                    color = colorPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = torchBrightness,
                                onValueChange = { newVal ->
                                    viewModel.setTorchBrightness(context, newVal)
                                },
                                valueRange = 0.2f..1.0f,
                                steps = 3,
                                colors = SliderDefaults.colors(
                                    thumbColor = colorPrimary,
                                    activeTrackColor = colorPrimary,
                                    inactiveTrackColor = colorSurfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Preset Level Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(
                                    0.25f to "25%",
                                    0.50f to "50%",
                                    0.75f to "75%",
                                    1.00f to "100%"
                                ).forEach { (level, label) ->
                                    val isSelected = kotlin.math.abs(torchBrightness - level) < 0.12f
                                    Surface(
                                        color = if (isSelected) colorPrimary else colorSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable {
                                            viewModel.setTorchBrightness(context, level)
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) colorOnPrimary else colorOnSurfaceVariant,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = colorOutlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Button(
                                onClick = {
                                    viewModel.turnOffTorch(context)
                                    showTorchBrightnessMenu = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorError,
                                    contentColor = colorOnError
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.FlashlightOff, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("關閉手電筒", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // History
                IconButton(
                    onClick = onShowHistoryClick,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.88f), CircleShape)
                        .border(
                            width = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f),
                            shape = CircleShape
                        )
                        .shadow(2.dp, CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = "History",
                        tint = colorOnSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Settings
                IconButton(
                    onClick = onShowSettingsClick,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.88f), CircleShape)
                        .border(
                            width = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f),
                            shape = CircleShape
                        )
                        .shadow(2.dp, CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = colorOnSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // 2. AR Horizon & Spatial Surface Guidance Indicator
        ArHorizonGuidanceIndicator(
            viewModel = viewModel,
            planesCount = planesCount,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 54.dp)
        )

        // 3. Aspect Ratio Framing Overlay
        if (cameraAspectRatio != "FULL") {
            val targetRatio = when (cameraAspectRatio) {
                "4_3" -> 3f / 4f
                "16_9" -> 9f / 16f
                "1_1" -> 1.0f
                else -> 0f
            }
            if (targetRatio > 0f) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val totalW = maxWidth
                    val totalH = maxHeight
                    if (totalW.value > 0f && totalH.value > 0f) {
                        val currentRatio = totalW.value / totalH.value
                        var padH = 0.dp
                        var padV = 0.dp

                        if (currentRatio > targetRatio) {
                            val frameW = totalH.value * targetRatio
                            padH = ((totalW.value - frameW) / 2f).coerceAtLeast(0f).dp
                        } else {
                            val frameH = totalW.value / targetRatio
                            padV = ((totalH.value - frameH) / 2f).coerceAtLeast(0f).dp
                        }

                        val maskColor = Color.Black.copy(alpha = 0.45f)
                        if (padV > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(padV)
                                    .align(Alignment.TopCenter)
                                    .background(maskColor)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(padV)
                                    .align(Alignment.BottomCenter)
                                    .background(maskColor)
                            )
                        }
                        if (padH > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(padH)
                                    .align(Alignment.CenterStart)
                                    .background(maskColor)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(padH)
                                    .align(Alignment.CenterEnd)
                                    .background(maskColor)
                            )
                        }
                    }
                }
            }
        }

        // 4. Video Recording Active HUD
        if (isRecordingVideo) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, Color(0xFFE53935)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 58.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    )
                    val mins = recordingSeconds / 60
                    val secs = recordingSeconds % 60
                    Text(
                        text = String.format("REC %02d:%02d", mins, secs),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "· 點擊快門停止",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ArHorizonGuidanceIndicator(
    viewModel: MeasureViewModel,
    planesCount: Int,
    modifier: Modifier = Modifier
) {
    val sensorTelemetry by viewModel.sensorTelemetry.collectAsState()
    val pitch: Float = sensorTelemetry.pitchDeg
    val roll: Float = sensorTelemetry.rollDeg
    val isRollLevel = kotlin.math.abs(roll) < 2.0f
    val isPitchLevel = kotlin.math.abs(pitch) < 2.5f || kotlin.math.abs(pitch - 90f) < 2.5f || kotlin.math.abs(pitch + 90f) < 2.5f
    val isLevel = isRollLevel && isPitchLevel

    val animatedIndicatorColor by animateColorAsState(
        targetValue = if (isLevel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        animationSpec = tween(200),
        label = "horizonColor"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, animatedIndicatorColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (isLevel) Icons.Rounded.CheckCircle else Icons.Rounded.FilterTiltShift,
                contentDescription = null,
                tint = if (isLevel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (isLevel) "🎯 基準水平對齊 (0°)" else "俯仰 ${String.format(Locale.US, "%.0f°", pitch)} · 滾轉 ${String.format(Locale.US, "%.0f°", roll)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLevel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isLevel) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            )
            if (planesCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "表面×$planesCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
