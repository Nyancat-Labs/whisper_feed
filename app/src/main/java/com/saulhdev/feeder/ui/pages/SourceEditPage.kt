/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team <saulhdev@hotmail.com>
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.FocusRequester.Companion.createRefs
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saulhdev.feeder.R
import org.koin.compose.koinInject
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.entity.SourceEditViewState
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.ComposeSwitchView
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.components.dialog.ActionsDialogUI
import com.saulhdev.feeder.ui.components.traceFocus
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Check
import com.saulhdev.feeder.ui.icons.phosphor.TrashSimple
import com.saulhdev.feeder.utils.extensions.interceptKey
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.SourceEditViewModel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.saulhdev.feeder.ui.icons.phosphor.Plus
import com.saulhdev.feeder.viewmodels.SourceListViewModel
import com.saulhdev.feeder.data.content.asState


@Composable
fun SourceEditPage(
    feedId: Long = -1,
    viewModel: SourceEditViewModel = koinNeoViewModel(),
    onDismiss: (() -> Unit),
) {
    val title = stringResource(id = R.string.edit_rss)
    val viewState by viewModel.viewState.collectAsState()
    // Every category already in use, so one can be picked rather than retyped.
    val sourcesViewModel: SourceListViewModel = koinNeoViewModel()
    val sourcesState by sourcesViewModel.state.collectAsState()
    val allTags = sourcesState.allTags
    var newTag by remember { mutableStateOf("") }
    val prefs: FeedPreferences = koinInject()
    val fullTextForAll by prefs.fullTextForAllFeeds.asState()
    // Initialise once per feed and do not overwrite user edits when viewState re-emits.
    val editState = remember(feedId) {
        mutableStateOf(viewState)
    }
    var hasLoaded by remember { mutableStateOf(false) }
    var hasEdited by remember { mutableStateOf(false) }
    val showDialog = remember { mutableStateOf(false) }

    LaunchedEffect(feedId) {
        viewModel.setFeedId(feedId)
        if (!hasEdited && feedId != -1L) {
            val freshFeed = viewModel.loadFeed(feedId)
            if (freshFeed != null) {
                editState.value = SourceEditViewState(
                    title = freshFeed.title,
                    url = freshFeed.url.toString(),
                    tag = freshFeed.tag,
                    fullTextByDefault = freshFeed.fullTextByDefault,
                    isEnabled = freshFeed.isEnabled,
                )
                hasLoaded = true
            }
        }
    }

    LaunchedEffect(viewState) {
        if (!hasLoaded && !hasEdited && viewState.url.isNotBlank()) {
            editState.value = viewState
            hasLoaded = true
        }
    }

    ViewWithActionBar(
        title = title,
        showBackButton = true,
        onBackAction = onDismiss,
        bottomBar = {
            // The buttons used to sit flush against the bottom of the display,
            // under the gesture bar, separated from the form by a two-pixel
            // rule — so the most consequential control on the screen was the
            // one hardest to hit and closest to the edge.
            //
            // A surface of its own with room around it, clear of the system
            // bars and lifted by the keyboard, which is what Material's bottom
            // button group is and what every picker on the phone already does.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedActionButton(
                        text = stringResource(id = R.string.action_delete),
                        icon = Phosphor.TrashSimple,
                        positive = false,
                    ) {
                        showDialog.value = true
                    }
                    ActionButton(
                        text = stringResource(R.string.action_save),
                        icon = Phosphor.Check,
                        modifier = Modifier.weight(1f),
                        positive = true,
                    ) {
                        // A category typed but not committed with the + button
                        // is still a category the reader asked for. Losing it
                        // because they pressed Save instead of Done is the
                        // kind of thing nobody reports and everybody notices.
                        val pending = newTag.trim()
                        val toSave =
                            if (pending.isEmpty()) editState.value
                            else {
                                val tags = editState.value.tag
                                    .split(",").map(String::trim).filter(String::isNotEmpty)
                                    .toSet() + pending
                                editState.value.copy(tag = tags.joinToString(","))
                            }
                        viewModel.updateFeed(toSave)
                        onDismiss()
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding(),
                start = 8.dp,
                end = 8.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SourceEditView(
                editState = editState,
                onEdited = { hasEdited = true },
                allTags = allTags,
                newTag = newTag,
                onNewTagChange = { newTag = it },
                fullTextForAll = fullTextForAll,
            )
        }
    }

    if (showDialog.value) {
        Dialog(
            onDismissRequest = { showDialog.value = false },
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            ActionsDialogUI(
                titleText = stringResource(id = R.string.remove_title),
                messageText = stringResource(
                    id = R.string.remove_desc,
                    viewState.title,
                ),
                openDialogCustom = showDialog,
                primaryText = stringResource(id = android.R.string.ok),
                primaryAction = {
                    onDismiss()
                    viewModel.deleteFeed(feedId)
                }
            )
        }
    }
}

@Composable
fun SourceEditView(
    editState: MutableState<SourceEditViewState>,
    onEdited: () -> Unit = {},
    allTags: List<String> = emptyList(),
    newTag: String = "",
    onNewTagChange: (String) -> Unit = {},
    fullTextForAll: Boolean = false,
) {
    val (focusTitle, focusTag) = createRefs()
    val focusManager = LocalFocusManager.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedTextField(
                value = editState.value.url,
                onValueChange = {
                    editState.value = editState.value.copy(url = it)
                    onEdited()
                },
                label = {
                    Text(stringResource(id = R.string.add_input_hint))
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusTitle.requestFocus()
                    }
                ),
                shape = MaterialTheme.shapes.large,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .traceFocus("url")
                    .interceptKey(Key.Enter) {
                        focusTitle.requestFocus()
                    }
                    .interceptKey(Key.Escape) {
                        focusManager.clearFocus()
                    },
            )
        }
        item {
            OutlinedTextField(
                value = editState.value.title,
                onValueChange = {
                    editState.value = editState.value.copy(title = it)
                    onEdited()
                },
                label = {
                    Text(stringResource(id = R.string.title))
                },
                shape = MaterialTheme.shapes.large,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusTag.requestFocus()
                    }
                ),
                modifier = Modifier
                    .focusRequester(focusTitle)
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .traceFocus("title")
                    .interceptKey(Key.Enter) {
                        focusTag.requestFocus()
                    }
                    .interceptKey(Key.Escape) {
                        focusManager.clearFocus()
                    },
            )
        }
        item {
            // Above the categories, not below them: the chip cloud grows with
            // every category the user has, and these two switches were being
            // pushed off the bottom of the screen by it.
            ComposeSwitchView(
                titleId = R.string.fetch_full_articles_by_default,
                // When the global switch is on this one cannot change anything,
                // so it says why rather than sitting there looking broken.
                summaryId = if (fullTextForAll) R.string.fetch_full_articles_all_feeds_on
                else R.string.fetch_full_articles_summary,
                isChecked = fullTextForAll || editState.value.fullTextByDefault,
                isEnabled = !fullTextForAll,
                onCheckedChange = {
                    editState.value = editState.value.copy(fullTextByDefault = it)
                    onEdited()
                },
                index = 0,
                groupSize = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            ComposeSwitchView(
                titleId = R.string.source_enabled,
                summaryId = R.string.source_enabled_summary,
                isChecked = editState.value.isEnabled,
                onCheckedChange = {
                    editState.value = editState.value.copy(isEnabled = it)
                    onEdited()
                },
                index = 1,
                groupSize = 2
            )
        }

        item {
            // Categories are picked, not typed. The field here was free text,
            // so a category was created by spelling it right and lost by
            // spelling it wrong — and it read as one value even though the
            // column has always held a comma-separated list, which is what
            // several bugs came from. Typing is still how a *new* one is made.
            val selected = editState.value.tag
                .split(",").map(String::trim).filter(String::isNotEmpty).toSet()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.source_tags),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (allTags + selected).distinct().sorted().forEach { tag ->
                        FilterChip(
                            selected = tag in selected,
                            onClick = {
                                val next = if (tag in selected) selected - tag
                                else selected + tag
                                editState.value =
                                    editState.value.copy(tag = next.joinToString(","))
                                onEdited()
                            },
                            label = { Text(tag) },
                        )
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = onNewTagChange,
                    label = { Text(stringResource(id = R.string.sources_add_tag)) },
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    trailingIcon = {
                        if (newTag.isNotBlank()) {
                            IconButton(onClick = {
                                val next = selected + newTag.trim()
                                editState.value =
                                    editState.value.copy(tag = next.joinToString(","))
                                onNewTagChange("")
                                onEdited()
                            }) { Icon(Phosphor.Plus, null) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newTag.isNotBlank()) {
                                val next = selected + newTag.trim()
                                editState.value =
                                    editState.value.copy(tag = next.joinToString(","))
                                onNewTagChange("")
                                onEdited()
                            }
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .traceFocus("add category"),
                )
            }
        }
    }
}

@Composable
@Preview
fun SourceEditPagePreview() {
    val state = remember {
        mutableStateOf(
            SourceEditViewState(
                url = "https://example.com/feed",
                title = "Example Feed",
                fullTextByDefault = true,
                isEnabled = true
            )
        )
    }

    SourceEditView(editState = state)
}