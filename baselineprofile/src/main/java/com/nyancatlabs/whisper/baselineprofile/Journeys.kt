package com.nyancatlabs.whisper.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * The app the profile is recorded with: its own ID, so the preview and the
 * Play version on the same phone are never replaced. See app/build.gradle.kts.
 */
const val PACKAGE = "com.nyancatlabs.whisper.profile"

private const val SHORT = 3_000L
private const val LONG = 60_000L

/**
 * A fresh install opens on the welcome, the starter feeds and the tour.
 * Through them the way a new reader would go, taking the starter feeds so
 * the feed has articles in it. Each step is skipped if it isn't there, so
 * the same journey works on the second run, when the app already has data.
 */
fun MacrobenchmarkScope.getThroughFirstRun() {
    device.wait(Until.findObject(By.text("Get started")), SHORT)?.click()
    // "Add 12 feeds": take the starter feeds, whatever their number.
    device.wait(Until.findObject(By.text(Pattern.compile("Add \\d+ feeds?"))), SHORT)?.click()
    repeat(2) { device.wait(Until.findObject(By.text("Skip")), SHORT)?.click() }
    // Android's notification question, if it comes.
    device.wait(Until.findObject(By.text(Pattern.compile("Don.t allow"))), SHORT)?.click()
}

/** The feed list, once it has a few articles in it (a first sync can take a while). */
fun MacrobenchmarkScope.waitForFeed(): UiObject2? {
    val deadline = System.currentTimeMillis() + LONG
    while (System.currentTimeMillis() < deadline) {
        val list = device.findObject(By.scrollable(true))
        if (list != null && list.childCount >= 3) return list
        device.waitForIdle()
        Thread.sleep(1_000)
    }
    return device.findObject(By.scrollable(true))
}

/** Down the feed and back up: what a reader does every time. */
fun MacrobenchmarkScope.scrollFeed(list: UiObject2) {
    list.setGestureMargin(device.displayWidth / 5)
    repeat(3) { list.fling(Direction.DOWN); device.waitForIdle() }
    repeat(3) { list.fling(Direction.UP); device.waitForIdle() }
}

/** Opens an article in the reader, scrolls it a little, and comes back. */
fun MacrobenchmarkScope.openAnArticle(list: UiObject2) {
    val card = list.children.getOrNull(1) ?: return
    card.click()
    device.waitForIdle()
    device.findObject(By.scrollable(true))?.let {
        it.setGestureMargin(device.displayWidth / 5)
        it.fling(Direction.DOWN)
    }
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}

/** The Filters sheet (sliders icon at the top of the feed), opened and closed. */
fun MacrobenchmarkScope.openFilters() {
    device.wait(Until.findObject(By.desc("Filters")), SHORT)?.click() ?: return
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}
