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

import com.saulhdev.feeder.utils.SyncEntry
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.resumeFrom
import com.saulhdev.feeder.utils.syncIntervalMs
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val MIN = 60_000L
private const val HOUR = 60 * MIN
private const val NOW = 1_800_000_000_000L

/**
 * One evening's restarts each fetched the feeds from the top: Science
 * Magazine at 20:38, 20:44 and 20:50, identical each time.
 */
class ResumeSyncTest {

    /** A run that started [startAgo] minutes ago and lasted [minutes]. */
    private fun run(startAgo: Long, minutes: Long, outcome: String, origin: String = SyncLog.ORIGIN_SCHEDULED) =
        SyncEntry(NOW - startAgo * MIN, NOW - (startAgo - minutes) * MIN, origin, outcome, "")

    private val running = SyncEntry(NOW, 0L, SyncLog.ORIGIN_SCHEDULED, "running", "")
    private val cut = "stopped: network changed or dropped"

    private fun resume(vararg past: SyncEntry, interval: Long = 2 * HOUR) =
        resumeFrom(listOf(running) + past, NOW, interval)

    @Test
    fun `after a cut-off, the sync picks up from where that one started`() {
        assertEquals(NOW - 6 * MIN, resume(run(6, 2, cut)))
    }

    @Test
    fun `a string of cut-offs reaches back to the first of them`() {
        assertEquals(
            NOW - 12 * MIN,
            resume(run(3, 1, cut), run(6, 2, "skipped: cut off a moment ago"), run(9, 2, "stopped: device state changed"), run(12, 2, cut)),
        )
    }

    @Test
    fun `a finished sync ends the string`() {
        assertEquals(NOW - 6 * MIN, resume(run(6, 2, cut), run(20, 1, "ok (120 feeds)"), run(30, 2, cut)))
        assertNull(resume(run(6, 1, "ok (120 feeds)"), run(12, 2, cut)))
        assertNull(resume())
    }

    @Test
    fun `never further back than one interval, and not at all once that has passed`() {
        assertEquals(NOW - 2 * HOUR, resume(run(10, 2, cut), run(150, 2, cut)))
        assertNull(resume(run(130, 2, cut)))
    }

    @Test
    fun `full-text runs and pulls are not syncs it picks up from`() {
        assertNull(resume(run(6, 2, "stopped after 68", SyncLog.ORIGIN_FULL_TEXT)))
        assertNull(resume(run(6, 2, cut, SyncLog.ORIGIN_PULL)))
        assertEquals(NOW - 6 * MIN, resume(run(6, 2, cut, SyncLog.ORIGIN_PANEL)))
    }

    @Test
    fun `one interval from the setting, an hour when syncing is manual`() {
        assertEquals(2 * HOUR, syncIntervalMs("2"))
        assertEquals(HOUR / 2, syncIntervalMs("0.5"))
        assertEquals(HOUR, syncIntervalMs("0"))
        assertEquals(HOUR, syncIntervalMs("x"))
    }

    @Test
    fun `the history says how many were done before the cut-off`() {
        assertEquals(
            "ok (40 feeds, 80 done before the cut-off)",
            syncOutcome(SyncResult(due = 40, resumed = 80)),
        )
    }

    @Test
    fun `only the syncs nobody forced pick up, and the setting is read`() {
        val sync = File("src/main/java/com/saulhdev/feeder/manager/sync/RssLocalSync.kt").readText()
        assertTrue(sync.contains("val resume = if (paced) {\n                    resumeFrom(SyncLog.entries(context), nowMs, syncIntervalMs(prefs.syncFrequency.getValue()))"))
        assertTrue(sync.contains("byPace.filter { it.lastSync.toEpochMilliseconds() < resume }"))
        assertTrue(sync.contains("val paced = !forceNetwork && feedId <= 0 && feedTag.isEmpty()"))
    }
}
