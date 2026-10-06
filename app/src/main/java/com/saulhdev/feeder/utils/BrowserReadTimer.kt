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

import android.os.SystemClock

/**
 * Longer than this away from Whisper and the trip is not counted at all.
 *
 * Fifteen minutes. Long enough to cover a genuine long-form read, short enough
 * that lunch does not become one.
 *
 * Discarded rather than clamped, which is the opposite of what the in-app
 * clock does with its ceiling, and the difference is not an inconsistency. In
 * the app, a long time resumed means the article really was on screen for it,
 * so clamping keeps a true fact and bounds its influence. Here, a long absence
 * says nothing whatsoever: the reader may have read for twenty minutes, or
 * read for two and then answered messages, taken a call and made coffee.
 * Clamping would turn "we have no idea" into the confident claim that they
 * read for fifteen minutes. Recording nothing claims nothing, which is the
 * only honest thing left to say — the article keeps the plain opened band it
 * already earned.
 */
const val BROWSER_READ_MAX_MS = 15L * 60 * 1000

/**
 * Times an article read in an external browser, by how long Whisper was away.
 *
 * The in-app clock cannot help here. Opening an article in the browser means
 * Whisper is not on screen, has no lifecycle to tick against, and could only
 * measure its own absence — so for a reader who prefers their own browser, and
 * many do, every article scored the same whether they read it or bounced.
 *
 * ## This one has to subtract, so the safety lives elsewhere
 *
 * There is no way to accumulate forward while backgrounded, so this does the
 * thing the in-app clock refuses to do: it notes the moment of leaving and
 * takes a difference on the way back. Three rules keep that from becoming the
 * three-day read.
 *
 * **Nothing is persisted.** The pending trip lives in memory and nowhere else.
 * If the process is killed while the reader is in the browser — which is
 * exactly what Android does to a backgrounded app under pressure — the trip is
 * forgotten rather than resumed, and the next launch finds nothing to subtract
 * from. A start time written to disk is the whole bug; there is nowhere here
 * for one to be written.
 *
 * **Anything over [BROWSER_READ_MAX_MS] is discarded, not clamped.** See that
 * constant: a long absence is not evidence of a long read, and rounding it
 * down to the limit would assert something nobody knows.
 *
 * **The clock is [SystemClock.elapsedRealtime], not the wall clock.** It counts
 * while the device sleeps, which is what "how long were they gone" means, and
 * it cannot be moved by the user, the network or a timezone change. A wall
 * clock nudged backwards mid-trip would otherwise produce a negative duration,
 * and nudged forwards, an enormous one.
 *
 * ## What it still cannot know
 *
 * Whether the reader was reading. Fifteen minutes away with the article open
 * and fifteen minutes away having wandered off to something else are the same
 * observation from here, and no amount of care changes that. This measurement
 * is weaker than the in-app one by nature and is worth having anyway, because
 * the alternative for a browser reader is no signal at all.
 */
object BrowserReadTimer {

    private var pendingId: String? = null
    private var leftAt: Long = 0L

    /** Notes that an article is being opened outside the app. */
    @Synchronized
    fun left(articleId: String, now: Long = SystemClock.elapsedRealtime()) {
        // A second departure replaces the first rather than queueing. Two
        // trips cannot overlap — there is one screen — so an unsettled trip at
        // this point is one that never came back through a hook, and guessing
        // at its length now would be the worst of both approaches.
        pendingId = articleId
        leftAt = now
    }

    /**
     * Closes off a trip, returning what to record, or null for nothing.
     *
     * Null covers every uncertain case: no trip outstanding, a trip longer
     * than the limit, and a duration that came back negative because something
     * moved the clock underneath us.
     *
     * Clears the pending trip either way, so the two callers that both fire on
     * a return — an Activity resuming and the launcher panel reopening —
     * cannot record one read twice.
     */
    @Synchronized
    fun settle(now: Long = SystemClock.elapsedRealtime()): Pair<String, Long>? {
        val id = pendingId ?: return null
        val elapsed = now - leftAt
        pendingId = null
        leftAt = 0L
        if (elapsed <= 0L || elapsed > BROWSER_READ_MAX_MS) return null
        return id to elapsed
    }

    /** Drops any outstanding trip without recording it. */
    @Synchronized
    fun forget() {
        pendingId = null
        leftAt = 0L
    }
}
