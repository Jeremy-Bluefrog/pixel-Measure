@file:androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.vulkan.VulkanArSceneView
import com.example.logic.ar.ArCoreEngineStatus
import com.example.logic.ar.StabilityLevel
import com.example.logic.camera.ArVideoRecorder
import com.example.logic.camera.HighSpeedCamera2Manager
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernArCameraView(
    viewModel: MeasureViewModel,
    onShowHistoryClick: () -> Unit,
    onShowSettingsClick: () -> Unit,
    bottomPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Reactive camera permission tracking with automatic lifecycle refresh
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val openAppSettings = {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    // ViewModel States (Lifecycle-aware collection to eliminate background wakeup churn)
    val trackingStability by viewModel.trackingStability.collectAsStateWithLifecycle()
    val planesCount by viewModel.arPlanesCount.collectAsStateWithLifecycle()
    val subMode by viewModel.cameraSubMode.collectAsStateWithLifecycle()
    val autoDetectedType by viewModel.autoDetectedType.collectAsStateWithLifecycle()
    val selectedUnit by viewModel.selectedUnit.collectAsStateWithLifecycle()
    val isSnapped by viewModel.isSnapped.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val torchBrightness by viewModel.torchBrightness.collectAsStateWithLifecycle()
    val isMeasurementAvailable by viewModel.isMeasurementAvailable.collectAsStateWithLifecycle()
    val isTakingPhoto by viewModel.isTakingPhoto.collectAsStateWithLifecycle()
    val effectiveTargetFps by viewModel.effectiveTargetFps.collectAsStateWithLifecycle()

    val capturedPoints = viewModel.capturedPoints
    val hasCapturedPoints by remember { derivedStateOf { capturedPoints.isNotEmpty() } }
    val isWaitingForSecondPoint by remember { derivedStateOf { capturedPoints.size % 2 == 1 } }

    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var modernArVulkanViewRef by remember { mutableStateOf<VulkanArSceneView?>(null) }

    // Dedicated single-instance Camera2 manager
    val camera2Manager = remember {
        HighSpeedCamera2Manager(context).also { manager ->
            viewModel.setHighSpeedCamera2Manager(manager)
        }
    }

    val cameraAspectRatio by viewModel.cameraAspectRatio.collectAsStateWithLifecycle()
    val useDisplayP3ColorSpace by viewModel.useDisplayP3ColorSpace.collectAsStateWithLifecycle()
    val isLensSmudged by viewModel.isLensSmudged.collectAsStateWithLifecycle()
    val simulatedAlert by viewModel.simulatedAlert.collectAsStateWithLifecycle()
    val uiButtonScale by viewModel.uiButtonScale.collectAsStateWithLifecycle()
    val arCoreStatus by viewModel.arCoreStatus.collectAsStateWithLifecycle()
    val isCameraSimulationActive by viewModel.isCameraSimulationActive.collectAsStateWithLifecycle()

    // Video Recorder
    val videoRecorder = remember { ArVideoRecorder(context) }
    val isRecordingVideo by videoRecorder.isRecording.collectAsStateWithLifecycle()
    val recordingSeconds by videoRecorder.recordingSeconds.collectAsStateWithLifecycle()

    // Touch ripple visual pings
    val pings = remember { mutableStateListOf<Pair<Offset, Animatable<Float, AnimationVector1D>>>() }

    val startCameraAndArPipeline: (TextureView) -> Unit = remember(viewModel, camera2Manager) {
        { tv: TextureView ->
            coroutineScope.launch {
                viewModel.setHighSpeedCamera2Manager(camera2Manager)
                viewModel.ensureArCoreInitialized(
                    activity = context as? android.app.Activity,
                    forceRetry = (viewModel.modernArEngine.session == null)
                )
                if (!tv.isAvailable) return@launch
                val rot = getDisplayRotation(context)
                if (tv.width > 0 && tv.height > 0) {
                    viewModel.updateDisplayGeometry(rot, tv.width, tv.height)
                    viewModel.modernArEngine.setDisplayGeometry(rot, tv.width, tv.height)
                }
                viewModel.modernArEngine.sharedCamera?.let { sc ->
                    camera2Manager.sharedCamera = sc
                    camera2Manager.preferredCameraId = viewModel.modernArEngine.selectedCameraId
                    camera2Manager.preferredAspectRatio = viewModel.modernArEngine.selectedCameraImageSize?.let {
                        if (it.height > 0) it.width.toFloat() / it.height.toFloat() else null
                    }
                }
                camera2Manager.openCameraAndStartSession(tv) { isHighSpeed, size ->
                    android.util.Log.i("CameraView", "Session ready. HighSpeed=$isHighSpeed, Size=${size.width}x${size.height}")
                    tv.post {
                        camera2Manager.configureTransform(tv, tv.width, tv.height)
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            textureViewRef?.let { tv ->
                if (tv.isAvailable) {
                    startCameraAndArPipeline(tv)
                }
            }
        }
    }

    LaunchedEffect(hasCameraPermission, isCameraSimulationActive) {
        if (hasCameraPermission && isCameraSimulationActive && viewModel.modernArEngine.session == null) {
            viewModel.checkAndInitArCore(context as? android.app.Activity, forceRetry = true)
        }
    }

    // Dialogs Visibility
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSensorStatusDialog by remember { mutableStateOf(false) }
    var showStabilityDiagnosticsDialog by remember { mutableStateOf(false) }
    var showLensCleaningDialog by remember { mutableStateOf(false) }
    var showLowLightDialog by remember { mutableStateOf(false) }
    var showMotionDialog by remember { mutableStateOf(false) }
    var showFeatureDialog by remember { mutableStateOf(false) }

    val isArOverlayOpen by remember {
        derivedStateOf {
            showHelpDialog || showSensorStatusDialog || showStabilityDiagnosticsDialog ||
                showLensCleaningDialog || showLowLightDialog || showMotionDialog || showFeatureDialog
        }
    }
    val arOverlayBlurRadius by animateFloatAsState(
        targetValue = if (isArOverlayOpen) 28f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "ArOverlayBackdropBlur"
    )

    LaunchedEffect(effectiveTargetFps, modernArVulkanViewRef) {
        val fpsFloat = effectiveTargetFps.toFloat()
        modernArVulkanViewRef?.setTargetFrameRate(fpsFloat)
        (context as? com.example.MainActivity)?.enforceHighDisplayRefreshRate(fpsFloat)
    }

    // Lifecycle sync for Camera2, ARCore & Vulkan loop
    DisposableEffect(lifecycleOwner) {
        viewModel.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    (context as? com.example.MainActivity)?.enforceHighDisplayRefreshRate(viewModel.effectiveTargetFps.value.toFloat())
                    hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    viewModel.onResume()
                    modernArVulkanViewRef?.onResume()
                    if (hasCameraPermission) {
                        textureViewRef?.let { tv ->
                            if (tv.isAvailable) {
                                startCameraAndArPipeline(tv)
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    modernArVulkanViewRef?.onPause()
                    viewModel.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pauseArAndCamera(camera2Manager)
            modernArVulkanViewRef?.destroy()
            viewModel.onPause()
            viewModel.setHighSpeedCamera2Manager(null)
            viewModel.releaseVulkanPipelineBuffers()
            viewModel.closeHighSpeedCamera()
            if (videoRecorder.isRecording.value) {
                videoRecorder.stopRecording { _, _, _ -> }
            }
        }
    }

    // Modern Refined Haptic Feedback Handler
    LaunchedEffect(Unit) {
        viewModel.hapticEvent.collect { type ->
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            when (type) {
                HapticType.SNAP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0, 18, 25, 28),
                                intArrayOf(0, 255, 0, 160),
                                -1
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(35)
                    }
                }
                HapticType.CLICK -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(20)
                    }
                }
                HapticType.HEAVY -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(45)
                    }
                }
                HapticType.DOUBLE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0, 30, 40, 30),
                                intArrayOf(0, 255, 0, 200),
                                -1
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(60)
                    }
                }
            }
        }
    }

    // Dynamic UI Pulse Animations (passed as State<Float> to ArMeasurementCanvasOverlay)
    val infiniteTransition = rememberInfiniteTransition(label = "infinitePulse")
    val reticlePulseScale = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reticleScale"
    )

    val dashPhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashPhase"
    )

    val planeLockedParticleAnim = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "planeLockedParticle"
    )

    val snapScaleAnimated = animateFloatAsState(
        targetValue = if (isSnapped) 1.20f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "snapScaleAnimated"
    )
    val snapGlowAlphaAnimated = animateFloatAsState(
        targetValue = if (isSnapped) 0.9f else 0.15f,
        animationSpec = tween(220, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
        label = "snapGlowAlphaAnimated"
    )

    // Shutter Flash State
    var isShutterFlash by remember { mutableStateOf(false) }

    if (!hasCameraPermission) {
        // Permission Request UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = viewModel.getString("perm_camera_title").ifEmpty { "需要相機存取權限" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = viewModel.getString("perm_camera_desc").ifEmpty { "此工具需要相機畫面進行 3D 空間投影。請授予權限以繼續使用測量功能。" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(56.dp)
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(viewModel.getString("btn_grant_perm").ifEmpty { "授與相機權限" }, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = openAppSettings,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(52.dp)
                ) {
                    Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(viewModel.getString("btn_open_settings").ifEmpty { "前往系統設定" }, fontWeight = FontWeight.Medium)
                }
            }
        }
    } else {
        // Active AR Camera Viewport
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .hardwareBackdropBlur(isArOverlayOpen, arOverlayBlurRadius)
        ) {
            val localDensity = androidx.compose.ui.platform.LocalDensity.current
            val viewportPxW = with(localDensity) { maxWidth.roundToPx() }
            val viewportPxH = with(localDensity) { maxHeight.roundToPx() }
            val currentRotation = getDisplayRotation(context)

            LaunchedEffect(viewportPxW, viewportPxH, currentRotation) {
                if (viewportPxW > 0 && viewportPxH > 0) {
                    viewModel.updateDisplayGeometry(currentRotation, viewportPxW, viewportPxH)
                    viewModel.modernArEngine.setDisplayGeometry(currentRotation, viewportPxW, viewportPxH)
                }
            }

            // 0. ARCore / Vulkan 60 FPS Frame Driver Companion
            AndroidView(
                factory = { ctx ->
                    VulkanArSceneView(
                        context = ctx,
                        session = viewModel.arSession,
                        modernArEngine = viewModel.modernArEngine,
                        viewModel = viewModel
                    ).also {
                        modernArVulkanViewRef = it
                    }
                },
                update = { v ->
                    modernArVulkanViewRef = v
                },
                onRelease = { v ->
                    v.destroy()
                    if (modernArVulkanViewRef == v) {
                        modernArVulkanViewRef = null
                    }
                },
                modifier = Modifier.size(1.dp)
            )

            // 1. High-Performance Camera2 TextureView Surface or Simulated 3D Environment Canvas
            if (isCameraSimulationActive) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                ) {
                    val w = size.width
                    val h = size.height
                    val lineColor = Color(0x3300E5FF)
                    val step = 60.dp.toPx()
                    var x = 0f
                    while (x < w) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, h), 1f)
                        x += step
                    }
                    var y = 0f
                    while (y < h) {
                        drawLine(lineColor, Offset(0f, y), Offset(w, y), 1f)
                        y += step
                    }
                    drawCircle(
                        color = Color(0x1A00E5FF),
                        radius = w * 0.45f,
                        center = Offset(w / 2f, h * 0.55f)
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        val textureView = TextureView(ctx).apply {
                            isOpaque = true
                        }
                        textureViewRef = textureView
                        viewModel.setHighSpeedCamera2Manager(camera2Manager)

                        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                val rot = getDisplayRotation(ctx)
                                viewModel.updateDisplayGeometry(rot, width, height)
                                viewModel.modernArEngine.setDisplayGeometry(rot, width, height)
                                startCameraAndArPipeline(textureView)
                            }
                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                                val rot = getDisplayRotation(ctx)
                                viewModel.updateDisplayGeometry(rot, width, height)
                                viewModel.modernArEngine.setDisplayGeometry(rot, width, height)
                                camera2Manager.configureTransform(textureView, width, height)
                            }
                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                viewModel.pauseArAndCamera(camera2Manager)
                                return true
                            }
                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }

                        if (textureView.isAvailable) {
                            val rot = getDisplayRotation(ctx)
                            viewModel.updateDisplayGeometry(rot, textureView.width, textureView.height)
                            viewModel.modernArEngine.setDisplayGeometry(rot, textureView.width, textureView.height)
                            startCameraAndArPipeline(textureView)
                        }

                        textureView
                    },
                    update = { tv ->
                        textureViewRef = tv
                    },
                    onRelease = { _ ->
                        viewModel.pauseArAndCamera(camera2Manager)
                        textureViewRef = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                )
            }

            // 2. 3D Augmented Overlay Canvas (Planes, Projected Points, Lines, Measurements, Pings, Reticle)
            ArMeasurementCanvasOverlay(
                viewModel = viewModel,
                pings = pings,
                dashPhase = dashPhase,
                reticlePulseScale = reticlePulseScale,
                snapScaleAnimated = snapScaleAnimated,
                snapGlowAlphaAnimated = snapGlowAlphaAnimated,
                planeLockedParticleAnim = planeLockedParticleAnim
            )

            // 3. Clean Unified Top HUD Stack (Zero Overlap)
            if (!isTakingPhoto) {
                ModernArTopBar(
                    viewModel = viewModel,
                    context = context,
                    subMode = subMode,
                    autoDetectedType = autoDetectedType,
                    selectedUnit = selectedUnit,
                    isTorchOn = isTorchOn,
                    torchBrightness = torchBrightness,
                    cameraAspectRatio = cameraAspectRatio,
                    planesCount = planesCount,
                    isRecordingVideo = isRecordingVideo,
                    recordingSeconds = recordingSeconds,
                    onShowHelp = { showHelpDialog = true },
                    onShowHistoryClick = onShowHistoryClick,
                    onShowSettingsClick = onShowSettingsClick,
                    onSwitchCamera = { viewModel.switchCameraFacing(textureViewRef) },
                    modifier = Modifier.align(Alignment.TopCenter),
                    extraTopHudContent = {
                        MeasurementGuidancePill(
                            viewModel = viewModel,
                            subMode = subMode,
                            autoDetectedType = autoDetectedType,
                            planesCount = planesCount,
                            hasCapturedPoints = hasCapturedPoints,
                            isWaitingForSecondPoint = isWaitingForSecondPoint,
                            capturedPointsCount = capturedPoints.size,
                            isMeasurementAvailable = isMeasurementAvailable,
                            isSnapped = isSnapped,
                            onOpenTutorial = { showHelpDialog = true }
                        )

                        if (trackingStability.level == StabilityLevel.POOR) {
                            ActiveTrackingStabilityWarningBanner(
                                stability = trackingStability,
                                onActionClick = { showStabilityDiagnosticsDialog = true }
                            )
                        }
                    }
                )
            }

            // 5. Bottom Dynamic Control Deck
            if (!isTakingPhoto) {
                ModernArBottomDeck(
                    viewModel = viewModel,
                    subMode = subMode,
                    selectedUnit = selectedUnit,
                    hasCapturedPoints = hasCapturedPoints,
                    isWaitingForSecondPoint = isWaitingForSecondPoint,
                    capturedPointsCount = capturedPoints.size,
                    isMeasurementAvailable = isMeasurementAvailable,
                    uiButtonScale = uiButtonScale,
                    isRecordingVideo = isRecordingVideo,
                    bottomPadding = bottomPadding,
                    textureViewRef = { textureViewRef },
                    modernArVulkanViewRef = { modernArVulkanViewRef },
                    cameraAspectRatio = cameraAspectRatio,
                    useDisplayP3ColorSpace = useDisplayP3ColorSpace,
                    videoRecorder = videoRecorder,
                    onShowHistoryClick = onShowHistoryClick,
                    onTriggerShutterFlash = {
                        isShutterFlash = true
                        coroutineScope.launch {
                            delay(120)
                            isShutterFlash = false
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 6. Shutter Flash Effect
            if (isShutterFlash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.75f))
                )
            }
        }
    }

    // 7. AR Dialogs
    ModernArDialogs(
        viewModel = viewModel,
        context = context,
        showHelpDialog = showHelpDialog,
        onDismissHelp = { showHelpDialog = false },
        showSensorStatusDialog = showSensorStatusDialog,
        onDismissSensorStatus = { showSensorStatusDialog = false },
        showLensCleaningDialog = showLensCleaningDialog,
        onDismissLensCleaning = { showLensCleaningDialog = false },
        showLowLightDialog = showLowLightDialog,
        onDismissLowLight = { showLowLightDialog = false },
        showMotionDialog = showMotionDialog,
        onDismissMotion = { showMotionDialog = false },
        showFeatureDialog = showFeatureDialog,
        onDismissFeature = { showFeatureDialog = false },
        showStabilityDiagnosticsDialog = showStabilityDiagnosticsDialog,
        onDismissStabilityDiagnostics = { showStabilityDiagnosticsDialog = false },
        trackingStability = trackingStability,
        isLensSmudged = isLensSmudged,
        simulatedAlert = simulatedAlert
    )
}

private fun getDisplayRotation(context: Context): Int {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
    }
    return display?.rotation ?: android.view.Surface.ROTATION_0
}
