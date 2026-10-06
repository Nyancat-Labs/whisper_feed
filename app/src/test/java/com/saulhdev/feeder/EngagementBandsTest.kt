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

import com.saulhdev.feeder.ui.overlay.ArticleWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four bands, as the SQL applies them.
 *
 * The CASE in engagementPerSource cannot be unit-tested without a database,
 * so this pins the rule it encodes — strongest condition first, thresholds
 * inclusive — against the constants it is given. If the two ever disagree,
 * the ordering quietly starts meaning something else.
 */
private fun band(openedAt: Long, dwellMs: Long): Int = when {
    openedAt > 0 -> ArticleWeight.BAND_OPENED
    dwellMs >= ArticleWeight.HELD_MS -> ArticleWeight.BAND_HELD
    dwellMs >= ArticleWeight.GLANCED_MS -> ArticleWeight.BAND_GLANCED
    else -> ArticleWeight.BAND_PASSED
}

class EngagementBandsTest {

    /**
     * The whole point of the change. Scrolling past is not evidence of
     * wanting something, and the old count treated it as though it were.
     */
    @Test
    fun `scrolled past with no dwell is worth nothing`() {
        assertEquals(0, band(openedAt = 0, dwellMs = 0))
        assertEquals(0, band(openedAt = 0, dwellMs = 999))
    }

    @Test
    fun `a second on screen is a glance`() {
        assertEquals(ArticleWeight.BAND_GLANCED, band(0, 1_000))
        assertEquals(ArticleWeight.BAND_GLANCED, band(0, 4_999))
    }

    @Test
    fun `five seconds is having stopped at it`() {
        assertEquals(ArticleWeight.BAND_HELD, band(0, 5_000))
        assertEquals(ArticleWeight.BAND_HELD, band(0, 29_000))
    }

    /**
     * Strongest condition first. An article opened in a browser may have sat
     * in the list for a second on the way there, and that is not the
     * interesting fact about it.
     */
    @Test
    fun `opening outranks whatever time was spent in the list`() {
        assertEquals(ArticleWeight.BAND_OPENED, band(1L, 0))
        assertEquals(ArticleWeight.BAND_OPENED, band(1L, 30_000))
    }

    /** One article opened has to beat a handful merely lingered on. */
    @Test
    fun `the bands are ordered and an open outweighs several holds`() {
        assertTrue(ArticleWeight.BAND_OPENED > ArticleWeight.BAND_HELD)
        assertTrue(ArticleWeight.BAND_HELD > ArticleWeight.BAND_GLANCED)
        assertTrue(ArticleWeight.BAND_GLANCED > ArticleWeight.BAND_PASSED)
        assertTrue(ArticleWeight.BAND_OPENED > 2 * ArticleWeight.BAND_HELD)
    }

    /**
     * A hundred articles hurried past must not outweigh one read, or the
     * fastest scroller's noisiest source wins again by another route.
     */
    @Test
    fun `a source only ever scrolled past cannot outscore one read once`() {
        val hurried = (1..100).sumOf { band(0, 0) }
        val read = band(openedAt = 1L, dwellMs = 0)
        assertTrue(hurried < read)
    }
}
