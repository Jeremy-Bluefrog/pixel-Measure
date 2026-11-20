package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.ShareUtility
import com.example.ui.components.EnableWindowBlur
import com.example.ui.components.ExpressiveButtonOption
import com.example.ui.components.ExpressiveConnectedButtonGroup
import com.example.ui.components.GradientBlurTopBar
import com.example.ui.components.RulerEditBodyContent
import com.example.ui.components.gradientBlurSource
import com.example.ui.components.rememberGradientBlurBackdropState
import com.example.ui.theme.ExpressiveCookie8Shape
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.GoogleSansRounded
import java.util.Locale

val LocalSettingsEmbeddedInTwoPane = compositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubPageScaffold(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onNavigateBack: () -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isEmbedded = LocalSettingsEmbeddedInTwoPane.current
    BackHandler(enabled = !isEmbedded) { onNavigateBack() }

    val backdropState = rememberGradientBlurBackdropState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(testTag),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            GradientBlurTopBar(
                backdropState = backdropState,
                baseColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.80f),
                blurRadius = 22.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (!isEmbedded) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onNavigateBack()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("settings_subpage_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "返回設定"
                                )
                            }
                        }
                    },
                    actions = topBarActions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .gradientBlurSource(backdropState)
                .verticalScroll(rememberScrollState())
                .padding(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Summary Banner Card
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = ExpressiveCookie8Shape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                content()

                if (!isEmbedded) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateBack()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("settings_subpage_done_btn")
                    ) {
                        Text(
                            text = "完成",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeAndAppearancePage(
    themeMode: String,
    onSelectTheme: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val options = remember {
        listOf(
            ExpressiveButtonOption("SYSTEM", "跟隨系統", testTag = "theme_option_system"),
            ExpressiveButtonOption("LIGHT", "日間", testTag = "theme_option_light"),
            ExpressiveButtonOption("DARK", "夜間", testTag = "theme_option_dark")
        )
    }

    SettingsSubPageScaffold(
        title = "外觀與深淺色模式",
        subtitle = "自訂日間明亮與夜間護眼主題",
        icon = Icons.Rounded.DarkMode,
        testTag = "settings_theme_page",
        onNavigateBack = onNavigateBack
    ) {
        ExpressiveConnectedButtonGroup(
            options = options,
            selectedValue = themeMode,
            onValueSelected = onSelectTheme,
            itemHeight = 48.dp,
            modifier = Modifier.fillMaxWidth()
        )

        val isPreviewDark = when (themeMode) {
            "DARK" -> true
            "LIGHT" -> false
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isPreviewDark) Color(0xFF101727) else Color(0xFFF3F6FC),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when (themeMode) {
                            "LIGHT" -> "已啟用日間模式"
                            "DARK" -> "已啟用夜間模式"
                            else -> "跟隨系統外觀設定"
                        },
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = if (isPreviewDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)
                    )
                    Text(
                        text = "所有尺規、浮動工具列與視窗同步套用",
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPreviewDark) Color(0xFF94A3B8) else Color(0xFF475569)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "24.5 cm",
                        style = ExpressiveTypography.labelNumeral,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DefaultUnitPage(
    selectedUnit: String,
    onSelectUnit: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val unitOptions = remember {
        listOf(
            ExpressiveButtonOption("mm", "公釐", testTag = "unit_chip_mm"),
            ExpressiveButtonOption("cm", "公分", testTag = "unit_chip_cm"),
            ExpressiveButtonOption("m", "公尺", testTag = "unit_chip_m"),
            ExpressiveButtonOption("in", "英吋", testTag = "unit_chip_in"),
            ExpressiveButtonOption("ft", "英呎", testTag = "unit_chip_ft")
        )
    }

    SettingsSubPageScaffold(
        title = "預設測量單位",
        subtitle = "套用於 AR 空間量測與歷史紀錄換算",
        icon = Icons.Rounded.Straighten,
        testTag = "settings_unit_page",
        onNavigateBack = onNavigateBack
    ) {
        ExpressiveConnectedButtonGroup(
            options = unitOptions,
            selectedValue = selectedUnit,
            onValueSelected = onSelectUnit,
            itemHeight = 48.dp,
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "即時換算對照 (以 1 公尺為基準)",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = when (selectedUnit) {
                        "mm" -> "1000 mm  ·  適合精密零件與極微距離量測"
                        "m" -> "1.00 m  ·  適合房間長寬與家具尺寸"
                        "in" -> "39.37 in  ·  適合螢幕與家電規格"
                        "ft" -> "3.28 ft  ·  適合室內設計與空間規劃"
                        "yd" -> "1.09 yd  ·  適合長距離與場地規劃"
                        else -> "100.0 cm  ·  適合日常物件與桌面量測"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ArVisualStylePage(
    reticleStyle: String,
    arAccentPalette: String,
    lineThickness: Float,
    arFontSize: String,
    badgeOpacity: String,
    gridOverlayStyle: String,
    onReticleStyleChange: (String) -> Unit,
    onArAccentPaletteChange: (String) -> Unit,
    onLineThicknessChange: (Float) -> Unit,
    onArFontSizeChange: (String) -> Unit,
    onBadgeOpacityChange: (String) -> Unit,
    onGridOverlayStyleChange: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val reticleOptions = remember {
        listOf(
            ExpressiveButtonOption("DOUBLE_RING", "雙環", testTag = "reticle_option_double_ring"),
            ExpressiveButtonOption("PRECISION_CROSSHAIR", "十字", testTag = "reticle_option_crosshair"),
            ExpressiveButtonOption("MINIMAL_DOT", "圓點", testTag = "reticle_option_dot"),
            ExpressiveButtonOption("HOLOGRAPHIC_RADAR", "雷達", testTag = "reticle_option_radar")
        )
    }
    val paletteSwatches = remember {
        listOf(
            Triple("DYNAMIC", "動態色", Color.Unspecified),
            Triple("PIXEL_BLUE", "Pixel 藍", Color(0xFF4C8DF6)),
            Triple("NEON_LIME", "螢光綠", Color(0xFF22C55E)),
            Triple("AMBER_GOLD", "金橘", Color(0xFFF59E0B)),
            Triple("CORAL_ROSE", "珊瑚紅", Color(0xFFF43F5E))
        )
    }
    val fontOptions = remember {
        listOf(
            ExpressiveButtonOption("COMPACT", "精簡"),
            ExpressiveButtonOption("STANDARD", "標準"),
            ExpressiveButtonOption("LARGE", "放大")
        )
    }
    val badgeOptions = remember {
        listOf(
            ExpressiveButtonOption("SOLID", "高顯色"),
            ExpressiveButtonOption("GLASS", "柔和玻璃"),
            ExpressiveButtonOption("CLEAR", "極簡透明")
        )
    }
    val gridOptions = remember {
        listOf(
            ExpressiveButtonOption("OFF", "關閉", testTag = "grid_option_off"),
            ExpressiveButtonOption("PERSPECTIVE_GRID", "透視網格", testTag = "grid_option_perspective"),
            ExpressiveButtonOption("DOT_MATRIX", "幾何點陣", testTag = "grid_option_dot_matrix")
        )
    }
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeTertiary = MaterialTheme.colorScheme.tertiary
    val previewStrokeColor = when (arAccentPalette) {
        "PIXEL_BLUE" -> Color(0xFF4C8DF6)
        "NEON_LIME" -> Color(0xFF22C55E)
        "AMBER_GOLD" -> Color(0xFFF59E0B)
        "CORAL_ROSE" -> Color(0xFFF43F5E)
        else -> themePrimary
    }
    val previewRingColor = when (arAccentPalette) {
        "PIXEL_BLUE" -> Color(0xFF38BDF8)
        "NEON_LIME" -> Color(0xFFA3E635)
        "AMBER_GOLD" -> Color(0xFFFDE047)
        "CORAL_ROSE" -> Color(0xFFFB7185)
        else -> themeTertiary
    }

    SettingsSubPageScaffold(
        title = "AR 準心、色彩與線條樣式",
        subtitle = "自訂 3D 準心外觀、主題色調、線條粗細與輔助網格",
        icon = Icons.Rounded.Palette,
        testTag = "settings_visual_style_page",
        onNavigateBack = onNavigateBack
    ) {
        // Live Canvas Preview (shows line, reticle style, color palette & badge)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val left = Offset(size.width * 0.18f, size.height * 0.62f)
                    val right = Offset(size.width * 0.78f, size.height * 0.62f)
                    val strokePx = lineThickness.dp.toPx()

                    if (gridOverlayStyle == "DOT_MATRIX") {
                        for (gx in 1..7) {
                            for (gy in 1..4) {
                                drawCircle(
                                    color = previewStrokeColor.copy(alpha = 0.25f),
                                    radius = 1.8.dp.toPx(),
                                    center = Offset(size.width * gx / 8f, size.height * gy / 5f)
                                )
                            }
                        }
                    } else if (gridOverlayStyle == "PERSPECTIVE_GRID") {
                        for (i in 1..4) {
                            val y = size.height * (0.45f + i * 0.12f)
                            drawLine(previewStrokeColor.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                    }

                    drawLine(
                        color = previewStrokeColor,
                        start = left,
                        end = right,
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = previewRingColor, radius = 8.dp.toPx(), center = left, style = Stroke(width = 2.2.dp.toPx()))
                    drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = left)

                    // Draw chosen reticle style at right endpoint
                    when (reticleStyle) {
                        "PRECISION_CROSSHAIR" -> {
                            val arm = 16.dp.toPx()
                            val gap = 5.dp.toPx()
                            drawLine(previewStrokeColor, Offset(right.x - arm, right.y), Offset(right.x - gap, right.y), 2.dp.toPx())
                            drawLine(previewStrokeColor, Offset(right.x + gap, right.y), Offset(right.x + arm, right.y), 2.dp.toPx())
                            drawLine(previewStrokeColor, Offset(right.x, right.y - arm), Offset(right.x, right.y - gap), 2.dp.toPx())
                            drawLine(previewStrokeColor, Offset(right.x, right.y + gap), Offset(right.x, right.y + arm), 2.dp.toPx())
                            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = right)
                        }
                        "MINIMAL_DOT" -> {
                            drawCircle(previewStrokeColor.copy(alpha = 0.35f), radius = 12.dp.toPx(), center = right, style = Stroke(width = 1.6.dp.toPx()))
                            drawCircle(Color.White, radius = 4.2.dp.toPx(), center = right)
                        }
                        "HOLOGRAPHIC_RADAR" -> {
                            val rr = 14.dp.toPx()
                            drawArc(
                                color = previewStrokeColor,
                                startAngle = -35f,
                                sweepAngle = 80f,
                                useCenter = false,
                                topLeft = Offset(right.x - rr, right.y - rr),
                                size = Size(rr * 2f, rr * 2f),
                                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = previewStrokeColor.copy(alpha = 0.45f),
                                startAngle = 145f,
                                sweepAngle = 80f,
                                useCenter = false,
                                topLeft = Offset(right.x - rr, right.y - rr),
                                size = Size(rr * 2f, rr * 2f),
                                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawCircle(Color.White, radius = 3.dp.toPx(), center = right)
                        }
                        else -> {
                            drawCircle(previewStrokeColor, radius = 15.dp.toPx(), center = right, style = Stroke(width = 2.2.dp.toPx()))
                            drawCircle(previewStrokeColor.copy(alpha = 0.55f), radius = 8.5.dp.toPx(), center = right, style = Stroke(width = 1.4.dp.toPx()))
                            drawCircle(Color.White, radius = 3.5.dp.toPx(), center = right)
                        }
                    }
                }

                val badgeBgAlpha = when (badgeOpacity) {
                    "SOLID" -> 0.94f
                    "CLEAR" -> 0.42f
                    else -> 0.74f
                }
                val labelSp = when (arFontSize) {
                    "COMPACT" -> 11.sp
                    "LARGE" -> 15.sp
                    else -> 13.sp
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = badgeBgAlpha),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = "42.8 cm",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = labelSp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "3D 瞄準準心樣式",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
            ExpressiveConnectedButtonGroup(
                options = reticleOptions,
                selectedValue = reticleStyle,
                onValueSelected = onReticleStyleChange,
                itemHeight = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "AR 線條與標註強調色",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                paletteSwatches.forEach { (key, label, swatchColor) ->
                    val isSelected = arAccentPalette == key
                    val dotColor = if (swatchColor == Color.Unspecified) themePrimary else swatchColor
                    Surface(
                        onClick = { onArAccentPaletteChange(key) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) dotColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("palette_option_$key")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontFamily = GoogleSansRounded,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "測量線條粗細",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format(Locale.US, "%.1f dp", lineThickness),
                    style = ExpressiveTypography.labelNumeral,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = lineThickness.coerceIn(2.0f, 6.5f),
                onValueChange = onLineThicknessChange,
                valueRange = 2.0f..6.5f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "AR 數值標籤大小",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
            ExpressiveConnectedButtonGroup(
                options = fontOptions,
                selectedValue = arFontSize,
                onValueSelected = onArFontSizeChange,
                itemHeight = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "標籤底框透明度",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
            ExpressiveConnectedButtonGroup(
                options = badgeOptions,
                selectedValue = badgeOpacity,
                onValueSelected = onBadgeOpacityChange,
                itemHeight = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "3D 空間輔助網格",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
            ExpressiveConnectedButtonGroup(
                options = gridOptions,
                selectedValue = gridOverlayStyle,
                onValueSelected = onGridOverlayStyleChange,
                itemHeight = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SmartAssistPage(
    vibrateOnAlign: Boolean,
    antiJitterEnabled: Boolean,
    orthogonalSnapEnabled: Boolean,
    gravityAlignmentEnabled: Boolean,
    showPointCloud: Boolean,
    vertexSnapThresholdCm: Float,
    scaleCalibrationFactor: Float,
    onVibrateChange: (Boolean) -> Unit,
    onAntiJitterChange: (Boolean) -> Unit,
    onOrthogonalSnapChange: (Boolean) -> Unit,
    onGravityAlignmentChange: (Boolean) -> Unit,
    onShowPointCloudChange: (Boolean) -> Unit,
    onVertexSnapChange: (Float) -> Unit,
    onScaleCalibrationChange: (Float) -> Unit,
    onNavigateBack: () -> Unit
) {
    val snapSelectedKey = when {
        vertexSnapThresholdCm <= 0.1f -> "OFF"
        vertexSnapThresholdCm >= 4.0f -> "STRONG"
        else -> "PRECISION"
    }
    val snapOptions = remember {
        listOf(
            ExpressiveButtonOption("OFF", "關閉吸附", testTag = "snap_option_off"),
            ExpressiveButtonOption("PRECISION", "精準 2.5cm", testTag = "snap_option_precision"),
            ExpressiveButtonOption("STRONG", "強效 5.0cm", testTag = "snap_option_strong")
        )
    }

    SettingsSubPageScaffold(
        title = "智慧對齊與觸覺輔助",
        subtitle = "讓手持量測更平穩、自訂端點磁吸與尺度微調",
        icon = Icons.Rounded.AutoFixHigh,
        testTag = "settings_smart_assist_page",
        onNavigateBack = onNavigateBack
    ) {
        DialogSwitchRow(
            icon = Icons.Rounded.Vibration,
            title = "觸覺微震動回饋",
            subtitle = "落點、吸附角度與按鍵操作時震動提示",
            checked = vibrateOnAlign,
            onCheckedChange = onVibrateChange,
            testTag = "switch_vibrate"
        )
        DialogSwitchRow(
            icon = Icons.Rounded.Sensors,
            title = "防手震穩定平滑",
            subtitle = "抑制手部晃動帶來的游標微幅漂移",
            checked = antiJitterEnabled,
            onCheckedChange = onAntiJitterChange,
            testTag = "switch_sensor_fusion_master"
        )
        DialogSwitchRow(
            icon = Icons.Rounded.Architecture,
            title = "90° / 180° 直角自動吸附",
            subtitle = "接近水平或垂直軸線時自動貼合對齊",
            checked = orthogonalSnapEnabled,
            onCheckedChange = onOrthogonalSnapChange,
            testTag = "switch_orthogonal_snap"
        )
        DialogSwitchRow(
            icon = Icons.Rounded.VerticalAlignBottom,
            title = "牆面高度垂直校正",
            subtitle = "測量物品高度時對齊重力垂直方向",
            checked = gravityAlignmentEnabled,
            onCheckedChange = onGravityAlignmentChange,
            testTag = "switch_gravity_alignment"
        )
        DialogSwitchRow(
            icon = Icons.Rounded.Grain,
            title = "顯示空間深度特徵點",
            subtitle = "於平面掃描時顯示偵測到的深度參考點",
            checked = showPointCloud,
            onCheckedChange = onShowPointCloudChange,
            testTag = "switch_point_cloud"
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "端點與邊線磁吸靈敏度",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
                ExpressiveConnectedButtonGroup(
                    options = snapOptions,
                    selectedValue = snapSelectedKey,
                    onValueSelected = { key ->
                        val cm = when (key) {
                            "OFF" -> 0f
                            "STRONG" -> 5.0f
                            else -> 2.5f
                        }
                        onVertexSnapChange(cm)
                    },
                    itemHeight = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AR 空間尺度微調校準",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "對照實體捲尺微調比例係數",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.3fx", scaleCalibrationFactor),
                            style = ExpressiveTypography.labelNumeral,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (kotlin.math.abs(scaleCalibrationFactor - 1.0f) > 0.002f) {
                            TextButton(
                                onClick = { onScaleCalibrationChange(1.0f) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
                                Text("重設", fontSize = 12.sp, fontFamily = GoogleSansRounded)
                            }
                        }
                    }
                }
                Slider(
                    value = scaleCalibrationFactor.coerceIn(0.90f, 1.10f),
                    onValueChange = onScaleCalibrationChange,
                    valueRange = 0.90f..1.10f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_scale_calibration")
                )
            }
        }
    }
}

@Composable
fun CameraQualityPage(
    cameraAspectRatio: String,
    highFpsModeEnabled: Boolean,
    highDefinitionQualityEnabled: Boolean,
    useDisplayP3ColorSpace: Boolean,
    isLensDirtWarningEnabled: Boolean,
    torchBrightness: Float,
    refreshRateMode: String,
    onAspectRatioChange: (String) -> Unit,
    onHighFpsChange: (Boolean) -> Unit,
    onHighDefinitionChange: (Boolean) -> Unit,
    onDisplayP3Change: (Boolean) -> Unit,
    onLensDirtWarningChange: (Boolean) -> Unit,
    onTorchBrightnessChange: (Float) -> Unit,
    onRefreshRateChange: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val ratioOptions = remember {
        listOf(
            ExpressiveButtonOption("FULL", "全螢幕"),
            ExpressiveButtonOption("16_9", "16:9"),
            ExpressiveButtonOption("4_3", "4:3"),
            ExpressiveButtonOption("1_1", "1:1")
        )
    }
    val refreshRateOptions = remember {
        listOf(
            ExpressiveButtonOption("120Hz", "120Hz 極速", testTag = "refresh_rate_120hz"),
            ExpressiveButtonOption("60Hz", "60Hz 標準", testTag = "refresh_rate_60hz"),
            ExpressiveButtonOption("30Hz", "30Hz 省電", testTag = "refresh_rate_30hz")
        )
    }

    SettingsSubPageScaffold(
        title = "相機畫質、色彩與補光",
        subtitle = "控制取景比例、120Hz 流暢度、廣色域與手電筒預設亮度",
        icon = Icons.Rounded.CameraAlt,
        testTag = "settings_camera_quality_page",
        onNavigateBack = onNavigateBack
    ) {
        // Aspect Ratio Visual Preview Box
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                val frameRatio = when (cameraAspectRatio) {
                    "16_9" -> 1.77f
                    "4_3" -> 1.33f
                    "1_1" -> 1.0f
                    else -> 2.15f
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .height(68.dp)
                        .aspectRatio(frameRatio)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (cameraAspectRatio == "FULL") "全螢幕視野" else cameraAspectRatio.replace("_", ":"),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        ExpressiveConnectedButtonGroup(
            options = ratioOptions,
            selectedValue = cameraAspectRatio,
            onValueSelected = onAspectRatioChange,
            itemHeight = 48.dp,
            modifier = Modifier.fillMaxWidth()
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "畫面更新率與 120Hz Pro 暢順顯示",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
            ExpressiveConnectedButtonGroup(
                options = refreshRateOptions,
                selectedValue = refreshRateMode,
                onValueSelected = onRefreshRateChange,
                itemHeight = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        DialogSwitchRow(
            icon = Icons.Rounded.Speed,
            title = "60 FPS 高流暢預覽",
            subtitle = "鏡頭移動時保持高更新率與即時準心追蹤",
            checked = highFpsModeEnabled,
            onCheckedChange = onHighFpsChange,
            testTag = "switch_high_fps"
        )

        DialogSwitchRow(
            icon = Icons.Rounded.Hd,
            title = "高清晰度成像 (Ultra HD)",
            subtitle = "提升遠距表面紋理辨識與拍照清晰度",
            checked = highDefinitionQualityEnabled,
            onCheckedChange = onHighDefinitionChange,
            testTag = "switch_high_definition"
        )

        DialogSwitchRow(
            icon = Icons.Rounded.ColorLens,
            title = "Display P3 廣色域相片",
            subtitle = "儲存測量照片時保留 Pixel 寬廣色彩表現",
            checked = useDisplayP3ColorSpace,
            onCheckedChange = onDisplayP3Change,
            testTag = "switch_display_p3"
        )

        DialogSwitchRow(
            icon = Icons.Rounded.CleaningServices,
            title = "鏡頭髒污與指紋提醒",
            subtitle = "偵測畫面霧化時貼心提示清潔鏡頭提升準度",
            checked = isLensDirtWarningEnabled,
            onCheckedChange = onLensDirtWarningChange,
            testTag = "switch_lens_dirt_warning"
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "手電筒預設補光亮度",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(torchBrightness * 100).toInt()}%",
                        style = ExpressiveTypography.labelNumeral,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = torchBrightness.coerceIn(0.2f, 1.0f),
                    onValueChange = onTorchBrightnessChange,
                    valueRange = 0.2f..1.0f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.25f to "25%", 0.50f to "50%", 0.75f to "75%", 1.00f to "100%").forEach { (level, lbl) ->
                        val isSelected = kotlin.math.abs(torchBrightness - level) < 0.12f
                        FilterChip(
                            selected = isSelected,
                            onClick = { onTorchBrightnessChange(level) },
                            label = {
                                Text(
                                    text = lbl,
                                    fontSize = 12.sp,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InterfaceCustomizationPage(
    uiButtonScale: Float,
    keepScreenOn: Boolean,
    autoCopyOnComplete: Boolean,
    onUiButtonScaleChange: (Float) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onAutoCopyOnCompleteChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    val scaleSelectedKey = when {
        uiButtonScale <= 0.90f -> "0.85"
        uiButtonScale <= 1.07f -> "1.0"
        uiButtonScale <= 1.22f -> "1.15"
        else -> "1.30"
    }
    val scaleOptions = remember {
        listOf(
            ExpressiveButtonOption("0.85", "緊湊", testTag = "btn_scale_085"),
            ExpressiveButtonOption("1.0", "標準", testTag = "btn_scale_100"),
            ExpressiveButtonOption("1.15", "放大", testTag = "btn_scale_115"),
            ExpressiveButtonOption("1.30", "特大", testTag = "btn_scale_130")
        )
    }

    SettingsSubPageScaffold(
        title = "操作介面與便利自訂",
        subtitle = "調整主按鈕操作尺寸、螢幕恆亮與自動剪貼簿功能",
        icon = Icons.Rounded.Tune,
        testTag = "settings_interface_page",
        onNavigateBack = onNavigateBack
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AR 下方操作按鈕大小",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size((32 * uiButtonScale.coerceIn(0.85f, 1.30f)).dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size((18 * uiButtonScale.coerceIn(0.85f, 1.30f)).dp)
                            )
                        }
                    }
                }
                ExpressiveConnectedButtonGroup(
                    options = scaleOptions,
                    selectedValue = scaleSelectedKey,
                    onValueSelected = { onUiButtonScaleChange(it.toFloatOrNull() ?: 1.0f) },
                    itemHeight = 46.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        DialogSwitchRow(
            icon = Icons.Rounded.WbSunny,
            title = "測量期間保持螢幕恆亮",
            subtitle = "避免長時間對齊物件時螢幕自動休眠變暗",
            checked = keepScreenOn,
            onCheckedChange = onKeepScreenOnChange,
            testTag = "switch_keep_screen_on"
        )

        DialogSwitchRow(
            icon = Icons.Rounded.ContentCopy,
            title = "完成線段後自動複製結果",
            subtitle = "完成兩點或幾何量測時自動將數值寫入剪貼簿",
            checked = autoCopyOnComplete,
            onCheckedChange = onAutoCopyOnCompleteChange,
            testTag = "switch_auto_copy"
        )
    }
}

@Composable
fun ToolsCustomizationPage(
    rulerTheme: String,
    rulerDualUnit: Boolean,
    onRulerThemeChange: (String) -> Unit,
    onRulerDualUnitChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    val rulerThemeOptions = remember {
        listOf(
            ExpressiveButtonOption("MATERIAL_YOU", "動態色", testTag = "ruler_theme_material"),
            ExpressiveButtonOption("STEEL", "鋼鐵灰", testTag = "ruler_theme_steel"),
            ExpressiveButtonOption("NEON_CYAN", "霓光青", testTag = "ruler_theme_cyan"),
            ExpressiveButtonOption("HIGH_CONTRAST", "工程黃", testTag = "ruler_theme_contrast")
        )
    }

    val (previewBg, previewAccent, previewText) = when (rulerTheme) {
        "STEEL" -> Triple(Color(0xFF1E293B), Color(0xFF94A3B8), Color(0xFFF1F5F9))
        "NEON_CYAN" -> Triple(Color(0xFF091522), Color(0xFF22D3EE), Color(0xFFCFFAFE))
        "HIGH_CONTRAST" -> Triple(Color(0xFF18181B), Color(0xFFFACC15), Color(0xFFFEF08A))
        else -> Triple(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface)
    }

    SettingsSubPageScaffold(
        title = "螢幕尺風格與對照個人化",
        subtitle = "自訂螢幕尺外觀色彩與英吋雙制對照顯示",
        icon = Icons.Rounded.Colorize,
        testTag = "settings_tools_custom_page",
        onNavigateBack = onNavigateBack
    ) {
        // Mini Ruler Theme Preview Strip
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = previewBg,
            border = BorderStroke(1.dp, previewAccent.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val steps = 20
                    for (i in 0..steps) {
                        val x = (size.width * (i + 1) / (steps + 2))
                        val isMajor = i % 5 == 0
                        val len = if (isMajor) 20.dp.toPx() else 10.dp.toPx()
                        drawLine(
                            color = if (isMajor) previewAccent else previewText.copy(alpha = 0.45f),
                            start = Offset(x, 0f),
                            end = Offset(x, len),
                            strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                        )
                    }
                }
                Text(
                    text = if (rulerDualUnit) "cm / in 雙制對照預覽" else "雙側公分 (cm) 預覽",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = previewText,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "螢幕尺刻度外觀主題",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
            ExpressiveConnectedButtonGroup(
                options = rulerThemeOptions,
                selectedValue = rulerTheme,
                onValueSelected = onRulerThemeChange,
                itemHeight = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        DialogSwitchRow(
            icon = Icons.Rounded.SwapHoriz,
            title = "右側顯示英吋 (in) 對照刻度",
            subtitle = "左側維持公分 (cm)，右側切換為英吋以利快速換算",
            checked = rulerDualUnit,
            onCheckedChange = onRulerDualUnitChange,
            testTag = "switch_ruler_dual_unit"
        )
    }
}

@Composable
fun RulerEditPage(
    calibrationFactor: Float,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onCalibrationChange: (Float) -> Unit,
    onResetAll: () -> Unit,
    onNavigateBack: () -> Unit
) {
    SettingsSubPageScaffold(
        title = "螢幕尺數字大小與校正",
        subtitle = "調整螢幕尺刻度文字縮放倍率與實體信用卡精準比例係數",
        icon = Icons.Rounded.Edit,
        testTag = "settings_ruler_edit_page",
        onNavigateBack = onNavigateBack,
        topBarActions = {
            TextButton(
                onClick = onResetAll,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("全部重設", fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        RulerEditBodyContent(
            calibrationFactor = calibrationFactor,
            fontScale = fontScale,
            onFontScaleChange = onFontScaleChange,
            onCalibrationChange = onCalibrationChange
        )
    }
}

@Composable
fun MeasurementTutorialPage(
    onReplayWelcome: () -> Unit,
    onNavigateBack: () -> Unit
) {
    SettingsSubPageScaffold(
        title = "AR 測量使用指南",
        subtitle = "掌握空間測距與 3D 圓形量測訣竅",
        icon = Icons.Rounded.AutoAwesome,
        testTag = "settings_tutorial_page",
        onNavigateBack = onNavigateBack
    ) {
        TutorialTipCard(
            step = "1",
            title = "掃描平面直到 3D 雙環穩定貼合",
            desc = "在光線充足、有紋理的桌面或地面緩慢左右移動手機，讓雙環準心感應平面傾斜角度。"
        )
        TutorialTipCard(
            step = "2",
            title = "3D 空間圓圈與直徑量測",
            desc = "切換至「圓形」模式後，先點選邊緣第 1 點與第 2 點預覽直徑，補上第 3 點即可精準定出空間圓心與周長。"
        )
        TutorialTipCard(
            step = "3",
            title = "拍照、錄影與歷史紀錄查看",
            desc = "點擊拍照鍵可儲存含數據浮水印的照片；長按或切換錄影鍵可保存帶測量標註的影片至歷史紀錄。"
        )

        FilledTonalButton(
            onClick = {
                onNavigateBack()
                onReplayWelcome()
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(Icons.Rounded.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("重播完整歡迎導覽", fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AboutFeedbackPage(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val feedbackEmail = "jeremy1030623@gmail.com"

    SettingsSubPageScaffold(
        title = "關於 Measure",
        subtitle = "Google Pixel 原生 AR 空間與日常測量工具",
        icon = Icons.Rounded.Info,
        testTag = "settings_about_page",
        onNavigateBack = onNavigateBack
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Measure",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "整合 Google Play Services for AR 空間測距、3D 圓圈擬合與螢幕直尺，提供直覺精準的日常量測體驗。",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "問題回報與建議信箱",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = feedbackEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { ShareUtility.copyToClipboard(context, feedbackEmail, "已複製信箱：$feedbackEmail") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("複製信箱", fontFamily = GoogleSansRounded)
                    }
                    Button(
                        onClick = { ShareUtility.sendFeedbackEmail(context, feedbackEmail) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Mail, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("寄送回饋", fontFamily = GoogleSansRounded)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            EnableWindowBlur(blurRadiusDp = 44)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(confirmLabel, fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("取消", fontFamily = GoogleSansRounded)
            }
        }
    )
}

@Composable
private fun DialogSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Switch) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(it)
                },
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun TutorialTipCard(
    step: String,
    title: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = step,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
