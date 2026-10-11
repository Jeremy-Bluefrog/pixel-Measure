package com.example.ui.components

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
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import com.example.ui.theme.GoogleSansRounded
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
 * 5. 極簡易用：保留最實用的「相對歸零」、「數值鎖定」與「紀錄儲存」。
 */
private const val LEVEL_TOLERANCE_DEG = 0.5f

@Stable
private class SpiritLevelStateHolder {
    var drawPitch by mutableFloatStateOf(0f)
    var drawRoll by mutableFloatStateOf(0f)

    var displayPitch by mutableFloatStateOf(0f)
    var displayRoll by mutableFloatStateOf(0f)
    var displayDeviation by mutableFloatStateOf(0f)
    var isLevel by mutableStateOf(false)
}

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

    val stateHolder = remember { SpiritLevelStateHolder() }

    // User configurations
    var isHoldLocked by remember { mutableStateOf(false) }
    var isAudioEnabled by remember { mutableStateOf(true) }

    // Calibration offsets for relative zeroing (相對歸零)
    var zeroOffsetPitch by remember { mutableFloatStateOf(0f) }
    var zeroOffsetRoll by remember { mutableFloatStateOf(0f) }

    var lockedPitch by remember { mutableFloatStateOf(0f) }
    var lockedRoll by remember { mutableFloatStateOf(0f) }

    // Save record dialog state
    var showSaveDialog by remember { mutableStateOf(false) }
    var recordNotesInput by remember { mutableStateOf("") }

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
                        // Smooth physical dampening (80% prev + 20% new)
                        sensorFilter.filteredPitch = sensorFilter.filteredPitch * 0.80f + p * 0.20f
                        sensorFilter.filteredRoll = sensorFilter.filteredRoll * 0.80f + r * 0.20f
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

    // High performance V-Sync render loop
    LaunchedEffect(isHoldLocked, zeroOffsetPitch, zeroOffsetRoll) {
        var lastUiUpdateTimeMs = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                val curP = if (isHoldLocked) lockedPitch else (sensorFilter.filteredPitch - zeroOffsetPitch)
                val curR = if (isHoldLocked) lockedRoll else (sensorFilter.filteredRoll - zeroOffsetRoll)

                stateHolder.drawPitch = curP
                stateHolder.drawRoll = curR

                val dev = sqrt(curP * curP + curR * curR)
                val curLevel = dev <= LEVEL_TOLERANCE_DEG

                val nowMs = frameTimeNanos / 1_000_000L
                if (nowMs - lastUiUpdateTimeMs > 40L || curLevel != stateHolder.isLevel) {
                    lastUiUpdateTimeMs = nowMs
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(32, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(32)
                }
            } catch (_: Throwable) {}
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
    val accentColor by animateColorAsState(
        targetValue = if (stateHolder.isLevel) levelColor else MaterialTheme.colorScheme.primary,
        animationSpec = tween(220),
        label = "levelAccentColor"
    )

    // Soft Ambient Glow Background Brush
    val ambientGlowColor = if (stateHolder.isLevel) levelColor.copy(alpha = 0.08f) else accentColor.copy(alpha = 0.04f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                // Subtle radial light bloom centered behind the dial
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ambientGlowColor, Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.width * 0.75f
                    )
                )
            }
            .padding(bottom = bottomPadding)
            .testTag("spirit_level_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Refined Minimalist Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SquareFoot,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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

            // Quick Audio & Save Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Sound Toggle
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isAudioEnabled = !isAudioEnabled
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isAudioEnabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (isAudioEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            CircleShape
                        )
                        .testTag("level_btn_sound")
                ) {
                    Icon(
                        imageVector = if (isAudioEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        contentDescription = "提示音開關",
                        tint = if (isAudioEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Quick Save Record Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        recordNotesInput = ""
                        showSaveDialog = true
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .shadow(3.dp, CircleShape)
                        .testTag("level_btn_save")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = "儲存紀錄",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. The Hero Precision Spirit Dial (航空鐘錶級雙軸儀表盤)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            PrecisionMasterpieceBullseyeView(
                pitch = stateHolder.drawPitch,
                roll = stateHolder.drawRoll,
                deviation = stateHolder.displayDeviation,
                isLevel = stateHolder.isLevel,
                accentColor = accentColor,
                levelColor = levelColor,
                isDark = isDark
            )
        }

        // 3. Precision Readout Deck with Mini-Axis Level Tracks
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Deviation Status & Large Typography
                Column {
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
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = GoogleSansRounded,
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

                // Right: Dual-Axis Micro Track Gauges
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniAxisTrackPill(
                        axisName = "X 俯仰",
                        angle = stateHolder.displayPitch,
                        isLevel = abs(stateHolder.displayPitch) <= LEVEL_TOLERANCE_DEG,
                        levelColor = levelColor
                    )
                    MiniAxisTrackPill(
                        axisName = "Y 橫滾",
                        angle = stateHolder.displayRoll,
                        isLevel = abs(stateHolder.displayRoll) <= LEVEL_TOLERANCE_DEG,
                        levelColor = levelColor
                    )
                }
            }
        }

        // 4. Primary Tactile Bottom Controls: Zero (Calibrate) & Hold (Freeze)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Relative Zero Calibration Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isRelativeZeroActive) {
                        zeroOffsetPitch = 0f
                        zeroOffsetRoll = 0f
                    } else {
                        zeroOffsetPitch = stateHolder.displayPitch + zeroOffsetPitch
                        zeroOffsetRoll = stateHolder.displayRoll + zeroOffsetRoll
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("level_btn_zero"),
                shape = RoundedCornerShape(16.dp),
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

            // Hold / Freeze Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!isHoldLocked) {
                        lockedPitch = stateHolder.drawPitch
                        lockedRoll = stateHolder.drawRoll
                        isHoldLocked = true
                    } else {
                        isHoldLocked = false
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("level_btn_hold"),
                shape = RoundedCornerShape(16.dp),
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

        // 5. Minimal Friendly Placement Tip
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

    // Save Measurement Record Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.BookmarkAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "儲存水平測量紀錄",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "傾斜偏差：${String.format(Locale.US, "%.1f°", stateHolder.displayDeviation)} (${if (stateHolder.isLevel) "水平精確 ✓" else "傾斜"})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (stateHolder.isLevel) levelColor else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "X 俯仰: ${String.format(Locale.US, "%+.1f°", stateHolder.displayPitch)} | Y 橫滾: ${String.format(Locale.US, "%+.1f°", stateHolder.displayRoll)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = recordNotesInput,
                        onValueChange = { recordNotesInput = it },
                        label = { Text("備註 (可選，如：客廳電視櫃、相框吊掛)") },
                        placeholder = { Text("輸入測量位置或說明...") },
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
                            pitchDeg = stateHolder.displayPitch,
                            rollDeg = stateHolder.displayRoll,
                            modeName = "數位水平儀",
                            toleranceDeg = LEVEL_TOLERANCE_DEG,
                            customNotes = recordNotesInput.ifBlank { null }
                        )
                        showSaveDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("確認儲存")
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
 * Mini-Axis Level Track Pill with Visual Sliding Bubble (迷你雙軸水平微滑軌)
 */
@Composable
private fun MiniAxisTrackPill(
    axisName: String,
    angle: Float,
    isLevel: Boolean,
    levelColor: Color
) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isLevel) levelColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (isLevel) levelColor.copy(alpha = 0.55f) else outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.width(96.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = axisName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format(Locale.US, "%+.1f°", angle),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isLevel) levelColor else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Micro sliding indicator track
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isLevel) levelColor.copy(alpha = 0.20f) else outlineVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                // Center zero notch
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(6.dp)
                        .background(if (isLevel) levelColor else outlineVariant)
                )

                // Micro sliding dot (-10° to +10° range)
                val dotFraction = (angle / 10f).coerceIn(-1f, 1f)
                val xOffsetDp = (dotFraction * 26f).dp

                Box(
                    modifier = Modifier
                        .offset(x = xOffsetDp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isLevel) levelColor else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/**
 * 瑞士鐘錶工藝級圓盤水準儀 (Precision Masterpiece Bullseye View)
 */
@Composable
private fun PrecisionMasterpieceBullseyeView(
    pitch: Float,
    roll: Float,
    deviation: Float,
    isLevel: Boolean,
    accentColor: Color,
    levelColor: Color,
    isDark: Boolean
) {
    // Liquid spring physics
    val animatedPitch by animateFloatAsState(
        targetValue = pitch,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 360f),
        label = "animPitch"
    )

    val animatedRoll by animateFloatAsState(
        targetValue = roll,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 360f),
        label = "animRoll"
    )

    // Breathing pulse scale for level target ring
    val infiniteTransition = rememberInfiniteTransition(label = "levelPulse")
    val levelAuraScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isLevel) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceContainerLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadiusPx = kotlin.math.min(size.width, size.height) / 2f * 0.90f

            // Max visible range = 15°
            val maxAngleDeg = 15f
            val bubbleRadiusPx = outerRadiusPx * 0.22f
            val maxBubbleTravel = outerRadiusPx - bubbleRadiusPx - 4f

            // Map pitch (Y offset) and roll (X offset) to pixel coords
            val bubbleOffsetX = (animatedRoll / maxAngleDeg).coerceIn(-1f, 1f) * maxBubbleTravel
            val bubbleOffsetY = (-animatedPitch / maxAngleDeg).coerceIn(-1f, 1f) * maxBubbleTravel

            // -------------------------------------------------------------
            // 1. Outer Bezel & Brushed Metallic Bevel (外圈金屬倒角與陰影)
            // -------------------------------------------------------------
            // Bezel Outer Cast Shadow
            drawCircle(
                color = Color.Black.copy(alpha = if (isDark) 0.45f else 0.12f),
                center = Offset(center.x, center.y + 4.dp.toPx()),
                radius = outerRadiusPx + 4.dp.toPx()
            )

            // Metallic Bezel Outer Rim
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isDark) listOf(
                        surfaceContainerHigh,
                        surfaceContainerLowest
                    ) else listOf(
                        Color(0xFFE2E8F0),
                        Color(0xFFCBD5E1)
                    ),
                    center = Offset(center.x - outerRadiusPx * 0.3f, center.y - outerRadiusPx * 0.3f),
                    radius = outerRadiusPx * 1.4f
                ),
                center = center,
                radius = outerRadiusPx
            )

            // -------------------------------------------------------------
            // 2. Liquid Spirit Vial Chamber (內凹高透光液體腔體)
            // -------------------------------------------------------------
            val innerChamberRadius = outerRadiusPx - 10.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isDark) listOf(
                        Color(0xFF0F172A),
                        Color(0xFF070B12)
                    ) else listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF1F5F9)
                    ),
                    center = center,
                    radius = innerChamberRadius
                ),
                center = center,
                radius = innerChamberRadius
            )

            // Inner Shadow Inset Edge
            drawCircle(
                color = Color.Black.copy(alpha = if (isDark) 0.35f else 0.08f),
                center = center,
                radius = innerChamberRadius,
                style = Stroke(width = 3.dp.toPx())
            )

            // -------------------------------------------------------------
            // 3. Laser-Etched Compass & Angle Tick Marks (360° 雷射刻度)
            // -------------------------------------------------------------
            val tickRadiusOuter = outerRadiusPx - 2.dp.toPx()
            val tickRadiusInnerMajor = outerRadiusPx - 8.dp.toPx()
            val tickRadiusInnerMinor = outerRadiusPx - 5.dp.toPx()

            for (deg in 0 until 360 step 15) {
                val rad = Math.toRadians(deg.toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()

                val isMajor = deg % 30 == 0
                val isCardinal = deg % 90 == 0
                val tickInnerR = if (isMajor) tickRadiusInnerMajor else tickRadiusInnerMinor
                val tickStroke = if (isCardinal) 2.2.dp.toPx() else if (isMajor) 1.5.dp.toPx() else 0.8.dp.toPx()
                val tickColor = if (isCardinal) {
                    if (isLevel) levelColor else accentColor
                } else if (isMajor) {
                    outlineVariant.copy(alpha = 0.75f)
                } else {
                    outlineVariant.copy(alpha = 0.35f)
                }

                drawLine(
                    color = tickColor,
                    start = Offset(center.x + tickRadiusOuter * cosA, center.y + tickRadiusOuter * sinA),
                    end = Offset(center.x + tickInnerR * cosA, center.y + tickInnerR * sinA),
                    strokeWidth = tickStroke,
                    cap = StrokeCap.Round
                )
            }

            // -------------------------------------------------------------
            // 4. Concentric Guide Rings & Level Aura (同心引導圓環與光暈)
            // -------------------------------------------------------------
            // Level State Radiant Bloom
            if (isLevel) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            levelColor.copy(alpha = 0.28f),
                            levelColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = bubbleRadiusPx * 2.2f * levelAuraScale
                    ),
                    center = center,
                    radius = bubbleRadiusPx * 2.2f * levelAuraScale
                )
            }

            // 10° Guide Ring
            val ring10Px = (10f / maxAngleDeg) * maxBubbleTravel
            drawCircle(
                color = outlineVariant.copy(alpha = 0.28f),
                center = center,
                radius = ring10Px,
                style = Stroke(width = 1.dp.toPx())
            )

            // 5° Guide Ring
            val ring5Px = (5f / maxAngleDeg) * maxBubbleTravel
            drawCircle(
                color = outlineVariant.copy(alpha = 0.38f),
                center = center,
                radius = ring5Px,
                style = Stroke(width = 1.dp.toPx())
            )

            // Inner Target Ring (0.5° Precision Bullseye)
            val targetRingPx = bubbleRadiusPx
            drawCircle(
                color = if (isLevel) levelColor.copy(alpha = 0.16f) else Color.Transparent,
                center = center,
                radius = targetRingPx
            )
            drawCircle(
                color = if (isLevel) levelColor else accentColor.copy(alpha = 0.65f),
                center = center,
                radius = targetRingPx * (if (isLevel) levelAuraScale else 1f),
                style = Stroke(width = if (isLevel) 2.5.dp.toPx() else 1.5.dp.toPx())
            )

            // -------------------------------------------------------------
            // 5. High-Precision Hairline Crosshairs (髮絲十字準心線)
            // -------------------------------------------------------------
            val crosshairLen = innerChamberRadius * 0.96f
            val crosshairColor = if (isLevel) levelColor.copy(alpha = 0.65f) else outlineVariant.copy(alpha = 0.45f)
            val strokeW = 1.dp.toPx()

            // Horizontal Line (broken at central bullseye)
            drawLine(
                color = crosshairColor,
                start = Offset(center.x - crosshairLen, center.y),
                end = Offset(center.x - targetRingPx * 0.35f, center.y),
                strokeWidth = strokeW
            )
            drawLine(
                color = crosshairColor,
                start = Offset(center.x + targetRingPx * 0.35f, center.y),
                end = Offset(center.x + crosshairLen, center.y),
                strokeWidth = strokeW
            )

            // Vertical Line (broken at central bullseye)
            drawLine(
                color = crosshairColor,
                start = Offset(center.x, center.y - crosshairLen),
                end = Offset(center.x, center.y - targetRingPx * 0.35f),
                strokeWidth = strokeW
            )
            drawLine(
                color = crosshairColor,
                start = Offset(center.x, center.y + targetRingPx * 0.35f),
                end = Offset(center.x, center.y + crosshairLen),
                strokeWidth = strokeW
            )

            // -------------------------------------------------------------
            // 6. Fluid Mineral Bubble with 3D Depth (逼真物理流體氣泡)
            // -------------------------------------------------------------
            val bubbleCenter = Offset(center.x + bubbleOffsetX, center.y + bubbleOffsetY)

            // Bubble Ambient Fluid Drop Shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                center = Offset(bubbleCenter.x + 2.5f, bubbleCenter.y + 4.5f),
                radius = bubbleRadiusPx
            )

            // Bubble Fluid Core Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isLevel) listOf(
                        levelColor,
                        Color(0xFF059669)
                    ) else listOf(
                        Color(0xFF38BDF8),
                        Color(0xFF0284C7)
                    ),
                    center = Offset(bubbleCenter.x - bubbleRadiusPx * 0.22f, bubbleCenter.y - bubbleRadiusPx * 0.22f),
                    radius = bubbleRadiusPx * 1.15f
                ),
                center = bubbleCenter,
                radius = bubbleRadiusPx
            )

            // Primary Specular Reflection Highlight (Top-left glass reflection)
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                center = Offset(bubbleCenter.x - bubbleRadiusPx * 0.35f, bubbleCenter.y - bubbleRadiusPx * 0.35f),
                radius = bubbleRadiusPx * 0.28f
            )

            // Secondary Diffuse Rim Bounce Light (Bottom-right refraction)
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                center = Offset(bubbleCenter.x + bubbleRadiusPx * 0.32f, bubbleCenter.y + bubbleRadiusPx * 0.32f),
                radius = bubbleRadiusPx * 0.16f
            )

            // Outer Meniscus Rim Stroke
            drawCircle(
                color = if (isLevel) Color.White else Color.White.copy(alpha = 0.92f),
                center = bubbleCenter,
                radius = bubbleRadiusPx,
                style = Stroke(width = 2.dp.toPx())
            )
        }

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
                    shadowElevation = 6.dp
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
                    shadowElevation = 4.dp
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
