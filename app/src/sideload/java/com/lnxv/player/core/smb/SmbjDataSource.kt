package com.lnxv.player.core.smb

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.share.DiskShare
import java.io.IOException
import java.util.EnumSet

/** Reads a file from an SMB share with random access. Uses the session-only credentials in [SmbSession]. */
class SmbjDataSource : BaseDataSource(true) {
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var share: DiskShare? = null
    private var file: com.hierynomus.smbj.share.File? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val path = SmbPath.parse(dataSpec.uri.toString()) ?: throw IOException("Bad SMB address")
        val creds = SmbSession.forPath(path) ?: throw IOException("Not signed in to this SMB server")
        try {
            val c = SMBClient()
            client = c
            val conn = c.connect(creds.host, creds.port)
            connection = conn
            val password = creds.password.toCharArray()
            val session = try {
                conn.authenticate(AuthenticationContext(creds.user, password, creds.domain))
            } finally {
                password.fill('\\u0000')
            }
            val s = session.connectShare(path.share) as? DiskShare ?: throw IOException("Not a file share")
            share = s
            val f = s.openFile(
                path.path.replace('/', '\\'), EnumSet.of(AccessMask.GENERIC_READ), null,
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null
            )
            file = f
            val size = f.fileInformation.standardInformation.endOfFile
            position = dataSpec.position
            if (position < 0 || position > size) throw IOException("Invalid SMB read position")
            if (dataSpec.length != C.LENGTH_UNSET.toLong() && dataSpec.length < 0) throw IOException("Invalid SMB read length")
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - position else minOf(dataSpec.length, size - position)
        } catch (e: IOException) {
            close(); throw e
        } catch (e: Exception) {
            close(); throw IOException("SMB open failed", e)
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val want = minOf(remaining, length.toLong()).toInt()
        val n = try { file!!.read(buffer, position, offset, want) } catch (e: Exception) { throw IOException("SMB read failed", e) }
        if (n < 0) return C.RESULT_END_OF_INPUT
        if (n == 0) throw IOException("SMB read made no progress")
        position += n; remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { file?.close() } catch (_: Exception) {}
        try { share?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        file = null; share = null; connection = null; client = null; uri = null
        if (opened) { opened = false; transferEnded() }
    }
}
