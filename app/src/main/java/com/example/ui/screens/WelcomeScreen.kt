package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel

/**
 * Clean, minimalist Material 3 Welcome & Quick Start Screen.
 * Focuses on clarity, generous negative space, and instant usability.
 */
@Composable
fun WelcomeScreen(
    viewModel: MeasureViewModel,
    onStartMeasuring: (initialMode: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val colorContainer = MaterialTheme.colorScheme.surfaceContainerLow

    var dontShowAgain by remember { mutableStateOf(true) }

    Surface(
        color = colorSurface,
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isExpanded = maxWidth > 600.dp
            val contentMaxWidth = if (isExpanded) 520.dp else maxWidth

            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top minimal bar with skip action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.dismissWelcomeScreen(neverShowAgain = dontShowAgain)
                            onStartMeasuring(0)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "略過",
                            color = colorOnSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Main Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Minimalist Hero Image Banner
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("welcome_hero_card")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = R.drawable.img_welcome_hero),
                                contentDescription = "AR 空間測量",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Soft gradient scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.7f)
                                            )
                                        )
                                    )
                            )

                            // Clean badge
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xCC0F172A),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "高精度 AR 空間運算",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title & Clean Subtitle
                    Text(
                        text = "AR 空間測量儀",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorOnSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "簡潔、精準的空間丈量與實物量測工具",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorOnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3 Concise Feature Items
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CleanFeatureRow(
                            icon = Icons.Rounded.CenterFocusStrong,
                            iconColor = colorPrimary,
                            title = "3D 空間 AR 測量",
                            description = "點選地面錨點，自動即時計算多點長度與多邊形面積。"
                        )

                        CleanFeatureRow(
                            icon = Icons.Rounded.Straighten,
                            iconColor = MaterialTheme.colorScheme.secondary,
                            title = "螢幕尺 & 卡尺",
                            description = "雙指游標微調，毫米級刻度，適合實體小物直接比對。"
                        )

                        CleanFeatureRow(
                            icon = Icons.Rounded.ViewModule,
                            iconColor = Color(0xFFF59E0B),
                            title = "智慧地磚與磁磚網格",
                            description = "自動辨識磁磚紋理，計算單塊規格與整區鋪設數量。"
                        )
                    }
                }

                // Clean Bottom Action Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Checkbox option
                    Row(
                        modifier = Modifier
                            .clickable {
                                dontShowAgain = !dontShowAgain
                                viewModel.triggerHapticFeedback(HapticType.CLICK)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = {
                                dontShowAgain = it
                                viewModel.triggerHapticFeedback(HapticType.CLICK)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = colorPrimary)
                        )
                        Text(
                            text = "下次啟動不再顯示此導覽",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorOnSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.dismissWelcomeScreen(neverShowAgain = dontShowAgain)
                                onStartMeasuring(1)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("welcome_btn_ruler")
                        ) {
                            Icon(
                                Icons.Rounded.Straighten,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "螢幕直尺",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.dismissWelcomeScreen(neverShowAgain = dontShowAgain)
                                onStartMeasuring(0)
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorPrimary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                                .testTag("welcome_btn_start_ar")
                        ) {
                            Icon(
                                Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "開始 AR 測量",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimalist Clean Feature Row
 */
@Composable
private fun CleanFeatureRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
