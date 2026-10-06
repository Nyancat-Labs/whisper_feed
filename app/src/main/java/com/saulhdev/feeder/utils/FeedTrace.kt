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

import android.util.Log
import coil.decode.DataSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val FEED_TAG = "FeedTrace"

/**
 * Counts what the feed pipeline does, so a scroll can be measured rather than
 * reasoned about.
 *
 * A diagnostics report showed the collector freeing 100–200MB per cycle on a
 * 256MB heap, every six to eight seconds, throughout a scroll. Reading the
 * source produced a plausible account — the feed query returns five hundred
 * whole article rows including their full text, any write to the Article table
 * re-runs it, and the dwell tracker writes a row every time an article leaves
 * the screen — but plausible is where the last three days' worth of wrong
 * guesses also started. The crash in this app's history was found by a stack
 * trace, and the card padding by measuring a screenshot; neither was found by
 * reading.
 *
 * So the pipeline counts itself. Emissions, the rows behind them, how long the
 * processing took, and how many dwell writes each flush collapsed — enough to
 * say whether the account above is right and, afterwards, whether the change
 * made from it actually worked.
 *
 * **Counts and durations only, never content.** These lines go into a file the
 * reader sends to a stranger. What was on screen, what they searched for and
 * what they subscribe to are all absent by construction: there is nowhere in
 * this file to put them.
 *
 * Summarised on a cadence rather than logged per event. The report carries the
 * last 2000 lines of logcat, and a line per emission during a fast scroll
 * would push everything else — the GC lines this exists to explain — out of
 * the window before it was read.
 *
 * Nothing is written unless the reader has turned Debugging on; every counter
 * here is an atomic add on a path that already allocates, and the reporter
 * only runs while tracing.
 */
object FeedTrace {

    /**
     * Table-change signals: how often Room said Article or Feeds had changed.
     *
     * Counted apart from the loads below because the gap between them is the
     * measurement. A signal costs nothing to discard; a load rebuilds five
     * hundred whole article rows. Before the debounce was moved onto the
     * signal the two numbers were the same, and that was the bug.
     */
    private val invalidations = AtomicInteger()

    /** Feed query emissions: how often the expensive query actually ran. */
    private val emissions = AtomicInteger()

    /** Rows across those emissions, for the cost each one carries. */
    private val rows = AtomicLong()

    /** Emissions that survived the debounce and were actually processed. */
    private val processed = AtomicInteger()

    /** Wall time inside processArticles, in microseconds. */
    private val processMicros = AtomicLong()

    /** Dwell flushes, and the writes each one stood in for. */
    private val flushes = AtomicInteger()
    private val flushedRows = AtomicInteger()

    /** Dwell increments asked for, whether or not they have been written yet. */
    private val dwellAsks = AtomicInteger()

    /**
     * Decodes per image format, and the microseconds they took.
     *
     * Per format rather than in total because the question is comparative:
     * AVIF goes through a software AV1 codec on this device and JPEG does not,
     * and "images are slow" is not an answer anyone can act on. A map because
     * the formats a feed serves are whatever its publishers chose.
     */
    private val decodeCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val decodeMicros = ConcurrentHashMap<String, AtomicLong>()

    /** Images served, by where they came from: memory, disk or network. */
    private val served = ConcurrentHashMap<String, AtomicInteger>()

    /** Images that never arrived, by why. */
    private val failures = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Frames drawn, the ones slow enough to see, and the slowest of them.
     *
     * The one number here that is what the reader experiences rather than a
     * cause of it; see [FrameWatch].
     */
    private val frames = AtomicInteger()
    private val slowFrames = AtomicInteger()
    private val worstFrameNs = AtomicLong()

    fun frame(durationNs: Long, intervalNs: Long) {
        frames.incrementAndGet()
        if (isSlowFrame(durationNs, intervalNs)) {
            slowFrames.incrementAndGet()
            worstFrameNs.accumulateAndGet(durationNs, ::maxOf)
        }
    }

    fun invalidated() {
        invalidations.incrementAndGet()
    }

    fun emission(count: Int) {
        emissions.incrementAndGet()
        rows.addAndGet(count.toLong())
    }

    fun processed(micros: Long) {
        processed.incrementAndGet()
        processMicros.addAndGet(micros)
    }

    fun dwellAsked() {
        dwellAsks.incrementAndGet()
    }

    fun dwellFlushed(rowCount: Int) {
        flushes.incrementAndGet()
        flushedRows.addAndGet(rowCount)
    }

    fun imageDecoded(format: String, micros: Long) {
        decodeCounts.getOrPut(format) { AtomicInteger() }.incrementAndGet()
        decodeMicros.getOrPut(format) { AtomicLong() }.addAndGet(micros)
    }

    fun imageFailed(reason: String, surface: String = OTHER_SURFACE) {
        failures.getOrPut(reason) { AtomicInteger() }.incrementAndGet()
        attemptsSinceStart.incrementAndGet()
        failuresSinceStart.getOrPut("$surface: $reason") { AtomicInteger() }.incrementAndGet()
    }

    fun imageServed(source: DataSource) {
        served.getOrPut(source.shortName()) { AtomicInteger() }.incrementAndGet()
        attemptsSinceStart.incrementAndGet()
    }

    /**
     * Every image failure since the process started, and how many were asked
     * for altogether.
     *
     * The counters above are drained every five seconds, and the report
     * carries the last two thousand log lines — so a failure reaches the
     * developer only if it happened in roughly the last window before the
     * reader pressed the button. "I thought some images were going missing but
     * I wasn't able to catch it" is that gap described from the outside, and
     * catching it was never the reader's job: the app was there when it
     * happened and can simply keep count.
     *
     * Not drained by [reset], which clears the window. A tally that started
     * again whenever tracing was switched on would be the window under another
     * name, and these are counted whether tracing is on or not.
     */
    private val failuresSinceStart = ConcurrentHashMap<String, AtomicInteger>()

    /** Images that reached a conclusion, either way. The denominator. */
    private val attemptsSinceStart = AtomicInteger()

    /**
     * The tally, as a line for the diagnostics file.
     *
     * With the total beside it, always. Nine failures is a broken feature or a
     * rounding error depending on whether thirty pictures were drawn or three
     * thousand, and a bare count invites the wrong one of those.
     */
    fun imageTally(): String {
        val attempts = attemptsSinceStart.get()
        if (attempts == 0) return "no images requested yet"
        val failed = failuresSinceStart.mapValues { it.value.get() }.filterValues { it > 0 }
        val total = failed.values.sum()
        if (total == 0) return "%d requested, none failed".format(attempts)
        return buildString {
            append(
                "%d requested, %d failed (%.1f%%)".format(
                    attempts, total, 100.0 * total / attempts,
                )
            )
            failed.entries.sortedByDescending { it.value }.forEach {
                append("\n  %-5d %s".format(it.value, it.key))
            }
        }
    }

    /**
     * The image half of a window, or null when no picture was drawn.
     *
     * Formats are ordered by how long they cost in total rather than
     * alphabetically or by count: the one to look at first is the one taking
     * the time, which is not always the one appearing most.
     */
    private fun drainImages(): String? {
        val counts = decodeCounts.mapValues { it.value.getAndSet(0) }.filterValues { it > 0 }
        val micros = decodeMicros.mapValues { it.value.getAndSet(0) }
        val sources = served.mapValues { it.value.getAndSet(0) }.filterValues { it > 0 }
        val failed = failures.mapValues { it.value.getAndSet(0) }.filterValues { it > 0 }
        if (counts.isEmpty() && sources.isEmpty() && failed.isEmpty()) return null

        return buildString {
            val total = sources.values.sum()
            append("images %d".format(total))
            if (sources.isNotEmpty()) {
                append(sources.entries.sortedBy { it.key }
                    .joinToString(", ", " (", ")") { "${it.key} ${it.value}" })
            }
            if (counts.isNotEmpty()) {
                append(", decoded %d: ".format(counts.values.sum()))
                append(counts.entries
                    .sortedByDescending { micros[it.key] ?: 0L }
                    .joinToString(", ") { (format, n) ->
                        val avg = (micros[format] ?: 0L) / n / 1000.0
                        "%s %d avg %.1fms".format(format, n, avg)
                    })
            }
            if (failed.isNotEmpty()) {
                append(", failed %d: ".format(failed.values.sum()))
                append(failed.entries.sortedByDescending { it.value }
                    .joinToString(", ") { "${it.key} ${it.value}" })
            }
        }
    }

    /**
     * Empties the counters into one line, or returns null if nothing happened.
     *
     * Read-and-reset rather than read: each line is the window since the last
     * one, so two lines can be compared without subtracting them, and a quiet
     * window produces no line at all rather than a repeat of the last figures.
     */
    private fun drain(windowMs: Long): String? {
        val inv = invalidations.getAndSet(0)
        val e = emissions.getAndSet(0)
        val r = rows.getAndSet(0)
        val p = processed.getAndSet(0)
        val micros = processMicros.getAndSet(0)
        val f = flushes.getAndSet(0)
        val fr = flushedRows.getAndSet(0)
        val asks = dwellAsks.getAndSet(0)
        val images = drainImages()
        val drawn = frames.getAndSet(0)
        val slow = slowFrames.getAndSet(0)
        val worst = worstFrameNs.getAndSet(0)
        if (inv == 0 && e == 0 && p == 0 && f == 0 && asks == 0 && images == null && drawn == 0) return null

        val seconds = windowMs / 1000.0
        val perSecond = if (seconds > 0) e / seconds else 0.0
        // Rows per emission rather than total: the total is the product and
        // says less. Five hundred rows an emission is the whole point — it is
        // what makes an emission expensive.
        val perEmission = if (e > 0) r / e else 0L
        val avgMs = if (p > 0) micros / p / 1000.0 else 0.0

        return buildString {
            append("changes %d (%.1f/s)".format(inv, inv / seconds))
            append(" -> loads %d (%.1f/s, %d rows each)".format(e, perSecond, perEmission))
            append(", processed %d avg %.1fms".format(p, avgMs))
            append(", dwell %d asks -> %d writes".format(asks, f))
            if (f > 0) append(" (%d rows)".format(fr))
            if (images != null) append(", ").append(images)
            if (drawn > 0) append(", ").append(frameSummary(drawn, slow, worst))
        }
    }

    /**
     * One summary line per window, for as long as tracing is on.
     *
     * `Log.println` rather than `Log.d`, for the reason
     * [com.saulhdev.feeder.ui.overlay.gateLog] gives: proguard-rules.pro strips
     * `Log.d`/`v`/`i`, so a trace written with those exists only in the debug
     * build — which is the one build where the question this answers cannot
     * come up.
     */
    fun report(windowMs: Long, cache: CacheState? = null) {
        val line = drain(windowMs) ?: return
        val withCache = if (cache == null) line else "$line, $cache"
        Log.println(Log.DEBUG, FEED_TAG, withCache)
    }

    /**
     * How full the decoded-image cache is, at the moment of the report.
     *
     * Raising the cache from 20% to 35% did not visibly move the hit rate,
     * and the reason could not be told from the outside: either the cache is
     * full and still too small, or it is not full and something else decides
     * what is kept. Those want opposite answers, and the numbers to tell them
     * apart were sitting unread on the loader.
     *
     * A point-in-time reading rather than a rate, because that is what it is —
     * an occupancy, sampled when the line is written.
     */
    data class CacheState(val usedBytes: Int, val maxBytes: Int, val entries: Int) {
        override fun toString(): String {
            val used = usedBytes / 1_048_576.0
            val max = maxBytes / 1_048_576.0
            // Long, because 100 * usedBytes overflows a signed Int once the
            // cache passes 21MB — which is to say for every value this is
            // actually going to be asked about. A full cache reported itself
            // as negative.
            val full = if (maxBytes > 0) 100L * usedBytes / maxBytes else 0L
            return "cache %.0f/%.0fMB (%d%%, %d images)".format(used, max, full, entries)
        }
    }

    /** Throws away anything counted so far, so a window starts clean. */
    fun reset() {
        drain(1L)
    }
}

/** "frames 600, slow 3 (worst 48ms)", or "frames 600, none slow". */
internal fun frameSummary(frames: Int, slow: Int, worstNs: Long): String =
    if (slow == 0) "frames %d, none slow".format(frames)
    else "frames %d, slow %d (worst %dms)".format(frames, slow, worstNs / 1_000_000)

/** How often [FeedTrace] writes a line while Debugging is on. */
const val FEED_TRACE_WINDOW_MS = 5_000L
