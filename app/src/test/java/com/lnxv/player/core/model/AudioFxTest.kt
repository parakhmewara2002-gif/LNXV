package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFxTest {
    @Test fun bluetoothWinsOverSpeaker() {
        assertEquals(OutputRoute.BLUETOOTH, OutputRouteClassifier.classify(listOf(2, 8)))
    }

    @Test fun wiredWinsOverSpeakerAndHdmi() {
        assertEquals(OutputRoute.WIRED, OutputRouteClassifier.classify(listOf(2, 3, 9)))
        assertEquals(OutputRoute.WIRED, OutputRouteClassifier.classify(listOf(22)))
    }

    @Test fun speakerHdmiAndUnknown() {
        assertEquals(OutputRoute.SPEAKER, OutputRouteClassifier.classify(listOf(2)))
        assertEquals(OutputRoute.HDMI, OutputRouteClassifier.classify(listOf(9, 2)))
        assertEquals(OutputRoute.OTHER, OutputRouteClassifier.classify(emptyList()))
        assertEquals(OutputRoute.OTHER, OutputRouteClassifier.classify(listOf(999)))
    }

    @Test fun roundTrip() {
        val s = AudioFxSettings(EqPreset.VOCAL, 400, 250)
        assertEquals(s, AudioFxSettings.decode(s.encode()))
    }

    @Test fun decodeIsTolerant() {
        assertTrue(AudioFxSettings.decode(null).isDefault)
        assertTrue(AudioFxSettings.decode("").isDefault)
        assertTrue(AudioFxSettings.decode("garbage").isDefault)
        assertEquals(AudioFxSettings(EqPreset.FLAT, 1000, 0), AudioFxSettings.decode("NOPE|99999|-4"))
        assertEquals(EqPreset.BASS, AudioFxSettings.decode("BASS").preset)
    }

    @Test fun sanitizedClamps() {
        assertEquals(AudioFxSettings(EqPreset.FLAT, 0, 1000), AudioFxSettings(EqPreset.FLAT, -5, 5000).sanitized())
    }
}
