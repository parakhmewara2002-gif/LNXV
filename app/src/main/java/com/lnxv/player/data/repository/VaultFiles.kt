package com.lnxv.player.data.repository

import android.content.Context
import java.io.File

object VaultFiles {
    const val EXT = ".lnxv"
    const val PART_EXT = ".part"

    fun dir(context: Context): File {
        val dir = File(context.filesDir, "lnxv_vault")
        if (!dir.exists()) dir.mkdirs()
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) runCatching { noMedia.createNewFile() }
        return dir
    }

    fun keyFile(context: Context): File = File(context.filesDir, "lnxv_vault_key.bin")
}
