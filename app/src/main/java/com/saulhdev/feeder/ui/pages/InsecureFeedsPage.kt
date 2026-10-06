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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Check
import com.saulhdev.feeder.ui.icons.phosphor.MagnifyingGlass
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.InsecureFeedsViewModel
import com.saulhdev.feeder.viewmodels.Upgrade

/**
 * Subscriptions still stored with an `http://` address, and the offer to move
 * them.
 *
 * Nothing here is fetched in the clear — an interceptor already rewrites the
 * scheme before any connection opens, and Android is told to refuse cleartext
 * besides. What this fixes is the address that is *stored*, which the
 * interceptor never touches: the sources list showing `http://`, an OPML
 * export handing that address to the next reader, and the fact that the
 * upgrade only holds for requests going through the clients that carry the
 * interceptor.
 *
 * Every address is checked before anything is written, so the page can say
 * which sites actually answer over https rather than assuming they all do.
 */
@Composable
fun InsecureFeedsPage(
    viewModel: InsecureFeedsViewModel = koinNeoViewModel<InsecureFeedsViewModel>(),
) {
    val insecure by viewModel.insecure.collectAsState()
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()

    val checked = insecure.count { state[it.id] != null && state[it.id] != Upgrade.Checking }
    val ready = insecure.count { state[it.id] is Upgrade.Works }

    ViewWithActionBar(
        title = stringResource(R.string.pref_insecure_feeds),
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
            item {
                Text(
                    text = stringResource(
                        if (insecure.isEmpty()) R.string.insecure_none
                        else R.string.insecure_explanation
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (insecure.isNotEmpty()) {
                item {
                    Column {
                        // Checking everything first, then applying what
                        // worked. Two steps rather than one button that does
                        // both, because the interesting outcome is the list of
                        // sites that have no https at all — and a single
                        // action would finish with that list unread.
                        ActionButton(
                            text = pluralStringResource(
                                R.plurals.insecure_check_all,
                                insecure.size,
                                insecure.size,
                            ),
                            icon = Phosphor.MagnifyingGlass,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.checkAll() },
                        )
                        if (ready > 0) {
                            Spacer(Modifier.height(10.dp))
                            ActionButton(
                                text = pluralStringResource(
                                    R.plurals.insecure_apply_all,
                                    ready,
                                    ready,
                                ),
                                icon = Phosphor.Check,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.applyAll() },
                            )
                        }
                        if (busy) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "  " + stringResource(
                                        R.string.insecure_checking_progress,
                                        checked,
                                        insecure.size,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            items(insecure, key = { it.id }) { feed ->
                InsecureRow(
                    feed = feed,
                    state = state[feed.id],
                    onCheck = { viewModel.check(feed.id) },
                    onApply = { viewModel.applyOne(feed.id) },
                )
            }
        }
    }
}

@Composable
private fun InsecureRow(
    feed: Feed,
    state: Upgrade?,
    onCheck: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = feed.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = feed.url.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            when (state) {
                Upgrade.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "  " + stringResource(R.string.insecure_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is Upgrade.Works -> Column {
                    Text(
                        text = state.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    ActionButton(
                        text = stringResource(R.string.insecure_apply),
                        icon = Phosphor.Check,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onApply,
                    )
                }

                Upgrade.Duplicate -> Text(
                    text = stringResource(R.string.insecure_duplicate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Upgrade.NoHttps -> Text(
                    text = stringResource(R.string.insecure_no_https),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                // Applied leaves the list on the next emission from the
                // sources flow, so this is only ever seen for an instant.
                Upgrade.Applied -> Text(
                    text = stringResource(R.string.insecure_applied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                null -> ActionButton(
                    text = stringResource(R.string.insecure_check),
                    icon = Phosphor.MagnifyingGlass,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCheck,
                )
            }
        }
    }
}
