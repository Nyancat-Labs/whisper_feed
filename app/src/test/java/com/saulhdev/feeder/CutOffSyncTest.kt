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

import com.saulhdev.feeder.manager.models.FULL_TEXT_PER_RUN
import com.saulhdev.feeder.manager.models.fullTextBatch
import com.saulhdev.feeder.manager.sync.greader.Outbox
import com.saulhdev.feeder.utils.CUT_OFF_HOLD_MS
import com.saulhdev.feeder.utils.SyncEntry
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.fullTextOutcome
import com.saulhdev.feeder.utils.skipAfterCutOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val MIN = 60_000L
private const val NOW = 1_800_000_000_000L
private const val CURRENT = NOW

/**
 * One evening's report: seven scheduled syncs in twelve minutes, each cut off
 * as Whisper left the screen and started again at once; a full-article
 * backlog of four hundred restarted with them; and twelve reads that waited
 * behind runs that never got as far as sending.
 */
class CutOffSyncTest {

    private fun entry(minutesAgo: Long, outcome: String, origin: String = SyncLog.ORIGIN_SCHEDULED) =
        SyncEntry(NOW - (minutesAgo + 1) * MIN, NOW - minutesAgo * MIN, origin, outcome, "")

    private val running = SyncEntry(CURRENT, 0L, SyncLog.ORIGIN_SCHEDULED, "running", "")
    private val cutOff = "stopped: network changed or dropped"

    private fun skip(vararg past: SyncEntry, automatic: Boolean = true, onScreen: Boolean = false) =
        skipAfterCutOff(automatic, onScreen, listOf(running) + past, NOW, CURRENT)

    @Test
    fun `a background sync cut off a moment ago holds the next one back`() {
        assertTrue(skip(entry(2, cutOff)))
        assertTrue(skip(entry(3, "stopped: device state changed")))
    }

    @Test
    fun `the hold lasts half an hour from the cut-off, skips or not`() {
        assertTrue(skip(entry(2, "skipped: cut off a moment ago"), entry(29, cutOff)))
        assertFalse(skip(entry(31, cutOff)))
        assertEquals(30 * MIN, CUT_OFF_HOLD_MS)
    }

    @Test
    fun `nothing is held on screen, for a sync the reader asked for, or after one that finished`() {
        assertFalse(skip(entry(2, cutOff), onScreen = true))
        assertFalse(skip(entry(2, cutOff), automatic = false))
        assertFalse(skip(entry(2, "ok (120 feeds)"), entry(10, cutOff)))
        assertFalse(skip(entry(2, "stopped: battery went low")))
        assertFalse(skip())
    }

    @Test
    fun `only automatic runs count, so a full-text run or a pull does not hold anything`() {
        assertFalse(skip(entry(2, cutOff, SyncLog.ORIGIN_FULL_TEXT)))
        assertFalse(skip(entry(2, cutOff, SyncLog.ORIGIN_PULL)))
        assertTrue(skip(entry(2, cutOff, SyncLog.ORIGIN_PANEL)))
    }

    @Test
    fun `the worker asks before it starts any work`() {
        val worker = File("src/main/java/com/saulhdev/feeder/manager/sync/FeedSyncer.kt").readText()
        val hold = worker.indexOf("skipAfterCutOff(")
        assertTrue(hold > 0 && hold < worker.indexOf("setForeground(getForegroundInfo())"))
        assertTrue(hold < worker.indexOf("service.sync("))
    }

    @Test
    fun `one full-text run takes fifty, saved runs take all`() {
        val waiting = (1..410).toList()
        assertEquals(FULL_TEXT_PER_RUN, fullTextBatch(waiting, savedOnly = false).size)
        assertEquals((1..50).toList(), fullTextBatch(waiting, savedOnly = false))
        assertEquals(410, fullTextBatch(waiting, savedOnly = true).size)
        assertEquals(listOf(1, 2), fullTextBatch(listOf(1, 2), savedOnly = false))
    }

    @Test
    fun `the backlog goes saved first, then newest`() {
        val dao = File("src/main/java/com/saulhdev/feeder/data/db/dao/FeedArticleDao.kt").readText()
        val query = dao.substringBefore("fun getArticleIdLinks(").substringAfterLast("@Query(")
        assertTrue(query.contains("ORDER BY Article.bookmarked DESC, Article.primarySortTime DESC"))
    }

    @Test
    fun `the history says how many wait for the next runs`() {
        assertEquals("ok (50 fetched, 360 for the next runs, 4.0 MB)", fullTextOutcome(50, 0, 4_000_000, held = 360))
        assertEquals("ok (5 fetched)", fullTextOutcome(5, 0, null))
    }

    @Test
    fun `sending early takes only what the server can place, and leaves the rest`() {
        val outbox = Outbox(read = setOf("m1", "u1"), unread = setOf("m2"), star = setOf("u2"))
        val early = outbox.only(setOf("m1", "m2"))
        assertEquals(Outbox(read = setOf("m1"), unread = setOf("m2")), early)
        assertEquals(Outbox(read = setOf("u1"), star = setOf("u2")), outbox.without(early))
    }

    @Test
    fun `only the early sending is limited to matched articles`() {
        val service = File("src/main/java/com/saulhdev/feeder/manager/sync/service/GoogleReaderService.kt").readText()
        val push = service.substring(service.indexOf("private suspend fun pushChanges(")).substringBefore("\n    }\n")
        assertTrue(push.contains("val outbox = if (matchedOnly) waiting.only(remoteIds.keys) else waiting"))
        assertTrue(service.contains("matchedOnly: Boolean = false"))
        assertEquals(1, Regex("matchedOnly = true").findAll(service).count())
    }
}
