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
package com.saulhdev.feeder.viewmodels

/**
 * How far back "Mark all as read" reaches, by the date the article carries.
 *
 * By publication date rather than by when it was fetched: "older than an hour"
 * means what the card says, and the card shows the article's own age.
 */
enum class MarkReadRange(private val olderThanMs: Long?) {
    Everything(null),
    OlderThanHour(60 * 60 * 1000L),
    OlderThanDay(24 * 60 * 60 * 1000L),
    OlderThanTwoDays(48 * 60 * 60 * 1000L);

    /**
     * The cut-off: articles dated before this are marked. Everything has none,
     * which includes an article a site dated in the future.
     */
    fun before(nowMs: Long): Long = olderThanMs?.let { nowMs - it } ?: Long.MAX_VALUE
}

/**
 * The buttons worth showing, in the order the sheet shows them: 1 h, 24 h,
 * 48 h, then All.
 *
 * A range that would mark exactly what the next wider one marks is left out
 * (review 7: "Everything" and "Older than 1 hour" both said 13,928 whenever
 * nothing had arrived in the last hour), and so is one with nothing to mark.
 * All is always there, so the row is never empty.
 */
fun markReadChoices(counts: Map<MarkReadRange, Int>): List<MarkReadRange> {
    val widestFirst = MarkReadRange.entries // Everything, then each narrower
    val shown = widestFirst.filterIndexed { i, range ->
        if (range == MarkReadRange.Everything) return@filterIndexed true
        val count = counts[range] ?: 0
        count > 0 && count != (counts[widestFirst[i - 1]] ?: 0)
    }
    return shown.filter { it != MarkReadRange.Everything } + MarkReadRange.Everything
}
