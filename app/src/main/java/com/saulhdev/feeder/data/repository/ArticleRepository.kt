/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
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

package com.saulhdev.feeder.data.repository

import com.saulhdev.feeder.data.db.models.MappedArticle
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.db.models.SavedArticleRef
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.utils.blobInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import com.saulhdev.feeder.data.db.models.DayCount
import com.saulhdev.feeder.data.db.models.HourCount
import com.saulhdev.feeder.data.db.models.ReadingTime
import com.saulhdev.feeder.data.db.models.SourceEngagement
import com.saulhdev.feeder.ui.overlay.ArticleWeight
import com.saulhdev.feeder.ui.overlay.sourcePaceHours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import com.saulhdev.feeder.utils.FeedTrace
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleRepository(db: NeoFeedDb) {

    /**
     * Told when an article stops being saved, with its source's id.
     *
     * A callback rather than a repository reference, because sources already
     * depend on articles and the reverse would be a cycle Koin would have to
     * be talked out of. Set once at startup.
     */
    var onSavedRemoved: ((Long) -> Unit)? = null

    /**
     * Told when an article becomes saved, here or from the account, so its
     * whole text can be downloaded for reading later. Set once at startup.
     */
    var onSaved: (() -> Unit)? = null

    /** Told when an article stops being saved, so its kept pictures can go. */
    var onUnsaved: ((String) -> Unit)? = null

    /**
     * Told when the reader changes whether articles are read, by local id.
     *
     * Only for what the reader did: a sync applying the server's read state
     * goes through [applyServerRead], which says nothing, or every change
     * would be sent straight back to the server it came from. Set at startup;
     * see NeoApp, and GoogleReaderState for where the changes wait.
     */
    var onReadChanged: ((ids: List<String>, read: Boolean) -> Unit)? = null

    /** The same, for saving and unsaving. See [onReadChanged]. */
    var onStarredChanged: ((id: String, starred: Boolean) -> Unit)? = null
    private val cc = Dispatchers.IO
    private val jcc = Dispatchers.IO + SupervisorJob()

    /** Kept for [flushDwell]'s transaction; the DAOs below come from it. */
    private val db = db

    /**
     * Dwell increments, collected and written together. See [DwellBatch].
     *
     * One transaction per batch, because Room notifies once at commit however
     * many rows it carries — and that single fact is the whole of why this
     * exists.
     */
    private val dwellBatch = DwellBatch(
        scope = CoroutineScope(jcc),
        windowMs = DWELL_FLUSH_MS,
    ) { batch ->
        db.withTransaction {
            batch.forEach { (id, millis) ->
                articlesDao.addDwell(id, millis, DWELL_CAP_MS)
            }
        }
        // Counted here rather than at either caller: most flushes are the
        // timer's, and a counter at the explicit flush alone would report the
        // one kind that happens least.
        FeedTrace.dwellFlushed(batch.size)
    }

    private val articlesDao = db.feedArticleDao()
    private val tallyDao = db.readingTallyDao()
    private val feedsDao = db.feedSourceDao()

    suspend fun deleteArticles(ids: List<String>) = withContext(jcc) {
        articlesDao.deleteArticles(ids)
    }

    suspend fun deleteArticlesForFeed(feedId: Long) = withContext(jcc) {
        articlesDao.deleteFeedArticle(feedId)
    }

    suspend fun deleteArticlesMatchingWords(words: Set<String>, filesDir: File) = withContext(jcc) {
        val blocked = words.map { it.lowercase() }.filter { it.isNotBlank() }
        if (blocked.isEmpty()) return@withContext
        val articles = articlesDao.loadAllEnabledArticles()
        val toDelete = articles.mapNotNull { article ->
            val haystack = buildString {
                append(article.title)
                append(article.plainTitle)
                append(article.description)
                append(article.plainSnippet)
                article.author?.let { append(it) }
                article.link?.let { append(it) }
                try {
                    blobInputStream(article.uuid, filesDir).bufferedReader().use { append(it.readText()) }
                } catch (_: Throwable) {
                }
            }.lowercase()
            if (blocked.any { haystack.contains(it) }) article.uuid else null
        }
        if (toDelete.isNotEmpty()) {
            articlesDao.deleteArticles(toDelete)
        }
    }

    suspend fun getArticleByGuid(guid: String, feedId: Long): Article? {
        return withContext(jcc) {
            articlesDao.loadArticle(guid = guid, feedId = feedId)
        }
    }

    fun getArticleById(articleId: String): Flow<Article?> =
        articlesDao.loadArticleById(id = articleId)
            .flowOn(cc)

    fun getEnabledFeedItems(limit: Int = FEED_WINDOW): Flow<List<FeedItem>> =
        whenChanged { articlesDao.loadAllEnabledFeedItems(limit) }

    /**
     * Articles from every source carrying any of [tags].
     *
     * This used to be a single query matching `Feeds.tag IN (:tags)`, which
     * only ever matched a feed whose *entire* tag string equalled a chip. A
     * feed tagged "Tech,News" therefore matched neither the Tech chip nor the
     * News chip and simply disappeared when either was selected — invisibly,
     * because single-category feeds still worked.
     *
     * Matching a comma list needs one LIKE per tag, which Room cannot express
     * against a set in a static query, so the feeds are resolved first and the
     * articles fetched by id. The id list is deduplicated before it reaches the
     * article query: a sync writes `currentlySyncing` to Feeds constantly, and
     * without that every one of those writes would restart the query for an
     * identical set of ids.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getFeedItemsByTags(tags: Set<String>, limit: Int = FEED_WINDOW): Flow<List<FeedItem>> =
        feedsDao.getEnabledFeeds()
            .map { feeds ->
                feeds.filter { feed -> feed.tags.any { it in tags } }.map(Feed::id)
            }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList())
                else whenChanged { articlesDao.loadFeedItemsByFeedIds(ids, limit) }
            }

    /**
     * Persists articles, then writes their bodies to disk *outside* the database
     * transaction — the writes are per-article file I/O and have no business
     * holding the write lock while they run.
     */
    suspend fun updateOrInsertArticle(
        itemsWithText: List<Pair<Article, String>>,
        block: suspend (Article, String) -> Unit
    ) = withContext(jcc) {
        val stored = articlesDao.insertOrUpdate(itemsWithText)
        stored.forEach { (article, text) -> block(article, text) }
    }

    /** Records that an article was opened. First open wins; re-opens are a no-op. */
    suspend fun markRead(articleId: String) = withContext(jcc) {
        // Tallied only when the update actually changed a row. The query
        // carries `AND readAt = 0`, so re-reading an article is already a
        // no-op here; counting the call rather than the change would let a
        // list that redraws inflate the chart.
        if (articlesDao.markRead(articleId, System.currentTimeMillis()) == 1) {
            tally(seen = 1)
            onReadChanged?.invoke(listOf(articleId), true)
        }
    }

    /**
     * Records an article being opened to read in full.
     *
     * The strongest signal the app has, and the only unambiguous one:
     * everything else is inferred from a card going past. Written at the
     * moment of the tap rather than on the way back, because there may be no
     * way back — an article opened in the browser from the launcher overlay
     * often never returns to Whisper at all, and treating that as a failure to
     * read would punish exactly the articles somebody went furthest to read.
     */
    suspend fun markOpened(articleId: String) = withContext(jcc) {
        // The same query sets readAt when it was still zero, so an article
        // opened without ever being scrolled past counts once as both.
        if (articlesDao.markOpened(articleId, System.currentTimeMillis()) == 1) {
            tally(seen = 1, opened = 1)
            onReadChanged?.invoke(listOf(articleId), true)
        }
    }

    /**
     * Takes back a breaking story's promotion, at the reader's request.
     *
     * Not tallied and not a read: dismissing is the reader declining to be
     * shown something, which is the opposite of having read it, and counting
     * it either way would teach the weighting the wrong lesson.
     */
    suspend fun dismissStory(articleId: String) = withContext(jcc) {
        articlesDao.setDismissed(articleId, System.currentTimeMillis())
    }

    /** Where a recent article from this feed actually lives; see the DAO. */
    suspend fun publisherLink(feedId: Long): String? = withContext(jcc) {
        articlesDao.latestArticleLink(feedId)
    }

    /**
     * Reloads the feed when its tables change, once the change has finished.
     *
     * A Room `Flow` query re-runs on every write to any table it names, and
     * this one names Article and Feeds — both of which a sync writes to
     * continuously. Instrumentation measured thirty of those a second while
     * sources were being added, each rebuilding five hundred whole article
     * rows, full text included, against a 256MB heap. The collector was
     * freeing 100-200MB per cycle and threads were blocking on allocation.
     *
     * A 300ms debounce was already sitting downstream and was not helping,
     * for a reason worth stating plainly: a debounce drops a value it has
     * already been handed. The query had run and built its rows before
     * anything could decide they were not wanted. Of 151 emissions in one
     * five-second window, 25 were used.
     *
     * So the debounce moves to the signal. `createFlow` reports *that* the
     * tables changed without reading them, which costs nothing to discard,
     * and the expensive query runs once the burst has settled. `mapLatest`
     * abandons a load still running when another change arrives, so a long
     * sync cannot queue work up behind itself.
     *
     * The first load is immediate. Tapping a category and waiting a third of
     * a second for the list would be trading one visible fault for another.
     */
    @OptIn(FlowPreview::class)
    private fun <T> whenChanged(load: suspend () -> T): Flow<T> = flow {
        // `first` is declared inside the builder, so each collector gets its
        // own. Declared outside it — which is where it was first written, and
        // where the version this replaced had it — one flag is shared by every
        // collection of the same Flow, and the second collector inherits a
        // `false` set by the first. Its opening list then arrives 300ms late
        // for no reason it could know about, on a screen that has just been
        // opened. The old code got away with the same shape only because it
        // sat inside a flatMapLatest that rebuilt the flow every time.
        var first = true
        emitAll(
            db.invalidationTracker.createFlow("Article", "Feeds", emitInitialState = true)
                .onEach { FeedTrace.invalidated() }
                .debounce {
                    if (first) {
                        first = false
                        0L
                    } else {
                        FEED_INVALIDATION_DEBOUNCE_MS
                    }
                }
                .mapLatest { load() }
        )
    }.flowOn(cc)

    /**
     * Adds to an article's accumulated time on screen, up to [DWELL_CAP_MS].
     *
     * Held in memory and written in batches, which is a change of shape rather
     * than of meaning, and the reason is worth setting down.
     *
     * The tracker calls this once per article as it leaves the screen, which
     * during a scroll is a steady stream. Each call used to be its own UPDATE
     * on Article — and Room invalidates per *table*, so every one of them
     * re-ran the feed query. That query returns five hundred whole article
     * rows including their full text; the pipeline downstream debounces at
     * 300ms, but a debounce discards the list after it has been built, so the
     * cost was paid every time and thrown away most times.
     *
     * A diagnostics report put a number on it: 100–200MB collected per cycle
     * on a 256MB heap, a cycle every six to eight seconds, for the length of
     * the scroll, with threads blocking on the allocations in between.
     *
     * So the increments accumulate here and go out together. One transaction
     * is one invalidation however many rows it carries, which turns a scroll's
     * worth of re-queries into one every [DWELL_FLUSH_MS]. What is written is
     * identical — the same articles, the same milliseconds, the same cap
     * applied by the same SQL.
     *
     * The risk is bounded and named: up to [DWELL_FLUSH_MS] of dwell is lost
     * if the process dies mid-scroll. Dwell is a weighting signal built from
     * thousands of small observations, not something the reader typed, and
     * losing five seconds of it changes nothing they could notice. Anything
     * that *is* the reader's — read state, bookmarks, pins — is written
     * immediately, as before, and none of it comes through here.
     */
    suspend fun addDwell(articleId: String, millis: Long) {
        if (millis <= 0L) return
        FeedTrace.dwellAsked()
        dwellBatch.add(articleId, millis)
    }

    /**
     * Writes any dwell still held, now.
     *
     * Called when the reader leaves the feed. The batching timer is right
     * while they are scrolling and wrong the moment they stop: the reason to
     * hold increments is that more are coming, and leaving is that ceasing to
     * be true.
     */
    suspend fun flushDwell() = withContext(jcc) {
        dwellBatch.flush()
        Unit
    }

    /**
     * Adds to the time spent inside an article, up to [READ_CAP_MS].
     *
     * Called repeatedly with small increments while the article is on screen,
     * rather than once with a total at the end. See [addReading]'s caller for
     * why, and `Article.readMs` for why there is no total to pass.
     */
    suspend fun addReading(articleId: String, millis: Long) = withContext(jcc) {
        if (millis <= 0L) return@withContext
        // Read first so the tally gets what was actually applied rather than
        // what was asked for. The article's own total is capped, and once it
        // is at the cap every further tick adds nothing — tallying the request
        // would keep adding time to the chart that nothing is counting.
        val before = articlesDao.readingMsOf(articleId) ?: return@withContext
        val applied = appliedReading(before, millis, READ_CAP_MS)
        if (applied <= 0L) return@withContext
        articlesDao.addReading(articleId, millis, READ_CAP_MS)
        tally(readMs = applied, timed = if (before == 0L) 1 else 0)
    }

    /**
     * Marks every unread article read, returning what it changed.
     *
     * The ids come back so the action can be undone: after the write every
     * article carries the same timestamp, and nothing distinguishes the ones
     * this marked from the ones that were already read.
     *
     * Chunked because SQLite binds a limited number of host parameters per
     * statement — a few hundred unread articles is an ordinary feed, and one
     * IN clause holding all of them fails rather than truncating.
     */
    suspend fun markAllRead(before: Long = Long.MAX_VALUE): List<String> = withContext(jcc) {
        val ids = articlesDao.unreadIdsBefore(before)
        val now = System.currentTimeMillis()
        ids.chunked(SQLITE_ARG_LIMIT).forEach { articlesDao.markReadBatch(it, now) }
        if (ids.isNotEmpty()) {
            tally(seen = ids.size)
            onReadChanged?.invoke(ids, true)
        }
        ids
    }

    /** Unread articles dated before [before], as [markAllRead] would count them. */
    suspend fun unreadCountBefore(before: Long): Int = withContext(jcc) {
        articlesDao.unreadCountBefore(before)
    }

    /** Puts back what [markAllRead], or a run of scroll marks, took. */
    suspend fun unmarkRead(ids: List<String>) = withContext(jcc) {
        ids.chunked(SQLITE_ARG_LIMIT).forEach { articlesDao.unmarkRead(it) }
        // Out of the current hour, which is where they went in a moment ago.
        // An undo that crosses the turn of an hour takes them out of the wrong
        // bucket; the subtraction floors at zero so that is a rounding error
        // in one bar rather than a negative count.
        if (ids.isNotEmpty()) {
            untally(seen = ids.size)
            onReadChanged?.invoke(ids, false)
        }
    }

    /** Reads per source since [since], for the reading-habit weight term. */
    /**
     * What each source earned from the reader, since [since].
     *
     * The band constants are passed in rather than written into the query so
     * that the four numbers live in one place — beside the rest of the
     * weighting, where anybody arguing with them will be looking.
     */
    fun engagementPerSource(since: Long): Flow<Map<Long, SourceEngagement>> =
        articlesDao.engagementPerSource(
            since = since,
            glancedMs = ArticleWeight.GLANCED_MS,
            heldMs = ArticleWeight.HELD_MS,
            readingMs = ArticleWeight.READING_MS,
            finishedMs = ArticleWeight.FINISHED_MS,
            passed = ArticleWeight.BAND_PASSED,
            glanced = ArticleWeight.BAND_GLANCED,
            held = ArticleWeight.BAND_HELD,
            opened = ArticleWeight.BAND_OPENED,
            read = ArticleWeight.BAND_READ,
            finished = ArticleWeight.BAND_FINISHED,
        ).map { rows -> rows.associateBy { it.feedId } }

    /**
     * Today's date and hour, as the tally keys them.
     *
     * Local rather than UTC. The chart answers "what are my days like", and a
     * reader's 11pm belongs on their own Tuesday whatever UTC thinks.
     */
    private fun dayKey(): String = LocalDate.now().toString()

    private fun hourKey(): String = "%02d".format(LocalDateTime.now().hour)

    /** Adds to the current hour's counts. See [ReadingTally] for why it exists. */
    private suspend fun tally(
        seen: Int = 0,
        opened: Int = 0,
        readMs: Long = 0L,
        timed: Int = 0,
    ) = tallyDao.add(dayKey(), hourKey(), seen, opened, readMs, timed)

    private suspend fun untally(seen: Int = 0, opened: Int = 0) =
        tallyDao.subtract(dayKey(), hourKey(), seen, opened)

    /**
     * Reading per day, from the tally rather than from the articles.
     *
     * The quiet days are filled in here, and they are as much of the answer as
     * the counts are: the query returns only days that have a row, and a chart
     * drawn from those alone lies twice over — a fortnight away from the app
     * closes up into nothing, and the bars either side of the gap end up
     * adjacent, so a break in reading reads as continuous reading.
     *
     * [today] is a parameter so a test can pin the window; nothing else passes
     * it.
     */
    fun readingByDay(
        sinceDay: String,
        days: Int,
        today: LocalDate = LocalDate.now(),
    ): Flow<List<DayCount>> =
        tallyDao.byDay(sinceDay).map { rows -> fillDays(rows, days, today) }

    fun readingByHour(sinceDay: String): Flow<List<HourCount>> =
        tallyDao.byHour(sinceDay).map { rows -> fillHours(rows) }

    /** Time spent reading since [sinceDay], from the tally. */
    fun readingTime(sinceDay: String): Flow<ReadingTime> =
        tallyDao.readingTime(sinceDay)

    /** Drops tally rows the charts can no longer reach. Called from the sync. */
    suspend fun pruneTally(beforeDay: String) = withContext(jcc) {
        tallyDao.prune(beforeDay)
    }


    /**
     * Reading by hour of the day, with all twenty-four buckets present.
     *
     * Filled for the same reason and with more force: an hour nobody reads in
     * is the shape of the chart. Somebody who reads at breakfast and on the
     * commute home has two peaks and a trough, and the trough is only visible
     * if the empty hours are drawn.
     */
    /**
     * How often each source publishes, in hours between articles.
     *
     * The arithmetic is in [sourcePaceHours] rather than the query, so what
     * counts as too few articles to judge can be argued with in a test.
     */
    fun sourcePace(): Flow<Map<Long, Float>> =
        articlesDao.sourcePace().map(::sourcePaceHours)

    fun readsPerSource(since: Long): Flow<Map<Long, Int>> =
        articlesDao.readsPerSource(since)
            .map { rows -> rows.associate { it.feedId to it.reads } }
            .flowOn(cc)

    /** Every article in the database, read or not, enabled source or not. */
    suspend fun countAll(): Int = withContext(cc) { articlesDao.countAll() }

    fun countUnread(): Flow<Int> = articlesDao.countUnread().flowOn(cc)

    fun countReadSince(since: Long): Flow<Int> = articlesDao.countReadSince(since).flowOn(cc)

    /**
     * Saves an article, and only that.
     *
     * This used to set `pinned` to the same value, so saving something put it
     * at the top of the feed and unsaving took it down again. The two are
     * different things — saving is "I want to find this later", pinning is "I
     * am following this, keep it in front of me" — and conflating them meant
     * neither could be used without the other happening.
     */
    suspend fun bookmarkArticle(
        articleId: String,
        bookmark: Boolean,
    ) = withContext(jcc) {
        articlesDao.getArticleById(articleId)?.let {
            articlesDao.updateFeedArticle(it.copy(bookmarked = bookmark))
            if (it.bookmarked != bookmark) onStarredChanged?.invoke(articleId, bookmark)
            if (bookmark && !it.bookmarked) onSaved?.invoke()
            if (!bookmark && it.bookmarked) onUnsaved?.invoke(articleId)
            // A source removed while it still held saved articles is kept
            // only for their sake. Taking the last one back is what ends
            // that, and is what makes "kept until you un-bookmark it" a fact
            // rather than a promise to hold a row for ever.
            if (!bookmark) onSavedRemoved?.invoke(it.feedId)
        }
    }

    /** Holds an article at the top of the feed, or lets it go. */
    suspend fun setPinned(
        articleId: String,
        pinned: Boolean,
    ) = withContext(jcc) {
        articlesDao.getArticleById(articleId)?.let {
            articlesDao.updateFeedArticle(it.copy(pinned = pinned))
        }
    }

    suspend fun getItemsToBeCleanedFromFeed(feedId: Long, minKeptPubDate: Long) = withContext(jcc) {
        articlesDao.getItemsToBeCleanedFromFeed(
            feedId = feedId,
            minKeptPubDate = minKeptPubDate
        )
    }

    /** Every stored article's id; see sweepOrphanArticleFiles. */
    suspend fun allArticleIds(): Set<String> = withContext(cc) {
        articlesDao.loadAllUuids().toHashSet()
    }

    /** How many articles one source has stored. */
    suspend fun countInFeed(feedId: Long): Int = withContext(cc) {
        articlesDao.countInFeed(feedId)
    }

    fun getFeedsItemsWithDefaultFullTextParse(allFeeds: Boolean): Flow<List<ArticleIdWithLink>> =
        articlesDao.getArticleIdLinks(allFeeds)
            .flowOn(cc)

    suspend fun savedArticleIdLinks(): List<ArticleIdWithLink> = withContext(cc) {
        articlesDao.getSavedArticleIdLinks()
    }

    suspend fun feedUrlsOf(ids: List<String>): Map<String, String> = withContext(cc) {
        ids.chunked(SQLITE_ARG_LIMIT).flatMap { articlesDao.feedUrlsOf(it) }.associate { it.uuid to it.url }
    }

    suspend fun savedIds(): List<String> = withContext(cc) {
        articlesDao.getSavedArticleIdLinks().map { it.uuid }
    }

    suspend fun savedArticleRefs(): List<SavedArticleRef> = withContext(cc) {
        articlesDao.getSavedArticleRefs()
    }

    fun getBookmarkedFeedItems(): Flow<List<FeedItem>> =
        whenChanged { articlesDao.loadAllBookmarkedFeedItems() }

    /** Attaches a server's id to the article at that address. */
    suspend fun attachRemoteId(link: String, remoteId: String): Int = withContext(cc) {
        articlesDao.attachRemoteId(link, remoteId)
    }

    /** The server's id for one article, if a server has claimed it. */
    suspend fun remoteIdFor(uuid: String): String? = withContext(cc) {
        articlesDao.remoteIdFor(uuid)
    }

    /** Every article a server has claimed, with its read and saved state. */
    suspend fun mappedArticles(): List<MappedArticle> = withContext(cc) { articlesDao.loadMapped() }

    /**
     * Read state from the server, applied without telling anyone.
     *
     * Not tallied: these were read somewhere else, at a time the server did
     * not say, and the only timestamp available is when this sync ran -
     * putting fifty of them into that hour would show the scheduler's habits
     * on the time-of-day chart rather than the reader's. And not passed to
     * [onReadChanged]: it came from the server, and sending it back would be
     * an echo.
     */
    suspend fun applyServerRead(read: List<String>, unread: List<String>) = withContext(cc) {
        val now = System.currentTimeMillis()
        read.chunked(SQLITE_ARG_LIMIT).forEach { articlesDao.markReadBatch(it, now) }
        unread.chunked(SQLITE_ARG_LIMIT).forEach { articlesDao.unmarkRead(it) }
    }

    /** Stars from the server, as saves. Additions only; see GoogleReaderService. */
    suspend fun applyServerStars(ids: List<String>) = withContext(cc) {
        ids.chunked(SQLITE_ARG_LIMIT).forEach { articlesDao.setBookmarked(it) }
        if (ids.isNotEmpty()) onSaved?.invoke()
    }

    /** How many articles a server has claimed. */
    suspend fun countWithRemoteId(): Int = withContext(cc) { articlesDao.countWithRemoteId() }

    /** When each feed last had an article, for the source list's sort. */
    fun latestArticlePerFeed(): Flow<Map<Long, Long>> =
        articlesDao.latestArticlePerFeed()
            .map { rows -> rows.associate { it.feedId to it.latest } }
            .flowOn(cc)
        .flowOn(cc)
}

/**
 * How many ids to put in one `IN` clause.
 *
 * SQLite's default limit is 999 host parameters; well under it, because the
 * cost of an extra statement is nothing next to a query that throws.
 */
private const val SQLITE_ARG_LIMIT = 500

/**
 * How many articles the feed holds at once.
 *
 * A window, not a page. The distinction matters: the weighting, the
 * breaking-news clustering and the two diversity rules all reason about the
 * *whole* list — clustering has to see every article to find a story across
 * sources, and spreading a run of one publisher needs the ordering entire. A
 * pager that handed those a page at a time would change what they mean rather
 * than making them cheaper.
 *
 * So the list stays whole and is simply bounded. Five hundred is far past
 * anywhere anyone scrolls, and it makes the working set a property of this
 * constant instead of a property of how many feeds somebody happens to have
 * subscribed to.
 */
const val FEED_WINDOW = 500

/**
 * The most time on screen any one article can contribute.
 *
 * Thirty seconds. Long enough to cover reading a headline, a summary and a
 * decent extract; short enough that a phone left face-up on a desk with the
 * feed open cannot turn one article into the most-read thing anybody owns.
 *
 * The cap matters more than the number. Time on screen has no natural ceiling
 * and every other signal here does, so without one a single forgotten session
 * would dominate every comparison it took part in.
 */
/**
 * How long to let a burst of table changes settle before reloading the feed.
 *
 * A sync writes to Feeds twice per source and inserts that source's articles,
 * so a hundred sources is a storm rather than an event. Long enough to let one
 * source's writes land together; short enough that finished sources appear
 * while the sync is still running.
 */
const val FEED_INVALIDATION_DEBOUNCE_MS = 300L

const val DWELL_CAP_MS = 30_000L

/**
 * How long dwell increments are held before being written together.
 *
 * Five seconds. The figure is a trade between two costs that pull opposite
 * ways: every flush re-runs the feed query, so fewer is cheaper; and every
 * unflushed increment is lost if the process dies, so fewer is riskier.
 *
 * Five puts the flush well below the six-to-eight second collection cycle seen
 * in the field, so a scroll's worth of writes lands as a handful of
 * invalidations rather than one per article, while the most that can be lost
 * is five seconds of a signal that accumulates over weeks.
 *
 * It is deliberately not longer. Dwell feeds the weighting that decides what
 * the reader sees next, and a window measured in minutes would leave the feed
 * reasoning from what they were reading some time ago.
 */
const val DWELL_FLUSH_MS = 5_000L

/**
 * The most reading time one article can accumulate.
 *
 * Ten minutes. Longer than almost any article takes to read, and short enough
 * that a phone left unlocked on a desk with one open stops contributing well
 * before it could outweigh a month of genuine reading.
 *
 * The ceiling is the second line of defence rather than the first. The clock
 * only advances while the article is in front of somebody, so a backgrounded
 * app or a locked phone contributes nothing at all and never reaches this.
 * What it catches is the case the clock cannot argue with: awake, on screen,
 * and nobody there.
 */
const val READ_CAP_MS = 600_000L

/**
 * The window's days in order, with the ones the query had nothing for.
 *
 * Separated from the flow so it can be tested without a database: the part
 * that can be wrong here is the date arithmetic and the key format, neither of
 * which needs Room to demonstrate. Dates are formatted exactly as SQLite's
 * `date(..., 'localtime')` writes them, and both run in the device's zone, so
 * the two sides meet without either having to parse the other.
 */
internal fun fillDays(
    rows: List<DayCount>,
    days: Int,
    today: LocalDate,
): List<DayCount> {
    val byDay = rows.associateBy { it.day }
    return (days - 1 downTo 0).map { back ->
        val key = today.minusDays(back.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        byDay[key] ?: DayCount(day = key, seen = 0, opened = 0)
    }
}

/** All twenty-four buckets, in order, whether or not anything was read in them. */
internal fun fillHours(rows: List<HourCount>): List<HourCount> {
    // Keyed on the integer rather than the text: SQLite gives "07", and a
    // chart indexing on 7 would silently miss every morning.
    val byHour = rows.associateBy { it.hour.toIntOrNull() ?: -1 }
    return (0..23).map { hour ->
        byHour[hour] ?: HourCount(hour = "%02d".format(hour), seen = 0, opened = 0)
    }
}

/**
 * How much of a requested reading increment the article's total will take.
 *
 * The article's own `readMs` is capped, so past the cap every further tick
 * adds nothing. The tally has to add what was applied rather than what was
 * asked for, or a single article left open would go on contributing to the
 * chart's reading total long after it stopped contributing to its own.
 *
 * Pulled out of the repository because it is the one piece of arithmetic here
 * that can be wrong in a way nothing would notice: an off-by-one at the cap
 * shows up as a reading total that drifts slowly upward over months.
 */
internal fun appliedReading(before: Long, requested: Long, cap: Long): Long {
    if (requested <= 0L) return 0L
    return (minOf(before + requested, cap) - before).coerceAtLeast(0L)
}
