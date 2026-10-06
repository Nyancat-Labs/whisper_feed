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
package com.saulhdev.feeder.utils

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How one fetch of one feed went. */
enum class FetchKind {
    New,
    Unchanged,
    /** Sent in full, identical to the last download, so not read again. */
    Same,
    Failed,
    NoNetwork,
}

/**
 * One fetch of one feed.
 *
 * [detail] is the count of articles for [FetchKind.New], and the kind of
 * error otherwise - an HTTP status or an exception's class name, never its
 * message. [bytes] is what came over the wire for this feed alone, or -1
 * where it is not known.
 */
data class FeedFetch(
    val at: Long,
    val kind: FetchKind,
    val detail: String = "",
    val bytes: Long = -1,
)

/**
 * The last few fetches of every feed.
 *
 * The sync history is one line per sync, shared by all 120 feeds, and says
 * "4 failed" without saying which four or why. A night when 114 of 120
 * failed over 84 minutes on Wi-Fi could not be explained from it at all.
 * This keeps the last [MAX_PER_FEED] fetches of each feed, so the report can
 * say which feeds failed, how, and what each costs to download.
 *
 * One SharedPreferences key per feed: syncs fetch four feeds at once, and
 * separate keys mean no fetch ever rewrites another's record.
 */
object FeedHistory {
    const val MAX_PER_FEED = 5
    private const val FILE = "feed_history"

    fun record(context: Context, feedId: Long, fetch: FeedFetch) {
        runCatching {
            val prefs = prefs(context)
            val key = feedId.toString()
            val next = FeedHistoryCodec.add(FeedHistoryCodec.decode(prefs.getString(key, null).orEmpty()), fetch)
            prefs.edit { putString(key, FeedHistoryCodec.encode(next)) }
        }
    }

    fun read(context: Context, feedId: Long): List<FeedFetch> = runCatching {
        FeedHistoryCodec.decode(prefs(context).getString(feedId.toString(), null).orEmpty())
    }.getOrDefault(emptyList())

    /** Drops feeds that no longer exist. */
    fun keepOnly(context: Context, feedIds: Set<Long>) {
        runCatching {
            val prefs = prefs(context)
            val stale = prefs.all.keys.filter { it.toLongOrNull() !in feedIds }
            if (stale.isNotEmpty()) prefs.edit { stale.forEach(::remove) }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/** The pure half: how a feed's fetches are kept, stored and written out. */
internal object FeedHistoryCodec {
    private const val FIELD = '\t'

    /** Newest first, never more than [FeedHistory.MAX_PER_FEED]. */
    fun add(fetches: List<FeedFetch>, fetch: FeedFetch): List<FeedFetch> =
        (listOf(fetch) + fetches).take(FeedHistory.MAX_PER_FEED)

    fun encode(fetches: List<FeedFetch>): String = fetches.joinToString("\n") {
        listOf(it.at, it.kind.name, it.detail.replace(FIELD, ' ').replace('\n', ' '), it.bytes)
            .joinToString(FIELD.toString())
    }

    fun decode(text: String): List<FeedFetch> = text.lineSequence().mapNotNull { line ->
        val f = line.split(FIELD)
        if (f.size != 4) return@mapNotNull null
        val at = f[0].toLongOrNull() ?: return@mapNotNull null
        val kind = FetchKind.entries.firstOrNull { it.name == f[1] } ?: return@mapNotNull null
        val bytes = f[3].toLongOrNull() ?: return@mapNotNull null
        FeedFetch(at, kind, f[2], bytes)
    }.toList()

    /** "12:00 new 3 45 KB", "11:45 same 137 KB", "11:30 unchanged", "11:00 failed 503". */
    fun describe(fetch: FeedFetch, clock: SimpleDateFormat): String {
        val time = clock.format(Date(fetch.at))
        val what = when (fetch.kind) {
            FetchKind.New -> "new ${fetch.detail}"
            FetchKind.Unchanged -> "unchanged"
            FetchKind.Same -> "same"
            FetchKind.Failed -> "failed ${fetch.detail}"
            FetchKind.NoNetwork -> "no network"
        }
        val size = fetch.bytes.takeIf { it >= 0 && fetch.kind != FetchKind.Unchanged }?.let(::formatBytes)
        return listOfNotNull(time, what, size).joinToString(" ")
    }

    /**
     * The totals line: what one download of every feed costs, from each
     * feed's newest fetch that came with a body, and the heaviest five - the
     * ones worth checking less often, or dropping.
     */
    fun summary(histories: List<Pair<String, List<FeedFetch>>>): List<String> {
        // A list, not a map: two sources can share a title.
        // A download identical to the last is still a download: the feed
        // costs what it sent, whether or not anything in it was new.
        val latest = histories.map { (title, fetches) ->
            title to fetches.firstOrNull {
                (it.kind == FetchKind.New || it.kind == FetchKind.Same) && it.bytes >= 0
            }?.bytes
        }
        val measured = latest.mapNotNull { it.second }
        val heaviest = latest
            .mapNotNull { (title, bytes) -> bytes?.let { title to it } }
            .sortedByDescending { it.second }
            .take(5)
        return listOfNotNull(
            "One download of every feed: ${formatBytes(measured.sum())} " +
                "(measured for ${measured.size} of ${histories.size})",
            heaviest.takeIf { it.isNotEmpty() }?.let { list ->
                "Heaviest: " + list.joinToString(", ") { (title, bytes) -> "${title.take(28)} ${formatBytes(bytes)}" }
            },
        )
    }

    /** One report line: the feed's name, then its fetches, newest first. */
    fun line(title: String, fetches: List<FeedFetch>): String {
        val clock = SimpleDateFormat("HH:mm", Locale.US)
        val name = title.take(28).padEnd(28)
        val body = if (fetches.isEmpty()) "no fetches recorded" else fetches.joinToString(" · ") { describe(it, clock) }
        return "  $name $body"
    }
}

/**
 * Whether a failure says something about the source itself.
 *
 * An answer from the server - any HTTP status - does. A connection that
 * never happened only does if Whisper had a network to make it on: with
 * none, or with Android keeping Whisper off the one there is, every feed
 * fails at once and none of them is at fault. Counting those is how a
 * phone losing signal in a tunnel could mark every feed as failing.
 */
fun countsAgainstSource(httpCode: Int?, hadNetwork: Boolean): Boolean = httpCode != null || hadNetwork
