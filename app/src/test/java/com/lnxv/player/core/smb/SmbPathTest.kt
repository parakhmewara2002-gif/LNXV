package com.lnxv.player.core.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbPathTest {
    @Test fun parsesHostShareAndPath() {
        val p = SmbPath.parse("smb://nas.local/media/Movies/Action")!!
        assertEquals("nas.local", p.host)
        assertEquals(445, p.port)
        assertEquals("media", p.share)
        assertEquals("Movies/Action", p.path)
    }

    @Test fun parsesPortAndShareRoot() {
        val p = SmbPath.parse("SMB://10.0.0.5:4455/share")!!
        assertEquals(4455, p.port)
        assertTrue(p.isShareRoot)
    }

    @Test fun rejectsBadInput() {
        assertNull(SmbPath.parse("http://x/y"))
        assertNull(SmbPath.parse("smb://hostonly"))
        assertNull(SmbPath.parse("smb://user:pw@host/share"))
        assertNull(SmbPath.parse("smb://host:abc/share"))
        assertNull(SmbPath.parse("smb://host:0/share"))
        assertNull(SmbPath.parse("smb://host#fragment/share"))
        assertNull(SmbPath.parse("smb://host?query/share"))
        assertNull(SmbPath.parse("smb://host/share/../etc"))
        assertNull(SmbPath.parse("smb://host/share/a%2Fb%5Cc/.."))
        assertNull(SmbPath.parse("smb://host/share/%zz"))
        assertNull(SmbPath.parse("smb://host/share/%2e%2e%2fsecret"))
        assertNull(SmbPath.parse("smb://host/share/a%2fb"))
    }

    @Test fun childParentAndUrlRoundTrip() {
        val p = SmbPath.parse("smb://nas/media")!!
        val c = p.child("My Movies").child("a b.mkv")
        assertEquals("My Movies/a b.mkv", c.path)
        assertEquals("smb://nas/media/My%20Movies/a%20b.mkv", c.toUrl())
        assertEquals(c, SmbPath.parse(c.toUrl()))
        assertEquals("My Movies", c.parent()!!.path)
        assertNull(p.parent())
    }

    @Test fun portIsKeptInUrlWhenNotDefault() {
        assertEquals("smb://h:4455/s/x", SmbPath("h", 4455, "s", "x").toUrl())
    }

    @Test fun unicodeEncoding() {
        val s = "भाग 1.mkv"
        assertEquals(s, SmbPath.decode(SmbPath.encode(s)))
        assertFalse(SmbPath.encode(s).any { it.code > 127 })
    }

    @Test fun sessionOnlyMatchesSignedInHost() {
        SmbSession.clear()
        val p = SmbPath.parse("smb://NAS/media")!!
        assertNull(SmbSession.forPath(p))
        SmbSession.start(p, "u", "p", "")
        assertNotNull(SmbSession.forPath(SmbPath.parse("smb://nas/other")!!))
        assertNull(SmbSession.forPath(SmbPath.parse("smb://nas:4455/other")!!))
        assertNull(SmbSession.forPath(SmbPath.parse("smb://evil/other")!!))
        SmbSession.clear()
        assertNull(SmbSession.forPath(p))
    }
}
