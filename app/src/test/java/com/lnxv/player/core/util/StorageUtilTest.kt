package com.lnxv.player.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageUtilTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun sizeAndClear() {
        val root = tmp.newFolder("c")
        java.io.File(root, "a.bin").writeBytes(ByteArray(100))
        val sub = java.io.File(root, "sub").apply { mkdirs() }
        java.io.File(sub, "b.bin").writeBytes(ByteArray(50))
        assertEquals(150L, StorageUtil.sizeOf(root))
        assertEquals(150L, StorageUtil.clearContents(root))
        assertTrue(root.exists())
        assertEquals(0L, StorageUtil.sizeOf(root))
    }
    @Test fun missingDirIsZero() = assertEquals(0L, StorageUtil.sizeOf(java.io.File("/nonexistent/lnxv")))
    @Test fun formatting() {
        assertEquals("512 B", StorageUtil.format(512))
        assertEquals("1.0 KB", StorageUtil.format(1024))
        assertEquals("2.0 MB", StorageUtil.format(2L * 1024 * 1024))
    }
}
