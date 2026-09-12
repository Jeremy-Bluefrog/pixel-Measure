@file:androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.example.ui.components

import android.Manifest
import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.TextureView
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.WallMeasurementInfo
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.logic.ShareUtility
import com.example.logic.ai.DetectedTile
import com.example.logic.ai.Objectron3DBox
import com.example.logic.ai.ObjectronEngine
import com.example.logic.ai.SegmentedObject
import com.example.logic.ar.ArMath
import com.example.logic.ar.ArTrackingStability
import com.example.logic.ar.ModernArGlView
import com.example.logic.ar.StabilityLevel
import com.example.logic.camera.HighSpeedCamera2Manager
import com.example.ui.components.TileDetailBottomSheet
import com.example.ui.viewmodel.MeasureViewModel
import com.example.ui.viewmodel.Point3D
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.ar.core.TrackingFailureReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.google.ar.core.TrackingState
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import kotlin.math.*

@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class
)
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
@Composable
fun ModernArCameraView(
    viewModel: MeasureViewModel,
    onShowHistoryClick: () -> Unit,
    onShowSettingsClick: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Observe ViewModel states
    val trackingState by viewModel.arTrackingState.collectAsState()
    val trackingFailureReason by viewModel.trackingFailureReason.collectAsState()
    val trackingStability by viewModel.trackingStability.collectAsState()
    val isDepthAvailable by viewModel.isDepthAvailable.collectAsState()
    val planesCount by viewModel.arPlanesCount.collectAsState()
    val surfaceTypeAtCenter by viewModel.surfaceTypeAtCenter.collectAsState()
    val detectedPlanes by viewModel.detectedPlanes.collectAsState()
    val viewMatrixState = viewModel.viewMatrix.collectAsState()
    val projectionMatrixState = viewModel.projectionMatrix.collectAsState()
    val subMode by viewModel.cameraSubMode.collectAsState()
    val autoDetectedType by viewModel.autoDetectedType.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val liveDistanceMetersState = viewModel.liveDistanceMeters.collectAsState()
    val liveTargetPointState = viewModel.liveTargetPoint.collectAsState()
    val isSnapped by viewModel.isSnapped.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val torchBrightness by viewModel.torchBrightness.collectAsState()
    var showTorchBrightnessMenu by remember { mutableStateOf(false) }
    val showPointCloud by viewModel.showPointCloud.collectAsState()
    val capturedPoints = viewModel.capturedPoints
    val sensorTelemetryState = viewModel.sensorTelemetry.collectAsState()
    val sensorCorrectionEnabled by viewModel.sensorCorrectionEnabled.collectAsState()
    val highFpsModeEnabled by viewModel.highFpsModeEnabled.collectAsState()
    val isObjectronMode by viewModel.isObjectronMode.collectAsState()
    val objectron3DBox by viewModel.objectron3DBox.collectAsState()
    val isMobileSamMode by viewModel.isMobileSamMode.collectAsState()
    val segmentedObject by viewModel.segmentedObject.collectAsState()
    val isAiTileMode by viewModel.isAiTileMode.collectAsState()
    val isAiTileAnalyzing by viewModel.isAiTileAnalyzing.collectAsState()
    val detectedTiles by viewModel.detectedTiles.collectAsState()
    val activeTilePreset by viewModel.activeTilePreset.collectAsState()
    val selectedTileForDetail by viewModel.selectedTileForDetail.collectAsState()
    var showTileDetailSheet by remember { mutableStateOf(false) }
    val revealedTileIds = remember { mutableStateMapOf<String, Boolean>() }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    // Simultaneous Wall Measurement state
    val isSimultaneousWallMeasureActive by viewModel.isSimultaneousWallMeasureActive.collectAsState()
    val detectedWalls by viewModel.detectedWalls.collectAsState()

    // AR Measurement Video Recorder (Tap photo, Long-press video recording)
    val videoRecorder = remember { com.example.logic.camera.ArVideoRecorder(context) }
    val isRecordingVideo by videoRecorder.isRecording.collectAsState()
    val recordingSeconds by videoRecorder.recordingSeconds.collectAsState()

    // Real-time background frame analyzer: processes live camera feed for genuine tile edges/grids
    LaunchedEffect(isAiTileMode) {
        if (!isAiTileMode) return@LaunchedEffect
        while (isActive) {
            delay(1000)
            val currentTv = textureViewRef
            if (currentTv != null && currentTv.isAvailable) {
                try {
                    val bmp = currentTv.bitmap
                    if (bmp != null) {
                        viewModel.processFrameForTiles(bmp)
                    }
                } catch (e: Throwable) {
                    // Ignore transient frame capture errors
                }
            }
        }
    }

    // Touch ripple visual pings
    val pings = remember { mutableStateListOf<Pair<Offset, Animatable<Float, AnimationVector1D>>>() }

    // Dialogs & Guidance Overlay
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSensorStatusDialog by remember { mutableStateOf(false) }
    var showStabilityDiagnosticsDialog by remember { mutableStateOf(false) }
    var showPlaneGuidanceOverlay by remember { mutableStateOf(true) }
    var showAiToolsMenu by remember { mutableStateOf(false) }

    // Lifecycle sync for ARCore & Camera
    DisposableEffect(lifecycleOwner) {
        viewModel.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPause()
            if (videoRecorder.isRecording.value) {
                videoRecorder.stopRecording { _, _, _ -> }
            }
        }
    }

    // Modern Refined Haptic Feedback Handler (Pure Android 12+ / Modern Device API)
    LaunchedEffect(Unit) {
        viewModel.hapticEvent.collect { type ->
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            if (vibrator?.hasVibrator() == true) {
                val effectId = when (type) {
                    HapticType.SNAP -> VibrationEffect.EFFECT_TICK
                    HapticType.CLICK -> VibrationEffect.EFFECT_CLICK
                    HapticType.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                    HapticType.DOUBLE -> VibrationEffect.EFFECT_DOUBLE_CLICK
                }
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            } else {
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
    val reticlePulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reticlePulse"
    )
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashPhase"
    )
    val planeLockedParticleAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "planeLockedParticle"
    )

    // Redesigned Reactive Spring Animations for Target Snapping & Lock (Ultra-Smooth & Responsive)
    val snapScaleAnimated by animateFloatAsState(
        targetValue = if (isSnapped) 1.20f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "snapScaleAnimated"
    )
    val snapGlowAlphaAnimated by animateFloatAsState(
        targetValue = if (isSnapped) 0.9f else 0.15f,
        animationSpec = tween(220, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
        label = "snapGlowAlphaAnimated"
    )
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnPrimary = MaterialTheme.colorScheme.onPrimary
    val colorPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val colorOnPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val colorSecondary = MaterialTheme.colorScheme.secondary
    val colorOnSecondary = MaterialTheme.colorScheme.onSecondary
    val colorTertiary = MaterialTheme.colorScheme.tertiary
    val colorOnTertiary = MaterialTheme.colorScheme.onTertiary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorSurfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val colorSurfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val colorSurfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val colorOutline = MaterialTheme.colorScheme.outline
    val colorOutlineVariant = MaterialTheme.colorScheme.outlineVariant
    val colorError = MaterialTheme.colorScheme.error
    val colorOnError = MaterialTheme.colorScheme.onError

    val isMeasurementAvailable by remember { derivedStateOf { trackingState == com.google.ar.core.TrackingState.TRACKING && liveTargetPointState.value != null } }

    val reticleColorAnimated by animateColorAsState(
        targetValue = when {
            !isMeasurementAvailable -> Color(0xFF8E8E93)
            trackingStability.isDriftRisk -> colorError
            isSnapped -> colorTertiary
            else -> colorPrimary
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "reticleColorAnimated"
    )

    val addFabContainerColor by animateColorAsState(
        targetValue = if (isMeasurementAvailable) colorPrimary else Color(0xFF3C3C3E),
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "addFabContainerColor"
    )

    val addFabIconColor by animateColorAsState(
        targetValue = if (isMeasurementAvailable) colorOnPrimary else Color(0xFF8E8E93),
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "addFabIconColor"
    )

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
                    color = colorPrimary.copy(alpha = 0.12f),
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = colorPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = viewModel.getString("perm_camera_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorOnSurface,
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
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. OpenGL ARCore 60 FPS View with Point Cloud
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
                            onResume()
                        }
                    },
                    update = { view ->
                        (view as? ModernArGlView)?.onResume()
                    },
                    onRelease = { view ->
                        (view as? android.opengl.GLSurfaceView)?.onPause()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)

                                // Trigger tactile ping ripple
                                val pingAnim = Animatable(0f)
                                val pingPair = offset to pingAnim
                                pings.add(pingPair)
                                coroutineScope.launch {
                                    pingAnim.animateTo(1f, animationSpec = tween(600, easing = LinearOutSlowInEasing))
                                    pings.remove(pingPair)
                                }

                                if (isMobileSamMode) {
                                    val screenW = localView.width.takeIf { it > 0 }?.toFloat() ?: 1080f
                                    val screenH = localView.height.takeIf { it > 0 }?.toFloat() ?: 1920f
                                    viewModel.triggerSamSegmentationAtTap(offset, screenW, screenH)
                                } else {
                                    // Request hit-test at tap coordinates
                                    viewModel.requestHitTest(offset.x, offset.y)
                                }
                            }
                        }
                )
            } else {
                // High-Performance Camera2 High-Speed / 60 FPS Direct Hardware Surface Composition
                // Step 1: ConstrainedHighSpeedCaptureSession (dedicated 60Hz HAL pipeline)
                // Step 2: Optimal resolution downscaling (1080p/720p) to satisfy HAL bandwidth limits
                // Step 3: Scene mode disabled & 16.6ms exposure clamp to prevent 30 FPS drop in dim conditions
                AndroidView(
                    factory = { ctx ->
                        val textureView = TextureView(ctx)
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

                        // If SurfaceTexture is already available immediately on creation/reattachment
                        if (textureView.isAvailable) {
                            camera2Manager.openCameraAndStartSession(textureView) { isHighSpeed, size ->
                                android.util.Log.i("CameraView", "Session ready (immediate). HighSpeed=$isHighSpeed, Size=${size.width}x${size.height}")
                            }
                        }

                        textureView
                    },
                    onRelease = { view ->
                        textureViewRef = null
                        viewModel.setHighSpeedCamera2Manager(null)
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

                                if (isMobileSamMode) {
                                    val screenW = localView.width.takeIf { it > 0 }?.toFloat() ?: 1080f
                                    val screenH = localView.height.takeIf { it > 0 }?.toFloat() ?: 1920f
                                    viewModel.triggerSamSegmentationAtTap(offset, screenW, screenH)
                                } else {
                                    viewModel.requestHitTest(offset.x, offset.y)
                                }
                            }
                        }
                )
            }

            // 2. 3D Augmented Overlay Canvas (Planes, Projected Points, Lines, Measurements, Pings, Reticle)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val screenW = size.width.toInt()
                val screenH = size.height.toInt()
                val screenCenter = Offset(size.width / 2f, size.height / 2f)

                // Calculate real-time projected reticle position from 3D live target point
                val liveTarget = liveTargetPointState.value
                val projectedReticle = if (liveTarget != null && viewMatrixState.value.size >= 16 && projectionMatrixState.value.size >= 16) {
                    ArMath.projectWorldToScreen(liveTarget, viewMatrixState.value, projectionMatrixState.value, screenW, screenH)
                } else null

                val currentReticlePos = if (projectedReticle != null &&
                    projectedReticle.first in (-120f)..(size.width + 120f) &&
                    projectedReticle.second in (-120f)..(size.height + 120f)
                ) {
                    Offset(projectedReticle.first, projectedReticle.second)
                } else {
                    screenCenter
                }

                // Draw touch ripples
                pings.forEach { (offset, anim) ->
                    val progress = anim.value
                    drawCircle(
                        color = colorPrimary.copy(alpha = 0.5f * (1f - progress)),
                        center = offset,
                        radius = 8.dp.toPx() + 36.dp.toPx() * progress,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                // Project 3D points to 2D screen positions
                val projectedPoints = capturedPoints.map { pt ->
                    ArMath.projectWorldToScreen(pt, viewMatrixState.value, projectionMatrixState.value, screenW, screenH)
                }

                // 2B. Draw confirmed connecting 3D virtual lines
                // 兩點成一線，不要有共用的點：每兩點獨立成一線段 (step = 2)
                val isAreaMode = subMode == 1 || (subMode == 0 && autoDetectedType == "AREA")
                val stepVal = if (isAreaMode) 1 else 2
                if (projectedPoints.size >= 2) {
                    for (i in 0 until projectedPoints.size - 1 step stepVal) {
                        val p1 = projectedPoints[i]
                        val p2 = projectedPoints[i + 1]
                        if (p1 != null && p2 != null) {
                            val startOffset = Offset(p1.first, p1.second)
                            val endOffset = Offset(p2.first, p2.second)
                            val dx = endOffset.x - startOffset.x
                            val dy = endOffset.y - startOffset.y
                            val segLen = sqrt(dx * dx + dy * dy)

                            // 1. Ambient dark drop shadow for maximum contrast
                            drawLine(
                                color = Color.Black.copy(alpha = 0.45f),
                                start = Offset(startOffset.x + 1f, startOffset.y + 2f),
                                end = Offset(endOffset.x + 1f, endOffset.y + 2f),
                                strokeWidth = 7.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 2. Luminous glow halo
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.35f),
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = 8.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 3. Core solid laser line
                            drawLine(
                                color = colorPrimary,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = 4.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 4. Perpendicular dimension ticks (Blueprint end-caps & scale ticks)
                            if (segLen > 20f) {
                                val nx = -dy / segLen
                                val ny = dx / segLen
                                val tickHalfLen = 9.dp.toPx()

                                // End-cap at Start Point
                                drawLine(
                                    color = Color.White,
                                    start = Offset(startOffset.x - nx * tickHalfLen, startOffset.y - ny * tickHalfLen),
                                    end = Offset(startOffset.x + nx * tickHalfLen, startOffset.y + ny * tickHalfLen),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // End-cap at End Point
                                drawLine(
                                    color = Color.White,
                                    start = Offset(endOffset.x - nx * tickHalfLen, endOffset.y - ny * tickHalfLen),
                                    end = Offset(endOffset.x + nx * tickHalfLen, endOffset.y + ny * tickHalfLen),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // Holographic ruler scale hash marks along the segment
                                val step = 28f
                                var d = step
                                while (d < segLen - step) {
                                    val px = startOffset.x + (dx / segLen) * d
                                    val py = startOffset.y + (dy / segLen) * d
                                    val subTickLen = 4.dp.toPx()
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.7f),
                                        start = Offset(px - nx * subTickLen, py - ny * subTickLen),
                                        end = Offset(px + nx * subTickLen, py + ny * subTickLen),
                                        strokeWidth = 1.8.dp.toPx()
                                    )
                                    d += step
                                }
                            }
                        }
                    }

                    // Closed Polygon in Area mode or Auto-Detected Area
                    val isArea = subMode == 1 || (subMode == 0 && autoDetectedType == "AREA")
                    if (isArea && projectedPoints.size >= 3) {
                        val first = projectedPoints.first()
                        val last = projectedPoints.last()
                        if (first != null && last != null) {
                            drawLine(
                                color = colorPrimary.copy(alpha = 0.85f),
                                start = Offset(last.first, last.second),
                                end = Offset(first.first, first.second),
                                strokeWidth = 3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), dashPhase)
                            )
                        }
                    }
                }

                // 2C. Draw active dynamic virtual line from last anchor point to current dynamic 3D surface reticle
                // 兩點成一線：僅在奇數個點（正在延伸該線段的終點）時繪製動態虛線
                val isActivelyDrawingLine = projectedPoints.size % 2 == 1
                if (isActivelyDrawingLine && projectedPoints.isNotEmpty()) {
                    val lastPt = projectedPoints.last()
                    if (lastPt != null) {
                        val startOffset = Offset(lastPt.first, lastPt.second)
                        val dx = currentReticlePos.x - startOffset.x
                        val dy = currentReticlePos.y - startOffset.y
                        val liveLen = sqrt(dx * dx + dy * dy)

                        // 1. Shadow under active line
                        drawLine(
                            color = Color.Black.copy(alpha = 0.4f),
                            start = Offset(startOffset.x + 1f, startOffset.y + 2f),
                            end = Offset(currentReticlePos.x + 1f, currentReticlePos.y + 2f),
                            strokeWidth = 7.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // 2. Luminous animated laser stream
                        drawLine(
                            color = colorPrimary.copy(alpha = 0.35f),
                            start = startOffset,
                            end = currentReticlePos,
                            strokeWidth = 8.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // 3. Flowing dynamic fine dashed scale line
                        drawLine(
                            color = colorPrimary,
                            start = startOffset,
                            end = currentReticlePos,
                            strokeWidth = 4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), dashPhase),
                            cap = StrokeCap.Round
                        )

                        // Solid endpoint cap at the moving end (currentReticlePos)
                        drawCircle(
                            color = Color.White,
                            center = currentReticlePos,
                            radius = 5.dp.toPx()
                        )
                        drawCircle(
                            color = colorSecondary,
                            center = currentReticlePos,
                            radius = 2.5.dp.toPx()
                        )

                        // 4. Live perpendicular fine scale tick marks along the dynamic active connection path
                        if (liveLen > 25f) {
                            val nx = -dy / liveLen
                            val ny = dx / liveLen
                            val tickHalfLen = 6.dp.toPx()

                            // End tick at start point
                            drawLine(
                                color = Color.White,
                                start = Offset(startOffset.x - nx * tickHalfLen, startOffset.y - ny * tickHalfLen),
                                end = Offset(startOffset.x + nx * tickHalfLen, startOffset.y + ny * tickHalfLen),
                                strokeWidth = 2.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Fine dashed scale ticks along the live path
                            val step = 24f
                            var d = step
                            while (d < liveLen - step) {
                                val px = startOffset.x + (dx / liveLen) * d
                                val py = startOffset.y + (dy / liveLen) * d
                                val subTickLen = 3f.dp.toPx()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.75f),
                                    start = Offset(px - nx * subTickLen, py - ny * subTickLen),
                                    end = Offset(px + nx * subTickLen, py + ny * subTickLen),
                                    strokeWidth = 1.4.dp.toPx()
                                )
                                d += step
                            }
                        }
                    }
                }

                // 2D. Draw MediaPipe Objectron 3D Bounding Box Wireframe & Oriented Cube
                if (isObjectronMode && objectron3DBox != null) {
                    val box = objectron3DBox!!
                    val boxScreenCorners = box.corners.map { cornerPt ->
                        ArMath.projectWorldToScreen(cornerPt, viewMatrixState.value, projectionMatrixState.value, screenW, screenH)
                    }

                    val boxCyan = colorPrimary
                    val boxAmber = colorTertiary

                    // Draw 12 Wireframe Edges
                    ObjectronEngine.WIREFRAME_EDGES.forEach { (i1, i2) ->
                        val p1 = boxScreenCorners.getOrNull(i1)
                        val p2 = boxScreenCorners.getOrNull(i2)
                        if (p1 != null && p2 != null) {
                            // Bottom face (0,1,2,3) in cyan, Top face (4,5,6,7) in amber, vertical pillars in white/cyan
                            val edgeColor = when {
                                i1 < 4 && i2 < 4 -> boxCyan
                                i1 >= 4 && i2 >= 4 -> boxAmber
                                else -> Color.White.copy(alpha = 0.85f)
                            }
                            drawLine(
                                color = edgeColor,
                                start = Offset(p1.first, p1.second),
                                end = Offset(p2.first, p2.second),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Draw 8 Vertex Keypoints
                    boxScreenCorners.forEachIndexed { vIdx, proj ->
                        if (proj != null) {
                            val vOffset = Offset(proj.first, proj.second)
                            val isTopVertex = vIdx >= 4
                            val vColor = if (isTopVertex) boxAmber else boxCyan

                            drawCircle(
                                color = Color.Black.copy(alpha = 0.5f),
                                center = Offset(vOffset.x, vOffset.y + 1.5f),
                                radius = 5.dp.toPx()
                            )
                            drawCircle(
                                color = Color.White,
                                center = vOffset,
                                radius = 4.5.dp.toPx()
                            )
                            drawCircle(
                                color = vColor,
                                center = vOffset,
                                radius = 3.dp.toPx()
                            )
                        }
                    }

                    // Draw Center Ground Projection Reticle
                    val centerProj = ArMath.projectWorldToScreen(box.center, viewMatrixState.value, projectionMatrixState.value, screenW, screenH)
                    if (centerProj != null) {
                        val cOffset = Offset(centerProj.first, centerProj.second)
                        drawCircle(
                            color = boxCyan.copy(alpha = 0.35f * reticlePulseScale),
                            center = cOffset,
                            radius = (16.dp * reticlePulseScale).toPx(),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                // 2D-2. Draw MobileSAM / FastSAM Segment Anything Mask & Boundary Polyline
                if (isMobileSamMode && segmentedObject != null) {
                    val seg = segmentedObject!!
                    if (seg.contour2D.size >= 3) {
                        val samPath = Path().apply {
                            moveTo(seg.contour2D[0].x, seg.contour2D[0].y)
                            for (i in 1 until seg.contour2D.size) {
                                lineTo(seg.contour2D[i].x, seg.contour2D[i].y)
                            }
                            close()
                        }
                        val samEmerald = colorPrimary
                        val samCyan = colorSecondary

                        // Translucent radial gradient fill mask
                        drawPath(
                            path = samPath,
                            brush = Brush.radialGradient(
                                colors = listOf(samEmerald.copy(alpha = 0.35f), samCyan.copy(alpha = 0.12f)),
                                center = seg.promptPoint,
                                radius = 220.dp.toPx()
                            )
                        )

                        // Glowing neon border stroke with dashes
                        drawPath(
                            path = samPath,
                            color = samEmerald,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f), 0f)
                            )
                        )

                        // Vertex pinpoints
                        seg.contour2D.forEach { pt ->
                            drawCircle(color = Color.White, center = pt, radius = 4.dp.toPx())
                            drawCircle(color = samEmerald, center = pt, radius = 2.5.dp.toPx())
                        }

                        // Prompt point radar beacon
                        drawCircle(
                            color = Color.White,
                            center = seg.promptPoint,
                            radius = 5.dp.toPx()
                        )
                        drawCircle(
                            color = samEmerald,
                            center = seg.promptPoint,
                            radius = (14.dp * reticlePulseScale).toPx(),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // 2F. Draw AI Detected Tiles AR Bounding Frame (Only if tiles are genuinely detected)
                if (detectedTiles.isNotEmpty()) {
                    val tilesToDraw = detectedTiles

                    val tileGold = colorPrimary
                    val tileCyan = colorSecondary

                    tilesToDraw.forEach { tile ->
                        val leftPx = tile.leftNorm * screenW
                        val topPx = tile.topNorm * screenH
                        val rightPx = tile.rightNorm * screenW
                        val bottomPx = tile.bottomNorm * screenH

                        val tWidth = rightPx - leftPx
                        val tHeight = bottomPx - topPx
                        val isRevealed = revealedTileIds[tile.id] == true

                        if (tWidth > 30f && tHeight > 30f) {
                            if (isRevealed) {
                                // Translucent Surface Fill when revealed
                                drawRoundRect(
                                    color = tileGold.copy(alpha = 0.16f),
                                    topLeft = Offset(leftPx, topPx),
                                    size = androidx.compose.ui.geometry.Size(tWidth, tHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                                )

                                // Glowing Dashed Border when revealed
                                drawRoundRect(
                                    color = tileGold,
                                    topLeft = Offset(leftPx, topPx),
                                    size = androidx.compose.ui.geometry.Size(tWidth, tHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                                    style = Stroke(
                                        width = 3.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                                    )
                                )
                            } else {
                                // Subtle boundary guide before clicking
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.28f),
                                    topLeft = Offset(leftPx, topPx),
                                    size = androidx.compose.ui.geometry.Size(tWidth, tHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                                    )
                                )
                            }

                            // Corner L-Brackets
                            val bracketLen = minOf(tWidth, tHeight) * 0.20f
                            val bracketColor = if (isRevealed) tileCyan else Color.White.copy(alpha = 0.65f)
                            val bracketStroke = if (isRevealed) 3.5.dp.toPx() else 2.dp.toPx()
                            // Top-Left
                            drawLine(bracketColor, Offset(leftPx, topPx), Offset(leftPx + bracketLen, topPx), bracketStroke, StrokeCap.Round)
                            drawLine(bracketColor, Offset(leftPx, topPx), Offset(leftPx, topPx + bracketLen), bracketStroke, StrokeCap.Round)
                            // Top-Right
                            drawLine(bracketColor, Offset(rightPx, topPx), Offset(rightPx - bracketLen, topPx), bracketStroke, StrokeCap.Round)
                            drawLine(bracketColor, Offset(rightPx, topPx), Offset(rightPx, topPx + bracketLen), bracketStroke, StrokeCap.Round)
                            // Bottom-Left
                            drawLine(bracketColor, Offset(leftPx, bottomPx), Offset(leftPx + bracketLen, bottomPx), bracketStroke, StrokeCap.Round)
                            drawLine(bracketColor, Offset(leftPx, bottomPx), Offset(leftPx, bottomPx - bracketLen), bracketStroke, StrokeCap.Round)
                            // Bottom-Right
                            drawLine(bracketColor, Offset(rightPx, bottomPx), Offset(rightPx - bracketLen, bottomPx), bracketStroke, StrokeCap.Round)
                            drawLine(bracketColor, Offset(rightPx, bottomPx), Offset(rightPx, bottomPx - bracketLen), bracketStroke, StrokeCap.Round)
                        }
                    }
                }

                // 2D-4. Draw Simultaneous Wall Measurement Overlay (即時牆面測量與 3D 投影網格)
                if (isSimultaneousWallMeasureActive && detectedWalls.isNotEmpty()) {
                    detectedWalls.forEach { wall ->
                        val screenCorners = wall.corners3D.map { cornerPt ->
                            ArMath.projectWorldToScreen(cornerPt, viewMatrixState.value, projectionMatrixState.value, screenW, screenH)
                        }
                        val pBL = screenCorners.getOrNull(0)
                        val pBR = screenCorners.getOrNull(1)
                        val pTR = screenCorners.getOrNull(2)
                        val pTL = screenCorners.getOrNull(3)

                        if (pBL != null && pBR != null && pTR != null && pTL != null) {
                            val wallPath = Path().apply {
                                moveTo(pBL.first, pBL.second)
                                lineTo(pBR.first, pBR.second)
                                lineTo(pTR.first, pTR.second)
                                lineTo(pTL.first, pTL.second)
                                close()
                            }

                            // 1. Semi-transparent holographic wall mesh fill
                            drawPath(
                                path = wallPath,
                                color = Color(0x2200E5FF)
                            )

                            // 2. Futuristic boundary outline with animated dash
                            drawPath(
                                path = wallPath,
                                color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                                style = Stroke(
                                    width = 2.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), dashPhase)
                                )
                            )

                            // 3. Holographic grid lines inside wall surface
                            for (fraction in listOf(0.33f, 0.66f)) {
                                // Horizontal lines across the wall
                                val hStart = Offset(
                                    pBL.first + (pTL.first - pBL.first) * fraction,
                                    pBL.second + (pTL.second - pBL.second) * fraction
                                )
                                val hEnd = Offset(
                                    pBR.first + (pTR.first - pBR.first) * fraction,
                                    pBR.second + (pTR.second - pBR.second) * fraction
                                )
                                drawLine(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                                    start = hStart,
                                    end = hEnd,
                                    strokeWidth = 1.2.dp.toPx()
                                )

                                // Vertical lines across the wall
                                val vStart = Offset(
                                    pBL.first + (pBR.first - pBL.first) * fraction,
                                    pBL.second + (pBR.second - pBL.second) * fraction
                                )
                                val vEnd = Offset(
                                    pTL.first + (pTR.first - pTL.first) * fraction,
                                    pTL.second + (pTR.second - pTL.second) * fraction
                                )
                                drawLine(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                                    start = vStart,
                                    end = vEnd,
                                    strokeWidth = 1.2.dp.toPx()
                                )
                            }

                            // 4. Corner bracket anchors (4 corners)
                            screenCorners.forEach { cornerProj ->
                                if (cornerProj != null) {
                                    val cOffset = Offset(cornerProj.first, cornerProj.second)
                                    drawCircle(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        center = Offset(cOffset.x, cOffset.y + 1f),
                                        radius = 6.dp.toPx()
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        center = cOffset,
                                        radius = 5.dp.toPx()
                                    )
                                    drawCircle(
                                        color = Color(0xFF00E5FF),
                                        center = cOffset,
                                        radius = 3.5.dp.toPx()
                                    )
                                }
                            }
                        }
                    }
                }

                // 2E. Draw start and confirmed anchor pin node markers (3D Spatial Anchors)
                projectedPoints.forEachIndexed { index, proj ->
                    if (proj != null) {
                        val offset = Offset(proj.first, proj.second)
                        val isStartNode = index == 0
                        val isLastNode = index == projectedPoints.size - 1 && projectedPoints.size > 1

                        // 1. Beacon pulse halo ring
                        drawCircle(
                            color = colorPrimary.copy(alpha = 0.25f * (2f - reticlePulseScale)),
                            center = offset,
                            radius = (14.dp * reticlePulseScale).toPx()
                        )

                        // 2. High-contrast ground shadow
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.45f),
                            center = Offset(offset.x, offset.y + 2f),
                            radius = 9.dp.toPx()
                        )

                        // 3. Solid Pin Outer Ring
                        drawCircle(
                            color = colorPrimary,
                            center = offset,
                            radius = 9.dp.toPx()
                        )

                        // 4. White Contrast Ring
                        drawCircle(
                            color = Color.White,
                            center = offset,
                            radius = 6.dp.toPx()
                        )

                        // 5. Center Core Pinpoint Dot
                        drawCircle(
                            color = if (isStartNode) colorPrimary else if (isLastNode) colorSecondary else colorPrimary,
                            center = offset,
                            radius = 3.5.dp.toPx()
                        )
                    }
                }

                // 4. Google Measure Style Dynamic 3D Target Reticle (Surface Locked & Spring Snapped)
                val reticleCenter = currentReticlePos
                val baseRadius = 14.dp.toPx()
                val currentRadius = baseRadius * snapScaleAnimated * reticlePulseScale

                // 1. Snapped Target Lock Radial Aura Glow
                if (isSnapped) {
                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = snapGlowAlphaAnimated * 0.45f),
                        center = reticleCenter,
                        radius = currentRadius * 1.6f
                    )
                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = snapGlowAlphaAnimated * 0.25f),
                        center = reticleCenter,
                        radius = currentRadius * 2.2f
                    )
                }

                // 2. High-contrast ground shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    center = Offset(reticleCenter.x + 1f, reticleCenter.y + 1.5f),
                    radius = currentRadius,
                    style = Stroke(width = 3.dp.toPx())
                )

                // 3. Clean Elegant Outer Gold Ring
                drawCircle(
                    color = if (isSnapped) Color(0xFFFFD54F) else Color(0xFFFBBF24),
                    center = reticleCenter,
                    radius = currentRadius,
                    style = Stroke(
                        width = if (isSnapped) 2.6.dp.toPx() else 2.0.dp.toPx()
                    )
                )

                // 3.1 Multi-Sample Burst Averaging Dynamic Progress Arc (Precision Lock)
                if (sensorTelemetryState.value.multiSampleProgress > 0f) {
                    val arcRadius = currentRadius + 5.dp.toPx()
                    drawArc(
                        color = if (sensorTelemetryState.value.isMultiSampleLocked) colorTertiary else colorPrimary,
                        startAngle = -90f,
                        sweepAngle = sensorTelemetryState.value.multiSampleProgress * 360f,
                        useCenter = false,
                        topLeft = Offset(reticleCenter.x - arcRadius, reticleCenter.y - arcRadius),
                        size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 5. Solid Center White & Gold Accent Core Pinpoint Dot
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    center = Offset(reticleCenter.x + 0.5f, reticleCenter.y + 0.5f),
                    radius = 4.5.dp.toPx()
                )
                drawCircle(
                    color = Color.White,
                    center = reticleCenter,
                    radius = 4.0.dp.toPx()
                )
                drawCircle(
                    color = if (isSnapped) Color(0xFFFFD54F) else Color(0xFFFBBF24),
                    center = reticleCenter,
                    radius = 2.2.dp.toPx()
                )

                // 6. Plane Locked Center Micro Particle Feedback Ring
                if (planesCount > 0) {
                    val particleCount = 8
                    for (i in 0 until particleCount) {
                        val angle = (i * (360f / particleCount)) + (planeLockedParticleAnim * 360f)
                        val rad = Math.toRadians(angle.toDouble())
                        val orbitRadius = (18.dp.toPx()) + (kotlin.math.sin(planeLockedParticleAnim * 6.28318f + i).toFloat() * 2.5.dp.toPx())
                        val px = reticleCenter.x + (kotlin.math.cos(rad).toFloat() * orbitRadius)
                        val py = reticleCenter.y + (kotlin.math.sin(rad).toFloat() * orbitRadius)
                        val sineVal = kotlin.math.sin(planeLockedParticleAnim * 6.28318f + i)
                        val pAlpha = ((sineVal + 1f) / 2f).coerceIn(0.25f, 0.9f)

                        drawCircle(
                            color = Color(0xFFFBBF24).copy(alpha = pAlpha),
                            center = Offset(px, py),
                            radius = 2f * density
                        )
                    }
                }
            }

            // 3. Dynamic Floating Measurement & Node Badges Overlay
            Box(modifier = Modifier.fillMaxSize()) {
                val screenW = localView.width.takeIf { it > 0 } ?: 1080
                val screenH = localView.height.takeIf { it > 0 } ?: 1920
                val projectedNodePoints = capturedPoints.map { pt ->
                    ArMath.projectWorldToScreen(pt, viewMatrixState.value, projectionMatrixState.value, screenW, screenH)
                }

                val isArea = subMode == 1 || (subMode == 0 && autoDetectedType == "AREA")

                // 3C. Capsules for confirmed line segments (兩點成一線，不共用點)
                val stepVal = if (isArea) 1 else 2
                if (capturedPoints.size >= 2) {
                    for (i in 0 until capturedPoints.size - 1 step stepVal) {
                        val mid3D = ArMath.midpoint(capturedPoints[i], capturedPoints[i + 1])
                        val midProj = ArMath.projectWorldToScreen(mid3D, viewMatrixState.value, projectionMatrixState.value, screenW, screenH)
                        if (midProj != null) {
                            val segDist = ArMath.distance(capturedPoints[i], capturedPoints[i + 1])
                            val distText = viewModel.formatLength(segDist, selectedUnit)

                            Surface(
                                color = colorPrimaryContainer,
                                shape = RoundedCornerShape(percent = 50),
                                border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.5f)),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .offset {
                                        androidx.compose.ui.unit.IntOffset(
                                            (midProj.first - 60.dp.toPx() / 2).toInt(),
                                            (midProj.second - 36.dp.toPx() / 2).toInt()
                                        )
                                    }
                            ) {
                                Text(
                                    text = distText,
                                    color = colorOnPrimaryContainer,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 3D. Active dynamic measurement capsule positioned along the live line
                // 兩點成一線：僅在奇數個點時（正延伸某條線段中），才在動態虛線上顯示即時距離膠囊
                val isActivelyDrawingSegment = capturedPoints.size % 2 == 1
                if (isActivelyDrawingSegment && capturedPoints.isNotEmpty()) {
                    val lastPt = capturedPoints.last()
                    val liveTarget = liveTargetPointState.value

                    if (liveTarget != null && liveDistanceMetersState.value != null && liveDistanceMetersState.value!! > 0.0) {
                        val mid3D = ArMath.midpoint(lastPt, liveTarget)
                        val midProj = ArMath.projectWorldToScreen(mid3D, viewMatrixState.value, projectionMatrixState.value, screenW, screenH)
                        val badgePos = midProj ?: Pair(screenW / 2f, screenH / 2f - 120f)
                        val distText = viewModel.formatLength(liveDistanceMetersState.value!!, selectedUnit)

                        Surface(
                            color = colorPrimary,
                            shape = RoundedCornerShape(percent = 50),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        (badgePos.first - 60.dp.toPx() / 2).toInt(),
                                        (badgePos.second - 36.dp.toPx() / 2).toInt()
                                    )
                                }
                        ) {
                            Text(
                                text = if (isSnapped) "吸附 $distText" else distText,
                                color = colorOnPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // 3E. AI Tile Pill Button with Blur Effect & Length/Width Reveal (藥丸狀模糊按鈕，點擊顯示長寬)
                // 只有在真正偵測到磁磚時才顯示
                if (detectedTiles.isNotEmpty()) {
                    val tilesToDisplay = detectedTiles

                    tilesToDisplay.forEach { tile ->
                        val leftPx = tile.leftNorm * screenW
                        val topPx = tile.topNorm * screenH
                        val rightPx = tile.rightNorm * screenW
                        val bottomPx = tile.bottomNorm * screenH

                        val centerX = (leftPx + rightPx) / 2f
                        val centerY = (topPx + bottomPx) / 2f

                        val widthCmVal = tile.estimatedWidthCm
                        val heightCmVal = tile.estimatedHeightCm

                        val wMeters = widthCmVal / 100.0
                        val hMeters = heightCmVal / 100.0

                        val widthFormatted = viewModel.formatLength(wMeters, selectedUnit)
                        val heightFormatted = viewModel.formatLength(hMeters, selectedUnit)
                        val isRevealed = revealedTileIds[tile.id] == true

                        // Top & Left Edge dimension badges are shown only after user taps the pill button
                        if (isRevealed) {
                            // 1. Top Edge Badge: 長 (Length)
                            Surface(
                                color = colorSurfaceContainerHighest.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colorPrimary),
                                shadowElevation = 6.dp,
                                modifier = Modifier.offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        (centerX - 48.dp.toPx()).toInt(),
                                        (topPx - 34.dp.toPx()).toInt().coerceAtLeast(60)
                                    )
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Straighten,
                                        contentDescription = null,
                                        tint = colorPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "長 $widthFormatted",
                                        color = colorOnSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // 2. Left Edge Badge: 寬 (Width)
                            Surface(
                                color = colorSurfaceContainerHighest.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colorSecondary),
                                shadowElevation = 6.dp,
                                modifier = Modifier.offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        (leftPx - 80.dp.toPx()).toInt().coerceAtLeast(10),
                                        (centerY - 15.dp.toPx()).toInt()
                                    )
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Height,
                                        contentDescription = null,
                                        tint = colorSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "寬 $heightFormatted",
                                        color = colorOnSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        // 3. Centered Pill Button with Blur Effect (藥丸狀模糊按鈕)
                        Box(
                            modifier = Modifier
                                .offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        (centerX - if (isRevealed) 110.dp.toPx() else 85.dp.toPx()).toInt(),
                                        (centerY - 22.dp.toPx()).toInt()
                                    )
                                }
                                .wrapContentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Frosted Glass Blur Backdrop Layer (模糊效果背景)
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(50))
                                    .blur(radius = 16.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.35f),
                                                colorSurfaceContainerHighest.copy(alpha = 0.60f)
                                            )
                                        )
                                    )
                            )

                            // Interactive Pill Button Surface
                            Surface(
                                onClick = {
                                    val nowRevealed = !(revealedTileIds[tile.id] ?: false)
                                    revealedTileIds[tile.id] = nowRevealed
                                    viewModel.triggerHapticFeedback()
                                    if (nowRevealed) {
                                        viewModel.selectTileForDetail(tile)
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                color = if (isRevealed) colorPrimaryContainer.copy(alpha = 0.92f) else colorSurfaceContainerHighest.copy(alpha = 0.85f),
                                contentColor = if (isRevealed) colorOnPrimaryContainer else colorOnSurface,
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isRevealed) colorPrimary else colorOutlineVariant.copy(alpha = 0.8f)
                                ),
                                shadowElevation = 10.dp,
                                modifier = Modifier.testTag("tile_pill_button_${tile.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 9.dp)
                                        .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!isRevealed) {
                                        // Before press: Pill button prompting to reveal dimensions
                                        Icon(
                                            imageVector = Icons.Rounded.GridOn,
                                            contentDescription = "磁磚",
                                            tint = colorPrimary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "磁磚 (點擊顯示長寬)",
                                            color = colorOnSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        // Pressed: Shows length and width!
                                        Icon(
                                            imageVector = Icons.Rounded.Straighten,
                                            contentDescription = "尺寸",
                                            tint = colorPrimary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "長 $widthFormatted × 寬 $heightFormatted",
                                            color = colorOnPrimaryContainer,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        // Quick apply / lock measurement
                                        IconButton(
                                            onClick = {
                                                viewModel.measureTileOneTap(tile)
                                            },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = "鎖定測量",
                                                tint = colorPrimary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3F. Simultaneous Wall Measurement 3D Floating Badges & Controls
                if (isSimultaneousWallMeasureActive && detectedWalls.isNotEmpty()) {
                    detectedWalls.forEach { wall ->
                        val screenCorners = wall.corners3D.map { cornerPt ->
                            ArMath.projectWorldToScreen(cornerPt, viewMatrixState.value, projectionMatrixState.value, screenW, screenH)
                        }
                        val pBL = screenCorners.getOrNull(0)
                        val pBR = screenCorners.getOrNull(1)
                        val pTR = screenCorners.getOrNull(2)
                        val pTL = screenCorners.getOrNull(3)

                        if (pBL != null && pBR != null && pTR != null && pTL != null) {
                            val midBottomX = (pBL.first + pBR.first) / 2f
                            val midBottomY = (pBL.second + pBR.second) / 2f

                            val midLeftX = (pBL.first + pTL.first) / 2f
                            val midLeftY = (pBL.second + pTL.second) / 2f

                            val centerX = (pBL.first + pBR.first + pTR.first + pTL.first) / 4f
                            val centerY = (pBL.second + pBR.second + pTR.second + pTL.second) / 4f

                            val wFormatted = viewModel.formatLength(wall.widthMeters.toDouble(), selectedUnit)
                            val hFormatted = viewModel.formatLength(wall.heightMeters.toDouble(), selectedUnit)
                            val aFormatted = viewModel.formatArea(wall.areaSqMeters.toDouble(), selectedUnit)

                            // 1. Bottom Width Badge
                            Surface(
                                color = Color(0xFF0F172A).copy(alpha = 0.88f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
                                shadowElevation = 6.dp,
                                modifier = Modifier.offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        (midBottomX - 45.dp.toPx()).toInt(),
                                        (midBottomY + 8.dp.toPx()).toInt().coerceIn(10, screenH - 120)
                                    )
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Straighten,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "牆寬 $wFormatted",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2. Side Height Badge
                            Surface(
                                color = Color(0xFF0F172A).copy(alpha = 0.88f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.7f)),
                                shadowElevation = 6.dp,
                                modifier = Modifier.offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        (midLeftX - 90.dp.toPx()).toInt().coerceAtLeast(10),
                                        (midLeftY - 14.dp.toPx()).toInt().coerceIn(60, screenH - 120)
                                    )
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Height,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "牆高 $hFormatted",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 3. Center Interactive Wall Card (One-tap lock wall measurement)
                            Surface(
                                color = Color.Black.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF00E5FF)),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .offset {
                                        androidx.compose.ui.unit.IntOffset(
                                            (centerX - 100.dp.toPx()).toInt().coerceIn(20, screenW - 220),
                                            (centerY - 24.dp.toPx()).toInt().coerceIn(100, screenH - 180)
                                        )
                                    }
                                    .clickable {
                                        viewModel.lockWallMeasurement(wall)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Layers,
                                                contentDescription = null,
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "垂直牆面 • $aFormatted",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$wFormatted × $hFormatted (輕觸鎖定)",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Clean Minimal Top Bar with Gradient Blur (Clear / Status indicator, Torch, History, Settings)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                // Top Progressive Variable Blur Scrim (漸進式毛玻璃模糊取代黑漸層)
                GradientBlurScrim(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    isTop = true,
                    baseColor = Color.White.copy(alpha = 0.05f),
                    blurRadius = 32.dp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // Left: Status badge or clear button with Material 3 AnimatedContent transition
                AnimatedContent(
                    targetState = capturedPoints.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(
                            initialScale = 0.92f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                        )).togetherWith(
                            fadeOut(animationSpec = tween(160)) + scaleOut(
                                targetScale = 0.92f,
                                animationSpec = tween(160)
                            )
                        )
                    },
                    label = "TopStatusChipAnim"
                ) { isMeasuring ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isMeasuring) {
                            // "測量中" active indicator badge
                            Surface(
                                color = colorPrimary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.shadow(4.dp, RoundedCornerShape(20.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 2.dp,
                                        color = colorOnPrimary
                                    )
                                    Text(
                                        text = "測量中...",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colorOnPrimary
                                    )
                                }
                            }
                        } else {
                            val stabilityColor = when (trackingStability.level) {
                                StabilityLevel.HIGH -> colorTertiary
                                StabilityLevel.MODERATE -> colorPrimary
                                StabilityLevel.LOW -> colorSecondary
                                StabilityLevel.POOR -> colorError
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(20.dp))
                                    .clickable { showStabilityDiagnosticsDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (trackingState == TrackingState.TRACKING) {
                                        Surface(
                                            color = stabilityColor,
                                            shape = CircleShape,
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                        Text(
                                            text = "穩定",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (trackingStability.isDriftRisk) colorError else Color.White
                                        )
                                        if (trackingStability.isFeatureDeficient) {
                                            Surface(
                                                color = colorError.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "特徵少",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorError,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 2.dp,
                                            color = colorPrimary
                                        )
                                        Text(
                                            text = "尋找表面...",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right: Clean quick actions with AI tool menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Simultaneous Wall Measurement Quick Toggle
                    val isWallDetected = detectedWalls.isNotEmpty()
                    IconButton(
                        onClick = { viewModel.toggleSimultaneousWallMeasure() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isSimultaneousWallMeasureActive) {
                                    if (isWallDetected) Color(0xFF00E5FF).copy(alpha = 0.85f) else Color(0xFF0F172A).copy(alpha = 0.75f)
                                } else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                CircleShape
                            )
                            .border(
                                width = if (isSimultaneousWallMeasureActive) 1.5.dp else 0.8.dp,
                                color = if (isSimultaneousWallMeasureActive) Color(0xFF00E5FF) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .shadow(if (isSimultaneousWallMeasureActive) 4.dp else 2.dp, CircleShape)
                            .testTag("wall_measure_toggle_button")
                    ) {
                        Icon(
                            Icons.Rounded.Layers,
                            contentDescription = "同時測量牆壁",
                            tint = if (isSimultaneousWallMeasureActive && isWallDetected) Color.Black else (if (isSimultaneousWallMeasureActive) Color(0xFF00E5FF) else Color.White),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Unified AI Smart Tools Anchor & Dropdown Menu
                    Box {
                        val isAnyAiActive = isMobileSamMode || isObjectronMode || isAiTileMode
                        IconButton(
                            onClick = { showAiToolsMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isAnyAiActive) colorPrimary else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                    CircleShape
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = if (isAnyAiActive) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                                .shadow(if (isAnyAiActive) 6.dp else 3.dp, CircleShape)
                                .testTag("ai_tools_menu_button")
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = "AI 智慧工具",
                                tint = if (isAnyAiActive) colorOnPrimary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showAiToolsMenu,
                            onDismissRequest = { showAiToolsMenu = false },
                            modifier = Modifier.background(colorSurfaceContainer)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "磁磚規格: ${activeTilePreset.widthCm.toInt()}×${activeTilePreset.heightCm.toInt()} cm",
                                            color = colorPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("● 自動偵測中", color = colorPrimary, fontSize = 11.sp)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.GridOn,
                                        contentDescription = null,
                                        tint = colorPrimary
                                    )
                                },
                                onClick = {
                                    // Cycle through tile presets: 60x60 -> 30x60 -> 80x80 -> 120x60 -> 30x30
                                    val nextPreset = when (activeTilePreset) {
                                        com.example.logic.ai.AiTilePreset.PRESET_60X60 -> com.example.logic.ai.AiTilePreset.PRESET_30X60
                                        com.example.logic.ai.AiTilePreset.PRESET_30X60 -> com.example.logic.ai.AiTilePreset.PRESET_80X80
                                        com.example.logic.ai.AiTilePreset.PRESET_80X80 -> com.example.logic.ai.AiTilePreset.PRESET_60X120
                                        com.example.logic.ai.AiTilePreset.PRESET_60X120 -> com.example.logic.ai.AiTilePreset.PRESET_30X30
                                        else -> com.example.logic.ai.AiTilePreset.PRESET_60X60
                                    }
                                    viewModel.setTilePreset(nextPreset)
                                    val currentTv = textureViewRef
                                    if (currentTv != null && currentTv.isAvailable) {
                                        val bmp = currentTv.bitmap
                                        if (bmp != null) viewModel.processFrameForTiles(bmp)
                                    }
                                    showAiToolsMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "立即分析畫面磁磚",
                                        color = colorOnSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.CenterFocusStrong,
                                        contentDescription = null,
                                        tint = colorPrimary
                                    )
                                },
                                onClick = {
                                    val currentTv = textureViewRef
                                    val bmp = if (currentTv != null && currentTv.isAvailable) currentTv.bitmap else null
                                    viewModel.triggerAiTileDetection(bmp)
                                    showAiToolsMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "MobileSAM 邊界輪廓",
                                            color = if (isMobileSamMode) colorTertiary else colorOnSurface,
                                            fontWeight = if (isMobileSamMode) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isMobileSamMode) {
                                            Text("● 開啟", color = colorTertiary, fontSize = 11.sp)
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.AutoAwesomeMosaic,
                                        contentDescription = null,
                                        tint = if (isMobileSamMode) colorTertiary else colorOnSurfaceVariant
                                    )
                                },
                                onClick = {
                                    viewModel.toggleMobileSamMode()
                                    showAiToolsMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Objectron 3D 包絡方框",
                                            color = if (isObjectronMode) colorSecondary else colorOnSurface,
                                            fontWeight = if (isObjectronMode) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isObjectronMode) {
                                            Text("● 開啟", color = colorSecondary, fontSize = 11.sp)
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.ViewInAr,
                                        contentDescription = null,
                                        tint = if (isObjectronMode) colorSecondary else colorOnSurfaceVariant
                                    )
                                },
                                onClick = {
                                    viewModel.toggleObjectronMode()
                                    showAiToolsMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "同時測量牆壁",
                                            color = if (isSimultaneousWallMeasureActive) Color(0xFF00E5FF) else colorOnSurface,
                                            fontWeight = if (isSimultaneousWallMeasureActive) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSimultaneousWallMeasureActive) {
                                            Text(
                                                if (detectedWalls.isNotEmpty()) "● 已鎖定牆面" else "● 偵測中",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Layers,
                                        contentDescription = null,
                                        tint = if (isSimultaneousWallMeasureActive) Color(0xFF00E5FF) else colorOnSurfaceVariant
                                    )
                                },
                                onClick = {
                                    viewModel.toggleSimultaneousWallMeasure()
                                    showAiToolsMenu = false
                                }
                            )
                        }
                    }

                    // Flashlight / Torch with multi-level brightness adjustment
                    Box {
                        IconButton(
                            onClick = {
                                if (!isTorchOn) {
                                    viewModel.toggleTorch(context)
                                } else {
                                    // If already on, click opens brightness selector or toggles
                                    showTorchBrightnessMenu = !showTorchBrightnessMenu
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isTorchOn) colorPrimary else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                    CircleShape
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = if (isTorchOn) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                                .shadow(if (isTorchOn) 6.dp else 3.dp, CircleShape)
                                .testTag("flashlight_toggle_button")
                        ) {
                            Icon(
                                if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                contentDescription = "手電筒補光",
                                tint = if (isTorchOn) colorOnPrimary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Torch Brightness Popup Panel
                        DropdownMenu(
                            expanded = showTorchBrightnessMenu,
                            onDismissRequest = { showTorchBrightnessMenu = false },
                            modifier = Modifier
                                .background(colorSurfaceContainer)
                                .widthIn(min = 220.dp)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.LightMode,
                                            contentDescription = null,
                                            tint = colorPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "手電筒亮度",
                                            color = colorOnSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        "${(torchBrightness * 100).toInt()}%",
                                        color = colorPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Brightness Slider (20% ~ 100%)
                                Slider(
                                    value = torchBrightness,
                                    onValueChange = { newVal ->
                                        viewModel.setTorchBrightness(context, newVal)
                                    },
                                    valueRange = 0.2f..1.0f,
                                    steps = 3, // 20%, 40%, 60%, 80%, 100%
                                    colors = SliderDefaults.colors(
                                        thumbColor = colorPrimary,
                                        activeTrackColor = colorPrimary,
                                        inactiveTrackColor = colorSurfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Preset Level Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    listOf(
                                        0.25f to "25%",
                                        0.50f to "50%",
                                        0.75f to "75%",
                                        1.00f to "100%"
                                    ).forEach { (level, label) ->
                                        val isSelected = kotlin.math.abs(torchBrightness - level) < 0.12f
                                        Surface(
                                            color = if (isSelected) colorPrimary else colorSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .clickable {
                                                    viewModel.setTorchBrightness(context, level)
                                                }
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) colorOnPrimary else colorOnSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    color = colorOutlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                // Turn off button inside menu
                                Button(
                                    onClick = {
                                        viewModel.turnOffTorch(context)
                                        showTorchBrightnessMenu = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorError,
                                        contentColor = colorOnError
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.FlashlightOff, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("關閉手電筒", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // History
                    IconButton(
                        onClick = onShowHistoryClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), CircleShape)
                            .border(
                                width = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .shadow(3.dp, CircleShape)
                    ) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = "History",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Settings
                    IconButton(
                        onClick = onShowSettingsClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), CircleShape)
                            .border(
                                width = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .shadow(3.dp, CircleShape)
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

            // 5B. Video Recording Active Top HUD & Screen Border (When recording video via long-press shutter)
            if (isRecordingVideo) {
                // Outer glowing recording border
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(width = 3.dp, color = Color(0xFFE53935).copy(alpha = 0.9f))
                )

                // Recording Time & REC Badge Pill
                Surface(
                    color = Color.Black.copy(alpha = 0.82f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFE53935)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 58.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pulsing Red Dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFFE53935), CircleShape)
                        )
                        val mins = recordingSeconds / 60
                        val secs = recordingSeconds % 60
                        Text(
                            text = String.format("REC %02d:%02d", mins, secs),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            text = "· 點擊快門停止",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 6. Bottom Dynamic Control Deck (+ / ✓ Button & Camera Shutter)
            var isShutterFlash by remember { mutableStateOf(false) }

            // Shutter Flash Animation
            if (isShutterFlash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.75f))
                )
            }

            // Bottom Progressive Variable Blur Scrim for camera control deck (漸進式毛玻璃模糊取代黑漸層)
            GradientBlurScrim(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .align(Alignment.BottomCenter),
                isTop = false,
                baseColor = Color.White.copy(alpha = 0.05f),
                blurRadius = 32.dp
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding + 64.dp)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // AI Specialized Guidance Pill (MobileSAM & Objectron & Wall if active)
                if (isMobileSamMode || isObjectronMode || (isSimultaneousWallMeasureActive && detectedWalls.isNotEmpty())) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isMobileSamMode -> colorTertiary.copy(alpha = 0.6f)
                                isObjectronMode -> colorSecondary.copy(alpha = 0.5f)
                                isSimultaneousWallMeasureActive && detectedWalls.isNotEmpty() -> Color(0xFF00E5FF).copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier
                            .shadow(4.dp, RoundedCornerShape(18.dp))
                            .then(
                                if (isMobileSamMode && segmentedObject != null) {
                                    Modifier.clickable {
                                        viewModel.applySegmentedObjectCorners()
                                    }
                                } else if (isObjectronMode && objectron3DBox != null) {
                                    Modifier.clickable {
                                        viewModel.applyObjectronBoxCorners()
                                    }
                                } else if (isSimultaneousWallMeasureActive && detectedWalls.isNotEmpty()) {
                                    Modifier.clickable {
                                        viewModel.lockWallMeasurement(detectedWalls.first())
                                    }
                                } else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isMobileSamMode && segmentedObject != null) {
                                Icon(Icons.Rounded.AutoAwesomeMosaic, null, tint = colorTertiary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "SAM ${segmentedObject!!.label}: ${"%.2f".format(segmentedObject!!.areaM2)} m² (輕觸一鍵鎖定)",
                                    color = colorTertiary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isMobileSamMode) {
                                Icon(Icons.Rounded.TouchApp, null, tint = colorTertiary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "輕觸畫面任意物件，即時分割邊界與面積",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (isObjectronMode && objectron3DBox != null) {
                                Icon(Icons.Rounded.ViewInAr, null, tint = colorSecondary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "AI 3D方框: ${"%.0f".format(objectron3DBox!!.widthMeters * 100)}×${"%.0f".format(objectron3DBox!!.heightMeters * 100)}×${"%.0f".format(objectron3DBox!!.depthMeters * 100)} cm (輕觸鎖定)",
                                    color = colorSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isSimultaneousWallMeasureActive && detectedWalls.isNotEmpty()) {
                                val firstWall = detectedWalls.first()
                                val wFormatted = viewModel.formatLength(firstWall.widthMeters.toDouble(), selectedUnit)
                                val hFormatted = viewModel.formatLength(firstWall.heightMeters.toDouble(), selectedUnit)
                                val aFormatted = viewModel.formatArea(firstWall.areaSqMeters.toDouble(), selectedUnit)
                                Icon(Icons.Rounded.Layers, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                Text(
                                    text = "牆面測量: 寬 $wFormatted × 高 $hFormatted ($aFormatted) 輕觸鎖定",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                // 2. Interactive Bottom Action Deck (Left, Center, Right Symmetrical Layout)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Slot (Balanced 1f weight): Undo & Clear buttons
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = capturedPoints.isNotEmpty(),
                            enter = slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(animationSpec = tween(200)) + scaleIn(
                                initialScale = 0.8f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                            ),
                            exit = slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(180)
                            ) + fadeOut(animationSpec = tween(150)) + scaleOut(
                                targetScale = 0.8f,
                                animationSpec = tween(180)
                            )
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.undo()
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f), CircleShape)
                                    .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CircleShape)
                                    .shadow(4.dp, CircleShape)
                            ) {
                                Icon(
                                    Icons.Rounded.Undo,
                                    contentDescription = "Undo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Center Slot: Primary Circular Main Button with dynamic press morphing
                    val addFabInteractionSource = remember { MutableInteractionSource() }
                    val isAddFabPressed by addFabInteractionSource.collectIsPressedAsState()

                    val addFabCornerRadius by animateDpAsState(
                        targetValue = if (isAddFabPressed) 16.dp else 34.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "addFabCornerRadius"
                    )

                    val addFabScale by animateFloatAsState(
                        targetValue = if (isAddFabPressed) 0.93f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "addFabScale"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Surface(
                            color = addFabContainerColor,
                            shape = RoundedCornerShape(addFabCornerRadius),
                            border = if (!isMeasurementAvailable) BorderStroke(1.5.dp, Color(0xFF5A5A5E)) else null,
                            shadowElevation = if (isMeasurementAvailable) (if (isAddFabPressed) 3.dp else 8.dp) else 2.dp,
                            modifier = Modifier
                                .size(68.dp)
                                .scale(addFabScale)
                                .clickable(
                                    interactionSource = addFabInteractionSource,
                                    indication = ripple(bounded = true, radius = 34.dp)
                                ) {
                                    if (isMeasurementAvailable) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        viewModel.requestHitTest()
                                    } else {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                .testTag("add_point_fab")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val isWaitingForSecondPoint = capturedPoints.size % 2 == 1
                                AnimatedContent(
                                    targetState = isWaitingForSecondPoint,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.6f, animationSpec = tween(150)))
                                            .togetherWith(fadeOut(animationSpec = tween(110)) + scaleOut(targetScale = 0.6f, animationSpec = tween(110)))
                                    },
                                    label = "AddPointFabIconTransition"
                                ) { waitingForSecond ->
                                    Icon(
                                        imageVector = if (waitingForSecond) Icons.Rounded.Check else Icons.Rounded.Add,
                                        contentDescription = if (waitingForSecond) "確認第二點 (Confirm Point)" else "加入點 (Add Point)",
                                        tint = addFabIconColor,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Right Slot (Balanced 1f weight): Camera Shutter Button
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Camera Shutter Button (Pixel Camera Style: Tap to take Photo, Long-Press to Record Video)
                            PixelShutterButton(
                                size = 52.dp,
                                isRecording = isRecordingVideo,
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    if (isRecordingVideo) {
                                        // Stop Video Recording
                                        videoRecorder.stopRecording { videoPath, thumbPath, durationSec ->
                                            if (videoPath != null) {
                                                viewModel.saveMeasurementRecord(
                                                    imagePath = thumbPath ?: videoPath,
                                                    customNotes = "AR 測量錄影 (${durationSec}秒): $videoPath"
                                                )
                                            }
                                        }
                                    } else {
                                        // Capture Photo Snapshot
                                        isShutterFlash = true
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(100)
                                            isShutterFlash = false
                                        }
                                        ShareUtility.captureViewSnapshot(localView) { path ->
                                            viewModel.saveMeasurementRecord(imagePath = path)
                                        }
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    if (!isRecordingVideo) {
                                        videoRecorder.startRecording(localView)
                                    }
                                },
                                modifier = Modifier.padding(end = 16.dp),
                                testTag = "camera_shutter_button"
                            )
                        }
                    }
                }
            }
        }
    }

    // Tile Detail Bottom Sheet (Only show if a genuine tile is selected or detected)
    val tileForDetail = selectedTileForDetail ?: if (showTileDetailSheet) detectedTiles.firstOrNull() else null
    if (tileForDetail != null) {
        TileDetailBottomSheet(
            tile = tileForDetail,
            viewModel = viewModel,
            onDismiss = {
                showTileDetailSheet = false
                viewModel.selectTileForDetail(null)
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TipsAndUpdates, null, tint = colorPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("AR 專業測量指南", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("• MobileSAM 智慧邊緣分割：輕觸畫面任意物件，即時偵測邊界輪廓、估算面積並支援一鍵鎖定測量。")
                    Text("• MediaPipe Objectron 3D AI：智慧 3D 物件包絡線與 8 頂點方框預測，即時計算長寬高與空間體積。")
                    Text("• 60 FPS 點雲渲染：相機即時偵測周圍環境的幾何特徵點與表面。")
                    Text("• 磁吸對齊功能：當準星靠近既有頂點時會自動吸附並震動提示，精準閉合多邊形。")
                    Text("• 多感應器融合校正：即時結合陀螺儀防抖、重力垂準、氣壓計高度、近接感應與雙鏡頭視差。")
                    Text("• 六大測量模式：直線/折線距離、封閉面積、垂直高度、3D 包絡體積、圓形半徑與直徑、空間夾角。")
                    Text("• 物理錨點鎖定：採用實體 ARCore Anchor，走動或轉移視角時完全無漂移。")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("我知道了")
                }
            }
        )
    }

    // Multi-Sensor Fusion Status Dialog
    if (showSensorStatusDialog) {
        val antiJitter by viewModel.antiJitterEnabled.collectAsState()
        val gravityAlign by viewModel.gravityAlignmentEnabled.collectAsState()
        val barometerFusion by viewModel.barometerFusionEnabled.collectAsState()
        val jerkRejection by viewModel.jerkRejectionEnabled.collectAsState()
        val rawDepthConfidenceEnabled by viewModel.rawDepthConfidenceEnabled.collectAsState()
        val rawDepthConfidenceThreshold by viewModel.rawDepthConfidenceThreshold.collectAsState()
        val df1 = remember { DecimalFormat("#,##0") }
        val df2 = remember { DecimalFormat("#,##0") }

        AlertDialog(
            onDismissRequest = { showSensorStatusDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Sensors, null, tint = colorPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("多感應器即時校正狀態", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "硬體感應器正在即時校驗 AR 空間座標，修正視角傾斜、手震與高程誤差：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Sensor Status Cards
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Gyro Stability
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (antiJitter) colorPrimary else Color.Gray,
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(Modifier.width(6.dp))
                                Text("陀螺儀防抖穩定度", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                "${(sensorTelemetryState.value.stabilityScore * 100).toInt()}% (${if (sensorTelemetryState.value.isHandSteady) "🎯 穩定鎖定" else "微動追蹤"})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Anti-Jitter toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAntiJitterEnabled(!antiJitter) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "陀螺儀防手震濾波 (Anti-Jitter)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = antiJitter,
                                onCheckedChange = { viewModel.setAntiJitterEnabled(it) }
                            )
                        }

                        // 2. Gravity Vector / Tilt
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = colorPrimary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(Modifier.width(6.dp))
                                Text("重力向量垂直基準", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                "俯仰: ${df1.format(sensorTelemetryState.value.pitchDeg)}° / 滾轉: ${df1.format(sensorTelemetryState.value.rollDeg)}°",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 3. Barometer Altitude
                        if (sensorTelemetryState.value.isBarometerAvailable) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = colorPrimary,
                                        shape = CircleShape,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                    Spacer(Modifier.width(6.dp))
                                    Text("氣壓計相對高程", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    "${if (sensorTelemetryState.value.barometricAltitudeMeters >= 0) "+" else ""}${df2.format(sensorTelemetryState.value.barometricAltitudeMeters)} m (${df1.format(sensorTelemetryState.value.currentPressureHpa)} hPa)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 4. Proximity Sensor (Surface Contact Zero-Point)
                        if (sensorTelemetryState.value.isProximityAvailable) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (sensorTelemetryState.value.isProximityNear) colorPrimary else Color.Gray,
                                        shape = CircleShape,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                    Spacer(Modifier.width(6.dp))
                                    Text("近接貼面零點校準", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    if (sensorTelemetryState.value.isProximityNear) "📐 貼面觸碰 (0 cm 零點補償)" else "遠離表面 (${df1.format(sensorTelemetryState.value.proximityDistanceCm)} cm)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 5. Dual-Camera Stereo Parallax Baseline
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = colorPrimary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(Modifier.width(6.dp))
                                Text("雙鏡頭同步視差基準", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                "物理基線: ${df1.format(sensorTelemetryState.value.stereoBaselineMm)} mm (置信度 ${(sensorTelemetryState.value.stereoScaleConfidence * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 6. Pixel Raw Depth Confidence Filter (ML Depth map confidence)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (rawDepthConfidenceEnabled) colorPrimary else Color.Gray,
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text("Pixel Raw Depth 置信度過濾", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("門檻: 置信度 ≥ ${rawDepthConfidenceThreshold}% (無效噪點自動剔除)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = rawDepthConfidenceEnabled,
                                onCheckedChange = { viewModel.setRawDepthConfidenceEnabled(it) }
                            )
                        }

                        // 7. 60Hz High Refresh Rate Camera Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setHighFpsModeEnabled(!highFpsModeEnabled) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (highFpsModeEnabled) colorPrimary else Color.Gray,
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text("60Hz 相機超流暢預覽", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("硬體直出零拷貝 SurfaceView，大幅降低畫面延遲", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = highFpsModeEnabled,
                                onCheckedChange = { viewModel.setHighFpsModeEnabled(it) }
                            )
                        }
                    }

                    // Reset Base Button
                    FilledTonalButton(
                        onClick = {
                            viewModel.calibrateSensors()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重新校準當前感應器零點基準")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSensorStatusDialog = false }) {
                    Text("確定")
                }
            }
        )
    }

    // AR Stability & Confidence Diagnostics Dialog
    if (showStabilityDiagnosticsDialog) {
        ArStabilityDiagnosticsDialog(
            stability = trackingStability,
            onDismiss = { showStabilityDiagnosticsDialog = false }
        )
    }
}

@Composable
private fun GuidanceTipChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PlaneDetectionInstructionOverlay(
    trackingState: TrackingState,
    trackingFailureReason: TrackingFailureReason,
    planesCount: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInitialized = trackingState == TrackingState.TRACKING && planesCount > 0

    val infiniteTransition = rememberInfiniteTransition(label = "PlaneScanMotion")
    val tiltAngle by infiniteTransition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tiltAngle"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f)
    val accentColor = if (isInitialized) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Surface(
        color = surfaceColor,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            accentColor.copy(alpha = if (isInitialized) 0.6f else 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Animated phone icon inside a glowing radar box
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                accentColor.copy(alpha = if (isInitialized) 0.18f else 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isInitialized) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                Icons.Rounded.ScreenRotation,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        rotationZ = tiltAngle
                                    }
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isInitialized) "空間平面偵測完成 ✨" else "緩慢平移裝置以建立空間偵測",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                isInitialized -> "已成功辨識 $planesCount 處環境平面與特徵點，隨時可標定測量！"
                                trackingFailureReason == TrackingFailureReason.EXCESSIVE_MOTION -> "移動速度過快，請放慢平移步調"
                                trackingFailureReason == TrackingFailureReason.INSUFFICIENT_LIGHT -> "環境過暗，建議開啟手電筒補光"
                                trackingFailureReason == TrackingFailureReason.INSUFFICIENT_FEATURES -> "表面缺少特徵，請朝向有紋理的地面"
                                else -> "請將相機對準地面或桌面，緩慢左右平移以初始化 AR 空間"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isInitialized) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (!isInitialized) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GuidanceTipChip(icon = Icons.Rounded.OpenWith, text = "左右平移")
                    GuidanceTipChip(icon = Icons.Rounded.Straighten, text = "距離 0.5-3m")
                    GuidanceTipChip(icon = Icons.Rounded.WbSunny, text = "均勻光線")
                }
            }
        }
    }
}

/**
 * Active Tracking Stability Warning Banner
 * Proactively notifies the user when camera moves too fast or feature points are deficient
 * (e.g. pure white wall/ceiling, dim lighting), preventing measurement drift.
 */
@Composable
fun ActiveTrackingStabilityWarningBanner(
    stability: ArTrackingStability,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bannerColor = if (stability.level == StabilityLevel.POOR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer
    val bannerTextColor = if (stability.level == StabilityLevel.POOR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onErrorContainer

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, bannerColor.copy(alpha = 0.85f)),
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(bannerColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        stability.isFeatureDeficient -> Icons.Rounded.Grain
                        stability.isMotionExcessive -> Icons.Rounded.Speed
                        stability.isLightingDeficient -> Icons.Rounded.WbSunny
                        else -> Icons.Rounded.WarningAmber
                    },
                    contentDescription = null,
                    tint = bannerColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stability.warningMessage ?: "特徵點不足，請慢速平移相機",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        stability.isFeatureDeficient -> "請對準有紋理邊緣並慢速平移，避免點位偏移"
                        stability.isMotionExcessive -> "目前平移速度 ${"%.2f".format(stability.cameraSpeedMps)}m/s，請放慢以確保精準"
                        stability.isLightingDeficient -> "環境偏暗可能引起空間漂移，建議補光"
                        else -> "慢速平移能建立穩固特徵點，防止測量偏移"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            Surface(
                color = bannerColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.6f)),
                modifier = Modifier.clickable { onActionClick() }
            ) {
                Text(
                    text = if (stability.isLightingDeficient) "補光" else "診斷",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/**
 * AR Stability & Confidence Diagnostics Dialog
 * Detailed telemetry inspection: confidence score, feature points, speed, illumination and drift protection guide.
 */
@Composable
fun ArStabilityDiagnosticsDialog(
    stability: ArTrackingStability,
    onDismiss: () -> Unit
) {
    val levelColor = when (stability.level) {
        StabilityLevel.HIGH -> MaterialTheme.colorScheme.tertiary
        StabilityLevel.MODERATE -> MaterialTheme.colorScheme.primary
        StabilityLevel.LOW -> MaterialTheme.colorScheme.secondary
        StabilityLevel.POOR -> MaterialTheme.colorScheme.error
    }

    val levelText = when (stability.level) {
        StabilityLevel.HIGH -> "🎯 極佳 (穩定鎖定，空間無偏移)"
        StabilityLevel.MODERATE -> "✨ 良好 (適宜正常測量)"
        StabilityLevel.LOW -> "⚠️ 特徵偏少 (慢速平移警告)"
        StabilityLevel.POOR -> "🔴 極低 (特徵匱乏，高偏移風險)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = levelColor)
                Spacer(Modifier.width(8.dp))
                Text("AR 空間追蹤與置信度診斷", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Confidence Score Gauge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, levelColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "即時置信指數 (Confidence)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(stability.confidenceScore * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = levelColor
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { stability.confidenceScore },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = levelColor,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "狀態：$levelText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = levelColor
                        )
                    }
                }

                // Environment Telemetry Breakdown
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Feature Points
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Grain, null, tint = if (stability.isFeatureDeficient) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("空間特徵點數量", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${stability.featurePointsCount} 個 (${if (stability.isFeatureDeficient) "⚠️ 偏少" else "充足"})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stability.isFeatureDeficient) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Camera Panning Speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, null, tint = if (stability.isMotionExcessive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("相機平移速度", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${"%.2f".format(stability.cameraSpeedMps)} m/s (${if (stability.isMotionExcessive) "⚠️ 過快" else "慢速穩定"})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stability.isMotionExcessive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Tracked Planes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Layers, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("環境辨識平面", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${stability.trackingPlanesCount} 處平面",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Environmental Lighting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WbSunny, null, tint = if (stability.isLightingDeficient) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("環境照度指標", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${"%.2f".format(stability.lightIntensity)} (${if (stability.isLightingDeficient) "⚠️ 偏暗" else "充足"})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stability.isLightingDeficient) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Anti-Drift Guidance Tips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "🛡️ 空間防偏移最佳實務：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "• 慢速勻速平移：轉換角度時請維持慢速平移，避免劇烈手震晃動導致空間錨點漂移。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        "• 避開純色無紋理表面：純白牆面或暗光下缺乏幾何特徵點，請對準接縫或紋理邊緣以鎖定坐標。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("確定")
            }
        }
    )
}
