package com.lnxv.player.playback.engine

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** Routes `lnxvvault://<file>` URIs to the decrypting source and everything else to the default one. */
@UnstableApi
class VaultRoutingDataSource(
    private val default: DataSource,
    private val vault: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        default.addTransferListener(transferListener)
        vault.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target = if (dataSpec.uri.scheme == VAULT_SCHEME) vault else default
        active = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try { active?.close() } finally { active = null }
    }

    class Factory(
        private val default: DataSource.Factory,
        private val vault: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            VaultRoutingDataSource(default.createDataSource(), vault.createDataSource())
    }

    companion object {
        const val VAULT_SCHEME = "lnxvvault"
        fun uriFor(fileName: String): Uri = Uri.Builder().scheme(VAULT_SCHEME).authority("v").appendPath(fileName).build()
    }
}
