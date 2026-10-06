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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.saulhdev.feeder.data.db.models.FeedItem

/**
 * Lands both edges of a narrowing on the article the reader tapped.
 *
 * A filtered feed is a different list of a different length, and a scroll
 * position is an index into whichever list is on screen. Kept across the
 * change it therefore points at a different story — tap a source on one
 * article and the filtered feed opens somewhere else entirely, which is
 * exactly how it looked. The way out has the same problem in reverse.
 *
 * The article id is the one thing both lists agree on, so it is the anchor in
 * both directions: into the narrowing, and back out of it.
 *
 * ## Keyed on the list that arrived, not on the request
 *
 * [appliedFocus] is the narrowing [articles] was actually built under, which
 * is not the one that was just asked for: the query has not run yet when the
 * request changes. Keyed on the request, this would measure the list being
 * left and scroll to a position in it moments before it was replaced. Keyed
 * on the applied focus, it runs once, on the list it is about to move.
 *
 * `scrollToItem` rather than an animated scroll. The list underneath has just
 * been replaced wholesale; animating through hundreds of cards that are not
 * the ones the reader was looking at is motion that means nothing.
 */
@Composable
fun AnchorFeedOnFocusChange(
    appliedFocus: String?,
    anchorId: String?,
    articles: List<FeedItem>,
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    /** Day headings above the articles, which move every article down. */
    breaks: List<DayBreak> = emptyList(),
) {
    LaunchedEffect(appliedFocus, anchorId) {
        val id = anchorId ?: return@LaunchedEffect
        val position = articles.indexOfFirst { it.id == id }
        // Not in this list at all — the article that started the detour can be
        // filtered out of the feed being returned to, by a category chip or by
        // hide-read. Leaving the scroll where it is beats guessing.
        if (position < 0) return@LaunchedEffect
        val index = feedListIndex(position, breaks)
        if (isGrid) gridState.scrollToItem(index) else listState.scrollToItem(index)
    }
}
