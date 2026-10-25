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

import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ThumbsDown
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipe a card to the right to say "Not interested".
 *
 * **Heavy on purpose**, by the owner's request: it goes only past 40% of the
 * card's width, or a quarter with a hard flick, with a tick where it commits.
 * Anything less springs back. A feed is scrolled with a thumb all day, and a
 * dismissal that happens by accident is worse than one that takes effort.
 *
 * The weight is in the distance, not in drag. The first version also moved
 * the card at half the finger's speed, which put the commit point a full
 * screen's width of finger travel away: from the middle of the card, where
 * a swipe has to start (the left edge is Android's back gesture), it could
 * not be reached at all (owner's test, 20 October).
 *
 * **Rightward only.** In the Lawnchair panel a leftward swipe is how the reader
 * goes home, so this never claims a drag that starts leftward, or one that is
 * more up-and-down than sideways: the list's own scroll and the launcher keep
 * those. The same direction in the app, so one habit works everywhere.
 *
 * With [onDismiss] null the card is as it was: no swipe and no action. That
 * is how saved and pinned cards are left alone.
 */
@Composable
fun NotInterestedSwipe(
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onDismiss == null) {
        Box(modifier) { content() }
        return
    }
    val offset = remember { Animatable(0f) }
    // 1 while the card is there, 0 once it has gone: the space closes behind
    // it rather than standing empty until the feed reloads (owner, 20 October).
    val height = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val flickPxPerS = with(density) { SwipeWeight.FLICK_DP_PER_S.dp.toPx() }
    val label = stringResource(R.string.not_interested)
    // Read through this rather than used as the gesture's key. A sync
    // refreshes the feed and hands every card a new lambda; keyed on it, the
    // gesture was cancelled mid-drag and the card left where the finger had
    // put it, half off the screen (owner's test, 19 October).
    val dismiss by rememberUpdatedState(onDismiss)

    Box(
        modifier = modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val shown = (placeable.height * height.value).roundToInt()
                layout(placeable.width, shown) { placeable.place(0, 0) }
            }
            .semantics {
            // TalkBack and switch users get the same action, by name.
            customActions = listOf(CustomAccessibilityAction(label) { onDismiss(); true })
        },
    ) {
        // What the swipe uncovers, so the reader can see what they are doing
        // before they finish it.
        val width = remember { floatArrayOf(1f) }
        Row(
            modifier = Modifier
                .matchParentSize()
                // Faded before it is painted. The other way round faded only
                // what was drawn after the colour, so the colour itself sat
                // there at full strength behind every card and showed in the
                // gaps between them as lines (owner, 20 October).
                .alpha((offset.value / (width[0] * SwipeWeight.DISTANCE)).coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = Phosphor.ThumbsDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        // The List and Text rows have no background of their own, and the
        // label behind would show through them as they move.
        val moving = if (offset.value > 0f) {
            Modifier.background(MaterialTheme.colorScheme.surface)
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offset.value }
                .then(moving)
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        width[0] = size.width.toFloat().coerceAtLeast(1f)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var total = Offset.Zero
                        var claimed = false
                        var crossed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change: PointerInputChange =
                                    event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                total += delta
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!claimed) {
                                    if (change.isConsumed) return@awaitEachGesture
                                    if (abs(total.x) < slop && abs(total.y) < slop) continue
                                    // Decided once, at the slop: rightward and
                                    // clearly sideways, or not ours at all.
                                    if (!SwipeWeight.claims(total.x, total.y)) return@awaitEachGesture
                                    claimed = true
                                }
                                change.consume()
                                val next = (offset.value + delta.x * SwipeWeight.RESISTANCE).coerceAtLeast(0f)
                                scope.launch { offset.snapTo(next) }
                                val past = next >= width[0] * SwipeWeight.DISTANCE
                                if (past != crossed) {
                                    crossed = past
                                    // Felt as well as seen: past here, letting go
                                    // dismisses it.
                                    if (past) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Whatever stopped the gesture, the card never
                            // stays where it was dropped.
                            if (claimed) scope.launch { offset.animateTo(0f, SwipeWeight.BACK) }
                            throw e
                        }
                        if (!claimed) return@awaitEachGesture
                        val velocity = tracker.calculateVelocity().x
                        if (SwipeWeight.dismisses(offset.value, width[0], velocity, flickPxPerS)) {
                            scope.launch {
                                offset.animateTo(width[0], spring(dampingRatio = 1f, stiffness = 600f))
                                height.animateTo(0f, tween(SwipeWeight.CLOSE_MS))
                                dismiss?.invoke()
                            }
                        } else {
                            scope.launch { offset.animateTo(0f, SwipeWeight.BACK) }
                        }
                    }
                },
        ) {
            content()
        }
    }
}

/** How hard the swipe is, apart from the gesture so it can be tested. */
internal object SwipeWeight {
    /** The card moves this much for each pixel the finger does. See above. */
    const val RESISTANCE = 1f

    /** Share of the card's width past which letting go dismisses. */
    const val DISTANCE = 0.4f

    /** With a flick at least this fast, this share is enough. */
    const val FLICK_DISTANCE = 0.25f
    const val FLICK_DP_PER_S = 1800f

    /** Sideways by at least this much more than up or down, to count as a swipe. */
    const val SIDEWAYS_RATIO = 2f

    /** How long the space a dismissed card leaves takes to close. */
    const val CLOSE_MS = 220

    /** Back into place, the Expressive spatial spring the picture viewer uses. */
    val BACK = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)

    fun claims(dx: Float, dy: Float): Boolean = dx > 0 && dx >= abs(dy) * SIDEWAYS_RATIO

    fun dismisses(offset: Float, width: Float, velocity: Float, flick: Float): Boolean =
        offset >= width * DISTANCE || (offset >= width * FLICK_DISTANCE && velocity >= flick)
}
