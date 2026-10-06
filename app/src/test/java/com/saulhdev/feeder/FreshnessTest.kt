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

import com.saulhdev.feeder.data.db.models.SourcePace
import com.saulhdev.feeder.ui.overlay.ArticleWeight
import com.saulhdev.feeder.ui.overlay.absoluteFreshness
import com.saulhdev.feeder.ui.overlay.freshnessFor
import com.saulhdev.feeder.ui.overlay.relativeFreshness
import com.saulhdev.feeder.ui.overlay.sourcePaceHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR = 3_600_000L

private fun pace(articles: Int, spanHours: Long, id: Long = 1L) =
    SourcePace(feedId = id, articles = articles, newest = spanHours * HOUR, oldest = 0L)

/**
 * Freshness measured against a source's own rhythm.
 *
 * The bug this exists for: the absolute curve turns negative after three days,
 * which describes a wire service exactly and a weekly blog not at all. A feed
 * of slow sources could not reach the hero band at any hour of any day, so it
 * drew as a list of rows whatever else was true of it.
 */
class FreshnessTest {

    private val weekly = 168f
    private val hourly = 1f

    /* ------------------------------------------------------------- the pair -- */

    /** The point of the change: a slow source's newest article is fresh. */
    @Test
    fun `three days old is fresh for a weekly source`() {
        assertTrue(relativeFreshness(72f, weekly)!! > 0f)
        assertTrue(freshnessFor(72f, weekly) > freshnessFor(72f, null))
    }

    /** And the constraint on it: news must not be marked down to pay for that. */
    @Test
    fun `a fresh news story scores what it always did`() {
        assertEquals(absoluteFreshness(1f), freshnessFor(1f, hourly), 0.001f)
        assertEquals(absoluteFreshness(4f), freshnessFor(4f, hourly), 0.001f)
        assertEquals(absoluteFreshness(12f), freshnessFor(12f, hourly), 0.001f)
    }

    /**
     * Taking the better of the two is what makes this a lift rather than a
     * reshuffle. Neither reading can ever lower the result below the other.
     */
    @Test
    fun `the combined answer is never worse than the absolute one`() {
        listOf(0.5f, 3f, 12f, 48f, 100f, 400f).forEach { age ->
            listOf(null, 0.5f, 6f, 72f, 168f, 1000f).forEach { p ->
                assertTrue(
                    "age $age pace $p went below the absolute answer",
                    freshnessFor(age, p) >= absoluteFreshness(age) - 0.0001f,
                )
            }
        }
    }

    @Test
    fun `an unknown pace has no opinion at all`() {
        assertNull(relativeFreshness(72f, null))
        assertEquals(absoluteFreshness(72f), freshnessFor(72f, null), 0.001f)
    }

    /* --------------------------------------------------------- the backstops -- */

    /**
     * A slow source's newest article is worth promoting. It is not worth
     * promoting as breaking, and a feed whose biggest card is a fortnight old
     * is not one anybody would trust.
     */
    @Test
    fun `past three days the relative answer cannot claim more than today`() {
        // Half an interval into a monthly source: the raw band would be 1.2.
        val score = relativeFreshness(100f, 1000f)!!
        assertEquals(0.4f, score, 0.001f)
    }

    @Test
    fun `past a week it cannot claim anything`() {
        assertTrue(relativeFreshness(200f, 1000f)!! <= 0f)
        assertTrue(relativeFreshness(500f, 2000f)!! <= 0f)
    }

    /**
     * Without a floor on pace, a firehose would make its own hour-old articles
     * look stale and the relative reading would start pulling news down.
     */
    @Test
    fun `a very fast source is treated as no faster than the floor`() {
        assertEquals(
            relativeFreshness(3f, ArticleWeight.PACE_MIN_HOURS),
            relativeFreshness(3f, 0.05f),
        )
    }

    @Test
    fun `a very slow source is treated as no slower than the ceiling`() {
        assertEquals(
            relativeFreshness(48f, ArticleWeight.PACE_MAX_HOURS),
            relativeFreshness(48f, 10_000f),
        )
    }

    /* -------------------------------------------------------------- the pace -- */

    @Test
    fun `pace is the mean gap between articles`() {
        // Four articles spanning 300 hours is three gaps of 100.
        assertEquals(100f, sourcePaceHours(listOf(pace(articles = 4, spanHours = 300)))[1L]!!, 0.01f)
    }

    /**
     * One article gives no interval and two give a single gap a holiday would
     * distort, so neither is an estimate worth acting on.
     */
    @Test
    fun `a source with too few articles has no pace`() {
        assertTrue(sourcePaceHours(listOf(pace(articles = 1, spanHours = 0))).isEmpty())
        assertTrue(sourcePaceHours(listOf(pace(articles = 2, spanHours = 100))).isEmpty())
    }

    /** A whole history arriving in one backfill has a span near zero. */
    @Test
    fun `a backfilled source falls back to the floor rather than to zero`() {
        val p = sourcePaceHours(listOf(pace(articles = 50, spanHours = 0)))[1L]
        assertEquals(0f, p!!, 0.001f)
        // Zero would divide badly downstream; the clamp is what saves it.
        assertEquals(
            relativeFreshness(3f, ArticleWeight.PACE_MIN_HOURS),
            relativeFreshness(3f, 0.0001f),
        )
    }

    /** Nothing is returned for a source the query had no rows for. */
    @Test
    fun `an unknown source is simply absent`() {
        assertNull(sourcePaceHours(listOf(pace(articles = 5, spanHours = 50)))[99L])
    }

    /* ----------------------------------------------------- the reported case -- */

    /**
     * The whole reason for this: a fortnight-old feed drew as rows.
     *
     * A weekly blog whose newest post is two days old should now clear the
     * medium band on freshness alone, which it could not before.
     */
    @Test
    fun `a weekly blog's newest post clears the medium band`() {
        val ordinary = ArticleWeight.BASE + 0.4f + 0.3f // title, summary
        assertTrue(ordinary + freshnessFor(48f, weekly) >= ArticleWeight.MEDIUM_AT)
        assertTrue(ordinary + freshnessFor(48f, null) < ArticleWeight.LARGE_AT)
    }
}
