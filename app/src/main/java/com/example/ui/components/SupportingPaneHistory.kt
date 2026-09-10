package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MeasureRecord
import com.example.ui.screens.HistorySheetContent
import com.example.ui.viewmodel.MeasureViewModel

/**
 * Material 3 Supporting Pane for displaying measurement records history
 * side-by-side with the primary workspace on expanded screens (tablets & foldables).
 */
@Composable
fun SupportingPaneHistory(
    records: List<MeasureRecord>,
    viewModel: MeasureViewModel,
    onSelectRecord: (MeasureRecord) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(400.dp)
            .fillMaxHeight()
            .testTag("supporting_pane_history"),
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HistorySheetContent(
                records = records,
                viewModel = viewModel,
                onSelectRecord = onSelectRecord,
                onClose = onClose
            )
        }
    }
}
