package com.lnxv.player.core.model

/**
 * Search with optional filter tokens (Phase 6), e.g. `holiday ext:mkv res:1080 size>500mb`.
 *  - `ext:mkv` / `type:mkv`  file extension
 *  - `res:720|1080|1440|4k|hd|fhd` minimum video height
 *  - `size>500mb`, `size<2gb` (kb, mb, gb)
 * Every other word must appear in the title or folder name. Unknown tokens are treated as plain words.
 */
data class SearchQuery(
    val terms: List<String>,
    val ext: String?,
    val minHeight: Int?,
    val minSizeBytes: Long?,
    val maxSizeBytes: Long?
) {
    val isEmpty: Boolean get() = terms.isEmpty() && ext == null && minHeight == null && minSizeBytes == null && maxSizeBytes == null

    fun matches(title: String, folder: String, path: String, height: Int, sizeBytes: Long): Boolean {
        if (ext != null && !path.lowercase().endsWith(".$ext")) return false
        if (minHeight != null && height < minHeight) return false
        if (minSizeBytes != null && sizeBytes < minSizeBytes) return false
        if (maxSizeBytes != null && sizeBytes > maxSizeBytes) return false
        return terms.all { title.contains(it, ignoreCase = true) || folder.contains(it, ignoreCase = true) }
    }

    fun matches(v: VideoItem): Boolean = matches(v.title, v.folderName, v.path, v.height, v.sizeBytes)

    companion object {
        private val SIZE = Regex("^size([<>])(\\d+(?:\\.\\d+)?)(kb|mb|gb)$")

        fun parse(raw: String): SearchQuery {
            val terms = mutableListOf<String>()
            var ext: String? = null
            var minH: Int? = null
            var minS: Long? = null
            var maxS: Long? = null
            raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { tok ->
                val t = tok.lowercase()
                val sizeMatch = SIZE.matchEntire(t)
                when {
                    t.startsWith("ext:") && t.length > 4 -> ext = t.removePrefix("ext:").removePrefix(".")
                    t.startsWith("type:") && t.length > 5 -> ext = t.removePrefix("type:").removePrefix(".")
                    t.startsWith("res:") && heightOf(t.removePrefix("res:")) != null -> minH = heightOf(t.removePrefix("res:"))
                    sizeMatch != null -> {
                        val n = sizeMatch.groupValues[2].toDouble()
                        val mult = when (sizeMatch.groupValues[3]) { "kb" -> 1L shl 10; "mb" -> 1L shl 20; else -> 1L shl 30 }
                        val bytes = (n * mult).toLong()
                        if (sizeMatch.groupValues[1] == ">") minS = bytes else maxS = bytes
                    }
                    else -> terms += tok
                }
            }
            return SearchQuery(terms, ext, minH, minS, maxS)
        }

        private fun heightOf(v: String): Int? = when (v) {
            "4k", "2160" -> 2160
            "1440", "2k" -> 1440
            "fhd", "1080" -> 1080
            "hd", "720" -> 720
            "480", "sd" -> 480
            else -> null
        }
    }
}
