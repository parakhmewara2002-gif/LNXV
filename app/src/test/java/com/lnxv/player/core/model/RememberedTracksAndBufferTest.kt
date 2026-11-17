package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RememberedTracksTest {
    private fun audio(id: String, selected: Boolean = false) = AudioTrackInfo(id, "en", id, 2, 48000, selected)
    private fun sub(id: String, selected: Boolean = false) = SubtitleTrackInfo(id, "en", id, false, selected)

    @Test fun empty_means_nothing_saved() {
        assertTrue(RememberedTracks().isEmpty)
        assertFalse(RememberedTracks(audioTrackId = "a").isEmpty)
        assertFalse(RememberedTracks(subtitlesOff = true).isEmpty)
    }

    @Test fun picks_saved_audio_track_when_not_already_selected() {
        val r = RememberedTracks(audioTrackId = "2")
        assertEquals("2", r.audioToApply(listOf(audio("1", true), audio("2")))?.id)
    }

    @Test fun nothing_to_apply_when_saved_track_is_already_selected_or_missing() {
        val r = RememberedTracks(audioTrackId = "2")
        assertNull(r.audioToApply(listOf(audio("1"), audio("2", true))))
        assertNull(r.audioToApply(listOf(audio("1"))))
    }

    @Test fun subtitles_off_wins_over_a_saved_track() {
        val r = RememberedTracks(subtitleTrackId = "s1", subtitlesOff = true)
        assertNull(r.subtitleToApply(listOf(sub("s1"))))
    }

    @Test fun resolvable_only_when_saved_tracks_exist() {
        val r = RememberedTracks(audioTrackId = "2", subtitleTrackId = "s1")
        assertFalse(r.isResolvable(listOf(audio("1")), listOf(sub("s1"))))
        assertTrue(r.isResolvable(listOf(audio("2")), listOf(sub("s1"))))
        assertTrue(RememberedTracks(subtitlesOff = true).isResolvable(emptyList(), emptyList()))
    }
}

class BufferProfilesTest {
    @Test fun network_schemes_are_detected_case_insensitively() {
        assertTrue(BufferProfiles.isNetwork("https"))
        assertTrue(BufferProfiles.isNetwork("RTSP"))
        assertFalse(BufferProfiles.isNetwork("content"))
        assertFalse(BufferProfiles.isNetwork("file"))
        assertFalse(BufferProfiles.isNetwork(null))
    }

    @Test fun network_buffers_are_deeper_than_local() {
        val local = BufferProfiles.forSource(network = false, lowRam = false)
        val net = BufferProfiles.forSource(network = true, lowRam = false)
        assertTrue(net.maxBufferMs > local.maxBufferMs)
        assertTrue(net.targetBytes > local.targetBytes)
    }

    @Test fun low_ram_uses_less_memory_for_both_sources() {
        listOf(false, true).forEach { network ->
            val normal = BufferProfiles.forSource(network, lowRam = false)
            val low = BufferProfiles.forSource(network, lowRam = true)
            assertTrue(low.targetBytes < normal.targetBytes)
            assertTrue(low.maxBufferMs < normal.maxBufferMs)
        }
    }

    @Test fun every_profile_satisfies_the_load_control_ordering() {
        for (network in listOf(false, true)) for (lowRam in listOf(false, true)) {
            val p = BufferProfiles.forSource(network, lowRam)
            assertTrue(p.minBufferMs >= p.playbackBufferMs)
            assertTrue(p.minBufferMs >= p.rebufferMs)
            assertTrue(p.maxBufferMs >= p.minBufferMs)
        }
    }

    @Test fun local_profile_keeps_the_previous_values() {
        val p = BufferProfiles.forSource(network = false, lowRam = false)
        assertEquals(15_000, p.minBufferMs)
        assertEquals(40_000, p.maxBufferMs)
        assertEquals(32 * 1024 * 1024, p.targetBytes)
    }
}
