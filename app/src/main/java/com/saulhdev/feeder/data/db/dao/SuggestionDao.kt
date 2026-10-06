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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saulhdev.feeder.data.db.models.Suggestion
import kotlinx.coroutines.flow.Flow

@Dao
interface SuggestionDao {

    /** What to offer, strongest evidence first, minus anything refused. */
    @Query("SELECT * FROM Suggestion WHERE dismissedAt = 0 ORDER BY mentions DESC")
    fun getSuggestions(): Flow<List<Suggestion>>

    /**
     * REPLACE on a unique host, so a later pass updates the count rather than
     * adding a second row for the same site.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(suggestion: Suggestion)

    /** Every host already suggested, refused ones included. */
    @Query("SELECT host FROM Suggestion")
    suspend fun knownHosts(): List<String>

    /** The reader said no. Kept, so the next pass does not ask again. */
    @Query("UPDATE Suggestion SET dismissedAt = :now WHERE host = :host")
    suspend fun dismiss(host: String, now: Long = System.currentTimeMillis())

    /** Taken up, so there is nothing left to suggest. */
    @Query("DELETE FROM Suggestion WHERE host = :host")
    suspend fun remove(host: String)
}
