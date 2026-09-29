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

package com.saulhdev.feeder.manager.sync

import com.saulhdev.feeder.utils.slowestOf
import com.saulhdev.feeder.utils.usableImageUrl
import com.saulhdev.feeder.utils.SyncResult
import android.content.Context
import android.util.Log
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.ID_ALL
import com.saulhdev.feeder.data.db.ID_UNSET
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.entity.JsonFeed
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.viewmodels.STATS_WINDOW_DAYS
import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.manager.models.getResponse
import com.saulhdev.feeder.manager.models.scheduleFullTextParse
import com.saulhdev.feeder.utils.HttpIdentity.asFeedReader
import com.saulhdev.feeder.utils.deleteArticleFiles
import com.saulhdev.feeder.utils.ByteCounter
import com.saulhdev.feeder.utils.dueByPace
import com.saulhdev.feeder.utils.FeedFetch
import com.saulhdev.feeder.utils.FeedHistory
import com.saulhdev.feeder.utils.FetchKind
import com.saulhdev.feeder.utils.countsAgainstSource
import com.saulhdev.feeder.utils.errorKind
import com.saulhdev.feeder.utils.FEED_MAX_BYTES
import com.saulhdev.feeder.utils.bytesAtMost
import com.saulhdev.feeder.utils.refuseMedia
import com.saulhdev.feeder.utils.whisperHasNetwork
import com.saulhdev.feeder.utils.blobOutputStream
import com.saulhdev.feeder.utils.getSyncDays
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.coroutines.flow.first
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import com.saulhdev.feeder.utils.FeedDigest
import com.saulhdev.feeder.utils.IconLookup
import com.saulhdev.feeder.utils.feedDigest
import com.saulhdev.feeder.utils.parseSettingsKey
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlin.time.Clock
import kotlin.time.Instant

val syncMutex = Mutex()
val prefs: FeedPreferences by inject(FeedPreferences::class.java)
val singleThreadedSync = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
const val TAG = "RssLocalSync"

/**
 * One client for every feed. A fresh OkHttpClient was being built per feed, so a
 * sync of an imported OPML created dozens of connection pools and thread pools
 * that could share nothing — no connection reuse, no keep-alive across feeds.
 */
/**
 * How many feeds are fetched and parsed at the same time.
 *
 * Not a throughput knob: it is what keeps the heap survivable. Each feed being
 * parsed holds its whole payload, its Article objects and their content bodies
 * live at once, so the peak is this number multiplied by the largest feed
 * rather than by the whole subscription list.
 */
private const val MAX_CONCURRENT_FEEDS = 4

/**
 * The feed client, with a disk cache.
 *
 * Every request already asked for revalidation - see getResponse - but the
 * client had no cache, so there was nothing to revalidate and every sync
 * downloaded every feed in full: ten megabytes an hour for a hundred and
 * twenty feeds that had mostly not changed. With a cache, OkHttp sends the
 * feed's ETag or Last-Modified back and a server with nothing new answers
 * 304 with no body. Feeds whose servers send neither still download in full;
 * nothing on this side can help those.
 *
 * Made on first use rather than at load, because the cache lives in the
 * app's cache directory and that needs a Context.
 */
private const val FEED_CACHE_BYTES = 20L * 1024 * 1024

@Volatile
private var feedClient: OkHttpClient? = null
private val feedClientLock = Any()

private fun syncHttpClient(context: Context): OkHttpClient =
    feedClient ?: synchronized(feedClientLock) {
        feedClient ?: OkHttpClient.Builder()
            .asFeedReader()
            .onlyPublicHttps()
            .cache(Cache(File(context.cacheDir, "feeds"), FEED_CACHE_BYTES))
            .eventListenerFactory(ByteCounter.factory)
            .build()
            .also { feedClient = it }
    }

/**
 * Whether a response is the copy already processed: a 304 from the server,
 * or the cache answering without asking (within getResponse's one-minute
 * window). Both mean the bytes are the ones the last sync parsed.
 */
internal fun servedUnchanged(networkCode: Int?, hadCachedCopy: Boolean): Boolean =
    hadCachedCopy && (networkCode == null || networkCode == 304)

suspend fun syncFeeds(
    context: Context,
    feedId: Long = ID_UNSET,
    feedTag: String = "",
    forceNetwork: Boolean = false,
    minFeedAgeMinutes: Int = 5
): SyncResult {
    // When this was asked for, before any wait for the lock. A forced sync
    // that had to queue behind another only needs what that one did not
    // fetch after this moment; see freshSince below.
    val requestedAt = Clock.System.now().toEpochMilliseconds()
    return syncMutex.withLock {
        val waited = Clock.System.now().toEpochMilliseconds() - requestedAt > QUEUED_AFTER_MS
        withContext(singleThreadedSync) {
            syncFeeds(
                context = context,
                feedId = feedId,
                feedTag = feedTag,
                maxFeedItemCount = prefs.itemsPerFeed.getValue().toInt(),
                forceNetwork = forceNetwork,
                minFeedAgeMinutes = minFeedAgeMinutes,
                freshSince = if (forceNetwork && waited) requestedAt else null,
            )
        }
    }
}

/** Longer than this for the lock means another sync was running. */
private const val QUEUED_AFTER_MS = 1_000L

internal suspend fun syncFeeds(
    context: Context,
    feedId: Long = ID_UNSET,
    feedTag: String = "",
    maxFeedItemCount: Int = 100,
    forceNetwork: Boolean = false,
    minFeedAgeMinutes: Int = 5,
    /**
     * For a forced sync that queued behind another: a feed fetched after this
     * moment already counts as fetched for it.
     *
     * A pull to refresh means "everything, now", and it forces a fetch of
     * every feed however recent. Behind a sync that was already running, that
     * meant downloading everything twice in a row - the reader asked at
     * 16:15, the running sync fetched every feed by 16:18, and the pull then
     * fetched them all again. Anything fetched after the pull was asked for
     * is exactly as fresh as the pull wanted.
     */
    freshSince: Long? = null,
): SyncResult {
    var result = SyncResult(due = 0)
    // Feeds that threw, counted for the history: a sync that reached 118 of
    // 120 feeds and one that reached none were both just "ok".
    val failedFeeds = AtomicInteger(0)
    // And the ones the server said had not changed, which cost a few hundred
    // bytes instead of the whole feed. See syncHttpClient.
    val unchangedFeeds = AtomicInteger(0)
    val identicalFeeds = AtomicInteger(0)
    // And those Whisper had no network to reach; see countsAgainstSource.
    val offlineFeeds = AtomicInteger(0)
    // And those left for a later sync because they rarely publish.
    var restingFeeds = 0
    // How long each feed took, title to milliseconds, for the history's
    // slowest few: a sync that takes minutes names what it waited for.
    val feedTimes = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, Long>>()
    val feedsRepo: SourcesRepository by inject(SourcesRepository::class.java)
    val articlesRepo: ArticleRepository by inject(ArticleRepository::class.java)
    val downloadTime = Clock.System.now()
    var needFullTextSync = false
    // Read once: the answer cannot change halfway through a sync, and reading
    // it per feed would hit the DataStore once for every source.
    val fullTextForAll = prefs.fullTextForAllFeeds.getValue()

    // The reading tally, trimmed to what the statistics screen can still show.
    // A generous margin past the window, because the charts are the only
    // reader and a few extra rows cost nothing next to the alternative of
    // discarding a day somebody could still be looking at.
    articlesRepo.pruneTally(
        java.time.LocalDate.now()
            .minusDays(STATS_WINDOW_DAYS.toLong() + 7)
            .toString()
    )
    val time = measureTimeMillis {
        try {
            supervisorScope {
                val sRepository: SourcesRepository by inject(SourcesRepository::class.java)
                val staleTime: Long = when {
                    freshSince != null -> freshSince
                    forceNetwork -> Clock.System.now().toEpochMilliseconds()
                    else -> Clock.System.now().minus(minFeedAgeMinutes.toLong(), DateTimeUnit.MINUTE)
                        .toEpochMilliseconds()
                }

                val coroutineContext =
                    Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                        Log.e(TAG, "Error during sync", throwable)
                    }

                val candidates = feedsToSync(
                    repository = sRepository,
                    feedId = feedId,
                    tag = feedTag,
                    staleTime = staleTime,
                    // Selection only: a queued forced sync picks by staleness
                    // like any other. The fetch itself is still forced.
                    forceNetwork = forceNetwork && freshSince == null
                )

                // Slow feeds rest between checks; see recheckAfterMs. Only
                // for the whole-list syncs nobody asked for at that moment:
                // a pull means everything, and a sync of one source is a
                // source that was just added or changed.
                val paced = !forceNetwork && feedId <= 0 && feedTag.isEmpty()
                val pace: Map<Long, Float> = if (paced) articlesRepo.sourcePace().first() else emptyMap()
                val nowMs = Clock.System.now().toEpochMilliseconds()
                val feedsToFetch = if (paced) {
                    candidates.filter { dueByPace(nowMs, it.lastSync.toEpochMilliseconds(), pace[it.id]) }
                } else {
                    candidates
                }
                restingFeeds = candidates.size - feedsToFetch.size

                Log.d(TAG, "Feeds to sync: ${feedsToFetch.size}, resting: $restingFeeds")

                // Every feed at once was the arrangement, and it does not
                // survive a real subscription list. Forty-five feeds launched
                // together each parse their payload and build their articles
                // at the same moment, so every one of those strings is live
                // simultaneously: a device log showed the heap pinned at
                // 244MB of 256MB with five hundred blocking collections, the
                // main thread stalled for over a second at a time, and a feed
                // that could not draw because nothing could get a frame in.
                val gate = Semaphore(MAX_CONCURRENT_FEEDS)
                val jobs = feedsToFetch.map { feed ->
                    needFullTextSync = needFullTextSync ||
                            feed.fullTextByDefault || fullTextForAll
                    launch(coroutineContext) {
                        gate.withPermit {
                            val fetchStarted = System.currentTimeMillis()
                            try {
                                // Mark as syncing START
                                feedsRepo.setCurrentlySyncingOn(feedId = feed.id, syncing = true)

                                val counter = ByteCounter()
                                val fetched = syncFeed(
                                    context = context,
                                    feedsRepo = feedsRepo,
                                    articleRepo = articlesRepo,
                                    feedSql = feed,
                                    filesDir = context.filesDir,
                                    maxFeedItemCount = maxFeedItemCount,
                                    forceNetwork = forceNetwork,
                                    downloadTime = downloadTime,
                                    counter = counter,
                                )
                                val at = Clock.System.now().toEpochMilliseconds()
                                FeedHistory.record(
                                    context,
                                    feed.id,
                                    when (fetched) {
                                        FeedFetchResult.Unchanged -> {
                                            unchangedFeeds.incrementAndGet()
                                            FeedFetch(at, FetchKind.Unchanged, bytes = counter.bytes)
                                        }
                                        FeedFetchResult.Identical -> {
                                            identicalFeeds.incrementAndGet()
                                            FeedFetch(at, FetchKind.Same, bytes = counter.bytes)
                                        }
                                        is FeedFetchResult.Parsed ->
                                            FeedFetch(at, FetchKind.New, "${fetched.newArticles}", counter.bytes)
                                    },
                                )

                                // Successful sync, update lastSync
                                feedsRepo.setCurrentlySyncingOn(
                                    feedId = feed.id,
                                    syncing = false,
                                    lastSync = Clock.System.now(),
                                )
                                // And forget any run of failures: whatever was
                                // wrong is not wrong now.
                                feedsRepo.clearFailures(feed.id)
                                feedTimes.add(feed.title to System.currentTimeMillis() - fetchStarted)
                            } catch (e: CancellationException) {
                                // Not a failure, and this catch used to treat
                                // it as one. `Throwable` includes
                                // CancellationException, so closing the
                                // launcher panel mid-sync recorded a failure
                                // against every feed still in flight — three
                                // of those and a publisher appears under
                                // "Feeds that stopped working" for the crime
                                // of the reader having swiped away. A device
                                // log showed five sources newly failing with
                                // "Job was cancelled" as the only reason.
                                //
                                // The clock is not stamped either: nothing was
                                // fetched, so the feed is exactly as stale as
                                // it was, and saying otherwise would hide a
                                // sync that never happened.
                                //
                                // NonCancellable because this coroutine is
                                // already cancelled and a suspending write
                                // would be cancelled with it, leaving the feed
                                // marked as syncing for ever — a spinner that
                                // never stops.
                                withContext(NonCancellable) {
                                    feedsRepo.setCurrentlySyncingOn(
                                        feedId = feed.id,
                                        syncing = false,
                                    )
                                }
                                // Rethrown, because swallowing it breaks the
                                // structured concurrency that cancelled us.
                                throw e
                            } catch (e: Throwable) {
                                // The title, never the address: some feed addresses carry a
                                // private token, and this line ends up in a shared report.
                                Log.e(TAG, "Failed to sync ${feed.title}", e)
                                // Error, clear syncing flag but don't update lastSync
                                feedsRepo.setCurrentlySyncingOn(feedId = feed.id, syncing = false)
                                val code = (e as? ResponseFailure)?.code
                                val at = Clock.System.now().toEpochMilliseconds()
                                // Counted rather than only logged. Without this
                                // a feed that has stopped working is
                                // indistinguishable from one with nothing to
                                // say, and the reader finds out by noticing a
                                // silence months later.
                                //
                                // But only when the feed can be blamed: with no
                                // network every feed fails together, and a
                                // phone that lost signal for an hour used to
                                // put a failure against each of them.
                                if (countsAgainstSource(code, whisperHasNetwork(context))) {
                                    feedsRepo.recordFailure(feed.id)
                                    failedFeeds.incrementAndGet()
                                    FeedHistory.record(
                                        context, feed.id,
                                        FeedFetch(at, FetchKind.Failed, code?.toString() ?: errorKind(e)),
                                    )
                                } else {
                                    offlineFeeds.incrementAndGet()
                                    FeedHistory.record(context, feed.id, FeedFetch(at, FetchKind.NoNetwork))
                                }
                                feedTimes.add(feed.title to System.currentTimeMillis() - fetchStarted)
                            }
                        }
                    }
                }

                jobs.joinAll()
                result = SyncResult(
                    due = feedsToFetch.size,
                    failed = failedFeeds.get(),
                    unchanged = unchangedFeeds.get(),
                    identical = identicalFeeds.get(),
                    offline = offlineFeeds.get(),
                    resting = restingFeeds,
                    slowest = slowestOf(feedTimes),
                )

            }
        } catch (e: CancellationException) {
            // Leaving the feed is not an error to report; see the per-feed
            // catch above.
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Outer error", e)
            result = SyncResult.broken(e)
        } finally {
            if (needFullTextSync) {
                scheduleFullTextParse()
            }
        }
    }
    Log.d(TAG, "Completed in $time ms")
    return result
}

private suspend fun syncFeed(
    context: Context,
    feedsRepo: SourcesRepository,
    articleRepo: ArticleRepository,
    feedSql: Feed,
    filesDir: File,
    maxFeedItemCount: Int,
    forceNetwork: Boolean = false,
    downloadTime: Instant,
    counter: ByteCounter? = null,
): FeedFetchResult {
    Log.d(TAG, "Fetching ${feedSql.title}")

    val response: Response =
        syncHttpClient(context).getResponse(url = feedSql.url, forceNetwork = forceNetwork, byteCounter = counter)

    // Everything that reads the response happens inside one `use`, so it is
    // closed whichever way this leaves - including a sync cancelled while
    // the database is being asked below, which is how a response came to be
    // reported as never closed.
    val download = response.use {
        // Nothing new: the parse, the article writes and the icon lookup all
        // redo what the last sync did, and the parse is the part that fills
        // the heap. Only when the source has articles, though - a source
        // whose articles were cleared, or one removed and added back at the
        // same address, would otherwise stay empty until the publisher next
        // posted.
        if (response.isSuccessful &&
            servedUnchanged(response.networkResponse?.code, response.cacheResponse != null) &&
            articleRepo.countInFeed(feedSql.id) > 0
        ) {
            null
        } else {
            if (!response.isSuccessful) {
                throw ResponseFailure(response.code, "${response.code} when fetching ${feedSql.title}")
            }
            // Read whole, then fingerprinted: a feed that sends everything
            // every time is most often sending exactly what it sent last
            // time. See FeedDigest. Whole up to a limit, though, and not at
            // all when it is a picture, a sound or a film: a feed address
            // that had become a podcast episode was read to the end, on
            // mobile data too, and then failed to parse.
            val body = response.body
            body.refuseMedia("a feed")
            Triple(body.bytesAtMost(FEED_MAX_BYTES), body.contentType(), response.request.url.toUrl())
        }
    }
    if (download == null) {
        Log.d(TAG, "Unchanged: ${feedSql.title}")
        cleanUpFeed(articleRepo, feedSql, filesDir)
        return FeedFetchResult.Unchanged
    }
    val (body, contentType, finalUrl) = download
    val digest = feedDigest(
        body,
        parseSettingsKey(maxFeedItemCount, getSyncDays(prefs), prefs.blockedWords.getValue()),
    )
    // The same condition as the unchanged case above, for the same reason.
    if (digest == FeedDigest.read(context, feedSql.id) && articleRepo.countInFeed(feedSql.id) > 0) {
        Log.d(TAG, "Identical: ${feedSql.title}")
        cleanUpFeed(articleRepo, feedSql, filesDir)
        return FeedFetchResult.Identical
    }

    val feedParser = FeedParser()
    val feed: JsonFeed = feedParser.parseFeedResponse(
        url = finalUrl,
        responseBody = body.toResponseBody(contentType),
        // The icon is settled below, from what is stored; see there.
        findIcon = false,
    ).let {
        when {
            it.icon?.startsWith("data") == true -> it.copy(icon = null)
            else                                -> it
        }
    }

    val syncedFeed = feedSql.copy(lastSync = Clock.System.now())
    val items = feed.items
    Log.d(TAG, "Parsed ${items?.size ?: 0} items for ${feedSql.title}")
    val days = getSyncDays(prefs)
    val minKeptPubDate = Clock.System.now().minus(
        period = DateTimePeriod(days = days),
        timeZone = TimeZone.currentSystemDefault()
    ).toEpochMilliseconds()
    // Trimmed *before* anything is built. maxFeedItemCount was passed in and
    // then used only for the cleanup afterwards, so a feed offering 135
    // entries had 135 Article objects and 135 content bodies materialised and
    // written, and the setting that was supposed to cap it at 25 capped
    // nothing. Feeds are newest-first by convention, so the newest are taken
    // and then reversed, since insertion goes oldest first.
    val articles =
        items?.take(maxFeedItemCount)
            ?.reversed()
            ?.map { item ->
                val itemGuid = (item.id ?: item.url).toString()
                val article = (articleRepo.getArticleByGuid(
                    guid = itemGuid,
                    feedId = syncedFeed.id
                ) ?: Article(firstSyncedTime = downloadTime))
                    .updateFromParsedEntry(item, itemGuid, feed, syncedFeed.id)
                article to (item.content_html ?: item.content_text ?: "")
            }
            ?.filter { (article, _) ->
                article.pubDate !in 1..<minKeptPubDate
            }
            ?.filterBlockedWords() ?: emptyList()

    Log.d(
        TAG,
        "Prepared ${articles.size} of ${items?.size ?: 0} for ${feedSql.title} (cap $maxFeedItemCount)"
    )

    // The source's mark, for the line under each headline. Three places to
    // look, cheapest first: the feed's own <icon>, then whatever is already
    // stored, then — only when both are empty — the site itself. That last
    // step is a network round trip, so it runs once per source rather than on
    // every sync: a feed that declares no icon and whose site offers none
    // keeps looking, but a feed that has one never asks again.
    //
    // "Already stored" is asked of usableImageUrl rather than isNotBlank. A
    // source with no icon stores `https:` - the default URL's fallback,
    // see FeedItem.feedIconUrl - which is not blank, so this step read it as
    // an icon already found and the site was never asked. Sources that
    // declare no icon in their feed therefore kept a monogram for good.
    val storedIcon = usableImageUrl(syncedFeed.feedImage.toString())
    val declaredIcon = feed.icon?.takeIf { it.isNotBlank() }
    val resolvedIcon = when {
        declaredIcon != null -> declaredIcon
        storedIcon != null -> storedIcon
        // A site that offered nothing is asked again after a week, not on
        // every sync. One source's home page redirected in a loop, twenty
        // requests deep, every time its feed was read.
        !IconLookup.due(context, feedSql.id) -> null
        else -> feed.home_page_url
            ?.let { runCatching { sloppyLinkToStrictURL(it) }.getOrNull() }
            ?.let { site ->
                IconLookup.tried(context, feedSql.id)
                feedParser.findSiteIcon(site)
            }
    }

    feedsRepo.updateSource(
        syncedFeed.copy(
            title = syncedFeed.title,
            feedImage = resolvedIcon?.let { sloppyLinkToStrictURLNoThrows(it) }
                ?: syncedFeed.feedImage
        ))

    articleRepo.updateOrInsertArticle(articles) { article, text ->
        withContext(Dispatchers.IO) {
            blobOutputStream(article.uuid, filesDir).bufferedWriter().use {
                it.write(text)
            }
        }
    }

    cleanUpFeed(articleRepo, syncedFeed, filesDir)
    // Only now, with everything stored: a fingerprint written before a parse
    // that then failed would skip the next download of the same bytes, and
    // the feed would never be read at all.
    FeedDigest.write(context, feedSql.id, digest)
    // New to Whisper, not merely changed: an article seen before keeps the
    // time it was first synced, and only this run's have this one.
    return FeedFetchResult.Parsed(articles.count { (article, _) -> article.firstSyncedTime == downloadTime })
}

/** How one feed's fetch went. */
internal sealed interface FeedFetchResult {
    /** The server said nothing had changed, and sent nothing. */
    data object Unchanged : FeedFetchResult

    /** The server sent the whole feed, byte for byte what it sent last time. */
    data object Identical : FeedFetchResult

    /** Read and stored; [newArticles] of its articles were new to Whisper. */
    data class Parsed(val newArticles: Int) : FeedFetchResult
}

/**
 * Drops articles older than the sync range, with their files.
 *
 * Its own step so an unchanged feed still ages out: a publisher that stops
 * posting would otherwise keep last month's articles for ever.
 */
private suspend fun cleanUpFeed(articleRepo: ArticleRepository, feed: Feed, filesDir: File) {
    val days = getSyncDays(prefs)
    val minKeptPubDate = Clock.System.now().minus(
        period = DateTimePeriod(days = days),
        timeZone = TimeZone.currentSystemDefault()
    ).toEpochMilliseconds()
    val ids = articleRepo.getItemsToBeCleanedFromFeed(
        feedId = feed.id,
        minKeptPubDate = minKeptPubDate
    )
    Log.d(TAG, "Cleanup ${feed.title}: days=$days cutoff=$minKeptPubDate deleting=${ids.size}")

    withContext(Dispatchers.IO) {
        for (id in ids) {
            try {
                deleteArticleFiles(itemId = id, filesDir = filesDir)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to delete the files of $id", e)
            }
        }
    }

    articleRepo.deleteArticles(ids)
}

class ResponseFailure(val code: Int, message: String?) : Exception(message)

fun List<Pair<Article, String>>.filterBlockedWords(): List<Pair<Article, String>> {
    val blocked = prefs.blockedWords.getValue()
        .map { it.lowercase() }
        .filter { it.isNotBlank() }
    if (blocked.isEmpty()) return this
    return filter { (article, text) ->
        val haystack = buildString {
            append(article.title)
            append(article.plainTitle)
            append(article.description)
            append(article.plainSnippet)
            article.author?.let { append(it) }
            article.link?.let { append(it) }
            append(text)
        }.lowercase()
        blocked.none { haystack.contains(it) }
    }
}

internal suspend fun feedsToSync(
    repository: SourcesRepository,
    feedId: Long,
    tag: String,
    staleTime: Long = -1L,
    forceNetwork: Boolean = false,
): List<Feed> {

    val sources = when {
        feedId > 0 -> {
            if (forceNetwork) {
                repository.loadFeedById(feedId)?.let { listOf(it) } ?: emptyList()
            } else {
                repository.loadFeedIfStale(feedId = feedId, staleTime = staleTime)
            }
        }

        feedId == ID_ALL -> {
            Log.d(TAG, "Checking all feeds  = $forceNetwork")
            if (forceNetwork) {
                repository.getAllSources()

            } else {
                repository.loadFeedIfStale(ID_ALL, staleTime)
            }
        }

        tag.isNotEmpty() -> {
            repository.loadFeedsByTag(tag)
        }

        else -> repository.getAllSources()
    }

    return if (tag.isNotEmpty() && feedId == ID_ALL) {
        Log.d(TAG, "Filtering by tag: $tag")
        sources.filter { it.tag.contains(tag) }
    } else {
        Log.d(TAG, "No tag filtering applied $sources")
        sources
    }
}
