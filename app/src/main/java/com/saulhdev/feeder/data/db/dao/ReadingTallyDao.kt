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
package com.saulhdev.feeder.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.saulhdev.feeder.data.db.models.DayCount
import com.saulhdev.feeder.data.db.models.HourCount
import com.saulhdev.feeder.data.db.models.ReadingTime
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingTallyDao {

    /**
     * Adds to one hour's counts, creating the row if this is its first reading.
     *
     * Two statements rather than an upsert. `INSERT … ON CONFLICT DO UPDATE`
     * arrived in SQLite 3.24, which is Android 11; this app runs back to
     * Android 8, where that statement is a syntax error at runtime and not at
     * compile time — the worst kind. `INSERT OR IGNORE` then `UPDATE` has
     * worked since long before that, and the transaction makes the pair atomic.
     */
    @Transaction
    suspend fun add(
        day: String,
        hour: String,
        seen: Int = 0,
        opened: Int = 0,
        readMs: Long = 0L,
        timed: Int = 0,
    ) {
        ensureRow(day, hour)
        increment(day, hour, seen, opened, readMs, timed)
    }

    @Query(
        """
        INSERT OR IGNORE INTO ReadingTally (day, hour, seen, opened, readMs, timed)
        VALUES (:day, :hour, 0, 0, 0, 0)
        """
    )
    suspend fun ensureRow(day: String, hour: String)

    @Query(
        """
        UPDATE ReadingTally SET
            seen = seen + :seen,
            opened = opened + :opened,
            readMs = readMs + :readMs,
            timed = timed + :timed
        WHERE day = :day AND hour = :hour
        """
    )
    suspend fun increment(
        day: String,
        hour: String,
        seen: Int,
        opened: Int,
        readMs: Long,
        timed: Int,
    )

    /**
     * Takes back what an undo took back.
     *
     * "Mark all read" can put a few hundred into one hour, and the undo that
     * follows a moment later has to come out of the same hour or the chart
     * keeps a spike for reading that was explicitly retracted. Floored at zero
     * because an undo crossing an hour boundary would otherwise leave a
     * negative count, and a negative bar is a drawing bug rather than a fact.
     */
    @Query(
        """
        UPDATE ReadingTally SET
            seen = MAX(seen - :seen, 0),
            opened = MAX(opened - :opened, 0)
        WHERE day = :day AND hour = :hour
        """
    )
    suspend fun subtract(day: String, hour: String, seen: Int, opened: Int)

    @Query(
        """
        SELECT day, SUM(seen) AS seen, SUM(opened) AS opened
        FROM ReadingTally
        WHERE day >= :sinceDay
        GROUP BY day
        ORDER BY day
        """
    )
    fun byDay(sinceDay: String): Flow<List<DayCount>>

    @Query(
        """
        SELECT hour, SUM(seen) AS seen, SUM(opened) AS opened
        FROM ReadingTally
        WHERE day >= :sinceDay
        GROUP BY hour
        ORDER BY hour
        """
    )
    fun byHour(sinceDay: String): Flow<List<HourCount>>

    @Query(
        """
        SELECT COALESCE(SUM(readMs), 0) AS totalMs,
            COALESCE(SUM(timed), 0) AS articles
        FROM ReadingTally
        WHERE day >= :sinceDay
        """
    )
    fun readingTime(sinceDay: String): Flow<ReadingTime>

    /** Rows older than the charts can show. Called from the sync cleanup. */
    @Query("DELETE FROM ReadingTally WHERE day < :beforeDay")
    suspend fun prune(beforeDay: String)
}
