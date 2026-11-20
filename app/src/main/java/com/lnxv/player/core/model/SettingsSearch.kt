package com.lnxv.player.core.model

/** Sections of the Settings screen, in display order. */
enum class SettingsSection(val keywords: List<String>) {
    APPEARANCE_GLASS("Appearance & Glass Engine", "appearance", "theme", "glass", "amoled", "dark", "light", "black", "थीम", "दिखावट"),
    PLAYBACK("Playback & Gestures", "playback", "gesture", "subtitle", "subtitles", "speed", "resume", "seek", "swipe", "प्लेबैक", "सबटाइटल"),
    UPDATES("Updates", "update", "version", "अपडेट"),
    PERFORMANCE("Appearance & performance", "performance", "effects", "blur", "material", "reduce", "animation", "प्रदर्शन"),
    SECURITY("Security", "security", "vault", "pin", "biometric", "fingerprint", "screenshot", "lock", "सुरक्षा", "वॉल्ट"),
    ADVANCED(
        "Advanced > Experimental", "advanced", "experimental", "flag", "developer", "picture", "enhancer", "sharpen", "sharpness",
        "colour blind", "color blind", "bookmarks", "equalizer", "skip silence", "audio only", "rotate", "labs", "एडवांस्ड"
    ),
    STORAGE("Storage", "storage", "cache", "clear", "स्टोरेज"),
    PRIVACY("Privacy", "privacy", "permission", "data", "गोपनीयता"),
    ABOUT("About LNXV", "about", "version", "info", "app", "के बारे में"),
    ONLINE_SUBTITLES("Online subtitles", "opensubtitles", "subtitles", "api key", "login", "download", "ऑनलाइन"),
    LICENSES("Open-source licenses", "licenses", "licences", "ffmpeg", "dav1d", "lgpl", "open source", "av1", "decoder", "लाइसेंस"),
    DEVELOPER("Developer", "system info", "device", "build", "debug", "reset flags", "डेवलपर");

    constructor(vararg words: String) : this(words.toList())
}

/** Pure matching for the Settings search box so it can be unit tested. */
object SettingsSearch {
    /** Empty query matches everything. Otherwise every whitespace-separated word must appear in one of the section's keywords. */
    fun matches(query: String, section: SettingsSection): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        return words.all { w -> section.keywords.any { it.contains(w, ignoreCase = true) } }
    }

    fun sectionsFor(query: String): List<SettingsSection> = SettingsSection.values().filter { matches(query, it) }
}
