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
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
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

    CompositionLocalProvider(LocalAppLanguage provides currentLanguage) {
    val currentLanguageDisplay = remember(currentLanguage) {
        TranslationManager.supportedLanguages.find { it.code == currentLanguage }?.name ?: currentLanguage
    }
    val themeLabel = when (themeMode) {
        "LIGHT" -> tr("set_theme_light_short")
        "DARK" -> tr("set_theme_dark_short")
        else -> tr("set_theme_system_short")
    }
    val unitLabel = when (selectedUnit) {
        "mm" -> tr("unit_mm")
        "m" -> tr("unit_meter")
        "in" -> tr("unit_inch")
        "ft" -> tr("unit_ft")
        "yd" -> tr("unit_yd")
        else -> tr("unit_cm")
    }
    val smartAssistCount = listOf(
        vibrateOnAlign,
        antiJitterEnabled,
        orthogonalSnapEnabled,
        gravityAlignmentEnabled,
        showPointCloud
    ).count { it }

    val ratioLabel = if (cameraAspectRatio == "FULL") tr("set_ratio_full") else cameraAspectRatio.replace("_", ":")
    val cameraSummary = "$ratioLabel · $refreshRateMode"
    val rulerSummary = "${(rulerFontScale * 100).roundToInt()}% · ${String.format(Locale.US, "%.3fx", rulerCalibration)}"
    val ifaceKeepOn = tr("set_iface_keep_on")
    val ifaceAutoCopy = tr("set_iface_auto_copy")
    val interfaceSummary = buildString {
        append("${(uiButtonScale * 100).roundToInt()}%")
        if (keepScreenOn) append(" · $ifaceKeepOn")
        if (autoCopyOnComplete) append(" · $ifaceAutoCopy")
    }
    val rulerThemeLabel = when (rulerTheme) {
        "STEEL" -> tr("set_ruler_steel")
        "NEON_CYAN" -> tr("set_ruler_cyan")
        "HIGH_CONTRAST" -> tr("set_ruler_contrast")
        else -> tr("set_ruler_dynamic")
    }
    val toolsSummary = if (rulerDualUnit) tr("set_tools_dual_fmt", rulerThemeLabel) else rulerThemeLabel
    val reticleLabel = when (reticleStyle) {
        "PRECISION_CROSSHAIR" -> tr("set_reticle_crosshair")
        "MINIMAL_DOT" -> tr("set_reticle_dot")
        "HOLOGRAPHIC_RADAR" -> tr("set_reticle_radar")
        else -> tr("set_reticle_double")
    }
    val visualSummary = "$reticleLabel · ${String.format(Locale.US, "%.1f dp", lineThickness)}"

    val categories = listOf(
        tr("set_cat_appearance") to listOf(
            SettingsRowEntry(
                id = "theme",
                icon = Icons.Rounded.DarkMode,
                title = tr("set_row_theme_title"),
                subtitle = tr("set_row_theme_sub"),
                badge = themeLabel,
                keywords = tr("set_kw_theme").split(","),
                testTag = "settings_row_theme",
                subPage = SettingsSubPage.THEME
            ),
            SettingsRowEntry(
                id = "language",
                icon = Icons.Rounded.Language,
                title = tr("set_row_lang_title"),
                subtitle = tr("set_row_lang_sub"),
                badge = currentLanguageDisplay,
                keywords = tr("set_kw_lang").split(","),
                testTag = "settings_row_language",
                subPage = SettingsSubPage.LANGUAGE
            ),
            SettingsRowEntry(
                id = "interface_custom",
                icon = Icons.Rounded.Tune,
                title = tr("set_row_iface_title"),
                subtitle = tr("set_row_iface_sub"),
                badge = interfaceSummary,
                keywords = tr("set_kw_iface").split(","),
                testTag = "settings_row_interface_custom",
                subPage = SettingsSubPage.INTERFACE_CUSTOM
            )
        ),
        tr("set_cat_measure") to listOf(
            SettingsRowEntry(
                id = "unit",
                icon = Icons.Rounded.Straighten,
                title = tr("set_row_unit_title"),
                subtitle = tr("set_row_unit_sub"),
                badge = unitLabel,
                keywords = tr("set_kw_unit").split(","),
                testTag = "settings_row_unit",
                subPage = SettingsSubPage.UNIT
            ),
            SettingsRowEntry(
                id = "visual_style",
                icon = Icons.Rounded.Palette,
                title = tr("set_row_visual_title"),
                subtitle = tr("set_row_visual_sub"),
                badge = visualSummary,
                keywords = tr("set_kw_visual").split(","),
                testTag = "settings_row_visual_style",
                subPage = SettingsSubPage.VISUAL_STYLE
            ),
            SettingsRowEntry(
                id = "smart_assist",
                icon = Icons.Rounded.AutoFixHigh,
                title = tr("set_row_assist_title"),
                subtitle = tr("set_row_assist_sub"),
                badge = tr("set_smart_count_fmt", smartAssistCount),
                keywords = tr("set_kw_assist").split(","),
                testTag = "settings_row_smart_assist",
                subPage = SettingsSubPage.SMART_ASSIST
            )
        ),
        tr("set_cat_camera_tools") to listOf(
            SettingsRowEntry(
                id = "camera_quality",
                icon = Icons.Rounded.CameraAlt,
                title = tr("set_row_camera_title"),
                subtitle = tr("set_row_camera_sub"),
                badge = cameraSummary,
                keywords = tr("set_kw_cam").split(","),
                testTag = "settings_row_camera_quality",
                subPage = SettingsSubPage.CAMERA_QUALITY
            ),
            SettingsRowEntry(
                id = "ruler_edit",
                icon = Icons.Rounded.Edit,
                title = tr("set_row_ruler_edit_title"),
                subtitle = tr("set_row_ruler_edit_sub"),
                badge = rulerSummary,
                keywords = tr("set_kw_ruler").split(","),
                testTag = "settings_row_ruler_edit",
                subPage = SettingsSubPage.RULER_EDIT
            ),
            SettingsRowEntry(
                id = "tools_custom",
                icon = Icons.Rounded.Colorize,
                title = tr("set_row_tools_title"),
                subtitle = tr("set_row_tools_sub"),
                badge = toolsSummary,
                keywords = tr("set_kw_tools").split(","),
                testTag = "settings_row_tools_custom",
                subPage = SettingsSubPage.TOOLS_CUSTOM
            )
        ),
        tr("set_cat_help_data") to listOf(
            SettingsRowEntry(
                id = "tutorial",
                icon = Icons.Rounded.AutoAwesome,
                title = tr("set_row_tutorial_title"),
                subtitle = tr("set_row_tutorial_sub"),
                keywords = tr("set_kw_tut").split(","),
                testTag = "settings_row_tutorial",
                subPage = SettingsSubPage.TUTORIAL
            ),
            SettingsRowEntry(
                id = "about",
                icon = Icons.Rounded.Info,
                title = tr("set_row_about_title"),
                subtitle = tr("set_row_about_sub"),
                keywords = tr("set_kw_about").split(","),
                testTag = "settings_row_about",
                subPage = SettingsSubPage.ABOUT
            ),
            SettingsRowEntry(
                id = "reset",
                icon = Icons.Rounded.RestartAlt,
                title = tr("set_row_reset_title"),
                subtitle = tr("set_row_reset_sub"),
                isDestructive = true,
                keywords = tr("set_kw_reset").split(","),
                testTag = "settings_row_reset",
                confirmDialog = PendingConfirmDialog.RESET_CONFIRM
            ),
            SettingsRowEntry(
                id = "clear",
                icon = Icons.Rounded.DeleteForever,
                title = tr("set_row_clear_title"),
                subtitle = tr("set_row_clear_sub"),
                isDestructive = true,
                keywords = tr("set_kw_clear").split(","),
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
                                text = tr("set_title"),
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
                                    contentDescription = tr("set_back_measure")
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
                                    contentDescription = tr("set_switch_lang"),
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
                                    contentDescription = tr("set_restore_defaults"),
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
                                text = tr("set_search_hint"),
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
                                        contentDescription = tr("common_clear_search"),
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
                                    text = tr("set_no_match_fmt", searchQuery),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tr("set_no_match_sub"),
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
            title = tr("set_reset_confirm_title"),
            message = tr("set_reset_confirm_msg"),
            confirmLabel = tr("set_restore_defaults"),
            icon = Icons.Rounded.RestartAlt,
            onConfirm = {
                viewModel.resetAllSettingsToDefault()
                pendingConfirm = PendingConfirmDialog.NONE
            },
            onDismiss = { pendingConfirm = PendingConfirmDialog.NONE }
        )
        PendingConfirmDialog.CLEAR_CONFIRM -> SettingsConfirmDialog(
            title = tr("set_clear_confirm_title"),
            message = tr("set_clear_confirm_msg"),
            confirmLabel = tr("ok_clear"),
            icon = Icons.Rounded.DeleteForever,
            onConfirm = {
                viewModel.clearAllRecords()
                pendingConfirm = PendingConfirmDialog.NONE
                Toast.makeText(
                    context,
                    TranslationManager.getString("set_cleared_toast", currentLanguage),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { pendingConfirm = PendingConfirmDialog.NONE }
        )
    }
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
