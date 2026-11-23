package com.lnxv.player.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferProfileTest {
    @Test fun recognizesNetworkSchemes() {
        listOf("http", "https", "rtsp", "smb").forEach {
            assertTrue("$it should use network buffering", BufferProfiles.isNetwork(it))
        }
    }

    @Test fun localAndUnknownSchemesStayLocal() {
        listOf(null, "file", "content", "asset", "unknown").forEach {
            assertFalse("$it should use local buffering", BufferProfiles.isNetwork(it))
        }
    }
}
