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

import com.saulhdev.feeder.utils.seconds
import android.content.Context
import androidx.core.content.edit

/**
 * What one account sync did with the server, for the reader to see.
 *
 * "ok" and a feed count said the feeds were fetched and nothing about the
 * account itself: whether the server had all of them, whether a read here
 * reached it, whether anything came back. After a night of moving a hundred
 * feeds across, "is it all there?" had no answer short of a shell on the
 * server. These are the answers, as counts.
 */
data class AccountTally(
    /** Feeds the server lists after this sync: what it had, plus what it took. */
    val serverFeeds: Int = 0,
    /** Ours sent up and accepted, removed from it, and brought down from it. */
    val feedsSent: Int = 0,
    val feedsRefused: Int = 0,
    val feedsRemoved: Int = 0,
    val feedsAdded: Int = 0,
    /** Articles here the server knows, after this sync. */
    val matched: Int = 0,
    val readSent: Int = 0,
    val unreadSent: Int = 0,
    val savedSent: Int = 0,
    val unsavedSent: Int = 0,
    /** Changes the server did not accept, kept for next time. */
    val changesKept: Int = 0,
    val readHere: Int = 0,
    val unreadHere: Int = 0,
    val savedHere: Int = 0,
    /**
     * Saves looked for on the server as many times as they get and not found,
     * so no longer sent. Still saved here; see planSaveLookups.
     */
    val savesNotFound: Int = 0,
    /**
     * How long each step of the sync took, in order, name to milliseconds:
     * where the minutes went when a sync is slow. For the history only; not
     * stored with the rest.
     */
    val steps: List<Pair<String, Long>> = emptyList(),
)

/**
 * The tally as the sync history writes it, after the feed counts.
 *
 * Always the server's feed count and the articles matched, which are the
 * standing answers; the rest only when something happened.
 */
fun accountSummary(t: AccountTally): String {
    val feeds = listOfNotNull(
        "${t.serverFeeds} feeds on the server",
        if (t.feedsSent > 0) "${t.feedsSent} sent" else null,
        if (t.feedsRefused > 0) "${t.feedsRefused} refused" else null,
        if (t.feedsRemoved > 0) "${t.feedsRemoved} removed" else null,
        if (t.feedsAdded > 0) "${t.feedsAdded} added here" else null,
    )
    val up = counts(t.readSent to "read", t.unreadSent to "unread", t.savedSent to "saved", t.unsavedSent to "unsaved")
    val down = counts(t.readHere to "read", t.unreadHere to "unread", t.savedHere to "saved")
    return listOfNotNull(
        feeds.joinToString(", "),
        "${t.matched} articles matched",
        up?.let { "sent $it" },
        if (t.changesKept > 0) "${t.changesKept} changes kept" else null,
        if (t.savesNotFound > 0) "${t.savesNotFound} saves not on the server" else null,
        down?.let { "received $it" },
        t.steps.takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = "took ") { (step, ms) -> "$step ${seconds(ms)}" },
    ).joinToString("; ")
}

private fun counts(vararg parts: Pair<Int, String>): String? =
    parts.filter { it.first > 0 }.joinToString(", ") { "${it.first} ${it.second}" }.ifEmpty { null }

/**
 * What the account's syncs moved in one day, added up.
 *
 * The screen showed the last sync alone, and a sync with nothing new to move
 * is all zeros: 163 reads came down at five past eleven, two more syncs ran,
 * and the screen said nothing had ever happened. Today's totals keep the
 * confirmation in view until midnight.
 */
data class DayTotals(
    /** The local date these are for, as yyyy-MM-dd. */
    val day: String,
    val feedsSent: Int = 0,
    val feedsRemoved: Int = 0,
    val feedsAdded: Int = 0,
    val readSent: Int = 0,
    val unreadSent: Int = 0,
    val savedSent: Int = 0,
    val unsavedSent: Int = 0,
    val readHere: Int = 0,
    val unreadHere: Int = 0,
    val savedHere: Int = 0,
) {
    val sentAny: Boolean get() = readSent + unreadSent + savedSent + unsavedSent > 0
    val receivedAny: Boolean get() = readHere + unreadHere + savedHere > 0
    val feedsChanged: Boolean get() = feedsSent + feedsRemoved + feedsAdded > 0
}

/** [tally] added to the day's totals, starting the day afresh when it has changed. */
fun DayTotals?.plus(tally: AccountTally, day: String): DayTotals {
    val base = this?.takeIf { it.day == day } ?: DayTotals(day)
    return base.copy(
        feedsSent = base.feedsSent + tally.feedsSent,
        feedsRemoved = base.feedsRemoved + tally.feedsRemoved,
        feedsAdded = base.feedsAdded + tally.feedsAdded,
        readSent = base.readSent + tally.readSent,
        unreadSent = base.unreadSent + tally.unreadSent,
        savedSent = base.savedSent + tally.savedSent,
        unsavedSent = base.unsavedSent + tally.unsavedSent,
        readHere = base.readHere + tally.readHere,
        unreadHere = base.unreadHere + tally.unreadHere,
        savedHere = base.savedHere + tally.savedHere,
    )
}

/** The local date of [ms], as the day totals key it. */
fun dayKey(ms: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))

/** The last sync's tally and today's totals, for the account screen. */
object AccountTallyStore {
    private const val FILE = "greader_tally"
    private const val AT = "at"
    private const val DAY = "day"
    private const val DAY_PREFIX = "day_"

    fun write(context: Context, tally: AccountTally, at: Long = System.currentTimeMillis()) {
        runCatching {
            val totals = today(context, at).plus(tally, dayKey(at))
            prefs(context).edit {
                putLong(AT, at)
                fields(tally).forEach { (key, value) -> putInt(key, value) }
                putString(DAY, totals.day)
                dayFields(totals).forEach { (key, value) -> putInt(DAY_PREFIX + key, value) }
            }
        }
    }

    /** Today's totals, or null when nothing has synced today. */
    fun today(context: Context, nowMs: Long = System.currentTimeMillis()): DayTotals? = runCatching {
        val p = prefs(context)
        val day = p.getString(DAY, null)?.takeIf { it == dayKey(nowMs) } ?: return@runCatching null
        fun i(key: String) = p.getInt(DAY_PREFIX + key, 0)
        DayTotals(
            day = day,
            feedsSent = i("feeds_sent"), feedsRemoved = i("feeds_removed"), feedsAdded = i("feeds_added"),
            readSent = i("read_sent"), unreadSent = i("unread_sent"), savedSent = i("saved_sent"),
            unsavedSent = i("unsaved_sent"), readHere = i("read_here"), unreadHere = i("unread_here"),
            savedHere = i("saved_here"),
        )
    }.getOrNull()

    private fun dayFields(t: DayTotals) = listOf(
        "feeds_sent" to t.feedsSent, "feeds_removed" to t.feedsRemoved, "feeds_added" to t.feedsAdded,
        "read_sent" to t.readSent, "unread_sent" to t.unreadSent, "saved_sent" to t.savedSent,
        "unsaved_sent" to t.unsavedSent, "read_here" to t.readHere, "unread_here" to t.unreadHere,
        "saved_here" to t.savedHere,
    )

    fun read(context: Context): AccountTally? = runCatching {
        val p = prefs(context)
        if (p.getLong(AT, 0L) == 0L) return@runCatching null
        fun i(key: String) = p.getInt(key, 0)
        AccountTally(
            serverFeeds = i("server_feeds"), feedsSent = i("feeds_sent"), feedsRefused = i("feeds_refused"),
            feedsRemoved = i("feeds_removed"), feedsAdded = i("feeds_added"), matched = i("matched"),
            readSent = i("read_sent"), unreadSent = i("unread_sent"), savedSent = i("saved_sent"),
            unsavedSent = i("unsaved_sent"), changesKept = i("changes_kept"),
            readHere = i("read_here"), unreadHere = i("unread_here"), savedHere = i("saved_here"),
            savesNotFound = i("saves_not_found"),
        )
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { prefs(context).edit { clear() } }
    }

    private fun fields(t: AccountTally) = listOf(
        "server_feeds" to t.serverFeeds, "feeds_sent" to t.feedsSent, "feeds_refused" to t.feedsRefused,
        "feeds_removed" to t.feedsRemoved, "feeds_added" to t.feedsAdded, "matched" to t.matched,
        "read_sent" to t.readSent, "unread_sent" to t.unreadSent, "saved_sent" to t.savedSent,
        "unsaved_sent" to t.unsavedSent, "changes_kept" to t.changesKept,
        "read_here" to t.readHere, "unread_here" to t.unreadHere, "saved_here" to t.savedHere,
        "saves_not_found" to t.savesNotFound,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
