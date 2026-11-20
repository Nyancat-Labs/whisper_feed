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

import com.saulhdev.feeder.ui.components.WhisperLoading
import com.saulhdev.feeder.ui.components.INLINE_LOADING

import com.saulhdev.feeder.ui.icons.phosphor.PencilSimple
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.manager.sync.greader.isHomeNetworkHost
import androidx.compose.ui.platform.LocalContext
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.ui.icons.phosphor.Browser
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.saulhdev.feeder.utils.FeedHistory
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.Check
import com.saulhdev.feeder.ui.icons.phosphor.TrashSimple
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.BrokenFeedsViewModel
import com.saulhdev.feeder.viewmodels.Recovery
import kotlinx.coroutines.launch

/**
 * Feeds that have stopped working, and the offer to fix them.
 *
 * A feed's address changes — a site moves to a new CMS, drops `/rss` for
 * `/feed`, changes host — and every reader in the world treats that as the
 * feed having died. The site is usually still there, still publishing, still
 * advertising its new address in its own head. Nobody looks, so nobody finds
 * it, and the reader eventually notices a silence and unsubscribes from
 * something that was working all along.
 *
 * Looking is one request against machinery that already exists for the
 * bookmark import. This is the cheapest genuinely useful thing in the app.
 */
@Composable
fun BrokenFeedsPage(
    viewModel: BrokenFeedsViewModel = koinNeoViewModel<BrokenFeedsViewModel>(),
) {
    val broken by viewModel.broken.collectAsState()
    val recovery by viewModel.recovery.collectAsState()
    val scope = rememberCoroutineScope()

    ViewWithActionBar(
        title = stringResource(R.string.pref_broken_feeds),
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
                        if (broken.isEmpty()) R.string.broken_none
                        else R.string.broken_explanation
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(broken, key = { it.id }) { feed ->
                BrokenRow(
                    feed = feed,
                    state = recovery[feed.id],
                    onLook = { viewModel.look(feed) },
                    onUse = { url -> scope.launch { viewModel.useNewAddress(feed, url) } },
                    onForget = { scope.launch { viewModel.forget(feed) } },
                    onDelete = { scope.launch { viewModel.delete(feed) } },
                )
            }
        }
    }
}

@Composable
private fun BrokenRow(
    feed: Feed,
    state: Recovery?,
    onLook: () -> Unit,
    onUse: (String) -> Unit,
    onForget: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
            Spacer(Modifier.height(6.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.broken_failures,
                    feed.consecutiveFailures,
                    feed.consecutiveFailures,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            // A feed at home fails because Android blocks it; say so rather
            // than leave the reader to look for a fault (sheet 73).
            if (isHomeNetworkHost(feed.url.host)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.broken_home_network),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // A site that turns apps away (sheet 117). Looking for a new
            // address would be turned away too and end in "may have stopped
            // publishing", which is wrong: the feed is there. Say what is
            // happening, and give the reader the way to see it themselves.
            val context = LocalContext.current
            val history = remember(feed.id, feed.consecutiveFailures) { FeedHistory.read(context, feed.id) }
            val refused = FeedHistory.refusesApps(history)
            // Answers, but with nothing in it (Quartz, 20 November): "the
            // server may be down, or blocking" was wrong for that.
            val empty = FeedHistory.cameBackEmpty(history)
            if (empty) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.broken_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (refused) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.blocks_apps_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.blocks_apps_check),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    text = stringResource(R.string.open_feed_in_browser),
                    icon = Phosphor.Browser,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { context.launchView(feed.url.toString()) },
                )
            }

            Spacer(Modifier.height(12.dp))

            if (!refused) when (state) {
                Recovery.Looking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    WhisperLoading(size = INLINE_LOADING)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "  " + stringResource(R.string.broken_looking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is Recovery.Found -> Column {
                    Text(
                        text = stringResource(R.string.broken_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = state.feedUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ActionButton(
                        text = stringResource(R.string.broken_use_new),
                        icon = Phosphor.Check,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onUse(state.feedUrl) },
                    )
                }

                Recovery.Unchanged -> Text(
                    text = stringResource(if (empty) R.string.broken_empty_unchanged else R.string.broken_unchanged),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Recovery.AlreadySubscribed -> Text(
                    text = stringResource(R.string.broken_already_subscribed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Recovery.NothingFound -> Column {
                    Text(
                        text = stringResource(R.string.broken_nothing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedActionButton(
                        text = stringResource(R.string.broken_forget),
                        positive = false,
                        destructive = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onForget,
                    )
                }

                null -> ActionButton(
                    text = stringResource(R.string.broken_look),
                    icon = Phosphor.ArrowCounterClockwise,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onLook,
                )
            }

            // On every row, not only on the ones that came back with nothing.
            // A feed can be gone for reasons looking will not settle — the
            // site is dead, the replacement is already subscribed to, or the
            // reader has simply stopped wanting it — and until now the only
            // way out was to leave this screen and hunt for the source in the
            // main list.
            // The site itself, to see whether it is still there before
            // deciding (owner, 14 November, sheet 107). Its home page, not
            // the feed's address, which would only show code.
            if (!refused) Spacer(Modifier.height(10.dp))
            OutlinedActionButton(
                text = stringResource(R.string.broken_open_website),
                icon = Phosphor.Browser,
                modifier = Modifier.fillMaxWidth(),
                onClick = { context.launchView(siteHome(feed.url)) },
            )
            // Having seen the site, change the address here rather than hunt
            // for the source in the list (owner, 19 November; sheet 116).
            Spacer(Modifier.height(10.dp))
            val navController = LocalNavController.current
            OutlinedActionButton(
                text = stringResource(R.string.broken_edit),
                icon = Phosphor.PencilSimple,
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(NavRoute.SourceEdit(feed.id)) },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedActionButton(
                text = stringResource(R.string.broken_delete),
                icon = Phosphor.TrashSimple,
                positive = false,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDelete,
            )
        }
    }
}

/** "https://example.com/feed.xml" → "https://example.com/". */
internal fun siteHome(feedUrl: java.net.URL): String =
    feedUrl.protocol + "://" + feedUrl.host + (if (feedUrl.port != -1) ":" + feedUrl.port else "") + "/"
