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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.asState
import com.saulhdev.feeder.data.db.models.FeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * The feed as it is drawn: the articles, and the sizes and stories worked out
 * for exactly those articles.
 *
 * One object because the three are only meaningful together. The sizes are a
 * list indexed by position, so sizes from one list paired with the articles of
 * the next put the hero on the wrong card; [focus] is the narrowing the
 * articles were built under, for the same reason. See AnchorFeedOnFocusChange.
 */
@Immutable
class FeedFrame(
    val articles: List<FeedItem>,
    val emphasis: List<FeedEmphasis>,
    val clusters: Map<String, StoryCluster>,
    val focus: String?,
)

/** What a frame is built from. Equal inputs build an equal frame. */
private data class FeedFrameInputs(
    val articles: List<FeedItem>,
    val focus: String?,
    val affinity: Map<String, Int>,
    val habit: Map<Long, Float>,
    val pace: Map<Long, Float>,
    val breaking: Boolean,
)

/**
 * Sizes and stories for [articles], with the clustering done once.
 *
 * It was done twice: the page asked for the clusters, and the sizing asked
 * for them again inside itself, for the same list.
 */
internal fun buildFeedFrame(
    articles: List<FeedItem>,
    focus: String?,
    affinity: Map<String, Int>,
    habit: Map<Long, Float>,
    pace: Map<Long, Float>,
    breaking: Boolean,
    nowMs: Long,
): FeedFrame {
    // Off unless the reader has asked for it. The detection is inference, and
    // inference is occasionally wrong in public: a feed that gives a hero slot
    // to the wrong article, for reasons nobody asked for, is worse than one
    // that never tries.
    val clusters = if (breaking) clusterStories(articles, nowMs) else emptyMap()
    // An article keeps the size it was first given. Without this the feed
    // reflows under the reader's finger: marking an article read subtracts
    // from its weight, so scrolling past one with read-on-scroll enabled
    // shrank it from a card to a row and shunted everything below it up the
    // screen. Every article did it in turn, so the whole list jumped
    // continuously while being scrolled.
    val emphasis = FeedEmphasisMemory.settle(
        articles,
        feedEmphasisFor(articles, affinity, nowMs, habit, clusters, pace),
    )
    return FeedFrame(articles, emphasis, clusters, focus)
}

/**
 * The frame to draw, rebuilt off the main thread and never under a moving
 * finger.
 *
 * The feed is reloaded every time an article is written to: each article
 * marked read while scrolling, each batch of time on screen, every source a
 * sync finishes. Each reload used to cluster and size all five hundred
 * articles during composition, on the main thread, twice over for the
 * clusters. Measured at about 6ms on a desktop, which is several frames on a
 * phone at 120Hz, and read-on-scroll triggers a reload every few cards. That
 * is the stutter "every 5th or 10th post".
 *
 * Now the work runs on a background thread, and a new frame waits for the
 * list to come to rest before it replaces the one being scrolled. Nothing is
 * lost by waiting: a card dimming or a new article arriving is something to
 * see when the list stops, not in the middle of a fling.
 *
 * The first frame is built here and now, so a feed that has just opened does
 * not show its empty state for a frame.
 *
 * @param isScrolling whether the list is being dragged or is still moving.
 */
@Composable
fun rememberFeedFrame(
    articles: List<FeedItem>,
    focus: String?,
    isScrolling: () -> Boolean,
): FeedFrame {
    val prefs: FeedPreferences = koinInject()
    val raw by prefs.sourceAffinity.get().collectAsState(initial = emptySet())
    val affinity = remember(raw) { parseAffinity(raw) }
    val habit = rememberReadingHabits()
    val pace = rememberSourcePace()
    val breaking by prefs.breakingNews.asState()
    val scrolling by rememberUpdatedState(isScrolling)

    val inputs = FeedFrameInputs(articles, focus, affinity, habit, pace, breaking)
    val built = remember { mutableStateOf(inputs) }
    var frame by remember {
        mutableStateOf(
            buildFeedFrame(articles, focus, affinity, habit, pace, breaking, System.currentTimeMillis())
        )
    }
    // Restarted by every new set of inputs, which also abandons a build or a
    // wait that a newer list has made pointless.
    LaunchedEffect(inputs) {
        if (inputs == built.value) return@LaunchedEffect
        snapshotFlow { scrolling() }.first { !it }
        frame = withContext(Dispatchers.Default) {
            with(inputs) {
                buildFeedFrame(articles, focus, affinity, habit, pace, breaking, System.currentTimeMillis())
            }
        }
        built.value = inputs
    }
    return frame
}
