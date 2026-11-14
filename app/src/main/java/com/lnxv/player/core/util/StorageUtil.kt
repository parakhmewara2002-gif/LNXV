package com.lnxv.player.core.util

import java.io.File

/** Disk-usage helpers for Settings > Storage (Phase 6 / rule B). Never touches anything outside the given directory. */
object StorageUtil {
    fun sizeOf(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /** Deletes everything inside [dir] but keeps [dir] itself. Returns the number of bytes freed. */
    fun clearContents(dir: File): Long {
        if (!dir.isDirectory) return 0L
        val before = sizeOf(dir)
        dir.listFiles()?.forEach { it.deleteRecursively() }
        return (before - sizeOf(dir)).coerceAtLeast(0L)
    }

    fun format(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
