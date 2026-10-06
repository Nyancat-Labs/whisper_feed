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

import com.saulhdev.feeder.data.repository.READ_CAP_MS
import com.saulhdev.feeder.utils.BROWSER_READ_MAX_MS
import com.saulhdev.feeder.utils.BrowserReadTimer
import com.saulhdev.feeder.ui.overlay.ArticleWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.min

/**
 * The band an article's reading time earns, exactly as the SQL decides it.
 *
 * Kotlin standing in for the `CASE` in `engagementPerSource`: the query cannot
 * be run here without a database, and what is worth pinning is the ordering of
 * the branches and the thresholds between them rather than SQLite's arithmetic.
 * If the two ever disagree this is the one that is wrong, so the branches are
 * written in the same order.
 */
private fun band(readMs: Long, opened: Boolean, dwellMs: Long): Int = when {
    readMs >= ArticleWeight.FINISHED_MS -> ArticleWeight.BAND_FINISHED
    readMs >= ArticleWeight.READING_MS -> ArticleWeight.BAND_READ
    opened -> ArticleWeight.BAND_OPENED
    dwellMs >= ArticleWeight.HELD_MS -> ArticleWeight.BAND_HELD
    dwellMs >= ArticleWeight.GLANCED_MS -> ArticleWeight.BAND_GLANCED
    else -> ArticleWeight.BAND_PASSED
}

/** What the database does to an increment: add it, then hold the ceiling. */
private fun accumulate(stored: Long, add: Long): Long = min(stored + add, READ_CAP_MS)

/**
 * The reading clock, and the guarantee that it cannot run away.
 *
 * The failure this is built against is the one that arises from measuring a
 * read by subtracting a stored start time from the present: the reader closes
 * the app mid-article, comes back three days later, and the subtraction says
 * they read for three days. The defence is structural rather than numeric —
 * time is accumulated forward in bounded steps taken only while the article is
 * on screen, so there is no start time for a later resume to find. These pin
 * the two halves of that: the arithmetic cannot exceed the ceiling, and the
 * bands mean what they say.
 */
class ReadingClockTest {

    /* ------------------------------------------------------------- ceiling -- */

    @Test
    fun `increments add up`() {
        var stored = 0L
        repeat(6) { stored = accumulate(stored, 5_000L) }
        assertEquals(30_000L, stored)
    }

    /**
     * The case the cap exists for: a phone left unlocked on a desk, which is
     * awake and resumed and not being read, and which no lifecycle callback
     * will ever describe.
     */
    @Test
    fun `a day of ticks cannot exceed the ceiling`() {
        var stored = 0L
        // 24 hours of one-second ticks, every one of them flushed.
        repeat(24 * 60 * 60) { stored = accumulate(stored, 1_000L) }
        assertEquals(READ_CAP_MS, stored)
    }

    /** Including a single absurd increment, which is what a bug would send. */
    @Test
    fun `one enormous increment is still bounded`() {
        assertEquals(READ_CAP_MS, accumulate(0L, 3L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun `an article already at the ceiling stays there`() {
        assertEquals(READ_CAP_MS, accumulate(READ_CAP_MS, 60_000L))
    }

    /**
     * The ceiling has to sit above a real long read, or it would compress
     * every serious article into the same figure and the bands above
     * [ArticleWeight.FINISHED_MS] would stop distinguishing anything.
     */
    @Test
    fun `the ceiling is longer than a long article`() {
        assertTrue(READ_CAP_MS > ArticleWeight.FINISHED_MS * 2)
    }

    /* --------------------------------------------------------------- bands -- */

    @Test
    fun `scrolling past earns nothing`() {
        assertEquals(ArticleWeight.BAND_PASSED, band(readMs = 0, opened = false, dwellMs = 200))
    }

    @Test
    fun `stopping on a headline is worth more than passing it`() {
        assertTrue(
            band(readMs = 0, opened = false, dwellMs = ArticleWeight.HELD_MS) >
                band(readMs = 0, opened = false, dwellMs = ArticleWeight.GLANCED_MS)
        )
    }

    /**
     * The distinction this whole change exists to draw: opening an article and
     * bouncing straight back out is a judgement that it was not worth reading,
     * and it used to score exactly what reading it to the end scored.
     */
    @Test
    fun `bouncing out of an article scores less than reading it`() {
        val bounced = band(readMs = 2_000, opened = true, dwellMs = 0)
        val read = band(readMs = ArticleWeight.READING_MS, opened = true, dwellMs = 0)
        val finished = band(readMs = ArticleWeight.FINISHED_MS, opened = true, dwellMs = 0)
        assertEquals(ArticleWeight.BAND_OPENED, bounced)
        assertTrue(bounced < read)
        assertTrue(read < finished)
    }

    /**
     * An article opened in an external browser records nothing, and must not
     * therefore score below one opened and bounced out of in the app. Zero
     * means unmeasured, not read for no time.
     */
    @Test
    fun `an unmeasured read still counts as opened`() {
        assertEquals(ArticleWeight.BAND_OPENED, band(readMs = 0, opened = true, dwellMs = 0))
    }

    /** And unmeasured-but-opened still beats anything never opened at all. */
    @Test
    fun `opened without a measurement beats the best unopened article`() {
        assertTrue(
            band(readMs = 0, opened = true, dwellMs = 0) >
                band(readMs = 0, opened = false, dwellMs = 60_000)
        )
    }

    @Test
    fun `the thresholds are ordered`() {
        assertTrue(ArticleWeight.GLANCED_MS < ArticleWeight.HELD_MS)
        assertTrue(ArticleWeight.HELD_MS < ArticleWeight.READING_MS)
        assertTrue(ArticleWeight.READING_MS < ArticleWeight.FINISHED_MS)
    }

    @Test
    fun `the bands are ordered`() {
        assertTrue(ArticleWeight.BAND_PASSED < ArticleWeight.BAND_GLANCED)
        assertTrue(ArticleWeight.BAND_GLANCED < ArticleWeight.BAND_HELD)
        assertTrue(ArticleWeight.BAND_HELD < ArticleWeight.BAND_OPENED)
        assertTrue(ArticleWeight.BAND_OPENED < ArticleWeight.BAND_READ)
        assertTrue(ArticleWeight.BAND_READ < ArticleWeight.BAND_FINISHED)
    }

    /**
     * Passing something has to be worth nothing rather than a little.
     *
     * Treating the absence of interest as a small amount of interest is how
     * the original count went wrong: it made a source somebody hurried past a
     * hundred times look like one they read.
     */
    @Test
    fun `passing is worth nothing at all`() {
        assertEquals(0, ArticleWeight.BAND_PASSED)
    }

    /* ------------------------------------------------- the browser round trip -- */

    @Before
    fun clearAnyPendingTrip() = BrowserReadTimer.forget()

    @Test
    fun `a trip away is measured from leaving to coming back`() {
        BrowserReadTimer.left("a", now = 1_000L)
        assertEquals("a" to 60_000L, BrowserReadTimer.settle(now = 61_000L))
    }

    @Test
    fun `nothing outstanding records nothing`() {
        assertNull(BrowserReadTimer.settle(now = 5_000L))
    }

    /**
     * The rule this was asked for. Above the limit the absence tells us
     * nothing — read for twenty minutes, or read for two and then had lunch —
     * so nothing is what gets recorded. Clamping would turn "no idea" into a
     * confident claim of fifteen minutes.
     */
    @Test
    fun `a trip longer than the limit is discarded, not clamped`() {
        BrowserReadTimer.left("a", now = 0L)
        assertNull(BrowserReadTimer.settle(now = BROWSER_READ_MAX_MS + 1))
    }

    @Test
    fun `a trip exactly at the limit still counts`() {
        BrowserReadTimer.left("a", now = 0L)
        assertEquals("a" to BROWSER_READ_MAX_MS, BrowserReadTimer.settle(now = BROWSER_READ_MAX_MS))
    }

    /** Three days in the browser is the exact case this exists to refuse. */
    @Test
    fun `an overnight absence records nothing at all`() {
        BrowserReadTimer.left("a", now = 0L)
        assertNull(BrowserReadTimer.settle(now = 3L * 24 * 60 * 60 * 1000))
    }

    /**
     * Two hooks fire on a return — an Activity resuming and the launcher panel
     * reopening — and one read must not be recorded twice.
     */
    @Test
    fun `settling twice records once`() {
        BrowserReadTimer.left("a", now = 0L)
        assertEquals("a" to 5_000L, BrowserReadTimer.settle(now = 5_000L))
        assertNull(BrowserReadTimer.settle(now = 6_000L))
    }

    /** A clock that went backwards is not a negative read. */
    @Test
    fun `a duration that comes back negative is refused`() {
        BrowserReadTimer.left("a", now = 10_000L)
        assertNull(BrowserReadTimer.settle(now = 9_000L))
    }

    @Test
    fun `a second departure replaces the first rather than queueing`() {
        BrowserReadTimer.left("a", now = 0L)
        BrowserReadTimer.left("b", now = 1_000L)
        assertEquals("b" to 4_000L, BrowserReadTimer.settle(now = 5_000L))
    }

    /**
     * Nothing is persisted, so a process killed mid-trip forgets it. This
     * stands in for that: the alternative — a start time surviving on disk —
     * is the whole bug the in-app clock is shaped to avoid.
     */
    @Test
    fun `a forgotten trip settles to nothing`() {
        BrowserReadTimer.left("a", now = 0L)
        BrowserReadTimer.forget()
        assertNull(BrowserReadTimer.settle(now = 5_000L))
    }

    /** A bounce out and straight back is a real answer: it lands in "opened". */
    @Test
    fun `a two second trip is recorded and lands below the reading band`() {
        BrowserReadTimer.left("a", now = 0L)
        val (_, millis) = BrowserReadTimer.settle(now = 2_000L)!!
        assertEquals(ArticleWeight.BAND_OPENED, band(readMs = millis, opened = true, dwellMs = 0))
    }

    /** And a genuine browser read reaches the same band an in-app one would. */
    @Test
    fun `a four minute trip earns the finished band`() {
        BrowserReadTimer.left("a", now = 0L)
        val (_, millis) = BrowserReadTimer.settle(now = 4L * 60 * 1000)!!
        assertEquals(ArticleWeight.BAND_FINISHED, band(readMs = millis, opened = true, dwellMs = 0))
    }

    /** The discard limit has to sit above the band it is meant to let through. */
    @Test
    fun `the limit is longer than a finished read`() {
        assertTrue(BROWSER_READ_MAX_MS > ArticleWeight.FINISHED_MS)
    }
}
