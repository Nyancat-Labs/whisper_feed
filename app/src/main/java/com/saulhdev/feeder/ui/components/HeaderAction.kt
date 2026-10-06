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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One size for every icon in a header, so a row of them sits on a rhythm. */
val HeaderIconSize = 22.dp

/**
 * A header action that is a toggle, not a command.
 *
 * Filled while it is on, so the state of the feed is legible from the header
 * alone rather than only after opening what the button opens.
 *
 * Shared between the launcher overlay and the app because the two headers
 * carry the same three actions, and they had drifted: the app drew its
 * bookmarks toggle as a Surface with its own padding while everything beside
 * it was an IconButton, so the row was subtly unevenly spaced — the sizes
 * differed, and only one of the three had a touch target the platform
 * considers large enough.
 */
@Composable
fun HeaderToggleAction(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) = HeaderToggleAction(description, active, onClick) {
    Icon(icon, description, modifier = Modifier.size(HeaderIconSize))
}

/** The same, for artwork that ships as a drawable rather than an ImageVector. */
@Composable
fun HeaderToggleAction(
    painter: Painter,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) = HeaderToggleAction(description, active, onClick) {
    Icon(painter, description, modifier = Modifier.size(HeaderIconSize))
}

@Composable
private fun HeaderToggleAction(
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (active) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { content() }
    } else {
        IconButton(onClick = onClick) { content() }
    }
}

/** A header action that just does something, sized to match the toggles. */
@Composable
fun HeaderAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, description, modifier = Modifier.size(HeaderIconSize))
    }
}
