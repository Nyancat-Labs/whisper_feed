/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.saulhdev.feeder.data.db.models.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.URL
import kotlin.time.Instant

@Dao
interface FeedSourceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feed: Feed): Long

    @Update
    suspend fun update(feed: Feed): Int

    @Update
    suspend fun updateAll(feeds: List<Feed>): Int

    @Query("DELETE FROM Feeds WHERE id IN (:ids)")
    suspend fun deleteFeedsByIds(ids: List<Long>)

    @Delete
    suspend fun delete(feed: Feed): Int

    @Query("SELECT * FROM Feeds WHERE url = :url AND removedAt = 0")
    suspend fun getFeedByURL(url: URL): Feed?

    @Query("SELECT * FROM Feeds WHERE id = :id")
    fun getFeedById(id: Long): Flow<Feed?>

    @Query("SELECT * FROM Feeds WHERE id = :id")
    suspend fun loadFeedById(id: Long): Feed?

    @Query("SELECT EXISTS (SELECT 1 FROM Feeds WHERE id = :id)")
    suspend fun existsById(id: Long): Boolean

    @Query("SELECT * FROM Feeds WHERE isEnabled IS 1 AND removedAt = 0")
    suspend fun loadFeeds(): List<Feed>

    @Query("SELECT * FROM Feeds WHERE isEnabled IS 1 AND removedAt = 0")
    fun getEnabledFeeds(): Flow<List<Feed>>

    @Query("SELECT id FROM Feeds WHERE isEnabled IS 1 AND removedAt = 0")
    suspend fun loadFeedIds(): List<Long>

    /**
     * The newest successful fetch among enabled sources, in epoch millis.
     *
     * Null when there are no enabled sources; zero when none has been fetched.
     */
    @Query("SELECT MAX(lastSync) FROM Feeds WHERE isEnabled IS 1 AND removedAt = 0")
    fun getNewestSync(): Flow<Long?>

    @Query("SELECT * FROM Feeds WHERE removedAt = 0")
    fun getAllFeeds(): Flow<List<Feed>>

    /** Every source, disabled ones included — loadFeeds() returns only enabled. */
    @Query("SELECT * FROM Feeds WHERE removedAt = 0")
    suspend fun loadAllFeeds(): List<Feed>

    @Query("SELECT DISTINCT tag FROM Feeds WHERE removedAt = 0 ORDER BY tag COLLATE NOCASE")
    fun getAllTagsFlow(): Flow<List<String>>

    @Query("SELECT DISTINCT tag FROM Feeds WHERE removedAt = 0 ORDER BY tag COLLATE NOCASE")
    suspend fun getAllTags(): List<String>

    @Query("SELECT * FROM feeds WHERE ',' || tag || ',' LIKE :pattern AND removedAt = 0")
    suspend fun loadFeedsByTag(pattern: String): List<Feed>

    /**
     * Removes the row outright, and with it every article it holds.
     *
     * `Article.feedId` carries `onDelete = CASCADE`, so this is not only a
     * feed being deleted — it is every article of that feed, including the
     * ones the reader bookmarked. Callers must not reach for it directly;
     * `SourcesRepository.deleteFeed` decides whether the row can go at all.
     */
    @Query("DELETE FROM Feeds WHERE id = :id")
    suspend fun deleteFeedById(id: Long)

    /** Marks a source as removed while keeping the row its saved articles need. */
    @Query("UPDATE Feeds SET removedAt = :at, isEnabled = 0 WHERE id = :id")
    suspend fun markRemoved(id: Long, at: Long)

    /** Puts a removed source back, which also restores what was saved from it. */
    @Query("UPDATE Feeds SET removedAt = 0, isEnabled = 1 WHERE id = :id")
    suspend fun restoreRemoved(id: Long)

    /** A removed row for this address, if one is waiting to be resurrected. */
    @Query("SELECT * FROM Feeds WHERE url = :url AND removedAt != 0 LIMIT 1")
    suspend fun findRemovedByUrl(url: URL): Feed?

    fun getFeedByTags(tags: Set<String>): Flow<List<Feed>> = flow {
        val allFeeds = tags.flatMap { tag ->
            loadFeedsByTag("%,$tag,%")
        }.distinctBy { it.id }
        emit(allFeeds)
    }

    @Query(
        """
       SELECT * FROM Feeds
       WHERE id is :feedId
       AND lastSync < :staleTime
       AND removedAt = 0
    """
    )
    suspend fun loadFeedIfStale(feedId: Long, staleTime: Long): Feed?

    @Query(
        """
       SELECT * FROM Feeds
       WHERE lastSync < :staleTime and isEnabled IS 1 and removedAt = 0
    """
    )
    suspend fun loadFeedIfStale(staleTime: Long): List<Feed>

    @Query(
        """
            UPDATE feeds
            SET currentlySyncing = :syncing
            WHERE id IS :feedId
        """
    )
    suspend fun setCurrentlySyncingOn(feedId: Long, syncing: Boolean): Int

    @Query(
        """
            UPDATE feeds
            SET isEnabled = :isEnabled
            WHERE id IS :feedId
        """
    )
    suspend fun setIsEnabled(feedId: Long, isEnabled: Boolean)

    @Query(
        """
            UPDATE feeds
            SET currentlySyncing = :syncing, lastSync = :lastSync
            WHERE id IS :feedId
        """
    )
    suspend fun setCurrentlySyncingOn(feedId: Long, syncing: Boolean, lastSync: Instant)

    /**
     * One more failure for this feed, and the moment the run began.
     *
     * `failingSince` is only set on the first failure of a run — COALESCE on a
     * zero — so the screen can say how long this has been going on rather than
     * how long ago the last attempt was.
     */
    @Query(
        """
    UPDATE Feeds SET
        consecutiveFailures = consecutiveFailures + 1,
        failingSince = CASE WHEN failingSince = 0 THEN :now ELSE failingSince END
    WHERE id = :feedId
    """
    )
    suspend fun recordFailure(feedId: Long, now: Long)

    /** A success wipes the record: one bad afternoon is not a dead feed. */
    @Query("UPDATE Feeds SET consecutiveFailures = 0, failingSince = 0 WHERE id = :feedId")
    suspend fun clearFailures(feedId: Long)

    /** Feeds that have failed enough times to be worth telling the reader about. */
    @Query(
        """
    SELECT * FROM Feeds WHERE consecutiveFailures >= :threshold AND removedAt = 0
    ORDER BY consecutiveFailures DESC
    """
    )
    fun getFailingFeeds(threshold: Int): Flow<List<Feed>>
}
