package com.example.ui.components

import android.Manifest
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

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
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
    val isSnapped by viewModel.isSnapped.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val showPointCloud by viewModel.showPointCloud.collectAsState()
    val capturedPoints = viewModel.capturedPoints
    val sensorTelemetry by viewModel.sensorTelemetry.collectAsState()
    val sensorCorrectionEnabled by viewModel.sensorCorrectionEnabled.collectAsState()
    val highFpsModeEnabled by viewModel.highFpsModeEnabled.collectAsState()

    // AI Core Tile Recognition states
    val isAiTileMode by viewModel.isAiTileMode.collectAsState()
    val isAiTileAnalyzing by viewModel.isAiTileAnalyzing.collectAsState()
    val detectedTiles by viewModel.detectedTiles.collectAsState()
    val selectedTileForDetail by viewModel.selectedTileForDetail.collectAsState()
    val activeTilePreset by viewModel.activeTilePreset.collectAsState()

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
        initialValue = 0.92f,
        targetValue = 1.08f,
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
    val geminiGlowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "geminiGlowRotation"
    )
    val geminiFloatBob by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "geminiFloatBob"
    )
    val geminiLassoPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "geminiLassoPulse"
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
                                    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
                                    val camera2Extender = androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder)
                                    camera2Extender.setCaptureRequestOption(
                                        android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                        android.util.Range(60, 60)
                                    )
                                    camera2Extender.setCaptureRequestOption(
                                        android.hardware.camera2.CaptureRequest.CONTROL_MODE,
                                        android.hardware.camera2.CaptureRequest.CONTROL_MODE_AUTO
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
                                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                            } catch (e: Exception) {
                                android.util.Log.e("CameraXFallback", "Camera binding failed: ${e.message}")
                            }
                        }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                        previewView
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

                        // 3. Flowing dynamic dash line
                        drawLine(
                            color = colorPrimary,
                            start = startOffset,
                            end = screenCenter,
                            strokeWidth = 4.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), dashPhase),
                            cap = StrokeCap.Round
                        )

                        // 4. Live perpendicular tick marks along the dynamic active line
                        if (liveLen > 25f) {
                            val nx = -dy / liveLen
                            val ny = dx / liveLen
                            val tickHalfLen = 7.dp.toPx()

                            // End tick at start point
                            drawLine(
                                color = Color.White,
                                start = Offset(startOffset.x - nx * tickHalfLen, startOffset.y - ny * tickHalfLen),
                                end = Offset(startOffset.x + nx * tickHalfLen, startOffset.y + ny * tickHalfLen),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Ticks along the live path
                            val step = 32f
                            var d = step
                            while (d < liveLen - step) {
                                val px = startOffset.x + (dx / liveLen) * d
                                val py = startOffset.y + (dy / liveLen) * d
                                val subTickLen = 3.5.dp.toPx()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.6f),
                                    start = Offset(px - nx * subTickLen, py - ny * subTickLen),
                                    end = Offset(px + nx * subTickLen, py + ny * subTickLen),
                                    strokeWidth = 1.5.dp.toPx()
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
                    val liveTarget = viewModel.liveTargetPoint.value

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

                // 3E. AI Core Tile Detection Gemini-Style Glowing Holographic Lasso & Auto-Tracking Badges
                if (isAiTileMode && detectedTiles.isNotEmpty()) {
                    detectedTiles.forEach { tile ->
                        // Calculate real-time 3D projected screen coordinates or fallback to normalized box
                        val has3D = tile.worldCorners.size == 4
                        val projTL = if (has3D) ArMath.projectWorldToScreen(tile.worldCorners[0], viewMatrix, projectionMatrix, screenW, screenH) else null
                        val projTR = if (has3D) ArMath.projectWorldToScreen(tile.worldCorners[1], viewMatrix, projectionMatrix, screenW, screenH) else null
                        val projBR = if (has3D) ArMath.projectWorldToScreen(tile.worldCorners[2], viewMatrix, projectionMatrix, screenW, screenH) else null
                        val projBL = if (has3D) ArMath.projectWorldToScreen(tile.worldCorners[3], viewMatrix, projectionMatrix, screenW, screenH) else null

                        val is3DValid = projTL != null && projTR != null && projBR != null && projBL != null

                        val pTL = if (is3DValid) projTL!! else Pair(tile.leftNorm * screenW, tile.topNorm * screenH)
                        val pTR = if (is3DValid) projTR!! else Pair(tile.rightNorm * screenW, tile.topNorm * screenH)
                        val pBR = if (is3DValid) projBR!! else Pair(tile.rightNorm * screenW, tile.bottomNorm * screenH)
                        val pBL = if (is3DValid) projBL!! else Pair(tile.leftNorm * screenW, tile.bottomNorm * screenH)

                        val minX = minOf(pTL.first, pTR.first, pBR.first, pBL.first)
                        val maxX = maxOf(pTL.first, pTR.first, pBR.first, pBL.first)
                        val minY = minOf(pTL.second, pTR.second, pBR.second, pBL.second)
                        val maxY = maxOf(pTL.second, pTR.second, pBR.second, pBL.second)
                        val boxW = (maxX - minX).coerceAtLeast(60f)
                        val boxH = (maxY - minY).coerceAtLeast(60f)
                        val centerX = (pTL.first + pTR.first + pBR.first + pBL.first) / 4f
                        val centerY = (pTL.second + pTR.second + pBR.second + pBL.second) / 4f
                        val topMidX = (pTL.first + pTR.first) / 2f
                        val topMidY = (pTL.second + pTR.second) / 2f

                        // 1. Dynamic Organic Glowing Lasso & Holographic Surface Canvas (Circles the Tile)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = Path().apply {
                                moveTo(pTL.first, pTL.second)
                                lineTo(pTR.first, pTR.second)
                                lineTo(pBR.first, pBR.second)
                                lineTo(pBL.first, pBL.second)
                                close()
                            }

                            // Inner Holographic Tint
                            drawPath(
                                path = path,
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x384285F4),
                                        Color(0x18AB47BC),
                                        Color(0x0600E5FF),
                                        Color.Transparent
                                    ),
                                    center = Offset(centerX, centerY),
                                    radius = maxOf(boxW, boxH) * 0.75f
                                )
                            )

                            // Outer Soft Glowing Halo Lasso (Circle Effect)
                            drawPath(
                                path = path,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0x6664B5F6),
                                        Color(0x66BA68C8),
                                        Color(0x66FFD54F),
                                        Color(0x6680D8FF)
                                    ),
                                    start = Offset(minX, minY),
                                    end = Offset(maxX, maxY)
                                ),
                                style = Stroke(
                                    width = (7.dp.toPx() * geminiLassoPulse),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Primary Crisp Animated Glowing Contour Stroke
                            drawPath(
                                path = path,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF80D8FF),
                                        Color(0xFFE1BEE7),
                                        Color(0xFFFFD54F),
                                        Color(0xFF00E5FF),
                                        Color(0xFF80D8FF)
                                    ),
                                    start = Offset(minX, minY),
                                    end = Offset(maxX, maxY)
                                ),
                                style = Stroke(
                                    width = 2.6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), dashPhase)
                                )
                            )

                            // 4 Corner Tactile Brackets for AR Spatial Precision
                            val bracketLen = 22.dp.toPx().coerceAtMost(minOf(boxW, boxH) / 3.5f)
                            val bracketStroke = 3.5.dp.toPx()
                            val bracketColor = Color(0xFF80D8FF)

                            // Top-Left bracket
                            drawLine(bracketColor, Offset(pTL.first, pTL.second), Offset(pTL.first + bracketLen, pTL.second), bracketStroke, cap = StrokeCap.Round)
                            drawLine(bracketColor, Offset(pTL.first, pTL.second), Offset(pTL.first, pTL.second + bracketLen), bracketStroke, cap = StrokeCap.Round)

                            // Top-Right bracket
                            drawLine(bracketColor, Offset(pTR.first, pTR.second), Offset(pTR.first - bracketLen, pTR.second), bracketStroke, cap = StrokeCap.Round)
                            drawLine(bracketColor, Offset(pTR.first, pTR.second), Offset(pTR.first, pTR.second + bracketLen), bracketStroke, cap = StrokeCap.Round)

                            // Bottom-Right bracket
                            drawLine(bracketColor, Offset(pBR.first, pBR.second), Offset(pBR.first - bracketLen, pBR.second), bracketStroke, cap = StrokeCap.Round)
                            drawLine(bracketColor, Offset(pBR.first, pBR.second), Offset(pBR.first, pBR.second - bracketLen), bracketStroke, cap = StrokeCap.Round)

                            // Bottom-Left bracket
                            drawLine(bracketColor, Offset(pBL.first, pBL.second), Offset(pBL.first + bracketLen, pBL.second), bracketStroke, cap = StrokeCap.Round)
                            drawLine(bracketColor, Offset(pBL.first, pBL.second), Offset(pBL.first, pBL.second - bracketLen), bracketStroke, cap = StrokeCap.Round)
                        }

                        // Transparent Clickable Overlay Region for Tile Surface
                        Box(
                            modifier = Modifier
                                .offset { androidx.compose.ui.unit.IntOffset(minX.toInt(), minY.toInt()) }
                                .size(
                                    width = (boxW / localView.resources.displayMetrics.density).dp,
                                    height = (boxH / localView.resources.displayMetrics.density).dp
                                )
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.measureTileOneTap(tile)
                                }
                        )

                        // 2. Gemini AI Intelligence Floating Frosted Glass Pill Badge (Auto-Tracks Tile)
                        val pillWidthDp = 248.dp
                        val pillHeightDp = 48.dp
                        val density = localView.resources.displayMetrics.density
                        val pillWpx = pillWidthDp.value * density
                        val pillHpx = pillHeightDp.value * density

                        val targetPillX = (topMidX - pillWpx / 2f).toInt().coerceIn(16, (screenW - pillWpx - 16).toInt().coerceAtLeast(16))
                        val targetPillY = (topMidY - pillHpx - (22f * density) + (geminiFloatBob * density)).toInt().coerceIn(50, (screenH - 140).coerceAtLeast(50))

                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color(0xF2141722),
                            shadowElevation = 10.dp,
                            border = BorderStroke(
                                1.3.dp,
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF80D8FF),
                                        Color(0xFFBA68C8),
                                        Color(0xFFFFD54F),
                                        Color(0xFF80D8FF)
                                    )
                                )
                            ),
                            modifier = Modifier
                                .offset { androidx.compose.ui.unit.IntOffset(targetPillX, targetPillY) }
                                .width(pillWidthDp)
                                .height(pillHeightDp)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.measureTileOneTap(tile)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                // Gemini 4-Point AI Sparkle Icon with Vibrant Animated Gradient Aura
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                colors = listOf(
                                                    Color(0xFFFFB74D),
                                                    Color(0xFFFF4081),
                                                    Color(0xFF7C4DFF),
                                                    Color(0xFF00E5FF),
                                                    Color(0xFFFFB74D)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Gemini AI",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Tile Identification Spec Details
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${tile.label} (${tile.estimatedWidthCm.toInt()}×${tile.estimatedHeightCm.toInt()} cm)",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${tile.material} · 輕觸即測",
                                        color = Color(0xFF80D8FF),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }

                                // Arrow Indicator
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 3F. AI Scanning Laser Sweep Line Animation (when analyzing)
                if (isAiTileMode && isAiTileAnalyzing) {
                    val scanProgress by infiniteTransition.animateFloat(
                        initialValue = 0.15f,
                        targetValue = 0.85f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1800, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "laserSweep"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val currentY = size.height * scanProgress
                        // Laser Glow Gradient
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colorPrimary.copy(alpha = 0.8f),
                                    Color.White,
                                    colorPrimary.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(0f, currentY),
                            end = Offset(size.width, currentY),
                            strokeWidth = 4.dp.toPx()
                        )
                    }

                    // Floating prompt indicator
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colorPrimary
                            )
                            Text(
                                text = "AI Core 正在識別磁磚紋理與接縫幾何...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 4. Center Target Reticle (Clean Dynamic Precision Ring + White Center Dot)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(54.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Subtle contact shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.25f),
                        center = Offset(center.x, center.y + 1f),
                        radius = 11.dp.toPx(),
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Dynamic Outer Precision Ring
                    drawCircle(
                        color = colorPrimary,
                        center = center,
                        radius = 11.dp.toPx() * (if (isSnapped) 1.2f else reticlePulseScale),
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Solid Center White Dot
                    drawCircle(
                        color = Color.White,
                        center = center,
                        radius = 4.5.dp.toPx()
                    )
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
                // Left: Status badge or clear button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (capturedPoints.isNotEmpty()) {
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
                                        color = colorPrimary,
                                        shape = CircleShape,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                    Text(
                                        text = "已就緒",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
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

                // Right: Essential Quick Actions (AI Tile, Torch, History, Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // AI Core Tile Recognition Button
                    Surface(
                        color = if (isAiTileMode) colorPrimary else Color.Black.copy(alpha = 0.55f),
                        contentColor = if (isAiTileMode) colorOnPrimary else Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .shadow(if (isAiTileMode) 6.dp else 3.dp, RoundedCornerShape(20.dp))
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                ShareUtility.captureViewSnapshot(localView) { path ->
                                    val bmp = if (path != null) android.graphics.BitmapFactory.decodeFile(path) else null
                                    viewModel.toggleAiTileMode(bmp)
                                }
                            }
                            .testTag("ai_tile_recognition_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isAiTileAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = if (isAiTileMode) colorOnPrimary else colorPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = "AI Tile Recognition",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isAiTileMode) colorOnPrimary else colorPrimary
                                )
                            }
                            Text(
                                text = if (isAiTileAnalyzing) "識別中..." else if (isAiTileMode) "磁磚模式" else "AI 磁磚",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Flashlight / Torch
                    IconButton(
                        onClick = { viewModel.toggleTorch(context) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isTorchOn) colorPrimary else Color.Black.copy(alpha = 0.55f),
                                CircleShape
                            )
                            .shadow(4.dp, CircleShape)
                    ) {
                        Icon(
                            if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = "Flashlight",
                            tint = if (isTorchOn) colorOnPrimary else Color.White,
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

            // Bottom Gradient Blur Scrim for camera control deck
            GradientBlurScrim(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .align(Alignment.BottomCenter),
                isTop = false,
                baseColor = Color.Black
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // AI Tile Interactive Floating Deck
                if (isAiTileMode) {
                    TileFloatingControlDeck(
                        viewModel = viewModel,
                        detectedTiles = detectedTiles,
                        isAiTileMode = isAiTileMode,
                        isAiTileAnalyzing = isAiTileAnalyzing,
                        activePreset = activeTilePreset,
                        onOpenDetailSheet = {
                            val targetTile = detectedTiles.firstOrNull() ?: DetectedTile(
                                label = activeTilePreset.name,
                                material = activeTilePreset.defaultMaterial,
                                estimatedWidthCm = activeTilePreset.widthCm,
                                estimatedHeightCm = activeTilePreset.heightCm,
                                areaM2 = activeTilePreset.singleTileAreaM2,
                                groutWidthMm = activeTilePreset.defaultGroutMm
                            )
                            viewModel.selectTileForDetail(targetTile)
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Live Auto-Detected Dimension / Result Pill
                if (capturedPoints.isNotEmpty()) {
                    val totalLen = viewModel.calculateTotalDistance()
                    val area = viewModel.calculatePolygonArea()
                    val height = viewModel.calculateVerticalHeight()

                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .shadow(6.dp, RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
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
                }

                // Interactive Bottom Action Deck
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Undo button (or spacer)
                    if (capturedPoints.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.undo() },
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .shadow(4.dp, CircleShape)
                        ) {
                            Icon(
                                Icons.Rounded.Undo,
                                contentDescription = "Undo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(54.dp))
                    }

                    // Center: Dynamic Primary Circular Main Button (+ when empty, ✓ when measuring)
                    Surface(
                        color = colorPrimary,
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                viewModel.requestHitTest()
                            }
                            .testTag("add_point_fab")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (capturedPoints.isEmpty()) Icons.Rounded.Add else Icons.Rounded.Check,
                                contentDescription = if (capturedPoints.isEmpty()) "Add Point" else "Confirm",
                                tint = colorOnPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Right: Camera Shutter Button
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(54.dp)
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                isShutterFlash = true
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(120)
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
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
                                    color = colorPrimary,
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

    // AI Core Tile Detail & Estimator Bottom Sheet
    if (selectedTileForDetail != null) {
        TileDetailBottomSheet(
            tile = selectedTileForDetail!!,
            viewModel = viewModel,
            onDismiss = { viewModel.selectTileForDetail(null) }
        )
    }
}
