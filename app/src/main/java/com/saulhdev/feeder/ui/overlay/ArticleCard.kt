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

import com.saulhdev.feeder.ui.icons.phosphor.BookBookmark
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.runtime.CompositionLocalProvider
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.components.SaveButton
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Asterisk
import com.saulhdev.feeder.ui.icons.phosphor.Megaphone
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.LAYOUT_LIST_PICTURES
import com.saulhdev.feeder.utils.LAYOUT_MAGAZINE
import com.saulhdev.feeder.utils.shortSourceName
import com.saulhdev.feeder.utils.TIME_RELATIVE
import com.saulhdev.feeder.utils.formatArticleTime

/**
 * One article in the feed, drawn in whichever shape the rhythm asks for.
 *
 * Both surfaces — the launcher overlay and the app — call this, so the feed
 * looks the same in each and a change to the rhythm lands in both at once.
 * See [feedCardShape] for how the shape is chosen.
 */
@Composable
fun FeedArticleItem(
    item: FeedItem,
    index: Int,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreLikeThis: () -> Unit = {},
    onLessLikeThis: () -> Unit = {},
    onHideSource: () -> Unit = {},
    layout: String = LAYOUT_CARDS,
    emphasis: FeedEmphasis = FeedEmphasis.Medium,
    dimRead: Boolean = false,
    /** Whether the cards that carry a summary show it. Off keeps picture and headline. */
    showSummaries: Boolean = true,
    /**
     * Whether this article's source is one the reader consistently skips.
     *
     * Passed rather than looked up here so the set is read once per feed
     * instead of once per card, and so a card in a preview or a test is never
     * silently faded by state it did not ask for.
     */
    dimSource: Boolean = false,
    cluster: StoryCluster? = null,
    onPin: (Boolean) -> Unit = {},
    /** Gives back a breaking story's promotion. See `Article.dismissedAt`. */
    onDismissStory: () -> Unit = {},
    /**
     * "Not interested": the swipe and the menu entry. Null where a surface
     * does not offer it. Never offered on a saved or pinned card.
     */
    onNotInterested: (() -> Unit)? = null,
) {
    val notInterested = onNotInterested?.takeIf { !item.bookmarked && !item.pinned }
    val hasImage = !item.imageUrl.isNullOrBlank()
    // One place rather than five: every shape below takes this modifier, so a
    // read article fades whichever way the feed happens to be drawing it.
    //
    // A pin never fades. It is held at the top of the feed until it is
    // unpinned, which makes it permanently visible and therefore permanently
    // "read" the moment it is opened once — and a story somebody is following
    // through the day greying out after the first read is the pin failing at
    // the one job it has. The hide setting already exempts pins for the same
    // reason; this is the half that was missed.
    //
    // A skipped source fades the same way and for a related reason: both mean
    // "you are unlikely to want this". Same alpha rather than a second, milder
    // one, because two shades of faded on one screen is a distinction nobody
    // can read and everybody has to wonder about.
    //
    // The held-breaking-story exemption that used to sit here is gone with the
    // hold itself; see FeedItems. A pin still keeps its exemption, because a
    // pin is still kept on screen by the app rather than by the reader.
    val faded = (dimRead && item.article.readAt != 0L) || dimSource
    // Each story on its own panel, a shade off the background, with a narrow
    // strip of the background between: where one story ends and the next
    // begins, seen without reading. Cards and Magazine only. Mosaic's tiles
    // are already blocks, and List is a list.
    val panel = if (layout == LAYOUT_CARDS || layout == LAYOUT_MAGAZINE) {
        Modifier
            .padding(vertical = CARD_PANEL_GAP)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    } else {
        Modifier
    }
    val cardFaded = isCardFaded(faded, pinned = item.pinned)
    val shapeModifier =
        if (cardFaded) {
            modifier.then(panel).alpha(READ_ALPHA)
        } else {
            modifier.then(panel)
        }
    // Worked out only when the menu is opened, not for every card in the feed:
    // this is an answer to a question almost nobody asks of almost any article.
    val reasons = rememberWeightReasons(item, clusterOf(item, cluster))
    // Only the card that was promoted says so. Every member of a cluster
    // carries the same count, and putting it on all six would turn one story
    // into six claims that a story is happening.
    val coverage = cluster?.takeIf { it.leadId == item.id }?.sources
    val menu: @Composable (Color?) -> Unit = { tint ->
        ArticleOverflowMenu(
            onMoreLikeThis = onMoreLikeThis,
            onLessLikeThis = onLessLikeThis,
            onHideSource = onHideSource,
            onShare = onShare,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            reasons = reasons,
            pinned = item.pinned,
            onPin = onPin,
            // The same test that decides whether to show "Covered by N
            // sources": only the promoted card has a promotion to return.
            breaking = coverage != null,
            onDismissStory = onDismissStory,
            onNotInterested = notInterested,
            sourceName = shortSourceName(item.feedTitle),
            // Where the card has no save button of its own (review 84).
            onSave = if (LocalMenuOffersSave.current) onBookmark else null,
            saved = item.bookmarked,
        )
    }
    // Long-press for a look at the whole article without leaving the feed
    // (review 67).
    var previewing by remember { mutableStateOf(false) }
    if (previewing) {
        ArticlePreviewDialog(
            item = item,
            onOpen = { previewing = false; onClick() },
            onBookmark = onBookmark,
            onShare = { previewing = false; onShare() },
            onDismiss = { previewing = false },
        )
    }
    NotInterestedSwipe(onDismiss = notInterested) {
        CompositionLocalProvider(
            LocalPictureFade provides if (cardFaded) PICTURE_FADE else 1f,
            LocalCardLongPress provides { previewing = true },
        ) {
        when (feedCardShape(index, hasImage, layout, emphasis)) {
            FeedCardShape.Hero    -> ArticleCard(
                item, onClick, onBookmark, onShare, menu = menu, modifier = shapeModifier, coverage = coverage, lead = true,
                showSummary = showSummaries,
            )
            FeedCardShape.Card    -> ArticleCard(
                item, onClick, onBookmark, onShare, menu = menu, modifier = shapeModifier, coverage = coverage,
                showSummary = showSummaries,
            )
            FeedCardShape.Compact -> ArticleCompactRow(item, onClick, onBookmark, menu = menu, modifier = shapeModifier)
            FeedCardShape.Text    -> ArticleTextRow(
                item, onClick, onBookmark, menu = menu, modifier = shapeModifier,
                picture = layout == LAYOUT_LIST_PICTURES,
            )
            FeedCardShape.Tile    -> ArticleMosaicTile(
                item, onClick, onBookmark, menu = menu, modifier = shapeModifier,
                size = emphasis,
                coverage = coverage,
                showSummary = showSummaries,
            )
        }
        }
    }
}

/**
 * Four by three, as a lead story's picture always was, and never taller than
 * half the window.
 *
 * Four by three of a phone is a third of its screen. Of a Fold opened out it
 * was most of it: at 692 by 717 the header and one story were the whole
 * screen, the story's source line pushed off the bottom. On a phone on its
 * side the same. Past the cap the picture is cropped wider, as a photograph
 * across a wide screen should be.
 */
private fun Modifier.leadPicture(windowHeightPx: Int): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val height = leadPictureHeight(width, windowHeightPx)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

/** See [leadPicture]; a window height of nothing, before one is known, caps nothing. */
internal fun leadPictureHeight(widthPx: Int, windowHeightPx: Int): Int {
    val fourByThree = widthPx * 3 / 4
    return if (windowHeightPx <= 0) fourByThree else minOf(fourByThree, windowHeightPx / 2)
}

/**
 * The middle weight: image above, headline and summary below. This is the shape
 * the Cards layout was built around and it stays the relaxed-browsing view.
 *
 * And, with [lead], the lead story's: its picture taller, its headline below
 * the picture like every other card's and the same size. It was
 * laid over the picture on a dark shade, and over a busy photograph - a
 * publisher's logo, a crowd, a chart - it was the hardest text in the feed to
 * read, however dark the shade. Below it, it reads the same on any picture,
 * and the lead story is the same shape as the rest, only bigger.
 */
@Composable
fun ArticleCard(
    item: FeedItem,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable (Color?) -> Unit = {},
    coverage: Int? = null,
    lead: Boolean = false,
    showSummary: Boolean = true,
) {
    val context = LocalContext.current
    // Cards merge their texts for a screen reader, so the headline is
    // already read out; what was missing was any hint that the thing read
    // out can be opened at all.
    val openLabel = stringResource(R.string.action_open_article)
    val windowHeight = LocalWindowInfo.current.containerSize.height

    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardClick(openLabel, onClick)
    ) {
        val image = item.imageUrl
        // Edge to edge, square corners, outside the text's margin — the way the
        // launcher's own feed does it. A photograph inset by sixteen points and
        // rounded reads as a component on a page; the same photograph running
        // the full width reads as the article itself, which is what it is.
        if (!image.isNullOrBlank()) {
            AsyncImage(
                alpha = LocalPictureFade.current,
                model = image,
                contentDescription = null,
                placeholder = painterResource(articlePlaceholder()),
                error = painterResource(articlePlaceholder()),
                contentScale = ContentScale.Crop,
                modifier = if (lead) {
                    Modifier.fillMaxWidth().leadPicture(windowHeight)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                },
            )
        }

        Column(
            modifier = Modifier.padding(
                horizontal = CARD_MARGIN,
                vertical = 12.dp,
            )
        ) {
            if (item.pinned) {
                PinnedLine(modifier = Modifier.padding(top = 0.dp))
            }
            coverage?.let {
                CoverageLine(
                    sources = it,
                    modifier = Modifier.padding(top = if (item.pinned) 4.dp else 0.dp),
                )
            }

            Text(
                text = item.contentTitle,
                // The same size as every other card's. A size up, the lead's
                // headline stood out as a mistake once it sat below the
                // picture rather than on it; the taller picture says "lead"
                // on its own.
                style = MaterialTheme.typography.titleMedium,
                // Medium rather than bold: at a headline's size the weight
                // closed the letters up, and a two-line title read as a block.
                // The size and the summary below still set it apart.
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    top = if (coverage != null || item.pinned) 6.dp else 0.dp
                ),
            )

            val summary = item.article.description
            if (showSummary && summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                ArticleMeta(
                    category = item.feedTag,
                    source = item.feedTitle,
                    sourceId = item.sourceId,
                    articleId = item.id,
                    age = withReadingTime(context, item.relativeAge(context, LocalTimeFormat.current), item.article.readingMinutes),
                    ageCompact = item.relativeAge(context, TIME_RELATIVE),
                    // A size down. It is a byline, not a heading, and at
                    // labelLarge it competed with the summary above it.
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    iconUrl = item.feedIconUrl,
                )

                // Share is in the menu; see the hero above.
                CardActions {
                    SaveButton(saved = item.bookmarked, onSavedChange = onBookmark)
                    menu(null)
                }
            }
        }
    }
}

/**
 * The dense shape: headline left, thumbnail right. Most of the feed is this,
 * which is what makes the occasional hero read as an accent rather than noise.
 */
@Composable
fun ArticleCompactRow(
    item: FeedItem,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable (Color?) -> Unit = {},
) {
    val context = LocalContext.current
    // Cards merge their texts for a screen reader, so the headline is
    // already read out; what was missing was any hint that the thing read
    // out can be opened at all.
    val openLabel = stringResource(R.string.action_open_article)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardClick(openLabel, onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.contentTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                ArticleMeta(
                    category = item.feedTag,
                    source = item.feedTitle,
                    sourceId = item.sourceId,
                    articleId = item.id,
                    age = withReadingTime(context, item.relativeAge(context, LocalTimeFormat.current), item.article.readingMinutes),
                    ageCompact = item.relativeAge(context, TIME_RELATIVE),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconUrl = item.feedIconUrl,
                )
            }

            val image = item.imageUrl
            if (!image.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    alpha = LocalPictureFade.current,
                    model = image,
                    contentDescription = null,
                    placeholder = painterResource(articlePlaceholder()),
                    error = painterResource(articlePlaceholder()),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
            // Compact is most of the feed, so its actions have to be here
            // rather than only on the shapes with room. Saving used to sit in
            // the *else* branch above — the slot where a thumbnail would
            // otherwise go — so any compact row that had an image silently
            // lost its save button, which is most of them.
            // Together, not SpaceBetween. SpaceBetween distributes over the
            // column's height, which is the row's height — so on a row with a
            // two-line headline the overflow dot went to the very top and the
            // save button to the very bottom, with a gap between them that
            // read as two unrelated controls belonging to different things.
            // Two 20dp glyphs are a pair; they sit as one.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                menu(null)
                SaveButton(
                    saved = item.bookmarked,
                    onSavedChange = onBookmark,
                    size = 20.dp,
                )
            }
        }
    }
}


/**
 * The List layout's row: headline, source, nothing else.
 *
 * No thumbnail in List: images are what make a feed slow to get through, and
 * this is the shape for getting through a backlog. "List with pictures", a
 * layout of its own, adds a 56dp picture at the right ([picture]); in List
 * nothing is downloaded at all.
 */
@Composable
fun ArticleTextRow(
    item: FeedItem,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable (Color?) -> Unit = {},
    picture: Boolean = false,
) {
    val context = LocalContext.current
    // Cards merge their texts for a screen reader, so the headline is
    // already read out; what was missing was any hint that the thing read
    // out can be opened at all.
    val openLabel = stringResource(R.string.action_open_article)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardClick(openLabel, onClick)
    ) {
        Row(
            // The end margin too: CardActions reaches 13dp into it to line
            // its glyphs up with the text's edge, and without one the last
            // glyph was cut off at the screen's edge.
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.contentTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                ArticleMeta(
                    category = item.feedTag,
                    source = item.feedTitle,
                    sourceId = item.sourceId,
                    articleId = item.id,
                    age = withReadingTime(context, item.relativeAge(context, LocalTimeFormat.current), item.article.readingMinutes),
                    ageCompact = item.relativeAge(context, TIME_RELATIVE),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconUrl = item.feedIconUrl,
                )
            }
            val image = item.imageUrl
            if (picture && !image.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    alpha = LocalPictureFade.current,
                    model = image,
                    contentDescription = null,
                    placeholder = painterResource(articlePlaceholder()),
                    error = painterResource(articlePlaceholder()),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(LIST_PICTURE_SIZE)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
            CardActions {
                SaveButton(
                    saved = item.bookmarked,
                    onSavedChange = onBookmark,
                    size = 20.dp,
                )
                menu(null)
            }
        }
    }
}

/**
 * The Mosaic layout's tile: image, headline, source, in one column.
 *
 * Comes in three weights. The original relied on the images' own aspect ratios
 * for variety, but press photography is nearly all 16:9, so every tile came out
 * the same height and the grid read as a table. [size] now sets it: a Small
 * tile crops its image short and stops the headline at two lines, a Medium one
 * lets the image keep its shape, and a Large one takes both columns and earns
 * the width with a wider crop and a line of summary.
 *
 * There is no share button at any size — at half width it does not survive, and
 * the overflow menu carries share anyway.
 */
@Composable
fun ArticleMosaicTile(
    item: FeedItem,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable (Color?) -> Unit = {},
    size: FeedEmphasis = FeedEmphasis.Medium,
    coverage: Int? = null,
    showSummary: Boolean = true,
) {
    val context = LocalContext.current
    // Cards merge their texts for a screen reader, so the headline is
    // already read out; what was missing was any hint that the thing read
    // out can be opened at all.
    val openLabel = stringResource(R.string.action_open_article)
    val large = size == FeedEmphasis.Large
    val image = item.imageUrl
    // A URL is a promise, not a picture. Until it resolves — and if it 404s,
    // for ever — there is nothing there, and the tile was still drawing the
    // scrim and the two buttons over the nothing: a grey band and a gap above
    // the headline. Treat a failed image as no image.
    var imageFailed by remember(image) { mutableStateOf(false) }
    val hasImage = !image.isNullOrBlank() && !imageFailed

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(6.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .cardClick(openLabel, onClick)
    ) {
        if (hasImage) {
            // The two buttons sit on the picture rather than under the text.
            // They are IconButtons, so each one is 48dp tall whatever size the
            // icon inside it is — in a row of their own under a half-width
            // tile that is a band of empty space taller than the line of text
            // beside it, which is where the gaps in the grid were coming from.
            // Over the image they cost nothing: the space is already spent.
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    alpha = LocalPictureFade.current,
                    model = image,
                    contentDescription = null,
                    onError = { imageFailed = true },
                    placeholder = painterResource(articlePlaceholder()),
                    // Cropped to a set ratio at the two fixed sizes, so the
                    // tile's height is the layout's decision rather than the
                    // picture's.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Medium used to take its height from the image, which
                        // meant no height at all until the image arrived: the
                        // tile composed short, then jumped, and the column
                        // beside it reflowed every time one loaded. A ratio
                        // that is reserved up front costs one crop and gives
                        // the grid a stable shape from the first frame.
                        .aspectRatio(
                            when (size) {
                                FeedEmphasis.Small  -> 16f / 9f
                                FeedEmphasis.Medium -> 4f / 3f
                                FeedEmphasis.Large  -> 2f / 1f
                            }
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                // A dark disc under each control. A photograph can be white
                // in the corner, and a white icon on it disappears; the fade
                // across the top that was here before had thinned to a
                // quarter by the icons' middle, and the menu's three dots
                // vanished on a white glasshouse roof.
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnPhoto {
                        SaveButton(
                            saved = item.bookmarked,
                            onSavedChange = onBookmark,
                            size = 20.dp,
                            onImage = true,
                        )
                    }
                    OnPhoto { menu(Color.White) }
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            if (item.pinned) {
                PinnedLine()
                Spacer(Modifier.height(4.dp))
            }
            coverage?.let {
                CoverageLine(sources = it)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = item.contentTitle,
                style = if (large) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = when (size) {
                    // With no picture there is room for a third line (review 84).
                    FeedEmphasis.Small  -> if (hasImage) 2 else 3
                    FeedEmphasis.Medium -> 4
                    FeedEmphasis.Large  -> 3
                },
                overflow = TextOverflow.Ellipsis,
            )
            if (large && showSummary) {
                val summary = item.article.description
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val meta: @Composable (Modifier) -> Unit = { metaModifier ->
                ArticleMeta(
                    category = item.feedTag,
                    source = item.feedTitle,
                    sourceId = item.sourceId,
                    articleId = item.id,
                    age = withReadingTime(context, item.relativeAge(context, LocalTimeFormat.current), item.article.readingMinutes),
                    ageCompact = item.relativeAge(context, TIME_RELATIVE),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = metaModifier,
                    iconUrl = item.feedIconUrl,
                )
            }
            // Only where there was no image to put them on: the ⋮ alone, with
            // Save inside it (review 84). Both buttons on the line left a
            // narrow tile four letters of the source's name ("Al Ja…") and a
            // band of empty space under it. The ⋮ keeps its 48dp target but
            // counts as one line of text high, so the tile ends with the line.
            Row(verticalAlignment = Alignment.CenterVertically) {
                meta(Modifier.weight(1f))
                if (!hasImage) {
                    if (item.bookmarked) {
                        // Saved, still visible at a glance without its button.
                        Icon(
                            imageVector = Phosphor.BookBookmark,
                            contentDescription = stringResource(R.string.action_unsave),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = CARD_ACTIONS_EDGE)
                            .layout { measurable, constraints ->
                                val p = measurable.measure(constraints)
                                val h = TILE_LINE_HEIGHT.roundToPx()
                                layout(p.width, h) { p.place(0, (h - p.height) / 2) }
                            },
                    ) {
                        CompositionLocalProvider(LocalMenuOffersSave provides true) { menu(null) }
                    }
                }
            }
        }
    }
}

/** The source line's height in a tile; the ⋮ beside it draws over its padding. */
private val TILE_LINE_HEIGHT = 20.dp

/**
 * "SFGATE · 10h" — the line under every headline.
 *
 * Two texts rather than one string, because a single one puts the ellipsis at
 * the end and the age is what gets eaten: some feeds title themselves with a
 * whole sentence ("io9 - We come from the future."), which overruns a compact
 * row's 231dp on its own. The age is given its intrinsic width first, so only
 * the source name shortens and the age is always readable.
 */
/**
 * Narrows the feed to one source, where that is possible.
 *
 * Takes the article the tap came from as well as the source, because the
 * filtered feed is a shorter list and the scroll index kept across the change
 * lands on a different story. Both surfaces provide it — the panel intercepts
 * back for the filter the same way it already does for search.
 */
val LocalFocusSource = compositionLocalOf<((String, String) -> Unit)?> { null }

/**
 * How article times are written: `TIME_RELATIVE`, `TIME_CLOCK` or `TIME_BOTH`.
 *
 * Provided once at the top of each surface - the app and the launcher panel -
 * rather than read from the preferences by every card's byline.
 */
val LocalTimeFormat = compositionLocalOf { TIME_RELATIVE }

/** Whether the card's ⋮ menu carries Save, because the card has no button for it. */
val LocalMenuOffersSave = compositionLocalOf { false }

/** What a long press on a card does; null where nothing does. See ArticlePreviewDialog. */
val LocalCardLongPress = compositionLocalOf<(() -> Unit)?> { null }

/**
 * A card's tap, and its long press when the surface offers one: a tap opens
 * the article, a long press shows its preview (review 67).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.cardClick(openLabel: String, onClick: () -> Unit): Modifier {
    val longPress = LocalCardLongPress.current
    val previewLabel = stringResource(R.string.action_preview_article)
    return combinedClickable(
        onClickLabel = openLabel,
        role = Role.Button,
        onLongClickLabel = if (longPress != null) previewLabel else null,
        onLongClick = longPress,
        onClick = onClick,
    )
}

/** The byline under a headline, on every card and in the reader. */
@Composable
internal fun ArticleMeta(
    source: String,
    age: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    sourceId: String? = null,
    articleId: String? = null,
    iconUrl: String? = null,
    onImage: Boolean = false,
    /** The article's category, after the mark; nothing when it has none. */
    category: String = "",
    /**
     * A shorter [age] for a narrow line: the relative age alone, without the
     * clock time or reading time. Null when there is nothing shorter.
     */
    ageCompact: String? = null,
) {
    // Null on a surface that cannot narrow the feed.
    // A CompositionLocal rather than another parameter because this row is
    // reached through five card shapes, and threading an optional callback
    // through all of them to be ignored by one surface is how the two feeds
    // drift apart.
    val focusSource = LocalFocusSource.current

    // The mark, the category and the name are the tap target, and nothing
    // else. The age sits beside them and the buttons beyond that; a target
    // that swallowed the whole row would take taps meant for the article,
    // since this row sits directly under the headline.
    // Said aloud, since "double-tap to activate" on a source's name says
    // nothing about narrowing the feed. Shorter than the 48dp minimum to
    // look at, but Compose widens a small target's touch area to 48dp
    // where nothing closer competes for the touch, so it is not made
    // taller, which would add height to every card.
    val focusLabel = stringResource(R.string.focus_on_source)
    val identity = if (focusSource != null && sourceId != null && articleId != null) {
        Modifier.clickable(onClickLabel = focusLabel) { focusSource(sourceId, articleId) }
    } else {
        Modifier
    }
    // The publication, not its strapline; see shortSourceName.
    val name = shortSourceName(source)
    val measurer = rememberTextMeasurer()
    val categoryText = if (category.isNotBlank()) "${category.uppercase()} · " else ""

    // Laid out by hand so the source's name is the last thing to go (review
    // 43: in a narrow Mosaic tile it shrank to ".." while the category and
    // the clock time stayed whole). When the line is short of room, the
    // category goes first, since the chip at the top already says it, then
    // the clock time and reading time. And the age follows the name's last
    // letter, not the end of the space the name was offered (review 40:
    // "Andr…  · 1h").
    Layout(
        modifier = modifier,
        content = {
            Box(identity.clearAndSetSemantics { }) {
                SourceMark(iconUrl = iconUrl, sourceName = source, onImage = onImage)
            }
            Text(
                text = categoryText,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = identity.clearAndSetSemantics { },
            )
            Text(
                text = name,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = identity,
            )
            Text(text = " · $age", style = style, color = color, maxLines = 1, softWrap = false)
            Text(text = " · ${ageCompact ?: age}", style = style, color = color, maxLines = 1, softWrap = false)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val mark = measurables[0].measure(loose)
        val gap = 6.dp.roundToPx()
        val cat = measurables[1].measure(loose)
        val ageFull = measurables[3].measure(loose)
        val ageShort = measurables[4].measure(loose)
        val nameFull = measurables[2].maxIntrinsicWidth(constraints.maxHeight)
        val max = if (constraints.hasBoundedWidth) constraints.maxWidth
        else mark.width + gap + cat.width + nameFull + ageFull.width
        val plan = bylinePlan(
            available = max - mark.width - gap,
            category = if (categoryText.isEmpty()) 0 else cat.width,
            name = nameFull,
            age = ageFull.width,
            ageShort = ageShort.width,
        )
        val ageP = if (plan.shortAge) ageShort else ageFull
        val nameWidth = if (plan.nameRoom >= nameFull) nameFull else {
            // What the ellipsised name really takes, so the age can follow it.
            val cut = measurer.measure(
                text = name,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = plan.nameRoom.coerceAtLeast(0)),
            )
            kotlin.math.ceil(cut.getLineRight(0)).toInt().coerceIn(0, plan.nameRoom.coerceAtLeast(0))
        }
        // Measured at the width the cut was worked out for, so it cuts in the
        // same place; any narrower, and it cut a word earlier and left the
        // gap this was meant to close ("telegraph.co.uk …  · 4m"). Placed by
        // the width its letters really take.
        val nameP = measurables[2].measure(
            Constraints(maxWidth = if (nameWidth < nameFull) plan.nameRoom.coerceAtLeast(0) else nameFull, maxHeight = loose.maxHeight)
        )
        val parts = buildList {
            add(mark); if (plan.showCategory) add(cat); add(nameP); add(ageP)
        }
        val height = parts.maxOf { it.height }
        // At least the width the row was told to take: smaller, and Compose
        // centres it in that width, which put the byline in the middle of a
        // full-width card (owner's screenshot, 5 November).
        // Each part's advance: the name's is its letters, not its box.
        val advance = parts.map { if (it === nameP) nameWidth else it.width }
        val width = (mark.width + gap + (if (plan.showCategory) cat.width else 0) + nameWidth + ageP.width)
            .coerceAtMost(max)
            .coerceAtLeast(constraints.minWidth)
        layout(width, height) {
            var x = 0
            parts.forEachIndexed { i, p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += advance[i] + if (i == 0) gap else 0
            }
        }
    }
}

/** What the byline shows in the room it has; see [bylinePlan]. */
internal data class BylinePlan(val showCategory: Boolean, val shortAge: Boolean, val nameRoom: Int)

/**
 * Fits the byline into [available] (after the mark). Everything if it fits;
 * otherwise the category goes first, then the long age (clock time and
 * reading time) gives way to the short one, until the source's whole name
 * fits. A whole name is worth more than the clock time: "The Independent ·
 * 7m" rather than "The Independ… · 12m · 16:14 · 2 min" (owner's
 * screenshots, 5 November). When even the shortest line cannot hold the
 * whole name, the name gets all the room there is.
 */
internal fun bylinePlan(available: Int, category: Int, name: Int, age: Int, ageShort: Int): BylinePlan {
    for ((withCategory, short) in listOf(true to false, false to false, false to true)) {
        if (withCategory && category == 0) continue
        val room = available - (if (withCategory) category else 0) - (if (short) ageShort else age)
        if (room >= name) return BylinePlan(withCategory && category > 0, short, name)
    }
    return BylinePlan(false, true, (available - ageShort).coerceAtLeast(0))
}

/**
 * The age with the reading time after it, "5h · 4 min", or the age alone
 * when no time was counted. See `Article.readingMinutes`.
 */
internal fun withReadingTime(context: android.content.Context, age: String, minutes: Int): String =
    if (minutes <= 0) age
    else age + " · " + context.getString(com.saulhdev.feeder.R.string.reading_minutes, minutes)

/**
 * The article's age, in the compact form the feed uses: "5h", "3d", "2w".
 *
 * The field is primarySortTime rather than pubDate, because the list is ordered
 * by primarySortTime and showing the other meant the date on a card could
 * disagree with the position it appeared in.
 */
private fun FeedItem.relativeAge(context: android.content.Context, format: String): String =
    formatArticleTime(context, article.primarySortTime.toEpochMilliseconds(), format)

/**
 * The stand-in for an article image, in the theme's own colourway.
 *
 * Chosen from the rendered surface rather than from the theme *setting*: the
 * setting has three values and one of them is pure black, and what the
 * placeholder has to sit against is whatever actually got painted.
 *
 * It is `nodpi` and one file per colourway rather than five density buckets —
 * it is a full-width decorative field, so there is no crispness to preserve at
 * a particular density, only bytes to waste on four extra copies.
 */
@Composable
fun articlePlaceholder(): Int =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) R.drawable.placeholder_article_dark
    else R.drawable.placeholder_article_light

/**
 * The margin every piece of text in the feed lines up on.
 *
 * Named because three different rows were using three values a couple of
 * points apart — enough that the glance row, the chips and the article text
 * each started in a slightly different place, which reads as sloppiness rather
 * than as a difference. Pictures ignore it on purpose and run full width.
 */
val CARD_MARGIN = 16.dp

/**
 * Half the strip of background between two stories' panels: six points in
 * all. Twelve was tried first and cost a story a screen; the panel's shade
 * does most of the separating, and the strip only has to show it is there.
 */
val CARD_PANEL_GAP = 3.dp

/** List's picture, when it has one: small enough that rows hardly grow. */
val LIST_PICTURE_SIZE = 56.dp

/**
 * How far a read article fades, when the reader has asked for that.
 *
 * The whole card went to 55%, its text with it, which took the headline
 * below the 4.5:1 contrast Android asks for small text. Now the text keeps
 * 80% and the picture fades further, to about half (owner, 25 October:
 * text-heavy cards with small pictures would not show a picture-only fade).
 * The owner suggested 75%; the light theme's summaries came out at 4.2:1
 * there, and 80% is the nearest that passes. See ReadFadeTest.
 */
internal const val READ_ALPHA = 0.80f

/** The picture's own fade inside a faded card: 0.80 × 0.62 ≈ half. */
internal const val PICTURE_FADE = 0.62f

/** Read by every picture in a card; 1 unless the card is faded. */
val LocalPictureFade = compositionLocalOf { 1f }

/**
 * The mark on an article the reader is following.
 *
 * Without it a pinned article is simply first for no visible reason, which
 * reads as the sort being broken — and the reader has no way to find the thing
 * they need to press to release it.
 */
@Composable
private fun PinnedLine(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Phosphor.Asterisk,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.pinned_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** The cluster map [rememberWeightReasons] wants, from the one cluster a card has. */
private fun clusterOf(item: FeedItem, cluster: StoryCluster?): Map<String, StoryCluster> =
    if (cluster == null) emptyMap() else mapOf(item.id to cluster)

/**
 * "Covered by 5 sources" — why this card is the size it is, said out loud.
 *
 * The count is the whole claim and it is worth stating plainly: a reader who
 * sees one story given the largest card should be able to tell at a glance
 * whether that is because several papers ran it or because the app guessed.
 */
@Composable
private fun CoverageLine(
    sources: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Phosphor.Megaphone,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = pluralStringResource(R.plurals.covered_by_sources, sources, sources),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}


/**
 * Whether a card is drawn faded.
 *
 * Pulled out of the composable so the exemption can be argued with in a test:
 * an article the app is keeping on screen by itself cannot be dimmed for
 * having been seen, because it was the app that put it there and kept it
 * there.
 *
 * There were two. The second was a clustered story held to the top by a
 * sticky header, which needed the exemption for a mechanical reason as well —
 * a sticky header is drawn over the list, so fading one did not dim a card,
 * it opened a window onto the articles sliding underneath. The hold is gone;
 * see FeedItems.
 */
internal fun isCardFaded(faded: Boolean, pinned: Boolean): Boolean =
    faded && !pinned

/**
 * Save and the menu, as one pair at the end of a byline.
 *
 * Each is a 48dp touch target around a 22dp glyph, so side by side the glyphs
 * sat a whole button apart, and the menu's dots a button's padding in from
 * the edge - while the source icon at the other end sat right on it. Drawn
 * [CARD_ACTIONS_OVERLAP] closer together and [CARD_ACTIONS_EDGE] further out,
 * the dots line up with the edge the source icon starts from. Only drawn
 * there: both targets keep their full size, and overlap where they meet.
 */
@Composable
fun CardActions(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(-CARD_ACTIONS_OVERLAP),
        modifier = Modifier.offset(x = CARD_ACTIONS_EDGE),
    ) { content() }
}

/**
 * A white control over a photograph, on a disc dark enough to read on any
 * picture: white on 45% black is at least 3:1 even over a white sky.
 */
@Composable
private fun OnPhoto(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.drawBehind {
            drawCircle(color = Color.Black.copy(alpha = PHOTO_DISC_ALPHA), radius = PHOTO_DISC_RADIUS.toPx())
        },
    ) { content() }
}

internal const val PHOTO_DISC_ALPHA = 0.45f
private val PHOTO_DISC_RADIUS = 16.dp


/** Half a button's spare width: where a 22dp glyph's box meets a 48dp target's edge. */
val CARD_ACTIONS_EDGE = 13.dp

/** How much closer the two glyphs sit than two buttons side by side would put them. */
val CARD_ACTIONS_OVERLAP = 12.dp
