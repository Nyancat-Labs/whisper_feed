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

import com.saulhdev.feeder.manager.sync.OUT_OF_TIME_OUTCOME
import com.saulhdev.feeder.manager.sync.SYNC_TIME_BUDGET_MS
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderIds
import com.saulhdev.feeder.manager.sync.greader.StreamItem
import com.saulhdev.feeder.manager.sync.service.DAY_MS
import com.saulhdev.feeder.manager.sync.service.MAP_OVERLAP_MS
import com.saulhdev.feeder.manager.sync.service.matchProgress
import com.saulhdev.feeder.manager.sync.service.matchSince
import com.saulhdev.feeder.utils.SyncEntry
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.resumeFrom
import com.saulhdev.feeder.utils.skipAfterCutOff
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress

private const val NOW = 1_800_000_000_000L
private const val HOUR = 60 * 60_000L

/**
 * One morning on the charger and Wi-Fi: three syncs in a row stopped at
 * Android's ten minutes, each matching the same seventeen hours of articles
 * from the start, because where the match got to was saved only at the end.
 */
class ResumableMatchTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `a match starts an hour before where the last got to, and never more than two days back`() {
        assertEquals(NOW - 3 * HOUR - MAP_OVERLAP_MS, matchSince(NOW - 3 * HOUR, NOW))
        assertEquals(NOW - 2 * DAY_MS, matchSince(NOW - 5 * DAY_MS, NOW))
        assertEquals(NOW - 2 * DAY_MS, matchSince(0L, NOW))
    }

    @Test
    fun `a page reaches as far as its newest item, by either clock`() {
        val page = listOf(
            StreamItem(crawlTimeMsec = "1000"),
            StreamItem(timestampUsec = "5000000"),
            StreamItem(crawlTimeMsec = "3000"),
            StreamItem(),
        )
        assertEquals(5000L, matchProgress(page))
        assertNull(matchProgress(listOf(StreamItem(), StreamItem(crawlTimeMsec = "x"))))
        assertNull(matchProgress(emptyList()))
    }

    @Test
    fun `the match asks oldest first, and the item's time is read`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val queries = mutableListOf<String>()
        server.createContext("/") { ex ->
            queries += ex.requestURI.rawQuery.orEmpty()
            val body = """{"items":[{"id":"tag:google.com,2005:reader/item/000000000000000a",
                "crawlTimeMsec":"1759300000123","timestampUsec":"1759300000123456",
                "alternate":[{"href":"https://example.com/story"}]}],"continuation":"next"}"""
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = GoogleReaderApi("http://127.0.0.1:${server.address.port}/api/greader.php")
            val (items, next) = runBlocking {
                api.contentsPage("auth", GoogleReaderIds.STREAM_READING_LIST, 250, NOW, null, oldestFirst = true)
            }
            assertEquals(1759300000123L, items.single().crawledAt())
            assertEquals("next", next)
            assertTrue(queries.toString(), queries.single().split('&').contains("r=o"))
            runBlocking { api.contentsPage("auth", "feed/12", 250, null, null) }
            assertFalse(queries.toString(), queries.last().split('&').contains("r=o"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `where the match got to is saved after every page, and the end only when it ran out`() {
        val service = source("manager/sync/service/GoogleReaderService.kt")
        val map = service.substring(service.indexOf("private suspend fun mapRemoteIds(")).substringBefore("\n    }\n")
        assertTrue(map.contains("since, continuation, oldestFirst = true"))
        assertTrue(map.contains("val since = matchSince(last, startedAt)"))
        val perPage = map.indexOf("reached?.let { GoogleReaderState.setMappedAt(context, it) }")
        val atEnd = map.indexOf("if (finished) GoogleReaderState.setMappedAt(context, startedAt)")
        assertTrue(perPage > 0 && atEnd > perPage)
        assertEquals("the end time is written in one place only", 1, Regex("setMappedAt\\(context, startedAt\\)").findAll(map).count())
        assertTrue(map.indexOf("newly += queueNewlyMatched(before)") < perPage)
    }

    @Test
    fun `a sync stops itself two minutes short of Android's limit`() {
        assertEquals(8 * 60_000L, SYNC_TIME_BUDGET_MS)
        val worker = source("manager/sync/FeedSyncer.kt")
        val budget = worker.indexOf("withTimeoutOrNull(SYNC_TIME_BUDGET_MS) {")
        assertTrue(budget > 0)
        assertTrue("the account sync is inside it", worker.indexOf("service.sync(") > budget)
        assertTrue("and the feeds", worker.indexOf("syncFeeds(") > budget)
        assertTrue(worker.contains("return if (automatic) Result.retry() else Result.success(output)"))
    }

    @Test
    fun `the next sync picks up after one that stopped itself, and is not held back`() {
        val stopped = SyncEntry(NOW - 9 * 60_000L, NOW - 60_000L, SyncLog.ORIGIN_SCHEDULED, OUT_OF_TIME_OUTCOME, "")
        val running = SyncEntry(NOW, 0L, SyncLog.ORIGIN_SCHEDULED, "running", "")
        assertEquals(NOW - 9 * 60_000L, resumeFrom(listOf(running, stopped), NOW, 2 * HOUR))
        assertFalse(skipAfterCutOff(automatic = true, onScreen = false, entries = listOf(running, stopped), nowMs = NOW, current = NOW))
    }
}
