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

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.asState
import com.saulhdev.feeder.data.db.models.FeedItem
import org.koin.compose.koinInject

/**
 * The feed, as a list of cards.
 *
 * This replaces `heldFeed`, which put a clustered story into a `stickyHeader`
 * so it stayed at the top for the next few articles. That is gone, and the
 * reasoning is worth keeping because the feature sounded right.
 *
 * Its condition was positional, not editorial:
 *
 *     val first = articles.firstOrNull() ?: return null
 *     val isLead = clusters[first.id]?.leadId == first.id
 *     if (isLead) first else null
 *
 * It held whatever happened to be first in the list *currently on screen*, if
 * that item led any cluster. In a search, "first in the list" means the first
 * result for whatever was typed; in a category, the first of that category;
 * and the same would have applied to a single-source view. None of those has
 * anything to do with a story breaking, so the hold fired in places where the
 * thing it claimed to be doing was meaningless — and a reader who had searched
 * for something got a card stuck over their results.
 *
 * It was also expensive to keep upright. A `stickyHeader` is drawn *over* the
 * list rather than in it, so every part of the card had to be opaque or it
 * became a window onto the articles sliding underneath; that was fixed twice,
 * and a held card with no image was see-through still. The read-dimming had to
 * learn an exemption for it. It needed a Dismiss action, because being
 * unavoidable is the one thing it was good at.
 *
 * What it was for survives, and was already on the card before any of this:
 * `CoverageLine` draws a megaphone and the number of sources carrying the
 * story, on the card, in its ordinary place in the feed. The clustering that
 * works that out is untouched — it still decides emphasis, still explains
 * itself in the weighting reasons, and a dismissal still gives back the
 * promotion. Only the sticking is gone.
 */
fun LazyListScope.feedItems(
    articles: List<FeedItem>,
    /**
     * Whether cards may move rather than appear.
     *
     * Passed rather than read here because this is a LazyListScope extension
     * and the setting is a composable read — and passed at all because it was
     * honoured on one of the four feed paths and ignored on the other three.
     * Somebody who has turned animations off has said what they want, and the
     * answer should not depend on which layout they chose or which surface
     * they are looking at.
     */
    animate: Boolean = true,
    /**
     * The card shape at each position; see [feedContentType].
     *
     * Without it every card is the same kind to the list, so the composition
     * it keeps from a compact row that scrolled away is reused for the hero
     * coming in: nothing in common, all of it rebuilt in the frame the hero
     * appears. Told the shape, the list reuses a hero for a hero.
     */
    contentType: (index: Int, item: FeedItem) -> Any? = { _, _ -> null },
    article: @Composable (index: Int, item: FeedItem) -> Unit,
) {
    // Keyed, so a sync moves cards rather than replacing the list.
    itemsIndexed(articles, key = { _, item -> item.id }, contentType = contentType) { index, item ->
        Box(modifier = if (animate) Modifier.animateItem() else Modifier) {
            article(index, item)
        }
    }
}

/**
 * The shape a card will take, as the lazy containers' content type.
 *
 * The same answer [FeedArticleItem] reaches, from the same function, so the
 * type a slot is filed under is the shape actually drawn in it.
 */
fun feedContentType(
    index: Int,
    item: FeedItem,
    layout: String,
    emphasis: List<FeedEmphasis>,
): FeedCardShape = feedCardShape(
    index = index,
    hasImage = !item.imageUrl.isNullOrBlank(),
    layout = layout,
    emphasis = emphasis.getOrNull(index) ?: FeedEmphasis.Medium,
)

const val FEED_END_KEY = "feed-end"

/**
 * How tall the space after the last article is, or null for none.
 *
 * Mark read on scroll counts an article read once it has been scrolled off the
 * top of the screen (see passedIds), which the last screenful never could be:
 * there was nothing below them to scroll. They stayed unread for good, and
 * with read articles hidden, stayed in the feed - reported on 17 October 2026,
 * "the last two remain because it's no longer possible to scroll further".
 *
 * A screen of space after them lets them leave like every other article, and
 * the rule stays the one that keeps an article from vanishing while it is
 * read. Only while the feature is on: nobody else has a reason to scroll into
 * an empty screen. A whole viewport, so the last card clears the top padding
 * too. The height is read through derivedStateOf, so scrolling does not
 * recompose the feed - only a change of window size does.
 */
@Composable
fun rememberFeedEndSpace(viewportHeightPx: () -> Int): Dp? {
    val prefs: FeedPreferences = koinInject()
    val setting by prefs.markReadOnScroll.asState()
    // The same "off" TrackReading reads: below a quarter of a second.
    if (setting < 0.25f) return null
    val px by remember { derivedStateOf(viewportHeightPx) }
    if (px <= 0) return null
    return with(LocalDensity.current) { px.toDp() }
}

/** The space after the last article; see [rememberFeedEndSpace]. */
fun LazyListScope.feedEnd(height: Dp?) {
    if (height == null) return
    item(key = FEED_END_KEY, contentType = FEED_END_KEY) { FeedEnd(height) }
}

/**
 * Whether the screen shows the end of the feed and no article: everything
 * above it read and hidden, or scrolled past. See FeedEndReturn.
 */
fun onlyTheEndShows(visibleKeys: List<Any>, articleIds: Set<String>): Boolean =
    FEED_END_KEY in visibleKeys && visibleKeys.none { it in articleIds }

/**
 * Back to the top when the end of the feed is all there is to see and there
 * is something new above it.
 *
 * A reader reported on 19 October 2026 that, having read to the end with
 * read articles hidden, she came back to the app - or pressed "Try again" -
 * and was still looking at the empty end, with the new articles out of sight
 * above, and had to use the arrow to reach them. The end space keeps its
 * place like any other position. So when the feed comes back into view, or
 * articles arrive that were not in it before, and nothing but the end is on
 * screen, it goes to the top. An article still on screen keeps the place
 * where it is; and articles leaving the list as they are read never move
 * it, or reading the last one would throw the reader upwards.
 */
@Composable
fun FeedEndReturn(
    articles: List<FeedItem>,
    visibleKeys: () -> List<Any>,
    scrollToTop: suspend () -> Unit,
) {
    val visible = LocalFeedVisible.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    val ids = remember(articles) { articles.mapTo(HashSet()) { it.id } }
    var known by remember { mutableStateOf<Set<String>?>(null) }
    // Coming back into view: the app resuming, or the panel opening.
    LaunchedEffect(visible, resumed) {
        if (visible && resumed && onlyTheEndShows(visibleKeys(), ids)) scrollToTop()
    }
    // Something new arrived.
    LaunchedEffect(ids) {
        val before = known
        known = ids
        if (before != null && ids.any { it !in before } && onlyTheEndShows(visibleKeys(), ids)) scrollToTop()
    }
}

/** The grid's, across both lanes. */
fun LazyStaggeredGridScope.feedEnd(height: Dp?) {
    if (height == null) return
    item(key = FEED_END_KEY, contentType = FEED_END_KEY, span = StaggeredGridItemSpan.FullLine) {
        FeedEnd(height)
    }
}

/**
 * "That's everything", rather than "all caught up": an article skimmed past
 * quicker than the threshold is not read, and the line should not say it is.
 */
@Composable
private fun FeedEnd(height: Dp) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Text(
            text = stringResource(R.string.feed_end),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}
