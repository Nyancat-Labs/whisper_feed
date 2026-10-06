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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * The controls the tour can point at.
 *
 * An enum rather than the string selectors the web version uses. There is no
 * `querySelector` in Compose and no way to build one — a composable is not
 * addressable from outside itself — so the targets announce themselves instead
 * of being looked up. The compiler then guarantees that a step names a control
 * that exists, which the string version could never do.
 */
enum class TourTarget {
    Glance,
    Chips,
    Article,
    Bookmarks,
    Filter,
    Overflow,
}

/**
 * Where each target currently is, in root coordinates.
 *
 * A snapshot map so the overlay recomposes when a rect arrives or moves. It is
 * deliberately shared mutable state rather than a hoisted value: the targets
 * are scattered across a screen that already has enough parameters, and every
 * one of them would otherwise need a callback threaded down to it.
 */
val LocalTourTargets = compositionLocalOf<MutableMap<TourTarget, Rect>> { mutableStateMapOf() }

/** Provides the map for one screen's worth of targets. */
@Composable
fun ProvideTourTargets(content: @Composable () -> Unit) {
    val targets = remember { mutableStateMapOf<TourTarget, Rect>() }
    CompositionLocalProvider(LocalTourTargets provides targets, content = content)
}

/**
 * Marks a control as something the tour can spotlight.
 *
 * Costs one `onGloballyPositioned` per target whether or not a tour is
 * running, which is a layout callback and not a measure — the alternative was
 * conditioning it on the tour being active, and a modifier that appears
 * halfway through a layout pass is a worse trade than a callback that writes
 * to a map nobody reads.
 */
fun Modifier.tourTarget(target: TourTarget, targets: MutableMap<TourTarget, Rect>): Modifier =
    this.onGloballyPositioned { targets[target] = it.boundsInRoot() }

/** The same, reading the map from the composition. */
@Composable
fun Modifier.tourTarget(target: TourTarget): Modifier {
    val targets = LocalTourTargets.current
    // Forgotten when the control leaves the screen. Without this a rect
    // outlives what it measured — a category chip row whose last category was
    // deleted, say — and the tour lights a hole over empty background.
    DisposableEffect(target) { onDispose { targets.remove(target) } }
    return this.tourTarget(target, targets)
}
