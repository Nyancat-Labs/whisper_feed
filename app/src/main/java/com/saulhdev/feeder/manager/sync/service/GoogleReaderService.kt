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
package com.saulhdev.feeder.manager.sync.service

import com.saulhdev.feeder.manager.sync.greader.isCatchAllFolder
import android.content.Context
import android.util.Log
import com.saulhdev.feeder.data.content.SyncAccount
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderIds
import com.saulhdev.feeder.manager.sync.greader.StreamItem
import com.saulhdev.feeder.manager.sync.syncFeeds
import com.saulhdev.feeder.data.db.ID_ALL
import com.saulhdev.feeder.utils.isSameFeedUrl
import com.saulhdev.feeder.utils.normalizeFeedUrl
import com.saulhdev.feeder.utils.isUnmetered
import com.saulhdev.feeder.manager.sync.greader.AccountTally
import com.saulhdev.feeder.manager.sync.greader.MatchStats
import com.saulhdev.feeder.utils.bytesSince
import com.saulhdev.feeder.utils.StepTrace
import com.saulhdev.feeder.utils.receivedBytes
import com.saulhdev.feeder.manager.sync.greader.AccountTallyStore
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderState
import com.saulhdev.feeder.manager.sync.greader.MissingFeed
import com.saulhdev.feeder.manager.sync.greader.MissingKind
import com.saulhdev.feeder.manager.sync.greader.Refusal
import com.saulhdev.feeder.manager.sync.greader.refusedDueForRetry
import com.saulhdev.feeder.manager.sync.greader.planSubscriptions
import com.saulhdev.feeder.manager.sync.greader.matchFeeds
import com.saulhdev.feeder.manager.sync.greader.readChanges
import com.saulhdev.feeder.manager.sync.greader.rememberAfter
import com.saulhdev.feeder.manager.sync.greader.savesToKeep
import com.saulhdev.feeder.manager.sync.greader.starsToLookUp
import com.saulhdev.feeder.manager.sync.greader.starsUnplacedAfter
import com.saulhdev.feeder.manager.sync.greader.SaveLookupsAfter
import com.saulhdev.feeder.manager.sync.greader.planSaveLookups
import com.saulhdev.feeder.manager.sync.greader.saveLookupDue
import java.net.URL
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Whisper with a Google Reader account attached.
 *
 * The division of labour is the part worth understanding. This service does
 * **not** fetch articles from the server. It syncs the *subscription list* and
 * the *read and starred state*, and leaves the fetching to the local path that
 * already works.
 *
 * That is a deliberate choice rather than a shortcut:
 *
 * - Whisper's articles carry things the protocol has no field for — the full
 *   text it extracted, the image it picked out, the summary it built. Taking
 *   articles from the server would mean either losing those or fetching twice.
 * - Fetching locally keeps the app's behaviour identical with and without an
 *   account, which is the whole local-first arrangement. An account changes
 *   *which feeds* and *what has been read*, not what an article is.
 * - It works when the server is down. A reader whose FreshRSS box is offline
 *   still gets their news.
 *
 * What the reader gets is what they actually wanted from sync: the same
 * subscriptions and the same read state on every device.
 */
/** See [GoogleReaderService.sync]: one account sync at a time, app-wide. */
private val accountLock = Mutex()

class GoogleReaderService(
    private val context: Context,
    private val account: SyncAccount,
    private val sources: SourcesRepository,
    private val articles: ArticleRepository,
    private val api: GoogleReaderApi = GoogleReaderApi(account.serverUrl),
) : RssService() {

    override suspend fun sync(forceNetwork: Boolean, retryRefused: Boolean, trace: StepTrace?): SyncOutcome {
        val auth = account.authToken
        if (auth.isEmpty()) return SyncOutcome.SignedOut

        // One account sync at a time, whoever asked. The first night with a
        // live server had the schedule, the panel and Sync now all comparing
        // feed lists at once, each adding what the others were adding, and
        // one feed came out four times. The feed fetching already had a lock
        // of its own; the account half did not.
        return accountLock.withLock { syncLocked(auth, forceNetwork, retryRefused, trace) }
    }

    private suspend fun syncLocked(auth: String, forceNetwork: Boolean, retryRefused: Boolean, trace: StepTrace?): SyncOutcome {
        return try {
            // The order is the design. Feeds first, both ways, so the
            // articles fetched next come from the right list. Then the
            // matching, which says which of those articles the server knows.
            // Then what changed here goes up, before anything comes down: a
            // read made in Whisper and not yet sent would otherwise be undone
            // by the server's older answer, which is what happened to every
            // read in the first version of this.
            // Each step timed, for the history: a sync that takes minutes
            // should say which step they went on.
            val steps = mutableListOf<Pair<String, Long>>()
            suspend fun <T> step(name: String, block: suspend () -> T): T {
                val started = System.currentTimeMillis()
                trace?.started(name)
                return try {
                    block()
                } finally {
                    val ms = System.currentTimeMillis() - started
                    steps += name to ms
                    trace?.finished(name, ms)
                }
            }
            val token = step("sign-in") { api.writeToken(auth) }
            // Except what the server can already place: that goes up first.
            // Android cuts a background sync off as Whisper leaves the
            // screen, often while the feeds are still coming in, and one
            // evening twelve reads waited nine hours behind runs that never
            // got as far as sending. Sent before the feeds, a short run still
            // delivers them. The rest waits for the matching, as before.
            val early = step("sending matched") { pushChanges(auth, token, matchedOnly = true) }
            var tally = step("subscriptions") { syncSubscriptions(auth, token, retryRefused) }

            // Articles still come from the feeds themselves; see the note above.
            // ID_ALL rather than the default, so a feed fetched in the last
            // few minutes is left alone unless the reader forced it, as it is
            // without an account.
            val feeds = step("feeds") { syncFeeds(context = context, feedId = ID_ALL, forceNetwork = forceNetwork) }

            val match = step("matching") { mapRemoteIds(auth) }
            requeueSavesOnce()
            val saves = step("saves lookup") { mapPendingSaves(auth) }
            val push = step("sending") { pushChanges(auth, token, saves.keep) }
            tally = tally.copy(
                readSent = early.read + push.read, unreadSent = early.unread + push.unread,
                savedSent = early.saved + push.saved, unsavedSent = early.unsaved + push.unsaved,
                changesKept = push.kept,
                savesNotFound = saves.givenUp.size,
            )
            if (push.ok) {
                val (read, unread) = step("read state") { pullReadState(auth) }
                tally = tally.copy(readHere = read, unreadHere = unread, savedHere = step("stars") { pullStars(auth) })
            } else {
                Log.w(TAG, "Changes not sent; the server's read state waits for the next sync")
            }
            tally = tally.copy(matched = articles.mappedArticles().size, steps = steps, match = match)

            account.lastSync = System.currentTimeMillis()
            AccountTallyStore.write(context, tally, account.lastSync)
            SyncOutcome.Success(feeds = feeds.copy(account = tally))
        } catch (e: CancellationException) {
            // Android stopping the work, or the reader leaving: not a failure.
            // Caught as one, it read "failed: pd2" - the obfuscated name of
            // the cancellation - and was retried at once, eight times over.
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Sync failed", t)
            // A 401 means the token has been revoked server-side, which needs
            // the reader rather than a retry. Anything else is worth retrying
            // quietly — a server being down is not a reason to log someone out.
            if (t.message?.contains("401") == true) SyncOutcome.SignedOut
            else SyncOutcome.Failed(t)
        }
    }

    /**
     * Makes the two subscription lists agree, in both directions.
     *
     * See [planSubscriptions] for how each side's additions and removals are
     * told apart. Removals on the server are still never applied here:
     * deleting somebody's feeds because a server did not mention them is
     * unrecoverable, and a partial response, a server mid-migration or the
     * wrong account are exactly what a first version meets. A feed removed
     * in Whisper is removed from the server, because that is what the reader
     * did, here, on purpose.
     *
     * Folders from the server win for a feed both sides have: they are what
     * the reader set on whichever device they set it.
     */
    private suspend fun syncSubscriptions(auth: String, token: String?, retryRefused: Boolean): AccountTally {
        val remote = api.subscriptions(auth)
        val local = sources.getAllSubscriptions()
        val localByKey = local.associateBy { normalizeFeedUrl(it.url) }
        val remoteByKey = remote.mapNotNull { sub ->
            runCatching { URL(sub.feedUrl) }.getOrNull()?.let { normalizeFeedUrl(it) to sub }
        }.toMap()

        // Which server feed is which of ours, addresses aside; see matchFeeds.
        val oldAliases = GoogleReaderState.aliases(context)
        val match = matchFeeds(
            local = localByKey.mapValues { it.value.title },
            server = remoteByKey.mapValues { it.value.title },
            aliases = oldAliases,
        )
        // In Whisper's keys from here on: a server feed matched to one of
        // ours goes by our key.
        val remoteAs = match.serverAs.entries
            .associate { (serverKey, localKey) -> localKey to remoteByKey.getValue(serverKey) }
        serverStreams = remoteAs.mapValues { it.value.id }.filterValues { it.isNotBlank() }
        val localKeys = localByKey.keys
        GoogleReaderState.setAliases(
            context,
            (oldAliases + match.newAliases).filterKeys { it in remoteByKey },
        )

        val lastLocal = GoogleReaderState.lastLocal(context)
        val everOnServer = GoogleReaderState.everOnServer(context)
        val plan = planSubscriptions(localKeys, remoteAs.keys, lastLocal, everOnServer)

        plan.addLocal.forEach { key ->
            val sub = remoteAs.getValue(key)
            val url = URL(sub.feedUrl)
            sources.insertSource(
                Feed(
                    title = sub.title.ifBlank { url.host },
                    url = url,
                    tag = sub.folders.joinToString(","),
                    isEnabled = true,
                )
            )
        }

        // Only with a write token: without one every edit would be refused,
        // and the plan is simply made again next time.
        val subscribed = mutableSetOf<String>()
        val unsubscribed = mutableSetOf<String>()
        // What the server refused before, and what it answered: FreshRSS
        // refuses a feed its server cannot fetch, and says why only in its
        // log. One refused within the week is not offered again unless the
        // reader asks, with Sync now; it keeps updating on the phone.
        val now = System.currentTimeMillis()
        val refusals = GoogleReaderState.refusals(context)
            .filterKeys { it in plan.subscribe }
            .toMutableMap()
        if (token != null) {
            plan.subscribe
                .filter { refusedDueForRetry(refusals[it], now, retryRefused) }
                .forEach { key ->
                    val feed = localByKey.getValue(key)
                    val folder = feed.tags.firstOrNull { it.isNotBlank() }
                    val status = api.editSubscriptionStatus(auth, token, "subscribe", feed.url.toString(), feed.title, folder)
                    if (status in 200..299) {
                        subscribed += key
                        refusals.remove(key)
                    } else {
                        refusals[key] = Refusal(now, status)
                    }
                }
            plan.unsubscribe.forEach { key ->
                // By the server's own id for it, which every server accepts.
                val sub = remoteAs.getValue(key)
                if (api.editSubscription(auth, token, "unsubscribe", sub.id.ifBlank { sub.feedUrl })) {
                    unsubscribed += key
                }
            }
        }
        if (plan.subscribe.isNotEmpty() || plan.unsubscribe.isNotEmpty() || plan.addLocal.isNotEmpty()) {
            Log.i(
                TAG,
                "Feeds: ${subscribed.size} of ${plan.subscribe.size} sent, " +
                    "${unsubscribed.size} of ${plan.unsubscribe.size} removed, ${plan.addLocal.size} added here"
            )
        }

        GoogleReaderState.setRefusals(context, refusals)

        // Ours that the server still lacks, and why: a count alone said four
        // were missing and nothing about which.
        val missing = (localKeys - remoteAs.keys - subscribed).map { key ->
            val title = localByKey.getValue(key).title
            when {
                key !in plan.subscribe -> MissingFeed(title, MissingKind.REMOVED_THERE)
                token == null -> MissingFeed(title, MissingKind.NO_WRITE)
                else -> MissingFeed(title, MissingKind.REFUSED, refusals[key]?.status ?: 0)
            }
        }
        GoogleReaderState.setNotOnServer(context, missing)

        // Feeds both sides have: the server's folders.
        remote.forEach { sub ->
            val url = runCatching { URL(sub.feedUrl) }.getOrNull() ?: return@forEach
            val existing = local.firstOrNull { isSameFeedUrl(it.url, url) } ?: return@forEach
            val tag = sub.folders.joinToString(",")
            if (existing.tag != tag && tag.isNotEmpty()) {
                sources.updateSource(existing.copy(tag = tag))
            } else if (tag.isEmpty() && existing.tags.isNotEmpty() && existing.tags.all(::isCatchAllFolder)) {
                // Taken as a category by an earlier version; see isCatchAllFolder.
                sources.updateSource(existing.copy(tag = ""))
            }
        }

        // What is remembered is what was done, so a subscribe that failed is
        // tried again next time rather than taken for a feed the server dropped.
        val (nextLocal, nextEver) = rememberAfter(
            plan = plan,
            local = localKeys,
            server = remoteAs.keys,
            lastLocal = lastLocal,
            everOnServer = everOnServer,
            subscribed = subscribed,
            unsubscribed = unsubscribed,
        )
        GoogleReaderState.rememberSubscriptions(context, nextLocal, nextEver)
        return AccountTally(
            serverFeeds = (remoteAs.keys + subscribed - unsubscribed).size,
            feedsSent = subscribed.size,
            feedsRefused = missing.count { it.kind == MissingKind.REFUSED },
            feedsRemoved = unsubscribed.size,
            feedsAdded = plan.addLocal.size,
        )
    }

    /**
     * Attaches the server's ids to the articles this app already has.
     *
     * The two sides name the same article differently and nothing connected
     * them: a local `uuid` is generated here and means nothing anywhere else,
     * while the server assigns an id of its own. Articles are fetched from the
     * feeds rather than from the server, so the server's id never arrives with
     * them — this is the call that goes and asks.
     *
     * Matched on the article's address, which is the only thing both sides
     * know. Not the guid: that is set by the publisher and has nothing to do
     * with the id the server assigned.
     *
     * Only what arrived since the last match, with an hour's overlap: each
     * item comes with its whole article, and asking for the lot every half
     * hour would download the server's copy of everything, every time.
     *
     * An article newly matched keeps what the reader did here: read in
     * Whisper before the server knew it, it is sent up as read, rather than
     * being marked unread by a server that has simply not heard yet. The same
     * for saved.
     */
    private suspend fun mapRemoteIds(auth: String): MatchStats? {
        val startedAt = System.currentTimeMillis()
        val receivedBefore = receivedBytes()
        val before = articles.mappedArticles().mapTo(HashSet()) { it.uuid }
        val last = GoogleReaderState.mappedAt(context)
        // The first match brings the server's copy of every article in the
        // window, which after signing in with a hundred feeds is tens of
        // megabytes. It waits for Wi-Fi; after that each match is the last
        // half hour's worth, and small.
        // Every match waits for Wi-Fi, not only the first. Each brings the
        // server's copy of every article since the last, whole: about 5.5 MB
        // of mobile data for each sync one afternoon, to learn eight hundred
        // links. Changes to articles already matched still go up on mobile
        // data; see the early sending in syncLocked. One read meanwhile on an
        // article not yet matched is sent once a match on Wi-Fi finds it.
        if (!isUnmetered(context)) {
            Log.i(TAG, "Matching articles waits for Wi-Fi")
            return MatchStats(pages = 0, items = 0, bytes = null, finished = false, waitingForWifi = true)
        }
        // Two days back at most, the first time and after a long gap alike:
        // each item arrives with its whole article, and a hundred and forty
        // feeds' week was fifty megabytes. Older articles simply keep their
        // read state to themselves.
        val since = matchSince(last, startedAt)
        // Oldest first, and where it got to saved after every page. Newest
        // first, with the time saved only at the end, a run Android stopped
        // left nothing behind: one morning three syncs on the charger each
        // spent ten minutes on the same seventeen hours of articles, and
        // were each stopped at the limit having saved none of it.
        var attached = 0
        var seen = 0
        var newly = 0
        var continuation: String? = null
        val pagesSeen = HashSet<String>()
        var pages = 0
        var finished = false
        while (pages < MAP_MAX_PAGES) {
            val (items, next) = api.contentsPage(
                auth, GoogleReaderIds.STREAM_READING_LIST, MAP_PAGE_SIZE, since, continuation, oldestFirst = true,
            )
            pages++
            seen += items.size
            items.forEach { item ->
                val (link, remoteId) = item.mapping() ?: return@forEach
                attached += articles.attachRemoteId(link, remoteId)
            }
            // What this page matched is queued now, not at the end: an
            // article matched by a run that is then stopped is "already
            // matched" to the next, which would never queue it.
            newly += queueNewlyMatched(before)
            val reached = matchProgress(items)
            if (next == null || !pagesSeen.add(next)) {
                finished = true
                break
            }
            reached?.let { GoogleReaderState.setMappedAt(context, it) }
            continuation = next
        }
        // All of it: the next match starts from here. Stopped by the cap
        // instead, it starts from the last page's newest, saved above, and
        // the next sync carries on.
        if (finished) GoogleReaderState.setMappedAt(context, startedAt)
        Log.i(TAG, "Mapped $attached of $seen server items in $pages pages, $newly newly${if (finished) "" else ", more next time"}")
        return MatchStats(pages = pages, items = seen, bytes = bytesSince(receivedBefore), finished = finished)
    }

    /**
     * Queues what the reader did here to articles matched since [before] was
     * taken, and adds them to it. Read in Whisper before the server knew it,
     * an article is sent up as read rather than marked unread by a server
     * that has simply not heard yet. The same for saved.
     */
    private suspend fun queueNewlyMatched(before: MutableSet<String>): Int {
        val newlyMapped = articles.mappedArticles().filter { it.uuid !in before }
        if (newlyMapped.isEmpty()) return 0
        GoogleReaderState.updateOutbox(context) { outbox ->
            outbox
                .withRead(newlyMapped.filter { it.readAt != 0L && it.uuid !in outbox.unread }.map { it.uuid }, true)
                .let { o ->
                    newlyMapped.filter { it.bookmarked && it.uuid !in o.unstar }
                        .fold(o) { acc, a -> acc.withStar(a.uuid, true) }
                }
        }
        newlyMapped.mapTo(before) { it.uuid }
        return newlyMapped.size
    }

    /**
     * Sends what the reader changed here: reads, unreads, saves, unsaves.
     *
     * By the server's id, so only for articles it has claimed. An article it
     * has not claimed by now has nothing to be sent to - its feed is not on
     * the server - and is let go rather than kept for ever.
     *
     * False if anything failed, and then nothing is let go: it all waits for
     * the next sync, and the server's read state is not applied this time,
     * because it would be older than what is still waiting to go.
     */
    /** Each of our feeds' stream on the server, by our key; set by syncSubscriptions. */
    private var serverStreams: Map<String, String> = emptyMap()

    /** See GoogleReaderState.savesRequeued. */
    private suspend fun requeueSavesOnce() {
        if (GoogleReaderState.savesRequeued(context)) return
        val saved = articles.savedIds()
        GoogleReaderState.updateOutbox(context) { outbox ->
            saved.filter { it !in outbox.unstar }.fold(outbox) { acc, id -> acc.withStar(id, true) }
        }
        GoogleReaderState.setSavesRequeued(context)
        Log.i(TAG, "Queued ${saved.size} saves for the server once")
    }

    /**
     * Finds the server's id for saves waiting to go whose article it has not
     * matched, by looking in that article's own feed on the server.
     *
     * Matching reads the reading list from a little before the last sync, so
     * an older article is never matched there, and a save of one used to be
     * let go unsent — the saves on one phone were simply never seen by the
     * others. A feed's own stream reaches back as far as the server keeps it.
     *
     * Returns the saves to hold for the next sync: still not found, but from
     * a feed the server carries (see savesToKeep), and not yet looked for as
     * often as a save gets (see planSaveLookups); and the ones let go.
     */
    private suspend fun mapPendingSaves(auth: String): SaveLookupsAfter {
        val outbox = GoogleReaderState.outbox(context)
        val saves = outbox.star + outbox.unstar
        val none = SaveLookupsAfter(keep = emptySet(), records = emptyMap(), givenUp = emptySet())
        val mappedBefore = if (saves.isEmpty()) emptySet() else articles.mappedArticles().mapTo(HashSet()) { it.uuid }
        val missing = saves.filter { it !in mappedBefore }
        if (missing.isEmpty()) {
            GoogleReaderState.setSaveLookups(context, emptyMap())
            return none
        }
        val streamOf = articles.feedUrlsOf(missing).mapNotNull { (uuid, url) ->
            val key = runCatching { normalizeFeedUrl(URL(url)) }.getOrNull() ?: return@mapNotNull null
            serverStreams[key]?.let { uuid to it }
        }.toMap()
        // Only the ones due another look. Each costs up to a thousand items
        // of its feed, and one the server has purged is never found.
        val now = System.currentTimeMillis()
        val before = GoogleReaderState.saveLookups(context)
        val due = streamOf.filterKeys { saveLookupDue(before[it], now) }
        var attached = 0
        due.entries.groupBy({ it.value }, { it.key }).forEach { (stream, ids) ->
            var continuation: String? = null
            val seen = HashSet<String>()
            var pages = 0
            while (pages < SAVE_LOOKUP_PAGES) {
                val (items, next) = api.contentsPage(auth, stream, MAP_PAGE_SIZE, null, continuation)
                pages++
                items.forEach { item ->
                    val (link, remoteId) = item.mapping() ?: return@forEach
                    attached += articles.attachRemoteId(link, remoteId)
                }
                if (ids.all { articles.remoteIdFor(it) != null }) break
                if (next == null || !seen.add(next)) break
                continuation = next
            }
        }
        val mapped = articles.mappedArticles().mapTo(HashSet()) { it.uuid }
        val held = savesToKeep(outbox, mapped, streamOf.keys)
        val after = planSaveLookups(held, looked = due.keys, before = before, now = now)
        GoogleReaderState.setSaveLookups(context, after.records)
        Log.i(
            TAG,
            "Saves: ${missing.size} unmatched, $attached matched in their feeds, " +
                "${after.keep.size} held, ${after.givenUp.size} let go",
        )
        return after
    }

    /**
     * Sends what is waiting in the outbox.
     *
     * With [matchedOnly], only the changes to articles the server has already
     * matched, and only those leave the outbox: one it has not matched yet
     * may be by the matching that follows, and has to still be there then.
     */
    private suspend fun pushChanges(
        auth: String,
        token: String?,
        keepSaves: Set<String> = emptySet(),
        matchedOnly: Boolean = false,
    ): PushResult {
        val waiting = GoogleReaderState.outbox(context)
        if (waiting.isEmpty) return PushResult(ok = true)
        if (token == null) return PushResult(ok = false, kept = waiting.pending.size)
        val remoteIds = articles.mappedArticles().associate { it.uuid to it.remoteId }
        val outbox = if (matchedOnly) waiting.only(remoteIds.keys) else waiting
        if (outbox.isEmpty) return PushResult(ok = true)
        val batches = listOf(
            Triple(outbox.read, GoogleReaderIds.TAG_READ, true),
            Triple(outbox.unread, GoogleReaderIds.TAG_READ, false),
            Triple(outbox.star, GoogleReaderIds.TAG_STARRED, true),
            Triple(outbox.unstar, GoogleReaderIds.TAG_STARRED, false),
        )
        var ok = true
        val accepted = IntArray(batches.size)
        batches.forEachIndexed { i, (ids, tag, add) ->
            ids.mapNotNull(remoteIds::get).chunked(EDIT_BATCH).forEach { chunk ->
                val done = api.editTag(
                    auth = auth,
                    token = token,
                    itemIds = chunk,
                    addTag = if (add) tag else null,
                    removeTag = if (add) null else tag,
                )
                if (done) accepted[i] += chunk.size else ok = false
            }
        }
        if (ok) {
            // Everything in the outbox as it was read above is done with:
            // sent, or never sendable - except the saves still waiting for
            // the server to know their article, which go round again.
            // Anything the reader did during the sending is still there.
            val done = outbox.copy(star = outbox.star - keepSaves, unstar = outbox.unstar - keepSaves)
            GoogleReaderState.updateOutbox(context) { it.without(done) }
        }
        Log.i(TAG, "Sent ${outbox.pending.count { it in remoteIds }} changes; ${if (ok) "all accepted" else "some refused, kept"}")
        return PushResult(
            ok = ok,
            read = accepted[0], unread = accepted[1], saved = accepted[2], unsaved = accepted[3],
            kept = if (ok) 0 else outbox.pending.size - accepted.sum(),
        )
    }

    /** What went up, by kind, and what the server did not take. */
    private data class PushResult(
        val ok: Boolean,
        val read: Int = 0,
        val unread: Int = 0,
        val saved: Int = 0,
        val unsaved: Int = 0,
        val kept: Int = 0,
    )

    /**
     * Brings read state down from the server and applies it.
     *
     * **Only articles the server has claimed are touched.** An article with
     * no `remoteId` is one the server has never mentioned, so its absence from
     * a list of unread ids means nothing about whether it has been read.
     *
     * The unread list is read page by page, and only a *complete* one is
     * allowed to mark anything read: "not in the list" means read only when
     * the list is all of it. An incomplete one still marks unread what it
     * does name. An empty one changes nothing - everything read, or a server
     * that answered oddly, and the second is the one to guard against.
     *
     * Nothing still waiting in the outbox is touched: what the reader did
     * here is newer than anything the server can say.
     */
    private suspend fun pullReadState(auth: String): Pair<Int, Int> {
        val page = api.allItemIds(
            auth = auth,
            stream = GoogleReaderIds.STREAM_READING_LIST,
            excludeTag = GoogleReaderIds.TAG_READ,
        )
        val unread = page.items.toHashSet()
        val waiting = GoogleReaderState.outbox(context).pending
        val change = readChanges(articles.mappedArticles(), unread, page.complete, waiting)
        articles.applyServerRead(read = change.first, unread = change.second)
        Log.i(
            TAG,
            "Read state applied: ${change.first.size} read, ${change.second.size} unread, " +
                "${unread.size} unread on the server${if (page.complete) "" else " (partial)"}"
        )
        return change.first.size to change.second.size
    }

    /**
     * Stars from the server, as saves here. Additions only: an empty or
     * partial list must not unsave anything, and a save is the one thing the
     * reader would least forgive losing. Unsaving here does reach the server.
     */
    private suspend fun pullStars(auth: String): Int {
        // The saved list as ids: a few kilobytes. It was read with contents,
        // eight pages of 250 on every sync, and an id written for each, to
        // learn what the ids alone say for every article already matched.
        val starred = api.allItemIds(auth, stream = GoogleReaderIds.STREAM_STARRED, pageSize = STAR_IDS_PAGE, maxPages = 2)
            .items.toHashSet()
        val unplacedBefore = GoogleReaderState.starsUnplaced(context)
        if (starred.isEmpty()) {
            if (unplacedBefore.isNotEmpty()) GoogleReaderState.setStarsUnplaced(context, emptySet())
            return 0
        }
        // Contents only for saves this phone cannot place yet: an article
        // saved on another device and older than matching reaches, whose
        // address is the only way to find it here. Each is asked once; one
        // still not found is remembered, and not asked again while it stays
        // saved.
        val known = articles.mappedArticles().mapTo(HashSet()) { it.remoteId }
        val lookUp = starsToLookUp(starred, known, unplacedBefore)
        lookUp.chunked(STAR_CONTENTS_BATCH).forEach { chunk ->
            api.itemsContents(auth, chunk).forEach { item ->
                val (link, remoteId) = item.mapping() ?: return@forEach
                articles.attachRemoteId(link, remoteId)
            }
        }
        val mapped = articles.mappedArticles()
        GoogleReaderState.setStarsUnplaced(
            context,
            starsUnplacedAfter(starred, unplacedBefore, lookUp, mapped.mapTo(HashSet()) { it.remoteId }),
        )
        val waiting = GoogleReaderState.outbox(context).pending
        val toSave = mapped
            .filter { !it.bookmarked && it.remoteId in starred && it.uuid !in waiting }
            .map { it.uuid }
        if (toSave.isNotEmpty()) articles.applyServerStars(toSave)
        Log.i(TAG, "Stars: ${starred.size} on the server, ${lookUp.size} looked up, ${toSave.size} saved here")
        return toSave.size
    }

    override suspend fun setRead(articleId: String, read: Boolean) {
        if (read) articles.markRead(articleId) else articles.unmarkRead(listOf(articleId))

        // And tell the server, if it knows this article. An article with no
        // remoteId is one the server has never seen — there is nothing to tell
        // it, and inventing an id would edit somebody else's article.
        val remoteId = articles.remoteIdFor(articleId) ?: return
        val auth = account.authToken
        if (auth.isEmpty()) return
        val token = api.writeToken(auth) ?: return
        api.editTag(
            auth = auth,
            token = token,
            itemIds = listOf(remoteId),
            addTag = if (read) GoogleReaderIds.TAG_READ else null,
            removeTag = if (read) null else GoogleReaderIds.TAG_READ,
        )
    }

    override suspend fun setStarred(articleId: String, starred: Boolean) {
        articles.bookmarkArticle(articleId, starred)
    }

    override suspend fun subscribe(url: String, title: String?, folder: String?) {
        val auth = account.authToken.ifEmpty { return }
        val token = api.writeToken(auth) ?: return
        api.editSubscription(auth, token, "subscribe", url, title, folder)
    }

    override suspend fun unsubscribe(url: String) {
        val auth = account.authToken.ifEmpty { return }
        val token = api.writeToken(auth) ?: return
        api.editSubscription(auth, token, "unsubscribe", url)
    }

    override suspend fun editFeed(url: String, title: String?, folder: String?) {
        val auth = account.authToken.ifEmpty { return }
        val token = api.writeToken(auth) ?: return
        api.editSubscription(auth, token, "edit", url, title, folder)
    }

    private companion object {
        const val TAG = "GoogleReaderSync"

        /** Items per edit-tag call; the protocol repeats a parameter per item. */
        const val EDIT_BATCH = 100

        /** Items per page, and pages per match: at most two thousand articles. */
        const val MAP_PAGE_SIZE = 250
        const val MAP_MAX_PAGES = 8

        /** How far into one feed's stream a waiting save is looked for: 1,000 items. */
        const val SAVE_LOOKUP_PAGES = 4

        /** The saved list, read in full up to 2,000 saves. */
        /** Ids per page of the saved list; two pages is twenty thousand saves. */
        const val STAR_IDS_PAGE = 10_000

        /** Saved items asked for by id in one request. */
        const val STAR_CONTENTS_BATCH = 100
    }
}

/**
 * Overlap with the last match, for items the server took in late. Ten
 * minutes, not the hour it was: on a two-hour schedule the hour made every
 * match read three hours of items, each with its whole article.
 */
internal const val MAP_OVERLAP_MS = 10 * 60_000L

internal const val DAY_MS = 24 * 60 * 60_000L

/** How far back a match looks at most, the first time or after a gap. See mapRemoteIds. */
internal const val MAP_FIRST_WINDOW_MS = 2 * DAY_MS

/**
 * Where a match starts: an hour before where the last one got to, for items
 * the server took in late, and never more than [MAP_FIRST_WINDOW_MS] back.
 * [last] is zero before the first match.
 */
internal fun matchSince(last: Long, now: Long): Long {
    val floor = now - MAP_FIRST_WINDOW_MS
    return if (last > 0) maxOf(last - MAP_OVERLAP_MS, floor) else floor
}

/** How far a page of a match reached: the newest item on it, or null if none says. */
internal fun matchProgress(items: List<StreamItem>): Long? =
    items.mapNotNull { it.crawledAt() }.maxOrNull()

/** The status a refusal came with, when there was one to report. */
internal fun refusalCode(status: Int?): String = when {
    status == null -> ""
    status <= 0 -> " (no answer)"
    else -> " ($status)"
}
