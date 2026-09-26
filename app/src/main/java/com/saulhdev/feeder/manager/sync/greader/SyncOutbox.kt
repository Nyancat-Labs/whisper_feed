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
package com.saulhdev.feeder.manager.sync.greader

import android.content.Context
import androidx.core.content.edit

/**
 * Changes made in Whisper that the server has not been told about yet.
 *
 * Reads, unreads, saves and unsaves, by local article id. Collected as they
 * happen and sent at the next sync, so a change made with no connection is
 * not lost, and one undone before the sync cancels out rather than going out
 * twice.
 *
 * The opposite change replaces the first: reading and then undoing leaves
 * nothing to send, which is what the reader did.
 */
data class Outbox(
    val read: Set<String> = emptySet(),
    val unread: Set<String> = emptySet(),
    val star: Set<String> = emptySet(),
    val unstar: Set<String> = emptySet(),
) {
    fun withRead(ids: Collection<String>, read: Boolean): Outbox =
        if (read) copy(read = this.read + ids, unread = unread - ids.toSet())
        else copy(read = this.read - ids.toSet(), unread = unread + ids)

    fun withStar(id: String, starred: Boolean): Outbox =
        if (starred) copy(star = star + id, unstar = unstar - id)
        else copy(star = star - id, unstar = unstar + id)

    /** Everything waiting, by local id. */
    val pending: Set<String> get() = read + unread + star + unstar

    val isEmpty: Boolean get() = pending.isEmpty()

    /** What is left once [sent] has gone out. */
    fun without(sent: Outbox): Outbox = Outbox(
        read = read - sent.read,
        unread = unread - sent.unread,
        star = star - sent.star,
        unstar = unstar - sent.unstar,
    )
}

/**
 * Which feeds to subscribe, unsubscribe and add, from the two lists and what
 * was known the last time they were compared.
 *
 * Keys are normalised feed addresses. The two memories are what make this a
 * sync rather than a copy:
 *
 * - [lastLocal]: Whisper's feeds at the end of the last sync. A feed the
 *   server has and Whisper does not was either removed here since, and goes
 *   from the server too, or is new on the server, and comes here.
 * - [everOnServer]: every feed the server has been seen with. A feed Whisper
 *   has and the server does not was either removed on the server, and is left
 *   alone here - removals from the server are never applied, because a partial
 *   answer or the wrong account would take somebody's feeds - or is new here,
 *   and goes to the server.
 *
 * The first sync has no memory, so everything each side lacks goes across:
 * signing in on a phone that already has a hundred feeds puts them on the
 * server, with no OPML file to carry by hand.
 */
data class SubscriptionPlan(
    val subscribe: Set<String>,
    val unsubscribe: Set<String>,
    val addLocal: Set<String>,
)

fun planSubscriptions(
    local: Set<String>,
    server: Set<String>,
    lastLocal: Set<String>,
    everOnServer: Set<String>,
): SubscriptionPlan = SubscriptionPlan(
    subscribe = (local - server) - everOnServer,
    unsubscribe = (server - local) intersect lastLocal,
    addLocal = (server - local) - lastLocal,
)

/**
 * Which server feed is which Whisper feed, when their addresses differ.
 *
 * A server keeps a feed under the address it settled on - after redirects,
 * with its own idea of the canonical form - and that is often not the one
 * Whisper was given. Compared by address alone, the first sync against a
 * server that already had the reader's feeds took 29 of them for new ones on
 * each side: copied down to Whisper, and sent up to the server, twice over.
 *
 * So, in order: the same address; a pairing remembered from an earlier sync
 * ([aliases]); then the same title, one to one, which is what both sides take
 * from the feed itself. A pairing found by title is returned in
 * [FeedMatch.newAliases] to be remembered, so a rename later does not undo it.
 * When several feeds share a title - a paper's sections are often all called
 * "The Guardian" - the one whose address ends the most alike is taken.
 *
 * What is left is new on its side. A feed is never left out for having the
 * same title as one already matched: that held back a second Guardian feed
 * from the server as a supposed copy, when it was a different section. Real
 * copies are for "Subscribed twice" to find, and removing one here then
 * removes it from the server too.
 *
 * @param local Whisper's feeds, key to title.
 * @param server the server's feeds, key to title.
 */
data class FeedMatch(
    /** Each server feed's key, as the key it is here: its own when it is new. */
    val serverAs: Map<String, String>,
    val newAliases: Map<String, String>,
)

fun matchFeeds(
    local: Map<String, String>,
    server: Map<String, String>,
    aliases: Map<String, String>,
): FeedMatch {
    val serverAs = LinkedHashMap<String, String>()
    val claimed = HashSet<String>()
    server.keys.sorted().forEach { s ->
        if (s in local) {
            serverAs[s] = s
            claimed += s
        }
    }
    server.keys.sorted().filter { it !in serverAs }.forEach { s ->
        val k = aliases[s]
        if (k != null && k in local && k !in claimed) {
            serverAs[s] = k
            claimed += k
        }
    }
    val newAliases = LinkedHashMap<String, String>()
    val byTitle = local.keys.sorted().groupBy { titleKey(local.getValue(it)) }
    server.keys.sorted().filter { it !in serverAs }.forEach { s ->
        val t = titleKey(server.getValue(s))
        if (t.isEmpty()) return@forEach
        val pick = byTitle[t].orEmpty().filter { it !in claimed }
            .maxByOrNull { commonSuffixLength(it, s) } ?: return@forEach
        serverAs[s] = pick
        claimed += pick
        newAliases[s] = pick
    }
    server.keys.sorted().filter { it !in serverAs }.forEach { serverAs[it] = it }
    return FeedMatch(serverAs, newAliases)
}

private fun commonSuffixLength(a: String, b: String): Int {
    var n = 0
    while (n < a.length && n < b.length && a[a.length - 1 - n] == b[b.length - 1 - n]) n++
    return n
}

/** A title as both sides would agree on it: case, spacing and trailing marks aside. */
fun titleKey(title: String): String =
    title.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', ':', '-', '|', ' ')

/**
 * What the memories become once a plan has been carried out.
 *
 * A feed that left Whisper is forgotten as having been on the server, so that
 * adding it back later sends it again rather than being taken for one the
 * server dropped. One whose removal from the server did not go through is
 * still remembered as Whisper's, so the removal is tried again next time
 * rather than the feed being taken for new on the server and added back.
 */
fun rememberAfter(
    plan: SubscriptionPlan,
    local: Set<String>,
    server: Set<String>,
    lastLocal: Set<String>,
    everOnServer: Set<String>,
    subscribed: Set<String>,
    unsubscribed: Set<String>,
): Pair<Set<String>, Set<String>> {
    val nowLocal = local + plan.addLocal + (plan.unsubscribe - unsubscribed)
    val leftLocally = lastLocal - local - (plan.unsubscribe - unsubscribed)
    val ever = (everOnServer + server + subscribed) - unsubscribed - leftLocally
    return nowLocal to ever
}

/**
 * What the server's unread list changes here: (to mark read, to mark unread).
 *
 * Over articles the server has claimed only, and never one still waiting in
 * the outbox. "Read" only from a [complete] list, and never from an empty
 * one; see GoogleReaderService.pullReadState.
 */
fun readChanges(
    mapped: List<com.saulhdev.feeder.data.db.models.MappedArticle>,
    serverUnread: Set<String>,
    complete: Boolean,
    waiting: Set<String>,
): Pair<List<String>, List<String>> {
    val settled = mapped.filter { it.uuid !in waiting }
    val toUnread = settled.filter { it.readAt != 0L && it.remoteId in serverUnread }.map { it.uuid }
    val toRead = if (complete && serverUnread.isNotEmpty()) {
        settled.filter { it.readAt == 0L && it.remoteId !in serverUnread }.map { it.uuid }
    } else {
        emptyList()
    }
    return toRead to toUnread
}

/**
 * The account's sync state on this phone: the outbox, the two subscription
 * memories and when articles were last matched to the server's.
 *
 * Cleared on signing in and out, so one account's memory never decides what
 * happens to another's feeds.
 */
object GoogleReaderState {
    private const val FILE = "greader_state"
    private const val READ = "read"
    private const val UNREAD = "unread"
    private const val STAR = "star"
    private const val UNSTAR = "unstar"
    private const val LAST_LOCAL = "last_local"
    private const val EVER_ON_SERVER = "ever_on_server"
    private const val MAPPED_AT = "mapped_at"
    private const val SAVES_REQUEUED = "saves_requeued_v1"
    private const val ALIASES = "aliases"
    private const val NOT_ON_SERVER = "not_on_server_v2"
    private const val REFUSED = "refused"

    private val lock = Any()

    fun outbox(context: Context): Outbox = with(prefs(context)) {
        Outbox(
            read = getStringSet(READ, null).orEmpty().toSet(),
            unread = getStringSet(UNREAD, null).orEmpty().toSet(),
            star = getStringSet(STAR, null).orEmpty().toSet(),
            unstar = getStringSet(UNSTAR, null).orEmpty().toSet(),
        )
    }

    fun updateOutbox(context: Context, change: (Outbox) -> Outbox) {
        runCatching {
            synchronized(lock) {
                val next = change(outbox(context))
                prefs(context).edit {
                    putStringSet(READ, next.read)
                    putStringSet(UNREAD, next.unread)
                    putStringSet(STAR, next.star)
                    putStringSet(UNSTAR, next.unstar)
                }
            }
        }
    }

    fun lastLocal(context: Context): Set<String> =
        prefs(context).getStringSet(LAST_LOCAL, null).orEmpty().toSet()

    fun everOnServer(context: Context): Set<String> =
        prefs(context).getStringSet(EVER_ON_SERVER, null).orEmpty().toSet()

    fun rememberSubscriptions(context: Context, lastLocal: Set<String>, everOnServer: Set<String>) {
        prefs(context).edit {
            putStringSet(LAST_LOCAL, lastLocal)
            putStringSet(EVER_ON_SERVER, everOnServer)
        }
    }

    /** Server feed key to Whisper feed key, for pairs whose addresses differ. See matchFeeds. */
    fun aliases(context: Context): Map<String, String> =
        prefs(context).getStringSet(ALIASES, null).orEmpty().mapNotNull { entry ->
            val tab = entry.indexOf('\t')
            if (tab <= 0) null else entry.substring(0, tab) to entry.substring(tab + 1)
        }.toMap()

    fun setAliases(context: Context, aliases: Map<String, String>) {
        prefs(context).edit { putStringSet(ALIASES, aliases.mapTo(HashSet()) { (s, k) -> "$s\t$k" }) }
    }

    /**
     * Whisper's feeds the server did not have at the last sync, for the
     * account screen and the report. Titles only, never addresses.
     */
    fun notOnServer(context: Context): List<MissingFeed> =
        prefs(context).getStringSet(NOT_ON_SERVER, null).orEmpty().mapNotNull { entry ->
            val parts = entry.split('\t')
            val kind = parts.getOrNull(1)?.let { k -> MissingKind.entries.firstOrNull { it.name == k } }
                ?: return@mapNotNull null
            MissingFeed(parts[0], kind, parts.getOrNull(2)?.toIntOrNull() ?: 0)
        }.sortedBy { it.title.lowercase() }

    fun setNotOnServer(context: Context, feeds: List<MissingFeed>) {
        prefs(context).edit {
            putStringSet(NOT_ON_SERVER, feeds.mapTo(HashSet()) { "${it.title}\t${it.kind.name}\t${it.status}" })
        }
    }

    /** Feeds the server refused, by key: when, and with what status. */
    fun refusals(context: Context): Map<String, Refusal> =
        prefs(context).getStringSet(REFUSED, null).orEmpty().mapNotNull { entry ->
            val parts = entry.split('\t')
            val at = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            parts[0] to Refusal(at, parts.getOrNull(2)?.toIntOrNull() ?: 0)
        }.toMap()

    fun setRefusals(context: Context, refusals: Map<String, Refusal>) {
        prefs(context).edit {
            putStringSet(REFUSED, refusals.mapTo(HashSet()) { (key, r) -> "$key\t${r.at}\t${r.status}" })
        }
    }

    /** When articles were last matched to the server's, or 0 for never. */
    fun mappedAt(context: Context): Long = prefs(context).getLong(MAPPED_AT, 0L)

    /**
     * Whether every save here has been queued for the server once.
     *
     * Saves of articles the server had not matched used to be let go
     * unsent, so a device could hold saves the server never heard of. Queued
     * once, after the fix, to send what was lost; after that each save is
     * queued as it is made.
     */
    fun savesRequeued(context: Context): Boolean = prefs(context).getBoolean(SAVES_REQUEUED, false)

    fun setSavesRequeued(context: Context) {
        prefs(context).edit { putBoolean(SAVES_REQUEUED, true) }
    }

    fun setMappedAt(context: Context, at: Long) {
        prefs(context).edit { putLong(MAPPED_AT, at) }
    }

    fun clear(context: Context) {
        runCatching { synchronized(lock) { prefs(context).edit { clear() } } }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * Saves and unsaves to hold for the next sync rather than let go.
 *
 * Those of articles the server has not matched yet, but whose feed it has:
 * it may not have fetched the article yet, or it is older than matching
 * reaches, and either can change. A save of an article from a feed the server
 * does not carry has nowhere to go, and is let go as before.
 */
fun savesToKeep(outbox: Outbox, mapped: Set<String>, feedOnServer: Set<String>): Set<String> =
    (outbox.star + outbox.unstar).filterTo(HashSet()) { it !in mapped && it in feedOnServer }

/** Why one of Whisper's feeds is not on the server. */
enum class MissingKind {
    /** Sent, and the server would not take it: usually the site blocks the server. */
    REFUSED,

    /** The server had it and dropped it; kept here, because removals there are never applied. */
    REMOVED_THERE,

    /** Not sent: the server gave no permission to change anything. */
    NO_WRITE,
}

data class MissingFeed(val title: String, val kind: MissingKind, val status: Int = 0)

/** The last time the server refused a feed, and what it answered. */
data class Refusal(val at: Long, val status: Int)

/**
 * A week between tries at a feed the server refused.
 *
 * FreshRSS refuses a feed its server cannot fetch - most often a site that
 * blocks servers, and that answers the same way tomorrow. Asked on every sync,
 * four such feeds were four failing fetches for the server each time, with
 * the phone's copy of each working the whole while.
 */
const val REFUSED_RETRY_MS = 7 * 24 * 60 * 60_000L

/**
 * Whether to offer the server a feed again. Always when the reader asked,
 * with Sync now - that is how a fix on the server gets tried - and otherwise
 * once the week is up.
 */
fun refusedDueForRetry(last: Refusal?, nowMs: Long, asked: Boolean): Boolean =
    asked || last == null || nowMs - last.at >= REFUSED_RETRY_MS
