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
package com.saulhdev.feeder.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.EyeSlash
import com.saulhdev.feeder.ui.icons.phosphor.TrashSimple
import com.saulhdev.feeder.ui.overlay.SourceMark
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.LearnedSource
import com.saulhdev.feeder.viewmodels.LearnedViewModel
import kotlin.math.roundToInt

/**
 * Everything Whisper has worked out about the reader, on one screen.
 *
 * The reason this exists rather than a bare "reset" button: a preference
 * system nobody can see is one nobody can correct, and a reader who suspects
 * the feed has got the wrong idea has no way to check without it. Every number
 * here is a plain tally — presses of More and Less, articles opened — so it
 * can be compared against memory rather than taken on trust.
 *
 * It is also the reset control. Per source, so one wrong signal can be undone
 * without discarding everything; and for the lot, for a reader who would
 * rather start again.
 */
@Composable
fun LearnedPage(
    viewModel: LearnedViewModel = koinNeoViewModel(),
) {
    val sources by viewModel.sources.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    ViewWithActionBar(
        title = stringResource(R.string.pref_learned),
        largeTitle = true,
        showBackButton = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.learned_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            items(sources, key = { it.id }) { source ->
                LearnedRow(
                    source = source,
                    onForget = { viewModel.forget(source.id) },
                    onUnhide = { viewModel.unhide(source.id) },
                )
            }

            item {
                OutlinedActionButton(
                    text = stringResource(R.string.learned_reset_all),
                    icon = Phosphor.ArrowCounterClockwise,
                    positive = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { confirmReset = true }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.learned_reset_all)) },
            text = { Text(stringResource(R.string.learned_reset_all_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAll()
                    confirmReset = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LearnedRow(
    source: LearnedSource,
    onForget: () -> Unit,
    onUnhide: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.large),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        leadingContent = {
            SourceMark(iconUrl = source.iconUrl, sourceName = source.title, size = 28.dp)
        },
        headlineContent = { Text(source.title) },
        supportingContent = {
            Column {
                Text(
                    text = stringResource(
                        R.string.learned_detail,
                        source.affinity,
                        source.opened,
                        source.seen,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    // Rounded to two places and shown with its sign, because
                    // the sign is the part that matters: whether this source is
                    // being helped or held back.
                    text = stringResource(
                        R.string.learned_nudge,
                        (source.nudge * 100).roundToInt() / 100f,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source.hidden) {
                    IconButton(onClick = onUnhide) {
                        Icon(Phosphor.EyeSlash, stringResource(R.string.learned_unhide))
                    }
                }
                if (source.affinity != 0) {
                    IconButton(onClick = onForget) {
                        Icon(Phosphor.TrashSimple, stringResource(R.string.learned_forget))
                    }
                }
            }
        },
    )
}
