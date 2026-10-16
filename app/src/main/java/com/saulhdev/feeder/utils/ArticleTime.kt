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
package com.saulhdev.feeder.utils

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "2h": how long ago. The default, and the only form before this setting. */
const val TIME_RELATIVE = "relative"

/** "14:32": when it was published. */
const val TIME_CLOCK = "clock"

/** "2h · 14:32". */
const val TIME_BOTH = "both"

/**
 * When an article was published, as a reader would write it down.
 *
 * The time alone today, the weekday and time for the six days before, and the
 * date alone after that: a week back, the hour stops mattering, which is the
 * same judgement [articleAge] makes, and "12 Oct" fits on a compact row where
 * "12 Oct 14:32" would push the source's name into an ellipsis. A date in the
 * future, which feeds do publish, reads as its time.
 *
 * Self-contained rather than leaning on the feed's day headings: the launcher
 * panel, search, title order and the reader have none.
 *
 * Kept free of Context so it can be tested directly; see [formatArticleTime].
 */
fun clockLabel(
    thenMillis: Long,
    nowMillis: Long,
    zone: ZoneId,
    is24Hour: Boolean,
    locale: Locale,
): String {
    val then = Instant.ofEpochMilli(thenMillis).atZone(zone)
    val days = ChronoUnit.DAYS.between(then.toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
    val time = if (is24Hour) "HH:mm" else "h:mm a"
    val pattern = when {
        days <= 0 -> time
        days < 7 -> "EEE $time"
        then.year == Instant.ofEpochMilli(nowMillis).atZone(zone).year -> "d MMM"
        else -> "d MMM yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(then)
}

/** The age, the clock time, or both, as [format] asks. */
fun articleTimeLabel(relative: String, clock: String, format: String): String = when (format) {
    TIME_CLOCK -> clock
    TIME_BOTH -> "$relative · $clock"
    else -> relative
}

/**
 * An article's time, ready to draw, in the reader's chosen [format].
 *
 * The 12- or 24-hour clock is the phone's own setting rather than the
 * language's, so somebody in an English locale who set 24-hour gets 14:32.
 */
fun formatArticleTime(
    context: Context,
    thenMillis: Long,
    format: String,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val relative = formatArticleAge(context, thenMillis, nowMillis)
    if (format == TIME_RELATIVE) return relative
    val clock = clockLabel(
        thenMillis, nowMillis, ZoneId.systemDefault(),
        DateFormat.is24HourFormat(context), Locale.getDefault(),
    )
    return articleTimeLabel(relative, clock, format)
}
