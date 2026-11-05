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

import com.saulhdev.feeder.data.db.models.FeedItem
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/**
 * Mosaic, with fewer holes above its lead stories (review 42).
 *
 * A lead story spans both lanes, so it starts only when both lanes above it
 * have ended, and whatever one lane is shorter than the other is left empty.
 * Decided 27 October: shuffle. Before each lead, a tile or two from just
 * after it (never more than [LOOKAHEAD] places away, never across a day, never
 * a pinned one) moves up into the shorter lane when that brings the lanes
 * closer to level. The order stays nearly chronological, and some white
 * space will remain: the heights are estimates, made before anything is
 * drawn, so the feed does not reshuffle as pictures arrive.
 *
 * Returns the new order, as indices into [items].
 */
internal fun levelBeforeLeads(
    items: List<FeedItem>,
    emphasis: List<FeedEmphasis>,
    lanes: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Int> {
    if (lanes < 2 || items.size != emphasis.size) return items.indices.toList()
    val heights = items.indices.map { mosaicTileHeight(items[it], emphasis[it]) }
    val lead = emphasis.map { it == FeedEmphasis.Large }
    val movable = items.map { !it.article.pinned }
    val day = items.map {
        Instant.ofEpochMilli(it.article.primarySortTime.toEpochMilliseconds()).atZone(zone).toLocalDate()
    }
    return levelOrder(heights, lead, movable, day, lanes)
}

/** The shuffle itself, on plain numbers; see [levelBeforeLeads]. */
internal fun levelOrder(
    heights: List<Float>,
    lead: List<Boolean>,
    movable: List<Boolean>,
    group: List<Any?>,
    lanes: Int,
): List<Int> {
    val order = heights.indices.toMutableList()
    val lane = FloatArray(lanes)
    fun shortest(): Int = lane.indices.minBy { lane[it] }
    fun gap(l: FloatArray): Float = l.max() - l.min()

    var i = 0
    while (i < order.size) {
        val at = order[i]
        if (!lead[at]) {
            lane[shortest()] += heights[at]
            i++
            continue
        }
        var moves = 0
        while (moves < MAX_MOVES && gap(lane) >= MIN_GAP) {
            var best = -1
            var bestGap = gap(lane) - MIN_GAIN
            for (j in i + 1..minOf(i + LOOKAHEAD, order.size - 1)) {
                val c = order[j]
                if (lead[c] || !movable[c] || group[c] != group[at]) continue
                val trial = lane.copyOf().also { it[shortest()] += heights[c] }
                val g = gap(trial)
                if (g < bestGap) { best = j; bestGap = g }
            }
            if (best < 0) break
            val c = order.removeAt(best)
            order.add(i, c)
            lane[shortest()] += heights[c]
            i++
            moves++
        }
        val top = lane.max()
        lane.fill(top)
        i++
    }
    return order
}

/** How far after a lead story a tile may come from. */
private const val LOOKAHEAD = 4

/** At most this many tiles move up before any one lead. */
private const val MAX_MOVES = 2

/** A difference this small (dp) is not worth moving anything for. */
private const val MIN_GAP = 40f

/** A move must close the gap by at least this much (dp). */
private const val MIN_GAIN = 16f

/**
 * A Mosaic tile's height in dp, roughly, at the width of a two-lane tile on a
 * phone (about 150dp inside its padding): the picture at its fixed ratio, the
 * headline's lines, the source line, and the row of buttons a tile without a
 * picture has under its headline.
 */
internal fun mosaicTileHeight(item: FeedItem, emphasis: FeedEmphasis): Float {
    if (emphasis == FeedEmphasis.Large) return 0f
    val medium = emphasis == FeedEmphasis.Medium
    val image = !item.imageUrl.isNullOrBlank()
    var h = 12f + 20f
    if (image) h += if (medium) 112f else 84f
    val maxLines = if (medium) 4 else 2
    val lines = ceil(item.contentTitle.length / CHARS_PER_LINE).toInt().coerceIn(1, maxLines)
    h += lines * 20f
    h += 6f + if (image) 18f else 48f
    if (item.pinned) h += 22f
    return h
}

private const val CHARS_PER_LINE = 22f
