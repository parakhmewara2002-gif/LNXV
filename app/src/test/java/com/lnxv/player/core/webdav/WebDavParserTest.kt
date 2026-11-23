package com.lnxv.player.core.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavParserTest {
    private fun response(href: String, collection: Boolean = false) = """
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>$href</d:href>
            <d:propstat><d:prop>
              <d:resourcetype>${if (collection) "<d:collection/>" else ""}</d:resourcetype>
              <d:getcontentlength>123</d:getcontentlength>
            </d:prop></d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test fun acceptsOnlyEntriesUnderConfiguredRootAndStripsCredentials() {
        val entries = WebDavParser.parse(
            response("/dav/Movies/clip.mp4"),
            "https://user:secret@nas.local/dav/"
        )
        assertEquals(1, entries.size)
        assertEquals("clip.mp4", entries.single().name)
        assertEquals("https://nas.local/dav/Movies/clip.mp4", entries.single().url)
    }

    @Test fun rejectsSameHostPathsOutsideConfiguredRoot() {
        assertTrue(WebDavParser.parse(response("/private/clip.mp4"), "https://nas.local/dav/").isEmpty())
        assertTrue(WebDavParser.parse(response("/dav2/clip.mp4"), "https://nas.local/dav/").isEmpty())
        assertTrue(WebDavParser.parse(response("/dav/%2e%2e%2fsecret.mp4"), "https://nas.local/dav/").isEmpty())
        assertTrue(WebDavParser.parse(response("/dav/%2e%2e/secret.mp4"), "https://nas.local/dav/").isEmpty())
    }

    @Test fun rejectsOtherOriginsSchemesAndPorts() {
        assertTrue(WebDavParser.parse(response("https://evil.local/dav/clip.mp4"), "https://nas.local/dav/").isEmpty())
        assertTrue(WebDavParser.parse(response("http://nas.local/dav/clip.mp4"), "https://nas.local/dav/").isEmpty())
        assertTrue(WebDavParser.parse(response("https://nas.local:9443/dav/clip.mp4"), "https://nas.local/dav/").isEmpty())
    }

    @Test fun allowsFolderEntriesWithinRoot() {
        val entries = WebDavParser.parse(
            response("/dav/Movies/", collection = true),
            "https://nas.local/dav/"
        )
        assertEquals("Movies", entries.single().name)
        assertTrue(entries.single().isDir)
    }
}
