package com.lnxv.player.core.flags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagTest {
    @Test fun defaultsCoverEveryFlag() {
        val d = FeatureFlag.defaults()
        assertEquals(FeatureFlag.values().size, d.size)
        FeatureFlag.values().forEach { assertEquals(it.defaultEnabled, d[it]) }
    }

    @Test fun storageKeysAreUnique() {
        val keys = FeatureFlag.values().map { "ff_" + it.name.lowercase() }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun securityFlagsDefaultOn() {
        assertTrue(FeatureFlag.ENCRYPTED_VAULT.defaultEnabled)
        assertTrue(FeatureFlag.VAULT_AUTO_LOCK.defaultEnabled)
        assertTrue(FeatureFlag.SECURE_UPDATER_CHECKS.defaultEnabled)
    }
}
