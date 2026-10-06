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
package com.saulhdev.feeder.ui.onboarding

import androidx.annotation.StringRes
import com.saulhdev.feeder.R

/** One stop on the tour: a control, and the one sentence about it. */
data class TourStep(
    val target: TourTarget,
    @param:StringRes val titleId: Int,
    @param:StringRes val bodyId: Int,
)

/**
 * The tour.
 *
 * Six stops, which is already the limit — the point of a tour is that somebody
 * finishes it, and each extra stop is another chance for them not to. Every
 * one names a control that is on the screen; nothing here explains a feature
 * that has to be gone looking for, because that is what the settings screen
 * and its summaries are for.
 */
val TOUR_STEPS: List<TourStep> = listOf(
    TourStep(TourTarget.Glance, R.string.tour_glance_title, R.string.tour_glance_body),
    TourStep(TourTarget.Chips, R.string.tour_chips_title, R.string.tour_chips_body),
    TourStep(TourTarget.Article, R.string.tour_article_title, R.string.tour_article_body),
    TourStep(TourTarget.Bookmarks, R.string.tour_bookmarks_title, R.string.tour_bookmarks_body),
    TourStep(TourTarget.Filter, R.string.tour_filter_title, R.string.tour_filter_body),
    TourStep(TourTarget.Overflow, R.string.tour_overflow_title, R.string.tour_overflow_body),
)

/**
 * Which step comes next, given what is actually on screen.
 *
 * Pure, and separate from the overlay, because this is the part with rules in
 * it and the overlay is the part with pixels in it. A control can be missing
 * for perfectly ordinary reasons — the glance row is a setting, the chips need
 * categories to exist — and a tour that stops dead pointing at nothing is
 * worse than one that is a step shorter.
 *
 * Returns `null` when there is nothing left, which is how the tour ends.
 */
object TourMachine {

    /** The first step whose target is present, if any. */
    fun first(steps: List<TourStep>, present: Set<TourTarget>): Int? =
        steps.indexOfFirst { it.target in present }.takeIf { it >= 0 }

    /** The next present step after [current], or null when the tour is over. */
    fun next(steps: List<TourStep>, current: Int, present: Set<TourTarget>): Int? {
        if (current < 0) return first(steps, present)
        for (i in (current + 1) until steps.size) {
            if (steps[i].target in present) return i
        }
        return null
    }

    /** Whether [current] is the last step that will be shown. */
    fun isLast(steps: List<TourStep>, current: Int, present: Set<TourTarget>): Boolean =
        next(steps, current, present) == null

    /**
     * How many steps the reader will be shown, and where they are in them.
     *
     * The counter has to say "2 of 4" and not "2 of 6" when two targets are
     * absent, or it promises stops that are never going to arrive.
     */
    fun position(steps: List<TourStep>, current: Int, present: Set<TourTarget>): Pair<Int, Int> {
        val shown = steps.indices.filter { steps[it].target in present }
        val total = shown.size
        val index = shown.indexOf(current)
        return (if (index >= 0) index + 1 else 0) to total
    }
}
