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
import com.saulhdev.feeder.manager.sync.greader.MatchStats
import com.saulhdev.feeder.manager.sync.greader.matchSummary
import com.saulhdev.feeder.manager.sync.service.MAP_OVERLAP_MS
import com.saulhdev.feeder.manager.sync.withTrace
import com.saulhdev.feeder.utils.StepTrace
import com.saulhdev.feeder.utils.SyncEntry
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.resumeFrom
import com.saulhdev.feeder.utils.skipAfterCutOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val NOW = 1_800_000_000_000L

/**
 * 2 October: a sync on mobile data used all eight minutes and said nothing
 * about where, and each match read three hours of the server's articles,
 * whole, for about 5.5 MB a sync.
 */
class StoppedSyncTraceTest {

    private var clock = 0L
    private fun trace() = StepTrace { clock }

    @Test
    fun `a stopped sync says which steps it finished and which it was in`() {
        val t = trace()
        assertNull("nothing traced, nothing said", t.describe())
        t.started("sign-in"); clock += 500; t.finished("sign-in", 500)
        t.started("feeds"); clock += 41_000; t.finished("feeds", 41_000)
        t.started("matching"); clock += 380_000
        assertEquals("reached sign-in 0.5s, feeds 41s; stopped in matching after 380s", t.describe())
    }

    @Test
    fun `the line starts with the stop, so the hold and the pick-up still read it`() {
        val t = trace()
        t.started("feeds"); clock += 9_000; t.finished("feeds", 9_000)
        val line = withTrace("stopped: network changed or dropped", t)
        assertEquals("stopped: network changed or dropped; reached feeds 9.0s", line)
        assertEquals(OUT_OF_TIME_OUTCOME, withTrace(OUT_OF_TIME_OUTCOME, trace()))

        val cut = SyncEntry(NOW - 3 * 60_000L, NOW - 60_000L, SyncLog.ORIGIN_SCHEDULED, line, "")
        val running = SyncEntry(NOW, 0L, SyncLog.ORIGIN_SCHEDULED, "running", "")
        assertTrue(skipAfterCutOff(automatic = true, onScreen = false, entries = listOf(running, cut), nowMs = NOW, current = NOW))
        assertEquals(NOW - 3 * 60_000L, resumeFrom(listOf(running, cut), NOW, 2 * 60 * 60_000L))
    }

    @Test
    fun `the worker writes the trace on both ways a run is stopped`() {
        val worker = File("src/main/java/com/saulhdev/feeder/manager/sync/FeedSyncer.kt").readText()
        assertTrue(worker.contains("withTrace(OUT_OF_TIME_OUTCOME, trace)"))
        assertTrue(worker.contains("withTrace(\"stopped: \$why\", trace)"))
        assertTrue(worker.contains("trace = trace,"))
        val service = File("src/main/java/com/saulhdev/feeder/manager/sync/service/GoogleReaderService.kt").readText()
        assertTrue(service.contains("trace?.started(name)"))
        assertTrue(service.contains("trace?.finished(name, ms)"))
    }

    @Test
    fun `matching overlaps the last by ten minutes, and waits for Wi-Fi`() {
        assertEquals(10 * 60_000L, MAP_OVERLAP_MS)
        assertEquals("nothing, waiting for Wi-Fi", matchSummary(MatchStats(0, 0, null, finished = false, waitingForWifi = true)))
        val service = File("src/main/java/com/saulhdev/feeder/manager/sync/service/GoogleReaderService.kt").readText()
        val map = service.substring(service.indexOf("private suspend fun mapRemoteIds(")).substringBefore("\n    }\n")
        val wait = map.indexOf("if (!isUnmetered(context)) {")
        assertTrue("before anything is downloaded", wait > 0 && wait < map.indexOf("api.contentsPage("))
        assertTrue(map.contains("waitingForWifi = true"))
        assertTrue("and the early sending still runs first", service.indexOf("pushChanges(auth, token, matchedOnly = true)") < service.indexOf("mapRemoteIds(auth, maxPages ="))
    }
}
