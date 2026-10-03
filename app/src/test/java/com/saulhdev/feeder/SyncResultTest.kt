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

import android.net.ConnectivityManager
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.errorKind
import com.saulhdev.feeder.utils.restrictsBackground
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException

/**
 * What the sync history says about a finished run, and the syncs that are
 * not started at all because Android would cut them off.
 */
class SyncResultTest {

    @Test
    fun `nothing due is not a failure`() {
        // The 16:06 line: a panel sync two minutes after a scheduled one,
        // every feed already fresh, recorded as "failed", 0s.
        val idle = SyncResult(due = 0)
        assertTrue(idle.ok)
        assertEquals("nothing due", syncOutcome(idle))
    }

    @Test
    fun `a run says how many feeds it reached and how many failed`() {
        assertEquals("ok (120 feeds)", syncOutcome(SyncResult(due = 120)))
        assertEquals("ok (120 feeds, 2 failed)", syncOutcome(SyncResult(due = 120, failed = 2)))
        assertTrue("some feeds failing is still a sync", SyncResult(due = 120, failed = 2).ok)
        assertEquals("ok (1 feed, queued)", syncOutcome(SyncResult(due = 1), listOf("queued")))
    }

    @Test
    fun `the worker's notes come last`() {
        assertEquals(
            "ok (5 feeds, queued, foreground)",
            syncOutcome(SyncResult(due = 5), listOf("queued", "foreground")),
        )
        assertEquals("nothing due (queued)", syncOutcome(SyncResult(due = 0), listOf("queued")))
    }

    @Test
    fun `a broken run names the kind of error and nothing else`() {
        val broken = SyncResult.broken(SocketTimeoutException("timeout reaching https://example.com/feed"))
        assertFalse(broken.ok)
        assertEquals("failed: SocketTimeoutException", syncOutcome(broken))
        assertFalse(syncOutcome(broken).contains("example.com"))
        assertEquals("IllegalStateException", errorKind(IllegalStateException("at https://x.test/a")))
    }

    @Test
    fun `an account sync has no feed count to give`() {
        assertEquals("ok", syncOutcome(SyncResult.uncounted))
        assertEquals("ok (foreground)", syncOutcome(SyncResult.uncounted, listOf("foreground")))
    }

    @Test
    fun `only a plain restriction blocks, an exemption does not`() {
        assertTrue(restrictsBackground(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED))
        assertFalse(restrictsBackground(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED))
        assertFalse(restrictsBackground(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the local sync reports what it did`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        val built = sync.substring(sync.indexOf("result = SyncResult(\n")).substringBefore(")\n")
        assertTrue(built.contains("due = feedsToFetch.size"))
        assertTrue(built.contains("failed = failedFeeds.get()"))
        assertTrue(built.contains("unchanged = unchangedFeeds.get()"))
        assertTrue(sync.contains("result = SyncResult.broken(e)"))
        assertFalse("an empty list is not a failure any more", sync.contains("result = feedsToFetch.isNotEmpty()"))
        // Counted in the failure catch, not the cancellation one before it:
        // leaving the panel is not a feed failing.
        val cancel = sync.indexOf("} catch (e: CancellationException) {\n                                // Not a failure")
        val failure = sync.indexOf("Log.e(TAG, \"Failed to sync")
        val counted = sync.indexOf("failedFeeds.incrementAndGet()")
        assertTrue(cancel in 0 until failure)
        assertTrue(counted > failure)
    }

    @Test
    fun `the history line is built from the result`() {
        val worker = source("manager/sync/FeedSyncer.kt")
        assertTrue(worker.contains("syncOutcome(result, notes)"))
        assertTrue(worker.contains("result = SyncResult.broken(e)"))
    }

    @Test
    fun `a sync Android would cut off is not started`() {
        val worker = source("manager/sync/FeedSyncer.kt")
        val skip = worker.indexOf("if (skipForBlockedData(automatic, dataBlocked, onScreen)) {")
        assertTrue(skip > 0)
        assertTrue(
            "only the automatic ones",
            worker.contains("val dataBlocked = automatic && backgroundMobileDataBlocked(applicationContext)"),
        )
        assertTrue(worker.contains("\"skipped: background mobile data blocked\""))
        assertTrue("before anything is fetched", skip < worker.indexOf("syncFeeds("))
        assertTrue("and before it asks to run in the foreground", skip < worker.indexOf("setForeground("))
        val device = source("utils/DeviceState.kt")
        assertTrue(
            "never on Wi-Fi, where the block does not apply",
            device.contains("!isUnmetered(context) && restrictsBackground(backgroundStatus(context))"),
        )
    }

    @Test
    fun `settings points at the switch that lifts the block`() {
        val hint = source("ui/components/BackgroundDataHint.kt")
        assertTrue(hint.contains("if (wifiOnly || !restrictsBackground(status)) return"))
        assertTrue(hint.contains("Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS"))
        assertTrue("read again on return from Android's settings", hint.contains("LifecycleResumeEffect"))
        val page = source("ui/pages/PreferencesPage.kt")
        val fetching = page.substring(page.indexOf("item(key = R.string.pref_cat_fetching)"))
            .substringBefore("item(key = R.string.pref_cat_feed)")
        assertTrue(fetching.contains("BackgroundDataHint("))
        assertTrue(fetching.contains("wifiOnly = syncWifiOnly"))
    }

    @Test
    fun `the reader's source name is not dressed as a link`() {
        val page = source("ui/pages/ArticlePage.kt")
        val name = page.substring(page.indexOf("WithBidiDeterminedLayoutDirection(paragraph = feedTitle)"))
            .substringBefore("if (authorDate != null)")
        assertFalse(name.contains("linkTextStyle"))
        assertFalse(name.contains("clickable"))
    }
}
