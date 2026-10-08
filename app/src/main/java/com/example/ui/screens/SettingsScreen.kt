package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
 * Material Design 3 簡約精美設定頁面 (Simplified & Elegant Settings Page)
 * 重新精簡分組，突出核心常用設定，進階演算法折疊收納，操作直覺流暢。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MeasureViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    BackHandler {
        onNavigateBack()
    }

    // Dialog States
    var showClearRecordsConfirmDialog by remember { mutableStateOf(false) }
    var showResetDefaultsConfirmDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    // Search query
    var searchQuery by remember { mutableStateOf("") }

    // ViewModel States
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val vibrateOnAlign by viewModel.vibrateOnAlignment.collectAsState()
    val showPointCloud by viewModel.showPointCloud.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val rulerCalibration by viewModel.rulerCalibration.collectAsState()
    val highFpsModeEnabled by viewModel.highFpsModeEnabled.collectAsState()
    val highDefinitionQualityEnabled by viewModel.highDefinitionQualityEnabled.collectAsState()
    val torchBrightness by viewModel.torchBrightness.collectAsState()

    // Battery & Power Optimization States
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isPowerSaveMode by viewModel.isPowerSaveMode.collectAsState()
    val autoBatteryProfileEnabled by viewModel.autoBatteryProfileEnabled.collectAsState()
    val currentAppliedProfile by viewModel.currentAppliedProfile.collectAsState()

    // Advanced Sensor Fusion & Hardware States
    val sensorCorrectionEnabled by viewModel.sensorCorrectionEnabled.collectAsState()
    val antiJitterEnabled by viewModel.antiJitterEnabled.collectAsState()
    val gravityAlignmentEnabled by viewModel.gravityAlignmentEnabled.collectAsState()
    val barometerFusionEnabled by viewModel.barometerFusionEnabled.collectAsState()
    val jerkRejectionEnabled by viewModel.jerkRejectionEnabled.collectAsState()
    val orthogonalSnapEnabled by viewModel.orthogonalSnapEnabled.collectAsState()
    val multiSampleAveragingEnabled by viewModel.multiSampleAveragingEnabled.collectAsState()
    val coplanarProjectionEnabled by viewModel.coplanarProjectionEnabled.collectAsState()
    val isVulkanGraphicsEnabled by viewModel.isVulkanGraphicsEnabled.collectAsState()
    val isVulkanAiEnabled by viewModel.isVulkanAiEnabled.collectAsState()

    // UI Styles
    val reticleStyle by viewModel.reticleStyle.collectAsState()
    val lineThickness by viewModel.lineThickness.collectAsState()
    val cameraAspectRatio by viewModel.cameraAspectRatio.collectAsState()

    val currentLanguageDisplay = remember(currentLanguage) {
        TranslationManager.supportedLanguages.find { it.code == currentLanguage }?.name ?: currentLanguage
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "設定",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                            contentDescription = "返回測量"
                        )
                    }
                },
                actions = {
                    // Language switcher button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showLanguageDialog = true
                        },
                        modifier = Modifier.testTag("settings_language_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = "切換語言",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Reset defaults button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showResetDefaultsConfirmDialog = true
                        },
                        modifier = Modifier.testTag("settings_reset_defaults_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "重設預設值",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                // Quick Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "搜尋設定 (如：單位、畫質、準心、震動...)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "清除搜尋",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_search_field")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // 1. 快速情境設定 (Quick Presets)
                // ==========================================
                if (searchQuery.isBlank()) {
                    Text(
                        text = "快速情境模式",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickPresetChip(
                            title = "🎯 專業精密",
                            subtitle = "mm · 60fps · 抗抖",
                            isSelected = selectedUnit == "mm" && antiJitterEnabled,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.applyMeasurementProfile("PRO_PRECISION")
                                Toast.makeText(context, "已切換至「專業精密」模式", Toast.LENGTH_SHORT).show()
                            }
                        )

                        QuickPresetChip(
                            title = "⚖️ 智慧平衡",
                            subtitle = "cm · 60fps · 點雲",
                            isSelected = selectedUnit == "cm" && highFpsModeEnabled && antiJitterEnabled,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.applyMeasurementProfile("BALANCED")
                                Toast.makeText(context, "已切換至「智慧平衡」模式", Toast.LENGTH_SHORT).show()
                            }
                        )

                        QuickPresetChip(
                            title = "⚡ 省電模式",
                            subtitle = "標準幀率 · 輕量",
                            isSelected = !highFpsModeEnabled && !antiJitterEnabled,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.applyMeasurementProfile("POWER_SAVER")
                                Toast.makeText(context, "已切換至「省電模式」", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // ⚡ 電量與省電模式自動情境調優 (極簡化版)
                // ==========================================
                if (searchQuery.isBlank() || matchesSearch(searchQuery, listOf("電量", "省電", "電池", "精密", "平衡", "自動", "調優"))) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setAutoBatteryProfileEnabled(!autoBatteryProfileEnabled)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isPowerSaveMode) Icons.Rounded.BatterySaver else Icons.Rounded.BatteryChargingFull,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "智能自動電量調優",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "電量 $batteryLevel% · ${if (isPowerSaveMode) "省電模式開啟" else "一般模式"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = autoBatteryProfileEnabled,
                                    onCheckedChange = { viewModel.setAutoBatteryProfileEnabled(it) },
                                    modifier = Modifier.testTag("auto_battery_profile_switch")
                                )
                            }

                            if (autoBatteryProfileEnabled) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(12.dp))

                                // Minimal 3-Stage Profile Pills Bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val isPro = currentAppliedProfile == "PRO_PRECISION"
                                    val isPower = currentAppliedProfile == "POWER_SAVER"
                                    val isBalanced = !isPro && !isPower

                                    SimpleProfileStatusChip(
                                        label = "🎯 專業極限",
                                        subtitle = "> 59%",
                                        isActive = isPro,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SimpleProfileStatusChip(
                                        label = "⚖️ 智慧平衡",
                                        subtitle = "45% ~ 59%",
                                        isActive = isBalanced,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SimpleProfileStatusChip(
                                        label = "⚡ 長效省電",
                                        subtitle = "< 45%",
                                        isActive = isPower,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ==========================================
                // 2. 測量偏好 (Measurement Preferences)
                // ==========================================
                if (matchesSearch(searchQuery, listOf("單位", "公分", "公尺", "英吋", "英呎", "震動", "點雲", "校準", "尺"))) {
                    SectionHeader(
                        title = "測量偏好",
                        icon = Icons.Rounded.Straighten,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group Container for Measurement Preferences
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Unit Selector
                            Text(
                                text = "預設測量單位",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val unitsList = listOf(
                                "cm" to "公分 (cm)",
                                "m" to "公尺 (m)",
                                "in" to "英吋 (in)",
                                "ft" to "英呎 (ft)"
                            )

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        modifier = Modifier.testTag("unit_chip_$unitCode")
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Haptic Vibration Switch
                            SettingSwitchRow(
                                icon = Icons.Rounded.Vibration,
                                title = "觸覺微震動回饋",
                                subtitle = "對齊、吸附與按鍵操作時震動",
                                checked = vibrateOnAlign,
                                onCheckedChange = { viewModel.setVibrateOnAlignment(it) },
                                testTag = "switch_vibrate"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Point Cloud Switch
                            SettingSwitchRow(
                                icon = Icons.Rounded.Grain,
                                title = "顯示空間特徵點雲",
                                subtitle = "即時在畫面中可視化空間深度點",
                                checked = showPointCloud,
                                onCheckedChange = { viewModel.setShowPointCloud(it) },
                                testTag = "switch_point_cloud"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Screen Ruler Calibration Action
                            SettingActionRow(
                                icon = Icons.Rounded.AspectRatio,
                                title = "螢幕尺精度校準",
                                subtitle = "當前係數: ${String.format(java.util.Locale.US, "%.3fx", rulerCalibration)}",
                                actionLabel = "校準",
                                onClick = {
                                    viewModel.setMode(1)
                                    viewModel.setRulerCalibrationActive(true)
                                    onNavigateBack()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 3. 相機與顯示 (Camera & Display)
                // ==========================================
                if (matchesSearch(searchQuery, listOf("相機", "畫質", "fps", "清晰", "60", "手電筒", "補光", "比例"))) {
                    SectionHeader(
                        title = "相機與顯示",
                        icon = Icons.Rounded.CameraAlt,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 60 FPS Camera Switch
                            SettingSwitchRow(
                                icon = Icons.Rounded.Speed,
                                title = "相機 60 FPS 高影格率",
                                subtitle = "鏡頭畫面更流暢滑順",
                                checked = highFpsModeEnabled,
                                onCheckedChange = { viewModel.setHighFpsModeEnabled(it) },
                                testTag = "switch_high_fps"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Ultra HD Switch
                            SettingSwitchRow(
                                icon = Icons.Rounded.Hd,
                                title = "超高清晰度畫質 (Ultra HD)",
                                subtitle = "提升相機解析度與邊緣細節",
                                checked = highDefinitionQualityEnabled,
                                onCheckedChange = { viewModel.setHighDefinitionQualityEnabled(it) },
                                testTag = "switch_high_definition"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Aspect Ratio
                            Text(
                                text = "相機畫面比例",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val ratios = listOf(
                                "4_3" to "4:3",
                                "16_9" to "16:9",
                                "1_1" to "1:1",
                                "FULL" to "全螢幕"
                            )

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                ratios.forEachIndexed { index, (key, label) ->
                                    val isSelected = cameraAspectRatio == key
                                    SegmentedButton(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setCameraAspectRatio(key)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = ratios.size
                                        ),
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Torch brightness
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LightMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "手電筒預設補光亮度",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "${(torchBrightness * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = torchBrightness,
                                onValueChange = { viewModel.setTorchBrightness(context, it) },
                                valueRange = 0.2f..1.0f,
                                steps = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 4. 視覺與外觀樣式 (Visual & Styles)
                // ==========================================
                if (matchesSearch(searchQuery, listOf("準心", "樣式", "線條", "粗細", "語言", "風格", "ui", "外觀"))) {
                    SectionHeader(
                        title = "視覺與外觀",
                        icon = Icons.Rounded.Palette,
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Language
                            SettingActionRow(
                                icon = Icons.Rounded.Language,
                                title = "應用程式語言",
                                subtitle = currentLanguageDisplay,
                                actionLabel = "切換",
                                onClick = { showLanguageDialog = true }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Reticle Style
                            Text(
                                text = "AR 瞄準準心樣式",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val reticles = listOf(
                                "DOUBLE_RING" to "雙環",
                                "PRECISION_CROSSHAIR" to "十字",
                                "TARGET_BOX" to "網格",
                                "MINIMAL_DOT" to "微點"
                            )

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                reticles.forEachIndexed { index, (code, label) ->
                                    val isSelected = reticleStyle == code
                                    SegmentedButton(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setReticleStyle(code)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = reticles.size
                                        ),
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        modifier = Modifier.testTag("reticle_btn_$code")
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Line Thickness
                            Text(
                                text = "測量標註線條粗細",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val thicknesses = listOf(
                                2.0f to "細緻",
                                3.5f to "標準",
                                5.0f to "粗體"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                thicknesses.forEach { (thick, label) ->
                                    val isSelected = kotlin.math.abs(lineThickness - thick) < 0.2f
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setLineThickness(thick)
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("thickness_chip_${thick.toInt()}")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 5. 智慧輔助與進階演算法 (Smart Assist & Advanced)
                // ==========================================
                if (matchesSearch(searchQuery, listOf("防手震", "吸附", "正交", "重力", "氣壓", "同平面", "vulkan", "硬體", "加速", "感應器"))) {
                    SectionHeader(
                        title = "智慧輔助與演算法",
                        icon = Icons.Rounded.AutoFixHigh,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Master Sensor Fusion
                            SettingSwitchRow(
                                icon = Icons.Rounded.Sensors,
                                title = "智慧感應器防手震融合",
                                subtitle = "融合陀螺儀與加速度計抑制手震漂移",
                                checked = sensorCorrectionEnabled,
                                onCheckedChange = { viewModel.setSensorCorrectionEnabled(it) },
                                testTag = "switch_sensor_fusion_master"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Orthogonal Snap
                            SettingSwitchRow(
                                icon = Icons.Rounded.Architecture,
                                title = "90° / 180° 正交吸附",
                                subtitle = "接近水平或垂直角度時自動磁吸對齊",
                                checked = orthogonalSnapEnabled,
                                onCheckedChange = { viewModel.setOrthogonalSnapEnabled(it) },
                                testTag = "switch_orthogonal_snap"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Gravity Alignment
                            SettingSwitchRow(
                                icon = Icons.Rounded.VerticalAlignBottom,
                                title = "重力垂直投影校正",
                                subtitle = "自動校正牆面高度與垂直面測量",
                                checked = gravityAlignmentEnabled,
                                onCheckedChange = { viewModel.setGravityAlignmentEnabled(it) },
                                testTag = "switch_gravity_alignment"
                            )

                            // Collapsible Advanced Settings
                            AnimatedVisibility(visible = showAdvancedSettings || searchQuery.isNotEmpty()) {
                                Column {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    SettingSwitchRow(
                                        icon = Icons.Rounded.Compress,
                                        title = "氣壓計相對高程融合",
                                        subtitle = "利用氣壓變化輔助垂直高差精準度",
                                        checked = barometerFusionEnabled,
                                        onCheckedChange = { viewModel.setBarometerFusionEnabled(it) },
                                        testTag = "switch_barometer_fusion"
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    SettingSwitchRow(
                                        icon = Icons.Rounded.FilterTiltShift,
                                        title = "多樣本空間取樣平滑",
                                        subtitle = "連續多影格平均提高微距精度",
                                        checked = multiSampleAveragingEnabled,
                                        onCheckedChange = { viewModel.setMultiSampleAveragingEnabled(it) },
                                        testTag = "switch_multisample_averaging"
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    SettingSwitchRow(
                                        icon = Icons.Rounded.Layers,
                                        title = "同平面多邊形約束",
                                        subtitle = "將多點約束在同一平面提升面積精度",
                                        checked = coplanarProjectionEnabled,
                                        onCheckedChange = { viewModel.setCoplanarProjectionEnabled(it) },
                                        testTag = "switch_coplanar_projection"
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    SettingSwitchRow(
                                        icon = Icons.Rounded.Bolt,
                                        title = "Vulkan 1.3 空間網格硬體加速",
                                        subtitle = "GPU 批次頂點渲染與繪圖管線",
                                        checked = isVulkanGraphicsEnabled,
                                        onCheckedChange = { viewModel.setVulkanGraphicsEnabled(it) },
                                        testTag = "switch_vulkan_graphics"
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    SettingSwitchRow(
                                        icon = Icons.Rounded.Psychology,
                                        title = "Vulkan TFLite GPU 視覺推論",
                                        subtitle = "3D 物件辨識 GPU 加速推論",
                                        checked = isVulkanAiEnabled,
                                        onCheckedChange = { viewModel.setVulkanAiEnabled(it) },
                                        testTag = "switch_vulkan_ai"
                                    )
                                }
                            }

                            if (searchQuery.isEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showAdvancedSettings = !showAdvancedSettings
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (showAdvancedSettings) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (showAdvancedSettings) "收起進階演算法與硬體選項" else "展開更多進階演算法與硬體加速",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 6. 系統與支援 (System & Support)
                // ==========================================
                if (matchesSearch(searchQuery, listOf("導覽", "重設", "清除", "報告", "回饋", "信箱", "關於"))) {
                    SectionHeader(
                        title = "系統與支援",
                        icon = Icons.Rounded.Info,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Welcome Guide
                            SettingActionRow(
                                icon = Icons.Rounded.AutoAwesome,
                                title = "功能指南與歡迎導覽",
                                subtitle = "快速查看功能操作技巧",
                                actionLabel = "查看",
                                onClick = {
                                    viewModel.openWelcomeScreen()
                                    onNavigateBack()
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Feedback Email
                            val feedbackEmail = "jeremy1030623@gmail.com"
                            SettingActionRow(
                                icon = Icons.Rounded.Mail,
                                title = "意見回饋",
                                subtitle = feedbackEmail,
                                actionLabel = "寫信",
                                onClick = { ShareUtility.sendFeedbackEmail(context, feedbackEmail) }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Copy Settings Report
                            SettingActionRow(
                                icon = Icons.Rounded.Description,
                                title = "複製系統診斷報告",
                                subtitle = "將當前設定與環境資訊複製到剪貼簿",
                                actionLabel = "複製",
                                onClick = {
                                    val report = viewModel.getSettingsSummaryReport()
                                    ShareUtility.copyToClipboard(context, report, "已複製系統設定報告")
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Reset Defaults
                            SettingActionRow(
                                icon = Icons.Rounded.RestartAlt,
                                title = "恢復原廠預設設定",
                                subtitle = "重設所有設定值（保留測量紀錄）",
                                actionLabel = "重設",
                                isDestructive = true,
                                onClick = { showResetDefaultsConfirmDialog = true }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Clear Records
                            SettingActionRow(
                                icon = Icons.Rounded.DeleteForever,
                                title = "清除所有測量紀錄",
                                subtitle = "永久刪除所有歷史測量數據與截圖",
                                actionLabel = "清除",
                                isDestructive = true,
                                onClick = { showClearRecordsConfirmDialog = true }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // App Info Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "相機 AR 測量儀 Pro",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Google ARCore & Camera2 Engine · Vulkan 1.3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    // Language Dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { newLang ->
                viewModel.setLanguage(newLang)
                showLanguageDialog = false
            },
            onDismissRequest = { showLanguageDialog = false },
            onOpenSystemSettings = {
                viewModel.openSystemLanguageSettings(context)
            }
        )
    }

    // Reset Defaults Confirmation Dialog
    if (showResetDefaultsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetDefaultsConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "恢復原廠預設值？",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "這會將所有單位、準心樣式、相機與演算法參數重設為預設值。已儲存的測量紀錄不會被刪除。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.resetAllSettingsToDefault()
                        showResetDefaultsConfirmDialog = false
                        Toast.makeText(context, "已恢復原廠預設值", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("重設")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetDefaultsConfirmDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // Clear Records Confirmation Dialog
    if (showClearRecordsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecordsConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "確認清除所有紀錄？",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "此操作無法復原，本機所有測量歷史數據與截圖將被永久刪除。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.clearAllRecords()
                        showClearRecordsConfirmDialog = false
                        Toast.makeText(context, "已清除所有測量紀錄", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("確定清除")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearRecordsConfirmDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * Check if search query matches any keywords
 */
private fun matchesSearch(query: String, keywords: List<String>): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return keywords.any { it.lowercase().contains(q) || q.contains(it.lowercase()) }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun QuickPresetChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .widthIn(min = 135.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(it)
            },
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDestructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FilledTonalButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            colors = if (isDestructive) ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) else ButtonDefaults.filledTonalButtonColors(),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(text = actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SimpleProfileStatusChip(
    label: String,
    subtitle: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                fontSize = 10.sp
            )
        }
    }
}
