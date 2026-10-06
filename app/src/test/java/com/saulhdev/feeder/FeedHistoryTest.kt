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

import androidx.work.NetworkType
import com.saulhdev.feeder.manager.models.fullTextNetwork
import com.saulhdev.feeder.utils.FeedFetch
import com.saulhdev.feeder.utils.FeedHistory
import com.saulhdev.feeder.utils.FeedHistoryCodec
import com.saulhdev.feeder.utils.FetchKind
import com.saulhdev.feeder.utils.MAX_RECHECK_MS
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.countsAgainstSource
import com.saulhdev.feeder.utils.dueByPace
import com.saulhdev.feeder.utils.fullTextOutcome
import com.saulhdev.feeder.utils.recheckAfterMs
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val MIN = 60_000L
private const val HOUR = 60 * MIN
private const val NOW = 1_800_000_000_000L

/**
 * Full articles waiting for Wi-Fi, failures that were the network's fault,
 * each feed's own history, and slow feeds left to rest.
 */
class FeedHistoryTest {

    @Test
    fun `full articles wait for Wi-Fi unless both switches allow mobile data`() {
        assertEquals(NetworkType.UNMETERED, fullTextNetwork(syncOnlyOnWifi = false, fullTextOnMobile = false))
        assertEquals(NetworkType.UNMETERED, fullTextNetwork(syncOnlyOnWifi = true, fullTextOnMobile = true))
        assertEquals(NetworkType.CONNECTED, fullTextNetwork(syncOnlyOnWifi = false, fullTextOnMobile = true))
    }

    @Test
    fun `an answer from the server always counts, silence only with a network`() {
        assertTrue(countsAgainstSource(httpCode = 503, hadNetwork = false))
        assertTrue(countsAgainstSource(httpCode = null, hadNetwork = true))
        assertFalse("a tunnel is not the feed's fault", countsAgainstSource(httpCode = null, hadNetwork = false))
    }

    @Test
    fun `a feed keeps its last five fetches, newest first`() {
        var list = emptyList<FeedFetch>()
        repeat(8) { list = FeedHistoryCodec.add(list, FeedFetch(NOW + it, FetchKind.Unchanged)) }
        assertEquals(FeedHistory.MAX_PER_FEED, list.size)
        assertEquals(NOW + 7, list.first().at)
    }

    @Test
    fun `the record survives a round trip, and junk lines are dropped`() {
        val list = listOf(
            FeedFetch(NOW, FetchKind.New, "3", 45_000),
            FeedFetch(NOW - HOUR, FetchKind.Failed, "503"),
            FeedFetch(NOW - 2 * HOUR, FetchKind.NoNetwork),
        )
        assertEquals(list, FeedHistoryCodec.decode(FeedHistoryCodec.encode(list)))
        assertEquals(1, FeedHistoryCodec.decode("1\tNew\t2\t10\nnot a line\n5\tWhat\t\t0").size)
    }

    @Test
    fun `each fetch reads as what happened`() {
        val clock = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val at = 12 * HOUR
        assertEquals("12:00 new 3 45 KB", FeedHistoryCodec.describe(FeedFetch(at, FetchKind.New, "3", 45_000), clock))
        assertEquals("12:00 unchanged", FeedHistoryCodec.describe(FeedFetch(at, FetchKind.Unchanged, bytes = 0), clock))
        assertEquals("12:00 failed 503", FeedHistoryCodec.describe(FeedFetch(at, FetchKind.Failed, "503"), clock))
        assertEquals("12:00 no network", FeedHistoryCodec.describe(FeedFetch(at, FetchKind.NoNetwork), clock))
    }

    @Test
    fun `a feed with no record says so`() {
        assertTrue(FeedHistoryCodec.line("Engadget", emptyList()).endsWith("no fetches recorded"))
    }

    @Test
    fun `the summary totals every feed's newest download and names the heaviest`() {
        val lines = FeedHistoryCodec.summary(
            listOf(
                "Big" to listOf(FeedFetch(NOW, FetchKind.Unchanged, bytes = 0), FeedFetch(NOW - HOUR, FetchKind.New, "2", 900_000)),
                "Small" to listOf(FeedFetch(NOW, FetchKind.New, "1", 100_000)),
                // Two sources can share a name, and both count.
                "Small" to listOf(FeedFetch(NOW, FetchKind.New, "1", 100_000)),
                "Never" to emptyList(),
            ),
        )
        assertEquals("One download of every feed: 1.1 MB (measured for 3 of 4)", lines[0])
        assertTrue(lines[1].startsWith("Heaviest: Big 900 KB, Small 100 KB"))
    }

    @Test
    fun `a slow feed rests for a quarter of its gap, never more than six hours`() {
        assertEquals("unknown pace: every time", 0L, recheckAfterMs(null))
        assertEquals(30 * MIN, recheckAfterMs(2f))
        assertEquals(6 * HOUR, recheckAfterMs(24f))
        assertEquals(MAX_RECHECK_MS, recheckAfterMs(24f * 7))
        assertEquals(90_000L, recheckAfterMs(0.1f))
    }

    @Test
    fun `a feed is due when never fetched or rested long enough`() {
        assertTrue(dueByPace(NOW, lastSyncMs = 0L, paceHours = 24f))
        assertFalse(dueByPace(NOW, lastSyncMs = NOW - HOUR, paceHours = 24f))
        assertTrue(dueByPace(NOW, lastSyncMs = NOW - 6 * HOUR, paceHours = 24f))
        assertTrue(dueByPace(NOW, lastSyncMs = NOW - MIN, paceHours = null))
    }

    @Test
    fun `the sync line says what was skipped and why`() {
        assertEquals(
            "ok (80 feeds, 30 unchanged, 1 failed, 2 without network, 38 not due yet, 1.2 MB)",
            syncOutcome(SyncResult(due = 80, failed = 1, unchanged = 30, offline = 2, resting = 38, bytes = 1_200_000)),
        )
        assertEquals("nothing due (120 not due yet)", syncOutcome(SyncResult(due = 0, resting = 120)))
        assertEquals(
            "ok (3 fetched, 12 left for later, no network)",
            fullTextOutcome(fetched = 3, failed = 0, bytes = null, unreached = 12),
        )
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the switch is in Settings and off by default`() {
        val prefs = source("data/content/FeedPreferences.kt")
        val pref = prefs.substring(prefs.indexOf("var fullTextOnMobile = BooleanPref(")).substringBefore("\n    )")
        assertTrue(pref.contains("defaultValue = false"))
        val page = source("ui/pages/PreferencesPage.kt")
        val fetching = page.substring(page.indexOf("val fetchingPrefs = listOf(")).substringBefore(")\n")
        assertTrue(fetching.contains("prefs.fullTextOnMobile"))
        val full = source("manager/models/FullTextParser.kt")
        assertTrue(full.contains("fullTextOnMobile = prefs.fullTextOnMobile.getValue()"))
        val work = full.substring(full.indexOf("override suspend fun doWork()"))
        val guard = work.indexOf("!prefs.fullTextOnMobile.getValue() || backgroundMobileDataBlocked(context)")
        assertTrue("checked in the worker too, before anything is read", guard in 0 until work.indexOf("val toFetch"))
    }

    @Test
    fun `a feed is only blamed when it could have answered`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        val failure = sync.substring(sync.indexOf("Log.e(TAG, \"Failed to sync"))
            .substringBefore("\n                            }\n")
        val check = failure.indexOf("if (countsAgainstSource(code, whisperHasNetwork(context))) {")
        assertTrue(check >= 0)
        assertTrue(check < failure.indexOf("feedsRepo.recordFailure(feed.id)"))
        assertTrue(failure.contains("offlineFeeds.incrementAndGet()"))
        assertTrue(failure.contains("FetchKind.NoNetwork"))
    }

    @Test
    fun `an article is only blamed when it could have answered`() {
        val full = source("manager/models/FullTextParser.kt")
        val prefetch = full.substring(full.indexOf("private suspend fun prefetchFullArticle(")).substringBefore("\n}\n")
        val unreached = prefetch.indexOf("return Prefetch.Unreached")
        assertTrue(unreached >= 0 && unreached < prefetch.indexOf("writeText("))
        val work = full.substring(full.indexOf("override suspend fun doWork()"))
        assertTrue("the rest are left, not tried", work.contains("unreached = toFetch.size - i\n                        break"))
    }

    @Test
    fun `every fetch of a feed is recorded, with its own size`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        assertTrue(sync.contains(".eventListenerFactory(ByteCounter.factory)"))
        assertTrue(sync.contains("getResponse(url = feedSql.url, forceNetwork = forceNetwork, byteCounter = counter)"))
        assertTrue(sync.contains("FetchKind.New, \"\${fetched.newArticles}\", counter.bytes"))
        assertTrue(sync.contains("FetchKind.Unchanged, bytes = counter.bytes"))
        assertTrue(sync.contains("FetchKind.Same, bytes = counter.bytes"))
        val parser = source("manager/models/FeedParser.kt")
        assertTrue(parser.contains(".apply { byteCounter?.let { tag(ByteCounter::class.java, it) } }"))
        val report = source("utils/Diagnostics.kt")
        assertTrue(report.contains("FeedHistory.keepOnly(context, sources.map { it.id }.toSet())"))
        assertTrue(report.contains("FeedHistoryCodec.line(feed.title, fetches)"))
    }

    @Test
    fun `only the whole-list syncs nobody asked for are paced`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        assertTrue(sync.contains("val paced = !forceNetwork && feedId <= 0 && feedTag.isEmpty()"))
        assertTrue(sync.contains("candidates.filter { dueByPace(nowMs, it.lastSync.toEpochMilliseconds(), pace[it.id]) }"))
    }
}
