package com.lnxv.player.core.cast

import com.lnxv.player.core.webdav.WebDavAuth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastPolicyTest {
    @Test fun httpAndHttpsAreCastable() {
        assertTrue(CastPolicy.canCast("https://example.com/video.mp4"))
        assertTrue(CastPolicy.canCast("http://192.168.1.20:8080/stream.m3u8"))
    }

    @Test fun otherSchemesAndEmptyAreNot() {
        assertFalse(CastPolicy.canCast(null))
        assertFalse(CastPolicy.canCast(""))
        assertFalse(CastPolicy.canCast("content://media/external/video/1"))
        assertFalse(CastPolicy.canCast("file:///sdcard/a.mp4"))
        assertFalse(CastPolicy.canCast("smb://nas/share/a.mkv"))
        assertFalse(CastPolicy.canCast("rtsp://cam/stream"))
    }

    @Test fun loopbackAndCredentialsAreNot() {
        assertFalse(CastPolicy.canCast("http://localhost:8080/a.mp4"))
        assertFalse(CastPolicy.canCast("http://127.0.0.1/a.mp4"))
        assertFalse(CastPolicy.canCast("https://user:pw@example.com/a.mp4"))
    }

    @Test fun webDavWithSignInIsNot() {
        WebDavAuth.clear()
        assertTrue(CastPolicy.canCast("https://nas.local/dav/a.mp4"))
        WebDavAuth.start("https://nas.local/dav/", "u", "p")
        assertFalse(CastPolicy.canCast("https://nas.local/dav/a.mp4"))
        assertTrue(CastPolicy.canCast("https://other.example/a.mp4"))
        WebDavAuth.clear()
    }
}
