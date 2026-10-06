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
package com.saulhdev.feeder.manager.discovery

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.models.Suggestion
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.data.FeedLibrary
import com.saulhdev.feeder.data.db.models.FROM_LIBRARY
import com.saulhdev.feeder.utils.registrableDomain
import com.saulhdev.feeder.utils.blobFile
import com.saulhdev.feeder.utils.blobFullFile
import com.saulhdev.feeder.utils.blobFullInputStream
import com.saulhdev.feeder.utils.blobInputStream
import com.saulhdev.feeder.utils.isFeedHost
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Looks at what the reader actually read, and notices what it kept linking to.
 *
 * Runs weekly rather than on every sync, because the question it answers
 * changes over weeks: three articles linking somewhere is a habit, and a habit
 * is not visible in an hour. Weekly also keeps the cost invisible — this reads
 * article bodies off disk and parses them, which is not something to do while
 * somebody is holding the phone.
 */
class DiscoveryWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { discover() }
            .onFailure { Log.e(TAG, "Discovery pass failed", it) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.failure() })
    }

    private suspend fun discover() {
        val db: NeoFeedDb by inject(NeoFeedDb::class.java)
        val articles = db.feedArticleDao()
        val suggestions = db.suggestionDao()
        val feeds = db.feedSourceDao()

        val since = System.currentTimeMillis() - WINDOW_MS
        val read = articles.readArticlesSince(since, MAX_ARTICLES)
        if (read.size < LinkHarvest.MENTIONS_NEEDED) {
            Log.i(TAG, "Only ${read.size} read articles in the window; nothing to learn from")
            return
        }

        // One article at a time, so a few thousand gzipped bodies never sit in
        // memory together.
        val counts = mutableMapOf<String, Int>()
        read.forEach { article ->
            // The full page first, the feed's own summary only as a fallback.
            //
            // This read the summary alone, which is why the screen stayed
            // empty. An RSS description is a teaser — a paragraph and a link
            // back to the article — so its only outbound link is to the
            // publisher, which is excluded as the article's own host. The
            // links that say anything about what a reader might follow next
            // are in the body of the piece, and the body is in a different
            // file that full-text fetching already writes.
            val full = blobFullFile(article.uuid, context.filesDir)
            val html = if (full.isFile) {
                runCatching {
                    blobFullInputStream(article.uuid, context.filesDir).use { it.reader().readText() }
                }.getOrNull()
            } else {
                val file = blobFile(article.uuid, context.filesDir)
                if (!file.isFile) null
                else runCatching {
                    blobInputStream(article.uuid, context.filesDir).use { it.reader().readText() }
                }.getOrNull()
            } ?: return@forEach
            val ownHost = runCatching { URL(article.link ?: "").host }.getOrNull()
            LinkHarvest.tally(counts, LinkHarvest.domainsIn(html, ownHost))
        }

        // Everything already subscribed to, and everything already offered —
        // including what was refused, because saying no once should mean no.
        // A hosted feed's own address names the service, not the publisher, so
        // a FeedBurner subscription to Android Authority would register as
        // "feedburner.com" and leave androidauthority.com looking unfollowed
        // — and then be suggested, as a site the reader already reads. The
        // articles know where they live even when the feed address does not.
        val subscribed = feeds.loadAllFeeds()
            .flatMap { feed ->
                val own = runCatching { feed.url.host }.getOrNull()
                if (own != null && !isFeedHost(own)) return@flatMap listOf(own)
                val link = articles.latestArticleLink(feed.id)
                listOfNotNull(own, runCatching { URL(link ?: "").host }.getOrNull())
            }
            .map(::registrableDomain)
            .toSet()
        val known = suggestions.knownHosts().map(::registrableDomain).toSet()

        val candidates = LinkHarvest.candidates(counts, subscribed + known)
            .take(MAX_SUGGESTIONS)

        val parser: FeedParser by inject(FeedParser::class.java)
        candidates.forEach { (host, mentions) ->
            // The same autodiscovery adding a feed by hand uses. A domain that
            // publishes no feed is not a suggestion — there would be nothing to
            // subscribe to — so it is dropped rather than recorded.
            val home = sloppyLinkToStrictURLNoThrows("https://$host")
            val found = runCatching { parser.getAlternateFeedLinksAtUrl(home) }
                .getOrDefault(emptyList())
                .firstOrNull() ?: return@forEach

            val feedUrl = found.first
            val title = runCatching { parser.parseFeedUrl(URL(feedUrl))?.title }
                .getOrNull().orEmpty().ifBlank { host }

            suggestions.insert(
                Suggestion(
                    host = registrableDomain(host),
                    feedUrl = feedUrl,
                    title = title,
                    mentions = mentions,
                )
            )
            Log.i(TAG, "Suggesting a site on the evidence of $mentions read articles")
        }

        suggestFromLibrary(suggestions, subscribed, known + candidates.map { it.first }.toSet())
    }

    /**
     * Offers what sits beside the reader's own sources in the bundled packs.
     *
     * The other half of discovery, and the half that works for somebody who
     * reads the news. Link harvesting asks what the things you read point at,
     * which a BBC article answers with the BBC — so a reader of large news
     * sites got an empty screen while the obvious suggestion, that ITV and
     * Sky News exist and are not being followed, sat in a file shipped with
     * the app.
     *
     * Nothing is fetched. The pack already carries the feed's address and its
     * title, both verified before they shipped, so unlike a link suggestion
     * there is no autodiscovery pass and no request to anybody.
     */
    private suspend fun suggestFromLibrary(
        suggestions: com.saulhdev.feeder.data.db.dao.SuggestionDao,
        subscribed: Set<String>,
        excluded: Set<String>,
    ) {
        val library = runCatching { FeedLibrary.allFeeds(context) }.getOrNull().orEmpty()
        if (library.isEmpty()) return

        val domainToPacks = mutableMapOf<String, MutableSet<String>>()
        library.forEach { (pack, feed) ->
            val host = runCatching { URL(feed.url).host }.getOrNull() ?: return@forEach
            domainToPacks.getOrPut(registrableDomain(host)) { mutableSetOf() }.add(pack.slug)
        }

        // Compared on the registrable domain rather than the address: the
        // reader's BBC feed is almost never the one the pack happens to list,
        // and matching on the address would call them unfollowed.
        val mine = subscribed.mapTo(mutableSetOf()) { registrableDomain(it) }
        val packs = LibraryNeighbours.topPacks(mine, domainToPacks)
        if (packs.isEmpty()) return

        val skip = mine + excluded.map(::registrableDomain)
        var offered = 0
        packs.forEach { (slug, shared) ->
            val pack = library.firstOrNull { it.first.slug == slug }?.first ?: return@forEach
            library.asSequence()
                .filter { it.first.slug == slug }
                .map { it.second }
                .filter { feed ->
                    val host = runCatching { URL(feed.url).host }.getOrNull()
                    host != null && registrableDomain(host) !in skip
                }
                .take(MAX_PER_PACK)
                .forEach { feed ->
                    if (offered >= MAX_SUGGESTIONS) return
                    val host = runCatching { URL(feed.url).host }.getOrNull() ?: return@forEach
                    suggestions.insert(
                        Suggestion(
                            host = registrableDomain(host),
                            feedUrl = feed.url,
                            title = feed.title,
                            mentions = shared,
                            kind = FROM_LIBRARY,
                            context = pack.name,
                        )
                    )
                    offered++
                    Log.i(TAG, "Suggesting ${feed.title} from ${pack.name} ($shared of yours)")
                }
        }
    }

    companion object {
        private const val TAG = "Discovery"

        /**
         * How far back the evidence runs.
         *
         * Long enough that a habit shows and short enough that it is a current
         * one: what somebody was reading six months ago is not a suggestion.
         */
        private val WINDOW_MS = TimeUnit.DAYS.toMillis(30)

        /** A ceiling on the pass, so a heavy reader does not pay for it. */
        private const val MAX_ARTICLES = 500

        /**
         * How many to take from any one pack.
         *
         * Two, so three packs cannot become one pack's catalogue. Somebody
         * who follows three papers from one country wants to hear about a fourth, not
         * about the other eleven.
         */
        private const val MAX_PER_PACK = 2

        /**
         * How many to offer at once.
         *
         * A list of thirty is a directory, and the point of this is that each
         * one arrives with a reason worth reading.
         */
        private const val MAX_SUGGESTIONS = 5

        /** The name the on-demand pass runs under, so two cannot overlap. */
        private const val NOW_WORK = "whisper_discovery_now"

        /**
         * Runs the pass at once, because somebody asked for it.
         *
         * The scheduled pass waits for an unmetered network and a healthy
         * battery, which is right for work nobody requested and wrong the
         * moment they press a button: a reader who has just added three
         * sources and wants to know what sits beside them should not be told
         * to wait for Sunday and a wifi network. It still needs *a* network,
         * because half the pass looks for feeds on sites it has only a domain
         * for — though the library half needs none and will produce its
         * suggestions regardless.
         *
         * KEEP rather than REPLACE: pressing it twice should be one pass, not
         * a cancelled one and a fresh start.
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DiscoveryWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_WORK,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private const val PERIODIC_WORK = "whisper_discovery"

        /** Whether an on-demand pass is waiting or running. */
        fun runningNow(context: Context): Flow<Boolean> =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(NOW_WORK)
                .map { infos -> infos.any { !it.state.isFinished } }

        /**
         * Stops the weekly pass. Suggestions are out of Settings until they
         * are worth their screen (ROADMAP, "Suggestions, after the device
         * report"), and a pass fetching home pages for a screen nobody can
         * reach would be network use with nothing to show for it.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DiscoveryWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        // It fetches home pages looking for feeds. Nobody asked
                        // for that at this moment, so it waits for conditions
                        // that cost the reader nothing.
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
