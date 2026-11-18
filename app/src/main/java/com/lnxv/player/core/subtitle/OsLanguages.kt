package com.lnxv.player.core.subtitle

/** Languages offered as filter chips for online search: OpenSubtitles code (lower case) to native name. */
object OsLanguages {
    val ALL: List<Pair<String, String>> = listOf(
        "en" to "English", "hi" to "हिन्दी", "es" to "Español", "fr" to "Français", "ar" to "العربية", "ru" to "Русский",
        "pt-br" to "Português (BR)", "de" to "Deutsch", "it" to "Italiano", "tr" to "Türkçe", "id" to "Indonesia",
        "zh-cn" to "中文", "ja" to "日本語", "ko" to "한국어"
    )

    /** Toggles [code] in [selected]; the list never becomes empty (the last language stays on). */
    fun toggle(selected: List<String>, code: String): List<String> {
        val next = if (code in selected) selected - code else selected + code
        return if (next.isEmpty()) selected else next
    }
}
