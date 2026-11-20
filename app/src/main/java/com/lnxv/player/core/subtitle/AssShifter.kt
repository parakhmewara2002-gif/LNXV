package com.lnxv.player.core.subtitle

/** Shifts the Start/End time of every `Dialogue:` / `Comment:` line of an ASS/SSA script by [offsetMs]. */
object AssShifter {
    private val TIME = Regex("""^(\d+):(\d{2}):(\d{2})\.(\d{2})$""")

    fun shift(content: String, offsetMs: Long): String {
        if (offsetMs == 0L) return content
        return content.lineSequence().joinToString("\n") { line ->
            val isEvent = line.startsWith("Dialogue:") || line.startsWith("Comment:")
            if (!isEvent) return@joinToString line
            val colon = line.indexOf(':')
            val fields = line.substring(colon + 1).split(",", limit = 10).toMutableList()
            if (fields.size < 3) return@joinToString line
            fields[1] = shiftTime(fields[1].trim(), offsetMs)
            fields[2] = shiftTime(fields[2].trim(), offsetMs)
            line.substring(0, colon + 1) + fields.joinToString(",")
        }
    }

    internal fun shiftTime(time: String, offsetMs: Long): String {
        val m = TIME.matchEntire(time) ?: return time
        val total = ((m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 60 + m.groupValues[3].toLong()) * 1000 +
            m.groupValues[4].toLong() * 10 + offsetMs
        val t = total.coerceAtLeast(0L)
        val cs = (t % 1000) / 10
        return "%d:%02d:%02d.%02d".format(t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, cs)
    }
}
