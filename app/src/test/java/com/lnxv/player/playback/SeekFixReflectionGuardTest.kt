package com.lnxv.player.playback

import androidx.media3.extractor.mkv.MatroskaExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The MKV seek workaround writes MatroskaExtractor.cuesContentPosition by reflection (Media3 1.4.1).
 * If a Media3 upgrade renames or removes the field this test fails loudly, so the upgrade is not shipped
 * with a silently broken seek fix. At runtime a failure is caught and the player still works (no seek fix).
 */
class SeekFixReflectionGuardTest {
    @Test fun cuesContentPositionFieldStillExistsAsPrivateLong() {
        val f = MatroskaExtractor::class.java.getDeclaredField("cuesContentPosition")
        assertEquals(Long::class.javaPrimitiveType, f.type)
        f.isAccessible = true
    }
}
