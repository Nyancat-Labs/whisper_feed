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

/**
 * What, if anything, is worth a notification.
 *
 * Syncing itself never is — a background sync that works is silent, and the
 * "Updated 12m ago" line under the chips is where anyone looks for it. These
 * are the two cases that line cannot reach, because nobody is looking at the
 * feed while they happen.
 */
enum class SyncProblem { None, SyncStuck, SourcesStuck }

/** Failing feeds before it is worth saying so; one dead feed is not news. */
const val SOURCES_STUCK_BEFORE_NOTIFYING = 3

/** A stuck sync is said again at most this often, while it stays stuck. */
const val RENOTIFY_STUCK_AFTER_MS = 24 * 60 * 60_000L

/**
 * [newestSyncMs] is the newest successful fetch among enabled sources, null
 * when there are none. [stoppedSources] counts enabled sources that have
 * failed [com.saulhdev.feeder.data.repository.FAILURES_BEFORE_BROKEN] times
 * in a row — the ones "Feeds that stopped working" lists.
 *
 * A stuck sync comes first and hides the rest: with the phone offline for a
 * day every feed would be failing, and naming a hundred of them would bury
 * the one thing that is actually wrong.
 *
 * Nothing when no source has ever synced. That is a fresh install or an
 * import still settling, the feed already says "Not updated yet", and a
 * notification about it would arrive before the reader has finished setting
 * up.
 */
fun syncProblem(
    nowMs: Long,
    newestSyncMs: Long?,
    overdueAfterMs: Long,
    stoppedSources: Int,
): SyncProblem = when {
    newestSyncMs == null || newestSyncMs <= 0L -> SyncProblem.None
    nowMs - newestSyncMs > overdueAfterMs -> SyncProblem.SyncStuck
    stoppedSources >= SOURCES_STUCK_BEFORE_NOTIFYING -> SyncProblem.SourcesStuck
    else -> SyncProblem.None
}

/** Whether a stuck sync last notified at [lastNotifiedMs] may be said again. */
fun mayRenotifyStuck(nowMs: Long, lastNotifiedMs: Long): Boolean =
    lastNotifiedMs <= 0L || nowMs - lastNotifiedMs >= RENOTIFY_STUCK_AFTER_MS

/**
 * Whether [stopped] sources are news, given [lastNotifiedCount] were reported.
 *
 * Only a larger count is. A dead feed stays dead until somebody removes it, so
 * a daily reminder about the same three would be a notification to swipe away
 * every morning — the kind that ends with the whole channel switched off, and
 * the stuck-sync one with it.
 */
fun sourcesAreNews(stopped: Int, lastNotifiedCount: Int): Boolean =
    stopped >= SOURCES_STUCK_BEFORE_NOTIFYING && stopped > lastNotifiedCount

/** What a stuck scheduled sync is waiting for, as far as the phone can tell. */
enum class StuckReason { Charger, WiFi, BatterySaver, MoreBattery, Unknown }

/**
 * Asked of the phone's state *now*, not of whatever held the sync back
 * overnight — which is all anyone can see, and usually the same thing.
 *
 * Never empty: [StuckReason.Unknown] when every condition is met, which means
 * Android itself has been putting it off.
 */
fun stuckReasons(
    requiresCharging: Boolean,
    pluggedIn: Boolean,
    wifiOnly: Boolean,
    unmetered: Boolean,
    batterySaver: Boolean,
    batteryLow: Boolean,
): List<StuckReason> = buildList {
    if (requiresCharging && !pluggedIn) add(StuckReason.Charger)
    if (wifiOnly && !unmetered) add(StuckReason.WiFi)
    if (batterySaver) add(StuckReason.BatterySaver)
    if (batteryLow) add(StuckReason.MoreBattery)
    if (isEmpty()) add(StuckReason.Unknown)
}
