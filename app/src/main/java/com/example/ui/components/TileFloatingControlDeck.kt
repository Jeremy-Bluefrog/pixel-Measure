package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.ai.AiTilePreset
import com.example.logic.ai.DetectedTile
import com.example.ui.viewmodel.MeasureViewModel
import java.text.DecimalFormat

@Composable
fun TileFloatingControlDeck(
    viewModel: MeasureViewModel,
    detectedTiles: List<DetectedTile>,
    isAiTileMode: Boolean,
    isAiTileAnalyzing: Boolean,
    activePreset: AiTilePreset,
    onOpenDetailSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnPrimary = MaterialTheme.colorScheme.onPrimary
    val currentTile = detectedTiles.firstOrNull()
    val widthCm = Math.round(currentTile?.estimatedWidthCm ?: activePreset.widthCm).toInt()
    val heightCm = Math.round(currentTile?.estimatedHeightCm ?: activePreset.heightCm).toInt()
    val areaCm2 = (widthCm.toLong() * heightCm.toLong())
    val df = remember { DecimalFormat("#,##0") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Clean Glassmorphic Tile Hub Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF2121520),
            shadowElevation = 12.dp,
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF80D8FF).copy(alpha = 0.7f),
                        Color(0xFFBA68C8).copy(alpha = 0.6f),
                        Color(0xFFFFD54F).copy(alpha = 0.7f)
                    )
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Row: Tile Centimeters & Area Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        colors = listOf(
                                            Color(0xFFFFB74D),
                                            Color(0xFFFF4081),
                                            Color(0xFF7C4DFF),
                                            Color(0xFF00E5FF),
                                            Color(0xFFFFB74D)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SquareFoot,
                                contentDescription = "Tile Measurement",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "磁磚尺寸 (公分)",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${df.format(widthCm)} × ${df.format(heightCm)} cm",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Area Badge
                    Surface(
                        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "磁磚面積",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${df.format(areaCm2)} cm²",
                                color = Color(0xFF80D8FF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                // Bottom Row: Clean Action Controls (One-Tap Measure, View Details, Re-Scan)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. One-Tap Lock Tile Button
                    Button(
                        onClick = {
                            viewModel.measureTileUnderReticle()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorPrimary,
                            contentColor = colorOnPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "鎖定測量",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. View Simple Detail
                    OutlinedButton(
                        onClick = onOpenDetailSheet,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFFD54F)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Straighten, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "詳細數據",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 3. Rescan AI Core Button
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                viewModel.triggerAiTileDetection()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isAiTileAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF80D8FF)
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "重新偵測",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
