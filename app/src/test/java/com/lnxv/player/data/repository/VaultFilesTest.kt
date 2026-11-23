package com.lnxv.player.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultFilesTest {
    @Test fun acceptsOnlyGeneratedUuidVaultNames() {
        assertTrue(VaultFiles.isValidFileName("01234567-89ab-cdef-0123-456789abcdef.lnxv"))
        assertTrue(VaultFiles.isValidFileName("ABCDEF01-2345-6789-ABCD-EF0123456789.lnxv"))
    }

    @Test fun rejectsTraversalAndUnexpectedNames() {
        assertFalse(VaultFiles.isValidFileName("../outside.lnxv"))
        assertFalse(VaultFiles.isValidFileName("folder/01234567-89ab-cdef-0123-456789abcdef.lnxv"))
        assertFalse(VaultFiles.isValidFileName("01234567-89ab-cdef-0123-456789abcdef.part"))
        assertFalse(VaultFiles.isValidFileName("custom-name.lnxv"))
        assertFalse(VaultFiles.isValidFileName(""))
    }
}
