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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.data.StarterSource

/**
 * The list of feeds on offer, with everything visible before anything happens.
 *
 * One row per feed rather than one row per category with a count: the reader
 * has to be able to see the name of every publication they are about to
 * subscribe to, or "no algorithm decides what you see" is being said over a
 * list they cannot read.
 */
fun LazyListScope.starterSources(
    sources: List<StarterSource>,
    selected: Set<String>,
    onToggle: (StarterSource) -> Unit,
) {
    // Grouped so the list reads as three short lists rather than one long one,
    // and because these categories become the feed's first chips.
    val grouped = sources.groupBy { it.categoryId }
    grouped.forEach { (categoryId, inCategory) ->
        item(key = "cat-$categoryId") {
            Text(
                text = stringResource(categoryId),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        items(inCategory) { source ->
            StarterSourceRow(
                source = source,
                checked = source.url in selected,
                onToggle = { onToggle(source) },
            )
        }
    }
}

private fun LazyListScope.items(
    sources: List<StarterSource>,
    row: @Composable (StarterSource) -> Unit,
) = sources.forEach { source -> item(key = source.url) { row(source) } }

@Composable
private fun StarterSourceRow(
    source: StarterSource,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (checked) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            // The whole row, not only the box. A 24dp target for a decision
            // somebody is asked to make nine times is a bad trade.
            .clickable(onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = source.url.substringAfter("://").substringBefore("/"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
