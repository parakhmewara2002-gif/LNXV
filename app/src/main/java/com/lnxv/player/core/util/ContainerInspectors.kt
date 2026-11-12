package com.lnxv.player.core.util

/** Minimal random-access byte source so the container inspectors stay pure Kotlin (unit-testable). */
interface RandomAccessSource {
    val length: Long
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

class ByteArraySource(private val data: ByteArray) : RandomAccessSource {
    override val length: Long get() = data.size.toLong()
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position < 0 || position >= data.size) return -1
        val n = minOf(length, (data.size - position).toInt())
        System.arraycopy(data, position.toInt(), buffer, offset, n)
        return n
    }
}

enum class CuesStatus {
    NOT_MATROSKA, UNKNOWN, MISSING, BEFORE_CLUSTERS, LISTED_OK, LISTED_ONLY_LATE, NOT_LISTED, WRONG_POSITION;

    fun describe(): String = when (this) {
        NOT_MATROSKA -> "not a Matroska file"
        UNKNOWN -> "could not be determined"
        MISSING -> "NO Cues (seek index) in the file -> ExoPlayer cannot seek (restarts from 0)"
        BEFORE_CLUSTERS -> "Cues are stored before the video data -> seeking should work"
        LISTED_OK -> "Cues at end of file and the SeekHead at the START of the file points to them -> seeking should work"
        LISTED_ONLY_LATE -> "Cues are listed ONLY in a second SeekHead stored after the video data (Media3 ignores it -> ExoPlayer treats the file as UNSEEKABLE; LNXV applies a workaround)"
        NOT_LISTED -> "Cues exist at end of file but SeekHead has NO entry for them -> ExoPlayer likely treats the file as UNSEEKABLE"
        WRONG_POSITION -> "SeekHead points to the wrong position for Cues -> likely UNSEEKABLE"
    }
}

data class MkvReport(
    val isMatroska: Boolean,
    val docType: String?,
    val clusterCount: Int,
    val elements: List<String>,
    val seekHead: Map<String, Long>,
    val earlySeekHead: Map<String, Long>,
    val lateSeekHead: Map<String, Long>,
    val cuesPosition: Long?,
    val cuesStatus: CuesStatus,
    val notes: List<String>
) {
    fun toText(): String = buildString {
        if (!isMatroska) {
            appendLine("Container: not Matroska/WebM")
            notes.forEach { appendLine("Note: $it") }
            return@buildString
        }
        appendLine("Container: Matroska/WebM (DocType=$docType)")
        appendLine("Clusters: $clusterCount")
        appendLine("Top-level elements: " + elements.joinToString(", "))
        appendLine(
            "SeekHead at start of file: " +
                if (earlySeekHead.isEmpty()) "none" else earlySeekHead.entries.joinToString(", ") { "${it.key}@${it.value}" }
        )
        appendLine(
            "SeekHead after video data: " +
                if (lateSeekHead.isEmpty()) "none" else lateSeekHead.entries.joinToString(", ") { "${it.key}@${it.value}" }
        )
        appendLine("Cues position: " + (cuesPosition?.toString() ?: "NOT FOUND"))
        appendLine("Seek index verdict: " + cuesStatus.describe())
        notes.forEach { appendLine("Note: $it") }
    }
}

/** Walks the top-level EBML elements of a Matroska/WebM file (clusters are skipped, not read). */
class MkvInspector(private val src: RandomAccessSource) {

    companion object {
        const val EBML = 0x1A45DFA3L
        const val SEGMENT = 0x18538067L
        const val SEEKHEAD = 0x114D9B74L
        const val INFO = 0x1549A966L
        const val TRACKS = 0x1654AE6BL
        const val CLUSTER = 0x1F43B675L
        const val CUES = 0x1C53BB6BL
        const val TAGS = 0x1254C367L
        const val ATTACHMENTS = 0x1941A469L
        const val CHAPTERS = 0x1043A770L
        const val VOID = 0xECL
        const val SEEK = 0x4DBBL
        const val SEEK_ID = 0x53ABL
        const val SEEK_POSITION = 0x53ACL
        const val DOC_TYPE = 0x4282L

        fun isMatroskaHeader(head: ByteArray): Boolean =
            head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
                head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()

        private fun nameOf(id: Long): String = when (id) {
            SEEKHEAD -> "SeekHead"
            INFO -> "Info"
            TRACKS -> "Tracks"
            CLUSTER -> "Cluster"
            CUES -> "Cues"
            TAGS -> "Tags"
            ATTACHMENTS -> "Attachments"
            CHAPTERS -> "Chapters"
            VOID -> "Void"
            else -> "0x" + id.toString(16).uppercase()
        }
    }

    private class Header(val id: Long, val idLen: Int, val size: Long, val sizeLen: Int) {
        val headerLen: Int get() = idLen + sizeLen
    }

    private fun readHeader(pos: Long): Header? {
        val b = ByteArray(12)
        val n = src.readAt(pos, b, 0, 12)
        if (n < 2) return null
        val first = b[0].toInt() and 0xFF
        if (first == 0) return null
        val idLen = Integer.numberOfLeadingZeros(first) - 24 + 1
        if (idLen > 4 || idLen + 1 > n) return null
        var id = 0L
        for (i in 0 until idLen) id = (id shl 8) or (b[i].toLong() and 0xFF)
        val sFirst = b[idLen].toInt() and 0xFF
        if (sFirst == 0) return null
        val sizeLen = Integer.numberOfLeadingZeros(sFirst) - 24 + 1
        if (sizeLen > 8 || idLen + sizeLen > n) return null
        var v = (sFirst and (0xFF shr sizeLen)).toLong()
        var allOnes = v == ((1L shl (8 - sizeLen)) - 1)
        for (i in 1 until sizeLen) {
            val x = b[idLen + i].toInt() and 0xFF
            v = (v shl 8) or x.toLong()
            if (x != 0xFF) allOnes = false
        }
        return Header(id, idLen, if (allOnes) -1L else v, sizeLen)
    }

    private fun byteAt(pos: Long): Int {
        val b = ByteArray(1)
        return if (src.readAt(pos, b, 0, 1) < 1) 0 else b[0].toInt() and 0xFF
    }

    private fun readUInt(start: Long, size: Int): Long {
        var v = 0L
        for (i in 0 until size) v = (v shl 8) or byteAt(start + i).toLong()
        return v
    }

    private fun parseSeekHead(start: Long, size: Long, out: MutableMap<String, Long>) {
        for ((id, position) in readSeekEntries(start, size)) out[nameOf(id)] = position
    }

    /** (elementId, positionRelativeToSegmentData) pairs listed in one SeekHead. */
    private fun readSeekEntries(start: Long, size: Long): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        if (size <= 0 || size > 8192) return out
        val buf = ByteArray(size.toInt())
        val n = src.readAt(start, buf, 0, buf.size)
        if (n <= 0) return out
        val sub = MkvInspector(ByteArraySource(buf.copyOf(n)))
        var p = 0L
        while (p < n) {
            val h = sub.readHeader(p) ?: break
            if (h.id == SEEK && h.size >= 0) {
                var seekId: Long? = null
                var seekPos: Long? = null
                var q = p + h.headerLen
                val qEnd = q + h.size
                while (q < qEnd) {
                    val c = sub.readHeader(q) ?: break
                    val payloadStart = q + c.headerLen
                    if (c.size in 1..8) {
                        val v = sub.readUInt(payloadStart, c.size.toInt())
                        if (c.id == SEEK_ID) seekId = v else if (c.id == SEEK_POSITION) seekPos = v
                    }
                    q = payloadStart + maxOf(c.size, 0L)
                }
                if (seekId != null && seekPos != null) out.add(Pair(seekId, seekPos))
            }
            if (h.size < 0) break
            p += h.headerLen + h.size
        }
        return out
    }

    /**
     * For files where the SeekHead at the START of the file does NOT list Cues but a later SeekHead does
     * (typical for mkvmerge output): returns the Cues position (relative to Segment data) so the player
     * can be told about it. Returns null for healthy files (start SeekHead already lists Cues) or when
     * no Cues element can be located.
     */
    fun findCuesRelativePosition(): Long? {
        val h0 = readHeader(0) ?: return null
        if (h0.id != EBML) return null
        val segPos = h0.headerLen + maxOf(h0.size, 0L)
        val seg = readHeader(segPos) ?: return null
        if (seg.id != SEGMENT) return null
        val dataStart = segPos + seg.headerLen

        var first: Long? = null
        var pos = 0L
        for (i in 0 until 4) {
            val h = readHeader(dataStart + pos) ?: break
            if (h.id == SEEKHEAD) {
                first = pos
                break
            }
            if (h.size < 0 || h.id == CLUSTER) break
            pos += h.headerLen + h.size
        }
        if (first == null) return null

        val queue = ArrayDeque<Long>()
        val visited = HashSet<Long>()
        queue.add(first)
        var isFirstHead = true
        var steps = 0
        while (queue.isNotEmpty() && steps++ < 8) {
            val rel = queue.removeFirst()
            if (!visited.add(rel)) continue
            val h = readHeader(dataStart + rel) ?: continue
            if (h.id != SEEKHEAD || h.size <= 0) continue
            for ((id, p) in readSeekEntries(dataStart + rel + h.headerLen, h.size)) {
                if (id == CUES) {
                    if (isFirstHead) return null // already visible to the player
                    val c = readHeader(dataStart + p)
                    if (c != null && c.id == CUES) return p
                }
                if (id == SEEKHEAD) queue.add(p)
            }
            isFirstHead = false
        }
        return null
    }

    private fun findDocType(payloadStart: Long, size: Long): String? {
        var p = payloadStart
        val end = payloadStart + size
        while (p < end) {
            val h = readHeader(p) ?: return null
            if (h.id == DOC_TYPE && h.size in 1..32) {
                val buf = ByteArray(h.size.toInt())
                src.readAt(p + h.headerLen, buf, 0, buf.size)
                return String(buf, Charsets.ISO_8859_1)
            }
            if (h.size < 0) return null
            p += h.headerLen + h.size
        }
        return null
    }

    fun inspect(): MkvReport {
        val notes = mutableListOf<String>()
        val h0 = readHeader(0)
        if (h0 == null || h0.id != EBML) {
            return MkvReport(false, null, 0, emptyList(), emptyMap(), emptyMap(), emptyMap(), null, CuesStatus.NOT_MATROSKA, listOf("No EBML header"))
        }
        val docType = if (h0.size > 0) findDocType(h0.headerLen.toLong(), h0.size) else null
        val segPos = h0.headerLen + maxOf(h0.size, 0L)
        val seg = readHeader(segPos)
        if (seg == null || seg.id != SEGMENT) {
            return MkvReport(true, docType, 0, emptyList(), emptyMap(), emptyMap(), emptyMap(), null, CuesStatus.UNKNOWN, listOf("Segment not found after EBML header"))
        }
        val dataStart = segPos + seg.headerLen
        val end = if (seg.size >= 0) minOf(src.length, dataStart + seg.size) else src.length
        if (seg.size >= 0 && dataStart + seg.size > src.length) {
            notes += "File looks truncated (Segment claims more bytes than the file contains)"
        }

        var pos = dataStart
        var clusters = 0
        var firstCluster: Long? = null
        var cues: Long? = null
        val elements = ArrayList<String>()
        val seekHead = LinkedHashMap<String, Long>()
        val earlyHead = LinkedHashMap<String, Long>()
        val lateHead = LinkedHashMap<String, Long>()
        var guard = 0
        while (pos < end) {
            if (guard++ > 500_000) {
                notes += "Stopped after 500000 elements"
                break
            }
            val h = readHeader(pos)
            if (h == null) {
                notes += "Unreadable element at ${pos - dataStart}"
                break
            }
            val rel = pos - dataStart
            when (h.id) {
                CLUSTER -> {
                    clusters++
                    if (firstCluster == null) {
                        firstCluster = rel
                        elements += "Cluster@$rel"
                    }
                }
                SEEKHEAD -> {
                    elements += "SeekHead@$rel"
                    parseSeekHead(pos + h.headerLen, h.size, if (firstCluster == null) earlyHead else lateHead)
                }
                CUES -> {
                    cues = rel
                    elements += "Cues@$rel"
                }
                VOID -> Unit
                else -> elements += "${nameOf(h.id)}@$rel"
            }
            if (h.size < 0) {
                notes += "Element ${nameOf(h.id)} at $rel has unknown size; stopped scanning"
                break
            }
            pos += h.headerLen + h.size
        }

        seekHead.putAll(earlyHead)
        seekHead.putAll(lateHead)
        val early = earlyHead["Cues"]
        val late = lateHead["Cues"]
        val status = when {
            cues == null -> CuesStatus.MISSING
            firstCluster != null && cues < firstCluster -> CuesStatus.BEFORE_CLUSTERS
            early == cues -> CuesStatus.LISTED_OK
            early != null -> CuesStatus.WRONG_POSITION
            late == cues -> CuesStatus.LISTED_ONLY_LATE
            late != null -> CuesStatus.WRONG_POSITION
            else -> CuesStatus.NOT_LISTED
        }
        return MkvReport(true, docType, clusters, elements, seekHead, earlyHead, lateHead, cues, status, notes)
    }
}

data class Mp4Report(val boxes: List<String>, val moovBeforeMdat: Boolean?, val notes: List<String>) {
    fun toText(): String = buildString {
        appendLine("Container: MP4/MOV-style boxes: " + boxes.joinToString(", "))
        appendLine(
            "moov (index) position: " + when (moovBeforeMdat) {
                true -> "before media data (fast-start, good for seeking)"
                false -> "after media data (still seekable for local files)"
                null -> "unknown"
            }
        )
        notes.forEach { appendLine("Note: $it") }
    }
}

object Mp4Inspector {
    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, o: Int): Long = (u32(b, o) shl 32) or u32(b, o + 4)

    fun inspect(src: RandomAccessSource): Mp4Report {
        val boxes = ArrayList<String>()
        val notes = ArrayList<String>()
        var moov: Long? = null
        var mdat: Long? = null
        var pos = 0L
        var guard = 0
        while (pos < src.length && guard++ < 1000) {
            val b = ByteArray(16)
            val n = src.readAt(pos, b, 0, 16)
            if (n < 8) break
            var size = u32(b, 0)
            val type = String(b, 4, 4, Charsets.ISO_8859_1)
            var hdr = 8
            if (size == 1L) {
                if (n < 16) break
                size = u64(b, 8)
                hdr = 16
            } else if (size == 0L) {
                size = src.length - pos
            }
            if (guard == 1 && type != "ftyp" && type != "moov" && type != "mdat" && type != "free" && type != "wide") {
                notes += "First box is '$type' - probably not an MP4/MOV file"
            }
            boxes += "$type@$pos"
            if (type == "moov" && moov == null) moov = pos
            if (type == "mdat" && mdat == null) mdat = pos
            if (size < hdr) {
                notes += "Invalid box size at $pos"
                break
            }
            pos += size
        }
        val order = if (moov != null && mdat != null) moov < mdat else null
        return Mp4Report(boxes, order, notes)
    }
}
