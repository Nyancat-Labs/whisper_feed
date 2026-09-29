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

import com.saulhdev.feeder.manager.sync.greader.AccountTally
import com.saulhdev.feeder.manager.sync.greader.accountSummary

/**
 * What one sync did: how many feeds were due, how many of those failed, and
 * the error that stopped the whole run, if one did.
 *
 * This was a Boolean, and false meant two unrelated things - the run broke,
 * or no feed was due because every one had been fetched minutes earlier. The
 * history then called a panel sync two minutes after a scheduled one
 * "failed", 0s, when it had simply had nothing to do.
 */
data class SyncResult(
    val due: Int,
    val failed: Int = 0,
    /** The kind of error that ended the run - a class name, never a message. */
    val error: String? = null,
    /**
     * False for an account sync, which says only whether it worked. Its line
     * then reads plain "ok" rather than a feed count made up to fill it.
     */
    val counted: Boolean = true,
    /** Due feeds whose server said nothing had changed (a 304, no body). */
    val unchanged: Int = 0,
    /**
     * Due feeds that sent the whole feed, identical to last time: downloaded,
     * but not read again. See FeedDigest.
     */
    val identical: Int = 0,
    /**
     * What Whisper received over the network while the run lasted, or null
     * where Android does not say. Everything the app received, not the feeds
     * alone - images loading on screen at the same moment count too - which
     * is why it is labelled as data, not as feed size.
     */
    val bytes: Long? = null,
    /**
     * Due feeds that could not be reached because Whisper had no network -
     * not failures of the feeds, and not counted against them.
     */
    val offline: Int = 0,
    /** Feeds left for a later sync because they rarely publish; see dueByPace. */
    val resting: Int = 0,
    /** What an account sync did with the server; see AccountTally. */
    val account: AccountTally? = null,
    /** The feeds that took longest, title to milliseconds; see slowestOf. */
    val slowest: List<Pair<String, Long>> = emptyList(),
) {
    /** Not broken. Nothing due is fine; some feeds failing is still a sync. */
    val ok: Boolean get() = error == null

    companion object {
        fun broken(error: Throwable): SyncResult = SyncResult(due = 0, error = errorKind(error))

        /** An account sync that worked; see [counted]. */
        val uncounted = SyncResult(due = 0, counted = false)
    }
}

/**
 * The name of what went wrong, safe for a report that is sent to the
 * developer: the class, with no message. A message can carry an address.
 * A refused download says why in words, since a release build shortens
 * class names to three letters.
 */
fun errorKind(error: Throwable): String =
    (error as? DownloadRefused)?.kind ?: error::class.java.simpleName.ifBlank { "error" }

/**
 * The history's outcome for a finished run.
 *
 * "ok" and "failed" still lead, so the lines read as they did; what follows
 * in brackets is what the Boolean could not say. [notes] are the worker's own
 * ("queued", "foreground"), kept at the end.
 */
fun syncOutcome(result: SyncResult, notes: List<String> = emptyList()): String {
    val (head, details) = when {
        result.error != null -> "failed: ${result.error}" to emptyList()
        !result.counted -> "ok" to emptyList()
        result.due == 0 -> "nothing due" to emptyList()
        else -> "ok" to listOfNotNull(
            if (result.due == 1) "1 feed" else "${result.due} feeds",
            if (result.unchanged > 0) "${result.unchanged} unchanged" else null,
            if (result.identical > 0) "${result.identical} identical" else null,
            if (result.failed > 0) "${result.failed} failed" else null,
            if (result.offline > 0) "${result.offline} without network" else null,
        )
    }
    val data = result.bytes?.let(::formatBytes)
    val resting = if (result.resting > 0 && result.error == null) "${result.resting} not due yet" else null
    val slow = result.slowest.takeIf { it.isNotEmpty() && result.error == null }
        ?.joinToString(", ", prefix = "slowest ") { (title, ms) -> "${title.take(SLOW_TITLE)} ${seconds(ms)}" }
    val all = details + listOfNotNull(resting, data, slow) + notes
    val line = if (all.isEmpty()) head else "$head (${all.joinToString(", ")})"
    return result.account?.takeIf { result.error == null }?.let { "$line; ${accountSummary(it)}" } ?: line
}

/** How much of a feed's title the slowest list shows. */
private const val SLOW_TITLE = 24

/** How many of the slowest feeds the history names. */
private const val SLOWEST_SHOWN = 3

/** Below this a feed was not slow, whatever else was slower. */
private const val SLOW_FROM_MS = 3_000L

/**
 * The few feeds a sync waited longest for, slowest first, and only those
 * that took [SLOW_FROM_MS] or more: a sync that took thirty seconds has no
 * culprit worth naming.
 */
fun slowestOf(times: Collection<Pair<String, Long>>): List<Pair<String, Long>> =
    times.filter { it.second >= SLOW_FROM_MS }.sortedByDescending { it.second }.take(SLOWEST_SHOWN)

/** Whole seconds, or tenths below ten: "0.4s", "7.2s", "45s". */
fun seconds(ms: Long): String =
    if (ms < 10_000) String.format(java.util.Locale.US, "%.1fs", ms / 1000.0) else "${ms / 1000}s"

/**
 * A byte count the way Android's own data screen writes it: thousands, not
 * 1024s, so a figure here can be set beside the one in Settings.
 */
fun formatBytes(bytes: Long): String = when {
    bytes < 1_000 -> "<1 KB"
    bytes < 1_000_000 -> "${bytes / 1_000} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
}

/** The history's outcome for a full-article prefetch run. */
fun fullTextOutcome(fetched: Int, failed: Int, bytes: Long?, unreached: Int = 0): String =
    "ok (" + listOfNotNull(
        "$fetched fetched",
        if (failed > 0) "$failed failed" else null,
        if (unreached > 0) "$unreached left for later, no network" else null,
        bytes?.let(::formatBytes),
    ).joinToString(", ") + ")"
