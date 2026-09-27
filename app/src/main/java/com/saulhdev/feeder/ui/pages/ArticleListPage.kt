/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Saul Henriquez <henriquez.saul@gmail.com>
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

package com.saulhdev.feeder.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import com.saulhdev.feeder.ui.components.BrandTitle
import com.saulhdev.feeder.ui.overlay.feedColumns
import com.saulhdev.feeder.data.entity.SORT_CHRONOLOGICAL
import com.saulhdev.feeder.ui.overlay.feedSegments
import com.saulhdev.feeder.ui.overlay.feedDayBreaks
import com.saulhdev.feeder.ui.overlay.dayHeadingKey
import com.saulhdev.feeder.ui.overlay.DayHeading
import com.saulhdev.feeder.ui.overlay.leadSpansRow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import com.saulhdev.feeder.NeoApp
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.ui.components.HeaderToggleAction
import com.saulhdev.feeder.ui.components.OverflowMenu
import com.saulhdev.feeder.ui.components.PullToRefreshStaggeredGrid
import com.saulhdev.feeder.ui.components.PullToRefreshLazyColumn
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.ui.icons.phosphor.Filter
import com.saulhdev.feeder.ui.icons.phosphor.Filtered
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.ui.icons.phosphor.Graph
import com.saulhdev.feeder.ui.icons.phosphor.Power
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.extensions.safeShareIntent
import androidx.compose.foundation.gestures.animateScrollBy
import com.saulhdev.feeder.ui.overlay.feedLayoutIsGrid
import com.saulhdev.feeder.utils.VolumeScroll
import com.saulhdev.feeder.ui.overlay.FeedEmphasis
import com.saulhdev.feeder.ui.overlay.TrackReading
import com.saulhdev.feeder.ui.overlay.rememberDimRead
import com.saulhdev.feeder.ui.overlay.rememberSkippedSources
import com.saulhdev.feeder.ui.theme.reducedMotion
import com.saulhdev.feeder.ui.overlay.feedItems
import com.saulhdev.feeder.ui.overlay.rememberFeedFrame
import com.saulhdev.feeder.ui.overlay.feedContentType
import com.saulhdev.feeder.ui.overlay.ActiveFilterBar
import com.saulhdev.feeder.ui.overlay.AnchorFeedOnFocusChange
import com.saulhdev.feeder.ui.overlay.FEED_HEADER_KEY
import com.saulhdev.feeder.utils.BrowserReadTimer
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.ui.overlay.FeedArticleItem
import com.saulhdev.feeder.ui.overlay.GlanceRow
import com.saulhdev.feeder.ui.overlay.SyncFreshnessLine
import com.saulhdev.feeder.ui.overlay.SyncFreshnessText
import com.saulhdev.feeder.ui.overlay.firstSegmentHasHeading
import com.saulhdev.feeder.ui.overlay.CategoryChipRow
import kotlinx.coroutines.Dispatchers
import org.koin.compose.koinInject
import com.saulhdev.feeder.ui.components.FeedSearchBar
import com.saulhdev.feeder.ui.components.SourceFilterBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import com.saulhdev.feeder.ui.overlay.LocalFocusSource
import com.saulhdev.feeder.ui.components.HeaderAction
import com.saulhdev.feeder.ui.icons.phosphor.MagnifyingGlass
import com.saulhdev.feeder.ui.components.FeedEmptyReason
import com.saulhdev.feeder.ui.components.BookmarksEmptyState
import com.saulhdev.feeder.ui.components.FeedEmptyState
import com.saulhdev.feeder.ui.components.SearchEmptyState
import com.saulhdev.feeder.ui.onboarding.ProvideTourTargets
import com.saulhdev.feeder.ui.onboarding.TourOverlay
import com.saulhdev.feeder.ui.onboarding.TourTarget
import com.saulhdev.feeder.ui.onboarding.tourTarget
import androidx.compose.foundation.layout.Box
import com.saulhdev.feeder.data.content.asState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun ArticleListPage(
    prefs: FeedPreferences = koinInject(),
    syncClient: SyncRestClient = koinInject(),
    sourcesRepo: SourcesRepository = koinInject(),
    glanceHolder: com.saulhdev.feeder.manager.glance.GlanceStateHolder = koinInject(),
    viewModel: ArticleListViewModel = koinNeoViewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { glanceHolder.refreshIfStale() }
    val scaffoldState = rememberBottomSheetScaffoldState()
    val paneNavigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val articleId = remember { mutableStateOf("") }

    val state by viewModel.articleListState.collectAsState()
    // Collected once rather than read inside onClick. getValue() is a
    // runBlocking DataStore read, so reading it there blocked the main thread
    // on every article tap — the one moment the UI most has to stay responsive.
    val openMode by prefs.articleOpenMode.asState()
    val bookmarked by viewModel.bookmarksState.collectAsState()
    val layout by prefs.feedLayout.get().collectAsState(initial = LAYOUT_CARDS)
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusedSource by viewModel.focusedSource.collectAsState()
    val focusAnchor by viewModel.focusAnchor.collectAsState()
    // Resolved from the articles on screen rather than fetched: the feed is
    // already filtered to this source, so its first card carries the name and
    // the icon the bar needs, and a second query for what is already in hand
    // would be a second query.
    val focusedFeed = remember(focusedSource, state.articles) {
        state.articles.firstOrNull { it.sourceId == focusedSource }
    }
    var searching by remember { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()

    // The same offer the overlay feed makes: hiding a source is one tap from a
    // menu, so the way back is shown where the article disappeared from.
    val snackbarHostState = remember { SnackbarHostState() }
    val hiddenSource by viewModel.recentlyHidden.collectAsState()
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(hiddenSource) {
        val item = hiddenSource ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.source_hidden, item.feedTitle),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoHideSource()
        else viewModel.forgetHiddenSource()
    }

    // Articles marked read without the reader pressing anything — by scrolling
    // past them, or by marking the lot from Settings — are offered back. An
    // article they opened is not: they know they opened it.
    val undoableReads by viewModel.undoableReads.collectAsState()
    val resources = LocalResources.current
    // The one offer, whoever marked them. Mark as read calls it at once; a run
    // of scroll marks calls it once the run has settled.
    val offerReadsBack: suspend (Int) -> Unit = { count ->
        val result = snackbarHostState.showSnackbar(
            message = resources.getQuantityString(R.plurals.articles_marked_read, count, count),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoReads()
        else viewModel.forgetUndoableReads()
    }
    LaunchedEffect(undoableReads.size) {
        val count = undoableReads.size
        if (count < UNDO_MIN_COUNT) return@LaunchedEffect
        // A batch arrives one article at a time while scrolling, so wait for
        // the run to finish rather than replacing the snackbar on every mark.
        delay(UNDO_SETTLE_MS)
        if (viewModel.undoableReads.value.size != count) return@LaunchedEffect
        offerReadsBack(count)
    }

    var showBookmarks by remember { mutableStateOf(false) }
    // True until DataStore says otherwise, so neither the welcome nor the tour
    // flashes over the feed for a frame on every launch of an app that has
    // already shown both.
    val onboardingSeen by prefs.onboardingSeen.get().collectAsState(initial = true)
    val tourSeen by prefs.tourSeen.get().collectAsState(initial = true)
    // Movement the reader has not switched off. Everything below asks this
    // rather than assuming; somebody who has set the animation scale to zero
    // has said what they want.
    val animate = !reducedMotion()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val showFAB by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }

    // Volume keys, when the reader has turned them on. Whichever container the
    // layout is using is the one that moves; asking both would fight.
    // Columns by the width the feed has: the window, or the narrow pane
    // beside an open article. See feedColumns.
    val windowWidthDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp().value.toInt()
    }
    // An article actually open, not merely room for one: on a wide screen the
    // scaffold reports the detail pane expanded with nothing in it, which
    // read as "article open" and kept the tablet in one column.
    val articleSelected = paneNavigator.currentDestination
        ?.takeIf { it.pane == ListDetailPaneScaffoldRole.Detail }?.contentKey != null
    val articleOpenBeside = articleSelected &&
        paneNavigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val feedWidthDp = if (articleOpenBeside) FEED_BESIDE_ARTICLE_DP else windowWidthDp
    val columns = feedColumns(layout, feedWidthDp)
    val isGridLayout = columns > 1
    // The two containers keep separate positions, so switching between them
    // - opening an article narrows the feed to one column - carries the place
    // across rather than jumping back to the top.
    LaunchedEffect(isGridLayout) {
        if (isGridLayout) {
            if (listState.firstVisibleItemIndex > 0) gridState.scrollToItem(listState.firstVisibleItemIndex)
        } else if (gridState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(gridState.firstVisibleItemIndex)
        }
    }
    LaunchedEffect(isGridLayout) {
        VolumeScroll.events.collect { direction ->
            val viewport = if (isGridLayout) gridState.layoutInfo.viewportSize.height
            else listState.layoutInfo.viewportSize.height
            val distance = viewport * VolumeScroll.PAGE_FRACTION * direction
            if (isGridLayout) gridState.animateScrollBy(distance)
            else listState.animateScrollBy(distance)
        }
    }

    // What is drawn: the articles with their sizes and stories, built off the
    // main thread and never swapped in under a moving finger. See FeedFrame.
    val frame = rememberFeedFrame(state.articles, state.focusedSource) {
        listState.isScrollInProgress || gridState.isScrollInProgress
    }

    // Day headings, in date order only: sorted by title or by source, a
    // heading per day would be scattered through the list. See FeedDays.
    val sort by prefs.sortingFilter.get().collectAsState(initial = SORT_CHRONOLOGICAL)
    val dayBreaks = remember(frame.articles, sort, searching) {
        if (sort == SORT_CHRONOLOGICAL && !searching) feedDayBreaks(frame.articles) else emptyList()
    }
    val segments = remember(frame.articles, dayBreaks) { feedSegments(frame.articles.size, dayBreaks) }

    // Tapping a source narrows the feed to it, and backing out widens it
    // again; both land on the article the tap came from. See FocusAnchor.
    AnchorFeedOnFocusChange(
        appliedFocus = frame.focus,
        anchorId = focusAnchor,
        articles = frame.articles,
        isGrid = isGridLayout,
        listState = listState,
        gridState = gridState,
        breaks = dayBreaks,
    )

    BackHandler(scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }

    // Whether something is covering the feed. A screen reader does not care
    // what is drawn on top of what: without this the feed behind the welcome
    // panes and the tour stayed in the accessibility tree, so a swipe went
    // straight past the tooltip into the articles underneath — the overlay was
    // modal for everybody except the people it matters most to.
    val covered = !onboardingSeen || (!tourSeen && state.articles.isNotEmpty() && !searching)

    // The way out that the reader will reach for first. The filter is not a
    // navigation destination — it narrows the feed in place — so there is no
    // back stack entry for the system to pop, and without this the gesture
    // leaves the app with the feed still narrowed.
    BackHandler(enabled = focusedSource != null) { viewModel.clearFocusedSource() }

    ProvideTourTargets {
    // Offered here, so every card on this surface can narrow the feed, and
    // offered *only* here: the launcher panel leaves it null, because its back
    // gesture belongs to the launcher and a filter opened there could not be
    // undone. See LocalFocusSource.
    CompositionLocalProvider(LocalFocusSource provides viewModel::focusSource) {
    Box(modifier = Modifier.fillMaxSize()) {
    NavigableListDetailPaneScaffold(
        modifier = if (covered) Modifier.clearAndSetSemantics { } else Modifier,
        navigator = paneNavigator,
        listPane = {
            AnimatedPane {
                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 0.dp,
                    containerColor = Color.Transparent,
                    sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    sheetShape = MaterialTheme.shapes.extraSmall,
                    sheetContent = {
                        if (scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
                            SortFilterSheet(
                                markReadCounts = { now -> viewModel.unreadCounts(now) },
                                onMarkRead = { range, now ->
                                    viewModel.markAllRead(range, now) { count ->
                                        if (count > 0) scope.launch { offerReadsBack(count) }
                                    }
                                },
                            ) {
                                scope.launch {
                                    scaffoldState.bottomSheetState.partialExpand()
                                }
                            }
                        } else Spacer(modifier = Modifier.height(8.dp))
                    },
                ) {
                    Scaffold(
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        containerColor = Color.Transparent,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            if (focusedSource != null) SourceFilterBar(
                                title = focusedFeed?.feedTitle.orEmpty(),
                                iconUrl = focusedFeed?.feedIconUrl,
                                onClear = viewModel::clearFocusedSource,
                            ) else if (searching) FeedSearchBar(
                                query = searchQuery,
                                onQueryChange = viewModel::setSearchQuery,
                                onClose = { searching = false },
                            ) else TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                                ),
                                // The overlay's header, to the digit. Both were
                                // meant to be sized to the mockup — the mark
                                // standing about twice the cap height of the
                                // name — and only the overlay actually was; this
                                // one kept the number it already had while its
                                // drawable was swapped underneath it, so the two
                                // headers have been quietly different sizes
                                // since.
                                title = { BrandTitle() },
                                scrollBehavior = scrollBehavior,
                                actions = {
                                    // The same three actions the overlay's
                                    // header carries, drawn by the same
                                    // component so the two rows sit on one
                                    // rhythm. The bookmarks toggle used to be
                                    // a Surface with its own padding between
                                    // two IconButtons, which is what made the
                                    // spacing look off.
                                    HeaderAction(
                                        icon = Phosphor.MagnifyingGlass,
                                        description = stringResource(R.string.action_search),
                                        onClick = { searching = true },
                                    )

                                    Box(modifier = Modifier.tourTarget(TourTarget.Filter)) {
                                    HeaderToggleAction(
                                        icon = if (state.isFilterModified) Phosphor.Filtered
                                        else Phosphor.Filter,
                                        // "Filters", not "Sorting order": the
                                        // active state tracks whether the feed
                                        // is being narrowed, so that is what
                                        // the control should be called — and
                                        // it is what the overlay calls it.
                                        description = stringResource(id = R.string.pref_cat_filters),
                                        active = state.isFilterModified,
                                        onClick = {
                                            scope.launch {
                                                scaffoldState.bottomSheetState.expand()
                                            }
                                        },
                                    )
                                    }

                                    Box(modifier = Modifier.tourTarget(TourTarget.Bookmarks)) {
                                    HeaderToggleAction(
                                        painter = painterResource(R.drawable.ic_whisper_save),
                                        description = stringResource(id = R.string.title_bookmarks),
                                        active = showBookmarks,
                                        onClick = { showBookmarks = !showBookmarks },
                                    )
                                    }

                                    // Straight to Settings rather than a menu
                                    // of four. Three of the four were things
                                    // that belong in Settings and one was
                                    // Settings itself, so the menu existed to
                                    // ask which kind of settings the reader
                                    // wanted — a question with one answer.
                                    HeaderAction(
                                        icon = Phosphor.GearSix,
                                        description = stringResource(R.string.title_settings),
                                        onClick = { navController.navigate(NavRoute.Settings) },
                                        modifier = Modifier.tourTarget(TourTarget.Overflow),
                                    )
                                }
                            )
                        },
                        floatingActionButton = {
                            // Fading and scaling, as the overlay's does. A
                            // button that fades in at full size reads as having
                            // been there all along and missed.
                            AnimatedVisibility(
                                visible = showFAB,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                            ) {
                                FloatingActionButton(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    onClick = {
                                        scope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Phosphor.CaretUp,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            // Same strip as the launcher surface, reading the same
                            // preference, so a category chosen in one is reflected
                            // in the other.
                            val categories by sourcesRepo.getAllTagsFlow()
                                .collectAsState(initial = emptyList())
                            // "No sources" and "sources but no articles" are
                            // different problems with different answers, and
                            // the empty state has to tell them apart.
                            val allSources by sourcesRepo.getAllSourcesFlow()
                                .collectAsState(initial = emptyList())
                            val selectedCategories by prefs.categoryFilter.get()
                                .collectAsState(initial = emptySet())

                            val glance by glanceHolder.state.collectAsState()
                            // Scrolled with the feed rather than fixed above
                            // it, the way the launcher's own page behaves: on a
                            // phone these two take a third of the screen, and a
                            // third of the screen that never moves is a third
                            // of the screen not showing articles.
                            //
                            // They are drawn inside the scrolling container,
                            // which is why read-on-scroll now matches articles
                            // by key rather than by layout position — with
                            // headers in the list those two stopped agreeing.
                            val freshnessBesideHeading =
                                !showBookmarks && firstSegmentHasHeading(segments)
                            val header: @Composable () -> Unit = {
                                Column {
                                    GlanceRow(
                                        state = glance,
                                        onSetLocation = {
                                            navController.navigate(NavRoute.Settings)
                                        },
                                        modifier = Modifier.tourTarget(TourTarget.Glance),
                                    )
                                    CategoryChipRow(
                                        modifier = Modifier.tourTarget(TourTarget.Chips),
                                        categories = categories,
                                        selected = selectedCategories,
                                        onSelectedChange = { picked ->
                                            prefs.categoryFilter.set(picked)
                                        },
                                    )
                                    // Named, and undoable, where the shortened
                                    // feed is rather than inside the sheet
                                    // that set it.
                                    ActiveFilterBar()
                                    // Beside the first day heading when the
                                    // feed opens on one; on its own line when
                                    // it does not (saved, searching, another
                                    // sort, pinned stories first, empty).
                                    if (!freshnessBesideHeading) SyncFreshnessLine()
                                }
                            }
                            val firstHeadingTrailing: @Composable (Modifier) -> Unit = {
                                SyncFreshnessText(modifier = it, textAlign = TextAlign.End)
                            }

                            when {
                                showBookmarks -> LazyColumn(
                                    state = listState,
                                    contentPadding = FEED_PADDING,
                                ) {
                                    item(key = FEED_HEADER_KEY) { header() }
                                    if (bookmarked.bookmarkedArticles.isEmpty()) {
                                        item(key = "bookmarks-empty") { BookmarksEmptyState() }
                                    }
                                    itemsIndexed(
                                        bookmarked.bookmarkedArticles,
                                        key = { _, item -> item.id },
                                    ) { index, item ->
                                        FeedArticleItem(
                                            item = item,
                                            index = index,
                                            onClick = {
                                                viewModel.markOpened(item.id)
                                                if (openMode == FeedPreferences.OPEN_MODE_BROWSER) {
                                                    // Whisper is about to stop
                                                    // being on screen, so the
                                                    // only clock left is how
                                                    // long until it is again.
                                                    BrowserReadTimer.left(item.id)
                                                    context.launchView(item.link)
                                                } else {
                                                    scope.launch {
                                                        paneNavigator.navigateTo(
                                                            ListDetailPaneScaffoldRole.Detail,
                                                            item.id
                                                        )
                                                    }
                                                }
                                            },
                                            // The overflow menu offers Pin on
                                            // every card, and this list was
                                            // the one that wired nothing to
                                            // it — so pinning from Bookmarks
                                            // silently did nothing at all.
                                            onPin = { viewModel.setPinned(item.id, it) },
                                            onBookmark = { viewModel.bookmarkArticle(item.id, it) },
                                            onShare = {
                                                context.safeShareIntent(item.link, item.contentTitle)
                                            },
                                            onMoreLikeThis = {
                                                viewModel.recordAffinity(item.sourceId, 1)
                                            },
                                            onLessLikeThis = {
                                                viewModel.recordAffinity(item.sourceId, -1)
                                            },
                                            onHideSource = {
                                                viewModel.hideSource(item)
                                            },
                                        )
                                    }
                                }

                                // The same shapes and the same rhythm the launcher feed
                                // draws, so the two surfaces are not mistaken for
                                // different apps — and the same container, which for
                                // Mosaic is a staggered grid rather than a column.
                                else          -> {
                                    val dimRead = rememberDimRead()
                    val skipped = rememberSkippedSources()
                                    val clusters = frame.clusters
                                    TrackReading(
                                        articles = frame.articles,
                                        isGrid = isGridLayout,
                                        listState = listState,
                                        gridState = gridState,
                                        onRead = { viewModel.markReadOnScroll(it.id) },
                                        onDwell = viewModel::addDwell,
                                        onLeave = viewModel::flushDwell,
                                    )
                                    val article: @Composable (Int, FeedItem, FeedEmphasis) -> Unit =
                                        { index, item, emphasis ->
                                        FeedArticleItem(
                                            item = item,
                                            index = index,
                                            // The tour points at one card, and
                                            // the first is the only one certain
                                            // to be on screen when it runs.
                                            modifier = if (index == 0)
                                                Modifier.tourTarget(TourTarget.Article)
                                            else Modifier,
                                            layout = layout,
                                            emphasis = emphasis,
                                            dimRead = dimRead,
                                    dimSource = item.feed.id in skipped,
                                            cluster = clusters[item.id],
                                            onPin = { viewModel.setPinned(item.id, it) },
                                            onClick = {
                                                viewModel.markOpened(item.id)
                                                if (openMode == FeedPreferences.OPEN_MODE_BROWSER) {
                                                    // Whisper is about to stop
                                                    // being on screen, so the
                                                    // only clock left is how
                                                    // long until it is again.
                                                    BrowserReadTimer.left(item.id)
                                                    context.launchView(item.link)
                                                } else {
                                                    scope.launch {
                                                        paneNavigator.navigateTo(
                                                            ListDetailPaneScaffoldRole.Detail,
                                                            item.id
                                                        )
                                                    }
                                                }
                                            },
                                            onBookmark = { viewModel.bookmarkArticle(item.id, it) },
                                            onShare = {
                                                context.safeShareIntent(item.link, item.contentTitle)
                                            },
                                            onMoreLikeThis = { viewModel.recordAffinity(item.sourceId, 1) },
                                            onLessLikeThis = { viewModel.recordAffinity(item.sourceId, -1) },
                                            onHideSource = { viewModel.hideSource(item) },
                                            onDismissStory = { viewModel.dismissStory(item.id) },
                                        )
                                    }

                                    // One decision for every layout, made once.
                                    val emphasis = frame.emphasis
                                    if (searching && searchQuery.isNotBlank() &&
                                        frame.articles.isEmpty()
                                    ) {
                                        SearchEmptyState(searchQuery)
                                    } else if (frame.articles.isEmpty()) {
                                        // Nothing to scroll, so the header has
                                        // nowhere to scroll away to.
                                        header()
                                        // Which of the four it is decides what
                                        // the reader should do about it, so the
                                        // reasons are told apart rather than
                                        // collapsed into "nothing here".
                                        FeedEmptyState(
                                            when {
                                                allSources.isEmpty() -> FeedEmptyReason.NoSources
                                                state.isSyncing -> FeedEmptyReason.Syncing
                                                state.isFilterModified ||
                                                        selectedCategories.isNotEmpty() ->
                                                    FeedEmptyReason.FilteredOut

                                                else -> FeedEmptyReason.NothingFetched
                                            }
                                        )
                                    } else if (isGridLayout) {
                                        PullToRefreshStaggeredGrid(
                                            isRefreshing = state.isSyncing,
                                            onRefresh = { syncClient.syncAllFeeds() },
                                            gridState = gridState,
                                            columns = columns,
                                            laneSpacing = if (feedLayoutIsGrid(layout)) 0.dp else 12.dp,
                                            contentPadding = FEED_PADDING,
                                            content = {
                                                item(
                                                    key = FEED_HEADER_KEY,
                                                    span = StaggeredGridItemSpan.FullLine,
                                                ) { header() }
                                                segments.forEach { segment ->
                                                segment.day?.let { day ->
                                                    item(
                                                        key = dayHeadingKey(day),
                                                        span = StaggeredGridItemSpan.FullLine,
                                                        contentType = "day",
                                                    ) {
                                                        DayHeading(
                                                            day,
                                                            first = segment.from == 0,
                                                            trailing = firstHeadingTrailing
                                                                .takeIf { segment.from == 0 },
                                                        )
                                                    }
                                                }
                                                val offset = segment.from
                                                itemsIndexed(
                                                    frame.articles.subList(segment.from, segment.until),
                                                    key = { _, item -> item.id },
                                                    contentType = { i, item ->
                                                        feedContentType(i + offset, item, layout, emphasis)
                                                    },
                                                    // A lead story spans Mosaic's
                                                    // two lanes on a phone, and no
                                                    // more than two anywhere: the
                                                    // grid spans one lane or all of
                                                    // them, and across a tablet's
                                                    // four it was too big, and left
                                                    // a hole in the row above it.
                                                    // In the other layouts, columned
                                                    // only for the width, it keeps
                                                    // to its column.
                                                    span = { i, _ ->
                                                        if (leadSpansRow(layout, columns) &&
                                                            emphasis.getOrNull(i + offset) ==
                                                            FeedEmphasis.Large
                                                        ) StaggeredGridItemSpan.FullLine
                                                        else StaggeredGridItemSpan.SingleLane
                                                    },
                                                ) { i, item ->
                                                    val index = i + offset
                                                    // Articles arriving from a
                                                    // sync used to appear by
                                                    // replacement: the list
                                                    // simply became a
                                                    // different list under the
                                                    // reader's thumb. Keyed
                                                    // items can be moved
                                                    // instead of swapped, and
                                                    // seeing a card move is
                                                    // what tells you it is the
                                                    // same card.
                                                    Box(
                                                        modifier = if (animate) {
                                                            Modifier.animateItem()
                                                        } else Modifier
                                                    ) {
                                                        article(
                                                            index,
                                                            item,
                                                            emphasis.getOrNull(index)
                                                                ?: FeedEmphasis.Medium,
                                                        )
                                                    }
                                                }
                                                }
                                            },
                                        )
                                    } else {
                                        PullToRefreshLazyColumn(
                                            isRefreshing = state.isSyncing,
                                            onRefresh = { syncClient.syncAllFeeds() },
                                            listState = listState,
                                            // Said rather than inherited. The
                                            // default is 8dp on all four
                                            // sides, which the launcher feed
                                            // overrides and this one did not
                                            // — so the same card was full
                                            // bleed on one surface and inset
                                            // by eight points on the other,
                                            // and a photograph with a margin
                                            // reads as a component on a page
                                            // rather than as the article.
                                            contentPadding = FEED_PADDING,
                                            content = {
                                                item(key = FEED_HEADER_KEY) { header() }
                                                segments.forEach { segment ->
                                                    segment.day?.let { day ->
                                                        item(key = dayHeadingKey(day), contentType = "day") {
                                                            DayHeading(
                                                                day,
                                                                first = segment.from == 0,
                                                                trailing = firstHeadingTrailing
                                                                    .takeIf { segment.from == 0 },
                                                            )
                                                        }
                                                    }
                                                    val offset = segment.from
                                                    feedItems(
                                                        frame.articles.subList(segment.from, segment.until),
                                                        animate,
                                                        contentType = { i, item ->
                                                            feedContentType(i + offset, item, layout, emphasis)
                                                        },
                                                    ) { i, item ->
                                                        article(
                                                            i + offset,
                                                            item,
                                                            emphasis.getOrNull(i + offset)
                                                                ?: FeedEmphasis.Medium,
                                                        )
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            articleId.value = paneNavigator.currentDestination
                ?.takeIf { it.pane == this.paneRole }?.contentKey
                ?.toString().orEmpty()

            articleId.value.takeIf { it.isNotEmpty() }?.let { id ->
                AnimatedPane {
                    ArticlePage(id) {
                        scope.launch {
                            paneNavigator.navigateBack()
                        }
                    }
                }
            }
        }
    )

    // The welcome first, then the tour — and the tour waits for articles.
    // Spotlighting an empty feed would teach nothing and look broken, which on
    // a new install is exactly the wrong first impression: the sync that fills
    // it takes a moment longer than the app takes to open.
    when {
        !onboardingSeen -> OnboardingPage {
            scope.launch(Dispatchers.IO) { prefs.onboardingSeen.setValue(true) }
        }

        !tourSeen && state.articles.isNotEmpty() && !searching -> TourOverlay {
            scope.launch(Dispatchers.IO) { prefs.tourSeen.setValue(true) }
        }
    }
    }
    }
    }
}

/**
 * How long a run of automatic read marks has to stop before the undo is
 * offered.
 *
 * It was 1.2 seconds, which is not a pause — it is what happens between two
 * flicks of a thumb, or while looking at a headline. So the offer arrived
 * every few seconds throughout a scroll, covering the article being read to
 * report a number nobody had asked for. Half a minute of genuine stillness
 * means the reading has stopped, which is the only moment the offer is worth
 * the interruption.
 *
 * The cost is honest and accepted: leaving the feed inside that window means
 * the offer never appears and the marks stand. A reader who did not want them
 * still has "What Whisper has learned" and the read-visibility setting; a
 * reader who is scrolling has their screen back, which is the trade being
 * made.
 */
private const val UNDO_SETTLE_MS = 30_000L

/**
 * How many automatic marks are worth mentioning at all.
 *
 * Scrolling past two articles is not an event. The offer exists for the case
 * where a lot went by unnoticed, and below a handful there is nothing to have
 * failed to notice.
 */
private const val UNDO_MIN_COUNT = 5


/**
 * The feed's own padding: vertical breathing room, and nothing at the sides.
 *
 * The same value the launcher feed uses, named once so the two surfaces
 * cannot drift apart again. A card carries its own margins for text; the
 * photograph does not want them, and a list that adds eight points of its own
 * takes that decision away from the card.
 */
private val FEED_PADDING = PaddingValues(vertical = 4.dp)

/**
 * The width the feed is laid out for with an article open beside it: the list
 * pane is narrow then, whatever the window, so one column (two for Mosaic).
 */
private const val FEED_BESIDE_ARTICLE_DP = 360
