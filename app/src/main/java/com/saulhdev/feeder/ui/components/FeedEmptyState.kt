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
import androidx.compose.material3.CircularProgressIndicator
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

/** Why the feed has nothing in it. The four cases are genuinely different. */
enum class FeedEmptyReason {
    /** No sources at all. Nothing has gone wrong; nothing has been added. */
    NoSources,

    /** Sources exist and a sync is running. Articles are on their way. */
    Syncing,

    /** Sources exist, a sync has finished, and nothing came back. */
    NothingFetched,

    /** There are articles, but the current filter or category excludes them. */
    FilteredOut,
}

/**
 * What an empty feed says for itself.
 *
 * It used to say nothing: no message, no spinner, no artwork — a header, the
 * glance row, the category chips, and then a void. That is indistinguishable
 * from a broken app, and it made an empty feed impossible to diagnose from a
 * screenshot, since every cause looks identical.
 *
 * The four reasons are separated because the right response differs completely.
 * Somebody with no sources needs to add one; somebody mid-sync needs to wait;
 * somebody whose sync returned nothing has a problem worth looking at; and
 * somebody who has filtered everything out has done it to themselves and needs
 * to know that rather than to be told their feed is empty.
 */
@Composable
fun FeedEmptyState(
    reason: FeedEmptyReason,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (reason == FeedEmptyReason.Syncing) {
            CircularProgressIndicator()
        } else {
            Image(
                painter = painterResource(
                    if (dark) R.drawable.empty_feed_dark else R.drawable.empty_feed_light
                ),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
            )
        }

        Text(
            text = stringResource(
                when (reason) {
                    FeedEmptyReason.NoSources -> R.string.empty_no_sources
                    FeedEmptyReason.Syncing -> R.string.empty_syncing
                    FeedEmptyReason.NothingFetched -> R.string.empty_nothing_fetched
                    FeedEmptyReason.FilteredOut -> R.string.empty_filtered
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(
                when (reason) {
                    FeedEmptyReason.NoSources -> R.string.empty_no_sources_hint
                    FeedEmptyReason.Syncing -> R.string.empty_syncing_hint
                    FeedEmptyReason.NothingFetched -> R.string.empty_nothing_fetched_hint
                    FeedEmptyReason.FilteredOut -> R.string.empty_filtered_hint
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
