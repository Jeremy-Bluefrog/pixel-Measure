package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
 * Comprehensive system configuration, pro presets, sensor fusion engine,
 * hardware acceleration (Vulkan/AI), UI personalization, and system diagnostics.
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

    // Dialog States
    var showClearRecordsConfirmDialog by remember { mutableStateOf(false) }
    var showResetDefaultsConfirmDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Search and Category Filter State
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    // ViewModel Collected States
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val vibrateOnAlign by viewModel.vibrateOnAlignment.collectAsState()
    val showPointCloud by viewModel.showPointCloud.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val rulerCalibration by viewModel.rulerCalibration.collectAsState()
    val highFpsModeEnabled by viewModel.highFpsModeEnabled.collectAsState()
    val highDefinitionQualityEnabled by viewModel.highDefinitionQualityEnabled.collectAsState()
    val torchBrightness by viewModel.torchBrightness.collectAsState()

    // Sensor Fusion States
    val sensorCorrectionEnabled by viewModel.sensorCorrectionEnabled.collectAsState()
    val antiJitterEnabled by viewModel.antiJitterEnabled.collectAsState()
    val gravityAlignmentEnabled by viewModel.gravityAlignmentEnabled.collectAsState()
    val barometerFusionEnabled by viewModel.barometerFusionEnabled.collectAsState()
    val jerkRejectionEnabled by viewModel.jerkRejectionEnabled.collectAsState()
    val proximityContactEnabled by viewModel.proximityContactEnabled.collectAsState()
    val stereoParallaxEnabled by viewModel.stereoParallaxEnabled.collectAsState()
    val multiSampleAveragingEnabled by viewModel.multiSampleAveragingEnabled.collectAsState()
    val coplanarProjectionEnabled by viewModel.coplanarProjectionEnabled.collectAsState()
    val orthogonalSnapEnabled by viewModel.orthogonalSnapEnabled.collectAsState()
    val rawDepthConfidenceEnabled by viewModel.rawDepthConfidenceEnabled.collectAsState()
    val rawDepthConfidenceThreshold by viewModel.rawDepthConfidenceThreshold.collectAsState()
    val scaleCalibrationFactor by viewModel.scaleCalibrationFactor.collectAsState()
    val sensorTelemetry by viewModel.sensorTelemetry.collectAsState()

    // Vulkan & AI Hardware States
    val isVulkanGraphicsEnabled by viewModel.isVulkanGraphicsEnabled.collectAsState()
    val isVulkanAiEnabled by viewModel.isVulkanAiEnabled.collectAsState()
    val vulkanAiMetrics by viewModel.vulkanAiMetrics.collectAsState()

    // UI Customization States
    val reticleStyle by viewModel.reticleStyle.collectAsState()
    val lineThickness by viewModel.lineThickness.collectAsState()
    val hudStyle by viewModel.hudStyle.collectAsState()
    val arFontSize by viewModel.arFontSize.collectAsState()
    val rulerTheme by viewModel.rulerTheme.collectAsState()
    val badgeOpacity by viewModel.badgeOpacity.collectAsState()
    val gridOverlayStyle by viewModel.gridOverlayStyle.collectAsState()
    val cameraAspectRatio by viewModel.cameraAspectRatio.collectAsState()
    val useDisplayP3ColorSpace by viewModel.useDisplayP3ColorSpace.collectAsState()
    val isLensDirtWarningEnabled by viewModel.isLensDirtWarningEnabled.collectAsState()
    val uiButtonScale by viewModel.uiButtonScale.collectAsState()

    // Language Name Helper
    val currentLanguageDisplay = remember(currentLanguage) {
        TranslationManager.supportedLanguages.find { it.code == currentLanguage }?.name
            ?: currentLanguage
    }

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
                            text = "設定與系統調校",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "情境預設 · 感應器融合 · 硬體加速 · 個人化",
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
                actions = {
                    // Quick Language Switcher Button
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

                    // Share Settings Report Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val report = viewModel.getSettingsSummaryReport()
                            ShareUtility.copyToClipboard(context, report, "已複製系統設定參數報告至剪貼簿")
                        },
                        modifier = Modifier.testTag("settings_share_report_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = "複製設定報告",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Factory Reset Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showResetDefaultsConfirmDialog = true
                        },
                        modifier = Modifier.testTag("settings_reset_defaults_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "重設原廠設定",
                            tint = MaterialTheme.colorScheme.error
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            ) {
                // ==========================================
                // 0. 搜尋列與分類導覽標籤 (Search & Categories)
                // ==========================================
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "搜尋設定項目 (如：單位、防手震、Vulkan、相機...)",
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

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Chips Row
                val categories = listOf(
                    "ALL" to "全部設定",
                    "PRESETS" to "🚀 情境預設",
                    "MEASURE" to "📏 測量偏好",
                    "SENSOR" to "🧭 感應器融合",
                    "VULKAN" to "⚡ Vulkan & AI",
                    "UI" to "🎨 個人化風格",
                    "DATA" to "💾 資料與系統"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { (catKey, catLabel) ->
                        val isSelected = selectedCategory == catKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedCategory = catKey
                            },
                            label = {
                                Text(
                                    text = catLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 1. 專業情境模式預設 (Pro Scenario Presets)
                // ==========================================
                if (shouldShowSection(selectedCategory, "PRESETS", searchQuery, listOf("情境", "預設", "精密", "省電", "平衡", "preset", "pro"))) {
                    SettingsCategoryHeader(
                        title = "專業情境模式 (Pro Presets)",
                        icon = Icons.Rounded.AutoAwesome,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PresetScenarioCards(
                        selectedUnit = selectedUnit,
                        highFps = highFpsModeEnabled,
                        antiJitter = antiJitterEnabled,
                        onApplyProfile = { profileKey ->
                            viewModel.applyMeasurementProfile(profileKey)
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 2. 測量與空間核心偏好 (Measurement Core Preferences)
                // ==========================================
                if (shouldShowSection(selectedCategory, "MEASURE", searchQuery, listOf("測量", "單位", "刻度", "公分", "公尺", "英吋", "英呎", "尺", "校準", "清晰", "畫質", "影格", "fps", "震動", "點雲", "手電筒", "補光"))) {
                    SettingsCategoryHeader(
                        title = "測量與空間核心偏好",
                        icon = Icons.Rounded.Straighten,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group 1 Item 1 (TOP): Unit Selection
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

                    // Group 1 Item 2 (MIDDLE): Screen Ruler Calibration Item
                    M3GroupedActionItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.AspectRatio,
                        title = "螢幕尺實體精準度校準",
                        subtitle = "當前係數: ${String.format(java.util.Locale.US, "%.3fx", rulerCalibration)} · 支援毫米微調",
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

                    // Group 1 Item 3 (MIDDLE): AR Scale Calibration Factor Slider
                    M3GroupedItemContainer(position = GroupedPosition.MIDDLE) {
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
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Tune,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "空間深度尺度校正微調",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "微調鏡頭焦距與實體空間比例 (0.90x ~ 1.10x)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.4f", scaleCalibrationFactor)}x",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (kotlin.math.abs(scaleCalibrationFactor - 1.0f) > 0.0005f) {
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.setScaleCalibrationFactor(1.0000f)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Replay,
                                                contentDescription = "重設為 1.0000x",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Slider(
                                value = scaleCalibrationFactor,
                                onValueChange = { viewModel.setScaleCalibrationFactor(it) },
                                valueRange = 0.9000f..1.1000f,
                                steps = 39,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 1 Item 4 (MIDDLE): High Definition Quality (Ultra HD)
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

                    // Group 1 Item 5 (MIDDLE): 60Hz High FPS Camera Preview
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

                    // Group 1 Item 6 (MIDDLE): Feature Point Cloud
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.Grain,
                        title = "顯示空間特徵點雲",
                        subtitle = "即時在空間中渲染深度偵測特徵點與平面幾何",
                        checked = showPointCloud,
                        onCheckedChange = { viewModel.setShowPointCloud(it) },
                        testTag = "switch_point_cloud"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 1 Item 7 (MIDDLE): Haptic Feedback
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.Vibration,
                        title = "觸覺微震動回饋",
                        subtitle = "錨點吸附、測量閉合與按鍵操作時提供微震反饋",
                        checked = vibrateOnAlign,
                        onCheckedChange = { viewModel.setVibrateOnAlignment(it) },
                        testTag = "switch_vibrate"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 1 Item 8 (BOTTOM): Flashlight Brightness Slider
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

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 3. 感應器融合與演算法高階調校 (Sensor Fusion & Calibration)
                // ==========================================
                if (shouldShowSection(selectedCategory, "SENSOR", searchQuery, listOf("感應器", "融合", "防手震", "重力", "氣壓", "陀螺儀", "正交", "吸附", "同平面", "視差", "零接觸", "置信度", "raw depth", "sensor", "depth"))) {
                    SettingsCategoryHeader(
                        title = "感應器融合與高階演算法調校",
                        icon = Icons.Rounded.Sensors,
                        tint = Color(0xFF00E5FF)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sensor Fusion Live Telemetry Status Card
                    SensorTelemetryCard(
                        telemetry = sensorTelemetry,
                        onCalibrateSensors = {
                            viewModel.calibrateSensors()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group 2 Item 1 (TOP): Master Sensor Fusion Switch
                    M3GroupedSwitchItem(
                        position = GroupedPosition.TOP,
                        icon = Icons.Rounded.AutoFixHigh,
                        title = "智慧感應器防手震融合 (Master)",
                        subtitle = "多感測器即時融合以抑制手震與漂移",
                        checked = sensorCorrectionEnabled,
                        onCheckedChange = { viewModel.setSensorCorrectionEnabled(it) },
                        testTag = "switch_sensor_fusion_master"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 2 Item 2 (MIDDLE): Orthogonal Snap
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.Architecture,
                        title = "90° / 180° 正交垂直水平自動吸附",
                        subtitle = "測量線段接近水平、垂直或直角時自動磁吸鎖定",
                        checked = orthogonalSnapEnabled,
                        onCheckedChange = { viewModel.setOrthogonalSnapEnabled(it) },
                        testTag = "switch_orthogonal_snap"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 2 Item 3 (MIDDLE): Jerk Rejection
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.Speed,
                        title = "加速度突波拒斥 (Jerk Filter)",
                        subtitle = "消除手部突發晃動或步態震動產生的跳點",
                        checked = jerkRejectionEnabled,
                        onCheckedChange = { viewModel.setJerkRejectionEnabled(it) },
                        testTag = "switch_jerk_rejection"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 2 Item 4 (MIDDLE): Gravity Alignment
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.VerticalAlignBottom,
                        title = "重力向量垂直投影校正",
                        subtitle = "利用機身重力加速度計校正高度測量與垂直牆面",
                        checked = gravityAlignmentEnabled,
                        onCheckedChange = { viewModel.setGravityAlignmentEnabled(it) },
                        testTag = "switch_gravity_alignment"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 2 Item 5 (MIDDLE): Barometer Fusion
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.Compress,
                        title = "氣壓計相對高程融合",
                        subtitle = "融合氣壓感測器微氣壓變化輔助垂直高度精準度",
                        checked = barometerFusionEnabled,
                        onCheckedChange = { viewModel.setBarometerFusionEnabled(it) },
                        testTag = "switch_barometer_fusion"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 2 Item 6 (MIDDLE): Multi-Sample Averaging
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.FilterTiltShift,
                        title = "多樣本空間取樣平滑濾波",
                        subtitle = "連續多影格空間採樣取平均，大幅提升微距精度",
                        checked = multiSampleAveragingEnabled,
                        onCheckedChange = { viewModel.setMultiSampleAveragingEnabled(it) },
                        testTag = "switch_multisample_averaging"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 2 Item 7 (MIDDLE): Coplanar Projection
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.Layers,
                        title = "同平面投影校正 (Coplanar Constraint)",
                        subtitle = "將多邊形測量錨點約束在同一空間平面以提升面積精度",
                        checked = coplanarProjectionEnabled,
                        onCheckedChange = { viewModel.setCoplanarProjectionEnabled(it) },
                        testTag = "switch_coplanar_projection"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 2 Item 8 (MIDDLE): Stereo Parallax Scale
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.CenterFocusStrong,
                        title = "雙鏡頭立體視差校正",
                        subtitle = "結合超廣角/長焦鏡頭基線視差校準物體深度",
                        checked = stereoParallaxEnabled,
                        onCheckedChange = { viewModel.setStereoParallaxEnabled(it) },
                        testTag = "switch_stereo_parallax"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 2 Item 9 (MIDDLE): Proximity Zero-Contact
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.TouchApp,
                        title = "近距離零接觸校正 (Proximity Contact)",
                        subtitle = "手機緊貼物體測量時自動補償機身厚度與鏡頭偏差",
                        checked = proximityContactEnabled,
                        onCheckedChange = { viewModel.setProximityContactEnabled(it) },
                        testTag = "switch_proximity_contact"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 2 Item 10 (BOTTOM): Raw Depth Confidence & Threshold
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
                                        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.BlurCircular,
                                                contentDescription = null,
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Pixel Raw Depth 置信度過濾",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "過濾低置信度深度圖雜訊點 (當前門檻: $rawDepthConfidenceThreshold%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Switch(
                                    checked = rawDepthConfidenceEnabled,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setRawDepthConfidenceEnabled(it)
                                    },
                                    modifier = Modifier.testTag("switch_raw_depth")
                                )
                            }

                            if (rawDepthConfidenceEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "置信度門檻 (Confidence Threshold)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "$rawDepthConfidenceThreshold%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Slider(
                                    value = rawDepthConfidenceThreshold.toFloat(),
                                    onValueChange = { viewModel.setRawDepthConfidenceThreshold(it.toInt()) },
                                    valueRange = 15f..85f,
                                    steps = 13,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 4. Vulkan 1.3 硬體加速與 AI 視覺推論 (Hardware & AI)
                // ==========================================
                if (shouldShowSection(selectedCategory, "VULKAN", searchQuery, listOf("vulkan", "硬體", "加速", "gpu", "ai", "推論", "tflite", "渲染", "fps", "效能"))) {
                    SettingsCategoryHeader(
                        title = "Vulkan 1.3 硬體加速與 AI 視覺推論",
                        icon = Icons.Rounded.Bolt,
                        tint = Color(0xFFFF9800)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Vulkan AI Metrics Telemetry Card
                    VulkanAiMetricsCard(metrics = vulkanAiMetrics)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group 3 Item 1 (TOP): Vulkan 3D Graphics Pipeline & Batch Rendering
                    M3GroupedSwitchItem(
                        position = GroupedPosition.TOP,
                        icon = Icons.Rounded.ViewInAr,
                        title = "Vulkan 3D 空間網格與頂點批次渲染",
                        subtitle = "啟用 Vulkan 1.3 / OpenGL ES 頂點批次 (Batch Rendering) 與 RenderThread 硬件圖層加速",
                        checked = isVulkanGraphicsEnabled,
                        iconTint = Color(0xFFFF9800),
                        iconContainerColor = Color(0xFFFF9800).copy(alpha = 0.15f),
                        onCheckedChange = { viewModel.setVulkanGraphicsEnabled(it) },
                        testTag = "switch_vulkan_graphics"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 3 Item 2 (MIDDLE): Vulkan TFLite GPU AI Bridge
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.Psychology,
                        title = "Vulkan TFLite GPU 視覺推論加速",
                        subtitle = "MobileSAM / Objectron 3D 深度視覺模型 Vulkan GPU 運算",
                        checked = isVulkanAiEnabled,
                        iconTint = Color(0xFFFF9800),
                        iconContainerColor = Color(0xFFFF9800).copy(alpha = 0.15f),
                        onCheckedChange = { viewModel.setVulkanAiEnabled(it) },
                        testTag = "switch_vulkan_ai"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 3 Item 3 (BOTTOM): Background Sensor & Frame Pipeline Info
                    M3GroupedActionItem(
                        position = GroupedPosition.BOTTOM,
                        icon = Icons.Rounded.Speed,
                        title = "背景線程化與事件降頻架構",
                        subtitle = "環形緩衝 (Ring Buffer) 100~200Hz 批次融合 · 鏡頭分析 Dispatchers.Default 2.5s 節流",
                        iconTint = Color(0xFF10B981),
                        iconContainerColor = Color(0xFF10B981).copy(alpha = 0.15f),
                        trailingContent = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "極致流暢",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        },
                        onClick = {
                            Toast.makeText(context, "已套用全鏈路流暢度架構：GPU批次渲染、RenderThread圖層、零GC緩存與背景降頻調度", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 5. 自訂 UI 與個人化風格 (Custom UI & Personalization)
                // ==========================================
                if (shouldShowSection(selectedCategory, "UI", searchQuery, listOf("ui", "外觀", "個人化", "風格", "準心", "線條", "粗細", "hud", "字體", "比例", "4:3", "16:9", "色域", "p3", "鏡頭髒污", "網格", "透明度", "直尺主題", "語言", "language"))) {
                    SettingsCategoryHeader(
                        title = "自訂 UI 與個人化風格",
                        icon = Icons.Rounded.Palette,
                        tint = MaterialTheme.colorScheme.tertiary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group 4 Item 1 (TOP): Language Selection Action Item
                    M3GroupedActionItem(
                        position = GroupedPosition.TOP,
                        icon = Icons.Rounded.Language,
                        title = "應用程式語言 (Language)",
                        subtitle = "當前語言: $currentLanguageDisplay · 支援 90+ 種語言",
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        trailingContent = {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = currentLanguageDisplay,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        },
                        onClick = {
                            showLanguageDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 2 (MIDDLE): Customizable Button Size
                    M3GroupedItemContainer(position = GroupedPosition.MIDDLE) {
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
                                            imageVector = Icons.Rounded.SmartButton,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "AR 操作按鈕尺寸",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "自訂 AR 畫面中加點 FAB、快門按鈕與工具甲板縮放大小",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val scales = listOf(
                                0.85f to "緊湊 (85%)",
                                1.00f to "標準 (100%)",
                                1.15f to "放大 (115%)",
                                1.30f to "特大 (130%)"
                            )

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                scales.forEachIndexed { index, (scaleVal, label) ->
                                    SegmentedButton(
                                        selected = kotlin.math.abs(uiButtonScale - scaleVal) < 0.05f,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setUiButtonScale(scaleVal)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = scales.size),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 3 (MIDDLE): Camera Aspect Ratio Selector
                    M3GroupedItemContainer(position = GroupedPosition.MIDDLE) {
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
                                            imageVector = Icons.Rounded.AspectRatio,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "相片與影片長寬比",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "設定快門拍照與錄影裁切比例 (支援 4:3 / 16:9 / 1:1 / 全螢幕)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val ratios = listOf(
                                "4_3" to "4:3 標準",
                                "16_9" to "16:9 寬螢幕",
                                "1_1" to "1:1 正方形",
                                "FULL" to "全螢幕"
                            )

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ratios.forEachIndexed { index, (key, label) ->
                                    SegmentedButton(
                                        selected = cameraAspectRatio == key,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setCameraAspectRatio(key)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ratios.size),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 4 (MIDDLE): Display P3 Wide Color Gamut
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.Palette,
                        title = "Display P3 廣色域相片格式",
                        subtitle = "快門拍攝採用 Display P3 廣色域，色彩呈現更加豐富鮮豔",
                        checked = useDisplayP3ColorSpace,
                        onCheckedChange = { viewModel.setUseDisplayP3ColorSpace(it) },
                        testTag = "switch_display_p3"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 5 (MIDDLE): Lens Dirt Warning
                    M3GroupedSwitchItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.CleaningServices,
                        title = "鏡頭髒污與指紋自動檢測警示",
                        subtitle = "即時分析相機鏡頭模糊與油污遮擋，彈出擦拭提醒",
                        checked = isLensDirtWarningEnabled,
                        onCheckedChange = { viewModel.setLensDirtWarningEnabled(it) },
                        testTag = "switch_lens_dirt_warning"
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 6 (MIDDLE): AR Reticle Crosshair Style
                    M3GroupedItemContainer(position = GroupedPosition.MIDDLE) {
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
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.CenterFocusWeak,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "AR 空間準心標記樣式",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "選擇最符合您習慣的 3D 瞄準十字座標",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val reticles = listOf(
                                "DOUBLE_RING" to "預設雙環",
                                "PRECISION_CROSSHAIR" to "精密十字",
                                "TARGET_BOX" to "貼地網格",
                                "MINIMAL_DOT" to "極簡微點"
                            )

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
                                        icon = {
                                            SegmentedButtonDefaults.Icon(active = isSelected)
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        },
                                        modifier = Modifier.testTag("reticle_btn_$code")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 7 (MIDDLE): Line Weight Customization
                    M3GroupedItemContainer(position = GroupedPosition.MIDDLE) {
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
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Polyline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "測量標註線條粗細",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "微調實境線段與多邊形渲染寬度",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val thicknesses = listOf(
                                2.0f to "細緻 (2dp)",
                                3.5f to "標準 (3.5dp)",
                                5.0f to "粗體 (5dp)"
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

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 8 (MIDDLE): HUD Style
                    M3GroupedItemContainer(position = GroupedPosition.MIDDLE) {
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
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Layers,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "HUD 控制面板外觀風格",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "切換懸浮毛玻璃、深色高對比或極簡樣式",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val hudStyles = listOf(
                                "GLASS" to "懸浮毛玻璃",
                                "SOLID" to "深色高對比",
                                "MINIMAL" to "極簡無邊框"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                hudStyles.forEach { (code, label) ->
                                    val isSelected = hudStyle == code
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setHudStyle(code)
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
                                            .testTag("hud_style_chip_$code")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 9 (MIDDLE): AR Label Text Size
                    M3GroupedItemContainer(position = GroupedPosition.MIDDLE) {
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
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.TextFields,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "AR 空間標籤字體大小",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "調整 3D 浮動數據標註與角度文字等級",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val fontSizes = listOf(
                                "COMPACT" to "精簡 (12sp)",
                                "STANDARD" to "標準 (14sp)",
                                "LARGE" to "放大 (17sp)"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                fontSizes.forEach { (code, label) ->
                                    val isSelected = arFontSize == code
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setArFontSize(code)
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
                                            .testTag("ar_font_chip_$code")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 10 (MIDDLE): Screen Ruler Theme
                    M3GroupedItemContainer(position = GroupedPosition.MIDDLE) {
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
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Straighten,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "螢幕直尺刻度主題",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "選擇 2D 直尺與雙指卡尺視覺配色風格",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val rulerThemes = listOf(
                                "STEEL" to "質感鋼鐵灰",
                                "NEON_CYAN" to "霓光青黑",
                                "HIGH_CONTRAST" to "工程高對比"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rulerThemes.forEach { (code, label) ->
                                    val isSelected = rulerTheme == code
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setRulerTheme(code)
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
                                            .testTag("ruler_theme_chip_$code")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 11 (MIDDLE): AR Badge Opacity
                    M3GroupedItemContainer(position = GroupedPosition.MIDDLE) {
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
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Opacity,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "AR 數據標註底框透明度",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "微調懸浮長度/面積卡片背景透明度",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val opacities = listOf(
                                "GLASS" to "70% 毛玻璃",
                                "SOLID" to "95% 高對比",
                                "CLEAR" to "40% 微透影"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                opacities.forEach { (code, label) ->
                                    val isSelected = badgeOpacity == code
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setBadgeOpacity(code)
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
                                            .testTag("badge_opacity_chip_$code")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 4 Item 12 (BOTTOM): AR Grid Overlay Style
                    M3GroupedItemContainer(position = GroupedPosition.BOTTOM) {
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
                                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Grid4x4,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "AR 輔助地面參考網格",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "切換空間平面捕捉時的透視參考軸網格",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val gridStyles = listOf(
                                "PERSPECTIVE_GRID" to "透視地面網格",
                                "DOT_MATRIX" to "3D 幾何點陣",
                                "OFF" to "關閉輔助網格"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                gridStyles.forEach { (code, label) ->
                                    val isSelected = gridOverlayStyle == code
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setGridOverlayStyle(code)
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
                                            .testTag("grid_style_chip_$code")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 6. 資料維護、系統報告與重設 (Data, Guide & System)
                // ==========================================
                if (shouldShowSection(selectedCategory, "DATA", searchQuery, listOf("資料", "導覽", "清除", "重設", "原廠", "備份", "報告", "回饋", "mail", "信箱", "關於", "版本", "arcore", "vulkan"))) {
                    SettingsCategoryHeader(
                        title = "資料管理、系統診斷與支援",
                        icon = Icons.Rounded.Security,
                        tint = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group 5 Item 1 (TOP): Welcome Guide Action Item
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

                    // Group 5 Item 2 (MIDDLE): Export/Copy Settings Report
                    M3GroupedActionItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.ContentPaste,
                        title = "產生並複製系統設定報告",
                        subtitle = "匯出當前所有量測演算法、單位與硬體參數清單",
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        onClick = {
                            val report = viewModel.getSettingsSummaryReport()
                            ShareUtility.copyToClipboard(context, report, "已複製系統設定參數報告至剪貼簿")
                        }
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 5 Item 3 (MIDDLE): Reset All Settings Destructive Action
                    M3GroupedActionItem(
                        position = GroupedPosition.MIDDLE,
                        icon = Icons.Rounded.RestartAlt,
                        title = "恢復原廠預設設定",
                        subtitle = "重置所有測量單位、準心樣式、感應器參數與 UI 風格",
                        iconTint = MaterialTheme.colorScheme.error,
                        iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        trailingContent = {
                            FilledTonalButton(
                                onClick = { showResetDefaultsConfirmDialog = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("重設", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        onClick = {
                            showResetDefaultsConfirmDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Group 5 Item 4 (MIDDLE): Clear Records Destructive Action Item
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

                    // Group 5 Item 5 (BOTTOM): Feedback Email Item
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

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 7. 系統硬體環境資訊卡片 (System Diagnostics Footer)
                // ==========================================
                SystemEnvironmentCard()

                Spacer(modifier = Modifier.height(24.dp))

                // M3 Footer App Identity
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AR 尺子與空間測量儀 Pro",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Material 3 · Google ARCore & Camera2 Engine · Vulkan 1.3 Accelerate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    // Language Selection Dialog
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
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "恢復原廠預設設定？",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "此操作將重設所有測量偏好、準心樣式、線條寬度、感應器融合參數與 UI 設定為官方出廠狀態（已儲存的測量歷史紀錄不會被刪除）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.resetAllSettingsToDefault()
                        showResetDefaultsConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("確定重設", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetDefaultsConfirmDialog = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("取消")
                }
            }
        )
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
 * Filter helper that determines if a section should be rendered based on selected category and search query.
 */
private fun shouldShowSection(
    selectedCategory: String,
    targetCategory: String,
    searchQuery: String,
    keywords: List<String>
): Boolean {
    if (selectedCategory != "ALL" && selectedCategory != targetCategory) {
        return false
    }
    if (searchQuery.isBlank()) {
        return true
    }
    val query = searchQuery.trim().lowercase()
    return keywords.any { it.lowercase().contains(query) || query.contains(it.lowercase()) }
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
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
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

/**
 * Interactive Pro Presets Scenario Cards
 */
@Composable
private fun PresetScenarioCards(
    selectedUnit: String,
    highFps: Boolean,
    antiJitter: Boolean,
    onApplyProfile: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val isPrecisionActive = selectedUnit == "mm" && antiJitter
    val isPowerSaverActive = !highFps && !antiJitter
    val isBalancedActive = !isPrecisionActive && !isPowerSaverActive

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Preset 1: 專業工程極限精密 (Pro Precision)
        PresetCardItem(
            title = "專業工程極限精密 (Pro Precision)",
            subtitle = "mm 單位 · 60fps · 9軸抗抖 · 多樣本平滑濾波 · 90° 正交吸附 · Raw Depth 55%+",
            icon = Icons.Rounded.PrecisionManufacturing,
            accentColor = Color(0xFF00E5FF),
            isActive = isPrecisionActive,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onApplyProfile("PRO_PRECISION")
            }
        )

        // Preset 2: 日常智慧平衡 (Balanced Default)
        PresetCardItem(
            title = "日常智慧平衡 (Smart Balanced)",
            subtitle = "cm 單位 · 60fps · 智慧感應器融合校正 · 雙環準心 · 空間特徵點雲可視化",
            icon = Icons.Rounded.Balance,
            accentColor = MaterialTheme.colorScheme.primary,
            isActive = isBalancedActive,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onApplyProfile("BALANCED")
            }
        )

        // Preset 3: 省電長效極速 (Power Saver)
        PresetCardItem(
            title = "省電長效極速 (Power Saver)",
            subtitle = "標準幀率 · 關閉高頻後台計算與點雲渲染 · 極簡微點 · 最大化延長電池續航",
            icon = Icons.Rounded.BatteryChargingFull,
            accentColor = Color(0xFF4CAF50),
            isActive = isPowerSaverActive,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onApplyProfile("POWER_SAVER")
            }
        )
    }
}

@Composable
private fun PresetCardItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 0.dp,
            color = if (isActive) accentColor.copy(alpha = 0.6f) else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = if (isActive) 0.25f else 0.15f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor
                        ) {
                            Text(
                                text = "使用中",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            if (!isActive) {
                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("套用", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Real-time Sensor Fusion Telemetry Card
 */
@Composable
private fun SensorTelemetryCard(
    telemetry: com.example.logic.sensor.SensorCorrectionTelemetry,
    onCalibrateSensors: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "即時感應器融合數據監控",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCalibrateSensors()
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重設基準", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Grid of live values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMetricPill(
                    label = "機身穩定度",
                    value = "${(telemetry.stabilityScore * 100).toInt()}%",
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricPill(
                    label = "估算誤差",
                    value = "±${String.format(java.util.Locale.US, "%.1f", telemetry.estimatedErrorMm)} mm",
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricPill(
                    label = "氣壓相對高度",
                    value = "${String.format(java.util.Locale.US, "%.2f", telemetry.barometricAltitudeMeters)} m",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Real-time Vulkan & AI Inference Status Card
 */
@Composable
private fun VulkanAiMetricsCard(
    metrics: com.example.logic.vulkan.VulkanAiMetrics
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF9800).copy(alpha = 0.2f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "Vulkan 1.3 & GPU 視覺推論遙測",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMetricPill(
                    label = "GPU 推論延遲",
                    value = "${String.format(java.util.Locale.US, "%.1f", metrics.inferenceTimeMs)} ms",
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricPill(
                    label = "渲染吞吐量",
                    value = "${metrics.computeThroughputFps} FPS",
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricPill(
                    label = "運算精度",
                    value = if (metrics.fp16PrecisionEnabled) "FP16 半精度" else "FP32 全精度",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * System Environment and Diagnostic Footer Card
 */
@Composable
private fun SystemEnvironmentCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "硬體與系統運算環境",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val envInfo = listOf(
                "AR 引擎核心" to "Google ARCore 1.48+ (Depth API & Mesh)",
                "相機管線架構" to "Android Camera2 High-Speed Pipeline (60 FPS)",
                "3D 渲染加速" to "Vulkan 1.3 Hardware Graphics Pipeline",
                "本機空間資料庫" to "Room SQLite Database Persistence",
                "介面設計語彙" to "Material Design 3 Expressive (M3 Grouped Shapes)"
            )

            envInfo.forEach { (title, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
