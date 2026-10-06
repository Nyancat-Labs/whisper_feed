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

/** A feed is never left longer than this between checks, however slow it is. */
const val MAX_RECHECK_MS = 6 * 60 * 60_000L

/**
 * How long after a successful fetch a feed is worth asking again.
 *
 * A quarter of its usual gap between articles: a feed that posts every two
 * hours is asked every thirty minutes, one that posts daily every six hours.
 * Most feeds cannot say "nothing new" cheaply - two thirds of a 120-feed list
 * answered every sync with the whole feed - so asking a daily blog every
 * half hour cost a full download 47 times a day to find one article.
 *
 * [paceHours] is null when there are too few articles to judge, and then the
 * feed is asked every time, as before: not knowing is no reason to wait.
 */
fun recheckAfterMs(paceHours: Float?): Long {
    val pace = paceHours ?: return 0L
    return (pace * 3_600_000f / 4f).toLong().coerceIn(0L, MAX_RECHECK_MS)
}

/**
 * Whether an automatic sync should fetch this feed now.
 *
 * Last fetched long enough ago for its pace - which a feed never fetched
 * always is, its last fetch being the epoch. A pull to refresh does not ask:
 * it means "everything, now".
 */
fun dueByPace(nowMs: Long, lastSyncMs: Long, paceHours: Float?): Boolean =
    nowMs - lastSyncMs >= recheckAfterMs(paceHours)
