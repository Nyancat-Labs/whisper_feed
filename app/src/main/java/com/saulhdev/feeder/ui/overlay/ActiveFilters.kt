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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.X
import com.saulhdev.feeder.data.entity.SORT_TITLE
import com.saulhdev.feeder.data.entity.SORT_SOURCE
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * What the feed is being narrowed by, said out loud above the articles.
 *
 * The funnel in the header goes solid when a filter is on, which answers
 * *whether* and not *what*. A reader who has muted a category, forgotten, and
 * come back a day later sees a shorter feed and a filled icon, and has to open
 * a sheet to find out why — which is the one moment they are most likely to
 * conclude the app has lost their articles.
 *
 * So each active narrowing is a chip, named, with its own cross. Removing one
 * is a tap where the evidence is, rather than a trip into a settings screen:
 * the sheet configures the filter, this is the filter itself.
 *
 * Nothing is drawn when nothing is filtered, so the ordinary feed is unchanged.
 *
 * Reads its own state rather than taking it as parameters. Both surfaces show
 * this, the launcher's scaffold takes thirty arguments already, and every
 * other cross-surface reader here — the emphasis, the dimming, the pinned
 * sources — does the same.
 */
@Composable
fun ActiveFilterBar(modifier: Modifier = Modifier) {
    val prefs: FeedPreferences = koinInject()
    val sources: SourcesRepository = koinInject()
    val scope = rememberCoroutineScope()

    val sourceIds by prefs.sourcesFilter.get().collectAsState(initial = emptySet())
    val tags by prefs.tagsFilter.get().collectAsState(initial = emptySet())
    val sort by prefs.sortingFilter.get().collectAsState(initial = "")
    val allSources by sources.getAllSourcesFlow().collectAsState(initial = emptyList())

    // A muted source is stored by id, and an id is not a thing to show
    // somebody. A source deleted while muted leaves an id naming nothing,
    // which is dropped rather than drawn as a blank chip.
    val mutedNames = remember(sourceIds, allSources) { mutedSources(sourceIds, allSources) }
    val nonDefaultSort = isNamedSort(sort)
    if (mutedNames.isEmpty() && tags.isEmpty() && !nonDefaultSort) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The sort is shown but not removable: there is no "no sort", so a
        // cross on it would have to mean "back to newest first", which is a
        // different action wearing the same symbol as the others.
        if (nonDefaultSort) {
            InputChip(
                selected = false,
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        stringResource(
                            if (sort == SORT_TITLE) R.string.filter_chip_sort_title
                            else R.string.filter_chip_sort_source
                        )
                    )
                },
            )
        }
        tags.forEach { tag ->
            FilterChip(
                text = stringResource(R.string.filter_chip_category, tag),
                onRemove = {
                    scope.launch { prefs.tagsFilter.setValue(prefs.tagsFilter.getValue() - tag) }
                },
            )
        }
        mutedNames.forEach { (id, title) ->
            FilterChip(
                text = stringResource(R.string.filter_chip_source, title),
                onRemove = {
                    scope.launch {
                        prefs.sourcesFilter.setValue(
                            prefs.sourcesFilter.getValue() - id.toString()
                        )
                    }
                },
            )
        }
        if (mutedNames.isNotEmpty() || tags.isNotEmpty()) {
            TextButton(onClick = {
                scope.launch {
                    prefs.sourcesFilter.setValue(emptySet())
                    prefs.tagsFilter.setValue(emptySet())
                }
            }) {
                Text(stringResource(R.string.filter_chip_clear))
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(text) },
        trailingIcon = {
            Icon(
                imageVector = Phosphor.X,
                // The chip itself is the button, and a description on the
                // icon would have a screen reader announce the same control
                // twice.
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}


/**
 * The muted sources that still name something, as id and title.
 *
 * A filter holds ids, and an id is not a thing to show anybody. A source
 * deleted while muted leaves an id naming nothing — the same shape as the
 * pinned-source ghost — and it is dropped rather than drawn as a blank chip
 * with a cross that removes an invisible thing.
 *
 * In the order the sources come in, so the chips follow the list rather than
 * whatever order a set iterates in.
 */
fun mutedSources(ids: Set<String>, sources: List<Feed>): List<Pair<Long, String>> =
    sources.filter { it.id.toString() in ids }.map { it.id to it.title }

/** Whether a sort is worth naming, which the default ordering is not. */
fun isNamedSort(sort: String): Boolean = sort == SORT_TITLE || sort == SORT_SOURCE
