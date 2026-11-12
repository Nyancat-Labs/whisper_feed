package com.saulhdev.feeder.ui.components

import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch

/**
 * How long the indicator stays up once it has been shown.
 *
 * `syncAllFeeds` enqueues work and returns, so an indicator driven only by
 * that call would appear and vanish within a frame. The old fix was to hold it
 * up for a flat two seconds after the call returned, which meant the spinner
 * had no relationship to the sync at all: it stopped on a timer whether the
 * sync had finished, failed, or not started.
 *
 * The indicator follows the real syncing state now, with this as a floor so a
 * sync that resolves instantly still reads as something having happened.
 */
private const val MIN_VISIBLE_MS = 600L

/**
 * The refreshing flag the indicator should actually use.
 *
 * True while the caller says a sync is running, and for a moment after a pull
 * so the gesture is acknowledged even if the sync has not been reported yet.
 */
@Composable
private fun rememberRefreshing(isRefreshing: Boolean): Pair<Boolean, () -> Unit> {
    var pulledAt by remember { mutableLongStateOf(0L) }
    var settled by remember { mutableStateOf(false) }
    val running by rememberUpdatedState(isRefreshing)

    // Held from the pull until the update it started has finished, not for a
    // flat moment. Enqueued work can take longer than that to report itself
    // running, and the gap dropped the indicator and brought it back: up,
    // then down again (owner, 9 November). If no update ever reports, it
    // gives up after START_GRACE_MS.
    LaunchedEffect(pulledAt) {
        if (pulledAt == 0L) return@LaunchedEffect
        settled = false
        delay(MIN_VISIBLE_MS)
        val started = running || withTimeoutOrNull(START_GRACE_MS) {
            snapshotFlow { running }.first { it }
        } != null
        if (started) snapshotFlow { running }.first { !it }
        settled = true
    }

    val showing = isRefreshing || (pulledAt != 0L && !settled)
    return showing to { pulledAt = System.currentTimeMillis() }
}

/** How long after a pull to wait for the update to report that it started. */
private const val START_GRACE_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshLazyColumn(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: suspend () -> Unit,
    content: LazyListScope.() -> Unit,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    val (showing, onPulled) = rememberRefreshing(isRefreshing)
    val coroutineScope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        PullToRefreshBox(
            isRefreshing = showing,
            state = pullToRefreshState,
            onRefresh = {
                onPulled()
                coroutineScope.launch { onRefresh() }
            },
            modifier = Modifier.fillMaxSize(),
            indicator = { RefreshIndicator(pullToRefreshState, showing) },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/**
 * The Mosaic layout's container: the same pull-to-refresh, a staggered grid
 * inside it.
 *
 * A separate function rather than a flag on the one above, because the two take
 * different content scopes — LazyListScope and LazyStaggeredGridScope — and a
 * single function would have to take both and ignore one.
 */
@Composable
fun PullToRefreshStaggeredGrid(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: suspend () -> Unit,
    content: LazyStaggeredGridScope.() -> Unit,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    contentPadding: PaddingValues = PaddingValues(8.dp),
    columns: Int = 2,
    /** Between lanes. Mosaic's tiles bring their own; full cards side by side do not. */
    laneSpacing: Dp = 0.dp,
) {
    val (showing, onPulled) = rememberRefreshing(isRefreshing)
    val coroutineScope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = showing,
            state = pullToRefreshState,
            onRefresh = {
                onPulled()
                coroutineScope.launch { onRefresh() }
            },
            modifier = Modifier.fillMaxSize(),
            indicator = { RefreshIndicator(pullToRefreshState, showing) },
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(laneSpacing),
                state = gridState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        }
    }
}

/**
 * Material 3 Expressive's own pull-to-refresh indicator, used as Material
 * intends (owner, 12 November: "follow the Material guidelines").
 *
 * It follows the pull, then rests at Material's fixed spot near the top of
 * the list for the whole update, in its own raised container, and does not
 * move while the list scrolls. Two hand-made versions came before it: one
 * that left on release for a small indicator in the "Today" line (it jumped
 * between the two), and one that glided to that line and chased it while the
 * list scrolled. Both tried to keep it off the weather tiles; resting over
 * the top of the list, raised, is what the guidelines ask for.
 *
 * [refreshing] is held from the pull to the end of the update by
 * rememberRefreshing, so the indicator does not drop away and come back
 * while the update is being queued.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.RefreshIndicator(state: PullToRefreshState, refreshing: Boolean) {
    PullToRefreshDefaults.LoadingIndicator(
        state = state,
        isRefreshing = refreshing,
        modifier = Modifier.align(Alignment.TopCenter),
    )
}
