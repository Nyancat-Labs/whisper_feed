/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    heading: String? = null,
    content: @Composable () -> Unit
) {
    PreferenceGroupHeading(heading = heading)
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.primary
    ) {
        Surface(color = Color.Transparent) {
            Column(modifier = modifier) {
                content()
            }
        }
    }
}

@Composable
fun PreferenceGroup(
    heading: String,
    prefs: List<Any>,
    modifier: Modifier = Modifier,
    onPrefDialog: (Any) -> Unit = {}
) {
    val size = prefs.size

    PreferenceGroup(
        heading = heading,
        modifier = modifier,
    ) {
        prefs.forEachIndexed { i, it ->
            PreferenceBuilder(it, onPrefDialog, i, size)
            if (i < size - 1) Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun PreferenceGroupHeading(
    heading: String? = null,
    textAlignment: Alignment.Horizontal = Alignment.Start,
) = if (heading != null) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(48.dp)
            // Aligned with the text of the rows it heads: the group's cards
            // start at the screen's 16dp margin and a ListItem insets its own
            // content by another 16, so the rows read from 32.
            .padding(horizontal = 32.dp)
            .fillMaxWidth(),
        horizontalAlignment = textAlignment
    ) {
        // Primary-coloured, which is what makes a settings list read as
        // themed rather than as a stack of grey rows.
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
} else Spacer(modifier = Modifier.requiredHeight(8.dp))