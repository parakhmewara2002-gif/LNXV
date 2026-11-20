package com.lnxv.player.core.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavTest {
    private val xml = """<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
 <d:response><d:href>/dav/movies/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
 <d:response><d:href>/dav/movies/Zeta.mkv</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>1234</d:getcontentlength></d:prop></d:propstat></d:response>
 <d:response><d:href>/dav/movies/alpha%20clip.mp4</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>99</d:getcontentlength></d:prop></d:propstat></d:response>
 <d:response><d:href>/dav/movies/notes.txt</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
 <d:response><d:href>/dav/movies/Sub</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
 <d:response><d:href>https://evil.example/dav/movies/x.mp4</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
</d:multistatus>"""

    @Test fun parsesFoldersThenVideosAndSkipsSelfAndOthers() {
        val e = WebDavParser.parse(xml, "https://nas.local/dav/movies/")
        assertEquals(listOf("Sub", "alpha clip.mp4", "Zeta.mkv"), e.map { it.name })
        assertTrue(e[0].isDir)
        assertTrue(e[0].url.endsWith("/dav/movies/Sub/"))
        assertEquals("https://nas.local/dav/movies/alpha%20clip.mp4", e[1].url)
        assertEquals(99L, e[1].sizeBytes)
    }

    @Test fun rejectsDoctype() {
        var failed = false
        try { WebDavParser.parse("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a \"b\">]><d:multistatus xmlns:d=\"DAV:\"/>", "https://h/") } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun rejectsGarbage() {
        var failed = false
        try { WebDavParser.parse("not xml", "https://h/") } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun videoNames() {
        assertTrue(WebDavParser.isVideoName("a.MKV"))
        assertFalse(WebDavParser.isVideoName("a.txt"))
        assertFalse(WebDavParser.isVideoName("noext"))
    }

    @Test fun propfindRequestShape() {
        val req = String(WebDavHttp.buildPropfind("h", "/a b\r\nX: y", "Basic abc"))
        assertTrue(req.startsWith("PROPFIND /a bX: y HTTP/1.1\r\n"))
        assertTrue(req.contains("Depth: 1\r\n"))
        assertTrue(req.contains("Authorization: Basic abc\r\n"))
        assertFalse(String(WebDavHttp.buildPropfind("h", "/", null)).contains("Authorization"))
    }

    @Test fun parsesContentLengthResponse() {
        val raw = "HTTP/1.1 207 Multi-Status\r\nContent-Length: 5\r\nX-A: b\r\n\r\nhelloEXTRA".toByteArray()
        val r = WebDavHttp.parseResponse(raw)
        assertEquals(207, r.status)
        assertEquals("hello", String(r.body))
        assertEquals("b", r.headers["x-a"])
    }

    @Test fun parsesChunkedResponse() {
        val raw = "HTTP/1.1 207 X\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n6;ext=1\r\n world\r\n0\r\n\r\n".toByteArray()
        assertEquals("hello world", String(WebDavHttp.parseResponse(raw).body))
    }

    @Test fun truncatedChunkFails() {
        var failed = false
        try { WebDavHttp.dechunk("9\r\nshort".toByteArray()) } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun base64MatchesKnownValues() {
        assertEquals("", WebDavAuth.base64(ByteArray(0)))
        assertEquals("Zg==", WebDavAuth.base64("f".toByteArray()))
        assertEquals("Zm8=", WebDavAuth.base64("fo".toByteArray()))
        assertEquals("Zm9v", WebDavAuth.base64("foo".toByteArray()))
        assertEquals("Zm9vYg==", WebDavAuth.base64("foob".toByteArray()))
        assertEquals("Zm9vYmE=", WebDavAuth.base64("fooba".toByteArray()))
        assertEquals("Zm9vYmFy", WebDavAuth.base64("foobar".toByteArray()))
    }

    @Test fun authOnlyForSameHttpsOrigin() {
        WebDavAuth.clear()
        assertNull(WebDavAuth.headerFor("https://nas.local/a"))
        assertFalse(WebDavAuth.start("http://nas.local/", "u", "p"))
        assertTrue(WebDavAuth.start("https://NAS.local/dav", "u", "p"))
        assertEquals("Basic dTpw", WebDavAuth.headerFor("https://nas.local:443/x.mp4"))
        assertNull(WebDavAuth.headerFor("https://nas.local:8443/x.mp4"))
        assertNull(WebDavAuth.headerFor("https://other.local/x.mp4"))
        assertNull(WebDavAuth.headerFor("http://nas.local/x.mp4"))
        WebDavAuth.clear()
        assertFalse(WebDavAuth.active)
    }
}
