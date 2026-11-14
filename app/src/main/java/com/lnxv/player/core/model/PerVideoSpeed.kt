package com.lnxv.player.core.model

/**
 * Compact "id=speed;id=speed" store for per-video playback speed (Phase 5).
 * Most-recent-first, capped, so DataStore never grows unbounded. No DB migration needed.
 */
object PerVideoSpeed {
    const val MAX_ENTRIES = 200

    fun decode(encoded: String): LinkedHashMap<Long, Float> {
        val out = LinkedHashMap<Long, Float>()
        encoded.split(';').forEach { part ->
            val kv = part.split('=')
            if (kv.size != 2) return@forEach
            val id = kv[0].toLongOrNull() ?: return@forEach
            val speed = kv[1].toFloatOrNull() ?: return@forEach
            if (id >= 0 && speed in 0.25f..4f) out[id] = speed
        }
        return out
    }

    fun encode(map: Map<Long, Float>): String =
        map.entries.take(MAX_ENTRIES).joinToString(";") { "${it.key}=${it.value}" }

    /** Returns the new encoded string. A speed of 1.0 removes the entry (that is the default). */
    fun with(encoded: String, videoId: Long, speed: Float): String {
        if (videoId < 0) return encoded
        val map = decode(encoded)
        map.remove(videoId)
        val result = LinkedHashMap<Long, Float>()
        if (speed != 1.0f) result[videoId] = speed
        result.putAll(map)
        return encode(result)
    }

    fun get(encoded: String, videoId: Long): Float? = decode(encoded)[videoId]
}
