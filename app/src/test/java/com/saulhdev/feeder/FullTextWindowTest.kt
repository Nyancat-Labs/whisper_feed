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

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.manager.models.FULL_TEXT_CALL_TIMEOUT_S
import com.saulhdev.feeder.manager.models.FULL_TEXT_TIME_BUDGET_MS
import com.saulhdev.feeder.manager.models.FULL_TEXT_WINDOW_MS
import com.saulhdev.feeder.manager.sync.greader.AccountTally
import com.saulhdev.feeder.manager.sync.greader.MatchStats
import com.saulhdev.feeder.manager.sync.greader.accountSummary
import com.saulhdev.feeder.manager.sync.greader.matchSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private const val NOW = 1_800_000_000_000L
private const val DAY = 24 * 60 * 60_000L

/**
 * One night's full-article runs downloaded 48 MB for a backlog of 2,276
 * pages, nearly all of them for articles that had already gone by unopened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FullTextWindowTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        NeoFeedDb::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    private fun sql(s: String) = db.openHelper.writableDatabase.execSQL(s)

    private fun feed(id: Long, fullText: Boolean) = sql(
        "INSERT INTO Feeds (id, title, description, url, feedImage, lastSync, alternateId, fullTextByDefault, tag, currentlySyncing, isEnabled) " +
            "VALUES ($id, 'f$id', '', 'https://example.org/$id', '', 0, 0, ${if (fullText) 1 else 0}, '', 0, 1)"
    )

    private fun article(uuid: String, feedId: Long, ageDays: Double, read: Boolean = false, saved: Boolean = false) = sql(
        "INSERT INTO Article (uuid, guid, title, plainTitle, plainSnippet, description, feedId, firstSyncedTime, primarySortTime, categories, pinned, bookmarked, readAt, link) " +
            "VALUES ('$uuid', '$uuid', '', '', '', '', $feedId, 0, ${NOW - (ageDays * DAY).toLong()}, '[]', 0, ${if (saved) 1 else 0}, ${if (read) NOW else 0}, 'https://example.org/a/$uuid')"
    )

    private fun waiting(allFeeds: Boolean) = runBlocking {
        db.feedArticleDao().getArticleIdLinks(allFeeds, NOW - FULL_TEXT_WINDOW_MS).first().map { it.uuid }
    }

    @Test
    fun `only unread articles from the last two days, and every saved one`() {
        feed(1, fullText = true)
        feed(2, fullText = false)
        article("new-unread", 1, 0.5)
        article("newer-unread", 1, 0.1)
        article("new-read", 1, 0.5, read = true)
        article("old-unread", 1, 3.0)
        article("old-saved", 1, 30.0, read = true, saved = true)
        article("other-feed", 2, 0.5)
        article("other-saved", 2, 10.0, saved = true)

        assertEquals(listOf("other-saved", "old-saved", "newer-unread", "new-unread").sorted(), waiting(false).sorted())
        assertEquals("saved first, then newest", listOf("old-saved", "other-saved"), waiting(false).take(2).sorted())
        assertEquals("newer-unread", waiting(false)[2])
        assertTrue("the all-feeds switch widens the feeds, not the window", "other-feed" in waiting(true))
        assertTrue("old-unread" !in waiting(true))
    }

    @Test
    fun `a page download has a whole-request limit, and a run stops between pages at eight minutes`() {
        assertEquals(2 * DAY, FULL_TEXT_WINDOW_MS)
        assertEquals(60L, FULL_TEXT_CALL_TIMEOUT_S)
        assertEquals(8 * 60_000L, FULL_TEXT_TIME_BUDGET_MS)
        val parser = File("src/main/java/com/saulhdev/feeder/manager/models/FullTextParser.kt").readText()
        assertTrue(parser.contains(".callTimeout(FULL_TEXT_CALL_TIMEOUT_S, TimeUnit.SECONDS)"))
        val loop = parser.substring(parser.indexOf("for ((i, item) in toFetch.withIndex())"))
        assertTrue(loop.indexOf("FULL_TEXT_TIME_BUDGET_MS") < loop.indexOf("prefetchFullArticle("))
        assertTrue(parser.contains("since = now - FULL_TEXT_WINDOW_MS"))
    }

    @Test
    fun `the history says what a match read`() {
        assertEquals("412 items in 2 pages, 4.8 MB", matchSummary(MatchStats(2, 412, 4_800_000, finished = true)))
        assertEquals("250 items in 1 page, more next time", matchSummary(MatchStats(1, 250, null, finished = false)))
        val line = accountSummary(AccountTally(serverFeeds = 128, matched = 17046, match = MatchStats(2, 412, 4_800_000, true)))
        assertTrue(line, line.contains("; match read 412 items in 2 pages, 4.8 MB"))
        val service = File("src/main/java/com/saulhdev/feeder/manager/sync/service/GoogleReaderService.kt").readText()
        assertTrue(service.contains("val match = step(\"matching\") { mapRemoteIds(auth) }"))
        assertTrue(service.contains("steps = steps, match = match)"))
    }
}
