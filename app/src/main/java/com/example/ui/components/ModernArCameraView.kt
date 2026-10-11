@file:androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.example.ui.components

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.TextureView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.logic.ar.ModernArGlView
import com.example.logic.ar.StabilityLevel
import com.example.logic.camera.ArVideoRecorder
import com.example.logic.camera.HighSpeedCamera2Manager
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class
)
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

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

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
    var modernArGlViewRef by remember { mutableStateOf<ModernArGlView?>(null) }

    val cameraAspectRatio by viewModel.cameraAspectRatio.collectAsState()
    val useDisplayP3ColorSpace by viewModel.useDisplayP3ColorSpace.collectAsState()
    val isLensDirtWarningEnabled by viewModel.isLensDirtWarningEnabled.collectAsState()
    val isLensSmudged by viewModel.isLensSmudged.collectAsState()
    val simulatedAlert by viewModel.simulatedAlert.collectAsState()
    val uiButtonScale by viewModel.uiButtonScale.collectAsState()
    val sensorTelemetry by viewModel.sensorTelemetry.collectAsState()

    // Video Recorder
    val videoRecorder = remember { ArVideoRecorder(context) }
    val isRecordingVideo by videoRecorder.isRecording.collectAsState()
    val recordingSeconds by videoRecorder.recordingSeconds.collectAsState()

    // Real-time background frame analyzer: throttled & offloaded to Dispatchers.Default
    LaunchedEffect(isLensDirtWarningEnabled) {
        if (!isLensDirtWarningEnabled) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val downscaledBmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            while (isActive) {
                delay(2600L)
                val currentTv = textureViewRef
                if (currentTv != null && currentTv.isAvailable) {
                    try {
                        withContext(Dispatchers.Main) {
                            currentTv.getBitmap(downscaledBmp)
                        }
                        if (isLensDirtWarningEnabled) {
                            viewModel.analyzeLensCleanliness(downscaledBmp)
                        }
                    } catch (_: Throwable) {
                        // Ignore transient frame capture errors
                    }
                }
            }
        }
    }

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

    // Lifecycle sync for ARCore & Camera
    DisposableEffect(lifecycleOwner) {
        viewModel.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onResume()
                    if (!viewModel.isArCoreSessionActive) {
                        textureViewRef?.let { tv ->
                            if (tv.isAvailable) {
                                val manager = HighSpeedCamera2Manager(context)
                                viewModel.setHighSpeedCamera2Manager(manager)
                                manager.openCameraAndStartSession(tv) { isHighSpeed, size ->
                                    android.util.Log.i("CameraView", "Session resumed. HighSpeed=$isHighSpeed, Size=${size.width}x${size.height}")
                                }
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPause()
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

            var didVibrate = false
            if (vibrator?.hasVibrator() == true) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val effectId = when (type) {
                            HapticType.SNAP -> VibrationEffect.EFFECT_TICK
                            HapticType.CLICK -> VibrationEffect.EFFECT_CLICK
                            HapticType.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                            HapticType.DOUBLE -> VibrationEffect.EFFECT_DOUBLE_CLICK
                        }
                        vibrator.vibrate(VibrationEffect.createPredefined(effectId))
                        didVibrate = true
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val duration = when (type) {
                            HapticType.SNAP -> 10L
                            HapticType.CLICK -> 20L
                            HapticType.HEAVY -> 45L
                            HapticType.DOUBLE -> 30L
                        }
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                        didVibrate = true
                    }
                } catch (_: Throwable) {
                    didVibrate = false
                }
            }

            if (!didVibrate) {
                val feedbackType = when (type) {
                    HapticType.SNAP, HapticType.CLICK -> androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                    HapticType.HEAVY, HapticType.DOUBLE -> androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                }
                haptic.performHapticFeedback(feedbackType)
            }
        }
    }

    // Reticle & HUD pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "ReticlePulse")
    val reticlePulseScale = infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reticlePulse"
    )
    val dashPhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
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

    if (!cameraPermissionState.status.isGranted) {
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
                    text = viewModel.getString("perm_camera_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = viewModel.getString("perm_camera_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(56.dp)
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(viewModel.getString("btn_grant_perm"), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        // Active AR Camera Viewport
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hardwareBackdropBlur(isArOverlayOpen, arOverlayBlurRadius)
        ) {
            // 1. OpenGL ARCore View or High-Speed Camera2 View
            val session = remember(cameraPermissionState.status.isGranted) {
                viewModel.modernArEngine.createSession()
            }

            if (session != null) {
                AndroidView(
                    factory = { ctx ->
                        ModernArGlView(
                            context = ctx,
                            session = session,
                            modernArEngine = viewModel.modernArEngine,
                            viewModel = viewModel
                        ).apply {
                            modernArGlViewRef = this
                            onResume()
                        }
                    },
                    update = { view ->
                        (view as? ModernArGlView)?.let {
                            modernArGlViewRef = it
                            it.onResume()
                        }
                    },
                    onRelease = { view ->
                        modernArGlViewRef = null
                        (view as? android.opengl.GLSurfaceView)?.onPause()
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
            } else {
                AndroidView(
                    factory = { ctx ->
                        val textureView = TextureView(ctx).apply {
                            isOpaque = false
                        }
                        textureViewRef = textureView
                        val camera2Manager = HighSpeedCamera2Manager(ctx)
                        viewModel.setHighSpeedCamera2Manager(camera2Manager)

                        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                camera2Manager.openCameraAndStartSession(textureView) { isHighSpeed, size ->
                                    android.util.Log.i("CameraView", "Session ready. HighSpeed=$isHighSpeed, Size=${size.width}x${size.height}")
                                }
                            }
                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                camera2Manager.closeCamera()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }

                        if (textureView.isAvailable) {
                            camera2Manager.openCameraAndStartSession(textureView) { isHighSpeed, size ->
                                android.util.Log.i("CameraView", "Session ready (immediate). HighSpeed=$isHighSpeed, Size=${size.width}x${size.height}")
                            }
                        }

                        textureView
                    },
                    onRelease = { _ ->
                        viewModel.closeHighSpeedCamera()
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
                    sensorTelemetry = sensorTelemetry,
                    planesCount = planesCount,
                    isRecordingVideo = isRecordingVideo,
                    recordingSeconds = recordingSeconds,
                    onShowHelp = { showHelpDialog = true },
                    onShowHistoryClick = onShowHistoryClick,
                    onShowSettingsClick = onShowSettingsClick,
                    modifier = Modifier.align(Alignment.TopCenter)
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
                    modernArGlViewRef = { modernArGlViewRef },
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
