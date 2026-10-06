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
package com.saulhdev.feeder.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * A count of reading, kept apart from the articles it was counted from.
 *
 * The statistics screen used to derive its charts from the Article table
 * directly — group by `date(readAt)`, count the rows. That reads correctly and
 * is wrong over time, because articles do not survive: the sync cleanup
 * deletes anything published longer ago than the sync range, which defaults to
 * a week. An article read today is usually published within a day or two of
 * today, so it is gone about a week after it was read.
 *
 * The consequence is a chart that rewrites its own past. A day that showed
 * fourteen articles read shows two a fortnight later, and then none — not
 * because the reading did not happen, but because the evidence was tidied
 * away. Somebody checking whether they read more on Sundays would be reading a
 * decay curve of the cleanup schedule and mistaking it for their own habits.
 *
 * So the count is recorded when the reading happens and kept on its own. It is
 * small by construction: one row per hour that had any reading in it, at most
 * 24 a day, about 8,800 a year, and rows past the window are deleted on sync.
 * It holds no article ids, no titles and no addresses — only how many, and
 * when.
 *
 * @param day local date as `YYYY-MM-DD`. Local rather than UTC because the
 *   question the chart answers is "what are my days like", and a reader in
 *   Auckland reading at 9am does not want that on yesterday's bar.
 * @param hour local hour as `00`–`23`, as text so it matches [HourCount]
 *   without a conversion in between.
 */
@Entity(tableName = "ReadingTally", primaryKeys = ["day", "hour"])
data class ReadingTally(
    val day: String,
    val hour: String,

    /** Articles that went past and were counted read, first time only. */
    @ColumnInfo(defaultValue = "0")
    val seen: Int = 0,

    /** Of those, the ones actually opened to read. First open only. */
    @ColumnInfo(defaultValue = "0")
    val opened: Int = 0,

    /** Time spent reading, as it accrued. Already capped per article. */
    @ColumnInfo(defaultValue = "0")
    val readMs: Long = 0L,

    /**
     * Articles that contributed any reading time at all.
     *
     * Counted on an article's *first* contribution only, so an article read
     * across the turn of an hour is one article rather than two. That places
     * it in the hour the reading started, which is the honest answer to "how
     * many articles did I read" and slightly the wrong answer to "when" — the
     * alternative counts one article twice, which is wrong about both.
     */
    @ColumnInfo(defaultValue = "0")
    val timed: Int = 0,
)
