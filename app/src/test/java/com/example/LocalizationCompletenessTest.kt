package com.example

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MeasureRecord
import com.example.logic.ShareUtility
import com.example.logic.TranslationManager
import com.example.ui.components.RulerComponent
import com.example.ui.screens.HistorySheetContent
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MeasureViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW-w411dp-h891dp")
class LocalizationCompletenessTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val printfPlaceholderRegex =
        Regex("%(?:\\d+\\$)?[\\-#+0,(]*\\d*(?:\\.\\d+)?[sdfox]")

    private val cjkRegex = Regex("[\\u4e00-\\u9fff]")

    private fun extractPlaceholderSignature(template: String): List<String> {
        val cleaned = template.replace("%%", "")
        return printfPlaceholderRegex.findAll(cleaned).map { match ->
            val token = match.value
            val typeChar = token.last().lowercaseChar()
            val argPos = Regex("^%(\\d+)\\$").find(token)?.groupValues?.get(1)
            if (argPos != null) "$argPos:$typeChar" else typeChar.toString()
        }.toList().sorted()
    }

    @Test
    fun `supportedLanguages has 110 valid unique entries`() {
        val languages = TranslationManager.supportedLanguages
        assertEquals(110, languages.size)
        val codes = languages.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
        languages.forEach { lang ->
            assertTrue("Blank language code", lang.code.isNotBlank())
            assertTrue("Blank display name for ${lang.code}", lang.name.isNotBlank())
        }
    }

    @Test
    fun `core locales zh-TW zh-CN and en contain every key with matching format specifiers`() {
        val allKeys = TranslationManager.allKeys()
        assertTrue("Expected non-empty translation key set", allKeys.size >= 400)

        val allMap = TranslationManager.allTranslationsMap()
        val zhTw = allMap["zh-TW"] ?: error("Missing zh-TW map")
        val zhCn = allMap["zh-CN"] ?: error("Missing zh-CN map")
        val en = allMap["en"] ?: error("Missing en map")

        listOf("zh-TW" to zhTw, "zh-CN" to zhCn, "en" to en).forEach { (locale, map) ->
            val missing = allKeys.filter { key -> map[key].isNullOrBlank() }
            assertTrue("Missing or blank keys in $locale: $missing", missing.isEmpty())
        }

        allKeys.forEach { key ->
            val twTemplate = zhTw.getValue(key)
            val cnTemplate = zhCn.getValue(key)
            val enTemplate = en.getValue(key)

            val twSig = extractPlaceholderSignature(twTemplate)
            val cnSig = extractPlaceholderSignature(cnTemplate)
            val enSig = extractPlaceholderSignature(enTemplate)

            assertEquals("Placeholder mismatch between zh-TW and zh-CN for $key", twSig, cnSig)
            assertEquals("Placeholder mismatch between zh-TW and en for $key", twSig, enSig)
        }
    }

    @Test
    fun `all 110 supported languages resolve every key cleanly without raw key leakage`() {
        val allKeys = TranslationManager.allKeys()
        TranslationManager.supportedLanguages.forEach { lang ->
            allKeys.forEach { key ->
                val resolved = TranslationManager.getString(key, lang.code)
                assertTrue("Blank translation for key=$key lang=${lang.code}", resolved.isNotBlank())
                assertNotEquals("Raw key returned for key=$key lang=${lang.code}", key, resolved)
            }
        }
    }

    @Test
    fun `cascade fallback and matchLanguageCode resolve regional tags accurately`() {
        // Explicit BCP-47 matcher checks
        assertEquals("zh-HK", TranslationManager.matchLanguageCode("zh-Hant-HK"))
        assertEquals("zh-TW", TranslationManager.matchLanguageCode("zh-Hant-TW"))
        assertEquals("zh-CN", TranslationManager.matchLanguageCode("zh-Hans-CN"))
        assertEquals("en", TranslationManager.matchLanguageCode("en-AU"))
        assertEquals("zh-TW", TranslationManager.matchLanguageCode("xx-UNKNOWN"))

        // Cascade lookup checks: zh-HK falls back to zh-TW when a key is not overridden in zh-HK
        val allMap = TranslationManager.allTranslationsMap()
        val keyNotInZhHk = TranslationManager.allKeys().first { allMap["zh-HK"]?.get(it) == null }
        assertEquals(
            TranslationManager.getString(keyNotInZhHk, "zh-TW"),
            TranslationManager.getString(keyNotInZhHk, "zh-HK")
        )

        // Regional English code en-AU cascades via base "en"
        assertEquals(
            TranslationManager.getString("history_title", "en"),
            TranslationManager.getString("history_title", "en-AU")
        )

        // Completely unknown language code falls back cleanly to English / zh-TW without raw key
        val unknownResolved = TranslationManager.getString("history_title", "zz-NONEXISTENT")
        assertNotEquals("history_title", unknownResolved)
        assertTrue(unknownResolved.isNotBlank())
    }

    @Test
    fun `ShareUtility formatTextReport localizes headers and mode labels without CJK leakage in English`() {
        val sampleRecord = MeasureRecord(
            id = 1,
            title = "Desk Width",
            value = 1.45,
            unit = "m",
            type = "CAM",
            notes = "Sample",
            pointsData = "0.0,0.0,-1.0;1.45,0.0,-1.0"
        )

        val enReport = ShareUtility.formatTextReport(sampleRecord, "en")
        assertTrue(enReport.contains("Digital AR Measure Report"))
        assertTrue(enReport.contains("Camera AR Distance"))
        assertTrue(enReport.contains("Desk Width"))
        assertTrue(enReport.contains("Sample"))
        assertFalse("Unexpected CJK leakage in English report:\n$enReport", cjkRegex.containsMatchIn(enReport))

        val twReport = ShareUtility.formatTextReport(sampleRecord, "zh-TW")
        assertTrue(twReport.contains("數位 AR 測量報告"))
        assertTrue(twReport.contains("相機 AR 距離測量"))
        assertTrue(cjkRegex.containsMatchIn(twReport))
    }

    @Test
    fun `WelcomeScreen updates rendered copy dynamically when switching languages`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        vm.setLanguage("zh-TW")

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                WelcomeScreen(viewModel = vm, onStartMeasuring = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("歡迎使用空間測量工具").assertIsDisplayed()

        vm.setLanguage("en")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Welcome to Spatial Measure").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("歡迎使用空間測量工具").assertCountEquals(0)

        vm.setLanguage("ja")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("空間測定ツールへようこそ").assertIsDisplayed()
    }

    @Test
    fun `SettingsScreen updates rendered labels dynamically when switching languages`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        vm.setLanguage("zh-TW")

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                SettingsScreen(viewModel = vm, onNavigateBack = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("設定").assertIsDisplayed()
        composeTestRule.onNodeWithText("日間與夜間外觀").assertIsDisplayed()

        vm.setLanguage("en")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Light & Dark Theme").assertIsDisplayed()

        vm.setLanguage("ja")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ライト / ダークテーマ").assertIsDisplayed()
    }

    @Test
    fun `HistorySheetContent updates rendered labels dynamically when switching languages`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        vm.setLanguage("zh-TW")

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                HistorySheetContent(
                    records = emptyList(),
                    viewModel = vm,
                    onSelectRecord = {},
                    onClose = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("AR 歷史測量紀錄 (0)").assertIsDisplayed()

        vm.setLanguage("en")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("AR History (0)").assertIsDisplayed()

        vm.setLanguage("ja")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("AR 測定履歴 (0)").assertIsDisplayed()
    }

    @Test
    fun `RulerComponent updates edit button label dynamically when switching languages`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        vm.setLanguage("zh-TW")

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                RulerComponent(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("編輯").assertIsDisplayed()

        vm.setLanguage("en")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()

        vm.setLanguage("ja")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("編集").assertIsDisplayed()
    }
}
