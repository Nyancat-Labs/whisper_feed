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

import com.saulhdev.feeder.manager.sync.BACKGROUND_FEEDS_BUDGET_MS
import com.saulhdev.feeder.manager.sync.SYNC_TIME_BUDGET_MS
import com.saulhdev.feeder.manager.sync.backgroundFeedDeadline
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 5 October: Android 17 refused every background sync the foreground, and in
 * the background a night's run on the charger took 235 s over the feeds and
 * 243 s matching, and was stopped at eight minutes. A background run is now a
 * lighter one; a run in the foreground still does everything.
 */
class LighterBackgroundSyncTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `a background run stops starting feeds halfway through its time`() {
        assertNull(backgroundFeedDeadline(background = false, nowMs = 1_000L))
        assertEquals(1_000L + BACKGROUND_FEEDS_BUDGET_MS, backgroundFeedDeadline(background = true, nowMs = 1_000L))
        assertEquals(SYNC_TIME_BUDGET_MS / 2, BACKGROUND_FEEDS_BUDGET_MS)
    }

    @Test
    fun `a background run matches a quarter as much`() {
        val service = source("manager/sync/service/GoogleReaderService.kt")
        assertTrue(service.contains("const val MAP_BACKGROUND_PAGES = 2"))
        assertTrue(service.contains("const val MAP_MAX_PAGES = 8"))
        assertTrue(service.contains("mapRemoteIds(auth, maxPages = if (background) MAP_BACKGROUND_PAGES else MAP_MAX_PAGES)"))
        assertTrue(service.contains("while (pages < maxPages) {"))
    }

    @Test
    fun `background is a run Android kept out of the foreground`() {
        val worker = source("manager/sync/FeedSyncer.kt")
        assertTrue(worker.contains("val background = !inForeground"))
        assertTrue(worker.contains("background = background,"))
        assertTrue(worker.contains("feedDeadlineMs = backgroundFeedDeadline(background),"))
        assertTrue(source("manager/sync/service/LocalRssService.kt").contains("feedDeadlineMs = backgroundFeedDeadline(background)"))
        assertTrue(source("manager/sync/service/GoogleReaderService.kt").contains("feedDeadlineMs = backgroundFeedDeadline(background),"))
    }

    @Test
    fun `feeds past the deadline wait, oldest first, and are counted`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        val start = sync.indexOf("if (feedDeadlineMs != null && fetchStarted >= feedDeadlineMs) {")
        assertTrue(start > 0)
        assertTrue("checked before the feed is marked as syncing", start < sync.indexOf("// Mark as syncing START"))
        assertTrue(sync.contains("if (feedDeadlineMs != null) feeds.sortedBy { it.lastSync } else feeds"))
        assertTrue(sync.contains("due = feedsToFetch.size - deferredFeeds.get(),"))
    }

    @Test
    fun `the history says what was left`() {
        assertEquals(
            "ok (60 feeds, 66 left for the next sync)",
            syncOutcome(SyncResult(due = 60, deferred = 66)),
        )
    }
}
