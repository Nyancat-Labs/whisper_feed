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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.saulhdev.feeder.ui.components.BrandTitle
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.manager.glance.GlanceState
import com.saulhdev.feeder.ui.components.PullToRefreshLazyColumn
import com.saulhdev.feeder.ui.components.PullToRefreshStaggeredGrid
import com.saulhdev.feeder.ui.theme.reducedMotion
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.components.HeaderToggleAction
import com.saulhdev.feeder.ui.components.HeaderAction
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import kotlinx.coroutines.launch
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import com.saulhdev.feeder.ui.pages.SortFilterSheet
import com.saulhdev.feeder.ui.components.FeedSearchBar
import com.saulhdev.feeder.ui.components.SourceFilterBar
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.ui.icons.phosphor.Filter
import com.saulhdev.feeder.ui.icons.phosphor.Filtered
import com.saulhdev.feeder.ui.icons.phosphor.MagnifyingGlass
import com.saulhdev.feeder.ui.components.FeedEmptyReason
import com.saulhdev.feeder.ui.components.FeedEmptyState
import com.saulhdev.feeder.ui.components.SearchEmptyState

/**
 * The whole minus-one surface: header, category strip and article list.
 *
 * These are one composable rather than three converted pieces because they are
 * joined by scrolling. The View version coupled them through CoordinatorLayout
 * — the app bar collapsed in response to the RecyclerView's nested scroll, a
 * SwipeRefreshLayout wrapped the list, and a scroll listener drove the
 * scroll-to-top button. Converting the header or the list alone would have
 * meant building View/Compose nested-scroll interop to preserve that, only to
 * delete it when the other half followed. Here the coupling is native.
 *
 * Insets are passed in rather than read from `WindowInsets`. This window is
 * created against the launcher's token with an unusual flag set, so the
 * overlay's own inset listener is the value already known to be correct.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScaffold(
    articles: List<FeedItem>,
    categories: List<String>,
    selectedCategories: Set<String>,
    isRefreshing: Boolean,
    isFilterActive: Boolean,
    isShowingBookmarks: Boolean,
    glanceState: GlanceState,
    topInset: Dp,
    bottomInset: Dp,
    onCategoriesChange: (Set<String>) -> Unit,
    onRefresh: () -> Unit,
    onArticleClick: (FeedItem) -> Unit,
    onBookmark: (FeedItem, Boolean) -> Unit,
    onShare: (FeedItem) -> Unit,
    onMoreLikeThis: (FeedItem) -> Unit,
    onLessLikeThis: (FeedItem) -> Unit,
    onHideSource: (FeedItem) -> Unit,
    hiddenSource: FeedItem?,
    onUndoHideSource: () -> Unit,
    onDismissHideSource: () -> Unit,
    layout: String,
    isFilterSheetOpen: Boolean,
    onFilterSheetOpenChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    /** The one source the feed is narrowed to, or null for all of them. */
    focusedSource: String? = null,
    /**
     * The narrowing [articles] was actually built under.
     *
     * Not the same as [focusedSource] for the moment between the tap and the
     * query coming back, which is the moment that matters here. See
     * [AnchorFeedOnFocusChange].
     */
    appliedFocus: String? = null,
    /** The article a narrowing started from, to land on at both its edges. */
    focusAnchor: String? = null,
    onFocusSource: (String, String) -> Unit = { _, _ -> },
    onClearFocusedSource: () -> Unit = {},
    onBookmarksClick: () -> Unit,
    onSettings: () -> Unit,
    onArticleSeen: (FeedItem) -> Unit = {},
    onArticleDwell: (String, Long) -> Unit = { _, _ -> },
    /** Writes any batched dwell now; see TrackReading's onLeave. */
    onDwellFlush: () -> Unit = {},
    onPin: (FeedItem, Boolean) -> Unit = { _, _ -> },
    /** Gives back a breaking story's promotion. See `Article.dismissedAt`. */
    onDismissStory: (FeedItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)

    // Matches the old scroll listener's threshold, which only offered the jump
    // back once a few articles had gone by.
    val isGrid = feedLayoutIsGrid(layout)
    val showScrollToTop by remember(isGrid) {
        derivedStateOf {
            if (isGrid) gridState.firstVisibleItemIndex > 5
            else listState.firstVisibleItemIndex > 5
        }
    }

    // What is drawn: the articles with their sizes and stories, built off the
    // main thread and never swapped in under a moving finger. See FeedFrame.
    val frame = rememberFeedFrame(articles, appliedFocus) {
        listState.isScrollInProgress || gridState.isScrollInProgress
    }

    // Tapping a source narrows the feed to it, and backing out widens it
    // again; both land on the article the tap came from. See FocusAnchor.
    AnchorFeedOnFocusChange(
        appliedFocus = frame.focus,
        anchorId = focusAnchor,
        articles = frame.articles,
        isGrid = isGrid,
        listState = listState,
        gridState = gridState,
    )

    // Hiding a source is one tap with no confirmation, so the way back is
    // offered here rather than left to the sources screen.
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(hiddenSource) {
        val item = hiddenSource ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.source_hidden, item.feedTitle),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoHideSource()
        else onDismissHideSource()
    }

    // Offered on this surface too, now that the panel is known to handle back
    // for its own state — it already does for the filter sheet and for search.
    // The earlier reasoning for withholding it, that the gesture belongs to
    // the launcher, was simply wrong.
    CompositionLocalProvider(LocalFocusSource provides onFocusSource) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (focusedSource != null) {
                // Ahead of the search bar, because the two cannot both be
                // showing and this is the more specific state: a search can be
                // started from inside a narrowed feed, and coming back out of
                // it should land here rather than at the whole feed.
                val focusedFeed = remember(focusedSource, articles) {
                    articles.firstOrNull { it.sourceId == focusedSource }
                }
                SourceFilterBar(
                    title = focusedFeed?.feedTitle.orEmpty(),
                    iconUrl = focusedFeed?.feedIconUrl,
                    onClear = onClearFocusedSource,
                    // This window's own insets are not to be trusted; topInset
                    // is measured by the overlay's listener. Same reasoning as
                    // the search bar below.
                    windowInsets = WindowInsets(0),
                    modifier = Modifier.padding(top = topInset),
                )
            } else if (isSearching) {
                FeedSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = { onSearchingChange(false) },
                    // This window's own insets are not to be trusted; topInset
                    // is measured by the overlay's listener. Same reasoning as
                    // the app bar below.
                    windowInsets = WindowInsets(0),
                    modifier = Modifier.padding(top = topInset),
                )
            } else TopAppBar(
                // The same title as the app's own header, from one place. See
                // BrandTitle for why the name sometimes stands aside.
                title = { BrandTitle() },
                actions = {
                    // Both of these are modes rather than one-shot actions, so
                    // they have to show when they are on — the View header only
                    // ever tinted the bookmark toggle, and the filter gave no
                    // indication at all that it was narrowing the feed.
                    HeaderAction(
                        icon = Phosphor.MagnifyingGlass,
                        description = stringResource(R.string.action_search),
                        onClick = { onSearchingChange(true) },
                    )
                    // The same two-state icon the app uses. A funnel that
                    // looks identical whether or not it is narrowing the feed
                    // says nothing; the filled one says the list you are
                    // looking at is not the whole list.
                    HeaderToggleAction(
                        icon = if (isFilterActive) Phosphor.Filtered else Phosphor.Filter,
                        description = stringResource(R.string.pref_cat_filters),
                        active = isFilterActive,
                        onClick = { onFilterSheetOpenChange(true) },
                    )
                    HeaderToggleAction(
                        painter = painterResource(R.drawable.ic_whisper_save),
                        description = stringResource(R.string.title_bookmarks),
                        active = isShowingBookmarks,
                        onClick = onBookmarksClick,
                    )
                    // One action, not a menu: reload duplicates
                    // pull-to-refresh and restart was a development leftover,
                    // which left Settings as the only real entry.
                    //
                    // A gear, not three dots. Three dots promise a menu, and
                    // this opens Settings directly — and the app's header has
                    // said so with a gear since the menu went. Two icons for
                    // one destination is the reader having to learn the app
                    // twice.
                    HeaderAction(
                        icon = Phosphor.GearSix,
                        description = stringResource(R.string.title_settings),
                        onClick = onSettings,
                    )
                },
                // The overlay draws its own background, so the bar stays
                // transparent and the feed shows through behind it.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                scrollBehavior = scrollBehavior,
                // The bar insets for the status bar itself by default, and
                // this window is already given the inset explicitly — see
                // topInset, which is measured by the overlay's own listener
                // because this window's own insets are not to be trusted.
                // Both applied means two status bars of empty space above the
                // glance row once the bar scrolls away.
                windowInsets = WindowInsets(0),
                modifier = Modifier.padding(top = topInset),
            )

            // The glance row and the chips used to stand here, above the
            // list and fixed: only the app bar scrolled away, so on a phone a
            // third of the panel never moved and never showed an article. The
            // app has scrolled them with the feed for a while; this is the
            // overlay catching up, because the two surfaces disagreeing about
            // what a scroll does is worse than either behaviour on its own.
            val header: @Composable () -> Unit = {
                Column {
                    GlanceRow(
                        state = glanceState,
                        // Straight to settings. It used to open the overflow
                        // menu, so "set a location" meant opening a menu and
                        // then finding settings in it.
                        onSetLocation = onSettings,
                    )

                    // Hidden while searching: a search deliberately ignores
                    // the selected category, so leaving the chips up — one of
                    // them highlighted — would claim a narrowing that is not
                    // happening.
                    if (!isSearching) {
                        CategoryChipRow(
                            categories = categories,
                            selected = selectedCategories,
                            onSelectedChange = onCategoriesChange,
                        )
                        // What the filter is doing, where the shortened feed
                        // is. The funnel in the header says only that one is
                        // on; this says which, and lets it be undone here.
                        ActiveFilterBar()
                    }
                    // Outside the search check: how current the feed is does
                    // not change with what is being looked for.
                    SyncFreshnessLine()
                }
            }

            // A plain Box: pull-to-refresh belongs to the list containers
            // below, which are the app's, so nesting a second one here would
            // put two refresh gestures on top of each other.
            Box(modifier = Modifier.fillMaxSize()) {
                val padding = PaddingValues(
                    top = 4.dp,
                    bottom = bottomInset + 16.dp,
                )
                // Mosaic is a staggered grid rather than a column, so the
                // container changes with the layout and not only the shapes
                // inside it. Everything else is a single column.
                // Which article gets the big shape is one decision for every
                // layout, so it is made once here rather than per container.
                val emphasis = frame.emphasis
                // Asked of the system rather than assumed, and asked on both
                // surfaces: somebody who has set the animation scale to zero
                // has said what they want, and the panel is no more exempt
                // from that than the app is.
                val animate = !reducedMotion()
                val dimRead = rememberDimRead()
                    val skipped = rememberSkippedSources()
                val clusters = frame.clusters
                TrackReading(
                    articles = frame.articles,
                    isGrid = isGrid,
                    listState = listState,
                    gridState = gridState,
                    onRead = onArticleSeen,
                    onDwell = onArticleDwell,
                    onLeave = onDwellFlush,
                )
                if (isSearching && searchQuery.isNotBlank() && frame.articles.isEmpty()) {
                    SearchEmptyState(searchQuery)
                } else if (frame.articles.isEmpty()) {
                    // Nothing to scroll, so the header has nowhere to scroll
                    // away to and is drawn plainly.
                    header()
                    // The launcher surface cannot reach the sources list, so it
                    // reports the two cases it can actually tell apart and
                    // leaves "no sources" to the app, which can say where to
                    // go and open it.
                    FeedEmptyState(
                        when {
                            isRefreshing -> FeedEmptyReason.Syncing
                            isFilterActive || selectedCategories.isNotEmpty() ->
                                FeedEmptyReason.FilteredOut

                            else -> FeedEmptyReason.NothingFetched
                        }
                    )
                } else if (feedLayoutIsGrid(layout)) {
                    // The app's own container, rather than a second one built
                    // here. Two wrappers around the same Material indicator is
                    // how the two surfaces drifted apart in the first place —
                    // and one of them had a two-second timer on the spinner
                    // that the other did not.
                    PullToRefreshStaggeredGrid(
                        isRefreshing = isRefreshing,
                        onRefresh = { onRefresh() },
                        gridState = gridState,
                        contentPadding = padding,
                        modifier = Modifier.padding(horizontal = 10.dp),
                        content = {
                        item(
                            key = FEED_HEADER_KEY,
                            span = StaggeredGridItemSpan.FullLine,
                        ) { header() }
                        itemsIndexed(
                            frame.articles,
                            key = { _, item -> item.id },
                            contentType = { index, item ->
                                feedContentType(index, item, layout, emphasis)
                            },
                            // The grid needs the span before it composes the
                            // item, so the sizes are worked out for the whole
                            // list up front rather than asked per tile.
                            span = { index, _ ->
                                if (emphasis.getOrNull(index) == FeedEmphasis.Large)
                                    StaggeredGridItemSpan.FullLine
                                else StaggeredGridItemSpan.SingleLane
                            },
                        ) { index, item ->
                            // Keyed and animated, as the column beside it and
                            // the app's own grid already were: a sync should
                            // move a card rather than replace the list under
                            // the reader's thumb.
                            Box(
                                modifier = if (animate) Modifier.animateItem()
                                else Modifier
                            ) {
                                FeedArticleItem(
                                    item = item,
                                    index = index,
                                    onClick = { onArticleClick(item) },
                                    onBookmark = { onBookmark(item, it) },
                                    onShare = { onShare(item) },
                                    onMoreLikeThis = { onMoreLikeThis(item) },
                                    onLessLikeThis = { onLessLikeThis(item) },
                                    onHideSource = { onHideSource(item) },
                                    onDismissStory = { onDismissStory(item) },
                                    layout = layout,
                                    emphasis = emphasis.getOrNull(index)
                                        ?: FeedEmphasis.Medium,
                                    dimRead = dimRead,
                                    dimSource = item.feed.id in skipped,
                                    cluster = clusters[item.id],
                                    onPin = { onPin(item, it) },
                                )
                            }
                        }
                        },
                    )
                } else {
                    val article: @Composable (Int, FeedItem) -> Unit = { index, item ->
                        FeedArticleItem(
                            item = item,
                            index = index,
                            onClick = { onArticleClick(item) },
                            onBookmark = { onBookmark(item, it) },
                            onShare = { onShare(item) },
                            onMoreLikeThis = { onMoreLikeThis(item) },
                            onLessLikeThis = { onLessLikeThis(item) },
                            onHideSource = { onHideSource(item) },
                            onDismissStory = { onDismissStory(item) },
                            layout = layout,
                            emphasis = emphasis.getOrNull(index) ?: FeedEmphasis.Medium,
                            dimRead = dimRead,
                                    dimSource = item.feed.id in skipped,
                            cluster = clusters[item.id],
                            onPin = { onPin(item, it) },
                        )
                    }
                    PullToRefreshLazyColumn(
                        isRefreshing = isRefreshing,
                        onRefresh = { onRefresh() },
                        listState = listState,
                        contentPadding = padding,
                        content = {
                            item(key = FEED_HEADER_KEY) { header() }
                            feedItems(
                                frame.articles,
                                animate,
                                contentType = { index, item ->
                                    feedContentType(index, item, layout, emphasis)
                                },
                                article = article,
                            )
                        },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 16.dp),
        )

        // The same sort-and-filter screen the app shows, rather than the
        // View-based bottom sheet this used to open — which was the last
        // screen in the overlay still built out of XML, and looked it.
        //
        // Drawn inside this composition rather than as a ModalBottomSheet: a
        // modal sheet is its own window, and this window is created against
        // the launcher's token with an unusual flag set. Everything already
        // here is laid out by hand for that reason, so the sheet is too.
        AnimatedVisibility(
            visible = isFilterSheetOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onFilterSheetOpenChange(false) }
            )
        }

        AnimatedVisibility(
            visible = isFilterSheetOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge.copy(
                    bottomStart = CornerSize(0.dp),
                    bottomEnd = CornerSize(0.dp),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = bottomInset),
                ) {
                    // A grab handle, because without a modal sheet's own
                    // chrome there is nothing else saying this is dismissible.
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    SortFilterSheet(onDismiss = { onFilterSheetOpenChange(false) })
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = bottomInset + 24.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        if (isGrid) gridState.animateScrollToItem(0)
                        else listState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Phosphor.CaretUp, stringResource(R.string.back_to_top))
            }
        }
    }
    }
}

