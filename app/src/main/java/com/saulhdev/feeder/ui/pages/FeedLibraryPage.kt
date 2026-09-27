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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.FeedLibrary
import com.saulhdev.feeder.data.LibraryFeed
import com.saulhdev.feeder.data.LibraryPack
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Plus
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * A directory of feeds to browse, for the reader who does not yet know what
 * they want to follow.
 *
 * Every other way into Whisper assumes an answer: paste an address, import a
 * file, scan your bookmarks, or wait a week for it to notice what your reading
 * links to. None of them help on the first day, and an empty reader is a
 * reader who stops opening the app.
 *
 * Nothing is added without being chosen. The pack is opened, the feeds are
 * listed with their addresses, everything starts ticked and anything can be
 * unticked — the same bargain the starter list makes, for the same reason: the
 * app claims no algorithm decides what you see, and subscribing somebody in
 * bulk on a single tap would make that untrue.
 */
@Composable
fun FeedLibraryPage() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val sources: SourcesRepository = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var packs by remember { mutableStateOf<List<LibraryPack>>(emptyList()) }
    var open by remember { mutableStateOf<LibraryPack?>(null) }
    var feeds by remember { mutableStateOf<List<LibraryFeed>>(emptyList()) }
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) { packs = FeedLibrary.packs(context) }

    // Everything ticked on opening: somebody who picked "Photography" wants
    // photography feeds, and making them tick eight boxes to say so again is
    // ceremony. Unticking is where the real choice lives.
    LaunchedEffect(open) {
        val pack = open
        if (pack == null) {
            feeds = emptyList(); chosen = emptySet()
        } else {
            feeds = FeedLibrary.feeds(context, pack)
            chosen = feeds.map { it.url }.toSet()
        }
    }

    ViewWithActionBar(
        title = open?.name ?: stringResource(R.string.pref_feed_library),
        largeTitle = true,
        showBackButton = true,
        onBackAction = if (open != null) ({ open = null }) else null,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val pack = open
            if (pack == null) {
                item {
                    Text(
                        text = stringResource(R.string.library_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                listOf("topic" to R.string.library_topics, "country" to R.string.library_countries)
                    .forEach { (kind, heading) ->
                        val group = packs.filter { it.kind == kind }
                        if (group.isEmpty()) return@forEach
                        item(key = kind) {
                            Text(
                                text = stringResource(heading),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(group, key = { it.slug }) { row ->
                            PackRow(row) { open = row }
                        }
                    }
            } else {
                item {
                    ActionButton(
                        text = pluralStringResource(
                            R.plurals.library_add, chosen.size, chosen.size
                        ),
                        icon = Phosphor.Plus,
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val picked = feeds.filter { it.url in chosen }
                            scope.launch {
                                val added = FeedLibrary.subscribe(sources, picked)
                                open = null
                                snackbarHostState.showSnackbar(
                                    resources.getQuantityString(
                                        R.plurals.library_added, added, added
                                    ),
                                    withDismissAction = true,
                                )
                            }
                        },
                    )
                }
                items(feeds, key = { it.url }) { feed ->
                    FeedRow(feed, feed.url in chosen) {
                        chosen = if (feed.url in chosen) chosen - feed.url else chosen + feed.url
                    }
                }
            }
        }
    }
}

@Composable
private fun PackRow(pack: LibraryPack, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = pack.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.library_count, pack.count, pack.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedRow(feed: LibraryFeed, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = feed.title, style = MaterialTheme.typography.titleSmall)
                // The address, because a title is a claim and an address is
                // checkable — and because two feeds called "News" are only
                // told apart by where they come from.
                Text(
                    text = feed.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (feed.category.isNotBlank() && feed.category != feed.title) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = feed.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}
