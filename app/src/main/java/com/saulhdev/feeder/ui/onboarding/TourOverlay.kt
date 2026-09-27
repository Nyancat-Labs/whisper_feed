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
package com.saulhdev.feeder.ui.onboarding

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.theme.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The spotlight.
 *
 * A scrim over the whole screen with a hole cut in it over one real control,
 * and one sentence beside the hole. Nothing here knows what a feed is: it is
 * handed a rect and a sentence, which is the only reason it can be reused for
 * a second screen later without being rewritten.
 */
@Composable
fun TourOverlay(
    steps: List<TourStep> = TOUR_STEPS,
    onFinished: () -> Unit,
) {
    val targets = LocalTourTargets.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val animate = !reducedMotion()

    var current by remember { mutableIntStateOf(-1) }
    var rect by remember { mutableStateOf<Rect?>(null) }

    // What is actually on screen. Read fresh at every step rather than once at
    // the start: the chips row appears as soon as a sync produces categories,
    // and a set captured before that would hide a step that is now there.
    val present = { targets.keys.toSet() }

    // Wait a beat before measuring. A step whose control is still animating in
    // would otherwise be spotlighted where it started rather than where it
    // comes to rest, and the hole would sit next to the thing it describes.
    LaunchedEffect(current) {
        if (current < 0) {
            // Nothing has announced itself yet. onGloballyPositioned runs
            // after composition, so on the first frame this map is empty —
            // and an earlier version treated that as "no targets exist",
            // finished the tour on the spot, and wrote the flag that stops it
            // ever running again. The tour was consumed without appearing.
            val arrived = withTimeoutOrNull(FIRST_TARGET_WAIT_MS) {
                while (present().isEmpty()) delay(TICK_MS)
                true
            }
            if (arrived == null) {
                // Genuinely nothing to point at after a second of waiting.
                // Finishing is right; this is the case the timeout is for.
                onFinished()
                return@LaunchedEffect
            }
            // One more beat so the rest of the row is measured too, rather
            // than starting at whichever target happened to report first.
            delay(SETTLE_MS)
            current = TourMachine.first(steps, present())
                ?: run { onFinished(); return@LaunchedEffect }
            return@LaunchedEffect
        }
        rect = null
        delay(SETTLE_MS)
        val measured = targets[steps[current].target]
        if (measured == null) {
            // The control stopped existing between choosing this step and
            // arriving at it. Advancing is the only sane response; stalling
            // would leave the reader looking at a lit hole over nothing.
            val next = TourMachine.next(steps, current, present())
            if (next == null) onFinished() else current = next
        } else {
            rect = measured
        }
    }

    if (current < 0) return
    val step = steps[current]
    val hole = rect ?: return

    // Skip counts the same as finishing. The point of the flag is "do not show
    // this again", and somebody who has decided they do not want it has said
    // that at least as clearly as somebody who pressed Next six times.
    BackHandler { onFinished() }

    val advance = {
        val next = TourMachine.next(steps, current, present())
        if (next == null) onFinished() else current = next
    }

    val ring = MaterialTheme.colorScheme.primary

    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(if (animate) FADE_MS else 0),
        label = "tourScrim",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Look, don't touch. Advancing is always the tooltip's own button,
            // so the tour never has to guess whether the real interaction
            // happened the way it expected — and a mis-tap on a spotlighted
            // control cannot navigate away mid-tour.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Without an offscreen layer, BlendMode.Clear punches through
                // to black rather than to the app underneath. This is the one
                // line that makes the hole a hole.
                .graphicsLayer(
                    alpha = alpha,
                    compositingStrategy = CompositingStrategy.Offscreen,
                )
                .clearAndSetSemantics { }
        ) {
            drawRect(Color.Black.copy(alpha = SCRIM_ALPHA))
            val pad = PADDING_PX * density.density
            val topLeft = Offset(hole.left - pad, hole.top - pad)
            val size = Size(hole.width + pad * 2, hole.height + pad * 2)
            val radius = androidx.compose.ui.geometry.CornerRadius(
                HOLE_RADIUS_DP * density.density
            )
            drawRoundRect(
                color = Color.Transparent,
                topLeft = topLeft,
                size = size,
                cornerRadius = radius,
                blendMode = BlendMode.Clear,
            )
            // A ring, because cutting the scrim only helps a control that was
            // bright to begin with. A settings gear is a thin grey glyph on a
            // dark bar: undimmed it looks very much like the dimmed one next
            // to it, and the reader is left hunting for what changed. The ring
            // does not depend on what is underneath it.
            drawRoundRect(
                color = ring,
                topLeft = topLeft,
                size = size,
                cornerRadius = radius,
                style = Stroke(width = RING_WIDTH_DP * density.density),
            )
        }

        TourTooltip(
            step = step,
            hole = hole,
            position = TourMachine.position(steps, current, present()),
            isLast = TourMachine.isLast(steps, current, present()),
            onSkip = onFinished,
            onNext = advance,
        )
    }
}

/**
 * The card, placed so it does not cover the thing it is talking about.
 *
 * Below the target, above it when there is no room below, and at the foot of
 * the screen when the target is too tall for either — which is the case for a
 * large article card on a short phone.
 */
@Composable
private fun TourTooltip(
    step: TourStep,
    hole: Rect,
    position: Pair<Int, Int>,
    isLast: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val density = LocalDensity.current
    // Measured rather than guessed at: the placement depends on the card's
    // height, and the text is translated into fifteen languages with fifteen
    // different heights.
    var height by remember { mutableIntStateOf(0) }
    val gap = GAP_DP * density.density

    // The status bar, the camera cutout, and the gesture bar. The card is
    // positioned in root coordinates so it can be compared against the hole,
    // which means these have to be subtracted by hand rather than by a
    // windowInsetsPadding that would move the origin out from under it.
    val insets = WindowInsets.safeDrawing
    val topLimit = insets.getTop(density).toFloat()
    val bottomInset = insets.getBottom(density).toFloat()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = constraints.maxHeight.toFloat()
        val bottomLimit = (screenHeight - bottomInset - height).coerceAtLeast(topLimit)

        // Below the target, above it when there is no room below, and at the
        // foot of the screen when the target is too tall for either.
        //
        // The comparison used to be written inside a graphicsLayer block,
        // where `size` is the card's own size rather than the screen's — so
        // "does it fit below?" compared the card against itself, always failed,
        // and fell through to a fallback that worked out to zero. The card sat
        // at the very top of the display on every step, under the status bar
        // and the camera cutout, covering the control it was describing.
        val target = when {
            hole.bottom + gap + height <= screenHeight - bottomInset -> hole.bottom + gap
            hole.top - gap - height >= topLimit -> hole.top - gap - height
            else -> bottomLimit
        }
        val y = target.coerceIn(topLimit, bottomLimit)

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset { IntOffset(0, y.roundToInt()) }
                .onSizeChanged { height = it.height }
                // Nothing to show until it has been measured once: drawing it
                // at a position worked out from a height of zero would put it
                // in the wrong place for exactly one frame, which reads as a
                // flicker every time a step changes.
                .alpha(if (height == 0) 0f else 1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(step.titleId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(step.bodyId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dots(position)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            R.string.tour_step_of, position.first, position.second
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.tour_skip))
                    }
                    TextButton(onClick = onNext) {
                        Text(
                            stringResource(
                                if (isLast) R.string.tour_got_it else R.string.tour_next
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dots(position: Pair<Int, Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(position.second) { i ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (i + 1 == position.first) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

private const val SETTLE_MS = 250L

/**
 * How long to wait for the first control to report where it is.
 *
 * Composition finishes before layout does, so on the frame the tour starts
 * nothing has measured itself yet. A second is far longer than that takes and
 * still short enough that a screen with genuinely nothing on it does not hang.
 */
private const val FIRST_TARGET_WAIT_MS = 1_000L

/** How often to look while waiting for that first one. */
private const val TICK_MS = 16L
private const val FADE_MS = 200
/**
 * How dark the screen goes behind the spotlight.
 *
 * It was 0.78, which buried the feed so completely that the tour looked like
 * it was describing a black rectangle. The point is to say "look here", not to
 * hide everything else — the reader should still recognise the screen they are
 * being shown around.
 */
private const val SCRIM_ALPHA = 0.55f
private const val PADDING_PX = 6f
private const val HOLE_RADIUS_DP = 16f

/** How heavy the ring around the lit control is. */
private const val RING_WIDTH_DP = 2.5f
private const val GAP_DP = 12f
