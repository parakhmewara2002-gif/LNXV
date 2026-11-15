package com.lnxv.player.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home list scroll jank (frame timing). Needs the media permission granted and a library with enough videos.
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lnxv.player.benchmark.HomeScrollBenchmark
 * NOT RUN in CI (no device); numbers must be recorded on real hardware.
 */
@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun scrollHome() = rule.measureRepeated(
        packageName = StartupBenchmark.TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial()
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        val list = device.findObject(By.scrollable(true))
        list?.setGestureMargin(device.displayWidth / 5)
        repeat(3) { list?.fling(Direction.DOWN); device.waitForIdle() }
        repeat(3) { list?.fling(Direction.UP); device.waitForIdle() }
    }
}
