package com.lnxv.player.core.util

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CacheCleanerTest {
    private fun tmp(): File = java.nio.file.Files.createTempDirectory("cc").toFile()

    @Test fun deletesOldFilesOnly() {
        val d = tmp()
        val old = File(d, "old").apply { writeText("x"); setLastModified(1_000L) }
        val fresh = File(d, "new").apply { writeText("y") }
        CacheCleaner.prune(d, 10_000L, 1_000_000L, now = 100_000L.coerceAtLeast(fresh.lastModified()))
        assertFalse(old.exists()); assertTrue(fresh.exists())
    }

    @Test fun capsTotalSizeOldestFirst() {
        val d = tmp(); val now = 1_000_000L
        val a = File(d, "a").apply { writeBytes(ByteArray(100)); setLastModified(now - 300) }
        val b = File(d, "b").apply { writeBytes(ByteArray(100)); setLastModified(now - 200) }
        val c = File(d, "c").apply { writeBytes(ByteArray(100)); setLastModified(now - 100) }
        val freed = CacheCleaner.prune(d, 1_000_000L, 150L, now)
        assertEquals(200L, freed)
        assertFalse(a.exists()); assertFalse(b.exists()); assertTrue(c.exists())
    }

    @Test fun missingDirIsNoop() = assertEquals(0L, CacheCleaner.prune(File("/nonexistent/zzz"), 1, 1))
}
