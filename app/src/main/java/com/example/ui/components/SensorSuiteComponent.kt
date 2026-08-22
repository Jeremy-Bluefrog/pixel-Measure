package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.ShareUtility
import com.example.logic.sensor.DeviceSensorInfo
import com.example.logic.sensor.SensorSuiteState
import com.example.ui.viewmodel.MeasureViewModel
import java.text.DecimalFormat
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorSuiteComponent(
    viewModel: MeasureViewModel,
    onShowHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sensorState by viewModel.sensorState.collectAsState()
    val installedSensors by viewModel.installedSensors.collectAsState()
    val vibrateOnAlign by viewModel.vibrateOnAlignment.collectAsState()

    // Sub-tool index:
    // 0: 水平儀 (Spirit Level)
    // 1: 電子羅盤 (Compass)
    // 2: 氣壓高度 (Barometer & Altimeter)
    // 3: 照度計 (Lux Light)
    // 4: 加速度/重力 (G-Force & Accel)
    // 5: 感應器清單 (Hardware Sensors)
    var activeSensorTool by remember { mutableIntStateOf(0) }

    // Level alignment haptic trigger
    var wasLevel by remember { mutableStateOf(false) }
    LaunchedEffect(sensorState.isLevel) {
        if (sensorState.isLevel && !wasLevel && vibrateOnAlign) {
            viewModel.triggerHapticFeedback()
        }
        wasLevel = sensorState.isLevel
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sensor Suite Sub-Tool Tab Row
        ScrollableTabRow(
            selectedTabIndex = activeSensorTool,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
        ) {
            val tabs = listOf(
                "🎯 電子水平儀",
                "🧭 數位羅盤",
                "⛰️ 氣壓高度計",
                "💡 環境照度計",
                "📊 重力與加速度",
                "📱 硬體感應器"
            )
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = activeSensorTool == index,
                    onClick = { activeSensorTool = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (activeSensorTool == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Active Tool View Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            AnimatedContent(
                targetState = activeSensorTool,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "SensorToolTransition"
            ) { tool ->
                when (tool) {
                    0 -> SpiritLevelView(
                        state = sensorState,
                        onCalibrate = { viewModel.calibrateSensorLevel() },
                        onResetCalibration = { viewModel.resetSensorLevelCalibration() },
                        onSaveSnapshot = {
                            viewModel.saveSensorRecord(
                                title = "水平儀角度 (Pitch ${DecimalFormat("0.0").format(sensorState.pitchDegrees)}°, Roll ${DecimalFormat("0.0").format(sensorState.rollDegrees)}°)",
                                value = sensorState.pitchDegrees.toDouble(),
                                unit = "°",
                                type = "LEVEL"
                            )
                        }
                    )
                    1 -> CompassView(
                        state = sensorState,
                        onSaveSnapshot = {
                            viewModel.saveSensorRecord(
                                title = "羅盤方位角 (${sensorState.cardinalDirection} ${DecimalFormat("0.0").format(sensorState.azimuthDegrees)}°)",
                                value = sensorState.azimuthDegrees.toDouble(),
                                unit = "°",
                                type = "COMPASS"
                            )
                        }
                    )
                    2 -> BarometerAltimeterView(
                        state = sensorState,
                        onSaveSnapshot = {
                            viewModel.saveSensorRecord(
                                title = "氣壓海拔 (${DecimalFormat("#,##0.0").format(sensorState.pressureHpa)} hPa · ${DecimalFormat("#,##0").format(sensorState.altitudeMeters)} m)",
                                value = sensorState.pressureHpa.toDouble(),
                                unit = "hPa",
                                type = "BAROMETER"
                            )
                        }
                    )
                    3 -> LightMeterView(
                        state = sensorState,
                        onSaveSnapshot = {
                            viewModel.saveSensorRecord(
                                title = "環境照度 (${DecimalFormat("#,##0").format(sensorState.lightLux)} Lux · ${sensorState.lightCondition})",
                                value = sensorState.lightLux.toDouble(),
                                unit = "Lux",
                                type = "LIGHT"
                            )
                        }
                    )
                    4 -> GForceAccelView(
                        state = sensorState,
                        onSaveSnapshot = {
                            viewModel.saveSensorRecord(
                                title = "重力加速度 (${DecimalFormat("0.00").format(sensorState.totalGForce)} G)",
                                value = sensorState.totalGForce.toDouble(),
                                unit = "G",
                                type = "ACCEL"
                            )
                        }
                    )
                    else -> InstalledSensorsListView(
                        sensors = installedSensors,
                        onShare = {
                            val text = buildString {
                                appendLine("=== 裝置硬體感應器清單 ===")
                                appendLine("總計 ${installedSensors.size} 個感應器模組")
                                appendLine()
                                installedSensors.forEachIndexed { i, s ->
                                    appendLine("${i + 1}. ${s.name} (${s.typeName})")
                                    appendLine("   製造商: ${s.vendor}")
                                    appendLine("   最大量程: ${s.maximumRange} | 解析度: ${s.resolution}")
                                }
                            }
                            ShareUtility.shareText(context, text, "分享裝置感應器規格")
                        }
                    )
                }
            }
        }
    }
}

/**
 * 1. Spirit Level / Inclinometer View
 */
@Composable
fun SpiritLevelView(
    state: SensorSuiteState,
    onCalibrate: () -> Unit,
    onResetCalibration: () -> Unit,
    onSaveSnapshot: () -> Unit
) {
    val df = remember { DecimalFormat("0.0") }
    val isLevel = state.isLevel
    val levelColor = if (isLevel) Color(0xFF34A853) else MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Badge
        Surface(
            color = if (isLevel) Color(0xFF34A853).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (isLevel) Color(0xFF34A853) else MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (isLevel) Icons.Rounded.CheckCircle else Icons.Rounded.Explore,
                    contentDescription = null,
                    tint = if (isLevel) Color(0xFF34A853) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isLevel) "水平已校準對齊 (0.0°)" else "請調整手機至水平中心",
                    fontWeight = FontWeight.Bold,
                    color = if (isLevel) Color(0xFF34A853) else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Circular Spirit Bubble Canvas
        Card(
            modifier = Modifier
                .size(260.dp)
                .shadow(8.dp, CircleShape),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f

                    // Outer ring
                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        radius = radius,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Middle 5-degree ring
                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        radius = radius * 0.6f,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Target center zero ring
                    drawCircle(
                        color = if (isLevel) Color(0xFF34A853) else Color.Gray.copy(alpha = 0.5f),
                        radius = radius * 0.22f,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Crosshair guidelines
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.35f),
                        start = Offset(center.x - radius, center.y),
                        end = Offset(center.x + radius, center.y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.35f),
                        start = Offset(center.x, center.y - radius),
                        end = Offset(center.x, center.y + radius),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Bubble position mapped from roll (X) and pitch (Y)
                    // Clamp to maximum 15 degrees visual deflection
                    val maxAngle = 15.0f
                    val clampedRoll = state.rollDegrees.coerceIn(-maxAngle, maxAngle)
                    val clampedPitch = state.pitchDegrees.coerceIn(-maxAngle, maxAngle)

                    val bubbleX = center.x + (clampedRoll / maxAngle) * (radius * 0.78f)
                    val bubbleY = center.y + (clampedPitch / maxAngle) * (radius * 0.78f)

                    // Draw moving liquid bubble
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = if (isLevel) {
                                listOf(Color(0xFF81C784), Color(0xFF34A853))
                            } else {
                                listOf(Color(0xFF80D8FF), Color(0xFF0091EA))
                            },
                            center = Offset(bubbleX, bubbleY),
                            radius = 24.dp.toPx()
                        ),
                        radius = 22.dp.toPx(),
                        center = Offset(bubbleX, bubbleY)
                    )
                }

                // Center Angle Readout inside bubble dial
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val totalTilt = sqrt((state.pitchDegrees * state.pitchDegrees + state.rollDegrees * state.rollDegrees).toDouble()).toFloat()
                    Text(
                        text = "${df.format(totalTilt)}°",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = levelColor
                    )
                }
            }
        }

        // Real-time Angle Cards: Pitch (前後俯仰) & Roll (左右橫滾)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("前後俯仰 (Pitch)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${df.format(state.pitchDegrees)}°",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (state.pitchDegrees > 0) "向前傾斜" else if (state.pitchDegrees < 0) "向後傾斜" else "水平",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("左右橫滾 (Roll)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${df.format(state.rollDegrees)}°",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (state.rollDegrees > 0) "向右傾斜" else if (state.rollDegrees < 0) "向左傾斜" else "水平",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action Buttons: Calibrate Zero & Save Snapshot
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCalibrate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("此處歸零")
            }

            if (state.pitchOffset != 0f || state.rollOffset != 0f) {
                IconButton(onClick = onResetCalibration) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "重設校準")
                }
            }

            FilledTonalButton(
                onClick = onSaveSnapshot,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("記錄角度")
            }
        }
    }
}

/**
 * 2. Digital Compass View
 */
@Composable
fun CompassView(
    state: SensorSuiteState,
    onSaveSnapshot: () -> Unit
) {
    val df = remember { DecimalFormat("0.0") }
    val animatedAzimuth by animateFloatAsState(
        targetValue = state.azimuthDegrees,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CompassNeedle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Heading Display
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("當前方位 (Heading)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${df.format(state.azimuthDegrees)}°",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = state.cardinalDirection,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Compass Rose Dial
        Box(
            modifier = Modifier
                .size(280.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            // Rotating Dial
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .rotate(-animatedAzimuth)
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f

                // Outer border circle
                drawCircle(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    radius = radius,
                    style = Stroke(width = 2.dp.toPx())
                )

                // 360 degree ticks
                for (i in 0 until 360 step 5) {
                    val angleRad = Math.toRadians(i.toDouble() - 90.0)
                    val isMajor = (i % 30 == 0)
                    val isCardinal = (i % 90 == 0)
                    val tickLen = if (isCardinal) 16.dp.toPx() else if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                    val strokeW = if (isCardinal) 2.5.dp.toPx() else if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()

                    val startX = (center.x + (radius - tickLen) * cos(angleRad)).toFloat()
                    val startY = (center.y + (radius - tickLen) * sin(angleRad)).toFloat()
                    val endX = (center.x + radius * cos(angleRad)).toFloat()
                    val endY = (center.y + radius * sin(angleRad)).toFloat()

                    val color = if (i == 0) Color(0xFFEA4335) else Color.Gray.copy(alpha = 0.6f)
                    drawLine(
                        color = color,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Fixed Center Pointer Needle (North pointer red, South pointer blue)
            Canvas(modifier = Modifier.size(160.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)

                // North arrow (Red)
                drawLine(
                    color = Color(0xFFEA4335),
                    start = center,
                    end = Offset(center.x, center.y - 70.dp.toPx()),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // South arrow (Blue)
                drawLine(
                    color = Color(0xFF1A73E8),
                    start = center,
                    end = Offset(center.x, center.y + 70.dp.toPx()),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Center pivot cap
                drawCircle(color = Color.DarkGray, radius = 6.dp.toPx(), center = center)
                drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = center)
            }
        }

        // Magnetometer Details (μT)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("地磁場強度 (Magnetometer)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${df.format(state.magTotal)} μT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("X: ${df.format(state.magX)} μT", style = MaterialTheme.typography.bodySmall)
                    Text("Y: ${df.format(state.magY)} μT", style = MaterialTheme.typography.bodySmall)
                    Text("Z: ${df.format(state.magZ)} μT", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Save Snapshot
        Button(
            onClick = onSaveSnapshot,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("儲存當前方位置紀錄")
        }
    }
}

/**
 * 3. Barometer & Altimeter View
 */
@Composable
fun BarometerAltimeterView(
    state: SensorSuiteState,
    onSaveSnapshot: () -> Unit
) {
    val dfPress = remember { DecimalFormat("#,##0.0") }
    val dfAlt = remember { DecimalFormat("#,##0.0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!state.isBarometerAvailable) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "此裝置未配備硬體氣壓感應器 (Barometer)，顯示標準參考大氣壓數據。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Atmospheric Pressure Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("環境大氣壓力 (Atmospheric Pressure)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = dfPress.format(state.pressureHpa),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "hPa / mbar",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Gauge Bar
                LinearProgressIndicator(
                    progress = { ((state.pressureHpa - 950f) / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("950 hPa (低壓)", style = MaterialTheme.typography.labelSmall)
                    Text("1013.25 (標準)", style = MaterialTheme.typography.labelSmall)
                    Text("1050 hPa (高壓)", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Estimated Barometric Altitude Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("推算海拔高度 (Barometric Altitude)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = dfAlt.format(state.altitudeMeters),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00897B)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "公尺 (m)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    text = "約 ${(state.altitudeMeters * 3.28084).roundToInt()} 英呎 (ft)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Save Snapshot
        Button(
            onClick = onSaveSnapshot,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("記錄氣壓與海拔")
        }
    }
}

/**
 * 4. Ambient Light (Lux) Meter View
 */
@Composable
fun LightMeterView(
    state: SensorSuiteState,
    onSaveSnapshot: () -> Unit
) {
    val dfLux = remember { DecimalFormat("#,##0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Lux Display Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("環境光照度 (Illuminance)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = dfLux.format(state.lightLux),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFF57C00)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lux (lx)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "當前環境：${state.lightCondition}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }

        // Lighting Reference Standards Table
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("照明標準參考值 (Standard Reference)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                val standards = listOf(
                    "客廳 / 休閒空間" to "100 ~ 300 Lux",
                    "閱讀 / 書寫作業" to "500 ~ 750 Lux",
                    "精密繪圖 / 手術" to "1,000 ~ 2,000 Lux",
                    "陰天戶外" to "1,000 ~ 10,000 Lux",
                    "晴天戶外日照" to "30,000 ~ 100,000 Lux"
                )

                standards.forEach { (scene, range) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(scene, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(range, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Save Snapshot
        Button(
            onClick = onSaveSnapshot,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("記錄光照度數值")
        }
    }
}

/**
 * 5. G-Force & Accelerometer View
 */
@Composable
fun GForceAccelView(
    state: SensorSuiteState,
    onSaveSnapshot: () -> Unit
) {
    val dfG = remember { DecimalFormat("0.00") }
    val dfAcc = remember { DecimalFormat("0.0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // G-Force Main Display
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("綜合重力加速度 (Total G-Force)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = dfG.format(state.totalGForce),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "G (g-force)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }

        // Tri-Axis Acceleration Vectors
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("三軸加速度向量 (m/s²)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                // X-Axis
                AxisBar(label = "X 軸 (左右橫向)", value = state.accelX, color = Color(0xFFEA4335))
                // Y-Axis
                AxisBar(label = "Y 軸 (前後縱向)", value = state.accelY, color = Color(0xFF34A853))
                // Z-Axis
                AxisBar(label = "Z 軸 (上下垂直)", value = state.accelZ, color = Color(0xFF4285F4))
            }
        }

        // Gyroscope Angular Speed
        if (state.isGyroscopeAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("陀螺儀角速度 (Gyroscope rad/s)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("X: ${dfAcc.format(state.gyroX)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Y: ${dfAcc.format(state.gyroY)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Z: ${dfAcc.format(state.gyroZ)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Save Snapshot
        Button(
            onClick = onSaveSnapshot,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("記錄重力與加速度")
        }
    }
}

@Composable
private fun AxisBar(label: String, value: Float, color: Color) {
    val df = remember { DecimalFormat("0.00") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${df.format(value)} m/s²", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ((value + 19.6f) / 39.2f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/**
 * 6. Installed Hardware Sensors Explorer
 */
@Composable
fun InstalledSensorsListView(
    sensors: List<DeviceSensorInfo>,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已偵測 ${sensors.size} 個硬體感應器",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = "分享硬體規格")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(sensors) { sensor ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sensor.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = sensor.typeName,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "製造商: ${sensor.vendor}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "最大量程: ${sensor.maximumRange} | 功耗: ${sensor.power} mA | 解析度: ${sensor.resolution}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
