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

import com.saulhdev.feeder.manager.sync.runInForeground
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.articleOverlap
import com.saulhdev.feeder.utils.duplicateReason
import com.saulhdev.feeder.utils.sameArticleGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 3 October: six syncs on Wi-Fi stopped as the reader looked away, each a
 * minute or two into the feeds, and twenty-five reads sat unsent; and two NPR
 * feeds were reported as "subscribed twice" when they were two feeds that
 * share most of their stories.
 */
class ForegroundAndOverlapTest {

    @Test
    fun `a sync begun with Whisper on screen keeps going when the reader leaves`() {
        assertTrue(runInForeground(SyncLog.ORIGIN_PANEL, dataBlocked = false, onScreen = true))
        assertTrue(runInForeground(SyncLog.ORIGIN_SCHEDULED, dataBlocked = false, onScreen = true))
        assertTrue("a pull, as before", runInForeground(SyncLog.ORIGIN_PULL, dataBlocked = false, onScreen = false))
        assertTrue("blocked mobile data, as before", runInForeground(SyncLog.ORIGIN_SCHEDULED, dataBlocked = true, onScreen = false))
        assertFalse("one begun in the background stays there", runInForeground(SyncLog.ORIGIN_SCHEDULED, dataBlocked = false, onScreen = false))
    }

    /**
     * 4 October: overnight on the charger the background syncs took 129-203 s
     * for the feeds and 0.6 s an item matching; begun on screen, 10-32 s and
     * 0.05 s. One ran 426 s of its 480.
     */
    @Test
    fun `a sync begun on the charger asks for the foreground, and says if refused`() {
        assertTrue(runInForeground(SyncLog.ORIGIN_SCHEDULED, dataBlocked = false, onScreen = false, pluggedIn = true))
        assertTrue(runInForeground(SyncLog.ORIGIN_PANEL, dataBlocked = false, onScreen = false, pluggedIn = true))
        assertFalse("on battery, in the background, as before", runInForeground(SyncLog.ORIGIN_SCHEDULED, dataBlocked = false, onScreen = false, pluggedIn = false))
        val worker = File("src/main/java/com/saulhdev/feeder/manager/sync/FeedSyncer.kt").readText()
        assertTrue(worker.contains("val pluggedIn = powerState(applicationContext).pluggedIn"))
        assertTrue(worker.contains("foregroundRefused = true"))
        assertTrue(worker.contains("if (foregroundRefused) \"foreground refused\" else null"))
    }

    @Test
    fun `the worker asks once, before anything is decided`() {
        val worker = File("src/main/java/com/saulhdev/feeder/manager/sync/FeedSyncer.kt").readText()
        assertEquals(1, Regex("whisperOnScreen\\(\\)").findAll(worker).count())
        assertTrue(worker.contains("val foreground = runInForeground(origin, dataBlocked, onScreen, pluggedIn)"))
        assertTrue(worker.indexOf("val onScreen = whisperOnScreen()") < worker.indexOf("skipForBlockedData(automatic, dataBlocked, onScreen)"))
    }

    private fun links(source: Long, vararg ids: Int) = ids.map { source to "https://example.org/story/$it" }

    @Test
    fun `overlap is the share of the smaller source the other also carried`() {
        // NPR's main feed: 12 stories, 11 of them in the News topic's 40.
        val npr = links(1, *(1..12).toList().toIntArray())
        val news = links(2, *((2..12) + (100..128)).toList().toIntArray())
        val overlap = articleOverlap(npr + news)
        assertEquals(11f / 12f, overlap.getValue(1L to 2L), 0.001f)
        assertEquals(listOf(setOf(1L, 2L)), sameArticleGroups(npr + news))
    }

    @Test
    fun `two sections that share a story now and then are no pair`() {
        val world = links(1, *(1..20).toList().toIntArray())
        val europe = links(2, 1, 2, 3, *(50..66).toList().toIntArray())
        assertEquals(3f / 20f, articleOverlap(world + europe).getValue(1L to 2L), 0.001f)
        assertTrue(sameArticleGroups(world + europe).isEmpty())
    }

    @Test
    fun `the report says why, and how much`() {
        assertEquals("mostly the same articles, 91%", duplicateReason(sameAddress = false, articlesShared = 11f / 12f))
        assertEquals("same address", duplicateReason(sameAddress = true, articlesShared = null))
        assertEquals("same address; mostly the same articles, 100%", duplicateReason(sameAddress = true, articlesShared = 1f))
        val report = File("src/main/java/com/saulhdev/feeder/utils/Diagnostics.kt").readText()
        assertTrue(report.contains("appendLine(\"Duplicates:        \${duplicates.size}\")"))
        assertFalse(report.contains("Subscribed twice"))
    }
}
