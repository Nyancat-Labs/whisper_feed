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

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Hands every frame a window draws to [FeedTrace], so a stutter is counted
 * rather than described.
 *
 * "A stutter every fifth or tenth post" was a good description and could not
 * be checked: the trace said how often the feed reloaded and how much memory
 * was collected, but nothing said which of those the reader actually saw.
 * Frame times do. A window that shows slow frames next to reloads, and none
 * without them, settles it either way.
 *
 * Only attached while Debugging is on. The listener runs on its own thread,
 * never the one drawing, and records durations only.
 */
object FrameWatch {

    private val thread by lazy { HandlerThread("FrameWatch").apply { start() } }

    /**
     * Counts [window]'s frames whenever [enabled] says so, until cancelled.
     *
     * Follows the setting rather than reading it once, so turning Debugging
     * on is enough; see NeoApp.reportFeedTrace for why that matters.
     */
    suspend fun watch(window: Window, enabled: Flow<Boolean>) {
        enabled.collectLatest { on ->
            if (!on) return@collectLatest
            val stop = attach(window)
            try {
                awaitCancellation()
            } finally {
                stop()
            }
        }
    }

    /** Starts counting [window]'s frames. Call the result to stop. */
    fun attach(window: Window): () -> Unit {
        val listener = Window.OnFrameMetricsAvailableListener { w, metrics, _ ->
            // The deadline the system set this frame, where Android says it:
            // the display changes rate, and the deadline follows it.
            val interval = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                metrics.getMetric(FrameMetrics.DEADLINE)
            } else {
                val refresh = w.decorView.display?.refreshRate?.takeIf { it > 0f } ?: 60f
                (1_000_000_000L / refresh).toLong()
            }
            FeedTrace.frame(
                durationNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION),
                intervalNs = interval,
            )
        }
        return runCatching {
            window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
            val detach: () -> Unit = {
                runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
            }
            detach
        }.getOrDefault {}
    }
}

/**
 * Whether a frame took long enough to be seen as a stutter.
 *
 * Over twice the time the frame was given: one frame late is a frame
 * repeated, which the eye forgives at 120Hz; more than that is a visible
 * hitch. Measured against the rate the display is actually running at,
 * because a Pixel moves between 60 and 120Hz and a fixed 16ms would call a
 * frame that ran three deadlines late at 120Hz fine.
 */
fun isSlowFrame(durationNs: Long, intervalNs: Long): Boolean = durationNs > 2 * intervalNs
