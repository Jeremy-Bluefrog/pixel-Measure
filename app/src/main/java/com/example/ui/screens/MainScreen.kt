package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MeasureRecord
import com.example.logic.TranslationManager
import com.example.ui.components.ModernArCameraView
import com.example.ui.components.RulerComponent
import com.example.ui.components.SensorSuiteComponent
import com.example.ui.viewmodel.MeasureViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MeasureViewModel) {
    val currentMode by viewModel.currentMode.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val savedRecords by viewModel.savedRecords.collectAsState()
    val currentLang by viewModel.currentLanguage.collectAsState()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUnitMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    val colorPrimary = MaterialTheme.colorScheme.primary

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            )
        },
        topBar = {
            if (currentMode == 1) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "螢幕高精直尺",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        // Unit selector button
                        Box {
                            TextButton(
                                onClick = { showUnitMenu = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = selectedUnit.uppercase(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colorPrimary
                                )
                                Icon(
                                    Icons.Rounded.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showUnitMenu,
                                onDismissRequest = { showUnitMenu = false }
                            ) {
                                listOf("cm" to "公分 (cm)", "m" to "公尺 (m)", "in" to "英吋 (in)", "ft" to "英呎 (ft)", "yd" to "碼 (yd)").forEach { (unitCode, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontWeight = if (selectedUnit == unitCode) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            viewModel.setSelectedUnit(unitCode)
                                            showUnitMenu = false
                                        },
                                        leadingIcon = if (selectedUnit == unitCode) {
                                            { Icon(Icons.Rounded.Check, null, tint = colorPrimary) }
                                        } else null
                                    )
                                }
                            }
                        }

                        // History sheet button
                        IconButton(onClick = { showHistorySheet = true }) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = "歷史記錄",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Settings dialog button
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "設定",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            } else if (currentMode == 2) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "感應器儀表箱",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        IconButton(onClick = { showHistorySheet = true }) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = "歷史記錄",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "設定",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentMode == 0,
                    onClick = { viewModel.setMode(0) },
                    icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = "相機 AR") },
                    label = { Text("相機 AR", fontWeight = if (currentMode == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                NavigationBarItem(
                    selected = currentMode == 1,
                    onClick = { viewModel.setMode(1) },
                    icon = { Icon(Icons.Rounded.Straighten, contentDescription = "螢幕尺") },
                    label = { Text("螢幕尺", fontWeight = if (currentMode == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                NavigationBarItem(
                    selected = currentMode == 2,
                    onClick = { viewModel.setMode(2) },
                    icon = { Icon(Icons.Rounded.Sensors, contentDescription = "感應器") },
                    label = { Text("感應器", fontWeight = if (currentMode == 2) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (currentMode == 0) PaddingValues(bottom = innerPadding.calculateBottomPadding()) else innerPadding)
        ) {
            AnimatedContent(
                targetState = currentMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "ToolModeTransition"
            ) { mode ->
                when (mode) {
                    0 -> ModernArCameraView(
                        viewModel = viewModel,
                        onShowHistoryClick = { showHistorySheet = true },
                        onShowSettingsClick = { showSettingsDialog = true }
                    )
                    1 -> RulerComponent(
                        viewModel = viewModel,
                        onShowHistoryClick = { showHistorySheet = true }
                    )
                    else -> SensorSuiteComponent(
                        viewModel = viewModel,
                        onShowHistoryClick = { showHistorySheet = true }
                    )
                }
            }
        }
    }

    // History Bottom Sheet
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            HistorySheetContent(
                records = savedRecords,
                viewModel = viewModel,
                onClose = { showHistorySheet = false }
            )
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun HistorySheetContent(
    records: List<MeasureRecord>,
    viewModel: MeasureViewModel,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredRecords = remember(records, searchQuery) {
        if (searchQuery.isBlank()) records
        else records.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            (it.notes?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "歷史測量紀錄 (${records.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (records.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全部清除")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜尋測量紀錄...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (records.isEmpty()) "尚未儲存任何測量紀錄" else "找不到符合的紀錄",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = when (record.type) {
                                                "AREA" -> "面積"
                                                "HEIGHT" -> "垂直高度"
                                                "VOLUME" -> "3D 體積"
                                                "CIRCLE" -> "圓形直徑"
                                                "ANGLE" -> "空間夾角"
                                                "RULER" -> "螢幕尺"
                                                "LEVEL" -> "水平儀"
                                                "COMPASS" -> "羅盤"
                                                "BAROMETER" -> "氣壓"
                                                "LIGHT" -> "照度"
                                                "ACCEL" -> "加速度"
                                                else -> "長度"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = record.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                val formattedVal = when (record.type) {
                                    "AREA" -> viewModel.formatArea(record.value, record.unit)
                                    "VOLUME" -> viewModel.formatVolume(record.value, record.unit)
                                    "ANGLE", "LEVEL", "COMPASS" -> "${DecimalFormat("0.0").format(record.value)}${record.unit}"
                                    "BAROMETER" -> "${DecimalFormat("#,##0.0").format(record.value)} ${record.unit}"
                                    "LIGHT" -> "${DecimalFormat("#,##0").format(record.value)} ${record.unit}"
                                    "ACCEL" -> "${DecimalFormat("0.00").format(record.value)} ${record.unit}"
                                    else -> viewModel.formatLength(record.value, record.unit)
                                }
                                Text(
                                    text = formattedVal,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                if (!record.notes.isNullOrBlank()) {
                                    Text(
                                        text = record.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = dateFormat.format(Date(record.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteRecord(record) }
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "刪除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("確定清除所有紀錄？", fontWeight = FontWeight.Bold) },
            text = { Text("此動作將永久刪除所有的歷史測量紀錄，無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllRecords()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun SettingsDialog(
    viewModel: MeasureViewModel,
    onDismiss: () -> Unit
) {
    val currentLang by viewModel.currentLanguage.collectAsState()
    val vibrateOnAlign by viewModel.vibrateOnAlignment.collectAsState()
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val isDynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("偏好設定", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dynamic Color (Material You)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("動態色彩 (Material You)", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (isDynamicColorSupported) "依據系統桌布主題自動調整應用程式色彩" else "需要 Android 12 以上版本支援系統桌布配色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dynamicColorEnabled,
                        onCheckedChange = { viewModel.setDynamicColorEnabled(it) }
                    )
                }

                HorizontalDivider()

                // Vibration toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("對齊震動回饋", fontWeight = FontWeight.Bold)
                        Text(
                            "錨點吸附與測量操作時產生觸覺震動回饋",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vibrateOnAlign,
                        onCheckedChange = { viewModel.setVibrateOnAlignment(it) }
                    )
                }

                HorizontalDivider()

                // Scanning Feature Point Cloud Toggle (掃描時的點點)
                val showPointCloud by viewModel.showPointCloud.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("顯示掃描點 (特徵點雲)", fontWeight = FontWeight.Bold)
                        Text(
                            "在空間中顯示表面識別點，掌握 AR 追蹤與表面偵測狀態",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showPointCloud,
                        onCheckedChange = { viewModel.setShowPointCloud(it) }
                    )
                }

                HorizontalDivider()

                // Language selector
                Column {
                    Text("應用程式語言", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "zh-TW" to "繁體中文",
                            "zh-CN" to "简体中文",
                            "en" to "English",
                            "ja" to "日本語"
                        ).forEach { (code, name) ->
                            FilterChip(
                                selected = currentLang == code,
                                onClick = { viewModel.setLanguage(code) },
                                label = { Text(name, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
