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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.saulhdev.feeder.data.db.models.FeedItem

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
