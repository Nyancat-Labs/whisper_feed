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

import com.saulhdev.feeder.utils.AgeUnit
import com.saulhdev.feeder.utils.MIN_OVERDUE_MS
import com.saulhdev.feeder.utils.SyncFreshness
import com.saulhdev.feeder.utils.msUntilNextMinute
import com.saulhdev.feeder.utils.syncFreshness
import com.saulhdev.feeder.utils.syncOverdueAfterMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val MIN = 60_000L
private const val HOUR = 60 * MIN
private const val NOW = 1_800_000_000_000L

/**
 * The "Updated 12m ago" line under the chips, which is where a silent
 * background sync shows that it happened.
 */
class SyncFreshnessTest {

    private val day = syncOverdueAfterMs("1")

    @Test
    fun `no enabled sources says nothing`() {
        assertNull(syncFreshness(NOW, lastSyncMs = null, syncing = true, overdueAfterMs = day))
    }

    @Test
    fun `a running sync says updating whatever the age`() {
        assertEquals(SyncFreshness.Updating, syncFreshness(NOW, NOW - 5 * 24 * HOUR, true, day))
        assertEquals(SyncFreshness.Updating, syncFreshness(NOW, 0L, true, day))
    }

    @Test
    fun `sources never fetched say so`() {
        assertEquals(SyncFreshness.NeverUpdated, syncFreshness(NOW, 0L, false, day))
    }

    @Test
    fun `the age is the newest fetch floored`() {
        val f = syncFreshness(NOW, NOW - 12 * MIN - 59_000, false, day) as SyncFreshness.Updated
        assertEquals(AgeUnit.MINUTES, f.age.unit)
        assertEquals(12, f.age.count)
        assertFalse(f.overdue)
    }

    @Test
    fun `under a minute is just now`() {
        val f = syncFreshness(NOW, NOW - 30_000, false, day) as SyncFreshness.Updated
        assertEquals(AgeUnit.NOW, f.age.unit)
    }

    @Test
    fun `overdue only past the threshold`() {
        val at = syncFreshness(NOW, NOW - day, false, day) as SyncFreshness.Updated
        val past = syncFreshness(NOW, NOW - day - 1, false, day) as SyncFreshness.Updated
        assertFalse(at.overdue)
        assertTrue(past.overdue)
    }

    @Test
    fun `overdue is three intervals and never under a day`() {
        assertEquals(24 * HOUR, MIN_OVERDUE_MS)
        assertEquals(MIN_OVERDUE_MS, syncOverdueAfterMs("0.5"))
        assertEquals(MIN_OVERDUE_MS, syncOverdueAfterMs("6"))
        assertEquals(36 * HOUR, syncOverdueAfterMs("12"))
    }

    @Test
    fun `an unreadable frequency falls back to hourly, not to never late`() {
        assertEquals(MIN_OVERDUE_MS, syncOverdueAfterMs(""))
        assertEquals(MIN_OVERDUE_MS, syncOverdueAfterMs("0"))
        assertEquals(MIN_OVERDUE_MS, syncOverdueAfterMs("often"))
    }

    @Test
    fun `the ticker wakes on the minute`() {
        assertEquals(60_000L, msUntilNextMinute(NOW))
        assertEquals(1L, msUntilNextMinute(NOW + 59_999))
        assertEquals(59_000L, msUntilNextMinute(NOW + 1_000))
    }

    private val line =
        File("src/main/java/com/saulhdev/feeder/ui/overlay/SyncFreshnessLine.kt").readText()

    @Test
    fun `both feeds draw the line in their scrolling header`() {
        listOf(
            "src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt",
            "src/main/java/com/saulhdev/feeder/ui/overlay/FeedScaffold.kt",
        ).forEach { path ->
            val src = File(path).readText()
            val start = src.indexOf("val header: @Composable () -> Unit = {")
            assertTrue("$path has no header", start > 0)
            // The header lambda, up to the brace that closes it.
            var depth = 0
            var end = src.indexOf('{', start)
            do {
                when (src[end]) { '{' -> depth++; '}' -> depth-- }
                end++
            } while (depth > 0)
            assertTrue("$path header has no freshness line", src.substring(start, end).contains("SyncFreshnessLine()"))
        }
    }

    @Test
    fun `the panel line shows while searching`() {
        val src = File("src/main/java/com/saulhdev/feeder/ui/overlay/FeedScaffold.kt").readText()
        val search = src.indexOf("if (!isSearching) {\n                        CategoryChipRow")
        assertTrue(search > 0)
        val block = src.substring(search, src.indexOf("SyncFreshnessLine()"))
        // Every brace the search check opens is closed before the line.
        assertEquals(block.count { it == '{' }, block.count { it == '}' })
    }

    @Test
    fun `the line reads the newest successful fetch and the running syncs`() {
        assertTrue(line.contains("sources.newestSync"))
        assertTrue(line.contains("sources.isSyncing"))
        val dao = File("src/main/java/com/saulhdev/feeder/data/db/dao/FeedSourceDao.kt").readText()
        assertTrue(dao.contains("SELECT MAX(lastSync) FROM Feeds WHERE isEnabled IS 1 AND removedAt = 0"))
    }

    @Test
    fun `the clock restarts when a sync starts or lands`() {
        assertTrue(line.contains("LaunchedEffect(newestSync, syncing)"))
    }
}
