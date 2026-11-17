package com.lnxv.player.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivatePathTest {
    private val roots = listOf("/data/user/0/com.lnxv.player/files", "/data/user/0/com.lnxv.player/cache")

    @Test fun insideRootIsRefused() {
        assertTrue(IncomingIntentValidator.isUnderAny("/data/user/0/com.lnxv.player/files/lnxv_vault_key.bin", roots))
        assertTrue(IncomingIntentValidator.isUnderAny("/data/user/0/com.lnxv.player/cache", roots))
    }

    @Test fun siblingWithSamePrefixIsAllowed() {
        assertFalse(IncomingIntentValidator.isUnderAny("/data/user/0/com.lnxv.player/files2/x.mp4", roots))
    }

    @Test fun publicStorageIsAllowed() {
        assertFalse(IncomingIntentValidator.isUnderAny("/storage/emulated/0/Movies/a.mp4", roots))
    }
}
