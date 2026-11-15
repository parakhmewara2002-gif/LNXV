package com.lnxv.player.core.util

import java.io.File

/**
 * Rule B: automatic cleanup of app-owned temporary files. Only touches the given directories
 * (never user media or the vault). Deletes files older than [maxAgeMs], then oldest-first until
 * the directory is within [maxBytes].
 */
object CacheCleaner {
    fun prune(dir: File, maxAgeMs: Long, maxBytes: Long, now: Long = System.currentTimeMillis()): Long {
        if (!dir.isDirectory) return 0L
        var freed = 0L
        val files = dir.walkBottomUp().filter { it.isFile }.toMutableList()
        val keep = ArrayList<File>()
        for (f in files) {
            if (now - f.lastModified() > maxAgeMs) { val l = f.length(); if (f.delete()) freed += l } else keep += f
        }
        var total = keep.sumOf { it.length() }
        for (f in keep.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val l = f.length()
            if (f.delete()) { freed += l; total -= l }
        }
        return freed
    }

    private const val DAY = 24L * 60 * 60 * 1000

    /** Standard app cleanup, run off the main thread at startup. */
    fun runDefault(cacheDir: File): Long =
        prune(File(cacheDir, "subs"), 7 * DAY, 20L * 1024 * 1024) +
        prune(File(cacheDir, "updates"), 3 * DAY, 200L * 1024 * 1024) +
        prune(File(cacheDir, "diag"), 14 * DAY, 5L * 1024 * 1024)
}
