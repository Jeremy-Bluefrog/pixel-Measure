package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.ShareUtility
import com.example.logic.TranslationManager
import com.example.ui.components.LanguageSelectionDialog
import com.example.ui.theme.GroupedPosition
import com.example.ui.theme.groupedItemShape
import com.example.ui.viewmodel.MeasureViewModel

/**
 * Material Design 3 Expressive Settings Page:
 * Implements M3 Grouped List Shapes (Connected Container Shapes) where items in a
 * section morph their corner radii according to their position:
 * - TOP: Large top rounded corners (24dp), subtle bottom corners (4dp)
 * - MIDDLE: Subtle connected corners on all sides (4dp)
 * - BOTTOM: Subtle top corners (4dp), large bottom rounded corners (24dp)
 * - SINGLE: Large rounded corners on all sides (24dp)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MeasureViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Handle Android system back gesture and hardware back button
    BackHandler {
        onNavigateBack()
    }

    var showClearRecordsConfirmDialog by remember { mutableStateOf(false) }

    val vibrateOnAlign by viewModel.vibrateOnAlignment.collectAsState()
    val showPointCloud by viewModel.showPointCloud.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val rulerCalibration by viewModel.rulerCalibration.collectAsState()
    val highFpsModeEnabled by viewModel.highFpsModeEnabled.collectAsState()
    val highDefinitionQualityEnabled by viewModel.highDefinitionQualityEnabled.collectAsState()
    val sensorCorrectionEnabled by viewModel.sensorCorrectionEnabled.collectAsState()
    val torchBrightness by viewModel.torchBrightness.collectAsState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "設定",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "測量偏好、硬體加速與系統管理",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回測量",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            ) {
                // ==========================================
                // 1. 測量偏好 (Measurement Preferences)
                // ==========================================
                SettingsCategoryHeader(
                    title = "測量與顯示偏好",
                    icon = Icons.Rounded.Straighten,
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Group 1 Item 1 (TOP): M3 Segmented Button for Unit Selection
                M3GroupedItemContainer(position = GroupedPosition.TOP) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.SquareFoot,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "預設測量基準單位",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "即時切換長度、面積與體積運算刻度",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val unitsList = listOf(
                            "cm" to "公分 (cm)",
                            "m" to "公尺 (m)",
                            "in" to "英吋 (in)",
                            "ft" to "英呎 (ft)"
                        )

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            unitsList.forEachIndexed { index, (unitCode, label) ->
                                val isSelected = selectedUnit == unitCode
                                SegmentedButton(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setSelectedUnit(unitCode)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = unitsList.size
                                    ),
                                    icon = {
                                        SegmentedButtonDefaults.Icon(active = isSelected)
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.testTag("unit_chip_$unitCode")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Group 1 Item 2 (MIDDLE): Screen Ruler Physical Calibration Item
                M3GroupedActionItem(
                    position = GroupedPosition.MIDDLE,
                    icon = Icons.Rounded.AspectRatio,
                    title = "螢幕尺實體精準度校準",
                    subtitle = "當前係數: ${String.format(java.util.Locale.US, "%.3fx", rulerCalibration)} · 微調兩側螢幕刻度",
                    trailingContent = {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setMode(1)
                                viewModel.setRulerCalibrationActive(true)
                                onNavigateBack()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("立即校準", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    onClick = {
                        viewModel.setMode(1)
                        viewModel.setRulerCalibrationActive(true)
                        onNavigateBack()
                    }
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Group 1 Item 3 (MIDDLE): High Definition Quality (Ultra HD)
                M3GroupedSwitchItem(
                    position = GroupedPosition.MIDDLE,
                    icon = Icons.Rounded.Hd,
                    title = "超高清晰度畫質 (Ultra HD / 1080p+)",
                    subtitle = "開啟高解析度相機感測器採樣與降噪銳化演算法",
                    checked = highDefinitionQualityEnabled,
                    onCheckedChange = { viewModel.setHighDefinitionQualityEnabled(it) },
                    testTag = "switch_high_definition"
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Group 1 Item 4 (MIDDLE): 60Hz High FPS Camera Preview
                M3GroupedSwitchItem(
                    position = GroupedPosition.MIDDLE,
                    icon = Icons.Rounded.Speed,
                    title = "相機高影格率預覽 (60 FPS)",
                    subtitle = "啟用相機高影格率通道，空間移動更加流暢",
                    checked = highFpsModeEnabled,
                    onCheckedChange = { viewModel.setHighFpsModeEnabled(it) },
                    testTag = "switch_high_fps"
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Group 1 Item 5 (MIDDLE): Smart Sensor Fusion Anti-Shake
                M3GroupedSwitchItem(
                    position = GroupedPosition.MIDDLE,
                    icon = Icons.Rounded.AutoFixHigh,
                    title = "智慧感應器防手震校正",
                    subtitle = "自動融合陀螺儀與重力向量消除微小手震飄移",
                    checked = sensorCorrectionEnabled,
                    onCheckedChange = { viewModel.setSensorCorrectionEnabled(it) },
                    testTag = "switch_sensor_fusion_master"
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Group 1 Item 6 (MIDDLE): Haptic Feedback
                M3GroupedSwitchItem(
                    position = GroupedPosition.MIDDLE,
                    icon = Icons.Rounded.Vibration,
                    title = "觸覺震動回饋",
                    subtitle = "錨點吸附、測量閉合與按鍵操作時提供微震反饋",
                    checked = vibrateOnAlign,
                    onCheckedChange = { viewModel.setVibrateOnAlignment(it) },
                    testTag = "switch_vibrate"
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Group 1 Item 7 (MIDDLE): Feature Point Cloud
                M3GroupedSwitchItem(
                    position = GroupedPosition.MIDDLE,
                    icon = Icons.Rounded.Grain,
                    title = "顯示特徵點雲",
                    subtitle = "即時在空間中渲染深度偵測特徵點",
                    checked = showPointCloud,
                    onCheckedChange = { viewModel.setShowPointCloud(it) },
                    testTag = "switch_point_cloud"
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Group 1 Item 8 (BOTTOM): Flashlight Brightness Slider with Presets
                M3GroupedItemContainer(position = GroupedPosition.BOTTOM) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.LightMode,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "手電筒預設補光亮度",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "夜間測量多段調光補光強度",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = "${(torchBrightness * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Slider(
                            value = torchBrightness,
                            onValueChange = {
                                viewModel.setTorchBrightness(context, it)
                            },
                            valueRange = 0.2f..1.0f,
                            steps = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.25f to "25%", 0.50f to "50%", 0.75f to "75%", 1.00f to "100%").forEach { (level, lbl) ->
                                val isSelected = kotlin.math.abs(torchBrightness - level) < 0.12f
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setTorchBrightness(context, level)
                                    },
                                    label = {
                                        Text(
                                            text = lbl,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ==========================================
                // 2. 數據維護與說明導覽 (Data & Welcome Guide)
                // ==========================================
                SettingsCategoryHeader(
                    title = "資料管理與說明導覽",
                    icon = Icons.Rounded.Security,
                    tint = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Group 4 Item 1 (TOP): Welcome Guide Action Item
                M3GroupedActionItem(
                    position = GroupedPosition.TOP,
                    icon = Icons.Rounded.AutoAwesome,
                    title = "歡迎導覽與功能指南",
                    subtitle = "快速回顧 AR 測量、直尺卡尺與空間計算功能介紹",
                    iconTint = Color(0xFF00E5FF),
                    iconContainerColor = Color(0xFF00E5FF).copy(alpha = 0.15f),
                    onClick = {
                        viewModel.openWelcomeScreen()
                        onNavigateBack()
                    }
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Group 4 Item 2 (MIDDLE): Clear Records Destructive Action Item
                M3GroupedActionItem(
                    position = GroupedPosition.MIDDLE,
                    icon = Icons.Rounded.DeleteForever,
                    title = "清除所有測量紀錄",
                    subtitle = "永久刪除所有歷史測量數據、截圖與標註",
                    iconTint = MaterialTheme.colorScheme.error,
                    iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showClearRecordsConfirmDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Group 4 Item 3 (BOTTOM): Feedback Email Item
                val feedbackEmail = "jeremy1030623@gmail.com"
                M3GroupedActionItem(
                    position = GroupedPosition.BOTTOM,
                    icon = Icons.Rounded.Mail,
                    title = "問題回饋與功能建議",
                    subtitle = "$feedbackEmail · 點擊發送回饋郵件",
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    trailingContent = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                ShareUtility.copyToClipboard(context, feedbackEmail, "已複製回饋電子信箱：$feedbackEmail")
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "複製電子信箱",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        ShareUtility.sendFeedbackEmail(context, feedbackEmail)
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // M3 Footer App Identity
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AR 尺子與空間測量儀",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Material 3 · Google ARCore & Camera2 Engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    // Material 3 Confirmation Dialog for Clearing All Records
    if (showClearRecordsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecordsConfirmDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "確認清除所有紀錄？",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "此操作無法復原，本機保存的所有歷史測量紀錄、長度、面積與截圖資料都將被永久刪除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.clearAllRecords()
                        showClearRecordsConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("確定清除", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearRecordsConfirmDialog = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * Material 3 Section Category Header
 */
@Composable
private fun SettingsCategoryHeader(
    title: String,
    icon: ImageVector,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = tint,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Material 3 Grouped Item Container supporting custom contents
 */
@Composable
private fun M3GroupedItemContainer(
    position: GroupedPosition,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = groupedItemShape(position),
        color = containerColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

/**
 * Material 3 Standard Switch Item using Grouped Shape
 */
@Composable
private fun M3GroupedSwitchItem(
    position: GroupedPosition,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = groupedItemShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconContainerColor,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCheckedChange(it)
                    },
                    enabled = enabled,
                    thumbContent = if (checked) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    } else null,
                    modifier = Modifier.testTag(testTag)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    role = Role.Switch
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(!checked)
                }
                .padding(vertical = 2.dp)
        )
    }
}

/**
 * Material 3 Standard Action Item using Grouped Shape
 */
@Composable
private fun M3GroupedActionItem(
    position: GroupedPosition,
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = groupedItemShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconContainerColor,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            trailingContent = trailingContent ?: {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(vertical = 2.dp)
        )
    }
}
