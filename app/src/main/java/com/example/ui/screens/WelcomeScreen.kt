package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.BlurDirection
import com.example.ui.components.progressiveBlur
import com.example.ui.theme.ExpressiveCloverShape
import com.example.ui.theme.ExpressiveCookie8Shape
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveSquircle
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.rememberExpressiveConnectedShape
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel

/**
 * Material 3 Expressive Welcome & Quick Start Screen:
 * - Emphasized display typography
 * - Alternating geometric Expressive Shapes (Cookie8, Clover4, Squircle) for feature emblems
 * - Connected Action Bar with physics-based spring press morphing
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

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Hero Image Banner with Expressive Geometry Badge
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(184.dp)
                            .testTag("welcome_hero_card")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = R.drawable.img_welcome_hero),
                                contentDescription = "AR 空間測量",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .progressiveBlur(
                                        direction = BlurDirection.TOP_TO_BOTTOM,
                                        maxBlurRadius = 16.dp,
                                        startFraction = 0.42f,
                                        endFraction = 1.0f
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.72f)
                                            )
                                        )
                                    )
                            )

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Surface(
                                        shape = ExpressiveCookie8Shape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Verified,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Pixel Vulkan 1.4 高精度空間運算",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "AR 空間測量儀",
                        style = ExpressiveTypography.headlineEmphasized,
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

                    Spacer(modifier = Modifier.height(22.dp))

                    // 3 Concise Feature Items with Alternating Material 3 Expressive Shapes
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExpressiveFeatureRow(
                            icon = Icons.Rounded.CenterFocusStrong,
                            emblemShape = ExpressiveCookie8Shape,
                            iconColor = colorPrimary,
                            title = "3D 空間 AR 測量",
                            description = "點選地面錨點，自動即時計算多點長度與多邊形面積。"
                        )

                        ExpressiveFeatureRow(
                            icon = Icons.Rounded.Straighten,
                            emblemShape = ExpressiveCloverShape,
                            iconColor = MaterialTheme.colorScheme.secondary,
                            title = "螢幕尺 & 卡尺",
                            description = "雙指游標微調，毫米級刻度，適合實體小物直接比對。"
                        )

                        ExpressiveFeatureRow(
                            icon = Icons.Rounded.SquareFoot,
                            emblemShape = ExpressiveSquircle,
                            iconColor = MaterialTheme.colorScheme.tertiary,
                            title = "水平儀與傾角測量",
                            description = "結合重力與陀螺儀即時感應，精準測量物體表面水平與垂準角度。"
                        )
                    }
                }

                // Bottom Expressive Connected Action Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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

                    // Connected Dual Action Row (Asymmetric M3 Expressive corners + spring press)
                    val rulerInteraction = remember { MutableInteractionSource() }
                    val isRulerPressed by rulerInteraction.collectIsPressedAsState()
                    val rulerShape = rememberExpressiveConnectedShape(
                        index = 0,
                        count = 2,
                        isActive = false,
                        isPressed = isRulerPressed,
                        outerCorner = 24.dp,
                        innerRestingCorner = 8.dp,
                        innerActiveCorner = 18.dp
                    )
                    val rulerScale by animateFloatAsState(
                        targetValue = if (isRulerPressed) 0.96f else 1f,
                        animationSpec = ExpressiveMotion.spatialFast(),
                        label = "welcomeRulerScale"
                    )

                    val startInteraction = remember { MutableInteractionSource() }
                    val isStartPressed by startInteraction.collectIsPressedAsState()
                    val startShape = rememberExpressiveConnectedShape(
                        index = 1,
                        count = 2,
                        isActive = true,
                        isPressed = isStartPressed,
                        outerCorner = 24.dp,
                        innerRestingCorner = 8.dp,
                        innerActiveCorner = 18.dp
                    )
                    val startScale by animateFloatAsState(
                        targetValue = if (isStartPressed) 0.96f else 1f,
                        animationSpec = ExpressiveMotion.spatialFast(),
                        label = "welcomeStartScale"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.dismissWelcomeScreen(neverShowAgain = dontShowAgain)
                                onStartMeasuring(1)
                            },
                            interactionSource = rulerInteraction,
                            shape = rulerShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .graphicsLayer {
                                    scaleX = rulerScale
                                    scaleY = rulerScale
                                }
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
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.dismissWelcomeScreen(neverShowAgain = dontShowAgain)
                                onStartMeasuring(0)
                            },
                            interactionSource = startInteraction,
                            shape = startShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorPrimary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1.35f)
                                .height(54.dp)
                                .graphicsLayer {
                                    scaleX = startScale
                                    scaleY = startScale
                                }
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
                                fontWeight = FontWeight.ExtraBold,
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

@Composable
private fun ExpressiveFeatureRow(
    icon: ImageVector,
    emblemShape: Shape,
    iconColor: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = emblemShape,
                color = iconColor.copy(alpha = 0.14f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(21.dp)
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
