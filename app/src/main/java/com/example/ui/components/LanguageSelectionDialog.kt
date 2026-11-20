package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.TranslationManager
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
import com.example.ui.theme.GoogleSansRounded

/**
 * Full-screen Settings sub-page for selecting from 110 supported languages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionPage(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSystemSettings: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val isEmbedded = com.example.ui.screens.LocalSettingsEmbeddedInTwoPane.current
    BackHandler(enabled = !isEmbedded) { onNavigateBack() }

    val backdropState = rememberGradientBlurBackdropState()

    CompositionLocalProvider(LocalAppLanguage provides currentLanguage) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_language_page"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            GradientBlurTopBar(
                backdropState = backdropState,
                baseColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.80f),
                blurRadius = 22.dp
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = tr("lang_page_title"),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tr("lang_page_subtitle"),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                    contentDescription = tr("common_back_settings")
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .gradientBlurSource(backdropState)
                .padding(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            LanguageSelectionContent(
                currentLanguage = currentLanguage,
                onLanguageSelected = { code ->
                    onLanguageSelected(code)
                    onNavigateBack()
                },
                onOpenSystemSettings = onOpenSystemSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onOpenSystemSettings: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalAppLanguage provides currentLanguage) {
        EnableWindowBlur(blurRadiusDp = 50)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .widthIn(max = 520.dp)
                .testTag("language_selection_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = tr("lang_dialog_title_fmt", TranslationManager.supportedLanguages.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tr("lang_dialog_subtitle"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("close_language_dialog_btn")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("common_close"))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LanguageSelectionContent(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { code ->
                        onLanguageSelected(code)
                        onDismissRequest()
                    },
                    onOpenSystemSettings = onOpenSystemSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = onDismissRequest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(tr("common_done"), fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }
}

@Composable
private fun LanguageSelectionContent(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onOpenSystemSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val allLanguages = remember { TranslationManager.supportedLanguages }
    val filteredLanguages = remember(searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            allLanguages
        } else {
            allLanguages.filter { lang ->
                lang.code.lowercase().contains(query) ||
                    lang.name.lowercase().contains(query) ||
                    lang.englishName.lowercase().contains(query)
            }
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    tr("lang_search_hint"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = tr("common_search"),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("common_clear_search"))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("language_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isEmpty()) tr("lang_all_section") else tr("lang_search_section"),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${filteredLanguages.size} / ${allLanguages.size}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(8.dp))

        if (filteredLanguages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = tr("lang_no_result_title"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr("lang_no_result_sub"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("language_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredLanguages, key = { it.code }) { lang ->
                    val isSelected = currentLanguage == lang.code

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onLanguageSelected(lang.code) }
                            .testTag("lang_option_${lang.code}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = lang.code.take(2).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (lang.englishName.isNotEmpty() && lang.englishName != lang.name) {
                                    Text(
                                        text = "${lang.englishName} · ${lang.code}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = GoogleSansRounded,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = tr("lang_selected_desc"),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (onOpenSystemSettings != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenSystemSettings() }
                    .testTag("dialog_open_system_settings_btn")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = tr("lang_sys_title"),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tr("lang_sys_sub"),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = tr("lang_sys_goto_desc"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
