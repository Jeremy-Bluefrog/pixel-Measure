package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.ShareUtility
import com.example.logic.ai.AiTileDetector
import com.example.logic.ai.DetectedTile
import com.example.ui.viewmodel.MeasureViewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileDetailBottomSheet(
    tile: DetectedTile,
    viewModel: MeasureViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val targetAreaM2 by viewModel.tileTargetAreaM2.collectAsState()
    val wastagePercent by viewModel.tileWastagePercent.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()

    var isPingUnit by remember { mutableStateOf(false) } // 坪 vs m²
    var areaInputText by remember(targetAreaM2, isPingUnit) {
        val displayVal = if (isPingUnit) targetAreaM2 / 3.30578 else targetAreaM2
        mutableStateOf(DecimalFormat("0.#").format(displayVal))
    }

    val estimation = remember(tile, targetAreaM2, wastagePercent) {
        AiTileDetector.estimateTileRequirement(
            tileWidthCm = tile.estimatedWidthCm,
            tileHeightCm = tile.estimatedHeightCm,
            roomAreaM2 = targetAreaM2,
            wastagePercent = wastagePercent
        )
    }

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnPrimary = MaterialTheme.colorScheme.onPrimary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = colorPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = colorPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "AI Core 磁磚識別與規格報告",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${tile.label} · ${tile.material}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "關閉")
                }
            }

            // Tile Dimension Specification Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "單片磁磚尺寸 (寬 × 長)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${DecimalFormat("0.#").format(tile.estimatedWidthCm)} cm × ${DecimalFormat("0.#").format(tile.estimatedHeightCm)} cm",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorPrimary
                            )
                        }

                        Surface(
                            color = colorPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "單片面積 ${DecimalFormat("0.00").format(tile.areaM2)} m²",
                                color = colorPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Secondary Specs: Perimeter, Material, Grout width
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("單片周長", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${DecimalFormat("0.#").format((tile.estimatedWidthCm + tile.estimatedHeightCm) * 2)} cm", fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("填縫寬度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("約 ${tile.groutWidthMm} mm", fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("識別信心度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${(tile.confidence * 100).toInt()}%", fontWeight = FontWeight.Bold, color = colorPrimary)
                        }
                    }
                }
            }

            // Construction Quantity & Material Estimator Section
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Calculate, null, tint = colorPrimary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "鋪設用量與施工備料估算",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Unit switch: 坪 vs m²
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.shadow(2.dp, RoundedCornerShape(12.dp))
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                Surface(
                                    color = if (!isPingUnit) colorPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.clickable {
                                        isPingUnit = false
                                    }
                                ) {
                                    Text(
                                        text = "m²",
                                        color = if (!isPingUnit) colorOnPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    color = if (isPingUnit) colorPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.clickable {
                                        isPingUnit = true
                                    }
                                ) {
                                    Text(
                                        text = "坪",
                                        color = if (isPingUnit) colorOnPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Input Target Area
                    OutlinedTextField(
                        value = areaInputText,
                        onValueChange = { input ->
                            areaInputText = input
                            val num = input.toDoubleOrNull()
                            if (num != null && num > 0) {
                                val m2 = if (isPingUnit) num * 3.30578 else num
                                viewModel.setTileTargetAreaM2(m2)
                            }
                        },
                        label = { Text(if (isPingUnit) "輸入目標施工坪數 (坪)" else "輸入目標施工面積 (m²)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Wastage buffer selection chips (5%, 10%, 15%)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "裁切損耗備料率 (${wastagePercent}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5 to "5% (標準)", 10 to "10% (建議)", 15 to "15% (異形/斜角)").forEach { (pct, label) ->
                                FilterChip(
                                    selected = wastagePercent == pct,
                                    onClick = { viewModel.setTileWastagePercent(pct) },
                                    label = { Text(label, fontSize = 12.sp, fontWeight = if (wastagePercent == pct) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Calculation Result Highlight Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .shadow(2.dp, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("基礎淨需求", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${estimation.baseTileCount} 片",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            color = colorPrimary,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .shadow(4.dp, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("建議採購片數 (含損耗)", style = MaterialTheme.typography.labelSmall, color = colorOnPrimary.copy(alpha = 0.85f))
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${estimation.totalRecommendedTileCount} 片",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colorOnPrimary
                                )
                            }
                        }
                    }

                    Text(
                        text = "💡 填縫劑預估總長度：約 ${DecimalFormat("0.#").format(estimation.estimatedGroutLengthMeters)} 公尺",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Buttons: Save Record & Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val shareText = """
                            📐 【AI Core 磁磚規格與用量預算報告】
                            • 磁磚尺寸：${DecimalFormat("0.#").format(tile.estimatedWidthCm)} × ${DecimalFormat("0.#").format(tile.estimatedHeightCm)} cm
                            • 材質類型：${tile.material}
                            • 單片面積：${DecimalFormat("0.00").format(tile.areaM2)} m²
                            • 目標施工面積：${DecimalFormat("0.1").format(targetAreaM2)} m² (${DecimalFormat("0.1").format(targetAreaM2 / 3.30578)} 坪)
                            • 基礎需求片數：${estimation.baseTileCount} 片
                            • 建議採購片數：${estimation.totalRecommendedTileCount} 片 (含 ${wastagePercent}% 備料損耗)
                            • 填縫線總長預估：${DecimalFormat("0.#").format(estimation.estimatedGroutLengthMeters)} m
                        """.trimIndent()
                        ShareUtility.shareText(context, shareText, "分享磁磚測量報告")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("分享報告")
                }

                Button(
                    onClick = {
                        val title = "磁磚規格 (${DecimalFormat("0.#").format(tile.estimatedWidthCm)}×${DecimalFormat("0.#").format(tile.estimatedHeightCm)} cm)"
                        val notes = "材質：${tile.material} | 單片面積：${DecimalFormat("0.00").format(tile.areaM2)} m² | 施工面積：${DecimalFormat("0.1").format(targetAreaM2)} m² (建議採購 ${estimation.totalRecommendedTileCount} 片)"
                        viewModel.saveMeasurementRecord(
                            customTitle = title,
                            customNotes = notes
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorPrimary, contentColor = colorOnPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("儲存測量紀錄", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
