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

package com.saulhdev.feeder.data.repository

import kotlinx.coroutines.flow.combine
import com.saulhdev.feeder.utils.CategoryOrder
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.utils.SyncLog
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.saulhdev.feeder.data.db.ID_ALL
import com.saulhdev.feeder.data.db.ID_UNSET
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.utils.normalizeFeedUrl
import com.saulhdev.feeder.manager.models.scheduleFullTextParse
import com.saulhdev.feeder.manager.sync.FeedSyncer
import com.saulhdev.feeder.manager.sync.requestFeedSync
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import android.database.sqlite.SQLiteConstraintException
import com.saulhdev.feeder.utils.isSameFeedUrl
import com.saulhdev.feeder.utils.normalizeFeedUrl
import com.saulhdev.feeder.utils.preferringHttps
import com.saulhdev.feeder.utils.joinPairs
import com.saulhdev.feeder.utils.sameArticleGroups
import org.koin.java.KoinJavaComponent.inject
import java.net.URL

class SourcesRepository(db: NeoFeedDb) {
    private val cc = Dispatchers.IO
    private val jcc = Dispatchers.IO + SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("FeedSourceRepository")
    private val feedsDao = db.feedSourceDao()
    private val articlesDao = db.feedArticleDao()
    private val workManager: WorkManager by inject(WorkManager::class.java)
    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)

    /**
     * Adds a source, resurrecting a removed one at the same address.
     *
     * Without this, re-adding a site somebody had removed would insert a
     * second row while the first sat invisible holding their bookmarks — two
     * entries for one feed, and the saved articles attached to the one they
     * cannot see. Reusing the row gives them back instead, which is the
     * friendlier answer and the only consistent one.
     */
    suspend fun insertSource(feed: Feed) = withContext(jcc) {
        // Recorded over https, whatever scheme it arrived with. Every client
        // that fetches a feed upgrades the scheme before opening a socket, so
        // an address stored as http was never the address used — it was a
        // working subscription described wrongly, and then listed on the
        // "Feeds still on http" screen as though its publisher were at fault.
        //
        // Here rather than at the seven places that add a source, and here
        // rather than in the type converter: the converter runs after the
        // duplicate checks below, so an address that changed there could
        // collide with a row those checks had already cleared — and with a
        // unique index and REPLACE, that collision deletes the other feed and
        // its articles. Converting first means every check sees the address
        // that will actually be written.
        val source = feed.copy(url = feed.url.preferringHttps())

        val removed = feedsDao.findRemovedByUrl(source.url)
        if (removed != null) {
            feedsDao.restoreRemoved(removed.id)
            requestFeedSync(removed.id, origin = SyncLog.ORIGIN_SOURCE_CHANGE)
            return@withContext removed.id
        }

        // Five of the six callers check this for themselves before calling,
        // with the same comment about REPLACE each time; the sixth does not,
        // and is the one that would quietly take a feed's articles with it.
        // A check that has to be remembered at each call site is one that
        // will be missed at the next, so it lives here now and the callers'
        // own checks are merely redundant.
        val existing = feedsDao.loadAllFeeds().firstOrNull { isSameFeedUrl(it.url, source.url) }
        if (existing != null) return@withContext existing.id

        feedsDao.insert(source)
    }

    /**
     * Adds many sources at once, scanning what is already there once.
     *
     * [insertSource] asks the database what it already holds on every call,
     * and so does the [findSourceByUrl] its callers run first — matching on
     * the normalised address, which ignores `www.`, a trailing slash and the
     * scheme, and therefore cannot be an indexed query. For one feed that is
     * two scans nobody notices. For a bundled pack, a starter list or an
     * imported bookmark file it is two scans per feed: a hundred feeds against
     * a hundred and nineteen subscriptions is some twenty thousand rows
     * materialised to add a hundred.
     *
     * Here the scan happens once and the comparison set is built from it, so
     * the work is proportional to what is being added rather than to the
     * product. Duplicates within the batch itself are caught by the same set,
     * which the per-feed version could not do at all: two spellings of one
     * address in a single OPML both passed their checks, and the second
     * replaced the first.
     *
     * Returns how many were actually new, which is what the screens report.
     */
    suspend fun insertSources(feeds: List<Feed>): Int = withContext(jcc) {
        if (feeds.isEmpty()) return@withContext 0

        // Recorded over https, for the reason insertSource gives, and before
        // anything is compared so the comparison sees what will be written.
        val incoming = feeds.map { it.copy(url = it.url.preferringHttps()) }

        val taken = feedsDao.loadAllFeeds()
            .map { normalizeFeedUrl(it.url) }
            .toMutableSet()

        var added = 0
        incoming.forEach { feed ->
            // add() answers false for an address already present, whether it
            // came from the database or from earlier in this same batch.
            if (!taken.add(normalizeFeedUrl(feed.url))) return@forEach
            feedsDao.insert(feed)
            added++
        }
        added
    }

    /**
     * The existing subscription this address would collide with, if any.
     *
     * Matched on [normalizeFeedUrl] rather than on the string, so the same feed
     * typed with or without `www.`, with or without a trailing slash, or over
     * the other scheme is recognised. `Feeds.url` is uniquely indexed and the
     * insert strategy is REPLACE, so an unnoticed duplicate does not sit beside
     * the original — it replaces the row and takes that feed's articles down
     * with it.
     */
    suspend fun findSourceByUrl(url: URL): Feed? = withContext(jcc) {
        feedsDao.getFeedByURL(url)
            ?: feedsDao.loadAllFeeds().firstOrNull { isSameFeedUrl(it.url, url) }
    }

    /**
     * A *different* subscription that already holds this address.
     *
     * [findSourceByUrl] matches on the normalised address, which ignores the
     * scheme — so asking it whether `https://x/feed` is taken while holding
     * `http://x/feed` gets back the very feed doing the asking. Anything
     * deciding whether it may move a feed to a new address has to exclude
     * that feed, or it concludes every move is a collision.
     */
    suspend fun findOtherSourceByUrl(url: URL, excludingId: Long): Feed? = withContext(jcc) {
        feedsDao.loadAllFeeds().firstOrNull { it.id != excludingId && isSameFeedUrl(it.url, url) }
    }

    /**
     * Writes a changed subscription back, reporting a clash rather than dying.
     *
     * `Feeds.url` is uniquely indexed and Room's `@Update` defaults to ABORT,
     * so moving a feed onto an address another feed already holds threw
     * SQLiteConstraintException straight out of a tap handler and took the
     * process with it. That is reachable from ordinary use: "Feeds that
     * stopped working" offers the address a site advertises, and a site's
     * current feed is quite often one the reader is already subscribed to
     * under a different entry.
     *
     * @return false if another subscription already holds that address, in
     *   which case nothing was written. Callers that cannot meaningfully
     *   react may ignore it; what none of them may do is crash.
     */
    suspend fun updateSource(feed: Feed, resync: Boolean = false): Boolean = withContext(jcc) {
        // See insertSource: converted before the clash check, never after.
        @Suppress("NAME_SHADOWING")
        val feed = feed.copy(url = feed.url.preferringHttps())
        if (!feedsDao.existsById(feed.id)) return@withContext false
        // Asked first, so the ordinary case reports rather than relying on an
        // exception to notice. The catch is the backstop for the race between
        // this check and the write.
        if (findOtherSourceByUrl(feed.url, feed.id) != null) return@withContext false
        val written = runCatching { feedsDao.update(feed) }.getOrElse {
            if (it is SQLiteConstraintException) return@withContext false else throw it
        }
        if (written > 0) {
            if (resync) requestFeedSync(feed.id, origin = SyncLog.ORIGIN_SOURCE_CHANGE)
            if (feed.fullTextByDefault) scheduleFullTextParse()
        }
        true
    }

    fun getAllSourcesFlow(): Flow<List<Feed>> = feedsDao.getAllFeeds()

    /** One more failure for a feed, so a run of them can be noticed. */
    suspend fun recordFailure(feedId: Long) = withContext(jcc) {
        feedsDao.recordFailure(feedId, System.currentTimeMillis())
    }

    /** A success wipes the record. */
    suspend fun clearFailures(feedId: Long) = withContext(jcc) {
        feedsDao.clearFailures(feedId)
    }

    /** Feeds that have failed often enough to be worth mentioning. */
    fun getFailingFeeds(threshold: Int = FAILURES_BEFORE_BROKEN): Flow<List<Feed>> =
        feedsDao.getFailingFeeds(threshold)
        .flowOn(cc)

    suspend fun getAllSources(): List<Feed> = feedsDao.loadFeeds()

    /**
     * Every subscription, switched on or off - what a sync server should
     * hear about. [getAllSources] is the fetching question, enabled only, and
     * asked of it a server's feed that had merely been switched off here was
     * added a second time.
     */
    suspend fun getAllSubscriptions(): List<Feed> = withContext(jcc) { feedsDao.loadAllFeeds() }

    fun getEnabledSources(): Flow<List<Feed>> = feedsDao.getEnabledFeeds()
        .flowOn(cc)

    fun getSourceById(id: Long): Flow<Feed?> = feedsDao.getFeedById(id)
        .flowOn(cc)

    suspend fun loadFeedsByTag(tag: String): List<Feed> = withContext(jcc) {
        feedsDao.loadFeedsByTag(tag)
    }

    suspend fun loadFeedById(feedId: Long): Feed? = withContext(jcc) {
        feedsDao.loadFeedById(feedId)
    }

    suspend fun loadFeedIfStale(feedId: Long, staleTime: Long): List<Feed> = withContext(jcc) {
        if (feedId == ID_ALL)
            feedsDao.loadFeedIfStale(staleTime)
        else
            feedsDao.loadFeedIfStale(feedId, staleTime)?.let { listOf(it) } ?: emptyList()
    }

    suspend fun getAllTags(): List<String> {
        return feedsDao.getAllTags()
    }

    /**
     * Every category in use, in the reader's order: see CategoryOrder. The
     * one list every chip row reads, so the sources screen and both feeds
     * show the same order.
     */
    fun getAllTagsFlow(): Flow<List<String>> = feedsDao.getAllTagsFlow()
        .map { rawTags ->
            rawTags.flatMap { tagString ->
                tagString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }.distinct()
        }
        .combine(prefs.categoryOrder.get()) { tags, stored ->
            CategoryOrder.arrange(tags, CategoryOrder.decode(stored))
        }
        .flowOn(cc)

    /** Keeps [order] as the reader's order for categories. */
    fun setCategoryOrder(order: List<String>) {
        prefs.categoryOrder.set(CategoryOrder.encode(order))
    }

    suspend fun loadFeedIds(): List<Long> = withContext(jcc) {
        feedsDao.loadFeedIds()
    }

    @OptIn(FlowPreview::class)
    val isSyncing: StateFlow<Boolean> =
        workManager.getWorkInfosByTagFlow(FeedSyncer::class.qualifiedName!!)
            .map {
                workManager.pruneWork()
                it.any { work ->
                    work.state == WorkInfo.State.RUNNING || work.state == WorkInfo.State.BLOCKED
                }
            }
            .debounce(1000L)
            .stateIn(
                scope,
                SharingStarted.Lazily,
                false
            )

    /**
     * When the feed was last brought up to date: the newest successful fetch
     * among enabled sources. See [com.saulhdev.feeder.utils.syncFreshness].
     *
     * Feeds is written twice per source during a sync, so this re-queries
     * often; the value only moves when a fetch lands, and nothing downstream
     * hears the rest.
     */
    val newestSync: Flow<Long?> = feedsDao.getNewestSync().distinctUntilChanged()

    fun setCurrentlySyncingOn(feedId: Long, syncing: Boolean) {
        scope.launch {
            feedsDao.setCurrentlySyncingOn(feedId, syncing)
        }
    }

    fun setCurrentlySyncingOn(feedId: Long, syncing: Boolean, lastSync: kotlin.time.Instant) {
        scope.launch {
            feedsDao.setCurrentlySyncingOn(feedId, syncing, lastSync)
        }
    }

    /**
     * The source removed most recently, for as long as it can still be undone.
     *
     * Removal is confirmed by a dialog and then the screen closes, so by the
     * time a user realises they picked the wrong feed there is nothing left on
     * screen to undo it from. Holding the row here lets the list they land back
     * on offer it. Cleared once [undoDeleteSource] runs or [forgetDeletedSource]
     * is called by the snackbar going away.
     */
    private val _recentlyDeleted = MutableStateFlow<Feed?>(null)
    val recentlyDeleted: StateFlow<Feed?> = _recentlyDeleted.asStateFlow()

    /**
     * Removes a source, keeping anything the reader saved from it.
     *
     * `Article.feedId` carries `onDelete = CASCADE`, so deleting the row is
     * what deleted the articles — bookmarked ones included. A bookmark is
     * meant to last until it is taken back, and unsubscribing from a site is
     * not taking it back: the articles were saved because somebody wanted to
     * keep them, not because they wanted to keep the subscription.
     *
     * So a source holding saved articles is marked removed instead of
     * deleted. It vanishes from every listing, stops syncing, and its
     * unsaved articles go — but the row stays, because the bookmarks need
     * something to point at. One holding nothing saved is deleted outright,
     * as before, so this leaves no debris in the ordinary case.
     */
    fun deleteFeed(feedId: Long) {
        scope.launch {
            _recentlyDeleted.value = feedsDao.loadFeedById(feedId)
            if (articlesDao.countSavedInFeed(feedId) > 0) {
                feedsDao.markRemoved(feedId, System.currentTimeMillis())
                articlesDao.clearArticlesForFeeds(listOf(feedId))
            } else {
                feedsDao.deleteFeedById(feedId)
            }
        }
    }

    /**
     * Drops a removed source once nothing saved is left in it.
     *
     * What makes "kept until you un-bookmark it" literally true rather than a
     * promise to keep a row for ever. Called after un-bookmarking; does
     * nothing to a source the reader still has.
     */
    suspend fun reapIfEmpty(feedId: Long) = withContext(jcc) {
        val feed = feedsDao.loadFeedById(feedId) ?: return@withContext
        if (feed.removedAt == 0L) return@withContext
        if (articlesDao.countSavedInFeed(feedId) == 0) feedsDao.deleteFeedById(feedId)
    }

    /**
     * Puts a removed source back, and resyncs it.
     *
     * Two cases, because removal has two. A source that was marked rather
     * than deleted still has its row, and its saved articles with it, so it
     * is unmarked in place and everything bookmarked from it is there again.
     * One that was deleted outright is reinserted under a fresh id — the old
     * one may have been handed out again — and refetched, so what comes back
     * is the feed's current contents. Nothing was saved from it, which is
     * why it could be deleted at all.
     */
    fun undoDeleteSource() {
        scope.launch {
            val feed = _recentlyDeleted.value ?: return@launch
            _recentlyDeleted.value = null
            if (feedsDao.existsById(feed.id)) {
                feedsDao.restoreRemoved(feed.id)
                requestFeedSync(feed.id, origin = SyncLog.ORIGIN_SOURCE_CHANGE)
            } else {
                val id = feedsDao.insert(feed.copy(id = ID_UNSET))
                requestFeedSync(id, origin = SyncLog.ORIGIN_SOURCE_CHANGE)
            }
        }
    }

    fun forgetDeletedSource() {
        _recentlyDeleted.value = null
    }

    /* Bulk editing */

    /**
     * Applies one change to many sources at once.
     *
     * A single `updateAll` rather than a loop of `update`, because each write
     * invalidates every query that touches Feeds — and the article list is one
     * of them. Retagging forty sources one row at a time would rebuild the feed
     * forty times.
     */
    suspend fun updateSources(feeds: List<Feed>) = withContext(jcc) {
        if (feeds.isNotEmpty()) feedsDao.updateAll(feeds)
    }

    suspend fun setEnabled(ids: Collection<Long>, enabled: Boolean) = withContext(jcc) {
        val feeds = feedsDao.loadAllFeeds()
            .filter { it.id in ids && it.isEnabled != enabled }
            .map { it.copy(isEnabled = enabled) }
        if (feeds.isNotEmpty()) feedsDao.updateAll(feeds)
    }

    /**
     * Turns full-article fetching on or off across a selection.
     *
     * Per-feed and only reachable from each source's editor until now, which
     * made it the one setting people wanted in bulk and could not have: a list
     * imported from a browser is mostly summary-only newspapers, and twenty
     * visits to twenty editors is not a feature.
     */
    suspend fun setFullTextByDefault(ids: Collection<Long>, enabled: Boolean) = withContext(jcc) {
        val feeds = feedsDao.loadAllFeeds()
            .filter { it.id in ids && it.fullTextByDefault != enabled }
            .map { it.copy(fullTextByDefault = enabled) }
        if (feeds.isNotEmpty()) feedsDao.updateAll(feeds)
    }

    /**
     * Adds, removes or replaces categories across a selection.
     *
     * The whole reason this is here rather than in the view model: `tag` is a
     * comma-separated string, and every place that has treated it as a single
     * value has been a bug. Splitting and rejoining happens once, in one place.
     */
    suspend fun editTags(
        ids: Collection<Long>,
        add: Set<String> = emptySet(),
        remove: Set<String> = emptySet(),
        replaceWith: Set<String>? = null,
    ) = withContext(jcc) {
        val updated = feedsDao.loadAllFeeds()
            .filter { it.id in ids }
            .mapNotNull { feed ->
                val tags = when {
                    replaceWith != null -> replaceWith
                    else -> feed.tags.toSet() + add - remove
                }
                val joined = tags.filter(String::isNotBlank).joinToString(",")
                // Only the rows that actually change: every write to Feeds
                // invalidates the article query, which joins it.
                if (joined == feed.tag) null else feed.copy(tag = joined)
            }
        if (updated.isNotEmpty()) feedsDao.updateAll(updated)
    }

    /**
     * Renames one category everywhere it is used.
     *
     * Merging is the same operation with a target that already exists — the
     * distinct() is what turns a rename-onto-an-existing-name into a merge
     * rather than into a feed tagged "News,News".
     */
    suspend fun renameTag(from: String, to: String) = withContext(jcc) {
        if (from.isBlank() || to.isBlank() || from == to) return@withContext
        val updated = feedsDao.loadAllFeeds()
            .filter { from in it.tags }
            .map { feed ->
                val tags = feed.tags.map { if (it == from) to else it }.distinct()
                feed.copy(tag = tags.joinToString(","))
            }
        if (updated.isNotEmpty()) feedsDao.updateAll(updated)
        // The renamed category keeps its place rather than dropping to the
        // end as a newcomer.
        val order = CategoryOrder.decode(prefs.categoryOrder.getValue())
        if (from in order) prefs.categoryOrder.setValue(CategoryOrder.encode(CategoryOrder.renamed(order, from, to)))
    }

    /** Removes a category from every source, leaving the sources themselves. */
    suspend fun deleteTag(tag: String) = withContext(jcc) {
        val updated = feedsDao.loadAllFeeds()
            .filter { tag in it.tags }
            .map { feed -> feed.copy(tag = (feed.tags - tag).joinToString(",")) }
        if (updated.isNotEmpty()) feedsDao.updateAll(updated)
    }

    /**
     * Removes several sources, keeping them all for one undo.
     *
     * The single-source path holds one row; this holds the whole selection, so
     * undoing a mistaken "delete 12 sources" brings back twelve rather than the
     * last one.
     */
    private val _recentlyDeletedMany = MutableStateFlow<List<Feed>>(emptyList())
    val recentlyDeletedMany: StateFlow<List<Feed>> = _recentlyDeletedMany.asStateFlow()

    suspend fun deleteSources(ids: Collection<Long>) = withContext(jcc) {
        val feeds = feedsDao.loadAllFeeds().filter { it.id in ids }
        if (feeds.isEmpty()) return@withContext
        _recentlyDeletedMany.value = feeds
        feedsDao.deleteFeedsByIds(feeds.map(Feed::id))
    }

    fun undoDeleteSources() {
        scope.launch {
            val feeds = _recentlyDeletedMany.value
            _recentlyDeletedMany.value = emptyList()
            feeds.forEach { feedsDao.insert(it.copy(id = ID_UNSET)) }
            if (feeds.isNotEmpty()) requestFeedSync(ID_ALL, origin = SyncLog.ORIGIN_SOURCE_CHANGE)
        }
    }

    fun forgetDeletedSources() {
        _recentlyDeletedMany.value = emptyList()
    }

    /**
     * Throws away the articles of several sources, keeping the sources.
     *
     * Returns how many rows went, so the screen can say so rather than leave
     * the user guessing whether anything happened — clearing a feed that was
     * already empty looks identical to a button that does nothing.
     *
     * There is no undo, and deliberately no confirmation either: what this
     * deletes is a cache the next sync rebuilds, minus the bookmarked and
     * pinned articles the query refuses to touch.
     */
    suspend fun clearArticles(ids: Collection<Long>): Int = withContext(jcc) {
        if (ids.isEmpty()) return@withContext 0
        articlesDao.clearArticlesForFeeds(ids.toList())
    }

    /**
     * Sources that are the same feed as some other source.
     *
     * Two imports — an OPML and a browser's bookmarks, say — will happily add
     * the same feed twice under two different titles, and nothing in the list
     * shows it: they sort apart, they look alike, and they deliver the same
     * article twice. Matching is on the normalised address rather than the
     * stored one, so `http://x/feed`, `https://x/feed` and `https://x/feed/`
     * count as one feed, which is the whole reason a duplicate survives being
     * added in the first place.
     *
     * Groups of one are dropped: a source with no twin is not a duplicate.
     */
    suspend fun duplicateSources(): List<Feed> = duplicateGroups().flatten()

    /**
     * The same, kept as groups.
     *
     * The grouping was computed and then flattened away, so the screen could
     * say how many sets there were and not which feed belonged to which. With
     * a hundred sources sorted by name, the two halves of a pair can sit a
     * screen apart, and "these are duplicates" is not a useful thing to be
     * told about a list you then have to pair up yourself.
     */
    suspend fun duplicateGroups(): List<List<Feed>> = withContext(jcc) {
        val feeds = feedsDao.loadAllFeeds()
        val byId = feeds.associateBy(Feed::id)
        // Two kinds of evidence, both conclusive. The same address, written
        // differently; and the same articles, under addresses no rule could
        // match — a subscription list had four feeds subscribed twice and
        // the address check found none of them. See sameArticleGroups.
        val sameAddress = feeds.groupBy { normalizeFeedUrl(it.url) }.values
            .filter { it.size > 1 }
            .flatMap { group -> group.zipWithNext { a, b -> a.id to b.id } }
        val sameArticles = sameArticleGroups(articlesDao.loadFeedLinks().map { it.feedId to it.link })
            .flatMap { group -> group.sorted().zipWithNext() }
        joinPairs(sameAddress + sameArticles)
            .map { group -> group.mapNotNull(byId::get).sortedBy { it.title.lowercase() } }
            .filter { it.size > 1 }
            .sortedBy { it.first().title.lowercase() }
    }
}

/**
 * How many consecutive failures make a feed worth mentioning.
 *
 * Three, because servers have bad days: a single timeout is noise, and telling
 * somebody their feed is broken on the strength of one is how a warning
 * becomes something people learn to ignore. Three failed syncs in a row is a
 * feed that has stopped rather than stumbled.
 */
const val FAILURES_BEFORE_BROKEN = 3
