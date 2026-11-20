package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.adaptive.LocalAdaptiveWindowState
import com.example.ui.adaptive.rememberLocalHingeTracker
import com.example.ui.theme.ExpressiveCookie8Shape
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.GoogleSansRounded
import com.example.ui.theme.rememberExpressiveConnectedShape
import com.example.ui.viewmodel.MeasureViewModel
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.*

/**
 * 旗艦美學數位水平儀 (Masterpiece Aesthetic Spirit Level)
 *
 * 視覺亮點：
 * 1. 瑞士鐘錶工藝級 3D 雙軸圓盤水準儀：航空級外圈金屬雷雕刻度、內凹玻璃腔體與透光折射。
 * 2. 逼真物理流體礦物油氣泡：自然的雙重高光反光、柔和液體投影與表面張力彎月面。
 * 3. 命中水平高光時刻 (Level Hit)：翡翠綠極光光暈、中心微脈衝動畫與清脆觸覺反饋。
 * 4. 雙軸 X/Y 微型水平滑軌 (Mini-level track pills)：直觀看出前後俯仰與左右傾斜方位。
 * 5. 極簡易用：保留最實用的「相對歸零」、「數值鎖定」與「水平音訊提示」。
 */

@Stable
private class SpiritLevelStateHolder {
    var drawPitch by mutableFloatStateOf(0f)
    var drawRoll by mutableFloatStateOf(0f)

    var displayPitch by mutableFloatStateOf(0f)
    var displayRoll by mutableFloatStateOf(0f)
    var displayDeviation by mutableFloatStateOf(0f)
    var isLevel by mutableStateOf(false)
    var hasSensorSample by mutableStateOf(false)
}

@Composable
fun SpiritLevelComponent(
    viewModel: MeasureViewModel,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isDark = com.example.ui.theme.LocalAppDarkTheme.current
    val adaptiveState = LocalAdaptiveWindowState.current
    val (hingeTrackerModifier, hingeMetrics) = rememberLocalHingeTracker(adaptiveState)

    // Tone Generator for acoustic cue
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        } catch (_: Throwable) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                toneGenerator?.release()
            } catch (_: Throwable) {}
        }
    }

    val stateHolder = remember { SpiritLevelStateHolder() }

    // User configurations & relative zeroing preserved across fold/unfold transitions
    var isHoldLocked by rememberSaveable { mutableStateOf(false) }
    val levelToleranceDeg by viewModel.levelToleranceDeg.collectAsStateWithLifecycle()
    val isAudioEnabled by viewModel.levelAudioEnabled.collectAsStateWithLifecycle()

    var zeroOffsetPitch by rememberSaveable { mutableFloatStateOf(0f) }
    var zeroOffsetRoll by rememberSaveable { mutableFloatStateOf(0f) }

    var lockedPitch by rememberSaveable { mutableFloatStateOf(0f) }
    var lockedRoll by rememberSaveable { mutableFloatStateOf(0f) }

    // Responsive low-pass filter for rock-solid, jitter-free physics
    val sensorFilter = remember {
        object {
            var filteredPitch = 0f
            var filteredRoll = 0f
            var hasFirstSample = false
        }
    }

    // Register hardware sensors
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val targetSensor = rotationVectorSensor ?: gravitySensor ?: accelSensor

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)
            private var lastTimestampNs = 0L

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || isHoldLocked) return

                var p = 0f
                var r = 0f
                var valid = false

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    p = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    r = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                    valid = true
                } else if (event.sensor.type == Sensor.TYPE_GRAVITY || event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    val g = sqrt(ax * ax + ay * ay + az * az)
                    if (g > 0.1f) {
                        p = Math.toDegrees(asin((-ay / g).coerceIn(-1f, 1f).toDouble())).toFloat()
                        r = Math.toDegrees(atan2(ax.toDouble(), az.toDouble())).toFloat()
                        valid = true
                    }
                }

                if (valid && !p.isNaN() && !r.isNaN()) {
                    val ts = event.timestamp
                    val sensorDt = if (lastTimestampNs > 0L && ts > lastTimestampNs) {
                        ((ts - lastTimestampNs) / 1_000_000_000f).coerceIn(0.002f, 0.05f)
                    } else {
                        0.02f
                    }
                    lastTimestampNs = ts

                    if (!sensorFilter.hasFirstSample) {
                        sensorFilter.filteredPitch = p
                        sensorFilter.filteredRoll = r
                        sensorFilter.hasFirstSample = true
                    } else {
                        val dp = p - sensorFilter.filteredPitch
                        val dr = r - sensorFilter.filteredRoll
                        // Tiny stationary deadband eliminates sub-0.03° MEMS thermal noise
                        if (abs(dp) < 0.03f && abs(dr) < 0.03f) {
                            return
                        }
                        // dt-normalized exponential smoothing (~12 rad/s cutoff, invariant across 100-400Hz IMUs)
                        val alpha = (1f - exp(-12f * sensorDt)).coerceIn(0.04f, 0.45f)
                        sensorFilter.filteredPitch += dp * alpha
                        sensorFilter.filteredRoll += dr * alpha
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensorManager != null && targetSensor != null) {
            sensorManager.registerListener(listener, targetSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // High performance V-Sync render loop with dt-aware smoothing and throttled UI readout updates
    LaunchedEffect(isHoldLocked, zeroOffsetPitch, zeroOffsetRoll, levelToleranceDeg) {
        var lastUiUpdateTimeMs = 0L
        var prevFrameNanos = 0L
        var animP = stateHolder.drawPitch
        var animR = stateHolder.drawRoll
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                val hasSample = isHoldLocked || sensorFilter.hasFirstSample
                val curP = if (isHoldLocked) lockedPitch else (sensorFilter.filteredPitch - zeroOffsetPitch)
                val curR = if (isHoldLocked) lockedRoll else (sensorFilter.filteredRoll - zeroOffsetRoll)

                val dt = if (prevFrameNanos == 0L) {
                    0.016f
                } else {
                    (frameTimeNanos - prevFrameNanos).coerceIn(1_000_000L, 66_000_000L) / 1_000_000_000f
                }
                prevFrameNanos = frameTimeNanos

                val smoothFactor = 1f - exp(-18f * dt)
                animP += (curP - animP) * smoothFactor
                animR += (curR - animR) * smoothFactor

                stateHolder.drawPitch = animP
                stateHolder.drawRoll = animR

                val dev = sqrt(curP * curP + curR * curR)
                val curLevel = hasSample && dev <= levelToleranceDeg

                val nowMs = frameTimeNanos / 1_000_000L
                val significantUiDelta = hasSample && (
                    abs(curP - stateHolder.displayPitch) >= 0.04f ||
                        abs(curR - stateHolder.displayRoll) >= 0.04f
                    )
                if ((nowMs - lastUiUpdateTimeMs > 40L && significantUiDelta) ||
                    curLevel != stateHolder.isLevel ||
                    hasSample != stateHolder.hasSensorSample
                ) {
                    lastUiUpdateTimeMs = nowMs
                    stateHolder.hasSensorSample = hasSample
                    stateHolder.displayPitch = curP
                    stateHolder.displayRoll = curR
                    stateHolder.displayDeviation = dev
                    stateHolder.isLevel = curLevel
                }
            }
        }
    }

    // Acoustic & haptic feedback on crossing level boundary
    var wasLevel by remember { mutableStateOf(false) }
    LaunchedEffect(stateHolder.isLevel) {
        if (stateHolder.isLevel && !wasLevel) {
            com.example.logic.ModernHapticFeedback.play(context, com.example.ui.viewmodel.HapticType.SNAP)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            if (isAudioEnabled) {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                } catch (_: Throwable) {}
            }
        }
        wasLevel = stateHolder.isLevel
    }

    val isRelativeZeroActive = zeroOffsetPitch != 0f || zeroOffsetRoll != 0f

    // Theme Colors
    val levelColor = Color(0xFF00E676) // Radiant Emerald Green for level state
    val accentColorState = animateColorAsState(
        targetValue = if (stateHolder.isLevel) levelColor else MaterialTheme.colorScheme.primary,
        animationSpec = ExpressiveMotion.effectsDefault(),
        label = "levelAccentColor"
    )

    val onToggleAudio: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.setLevelAudioEnabled(!isAudioEnabled)
    }

    val onToggleZero: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (isRelativeZeroActive) {
            zeroOffsetPitch = 0f
            zeroOffsetRoll = 0f
        } else {
            zeroOffsetPitch = stateHolder.displayPitch + zeroOffsetPitch
            zeroOffsetRoll = stateHolder.displayRoll + zeroOffsetRoll
        }
    }

    val onToggleHold: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!isHoldLocked) {
            lockedPitch = stateHolder.drawPitch
            lockedRoll = stateHolder.drawRoll
            isHoldLocked = true
        } else {
            isHoldLocked = false
        }
    }

    val rootBackgroundModifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
        .then(hingeTrackerModifier)
        .drawWithCache {
            val glowCenter = Offset(size.width / 2f, size.height * 0.42f)
            val glowRadius = (size.width * 0.75f).coerceAtLeast(1f)
            val levelGlowBrush = Brush.radialGradient(
                colors = listOf(levelColor.copy(alpha = 0.08f), Color.Transparent),
                center = glowCenter,
                radius = glowRadius
            )
            onDrawBehind {
                if (stateHolder.isLevel) {
                    drawCircle(brush = levelGlowBrush)
                } else {
                    drawCircle(
                        color = accentColorState.value.copy(alpha = 0.04f),
                        center = glowCenter,
                        radius = glowRadius * 0.55f
                    )
                }
            }
        }
        .padding(bottom = bottomPadding)
        .testTag("spirit_level_screen")

    val useSideBySideLayout = adaptiveState.isBook ||
        (adaptiveState.isExpandedWidth && !adaptiveState.isTabletop) ||
        (adaptiveState.isMediumOrExpandedWidth && adaptiveState.isCompactHeight && !adaptiveState.isTabletop)

    when {
        adaptiveState.isTabletop -> {
            // Tabletop Posture: Upper pane = unobstructed Precision Dial, Lower pane = Readout + Controls
            val topPaneHeightDp = hingeMetrics.topPaneHeightDp(density, fallbackFraction = 0.50f)
            val hingeGapDp = hingeMetrics.horizontalHingeGapDp(density)
            Column(modifier = rootBackgroundModifier) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topPaneHeightDp)
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .testTag("spirit_level_tabletop_top_pane"),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionMasterpieceBullseyeView(
                        pitchProvider = { stateHolder.drawPitch },
                        rollProvider = { stateHolder.drawRoll },
                        deviation = stateHolder.displayDeviation,
                        isLevel = stateHolder.isLevel,
                        accentColorProvider = { accentColorState.value },
                        levelColor = levelColor,
                        isDark = isDark
                    )
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hingeGapDp)
                        .testTag("spirit_level_hinge_gap")
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("spirit_level_tabletop_bottom_pane"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SpiritLevelHeaderBar(
                        isLevel = stateHolder.isLevel,
                        isRelativeZeroActive = isRelativeZeroActive,
                        isAudioEnabled = isAudioEnabled,
                        levelColor = levelColor,
                        accentColorState = accentColorState,
                        onToggleAudio = onToggleAudio,
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                    SpiritLevelReadoutCard(
                        stateHolder = stateHolder,
                        levelColor = levelColor,
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                    SpiritLevelActionControls(
                        isRelativeZeroActive = isRelativeZeroActive,
                        isHoldLocked = isHoldLocked,
                        onToggleZero = onToggleZero,
                        onToggleHold = onToggleHold,
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }
            }
        }

        useSideBySideLayout -> {
            // Book Posture or Expanded Tablet Landscape: Side-by-side Dial + Extended Controls
            val hasBookHinge = adaptiveState.isBook && hingeMetrics.containerWidthPx > 0
            Row(
                modifier = rootBackgroundModifier.testTag(
                    if (adaptiveState.isBook) "spirit_level_book_layout" else "spirit_level_tablet_layout"
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val leftPaneModifier = if (hasBookHinge) {
                    Modifier
                        .width(hingeMetrics.leftPaneWidthDp(density, fallbackFraction = 0.5f))
                        .fillMaxHeight()
                } else {
                    Modifier
                        .weight(1.08f)
                        .fillMaxHeight()
                }

                Box(
                    modifier = leftPaneModifier
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionMasterpieceBullseyeView(
                        pitchProvider = { stateHolder.drawPitch },
                        rollProvider = { stateHolder.drawRoll },
                        deviation = stateHolder.displayDeviation,
                        isLevel = stateHolder.isLevel,
                        accentColorProvider = { accentColorState.value },
                        levelColor = levelColor,
                        isDark = isDark
                    )
                }

                if (hasBookHinge) {
                    Spacer(
                        modifier = Modifier
                            .width(hingeMetrics.verticalHingeGapDp(density))
                            .fillMaxHeight()
                            .testTag("spirit_level_hinge_gap")
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SpiritLevelHeaderBar(
                        isLevel = stateHolder.isLevel,
                        isRelativeZeroActive = isRelativeZeroActive,
                        isAudioEnabled = isAudioEnabled,
                        levelColor = levelColor,
                        accentColorState = accentColorState,
                        onToggleAudio = onToggleAudio,
                        modifier = Modifier.widthIn(max = 560.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SpiritLevelReadoutCard(
                        stateHolder = stateHolder,
                        levelColor = levelColor,
                        modifier = Modifier.widthIn(max = 560.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SpiritLevelActionControls(
                        isRelativeZeroActive = isRelativeZeroActive,
                        isHoldLocked = isHoldLocked,
                        onToggleZero = onToggleZero,
                        onToggleHold = onToggleHold,
                        modifier = Modifier.widthIn(max = 560.dp)
                    )
                }
            }
        }

        else -> {
            // Compact Phone & Portrait Medium Layout
            Column(
                modifier = rootBackgroundModifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SpiritLevelHeaderBar(
                    isLevel = stateHolder.isLevel,
                    isRelativeZeroActive = isRelativeZeroActive,
                    isAudioEnabled = isAudioEnabled,
                    levelColor = levelColor,
                    accentColorState = accentColorState,
                    onToggleAudio = onToggleAudio
                )

                // Hero Precision Spirit Dial
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionMasterpieceBullseyeView(
                        pitchProvider = { stateHolder.drawPitch },
                        rollProvider = { stateHolder.drawRoll },
                        deviation = stateHolder.displayDeviation,
                        isLevel = stateHolder.isLevel,
                        accentColorProvider = { accentColorState.value },
                        levelColor = levelColor,
                        isDark = isDark
                    )
                }

                SpiritLevelReadoutCard(
                    stateHolder = stateHolder,
                    levelColor = levelColor
                )

                SpiritLevelActionControls(
                    isRelativeZeroActive = isRelativeZeroActive,
                    isHoldLocked = isHoldLocked,
                    onToggleZero = onToggleZero,
                    onToggleHold = onToggleHold
                )
            }
        }
    }
}

@Composable
private fun SpiritLevelHeaderBar(
    isLevel: Boolean,
    isRelativeZeroActive: Boolean,
    isAudioEnabled: Boolean,
    levelColor: Color,
    accentColorState: State<Color>,
    onToggleAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpiritLevelLogoBadge(
                isLevel = isLevel,
                accentColorState = accentColorState
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "數位水平儀",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isRelativeZeroActive) MaterialTheme.colorScheme.secondary else levelColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isRelativeZeroActive) "相對基準校準模式" else "雙軸重力絕對基準",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRelativeZeroActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            onClick = onToggleAudio,
            shape = CircleShape,
            color = if (isAudioEnabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                1.dp,
                if (isAudioEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("level_btn_sound")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isAudioEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    contentDescription = "提示音開關",
                    tint = if (isAudioEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SpiritLevelReadoutCard(
    stateHolder: SpiritLevelStateHolder,
    levelColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (stateHolder.isLevel) levelColor else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(8.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (stateHolder.isLevel) "已精確水平 (0.0°)" else "當前傾角偏差",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (stateHolder.isLevel) levelColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", stateHolder.displayDeviation),
                        style = ExpressiveTypography.displayNumeralHero,
                        color = if (stateHolder.isLevel) levelColor else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "°",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (stateHolder.isLevel) levelColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpiritLevelActionControls(
    isRelativeZeroActive: Boolean,
    isHoldLocked: Boolean,
    onToggleZero: () -> Unit,
    onToggleHold: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val zeroInteraction = remember { MutableInteractionSource() }
        val isZeroPressed by zeroInteraction.collectIsPressedAsState()
        val zeroShape = rememberExpressiveConnectedShape(
            index = 0,
            count = 2,
            isActive = isRelativeZeroActive,
            isPressed = isZeroPressed,
            outerCorner = 24.dp,
            innerRestingCorner = 8.dp,
            innerActiveCorner = 18.dp
        )
        val zeroScale by animateFloatAsState(
            targetValue = if (isZeroPressed) 0.97f else 1.0f,
            animationSpec = ExpressiveMotion.spatialFast(),
            label = "zeroScale"
        )

        val holdInteraction = remember { MutableInteractionSource() }
        val isHoldPressed by holdInteraction.collectIsPressedAsState()
        val holdShape = rememberExpressiveConnectedShape(
            index = 1,
            count = 2,
            isActive = isHoldLocked,
            isPressed = isHoldPressed,
            outerCorner = 24.dp,
            innerRestingCorner = 8.dp,
            innerActiveCorner = 18.dp
        )
        val holdScale by animateFloatAsState(
            targetValue = if (isHoldPressed) 0.97f else 1.0f,
            animationSpec = ExpressiveMotion.spatialFast(),
            label = "holdScale"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = onToggleZero,
                interactionSource = zeroInteraction,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .graphicsLayer {
                        scaleX = zeroScale
                        scaleY = zeroScale
                    }
                    .testTag("level_btn_zero"),
                shape = zeroShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRelativeZeroActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isRelativeZeroActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(
                    1.2.dp,
                    if (isRelativeZeroActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = if (isRelativeZeroActive) Icons.Rounded.RestartAlt else Icons.Rounded.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRelativeZeroActive) "重設絕對基準" else "相對歸零",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded
                )
            }

            Button(
                onClick = onToggleHold,
                interactionSource = holdInteraction,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .graphicsLayer {
                        scaleX = holdScale
                        scaleY = holdScale
                    }
                    .testTag("level_btn_hold"),
                shape = holdShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHoldLocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isHoldLocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = BorderStroke(
                    1.2.dp,
                    if (isHoldLocked) MaterialTheme.colorScheme.error else Color.Transparent
                )
            ) {
                Icon(
                    imageVector = if (isHoldLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHoldLocked) "解除鎖定" else "鎖定數值",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "平放手機測量平面傾角，立起邊緣對齊垂直角度",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SpiritLevelLogoBadge(
    isLevel: Boolean,
    accentColorState: State<Color>
) {
    val logoScaleState = animateFloatAsState(
        targetValue = if (isLevel) 1.08f else 1.0f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "levelLogoPop"
    )
    val badgeAccentColor = accentColorState.value
    Surface(
        shape = ExpressiveCookie8Shape,
        color = badgeAccentColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, badgeAccentColor.copy(alpha = 0.42f)),
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                val s = logoScaleState.value
                scaleX = s
                scaleY = s
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.SquareFoot,
                contentDescription = null,
                tint = badgeAccentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 瑞士鐘錶工藝級圓盤水準儀 (Precision Masterpiece Bullseye View)
 */
@Composable
private fun PrecisionMasterpieceBullseyeView(
    pitchProvider: () -> Float,
    rollProvider: () -> Float,
    deviation: Float,
    isLevel: Boolean,
    accentColorProvider: () -> Color,
    levelColor: Color,
    isDark: Boolean
) {
    // Breathing pulse scale for level target ring (read in draw phase to avoid recomposition & brush rebuilds)
    val levelAuraScaleState = if (isLevel) {
        val infiniteTransition = rememberInfiniteTransition(label = "levelPulse")
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "auraScale"
        )
    } else {
        null
    }

    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceContainerLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    val tickUnitVectors = remember {
        Array(24) { idx ->
            val rad = Math.toRadians((idx * 15).toDouble())
            floatArrayOf(cos(rad).toFloat(), sin(rad).toFloat())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadiusPx = (kotlin.math.min(size.width, size.height) / 2f * 0.90f).coerceAtLeast(1f)
                    val maxAngleDeg = 15f
                    val bubbleRadiusPx = (outerRadiusPx * 0.22f).coerceAtLeast(1f)
                    val maxBubbleTravel = (outerRadiusPx - bubbleRadiusPx - 4f).coerceAtLeast(0f)
                    val innerChamberRadius = (outerRadiusPx - 10.dp.toPx()).coerceAtLeast(1f)

                    val bezelBrush = Brush.radialGradient(
                        colors = if (isDark) listOf(
                            surfaceContainerHigh,
                            surfaceContainerLowest
                        ) else listOf(
                            Color(0xFFE2E8F0),
                            Color(0xFFCBD5E1)
                        ),
                        center = Offset(center.x - outerRadiusPx * 0.3f, center.y - outerRadiusPx * 0.3f),
                        radius = outerRadiusPx * 1.4f
                    )

                    val chamberBrush = Brush.radialGradient(
                        colors = if (isDark) listOf(
                            Color(0xFF0F172A),
                            Color(0xFF070B12)
                        ) else listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF1F5F9)
                        ),
                        center = center,
                        radius = innerChamberRadius
                    )

                    val auraBaseRadius = bubbleRadiusPx * 2.2f
                    val auraBrush = Brush.radialGradient(
                        colors = listOf(
                            levelColor.copy(alpha = 0.28f),
                            levelColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = auraBaseRadius
                    )

                    val bubbleLevelBrush = Brush.radialGradient(
                        colors = listOf(levelColor, Color(0xFF059669)),
                        center = Offset(-bubbleRadiusPx * 0.22f, -bubbleRadiusPx * 0.22f),
                        radius = bubbleRadiusPx * 1.15f
                    )
                    val bubbleTiltBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7)),
                        center = Offset(-bubbleRadiusPx * 0.22f, -bubbleRadiusPx * 0.22f),
                        radius = bubbleRadiusPx * 1.15f
                    )

                    val shadowColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.12f)
                    val shadowOffset = 4.dp.toPx()
                    val innerShadowColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.08f)
                    val innerShadowStroke = Stroke(width = 3.dp.toPx())
                    val ringStroke = Stroke(width = 1.dp.toPx())
                    val ring10Px = (10f / maxAngleDeg) * maxBubbleTravel
                    val ring5Px = (5f / maxAngleDeg) * maxBubbleTravel
                    val targetRingPx = bubbleRadiusPx
                    val tickRadiusOuter = outerRadiusPx - 2.dp.toPx()
                    val tickRadiusInnerMajor = outerRadiusPx - 8.dp.toPx()
                    val tickRadiusInnerMinor = outerRadiusPx - 5.dp.toPx()
                    val tickStrokeCardinal = 2.2.dp.toPx()
                    val tickStrokeMajor = 1.5.dp.toPx()
                    val tickStrokeMinor = 0.8.dp.toPx()
                    val tickMajorColor = outlineVariant.copy(alpha = 0.75f)
                    val tickMinorColor = outlineVariant.copy(alpha = 0.35f)
                    val guideRing10Color = outlineVariant.copy(alpha = 0.28f)
                    val guideRing5Color = outlineVariant.copy(alpha = 0.38f)
                    val crosshairLen = innerChamberRadius * 0.96f
                    val crosshairStrokeW = 1.dp.toPx()
                    val meniscusStroke = Stroke(width = 2.dp.toPx())
                    val targetRingStrokeLevel = Stroke(width = 2.5.dp.toPx())
                    val targetRingStrokeTilt = Stroke(width = 1.5.dp.toPx())
                    val bubbleShadowColor = Color.Black.copy(alpha = 0.28f)
                    val specularPrimaryColor = Color.White.copy(alpha = 0.85f)
                    val specularSecondaryColor = Color.White.copy(alpha = 0.35f)
                    val meniscusTiltColor = Color.White.copy(alpha = 0.92f)

                    onDrawBehind {
                        val curAccentColor = accentColorProvider()
                        val levelAuraScale = levelAuraScaleState?.value ?: 1.0f
                        val curPitch = pitchProvider()
                        val curRoll = rollProvider()

                        // Map pitch (Y offset) and roll (X offset) to pixel coords
                        val bubbleOffsetX = (curRoll / maxAngleDeg).coerceIn(-1f, 1f) * maxBubbleTravel
                        val bubbleOffsetY = (-curPitch / maxAngleDeg).coerceIn(-1f, 1f) * maxBubbleTravel

                        // 1. Outer Bezel & Brushed Metallic Bevel
                        drawCircle(
                            color = shadowColor,
                            center = Offset(center.x, center.y + shadowOffset),
                            radius = outerRadiusPx + shadowOffset
                        )
                        drawCircle(
                            brush = bezelBrush,
                            center = center,
                            radius = outerRadiusPx
                        )

                        // 2. Liquid Spirit Vial Chamber
                        drawCircle(
                            brush = chamberBrush,
                            center = center,
                            radius = innerChamberRadius
                        )
                        drawCircle(
                            color = innerShadowColor,
                            center = center,
                            radius = innerChamberRadius,
                            style = innerShadowStroke
                        )

                        // 3. Laser-Etched Compass & Angle Tick Marks
                        for (idx in 0 until 24) {
                            val deg = idx * 15
                            val cosA = tickUnitVectors[idx][0]
                            val sinA = tickUnitVectors[idx][1]

                            val isMajor = deg % 30 == 0
                            val isCardinal = deg % 90 == 0
                            val tickInnerR = if (isMajor) tickRadiusInnerMajor else tickRadiusInnerMinor
                            val tickStroke = if (isCardinal) tickStrokeCardinal else if (isMajor) tickStrokeMajor else tickStrokeMinor
                            val tickColor = if (isCardinal) {
                                if (isLevel) levelColor else curAccentColor
                            } else if (isMajor) {
                                tickMajorColor
                            } else {
                                tickMinorColor
                            }

                            drawLine(
                                color = tickColor,
                                start = Offset(center.x + tickRadiusOuter * cosA, center.y + tickRadiusOuter * sinA),
                                end = Offset(center.x + tickInnerR * cosA, center.y + tickInnerR * sinA),
                                strokeWidth = tickStroke,
                                cap = StrokeCap.Round
                            )
                        }

                        // 4. Concentric Guide Rings & Level Aura
                        if (isLevel) {
                            scale(levelAuraScale, levelAuraScale, pivot = center) {
                                drawCircle(
                                    brush = auraBrush,
                                    center = center,
                                    radius = auraBaseRadius
                                )
                            }
                        }

                        drawCircle(
                            color = guideRing10Color,
                            center = center,
                            radius = ring10Px,
                            style = ringStroke
                        )
                        drawCircle(
                            color = guideRing5Color,
                            center = center,
                            radius = ring5Px,
                            style = ringStroke
                        )

                        // Inner Target Ring
                        if (isLevel) {
                            drawCircle(
                                color = levelColor.copy(alpha = 0.16f),
                                center = center,
                                radius = targetRingPx
                            )
                        }
                        drawCircle(
                            color = if (isLevel) levelColor else curAccentColor.copy(alpha = 0.65f),
                            center = center,
                            radius = targetRingPx * (if (isLevel) levelAuraScale else 1f),
                            style = if (isLevel) targetRingStrokeLevel else targetRingStrokeTilt
                        )

                        // 5. High-Precision Hairline Crosshairs
                        val crosshairColor = if (isLevel) levelColor.copy(alpha = 0.65f) else outlineVariant.copy(alpha = 0.45f)
                        drawLine(
                            color = crosshairColor,
                            start = Offset(center.x - crosshairLen, center.y),
                            end = Offset(center.x - targetRingPx * 0.35f, center.y),
                            strokeWidth = crosshairStrokeW
                        )
                        drawLine(
                            color = crosshairColor,
                            start = Offset(center.x + targetRingPx * 0.35f, center.y),
                            end = Offset(center.x + crosshairLen, center.y),
                            strokeWidth = crosshairStrokeW
                        )
                        drawLine(
                            color = crosshairColor,
                            start = Offset(center.x, center.y - crosshairLen),
                            end = Offset(center.x, center.y - targetRingPx * 0.35f),
                            strokeWidth = crosshairStrokeW
                        )
                        drawLine(
                            color = crosshairColor,
                            start = Offset(center.x, center.y + targetRingPx * 0.35f),
                            end = Offset(center.x, center.y + crosshairLen),
                            strokeWidth = crosshairStrokeW
                        )

                        // 6. Fluid Mineral Bubble with 3D Depth
                        val bubbleCenter = Offset(center.x + bubbleOffsetX, center.y + bubbleOffsetY)

                        drawCircle(
                            color = bubbleShadowColor,
                            center = Offset(bubbleCenter.x + 2.5f, bubbleCenter.y + 4.5f),
                            radius = bubbleRadiusPx
                        )

                        translate(left = bubbleCenter.x, top = bubbleCenter.y) {
                            drawCircle(
                                brush = if (isLevel) bubbleLevelBrush else bubbleTiltBrush,
                                center = Offset.Zero,
                                radius = bubbleRadiusPx
                            )
                        }

                        drawCircle(
                            color = specularPrimaryColor,
                            center = Offset(bubbleCenter.x - bubbleRadiusPx * 0.35f, bubbleCenter.y - bubbleRadiusPx * 0.35f),
                            radius = bubbleRadiusPx * 0.28f
                        )
                        drawCircle(
                            color = specularSecondaryColor,
                            center = Offset(bubbleCenter.x + bubbleRadiusPx * 0.32f, bubbleCenter.y + bubbleRadiusPx * 0.32f),
                            radius = bubbleRadiusPx * 0.16f
                        )
                        drawCircle(
                            color = if (isLevel) Color.White else meniscusTiltColor,
                            center = bubbleCenter,
                            radius = bubbleRadiusPx,
                            style = meniscusStroke
                        )
                    }
                }
        )

        // Center HUD Overlay: Large Degree Badge & Level Status Indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 0.dp)
        ) {
            if (isLevel) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = levelColor.copy(alpha = 0.22f),
                    border = BorderStroke(1.2.dp, levelColor.copy(alpha = 0.85f)),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = levelColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "0.0° 水平",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = levelColor,
                            fontFamily = GoogleSansRounded
                        )
                    }
                }
            } else if (deviation > 3.0f) {
                // Subtle floating degree capsule when tilted away from center
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, outlineVariant.copy(alpha = 0.5f)),
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f°", deviation),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        fontFamily = GoogleSansRounded,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
