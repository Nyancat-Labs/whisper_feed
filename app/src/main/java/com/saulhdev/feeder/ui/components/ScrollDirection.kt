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
package com.saulhdev.feeder.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/**
 * Whether a list was last moved towards its top.
 *
 * For the back-to-top button (review 83): shown while going back up, out of
 * the way while reading down, where it sat over the bottom card's ⋮. As
 * Google's own apps do. [position] is the list's first visible item and its
 * offset.
 */
@Composable
fun rememberScrollingUp(position: () -> Pair<Int, Int>): State<Boolean> {
    val up = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var last = position()
        snapshotFlow(position).collect { now ->
            if (now != last) {
                up.value = isTowardsTop(last, now)
                last = now
            }
        }
    }
    return up
}

/** Whether moving from [before] to [after] (item, offset) goes towards the top. */
internal fun isTowardsTop(before: Pair<Int, Int>, after: Pair<Int, Int>): Boolean =
    after.first < before.first || (after.first == before.first && after.second < before.second)
