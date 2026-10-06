/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder

import com.saulhdev.feeder.utils.RENOTIFY_STUCK_AFTER_MS
import com.saulhdev.feeder.utils.StuckReason
import com.saulhdev.feeder.utils.SyncProblem
import com.saulhdev.feeder.utils.mayRenotifyStuck
import com.saulhdev.feeder.utils.sourcesAreNews
import com.saulhdev.feeder.utils.stuckReasons
import com.saulhdev.feeder.utils.syncOverdueAfterMs
import com.saulhdev.feeder.utils.syncProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val HOUR = 3_600_000L
private const val NOW = 1_800_000_000_000L

/**
 * The notification that says syncing has stopped working, and only that.
 */
class SyncProblemsTest {

    private val day = syncOverdueAfterMs("1")

    @Test
    fun `a sync inside the window is no problem`() {
        assertEquals(SyncProblem.None, syncProblem(NOW, NOW - 23 * HOUR, day, stoppedSources = 0))
    }

    @Test
    fun `a sync past the window is stuck`() {
        assertEquals(SyncProblem.SyncStuck, syncProblem(NOW, NOW - 25 * HOUR, day, 0))
    }

    @Test
    fun `a stuck sync hides the stopped sources it causes`() {
        assertEquals(SyncProblem.SyncStuck, syncProblem(NOW, NOW - 4 * 24 * HOUR, day, 120))
    }

    @Test
    fun `three stopped sources are worth saying, two are not`() {
        assertEquals(SyncProblem.SourcesStuck, syncProblem(NOW, NOW - HOUR, day, 3))
        assertEquals(SyncProblem.None, syncProblem(NOW, NOW - HOUR, day, 2))
    }

    @Test
    fun `nothing to say before anything has synced`() {
        assertEquals(SyncProblem.None, syncProblem(NOW, null, day, 0))
        assertEquals(SyncProblem.None, syncProblem(NOW, 0L, day, 50))
    }

    @Test
    fun `a stuck sync is said at most once a day`() {
        assertTrue(mayRenotifyStuck(NOW, 0L))
        assertFalse(mayRenotifyStuck(NOW, NOW - RENOTIFY_STUCK_AFTER_MS + 1))
        assertTrue(mayRenotifyStuck(NOW, NOW - RENOTIFY_STUCK_AFTER_MS))
    }

    @Test
    fun `the same dead feeds are not news twice`() {
        assertTrue(sourcesAreNews(stopped = 3, lastNotifiedCount = 0))
        assertFalse(sourcesAreNews(stopped = 3, lastNotifiedCount = 3))
        assertTrue(sourcesAreNews(stopped = 4, lastNotifiedCount = 3))
        assertFalse(sourcesAreNews(stopped = 2, lastNotifiedCount = 0))
    }

    @Test
    fun `the reasons name what the phone is not doing`() {
        assertEquals(
            listOf(StuckReason.Charger, StuckReason.WiFi),
            stuckReasons(
                requiresCharging = true, pluggedIn = false,
                wifiOnly = true, unmetered = false,
                batterySaver = false, batteryLow = false,
            ),
        )
        assertEquals(
            listOf(StuckReason.BatterySaver),
            stuckReasons(
                requiresCharging = false, pluggedIn = false,
                wifiOnly = true, unmetered = true,
                batterySaver = true, batteryLow = false,
            ),
        )
    }

    @Test
    fun `a charger only counts when the switch asks for one`() {
        assertEquals(
            listOf(StuckReason.Unknown),
            stuckReasons(
                requiresCharging = false, pluggedIn = false,
                wifiOnly = false, unmetered = false,
                batterySaver = false, batteryLow = false,
            ),
        )
    }

    private val watchdog =
        File("src/main/java/com/saulhdev/feeder/manager/sync/SyncWatchdog.kt").readText()

    @Test
    fun `the check waits for nothing the sync waits for`() {
        val schedule = watchdog.substring(watchdog.indexOf("fun schedule("), watchdog.indexOf("fun clearIfCurrent("))
        assertFalse(schedule.contains("setConstraints"))
        assertTrue(schedule.contains("ExistingPeriodicWorkPolicy.KEEP"))
    }

    @Test
    fun `the app schedules the check`() {
        val main = File("src/main/java/com/saulhdev/feeder/MainActivity.kt").readText()
        assertTrue(main.contains("SyncWatchdog.schedule("))
    }

    @Test
    fun `it can be heard, on a channel of its own`() {
        assertTrue(watchdog.contains("NotificationManager.IMPORTANCE_DEFAULT"))
        assertTrue(watchdog.contains("CHANNEL_ID = \"syncProblems\""))
        val syncer = File("src/main/java/com/saulhdev/feeder/manager/sync/FeedSyncer.kt").readText()
        assertTrue(syncer.contains("syncChannelId = \"feederSyncNotifications\""))
    }

    @Test
    fun `nothing is posted without permission`() {
        val notify = watchdog.substring(watchdog.indexOf("private fun notify("))
        assertTrue(notify.indexOf("canNotify(context)") in 0 until notify.indexOf("manager.notify("))
        val check = watchdog.substring(watchdog.indexOf("private fun check("), watchdog.indexOf("when (problem)"))
        assertTrue(check.contains("if (!canNotify(context)) return"))
    }

    @Test
    fun `a successful sync takes the notice back`() {
        val syncer = File("src/main/java/com/saulhdev/feeder/manager/sync/FeedSyncer.kt").readText()
        val tail = syncer.substring(syncer.lastIndexOf("SyncLog.finished("))
        assertTrue(tail.contains("if (success) {"))
        assertTrue(tail.contains("SyncWatchdog.clearIfCurrent("))
    }

    @Test
    fun `failing feeds open the page that lists them`() {
        assertTrue(watchdog.contains("MainActivity.navigateIntent(context, Routes.BROKEN_FEEDS)"))
        val nav = File("src/main/java/com/saulhdev/feeder/ui/navigation/NavigationManager.kt").readText()
        val route = nav.substring(nav.indexOf("composable<NavRoute.BrokenFeeds>"))
            .substringBefore("BrokenFeedsPage()")
        assertTrue(route.contains("uriPattern = \"\$NAV_BASE\${Routes.BROKEN_FEEDS}\""))
    }

    @Test
    fun `the count is the page's count`() {
        // The page lists getFailingFeeds(FAILURES_BEFORE_BROKEN); the notice
        // counts the same threshold, among sources that are switched on.
        assertTrue(watchdog.contains("it.isEnabled && it.consecutiveFailures >= FAILURES_BEFORE_BROKEN"))
        val repo = File("src/main/java/com/saulhdev/feeder/data/repository/SourcesRepository.kt").readText()
        assertTrue(repo.contains("fun getFailingFeeds(threshold: Int = FAILURES_BEFORE_BROKEN)"))
        val vm = File("src/main/java/com/saulhdev/feeder/viewmodels/BrokenFeedsViewModel.kt").readText()
        assertTrue(vm.contains("sources.getFailingFeeds()"))
    }

    @Test
    fun `the report says what the check last did`() {
        val report = File("src/main/java/com/saulhdev/feeder/utils/Diagnostics.kt").readText()
        assertTrue(report.contains("\"Problems:     \" + SyncWatchdog.describe(context)"))
    }

    @Test
    fun `permission is asked once, after the welcome and the tour`() {
        val main = File("src/main/java/com/saulhdev/feeder/MainActivity.kt").readText()
        val ask = main.substring(main.indexOf("private fun askForNotificationsOnce()"))
            .substringBefore("\n    }\n")
        assertTrue(ask.contains("prefs.onboardingSeen.get()"))
        assertTrue(ask.contains("prefs.tourSeen.get()"))
        assertTrue(ask.contains("welcomed && toured && !asked"))
        assertTrue(ask.contains("prefs.notificationPermissionAsked.setValue(true)"))
        assertTrue(main.contains("askForNotificationsOnce()\n"))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
    }
}
