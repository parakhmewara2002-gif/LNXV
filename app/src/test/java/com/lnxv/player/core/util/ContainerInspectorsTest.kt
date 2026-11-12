package com.lnxv.player.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerInspectorsTest {

    private fun idBytes(v: Long): ByteArray {
        val out = ArrayList<Byte>()
        var started = false
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val b = ((v shr shift) and 0xFF).toInt()
            if (b != 0 || started) {
                out.add(b.toByte())
                started = true
            }
        }
        return out.toByteArray()
    }

    // 8-byte EBML size: 0x01 marker + 7 data bytes
    private fun sizeBytes(n: Long): ByteArray = byteArrayOf(
        0x01, 0, 0, 0,
        (n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte()
    )

    private fun element(id: Long, payload: ByteArray): ByteArray =
        idBytes(id) + sizeBytes(payload.size.toLong()) + payload

    private fun uint4(n: Long): ByteArray =
        byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

    private fun seekEntry(targetId: Long, position: Long): ByteArray =
        element(0x4DBB, element(0x53AB, idBytes(targetId)) + element(0x53AC, uint4(position)))

    private fun mkv(withCues: Boolean, cuesAtStart: Boolean = false, seekHeadMode: String = "correct"): ByteArray {
        val ebml = element(0x1A45DFA3, element(0x4282, "matroska".toByteArray()))
        val info = element(MkvInspector.INFO, ByteArray(10))
        val tracks = element(MkvInspector.TRACKS, ByteArray(10))
        val clusters = (1..3).map { element(MkvInspector.CLUSTER, ByteArray(100)) }
        val cues = element(MkvInspector.CUES, ByteArray(20))

        fun seekHead(cuesPos: Long): ByteArray = element(
            MkvInspector.SEEKHEAD,
            if (seekHeadMode == "none") seekEntry(MkvInspector.INFO, 0) else seekEntry(MkvInspector.CUES, cuesPos)
        )

        val placeholder = seekHead(0)
        val clustersBytes = clusters.fold(ByteArray(0)) { acc, c -> acc + c }
        val cuesPos = (placeholder.size + info.size + tracks.size + clustersBytes.size).toLong()
        val wrongPos = if (seekHeadMode == "wrong") cuesPos + 7 else cuesPos
        val sh = seekHead(wrongPos)
        val payload = when {
            !withCues -> sh + info + tracks + clustersBytes
            cuesAtStart -> sh + cues + info + tracks + clustersBytes
            else -> sh + info + tracks + clustersBytes + cues
        }
        return ebml + element(MkvInspector.SEGMENT, payload)
    }

    @Test
    fun cuesAtEnd_listedInSeekHead_isOk() {
        val r = MkvInspector(ByteArraySource(mkv(withCues = true))).inspect()
        assertTrue(r.isMatroska)
        assertEquals("matroska", r.docType)
        assertEquals(3, r.clusterCount)
        assertEquals(CuesStatus.LISTED_OK, r.cuesStatus)
    }

    @Test
    fun cuesAtEnd_notListed_isFlagged() {
        val r = MkvInspector(ByteArraySource(mkv(withCues = true, seekHeadMode = "none"))).inspect()
        assertEquals(CuesStatus.NOT_LISTED, r.cuesStatus)
    }

    @Test
    fun cuesAtEnd_wrongPosition_isFlagged() {
        val r = MkvInspector(ByteArraySource(mkv(withCues = true, seekHeadMode = "wrong"))).inspect()
        assertEquals(CuesStatus.WRONG_POSITION, r.cuesStatus)
    }

    @Test
    fun noCues_isMissing() {
        val r = MkvInspector(ByteArraySource(mkv(withCues = false))).inspect()
        assertEquals(CuesStatus.MISSING, r.cuesStatus)
        assertEquals(3, r.clusterCount)
    }

    @Test
    fun cuesBeforeClusters_isFine() {
        val r = MkvInspector(ByteArraySource(mkv(withCues = true, cuesAtStart = true, seekHeadMode = "none"))).inspect()
        assertEquals(CuesStatus.BEFORE_CLUSTERS, r.cuesStatus)
    }

    @Test
    fun seekHeadEntries_areParsed() {
        val r = MkvInspector(ByteArraySource(mkv(withCues = true))).inspect()
        assertNotNull(r.seekHead["Cues"])
        assertEquals(r.cuesPosition, r.seekHead["Cues"])
    }

    @Test
    fun nonMatroska_isRejected() {
        val r = MkvInspector(ByteArraySource(ByteArray(64) { 7 })).inspect()
        assertFalse(r.isMatroska)
        assertEquals(CuesStatus.NOT_MATROSKA, r.cuesStatus)
        assertTrue(MkvInspector.isMatroskaHeader(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())))
        assertFalse(MkvInspector.isMatroskaHeader(byteArrayOf(0, 0, 0, 0)))
    }

    private fun box(type: String, payloadSize: Int): ByteArray {
        val size = 8 + payloadSize
        return byteArrayOf((size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte()) +
            type.toByteArray(Charsets.ISO_8859_1) + ByteArray(payloadSize)
    }

    @Test
    fun mp4_moovAfterMdat_isDetected() {
        val data = box("ftyp", 16) + box("mdat", 100) + box("moov", 50)
        val r = Mp4Inspector.inspect(ByteArraySource(data))
        assertEquals(false, r.moovBeforeMdat)
        assertEquals(listOf("ftyp@0", "mdat@24", "moov@132"), r.boxes)
    }

    @Test
    fun mp4_moovBeforeMdat_isDetected() {
        val data = box("ftyp", 16) + box("moov", 50) + box("mdat", 100)
        assertEquals(true, Mp4Inspector.inspect(ByteArraySource(data)).moovBeforeMdat)
    }
}
