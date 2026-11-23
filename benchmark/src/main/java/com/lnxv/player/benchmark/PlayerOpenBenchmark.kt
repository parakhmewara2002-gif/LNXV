package com.lnxv.player.benchmark

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Time to open the player from an external ACTION_VIEW intent. Pass a readable video URI:
 *   -Pandroid.testInstrumentationRunnerArguments.videoUri=content://...  (or file:///sdcard/Movies/test.mp4)
 * Skipped when no URI is given. NOT RUN in CI.
 */
@RunWith(AndroidJUnit4::class)
class PlayerOpenBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun openPlayer() {
        val uri = InstrumentationRegistry.getArguments().getString("videoUri")
        assumeTrue("videoUri argument not provided", uri != null)
        rule.measureRepeated(
            packageName = StartupBenchmark.TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial()
        ) {
            pressHome()
            startActivityAndWait(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "video/*").setPackage(StartupBenchmark.TARGET_PACKAGE))
            device.waitForIdle()
        }
    }
}
