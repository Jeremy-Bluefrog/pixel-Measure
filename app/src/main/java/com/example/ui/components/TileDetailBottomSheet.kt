package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.ShareUtility
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
    val activePreset by viewModel.activeTilePreset.collectAsState()

    val widthCm = Math.round(if (tile.estimatedWidthCm > 0) tile.estimatedWidthCm else activePreset.widthCm).toInt()
    val heightCm = Math.round(if (tile.estimatedHeightCm > 0) tile.estimatedHeightCm else activePreset.heightCm).toInt()
    val areaCm2 = (widthCm.toLong() * heightCm.toLong())
    val perimeterCm = ((widthCm + heightCm) * 2).toLong()

    val df = remember { DecimalFormat("#,##0") }
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.sweepGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.tertiary,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.SquareFoot,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "磁磚測量結果",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "公分與面積測量數據",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "關閉", modifier = Modifier.size(18.dp))
                }
            }

            // Primary Measurement Result Cards
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Dimension (Width x Height)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "磁磚尺寸 (寬 × 長)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${df.format(widthCm)} × ${df.format(heightCm)} cm",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorPrimary
                            )
                        }

                        Surface(
                            color = colorPrimary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "公分",
                                color = colorOnPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Area & Perimeter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Area Card
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "磁磚面積",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${df.format(areaCm2)} cm²",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Perimeter Card
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "磁磚周長",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${df.format(perimeterCm)} cm",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons: Save & Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val shareText = """
                            📐 【磁磚測量結果】
                            • 尺寸：${df.format(widthCm)} × ${df.format(heightCm)} cm
                            • 面積：${df.format(areaCm2)} cm²
                            • 周長：${df.format(perimeterCm)} cm
                        """.trimIndent()
                        ShareUtility.shareText(context, shareText, "分享磁磚測量結果")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("分享結果")
                }

                Button(
                    onClick = {
                        val title = "磁磚測量 (${df.format(widthCm)}×${df.format(heightCm)} cm)"
                        val notes = "寬度：${df.format(widthCm)} cm | 長度：${df.format(heightCm)} cm | 面積：${df.format(areaCm2)} cm² | 周長：${df.format(perimeterCm)} cm"
                        viewModel.saveMeasurementRecord(
                            customTitle = title,
                            customNotes = notes
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorPrimary, contentColor = colorOnPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("儲存紀錄", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
