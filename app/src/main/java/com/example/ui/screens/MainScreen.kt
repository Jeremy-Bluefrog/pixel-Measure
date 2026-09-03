package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MeasureRecord
import com.example.logic.ShareUtility
import com.example.logic.TranslationManager
import com.example.ui.components.FloatingPillNavigationBar
import com.example.ui.components.FloatingPillNavItem
import com.example.ui.components.GradientBlurBottomBar
import com.example.ui.components.GradientBlurTopBar
import com.example.ui.components.ModernArCameraView
import com.example.ui.components.RulerComponent
import com.example.ui.components.SettingsSheet
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
    val isTorchOn by viewModel.isTorchOn.collectAsState()

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
            AnimatedVisibility(
                visible = currentMode == 1,
                enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            initialOffsetY = { -it / 2 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ),
                exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        slideOutVertically(
                            targetOffsetY = { -it / 2 },
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        )
            ) {
                GradientBlurTopBar(
                    baseColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("top_gradient_blur_bar")
                ) {
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

                            // Flashlight button
                            IconButton(
                                onClick = { viewModel.toggleTorch(context) },
                                modifier = Modifier.testTag("ruler_flashlight_button")
                            ) {
                                Icon(
                                    if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                    contentDescription = "手電筒",
                                    tint = if (isTorchOn) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface
                                )
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
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        },
        bottomBar = {
            val cameraLabel = viewModel.getString("nav_camera").ifEmpty { "相機 AR" }
            val rulerLabel = viewModel.getString("nav_ruler").ifEmpty { "螢幕尺" }

            val navItems = remember(cameraLabel, rulerLabel) {
                listOf(
                    FloatingPillNavItem(
                        id = 0,
                        label = cameraLabel,
                        icon = Icons.Rounded.GridView,
                        testTag = "segmented_button_camera"
                    ),
                    FloatingPillNavItem(
                        id = 1,
                        label = rulerLabel,
                        icon = Icons.Rounded.Straighten,
                        testTag = "segmented_button_ruler"
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .testTag("bottom_segmented_bar"),
                contentAlignment = Alignment.Center
            ) {
                FloatingPillNavigationBar(
                    selectedIndex = currentMode,
                    items = navItems,
                    onItemSelected = { id ->
                        viewModel.setMode(id)
                    },
                    modifier = Modifier.testTag("mode_segmented_button_row")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (currentMode == 0) PaddingValues(bottom = 0.dp) else innerPadding)
        ) {
            // Material 3 Shared Axis Mode Transition: Camera AR (0) <-> Screen Ruler (1)
            AnimatedContent(
                targetState = currentMode,
                transitionSpec = {
                    if (targetState > initialState) {
                        // Forward transition: Camera AR -> Ruler (Slide from right with scale and fade)
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        )).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (-fullWidth * 0.2f).toInt() },
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ) + fadeOut(
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            ) + scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            )
                        )
                    } else {
                        // Backward transition: Ruler -> Camera AR (Slide from left with scale and fade)
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> (-fullWidth * 0.2f).toInt() },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        )).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() },
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ) + fadeOut(
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            ) + scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            )
                        )
                    }
                },
                contentKey = { it },
                modifier = Modifier.fillMaxSize(),
                label = "MainModeSharedAxisTransition"
            ) { mode ->
                when (mode) {
                    0 -> {
                        ModernArCameraView(
                            viewModel = viewModel,
                            onShowHistoryClick = { showHistorySheet = true },
                            onShowSettingsClick = { showSettingsDialog = true },
                            bottomPadding = innerPadding.calculateBottomPadding()
                        )
                    }
                    1 -> {
                        RulerComponent(
                            viewModel = viewModel,
                            onShowHistoryClick = { showHistorySheet = true }
                        )
                    }
                }
            }

            // Quick Floating Share Banner when a record is newly saved
            AnimatedVisibility(
                visible = lastSavedRecord != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding() + 16.dp, start = 16.dp, end = 16.dp)
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

    // Redesigned Settings Sheet
    if (showSettingsDialog) {
        SettingsSheet(
            viewModel = viewModel,
            onDismissRequest = { showSettingsDialog = false }
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
                                    "ANGLE" -> "${Math.round(record.value)}°"
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
                            "ANGLE" -> "${Math.round(record.value)}°"
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
