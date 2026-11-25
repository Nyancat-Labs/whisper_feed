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

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.saulhdev.feeder.data.db.models.FeedItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
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

/** The two places the feed is shown. */
enum class FeedSurface { APP, PANEL }

/**
 * Where the reader last was in the feed, on either surface, for the other to
 * start from (Szept 479, 481).
 *
 * The app and the launcher panel kept separate scroll positions: back from an
 * article opened on the panel, the app's feed started at the top and then
 * refreshed, so the reader found newer articles instead of their place; and
 * scrolling one left the other where it was. Both run in Whisper's process,
 * so this is shared in memory. Each surface records where the reader stopped
 * scrolling (or the article they tapped), and the other takes the newest
 * mark once, when it holds that article. Only the surface being looked at is
 * scrolled by a finger, so the other never moves under one.
 */
object FeedPlace {
    /** The top of the feed, header and all. */
    const val TOP = "\u0000top"

    /**
     * [offset]: where the article's top was, in pixels from the top of the
     * list (negative when partly scrolled off), so the other surface can put
     * it in the same spot rather than a card's height out (Szept 483, T5).
     */
    class Mark(val articleId: String, val by: FeedSurface, val seq: Long, val offset: Int = 0)

    private val current = MutableStateFlow<Mark?>(null)
    val mark: StateFlow<Mark?> = current.asStateFlow()
    private var seq = 0L

    /**
     * The reader is at [articleId], [offset] pixels down, on [by]. The same
     * spot again (within a couple of pixels) is no news, so a surface that
     * has just caught up does not send the other one back.
     */
    @Synchronized
    fun record(by: FeedSurface, articleId: String, offset: Int = 0) {
        val now = current.value
        if (now != null && now.articleId == articleId && kotlin.math.abs(now.offset - offset) <= SAME_SPOT_PX) return
        current.value = Mark(articleId, by, ++seq, offset)
    }

    private const val SAME_SPOT_PX = 2

    fun clear() {
        current.value = null
    }
}

/**
 * The place to record for a list at rest: the top while the header shows,
 * otherwise the first article on screen (day headings are passed over), and
 * how far down its top is. [visible] pairs each visible item's key with its
 * offset from the top of the list.
 */
internal fun placeOf(firstVisibleIndex: Int, visible: List<Pair<Any, Int>>, ids: Set<String>): Pair<String, Int>? =
    if (firstVisibleIndex == 0) FeedPlace.TOP to 0
    else visible.firstOrNull { (key, _) -> key is String && key in ids }?.let { (key, offset) -> key as String to offset }

/**
 * Keeps this surface's feed in step with the other's: takes the other's
 * newest place once it holds that article, and records its own whenever the
 * reader stops scrolling. Off ([enabled] false) for a search or Saved, which
 * are not the feed.
 *
 * Returns true while this feed is still on its way to the other surface's
 * place: a freshly opened feed draws at the top while its articles load and
 * would then jump, so the caller keeps it out of sight until it is there,
 * for [PLACE_WAIT_MS] at most (owner: smooth, not jerky).
 */
@Composable
fun SyncFeedPlace(
    surface: FeedSurface,
    articles: List<FeedItem>,
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    breaks: List<DayBreak> = emptyList(),
    enabled: Boolean = true,
): Boolean {
    val mark by FeedPlace.mark.collectAsState()
    var taken by remember { mutableLongStateOf(0L) }
    // Set while this feed is being put in place: its own jump is not the
    // reader stopping somewhere, and recording it cancelled the exact
    // placing half-way (Szept 483).
    val applying = remember { AtomicBoolean(false) }
    val owed = mark?.takeIf { enabled && it.by != surface && it.seq > taken }
    // Owed and reachable: the article is here, or the list is still empty.
    val placing = owed != null && (articles.isEmpty() || owed.articleId == FeedPlace.TOP ||
            articles.any { it.id == owed.articleId })
    var gaveUp by remember { mutableStateOf(false) }
    LaunchedEffect(owed?.seq) {
        gaveUp = false
        if (owed == null) return@LaunchedEffect
        delay(PLACE_WAIT_MS)
        gaveUp = true
    }
    LaunchedEffect(mark, articles, enabled) {
        val m = mark ?: return@LaunchedEffect
        if (!enabled || m.by == surface || m.seq <= taken || articles.isEmpty()) return@LaunchedEffect
        taken = m.seq
        val index = if (m.articleId == FeedPlace.TOP) 0 else {
            // Not in this feed (a chip or hide-read can leave it out): stay put
            // rather than guess.
            val position = articles.indexOfFirst { it.id == m.articleId }
            if (position < 0) return@LaunchedEffect
            feedListIndex(position, breaks)
        }
        // The index is a first guess: the two surfaces do not hold quite the
        // same items above the articles. Then the article is found by its key
        // and set exactly where it was on the other surface.
        // The layout is read once it has caught up with the jump, not before:
        // read straight after, it still described the old place.
        applying.set(true)
        try { if (isGrid) {
            gridState.scrollToItem(index)
            val found = withTimeoutOrNull(LAYOUT_WAIT_MS) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
                    .first { items -> items.firstOrNull()?.index == index }
                    .firstOrNull { it.key == m.articleId }
            }
            found?.let { gridState.scrollBy((it.offset.y - m.offset).toFloat()) }
        } else {
            listState.scrollToItem(index)
            val found = withTimeoutOrNull(LAYOUT_WAIT_MS) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .first { items -> items.firstOrNull()?.index == index }
                    .firstOrNull { it.key == m.articleId }
            }
            found?.let { listState.scrollBy((it.offset - m.offset).toFloat()) }
        } } finally { applying.set(false) }
    }
    val currentArticles by rememberUpdatedState(articles)
    LaunchedEffect(isGrid, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { if (isGrid) gridState.isScrollInProgress else listState.isScrollInProgress }
            .distinctUntilChanged()
            .drop(1)
            .filter { moving -> !moving && !applying.get() }
            .collect {
                val ids = currentArticles.mapTo(HashSet()) { it.id }
                val place = if (isGrid) {
                    placeOf(gridState.firstVisibleItemIndex, gridState.layoutInfo.visibleItemsInfo.map { it.key to it.offset.y }, ids)
                } else {
                    placeOf(listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo.map { it.key to it.offset }, ids)
                }
                if (place != null) FeedPlace.record(surface, place.first, place.second)
            }
    }
    return placing && !gaveUp
}

/** How long to wait for a jump to be laid out before setting the article exactly. */
private const val LAYOUT_WAIT_MS = 300L

/** The longest a feed is kept out of sight while it finds its place. */
const val PLACE_WAIT_MS = 600L
