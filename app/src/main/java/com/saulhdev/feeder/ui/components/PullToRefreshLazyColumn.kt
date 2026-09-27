package com.saulhdev.feeder.ui.components

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
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

    LaunchedEffect(pulledAt) {
        if (pulledAt == 0L) return@LaunchedEffect
        settled = false
        delay(MIN_VISIBLE_MS)
        settled = true
    }

    val showing = isRefreshing || (pulledAt != 0L && !settled)
    return showing to { pulledAt = System.currentTimeMillis() }
}

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
