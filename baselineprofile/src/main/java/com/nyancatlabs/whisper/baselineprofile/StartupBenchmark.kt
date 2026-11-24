package com.nyancatlabs.whisper.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The before and after: cold start, and scrolling the feed, without the
 * profile and with it. Each prints a median in milliseconds; the gap
 * between the two is what the profile is worth.
 *
 *     ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 *
 * Run it after recording, with the phone plugged in and the screen on.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startWithoutProfile() = start(CompilationMode.None())

    @Test
    fun startWithProfile() = start(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun scrollWithoutProfile() = scroll(CompilationMode.None())

    @Test
    fun scrollWithProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun start(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        // Time to first frame only, so the first run's screens, if any,
        // don't count; the generator has already been through them.
        startActivityAndWait()
    }

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            getThroughFirstRun()
        },
    ) {
        waitForFeed()?.let { scrollFeed(it) }
    }
}
