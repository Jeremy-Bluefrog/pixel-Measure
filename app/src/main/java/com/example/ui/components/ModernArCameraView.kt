@file:androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.example.ui.components

import android.Manifest
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
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
import com.example.logic.ar.ArMath
import com.example.logic.ar.ModernArGlView
import com.example.ui.components.TileDetailBottomSheet
import com.example.ui.viewmodel.MeasureViewModel
import com.example.ui.viewmodel.Point3D
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.ar.core.TrackingFailureReason
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
    val isDepthAvailable by viewModel.isDepthAvailable.collectAsState()
    val planesCount by viewModel.arPlanesCount.collectAsState()
    val surfaceTypeAtCenter by viewModel.surfaceTypeAtCenter.collectAsState()
    val detectedPlanes by viewModel.detectedPlanes.collectAsState()
    val viewMatrix by viewModel.viewMatrix.collectAsState()
    val projectionMatrix by viewModel.projectionMatrix.collectAsState()
    val subMode by viewModel.cameraSubMode.collectAsState()
    val autoDetectedType by viewModel.autoDetectedType.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val liveDistanceMeters by viewModel.liveDistanceMeters.collectAsState()
    val liveTargetPoint by viewModel.liveTargetPoint.collectAsState()
    val isSnapped by viewModel.isSnapped.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val showPointCloud by viewModel.showPointCloud.collectAsState()
    val capturedPoints = viewModel.capturedPoints
    val sensorTelemetry by viewModel.sensorTelemetry.collectAsState()
    val sensorCorrectionEnabled by viewModel.sensorCorrectionEnabled.collectAsState()
    val highFpsModeEnabled by viewModel.highFpsModeEnabled.collectAsState()

    // Touch ripple visual pings
    val pings = remember { mutableStateListOf<Pair<Offset, Animatable<Float, AnimationVector1D>>>() }

    // Dialogs
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSensorStatusDialog by remember { mutableStateOf(false) }

    // Lifecycle sync for ARCore
    DisposableEffect(lifecycleOwner) {
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
        }
    }

    // Reticle & HUD pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "ReticlePulse")
    val reticlePulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reticlePulse"
    )
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
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
        targetValue = if (isSnapped) 1.22f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "snapScaleAnimated"
    )
    val snapGlowAlphaAnimated by animateFloatAsState(
        targetValue = if (isSnapped) 0.9f else 0.15f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "snapGlowAlphaAnimated"
    )
    val reticleColorAnimated by animateColorAsState(
        targetValue = if (isSnapped) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "reticleColorAnimated"
    )

    // Dynamic Material 3 Color Tokens
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnPrimary = MaterialTheme.colorScheme.onPrimary
    val colorPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val colorOnPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val colorSecondary = MaterialTheme.colorScheme.secondary
    val colorOnSecondary = MaterialTheme.colorScheme.onSecondary
    val colorTertiary = MaterialTheme.colorScheme.tertiary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

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
                        )
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

                                // Request hit-test at tap coordinates
                                viewModel.requestHitTest(offset.x, offset.y)
                            }
                        }
                )
            } else {
                // High-Precision CameraX Fallback Live Feed (60Hz / 60 FPS Direct Hardware Surface Composition)
                AndroidView(
                    factory = { ctx ->
                        val previewView = androidx.camera.view.PreviewView(ctx).apply {
                            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                            implementationMode = androidx.camera.view.PreviewView.ImplementationMode.PERFORMANCE
                        }
                        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val previewBuilder = androidx.camera.core.Preview.Builder()

                                try {
                                    val camera2Extender = androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder)
                                    val camManager = ctx.getSystemService(android.content.Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                                    val backCamId = camManager?.cameraIdList?.firstOrNull { id ->
                                        camManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                                                android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                                    } ?: camManager?.cameraIdList?.firstOrNull()

                                    val characteristics = if (backCamId != null) camManager?.getCameraCharacteristics(backCamId) else null
                                    val fpsRanges = characteristics?.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                                    val target60FpsRange = fpsRanges?.firstOrNull { range ->
                                        range.upper >= 60 && range.lower >= 30
                                    } ?: android.util.Range(60, 60)

                                    camera2Extender.setCaptureRequestOption(
                                        android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                        target60FpsRange
                                    )
                                    camera2Extender.setCaptureRequestOption(
                                        android.hardware.camera2.CaptureRequest.CONTROL_MODE,
                                        android.hardware.camera2.CaptureRequest.CONTROL_MODE_AUTO
                                    )
                                    // Clamp exposure time to max 1/60s (16.6ms) to maintain 60 FPS in dim environments
                                    camera2Extender.setCaptureRequestOption(
                                        android.hardware.camera2.CaptureRequest.SENSOR_EXPOSURE_TIME,
                                        1_000_000_000L / 60L
                                    )
                                    camera2Extender.setCaptureRequestOption(
                                        android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                        android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                                    )
                                } catch (e: Throwable) {
                                    android.util.Log.w("CameraX60Hz", "60 FPS request config: ${e.message}")
                                }

                                val preview = previewBuilder.build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val cameraSelector = if (cameraProvider.hasCamera(androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA)) {
                                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                                } else {
                                    androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
                                }
                                cameraProvider.unbindAll()
                                val camera = try {
                                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                                } catch (bindEx: Exception) {
                                    android.util.Log.w("CameraXFallback", "High FPS binding fallback to standard: ${bindEx.message}")
                                    val standardPreview = androidx.camera.core.Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, standardPreview)
                                }
                                viewModel.setCameraControl(camera.cameraControl)
                            } catch (e: Exception) {
                                android.util.Log.e("CameraXFallback", "Camera binding failed: ${e.message}")
                            }
                        }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    onRelease = {
                        viewModel.setCameraControl(null)
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

            // 2. 3D Augmented Overlay Canvas (Planes, Projected Points, Lines, Measurements, Pings)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val screenW = size.width.toInt()
                val screenH = size.height.toInt()
                val screenCenter = Offset(size.width / 2f, size.height / 2f)

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
                    ArMath.projectWorldToScreen(pt, viewMatrix, projectionMatrix, screenW, screenH)
                }

                // 2B. Draw confirmed connecting 3D virtual lines
                if (projectedPoints.size >= 2) {
                    for (i in 0 until projectedPoints.size - 1) {
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

                // 2C. Draw active dynamic virtual line from last anchor point to current center reticle
                if (projectedPoints.isNotEmpty()) {
                    val lastPt = projectedPoints.last()
                    if (lastPt != null) {
                        val startOffset = Offset(lastPt.first, lastPt.second)
                        val dx = screenCenter.x - startOffset.x
                        val dy = screenCenter.y - startOffset.y
                        val liveLen = sqrt(dx * dx + dy * dy)

                        // 1. Shadow under active line
                        drawLine(
                            color = Color.Black.copy(alpha = 0.4f),
                            start = Offset(startOffset.x + 1f, startOffset.y + 2f),
                            end = Offset(screenCenter.x + 1f, screenCenter.y + 2f),
                            strokeWidth = 7.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // 2. Luminous animated laser stream
                        drawLine(
                            color = colorPrimary.copy(alpha = 0.35f),
                            start = startOffset,
                            end = screenCenter,
                            strokeWidth = 8.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // 3. Flowing dynamic fine dashed scale line
                        drawLine(
                            color = colorPrimary,
                            start = startOffset,
                            end = screenCenter,
                            strokeWidth = 4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), dashPhase),
                            cap = StrokeCap.Round
                        )

                        // Solid endpoint cap at the moving end (screenCenter)
                        drawCircle(
                            color = Color.White,
                            center = screenCenter,
                            radius = 5.dp.toPx()
                        )
                        drawCircle(
                            color = colorSecondary,
                            center = screenCenter,
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

                // 2D. Draw start and confirmed anchor pin node markers (3D Spatial Anchors)
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
            }

            // 3. Dynamic Floating Measurement & Node Badges Overlay
            Box(modifier = Modifier.fillMaxSize()) {
                val screenW = localView.width.takeIf { it > 0 } ?: 1080
                val screenH = localView.height.takeIf { it > 0 } ?: 1920
                val projectedNodePoints = capturedPoints.map { pt ->
                    ArMath.projectWorldToScreen(pt, viewMatrix, projectionMatrix, screenW, screenH)
                }

                // 3A. Floating Node Badges (起點 A, 終點 B, 節點 C...)
                projectedNodePoints.forEachIndexed { index, proj ->
                    if (proj != null) {
                        val isStartNode = index == 0
                        val isLastNode = index == capturedPoints.size - 1 && capturedPoints.size > 1
                        val charLabel = ('A'.code + index).toChar()

                        val labelText = when {
                            isStartNode -> "起點 $charLabel"
                            isLastNode -> "終點 $charLabel"
                            else -> "節點 $charLabel"
                        }

                        Surface(
                            color = if (isStartNode) colorPrimary else if (isLastNode) colorSecondary else colorSurfaceVariant,
                            contentColor = if (isStartNode) colorOnPrimary else if (isLastNode) colorOnSecondary else colorOnSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        (proj.first - 42.dp.toPx()).toInt(),
                                        (proj.second - 48.dp.toPx()).toInt()
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isStartNode) Icons.Rounded.Flag else if (isLastNode) Icons.Rounded.SportsScore else Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = labelText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3B. Live Endpoint Preview Badge (When user has placed Start Point and is aiming for End Point)
                if (capturedPoints.size == 1) {
                    val nextChar = 'B'
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = (-45).dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AddLocationAlt,
                                contentDescription = null,
                                tint = colorPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "終點 $nextChar (點擊定位)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3C. Capsules for confirmed line segments
                if (capturedPoints.size >= 2) {
                    for (i in 0 until capturedPoints.size - 1) {
                        val mid3D = ArMath.midpoint(capturedPoints[i], capturedPoints[i + 1])
                        val midProj = ArMath.projectWorldToScreen(mid3D, viewMatrix, projectionMatrix, screenW, screenH)
                        if (midProj != null) {
                            val segDist = ArMath.distance(capturedPoints[i], capturedPoints[i + 1])
                            val distText = viewModel.formatLength(segDist, selectedUnit)

                            Surface(
                                color = colorPrimaryContainer,
                                shape = RoundedCornerShape(percent = 50),
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
                if (capturedPoints.isNotEmpty()) {
                    val lastPt = capturedPoints.last()
                    val liveTarget = liveTargetPoint

                    if (liveTarget != null && liveDistanceMeters != null && liveDistanceMeters!! > 0.0) {
                        val mid3D = ArMath.midpoint(lastPt, liveTarget)
                        val midProj = ArMath.projectWorldToScreen(mid3D, viewMatrix, projectionMatrix, screenW, screenH)
                        val badgePos = midProj ?: Pair(screenW / 2f, screenH / 2f - 120f)
                        val distText = viewModel.formatLength(liveDistanceMeters!!, selectedUnit)

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
            }

            // 4. Redesigned Futuristic AR Target Reticle (Spring Snap Aura + Rotating Crosshair Ticks + Center Laser Pinpoint)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = 14.dp.toPx()
                    val currentRadius = baseRadius * snapScaleAnimated * reticlePulseScale

                    // 1. Snapped Target Lock Radial Aura Glow
                    if (isSnapped) {
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = snapGlowAlphaAnimated * 0.45f),
                            center = center,
                            radius = currentRadius * 1.6f
                        )
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = snapGlowAlphaAnimated * 0.25f),
                            center = center,
                            radius = currentRadius * 2.2f
                        )
                    }

                    // 2. High-contrast ground shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        center = Offset(center.x + 1f, center.y + 1.5f),
                        radius = currentRadius,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // 3. Clean Elegant Static Outer Ring
                    drawCircle(
                        color = reticleColorAnimated,
                        center = center,
                        radius = currentRadius,
                        style = Stroke(
                            width = if (isSnapped) 2.5.dp.toPx() else 1.8.dp.toPx()
                        )
                    )

                    // 3.1 Multi-Sample Burst Averaging Dynamic Progress Arc (Precision Lock)
                    if (sensorTelemetry.multiSampleProgress > 0f) {
                        val arcRadius = currentRadius + 5.dp.toPx()
                        drawArc(
                            color = if (sensorTelemetry.isMultiSampleLocked) Color(0xFF00E676) else Color(0xFF00E5FF),
                            startAngle = -90f,
                            sweepAngle = sensorTelemetry.multiSampleProgress * 360f,
                            useCenter = false,
                            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                            size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 4. Precision Crosshair Hairlines (Top, Bottom, Left, Right Ticks)
                    val crossHairOffsetInner = currentRadius + 3.dp.toPx()
                    val crossHairLen = if (isSnapped) 10.dp.toPx() else 6.dp.toPx()
                    val crossHairStroke = if (isSnapped) 2.2.dp.toPx() else 1.6.dp.toPx()
                    val tickColor = if (isSnapped) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.9f)

                    // Top Hairline
                    drawLine(
                        color = tickColor,
                        start = Offset(center.x, center.y - crossHairOffsetInner),
                        end = Offset(center.x, center.y - crossHairOffsetInner - crossHairLen),
                        strokeWidth = crossHairStroke,
                        cap = StrokeCap.Round
                    )
                    // Bottom Hairline
                    drawLine(
                        color = tickColor,
                        start = Offset(center.x, center.y + crossHairOffsetInner),
                        end = Offset(center.x, center.y + crossHairOffsetInner + crossHairLen),
                        strokeWidth = crossHairStroke,
                        cap = StrokeCap.Round
                    )
                    // Left Hairline
                    drawLine(
                        color = tickColor,
                        start = Offset(center.x - crossHairOffsetInner, center.y),
                        end = Offset(center.x - crossHairOffsetInner - crossHairLen, center.y),
                        strokeWidth = crossHairStroke,
                        cap = StrokeCap.Round
                    )
                    // Right Hairline
                    drawLine(
                        color = tickColor,
                        start = Offset(center.x + crossHairOffsetInner, center.y),
                        end = Offset(center.x + crossHairOffsetInner + crossHairLen, center.y),
                        strokeWidth = crossHairStroke,
                        cap = StrokeCap.Round
                    )

                    // 5. Solid Center White & Accent Core Pinpoint Dot
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.4f),
                        center = Offset(center.x + 0.5f, center.y + 0.5f),
                        radius = 4.5.dp.toPx()
                    )
                    drawCircle(
                        color = Color.White,
                        center = center,
                        radius = 4.5.dp.toPx()
                    )
                    drawCircle(
                        color = reticleColorAnimated,
                        center = center,
                        radius = 2.5.dp.toPx()
                    )

                    // 6. Plane Locked Center Micro Particle Feedback Ring (When AR system detects planes)
                    if (planesCount > 0) {
                        val particleCount = 8
                        for (i in 0 until particleCount) {
                            val angle = (i * (360f / particleCount)) + (planeLockedParticleAnim * 360f)
                            val rad = Math.toRadians(angle.toDouble())
                            val orbitRadius = (18.dp.toPx()) + (kotlin.math.sin(planeLockedParticleAnim * 6.28318f + i).toFloat() * 2.5.dp.toPx())
                            val px = center.x + (kotlin.math.cos(rad).toFloat() * orbitRadius)
                            val py = center.y + (kotlin.math.sin(rad).toFloat() * orbitRadius)
                            val sineVal = kotlin.math.sin(planeLockedParticleAnim * 6.28318f + i)
                            val pAlpha = ((sineVal + 1f) / 2f).coerceIn(0.25f, 0.9f)

                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = pAlpha),
                                center = Offset(px, py),
                                radius = 2f * density
                            )
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
                // Top Gradient Blur Scrim
                GradientBlurScrim(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    isTop = true,
                    baseColor = Color.Black
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

                            // "清除" button
                            Surface(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .clickable {
                                        viewModel.clearActivePoints()
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    }
                                    .shadow(4.dp, RoundedCornerShape(20.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "清除",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.shadow(2.dp, RoundedCornerShape(20.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (trackingState == TrackingState.TRACKING) {
                                        Surface(
                                            color = if (sensorTelemetry.isMultiSampleLocked) Color(0xFF00E676) else colorPrimary,
                                            shape = CircleShape,
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                        Text(
                                            text = if (sensorTelemetry.isMultiSampleLocked) "超精準鎖定" else "已就緒",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        // Precision uncertainty pill
                                        Surface(
                                            color = (if (sensorTelemetry.isMultiSampleLocked) Color(0xFF00E676) else Color(0xFF00E5FF)).copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, (if (sensorTelemetry.isMultiSampleLocked) Color(0xFF00E676) else Color(0xFF00E5FF)).copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "±${"%.1f".format(sensorTelemetry.estimatedErrorMm)}mm",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (sensorTelemetry.isMultiSampleLocked) Color(0xFF00E676) else Color(0xFF00E5FF),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (sensorTelemetry.isOrthogonalSnapped) {
                                            Surface(
                                                color = Color(0xFFFFD54F).copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = "📐直角",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFFD54F),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (highFpsModeEnabled) {
                                            Surface(
                                                color = colorPrimary.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = "60Hz",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = colorPrimary,
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

                // Right: Essential Quick Actions (Torch, History, Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Flashlight / Torch
                    IconButton(
                        onClick = { viewModel.toggleTorch(context) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isTorchOn) Color(0xFFFFD54F) else Color.Black.copy(alpha = 0.55f),
                                CircleShape
                            )
                            .shadow(if (isTorchOn) 8.dp else 4.dp, CircleShape)
                            .testTag("flashlight_toggle_button")
                    ) {
                        Icon(
                            if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = "Flashlight",
                            tint = if (isTorchOn) Color(0xFF212121) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // History
                    IconButton(
                        onClick = onShowHistoryClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .shadow(4.dp, CircleShape)
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
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .shadow(4.dp, CircleShape)
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

            // Bottom subtle vignette for camera control deck
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.42f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding + 64.dp)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Live Auto-Detected Dimension / Guidance Pill with Fluid Spring Transition
                AnimatedContent(
                    targetState = capturedPoints.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(
                            initialScale = 0.92f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                        )).togetherWith(
                            fadeOut(animationSpec = tween(160)) + scaleOut(
                                targetScale = 0.92f,
                                animationSpec = tween(160)
                            )
                        )
                    },
                    label = "DimensionPillTransition"
                ) { hasPoints ->
                    if (hasPoints) {
                        val totalLen = viewModel.calculateTotalDistance()
                        val area = viewModel.calculatePolygonArea()
                        val height = viewModel.calculateVerticalHeight()

                        Surface(
                            color = Color.Black.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.shadow(8.dp, RoundedCornerShape(24.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val (badgeText, badgeIcon) = when {
                                    autoDetectedType == "AREA" && capturedPoints.size >= 3 ->
                                        "面積: ${viewModel.formatArea(area, selectedUnit)}" to Icons.Rounded.SquareFoot
                                    autoDetectedType == "HEIGHT" && capturedPoints.size >= 2 ->
                                        "高度: ${viewModel.formatLength(height, selectedUnit)}" to Icons.Rounded.Height
                                    else ->
                                        "總長: ${viewModel.formatLength(totalLen, selectedUnit)}" to Icons.Rounded.Straighten
                                }

                                Icon(
                                    badgeIcon,
                                    contentDescription = null,
                                    tint = colorPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = badgeText,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Guidance Pill
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.shadow(4.dp, RoundedCornerShape(18.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.TouchApp, null, tint = colorPrimary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "對準目標表面，點擊 ＋ 標定測量起點",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.undo()
                                    },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(Color.Black.copy(alpha = 0.60f), CircleShape)
                                        .shadow(4.dp, CircleShape)
                                ) {
                                    Icon(
                                        Icons.Rounded.Undo,
                                        contentDescription = "Undo",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.clearActivePoints()
                                    },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(Color.Black.copy(alpha = 0.60f), CircleShape)
                                        .shadow(4.dp, CircleShape)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = "Clear",
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Center Slot: Primary Circular Main Button with count badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Surface(
                            color = colorPrimary,
                            shape = CircleShape,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(68.dp)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.requestHitTest()
                                }
                                .testTag("add_point_fab")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "Add Point",
                                    tint = colorOnPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = capturedPoints.isNotEmpty(),
                            enter = scaleIn(
                                initialScale = 0.5f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(animationSpec = tween(180)),
                            exit = scaleOut(
                                targetScale = 0.5f,
                                animationSpec = tween(150)
                            ) + fadeOut(animationSpec = tween(150)),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(22.dp)
                                    .shadow(3.dp, CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${capturedPoints.size}",
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Right Slot (Balanced 1f weight): Save Result & Camera Shutter Button
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Save Result Button (Only shown when a valid measurement is completed)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = capturedPoints.size >= 2,
                                enter = scaleIn(
                                    initialScale = 0.8f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                ) + fadeIn(animationSpec = tween(180)),
                                exit = scaleOut(
                                    targetScale = 0.8f,
                                    animationSpec = tween(150)
                                ) + fadeOut(animationSpec = tween(150))
                            ) {
                                Surface(
                                    color = colorPrimary,
                                    shape = RoundedCornerShape(24.dp),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier
                                        .clickable {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            isShutterFlash = true
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(100)
                                                isShutterFlash = false
                                            }
                                            ShareUtility.captureAndSaveToGallery(localView) { uri ->
                                                if (uri != null) {
                                                    Toast.makeText(context, "已成功儲存至相簿", Toast.LENGTH_SHORT).show()
                                                    viewModel.saveMeasurementRecord(imagePath = uri.toString())
                                                } else {
                                                    Toast.makeText(context, "儲存失敗，請重試", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        .testTag("save_result_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.SaveAlt,
                                            contentDescription = "儲存結果",
                                            tint = colorOnPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "儲存",
                                            color = colorOnPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Camera Shutter Button
                            Surface(
                                color = Color.White.copy(alpha = 0.92f),
                                shape = CircleShape,
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        isShutterFlash = true
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(100)
                                            isShutterFlash = false
                                        }
                                        ShareUtility.captureViewSnapshot(localView) { path ->
                                            viewModel.saveMeasurementRecord(imagePath = path)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.PhotoCamera,
                                        contentDescription = "Take Photo Snapshot",
                                        tint = Color(0xFF1E293B),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
                                "${(sensorTelemetry.stabilityScore * 100).toInt()}% (${if (sensorTelemetry.isHandSteady) "🎯 穩定鎖定" else "微動追蹤"})",
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
                                "俯仰: ${df1.format(sensorTelemetry.pitchDeg)}° / 滾轉: ${df1.format(sensorTelemetry.rollDeg)}°",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 3. Barometer Altitude
                        if (sensorTelemetry.isBarometerAvailable) {
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
                                    "${if (sensorTelemetry.barometricAltitudeMeters >= 0) "+" else ""}${df2.format(sensorTelemetry.barometricAltitudeMeters)} m (${df1.format(sensorTelemetry.currentPressureHpa)} hPa)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 4. Proximity Sensor (Surface Contact Zero-Point)
                        if (sensorTelemetry.isProximityAvailable) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (sensorTelemetry.isProximityNear) colorPrimary else Color.Gray,
                                        shape = CircleShape,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                    Spacer(Modifier.width(6.dp))
                                    Text("近接貼面零點校準", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    if (sensorTelemetry.isProximityNear) "📐 貼面觸碰 (0 cm 零點補償)" else "遠離表面 (${df1.format(sensorTelemetry.proximityDistanceCm)} cm)",
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
                                "物理基線: ${df1.format(sensorTelemetry.stereoBaselineMm)} mm (置信度 ${(sensorTelemetry.stereoScaleConfidence * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
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
}
