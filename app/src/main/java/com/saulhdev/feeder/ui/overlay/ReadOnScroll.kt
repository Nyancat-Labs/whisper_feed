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

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import com.saulhdev.feeder.data.content.asState

/**
 * How often the tracker looks. Dwell is measured in whole seconds, so this is
 * fine-grained enough to be accurate and coarse enough that four wake-ups a
 * second are not spent watching a list nobody is scrolling.
 */
private const val TICK_MS = 500L

/**
 * How much of a card has to be on screen before its clock starts.
 *
 * A card a fifth visible at the bottom edge has not been read, and without
 * this the two items straddling the edges of the viewport accumulate time as
 * fast as the one in the middle.
 */
private const val VISIBLE_ENOUGH = 0.6f

/**
 * Whether the feed is in front of someone right now.
 *
 * The Activity answers this with its own lifecycle, and that is what the
 * tracker uses there. The launcher overlay cannot: its Compose host holds
 * RESUMED from creation until the overlay is destroyed, because the upstream
 * controller exposes no pause, so a panel swiped shut looks exactly like a
 * panel being read. The overlay therefore supplies this itself from the panel
 * state the launcher does report.
 *
 * Defaults to true, which is the right answer anywhere the lifecycle is the
 * whole story.
 */
val LocalFeedVisible = compositionLocalOf { true }

/**
 * Tag for the debug trace, shared with [OverlayView]'s panel-state line so one
 * grep of a diagnostics report shows both halves of the gate in order.
 */
private const val GATE_TAG = "ReadGate"

/**
 * Writes one line of the gate's trace.
 *
 * Deliberately not `Log.d`. Preview and release are both minified, and
 * proguard-rules.pro strips `Log.d`/`v`/`i` outright — so a trace written with
 * it exists only in a debug build, which is the one build where the question
 * this answers cannot come up. `println` is not on that list and logs at the
 * same DEBUG priority.
 *
 * Callers gate this on the Debugging preference; nothing is written unless the
 * reader has turned it on.
 */
internal fun gateLog(message: String) {
    Log.println(Log.DEBUG, GATE_TAG, message)
}

/**
 * Marks an article read once it has been on screen long enough.
 *
 * Time on screen rather than scroll speed. A fling past forty headlines is
 * plainly not reading, but velocity is the wrong way to detect it: it swings
 * inside a single fling, so the same gesture would mark some cards and not
 * others depending on where in the deceleration curve they landed. How long a
 * card was actually visible asks the question directly.
 *
 * The threshold is the reader's to set, because the honest answer is that
 * there is no right number — it depends on how fast someone reads and how they
 * scroll. Off by default: this is a surface people scroll idly and come back
 * to, not an inbox.
 *
 * **Time on screen is the qualifier, not the trigger.** An earlier version
 * marked an article the moment its clock ran out, which meant it faded out
 * while it was being read — the reader watching a card grey out under their
 * eyes, mid-sentence, because they had been looking at it too long. That is
 * the exact opposite of what the setting is for.
 *
 * So the mark happens when the article has been *passed*: it earned its dwell,
 * and then scrolled off the top of the screen. Leaving upwards is the part
 * that matters and is why this counts indices rather than pixels — an article
 * that leaves through the bottom edge was scrolled back away from, not read
 * past, and keeps the dwell it earned for when the reader comes back down.
 *
 * The consequence is deliberate: an article read carefully and left on screen
 * is never marked, and the last article in the list cannot be marked at all,
 * because neither has been passed. Both are correct. Nothing dims while it is
 * being looked at.
 *
 * **The clock only runs while someone is looking.** [LaunchedEffect] is not
 * lifecycle-aware, so until this was gated the tick loop kept running with the
 * phone in a pocket: whatever happened to be on screen when the app was left
 * accrued dwell, crossed the threshold, and was marked read by the next scroll.
 * Time the reader was not present is not time spent reading, so the loop is
 * held below RESUMED and, in the overlay, while the panel is shut — see
 * [LocalFeedVisible] for why one gate cannot do both.
 */
@Composable
fun TrackReading(
    articles: List<FeedItem>,
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    onRead: (FeedItem) -> Unit,
    onDwell: (String, Long) -> Unit,
    /**
     * Called once the last dwell has been handed over and no more is coming.
     *
     * Dwell increments are batched on a timer downstream, which is right while
     * the reader is scrolling and wrong the moment they stop: the reason to
     * hold them is that more are on the way, and here that has just ceased to
     * be true. This says so, rather than leaving the batch to time out in a
     * process the system is now free to kill.
     */
    onLeave: () -> Unit = {},
) {
    val prefs: FeedPreferences = koinInject()
    val setting by prefs.markReadOnScroll.asState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val feedVisible = LocalFeedVisible.current
    // Read from the cache rather than the datastore. This is a diagnostic, and
    // blocking composition on a disk read to decide whether to write a log line
    // would cost more than the line is worth.
    val trace = remember { prefs.debugging.peekOrDefault() }
    // Below a quarter of a second means off: the slider's own bottom stop,
    // and anything shorter would fire during a fling anyway.
    val thresholdMs = remember(setting) { if (setting < 0.25f) 0L else (setting * 1000).toLong() }

    // Survives list changes on purpose: a sync rebuilds the list, and without
    // this every visible article would start its clock again from zero.
    val marked = remember { mutableSetOf<String>() }

    // Looked up by the key the list was given rather than by layout position.
    // The two are not the same and were being treated as such: a sticky held
    // article occupies a slot of its own, and so does the empty one that
    // releases it, so from the release onwards every layout index pointed at
    // the article before the one actually on screen — and any header added
    // above the feed would have shifted the lot.
    //
    // Rebuilt when the list really changes, and read live by the loop below
    // rather than captured by it. See the effect's keys for why.
    val lookup = remember(articles) {
        FeedLookup(
            byId = articles.associateBy { it.id },
            position = articles.withIndex().associate { (i, item) -> item.id to i },
        )
    }
    val currentLookup by rememberUpdatedState(lookup)

    // Deliberately not keyed on `articles`.
    //
    // It was, and the feed hands down a new list instance on every emission —
    // a sync, a read mark, a filter change. Each one cancelled the loop and
    // started another: two maps over every article rebuilt, the dwell clocks
    // of everything on screen thrown away, and the `finally` below flushing
    // partial dwell to the database. A diagnostics report caught it happening
    // thirty-five times in ninety seconds, in bursts of nine within a single
    // second, while somebody was doing nothing but scrolling.
    //
    // None of that work is needed. The list is only ever *read* in here, so
    // the loop now reads whatever the current list is and keeps its clocks
    // across the change — which is what `marked` above already does, and for
    // the same reason.
    LaunchedEffect(thresholdMs, isGrid, lifecycleOwner, feedVisible) {
        if (!feedVisible) {
            if (trace) gateLog("panel not visible — tracker idle")
            return@LaunchedEffect
        }

        val dwell = mutableMapOf<String, Long>()
        // Articles that have earned their dwell and are now only waiting to be
        // scrolled past: deciding whether one left through the top or the
        // bottom is the whole point, and once it is gone from the layout there
        // is nothing left to ask.
        //
        // A set rather than a map of remembered positions. It held the
        // position an article had when it became ready, which was correct only
        // because a change to the list used to restart the whole loop; now
        // that the loop outlives the list, a remembered position is a position
        // in a feed that may no longer exist. The current one is looked up
        // when the question is actually asked.
        val ready = mutableSetOf<String>()

        // The gate. repeatOnLifecycle cancels the loop on pause and starts a
        // fresh one on resume; below DESTROYED it simply parks here, so there
        // is nothing to clean up. Partial dwell is dropped along with the loop,
        // which matches what the feed already does with an article scrolled
        // away before its threshold: a glance is a glance.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (trace) {
                gateLog("running: ${currentLookup.byId.size} articles, threshold ${thresholdMs}ms")
            }
            // A cancelled loop leaves through here, so the stop is logged in
            // the same place as the start rather than inferred from its absence.
            try {
                tick(
                    trace = trace,
                    thresholdMs = thresholdMs,
                    isGrid = isGrid,
                    listState = listState,
                    gridState = gridState,
                    lookup = { currentLookup },
                    dwell = dwell,
                    ready = ready,
                    marked = marked,
                    onRead = onRead,
                    onDwell = onDwell,
                )
            } finally {
                // After tick's own finally, which is where the last increments
                // are handed over: flushing before them would leave exactly
                // the articles the reader was looking at when they left.
                onLeave()
                if (trace) gateLog("stopped: left the feed")
            }
        }
    }
}

/**
 * The tick loop, lifted out of [MarkReadWhileScrolling] only so that starting
 * and stopping it reads as one statement at the call site.
 */
private suspend fun tick(
    trace: Boolean,
    thresholdMs: Long,
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    /** The current feed, read afresh each tick rather than captured once. */
    lookup: () -> FeedLookup,
    dwell: MutableMap<String, Long>,
    ready: MutableSet<String>,
    marked: MutableSet<String>,
    onRead: (FeedItem) -> Unit,
    onDwell: (String, Long) -> Unit,
) {
    // Time on screen, accumulated here and written when an article leaves.
    // Separate from `dwell` deliberately: that one is cleared the moment its
    // threshold is crossed, because it answers "has this been looked at long
    // enough yet". Reusing it would record zero for every article that
    // qualified — which is to say, the ones looked at longest.
    val seen = mutableMapOf<String, Long>()

    // Most runs of this loop end in cancellation: the reader backgrounds the
    // app, or shuts the panel, with articles still on screen. Without the
    // flush in the finally, every article being looked at when they left
    // would contribute nothing at all.
    try {
        while (true) {
            delay(TICK_MS)
            val (byId, position) = lookup()
            val visible = visibleEnoughKeys(isGrid, listState, gridState)
            val onScreen = visibleKeys(isGrid, listState, gridState)
            val ids = HashSet<String>(visible.size)

            visible.forEach { id ->
                val item = byId[id] ?: return@forEach
                ids += id
                seen[id] = (seen[id] ?: 0L) + TICK_MS

                // Everything past here is the mark-read-on-scroll feature,
                // which is off unless the reader turned it on. The line above
                // is the measurement, which is not optional — gating the two
                // together made anybody who left that setting alone invisible
                // to the weighting.
                if (thresholdMs <= 0L) return@forEach
                if (id in marked || id in ready || item.article.readAt != 0L) return@forEach

                val soFar = (dwell[id] ?: 0L) + TICK_MS
                dwell[id] = soFar
                if (soFar >= thresholdMs) {
                    // Not marked yet. It has been looked at long enough to
                    // count, and now has to be left behind before it counts.
                    dwell -= id
                    ready += id
                }
            }

            // Written when an article leaves the screen rather than on every
            // tick: one write per article per visit, instead of two a second
            // for everything visible.
            (seen.keys - ids).toList().forEach { id ->
                seen.remove(id)?.let { onDwell(id, it) }
            }

            // Anything above everything still on screen has been passed.
            // Non-article keys — a sticky header, the glance row, the chips —
            // simply have no position and drop out of the comparison.
            val topmost = onScreen.mapNotNull { position[it] }.minOrNull()
            // An article that has left the feed entirely can no longer be
            // judged passed or not, and keeping it would be a slow leak.
            ready.retainAll { it in position }
            passedIds(ready, position, topmost).forEach { id ->
                ready -= id
                marked += id
                val item = byId[id] ?: return@forEach
                // A count, not the headline: the report is shared, and the
                // titles would say what the reader has been reading.
                if (trace) {
                    gateLog("marked: ${marked.size} this visit")
                }
                onRead(item)
            }

            // An article scrolled away before the threshold starts again next
            // time it comes past. A glance is a glance, however many of them.
            // Anything in `ready` keeps what it earned: leaving through the
            // bottom means the reader scrolled back up, not that they passed
            // it, and making them dwell on it twice would be a strange thing
            // to ask.
            dwell.keys.retainAll(ids)
        }
    } finally {
        seen.forEach { (id, ms) -> onDwell(id, ms) }
    }
}

/**
 * The keys of everything showing at least [VISIBLE_ENOUGH] of itself.
 *
 * The two containers report their layout through different types with the same
 * shape, and neither shares an interface, so the arithmetic is written twice
 * rather than hidden behind a wrapper that would be longer than both.
 */
private fun visibleEnoughKeys(
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
): List<String> = if (isGrid) {
    val info = gridState.layoutInfo
    info.visibleItemsInfo.filter { item ->
        val height = item.size.height
        height > 0 && visibleFraction(
            start = item.offset.y,
            size = height,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
        ) >= VISIBLE_ENOUGH
    }.mapNotNull { it.key as? String }
} else {
    val info = listState.layoutInfo
    info.visibleItemsInfo.filter { item ->
        item.size > 0 && visibleFraction(
            start = item.offset,
            size = item.size,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
        ) >= VISIBLE_ENOUGH
    }.mapNotNull { it.key as? String }
}

/**
 * The keys of everything with any part of itself on screen.
 *
 * Separate from [visibleEnoughKeys], which answers "has this been looked at".
 * This answers "is this still there at all", and a card sliding out with a
 * sliver showing has not been passed yet.
 */
private fun visibleKeys(
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
): List<String> = if (isGrid) {
    gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
} else {
    listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
}

private fun visibleFraction(
    start: Int,
    size: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Float {
    val top = maxOf(start, viewportStart)
    val bottom = minOf(start + size, viewportEnd)
    return ((bottom - top).coerceAtLeast(0)).toFloat() / size
}

/**
 * Which of the waiting articles have been scrolled past.
 *
 * An article is passed when it sits above everything still on screen. Pulled
 * out of the loop because it is the rule the whole feature turns on, and
 * because the bug it replaced — articles dimming while they were being read —
 * came from having no name for this question at all.
 *
 * Nothing is passed when nothing is visible: an empty layout means the list is
 * still being measured, and treating that as "everything scrolled past" would
 * mark the whole feed read on a rotation.
 */
internal fun passedIds(
    ready: Set<String>,
    position: Map<String, Int>,
    topmostVisible: Int?,
): List<String> {
    if (topmostVisible == null) return emptyList()
    return ready.filter { id -> position[id]?.let { it < topmostVisible } == true }
}

/**
 * The current feed, by the two keys the tracker asks it about.
 *
 * One object rather than two parameters so the loop takes a single snapshot
 * per tick: reading them separately would let the list change between the two
 * and pair an article with another article's position.
 */
internal data class FeedLookup(
    val byId: Map<String, FeedItem>,
    val position: Map<String, Int>,
)
