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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * With an account signed in, every scheduled sync forced a fetch of every
 * feed, so slow feeds never rested, and its history line said only "ok".
 */
class AccountSyncPacingTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `an account sync fetches the feeds only as hard as it was asked to`() {
        val remote = source("manager/sync/service/GoogleReaderService.kt")
        assertTrue(remote.contains("syncFeeds(context = context, feedId = ID_ALL, forceNetwork = forceNetwork)"))
        assertFalse(remote.contains("forceNetwork = true"))
        val local = source("manager/sync/service/LocalRssService.kt")
        assertTrue(local.contains("syncFeeds(context = context, forceNetwork = forceNetwork)"))
        val base = source("manager/sync/service/RssService.kt")
        assertTrue("nobody asking means not forced", base.contains("forceNetwork: Boolean = false,\n        /**"))
    }

    @Test
    fun `every way of asking for all feeds reaches the account`() {
        assertTrue("the schedule", com.saulhdev.feeder.manager.sync.isWholeFeed(com.saulhdev.feeder.data.db.ID_UNSET, ""))
        assertTrue("the panel and the app", com.saulhdev.feeder.manager.sync.isWholeFeed(com.saulhdev.feeder.data.db.ID_ALL, ""))
        assertFalse("one feed", com.saulhdev.feeder.manager.sync.isWholeFeed(42L, ""))
        assertFalse("one tag", com.saulhdev.feeder.manager.sync.isWholeFeed(com.saulhdev.feeder.data.db.ID_ALL, "Tech"))
        assertTrue(source("manager/sync/FeedSyncer.kt").contains("val wholeFeed = isWholeFeed(feedId, feedTag)"))
    }

    @Test
    fun `the worker passes on what was asked, and Sync now asks for everything`() {
        assertTrue(source("manager/sync/FeedSyncer.kt").contains("service.sync(\n                        forceNetwork = forceNetwork,"))
        assertTrue(source("viewmodels/AccountViewModel.kt").contains("requestFeedSync(feedId = ID_ALL, forceNetwork = true, origin = SyncLog.ORIGIN_ACCOUNT)"))
    }

    @Test
    fun `an account sync's history line carries the feed counts`() {
        assertTrue(source("manager/sync/FeedSyncer.kt").contains("is SyncOutcome.Success -> outcome.feeds ?: SyncResult.uncounted"))
        assertTrue(source("manager/sync/service/GoogleReaderService.kt").contains("SyncOutcome.Success(feeds = feeds.copy(account = tally))"))
    }
}
