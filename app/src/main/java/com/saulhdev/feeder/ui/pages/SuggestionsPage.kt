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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.saulhdev.feeder.manager.discovery.DiscoveryWorker
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FROM_LIBRARY
import com.saulhdev.feeder.data.db.models.Suggestion
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Plus
import com.saulhdev.feeder.ui.icons.phosphor.Prohibit
import com.saulhdev.feeder.viewmodels.SuggestionsViewModel
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import kotlinx.coroutines.launch

/**
 * Sites the reader keeps following links to.
 *
 * A screen they come to, never something injected into the feed. The moment
 * articles from sites somebody did not choose start appearing among the ones
 * they did, the feed stops being theirs — and that difference is the whole
 * argument for this app over the thing it replaces.
 *
 * Every row carries its evidence, because a suggestion that cannot say why it
 * is there is indistinguishable from an advert.
 */
@Composable
fun SuggestionsPage(
    viewModel: SuggestionsViewModel = koinNeoViewModel<SuggestionsViewModel>(),
) {
    val suggestions by viewModel.suggestions.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Straight from WorkManager rather than a flag set on the button. The pass
    // outlives this screen, so a flag here would go back to "not running" on
    // the way out and forwards on the way back in, which is a lie about work
    // that is still happening.
    val checking by DiscoveryWorker.runningNow(context)
        .collectAsState(initial = false)

    ViewWithActionBar(
        title = stringResource(R.string.pref_suggestions),
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
                        if (suggestions.isEmpty()) R.string.suggestions_none
                        else R.string.suggestions_explanation
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // The pass runs weekly, which is right for work nobody asked for
            // and useless the moment somebody is standing in front of the
            // screen wondering why it is empty. A week is a long time to wait
            // to find out whether a feature works.
            item {
                ActionButton(
                    text = stringResource(
                        if (checking) R.string.suggestions_checking
                        else R.string.suggestions_check_now
                    ),
                    icon = Phosphor.ArrowCounterClockwise,
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { DiscoveryWorker.runNow(context) },
                )
            }

            items(suggestions, key = { it.host }) { suggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    onAdd = { scope.launch { viewModel.accept(suggestion) } },
                    onDismiss = { scope.launch { viewModel.dismiss(suggestion) } },
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = suggestion.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // The evidence, in the row rather than behind it — and each
            // mechanism says its own. "Six articles you read linked to this"
            // is a fact about the reader; "also in United Kingdom, where two
            // of your sources are" is a fact about a file that ships with the
            // app. One wording for both would let the weaker claim borrow the
            // stronger one's authority.
            Text(
                text = if (suggestion.kind == FROM_LIBRARY) pluralStringResource(
                    R.plurals.suggestion_reason_library,
                    suggestion.mentions,
                    suggestion.context,
                    suggestion.mentions,
                ) else pluralStringResource(
                    R.plurals.suggestion_reason,
                    suggestion.mentions,
                    suggestion.mentions,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = stringResource(R.string.add_feed),
                    icon = Phosphor.Plus,
                    modifier = Modifier.weight(1f),
                    onClick = onAdd,
                )
                OutlinedActionButton(
                    text = stringResource(R.string.suggestion_dismiss),
                    icon = Phosphor.Prohibit,
                    positive = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
            }
        }
    }
}
