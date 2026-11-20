package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.MeasureRecord
import com.example.logic.ShareUtility
import com.example.logic.TranslationManager
import com.example.ui.adaptive.AdaptiveWindowState
import com.example.ui.adaptive.rememberLocalHingeTracker
import com.example.ui.components.EnableWindowBlur
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
import com.example.ui.theme.ExpressiveCookie9Shape
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.GoogleSansRounded
import com.example.ui.theme.getGroupedPosition
import com.example.ui.theme.groupedItemShape
import com.example.ui.theme.rememberExpressiveConnectedShape
import com.example.ui.viewmodel.MeasureViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private object VideoThumbnailCache {
    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024 // 8 MB cap
    val cache = object : android.util.LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
    }
}

@Composable
private fun rememberVideoThumbnail(videoPath: String?): Bitmap? {
    val context = LocalContext.current
    var thumbBitmap by remember(videoPath) {
        mutableStateOf(videoPath?.let { VideoThumbnailCache.cache.get(it) })
    }
    LaunchedEffect(videoPath) {
        if (videoPath.isNullOrBlank()) {
            thumbBitmap = null
            return@LaunchedEffect
        }
        VideoThumbnailCache.cache.get(videoPath)?.let { cached ->
            thumbBitmap = cached
            return@LaunchedEffect
        }
        val bmp = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                    retriever.setDataSource(context, Uri.parse(videoPath))
                } else {
                    val f = File(videoPath)
                    if (!f.exists()) return@withContext null
                    retriever.setDataSource(f.absolutePath)
                }
                val raw = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        200_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        160,
                        160
                    ) ?: retriever.frameAtTime
                } else {
                    retriever.getFrameAtTime(200_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                }
                if (raw != null && (raw.width > 160 || raw.height > 160)) {
                    val scale = 160f / maxOf(raw.width, raw.height)
                    val sw = (raw.width * scale).toInt().coerceAtLeast(1)
                    val sh = (raw.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(raw, sw, sh, true).also { scaled ->
                        if (scaled !== raw) raw.recycle()
                    }
                } else {
                    raw
                }
            } catch (_: Throwable) {
                null
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }
        }
        if (bmp != null) {
            VideoThumbnailCache.cache.put(videoPath, bmp)
        }
        thumbBitmap = bmp
    }
    return thumbBitmap
}

private fun resolvePlayableVideoUri(rawPath: String?): Uri? {
    if (rawPath.isNullOrBlank()) return null
    return if (rawPath.startsWith("content://") || rawPath.startsWith("file://")) {
        Uri.parse(rawPath)
    } else {
        val f = File(rawPath)
        if (f.exists()) Uri.fromFile(f) else null
    }
}

/**
 * Large-screen & Foldable List-Detail workspace for Measurement History.
 * Left pane: Search, category chips, stats, and selectable record cards.
 * Right pane: Live detail view with inline video/photo preview, notes editor, and share/export tools.
 * Automatically respects physical vertical hinge bounds in Book posture.
 */
@Composable
fun HistoryTwoPaneWorkspace(
    records: List<MeasureRecord>,
    selectedRecordId: Int?,
    onSelectRecord: (MeasureRecord) -> Unit,
    onClearSelection: () -> Unit,
    onClose: () -> Unit,
    viewModel: MeasureViewModel,
    adaptiveState: AdaptiveWindowState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val (hingeTrackerModifier, hingeMetrics) = rememberLocalHingeTracker(adaptiveState)

    val arRecords = remember(records) {
        records.filter { it.type != "RULER" && it.type != "LEVEL" }
    }

    val activeRecord = remember(selectedRecordId, arRecords) {
        arRecords.find { it.id == selectedRecordId } ?: arRecords.firstOrNull()
    }

    LaunchedEffect(activeRecord?.id, selectedRecordId) {
        if (activeRecord != null && selectedRecordId != activeRecord.id) {
            onSelectRecord(activeRecord)
        } else if (activeRecord == null && selectedRecordId != null) {
            onClearSelection()
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides currentLang) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
            .fillMaxSize()
            .then(hingeTrackerModifier)
            .testTag("history_two_pane_workspace")
    ) {
        val hasHingeSplit = adaptiveState.isBook && hingeMetrics.containerWidthPx > 0
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal)
                )
        ) {
            // Left Pane: History List
            val listPaneModifier = if (hasHingeSplit) {
                Modifier
                    .width(hingeMetrics.leftPaneWidthDp(density, fallbackFraction = 0.44f))
                    .fillMaxHeight()
            } else {
                Modifier
                    .weight(0.43f)
                    .widthIn(min = 300.dp, max = 480.dp)
                    .fillMaxHeight()
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = listPaneModifier.testTag("history_list_pane")
            ) {
                HistorySheetContent(
                    records = records,
                    viewModel = viewModel,
                    onSelectRecord = onSelectRecord,
                    onClose = onClose,
                    selectedRecordId = activeRecord?.id,
                    isTwoPaneMode = true
                )
            }

            // Hinge Spacer or Subtle Flat Divider
            if (hasHingeSplit) {
                Spacer(
                    modifier = Modifier
                        .width(hingeMetrics.verticalHingeGapDp(density))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .testTag("history_hinge_gap")
                )
            } else {
                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }

            // Right Pane: Measurement Detail
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .testTag("history_detail_pane"),
                contentAlignment = Alignment.TopCenter
            ) {
                if (activeRecord != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = ExpressiveCookie9Shape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Analytics,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = tr("hist_detail_title"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = tr("hist_detail_subtitle"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("history_two_pane_close_btn")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = tr("hist_close_desc"))
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        RecordDetailPaneContent(
                            record = activeRecord,
                            viewModel = viewModel,
                            onAfterDelete = {
                                val next = arRecords.firstOrNull { it.id != activeRecord.id }
                                if (next != null) {
                                    onSelectRecord(next)
                                } else {
                                    onClearSelection()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // Inviting Empty Detail State
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = ExpressiveCookie9Shape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.size(84.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.SquareFoot,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                        Text(
                            text = tr("hist_select_left_title"),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tr("hist_select_left_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
fun HistorySheetContent(
    records: List<MeasureRecord>,
    viewModel: MeasureViewModel,
    onSelectRecord: (MeasureRecord) -> Unit,
    onClose: () -> Unit,
    selectedRecordId: Int? = null,
    isTwoPaneMode: Boolean = false
) {
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAppLanguage provides currentLang) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("ALL") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Exclude Screen Ruler and Spirit Level entries cleanly
    val arRecords = remember(records) {
        records.filter { it.type != "RULER" && it.type != "LEVEL" }
    }

    val categories = remember(currentLang) {
        listOf(
            "ALL" to TranslationManager.getString("hist_filter_all", currentLang),
            "DISTANCE" to TranslationManager.getString("hist_filter_distance", currentLang),
            "CIRCLE" to TranslationManager.getString("hist_filter_circle", currentLang),
            "AREA" to TranslationManager.getString("hist_filter_area", currentLang),
            "HEIGHT" to TranslationManager.getString("hist_filter_height", currentLang),
            "VOLUME" to TranslationManager.getString("hist_filter_volume", currentLang),
            "ANGLE" to TranslationManager.getString("hist_filter_angle", currentLang)
        )
    }

    val filteredRecords = remember(arRecords, searchQuery, selectedCategory) {
        arRecords.filter { record ->
            val matchesCategory = when (selectedCategory) {
                "ALL" -> true
                "DISTANCE" -> record.type != "CIRCLE" && record.type != "AREA" && record.type != "HEIGHT" && record.type != "VOLUME" && record.type != "ANGLE"
                else -> record.type == selectedCategory
            }
            val matchesSearch = searchQuery.isBlank() ||
                record.title.contains(searchQuery, ignoreCase = true) ||
                (record.notes?.contains(searchQuery, ignoreCase = true) == true)
            matchesCategory && matchesSearch
        }
    }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isTwoPaneMode) Modifier.fillMaxHeight() else Modifier)
            .padding(horizontal = 20.dp, vertical = if (isTwoPaneMode) 14.dp else 8.dp)
            .testTag("history_sheet_content")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr("hist_header_fmt", arRecords.size),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (arRecords.isNotEmpty()) {
                    IconButton(
                        onClick = { ShareUtility.exportAllRecordsCsv(context, arRecords, currentLang) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FileUpload,
                            contentDescription = tr("hist_export_csv"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    TextButton(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("clear_all"))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(tr("hist_search_hint")) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("common_clear_search"))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (arRecords.isNotEmpty()) {
            val totalCount = arRecords.size
            val sumDistance = remember(arRecords) {
                arRecords
                    .filter { it.type == "DISTANCE" || it.type == "CAM" || it.type.isEmpty() }
                    .sumOf { it.value }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("hist_stats_title"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            tr("hist_stats_summary_fmt", totalCount, String.format(Locale.getDefault(), "%.2f", sumDistance)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { ShareUtility.exportRecordsAsCsvText(context, arRecords, currentLang) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr("hist_export_report"), fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Unified Connected Category Filter Button Group
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_connected_filter_group")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp)
            ) {
                categories.forEachIndexed { idx, (code, label) ->
                    val isSelected = selectedCategory == code
                    val pillInteraction = remember { MutableInteractionSource() }
                    val isPressed by pillInteraction.collectIsPressedAsState()
                    val pillShape = rememberExpressiveConnectedShape(
                        index = idx,
                        count = categories.size,
                        isActive = isSelected,
                        isPressed = isPressed,
                        outerCorner = 18.dp,
                        innerRestingCorner = 3.dp,
                        innerActiveCorner = 12.dp
                    )
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = ExpressiveMotion.effectsDefault(),
                        label = "catPillBg_$code"
                    )
                    val pillFg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = ExpressiveMotion.effectsDefault(),
                        label = "catPillFg_$code"
                    )

                    Surface(
                        color = pillBg,
                        contentColor = pillFg,
                        shape = pillShape,
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(pillShape)
                            .clickable(
                                interactionSource = pillInteraction,
                                indication = ripple(bounded = true)
                            ) { selectedCategory = code }
                            .testTag("history_filter_${code.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = pillFg,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isTwoPaneMode) Modifier.weight(1f) else Modifier.height(210.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = ExpressiveCookie9Shape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.HistoryToggleOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (arRecords.isEmpty()) tr("hist_empty_title") else tr("hist_no_match_title"),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (arRecords.isEmpty()) tr("hist_empty_desc") else tr("hist_no_match_desc"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (arRecords.isNotEmpty() && (searchQuery.isNotBlank() || selectedCategory != "ALL")) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = {
                                searchQuery = ""
                                selectedCategory = "ALL"
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("history_reset_filters_button")
                        ) {
                            Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("hist_reset_filters"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isTwoPaneMode) Modifier.weight(1f) else Modifier.heightIn(max = 440.dp)),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(filteredRecords, key = { _, record -> record.id }) { index, record ->
                    val position = getGroupedPosition(index, filteredRecords.size)
                    val isSelectedInPane = isTwoPaneMode && selectedRecordId == record.id
                    val resolvedVideoPath = record.videoPath
                        ?: record.imagePath?.takeIf { it.endsWith(".mp4", ignoreCase = true) }
                    val isVideo = !resolvedVideoPath.isNullOrBlank()
                    val imageFile = record.imagePath
                        ?.takeIf { !it.endsWith(".mp4", ignoreCase = true) }
                        ?.let { File(it) }
                        ?.takeIf { it.exists() }
                    val videoFrameThumb = if (isVideo && imageFile == null) {
                        rememberVideoThumbnail(resolvedVideoPath)
                    } else null

                    val cardColor = if (isSelectedInPane) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                    val cardBorder = if (isSelectedInPane) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }

                    Surface(
                        color = cardColor,
                        border = cardBorder,
                        shape = groupedItemShape(position, outerCorner = 20.dp, innerCorner = 4.dp),
                        onClick = { onSelectRecord(record) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_record_item_${record.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Photo / Video Thumbnail Box
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    imageFile != null -> {
                                        AsyncImage(
                                            model = imageFile,
                                            contentDescription = if (isVideo) tr("hist_thumb_video") else tr("hist_thumb_photo"),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    videoFrameThumb != null -> {
                                        val thumbImageBitmap = remember(videoFrameThumb) { videoFrameThumb.asImageBitmap() }
                                        Image(
                                            bitmap = thumbImageBitmap,
                                            contentDescription = tr("hist_thumb_video"),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = when {
                                                isVideo -> Icons.Rounded.Videocam
                                                record.type == "AREA" -> Icons.Rounded.SquareFoot
                                                record.type == "HEIGHT" -> Icons.Rounded.Height
                                                record.type == "VOLUME" -> Icons.Rounded.ViewInAr
                                                record.type == "CIRCLE" -> Icons.Rounded.Adjust
                                                record.type == "ANGLE" -> Icons.Rounded.Architecture
                                                else -> Icons.Rounded.LinearScale
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                if (isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.62f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = tr("hist_play_video"),
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = tr("hist_badge_video"),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                } else if (imageFile != null) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhotoCamera,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
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
                                            text = when {
                                                isVideo -> tr("hist_badge_video")
                                                record.type == "AREA" -> tr("hist_badge_area")
                                                record.type == "HEIGHT" -> tr("hist_badge_height")
                                                record.type == "VOLUME" -> tr("hist_badge_volume")
                                                record.type == "CIRCLE" -> tr("hist_badge_circle")
                                                record.type == "ANGLE" -> tr("hist_badge_angle")
                                                record.value == 0.0 && imageFile != null -> tr("hist_badge_photo")
                                                else -> tr("hist_badge_distance")
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

                                val formattedVal = when {
                                    record.value == 0.0 && isVideo -> tr("hist_title_video")
                                    record.value == 0.0 && imageFile != null -> tr("hist_title_photo")
                                    record.type == "AREA" -> viewModel.formatArea(record.value, record.unit)
                                    record.type == "VOLUME" -> viewModel.formatVolume(record.value, record.unit)
                                    record.type == "ANGLE" -> "${Math.round(record.value)}°"
                                    else -> viewModel.formatLength(record.value, record.unit)
                                }
                                Text(
                                    text = formattedVal,
                                    style = ExpressiveTypography.displayNumeralMedium,
                                    fontSize = 19.sp,
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

                            // Right Action: Copy, Share & Delete (48dp touch targets)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        ShareUtility.copyToClipboard(context, record, currentLang)
                                        viewModel.showToast(
                                            TranslationManager.formatString("hist_copied_title_fmt", currentLang, record.title)
                                        )
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = tr("hist_copy_text"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { ShareUtility.shareRecord(context, record, currentLang) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Share,
                                        contentDescription = tr("common_share"),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteRecord(record) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = tr("common_delete"),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isTwoPaneMode) {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(tr("hist_clear_title"), fontWeight = FontWeight.Bold) },
            text = { Text(tr("hist_clear_msg")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllRecords()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(tr("hist_clear_confirm_btn"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(tr("cancel"))
                }
            }
        )
    }
    }
}

@Composable
fun RecordDetailDialog(
    record: MeasureRecord,
    viewModel: MeasureViewModel,
    onDismiss: () -> Unit
) {
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAppLanguage provides currentLang) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp),
        title = {
            EnableWindowBlur(blurRadiusDp = 50)
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
                        if (!record.videoPath.isNullOrBlank()) Icons.Rounded.Videocam else Icons.Rounded.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = tr("hist_detail_title"),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = tr("common_close"))
                }
            }
        },
        text = {
            RecordDetailPaneContent(
                record = record,
                viewModel = viewModel,
                onAfterDelete = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("common_done"))
            }
        }
    )
    }
}

@Composable
fun RecordDetailPaneContent(
    record: MeasureRecord,
    viewModel: MeasureViewModel,
    onAfterDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    var notesText by remember(record.id, record.notes) { mutableStateOf(record.notes ?: "") }
    var isEditingNotes by remember(record.id) { mutableStateOf(false) }
    var showFullscreenPhoto by remember { mutableStateOf(false) }
    var isVideoPlaying by remember(record.id) { mutableStateOf(true) }
    var videoPlaybackError by remember(record.id) { mutableStateOf(false) }
    var videoViewRef by remember(record.id) { mutableStateOf<VideoView?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
    val resolvedVideoPath = record.videoPath
        ?: record.imagePath?.takeIf { it.endsWith(".mp4", ignoreCase = true) }
    val videoUri = remember(resolvedVideoPath) { resolvePlayableVideoUri(resolvedVideoPath) }
    val imageFile = remember(record.imagePath) {
        record.imagePath
            ?.takeIf { !it.endsWith(".mp4", ignoreCase = true) }
            ?.let { File(it) }
            ?.takeIf { it.exists() }
    }

    DisposableEffect(videoUri) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Throwable) {}
            videoViewRef = null
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Inline Video Player or Clickable Photo Preview
        if (videoUri != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("detail_video_player")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (videoPlaybackError) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Rounded.VideocamOff,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = tr("hist_video_preview_err"),
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            key(videoUri) {
                                AndroidView(
                                    factory = { ctx ->
                                        FrameLayout(ctx).apply {
                                            val vv = VideoView(ctx).apply {
                                                layoutParams = FrameLayout.LayoutParams(
                                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                                    android.view.Gravity.CENTER
                                                )
                                                setVideoURI(videoUri)
                                                setOnPreparedListener { mp ->
                                                    mp.isLooping = true
                                                    if (isVideoPlaying) start()
                                                }
                                                setOnErrorListener { _, _, _ ->
                                                    videoPlaybackError = true
                                                    isVideoPlaying = false
                                                    true
                                                }
                                            }
                                            videoViewRef = vv
                                            addView(vv)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Center/Bottom Play-Pause overlay
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.48f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalIconButton(
                                    onClick = {
                                        val vv = videoViewRef
                                        if (vv != null && !videoPlaybackError) {
                                            if (vv.isPlaying) {
                                                vv.pause()
                                                isVideoPlaying = false
                                            } else {
                                                vv.start()
                                                isVideoPlaying = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        if (isVideoPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isVideoPlaying) tr("hist_pause_video") else tr("hist_play_video")
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        if (!videoPlaybackError) {
                                            videoViewRef?.let { vv ->
                                                vv.seekTo(0)
                                                vv.start()
                                                isVideoPlaying = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Replay, contentDescription = tr("hist_replay_video"))
                                }
                            }

                            TextButton(
                                onClick = {
                                    try {
                                        val shareableUri = if (resolvedVideoPath != null && !resolvedVideoPath.startsWith("content://")) {
                                            androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                File(resolvedVideoPath)
                                            )
                                        } else videoUri
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(shareableUri, "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(viewIntent)
                                    } catch (_: Throwable) {
                                        viewModel.showToast(TranslationManager.getString("hist_no_video_player", lang))
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("hist_fullscreen_play"), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else if (imageFile != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showFullscreenPhoto = true }
                    .testTag("detail_photo_preview")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = tr("hist_photo_desc"),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.ZoomIn, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text(tr("hist_tap_enlarge"), color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
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

                val formattedVal = when {
                    record.value == 0.0 && videoUri != null -> tr("hist_title_video")
                    record.value == 0.0 && imageFile != null -> tr("hist_title_photo")
                    record.type == "AREA" -> viewModel.formatArea(record.value, record.unit)
                    record.type == "VOLUME" -> viewModel.formatVolume(record.value, record.unit)
                    record.type == "ANGLE" -> "${Math.round(record.value)}°"
                    else -> viewModel.formatLength(record.value, record.unit)
                }
                Text(
                    text = formattedVal,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = tr("hist_time_fmt", dateFormat.format(Date(record.timestamp))),
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
                    text = tr("hist_notes_header"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (!isEditingNotes) {
                    TextButton(
                        onClick = { isEditingNotes = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(tr("common_edit"))
                    }
                }
            }

            if (isEditingNotes) {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    placeholder = { Text(tr("hist_notes_placeholder")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { isEditingNotes = false }) {
                        Text(tr("cancel"))
                    }
                    Button(
                        onClick = {
                            viewModel.updateRecordNotes(record, notesText)
                            isEditingNotes = false
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(tr("hist_save_notes"))
                    }
                }
            } else {
                Text(
                    text = if (notesText.isNotBlank()) notesText else tr("hist_no_notes"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notesText.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        HorizontalDivider()

        // Share Buttons Section
        Text(
            text = tr("hist_share_header"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = { ShareUtility.shareRecord(context, record, lang) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (videoUri != null) tr("hist_share_video_data") else tr("hist_share_photo_report"), fontWeight = FontWeight.Bold)
        }

        // Secondary Row: Email & PDF Report
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { ShareUtility.shareViaEmail(context, record, lang) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.Mail, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("hist_email"), fontSize = 13.sp)
            }

            FilledTonalButton(
                onClick = { ShareUtility.sharePdfReport(context, record, lang) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("hist_pdf_report"), fontSize = 13.sp)
            }
        }

        // Tertiary Row: Copy text & Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    ShareUtility.copyToClipboard(context, record, lang)
                    viewModel.showToast(TranslationManager.getString("hist_copied_text_toast", lang))
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("hist_copy_text"), fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    viewModel.deleteRecord(record)
                    onAfterDelete()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("hist_delete_record"), fontSize = 13.sp)
            }
        }
    }

    if (showFullscreenPhoto && imageFile != null) {
        Dialog(
            onDismissRequest = { showFullscreenPhoto = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { showFullscreenPhoto = false }
            ) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = tr("hist_fullscreen_photo_desc"),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                FilledTonalIconButton(
                    onClick = { showFullscreenPhoto = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(48.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = tr("hist_close_fullscreen_desc"))
                }
            }
        }
    }
}
