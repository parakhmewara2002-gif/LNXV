package com.lnxv.player.core.webdav

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class WebDavDataSourceTest {
    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val headers: Map<String, String> = emptyMap(),
        private val body: ByteArray = byteArrayOf()
    ) : HttpURLConnection(url) {
        val requestHeaders = mutableMapOf<String, String>()
        var disconnected = false

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getHeaderField(name: String?): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun setRequestProperty(key: String?, value: String?) {
            if (key != null && value != null) requestHeaders[key] = value
        }
    }

    private fun spec(position: Long = 0, length: Long = C.LENGTH_UNSET.toLong()) =
        DataSpec.Builder()
            .setUri(Uri.parse("https://nas.local/dav/movie.mp4"))
            .setPosition(position)
            .setLength(length)
            .setHttpRequestHeaders(mapOf("Authorization" to "Basic dTpw"))
            .build()

    @Test fun rangeRequestsAreValidatedAndRead() {
        val bytes = ByteArray(10) { it.toByte() }
        val connection = FakeConnection(
            URL("https://nas.local/dav/movie.mp4"),
            HttpURLConnection.HTTP_PARTIAL,
            mapOf("Content-Range" to "bytes 10-19/100", "Content-Length" to "10"),
            bytes
        )
        val source = WebDavDataSource { connection }
        assertEquals(10L, source.open(spec(position = 10, length = 10)))
        val actual = ByteArray(10)
        assertEquals(10, source.read(actual, 0, actual.size))
        assertArrayEquals(bytes, actual)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(actual, 0, actual.size))
        assertEquals("bytes=10-19", connection.requestHeaders["Range"])
        assertEquals("Basic dTpw", connection.requestHeaders["Authorization"])
        assertFalse(connection.instanceFollowRedirects)
        source.close()
        assertTrue(connection.disconnected)
    }

    @Test fun redirectsAreRejectedAndNeverFollowed() {
        val connection = FakeConnection(
            URL("https://nas.local/dav/movie.mp4"),
            HttpURLConnection.HTTP_MOVED_TEMP,
            mapOf("Location" to "https://evil.local/steal")
        )
        val source = WebDavDataSource { connection }
        try {
            source.open(spec())
            throw AssertionError("Expected redirect rejection")
        } catch (_: IOException) {
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun rejectsMismatchedContentRange() {
        val connection = FakeConnection(
            URL("https://nas.local/dav/movie.mp4"),
            HttpURLConnection.HTTP_PARTIAL,
            mapOf("Content-Range" to "bytes 0-9/100", "Content-Length" to "10"),
            ByteArray(10)
        )
        val source = WebDavDataSource { connection }
        try {
            source.open(spec(position = 10, length = 10))
            throw AssertionError("Expected range validation failure")
        } catch (_: IOException) {
            assertTrue(connection.disconnected)
        }
    }

    @Test fun rejectsServersThatIgnoreNonzeroRangeRequests() {
        val connection = FakeConnection(
            URL("https://nas.local/dav/movie.mp4"),
            HttpURLConnection.HTTP_OK,
            mapOf("Content-Length" to "100"),
            ByteArray(100)
        )
        val source = WebDavDataSource { connection }
        try {
            source.open(spec(position = 10, length = 10))
            throw AssertionError("Expected range support failure")
        } catch (_: IOException) {
            assertTrue(connection.disconnected)
        }
    }
}
