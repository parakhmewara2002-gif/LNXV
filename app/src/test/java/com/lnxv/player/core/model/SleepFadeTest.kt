package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepFadeTest {
    @Test fun fullVolumeBeforeFade() = assertEquals(1f, SleepFade.factor(60_000L), 0f)
    @Test fun silentAtZero() = assertEquals(0f, SleepFade.factor(0L), 0f)
    @Test fun silentWhenNegative() = assertEquals(0f, SleepFade.factor(-5L), 0f)
    @Test fun halfwayThroughFade() = assertEquals(0.5f, SleepFade.factor(5_000L), 0.0001f)
    @Test fun noFadeWhenDisabled() = assertEquals(1f, SleepFade.factor(1L, fadeMs = 0L), 0f)
}
