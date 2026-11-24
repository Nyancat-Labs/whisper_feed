package com.nyancatlabs.whisper.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what Whisper runs while it starts, and while a reader scrolls,
 * opens an article and the Filters sheet. Android compiles exactly that
 * ahead of time when the app is installed, so it isn't interpreted, then
 * compiled, on the first runs.
 *
 *     ./gradlew :app:generateReleaseBaselineProfile
 *
 * writes app/src/main/generated/baselineProfiles/, which is committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        getThroughFirstRun()
        val list = waitForFeed() ?: return@collect
        scrollFeed(list)
        openAnArticle(list)
        openFilters()
    }
}
