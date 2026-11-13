package com.example.ui.components

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.ShareUtility
import com.example.logic.camera.ArVideoRecorder
import com.example.logic.vulkan.VulkanArSceneView
import com.example.ui.theme.ExpressiveCookie8Shape
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.rememberExpressiveConnectedShape
import com.example.ui.theme.rememberExpressivePressCorner
import com.example.ui.viewmodel.MeasureViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ModernArBottomDeck(
    viewModel: MeasureViewModel,
    subMode: Int,
    selectedUnit: String,
    hasCapturedPoints: Boolean,
    isWaitingForSecondPoint: Boolean,
    capturedPointsCount: Int,
    isMeasurementAvailable: Boolean,
    uiButtonScale: Float,
    isRecordingVideo: Boolean,
    bottomPadding: Dp,
    textureViewRef: () -> TextureView?,
    modernArVulkanViewRef: () -> VulkanArSceneView? = { null },
    cameraAspectRatio: String,
    useDisplayP3ColorSpace: Boolean,
    videoRecorder: ArVideoRecorder,
    onShowHistoryClick: () -> Unit,
    onTriggerShutterFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val localView = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnPrimary = MaterialTheme.colorScheme.onPrimary
    val colorOnSurface = MaterialTheme.colorScheme.onSurface

    val addFabContainerColor by animateColorAsState(
        targetValue = if (isMeasurementAvailable) colorPrimary else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = ExpressiveMotion.effectsDefault(),
        label = "addFabContainerColor"
    )

    val addFabIconColor by animateColorAsState(
        targetValue = if (isMeasurementAvailable) colorOnPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = ExpressiveMotion.effectsDefault(),
        label = "addFabIconColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .padding(bottom = bottomPadding + 12.dp)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Real-Time Measurement Completed Summary Card (M3 Expressive Spring + Numeral Typography + Connected Actions)
        AnimatedVisibility(
            visible = hasCapturedPoints && capturedPointsCount >= 2,
            enter = fadeIn(animationSpec = ExpressiveMotion.effectsDefault()) +
                expandVertically(animationSpec = ExpressiveMotion.spatialDefault()) +
                scaleIn(initialScale = 0.92f, animationSpec = ExpressiveMotion.spatialDefault()),
            exit = fadeOut(animationSpec = ExpressiveMotion.effectsFast()) +
                shrinkVertically(animationSpec = ExpressiveMotion.spatialFast())
        ) {
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                MeasurementLiveSummaryCard(
                    viewModel = viewModel,
                    subMode = subMode,
                    selectedUnit = selectedUnit,
                    onShowHistory = onShowHistoryClick
                )
            }
        }

        // 2. Dynamic Live Measurement Guidance & Status Pill
        AnimatedVisibility(
            visible = isWaitingForSecondPoint,
            enter = fadeIn(animationSpec = ExpressiveMotion.effectsDefault()) +
                expandVertically(animationSpec = ExpressiveMotion.spatialDefault()) +
                scaleIn(initialScale = 0.90f, animationSpec = ExpressiveMotion.spatialDefault()),
            exit = fadeOut(animationSpec = ExpressiveMotion.effectsFast()) +
                shrinkVertically(animationSpec = ExpressiveMotion.spatialFast())
        ) {
            Box(modifier = Modifier.padding(bottom = 10.dp)) {
                LiveDistanceBadge(
                    viewModel = viewModel,
                    selectedUnit = selectedUnit
                )
            }
        }

        // 3. Interactive Bottom Action Deck (Plus button fixed dead-center, expressive back button on left, shutter on right)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.Center
        ) {
            // Center: M3 Expressive Morphing Add-Point FAB (Always fixed dead-center)
            val addFabInteractionSource = remember { MutableInteractionSource() }
            val isAddFabPressed by addFabInteractionSource.collectIsPressedAsState()

            val addFabCornerRadius by animateDpAsState(
                targetValue = when {
                    isAddFabPressed -> 16.dp
                    isWaitingForSecondPoint -> 20.dp
                    else -> 32.dp
                },
                animationSpec = ExpressiveMotion.spatialFast(),
                label = "addFabCornerRadius"
            )
            val addFabScale by animateFloatAsState(
                targetValue = when {
                    isAddFabPressed -> 0.89f
                    isMeasurementAvailable -> 1.03f
                    else -> 0.96f
                },
                animationSpec = ExpressiveMotion.spatialFast(),
                label = "addFabScale"
            )

            Surface(
                color = addFabContainerColor,
                shape = RoundedCornerShape(addFabCornerRadius),
                border = if (!isMeasurementAvailable) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
                } else {
                    BorderStroke(1.5.dp, colorOnPrimary.copy(alpha = 0.18f))
                },
                shadowElevation = 0.dp,
                onClick = {
                    if (isMeasurementAvailable) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.requestHitTest()
                    } else {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    }
                },
                interactionSource = addFabInteractionSource,
                modifier = Modifier
                    .size((64 * uiButtonScale).dp)
                    .graphicsLayer {
                        scaleX = addFabScale
                        scaleY = addFabScale
                    }
                    .testTag("add_point_fab")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isWaitingForSecondPoint,
                        transitionSpec = {
                            (fadeIn(animationSpec = ExpressiveMotion.effectsFast()) +
                                scaleIn(initialScale = 0.55f, animationSpec = ExpressiveMotion.spatialFast()))
                                .togetherWith(
                                    fadeOut(animationSpec = ExpressiveMotion.effectsFast()) +
                                        scaleOut(targetScale = 0.55f, animationSpec = ExpressiveMotion.spatialFast())
                                )
                        },
                        label = "AddPointFabIconTransition"
                    ) { waitingForSecond ->
                        Icon(
                            imageVector = if (waitingForSecond) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = if (waitingForSecond) "確認第二點" else "加入點",
                            tint = addFabIconColor,
                            modifier = Modifier.size((34 * uiButtonScale).dp)
                        )
                    }
                }
            }

            // Left Side (Back Button) & Right Side (Shutter Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hasCapturedPoints,
                        enter = fadeIn(animationSpec = ExpressiveMotion.effectsDefault()) +
                            scaleIn(initialScale = 0.72f, animationSpec = ExpressiveMotion.spatialFast()) +
                            slideInHorizontally(initialOffsetX = { -it }, animationSpec = ExpressiveMotion.spatialDefault()),
                        exit = fadeOut(animationSpec = ExpressiveMotion.effectsFast()) +
                            scaleOut(targetScale = 0.72f, animationSpec = ExpressiveMotion.spatialFast()) +
                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = ExpressiveMotion.spatialFast())
                    ) {
                        val backInteraction = remember { MutableInteractionSource() }
                        val isBackPressed by backInteraction.collectIsPressedAsState()
                        val backCorner by rememberExpressivePressCorner(
                            isPressed = isBackPressed,
                            restingCorner = 24.dp,
                            pressedCorner = 12.dp
                        )
                        val backScale by animateFloatAsState(
                            targetValue = if (isBackPressed) 0.90f else 1.0f,
                            animationSpec = ExpressiveMotion.spatialFast(),
                            label = "backBtnScale"
                        )

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                viewModel.undo()
                            },
                            interactionSource = backInteraction,
                            shape = RoundedCornerShape(backCorner),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.78f else 0.92f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .size((48 * uiButtonScale).dp)
                                .graphicsLayer {
                                    scaleX = backScale
                                    scaleY = backScale
                                }
                                .testTag("back_step_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "返回上一步",
                                    tint = colorOnSurface,
                                    modifier = Modifier.size((24 * uiButtonScale).dp)
                                )
                            }
                        }
                    }
                }

                // Camera Shutter Button
                val isCameraVideoMode by viewModel.isCameraVideoMode.collectAsStateWithLifecycle()

                PixelShutterButton(
                    size = (54 * uiButtonScale).dp,
                    isRecording = isRecordingVideo,
                    innerCircleColor = if (isCameraVideoMode) Color(0xFFE53935) else Color.White,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        if (isRecordingVideo) {
                            videoRecorder.stopRecording { videoPath, thumbPath, durationSec ->
                                if (videoPath != null) {
                                    viewModel.saveMeasurementRecord(
                                        imagePath = thumbPath ?: videoPath,
                                        customNotes = "AR 測量錄影 (${durationSec}秒): $videoPath"
                                    )
                                    viewModel.showToast("AR 測量錄影已儲存 (${durationSec}秒)")
                                } else {
                                    viewModel.showToast("錄影未完成或時間過短")
                                }
                            }
                        } else if (isCameraVideoMode) {
                            val currentTv = textureViewRef()
                            val currentVulkan = modernArVulkanViewRef()
                            videoRecorder.startRecording(
                                view = localView,
                                cameraBitmapProvider = {
                                    if (currentTv != null && currentTv.isAvailable) {
                                        try { currentTv.bitmap } catch (_: Throwable) { null }
                                    } else if (currentVulkan != null) {
                                        var vkBmp: Bitmap? = null
                                        val latch = java.util.concurrent.CountDownLatch(1)
                                        currentVulkan.captureBitmap { bmp ->
                                            vkBmp = bmp
                                            latch.countDown()
                                        }
                                        latch.await(80, java.util.concurrent.TimeUnit.MILLISECONDS)
                                        vkBmp
                                    } else {
                                        null
                                    }
                                },
                                onStarted = {
                                    viewModel.showToast("開始 AR 測量錄影 (點擊快門按鈕即可停止)")
                                }
                            )
                        } else {
                            coroutineScope.launch {
                                try {
                                    viewModel.setIsTakingPhoto(true)
                                    kotlinx.coroutines.delay(40)

                                    val currentTv = textureViewRef()
                                    val currentVulkan = modernArVulkanViewRef()

                                    val onCameraFrameReady: (Bitmap?) -> Unit = { cameraBmp ->
                                        ShareUtility.captureArCompositeSnapshot(
                                            view = localView,
                                            cameraBitmap = cameraBmp,
                                            textureView = currentTv,
                                            glView = currentVulkan,
                                            aspectRatio = cameraAspectRatio,
                                            useDisplayP3 = useDisplayP3ColorSpace,
                                            primaryMeasurement = viewModel.getPrimaryMeasurementDisplayString()
                                        ) { savedPath ->
                                            viewModel.setIsTakingPhoto(false)
                                            onTriggerShutterFlash()
                                            viewModel.saveMeasurementRecord(imagePath = savedPath)
                                        }
                                    }

                                    if (currentTv != null && currentTv.isAvailable) {
                                        val bmp = try {
                                            currentTv.bitmap ?: if (localView.width > 0 && localView.height > 0) {
                                                currentTv.getBitmap(localView.width, localView.height)
                                            } else null
                                        } catch (e: Throwable) {
                                            try { currentTv.bitmap } catch (_: Throwable) { null }
                                        }
                                        onCameraFrameReady(bmp)
                                    } else if (currentVulkan != null) {
                                        currentVulkan.captureBitmap { vkBmp ->
                                            onCameraFrameReady(vkBmp)
                                        }
                                    } else {
                                        onCameraFrameReady(null)
                                    }
                                } catch (e: Throwable) {
                                    viewModel.setIsTakingPhoto(false)
                                    android.util.Log.e("ModernArBottomDeck", "Error capturing snapshot", e)
                                }
                            }
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        if (!isRecordingVideo) {
                            val currentTv = textureViewRef()
                            val currentVulkan = modernArVulkanViewRef()
                            videoRecorder.startRecording(
                                view = localView,
                                cameraBitmapProvider = {
                                    if (currentTv != null && currentTv.isAvailable) {
                                        try { currentTv.bitmap } catch (_: Throwable) { null }
                                    } else if (currentVulkan != null) {
                                        var vkBmp: Bitmap? = null
                                        val latch = java.util.concurrent.CountDownLatch(1)
                                        currentVulkan.captureBitmap { bmp ->
                                            vkBmp = bmp
                                            latch.countDown()
                                        }
                                        latch.await(80, java.util.concurrent.TimeUnit.MILLISECONDS)
                                        vkBmp
                                    } else {
                                        null
                                    }
                                },
                                onStarted = {
                                    viewModel.showToast("開始 AR 測量錄影 (點擊快門按鈕即可停止)")
                                }
                            )
                        }
                    },
                    testTag = "camera_shutter_button"
                )
            }
        }
    }
}

@Composable
private fun LiveDistanceBadge(
    viewModel: MeasureViewModel,
    selectedUnit: String
) {
    val liveDistanceMeters by viewModel.liveDistanceMeters.collectAsStateWithLifecycle()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
        shadowElevation = 0.dp,
        modifier = Modifier.animateContentSize(animationSpec = ExpressiveMotion.spatialDefault())
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Straighten, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            val liveDistText = liveDistanceMeters?.let { viewModel.formatLength(it, selectedUnit) } ?: "量測中..."
            Text(
                text = "即時長度: $liveDistText • 輕觸 ✓ 釘選終點",
                style = ExpressiveTypography.labelNumeral,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MeasurementLiveSummaryCard(
    viewModel: MeasureViewModel,
    subMode: Int,
    selectedUnit: String,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val capturedPoints = viewModel.capturedPoints
    if (capturedPoints.size < 2) return

    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var isCopied by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    LaunchedEffect(capturedPoints.size) {
        isSaved = false
        isCopied = false
    }

    val autoDetectedType by viewModel.autoDetectedType.collectAsStateWithLifecycle()
    val effectiveType = if (subMode == 0) autoDetectedType else when (subMode) {
        1 -> "AREA"
        2 -> "HEIGHT"
        3 -> "VOLUME"
        4 -> "CIRCLE"
        5 -> "ANGLE"
        else -> "DISTANCE"
    }

    val (primaryText, secondaryText, icon) = when (effectiveType) {
        "AREA" -> {
            val area = viewModel.calculatePolygonArea()
            val ping = String.format(Locale.US, "%.2f 坪", area * 0.3025)
            val sqft = String.format(Locale.US, "%.1f sq ft", area * 10.7639)
            Triple(viewModel.formatArea(area, selectedUnit), "$ping · $sqft", Icons.Rounded.SquareFoot)
        }
        "HEIGHT" -> {
            val h = viewModel.calculateVerticalHeight()
            val cm = String.format(Locale.US, "%.1f cm", h * 100)
            val ft = String.format(Locale.US, "%.2f ft", h * 3.28084)
            val inch = String.format(Locale.US, "%.1f in", h * 39.3701)
            Triple("垂直高 ${viewModel.formatLength(h, selectedUnit)}", "$cm · $ft · $inch", Icons.Rounded.Height)
        }
        "VOLUME" -> {
            val box = viewModel.calculateBoundingBox()
            val liters = String.format(Locale.US, "%.1f L", box.volume * 1000.0)
            val cuM = String.format(Locale.US, "%.3f m³", box.volume)
            Triple(viewModel.formatVolume(box.volume, selectedUnit), "$cuM · $liters", Icons.Rounded.ViewInAr)
        }
        "CIRCLE" -> {
            val circle = viewModel.calculateCircle()
            val diameter = circle?.diameter ?: 0.0
            val circum = circle?.circumference ?: 0.0
            Triple(
                "直徑 ${viewModel.formatLength(diameter, selectedUnit)}",
                "周長 ${viewModel.formatLength(circum, selectedUnit)}",
                Icons.Rounded.Adjust
            )
        }
        "ANGLE" -> {
            val deg = viewModel.calculateAngle()
            val rad = String.format(Locale.US, "%.3f rad", Math.toRadians(deg.toDouble()))
            Triple("空間夾角 ${Math.round(deg)}°", rad, Icons.Rounded.Architecture)
        }
        else -> {
            val dist = viewModel.calculateTotalDistance()
            val cm = String.format(Locale.US, "%.1f cm", dist * 100)
            val ft = String.format(Locale.US, "%.2f ft", dist * 3.28084)
            val inch = String.format(Locale.US, "%.1f in", dist * 39.3701)
            Triple(viewModel.formatLength(dist, selectedUnit), "$cm · $ft · $inch", Icons.Rounded.Straighten)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = ExpressiveMotion.spatialDefault())
            .testTag("measurement_live_summary_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = ExpressiveCookie8Shape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = primaryText,
                        style = ExpressiveTypography.titleNumeral,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = secondaryText,
                        style = ExpressiveTypography.labelNumeral,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // M3 Expressive Connected Quick Action Pair (Copy + Save/History)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val copyInteraction = remember { MutableInteractionSource() }
                val isCopyPressed by copyInteraction.collectIsPressedAsState()
                val copyShape = rememberExpressiveConnectedShape(
                    index = 0,
                    count = 2,
                    isActive = isCopied,
                    isPressed = isCopyPressed,
                    outerCorner = 20.dp,
                    innerRestingCorner = 6.dp,
                    innerActiveCorner = 16.dp
                )
                val copyBg by animateColorAsState(
                    targetValue = if (isCopied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    animationSpec = ExpressiveMotion.effectsFast(),
                    label = "copyBg"
                )

                Surface(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(primaryText))
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        isCopied = true
                    },
                    interactionSource = copyInteraction,
                    shape = copyShape,
                    color = copyBg,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "複製測量數值",
                            tint = if (isCopied) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val saveInteraction = remember { MutableInteractionSource() }
                val isSavePressed by saveInteraction.collectIsPressedAsState()
                val saveShape = rememberExpressiveConnectedShape(
                    index = 1,
                    count = 2,
                    isActive = isSaved,
                    isPressed = isSavePressed,
                    outerCorner = 20.dp,
                    innerRestingCorner = 6.dp,
                    innerActiveCorner = 16.dp
                )
                val saveBg by animateColorAsState(
                    targetValue = if (isSaved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primary,
                    animationSpec = ExpressiveMotion.effectsFast(),
                    label = "saveBg"
                )

                Surface(
                    onClick = {
                        if (!isSaved) {
                            viewModel.saveMeasurementRecord {
                                isSaved = true
                            }
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        } else {
                            onShowHistory()
                        }
                    },
                    interactionSource = saveInteraction,
                    shape = saveShape,
                    color = saveBg,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                            contentDescription = "儲存至紀錄",
                            tint = if (isSaved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
