package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.ar.ArTrackingStability
import com.example.logic.ar.StabilityLevel
import com.example.ui.viewmodel.MeasureViewModel
import java.text.DecimalFormat

@Composable
fun ModernArDialogs(
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
    onDismissStabilityDiagnostics: () -> Unit,
    trackingStability: ArTrackingStability,
    isLensSmudged: Boolean,
    simulatedAlert: String?
) {
    val colorPrimary = MaterialTheme.colorScheme.primary

    // 1. Help Guide & Step Tutorial Animated Dialog
    if (showHelpDialog) {
        MeasurementTutorialDialog(
            onDismiss = onDismissHelp
        )
    }

    // 2. Multi-Sensor Fusion Status Dialog
    if (showSensorStatusDialog) {
        ModernArSensorStatusDialog(
            viewModel = viewModel,
            onDismiss = onDismissSensorStatus
        )
    }

    // 3. Lens Cleaning Warning Dialog
    if (showLensCleaningDialog) {
        AlertDialog(
            onDismissRequest = onDismissLensCleaning,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("鏡頭清潔提醒", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "偵測到鏡頭可能附著指紋油污或灰塵髒污。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "建議使用拭鏡布或乾淨棉布擦拭手機鏡頭表面，以維持最清晰的 AR 深度辨識與色彩品質。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissLensDirtWarning()
                        onDismissLensCleaning()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Text("已擦拭完成", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissLensDirtWarning()
                        onDismissLensCleaning()
                    }
                ) {
                    Text("暫時關閉")
                }
            }
        )
    }

    // 4. Low Light Environmental Alert Dialog
    if (showLowLightDialog) {
        AlertDialog(
            onDismissRequest = onDismissLowLight,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("環境光線不足", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "目前環境光照較弱，可能導致 AR 特徵點追蹤精度降低或產生飄移。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "建議開啟手電筒補光或前往光源充足之處進行精確量測。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleTorch(context)
                        viewModel.dismissLowLightWarning()
                        onDismissLowLight()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("開啟手電筒補光", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissLowLightWarning()
                        onDismissLowLight()
                    }
                ) {
                    Text("知道了")
                }
            }
        )
    }

    // 5. Excessive Motion Alert Dialog
    if (showMotionDialog) {
        AlertDialog(
            onDismissRequest = onDismissMotion,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("相機移動過快", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "移動速度過快容易引起空間錨點跳動或重定位失敗。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "請放慢平移與轉向速度，平穩掃描周遭環境以獲取最精準的尺寸數值。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissMovementWarning()
                        onDismissMotion()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("瞭解，放慢移動", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissMovementWarning()
                        onDismissMotion()
                    }
                ) {
                    Text("關閉提醒")
                }
            }
        )
    }

    // 6. Deficient Feature Points Alert Dialog
    if (showFeatureDialog) {
        AlertDialog(
            onDismissRequest = onDismissFeature,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Grain,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("環境紋理特徵不足", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "鏡頭視野內缺乏足夠的幾何或紋理特徵點（例如大面積純白牆面、光滑無紋理桌面）。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "建議將相機稍作平移，對準物體邊界、縫隙、踢腳板或有紋理的地面以建立堅固的空間錨點。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissFeatureDeficientWarning()
                        onDismissFeature()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Text("知道了", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissFeatureDeficientWarning()
                        onDismissFeature()
                    }
                ) {
                    Text("關閉提醒")
                }
            }
        )
    }

    // 7. AR Stability & Confidence Diagnostics Dialog
    if (showStabilityDiagnosticsDialog) {
        ArStabilityDiagnosticsDialog(
            stability = trackingStability,
            isLensSmudged = isLensSmudged,
            simulatedAlert = simulatedAlert,
            onTriggerCleaningTest = { viewModel.triggerLensDirtAlert() },
            onTriggerLowLightTest = { viewModel.triggerLowLightAlert() },
            onTriggerMovementTest = { viewModel.triggerFastMovementAlert() },
            onTriggerFeatureTest = { viewModel.triggerLowFeatureAlert() },
            onDismiss = onDismissStabilityDiagnostics
        )
    }
}

@Composable
private fun ModernArSensorStatusDialog(
    viewModel: MeasureViewModel,
    onDismiss: () -> Unit
) {
    val sensorTelemetry by viewModel.sensorTelemetry.collectAsState()
    val antiJitter by viewModel.antiJitterEnabled.collectAsState()
    val rawDepthConfidenceEnabled by viewModel.rawDepthConfidenceEnabled.collectAsState()
    val rawDepthConfidenceThreshold by viewModel.rawDepthConfidenceThreshold.collectAsState()
    val highFpsModeEnabled by viewModel.highFpsModeEnabled.collectAsState()
    val arCoreStatus by viewModel.arCoreStatus.collectAsState()
    val arCoreStatusMessage by viewModel.arCoreStatusMessage.collectAsState()
    val colorPrimary = MaterialTheme.colorScheme.primary

    val df1 = remember { DecimalFormat("#,##0") }
    val df2 = remember { DecimalFormat("#,##0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EnableWindowBlur(blurRadiusDp = 50)
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
                // Google ARCore Perception Engine Status Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (arCoreStatus) {
                        com.example.logic.ar.ArCoreEngineStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        com.example.logic.ar.ArCoreEngineStatus.NEEDS_INSTALLATION -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when (arCoreStatus) {
                            com.example.logic.ar.ArCoreEngineStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                            com.example.logic.ar.ArCoreEngineStatus.NEEDS_INSTALLATION -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ViewInAr,
                                contentDescription = null,
                                tint = when (arCoreStatus) {
                                    com.example.logic.ar.ArCoreEngineStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                    com.example.logic.ar.ArCoreEngineStatus.NEEDS_INSTALLATION -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Google ARCore 核心感知引擎",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = arCoreStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (arCoreStatus == com.example.logic.ar.ArCoreEngineStatus.NEEDS_INSTALLATION) {
                            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
                            activity?.let { act ->
                                Button(
                                    onClick = { viewModel.requestInstallArCore(act) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("安裝 Google Play Services for AR", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Text(
                    "硬體感應器正在即時校驗 AR 空間座標，修正視角傾斜、手震與高程誤差：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                                color = if (antiJitter) colorPrimary else MaterialTheme.colorScheme.outlineVariant,
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

                    // 4. Proximity Sensor
                    if (sensorTelemetry.isProximityAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (sensorTelemetry.isProximityNear) colorPrimary else MaterialTheme.colorScheme.outlineVariant,
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

                    // 6. Pixel Raw Depth Confidence Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (rawDepthConfidenceEnabled) colorPrimary else MaterialTheme.colorScheme.outlineVariant,
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
                                color = if (highFpsModeEnabled) colorPrimary else MaterialTheme.colorScheme.outlineVariant,
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
            TextButton(onClick = onDismiss) {
                Text("確定")
            }
        }
    )
}

@Composable
fun ActiveTrackingStabilityWarningBanner(
    stability: ArTrackingStability,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bannerColor = if (stability.level == StabilityLevel.POOR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer

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

@Composable
fun ArStabilityDiagnosticsDialog(
    stability: ArTrackingStability,
    isLensSmudged: Boolean,
    simulatedAlert: String?,
    onTriggerCleaningTest: (() -> Unit)? = null,
    onTriggerLowLightTest: (() -> Unit)? = null,
    onTriggerMovementTest: (() -> Unit)? = null,
    onTriggerFeatureTest: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val score = (stability.confidenceScore * 100).toInt()
    val scoreColor = when (stability.level) {
        StabilityLevel.HIGH -> MaterialTheme.colorScheme.tertiary
        StabilityLevel.MODERATE -> MaterialTheme.colorScheme.primary
        StabilityLevel.LOW -> MaterialTheme.colorScheme.secondary
        StabilityLevel.POOR -> MaterialTheme.colorScheme.error
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EnableWindowBlur(blurRadiusDp = 50)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Assessment, contentDescription = null, tint = scoreColor)
                Text("AR 追蹤品質與穩定度診斷", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Score Header Card
                Surface(
                    color = scoreColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("即時追蹤置信度", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "$score / 100",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = scoreColor
                            )
                        }
                        Surface(
                            color = scoreColor,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                stability.level.name,
                                color = MaterialTheme.colorScheme.surface,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Metrics Table
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("有效特徵點數量", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${stability.featurePointsCount} 點 (${if (stability.isFeatureDeficient) "⚠️ 偏少" else "良好"})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stability.isFeatureDeficient) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("相機平移速度", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${"%.2f".format(stability.cameraSpeedMps)} m/s (${if (stability.isMotionExcessive) "⚠️ 過快" else "平穩"})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stability.isMotionExcessive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("環境環境光照強度", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${"%.2f".format(stability.lightIntensity)} (${if (stability.isLightingDeficient) "⚠️ 偏暗" else "充足"})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stability.isLightingDeficient) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Lens Cleanliness Telemetry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CleaningServices,
                                null,
                                tint = if (isLensSmudged) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("相機鏡頭潔淨度", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (isLensSmudged) "⚠️ 需擦拭 (提醒中)" else "鏡頭清晰",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLensSmudged) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Alert Test Simulations
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "🧪 模擬觸發提醒測試：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (onTriggerCleaningTest != null) {
                            OutlinedButton(
                                onClick = {
                                    onTriggerCleaningTest()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text("清潔提醒", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        if (onTriggerLowLightTest != null) {
                            OutlinedButton(
                                onClick = {
                                    onTriggerLowLightTest()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text("光線不足", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (onTriggerMovementTest != null) {
                            OutlinedButton(
                                onClick = {
                                    onTriggerMovementTest()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text("移動過快", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        if (onTriggerFeatureTest != null) {
                            OutlinedButton(
                                onClick = {
                                    onTriggerFeatureTest()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text("特徵不足", fontSize = 11.sp, maxLines = 1)
                            }
                        }
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
                    Text(
                        "• 保持鏡頭乾淨：鏡頭附著指紋油污會干擾特徵點識別，若出現清潔提醒請及時擦拭。",
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
        },
        dismissButton = if (onTriggerCleaningTest != null) {
            {
                TextButton(
                    onClick = {
                        onTriggerCleaningTest()
                        onDismiss()
                    }
                ) {
                    Text("測試清潔提醒", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        } else null
    )
}
