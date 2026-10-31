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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            viewModel.checkAndInitArCore(context as? android.app.Activity, forceRetry = true)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            viewModel.checkAndInitArCore(context as? android.app.Activity, forceRetry = (viewModel.modernArEngine.session == null))
        }
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

    // ViewModel States
    val trackingStability by viewModel.trackingStability.collectAsState()
    val planesCount by viewModel.arPlanesCount.collectAsState()
    val subMode by viewModel.cameraSubMode.collectAsState()
    val autoDetectedType by viewModel.autoDetectedType.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val isSnapped by viewModel.isSnapped.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val torchBrightness by viewModel.torchBrightness.collectAsState()
    val isMeasurementAvailable by viewModel.isMeasurementAvailable.collectAsState()
    val isTakingPhoto by viewModel.isTakingPhoto.collectAsState()

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

    val cameraAspectRatio by viewModel.cameraAspectRatio.collectAsState()
    val useDisplayP3ColorSpace by viewModel.useDisplayP3ColorSpace.collectAsState()
    val isLensSmudged by viewModel.isLensSmudged.collectAsState()
    val simulatedAlert by viewModel.simulatedAlert.collectAsState()
    val uiButtonScale by viewModel.uiButtonScale.collectAsState()
    val arCoreStatus by viewModel.arCoreStatus.collectAsState()

    // Video Recorder
    val videoRecorder = remember { ArVideoRecorder(context) }
    val isRecordingVideo by videoRecorder.isRecording.collectAsState()
    val recordingSeconds by videoRecorder.recordingSeconds.collectAsState()

    // Touch ripple visual pings
    val pings = remember { mutableStateListOf<Pair<Offset, Animatable<Float, AnimationVector1D>>>() }

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

    // Lifecycle sync for Camera2 & Sensor Fusion
    DisposableEffect(lifecycleOwner) {
        viewModel.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    (context as? com.example.MainActivity)?.enforceHighDisplayRefreshRate()
                    hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    viewModel.onResume()
                    if (hasCameraPermission) {
                        viewModel.checkAndInitArCore(context as? android.app.Activity, forceRetry = (viewModel.modernArEngine.session == null))
                        textureViewRef?.let { tv ->
                            if (tv.isAvailable) {
                                viewModel.setHighSpeedCamera2Manager(camera2Manager)
                                viewModel.modernArEngine.sharedCamera?.let { camera2Manager.sharedCamera = it }
                                camera2Manager.openCameraAndStartSession(tv) { isHighSpeed, size ->
                                    android.util.Log.i("CameraView", "Session resumed. HighSpeed=$isHighSpeed, Size=${size.width}x${size.height}")
                                    viewModel.modernArEngine.resume()
                                    tv.post {
                                        camera2Manager.configureTransform(tv, tv.width, tv.height)
                                    }
                                }
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onPause()
                    camera2Manager.closeCamera()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            camera2Manager.closeCamera()
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

            LaunchedEffect(viewportPxW, viewportPxH) {
                if (viewportPxW > 0 && viewportPxH > 0) {
                    viewModel.updateDisplayGeometry(0, viewportPxW, viewportPxH)
                    viewModel.modernArEngine.setDisplayGeometry(0, viewportPxW, viewportPxH)
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

            // 1. High-Performance, Rock-Solid Camera2 TextureView Surface
            AndroidView(
                factory = { ctx ->
                    val textureView = TextureView(ctx).apply {
                        isOpaque = true
                    }
                    textureViewRef = textureView
                    viewModel.setHighSpeedCamera2Manager(camera2Manager)
                    viewModel.modernArEngine.sharedCamera?.let { camera2Manager.sharedCamera = it }

                    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ctx.display else (ctx.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
                            val rot = display?.rotation ?: android.view.Surface.ROTATION_0
                            viewModel.updateDisplayGeometry(rot, width, height)
                            viewModel.modernArEngine.setDisplayGeometry(rot, width, height)
                            if (viewModel.modernArEngine.session == null) {
                                viewModel.checkAndInitArCore(ctx as? android.app.Activity, forceRetry = true)
                            }
                            viewModel.modernArEngine.sharedCamera?.let { camera2Manager.sharedCamera = it }
                            camera2Manager.openCameraAndStartSession(textureView) { isHighSpeed, size ->
                                android.util.Log.i("CameraView", "Session ready. HighSpeed=$isHighSpeed, Size=${size.width}x${size.height}")
                                viewModel.modernArEngine.resume()
                                textureView.post {
                                    camera2Manager.configureTransform(textureView, width, height)
                                }
                            }
                        }
                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ctx.display else (ctx.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
                            val rot = display?.rotation ?: android.view.Surface.ROTATION_0
                            viewModel.updateDisplayGeometry(rot, width, height)
                            viewModel.modernArEngine.setDisplayGeometry(rot, width, height)
                            camera2Manager.configureTransform(textureView, width, height)
                        }
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            camera2Manager.closeCamera()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }

                    if (textureView.isAvailable) {
                        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ctx.display else (ctx.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
                        val rot = display?.rotation ?: android.view.Surface.ROTATION_0
                        viewModel.updateDisplayGeometry(rot, textureView.width, textureView.height)
                        viewModel.modernArEngine.sharedCamera?.let { camera2Manager.sharedCamera = it }
                        camera2Manager.openCameraAndStartSession(textureView) { isHighSpeed, size ->
                            android.util.Log.i("CameraView", "Session ready (immediate). HighSpeed=$isHighSpeed, Size=${size.width}x${size.height}")
                            viewModel.modernArEngine.resume()
                            textureView.post {
                                camera2Manager.configureTransform(textureView, textureView.width, textureView.height)
                            }
                        }
                    }

                    textureView
                },
                update = { tv ->
                    textureViewRef = tv
                },
                onRelease = { _ ->
                    camera2Manager.closeCamera()
                    textureViewRef = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val pingAnim = Animatable(0f)
                            val pingPair = offset to pingAnim
                            pings.add(pingPair)
                            coroutineScope.launch {
                                pingAnim.animateTo(1f, animationSpec = tween(600, easing = LinearOutSlowInEasing))
                                pings.remove(pingPair)
                            }
                            viewModel.requestHitTest(offset.x, offset.y)
                        }
                    }
            )

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

            // 3. Tracking Stability Warning Banner
            if (trackingStability.level == StabilityLevel.POOR) {
                ActiveTrackingStabilityWarningBanner(
                    stability = trackingStability,
                    onActionClick = { showStabilityDiagnosticsDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 96.dp, start = 16.dp, end = 16.dp)
                )
            }
            
            if (arCoreStatus != ArCoreEngineStatus.ACTIVE && arCoreStatus != ArCoreEngineStatus.UNSUPPORTED) {
                 // Non-intrusive status warning
                 Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                        .clickable { showSensorStatusDialog = true }
                 ) {
                     Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                         Icon(Icons.Rounded.HelpOutline, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                         Spacer(Modifier.width(8.dp))
                         Text("AR 功能受限，點擊檢查狀態", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                     }
                 }
            }

            // 4. Clean Minimal Top Bar
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
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // 4.5. Dynamic Silent Visual Guidance Pill
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
                    onOpenTutorial = { showHelpDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = if (trackingStability.level == StabilityLevel.POOR) 152.dp else 56.dp)
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
