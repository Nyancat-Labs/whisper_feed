/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.saulhdev.feeder.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.db.models.SavedArticleRef
import com.saulhdev.feeder.data.db.models.ArticleFeedUrl
import com.saulhdev.feeder.data.db.models.DayCount
import com.saulhdev.feeder.data.db.models.HourCount
import com.saulhdev.feeder.data.db.models.ReadingTime
import com.saulhdev.feeder.data.db.models.SourceEngagement
import com.saulhdev.feeder.data.db.models.SourcePace
import com.saulhdev.feeder.data.db.models.SourceReadCount
import com.saulhdev.feeder.data.db.models.FeedLink
import com.saulhdev.feeder.data.db.models.MappedArticle
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface FeedArticleDao {
    @Upsert
    suspend fun upsert(vararg item: Article)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeedArticle(item: Article): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeedArticle(items: List<Article>): List<Long>

    @Update
    suspend fun updateFeedArticle(item: Article): Int

    @Update
    suspend fun updateFeedArticle(items: List<Article>): Int

    @Delete
    suspend fun deleteFeedArticle(item: Article): Int

    @Query(
        """
        DELETE FROM Article WHERE uuid IN (:ids)
        """
    )
    suspend fun deleteArticles(ids: List<String>): Int

    @Query(
        """
        DELETE FROM Article WHERE uuid IN (:ids)
        """
    )
    suspend fun deleteFeedArticle(ids: List<String>): Int

    @Query(
        """
        DELETE FROM Article WHERE feedId = :feedId
        """
    )
    suspend fun deleteFeedArticle(feedId: Long?): Int

    /**
     * Empties several feeds without emptying the reader.
     *
     * Bookmarked and pinned articles are kept. Clearing a source is a tidying
     * action — "this feed is sitting on nine hundred items I will never read"
     * — and someone who saved an article did the opposite of asking for it to
     * go. The next sync refills the feed from the server anyway, so the only
     * thing this can destroy for good is the one thing it excludes.
     */
    @Query(
        """
        DELETE FROM Article
        WHERE feedId IN (:feedIds) AND bookmarked = 0 AND pinned = 0
        """
    )
    suspend fun clearArticlesForFeeds(feedIds: List<Long>): Int

    /** How many articles of a source the reader has asked to keep. */
    @Query("SELECT COUNT(*) FROM Article WHERE feedId = :feedId")
    suspend fun countInFeed(feedId: Long): Int

    @Query("SELECT uuid FROM Article")
    suspend fun loadAllUuids(): List<String>

    @Query("SELECT COUNT(*) FROM Article WHERE feedId = :feedId AND (bookmarked = 1 OR pinned = 1)")
    suspend fun countSavedInFeed(feedId: Long): Int

    @Query("SELECT * FROM Article WHERE guid IS :guid AND feedId IS :feedId")
    suspend fun loadArticle(guid: String, feedId: Long?): Article?

    @Query("SELECT * FROM Article WHERE uuid IS :id")
    suspend fun getArticleById(id: String): Article?

    @Query("SELECT * FROM Article WHERE uuid IS :id")
    fun loadArticleById(id: String): Flow<Article?>

    @Query(
        """
        SELECT Article.* FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Feeds.isEnabled = 1
    """
    )
    suspend fun loadAllEnabledArticles(): List<Article>

    @Query(
        """
        SELECT uuid FROM Article
        WHERE feedId = :feedId AND pinned = 0 AND bookmarked = 0
        AND pubDateV2 < :minKeptPubDate
        """
    )
    suspend fun getItemsToBeCleanedFromFeed(
        feedId: Long,
        minKeptPubDate: Long,
    ): List<String>

    @Query(
        """
        UPDATE Article SET readAt = :readAt WHERE uuid = :id AND readAt = 0
        """
    )
    suspend fun markRead(id: String, readAt: Long): Int

    /**
     * The unread articles currently in the feed, so a bulk mark can be undone.
     *
     * Collected before the write rather than derived after it: once everything
     * is marked with the same timestamp there is no way to tell what this
     * action changed from what was already read.
     */
    @Query(
        """
        SELECT Article.uuid FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Feeds.isEnabled = 1 AND Article.readAt = 0
          AND Article.primarySortTime < :before
        """
    )
    suspend fun unreadIdsBefore(before: Long): List<String>

    /** How many [unreadIdsBefore] would return, for the choice of how far back. */
    @Query(
        """
        SELECT COUNT(*) FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Feeds.isEnabled = 1 AND Article.readAt = 0
          AND Article.primarySortTime < :before
        """
    )
    suspend fun unreadCountBefore(before: Long): Int

    @Query("UPDATE Article SET readAt = :readAt WHERE uuid IN (:ids)")
    suspend fun markReadBatch(ids: List<String>, readAt: Long)

    /** How much reading an article has already accrued, for the tally. */
    @Query("SELECT readMs FROM Article WHERE uuid = :id")
    suspend fun readingMsOf(id: String): Long?



    /**
     * The address of a recent article from this feed.
     *
     * The only record of *whose* feed it is when the feed's own address does
     * not say. A FeedBurner or YouTube URL names the service, not the
     * publication; an article's link names the publisher, because that is
     * where the article lives.
     */
    @Query(
        """
        SELECT link FROM Article
        WHERE feedId = :feedId AND link IS NOT NULL AND link != ''
        ORDER BY primarySortTime DESC LIMIT 1
        """
    )
    suspend fun latestArticleLink(feedId: Long): String?

    /**
     * Records that an article was opened, the first time it is.
     *
     * `openedAt = 0` in the WHERE clause so a reopened article keeps the time
     * of the first opening rather than being rewritten on every visit: this
     * answers "was this wanted", which is a thing that happened once, and a
     * moving timestamp would quietly make a re-read look like a new read.
     *
     * Marks it read in the same statement, because opening an article is the
     * least ambiguous way of reading one and leaving it unread would be a lie
     * the rest of the app then has to work around.
     */
    @Query(
        """
        UPDATE Article SET openedAt = :at, readAt = CASE WHEN readAt = 0 THEN :at ELSE readAt END
        WHERE uuid = :id AND openedAt = 0
        """
    )
    suspend fun markOpened(id: String, at: Long): Int

    /** Retracts a breaking story's promotion. See `Article.dismissedAt`. */
    @Query("UPDATE Article SET dismissedAt = :at WHERE uuid = :id")
    suspend fun setDismissed(id: String, at: Long)

    /**
     * Adds to an article's time on screen.
     *
     * Capped in SQL rather than in Kotlin so a read-modify-write race between
     * the feed and the overlay cannot lose an increment or exceed the ceiling
     * — both surfaces run this against the same row, and the arithmetic is the
     * thing that has to be atomic.
     */
    @Query(
        """
        UPDATE Article SET dwellMs = MIN(dwellMs + :addMs, :capMs)
        WHERE uuid = :id
        """
    )
    suspend fun addDwell(id: String, addMs: Long, capMs: Long)

    /**
     * Adds to the time spent inside an article, up to a ceiling.
     *
     * An increment rather than a total, and that is the whole design. A
     * setter taking a duration would need somebody to have worked that
     * duration out, and the only way to work it out is to subtract a stored
     * start time from the present — which is exactly the arithmetic that
     * produces a three-day read when the reader closes the app mid-article
     * and comes back on Thursday. There is no start time here to subtract.
     *
     * Capped in SQL for the same reason [addDwell] is: the article page and
     * anything else that ever times a read are writing to one row, and the
     * ceiling has to hold across both rather than per-caller.
     */
    @Query(
        """
        UPDATE Article SET readMs = MIN(readMs + :addMs, :capMs)
        WHERE uuid = :id
        """
    )
    suspend fun addReading(id: String, addMs: Long, capMs: Long)


    /**
     * How much ground each source's articles cover in time, and how many there
     * are, which is everything needed to work out how often it publishes.
     *
     * Rows with no usable date are excluded rather than counted as 1970: a
     * single one of those would stretch a source's span to fifty years and
     * make everything it publishes look impossibly rare.
     */
    @Query(
        """
        SELECT feedId AS feedId,
            COUNT(*) AS articles,
            MAX(primarySortTime) AS newest,
            MIN(primarySortTime) AS oldest
        FROM Article
        WHERE primarySortTime > 0
        GROUP BY feedId
        """
    )
    fun sourcePace(): Flow<List<SourcePace>>

    /** Every article a sync server has claimed; see GoogleReaderService. */
    @Query("SELECT uuid, remoteId, readAt, bookmarked FROM Article WHERE remoteId IS NOT NULL")
    suspend fun loadMapped(): List<MappedArticle>

    @Query("UPDATE Article SET bookmarked = 1 WHERE uuid IN (:ids)")
    suspend fun setBookmarked(ids: List<String>)

    /** Every article's source and address, for telling which sources are one feed. */
    @Query("SELECT feedId, link FROM Article WHERE link IS NOT NULL AND link != ''")
    suspend fun loadFeedLinks(): List<FeedLink>

    @Query("UPDATE Article SET readAt = 0 WHERE uuid IN (:ids)")
    suspend fun unmarkRead(ids: List<String>)

    /**
     * How many articles from each source have been read since [since].
     *
     * The raw material for the reading-habit term in the weighting: a source
     * someone keeps opening is one they want more of. Grouped in SQL because
     * the alternative is loading every article to count them in Kotlin.
     */
    @Query(
        """
        SELECT feedId AS feedId, COUNT(*) AS reads FROM Article
        WHERE readAt >= :since
        GROUP BY feedId
        """
    )
    fun readsPerSource(since: Long): Flow<List<SourceReadCount>>

    /**
     * What each source earned, in the four engagement bands.
     *
     * The CASE is ordered strongest first, so an opened article is counted as
     * opened whatever its time on screen — an article read in a browser may
     * have spent a second in the list on the way there, and that is not the
     * interesting fact about it.
     *
     * Summed in SQL rather than over a list in Kotlin because the alternative
     * is loading every article of the last thirty days to add up four numbers.
     */
    @Query(
        """
        SELECT feedId AS feedId,
            SUM(CASE
                WHEN readMs >= :finishedMs THEN :finished
                WHEN readMs >= :readingMs THEN :read
                WHEN openedAt > 0 THEN :opened
                WHEN dwellMs >= :heldMs THEN :held
                WHEN dwellMs >= :glancedMs THEN :glanced
                ELSE :passed
            END) AS score,
            SUM(CASE WHEN openedAt > 0 THEN 1 ELSE 0 END) AS opened,
            COUNT(*) AS seen
        FROM Article
        WHERE readAt >= :since
        GROUP BY feedId
        """
    )
    fun engagementPerSource(
        since: Long,
        glancedMs: Long,
        heldMs: Long,
        readingMs: Long,
        finishedMs: Long,
        passed: Int,
        glanced: Int,
        held: Int,
        opened: Int,
        read: Int,
        finished: Int,
    ): Flow<List<SourceEngagement>>

    /**
     * Every row in the table, with no join and no condition.
     *
     * Deliberately unfiltered: this exists to tell "the database is empty"
     * apart from "the database is full and something is hiding it", and a
     * count that shared the feed query's conditions could not do that.
     */
    @Query("SELECT COUNT(*) FROM Article")
    suspend fun countAll(): Int

    /**
     * What is stored, by age, for the diagnostics report: whether the clean-up
     * keeps up with what comes in. Ages are by publication date, which is
     * what the clean-up goes by; see getItemsToBeCleanedFromFeed.
     */
    @Query(
        """
        SELECT COUNT(*) AS total,
            COALESCE(SUM(bookmarked = 1), 0) AS saved,
            COALESCE(SUM(pinned = 1), 0) AS pinned,
            COALESCE(SUM(readAt = 0), 0) AS unread,
            COALESCE(SUM(pubDateV2 >= :dayAgo), 0) AS underDay,
            COALESCE(SUM(pubDateV2 >= :threeDaysAgo AND pubDateV2 < :dayAgo), 0) AS dayToThree,
            COALESCE(SUM(pubDateV2 >= :cutoff AND pubDateV2 < :threeDaysAgo), 0) AS threeToRange,
            COALESCE(SUM(pubDateV2 < :cutoff AND bookmarked = 0 AND pinned = 0), 0) AS pastRange,
            COALESCE(SUM(pubDateV2 > :ahead), 0) AS datedAhead,
            COALESCE(SUM(pubDateV2 <= 0), 0) AS undated,
            COALESCE(SUM(firstSyncedTime >= :dayAgo), 0) AS addedDay,
            MIN(CASE WHEN bookmarked = 0 AND pinned = 0 AND pubDateV2 > 0 THEN pubDateV2 END) AS oldest
        FROM Article
        """
    )
    suspend fun storageCounts(dayAgo: Long, threeDaysAgo: Long, cutoff: Long, ahead: Long): StorageCounts

    /** The sources holding the most articles, for the diagnostics report. */
    @Query(
        """
        SELECT f.title AS title, COUNT(*) AS articles FROM Article a
        JOIN Feeds f ON a.feedId = f.id
        GROUP BY a.feedId ORDER BY articles DESC LIMIT :limit
        """
    )
    suspend fun largestSources(limit: Int): List<SourceArticleCount>

    @Query(
        """
        SELECT COUNT(*) FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Feeds.isEnabled = 1 AND Article.readAt = 0
        """
    )
    fun countUnread(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Feeds.isEnabled = 1 AND Article.readAt >= :since
        """
    )
    fun countReadSince(since: Long): Flow<Int>

    /**
     * Articles whose full text should be prefetched.
     *
     * The ArticleIdWithLink view answers this for the per-source switch, but a
     * view takes no parameters and the global switch is a parameter, so the
     * same rule is spelled out here with [allFeeds] widening it.
     *
     * Saved articles always. Others only while unread and newer than
     * [since]: the backlog was every article those feeds had ever brought,
     * two thousand and growing, and one night's runs downloaded 48 MB of
     * pages for articles that had already gone by unopened.
     */
    @Query(
        """
        SELECT Article.uuid, Article.link FROM Article
        JOIN Feeds f ON Article.feedId = f.id
        WHERE Article.bookmarked = 1
           OR ((:allFeeds OR f.fullTextByDefault = 1)
               AND Article.readAt = 0 AND Article.primarySortTime > :since)
        ORDER BY Article.bookmarked DESC, Article.primarySortTime DESC
        """
    )
    fun getArticleIdLinks(allFeeds: Boolean, since: Long): Flow<List<ArticleIdWithLink>>

    /** Saved articles, for downloading their whole text the moment they are saved. */
    @Query("SELECT uuid, link FROM Article WHERE bookmarked = 1")
    suspend fun getSavedArticleIdLinks(): List<ArticleIdWithLink>

    @Query("SELECT uuid, link, imageUrl FROM Article WHERE bookmarked = 1")
    suspend fun getSavedArticleRefs(): List<SavedArticleRef>

    /** Each article's feed address, for finding it in that feed on a server. */
    @Query(
        """
        SELECT Article.uuid AS uuid, Feeds.url AS url FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Article.uuid IN (:ids)
        """
    )
    suspend fun feedUrlsOf(ids: List<String>): List<ArticleFeedUrl>

    // Embedded FeedItem
    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Feeds.isEnabled = 1
    ORDER BY Article.primarySortTime DESC
    LIMIT :limit
    """
    )
    /**
     * The feed, newest first, capped.
     *
     * It had no cap at all: every article of every enabled source, whole rows
     * including the full article text, rebuilt on every emission. Forty-five
     * sources is a few thousand of those held in memory to show the twenty on
     * screen. The cap is a window rather than a page — see FEED_WINDOW — and
     * search passes a larger one, because a search that only covered the
     * newest few hundred would quietly stop being true.
     */
    fun getAllEnabledFeedItems(limit: Int): Flow<List<FeedItem>>

    /**
     * The same query, run once on demand.
     *
     * The Flow above re-runs on every write to Article or Feeds, and a sync
     * writes to both constantly — measured at thirty invalidations a second
     * while sources were being added. Each one rebuilt five hundred whole
     * rows, and the debounce downstream then threw most of them away, because
     * a debounce discards a value it has already paid for.
     *
     * Debouncing the *signal* instead needs a query that can be run when the
     * burst has settled rather than one that insists on running first. See
     * ArticleRepository.getEnabledFeedItems.
     */
    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Feeds.isEnabled = 1
    ORDER BY Article.primarySortTime DESC
    LIMIT :limit
    """
    )
    suspend fun loadAllEnabledFeedItems(limit: Int): List<FeedItem>

    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Article.bookmarked = 1
    ORDER BY Article.pinned DESC, Article.pubDateV2 DESC
    """
    )
    /**
     * Saved articles, from every source, whether or not it is still on.
     *
     * This used to require `Feeds.isEnabled = 1`, so turning a source off
     * took every article the reader had saved from it out of Bookmarks. A
     * bookmark is the strongest thing anybody says about an article — the
     * feed's own hide-read rule already exempts saved and pinned articles for
     * exactly this reason — and a switch about whether to keep *fetching* a
     * source has no business revoking it.
     *
     * The rows were never deleted, so they come back for anybody who had
     * saved something from a source they later switched off.
     */
    fun getAllBookmarkedFeedItems(): Flow<List<FeedItem>>

    /**
     * [getAllBookmarkedFeedItems], run once on demand; see
     * [loadAllEnabledFeedItems].
     *
     * The query is the one above, character for character — the join that
     * looks redundant is not (an article whose feed is gone must not appear),
     * and the pinned-first ordering is what Bookmarks shows. A "simplified"
     * copy here would be a second, quietly different Bookmarks screen.
     */
    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Article.bookmarked = 1
    ORDER BY Article.pinned DESC, Article.pubDateV2 DESC
    """
    )
    suspend fun loadAllBookmarkedFeedItems(): List<FeedItem>

    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Article.feedId IN (:feedIds) AND Feeds.isEnabled = 1
    ORDER BY Article.primarySortTime DESC
    LIMIT :limit
    """
    )
    fun getFeedItemsByFeedIdsFlow(feedIds: List<Long>, limit: Int): Flow<List<FeedItem>>

    /** [getFeedItemsByFeedIdsFlow], run once on demand; see [loadAllEnabledFeedItems]. */
    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Article.feedId IN (:feedIds) AND Feeds.isEnabled = 1
    ORDER BY Article.primarySortTime DESC
    LIMIT :limit
    """
    )
    suspend fun loadFeedItemsByFeedIds(feedIds: List<Long>, limit: Int): List<FeedItem>



    /**
     * Writes a feed's articles in one transaction and returns them paired with
     * their body text, uuids assigned, for the caller to persist to disk.
     *
     * The blob writes used to happen inside this transaction, one file at a
     * time, via a `withContext(Dispatchers.IO)` callback. That held the write
     * lock for the whole of a feed's disk I/O and switched dispatchers inside a
     * Room transaction, which Room confines to its own. Inserts were also issued
     * one row at a time despite a list overload existing.
     */
    @Transaction
    suspend fun insertOrUpdate(
        itemsWithText: List<Pair<Article, String>>
    ): List<Pair<Article, String>> {
        val (toUpdateItems, toInsertItems) = itemsWithText.partition { (item, _) ->
            item.uuid.isNotEmpty()
        }

        updateFeedArticle(toUpdateItems.map { (item, _) -> item })

        val inserted = toInsertItems.map { (item, text) ->
            item.copy(uuid = UUID.randomUUID().toString()) to text
        }
        insertFeedArticle(inserted.map { (article, _) -> article })

        return toUpdateItems + inserted
    }

    /**
     * The most recent article in each feed, as epoch milliseconds.
     *
     * Deliberately not `Feeds.lastSync`, which records when Whisper last
     * *fetched* a feed and says nothing about whether anything was there. A
     * feed checked faithfully every hour for a year that has published nothing
     * since March has a very recent lastSync and is the exact feed somebody
     * sorting this way is looking for.
     *
     * primarySortTime rather than pubDateV2: an item with no publication date
     * of its own falls back to when it arrived, so this is never zero for a
     * feed that has articles.
     */
    @Query(
        """
    SELECT feedId AS feedId, MAX(primarySortTime) AS latest
    FROM Article GROUP BY feedId
    """
    )
    fun latestArticlePerFeed(): Flow<List<FeedLatestArticle>>

    /**
     * Attaches a server's id to the article at a given link.
     *
     * Matched on link because that is the only thing both sides know. The
     * article was fetched from the feed by this app, so it has a local uuid the
     * server has never seen; the server names the same article with an id this
     * app has never seen. The canonical URL is what they agree on.
     */
    @Query("UPDATE Article SET remoteId = :remoteId WHERE link = :link AND remoteId IS NULL")
    suspend fun attachRemoteId(link: String, remoteId: String): Int

    /** The server's id for one article, for pushing a change back. */
    @Query("SELECT remoteId FROM Article WHERE uuid = :uuid")
    suspend fun remoteIdFor(uuid: String): String?

    /**
     * Articles the reader actually read, most recent first.
     *
     * `readAt` rather than delivery: what was put in front of somebody says
     * nothing, and the whole of the discovery pass rests on the difference.
     */
    @Query(
        """
    SELECT * FROM Article
    WHERE readAt >= :since AND readAt != 0
    ORDER BY readAt DESC LIMIT :limit
    """
    )
    suspend fun readArticlesSince(since: Long, limit: Int): List<Article>

    /** How many articles a server has claimed, for the sync log. */
    @Query("SELECT COUNT(*) FROM Article WHERE remoteId IS NOT NULL")
    suspend fun countWithRemoteId(): Int
}

/** One row of [FeedArticleDao.latestArticlePerFeed]. */
data class FeedLatestArticle(
    val feedId: Long,
    val latest: Long,
)

/** See FeedArticleDao.storageCounts. [oldest] is a publication time, null when nothing qualifies. */
data class StorageCounts(
    val total: Int,
    val saved: Int,
    val pinned: Int,
    val unread: Int,
    val underDay: Int,
    val dayToThree: Int,
    val threeToRange: Int,
    val pastRange: Int,
    val datedAhead: Int,
    val undated: Int,
    val addedDay: Int,
    val oldest: Long?,
)

/** A source's title and how many articles it has stored. */
data class SourceArticleCount(val title: String, val articles: Int)
