package com.lnxv.player.core.smb

import android.os.Build

/** Sideload build: SMB through smbj (needs Android 8+). */
object SmbBackendFactory {
    fun create(): SmbBackend? = if (Build.VERSION.SDK_INT >= 26) SmbjBackend() else null
}
