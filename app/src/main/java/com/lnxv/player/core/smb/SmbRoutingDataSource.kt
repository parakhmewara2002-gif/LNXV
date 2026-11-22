package com.lnxv.player.core.smb

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** Sends `smb://` requests to the SMB data source (when the build has one) and everything else to [default]. */
class SmbRoutingDataSource(private val default: DataSource, private val smb: DataSource?) : DataSource {
    private var active: DataSource = default

    override fun addTransferListener(l: TransferListener) {
        default.addTransferListener(l)
        smb?.addTransferListener(l)
    }

    override fun open(dataSpec: DataSpec): Long {
        active = if (dataSpec.uri.scheme.equals("smb", ignoreCase = true) && smb != null) smb else default
        return active.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active.read(buffer, offset, length)
    override fun getUri(): Uri? = active.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders
    override fun close() = active.close()
}

/** Resolves the per-build backend: real in the sideload build, null in the store build. */
object SmbBackends {
    val instance: SmbBackend? by lazy { runCatching { SmbBackendFactory.create() }.getOrNull() }
}
