package com.example.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ExpressiveCookie8Shape
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.rememberExpressiveConnectedShape
import com.example.ui.theme.rememberExpressivePressCorner
import com.example.ui.viewmodel.MeasureViewModel

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
    onShowHistoryClick: () -> Unit,
    onShowSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraTopHudContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Top Bar Controls Row (M3 Expressive SplitButton + Connected Action Group)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Expressive SubMode Split Button & Morphing Unit Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState())
            ) {
                var showSubModeMenu by remember { mutableStateOf(false) }
                val subModeLabel = when (subMode) {
                    1 -> "面積"
                    2 -> "高度"
                    5 -> "角度"
                    else -> when (autoDetectedType) {
                        "HEIGHT" -> "自動·高"
                        "AREA" -> "自動·面"
                        else -> "自動"
                    }
                }
                val subModeIcon = when (subMode) {
                    1 -> Icons.Rounded.SquareFoot
                    2 -> Icons.Rounded.Height
                    5 -> Icons.Rounded.Architecture
                    else -> Icons.Rounded.AutoMode
                }

                Box {
                    ExpressiveSplitButton(
                        label = subModeLabel,
                        leadingIcon = subModeIcon,
                        onPrimaryClick = {
                            val cycleOrder = listOf(0, 1, 2, 5)
                            val nextIdx = (cycleOrder.indexOf(subMode) + 1) % cycleOrder.size
                            viewModel.setCameraSubMode(cycleOrder[nextIdx])
                        },
                        onTrailingClick = { showSubModeMenu = !showSubModeMenu },
                        expanded = showSubModeMenu,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.78f else 0.92f),
                        contentColor = colorOnSurface,
                        accentColor = colorPrimary,
                        borderColor = colorOutlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f),
                        height = 40.dp,
                        leadingTestTag = "submode_selector_button",
                        trailingTestTag = "submode_dropdown_button"
                    )

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

                // Unit Quick Selector Pill with Expressive Corner Morph
                var showUnitMenu by remember { mutableStateOf(false) }
                val unitInteraction = remember { MutableInteractionSource() }
                val isUnitPressed by unitInteraction.collectIsPressedAsState()
                val unitCorner by rememberExpressivePressCorner(
                    isPressed = isUnitPressed || showUnitMenu,
                    restingCorner = 20.dp,
                    pressedCorner = 10.dp
                )
                val unitScale by animateFloatAsState(
                    targetValue = if (isUnitPressed) 0.93f else 1.0f,
                    animationSpec = ExpressiveMotion.spatialFast(),
                    label = "unitPillScale"
                )
                Box {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showUnitMenu = true
                        },
                        interactionSource = unitInteraction,
                        shape = RoundedCornerShape(unitCorner),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.78f else 0.92f),
                        contentColor = colorPrimary,
                        border = BorderStroke(0.8.dp, colorOutlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f)),
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .graphicsLayer {
                                scaleX = unitScale
                                scaleY = unitScale
                            }
                            .testTag("unit_selector_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = selectedUnit.uppercase(),
                                style = ExpressiveTypography.labelNumeral,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorPrimary,
                                maxLines = 1
                            )
                            Icon(
                                Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = colorPrimary,
                                modifier = Modifier.size(16.dp)
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

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Material 3 Expressive Connected Action Group (Torch · History · Settings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                val baseSurface = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.78f else 0.92f)
                val baseBorder = colorOutlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f)

                // 0: Torch Connected Action
                val torchInteraction = remember { MutableInteractionSource() }
                val isTorchPressed by torchInteraction.collectIsPressedAsState()
                val torchShape = rememberExpressiveConnectedShape(
                    index = 0,
                    count = 3,
                    isActive = isTorchOn || showTorchBrightnessMenu,
                    isPressed = isTorchPressed,
                    outerCorner = 20.dp,
                    innerRestingCorner = 6.dp,
                    innerActiveCorner = 16.dp,
                    pressedOuterCorner = 12.dp
                )
                val torchBg by animateColorAsState(
                    targetValue = if (isTorchOn) colorPrimary else baseSurface,
                    animationSpec = ExpressiveMotion.effectsFast(),
                    label = "torchConnectedBg"
                )
                val torchTint by animateColorAsState(
                    targetValue = if (isTorchOn) colorOnPrimary else colorOnSurface,
                    animationSpec = ExpressiveMotion.effectsFast(),
                    label = "torchConnectedTint"
                )
                val torchScale by animateFloatAsState(
                    targetValue = if (isTorchPressed) 0.92f else 1f,
                    animationSpec = ExpressiveMotion.spatialFast(),
                    label = "torchConnectedScale"
                )

                Box {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (!isTorchOn) {
                                viewModel.toggleTorch(context)
                            } else {
                                showTorchBrightnessMenu = !showTorchBrightnessMenu
                            }
                        },
                        interactionSource = torchInteraction,
                        shape = torchShape,
                        color = torchBg,
                        border = if (isTorchOn) null else BorderStroke(0.8.dp, baseBorder),
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = torchScale
                                scaleY = torchScale
                            }
                            .testTag("flashlight_toggle_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                contentDescription = "手電筒補光",
                                tint = torchTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

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
                                    style = ExpressiveTypography.labelNumeral,
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    0.25f to "25%",
                                    0.50f to "50%",
                                    0.75f to "75%",
                                    1.00f to "100%"
                                ).forEachIndexed { idx, (level, label) ->
                                    val isSelected = kotlin.math.abs(torchBrightness - level) < 0.12f
                                    val chipShape = rememberExpressiveConnectedShape(
                                        index = idx,
                                        count = 4,
                                        isActive = isSelected,
                                        isPressed = false,
                                        outerCorner = 12.dp,
                                        innerRestingCorner = 4.dp,
                                        innerActiveCorner = 10.dp
                                    )
                                    Surface(
                                        color = if (isSelected) colorPrimary else colorSurfaceVariant,
                                        shape = chipShape,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(chipShape)
                                            .clickable {
                                                viewModel.setTorchBrightness(context, level)
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                style = ExpressiveTypography.labelNumeral,
                                                color = if (isSelected) colorOnPrimary else colorOnSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                                            )
                                        }
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
                                shape = RoundedCornerShape(14.dp),
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

                // 1: History Connected Action
                val historyInteraction = remember { MutableInteractionSource() }
                val isHistoryPressed by historyInteraction.collectIsPressedAsState()
                val historyShape = rememberExpressiveConnectedShape(
                    index = 1,
                    count = 3,
                    isActive = false,
                    isPressed = isHistoryPressed,
                    outerCorner = 20.dp,
                    innerRestingCorner = 6.dp,
                    innerActiveCorner = 16.dp
                )
                val historyScale by animateFloatAsState(
                    targetValue = if (isHistoryPressed) 0.92f else 1f,
                    animationSpec = ExpressiveMotion.spatialFast(),
                    label = "historyConnectedScale"
                )

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onShowHistoryClick()
                    },
                    interactionSource = historyInteraction,
                    shape = historyShape,
                    color = baseSurface,
                    border = BorderStroke(0.8.dp, baseBorder),
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            scaleX = historyScale
                            scaleY = historyScale
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = "History",
                            tint = colorOnSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // 2: Settings Connected Action
                val settingsInteraction = remember { MutableInteractionSource() }
                val isSettingsPressed by settingsInteraction.collectIsPressedAsState()
                val settingsShape = rememberExpressiveConnectedShape(
                    index = 2,
                    count = 3,
                    isActive = false,
                    isPressed = isSettingsPressed,
                    outerCorner = 20.dp,
                    innerRestingCorner = 6.dp,
                    innerActiveCorner = 16.dp,
                    pressedOuterCorner = 12.dp
                )
                val settingsScale by animateFloatAsState(
                    targetValue = if (isSettingsPressed) 0.92f else 1f,
                    animationSpec = ExpressiveMotion.spatialFast(),
                    label = "settingsConnectedScale"
                )

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onShowSettingsClick()
                    },
                    interactionSource = settingsInteraction,
                    shape = settingsShape,
                    color = baseSurface,
                    border = BorderStroke(0.8.dp, baseBorder),
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            scaleX = settingsScale
                            scaleY = settingsScale
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = colorOnSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        // 2. Video Recording Active HUD OR AR Horizon Indicator (never overlapping)
        if (isRecordingVideo) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Color(0xFFE53935)),
                shadowElevation = 0.dp
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
                        text = String.format(java.util.Locale.US, "REC %02d:%02d", mins, secs),
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
        } else {
            ArHorizonGuidanceIndicator(
                viewModel = viewModel,
                planesCount = planesCount
            )
        }

        // 3. Optional Extra Top HUD Content (Guidance Pill, Stability Warning Banner) stacked cleanly
        extraTopHudContent?.invoke(this)
    }
}

@Composable
private fun ArHorizonGuidanceIndicator(
    viewModel: MeasureViewModel,
    planesCount: Int,
    modifier: Modifier = Modifier
) {
    val sensorTelemetryState = viewModel.sensorTelemetry.collectAsStateWithLifecycle()
    val horizonUiState by remember {
        derivedStateOf {
            val t = sensorTelemetryState.value
            val pitch = t.pitchDeg
            val roll = t.rollDeg
            val pitchInt = kotlin.math.round(pitch).toInt()
            val rollInt = kotlin.math.round(roll).toInt()
            val isRollLevel = kotlin.math.abs(roll) < 2.0f
            val isPitchLevel = kotlin.math.abs(pitch) < 2.5f || kotlin.math.abs(pitch - 90f) < 2.5f || kotlin.math.abs(pitch + 90f) < 2.5f
            val isLevel = isRollLevel && isPitchLevel
            Triple(isLevel, pitchInt, rollInt)
        }
    }
    val isLevel = horizonUiState.first
    val pitchInt = horizonUiState.second
    val rollInt = horizonUiState.third

    val levelText = remember(isLevel, pitchInt, rollInt) {
        if (isLevel) "基準水平對齊 (0°)" else "俯仰 $pitchInt° · 滾轉 $rollInt°"
    }

    val animatedIndicatorColor by animateColorAsState(
        targetValue = if (isLevel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        animationSpec = ExpressiveMotion.effectsDefault(),
        label = "horizonColor"
    )
    val horizonPopScale by animateFloatAsState(
        targetValue = if (isLevel) 1.04f else 1.0f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "horizonPopScale"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = CircleShape,
        border = BorderStroke(1.dp, animatedIndicatorColor),
        modifier = modifier
            .graphicsLayer {
                scaleX = horizonPopScale
                scaleY = horizonPopScale
            }
            .animateContentSize(animationSpec = ExpressiveMotion.spatialDefault())
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isLevel) {
                Surface(
                    shape = ExpressiveCookie8Shape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Rounded.FilterTiltShift,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = levelText,
                style = ExpressiveTypography.labelNumeral,
                color = if (isLevel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isLevel) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp
            )
            if (planesCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "表面×$planesCount",
                        style = ExpressiveTypography.labelNumeral,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
