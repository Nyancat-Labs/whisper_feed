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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.saulhdev.feeder.data.db.models.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 *
 * ## Once per change, not once per visit
 *
 * The anchor outlives its use: the view model keeps it so the way back out
 * can land on it too. A LaunchedEffect runs again whenever the feed comes
 * back into composition, as it does on the way back from Settings, so the
 * feed jumped to the source's article long after the reader had scrolled
 * away from it (Szept 457). The change last applied is saved with the
 * screen's state, and the same change is not applied twice.
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
    var applied by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(appliedFocus, anchorId) {
        val id = anchorId ?: return@LaunchedEffect
        val change = anchorChangeKey(appliedFocus, id)
        if (change == applied) return@LaunchedEffect
        applied = change
        val position = articles.indexOfFirst { it.id == id }
        // Not in this list at all — the article that started the detour can be
        // filtered out of the feed being returned to, by a category chip or by
        // hide-read. Leaving the scroll where it is beats guessing.
        if (position < 0) return@LaunchedEffect
        val index = feedListIndex(position, breaks)
        if (isGrid) gridState.scrollToItem(index) else listState.scrollToItem(index)
    }
}

/** One narrowing and the article it started from; see AnchorFeedOnFocusChange. */
internal fun anchorChangeKey(appliedFocus: String?, anchorId: String): String =
    "${appliedFocus.orEmpty()}\u0000$anchorId"

/**
 * The article last opened from the launcher panel, for the app's feed to
 * land on when the reader comes back to it.
 *
 * From the panel, Back (or closing the tab or browser) arrives on the app's
 * feed, not the panel's, and that feed starts at the top and then refreshes,
 * so the reader found newer articles instead of where they were (Szept 479).
 * The panel runs in Whisper's own process, so it can leave the id here
 * directly; the app's feed takes it once, when the article is in its list.
 */
object PanelReturnAnchor {
    private val pending = MutableStateFlow<String?>(null)
    val articleId: StateFlow<String?> = pending.asStateFlow()

    fun leave(articleId: String) {
        pending.value = articleId
    }

    /** Opened as an app, from its icon: nothing to come back to. */
    fun clear() {
        pending.value = null
    }

    /** Taken: cleared only if it is still [articleId], not a newer one. */
    fun take(articleId: String) {
        pending.compareAndSet(articleId, null)
    }
}

/**
 * Scrolls the app's feed to [PanelReturnAnchor]'s article, once.
 *
 * Waits for the article to arrive rather than giving up on the first, empty
 * list a fresh feed starts with. Dropped when the loaded feed does not hold
 * it (a category chip or hide-read can leave it out): guessing a place
 * would be worse than the top.
 */
@Composable
fun AnchorFeedOnPanelReturn(
    articles: List<FeedItem>,
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    breaks: List<DayBreak> = emptyList(),
) {
    val id by PanelReturnAnchor.articleId.collectAsState()
    LaunchedEffect(id, articles) {
        val anchor = id ?: return@LaunchedEffect
        if (articles.isEmpty()) return@LaunchedEffect
        val position = articles.indexOfFirst { it.id == anchor }
        PanelReturnAnchor.take(anchor)
        if (position < 0) return@LaunchedEffect
        val index = feedListIndex(position, breaks)
        if (isGrid) gridState.scrollToItem(index) else listState.scrollToItem(index)
    }
}
