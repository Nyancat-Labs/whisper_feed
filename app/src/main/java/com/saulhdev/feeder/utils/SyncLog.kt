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

/**
 * The last few syncs: what started each one, when, how it ended, and what the
 * phone was doing when it began.
 *
 * Every sync used to look the same afterwards. The report could show that
 * sources had synced at 06:41 and 08:07, but not whether the schedule, the
 * panel or a pull to refresh had started it - and that was the whole question
 * when testing a switch that is meant to stop two of the three. Each request
 * now carries its [origin][ORIGIN_KEY], and the worker writes a line here as
 * it starts and again as it ends.
 *
 * Kept in its own small preferences file, so it survives the process being
 * killed overnight, which is when the scheduled syncs happen. Nothing in it
 * names a source or an address: origin, times, outcome, plugged in or not,
 * network kind.
 */
object SyncLog {
    /** The input-data key a sync request carries its origin under. */
    const val ORIGIN_KEY = "origin"

    const val ORIGIN_SCHEDULED = "scheduled"
    const val ORIGIN_PANEL = "panel"
    /** The app coming to the front with a feed older than one interval. */
    const val ORIGIN_OPENED = "opened (app)"
    /**
     * Pull to refresh, by where it was pulled. Told apart because on the
     * launcher panel a downward swipe at the top of the list - the gesture
     * for pulling down notifications everywhere else - reaches Whisper first,
     * and six "pulls" in four minutes one afternoon looked more like that
     * than like six refreshes anybody meant.
     */
    const val ORIGIN_PULL = "pull (app)"
    const val ORIGIN_PULL_PANEL = "pull (panel)"
    const val ORIGIN_ONBOARDING = "onboarding"
    const val ORIGIN_IMPORT = "OPML import"
    const val ORIGIN_RESTORE = "restore"
    /** Adding back, editing or undoing the removal of a source. */
    const val ORIGIN_SOURCE_CHANGE = "source change"
    /** "Sync now" on the account screen. */
    const val ORIGIN_ACCOUNT = "account (sync now)"

    /**
     * The full-article prefetch after a sync. Logged because it is the
     * heaviest download the app makes, and it was the one nobody could see.
     */
    const val ORIGIN_FULL_TEXT = "full text"

    /**
     * The syncs nobody asked for at that moment. Battery Saver pauses these
     * and only these; see FeedSyncer.
     */
    val AUTOMATIC_ORIGINS = setOf(ORIGIN_SCHEDULED, ORIGIN_PANEL, ORIGIN_OPENED)

    /**
     * Syncs the reader asked for with a gesture or a button, which run as a
     * foreground task so they keep the network when the reader leaves.
     */
    val ASKED_ORIGINS = setOf(ORIGIN_PULL, ORIGIN_PULL_PANEL, ORIGIN_ACCOUNT)

    private const val FILE = "sync_log"
    private const val KEY = "entries"
    private val lock = Any()

    /** Records a sync starting. Returns the id to pass to [finished]. */
    fun started(context: Context, origin: String): Long {
        val now = System.currentTimeMillis()
        val phone = deviceSnapshot(context)
        update(context) { SyncHistory.start(it, SyncEntry(now, 0L, origin, "running", phone)) }
        return now
    }

    fun finished(context: Context, id: Long, outcome: String) {
        val phone = deviceSnapshot(context)
        update(context) { SyncHistory.finish(it, id, System.currentTimeMillis(), outcome, phone) }
    }

    fun entries(context: Context): List<SyncEntry> = runCatching {
        SyncHistory.decode(prefs(context).getString(KEY, null).orEmpty())
    }.getOrDefault(emptyList())

    private fun update(context: Context, change: (List<SyncEntry>) -> List<SyncEntry>) {
        runCatching {
            synchronized(lock) {
                val prefs = prefs(context)
                val next = change(SyncHistory.decode(prefs.getString(KEY, null).orEmpty()))
                // commit, not apply: the worker's process can be killed the
                // moment it returns, and a line that was still waiting to be
                // written is a sync that never happened as far as the report
                // is concerned.
                prefs.edit(commit = true) { putString(KEY, SyncHistory.encode(next)) }
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * One sync. [end] is zero while it is still running.
 *
 * [phone] is the phone as the run began; [phoneAtEnd] as it finished, empty
 * until then. Two readings rather than one because the interesting runs are
 * the ones where something changed in between.
 */
data class SyncEntry(
    val start: Long,
    val end: Long,
    val origin: String,
    val outcome: String,
    val phone: String,
    val phoneAtEnd: String = "",
)

/**
 * The record's pure half: how entries are kept, trimmed, finished and stored.
 * No Android in here, so it can be tested as ordinary code.
 */
internal object SyncHistory {
    // Thirty since the full-text runs joined the syncs here.
    const val MAX_ENTRIES = 30
    private const val FIELD = '\t'

    /** Newest first, and never more than [MAX_ENTRIES]. */
    fun start(entries: List<SyncEntry>, entry: SyncEntry): List<SyncEntry> =
        (listOf(entry) + entries).take(MAX_ENTRIES)

    /**
     * Fills in the end of the run that started at [id]. A run that has
     * already been trimmed away is simply not found; nothing is invented.
     */
    fun finish(
        entries: List<SyncEntry>,
        id: Long,
        end: Long,
        outcome: String,
        phoneAtEnd: String = "",
    ): List<SyncEntry> = entries.map {
        if (it.start == id && it.end == 0L) it.copy(end = end, outcome = outcome, phoneAtEnd = phoneAtEnd) else it
    }

    fun encode(entries: List<SyncEntry>): String = entries.joinToString("\n") {
        listOf(it.start, it.end, clean(it.origin), clean(it.outcome), clean(it.phone), clean(it.phoneAtEnd))
            .joinToString(FIELD.toString())
    }

    fun decode(text: String): List<SyncEntry> = text.lineSequence()
        .mapNotNull { line ->
            val f = line.split(FIELD)
            // Five fields is a line written before the end reading existed.
            if (f.size != 5 && f.size != 6) return@mapNotNull null
            val start = f[0].toLongOrNull() ?: return@mapNotNull null
            val end = f[1].toLongOrNull() ?: return@mapNotNull null
            SyncEntry(start, end, f[2], f[3], f[4], f.getOrElse(5) { "" })
        }
        .toList()

    private fun clean(value: String) = value.replace(FIELD, ' ').replace('\n', ' ')
}
