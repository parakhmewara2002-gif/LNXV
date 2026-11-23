package com.lnxv.player.data.repository

import android.content.Context
import java.io.File
import java.io.IOException

object VaultFiles {
    const val EXT = ".lnxv"
    const val PART_EXT = ".part"

    fun dir(context: Context): File {
        val dir = File(context.filesDir, "lnxv_vault")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create vault directory")
        if (!dir.isDirectory) throw IOException("Vault path is not a directory")
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) runCatching { noMedia.createNewFile() }
        return dir
    }

    fun file(context: Context, name: String): File {
        if (!isValidFileName(name)) throw IOException("Invalid vault file name")
        val root = dir(context).canonicalFile
        val target = File(root, name).canonicalFile
        if (target.parentFile != root) throw IOException("Vault file escaped its directory")
        return target
    }

    fun isValidFileName(name: String): Boolean =
        VAULT_FILE_NAME.matches(name)

    fun keyFile(context: Context): File = File(context.filesDir, "lnxv_vault_key.bin")

    private val VAULT_FILE_NAME = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.lnxv")
}
