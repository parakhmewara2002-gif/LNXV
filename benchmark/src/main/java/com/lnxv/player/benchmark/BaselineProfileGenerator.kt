package com.lnxv.player.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Generates a profile for the cold-start path (grant the media permission on the device first). */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = StartupBenchmark.TARGET_PACKAGE) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}
