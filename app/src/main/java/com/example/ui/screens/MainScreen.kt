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
import coil.compose.AsyncImage
import com.example.data.model.MeasureRecord
import com.example.logic.ShareUtility
import com.example.logic.TranslationManager
import com.example.ui.components.ModernArCameraView
import com.example.ui.components.RulerComponent
import com.example.ui.viewmodel.MeasureViewModel
import java.io.File
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
    var selectedRecordForDetail by remember { mutableStateOf<MeasureRecord?>(null) }
    val lastSavedRecord by viewModel.lastSavedRecord.collectAsState()
    val context = LocalContext.current

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
                    else -> RulerComponent(
                        viewModel = viewModel,
                        onShowHistoryClick = { showHistorySheet = true }
                    )
                }
            }

            // Quick Floating Share Banner when a record is newly saved
            AnimatedVisibility(
                visible = lastSavedRecord != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 85.dp, start = 16.dp, end = 16.dp)
            ) {
                lastSavedRecord?.let { saved ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "已儲存測量截圖與紀錄",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = saved.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        ShareUtility.shareRecord(context, saved)
                                        viewModel.clearLastSavedRecord()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("分享", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                IconButton(
                                    onClick = { viewModel.clearLastSavedRecord() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "關閉", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
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
                onSelectRecord = { record ->
                    selectedRecordForDetail = record
                },
                onClose = { showHistorySheet = false }
            )
        }
    }

    // Record Detail & Sharing Dialog
    selectedRecordForDetail?.let { record ->
        RecordDetailDialog(
            record = record,
            viewModel = viewModel,
            onDismiss = { selectedRecordForDetail = null }
        )
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
    onSelectRecord: (MeasureRecord) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
            .padding(horizontal = 20.dp, vertical = 8.dp)
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

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜尋測量紀錄與備註...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

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
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(18.dp),
                        onClick = { onSelectRecord(record) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Thumbnail / Screenshot Image Box
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val hasImage = !record.imagePath.isNullOrBlank() && File(record.imagePath).exists()
                                if (hasImage) {
                                    AsyncImage(
                                        model = File(record.imagePath!!),
                                        contentDescription = "測量截圖",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black, RoundedCornerShape(12.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = when (record.type) {
                                            "AREA" -> Icons.Rounded.SquareFoot
                                            "HEIGHT" -> Icons.Rounded.Height
                                            "VOLUME" -> Icons.Rounded.ViewInAr
                                            "CIRCLE" -> Icons.Rounded.Adjust
                                            "ANGLE" -> Icons.Rounded.Architecture
                                            "RULER" -> Icons.Rounded.Straighten
                                            else -> Icons.Rounded.LinearScale
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // Middle Info Column
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                                else -> "距離"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = record.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                val formattedVal = when (record.type) {
                                    "AREA" -> viewModel.formatArea(record.value, record.unit)
                                    "VOLUME" -> viewModel.formatVolume(record.value, record.unit)
                                    "ANGLE" -> "${DecimalFormat("0.0").format(record.value)}°"
                                    else -> viewModel.formatLength(record.value, record.unit)
                                }
                                Text(
                                    text = formattedVal,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                if (!record.notes.isNullOrBlank()) {
                                    Text(
                                        text = "📝 ${record.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = dateFormat.format(Date(record.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            // Right Action: Quick Share & Delete
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { ShareUtility.shareRecord(context, record) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Share,
                                        contentDescription = "分享",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteRecord(record) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "刪除",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
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
fun RecordDetailDialog(
    record: MeasureRecord,
    viewModel: MeasureViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var notesText by remember(record) { mutableStateOf(record.notes ?: "") }
    var isEditingNotes by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
    val hasImage = !record.imagePath.isNullOrBlank() && File(record.imagePath).exists()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "測量詳情與分享",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "關閉")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Screenshot Preview
                if (hasImage) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        AsyncImage(
                            model = File(record.imagePath!!),
                            contentDescription = "測量現場截圖",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }

                // Measurement Title & Value Banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = record.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        val formattedVal = when (record.type) {
                            "AREA" -> viewModel.formatArea(record.value, record.unit)
                            "VOLUME" -> viewModel.formatVolume(record.value, record.unit)
                            "ANGLE" -> "${DecimalFormat("0.0").format(record.value)}°"
                            else -> viewModel.formatLength(record.value, record.unit)
                        }
                        Text(
                            text = formattedVal,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "⏱️ 時間: ${dateFormat.format(Date(record.timestamp))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Notes Section (Editable)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📝 備註資訊",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isEditingNotes) {
                            TextButton(
                                onClick = { isEditingNotes = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("編輯")
                            }
                        }
                    }

                    if (isEditingNotes) {
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            placeholder = { Text("新增備註 (如：客廳沙發、餐桌長度)...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isEditingNotes = false }) {
                                Text("取消")
                            }
                            Button(
                                onClick = {
                                    viewModel.updateRecordNotes(record, notesText)
                                    isEditingNotes = false
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("儲存備註")
                            }
                        }
                    } else {
                        Text(
                            text = if (notesText.isNotBlank()) notesText else "無備註內容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (notesText.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider()

                // Share Buttons Section
                Text(
                    text = "📤 分享與匯出",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                // Primary Share: Messaging apps (LINE, WhatsApp, Telegram, etc.)
                Button(
                    onClick = { ShareUtility.shareRecord(context, record) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("分享至即時通訊 / 社群 (LINE, WhatsApp...)", fontWeight = FontWeight.Bold)
                }

                // Secondary Row: Email & PDF Report
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { ShareUtility.shareViaEmail(context, record) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Mail, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("電子郵件", fontSize = 13.sp)
                    }

                    FilledTonalButton(
                        onClick = { ShareUtility.sharePdfReport(context, record) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF 報告", fontSize = 13.sp)
                    }
                }

                // Tertiary Row: Copy text & Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { ShareUtility.copyToClipboard(context, record) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("複製文字", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.deleteRecord(record)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刪除紀錄", fontSize = 13.sp)
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

                // Multi-Sensor Fusion Settings Group
                val sensorCorrectionEnabled by viewModel.sensorCorrectionEnabled.collectAsState()
                val antiJitterEnabled by viewModel.antiJitterEnabled.collectAsState()
                val gravityAlignmentEnabled by viewModel.gravityAlignmentEnabled.collectAsState()
                val barometerFusionEnabled by viewModel.barometerFusionEnabled.collectAsState()
                val jerkRejectionEnabled by viewModel.jerkRejectionEnabled.collectAsState()
                val proximityContactEnabled by viewModel.proximityContactEnabled.collectAsState()
                val stereoParallaxEnabled by viewModel.stereoParallaxEnabled.collectAsState()

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Sensors, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("多感應器融合校正", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "運用重力、陀螺儀、氣壓計、近接感應器與雙鏡頭視差即時校準",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = sensorCorrectionEnabled,
                            onCheckedChange = { viewModel.setSensorCorrectionEnabled(it) }
                        )
                    }

                    if (sensorCorrectionEnabled) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("陀螺儀防手震濾波", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = antiJitterEnabled,
                                    onCheckedChange = { viewModel.setAntiJitterEnabled(it) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("重力向量垂直/水平校準", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = gravityAlignmentEnabled,
                                    onCheckedChange = { viewModel.setGravityAlignmentEnabled(it) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("氣壓計高度融合", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = barometerFusionEnabled,
                                    onCheckedChange = { viewModel.setBarometerFusionEnabled(it) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("近接感應器貼面零點校準", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = proximityContactEnabled,
                                    onCheckedChange = { viewModel.setProximityContactEnabled(it) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("雙鏡頭同步視差尺度校正", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = stereoParallaxEnabled,
                                    onCheckedChange = { viewModel.setStereoParallaxEnabled(it) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("突發加速度防誤觸", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = jerkRejectionEnabled,
                                    onCheckedChange = { viewModel.setJerkRejectionEnabled(it) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
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
