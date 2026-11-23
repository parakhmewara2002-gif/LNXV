package com.lnxv.player.core.smb

import androidx.media3.datasource.DataSource
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.util.EnumSet

class SmbjBackend : SmbBackend {
    override fun list(path: SmbPath, creds: SmbSession.Creds): SmbResult {
        return try {
            SMBClient().use { client ->
                client.connect(creds.host, creds.port).use { conn ->
                    val password = creds.password.toCharArray()
                    val session = try {
                        conn.authenticate(AuthenticationContext(creds.user, password, creds.domain))
                    } finally {
                        password.fill('\u0000')
                    }
                    (session.connectShare(path.share) as? DiskShare)?.use { share ->
                        val entries = share.list(path.path.replace('/', '\\')).mapNotNull { f ->
                            val name = f.fileName
                            if (!SmbPath.isValidSegment(name)) return@mapNotNull null
                            val isDir = EnumWithValue.EnumUtils.isSet(f.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
                            if (!isDir && !com.lnxv.player.core.webdav.WebDavParser.isVideoName(name)) return@mapNotNull null
                            SmbEntry(name, isDir, f.endOfFile)
                        }.sortedWith(compareBy<SmbEntry>({ !it.isDir }, { it.name.lowercase() }))
                        SmbResult.Ok(entries)
                    } ?: SmbResult.Error("This share is not a file share")
                }
            }
        } catch (e: com.hierynomus.mssmb2.SMBApiException) {
            SmbResult.Error(
                when (e.statusCode) {
                    0xC000006DL, 0xC000006AL -> "Wrong username or password"
                    0xC00000CCL -> "Share not found"
                    0xC0000022L -> "Access denied"
                    else -> "SMB error"
                }
            )
        } catch (e: java.io.IOException) {
            SmbResult.Error("Could not connect")
        } catch (e: Exception) {
            SmbResult.Error("SMB error")
        }
    }

    override fun newDataSource(): DataSource = SmbjDataSource()
}
