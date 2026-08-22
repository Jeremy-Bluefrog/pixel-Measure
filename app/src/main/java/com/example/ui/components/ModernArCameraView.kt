package com.example.ui.components

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import com.example.logic.ar.ArMath
import com.example.logic.ar.ModernArGlView
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
    onShowSettingsClick: () -> Unit
) {
    val context = LocalContext.current
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
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val liveDistanceMeters by viewModel.liveDistanceMeters.collectAsState()
    val isSnapped by viewModel.isSnapped.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val showPointCloud by viewModel.showPointCloud.collectAsState()
    val capturedPoints = viewModel.capturedPoints

    // Touch ripple visual pings
    val pings = remember { mutableStateListOf<Pair<Offset, Animatable<Float, AnimationVector1D>>>() }

    // Help Dialog
    var showHelpDialog by remember { mutableStateOf(false) }

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

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSecondary = MaterialTheme.colorScheme.secondary
    val colorTertiary = MaterialTheme.colorScheme.tertiary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface

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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorPrimary)
                }
            }

            // Google Measure Signature Colors
            val googleYellow = Color(0xFFFBBC04)
            val googleDarkText = Color(0xFF202124)
            val guideWhite = Color(0xDDFFFFFF)

            // 2. 3D Augmented Overlay Canvas (Planes, Projected Points, Lines, Measurements, Pings)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val screenW = size.width.toInt()
                val screenH = size.height.toInt()
                val screenCenter = Offset(size.width / 2f, size.height / 2f)

                // Draw touch ripples
                pings.forEach { (offset, anim) ->
                    val progress = anim.value
                    drawCircle(
                        color = googleYellow.copy(alpha = 0.5f * (1f - progress)),
                        center = offset,
                        radius = 8.dp.toPx() + 36.dp.toPx() * progress,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                // Project 3D points to 2D screen positions
                val projectedPoints = capturedPoints.map { pt ->
                    ArMath.projectWorldToScreen(pt, viewMatrix, projectionMatrix, screenW, screenH)
                }

                // 2A. If measuring in progress (>= 1 point), draw the extended collinear dashed alignment guide line
                if (projectedPoints.isNotEmpty()) {
                    val lastProj = projectedPoints.last()
                    if (lastProj != null) {
                        val pStart = Offset(lastProj.first, lastProj.second)
                        val pEnd = screenCenter
                        val dx = pEnd.x - pStart.x
                        val dy = pEnd.y - pStart.y
                        val dist = sqrt(dx * dx + dy * dy)

                        if (dist > 15f) {
                            val ux = dx / dist
                            val uy = dy / dist

                            // Extend line across the entire screen
                            val extStart = Offset(pStart.x - ux * 2500f, pStart.y - uy * 2500f)
                            val extEnd = Offset(pEnd.x + ux * 2500f, pEnd.y + uy * 2500f)

                            // Subtle dark drop shadow for white dashed guide
                            drawLine(
                                color = Color.Black.copy(alpha = 0.25f),
                                start = Offset(extStart.x + 1f, extStart.y + 1f),
                                end = Offset(extEnd.x + 1f, extEnd.y + 1f),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
                            )
                            // White dashed reference line (Google Measure signature alignment line)
                            drawLine(
                                color = guideWhite,
                                start = extStart,
                                end = extEnd,
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
                            )
                        }
                    }
                }

                // 2B. Draw confirmed connecting 3D lines
                if (projectedPoints.size >= 2) {
                    for (i in 0 until projectedPoints.size - 1) {
                        val p1 = projectedPoints[i]
                        val p2 = projectedPoints[i + 1]
                        if (p1 != null && p2 != null) {
                            val startOffset = Offset(p1.first, p1.second)
                            val endOffset = Offset(p2.first, p2.second)

                            // Subtle contact shadow
                            drawLine(
                                color = Color.Black.copy(alpha = 0.2f),
                                start = Offset(startOffset.x + 1f, startOffset.y + 2f),
                                end = Offset(endOffset.x + 1f, endOffset.y + 2f),
                                strokeWidth = 4.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Core solid yellow line
                            drawLine(
                                color = googleYellow,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = 4.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Closed Polygon in Area mode
                    if (subMode == 1 && projectedPoints.size >= 3) {
                        val first = projectedPoints.first()
                        val last = projectedPoints.last()
                        if (first != null && last != null) {
                            drawLine(
                                color = googleYellow.copy(alpha = 0.85f),
                                start = Offset(last.first, last.second),
                                end = Offset(first.first, first.second),
                                strokeWidth = 3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), dashPhase)
                            )
                        }
                    }
                }

                // 2C. Draw active dynamic line from last anchor point to current center reticle
                if (projectedPoints.isNotEmpty()) {
                    val lastPt = projectedPoints.last()
                    if (lastPt != null) {
                        val startOffset = Offset(lastPt.first, lastPt.second)

                        // Subtle shadow under active line
                        drawLine(
                            color = Color.Black.copy(alpha = 0.2f),
                            start = Offset(startOffset.x + 1f, startOffset.y + 2f),
                            end = Offset(screenCenter.x + 1f, screenCenter.y + 2f),
                            strokeWidth = 4.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Solid Google Yellow line extending to target
                        drawLine(
                            color = if (isSnapped) Color(0xFF00E676) else googleYellow,
                            start = startOffset,
                            end = screenCenter,
                            strokeWidth = 4.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 2D. Draw start and confirmed anchor pin points
                projectedPoints.forEachIndexed { index, proj ->
                    if (proj != null) {
                        val offset = Offset(proj.first, proj.second)

                        // Shadow
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.3f),
                            center = Offset(offset.x, offset.y + 2f),
                            radius = 8.dp.toPx()
                        )
                        // Solid Yellow Pin
                        drawCircle(
                            color = googleYellow,
                            center = offset,
                            radius = 8.dp.toPx()
                        )
                        // Center White Dot
                        drawCircle(
                            color = Color.White,
                            center = offset,
                            radius = 4.dp.toPx()
                        )
                    }
                }
            }

            // 3. Google Measure Signature Floating Measurement Capsules (Yellow Pills)
            Box(modifier = Modifier.fillMaxSize()) {
                val screenW = LocalView.current.width.takeIf { it > 0 } ?: 1080
                val screenH = LocalView.current.height.takeIf { it > 0 } ?: 1920

                // 3A. Capsules for confirmed line segments
                if (capturedPoints.size >= 2) {
                    for (i in 0 until capturedPoints.size - 1) {
                        val mid3D = ArMath.midpoint(capturedPoints[i], capturedPoints[i + 1])
                        val midProj = ArMath.projectWorldToScreen(mid3D, viewMatrix, projectionMatrix, screenW, screenH)
                        if (midProj != null) {
                            val segDist = ArMath.distance(capturedPoints[i], capturedPoints[i + 1])
                            val distText = viewModel.formatLength(segDist, selectedUnit)

                            Surface(
                                color = googleYellow,
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
                                    color = googleDarkText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 3B. Active dynamic measurement capsule positioned along the live line
                if (capturedPoints.isNotEmpty()) {
                    val lastPt = capturedPoints.last()
                    val liveTarget = viewModel.liveTargetPoint.value

                    if (liveTarget != null && liveDistanceMeters != null && liveDistanceMeters!! > 0.0) {
                        val mid3D = ArMath.midpoint(lastPt, liveTarget)
                        val midProj = ArMath.projectWorldToScreen(mid3D, viewMatrix, projectionMatrix, screenW, screenH)
                        val badgePos = midProj ?: Pair(screenW / 2f, screenH / 2f - 120f)
                        val distText = viewModel.formatLength(liveDistanceMeters!!, selectedUnit)

                        Surface(
                            color = if (isSnapped) Color(0xFF00E676) else googleYellow,
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
                                color = googleDarkText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // 4. Center Target Reticle (Google Measure: Solid white inner dot + thin yellow outer ring)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(48.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Subtle contact shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.25f),
                        center = Offset(center.x, center.y + 1f),
                        radius = 11.dp.toPx(),
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Google Yellow Outer Ring
                    drawCircle(
                        color = if (isSnapped) Color(0xFF00E676) else googleYellow,
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

            // 5. Top Google Measure Clean Bar (Tracking / Clear All, Unit Selector, More Menu)
            var showMenu by remember { mutableStateOf(false) }
            var showUnitMenu by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Google Measure "全部清除" or Tracking Quality Pill
                    if (capturedPoints.isNotEmpty()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .clickable {
                                    viewModel.clearActivePoints()
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                }
                                .shadow(4.dp, RoundedCornerShape(20.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Clear All",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "全部清除",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        // Tracking Quality Pill
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.shadow(4.dp, RoundedCornerShape(20.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (trackingState == TrackingState.TRACKING) {
                                    Surface(
                                        color = Color(0xFF4CAF50),
                                        shape = CircleShape,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                    Text(
                                        text = "$surfaceTypeAtCenter",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = googleYellow
                                    )
                                    Text(
                                        text = "尋找空間表面...",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }

                    // Right: Quick Unit Switcher + 3-Dots Overflow Menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Unit Selector Chip
                        Box {
                            Surface(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .clickable { showUnitMenu = true }
                                    .shadow(4.dp, RoundedCornerShape(20.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedUnit.uppercase(),
                                        color = googleYellow,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Switch Unit",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showUnitMenu,
                                onDismissRequest = { showUnitMenu = false }
                            ) {
                                listOf(
                                    "cm" to "公分 (cm)",
                                    "m" to "公尺 (m)",
                                    "in" to "英吋 (in)",
                                    "ft" to "英呎 (ft)",
                                    "yd" to "碼 (yd)"
                                ).forEach { (unitCode, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                label,
                                                fontWeight = if (selectedUnit == unitCode) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSelectedUnit(unitCode)
                                            showUnitMenu = false
                                        },
                                        leadingIcon = if (selectedUnit == unitCode) {
                                            { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                        } else null
                                    )
                                }
                            }
                        }

                        // Top-Right 3-Dots Overflow Menu
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .shadow(4.dp, CircleShape)
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isTorchOn) "關閉手電筒" else "開啟手電筒") },
                                    leadingIcon = {
                                        Icon(
                                            if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleTorch(context)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (showPointCloud) "隱藏掃描特徵點" else "顯示掃描特徵點") },
                                    leadingIcon = {
                                        Icon(
                                            if (showPointCloud) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.setShowPointCloud(!showPointCloud)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("歷史測量紀錄") },
                                    leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onShowHistoryClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("設定") },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onShowSettingsClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("使用指南") },
                                    leadingIcon = { Icon(Icons.Rounded.HelpOutline, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showHelpDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Selector Chips (Google Measure Style: Length & Area prominent)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple(0, "長度測量", Icons.Rounded.Straighten),
                        Triple(1, "面積測量", Icons.Rounded.SquareFoot),
                        Triple(2, "高度測量", Icons.Rounded.Height),
                        Triple(3, "3D 體積", Icons.Rounded.ViewInAr),
                        Triple(4, "圓形直徑", Icons.Rounded.RadioButtonUnchecked),
                        Triple(5, "空間角度", Icons.Rounded.ChangeHistory)
                    )

                    items(modes.size) { idx ->
                        val (modeId, label, icon) = modes[idx]
                        FilterChip(
                            selected = subMode == modeId,
                            onClick = { viewModel.setCameraSubMode(modeId) },
                            label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = googleYellow,
                                selectedLabelColor = googleDarkText,
                                selectedLeadingIconColor = googleDarkText,
                                containerColor = Color.Black.copy(alpha = 0.45f),
                                labelColor = Color.White,
                                iconColor = Color.White
                            )
                        )
                    }
                }
            }

            // 6. Bottom Google Measure Control Deck (+ / ✓ Button & Camera Shutter)
            var isShutterFlash by remember { mutableStateOf(false) }

            // Shutter Flash White Animation
            if (isShutterFlash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.75f))
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
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

                    // Center: Google Measure Signature White Circular Main Button (+ when empty, ✓ when measuring)
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                if (capturedPoints.isEmpty()) {
                                    // First point
                                    viewModel.requestHitTest()
                                } else {
                                    // Confirm/Finish current measurement segment
                                    viewModel.requestHitTest()
                                }
                            }
                            .testTag("add_point_fab")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (capturedPoints.isEmpty()) Icons.Rounded.Add else Icons.Rounded.Check,
                                contentDescription = if (capturedPoints.isEmpty()) "Add Point" else "Confirm",
                                tint = Color(0xFF202124),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Right: Google Measure Camera Shutter Button
                    Surface(
                        color = Color.White,
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
                                viewModel.saveMeasurementRecord()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.PhotoCamera,
                                contentDescription = "Take Photo Snapshot",
                                tint = Color(0xFF202124),
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
}
