package com.example.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.example.logic.TranslationManager

val LocalAppLanguage = compositionLocalOf { "zh-TW" }

@Composable
@ReadOnlyComposable
fun tr(key: String): String {
    return TranslationManager.getString(key, LocalAppLanguage.current)
}

@Composable
@ReadOnlyComposable
fun tr(key: String, vararg args: Any): String {
    return TranslationManager.formatString(key, LocalAppLanguage.current, *args)
}
