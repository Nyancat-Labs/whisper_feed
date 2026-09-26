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

import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.LAYOUT_LIST
import com.saulhdev.feeder.utils.LAYOUT_MAGAZINE
import com.saulhdev.feeder.utils.LAYOUT_MOSAIC

/** The shapes an article can take in the feed. */
enum class FeedCardShape {
    /** Full-bleed image with the headline laid over it. Anchors the feed. */
    Hero,

    /** Image above, headline and summary below. The relaxed middle weight. */
    Card,

    /** Headline left, thumbnail right. Dense, quick to scan. */
    Compact,

    /** Headline and source only. No image at any width. */
    Text,

    /** A grid tile: image on top, headline beneath, sized by its content. */
    Tile,
}

/**
 * Picks a shape for the article at [index], for the chosen [layout].
 *
 * The four layouts differ in what they are *for*, not in decoration:
 *
 *  - **Cards** varies the weight of consecutive items. A feed of identical
 *    cards reads as one undifferentiated column and gives the eye nothing to
 *    catch on, which is the thing a scrolling news surface most has to avoid.
 *    A hero opens, another lands every eighth item to reset the rhythm, a full
 *    card sits at a regular offset between, compact rows fill the rest.
 *  - **Magazine** commits to the image on every item. Fewer articles per
 *    screen, each one worth stopping at — for reading rather than triage.
 *  - **List** drops images entirely. Most articles per screen, for getting
 *    through a backlog.
 *  - **Mosaic** is a staggered grid of three tile sizes, the largest crossing
 *    both columns. Leaving the variation to the images' own aspect ratios was
 *    not enough in practice: news photography is overwhelmingly 16:9, so every
 *    tile came out the same height and the grid read as a table. Which article
 *    gets which size is the one thing here that is not positional — see
 *    FeedWeight.kt.
 *
 * The Cards pattern is positional rather than content-derived on purpose.
 * Deriving it from the article — image size, title length, source — would make
 * the layout jump around as a sync reorders the list, and an article would
 * change shape depending on what happened to load near it.
 *
 * In every layout except List, an article with no usable image falls back to a
 * shape that does not need one: the image-led shapes collapse into an oddly
 * padded blank without one.
 */
fun feedCardShape(
    index: Int,
    hasImage: Boolean,
    layout: String = LAYOUT_CARDS,
    emphasis: FeedEmphasis = FeedEmphasis.Medium,
): FeedCardShape = when (layout) {
    LAYOUT_LIST -> FeedCardShape.Text

    // Tile even without an image: the tile already omits it, and a full-width
    // text row dropped into a half-width grid cell reads as a broken tile
    // rather than a deliberate one. Which *size* of tile is the emphasis's
    // business, and the tile reads that directly.
    LAYOUT_MOSAIC -> FeedCardShape.Tile

    LAYOUT_MAGAZINE -> if (hasImage) FeedCardShape.Card else FeedCardShape.Compact

    // Cards was positional: a hero at 0, another every eighth, a full card
    // every fourth. That gave the biggest shape to whatever happened to be
    // eighth rather than to whatever was worth it, and changed an article's
    // shape every time a sync moved it. It now reads the same emphasis Mosaic
    // does, so the two layouts promote the same articles and differ only in
    // how they draw the promotion.
    else -> when {
        !hasImage                       -> FeedCardShape.Compact
        emphasis == FeedEmphasis.Large  -> FeedCardShape.Hero
        emphasis == FeedEmphasis.Medium -> FeedCardShape.Card
        else                            -> FeedCardShape.Compact
    }
}

/**
 * How much of the screen one article has earned.
 *
 * Shared by Cards and Mosaic rather than belonging to either. The two layouts
 * disagree about what emphasis *looks* like — Cards answers Large with a
 * full-bleed hero, Mosaic with a tile across both columns — but they should
 * never disagree about which article deserves it. One scale, two renderings.
 *
 * See FeedWeight.kt for how an article gets one.
 */
enum class FeedEmphasis {
    /** Enough to be legible and no more. The bulk of a feed. */
    Small,

    /** The comfortable middle: an image at its own size, room to read. */
    Medium,

    /** The thing the eye lands on. One per screenful at most. */
    Large,
}

/**
 * Whether a layout wants a staggered grid rather than a single column.
 *
 * Kept here beside the shape rules so the container and the shape it holds
 * cannot disagree — a Tile in a LazyColumn is a full-width card with a
 * grid tile's proportions, which looks like a mistake rather than a choice.
 */
fun feedLayoutIsGrid(layout: String): Boolean = layout == LAYOUT_MOSAIC

/**
 * How many columns the feed is laid out in, at a width.
 *
 * On a tablet with no article open the feed had the whole screen to itself
 * and filled it with one column: a card two thousand pixels wide, its photo
 * decoded at that size by an older chip, and scrolling that stuttered where
 * the phone did not. Columns put the width to use and make every card, and
 * every picture, a third to half the size. With an article open beside it,
 * the feed is narrow again and goes back to one column.
 *
 * Mosaic keeps its two lanes on a phone and gains more on a wide screen; the
 * other layouts stay a single column until there is room for a second card at
 * a phone's width, or the window is wider than any phone at all.
 *
 * That second rule is for foldables opened out. At 619 dp (a Galaxy Z Fold6)
 * to 692 dp (a Pixel 10 Pro Fold) there was not room for two cards of 360, so
 * the feed was one column the width of the screen, and a lead story's picture
 * grew with it until it filled the screen. From [TWO_COLUMNS_FROM_DP] the
 * feed has two columns, each about a small phone's width.
 *
 * @param widthDp the width the feed has, in dp.
 */
fun feedColumns(layout: String, widthDp: Int): Int =
    if (feedLayoutIsGrid(layout)) (widthDp / MOSAIC_LANE_DP).coerceIn(2, 4)
    else maxOf(widthDp / CARD_COLUMN_DP, if (widthDp >= TWO_COLUMNS_FROM_DP) 2 else 1).coerceIn(1, 3)

/**
 * Whether a lead story spans the whole row: in Mosaic, and only while the row
 * is two lanes. A staggered grid spans one lane or all of them, so "at most
 * two wide" means one lane once there are more than two.
 */
fun leadSpansRow(layout: String, columns: Int): Boolean =
    feedLayoutIsGrid(layout) && columns <= 2

/** About a phone's width: a column of cards past this makes room for another. */
const val CARD_COLUMN_DP = 360

/**
 * Wider than any phone: Android's own line between a phone's window and a
 * foldable opened out or a tablet. See [feedColumns].
 */
const val TWO_COLUMNS_FROM_DP = 600

/** Half of that, for Mosaic's tiles. */
const val MOSAIC_LANE_DP = 200

/**
 * The key the glance row and chips travel under, on both surfaces.
 *
 * A key rather than a bare item because read-on-scroll reads keys: it ignores
 * anything that is not an article, and it can only do that if the header has a
 * name to be ignored by.
 *
 * Shared rather than declared twice because the two surfaces are supposed to
 * scroll identically, and two private constants is how they stop.
 */
const val FEED_HEADER_KEY = "feed-header"

/**
 * How many items stand before the first article in every feed list.
 *
 * One: the header. Both surfaces, both containers. Named because it is the
 * offset between an article's position in the list of articles and its
 * position in the LazyList that draws them, and anything scrolling to a
 * particular article needs that offset and would otherwise carry its own
 * guess at it. See FocusAnchor.
 */
const val FEED_ARTICLES_START = 1
