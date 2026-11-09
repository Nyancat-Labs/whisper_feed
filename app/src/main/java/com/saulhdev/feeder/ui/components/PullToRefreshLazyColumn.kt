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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
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
    val anchor = remember { RefreshAnchor() }

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
            indicator = { RefreshIndicator(pullToRefreshState, showing, anchor) },
        ) {
            CompositionLocalProvider(LocalRefreshAnchor provides anchor) {
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
    val anchor = remember { RefreshAnchor() }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = showing,
            state = pullToRefreshState,
            onRefresh = {
                onPulled()
                coroutineScope.launch { onRefresh() }
            },
            modifier = Modifier.fillMaxSize(),
            indicator = { RefreshIndicator(pullToRefreshState, showing, anchor) },
        ) {
            CompositionLocalProvider(LocalRefreshAnchor provides anchor) {
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
}

/**
 * Where the update indicator rests while an update runs: the middle of the
 * line under the category chips, in window pixels. The freshness line
 * reports itself here; NaN while it is not on screen.
 */
class RefreshAnchor {
    var centreY by mutableFloatStateOf(Float.NaN)
}

/** Provided by the refresh containers to what they hold. */
val LocalRefreshAnchor = compositionLocalOf<RefreshAnchor?> { null }

/**
 * One indicator, Expressive's shape in a filled container (review 47), from
 * the pull to the end of the update (owner, 9 November).
 *
 * Material's own went back up to its resting place when you let go, which
 * left it over the weather tiles; hiding it there and showing a second, small
 * one in the line below made it jump up, down and in from the side. This one
 * follows the finger, then glides from wherever it was let go to the middle
 * of the line under the chips and spins there until the update ends, then
 * fades. A background update shows it there too. With that line scrolled
 * away it rests near the top instead.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.RefreshIndicator(state: PullToRefreshState, refreshing: Boolean, anchor: RefreshAnchor) {
    val density = LocalDensity.current
    val size = with(density) { REFRESH_INDICATOR.toPx() }
    val threshold = with(density) { PullToRefreshDefaults.PositionalThreshold.toPx() }
    val fallback = with(density) { REFRESH_FALLBACK.toPx() }
    var boxTop by remember { mutableFloatStateOf(0f) }
    val y = remember { Animatable(-size) }
    val alpha = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }

    val target = restingTop(anchor.centreY, boxTop, size, fallback)

    // Following the finger: only while no update runs and none is fading.
    LaunchedEffect(refreshing) {
        if (refreshing) return@LaunchedEffect
        snapshotFlow { state.distanceFraction }.collect { fraction ->
            if (leaving) return@collect
            y.snapTo(fraction * threshold - size)
            alpha.snapTo((fraction * 2f).coerceIn(0f, 1f))
        }
    }
    // Into place when an update starts, and kept there as the line moves.
    LaunchedEffect(refreshing, target) {
        if (!refreshing) return@LaunchedEffect
        launch { alpha.animateTo(1f) }
        y.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
    }
    // Out when it ends: faded where it is, not sent back up.
    LaunchedEffect(refreshing) {
        if (refreshing || alpha.value == 0f) return@LaunchedEffect
        leaving = true
        alpha.animateTo(0f)
        y.snapTo(-size)
        leaving = false
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .clipToBounds()
            .onGloballyPositioned { boxTop = it.positionInWindow().y },
    ) {
        if (alpha.value > 0f) {
            val pulled = { state.distanceFraction.coerceIn(0f, 1f) }
            val placed = Modifier
                .align(Alignment.TopCenter)
                .size(REFRESH_INDICATOR)
                .graphicsLayer {
                    translationY = y.value
                    this.alpha = alpha.value
                }
            if (refreshing || leaving) ContainedLoadingIndicator(modifier = placed)
            else ContainedLoadingIndicator(progress = pulled, modifier = placed)
        }
    }
}

/** The indicator's top while updating: centred on the anchor, or near the top without one. */
internal fun restingTop(anchorCentre: Float, boxTop: Float, size: Float, fallback: Float): Float {
    if (anchorCentre.isNaN()) return fallback
    val top = anchorCentre - boxTop - size / 2f
    return if (top < 0f) fallback else top
}

private val REFRESH_INDICATOR = 48.dp
private val REFRESH_FALLBACK = 16.dp
