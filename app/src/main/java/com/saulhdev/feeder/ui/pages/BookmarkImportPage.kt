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

import androidx.compose.ui.platform.LocalResources
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.bookmarks.BookmarkFolder
import com.saulhdev.feeder.manager.bookmarks.DiscoveredFeed
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowDown
import com.saulhdev.feeder.ui.icons.phosphor.Plus
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.BookmarkImportViewModel
import com.saulhdev.feeder.viewmodels.ImportStage
import kotlinx.coroutines.launch

/**
 * Bookmarks in, feeds out.
 *
 * The reader gives Whisper the websites and Whisper finds the feeds — nobody
 * should have to search for "BBC RSS feed".
 *
 * **Folders, not the whole file**, and that is the design rather than an
 * option. A bookmark collection is a junk drawer: shopping, a router's admin
 * page, forty half-read documentation tabs, most of it publishing nothing. A
 * folder somebody named is the opposite — curated by hand, over years, because
 * they read those sites.
 *
 * Nothing is uploaded anywhere. The file is read on the phone and the phone
 * does the fetching, which is a stronger answer than a privacy policy.
 */
@Composable
fun BookmarkImportPage(
    viewModel: BookmarkImportViewModel = koinNeoViewModel<BookmarkImportViewModel>(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val stage by viewModel.stage.collectAsState()
    val chosenFolders by viewModel.selectedFolders.collectAsState()
    val chosenFeeds by viewModel.selectedFeeds.collectAsState()

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.load(context, it) } }

    ViewWithActionBar(
        title = stringResource(R.string.pref_import_bookmarks),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val current = stage) {
                ImportStage.PickFile, ImportStage.Empty -> {
                    item {
                        Text(
                            text = stringResource(
                                if (current == ImportStage.Empty) R.string.import_nothing_found
                                else R.string.import_explanation
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    item {
                        ActionButton(
                            text = stringResource(R.string.import_choose_file),
                            icon = Phosphor.CloudArrowDown,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                pickFile.launch(arrayOf("text/html", "text/plain", "*/*"))
                            },
                        )
                    }
                }

                is ImportStage.ChooseFolders -> {
                    item {
                        Text(
                            text = stringResource(R.string.import_choose_folders),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(current.folders, key = { it.name }) { folder ->
                        FolderRow(
                            folder = folder,
                            checked = folder.name in chosenFolders,
                            onToggle = { viewModel.toggleFolder(folder.name) },
                        )
                    }
                    item {
                        ActionButton(
                            text = stringResource(R.string.import_scan),
                            icon = Phosphor.CloudArrowDown,
                            enabled = chosenFolders.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            onClick = { viewModel.scan() },
                        )
                    }
                }

                is ImportStage.Scanning -> {
                    item {
                        Column(modifier = Modifier.padding(vertical = 32.dp)) {
                            Text(
                                text = if (current.total == 0) {
                                    stringResource(R.string.import_preparing)
                                } else {
                                    stringResource(
                                        R.string.import_scanning,
                                        current.done,
                                        current.total,
                                    )
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            if (current.total > 0) {
                                LinearProgressIndicator(
                                    progress = { current.done.toFloat() / current.total },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                is ImportStage.Review -> {
                    item {
                        Text(
                            text = if (current.found.isEmpty()) {
                                stringResource(R.string.import_no_feeds)
                            } else {
                                pluralStringResource(
                                    R.plurals.import_found,
                                    current.found.size,
                                    current.found.size,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(current.found, key = { it.feedUrl }) { feed ->
                        FeedRow(
                            feed = feed,
                            category = current.category[feed.feedUrl].orEmpty(),
                            checked = feed.feedUrl in chosenFeeds,
                            onToggle = { viewModel.toggleFeed(feed.feedUrl) },
                        )
                    }
                    if (current.found.isNotEmpty()) {
                        item {
                            ActionButton(
                                text = stringResource(R.string.import_add, chosenFeeds.size),
                                icon = Phosphor.Plus,
                                enabled = chosenFeeds.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                onClick = {
                                    scope.launch {
                                        val added = viewModel.addSelected()
                                        Toast.makeText(
                                            context,
                                            resources.getQuantityString(R.plurals.starter_added, added, added),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        navController.popBackStack()
                                    }
                                },
                            )
                        }
                    }
                    item {
                        OutlinedActionButton(
                            text = stringResource(R.string.import_start_over),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.restart() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: BookmarkFolder,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (checked) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
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
                    text = folder.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Sites rather than bookmarks: fifteen bookmarks on one
                // publication are one site to look at, and a bookmark count
                // would promise fifteen times the work.
                Text(
                    text = pluralStringResource(
                        R.plurals.import_folder_sites,
                        folder.domains,
                        folder.domains,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeedRow(
    feed: DiscoveredFeed,
    category: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (checked) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
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
                    text = feed.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = feed.site,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Said, or guessed. Two states carry the whole distinction
                // that matters and the reader can act on both.
                Text(
                    text = stringResource(
                        if (feed.declared) R.string.import_declared else R.string.import_guessed
                    ) + if (category.isNotBlank()) " · $category" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (feed.declared) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
