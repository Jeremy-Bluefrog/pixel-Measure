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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.ar.ArTrackingStability
import com.example.logic.ar.StabilityLevel
import com.example.ui.i18n.tr
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
                Text(tr("dlg_lens_title"), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        tr("dlg_lens_msg1"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        tr("dlg_lens_msg2"),
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
                    Text(tr("dlg_lens_confirm"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissLensDirtWarning()
                        onDismissLensCleaning()
                    }
                ) {
                    Text(tr("dlg_dismiss_temp"))
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
                Text(tr("dlg_low_light_title"), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        tr("dlg_low_light_msg1"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        tr("dlg_low_light_msg2"),
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
                    Text(tr("dlg_low_light_confirm"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissLowLightWarning()
                        onDismissLowLight()
                    }
                ) {
                    Text(tr("dlg_got_it"))
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
                Text(tr("dlg_motion_title"), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        tr("dlg_motion_msg1"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        tr("dlg_motion_msg2"),
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
                    Text(tr("dlg_motion_confirm"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissMovementWarning()
                        onDismissMotion()
                    }
                ) {
                    Text(tr("dlg_close_reminder"))
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
                Text(tr("dlg_feature_title"), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        tr("dlg_feature_msg1"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        tr("dlg_feature_msg2"),
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
                    Text(tr("dlg_got_it"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissFeatureDeficientWarning()
                        onDismissFeature()
                    }
                ) {
                    Text(tr("dlg_close_reminder"))
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
    val sensorTelemetry by viewModel.sensorTelemetry.collectAsStateWithLifecycle()
    val antiJitter by viewModel.antiJitterEnabled.collectAsStateWithLifecycle()
    val highFpsModeEnabled by viewModel.highFpsModeEnabled.collectAsStateWithLifecycle()
    val arCoreStatus by viewModel.arCoreStatus.collectAsStateWithLifecycle()
    val arCoreStatusMessage by viewModel.arCoreStatusMessage.collectAsStateWithLifecycle()
    val colorPrimary = MaterialTheme.colorScheme.primary
    val df1 = remember { DecimalFormat("#,##0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EnableWindowBlur(blurRadiusDp = 50)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sensors, null, tint = colorPrimary)
                Spacer(Modifier.width(8.dp))
                Text(tr("dlg_sensor_title"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
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
                                tr("dlg_sensor_arcore"),
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
                                    Text(tr("dlg_sensor_install_arcore"), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                            Text(tr("dlg_sensor_hand_stability"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        val steadyLabel = tr(if (sensorTelemetry.isHandSteady) "dlg_sensor_hand_steady" else "dlg_sensor_hand_moving")
                        Text(
                            tr("dlg_sensor_hand_fmt", (sensorTelemetry.stabilityScore * 100).toInt(), steadyLabel),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }



                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAntiJitterEnabled(!antiJitter) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("dlg_sensor_antijitter"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = antiJitter,
                            onCheckedChange = { viewModel.setAntiJitterEnabled(it) }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setHighFpsModeEnabled(!highFpsModeEnabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("dlg_sensor_60fps"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = highFpsModeEnabled,
                            onCheckedChange = { viewModel.setHighFpsModeEnabled(it) }
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { viewModel.calibrateSensors() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("dlg_sensor_reset_bias"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("common_confirm"))
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
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
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
                    text = stability.warningMessage ?: tr("stab_banner_default"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        stability.isFeatureDeficient -> tr("stab_sub_feature")
                        stability.isMotionExcessive -> tr("stab_sub_motion_fmt", "%.2f".format(stability.cameraSpeedMps))
                        stability.isLightingDeficient -> tr("stab_sub_light")
                        else -> tr("stab_sub_default")
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
                    text = tr(if (stability.isLightingDeficient) "stab_action_torch" else "stab_action_diag"),
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
                Text(tr("diag_title"), fontWeight = FontWeight.Bold)
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
                            Text(tr("diag_confidence"), style = MaterialTheme.typography.labelMedium)
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
                        Text(tr("diag_feature_pts"), style = MaterialTheme.typography.bodySmall)
                        Text(
                            tr("diag_pts_fmt", stability.featurePointsCount, tr(if (stability.isFeatureDeficient) "diag_pts_few" else "diag_pts_good")),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stability.isFeatureDeficient) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tr("diag_speed_title"), style = MaterialTheme.typography.bodySmall)
                        Text(
                            tr("diag_speed_fmt", "%.2f".format(stability.cameraSpeedMps), tr(if (stability.isMotionExcessive) "diag_speed_fast" else "diag_speed_steady")),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stability.isMotionExcessive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tr("diag_light_title"), style = MaterialTheme.typography.bodySmall)
                        Text(
                            tr("diag_light_fmt", "%.2f".format(stability.lightIntensity), tr(if (stability.isLightingDeficient) "diag_light_dark" else "diag_light_bright")),
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
                            Text(tr("diag_lens_title"), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            tr(if (isLensSmudged) "diag_lens_dirty" else "diag_lens_clean"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLensSmudged) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Anti-Drift Guidance Tips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        tr("diag_tips_header"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        tr("diag_tip_1"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        tr("diag_tip_2"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        tr("diag_tip_3"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("common_confirm"))
            }
        }
    )
}
