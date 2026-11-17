package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderSelectionTest {
    private data class Dec(val name: String, val hw: Boolean)

    private val hwDec = Dec("c2.vendor.avc.decoder", true)
    private val swDec = Dec("c2.android.avc.decoder", false)
    private val all = listOf(hwDec, swDec)

    @Test fun auto_keeps_platform_order() {
        assertEquals(all, DecoderSelection.order(DecoderMode.AUTO, all) { it.hw })
    }

    @Test fun hardware_only_removes_software_decoders() {
        assertEquals(listOf(hwDec), DecoderSelection.order(DecoderMode.HARDWARE, all) { it.hw })
    }

    @Test fun hardware_only_with_no_hardware_decoder_yields_empty_list() {
        assertTrue(DecoderSelection.order(DecoderMode.HARDWARE, listOf(swDec)) { it.hw }.isEmpty())
    }

    @Test fun software_preferred_puts_software_first_and_keeps_hardware_as_last_resort() {
        assertEquals(listOf(swDec, hwDec), DecoderSelection.order(DecoderMode.SOFTWARE, all) { it.hw })
    }

    @Test fun software_preferred_with_only_hardware_still_plays() {
        assertEquals(listOf(hwDec), DecoderSelection.order(DecoderMode.SOFTWARE, listOf(hwDec)) { it.hw })
    }

    @Test fun decoder_mode_parse_is_tolerant() {
        assertEquals(DecoderMode.SOFTWARE, DecoderMode.fromName("SOFTWARE"))
        assertEquals(DecoderMode.AUTO, DecoderMode.fromName("garbage"))
        assertEquals(DecoderMode.AUTO, DecoderMode.fromName(null))
    }
}

class CodecQuirksTest {
    private val table = listOf(
        CodecQuirk(decoderName = "OMX.bad.avc", mimeType = "video/avc", hardware = "mt6765"),
        CodecQuirk(decoderName = "c2.bad.any")
    )

    @Test fun shipped_table_has_no_unverified_entries() {
        assertTrue(CodecQuirks.KNOWN.isEmpty())
    }

    @Test fun blocks_exact_match_on_name_mime_and_hardware() {
        assertTrue(CodecQuirks.isBlocked(table, "MT6765", "omx.bad.avc", "video/AVC"))
    }

    @Test fun does_not_block_other_hardware_or_mime() {
        assertFalse(CodecQuirks.isBlocked(table, "qcom", "OMX.bad.avc", "video/avc"))
        assertFalse(CodecQuirks.isBlocked(table, "mt6765", "OMX.bad.avc", "video/hevc"))
    }

    @Test fun entry_without_mime_or_hardware_matches_any() {
        assertTrue(CodecQuirks.isBlocked(table, "anything", "c2.bad.any", "video/hevc"))
    }

    @Test fun empty_table_blocks_nothing() {
        assertFalse(CodecQuirks.isBlocked(emptyList(), "x", "y", "z"))
    }
}

class DecoderNamesTest {
    @Test fun recognises_common_software_decoders() {
        assertTrue(DecoderNames.looksSoftware("c2.android.avc.decoder"))
        assertTrue(DecoderNames.looksSoftware("OMX.google.h264.decoder"))
        assertTrue(DecoderNames.looksSoftware("ffmpeg-ac3"))
    }

    @Test fun vendor_decoders_are_not_software() {
        assertFalse(DecoderNames.looksSoftware("c2.qti.avc.decoder"))
        assertFalse(DecoderNames.looksSoftware("OMX.MTK.VIDEO.DECODER.AVC"))
    }
}

class DecoderFallbackPolicyTest {
    @Test fun init_failure_in_auto_retries_in_software_once() {
        val p = DecoderFallbackPolicy()
        assertEquals(DecoderFallbackPolicy.Action.RETRY_SOFTWARE, p.onDecoderInitFailed(DecoderMode.AUTO))
        assertTrue(p.softwareFallbackUsed)
        assertEquals(DecoderFallbackPolicy.Action.GIVE_UP, p.onDecoderInitFailed(DecoderMode.AUTO))
    }

    @Test fun explicit_hardware_only_is_never_overridden() {
        val p = DecoderFallbackPolicy()
        assertEquals(DecoderFallbackPolicy.Action.GIVE_UP, p.onDecoderInitFailed(DecoderMode.HARDWARE))
        assertEquals(DecoderFallbackPolicy.Action.GIVE_UP, p.onDecodeError(DecoderMode.HARDWARE, 1_000L))
        assertFalse(p.softwareFallbackUsed)
    }

    @Test fun software_mode_has_nothing_to_fall_back_to() {
        val p = DecoderFallbackPolicy()
        assertEquals(DecoderFallbackPolicy.Action.GIVE_UP, p.onDecoderInitFailed(DecoderMode.SOFTWARE))
    }

    @Test fun first_decode_error_retries_same_then_second_goes_software() {
        val p = DecoderFallbackPolicy(decodeErrorThreshold = 2)
        assertEquals(DecoderFallbackPolicy.Action.RETRY_SAME, p.onDecodeError(DecoderMode.AUTO, 10_000L))
        assertEquals(DecoderFallbackPolicy.Action.RETRY_SOFTWARE, p.onDecodeError(DecoderMode.AUTO, 10_200L))
    }

    @Test fun errors_far_apart_in_playback_time_are_not_consecutive() {
        val p = DecoderFallbackPolicy(decodeErrorThreshold = 2, progressResetMs = 5_000L)
        assertEquals(DecoderFallbackPolicy.Action.RETRY_SAME, p.onDecodeError(DecoderMode.AUTO, 10_000L))
        assertEquals(DecoderFallbackPolicy.Action.RETRY_SAME, p.onDecodeError(DecoderMode.AUTO, 60_000L))
    }

    @Test fun after_software_retry_further_errors_give_up() {
        val p = DecoderFallbackPolicy(decodeErrorThreshold = 1)
        assertEquals(DecoderFallbackPolicy.Action.RETRY_SOFTWARE, p.onDecodeError(DecoderMode.AUTO, 0L))
        assertEquals(DecoderFallbackPolicy.Action.GIVE_UP, p.onDecodeError(DecoderMode.AUTO, 100L))
    }

    @Test fun reset_allows_a_new_video_to_fall_back_again() {
        val p = DecoderFallbackPolicy()
        p.onDecoderInitFailed(DecoderMode.AUTO)
        p.reset()
        assertFalse(p.softwareFallbackUsed)
        assertEquals(DecoderFallbackPolicy.Action.RETRY_SOFTWARE, p.onDecoderInitFailed(DecoderMode.AUTO))
    }
}
