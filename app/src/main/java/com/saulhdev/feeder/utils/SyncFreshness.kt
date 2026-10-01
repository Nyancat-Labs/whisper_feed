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

private const val HOUR_MS = 60 * 60_000L

/** Never call a sync late sooner than this, whatever the interval. */
const val MIN_OVERDUE_MS = 24 * HOUR_MS

/** What the line above the feed says about how current it is. */
sealed interface SyncFreshness {
    /** A sync is running now. */
    data object Updating : SyncFreshness

    /** There are sources, and not one of them has ever been fetched. */
    data object NeverUpdated : SyncFreshness

    /**
     * The newest successful fetch, and whether that is later than the
     * schedule can explain.
     */
    data class Updated(val age: ArticleAge, val overdue: Boolean) : SyncFreshness
}

/**
 * How long without a successful sync before it counts as stuck.
 *
 * Three missed intervals, and never under a day. A schedule that waits for a
 * charger or Wi-Fi can go a working day without either and be doing exactly
 * what it was told; three intervals alone would call a 30-minute schedule
 * stuck after an hour and a half on the bus.
 *
 * [frequencyHours] is the stored setting ("0.5", "1", "6"); anything
 * unreadable falls back to the hourly default rather than to "never late".
 */
fun syncOverdueAfterMs(frequencyHours: String): Long {
    val hours = frequencyHours.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
    return maxOf(MIN_OVERDUE_MS, (3 * hours * HOUR_MS).toLong())
}

/**
 * The freshness line for the feed, or null when there is nothing to say.
 *
 * [lastSyncMs] is the newest `lastSync` among enabled sources: null when
 * there are none (nothing to be fresh or stale about, so no line), zero or
 * less when none has ever been fetched. It moves only on a successful fetch,
 * so a sync that ran and failed everywhere leaves the old age showing, which
 * is the truth about what is on screen.
 */
fun syncFreshness(
    nowMs: Long,
    lastSyncMs: Long?,
    syncing: Boolean,
    overdueAfterMs: Long,
): SyncFreshness? = when {
    lastSyncMs == null -> null
    syncing -> SyncFreshness.Updating
    lastSyncMs <= 0L -> SyncFreshness.NeverUpdated
    else -> SyncFreshness.Updated(
        age = articleAge(nowMs, lastSyncMs),
        overdue = nowMs - lastSyncMs > overdueAfterMs,
    )
}

/** Milliseconds until the minute rolls over, so the line changes on time. */
/**
 * Whether opening the app should sync.
 *
 * When the feed is older than one scheduled interval, and never more often
 * than every quarter of an hour. With background data off, the schedule
 * never gets to run on mobile data, and an afternoon went by with the app
 * opened several times and nothing fetched since lunchtime. Opening the app
 * is the moment Android allows it.
 *
 * Not when syncing is set to manual ("0"), and not with no sources to sync
 * ([newestSyncMs] null).
 */
fun openedSyncDue(nowMs: Long, newestSyncMs: Long?, frequencyHours: String): Boolean {
    val hours = frequencyHours.toDoubleOrNull()?.takeIf { it > 0 } ?: return false
    val newest = newestSyncMs ?: return false
    val interval = maxOf(OPENED_SYNC_MIN_MS, (hours * HOUR_MS).toLong())
    return nowMs - newest >= interval
}

const val OPENED_SYNC_MIN_MS = 15 * 60_000L

/**
 * Whether an automatic sync should give way to blocked background data.
 *
 * Only when Whisper is not on screen. The block is Android's, and it is
 * lifted while Whisper is in the foreground; skipping then as well is how
 * six syncs in one afternoon were skipped while Whisper was open.
 */
fun skipForBlockedData(automatic: Boolean, blocked: Boolean, onScreen: Boolean): Boolean =
    automatic && blocked && !onScreen

fun msUntilNextMinute(nowMs: Long): Long = 60_000L - Math.floorMod(nowMs, 60_000L)

/**
 * How long a background sync that Android cut off holds back the next
 * automatic one that would start in the background.
 */
const val CUT_OFF_HOLD_MS = 30 * 60_000L

/**
 * The ends that mean Android took the network or the run away from Whisper,
 * as the history writes them; see stopReasonName.
 */
val CUT_OFF_OUTCOMES = setOf(
    "stopped: network changed or dropped",
    "stopped: device state changed",
)

/**
 * Whether an automatic sync should wait for its next slot because the last
 * automatic one was cut off a moment ago.
 *
 * WorkManager starts a stopped sync again as soon as it can, and one evening
 * that was every two or three minutes: seven runs between 20:38 and 20:50,
 * each fetching the feeds again from the top and each cut off as Whisper left
 * the screen, the network "blocked for Whisper" on Wi-Fi. Each one cost
 * battery and none got as far as the account. Skipped as success, the
 * schedule moves on to its next slot.
 *
 * Never while Whisper is on screen, when Android leaves it the network, and
 * never for a sync the reader asked for. The run looked at is the newest
 * automatic one that finished and was not itself skipped, so the hold lasts
 * [CUT_OFF_HOLD_MS] from the cut-off however many skips follow. [current] is
 * this run's own entry, still running.
 */
fun skipAfterCutOff(
    automatic: Boolean,
    onScreen: Boolean,
    entries: List<SyncEntry>,
    nowMs: Long,
    current: Long,
): Boolean {
    if (!automatic || onScreen) return false
    val last = entries.firstOrNull {
        it.start != current && it.end > 0L &&
            it.origin in SyncLog.AUTOMATIC_ORIGINS && !it.outcome.startsWith("skipped")
    } ?: return false
    return last.outcome in CUT_OFF_OUTCOMES && nowMs - last.end in 0..CUT_OFF_HOLD_MS
}

/**
 * One scheduled interval, from the sync frequency setting in hours. An hour
 * when syncing is manual ("0"), for the panel's and the app's own syncs.
 */
fun syncIntervalMs(frequencyHours: String): Long =
    frequencyHours.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * HOUR_MS).toLong() } ?: HOUR_MS

/**
 * Where an automatic sync picks up after syncs Android cut off: the start of
 * the unbroken run of cut-off automatic syncs just before it. A feed fetched
 * since then is not fetched again.
 *
 * A cut-off sync starts again from the top. Each skipped only what had been
 * fetched in the last five minutes, and restarts came three to six minutes
 * apart, so one evening downloaded the same feeds over and over: Science
 * Magazine at 20:38, 20:44 and 20:50, identical each time.
 *
 * Every stop counts, whatever the reason: each left feeds unfetched. Skips
 * are passed over, since they fetched nothing and ended nothing. Null when
 * the last automatic sync finished, or when the cut-off ones are more than
 * [intervalMs] old - by then everything is due anyway. Never reaching back
 * further than [intervalMs], so no feed goes longer than one interval.
 * The sync asking is still running, and is not counted.
 */
fun resumeFrom(entries: List<SyncEntry>, nowMs: Long, intervalMs: Long): Long? {
    val chain = entries.asSequence()
        .filter { it.end > 0L && it.origin in SyncLog.AUTOMATIC_ORIGINS && !it.outcome.startsWith("skipped") }
        .takeWhile { it.outcome.startsWith("stopped:") }
        .toList()
    if (chain.isEmpty() || nowMs - chain.first().end > intervalMs) return null
    return maxOf(chain.last().start, nowMs - intervalMs)
}
