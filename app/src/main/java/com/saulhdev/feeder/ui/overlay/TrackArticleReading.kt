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
package com.saulhdev.feeder.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** How often the reading clock advances. A second is finer than any band needs. */
private const val READ_TICK_MS = 1_000L

/**
 * How much time may go unwritten before it is flushed to the database.
 *
 * The trade is between writes and loss. Everything since the last flush is
 * lost if the process is killed, and five seconds of a read is nothing;
 * flushing every tick would write once a second for as long as somebody reads.
 */
private const val FLUSH_EVERY_MS = 5_000L

/**
 * Times how long an article is actually read for.
 *
 * ## Why this counts forward instead of subtracting
 *
 * The obvious implementation is to note the time on the way in, note it again
 * on the way out, and subtract. It is also the one that cannot be made safe,
 * because it assumes there is a way out. There is not: the reader can press
 * home, lock the phone, get a call, or have the process killed by the system
 * with the article still open. Every one of those leaves a start time and no
 * end, and whatever runs next — the next launch, a resume three days later —
 * finds it and computes a reading time of three days.
 *
 * Clamping that afterwards treats the symptom. Counting forward removes the
 * illness: this accumulates elapsed milliseconds in bounded steps, each of
 * which only happens while somebody is actually looking at the article, and
 * writes them as increments. There is no start timestamp anywhere for a later
 * resume to find, so a reading time longer than the reading is not a case that
 * has been handled — it is one that cannot be expressed.
 *
 * ## What stops the clock
 *
 * [repeatOnLifecycle] at RESUMED. The loop is cancelled when the owner leaves
 * the resumed state and restarted when it comes back, so the screen going off,
 * the app being backgrounded, a phone call, the notification shade, and
 * navigating away all stop it within a tick. Time spent anywhere but in front
 * of this article is never counted, rather than counted and then removed.
 *
 * Note that this is the Activity's lifecycle, which is genuine here. The feed
 * tracker needs [LocalFeedVisible] as well, because the launcher overlay holds
 * RESUMED for its whole existence; the article opens in a real Activity, which
 * pauses when it should.
 *
 * ## What the cap is still for
 *
 * A phone left unlocked on a desk with an article open is resumed and is not
 * being read, and no lifecycle callback will ever say so. `READ_CAP_MS` in the
 * repository bounds the total for one article, which handles that case without
 * the clock having to guess at what an idle reader looks like — a guess that
 * would punish slow readers and long articles, which are exactly the reading
 * worth counting.
 *
 * ## What this one does not cover
 *
 * An article opened in an external browser, where Whisper is not on screen and
 * has no lifecycle to tick against. That case is measured differently and much
 * more coarsely, by how long the app was away — see [BrowserReadTimer], which
 * has to subtract and therefore carries its own defences. Both write to the
 * same column through the same capped increment, so nothing downstream needs
 * to know which of the two produced a figure.
 *
 * @param articleId the article being read, or null for nothing to time.
 * @param onRead handed each accumulated chunk, in milliseconds, as an amount
 *   to add — never a total.
 */
@Composable
fun TrackArticleReading(
    articleId: String?,
    onRead: (String, Long) -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    // So that a flush on the way out uses the caller as it is now, rather than
    // the one captured when this article was first shown.
    val flush by rememberUpdatedState(onRead)

    // Held outside the effect: repeatOnLifecycle cancels and restarts the
    // block on every pause and resume, and a counter declared inside it would
    // be reset by the screen going off mid-paragraph.
    val pending = remember(articleId) { longArrayOf(0L) }

    LaunchedEffect(articleId, owner) {
        val id = articleId ?: return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(READ_TICK_MS)
                pending[0] += READ_TICK_MS
                if (pending[0] >= FLUSH_EVERY_MS) {
                    flush(id, pending[0])
                    pending[0] = 0L
                }
            }
        }
    }

    // The tail of the read, which is most of a short one. Runs when the
    // article changes or the screen leaves composition — including the back
    // press to the feed, which is the commonest way a read ends.
    DisposableEffect(articleId) {
        onDispose {
            val id = articleId
            val owed = pending[0]
            pending[0] = 0L
            if (id != null && owed > 0L) flush(id, owed)
        }
    }
}
