package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.ShareUtility
import com.example.ui.adaptive.LocalAdaptiveWindowState
import com.example.ui.adaptive.rememberAdaptiveWindowState
import com.example.ui.components.AdaptiveNavigationRail
import com.example.ui.components.BlurDirection
import com.example.ui.components.EnableWindowBlur
import com.example.ui.components.FloatingPillNavigationBar
import com.example.ui.components.FloatingPillNavItem
import com.example.ui.components.GradientBlurScrim
import com.example.ui.components.GradientBlurTopBar
import com.example.ui.components.LocalPixelGpuRenderPolicy
import com.example.ui.components.ModernArCameraView
import com.example.ui.components.RulerComponent
import com.example.ui.components.gradientBlurSource
import com.example.ui.components.hardwareBackdropBlur
import com.example.ui.components.progressiveGlass
import com.example.ui.components.rememberGradientBlurBackdropState
import com.example.logic.TranslationManager
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.viewmodel.MeasureViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MeasureViewModel) {
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val selectedUnit by viewModel.selectedUnit.collectAsStateWithLifecycle()
    val savedRecords by viewModel.savedRecords.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val showWelcomeScreen by viewModel.showWelcomeScreen.collectAsStateWithLifecycle()
    val gpuRenderPolicy by viewModel.gpuRenderPolicy.collectAsStateWithLifecycle()

    val adaptiveState = rememberAdaptiveWindowState()

    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var selectedRecordId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedRecordForDetail = remember(selectedRecordId, savedRecords) {
        selectedRecordId?.let { id -> savedRecords.find { it.id == id } }
    }
    val lastSavedRecord by viewModel.lastSavedRecord.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hostView = LocalView.current
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()

    DisposableEffect(keepScreenOn, hostView) {
        hostView.keepScreenOn = keepScreenOn
        onDispose {
            hostView.keepScreenOn = false
        }
    }

    // Hide system status bar cleanly when using full-screen Screen Ruler mode (currentMode == 1)
    DisposableEffect(currentMode, showSettingsDialog, showHistorySheet, showWelcomeScreen, hostView) {
        val window = context.findHostActivity()?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, hostView) else null
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (currentMode == 1 && !showSettingsDialog && !showHistorySheet && !showWelcomeScreen) {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    val arSavedRecords = remember(savedRecords) {
        savedRecords.filter { it.type != "RULER" && it.type != "LEVEL" }
    }

    val isCompactDetailOpen = !adaptiveState.useTwoPaneLayout && selectedRecordForDetail != null
    val isCompactHistoryModal = showHistorySheet && !adaptiveState.useTwoPaneLayout
    val isTranslucentOverlayActive by remember(adaptiveState.useTwoPaneLayout) {
        derivedStateOf {
            isCompactHistoryModal || isCompactDetailOpen
        }
    }
    val isObscuredByFullscreenOverlay by remember(adaptiveState.useTwoPaneLayout) {
        derivedStateOf {
            showWelcomeScreen || showSettingsDialog || (showHistorySheet && adaptiveState.useTwoPaneLayout)
        }
    }
    val hardwareBlurRadius by animateFloatAsState(
        targetValue = if (isTranslucentOverlayActive) 28f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "HardwareBackdropBlurAnim"
    )

    val isTakingPhoto by viewModel.isTakingPhoto.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.toastMessage.collectLatest { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    val cameraLabel = remember(currentLang) { TranslationManager.getString("nav_camera", currentLang) }
    val rulerLabel = remember(currentLang) { TranslationManager.getString("nav_ruler", currentLang) }

    // Ensure temporarily removed Spirit Level mode (2) resets cleanly to AR Measurement mode (0)
    LaunchedEffect(currentMode) {
        if (currentMode == 2) {
            viewModel.setMode(0)
        }
    }

    // Hierarchical Predictive-ready Back Handlers
    // 1. Compact Record Detail Dialog has highest priority
    androidx.activity.compose.BackHandler(enabled = isCompactDetailOpen) {
        selectedRecordId = null
    }

    // 2. Settings Screen (Full page / Two-Pane)
    androidx.activity.compose.BackHandler(enabled = showSettingsDialog && !isCompactDetailOpen) {
        showSettingsDialog = false
    }

    // 3. History Bottom Sheet / Two-Pane Workspace
    androidx.activity.compose.BackHandler(enabled = showHistorySheet && !showSettingsDialog && !isCompactDetailOpen) {
        showHistorySheet = false
    }

    // 4. Welcome Screen (Onboarding overlay)
    androidx.activity.compose.BackHandler(enabled = showWelcomeScreen && !showHistorySheet && !showSettingsDialog && !isCompactDetailOpen) {
        viewModel.dismissWelcomeScreen()
    }

    // 5. Secondary mode (Ruler -> return to Camera AR Home)
    androidx.activity.compose.BackHandler(enabled = currentMode != 0 && !showWelcomeScreen && !showHistorySheet && !showSettingsDialog && !isCompactDetailOpen) {
        viewModel.setMode(0)
    }

    val navItems = remember(cameraLabel, rulerLabel) {
        listOf(
            FloatingPillNavItem(
                id = 0,
                label = cameraLabel,
                icon = Icons.Rounded.CameraAlt,
                testTag = "segmented_button_camera"
            ),
            FloatingPillNavItem(
                id = 1,
                label = rulerLabel,
                icon = Icons.Rounded.Straighten,
                testTag = "segmented_button_ruler"
            )
        )
    }

    CompositionLocalProvider(
        LocalPixelGpuRenderPolicy provides gpuRenderPolicy,
        LocalAdaptiveWindowState provides adaptiveState,
        LocalAppLanguage provides currentLang
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val isCompact = !adaptiveState.useNavigationRail
            val useNavRail = adaptiveState.useNavigationRail

            Row(modifier = Modifier.fillMaxSize()) {
                // Adaptive Navigation Rail on medium, expanded, and Book-posture displays
                if (useNavRail) {
                    AdaptiveNavigationRail(
                        currentMode = currentMode,
                        onModeSelected = { viewModel.setMode(it) },
                        cameraLabel = cameraLabel,
                        rulerLabel = rulerLabel,
                        selectedUnit = selectedUnit,
                        onSelectUnit = { viewModel.setSelectedUnit(it) },
                        isTorchOn = isTorchOn,
                        onToggleTorch = { viewModel.toggleTorch(context) },
                        recordCount = arSavedRecords.size,
                        isHistoryOpen = showHistorySheet,
                        onToggleHistory = { showHistorySheet = !showHistorySheet },
                        onOpenSettings = { showSettingsDialog = true }
                    )
                }

                // Primary Workspace Layout
                val mainBackdropState = rememberGradientBlurBackdropState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Scaffold(
                        containerColor = if (currentMode == 0) Color.Transparent else MaterialTheme.colorScheme.background,
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    shape = RoundedCornerShape(16.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        topBar = {},
                        bottomBar = {
                            // Only show bottom navigation bar on compact screens (phones) and when not capturing a photo snapshot
                            if (isCompact && !isTakingPhoto) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bottom_segmented_bar"),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    if (currentMode != 0) {
                                        GradientBlurScrim(
                                            isTop = false,
                                            backdropState = mainBackdropState,
                                            blurRadius = 14.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(68.dp)
                                                .align(Alignment.BottomCenter)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .windowInsetsPadding(
                                                WindowInsets.navigationBars.only(
                                                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                                                )
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FloatingPillNavigationBar(
                                            selectedIndex = currentMode,
                                            items = navItems,
                                            onItemSelected = { id ->
                                                viewModel.setMode(id)
                                            },
                                            modifier = Modifier.testTag("mode_segmented_button_row")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (currentMode == 0 || currentMode == 1) PaddingValues(0.dp) else innerPadding)
                        ) {
                            // Material 3 Expressive Spring Scale & Fade Transition
                            AnimatedContent(
                                targetState = currentMode,
                                transitionSpec = {
                                    (fadeIn(animationSpec = ExpressiveMotion.effectsDefault()) +
                                        scaleIn(
                                            initialScale = 0.96f,
                                            animationSpec = ExpressiveMotion.spatialDefault()
                                        )).togetherWith(
                                        fadeOut(animationSpec = ExpressiveMotion.effectsFast())
                                    )
                                },
                                contentKey = { it },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .gradientBlurSource(mainBackdropState)
                                    .hardwareBackdropBlur(
                                        enabled = isTranslucentOverlayActive,
                                        blurRadius = hardwareBlurRadius
                                    ),
                                label = "MainModeFadeTransition"
                            ) { mode ->
                                when (mode) {
                                    1 -> {
                                        RulerComponent(
                                            viewModel = viewModel,
                                            bottomPadding = if (isCompact) innerPadding.calculateBottomPadding() else 0.dp
                                        )
                                    }
                                    else -> {
                                        ModernArCameraView(
                                            viewModel = viewModel,
                                            onShowHistoryClick = { showHistorySheet = !showHistorySheet },
                                            onShowSettingsClick = { showSettingsDialog = true },
                                            bottomPadding = if (isCompact) innerPadding.calculateBottomPadding() else 0.dp,
                                            isObscuredByFullscreenOverlay = isObscuredByFullscreenOverlay
                                        )
                                    }
                                }
                            }

                            // Quick Floating Share Banner when a record is newly saved (positioned at top to avoid blocking bottom controls)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = lastSavedRecord != null,
                                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 60.dp, start = 16.dp, end = 16.dp)
                                    .widthIn(max = 560.dp)
                                    .zIndex(20f)
                            ) {
                                lastSavedRecord?.let { saved ->
                                    val bannerShape = RoundedCornerShape(20.dp)
                                    Surface(
                                        color = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        shape = bannerShape,
                                        shadowElevation = 0.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(bannerShape)
                                            .progressiveGlass(
                                                backdropState = mainBackdropState,
                                                direction = BlurDirection.BOTTOM_TO_TOP,
                                                maxBlurRadius = 18.dp,
                                                tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f),
                                                shape = bannerShape,
                                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = tr("banner_saved_title"),
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = saved.title,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = {
                                                        ShareUtility.shareRecord(context, saved, currentLang)
                                                        viewModel.clearLastSavedRecord()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Share, null, Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(tr("common_share"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }

                                                IconButton(
                                                    onClick = { viewModel.clearLastSavedRecord() },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Close, contentDescription = tr("common_close"), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // History Presentation: List-Detail two-pane workspace on Tablets / Foldables; ModalBottomSheet on Compact phones
            if (showHistorySheet && !adaptiveState.useTwoPaneLayout) {
                ModalBottomSheet(
                    onDismissRequest = { showHistorySheet = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    EnableWindowBlur(blurRadiusDp = 50)
                    HistorySheetContent(
                        records = savedRecords,
                        viewModel = viewModel,
                        onSelectRecord = { record ->
                            selectedRecordId = record.id
                        },
                        onClose = { showHistorySheet = false }
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showHistorySheet && adaptiveState.useTwoPaneLayout,
                enter = slideInHorizontally(
                    initialOffsetX = { it / 4 },
                    animationSpec = ExpressiveMotion.spatialDefault()
                ) + fadeIn(animationSpec = ExpressiveMotion.effectsDefault()),
                exit = slideOutHorizontally(
                    targetOffsetX = { it / 4 },
                    animationSpec = ExpressiveMotion.spatialFast()
                ) + fadeOut(animationSpec = ExpressiveMotion.effectsFast()),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(90f)
            ) {
                HistoryTwoPaneWorkspace(
                    records = savedRecords,
                    selectedRecordId = selectedRecordId,
                    onSelectRecord = { record ->
                        selectedRecordId = record.id
                    },
                    onClearSelection = {
                        selectedRecordId = null
                    },
                    onClose = {
                        showHistorySheet = false
                    },
                    viewModel = viewModel,
                    adaptiveState = adaptiveState
                )
            }

            // Dedicated Full-Page / Two-Pane Settings Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = showSettingsDialog,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200, easing = LinearEasing)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(150, easing = LinearEasing)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { showSettingsDialog = false }
                )
            }

            // Dedicated Full-Page Welcome & Onboarding Guide Screen (M3 Expressive Spring Scale)
            androidx.compose.animation.AnimatedVisibility(
                visible = showWelcomeScreen,
                enter = fadeIn(animationSpec = ExpressiveMotion.effectsDefault()) + scaleIn(
                    initialScale = 0.93f,
                    animationSpec = ExpressiveMotion.spatialDefault()
                ),
                exit = fadeOut(animationSpec = ExpressiveMotion.effectsFast()) + scaleOut(
                    targetScale = 0.94f,
                    animationSpec = ExpressiveMotion.spatialFast()
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(150f)
            ) {
                WelcomeScreen(
                    viewModel = viewModel,
                    onStartMeasuring = { initialMode ->
                        viewModel.setMode(initialMode)
                    }
                )
            }
        }

        // Record Detail & Sharing Dialog for compact single-pane mode only
        if (!adaptiveState.useTwoPaneLayout) {
            selectedRecordForDetail?.let { record ->
                RecordDetailDialog(
                    record = record,
                    viewModel = viewModel,
                    onDismiss = { selectedRecordId = null }
                )
            }
        }
    }
}

private tailrec fun Context.findHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findHostActivity()
        else -> null
    }
}
