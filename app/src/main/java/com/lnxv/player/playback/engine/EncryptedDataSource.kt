package com.lnxv.player.playback.engine

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.lnxv.player.core.security.ChunkedAead
import java.io.File
import java.io.IOException

/**
 * Media3 DataSource that decrypts LNXV vault files on the fly with random-access seek.
 * Plaintext only ever exists in memory buffers; nothing is written to disk or cache.
 * Fails with IOException when the vault is locked ([keyProvider] throws).
 */
class EncryptedDataSource(
    private val keyProvider: () -> ByteArray,
    private val fileResolver: (Uri) -> File
) : BaseDataSource(/* isNetwork = */ false) {

    private var reader: ChunkedAead.Reader? = null
    private var uri: Uri? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val key = keyProvider()
        try {
            val r = ChunkedAead.Reader(ChunkedAead.FileSource(fileResolver(dataSpec.uri)), key)
            reader = r
            if (dataSpec.position > r.plainSize) throw IOException("Position out of range")
            position = dataSpec.position
            val available = r.plainSize - position
            bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available
            else minOf(dataSpec.length, available)
        } catch (e: IOException) {
            close()
            throw e
        } finally {
            key.fill(0)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val n = reader!!.read(position, buffer, offset, minOf(length.toLong(), bytesRemaining).toInt())
        if (n < 0) return C.RESULT_END_OF_INPUT
        position += n
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try { reader?.close() } finally {
            reader = null
            if (opened) { opened = false; transferEnded() }
        }
    }

    class Factory(
        private val keyProvider: () -> ByteArray,
        private val fileResolver: (Uri) -> File
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedDataSource(keyProvider, fileResolver)
    }
}
