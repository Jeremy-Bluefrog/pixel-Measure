package com.example.logic

import com.example.logic.translations.TranslationDataCore
import com.example.logic.translations.TranslationDataExtended1
import com.example.logic.translations.TranslationDataExtended2
import com.example.logic.translations.TranslationDataMajor1
import com.example.logic.translations.TranslationDataMajor2
import java.util.Locale

object TranslationManager {
    data class Language(val code: String, val name: String, val englishName: String = "")

    val supportedLanguages = listOf(
        Language("zh-TW", "繁體中文", "Traditional Chinese"),
        Language("zh-CN", "简体中文", "Simplified Chinese"),
        Language("zh-HK", "繁體中文 (香港)", "Chinese (Hong Kong)"),
        Language("en", "English", "English"),
        Language("en-US", "English (US)", "English (United States)"),
        Language("en-GB", "English (UK)", "English (United Kingdom)"),
        Language("ja", "日本語", "Japanese"),
        Language("ko", "한국어", "Korean"),
        Language("es", "Español", "Spanish"),
        Language("es-419", "Español (Latinoamérica)", "Spanish (Latin America)"),
        Language("fr", "Français", "French"),
        Language("fr-CA", "Français (Canada)", "French (Canada)"),
        Language("de", "Deutsch", "German"),
        Language("it", "Italiano", "Italian"),
        Language("pt", "Português", "Portuguese"),
        Language("pt-BR", "Português (Brasil)", "Portuguese (Brazil)"),
        Language("ru", "Русский", "Russian"),
        Language("ar", "العربية", "Arabic"),
        Language("hi", "हिन्दी", "Hindi"),
        Language("bn", "বাংলা", "Bengali"),
        Language("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        Language("jv", "Basa Jawa", "Javanese"),
        Language("vi", "Tiếng Việt", "Vietnamese"),
        Language("tr", "Türkçe", "Turkish"),
        Language("th", "ไทย", "Thai"),
        Language("id", "Bahasa Indonesia", "Indonesian"),
        Language("ms", "Bahasa Melayu", "Malay"),
        Language("nl", "Nederlands", "Dutch"),
        Language("pl", "Polski", "Polish"),
        Language("uk", "Українська", "Ukrainian"),
        Language("ro", "Română", "Romanian"),
        Language("el", "Ελληνικά", "Greek"),
        Language("he", "עברית", "Hebrew"),
        Language("sv", "Svenska", "Swedish"),
        Language("no", "Norsk", "Norwegian"),
        Language("da", "Dansk", "Danish"),
        Language("fi", "Suomi", "Finnish"),
        Language("cs", "Čeština", "Czech"),
        Language("hu", "Magyar", "Hungarian"),
        Language("tl", "Filipino", "Filipino"),
        Language("sw", "Kiswahili", "Swahili"),
        Language("fa", "فارسی", "Persian"),
        Language("ur", "اردو", "Urdu"),
        Language("mr", "मराठी", "Marathi"),
        Language("te", "తెలుగు", "Telugu"),
        Language("ta", "தமிழ்", "Tamil"),
        Language("gu", "ગુજરાતી", "Gujarati"),
        Language("kn", "ಕನ್ನಡ", "Kannada"),
        Language("ml", "മലയാളം", "Malayalam"),
        Language("my", "မြန်မာဘာသာ", "Burmese"),
        Language("km", "ខ្មែរ", "Khmer"),
        Language("sr", "Српски", "Serbian"),
        Language("hr", "Hrvatski", "Croatian"),
        Language("bg", "Български", "Bulgarian"),
        Language("sk", "Slovenčina", "Slovak"),
        Language("sl", "Slovenščina", "Slovenian"),
        Language("sq", "Shqip", "Albanian"),
        Language("ka", "ქართული", "Georgian"),
        Language("hy", "Հայերեն", "Armenian"),
        Language("kk", "Қазақша", "Kazakh"),
        Language("uz", "Oʻzbekcha", "Uzbek"),
        Language("mn", "Монгол", "Mongolian"),
        Language("az", "Azərbaycan dili", "Azerbaijani"),
        Language("af", "Afrikaans", "Afrikaans"),
        Language("et", "Eesti", "Estonian"),
        Language("lv", "Latviešu", "Latvian"),
        Language("lt", "Lietuvių", "Lithuanian"),
        Language("is", "Íslenska", "Icelandic"),
        Language("ga", "Gaeilge", "Irish"),
        Language("cy", "Cymraeg", "Welsh"),
        Language("eu", "Euskara", "Basque"),
        Language("gl", "Galego", "Galician"),
        Language("ca", "Català", "Catalan"),
        Language("mk", "Македонски", "Macedonian"),
        Language("bs", "Bosanski", "Bosnian"),
        Language("mt", "Malti", "Maltese"),
        Language("am", "አማርኛ", "Amharic"),
        Language("so", "Soomaali", "Somali"),
        Language("ha", "Hausa", "Hausa"),
        Language("yo", "Yorùbá", "Yoruba"),
        Language("ig", "Asụsụ Igbo", "Igbo"),
        Language("zu", "isiZulu", "Zulu"),
        Language("xh", "isiXhosa", "Xhosa"),
        Language("ne", "नेपाली", "Nepali"),
        Language("si", "සිංහල", "Sinhala"),
        Language("lo", "ລາວ", "Lao"),
        Language("bo", "བོད་སྐད་", "Tibetan"),
        Language("ky", "Кыргызча", "Kyrgyz"),
        Language("tg", "Тоҷикӣ", "Tajik"),
        Language("tk", "Türkmençe", "Turkmen"),
        Language("ps", "پښتو", "Pashto"),
        Language("ku", "Kurdî", "Kurdish"),
        Language("sd", "سنڌي", "Sindhi"),
        Language("ug", "ئۇيغۇرچە", "Uyghur"),
        Language("su", "Basa Sunda", "Sundanese"),
        Language("ceb", "Cebuano", "Cebuano"),
        Language("mg", "Malagasy", "Malagasy"),
        Language("eo", "Esperanto", "Esperanto"),
        Language("la", "Latina", "Latin"),
        Language("lb", "Lëtzebuergesch", "Luxembourgish"),
        Language("fy", "Frysk", "Western Frisian"),
        Language("gd", "Gàidhlig", "Scottish Gaelic"),
        Language("yi", "ייִדיש", "Yiddish"),
        Language("sn", "chiShona", "Shona"),
        Language("st", "Sesotho", "Southern Sotho"),
        Language("rw", "Kinyarwanda", "Kinyarwanda"),
        Language("be", "Беларуская", "Belarusian"),
        Language("tt", "Татарча", "Tatar"),
        Language("ba", "Башҡортса", "Bashkir"),
        Language("sa", "संस्कृतम्", "Sanskrit")
    )

    private val translations: Map<String, Map<String, String>> by lazy {
        buildMap(120) {
            putAll(TranslationDataCore.entries)
            putAll(TranslationDataMajor1.entries)
            putAll(TranslationDataMajor2.entries)
            putAll(TranslationDataExtended1.entries)
            putAll(TranslationDataExtended2.entries)
        }
    }

    fun getString(key: String, langCode: String): String {
        translations[langCode]?.get(key)?.takeIf { it.isNotEmpty() }?.let { return it }

        if (langCode.contains('-')) {
            val baseCode = if (langCode.equals("zh-HK", ignoreCase = true)) {
                "zh-TW"
            } else {
                langCode.substringBefore('-')
            }
            translations[baseCode]?.get(key)?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        translations["en"]?.get(key)?.takeIf { it.isNotEmpty() }?.let { return it }
        translations["zh-TW"]?.get(key)?.takeIf { it.isNotEmpty() }?.let { return it }
        return key
    }

    fun formatString(key: String, langCode: String, vararg args: Any): String {
        val template = getString(key, langCode)
        return runCatching {
            String.format(Locale.US, template, *args)
        }.getOrDefault(template)
    }

    fun allKeys(): Set<String> = translations["zh-TW"]?.keys ?: emptySet()

    fun allTranslationsMap(): Map<String, Map<String, String>> = translations

    /**
     * Matches any raw BCP-47 language tag or locale string (e.g. "zh-TW", "zh-Hant-TW", "en-US", "ja_JP", "es-419")
     * to the best matching supported language code in [supportedLanguages].
     */
    fun matchLanguageCode(rawTag: String?): String {
        if (rawTag.isNullOrBlank()) return "zh-TW"
        val normalized = rawTag.replace('_', '-').trim()

        // 1. Direct exact match (case insensitive)
        supportedLanguages.find { it.code.equals(normalized, ignoreCase = true) }?.let {
            return it.code
        }

        // 2. Normalize components
        val parts = normalized.split('-')
        val lang = parts.getOrNull(0)?.lowercase() ?: ""
        val subTag = parts.getOrNull(1)?.uppercase() ?: ""

        // Special handling for Chinese variants
        if (lang == "zh") {
            val fullUpper = normalized.uppercase()
            return when {
                fullUpper.contains("HK") -> "zh-HK"
                fullUpper.contains("HANT") || fullUpper.contains("TW") -> "zh-TW"
                fullUpper.contains("HANS") || fullUpper.contains("CN") || fullUpper.contains("SG") -> "zh-CN"
                else -> "zh-TW"
            }
        }

        // Special handling for English variants
        if (lang == "en") {
            return when (subTag) {
                "US" -> "en-US"
                "GB" -> "en-GB"
                else -> "en"
            }
        }

        // Special handling for French variants
        if (lang == "fr") {
            return when (subTag) {
                "CA" -> "fr-CA"
                else -> "fr"
            }
        }

        // Special handling for Spanish variants
        if (lang == "es") {
            val fullUpper = normalized.uppercase()
            return when {
                fullUpper.contains("419") || fullUpper.contains("MX") || fullUpper.contains("AR") || fullUpper.contains("CL") -> "es-419"
                else -> "es"
            }
        }

        // Special handling for Portuguese variants
        if (lang == "pt") {
            return when (subTag) {
                "BR" -> "pt-BR"
                else -> "pt"
            }
        }

        // 3. Match base language code (e.g., "de-AT" -> "de", "ja-JP" -> "ja")
        supportedLanguages.find { it.code.equals(lang, ignoreCase = true) }?.let {
            return it.code
        }

        // 4. Default fallback
        return "zh-TW"
    }
}
