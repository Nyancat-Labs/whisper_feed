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
package com.saulhdev.feeder.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collects dwell increments and hands them over in batches.
 *
 * The tracker reports one article at a time, as each leaves the screen, which
 * during a scroll is a steady stream. Every one of those used to be its own
 * UPDATE on the Article table — and Room invalidates per table, so each write
 * re-ran the feed query: five hundred whole article rows including their full
 * text, rebuilt, and then mostly discarded by the 300ms debounce downstream. A
 * debounce drops the list *after* it has been built, so the cost was paid
 * every time and used almost never.
 *
 * A diagnostics report measured what that came to: 100–200MB collected per
 * cycle against a 256MB heap, a cycle every six to eight seconds for the
 * length of the scroll, with threads blocking on allocation in between.
 *
 * Batching does not make the writes cheaper; it makes them fewer. One
 * transaction is one invalidation however many rows it carries.
 *
 * Its own class rather than three fields on the repository because the parts
 * that can go wrong are all timing: an increment arriving while a flush is in
 * progress, a window that keeps being pushed back and so never fires, a flush
 * on the way out that races the one on the timer. None of that is visible by
 * reading it, and none of it needs a database to test.
 */
internal class DwellBatch(
    private val scope: CoroutineScope,
    private val windowMs: Long,
    /** Told what to write, once per flush, with the totals owed per article. */
    private val write: suspend (Map<String, Long>) -> Unit,
) {
    private val pending = HashMap<String, Long>()
    private val lock = Mutex()
    private var timer: Job? = null

    /**
     * Records time owed, and makes sure a flush is coming.
     *
     * The window is started by whichever increment finds none running and is
     * deliberately *not* restarted by the ones after it. A window that reset
     * on every call would never expire while the reader kept scrolling —
     * which is precisely the case it exists for — and would turn a bounded
     * delay into an unbounded one.
     */
    suspend fun add(id: String, millis: Long) {
        if (millis <= 0L) return
        val needsTimer = lock.withLock {
            pending[id] = (pending[id] ?: 0L) + millis
            timer?.isActive != true
        }
        if (needsTimer) {
            timer = scope.launch {
                delay(windowMs)
                flush()
            }
        }
    }

    /**
     * Writes what is owed, now.
     *
     * Drained under the lock and before the write, so an increment arriving
     * mid-write joins the next batch instead of being erased by a clear that
     * happens after it lands. The reverse order loses exactly the articles the
     * reader was looking at.
     *
     * Returns the number of articles written, which is what the trace counts;
     * zero when there was nothing owed.
     */
    suspend fun flush(): Int {
        val batch = lock.withLock {
            if (pending.isEmpty()) return 0
            val copy = HashMap(pending)
            pending.clear()
            // Released here, with the drain, and not after the write. When the
            // timer is the one flushing, its own job is still active for the
            // whole of that write — so an increment arriving mid-write saw a
            // live timer, scheduled nothing, and waited for a flush that was
            // already past it. During a scroll the next increment covers for
            // it; at the end of one, those milliseconds simply never land.
            //
            // A stale timer left counting down is harmless: it wakes, finds
            // nothing owed and returns. Cancelling it here would be the same
            // line cancelling the coroutine it is running in.
            timer = null
            copy
        }
        write(batch)
        return batch.size
    }
}
