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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The feed's Undo messages, solid and lifted off the page.
 *
 * They sit over the feed, which is mostly photographs, and over a busy one
 * the message was hard to read (owner, 20 October). Every colour is named
 * rather than left to the default. The Snackbar's own elevation gives the
 * shadow: an extra one here was drawn round the 12dp margin Snackbar adds
 * inside its modifier, a square-cornered box under the message (owner,
 * 25 October).
 */
@Composable
fun FeedSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = state, modifier = modifier) { data ->
        val shape = MaterialTheme.shapes.medium
        Snackbar(
            snackbarData = data,
            shape = shape,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
            dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}
