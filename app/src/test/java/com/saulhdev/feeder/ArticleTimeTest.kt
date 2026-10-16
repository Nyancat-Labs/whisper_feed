package com.saulhdev.feeder

import com.saulhdev.feeder.utils.TIME_BOTH
import com.saulhdev.feeder.utils.TIME_CLOCK
import com.saulhdev.feeder.utils.TIME_RELATIVE
import com.saulhdev.feeder.utils.articleTimeLabel
import com.saulhdev.feeder.utils.clockLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * "Article times": the clock form, and how it combines with the age.
 *
 * Fixed zone and locale, so the test reads the same on any machine.
 */
class ArticleTimeTest {

    private val zone = ZoneId.of("Europe/Dublin")
    private val now = at(2026, 10, 16, 20, 0)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun clock(then: Long, is24: Boolean = true) =
        clockLabel(then, now, zone, is24, Locale.UK)

    @Test
    fun `today is the time alone`() {
        assertEquals("14:32", clock(at(2026, 10, 16, 14, 32)))
    }

    @Test
    fun `the phone's 12-hour setting is honoured`() {
        assertEquals("2:32 pm", clock(at(2026, 10, 16, 14, 32), is24 = false).lowercase())
    }

    @Test
    fun `the past week names the day`() {
        assertEquals("Thu 23:50", clock(at(2026, 10, 15, 23, 50)))
        assertEquals("Sat 09:05", clock(at(2026, 10, 10, 9, 5)))
    }

    @Test
    fun `a week back the hour gives way to the date`() {
        assertEquals("9 Oct", clock(at(2026, 10, 9, 9, 5)))
        assertEquals("12 Dec 2025", clock(at(2025, 12, 12, 9, 5)))
    }

    @Test
    fun `a date in the future reads as its time`() {
        assertEquals("21:15", clock(at(2026, 10, 16, 21, 15)))
    }

    @Test
    fun `the setting picks the age, the clock or both`() {
        assertEquals("2h", articleTimeLabel("2h", "14:32", TIME_RELATIVE))
        assertEquals("14:32", articleTimeLabel("2h", "14:32", TIME_CLOCK))
        assertEquals("2h · 14:32", articleTimeLabel("2h", "14:32", TIME_BOTH))
        assertEquals("2h", articleTimeLabel("2h", "14:32", "anything else"))
    }
}
