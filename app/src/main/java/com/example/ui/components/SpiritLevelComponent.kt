package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MeasureViewModel
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.*

/**
 * 水準儀測量模式 (Level Measurement Modes)
 */
enum class LevelType(val label: String) {
    SURFACE_2D("雙軸圓盤"),       // 雙軸圓盤水準儀 (Surface Bullseye)
    HORIZONTAL_1D("橫向水平"),    // 橫向管狀水準儀 (Horizontal Tube)
    VERTICAL_1D("立面垂直")       // 垂直鉛垂水準儀 (Vertical Plumb)
}

/**
 * 角度顯示單位 (Angle Units)
 */
enum class AngleUnit(val label: String, val symbol: String) {
    DEGREE("角度", "°"),
    PERCENT("坡度", "%"),
    ROOF_PITCH("斜率", "mm/m")
}

/**
 * 測量容差精度設定 (Measurement Tolerances)
 */
enum class LevelTolerance(val thresholdDeg: Float, val label: String, val description: String) {
    ULTRA_FINE(0.2f, "超高精 (±0.2°)", "精密機械 / 實驗室校準"),
    STANDARD(0.5f, "標準 (±0.5°)", "家具安裝 / 木工吊掛"),
    CONSTRUCTION(1.0f, "工程 (±1.0°)", "泥作建築 / 戶外坡度")
}

/**
 * High-performance state holder for the Spirit Level.
 * Decouples continuous 60/120 FPS hardware-accelerated Canvas drawing from UI text layout passes.
 */
@Stable
class SpiritLevelRenderState {
    // Continuous values consumed ONLY by Canvas draw scopes (Draw phase only)
    var drawPitch by mutableFloatStateOf(0f)
    var drawRoll by mutableFloatStateOf(0f)

    // Throttled/rounded values for Compose text readouts (prevents 120Hz text relayout)
    var displayPitch by mutableFloatStateOf(0f)
    var displayRoll by mutableFloatStateOf(0f)
    var displayDeviation by mutableFloatStateOf(0f)
    var isLevel by mutableStateOf(false)
}

/**
 * 旗艦級 Material 3 數位水準儀組件 (Material 3 Spirit Level Component)
 *
 * 核心升級亮點：
 * 1. 深度遵循 Material Design 3 設計語彙：Tonal Color Scheme, SingleChoiceSegmentedButtonRow,
 *    ElevatedCard, FilterChip, AssistChip, 柔和狀態微動效與色彩轉場。
 * 2. 雙重視覺適應性：完美支援 Material You 動態色彩、深色與淺色模式儀表刻度盤渲染。
 * 3. 業界級三段精度容差設定 (超高精 ±0.2° / 標準 ±0.5° / 工程 ±1.0°)，即時動態連動靶心與管狀容差線。
 * 4. 三維物理感氣泡渲染：多層次徑向光暈、液體折射鏡面、高光反光點與邊界彎月面。
 * 5. 全向調平引導指示：偏離時顯示微動態水平引導標誌，輔助快速歸平。
 * 6. 整合相對基準校準 (Relative Zero)、讀數鎖定 (Hold Freeze) 與一鍵測量紀錄保存 (Save to Room Database)。
 * 7. 60/120Hz V-Sync 硬體加速 Canvas 繪製，無重組抖動，低 CPU 與耗電量。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiritLevelComponent(
    viewModel: MeasureViewModel,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    // Tone Generator for acoustic cue
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
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

    // Vibrator Service for tactile cue
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // High performance render state
    val renderState = remember { SpiritLevelRenderState() }

    // User Configurations & States
    var levelType by remember { mutableStateOf(LevelType.SURFACE_2D) }
    var angleUnit by remember { mutableStateOf(AngleUnit.DEGREE) }
    var tolerance by remember { mutableStateOf(LevelTolerance.STANDARD) }
    var isHoldLocked by remember { mutableStateOf(false) }
    var isAudioEnabled by remember { mutableStateOf(true) }
    var isHapticEnabled by remember { mutableStateOf(true) }

    // Calibration offsets for relative zeroing (相對基準歸零)
    var zeroOffsetPitch by remember { mutableFloatStateOf(0f) }
    var zeroOffsetRoll by remember { mutableFloatStateOf(0f) }

    // Frozen values when locked
    var lockedPitch by remember { mutableFloatStateOf(0f) }
    var lockedRoll by remember { mutableFloatStateOf(0f) }

    // Save record dialog state
    var showSaveDialog by remember { mutableStateOf(false) }
    var recordNotesInput by remember { mutableStateOf("") }

    // Primitive sensor smoothing accumulator (no Compose state overhead on raw sensor events)
    val sensorFilter = remember {
        object {
            var filteredPitch = 0f
            var filteredRoll = 0f
            var hasFirstSample = false
        }
    }

    // Register high-frequency hardware sensor listener (ROTATION_VECTOR -> GRAVITY -> ACCELEROMETER)
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val targetSensor = rotationVectorSensor ?: gravitySensor ?: accelSensor

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)

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

                if (valid) {
                    if (!sensorFilter.hasFirstSample) {
                        sensorFilter.filteredPitch = p
                        sensorFilter.filteredRoll = r
                        sensorFilter.hasFirstSample = true
                    } else {
                        // Responsive physical low-pass filter (fast response, zero stutter)
                        sensorFilter.filteredPitch = sensorFilter.filteredPitch * 0.82f + p * 0.18f
                        sensorFilter.filteredRoll = sensorFilter.filteredRoll * 0.82f + r * 0.18f
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

    // 60/120 FPS display loop driven by V-Sync withFrameNanos
    LaunchedEffect(isHoldLocked, zeroOffsetPitch, zeroOffsetRoll, levelType, tolerance) {
        var lastUiUpdateTimeMs = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                val curP = if (isHoldLocked) lockedPitch else (sensorFilter.filteredPitch - zeroOffsetPitch)
                val curR = if (isHoldLocked) lockedRoll else (sensorFilter.filteredRoll - zeroOffsetRoll)

                renderState.drawPitch = curP
                renderState.drawRoll = curR

                val dev = when (levelType) {
                    LevelType.SURFACE_2D -> sqrt(curP * curP + curR * curR)
                    LevelType.HORIZONTAL_1D -> abs(curR)
                    LevelType.VERTICAL_1D -> abs(90f - abs(curP))
                }
                val curLevel = dev <= tolerance.thresholdDeg

                val nowMs = frameTimeNanos / 1_000_000L
                if (nowMs - lastUiUpdateTimeMs > 45L || curLevel != renderState.isLevel) {
                    lastUiUpdateTimeMs = nowMs
                    renderState.displayPitch = curP
                    renderState.displayRoll = curR
                    renderState.displayDeviation = dev
                    renderState.isLevel = curLevel
                }
            }
        }
    }

    // Acoustic & Haptic Feedback on crossing level boundary
    var wasLevel by remember { mutableStateOf(false) }
    LaunchedEffect(renderState.isLevel) {
        if (renderState.isLevel && !wasLevel) {
            if (isHapticEnabled) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(35)
                    }
                } catch (_: Throwable) {}
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (isAudioEnabled) {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
                } catch (_: Throwable) {}
            }
        }
        wasLevel = renderState.isLevel
    }

    // Material 3 Color Theme Mapping
    val levelAccentColor by animateColorAsState(
        targetValue = if (renderState.isLevel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 200),
        label = "levelAccentColor"
    )

    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHighColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    val isRelativeZeroActive = zeroOffsetPitch != 0f || zeroOffsetRoll != 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = bottomPadding)
            .testTag("spirit_level_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Material 3 Standard Segmented Button Row (Mode Selection)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            color = Color.Transparent
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                LevelType.values().forEachIndexed { index, type ->
                    val isSelected = levelType == type
                    val icon = when (type) {
                        LevelType.SURFACE_2D -> Icons.Rounded.FilterTiltShift
                        LevelType.HORIZONTAL_1D -> Icons.Rounded.LinearScale
                        LevelType.VERTICAL_1D -> Icons.Rounded.Height
                    }

                    SegmentedButton(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            levelType = type
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = LevelType.values().size),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = isSelected) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                )
                            }
                        },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("level_mode_${type.name.lowercase(Locale.ROOT)}")
                    ) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Tolerance & Unit Secondary Chips Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tolerance Selector Chip
            AssistChip(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    tolerance = when (tolerance) {
                        LevelTolerance.ULTRA_FINE -> LevelTolerance.STANDARD
                        LevelTolerance.STANDARD -> LevelTolerance.CONSTRUCTION
                        LevelTolerance.CONSTRUCTION -> LevelTolerance.ULTRA_FINE
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                label = {
                    Text(
                        text = tolerance.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = surfaceContainerHighColor,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.5f))
            )

            // Relative Zero Status Badge (if active)
            if (isRelativeZeroActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "相對基準模式",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Unit Toggle Chip
            AssistChip(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    angleUnit = when (angleUnit) {
                        AngleUnit.DEGREE -> AngleUnit.PERCENT
                        AngleUnit.PERCENT -> AngleUnit.ROOF_PITCH
                        AngleUnit.ROOF_PITCH -> AngleUnit.DEGREE
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                label = {
                    Text(
                        text = "單位: ${angleUnit.label} (${angleUnit.symbol})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = surfaceContainerHighColor,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.5f))
            )
        }

        // 3. Main Instrument Visualizer Display Area (Canvas only, isolated from recompositions)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            when (levelType) {
                LevelType.SURFACE_2D -> {
                    Material3BullseyeLevelView(
                        pitchProvider = { renderState.drawPitch },
                        rollProvider = { renderState.drawRoll },
                        isLevel = renderState.isLevel,
                        accentColor = levelAccentColor,
                        toleranceDeg = tolerance.thresholdDeg,
                        isDark = isDark
                    )
                }
                LevelType.HORIZONTAL_1D -> {
                    Material3TubularHorizontalLevelView(
                        angleProvider = { renderState.drawRoll },
                        isLevel = renderState.isLevel,
                        accentColor = levelAccentColor,
                        toleranceDeg = tolerance.thresholdDeg,
                        isDark = isDark
                    )
                }
                LevelType.VERTICAL_1D -> {
                    Material3TubularVerticalLevelView(
                        angleProvider = { 90f - abs(renderState.drawPitch) },
                        isLevel = renderState.isLevel,
                        accentColor = levelAccentColor,
                        toleranceDeg = tolerance.thresholdDeg,
                        isDark = isDark
                    )
                }
            }
        }

        // 4. Material 3 Precision Readout Card & Dynamic Angle Breakdown
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = surfaceContainerColor
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Top status and large deviation readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (renderState.isLevel) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (renderState.isLevel) "✓ 基準精確水平 (±${tolerance.thresholdDeg}°)" else "當前傾角偏差",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (renderState.isLevel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatAngleValue(renderState.displayDeviation, angleUnit),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (renderState.isLevel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace
                            )
                            if (isHoldLocked) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                ) {
                                    Text(
                                        text = "HOLD 保持中",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Save Record Button
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            recordNotesInput = ""
                            showSaveDialog = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = "儲存紀錄",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "記錄",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = outlineVariantColor.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(8.dp))

                // Detailed Pitch (X) and Roll (Y) Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Material3AxisReadoutItem(
                        axisName = "X (俯仰 Pitch)",
                        angle = renderState.displayPitch,
                        isTarget = abs(renderState.displayPitch) <= tolerance.thresholdDeg,
                        targetColor = MaterialTheme.colorScheme.tertiary,
                        unit = angleUnit
                    )
                    Material3AxisReadoutItem(
                        axisName = "Y (橫滾 Roll)",
                        angle = renderState.displayRoll,
                        isTarget = abs(renderState.displayRoll) <= tolerance.thresholdDeg,
                        targetColor = MaterialTheme.colorScheme.tertiary,
                        unit = angleUnit
                    )
                    val offsetMagnitude = sqrt(zeroOffsetPitch * zeroOffsetPitch + zeroOffsetRoll * zeroOffsetRoll)
                    Material3AxisReadoutItem(
                        axisName = "相對歸零偏移",
                        angle = offsetMagnitude,
                        isTarget = zeroOffsetPitch == 0f && zeroOffsetRoll == 0f,
                        targetColor = MaterialTheme.colorScheme.secondary,
                        unit = AngleUnit.DEGREE
                    )
                }
            }
        }

        // 5. Material 3 Bottom Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Relative Zero Calibration Button (相對基準歸零)
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isRelativeZeroActive) {
                        zeroOffsetPitch = 0f
                        zeroOffsetRoll = 0f
                    } else {
                        zeroOffsetPitch = renderState.displayPitch + zeroOffsetPitch
                        zeroOffsetRoll = renderState.displayRoll + zeroOffsetRoll
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("level_btn_zero"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRelativeZeroActive) MaterialTheme.colorScheme.secondaryContainer else surfaceContainerHighColor,
                    contentColor = if (isRelativeZeroActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = if (isRelativeZeroActive) Icons.Rounded.RestartAlt else Icons.Rounded.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRelativeZeroActive) "重設絕對基準" else "相對歸零",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Hold / Freeze Button (數值鎖定)
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!isHoldLocked) {
                        lockedPitch = renderState.drawPitch
                        lockedRoll = renderState.drawRoll
                        isHoldLocked = true
                    } else {
                        isHoldLocked = false
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("level_btn_hold"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHoldLocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isHoldLocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = if (isHoldLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHoldLocked) "解除鎖定" else "鎖定數值",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Audio Alert Toggle
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isAudioEnabled = !isAudioEnabled
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (isAudioEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else surfaceContainerHighColor,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Icon(
                    imageVector = if (isAudioEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = "水平提示聲",
                    tint = if (isAudioEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Haptic Alert Toggle
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isHapticEnabled = !isHapticEnabled
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (isHapticEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else surfaceContainerHighColor,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Icon(
                    imageVector = if (isHapticEnabled) Icons.Rounded.Vibration else Icons.Rounded.Smartphone,
                    contentDescription = "震動吸附",
                    tint = if (isHapticEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 6. Contextual Placement Advice Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            color = Color.Transparent
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when (levelType) {
                        LevelType.SURFACE_2D -> "提示：請將手機平放於待測物表面進行雙軸校平"
                        LevelType.HORIZONTAL_1D -> "提示：將手機長邊貼齊測量物體邊緣，調整至氣泡居中"
                        LevelType.VERTICAL_1D -> "提示：將手機側邊垂直貼靠牆面或立柱進行垂準測量"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }

    // Material 3 Save Measurement Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.FilterTiltShift,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "儲存水準儀測量紀錄",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "當前測量模式：${levelType.label}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "傾斜偏差：${String.format(Locale.US, "%.1f°", renderState.displayDeviation)} (${if (renderState.isLevel) "水平精確 ✓" else "未校平"})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (renderState.isLevel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "俯仰 (X): ${String.format(Locale.US, "%+.1f°", renderState.displayPitch)} | 橫滾 (Y): ${String.format(Locale.US, "%+.1f°", renderState.displayRoll)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = recordNotesInput,
                        onValueChange = { recordNotesInput = it },
                        label = { Text("備註 (可選，如：客廳電視牆水平校正)") },
                        placeholder = { Text("輸入測量位置或用途...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.saveLevelRecord(
                            pitchDeg = renderState.displayPitch,
                            rollDeg = renderState.displayRoll,
                            modeName = levelType.label,
                            toleranceDeg = tolerance.thresholdDeg,
                            customNotes = recordNotesInput.ifBlank { null }
                        )
                        showSaveDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("儲存至歷史紀錄")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 格式化角度、坡度與斜率文字
 */
private fun formatAngleValue(deviationDeg: Float, unit: AngleUnit): String {
    return when (unit) {
        AngleUnit.DEGREE -> String.format(Locale.US, "%.1f°", deviationDeg)
        AngleUnit.PERCENT -> {
            val pct = abs(tan(Math.toRadians(deviationDeg.toDouble()))) * 100.0
            String.format(Locale.US, "%.2f%%", pct)
        }
        AngleUnit.ROOF_PITCH -> {
            val mmPerM = abs(tan(Math.toRadians(deviationDeg.toDouble()))) * 1000.0
            String.format(Locale.US, "%.1f mm/m", mmPerM)
        }
    }
}

/**
 * Material 3 雙軸圓盤水準儀繪製畫布 (Bullseye / Surface Level)
 * Pure Hardware-Accelerated Draw Canvas: 0 recompositions, 60/120 FPS fluid physics.
 */
@Composable
private fun Material3BullseyeLevelView(
    pitchProvider: () -> Float,
    rollProvider: () -> Float,
    isLevel: Boolean,
    accentColor: Color,
    toleranceDeg: Float,
    isDark: Boolean
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .testTag("bullseye_level_canvas")
    ) {
        val pitch = pitchProvider()
        val roll = rollProvider()
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = minOf(size.width, size.height) * 0.44f

        // Tolerance target radius dynamically mapped to tolerance threshold (10° = 0.75 * outerRadius)
        val toleranceRatio = (toleranceDeg / 10f).coerceIn(0.12f, 0.40f)
        val innerTargetRadius = outerRadius * toleranceRatio

        // 1. Dial Metallic Bezel / Ring Base (Theme-aware)
        val dialBaseGradient = if (isDark) {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1E293B),
                    Color(0xFF0F172A),
                    Color(0xFF060911)
                ),
                center = center,
                radius = outerRadius
            )
        } else {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF8FAFC),
                    Color(0xFFE2E8F0),
                    Color(0xFFCBD5E1)
                ),
                center = center,
                radius = outerRadius
            )
        }

        drawCircle(
            brush = dialBaseGradient,
            center = center,
            radius = outerRadius
        )

        // Outer Metallic Rim Stroke
        val rimColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color(0xFF64748B).copy(alpha = 0.35f)
        drawCircle(
            color = rimColor,
            center = center,
            radius = outerRadius,
            style = Stroke(width = 3.dp.toPx())
        )

        // Subtle Outer Shadow / Inner Bevel
        drawCircle(
            color = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.6f),
            center = center,
            radius = outerRadius - 1.5.dp.toPx(),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 2. Concentric Angle Degree Rings (10°, 5°, 2°, and Target Zone)
        val ringColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFF475569).copy(alpha = 0.22f)
        val ringRatios = floatArrayOf(0.85f, 0.58f, 0.32f)
        for (ratio in ringRatios) {
            drawCircle(
                color = ringColor,
                center = center,
                radius = outerRadius * ratio,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // Active Tolerance Target Ring
        drawCircle(
            color = if (isLevel) accentColor.copy(alpha = 0.9f) else accentColor.copy(alpha = 0.5f),
            center = center,
            radius = innerTargetRadius,
            style = Stroke(width = if (isLevel) 2.5.dp.toPx() else 1.5.dp.toPx())
        )

        // 3. Precision Crosshair coordinate lines
        val crosshairColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color(0xFF334155).copy(alpha = 0.25f)
        drawLine(
            color = crosshairColor,
            start = Offset(center.x - outerRadius, center.y),
            end = Offset(center.x + outerRadius, center.y),
            strokeWidth = 1.2.dp.toPx()
        )
        drawLine(
            color = crosshairColor,
            start = Offset(center.x, center.y - outerRadius),
            end = Offset(center.x, center.y + outerRadius),
            strokeWidth = 1.2.dp.toPx()
        )

        // Radial degree tick marks every 15 degrees (precalculated PI / 180 = 0.0174532925f)
        for (deg in 0 until 360 step 15) {
            val rad = deg * 0.0174532925f
            val cosV = cos(rad)
            val sinV = sin(rad)
            val isMajor = deg % 45 == 0
            val tickLen = if (isMajor) 14.dp.toPx() else 7.dp.toPx()
            val rStart = outerRadius - tickLen
            val tickColor = if (isMajor) {
                if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF1E293B).copy(alpha = 0.6f)
            } else {
                if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF64748B).copy(alpha = 0.3f)
            }
            drawLine(
                color = tickColor,
                start = Offset(center.x + cosV * rStart, center.y + sinV * rStart),
                end = Offset(center.x + cosV * outerRadius, center.y + sinV * outerRadius),
                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
            )
        }

        // 4. Center Target Level Glow Disk when device is aligned
        if (isLevel) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.35f),
                        accentColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = innerTargetRadius * 1.4f
                ),
                center = center,
                radius = innerTargetRadius * 1.4f
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.22f),
                center = center,
                radius = innerTargetRadius
            )
        }

        // 5. Dynamic Bubble Physics Simulation
        // Bubble displacement scales with tilt angle: 10° corresponds to 75% of outer ring
        val maxAngle = 10f
        val normX = (roll / maxAngle).coerceIn(-1.15f, 1.15f)
        val normY = (pitch / maxAngle).coerceIn(-1.15f, 1.15f)
        val bubbleOffset = Offset(
            x = center.x + normX * (outerRadius * 0.75f),
            y = center.y + normY * (outerRadius * 0.75f)
        )
        val bubbleRadius = innerTargetRadius * 0.76f

        // Real-time direction guide chevron when off-level
        if (!isLevel) {
            val devX = center.x - bubbleOffset.x
            val devY = center.y - bubbleOffset.y
            val dist = sqrt(devX * devX + devY * devY)
            if (dist > bubbleRadius * 1.2f) {
                val dirX = devX / dist
                val dirY = devY / dist
                val arrowStart = Offset(center.x - dirX * (innerTargetRadius * 1.2f), center.y - dirY * (innerTargetRadius * 1.2f))
                val arrowEnd = Offset(center.x - dirX * (innerTargetRadius * 0.7f), center.y - dirY * (innerTargetRadius * 0.7f))
                drawLine(
                    color = accentColor.copy(alpha = 0.65f),
                    start = arrowStart,
                    end = arrowEnd,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Bubble Outer Glow & Liquid Aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.75f),
                    accentColor.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = bubbleOffset,
                radius = bubbleRadius * 1.4f
            ),
            center = bubbleOffset,
            radius = bubbleRadius * 1.4f
        )

        // 3D Spherical Fluid Body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    accentColor.copy(alpha = 0.90f),
                    accentColor.copy(alpha = 0.70f)
                ),
                center = Offset(bubbleOffset.x - bubbleRadius * 0.28f, bubbleOffset.y - bubbleRadius * 0.28f),
                radius = bubbleRadius
            ),
            center = bubbleOffset,
            radius = bubbleRadius
        )

        // Specular highlight spot for high realism
        drawCircle(
            color = Color.White.copy(alpha = 0.90f),
            center = Offset(bubbleOffset.x - bubbleRadius * 0.32f, bubbleOffset.y - bubbleRadius * 0.32f),
            radius = bubbleRadius * 0.25f
        )

        // Bubble crisp rim meniscus
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            center = bubbleOffset,
            radius = bubbleRadius,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Material 3 橫向管狀水準儀 (Horizontal Tubular Level)
 */
@Composable
private fun Material3TubularHorizontalLevelView(
    angleProvider: () -> Float,
    isLevel: Boolean,
    accentColor: Color,
    toleranceDeg: Float,
    isDark: Boolean
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .testTag("tubular_horizontal_level_canvas")
    ) {
        val angle = angleProvider()
        val tubeW = size.width * 0.92f
        val tubeH = 68.dp.toPx()
        val tubeLeft = (size.width - tubeW) / 2f
        val tubeTop = (size.height - tubeH) / 2f
        val tubeRadius = tubeH / 2f

        // Tube vial glass background
        val vialGradient = if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1E293B),
                    Color(0xFF0F172A),
                    Color(0xFF060911)
                ),
                startY = tubeTop,
                endY = tubeTop + tubeH
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFF1F5F9),
                    Color(0xFFE2E8F0),
                    Color(0xFFCBD5E1)
                ),
                startY = tubeTop,
                endY = tubeTop + tubeH
            )
        }

        drawRoundRect(
            brush = vialGradient,
            topLeft = Offset(tubeLeft, tubeTop),
            size = Size(tubeW, tubeH),
            cornerRadius = CornerRadius(tubeRadius, tubeRadius)
        )

        // Tube outer border
        val borderColor = if (isDark) Color.White.copy(alpha = 0.30f) else Color(0xFF64748B).copy(alpha = 0.45f)
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(tubeLeft, tubeTop),
            size = Size(tubeW, tubeH),
            cornerRadius = CornerRadius(tubeRadius, tubeRadius),
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Glass reflection sheen stripe
        drawRoundRect(
            color = Color.White.copy(alpha = if (isDark) 0.12f else 0.45f),
            topLeft = Offset(tubeLeft + 12.dp.toPx(), tubeTop + 6.dp.toPx()),
            size = Size(tubeW - 24.dp.toPx(), 8.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // Center level indicator target zone
        val centerX = size.width / 2f
        val targetZoneWidth = (toleranceDeg / 0.5f * 44.dp.toPx()).coerceIn(24.dp.toPx(), 72.dp.toPx())

        if (isLevel) {
            drawRoundRect(
                color = accentColor.copy(alpha = 0.22f),
                topLeft = Offset(centerX - targetZoneWidth / 2f, tubeTop),
                size = Size(targetZoneWidth, tubeH),
                cornerRadius = CornerRadius(12f, 12f)
            )
        }

        // Target alignment vertical boundary lines
        val lineCol = if (isLevel) accentColor else accentColor.copy(alpha = 0.7f)
        drawLine(
            color = lineCol,
            start = Offset(centerX - targetZoneWidth / 2f, tubeTop),
            end = Offset(centerX - targetZoneWidth / 2f, tubeTop + tubeH),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = lineCol,
            start = Offset(centerX + targetZoneWidth / 2f, tubeTop),
            end = Offset(centerX + targetZoneWidth / 2f, tubeTop + tubeH),
            strokeWidth = 2.dp.toPx()
        )

        // Center centerline tick
        drawLine(
            color = if (isDark) Color.White.copy(alpha = 0.45f) else Color(0xFF1E293B).copy(alpha = 0.5f),
            start = Offset(centerX, tubeTop + 6.dp.toPx()),
            end = Offset(centerX, tubeTop + tubeH - 6.dp.toPx()),
            strokeWidth = 1.2.dp.toPx()
        )

        // Degree scale ticks
        for (i in -4..4) {
            if (i == 0) continue
            val tickX = centerX + i * 22.dp.toPx()
            if (tickX > tubeLeft + tubeRadius && tickX < tubeLeft + tubeW - tubeRadius) {
                val tickH = if (abs(i) % 2 == 0) 14.dp.toPx() else 8.dp.toPx()
                drawLine(
                    color = if (isDark) Color.White.copy(alpha = 0.25f) else Color(0xFF64748B).copy(alpha = 0.35f),
                    start = Offset(tickX, tubeTop + 6.dp.toPx()),
                    end = Offset(tickX, tubeTop + 6.dp.toPx() + tickH),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // Bubble physics displacement
        val maxTravel = (tubeW - tubeH) / 2f
        val norm = (angle / 8f).coerceIn(-1f, 1f)
        val bubbleX = centerX + norm * maxTravel
        val bubbleY = tubeTop + tubeRadius
        val bubbleRadius = tubeRadius * 0.72f

        // Bubble glow & body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    accentColor,
                    accentColor.copy(alpha = 0.65f)
                ),
                center = Offset(bubbleX - bubbleRadius * 0.22f, bubbleY - bubbleRadius * 0.22f),
                radius = bubbleRadius
            ),
            center = Offset(bubbleX, bubbleY),
            radius = bubbleRadius
        )

        // Bubble specular highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            center = Offset(bubbleX - bubbleRadius * 0.28f, bubbleY - bubbleRadius * 0.28f),
            radius = bubbleRadius * 0.25f
        )

        // Bubble edge rim
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            center = Offset(bubbleX, bubbleY),
            radius = bubbleRadius,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Material 3 垂直垂準儀 (Vertical Plumb Level)
 */
@Composable
private fun Material3TubularVerticalLevelView(
    angleProvider: () -> Float,
    isLevel: Boolean,
    accentColor: Color,
    toleranceDeg: Float,
    isDark: Boolean
) {
    Canvas(
        modifier = Modifier
            .fillMaxHeight()
            .width(130.dp)
            .testTag("tubular_vertical_level_canvas")
    ) {
        val angle = angleProvider()
        val tubeW = 68.dp.toPx()
        val tubeH = size.height * 0.88f
        val tubeLeft = (size.width - tubeW) / 2f
        val tubeTop = (size.height - tubeH) / 2f
        val tubeRadius = tubeW / 2f

        // Tube vial glass background
        val vialGradient = if (isDark) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF1E293B),
                    Color(0xFF0F172A),
                    Color(0xFF060911)
                ),
                startX = tubeLeft,
                endX = tubeLeft + tubeW
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFF1F5F9),
                    Color(0xFFE2E8F0),
                    Color(0xFFCBD5E1)
                ),
                startX = tubeLeft,
                endX = tubeLeft + tubeW
            )
        }

        drawRoundRect(
            brush = vialGradient,
            topLeft = Offset(tubeLeft, tubeTop),
            size = Size(tubeW, tubeH),
            cornerRadius = CornerRadius(tubeRadius, tubeRadius)
        )

        // Tube outer border
        val borderColor = if (isDark) Color.White.copy(alpha = 0.30f) else Color(0xFF64748B).copy(alpha = 0.45f)
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(tubeLeft, tubeTop),
            size = Size(tubeW, tubeH),
            cornerRadius = CornerRadius(tubeRadius, tubeRadius),
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Glass reflection sheen stripe
        drawRoundRect(
            color = Color.White.copy(alpha = if (isDark) 0.12f else 0.45f),
            topLeft = Offset(tubeLeft + 6.dp.toPx(), tubeTop + 12.dp.toPx()),
            size = Size(8.dp.toPx(), tubeH - 24.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // Center level indicator target zone
        val centerY = size.height / 2f
        val targetZoneHeight = (toleranceDeg / 0.5f * 44.dp.toPx()).coerceIn(24.dp.toPx(), 72.dp.toPx())

        if (isLevel) {
            drawRoundRect(
                color = accentColor.copy(alpha = 0.22f),
                topLeft = Offset(tubeLeft, centerY - targetZoneHeight / 2f),
                size = Size(tubeW, targetZoneHeight),
                cornerRadius = CornerRadius(12f, 12f)
            )
        }

        // Target alignment horizontal lines
        val lineCol = if (isLevel) accentColor else accentColor.copy(alpha = 0.7f)
        drawLine(
            color = lineCol,
            start = Offset(tubeLeft, centerY - targetZoneHeight / 2f),
            end = Offset(tubeLeft + tubeW, centerY - targetZoneHeight / 2f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = lineCol,
            start = Offset(tubeLeft, centerY + targetZoneHeight / 2f),
            end = Offset(tubeLeft + tubeW, centerY + targetZoneHeight / 2f),
            strokeWidth = 2.dp.toPx()
        )

        // Center centerline tick
        drawLine(
            color = if (isDark) Color.White.copy(alpha = 0.45f) else Color(0xFF1E293B).copy(alpha = 0.5f),
            start = Offset(tubeLeft + 6.dp.toPx(), centerY),
            end = Offset(tubeLeft + tubeW - 6.dp.toPx(), centerY),
            strokeWidth = 1.2.dp.toPx()
        )

        // Degree scale ticks
        for (i in -4..4) {
            if (i == 0) continue
            val tickY = centerY + i * 22.dp.toPx()
            if (tickY > tubeTop + tubeRadius && tickY < tubeTop + tubeH - tubeRadius) {
                val tickW = if (abs(i) % 2 == 0) 14.dp.toPx() else 8.dp.toPx()
                drawLine(
                    color = if (isDark) Color.White.copy(alpha = 0.25f) else Color(0xFF64748B).copy(alpha = 0.35f),
                    start = Offset(tubeLeft + 6.dp.toPx(), tickY),
                    end = Offset(tubeLeft + 6.dp.toPx() + tickW, tickY),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // Bubble physics displacement
        val maxTravel = (tubeH - tubeW) / 2f
        val norm = (angle / 8f).coerceIn(-1f, 1f)
        val bubbleX = tubeLeft + tubeRadius
        val bubbleY = centerY - norm * maxTravel
        val bubbleRadius = tubeRadius * 0.72f

        // Bubble glow & body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    accentColor,
                    accentColor.copy(alpha = 0.65f)
                ),
                center = Offset(bubbleX - bubbleRadius * 0.22f, bubbleY - bubbleRadius * 0.22f),
                radius = bubbleRadius
            ),
            center = Offset(bubbleX, bubbleY),
            radius = bubbleRadius
        )

        // Specular highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            center = Offset(bubbleX - bubbleRadius * 0.28f, bubbleY - bubbleRadius * 0.28f),
            radius = bubbleRadius * 0.25f
        )

        // Bubble rim
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            center = Offset(bubbleX, bubbleY),
            radius = bubbleRadius,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Detailed readout item for X and Y axes
 */
@Composable
private fun Material3AxisReadoutItem(
    axisName: String,
    angle: Float,
    isTarget: Boolean,
    targetColor: Color,
    unit: AngleUnit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = axisName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        val formattedAngle = when (unit) {
            AngleUnit.DEGREE -> String.format(Locale.US, "%+.1f°", angle)
            AngleUnit.PERCENT -> {
                val pct = tan(Math.toRadians(angle.toDouble())) * 100.0
                String.format(Locale.US, "%+.2f%%", pct)
            }
            AngleUnit.ROOF_PITCH -> {
                val mmPerM = tan(Math.toRadians(angle.toDouble())) * 1000.0
                String.format(Locale.US, "%+.1f mm/m", mmPerM)
            }
        }
        Text(
            text = formattedAngle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isTarget) targetColor else MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )
    }
}
