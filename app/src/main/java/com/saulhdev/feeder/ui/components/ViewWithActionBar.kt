/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Saul Henriquez <henriquez.saul@gmail.com>
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

package com.saulhdev.feeder.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowLeft

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ViewWithActionBar(
    title: String,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 18.sp,
    subTitle: String = "",
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    showBackButton: Boolean = true,
    /**
     * Draws the header as a large collapsing title.
     *
     * Material You gives a top-level destination a large title that shrinks as
     * the content scrolls under it; a fixed 18sp row is what an app looks like
     * before anyone has themed it. Used for the destinations a user lands on
     * rather than passes through.
     */
    largeTitle: Boolean = false,
    actions: @Composable (RowScope.() -> Unit) = {},
    /**
     * What the navigation slot shows, when it is not going back.
     *
     * A screen holding a selection puts a close cross here instead of the back
     * arrow: the button dismisses the selection rather than the screen, and an
     * arrow promising to leave would be lying about where the tap goes.
     */
    backIcon: ImageVector = Phosphor.ArrowLeft,
    backDescription: Int = R.string.go_back,
    onBackAction: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val appBarState = rememberTopAppBarState()
    val scrollBehavior =
        if (largeTitle) TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
        else enterAlwaysScrollBehavior(appBarState)

    val titleContent: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        // In large mode the app bar supplies the type scale;
                        // forcing 18sp there would defeat the point of it.
                        Text(
                            text = title,
                            style = if (largeTitle) MaterialTheme.typography.headlineMedium
                            else MaterialTheme.typography.titleMedium,
                            fontSize = if (largeTitle) TextUnit.Unspecified else titleSize,
                            // One line. The large title and the collapsed bar
                            // draw this same text, so two lines would cut the
                            // collapsed one in half. Long titles wait for
                            // Expressive's flexible app bars (design audit 6).
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subTitle.isNotEmpty()) {
                            Text(
                                text = subTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
    }

    val navContent: @Composable () -> Unit = {
                    if (showBackButton) {
                        val backDispatcher =
                            LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                        IconButton(
                            onClick = {
                                onBackAction?.invoke()
                                    ?: backDispatcher?.onBackPressed()
                            }
                        ) {
                            Icon(
                                imageVector = backIcon,
                                contentDescription = stringResource(id = backDescription),
                            )
                        }
                    }
    }

    // A large title collapses only on a page that can scroll, and a page
    // that cannot still gives under the finger. See pageScrolling.
    val stretch = rememberOverscrollEffect()
    val connection = remember(scrollBehavior, largeTitle, stretch) {
        pageScrolling(scrollBehavior.nestedScrollConnection, holdBack = largeTitle, stretch = stretch)
    }

    // A title let go halfway finishes opening or closing (review 50, test
    // t003-06). Material settles it only at the end of a fling, and a fling
    // that a finger stops, or a drag that ends without one, never gets there.
    // So: whenever no finger is down and the bar has sat between open and
    // closed for a moment, it goes to the nearer end.
    var fingerDown by remember { mutableStateOf(false) }
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    if (largeTitle) {
        LaunchedEffect(appBarState) {
            var job: Job? = null
            var animating = false
            snapshotFlow { appBarState.heightOffset to fingerDown }.collect { (offset, down) ->
                if (down) {
                    job?.cancel()
                    return@collect
                }
                if (animating) return@collect
                job?.cancel()
                if (settleTarget(offset, appBarState.heightOffsetLimit) == null) return@collect
                job = launch {
                    delay(SETTLE_DELAY_MS)
                    val from = appBarState.heightOffset
                    val to = settleTarget(from, appBarState.heightOffsetLimit) ?: return@launch
                    animating = true
                    try {
                        animate(from, to, animationSpec = settleSpec) { value, _ ->
                            appBarState.heightOffset = value
                        }
                    } finally {
                        animating = false
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        fingerDown = event.changes.any { it.pressed }
                    }
                }
            }
            .nestedScroll(connection),
        topBar = {
            if (largeTitle) {
                // Expressive's large flexible bar, drawn through TwoRowsTopAppBar
                // so the large title and the small one can differ: the large
                // one wraps onto a second line rather than being shrunk or cut
                // ("Import from bookmarks", and longer once translated; review
                // 22, 64), and the bar grows to fit it. Collapsed, one line.
                TwoRowsTopAppBar(
                    title = { expanded ->
                        if (expanded) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.displaySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(
                                    bottom = if (subTitle.isEmpty()) LARGE_TITLE_BOTTOM else 0.dp,
                                ),
                            )
                        } else {
                            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    subtitle = if (subTitle.isNotEmpty()) {
                        { expanded ->
                            Text(
                                text = subTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (expanded) Modifier.padding(bottom = LARGE_TITLE_BOTTOM) else Modifier,
                            )
                        }
                    } else null,
                    navigationIcon = { navContent() },
                    actions = actions,
                    collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
                    expandedHeight = if (subTitle.isNotEmpty()) {
                        TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight
                    } else {
                        TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                TopAppBar(
                    title = { titleContent() },
                    navigationIcon = { navContent() },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = FabPosition.Center,
        // The bars and the camera cutout, named so the cutout is counted.
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    ) { padding ->
        // The sides applied here, once, for every page. Pages build their own
        // padding from the top and bottom and add a 16dp margin at the sides,
        // which threw the side insets away: in landscape the navigation bar
        // and a cutout sat over the content (design audit, 21 October). The
        // page is handed the top and bottom only, so nothing is counted twice.
        val direction = LocalLayoutDirection.current
        Box(
            modifier = Modifier
                .padding(
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                )
                .overscroll(stretch),
        ) {
            content(
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                )
            )
        }
    }
}

/** Below a large title with no subtitle: Material's own large-title spacing. */
private val LARGE_TITLE_BOTTOM = 28.dp

/** How long the bar must sit still, half open, before it settles. */
private const val SETTLE_DELAY_MS = 120L

/**
 * Where a bar held at [offset] settles: fully open (0) or fully closed
 * ([limit], negative), whichever is nearer; null when it is already at one
 * end, or cannot collapse at all.
 */
internal fun settleTarget(offset: Float, limit: Float): Float? {
    if (limit >= 0f) return null
    if (offset >= -0.5f || offset <= limit + 0.5f) return null
    return if (offset > limit / 2) 0f else limit
}

/**
 * A page's scrolling, between its content and the app bar.
 *
 * [holdBack]: Material's collapsing bar takes every upward drag first, so a
 * page that fits on the screen still shrank its title the moment it was
 * touched, and a slow drag left the title half collapsed and greyed out
 * (review 53 and 50). The content's own scrolling is not visible from here,
 * so the first drag a page actually scrolls is the evidence: until then, the
 * bar is passed only what the content left over, which on a page that fits is
 * nothing it acts on. Expanding at the top and settling after a fling are the
 * bar's, unchanged.
 *
 * [stretch]: Compose gives no stretch to a list with nothing to scroll, so a
 * short page (Settings) was dead under the finger, where Android's own
 * screens give a little (review 81). Until the content shows it scrolls,
 * whatever the bar leaves of a drag or a fling goes to [stretch]; after that
 * the list's own stretch does the job, as before.
 */
private fun pageScrolling(
    bar: NestedScrollConnection,
    holdBack: Boolean,
    stretch: OverscrollEffect?,
) = object : NestedScrollConnection {
    var contentScrolls = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (contentScrolls || !holdBack) bar.onPreScroll(available, source) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y != 0f) contentScrolls = true
        val barTook = bar.onPostScroll(consumed, available, source)
        if (contentScrolls || stretch == null || source != NestedScrollSource.UserInput) return barTook
        val left = available - barTook
        if (left.y == 0f) return barTook
        stretch.applyToScroll(Offset(0f, left.y), source) { Offset.Zero }
        return barTook + Offset(0f, left.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (contentScrolls || !holdBack) bar.onPreFling(available) else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val barTook = bar.onPostFling(consumed, available)
        if (contentScrolls || stretch == null) return barTook
        stretch.applyToFling(Velocity(0f, (available - barTook).y)) { Velocity.Zero }
        return available
    }
}

@Composable
fun RoundButton(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    description: String = "",
    onClick: () -> Unit,
) {
    IconButton(
        modifier = modifier.size(size = size),
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
        )
    }
}
