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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R

/**
 * Saved articles, when there are none.
 *
 * The bookmarks view showed a blank screen — no artwork, no words, nothing to
 * distinguish "you have not saved anything" from "this failed to load". It is
 * the one view somebody arrives at deliberately, by pressing a button, so
 * arriving at nothing is a worse answer here than anywhere else in the app.
 */
@Composable
fun BookmarksEmptyState(modifier: Modifier = Modifier) {
    // Picked by what actually got painted rather than the theme setting: the
    // theme has three values and one of them is pure black.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(
                if (dark) R.drawable.empty_bookmarks_dark
                else R.drawable.empty_bookmarks_light
            ),
            contentDescription = null,
            modifier = Modifier.size(140.dp),
        )
        Text(
            text = stringResource(R.string.empty_bookmarks),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.empty_bookmarks_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
