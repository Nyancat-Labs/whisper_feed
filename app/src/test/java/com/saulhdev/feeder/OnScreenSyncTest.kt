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

import com.saulhdev.feeder.utils.OPENED_SYNC_MIN_MS
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.openedSyncDue
import com.saulhdev.feeder.utils.skipForBlockedData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val MIN = 60_000L
private const val NOW = 1_800_000_000_000L

/**
 * An afternoon on mobile data with background data off: six automatic syncs
 * skipped, five of them while Whisper was on screen, and nothing fetched from
 * lunchtime until the reader pulled.
 */
class OnScreenSyncTest {

    @Test
    fun `blocked background data holds a sync back only in the background`() {
        assertTrue(skipForBlockedData(automatic = true, blocked = true, onScreen = false))
        assertFalse("on screen, Android allows it", skipForBlockedData(automatic = true, blocked = true, onScreen = true))
        assertFalse(skipForBlockedData(automatic = true, blocked = false, onScreen = false))
        assertFalse("a pull always runs", skipForBlockedData(automatic = false, blocked = true, onScreen = false))
    }

    @Test
    fun `opening the app syncs a feed older than one interval`() {
        assertTrue(openedSyncDue(NOW, NOW - 31 * MIN, "0.5"))
        assertFalse(openedSyncDue(NOW, NOW - 29 * MIN, "0.5"))
        assertTrue(openedSyncDue(NOW, NOW - 61 * MIN, "1"))
        assertFalse(openedSyncDue(NOW, NOW - 59 * MIN, "1"))
    }

    @Test
    fun `never more often than every quarter hour, and never when set to manual`() {
        assertFalse(openedSyncDue(NOW, NOW - OPENED_SYNC_MIN_MS + 1, "0.1"))
        assertTrue(openedSyncDue(NOW, NOW - OPENED_SYNC_MIN_MS, "0.1"))
        assertFalse("manual", openedSyncDue(NOW, NOW - 24 * 60 * MIN, "0"))
        assertFalse("no sources", openedSyncDue(NOW, null, "1"))
        assertTrue("never synced", openedSyncDue(NOW, 0L, "1"))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `an on-screen sync with blocked data runs as a foreground task`() {
        val worker = source("manager/sync/FeedSyncer.kt").substringAfter("override suspend fun doWork()")
        assertTrue(worker.contains("val foreground = runInForeground(origin, dataBlocked, onScreen, pluggedIn)"))
        assertTrue(com.saulhdev.feeder.manager.sync.runInForeground(SyncLog.ORIGIN_SCHEDULED, dataBlocked = true, onScreen = false))
        assertTrue(
            "decided before the skip is asked",
            worker.indexOf("val dataBlocked =") < worker.indexOf("skipForBlockedData("),
        )
    }

    @Test
    fun `opening the app asks through the automatic request, which keeps the switches`() {
        assertTrue(SyncLog.ORIGIN_OPENED in SyncLog.AUTOMATIC_ORIGINS)
        val activity = source("MainActivity.kt")
        val resume = activity.substring(activity.indexOf("override fun onResume()")).substringBefore("\n    }\n")
        assertTrue(resume.contains("syncIfStale()"))
        assertTrue(activity.contains("requestAutomaticFeedSync(origin = SyncLog.ORIGIN_OPENED)"))
        val request = source("manager/sync/FeedSyncer.kt").substringAfter("fun requestAutomaticFeedSync(")
        assertTrue(request.contains("SyncLog.ORIGIN_KEY to origin,"))
        assertTrue(request.contains(".setRequiresCharging(prefs.syncOnlyWhenCharging.getValue())"))
    }
}
