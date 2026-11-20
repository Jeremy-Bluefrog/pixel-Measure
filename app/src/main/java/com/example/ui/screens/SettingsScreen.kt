package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.TranslationManager
import com.example.ui.adaptive.LocalAdaptiveWindowState
import com.example.ui.adaptive.rememberLocalHingeTracker
import com.example.ui.components.GradientBlurTopBar
import com.example.ui.components.LanguageSelectionPage
import com.example.ui.theme.getGroupedPosition
import com.example.ui.components.gradientBlurSource
import com.example.ui.components.rememberGradientBlurBackdropState
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.GoogleSansRounded
import com.example.ui.theme.groupedItemShape
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel
import java.util.Locale
import kotlin.math.roundToInt

private enum class SettingsSubPage {
    NONE,
    THEME,
    LANGUAGE,
    INTERFACE_CUSTOM,
    UNIT,
    VISUAL_STYLE,
    SMART_ASSIST,
    CAMERA_QUALITY,
    RULER_EDIT,
    TOOLS_CUSTOM,
    TUTORIAL,
    ABOUT
}

private enum class PendingConfirmDialog {
    NONE,
    RESET_CONFIRM,
    CLEAR_CONFIRM
}

private data class SettingsRowEntry(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val badge: String? = null,
    val isDestructive: Boolean = false,
    val keywords: List<String>,
    val testTag: String,
    val subPage: SettingsSubPage? = null,
    val confirmDialog: PendingConfirmDialog? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MeasureViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val adaptiveState = LocalAdaptiveWindowState.current
    val (hingeTrackerModifier, hingeMetrics) = rememberLocalHingeTracker(adaptiveState)

    var activeSubPageName by rememberSaveable { mutableStateOf(SettingsSubPage.NONE.name) }
    val activeSubPage = remember(activeSubPageName) {
        runCatching { SettingsSubPage.valueOf(activeSubPageName) }.getOrDefault(SettingsSubPage.NONE)
    }
    val setActiveSubPage: (SettingsSubPage) -> Unit = remember {
        { page -> activeSubPageName = page.name }
    }
    var pendingConfirm by remember { mutableStateOf(PendingConfirmDialog.NONE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val mainScrollState = rememberScrollState()

    val isTwoPane = adaptiveState.useTwoPaneLayout
    val effectiveSubPage = if (isTwoPane && activeSubPage == SettingsSubPage.NONE) {
        SettingsSubPage.THEME
    } else {
        activeSubPage
    }

    BackHandler(enabled = pendingConfirm == PendingConfirmDialog.NONE) {
        if (!isTwoPane && activeSubPage != SettingsSubPage.NONE) {
            setActiveSubPage(SettingsSubPage.NONE)
        } else {
            onNavigateBack()
        }
    }

    // Consumer-facing ViewModel states
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val selectedUnit by viewModel.selectedUnit.collectAsStateWithLifecycle()
    val reticleStyle by viewModel.reticleStyle.collectAsStateWithLifecycle()
    val arAccentPalette by viewModel.arAccentPalette.collectAsStateWithLifecycle()
    val lineThickness by viewModel.lineThickness.collectAsStateWithLifecycle()
    val arFontSize by viewModel.arFontSize.collectAsStateWithLifecycle()
    val badgeOpacity by viewModel.badgeOpacity.collectAsStateWithLifecycle()
    val gridOverlayStyle by viewModel.gridOverlayStyle.collectAsStateWithLifecycle()
    val vibrateOnAlign by viewModel.vibrateOnAlignment.collectAsStateWithLifecycle()
    val antiJitterEnabled by viewModel.antiJitterEnabled.collectAsStateWithLifecycle()
    val orthogonalSnapEnabled by viewModel.orthogonalSnapEnabled.collectAsStateWithLifecycle()
    val gravityAlignmentEnabled by viewModel.gravityAlignmentEnabled.collectAsStateWithLifecycle()
    val showPointCloud by viewModel.showPointCloud.collectAsStateWithLifecycle()
    val vertexSnapThresholdCm by viewModel.vertexSnapThresholdCm.collectAsStateWithLifecycle()
    val scaleCalibrationFactor by viewModel.scaleCalibrationFactor.collectAsStateWithLifecycle()
    val cameraAspectRatio by viewModel.cameraAspectRatio.collectAsStateWithLifecycle()
    val highFpsModeEnabled by viewModel.highFpsModeEnabled.collectAsStateWithLifecycle()
    val highDefinitionQualityEnabled by viewModel.highDefinitionQualityEnabled.collectAsStateWithLifecycle()
    val useDisplayP3ColorSpace by viewModel.useDisplayP3ColorSpace.collectAsStateWithLifecycle()
    val isLensDirtWarningEnabled by viewModel.isLensDirtWarningEnabled.collectAsStateWithLifecycle()
    val torchBrightness by viewModel.torchBrightness.collectAsStateWithLifecycle()
    val refreshRateMode by viewModel.refreshRateMode.collectAsStateWithLifecycle()
    val uiButtonScale by viewModel.uiButtonScale.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val autoCopyOnComplete by viewModel.autoCopyOnComplete.collectAsStateWithLifecycle()
    val rulerCalibration by viewModel.rulerCalibration.collectAsStateWithLifecycle()
    val rulerFontScale by viewModel.rulerFontScale.collectAsStateWithLifecycle()
    val rulerTheme by viewModel.rulerTheme.collectAsStateWithLifecycle()
    val rulerDualUnit by viewModel.rulerDualUnit.collectAsStateWithLifecycle()

    val currentLanguageDisplay = remember(currentLanguage) {
        TranslationManager.supportedLanguages.find { it.code == currentLanguage }?.name ?: currentLanguage
    }
    val themeLabel = when (themeMode) {
        "LIGHT" -> "日間模式"
        "DARK" -> "夜間模式"
        else -> "跟隨系統"
    }
    val unitLabel = when (selectedUnit) {
        "mm" -> "公釐 (mm)"
        "m" -> "公尺 (m)"
        "in" -> "英吋 (in)"
        "ft" -> "英呎 (ft)"
        "yd" -> "碼 (yd)"
        else -> "公分 (cm)"
    }
    val smartAssistCount = listOf(
        vibrateOnAlign,
        antiJitterEnabled,
        orthogonalSnapEnabled,
        gravityAlignmentEnabled,
        showPointCloud
    ).count { it }

    val ratioLabel = if (cameraAspectRatio == "FULL") "全螢幕" else cameraAspectRatio.replace("_", ":")
    val cameraSummary = "$ratioLabel · $refreshRateMode"
    val rulerSummary = "${(rulerFontScale * 100).roundToInt()}% · ${String.format(Locale.US, "%.3fx", rulerCalibration)}"
    val interfaceSummary = buildString {
        append("${(uiButtonScale * 100).roundToInt()}%")
        if (keepScreenOn) append(" · 恆亮")
        if (autoCopyOnComplete) append(" · 自動複製")
    }
    val rulerThemeLabel = when (rulerTheme) {
        "STEEL" -> "鋼鐵灰"
        "NEON_CYAN" -> "霓光青"
        "HIGH_CONTRAST" -> "工程黃"
        else -> "動態色"
    }
    val toolsSummary = if (rulerDualUnit) "$rulerThemeLabel · 雙制對照" else rulerThemeLabel
    val reticleLabel = when (reticleStyle) {
        "PRECISION_CROSSHAIR" -> "十字"
        "MINIMAL_DOT" -> "圓點"
        "HOLOGRAPHIC_RADAR" -> "雷達"
        else -> "雙環"
    }
    val visualSummary = "$reticleLabel · ${String.format(Locale.US, "%.1f dp", lineThickness)}"

    val categories = listOf(
        "外觀與操作介面" to listOf(
            SettingsRowEntry(
                id = "theme",
                icon = Icons.Rounded.DarkMode,
                title = "日間與夜間外觀",
                subtitle = "切換明亮、深色或跟隨系統主題",
                badge = themeLabel,
                keywords = listOf("外觀", "主題", "日間", "夜間", "深色", "淺色", "系統"),
                testTag = "settings_row_theme",
                subPage = SettingsSubPage.THEME
            ),
            SettingsRowEntry(
                id = "language",
                icon = Icons.Rounded.Language,
                title = "應用程式語言",
                subtitle = "選擇顯示與量測單位介面語言",
                badge = currentLanguageDisplay,
                keywords = listOf("語言", "中文", "英文", "language"),
                testTag = "settings_row_language",
                subPage = SettingsSubPage.LANGUAGE
            ),
            SettingsRowEntry(
                id = "interface_custom",
                icon = Icons.Rounded.Tune,
                title = "操作介面與便利自訂",
                subtitle = "調整主按鈕大小、測量恆亮與完成後自動複製",
                badge = interfaceSummary,
                keywords = listOf("介面", "按鈕", "大小", "恆亮", "複製", "剪貼簿", "便利", "自訂"),
                testTag = "settings_row_interface_custom",
                subPage = SettingsSubPage.INTERFACE_CUSTOM
            )
        ),
        "測量與視覺" to listOf(
            SettingsRowEntry(
                id = "unit",
                icon = Icons.Rounded.Straighten,
                title = "預設測量單位",
                subtitle = "切換公釐、公分、公尺、英吋或英呎",
                badge = unitLabel,
                keywords = listOf("單位", "公釐", "公分", "公尺", "英吋", "英呎", "mm", "cm", "m", "in", "ft"),
                testTag = "settings_row_unit",
                subPage = SettingsSubPage.UNIT
            ),
            SettingsRowEntry(
                id = "visual_style",
                icon = Icons.Rounded.Palette,
                title = "AR 準心、色彩與線條樣式",
                subtitle = "自訂 3D 準心外觀、主題色調、線條粗細與參考網格",
                badge = visualSummary,
                keywords = listOf("準心", "顏色", "線條", "粗細", "字體", "標籤", "網格", "透明度", "樣式", "自訂"),
                testTag = "settings_row_visual_style",
                subPage = SettingsSubPage.VISUAL_STYLE
            ),
            SettingsRowEntry(
                id = "smart_assist",
                icon = Icons.Rounded.AutoFixHigh,
                title = "智慧對齊與觸覺輔助",
                subtitle = "防手震、直角吸附、磁吸靈敏度與空間尺度微調",
                badge = "$smartAssistCount/5 開啟",
                keywords = listOf("震動", "防手震", "吸附", "對齊", "直角", "點雲", "垂直", "磁吸", "尺度", "校正"),
                testTag = "settings_row_smart_assist",
                subPage = SettingsSubPage.SMART_ASSIST
            )
        ),
        "相機與工具個人化" to listOf(
            SettingsRowEntry(
                id = "camera_quality",
                icon = Icons.Rounded.CameraAlt,
                title = "相機畫質、色彩與補光",
                subtitle = "調整取景比例、120Hz、Display P3 與手電筒預設亮度",
                badge = cameraSummary,
                keywords = listOf("相機", "畫質", "fps", "120", "60", "清晰", "比例", "全螢幕", "p3", "補光", "手電筒", "鏡頭"),
                testTag = "settings_row_camera_quality",
                subPage = SettingsSubPage.CAMERA_QUALITY
            ),
            SettingsRowEntry(
                id = "ruler_edit",
                icon = Icons.Rounded.Edit,
                title = "螢幕尺數字大小與校正",
                subtitle = "調整螢幕尺刻度文字大小與實體比例係數",
                badge = rulerSummary,
                keywords = listOf("螢幕尺", "尺規", "數字", "大小", "校正", "實體"),
                testTag = "settings_row_ruler_edit",
                subPage = SettingsSubPage.RULER_EDIT
            ),
            SettingsRowEntry(
                id = "tools_custom",
                icon = Icons.Rounded.Colorize,
                title = "螢幕尺風格與對照設定",
                subtitle = "自訂尺規色彩主題與英吋雙制對照刻度",
                badge = toolsSummary,
                keywords = listOf("螢幕尺", "主題", "霓光", "鋼鐵", "雙制", "英吋", "個人化"),
                testTag = "settings_row_tools_custom",
                subPage = SettingsSubPage.TOOLS_CUSTOM
            )
        ),
        "說明與資料管理" to listOf(
            SettingsRowEntry(
                id = "tutorial",
                icon = Icons.Rounded.AutoAwesome,
                title = "AR 測量使用指南",
                subtitle = "查看 3D 空間圓圈量測、拍照與錄影訣竅",
                keywords = listOf("教學", "指南", "導覽", "圓圈", "使用", "說明"),
                testTag = "settings_row_tutorial",
                subPage = SettingsSubPage.TUTORIAL
            ),
            SettingsRowEntry(
                id = "about",
                icon = Icons.Rounded.Info,
                title = "關於 Measure 與意見回饋",
                subtitle = "版本介紹與開發者回饋信箱",
                keywords = listOf("關於", "回饋", "信箱", "意見", "版本"),
                testTag = "settings_row_about",
                subPage = SettingsSubPage.ABOUT
            ),
            SettingsRowEntry(
                id = "reset",
                icon = Icons.Rounded.RestartAlt,
                title = "恢復預設設定",
                subtitle = "還原所有視覺與量測設定（保留歷史紀錄）",
                isDestructive = true,
                keywords = listOf("重設", "預設", "恢復", "還原"),
                testTag = "settings_row_reset",
                confirmDialog = PendingConfirmDialog.RESET_CONFIRM
            ),
            SettingsRowEntry(
                id = "clear",
                icon = Icons.Rounded.DeleteForever,
                title = "清除所有測量紀錄",
                subtitle = "永久刪除已保存的 AR 數據、照片與影片",
                isDestructive = true,
                keywords = listOf("清除", "刪除", "歷史", "紀錄", "照片", "影片"),
                testTag = "settings_row_clear",
                confirmDialog = PendingConfirmDialog.CLEAR_CONFIRM
            )
        )
    )

    val filteredCategories = remember(categories, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            categories
        } else {
            categories.mapNotNull { (catTitle, items) ->
                val matched = items.filter { entry ->
                    entry.title.lowercase().contains(q) ||
                        entry.subtitle.lowercase().contains(q) ||
                        entry.keywords.any { kw -> kw.lowercase().contains(q) || q.contains(kw.lowercase()) }
                }
                if (matched.isNotEmpty()) catTitle to matched else null
            }
        }
    }

    val settingsBackdropState = rememberGradientBlurBackdropState()

    val renderSubPageContent: @Composable (SettingsSubPage) -> Unit = { page ->
        when (page) {
            SettingsSubPage.NONE -> Unit
            SettingsSubPage.THEME -> ThemeAndAppearancePage(
                themeMode = themeMode,
                onSelectTheme = { viewModel.setThemeMode(it) },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
            SettingsSubPage.LANGUAGE -> LanguageSelectionPage(
                currentLanguage = currentLanguage,
                onLanguageSelected = { newLang ->
                    viewModel.setLanguage(newLang)
                    if (!isTwoPane) {
                        setActiveSubPage(SettingsSubPage.NONE)
                    }
                },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) },
                onOpenSystemSettings = { viewModel.openSystemLanguageSettings(context) }
            )
            SettingsSubPage.INTERFACE_CUSTOM -> InterfaceCustomizationPage(
                uiButtonScale = uiButtonScale,
                keepScreenOn = keepScreenOn,
                autoCopyOnComplete = autoCopyOnComplete,
                onUiButtonScaleChange = { viewModel.setUiButtonScale(it) },
                onKeepScreenOnChange = { viewModel.setKeepScreenOn(it) },
                onAutoCopyOnCompleteChange = { viewModel.setAutoCopyOnComplete(it) },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
            SettingsSubPage.UNIT -> DefaultUnitPage(
                selectedUnit = selectedUnit,
                onSelectUnit = { viewModel.setSelectedUnit(it) },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
            SettingsSubPage.VISUAL_STYLE -> ArVisualStylePage(
                reticleStyle = reticleStyle,
                arAccentPalette = arAccentPalette,
                lineThickness = lineThickness,
                arFontSize = arFontSize,
                badgeOpacity = badgeOpacity,
                gridOverlayStyle = gridOverlayStyle,
                onReticleStyleChange = { viewModel.setReticleStyle(it) },
                onArAccentPaletteChange = { viewModel.setArAccentPalette(it) },
                onLineThicknessChange = { viewModel.setLineThickness(it) },
                onArFontSizeChange = { viewModel.setArFontSize(it) },
                onBadgeOpacityChange = { viewModel.setBadgeOpacity(it) },
                onGridOverlayStyleChange = { viewModel.setGridOverlayStyle(it) },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
            SettingsSubPage.SMART_ASSIST -> SmartAssistPage(
                vibrateOnAlign = vibrateOnAlign,
                antiJitterEnabled = antiJitterEnabled,
                orthogonalSnapEnabled = orthogonalSnapEnabled,
                gravityAlignmentEnabled = gravityAlignmentEnabled,
                showPointCloud = showPointCloud,
                vertexSnapThresholdCm = vertexSnapThresholdCm,
                scaleCalibrationFactor = scaleCalibrationFactor,
                onVibrateChange = { viewModel.setVibrateOnAlignment(it) },
                onAntiJitterChange = { enabled ->
                    viewModel.setAntiJitterEnabled(enabled)
                    viewModel.setSensorCorrectionEnabled(enabled)
                },
                onOrthogonalSnapChange = { viewModel.setOrthogonalSnapEnabled(it) },
                onGravityAlignmentChange = { viewModel.setGravityAlignmentEnabled(it) },
                onShowPointCloudChange = { viewModel.setShowPointCloud(it) },
                onVertexSnapChange = { viewModel.setVertexSnapThresholdCm(it) },
                onScaleCalibrationChange = { viewModel.setScaleCalibrationFactor(it) },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
            SettingsSubPage.CAMERA_QUALITY -> CameraQualityPage(
                cameraAspectRatio = cameraAspectRatio,
                highFpsModeEnabled = highFpsModeEnabled,
                highDefinitionQualityEnabled = highDefinitionQualityEnabled,
                useDisplayP3ColorSpace = useDisplayP3ColorSpace,
                isLensDirtWarningEnabled = isLensDirtWarningEnabled,
                torchBrightness = torchBrightness,
                refreshRateMode = refreshRateMode,
                onAspectRatioChange = { viewModel.setCameraAspectRatio(it) },
                onHighFpsChange = { viewModel.setHighFpsModeEnabled(it) },
                onHighDefinitionChange = { viewModel.setHighDefinitionQualityEnabled(it) },
                onDisplayP3Change = { viewModel.setUseDisplayP3ColorSpace(it) },
                onLensDirtWarningChange = { viewModel.setLensDirtWarningEnabled(it) },
                onTorchBrightnessChange = { viewModel.setTorchBrightness(context, it) },
                onRefreshRateChange = { viewModel.setRefreshRateMode(it) },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
            SettingsSubPage.RULER_EDIT -> RulerEditPage(
                calibrationFactor = rulerCalibration,
                fontScale = rulerFontScale,
                onFontScaleChange = { viewModel.updateRulerFontScale(it, persistImmediately = true) },
                onCalibrationChange = { viewModel.updateRulerCalibration(it, persistImmediately = true) },
                onResetAll = {
                    viewModel.updateRulerFontScale(1.0f, persistImmediately = true)
                    viewModel.updateRulerCalibration(1.0f, persistImmediately = true)
                    viewModel.triggerHapticFeedback(HapticType.CLICK)
                },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
            SettingsSubPage.TOOLS_CUSTOM -> ToolsCustomizationPage(
                rulerTheme = rulerTheme,
                rulerDualUnit = rulerDualUnit,
                onRulerThemeChange = { viewModel.setRulerTheme(it) },
                onRulerDualUnitChange = { viewModel.setRulerDualUnit(it) },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
            SettingsSubPage.TUTORIAL -> MeasurementTutorialPage(
                onReplayWelcome = {
                    viewModel.openWelcomeScreen()
                    onNavigateBack()
                },
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
            SettingsSubPage.ABOUT -> AboutFeedbackPage(
                onNavigateBack = { setActiveSubPage(SettingsSubPage.NONE) }
            )
        }
    }

    val renderMasterListScaffold: @Composable (Modifier, SettingsSubPage?) -> Unit = { listModifier, highlightedPage ->
        Scaffold(
            modifier = listModifier
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                .testTag("settings_screen"),
            topBar = {
                GradientBlurTopBar(
                    backdropState = settingsBackdropState,
                    baseColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.78f),
                    blurRadius = 22.dp,
                    modifier = Modifier.testTag("settings_gradient_blur_topbar")
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "設定",
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onNavigateBack()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("settings_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "返回測量"
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    setActiveSubPage(SettingsSubPage.LANGUAGE)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("settings_language_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = "切換語言",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pendingConfirm = PendingConfirmDialog.RESET_CONFIRM
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("settings_reset_defaults_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = "恢復預設值",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .gradientBlurSource(settingsBackdropState)
                    .verticalScroll(mainScrollState)
                    .padding(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 20.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "搜尋設定（外觀、單位、線條、螢幕尺...）",
                                fontSize = 13.sp,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
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
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "清除搜尋",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
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

                    if (filteredCategories.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "找不到符合「$searchQuery」的設定",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "請嘗試其他關鍵字或清除搜尋欄",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        filteredCategories.forEach { (categoryTitle, entries) ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = categoryTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    entries.forEachIndexed { idx, entry ->
                                        val position = getGroupedPosition(idx, entries.size)
                                        val itemShape = groupedItemShape(position, outerCorner = 22.dp, innerCorner = 5.dp)

                                        SettingsCategoryRow(
                                            entry = entry,
                                            shape = itemShape,
                                            isSelected = highlightedPage != null && entry.subPage == highlightedPage,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                entry.subPage?.let { setActiveSubPage(it) }
                                                entry.confirmDialog?.let { pendingConfirm = it }
                                            }
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

    if (isTwoPane) {
        val hasHingeSplit = adaptiveState.isBook && hingeMetrics.containerWidthPx > 0
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .then(hingeTrackerModifier)
                .testTag("settings_two_pane_workspace")
        ) {
            val listPaneModifier = if (hasHingeSplit) {
                Modifier
                    .width(hingeMetrics.leftPaneWidthDp(density, fallbackFraction = 0.42f))
                    .fillMaxHeight()
            } else {
                Modifier
                    .weight(0.42f)
                    .widthIn(min = 300.dp, max = 460.dp)
                    .fillMaxHeight()
            }

            Box(modifier = listPaneModifier.testTag("settings_list_pane")) {
                renderMasterListScaffold(Modifier.fillMaxSize(), effectiveSubPage)
            }

            if (hasHingeSplit) {
                Spacer(
                    modifier = Modifier
                        .width(hingeMetrics.verticalHingeGapDp(density))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .testTag("settings_hinge_gap")
                )
            } else {
                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .testTag("settings_detail_pane")
            ) {
                CompositionLocalProvider(LocalSettingsEmbeddedInTwoPane provides true) {
                    AnimatedContent(
                        targetState = effectiveSubPage,
                        transitionSpec = {
                            fadeIn(animationSpec = ExpressiveMotion.effectsFast())
                                .togetherWith(fadeOut(animationSpec = ExpressiveMotion.effectsFast()))
                        },
                        label = "SettingsTwoPaneDetailTransition"
                    ) { page ->
                        renderSubPageContent(page)
                    }
                }
            }
        }
    } else {
        AnimatedContent(
            targetState = activeSubPage,
            transitionSpec = {
                if (targetState != SettingsSubPage.NONE) {
                    (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 3 } +
                        fadeIn(tween(220))).togetherWith(
                        slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 6 } +
                            fadeOut(tween(160))
                    )
                } else {
                    (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 4 } +
                        fadeIn(tween(220))).togetherWith(
                        slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 3 } +
                            fadeOut(tween(160))
                    )
                }
            },
            label = "SettingsSubPageTransition"
        ) { currentSubPage ->
            if (currentSubPage == SettingsSubPage.NONE) {
                renderMasterListScaffold(Modifier.fillMaxSize(), null)
            } else {
                renderSubPageContent(currentSubPage)
            }
        }
    }

    // Destructive Action Confirmation AlertDialogs
    when (pendingConfirm) {
        PendingConfirmDialog.NONE -> Unit
        PendingConfirmDialog.RESET_CONFIRM -> SettingsConfirmDialog(
            title = "恢復預設設定？",
            message = "這會將外觀、單位、線條樣式與螢幕尺調整恢復為預設狀態，已儲存的測量紀錄不會被刪除。",
            confirmLabel = "恢復預設值",
            icon = Icons.Rounded.RestartAlt,
            onConfirm = {
                viewModel.resetAllSettingsToDefault()
                pendingConfirm = PendingConfirmDialog.NONE
            },
            onDismiss = { pendingConfirm = PendingConfirmDialog.NONE }
        )
        PendingConfirmDialog.CLEAR_CONFIRM -> SettingsConfirmDialog(
            title = "確認清除所有紀錄？",
            message = "此操作無法復原，本機保存的所有歷史測量數據、照片與影片都將被永久刪除。",
            confirmLabel = "確定清除",
            icon = Icons.Rounded.DeleteForever,
            onConfirm = {
                viewModel.clearAllRecords()
                pendingConfirm = PendingConfirmDialog.NONE
                Toast.makeText(context, "已清除所有測量紀錄", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { pendingConfirm = PendingConfirmDialog.NONE }
        )
    }
}

@Composable
private fun SettingsCategoryRow(
    entry: SettingsRowEntry,
    shape: RoundedCornerShape,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = when {
        entry.isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val iconContainer = if (entry.isDestructive) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
    } else if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    }
    val iconTint = if (entry.isDestructive) {
        MaterialTheme.colorScheme.error
    } else if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(entry.testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!entry.badge.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = entry.badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        maxLines = 1
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (entry.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
