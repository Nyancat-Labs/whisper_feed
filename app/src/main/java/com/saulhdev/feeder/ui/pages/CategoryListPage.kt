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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.PaintRoller
import com.saulhdev.feeder.ui.icons.phosphor.TrashSimple
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.SourceListViewModel

/**
 * Categories, as things in their own right.
 *
 * Until now a category existed only as text on a source, so it could be
 * created by typing it and lost by mistyping it, and nothing could rename one
 * after the fact — a typo across forty imported sources was permanent.
 *
 * Merging is deliberately not a separate action: renaming a category onto a
 * name that already exists *is* the merge, and one operation with an obvious
 * outcome beats two that overlap. Deleting removes the category from every
 * source and keeps the sources, which is the only sane reading — the
 * alternative would be a screen where deleting a label silently unsubscribes
 * you from things.
 */
@Composable
fun CategoryListPage(
    viewModel: SourceListViewModel = koinNeoViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    ViewWithActionBar(
        title = stringResource(R.string.manage_categories),
        largeTitle = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.allTags, key = { it }) { tag ->
                val count = state.tagsSourcesMap[tag]?.size ?: 0
                ListItem(
                    modifier = Modifier.clip(MaterialTheme.shapes.large),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    headlineContent = { Text(tag) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.category_in_use, count, count))
                    },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row {
                            IconButton(onClick = { renaming = tag }) {
                                Icon(Phosphor.PaintRoller, stringResource(R.string.category_rename))
                            }
                            IconButton(onClick = { deleting = tag }) {
                                Icon(Phosphor.TrashSimple, stringResource(R.string.category_delete))
                            }
                        }
                    },
                )
            }
        }
    }

    renaming?.let { from ->
        var text by remember(from) { mutableStateOf(from) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.category_rename)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                    Text(
                        text = stringResource(R.string.category_merge_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() && text != from,
                    onClick = {
                        viewModel.renameTag(from, text.trim())
                        renaming = null
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.category_delete)) },
            text = { Text(stringResource(R.string.category_delete_desc, tag)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(tag)
                    deleting = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
