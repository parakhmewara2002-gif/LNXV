package com.lnxv.player.core.webdav

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** Routes only the registered WebDAV root to a no-redirect HTTP source; all other URIs use [default]. */
class WebDavRoutingDataSource(
    private val default: DataSource,
    private val webDav: DataSource
) : DataSource {
    private var active: DataSource = default

    override fun addTransferListener(transferListener: TransferListener) {
        default.addTransferListener(transferListener)
        webDav.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val authorization = WebDavAuth.headerFor(dataSpec.uri.toString())
        active = if (authorization == null) {
            default
        } else {
            webDav
        }
        if (authorization == null) return active.open(dataSpec)
        val headers = dataSpec.httpRequestHeaders.toMutableMap()
        headers["Authorization"] = authorization
        return active.open(dataSpec.withRequestHeaders(headers))
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active.read(buffer, offset, length)

    override fun getUri(): Uri? = active.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders

    override fun close() = active.close()
}
