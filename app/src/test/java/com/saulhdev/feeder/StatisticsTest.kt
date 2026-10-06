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

import com.saulhdev.feeder.data.db.models.DayCount
import com.saulhdev.feeder.data.db.models.HourCount
import com.saulhdev.feeder.viewmodels.STATS_WINDOW_DAYS
import com.saulhdev.feeder.viewmodels.StatisticsState
import com.saulhdev.feeder.viewmodels.exampleDays
import com.saulhdev.feeder.data.db.models.ReadingTime
import com.saulhdev.feeder.data.db.models.SourceEngagement
import com.saulhdev.feeder.ui.pages.ChartPalette
import com.saulhdev.feeder.ui.pages.dayBandOf
import com.saulhdev.feeder.viewmodels.topSources
import com.saulhdev.feeder.data.repository.fillDays
import com.saulhdev.feeder.data.repository.fillHours
import com.saulhdev.feeder.viewmodels.exampleHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun days(vararg seen: Int) = seen.mapIndexed { i, n ->
    DayCount(day = LocalDate.of(2026, 1, 1).plusDays(i.toLong()).toString(), seen = n, opened = 0)
}

/**
 * The derived figures on the statistics screen.
 *
 * The charts themselves are drawing code and are left to the eye, but every
 * number printed beside them is arithmetic over a list and can be pinned. The
 * ones worth pinning are the ones with a judgement inside: what counts as a
 * day, what counts as a streak, and when the screen is allowed to show an
 * example instead of the truth.
 */
class StatisticsTest {

    @Test
    fun `totals add up across the window`() {
        val state = StatisticsState(
            byDay = listOf(
                DayCount("2026-01-01", seen = 10, opened = 3),
                DayCount("2026-01-02", seen = 5, opened = 1),
            ),
            loading = false,
        )
        assertEquals(15, state.seen)
        assertEquals(4, state.opened)
    }

    /**
     * The average is over days that had reading in them.
     *
     * Dividing by thirty would tell somebody who reads hard on Sundays that
     * they read two articles a day, which is true of no day they have ever
     * had. The quiet days are in the chart, where they belong.
     */
    @Test
    fun `the daily average ignores the days with nothing in them`() {
        val state = StatisticsState(byDay = days(20, 0, 0, 0, 10), loading = false)
        assertEquals(15, state.perActiveDay)
    }

    @Test
    fun `a month with no reading averages zero rather than dividing by it`() {
        assertEquals(0, StatisticsState(byDay = days(0, 0, 0), loading = false).perActiveDay)
    }

    @Test
    fun `the streak is the longest run, not the last one`() {
        val state = StatisticsState(byDay = days(1, 1, 1, 1, 0, 1, 1), loading = false)
        assertEquals(4, state.streak)
    }

    @Test
    fun `a streak running to the end of the window still counts`() {
        assertEquals(3, StatisticsState(byDay = days(0, 1, 1, 1), loading = false).streak)
    }

    @Test
    fun `the busiest hour is read back as a number, not as text`() {
        val hours = (0..23).map { HourCount("%02d".format(it), seen = if (it == 7) 40 else 1, opened = 0) }
        assertEquals(7, StatisticsState(byHour = hours, loading = false).busiestHour)
    }

    @Test
    fun `no reading means no busiest hour rather than midnight`() {
        val hours = (0..23).map { HourCount("%02d".format(it), seen = 0, opened = 0) }
        assertEquals(null, StatisticsState(byHour = hours, loading = false).busiestHour)
    }

    /**
     * The note that stops a true chart reading as a broken feature.
     *
     * Opening has only been counted since the release that added it, so a
     * history gathered before then is all seen and no opened — which draws as
     * one series flat against the axis.
     */
    @Test
    fun `seen without opened is called out`() {
        val state = StatisticsState(byDay = days(10, 10), loading = false)
        assertTrue(state.openingUncounted)
    }

    @Test
    fun `an example never claims opening is uncounted`() {
        val state = StatisticsState(
            byDay = days(10, 10),
            isExample = true,
            loading = false,
        )
        assertFalse(state.openingUncounted)
    }

    @Test
    fun `an empty window is not called out, because there is nothing to explain`() {
        assertFalse(StatisticsState(byDay = days(0, 0), loading = false).openingUncounted)
    }

    /* ------------------------------------------------------------ example -- */

    /**
     * The example has to fill the same chart the real data would, or the
     * screen changes shape when the reader's own figures arrive.
     */
    @Test
    fun `the example fills the whole window`() {
        val keys = (0 until STATS_WINDOW_DAYS).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString() }
        val example = exampleDays(STATS_WINDOW_DAYS, keys)
        assertEquals(STATS_WINDOW_DAYS, example.size)
        assertEquals(24, exampleHours().size)
    }

    /** Opened is a subset of seen. A stack where it is not would draw upside down. */
    @Test
    fun `the example never opens more than it saw`() {
        val keys = (0 until STATS_WINDOW_DAYS).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString() }
        exampleDays(STATS_WINDOW_DAYS, keys).forEach {
            assertTrue("opened ${it.opened} > seen ${it.seen}", it.opened <= it.seen)
        }
        exampleHours().forEach {
            assertTrue("opened ${it.opened} > seen ${it.seen}", it.opened <= it.seen)
        }
    }

    /**
     * An example that were the same every day would teach the reader nothing
     * about what a shape on this chart means, which is the only reason to
     * draw one rather than leave the screen empty.
     */
    @Test
    fun `the example has a shape to it`() {
        val hours = exampleHours()
        val night = (0..4).sumOf { hours[it].seen }
        val evening = (17..20).sumOf { hours[it].seen }
        assertTrue("an example day should not be flat", evening > night * 4)
        assertTrue("and should have a quiet stretch", hours.count { it.seen == 0 } >= 3)
    }

    /** Fixed, not random: a screen whose figures change on every open looks broken. */
    @Test
    fun `the example is the same every time it is asked for`() {
        val keys = (0 until STATS_WINDOW_DAYS).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString() }
        assertEquals(exampleDays(STATS_WINDOW_DAYS, keys), exampleDays(STATS_WINDOW_DAYS, keys))
        assertEquals(exampleHours(), exampleHours())
    }

    /* --------------------------------------------------------- gap filling -- */

    /**
     * The quiet days are part of the answer.
     *
     * `GROUP BY` can only return days that have rows, so a fortnight away from
     * the app comes back as nothing at all — and a chart drawn from what came
     * back closes the gap up, putting the bars either side of it next to one
     * another. A break in reading would render as continuous reading.
     */
    @Test
    fun `days with no reading are filled in`() {
        val today = LocalDate.of(2026, 3, 10)
        val filled = fillDays(
            rows = listOf(DayCount("2026-03-08", seen = 4, opened = 1)),
            days = 5,
            today = today,
        )
        assertEquals(5, filled.size)
        assertEquals(
            listOf("2026-03-06", "2026-03-07", "2026-03-08", "2026-03-09", "2026-03-10"),
            filled.map { it.day },
        )
        assertEquals(4, filled[2].seen)
        assertEquals(0, filled[0].seen)
    }

    /** Oldest first, so the chart reads left to right like a calendar. */
    @Test
    fun `the window ends today`() {
        val today = LocalDate.of(2026, 3, 10)
        val filled = fillDays(emptyList(), STATS_WINDOW_DAYS, today)
        assertEquals(today.toString(), filled.last().day)
        assertEquals(today.minusDays(29).toString(), filled.first().day)
    }

    /** A month boundary is exactly where naive date arithmetic goes wrong. */
    @Test
    fun `the window crosses the start of a month`() {
        val filled = fillDays(emptyList(), 3, LocalDate.of(2026, 3, 1))
        assertEquals(listOf("2026-02-27", "2026-02-28", "2026-03-01"), filled.map { it.day })
    }

    @Test
    fun `every hour of the day is present`() {
        val filled = fillHours(listOf(HourCount("07", seen = 9, opened = 2)))
        assertEquals(24, filled.size)
        assertEquals(9, filled[7].seen)
        assertEquals(0, filled[8].seen)
    }

    /**
     * SQLite writes the hour as "07", and a chart that indexed the list on the
     * string would drop every morning on the floor without failing.
     */
    @Test
    fun `a zero padded hour lands in the right bucket`() {
        val filled = fillHours(listOf(HourCount("00", seen = 3, opened = 1)))
        assertEquals(3, filled[0].seen)
        assertEquals("00", filled[0].hour)
        assertEquals("23", filled[23].hour)
    }

    /* -------------------------------------------------------- reading time -- */

    @Test
    fun `reading time is shown as hours and minutes`() {
        val state = StatisticsState(
            readingTime = ReadingTime(totalMs = 3 * 3_600_000L + 25 * 60_000L, articles = 40),
            loading = false,
        )
        assertEquals(3L to 25L, state.readingHoursMinutes)
    }

    @Test
    fun `under an hour is still minutes rather than zero hours`() {
        val state = StatisticsState(
            readingTime = ReadingTime(totalMs = 8 * 60_000L, articles = 4),
            loading = false,
        )
        assertEquals(0L to 8L, state.readingHoursMinutes)
    }

    /**
     * Nothing measured is a different statement from no time spent.
     *
     * An article opened in an external browser records nothing however long
     * it was read for, so a reader using that mode must not be told they read
     * for zero minutes.
     */
    @Test
    fun `nothing measured reads as nothing, not as zero`() {
        val state = StatisticsState(readingTime = ReadingTime(0, 0), loading = false)
        assertEquals(null, state.readingHoursMinutes)
    }

    @Test
    fun `the per-article average is over articles that were actually read`() {
        val state = StatisticsState(
            readingTime = ReadingTime(totalMs = 20 * 60_000L, articles = 10),
            loading = false,
        )
        assertEquals(2, state.minutesPerArticle)
    }

    @Test
    fun `no articles read averages zero rather than dividing by it`() {
        assertEquals(0, StatisticsState(readingTime = ReadingTime(0, 0), loading = false).minutesPerArticle)
    }

    /* ------------------------------------------------------------ sources -- */

    private fun eng(id: Long, seen: Int, opened: Int = 0) =
        SourceEngagement(feedId = id, score = seen, opened = opened, seen = seen)

    @Test
    fun `the busiest sources come back in order`() {
        val rows = topSources(
            titles = mapOf(1L to "A", 2L to "B", 3L to "C"),
            engagement = mapOf(1L to eng(1, 10), 2L to eng(2, 30), 3L to eng(3, 20)),
        )
        assertEquals(listOf("B", "C", "A"), rows.map { it.title })
    }

    /**
     * The tail is folded, not dropped.
     *
     * A chart of the top five that discards the rest answers "which are my
     * biggest sources" and silently refuses "and how much of my reading is
     * that" — which is the half somebody with a hundred subscriptions wants.
     */
    @Test
    fun `everything past the top few is folded into one bar`() {
        val engagement = (1L..9L).associateWith { eng(it, (10 - it).toInt() * 10) }
        val rows = topSources((1L..9L).associateWith { "S$it" }, engagement)
        assertEquals(ChartPalette.MAX_SLOTS + 1, rows.size)
        assertTrue(rows.last().other)
        // The fold keeps the totals honest.
        assertEquals(engagement.values.sumOf { it.seen }, rows.sumOf { it.seen })
    }

    @Test
    fun `a short list has no folded bar at all`() {
        val rows = topSources(mapOf(1L to "A", 2L to "B"), mapOf(1L to eng(1, 5), 2L to eng(2, 3)))
        assertEquals(2, rows.size)
        assertTrue(rows.none { it.other })
    }

    /** Articles outlive the subscription they came from; the row still counts. */
    @Test
    fun `a removed source keeps its reading`() {
        val rows = topSources(titles = emptyMap(), engagement = mapOf(7L to eng(7, 40)))
        assertEquals(1, rows.size)
        assertEquals(40, rows.first().seen)
        assertTrue(rows.first().title.isBlank())
    }

    @Test
    fun `a source nothing was seen from is left out`() {
        assertTrue(topSources(mapOf(1L to "A"), mapOf(1L to eng(1, 0))).isEmpty())
    }

    @Test
    fun `nothing read at all is an empty chart rather than a crash`() {
        assertTrue(topSources(emptyMap(), emptyMap()).isEmpty())
    }

    /* -------------------------------------------------------- time bands -- */

    @Test
    fun `hours fall into the four parts of the day`() {
        assertEquals(0, dayBandOf(0))
        assertEquals(0, dayBandOf(5))
        assertEquals(1, dayBandOf(6))
        assertEquals(1, dayBandOf(11))
        assertEquals(2, dayBandOf(12))
        assertEquals(2, dayBandOf(17))
        assertEquals(3, dayBandOf(18))
        assertEquals(3, dayBandOf(23))
    }

    /** Every hour of the day has a band, and no band is skipped. */
    @Test
    fun `the bands cover the whole day`() {
        assertEquals(setOf(0, 1, 2, 3), (0..23).map(::dayBandOf).toSet())
        assertTrue((0..23).map(::dayBandOf).zipWithNext().all { (a, b) -> b >= a })
    }
}
