@file:androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.ar.ArCoreEngineStatus
import com.example.logic.ar.ModernArEngine
import com.example.logic.vulkan.VulkanArSceneView
import com.example.logic.ar.StabilityLevel
import com.example.logic.camera.ArVideoRecorder
import com.example.logic.camera.HighSpeedCamera2Manager
import com.example.ui.adaptive.LocalAdaptiveWindowState
import com.example.ui.adaptive.rememberLocalHingeTracker
import com.example.ui.i18n.tr
import com.example.ui.viewmodel.MeasureViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernArCameraView(
    viewModel: MeasureViewModel,
    onShowHistoryClick: () -> Unit,
    onShowSettingsClick: () -> Unit,
    bottomPadding: Dp = 0.dp,
    isObscuredByFullscreenOverlay: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Reactive camera permission tracking with automatic lifecycle refresh
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val openAppSettings: () -> Unit = remember(context) {
        {
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
    }

    // Root-level lifecycle/visibility states only (high-frequency states are scoped to child hosts)
    val isTakingPhoto by viewModel.isTakingPhoto.collectAsStateWithLifecycle()
    val effectiveTargetFps by viewModel.effectiveTargetFps.collectAsStateWithLifecycle()
    val isCameraSimulationActive by viewModel.isCameraSimulationActive.collectAsStateWithLifecycle()

    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var modernArVulkanViewRef by remember { mutableStateOf<VulkanArSceneView?>(null) }

    // Dedicated single-instance Camera2 manager
    val camera2Manager = remember {
        HighSpeedCamera2Manager(context).also { manager ->
            viewModel.setHighSpeedCamera2Manager(manager)
        }
    }

    // Video Recorder
    val videoRecorder = remember { ArVideoRecorder(context) }

    val hostActivity = remember(context) { context.findActivity() }

    val startCameraAndArPipeline: (TextureView) -> Unit = remember(viewModel, camera2Manager, hostActivity) {
        { tv: TextureView ->
            coroutineScope.launch {
                viewModel.setHighSpeedCamera2Manager(camera2Manager)
                viewModel.ensureArCoreInitialized(
                    activity = hostActivity,
                    forceRetry = (viewModel.modernArEngine.session == null)
                )
                if (!tv.isAvailable) return@launch
                val rot = getDisplayRotation(context)
                if (tv.width > 0 && tv.height > 0) {
                    viewModel.updateDisplayGeometry(rot, tv.width, tv.height)
                    viewModel.modernArEngine.setDisplayGeometry(rot, tv.width, tv.height)
                }
                val sc = viewModel.modernArEngine.sharedCamera
                if (sc != null) {
                    camera2Manager.sharedCamera = sc
                    camera2Manager.preferredCameraId = viewModel.modernArEngine.selectedCameraId
                    camera2Manager.preferredPreviewSize = viewModel.modernArEngine.selectedCameraImageSize
                    camera2Manager.preferredAspectRatio = viewModel.modernArEngine.selectedCameraImageSize?.let {
                        if (it.height > 0) it.width.toFloat() / it.height.toFloat() else null
                    }
                    camera2Manager.preferredFpsRange = viewModel.modernArEngine.selectedCameraFpsRange
                    if (camera2Manager.isCameraOpenWithoutSharedCamera() && !camera2Manager.isFrontFacing) {
                        camera2Manager.reconnectWithSharedCamera(
                            sharedCam = sc,
                            cameraId = viewModel.modernArEngine.selectedCameraId,
                            imageSize = viewModel.modernArEngine.selectedCameraImageSize,
                            fpsRange = viewModel.modernArEngine.selectedCameraFpsRange,
                            onBeforeCloseCamera = { viewModel.modernArEngine.pause() }
                        ) { isHighSpeed, size ->
                            android.util.Log.i("CameraView", "Reconnected SharedCamera session. HighSpeed=$isHighSpeed, Size=${size.width}x${size.height}")
                            tv.post {
                                camera2Manager.configureTransform(tv, tv.width, tv.height)
                            }
                        }
                        return@launch
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
        if (hasCameraPermission && viewModel.modernArEngine.session == null) {
            viewModel.checkAndInitArCore(hostActivity, forceRetry = true)
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

    val currentIsObscured by rememberUpdatedState(isObscuredByFullscreenOverlay)

    LaunchedEffect(isObscuredByFullscreenOverlay, lifecycleOwner) {
        if (isObscuredByFullscreenOverlay) {
            modernArVulkanViewRef?.onPause()
            viewModel.onPause()
        } else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.onResume()
            modernArVulkanViewRef?.onResume()
        }
    }

    // Lifecycle sync for Camera2, ARCore & Vulkan loop
    DisposableEffect(lifecycleOwner) {
        if (!currentIsObscured) {
            viewModel.onResume()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    (context as? com.example.MainActivity)?.enforceHighDisplayRefreshRate(viewModel.effectiveTargetFps.value.toFloat())
                    hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (!currentIsObscured) {
                        viewModel.onResume()
                        modernArVulkanViewRef?.onResume()
                    }
                    if (hasCameraPermission) {
                        if (viewModel.isCameraSimulationActive.value && hasHardwareCamera(context)) {
                            viewModel.setCameraSimulationActive(false)
                        }
                        val tv = textureViewRef
                        if (tv != null && tv.isAvailable) {
                            startCameraAndArPipeline(tv)
                        } else if (viewModel.modernArEngine.session == null) {
                            viewModel.checkAndInitArCore(hostActivity, forceRetry = true)
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
            modernArVulkanViewRef?.destroy()
            modernArVulkanViewRef = null
            viewModel.pauseArAndCamera(camera2Manager)
            viewModel.onPause()
            viewModel.setHighSpeedCamera2Manager(null)
            viewModel.releaseVulkanPipelineBuffers()
            viewModel.closeHighSpeedCamera()
            if (videoRecorder.isRecording.value) {
                videoRecorder.stopRecording { _, _, _ -> }
            }
        }
    }

    // DisplayListener ensures 180-degree rotations (where width/height remain equal) still update ARCore & Camera2 transform
    DisposableEffect(context, camera2Manager) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                val tv = textureViewRef ?: return
                if (tv.width > 0 && tv.height > 0) {
                    val rot = getDisplayRotation(context)
                    viewModel.updateDisplayGeometry(rot, tv.width, tv.height)
                    viewModel.modernArEngine.setDisplayGeometry(rot, tv.width, tv.height)
                    tv.post {
                        camera2Manager.configureTransform(tv, tv.width, tv.height)
                    }
                }
            }
        }
        dm?.registerDisplayListener(displayListener, null)
        onDispose {
            dm?.unregisterDisplayListener(displayListener)
        }
    }

    // Modern Refined Haptic Feedback Handler (Google Pixel LRA Primitives)
    LaunchedEffect(Unit) {
        viewModel.hapticEvent.collect { type ->
            com.example.logic.ModernHapticFeedback.play(context, type)
        }
    }

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
                modifier = Modifier.widthIn(max = 460.dp),
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
                    text = tr("perm_camera_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = tr("perm_camera_desc"),
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
                    Text(tr("btn_grant_perm"), fontWeight = FontWeight.Bold)
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
                    Text(tr("btn_open_settings"), fontWeight = FontWeight.Medium)
                }
            }
        }
    } else {
        // Active AR Camera Viewport (Adapts to Flat, Tabletop, and Book foldable postures without tearing down AR/Camera)
        val adaptiveState = LocalAdaptiveWindowState.current
        val (hingeTrackerModifier, hingeMetrics) = rememberLocalHingeTracker(adaptiveState)
        val density = LocalDensity.current
        val arBackdropState = rememberGradientBlurBackdropState()

        val triggerShutterFlash: () -> Unit = remember(coroutineScope) {
            {
                isShutterFlash = true
                coroutineScope.launch {
                    delay(120)
                    isShutterFlash = false
                }
            }
        }

        val liveViewportContent = remember {
            movableContentOf {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .gradientBlurSource(arBackdropState)
                ) {
                    val viewportPxW = with(density) { maxWidth.roundToPx() }
                    val viewportPxH = with(density) { maxHeight.roundToPx() }
                    val currentRotation = getDisplayRotation(context)

                    LaunchedEffect(viewportPxW, viewportPxH, currentRotation, adaptiveState.posture) {
                        if (viewportPxW > 0 && viewportPxH > 0) {
                            viewModel.updateDisplayGeometry(currentRotation, viewportPxW, viewportPxH)
                            viewModel.modernArEngine.setDisplayGeometry(currentRotation, viewportPxW, viewportPxH)
                            textureViewRef?.let { tv ->
                                if (tv.width > 0 && tv.height > 0) {
                                    tv.post {
                                        camera2Manager.configureTransform(tv, tv.width, tv.height)
                                    }
                                }
                            }
                        }
                    }

                    // 0. ARCore / Vulkan 60/120 FPS Frame Driver Companion
                    AndroidView(
                        factory = { ctx ->
                            VulkanArSceneView(
                                context = ctx,
                                session = viewModel.arSession,
                                modernArEngine = viewModel.modernArEngine,
                                viewModel = viewModel
                            ).also {
                                it.setTargetFrameRate(effectiveTargetFps.toFloat())
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
                                        modernArVulkanViewRef?.onPause()
                                        viewModel.pauseArAndCamera(camera2Manager)
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }

                                textureView
                            },
                            update = { tv ->
                                textureViewRef = tv
                            },
                            onRelease = { _ ->
                                modernArVulkanViewRef?.onPause()
                                viewModel.pauseArAndCamera(camera2Manager)
                                textureViewRef = null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 2. 3D Augmented Overlay Canvas (Planes, Projected Points, Lines, Measurements, Reticle)
                    ArOverlayPulseHost(viewModel = viewModel)

                    // 3. Shutter Flash Effect
                    if (isShutterFlash) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.75f))
                        )
                    }
                }
            }
        }

        when {
            adaptiveState.isTabletop -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(hingeTrackerModifier)
                        .hardwareBackdropBlur(isArOverlayOpen, arOverlayBlurRadius)
                        .testTag("ar_tabletop_layout")
                ) {
                    val topHeight = hingeMetrics.topPaneHeightDp(density, fallbackFraction = 0.52f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topHeight)
                            .testTag("ar_tabletop_viewport_pane")
                    ) {
                        liveViewportContent()
                        if (!isTakingPhoto) {
                            ArViewportOverlayHudHost(
                                viewModel = viewModel,
                                videoRecorder = videoRecorder,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 8.dp)
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hingeMetrics.horizontalHingeGapDp(density))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .testTag("ar_tabletop_hinge_gap")
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("ar_tabletop_controls_pane")
                    ) {
                        if (!isTakingPhoto) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                ArTopHudHost(
                                    viewModel = viewModel,
                                    context = context,
                                    hostActivity = hostActivity,
                                    videoRecorder = videoRecorder,
                                    isCameraSimulationActive = isCameraSimulationActive,
                                    textureViewRef = { textureViewRef },
                                    startCameraAndArPipeline = startCameraAndArPipeline,
                                    onShowHistoryClick = onShowHistoryClick,
                                    onShowSettingsClick = onShowSettingsClick,
                                    onOpenTutorial = { showHelpDialog = true },
                                    onOpenStabilityDiagnostics = { showStabilityDiagnosticsDialog = true },
                                    applyStatusBarsPadding = false,
                                    showStatusOrRecHud = false
                                )

                                ArBottomDeckHost(
                                    viewModel = viewModel,
                                    bottomPadding = bottomPadding,
                                    textureViewRef = { textureViewRef },
                                    modernArVulkanViewRef = { modernArVulkanViewRef },
                                    videoRecorder = videoRecorder,
                                    onShowHistoryClick = onShowHistoryClick,
                                    onTriggerShutterFlash = triggerShutterFlash
                                )
                            }
                        }
                    }
                }
            }

            adaptiveState.isBook -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(hingeTrackerModifier)
                        .hardwareBackdropBlur(isArOverlayOpen, arOverlayBlurRadius)
                        .testTag("ar_book_layout")
                ) {
                    val leftWidth = hingeMetrics.leftPaneWidthDp(density, fallbackFraction = 0.52f)
                    Box(
                        modifier = Modifier
                            .width(leftWidth)
                            .fillMaxHeight()
                            .testTag("ar_book_viewport_pane")
                    ) {
                        liveViewportContent()
                        if (!isTakingPhoto) {
                            ArViewportOverlayHudHost(
                                viewModel = viewModel,
                                videoRecorder = videoRecorder,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 8.dp)
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .width(hingeMetrics.verticalHingeGapDp(density))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .testTag("ar_book_hinge_gap")
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("ar_book_controls_pane")
                    ) {
                        if (!isTakingPhoto) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                ArTopHudHost(
                                    viewModel = viewModel,
                                    context = context,
                                    hostActivity = hostActivity,
                                    videoRecorder = videoRecorder,
                                    isCameraSimulationActive = isCameraSimulationActive,
                                    textureViewRef = { textureViewRef },
                                    startCameraAndArPipeline = startCameraAndArPipeline,
                                    onShowHistoryClick = onShowHistoryClick,
                                    onShowSettingsClick = onShowSettingsClick,
                                    onOpenTutorial = { showHelpDialog = true },
                                    onOpenStabilityDiagnostics = { showStabilityDiagnosticsDialog = true },
                                    applyStatusBarsPadding = false,
                                    showStatusOrRecHud = false
                                )

                                ArBottomDeckHost(
                                    viewModel = viewModel,
                                    bottomPadding = bottomPadding,
                                    textureViewRef = { textureViewRef },
                                    modernArVulkanViewRef = { modernArVulkanViewRef },
                                    videoRecorder = videoRecorder,
                                    onShowHistoryClick = onShowHistoryClick,
                                    onTriggerShutterFlash = triggerShutterFlash
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hardwareBackdropBlur(isArOverlayOpen, arOverlayBlurRadius)
                        .testTag("ar_flat_layout")
                ) {
                    liveViewportContent()

                    if (!isTakingPhoto) {
                        GradientBlurScrim(
                            isTop = true,
                            backdropState = arBackdropState,
                            blurRadius = 16.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(84.dp)
                                .testTag("ar_top_gradient_blur_scrim")
                        )

                        ArTopHudHost(
                            viewModel = viewModel,
                            context = context,
                            hostActivity = hostActivity,
                            videoRecorder = videoRecorder,
                            isCameraSimulationActive = isCameraSimulationActive,
                            textureViewRef = { textureViewRef },
                            startCameraAndArPipeline = startCameraAndArPipeline,
                            onShowHistoryClick = onShowHistoryClick,
                            onShowSettingsClick = onShowSettingsClick,
                            onOpenTutorial = { showHelpDialog = true },
                            onOpenStabilityDiagnostics = { showStabilityDiagnosticsDialog = true },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

                        GradientBlurScrim(
                            isTop = false,
                            backdropState = arBackdropState,
                            blurRadius = 18.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(104.dp + bottomPadding)
                                .testTag("ar_bottom_gradient_blur_scrim")
                        )

                        ArBottomDeckHost(
                            viewModel = viewModel,
                            bottomPadding = bottomPadding,
                            textureViewRef = { textureViewRef },
                            modernArVulkanViewRef = { modernArVulkanViewRef },
                            videoRecorder = videoRecorder,
                            onShowHistoryClick = onShowHistoryClick,
                            onTriggerShutterFlash = triggerShutterFlash,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    // 7. AR Dialogs
    ArDialogsHost(
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
        onDismissStabilityDiagnostics = { showStabilityDiagnosticsDialog = false }
    )
}

@Composable
private fun ArOverlayPulseHost(
    viewModel: MeasureViewModel
) {
    val isSnapped by viewModel.isSnapped.collectAsStateWithLifecycle()
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
    val snapScaleAnimated = animateFloatAsState(
        targetValue = if (isSnapped) 1.20f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "snapScaleAnimated"
    )

    ArMeasurementCanvasOverlay(
        viewModel = viewModel,
        dashPhase = dashPhase,
        reticlePulseScale = reticlePulseScale,
        snapScaleAnimated = snapScaleAnimated
    )
}

@Composable
private fun ArViewportOverlayHudHost(
    viewModel: MeasureViewModel,
    videoRecorder: ArVideoRecorder,
    modifier: Modifier = Modifier
) {
    val planesCount by viewModel.arPlanesCount.collectAsStateWithLifecycle()
    val isRecordingVideo by videoRecorder.isRecording.collectAsStateWithLifecycle()
    val recordingSeconds by videoRecorder.recordingSeconds.collectAsStateWithLifecycle()

    Box(modifier = modifier) {
        if (isRecordingVideo) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE53935)),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            ArDetectedPlanesIndicator(planesCount = planesCount)
        }
    }
}

@Composable
private fun ArTopHudHost(
    viewModel: MeasureViewModel,
    context: Context,
    hostActivity: android.app.Activity?,
    videoRecorder: ArVideoRecorder,
    isCameraSimulationActive: Boolean,
    textureViewRef: () -> TextureView?,
    startCameraAndArPipeline: (TextureView) -> Unit,
    onShowHistoryClick: () -> Unit,
    onShowSettingsClick: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenStabilityDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarsPadding: Boolean = true,
    showStatusOrRecHud: Boolean = true
) {
    val subMode by viewModel.cameraSubMode.collectAsStateWithLifecycle()
    val autoDetectedType by viewModel.autoDetectedType.collectAsStateWithLifecycle()
    val selectedUnit by viewModel.selectedUnit.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val torchBrightness by viewModel.torchBrightness.collectAsStateWithLifecycle()
    val cameraAspectRatio by viewModel.cameraAspectRatio.collectAsStateWithLifecycle()
    val planesCount by viewModel.arPlanesCount.collectAsStateWithLifecycle()
    val isRecordingVideo by videoRecorder.isRecording.collectAsStateWithLifecycle()
    val recordingSeconds by videoRecorder.recordingSeconds.collectAsStateWithLifecycle()
    val isMeasurementAvailable by viewModel.isMeasurementAvailable.collectAsStateWithLifecycle()
    val isSnapped by viewModel.isSnapped.collectAsStateWithLifecycle()
    val arCoreStatus by viewModel.arCoreStatus.collectAsStateWithLifecycle()
    val arCoreStatusMessage by viewModel.arCoreStatusMessage.collectAsStateWithLifecycle()
    val trackingStability by viewModel.trackingStability.collectAsStateWithLifecycle()

    val capturedPoints = viewModel.capturedPoints
    val hasCapturedPoints by remember { derivedStateOf { capturedPoints.isNotEmpty() } }
    val isWaitingForSecondPoint by remember(subMode) {
        derivedStateOf {
            when (subMode) {
                1, 4, 5 -> capturedPoints.size == 1 || capturedPoints.size == 2
                else -> capturedPoints.size % 2 == 1
            }
        }
    }

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
        onShowHistoryClick = onShowHistoryClick,
        onShowSettingsClick = onShowSettingsClick,
        modifier = modifier,
        applyStatusBarsPadding = applyStatusBarsPadding,
        showStatusOrRecHud = showStatusOrRecHud,
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
                onOpenTutorial = onOpenTutorial,
                arCoreStatus = arCoreStatus,
                arCoreStatusMessage = arCoreStatusMessage
            )

            if (arCoreStatus != ArCoreEngineStatus.ACTIVE) {
                ArCoreRequirementBanner(
                    status = arCoreStatus,
                    message = arCoreStatusMessage,
                    onInstallClick = {
                        if (hostActivity != null) {
                            viewModel.requestInstallArCore(hostActivity)
                        } else {
                            ModernArEngine.openArCorePlayStorePage(context)
                        }
                    },
                    onRetryClick = {
                        if (isCameraSimulationActive && hasHardwareCamera(context)) {
                            viewModel.setCameraSimulationActive(false)
                        }
                        val tv = textureViewRef()
                        if (tv != null && tv.isAvailable) {
                            startCameraAndArPipeline(tv)
                        } else {
                            viewModel.checkAndInitArCore(hostActivity, forceRetry = true)
                        }
                    }
                )
            } else if (trackingStability.level == StabilityLevel.POOR) {
                ActiveTrackingStabilityWarningBanner(
                    stability = trackingStability,
                    onActionClick = onOpenStabilityDiagnostics
                )
            }
        }
    )
}

@Composable
private fun ArBottomDeckHost(
    viewModel: MeasureViewModel,
    bottomPadding: Dp,
    textureViewRef: () -> TextureView?,
    modernArVulkanViewRef: () -> VulkanArSceneView?,
    videoRecorder: ArVideoRecorder,
    onShowHistoryClick: () -> Unit,
    onTriggerShutterFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subMode by viewModel.cameraSubMode.collectAsStateWithLifecycle()
    val selectedUnit by viewModel.selectedUnit.collectAsStateWithLifecycle()
    val isMeasurementAvailable by viewModel.isMeasurementAvailable.collectAsStateWithLifecycle()
    val uiButtonScale by viewModel.uiButtonScale.collectAsStateWithLifecycle()
    val cameraAspectRatio by viewModel.cameraAspectRatio.collectAsStateWithLifecycle()
    val useDisplayP3ColorSpace by viewModel.useDisplayP3ColorSpace.collectAsStateWithLifecycle()
    val isRecordingVideo by videoRecorder.isRecording.collectAsStateWithLifecycle()

    val capturedPoints = viewModel.capturedPoints
    val hasCapturedPoints by remember { derivedStateOf { capturedPoints.isNotEmpty() } }
    val isWaitingForSecondPoint by remember(subMode) {
        derivedStateOf {
            when (subMode) {
                1, 4, 5 -> capturedPoints.size == 1 || capturedPoints.size == 2
                else -> capturedPoints.size % 2 == 1
            }
        }
    }

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
        textureViewRef = textureViewRef,
        modernArVulkanViewRef = modernArVulkanViewRef,
        cameraAspectRatio = cameraAspectRatio,
        useDisplayP3ColorSpace = useDisplayP3ColorSpace,
        videoRecorder = videoRecorder,
        onShowHistoryClick = onShowHistoryClick,
        onTriggerShutterFlash = onTriggerShutterFlash,
        modifier = modifier
    )
}

@Composable
private fun ArDialogsHost(
    viewModel: MeasureViewModel,
    context: Context,
    showHelpDialog: Boolean,
    onDismissHelp: () -> Unit,
    showSensorStatusDialog: Boolean,
    onDismissSensorStatus: () -> Unit,
    showLensCleaningDialog: Boolean,
    onDismissLensCleaning: () -> Unit,
    showLowLightDialog: Boolean,
    onDismissLowLight: () -> Unit,
    showMotionDialog: Boolean,
    onDismissMotion: () -> Unit,
    showFeatureDialog: Boolean,
    onDismissFeature: () -> Unit,
    showStabilityDiagnosticsDialog: Boolean,
    onDismissStabilityDiagnostics: () -> Unit
) {
    val trackingStability by viewModel.trackingStability.collectAsStateWithLifecycle()
    val isLensSmudged by viewModel.isLensSmudged.collectAsStateWithLifecycle()
    val simulatedAlert by viewModel.simulatedAlert.collectAsStateWithLifecycle()

    ModernArDialogs(
        viewModel = viewModel,
        context = context,
        showHelpDialog = showHelpDialog,
        onDismissHelp = onDismissHelp,
        showSensorStatusDialog = showSensorStatusDialog,
        onDismissSensorStatus = onDismissSensorStatus,
        showLensCleaningDialog = showLensCleaningDialog,
        onDismissLensCleaning = onDismissLensCleaning,
        showLowLightDialog = showLowLightDialog,
        onDismissLowLight = onDismissLowLight,
        showMotionDialog = showMotionDialog,
        onDismissMotion = onDismissMotion,
        showFeatureDialog = showFeatureDialog,
        onDismissFeature = onDismissFeature,
        showStabilityDiagnosticsDialog = showStabilityDiagnosticsDialog,
        onDismissStabilityDiagnostics = onDismissStabilityDiagnostics,
        trackingStability = trackingStability,
        isLensSmudged = isLensSmudged,
        simulatedAlert = simulatedAlert
    )
}

@Composable
private fun ArCoreRequirementBanner(
    status: ArCoreEngineStatus,
    message: String,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMissing = status == ArCoreEngineStatus.NEEDS_INSTALLATION
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isMissing) Color(0xFFF59E0B) else MaterialTheme.colorScheme.error
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag("arcore_status_banner")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isMissing) Icons.Rounded.DownloadForOffline else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = if (isMissing) Color(0xFFF59E0B) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = tr(if (isMissing) "arcore_install_title" else "arcore_not_ready_title"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (isMissing) {
                    tr("arcore_install_desc")
                } else {
                    message.ifBlank { tr("arcore_not_ready_desc") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMissing) {
                    Button(
                        onClick = onInstallClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("install_arcore_button")
                    ) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("arcore_install_btn"), fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onRetryClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("retry_arcore_button")
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("arcore_retry_btn"), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? {
    return when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun getDisplayRotation(context: Context): Int {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.display
        } catch (_: UnsupportedOperationException) {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
        }
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
    }
    return display?.rotation ?: android.view.Surface.ROTATION_0
}

private fun hasHardwareCamera(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
        cm?.cameraIdList?.isNotEmpty() == true
    } catch (_: Throwable) {
        false
    }
}
