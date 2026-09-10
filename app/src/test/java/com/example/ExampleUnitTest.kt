package com.example

import com.example.logic.TranslationManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun `verify at least 100 languages supported`() {
    val languages = TranslationManager.supportedLanguages
    assertTrue("Should support at least 100 languages", languages.size >= 100)
    assertEquals(110, languages.size)

    // Verify all codes are non-empty and unique
    val codes = languages.map { it.code }
    assertEquals("Language codes must be unique", codes.size, codes.toSet().size)
    languages.forEach { lang ->
      assertTrue("Language code should not be blank: ${lang.code}", lang.code.isNotBlank())
      assertTrue("Language name should not be blank: ${lang.name}", lang.name.isNotBlank())
    }
  }

  @Test
  fun `verify translations lookup and fallback`() {
    // Chinese (Traditional)
    assertEquals("相機 AR 測量儀", TranslationManager.getString("app_title", "zh-TW"))
    assertEquals("相機 AR", TranslationManager.getString("nav_camera", "zh-TW"))

    // English
    assertEquals("Camera AR Measure", TranslationManager.getString("app_title", "en"))
    assertEquals("Camera AR", TranslationManager.getString("nav_camera", "en"))

    // Japanese
    assertEquals("カメラ AR メジャー", TranslationManager.getString("app_title", "ja"))

    // French
    assertEquals("Mètre Caméra AR", TranslationManager.getString("app_title", "fr"))

    // German
    assertEquals("Kamera AR Messgerät", TranslationManager.getString("app_title", "de"))

    // Portuguese (Brazil)
    assertEquals("Trena AR & Régua Digital", TranslationManager.getString("app_title", "pt-BR"))

    // Latin
    assertEquals("Camera AR Mensura & Regula", TranslationManager.getString("app_title", "la"))

    // Fallback on missing key
    val fallbackResult = TranslationManager.getString("non_existent_key_12345", "ja")
    assertEquals("non_existent_key_12345", fallbackResult)
  }

  @Test
  fun `verify system locale matching logic`() {
    // Exact matches
    assertEquals("zh-TW", TranslationManager.matchLanguageCode("zh-TW"))
    assertEquals("zh-CN", TranslationManager.matchLanguageCode("zh-CN"))
    assertEquals("zh-HK", TranslationManager.matchLanguageCode("zh-HK"))
    assertEquals("en-US", TranslationManager.matchLanguageCode("en-US"))
    assertEquals("en-GB", TranslationManager.matchLanguageCode("en-GB"))
    assertEquals("ja", TranslationManager.matchLanguageCode("ja"))

    // Subtag and script matches
    assertEquals("zh-TW", TranslationManager.matchLanguageCode("zh-Hant-TW"))
    assertEquals("zh-CN", TranslationManager.matchLanguageCode("zh-Hans-CN"))
    assertEquals("zh-HK", TranslationManager.matchLanguageCode("zh-Hant-HK"))
    assertEquals("en", TranslationManager.matchLanguageCode("en-AU"))
    assertEquals("ja", TranslationManager.matchLanguageCode("ja-JP"))
    assertEquals("ko", TranslationManager.matchLanguageCode("ko-KR"))
    assertEquals("fr-CA", TranslationManager.matchLanguageCode("fr-CA"))
    assertEquals("fr", TranslationManager.matchLanguageCode("fr-FR"))
    assertEquals("es-419", TranslationManager.matchLanguageCode("es-MX"))
    assertEquals("es", TranslationManager.matchLanguageCode("es-ES"))
    assertEquals("pt-BR", TranslationManager.matchLanguageCode("pt-BR"))
    assertEquals("pt", TranslationManager.matchLanguageCode("pt-PT"))
    assertEquals("de", TranslationManager.matchLanguageCode("de-DE"))

    // Underscore notation (e.g. Android Locale.toString())
    assertEquals("zh-TW", TranslationManager.matchLanguageCode("zh_TW"))
    assertEquals("en-US", TranslationManager.matchLanguageCode("en_US"))

    // Fallbacks
    assertEquals("zh-TW", TranslationManager.matchLanguageCode(null))
    assertEquals("zh-TW", TranslationManager.matchLanguageCode(""))
    assertEquals("zh-TW", TranslationManager.matchLanguageCode("unknown-locale"))
  }

  @Test
  fun `verify system language settings strings`() {
    val titleZh = TranslationManager.getString("system_lang_title", "zh-TW")
    assertTrue(titleZh.contains("系統"))

    val titleEn = TranslationManager.getString("system_lang_title", "en")
    assertTrue(titleEn.contains("System"))

    val descZh = TranslationManager.getString("system_lang_desc", "zh-TW")
    assertTrue(descZh.contains("Android"))

    val descEn = TranslationManager.getString("system_lang_desc", "en")
    assertTrue(descEn.contains("Android"))
  }
}

