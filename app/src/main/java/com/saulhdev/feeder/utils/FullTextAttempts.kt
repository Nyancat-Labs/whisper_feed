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

import java.io.IOException

/** Tries before a full-article prefetch that keeps failing is given up. */
const val MAX_FULL_TEXT_ATTEMPTS = 3

/** How long after a failed prefetch before it is tried again. */
const val FULL_TEXT_RETRY_AFTER_MS = 6 * 60 * 60_000L

/**
 * The record of full-article prefetches that failed for one article.
 *
 * The prefetch runs after every sync and walks every stored article without
 * a full text. One that failed - a paywall answering 401, a page that has
 * gone - had no full text afterwards either, so it was fetched again after
 * the next sync, and the next, for as long as the article was kept: with
 * "Fetch full articles for every feed" on and syncs every half hour, dozens
 * of the same refused pages a day, on mobile data as readily as on Wi-Fi.
 */
data class FullTextAttempts(
    val count: Int,
    val lastMs: Long,
    /** The server said no in a way that will not change: stop asking. */
    val permanent: Boolean,
) {
    fun encode(): String = "$count $lastMs ${if (permanent) 1 else 0}"

    /** One more failure at [nowMs]. */
    fun next(nowMs: Long, permanent: Boolean): FullTextAttempts =
        FullTextAttempts(count + 1, nowMs, this.permanent || permanent)

    companion object {
        val none = FullTextAttempts(0, 0L, false)

        /** Null for anything unreadable, which then counts as never tried. */
        fun decode(text: String): FullTextAttempts? {
            val parts = text.trim().split(' ')
            if (parts.size != 3) return null
            val count = parts[0].toIntOrNull() ?: return null
            val last = parts[1].toLongOrNull() ?: return null
            val permanent = when (parts[2]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            return FullTextAttempts(count, last, permanent)
        }
    }
}

/** Whether a prefetch should be tried now, given what went before. */
fun shouldPrefetchFullText(nowMs: Long, attempts: FullTextAttempts?): Boolean = when {
    attempts == null -> true
    attempts.permanent -> false
    attempts.count >= MAX_FULL_TEXT_ATTEMPTS -> false
    else -> nowMs - attempts.lastMs >= FULL_TEXT_RETRY_AFTER_MS
}

/**
 * A refusal worth believing: the client's side of the 4xx range, less the
 * two that mean "not now" - a timeout and too many requests.
 */
fun isPermanentHttpFailure(code: Int): Boolean = code in 400..499 && code != 408 && code != 429

/** A response that was not a success, with its code kept for the decision above. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")
