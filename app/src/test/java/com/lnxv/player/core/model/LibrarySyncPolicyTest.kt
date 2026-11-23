package com.lnxv.player.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncPolicyTest {
    @Test fun smallLibrariesAlwaysPrune() {
        assertTrue(LibrarySyncPolicy.shouldPrune(scannedCount = 0, existingCount = 3))
        assertTrue(LibrarySyncPolicy.shouldPrune(scannedCount = 1, existingCount = 10))
    }

    @Test fun partialScanOfBigLibraryDoesNotPrune() {
        assertFalse(LibrarySyncPolicy.shouldPrune(scannedCount = 5, existingCount = 500))
        assertFalse(LibrarySyncPolicy.shouldPrune(scannedCount = 249, existingCount = 500))
    }

    @Test fun partialMediaAccessNeverPrunesEvenSmallLibraries() {
        assertFalse(LibrarySyncPolicy.shouldPrune(scannedCount = 2, existingCount = 3, hasFullAccess = false))
        assertFalse(LibrarySyncPolicy.shouldPrune(scannedCount = 250, existingCount = 500, hasFullAccess = false))
    }

    @Test fun normalScanPrunes() {
        assertTrue(LibrarySyncPolicy.shouldPrune(scannedCount = 250, existingCount = 500))
        assertTrue(LibrarySyncPolicy.shouldPrune(scannedCount = 495, existingCount = 500))
    }
}
