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

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.components.SaveButton
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Asterisk
import com.saulhdev.feeder.ui.icons.phosphor.Megaphone
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.formatArticleAge

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
) {
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
    val shapeModifier =
        if (isCardFaded(faded, pinned = item.pinned)) {
            modifier.alpha(READ_ALPHA)
        } else {
            modifier
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
        )
    }
    when (feedCardShape(index, hasImage, layout, emphasis)) {
        FeedCardShape.Hero    -> ArticleHeroCard(
            item, onClick, onBookmark, onShare, menu = menu, modifier = shapeModifier, coverage = coverage,
        )
        FeedCardShape.Card    -> ArticleCard(
            item, onClick, onBookmark, onShare, menu = menu, modifier = shapeModifier, coverage = coverage,
        )
        FeedCardShape.Compact -> ArticleCompactRow(item, onClick, onBookmark, menu = menu, modifier = shapeModifier)
        FeedCardShape.Text    -> ArticleTextRow(item, onClick, onBookmark, menu = menu, modifier = shapeModifier)
        FeedCardShape.Tile    -> ArticleMosaicTile(
            item, onClick, onBookmark, menu = menu, modifier = shapeModifier,
            size = emphasis,
            coverage = coverage,
        )
    }
}

/**
 * The anchor shape: a tall image with the headline laid over it.
 *
 * The scrim is a gradient rather than a flat overlay because a flat one has to
 * be dark enough for the worst image, which then greys out every good one.
 */
@Composable
fun ArticleHeroCard(
    item: FeedItem,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable (Color?) -> Unit = {},
    coverage: Int? = null,
) {
    val context = LocalContext.current
    // Cards merge their texts for a screen reader, so the headline is
    // already read out; what was missing was any hint that the thing read
    // out can be opened at all.
    val openLabel = stringResource(R.string.action_open_article)
    val windowHeight = LocalWindowInfo.current.containerSize.height

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .leadPicture(windowHeight)
                // The shade reaches above the text; never above the picture.
                .clipToBounds()
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(articlePlaceholder()),
                error = painterResource(articlePlaceholder()),
                modifier = Modifier.fillMaxSize(),
            )
            // The shade follows the text, not the picture: clear until a
            // little above the first line, then deepening to the bottom. It
            // was fixed to the card, clear down to 30% of it, and a headline
            // that ran taller (at 200% text, or on a narrow screen) put its
            // top line in white on the bare photograph. Where the text sits
            // as it usually does, the shade lands where the old one did, and
            // the top of the picture is still left alone.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .drawBehind { drawTextShade() }
                    .padding(16.dp)
            ) {
                if (item.pinned) {
                    PinnedLine(color = Color.White)
                    Spacer(Modifier.height(4.dp))
                }
                coverage?.let {
                    CoverageLine(sources = it, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                }
                val category = item.feedTag
                if (category.isNotBlank()) {
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = item.contentTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArticleMeta(
                        source = item.feedTitle,
                        sourceId = item.sourceId,
                        articleId = item.id,
                        age = item.relativeAge(context),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f),
                        iconUrl = item.feedIconUrl,
                        onImage = true,
                    )
                    // Share lives in the menu. Two ways to one action on
                    // every card was a button the card did not need.
                    CardActions {
                        SaveButton(
                            saved = item.bookmarked,
                            onSavedChange = onBookmark,
                            onImage = true,
                        )
                        menu(Color.White)
                    }
                }
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

/** How far above the text the shade starts to show. */
private val SHADE_HEADROOM = 72.dp

/**
 * Behind a lead story's text, from [SHADE_HEADROOM] above it to the bottom:
 * the three stops the card had, anchored to the text instead of the card.
 */
private fun DrawScope.drawTextShade() {
    val headroom = SHADE_HEADROOM.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            headroom / (headroom + size.height) to Color.Black.copy(alpha = 0.45f),
            1f to Color.Black.copy(alpha = 0.88f),
            startY = -headroom,
            endY = size.height,
        ),
        topLeft = Offset(0f, -headroom),
        size = Size(size.width, size.height + headroom),
    )
}

/**
 * The middle weight: image above, headline and summary below. This is the shape
 * the Cards layout was built around and it stays the relaxed-browsing view.
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
) {
    val context = LocalContext.current
    // Cards merge their texts for a screen reader, so the headline is
    // already read out; what was missing was any hint that the thing read
    // out can be opened at all.
    val openLabel = stringResource(R.string.action_open_article)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
    ) {
        val image = item.imageUrl
        // Edge to edge, square corners, outside the text's margin — the way the
        // launcher's own feed does it. A photograph inset by sixteen points and
        // rounded reads as a component on a page; the same photograph running
        // the full width reads as the article itself, which is what it is.
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                placeholder = painterResource(articlePlaceholder()),
                error = painterResource(articlePlaceholder()),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    top = if (coverage != null || item.pinned) 6.dp else 0.dp
                ),
            )

            val summary = item.article.description
            if (summary.isNotBlank()) {
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
                    source = item.feedTitle,
                    sourceId = item.sourceId,
                    articleId = item.id,
                    age = item.relativeAge(context),
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
        ArticleDivider()
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
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val category = item.feedTag
                if (category.isNotBlank()) {
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                }
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
                    source = item.feedTitle,
                    sourceId = item.sourceId,
                    articleId = item.id,
                    age = item.relativeAge(context),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconUrl = item.feedIconUrl,
                )
            }

            val image = item.imageUrl
            if (!image.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
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
        ArticleDivider()
    }
}


/**
 * The List layout's row: headline, source, nothing else.
 *
 * No thumbnail at any width — that is the point of the layout rather than a
 * limitation of it. Images are what make a feed slow to get through, and this
 * is the shape for getting through a backlog.
 */
@Composable
fun ArticleTextRow(
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
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
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
                    source = item.feedTitle,
                    sourceId = item.sourceId,
                    articleId = item.id,
                    age = item.relativeAge(context),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconUrl = item.feedIconUrl,
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
        ArticleDivider()
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
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
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
                // A short scrim under the controls only. A photograph can be
                // white in the corner, and a white icon on it disappears.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                1f to Color.Transparent,
                            )
                        )
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SaveButton(
                        saved = item.bookmarked,
                        onSavedChange = onBookmark,
                        size = 20.dp,
                        onImage = true,
                    )
                    menu(Color.White)
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
                    FeedEmphasis.Small  -> 2
                    FeedEmphasis.Medium -> 4
                    FeedEmphasis.Large  -> 3
                },
                overflow = TextOverflow.Ellipsis,
            )
            if (large) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArticleMeta(
                    source = item.feedTitle,
                    sourceId = item.sourceId,
                    articleId = item.id,
                    age = item.relativeAge(context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    iconUrl = item.feedIconUrl,
                )
                // Only where there was no image to put them on. The buttons
                // keep their full 48dp targets either way — the fix was to
                // stop them claiming a row of their own, not to shrink them.
                if (!hasImage) {
                    CardActions {
                        SaveButton(
                            saved = item.bookmarked,
                            onSavedChange = onBookmark,
                            size = 18.dp,
                        )
                        menu(null)
                    }
                }
            }
        }
    }
}

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

@Composable
private fun ArticleMeta(
    source: String,
    age: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    sourceId: String? = null,
    articleId: String? = null,
    iconUrl: String? = null,
    onImage: Boolean = false,
) {
    // Null on a surface that cannot narrow the feed.
    // A CompositionLocal rather than another parameter because this row is
    // reached through five card shapes, and threading an optional callback
    // through all of them to be ignored by one surface is how the two feeds
    // drift apart.
    val focusSource = LocalFocusSource.current

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // The mark and the name together, and nothing else. The age sits
        // beside them and the buttons beyond that; a target that swallowed
        // the whole row would take taps meant for the article, since this row
        // sits directly under the headline.
        val identity = if (focusSource != null && sourceId != null && articleId != null) {
            Modifier.clickable { focusSource(sourceId, articleId) }
        } else {
            Modifier
        }
        Row(
            modifier = identity.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceMark(iconUrl = iconUrl, sourceName = source, onImage = onImage)
            Spacer(Modifier.width(6.dp))
            Text(
                text = source,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = " · $age",
            style = style,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * The hairline between articles.
 *
 * Replaces the rounded container each article used to sit in. A column of
 * filled boxes puts a border around every headline and reads as a stack of
 * separate objects; a divider says "next item" with one line and lets the
 * content own the width. Inset from the left so it starts under the text
 * rather than cutting across the whole page.
 */
@Composable
private fun ArticleDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * The article's age, in the compact form the feed uses: "5h", "3d", "2w".
 *
 * The field is primarySortTime rather than pubDate, because the list is ordered
 * by primarySortTime and showing the other meant the date on a card could
 * disagree with the position it appeared in.
 */
private fun FeedItem.relativeAge(context: android.content.Context): String =
    formatArticleAge(context, article.primarySortTime.toEpochMilliseconds())

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

/** How far a read article fades, when the reader has asked for that. */
private const val READ_ALPHA = 0.55f

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

/** Half a button's spare width: where a 22dp glyph's box meets a 48dp target's edge. */
val CARD_ACTIONS_EDGE = 13.dp

/** How much closer the two glyphs sit than two buttons side by side would put them. */
val CARD_ACTIONS_OVERLAP = 12.dp
