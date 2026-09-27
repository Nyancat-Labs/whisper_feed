/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team <saulhdev@hotmail.com>
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

import androidx.compose.runtime.key
import android.os.SystemClock
import com.saulhdev.feeder.utils.CategoryOrder
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.platform.LocalResources
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.models.exportBookmarks
import com.saulhdev.feeder.manager.models.exportOpml
import com.saulhdev.feeder.manager.models.importBookmarks
import com.saulhdev.feeder.manager.models.importOpml
import com.saulhdev.feeder.ui.components.OverflowMenu
import com.saulhdev.feeder.ui.components.PreferenceGroupHeading
import com.saulhdev.feeder.ui.components.SourceItem
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.components.traceFocus
import com.saulhdev.feeder.ui.components.focusOnlyWhenTapped
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.BookBookmark
import com.saulhdev.feeder.ui.icons.phosphor.Bookmarks
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowDown
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowUp
import com.saulhdev.feeder.ui.icons.phosphor.Hash
import com.saulhdev.feeder.ui.icons.phosphor.Plus
import com.saulhdev.feeder.ui.icons.phosphor.SortAscending
import com.saulhdev.feeder.ui.icons.phosphor.SortDescending
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.utils.ApplicationCoroutineScope
import com.saulhdev.feeder.utils.FILE_DATETIME_FORMAT
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.MAX_PINNED_SOURCES
import com.saulhdev.feeder.viewmodels.SourceEditViewModel
import com.saulhdev.feeder.viewmodels.SourceListViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import okhttp3.internal.toLongOrDefault
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Clock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.ui.icons.phosphor.Check
import com.saulhdev.feeder.ui.icons.phosphor.Sort
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.BracketsSquare
import com.saulhdev.feeder.ui.icons.phosphor.DotsThreeVertical
import com.saulhdev.feeder.ui.icons.phosphor.Filtered
import com.saulhdev.feeder.ui.icons.phosphor.Graph
import com.saulhdev.feeder.ui.icons.phosphor.ListDashes
import com.saulhdev.feeder.ui.icons.phosphor.Power
import com.saulhdev.feeder.ui.icons.phosphor.Prohibit
import com.saulhdev.feeder.ui.icons.phosphor.TrashSimple
import com.saulhdev.feeder.ui.icons.phosphor.ArrowLeft
import com.saulhdev.feeder.ui.icons.phosphor.X
import com.saulhdev.feeder.viewmodels.SourceSort

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SourceListPage(
    viewModel: SourceListViewModel = koinNeoViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val localTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        .format(FILE_DATETIME_FORMAT)
    // TODO reconsider
    val coroutineScope: ApplicationCoroutineScope by inject(ApplicationCoroutineScope::class.java)
    val state by viewModel.state.collectAsState()
    val paneNavigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val sourceId = remember { mutableLongStateOf(-1L) }

    // Removing a source is confirmed on the editor screen, which then closes —
    // so the offer to undo has to be made here, on the screen the user lands
    // back on. The repository holds the removed row until this is answered.
    val snackbarHostState = remember { SnackbarHostState() }

    // The editor dismisses before its write finishes, so a refused save has
    // nobody left to tell. This screen is still here, and has the snackbar.
    val editViewModel: SourceEditViewModel = koinNeoViewModel()
    LaunchedEffect(Unit) {
        editViewModel.saveFailed.collect { name ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.source_address_taken, name),
                withDismissAction = true,
            )
        }
    }

    // Removing a subscription leaves its id in the pinned set, which does
    // nothing visible until the reader has five of those and cannot pinned
    // anything at all. Cleared whenever the list is known, because that is the
    // only place the answer is.
    LaunchedEffect(state.allSources) {
        if (state.allSources.isNotEmpty()) {
            viewModel.forgetMissingPins(state.allSources.mapTo(HashSet()) { it.id })
        }
    }
    val recentlyDeleted by viewModel.recentlyDeleted.collectAsState()
    val recentlyDeletedMany by viewModel.recentlyDeletedMany.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val query by viewModel.query.collectAsState()
    val category by viewModel.category.collectAsState()
    val duplicatesOnly by viewModel.duplicatesOnly.collectAsState()
    val sameSite by viewModel.sameSite.collectAsState()
    val sameSiteGroupCount by viewModel.sameSiteGroupCount.collectAsState()
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val duplicateCount by viewModel.duplicateCount.collectAsState()
    val articlesCleared by viewModel.articlesCleared.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val ascending by viewModel.ascending.collectAsState()
    var tagAction by remember { mutableStateOf<TagAction?>(null) }

    // Both bulk lists come from the same ordering the screen draws, so
    // "select all" and "everything between these two" mean what they look
    // like they mean.
    val shown = state.shownSources
    // Remembered against the list rather than rebuilt each frame: this screen
    // has form for recomposing on every write a sync makes, and both bulk
    // gestures want the ids rather than the rows.
    val shownIds = remember(shown) { shown.map(Feed::id) }

    // "Not updating" needs a fixed instant to compare against. Computed once
    // per composition of the screen rather than inside each row, where each
    // row would capture its own slightly different "now".
    val staleSince = remember {
        Clock.System.now().toEpochMilliseconds() - STALE_AFTER_MS
    }

    // Leaving the screen with a selection still live would mean coming back to
    // a bulk action armed against a list that may have changed underneath it.
    DisposableEffect(Unit) { onDispose { viewModel.clearSelection() } }

    // Back dismisses the selection before it dismisses the screen, which is
    // the same promise the close cross in the app bar makes. Without this the
    // two gestures disagree: the cross clears, back leaves.
    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(recentlyDeleted) {
        val feed = recentlyDeleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.source_removed, feed.title),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        else viewModel.forgetDeleted()
    }
    LaunchedEffect(recentlyDeletedMany) {
        if (recentlyDeletedMany.isEmpty()) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = resources.getQuantityString(
                R.plurals.sources_deleted,
                recentlyDeletedMany.size,
                recentlyDeletedMany.size,
            ),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteSources()
        else viewModel.forgetDeletedSources()
    }

    LaunchedEffect(articlesCleared) {
        val count = articlesCleared ?: return@LaunchedEffect
        // Said with a number because the query keeps bookmarked and pinned
        // articles: "done" would leave someone counting rows to find out
        // whether their saved items had just gone.
        snackbarHostState.showSnackbar(
            message = resources.getQuantityString(R.plurals.sources_articles_cleared, count, count),
            withDismissAction = true,
        )
        viewModel.forgetArticlesCleared()
    }

    LaunchedEffect(duplicatesOnly, sameSite, shown.size) {
        if (!duplicatesOnly || shown.isNotEmpty()) return@LaunchedEffect
        // Which filter came back empty, because "no duplicates" and "nothing
        // looks like a repeat" are different pieces of news.
        val empty = if (sameSite) R.string.sources_same_site_none
        else R.string.sources_duplicates_none
        // Disarmed before the message rather than after it, so the list comes
        // straight back: a filter that leaves an empty screen for as long as a
        // snackbar lasts reads as a screen that has broken.
        viewModel.setDuplicatesOnly(false)
        // In the screen's scope rather than this effect's, and that is the
        // whole of the fix. `duplicatesOnly` is a key of this effect, so the
        // line above cancels the coroutine it is running in — and took the
        // snackbar down with it, every time, before it could be shown.
        //
        // So the filter armed, found nothing, disarmed itself and returned the
        // list exactly as it was, with no message at all. From the outside
        // that is a menu entry that does nothing, which is what it was
        // reported as: the one explanation of why nothing changed was
        // destroyed by the line that made nothing change.
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(empty),
                withDismissAction = true,
            )
        }
    }

    val opmlExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/opml")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                context.contentResolver.exportOpml(
                    uri,
                    state.tagsSourcesMap
                )
            }
        }
    }

    val opmlImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                context.contentResolver.importOpml(uri)
            }
        }
    }

    val bookmarksExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/opml")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                // Fetched now rather than kept in the screen's state: it is
                // a join across two tables that a sync invalidates constantly,
                // and nothing looks at it until this moment.
                context.contentResolver.exportBookmarks(
                    context,
                    uri,
                    viewModel.bookmarksForExport()
                )
            }
        }
    }

    val bookmarksImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                context.contentResolver.importBookmarks(
                    context,
                    uri,
                )
            }
        }
    }

    tagAction?.let { action ->
        TagPickerDialog(
            action = action,
            allTags = state.allTags,
            onDismiss = { tagAction = null },
            onConfirm = { tags ->
                when (action) {
                    TagAction.Add -> viewModel.editSelectedTags(add = tags)
                    TagAction.Remove -> viewModel.editSelectedTags(remove = tags)
                    TagAction.Replace -> viewModel.editSelectedTags(replaceWith = tags)
                }
                tagAction = null
            },
        )
    }

    NavigableListDetailPaneScaffold(
        navigator = paneNavigator,
        listPane = {
            AnimatedPane {
                val selecting = selection.isNotEmpty()
                // The search field lets go of focus whenever the list is left:
                // for a source, a child screen, or another app. Kept, it was
                // handed back on the way in and brought the keyboard up over
                // a list the reader had come back to look at.
                val focusManager = LocalFocusManager.current
                LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { focusManager.clearFocus() }
                LaunchedEffect(sourceId.longValue) {
                    if (sourceId.longValue != -1L) focusManager.clearFocus()
                }
                DisposableEffect(Unit) { onDispose { focusManager.clearFocus() } }
                ViewWithActionBar(
                    // The app bar becomes the selection's own bar while one is
                    // running: count on the left, close where the back arrow
                    // was, actions on the right. This used to be a card in the
                    // list with the actions in a horizontally scrolling row,
                    // which put Delete sixth — off the right edge of a phone,
                    // with nothing to say the row scrolled at all. A
                    // destructive action that has to be discovered by swiping
                    // is one that will be found by accident.
                    title = if (selecting) {
                        pluralStringResource(R.plurals.sources_selected, selection.size, selection.size)
                    } else {
                        stringResource(id = R.string.title_sources)
                    },
                    largeTitle = true,
                    // Always a way back. The arrow showed only during a
                    // selection, from when this was a tab of its own; reached
                    // from Settings, the screen had no way out but a swipe.
                    showBackButton = true,
                    backIcon = if (selecting) Phosphor.X else Phosphor.ArrowLeft,
                    backDescription = if (selecting) R.string.sources_clear_selection else R.string.go_back,
                    onBackAction = if (selecting) viewModel::clearSelection else null,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    floatingActionButton = {
                        // Adding a source in the middle of picking sources to
                        // act on is not a thing anyone is doing, and the FAB
                        // sits over the last row of the list.
                        if (!selecting) {
                            // A plain FAB, not an extended one. The extended
                            // variant is for a button carrying a label, and
                            // this one never had text — so it drew as a wide
                            // pill with a single + adrift in the middle of it,
                            // which is why it read as something other than a
                            // button to add a source.
                            FloatingActionButton(
                                onClick = {
                                    navController.navigate(NavRoute.SourceAdd)
                                },
                                modifier = Modifier.padding(16.dp),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Icon(
                                    imageVector = Phosphor.Plus,
                                    contentDescription = stringResource(id = R.string.add_feed),
                                )
                            }
                        }
                    },
                    actions = {
                        if (selecting) SelectionActions(
                            onTag = { tagAction = it },
                            onDelete = viewModel::deleteSelected,
                            onSelectAll = { viewModel.selectAll(shownIds) },
                            onEnable = { viewModel.setSelectedEnabled(true) },
                            onDisable = { viewModel.setSelectedEnabled(false) },
                            onFullText = viewModel::setSelectedFullText,
                            onClearArticles = viewModel::clearSelectedArticles,
                        ) else OverflowMenu {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.Hash,
                                        contentDescription = stringResource(R.string.manage_categories),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    navController.navigate(NavRoute.Categories)
                                },
                                text = { Text(stringResource(R.string.manage_categories)) },
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.Filtered,
                                        contentDescription = stringResource(R.string.sources_find_duplicates),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    viewModel.setDuplicatesOnly(true)
                                },
                                text = { Text(stringResource(R.string.sources_find_duplicates)) },
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.Graph,
                                        contentDescription = stringResource(
                                            R.string.sources_find_same_site
                                        ),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    viewModel.setSameSiteOnly()
                                },
                                text = { Text(stringResource(R.string.sources_find_same_site)) },
                            )
                            // The two repair tools, moved off the settings
                            // screen. They are maintenance on this list
                            // rather than preferences about it, and the
                            // moment anybody wants either is while looking at
                            // the list — which is here, not two screens away
                            // under a heading about sources in general.
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.ArrowCounterClockwise,
                                        contentDescription = stringResource(
                                            R.string.pref_broken_feeds
                                        ),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    navController.navigate(NavRoute.BrokenFeeds)
                                },
                                text = { Text(stringResource(R.string.pref_broken_feeds)) },
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.Prohibit,
                                        contentDescription = stringResource(
                                            R.string.pref_insecure_feeds
                                        ),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    navController.navigate(NavRoute.InsecureFeeds)
                                },
                                text = { Text(stringResource(R.string.pref_insecure_feeds)) },
                            )
                            HorizontalDivider()
                            // Only the chosen row carries an arrow, and the
                            // arrow is the direction rather than a tick. Every
                            // row used to show a sort glyph whether or not it
                            // was in use, which made four identical-looking
                            // icons and told the reader nothing about which
                            // order they were actually in.
                            SourceSort.entries.forEach { option ->
                                val chosen = option == sort
                                DropdownMenuItem(
                                    leadingIcon = {
                                        if (chosen) {
                                            Icon(
                                                imageVector = if (ascending) {
                                                    Phosphor.SortAscending
                                                } else {
                                                    Phosphor.SortDescending
                                                },
                                                contentDescription = stringResource(
                                                    if (ascending) R.string.sort_ascending
                                                    else R.string.sort_descending
                                                ),
                                            )
                                        }
                                    },
                                    // Left open on the chosen row: tapping it
                                    // reverses the order, and closing the menu
                                    // would hide the arrow that just changed —
                                    // the only feedback the action has.
                                    onClick = {
                                        if (!chosen) hideMenu()
                                        viewModel.setSort(option)
                                    },
                                    text = {
                                        Text(
                                            text = stringResource(option.labelId),
                                            color = if (chosen) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.CloudArrowDown,
                                        contentDescription = stringResource(id = R.string.sources_import_opml),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    opmlImporter.launch(
                                        arrayOf(
                                            "text/plain",
                                            "text/xml",
                                            "text/opml",
                                            "*/*"
                                        )
                                    )
                                },
                                text = { Text(text = stringResource(id = R.string.sources_import_opml)) }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.CloudArrowUp,
                                        contentDescription = stringResource(id = R.string.sources_export_opml),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    opmlExporter.launch("whisper-${localTime}.opml")
                                },
                                text = { Text(text = stringResource(id = R.string.sources_export_opml)) }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.Bookmarks,
                                        contentDescription = stringResource(id = R.string.sources_import_bookmarks),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    bookmarksImporter.launch(
                                        arrayOf(
                                            "text/plain",
                                            "text/xml",
                                            "text/bkm",
                                            "*/*"
                                        )
                                    )
                                },
                                text = { Text(text = stringResource(id = R.string.sources_import_bookmarks)) }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.BookBookmark,
                                        contentDescription = stringResource(id = R.string.sources_export_bookmarks),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    bookmarksExporter.launch("NF-${localTime}.bkm")
                                },
                                text = { Text(text = stringResource(id = R.string.sources_export_bookmarks)) }
                            )
                        }
                    }
                ) { paddingValues ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = paddingValues.calculateTopPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            // The search field stays put while a selection is
                            // running. It used to be replaced by the selection
                            // bar, which hid a filter that was still in force
                            // — so Select all appeared to take what was on
                            // screen while actually taking the whole database.
                            OutlinedTextField(
                                value = query,
                                onValueChange = viewModel::setQuery,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusOnlyWhenTapped()
                                    .traceFocus("sources search"),
                                shape = MaterialTheme.shapes.large,
                                label = { Text(stringResource(R.string.sources_search)) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setQuery("") }) {
                                            // An X. This was SubtractSquare, a
                                            // minus in a box, which at icon
                                            // size reads as two stacked
                                            // squares — so the control for
                                            // emptying the field looked like a
                                            // copy button, and nothing about
                                            // it suggested clearing anything.
                                            Icon(
                                                Phosphor.X,
                                                contentDescription = stringResource(
                                                    R.string.sources_clear_search
                                                ),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        if (!duplicatesOnly && duplicateCount > 0) {
                            item(key = "duplicates-found") {
                                DuplicatesFound(
                                    count = duplicateCount,
                                    onReview = { viewModel.setDuplicatesOnly(true) },
                                )
                            }
                        }
                        if (duplicatesOnly) {
                            item {
                                DuplicatesBanner(
                                    sameSite = sameSite,
                                    groupCount = sameSiteGroupCount,
                                    onClear = { viewModel.setDuplicatesOnly(false) },
                                    // Only for the filter that is sure. The
                                    // same-site one is a guess, and acting on
                                    // a guess in one tap is how a Guardian
                                    // section goes.
                                    onRemoveExtra = if (sameSite) null else viewModel::removeDuplicateCopies,
                                )
                            }
                        } else if (state.allTags.isNotEmpty()) {
                            item {
                                // Categories exist in the database and on a
                                // screen two taps away; nothing on the list
                                // itself said which ones there were. Searching
                                // the name nearly does this, but it also
                                // matches titles and URLs containing the word.
                                CategoryChips(
                                    tags = state.allTags,
                                    chosen = category,
                                    onPick = viewModel::setCategory,
                                    onReorder = viewModel::setCategoryOrder,
                                )
                            }
                        }
                        if (duplicatesOnly) {
                            // Grouped, because the answer to "which of these
                            // is a duplicate of which" is the whole question.
                            // Sorted into one flat list, the two halves of a
                            // pair can sit a screen apart among a hundred
                            // sources, which tells the reader that duplicates
                            // exist and leaves them to do the pairing.
                            //
                            // Looked up against the live list rather than a
                            // snapshot, so deleting one member removes it from
                            // its group instead of leaving a row that is no
                            // longer there.
                            val byId = (state.enabledSources + state.disabledSources)
                                .associateBy { it.id }
                            duplicateGroups.forEach { group ->
                                val members = group.ids.mapNotNull(byId::get)
                                // A set with one member left is no longer a
                                // set: the reader has already resolved it.
                                if (members.size < 2) return@forEach
                                item(key = "group-${group.label}-${group.ids.first()}") {
                                    PreferenceGroupHeading(
                                        heading = pluralStringResource(
                                            R.plurals.sources_duplicate_group,
                                            members.size,
                                            group.label,
                                            members.size,
                                        )
                                    )
                                }
                                items(members, key = { it.id }) { item ->
                                    SourceItem(
                                        modifier = Modifier.animateItem(),
                                        source = item,
                                        selectionMode = selection.isNotEmpty(),
                                        selected = item.id in selection,
                                        onLongClick = { viewModel.toggleSelected(it.id) },
                                        onExtendSelection = {
                                            viewModel.extendSelection(shownIds, it.id)
                                        },
                                        staleSince = staleSince,
                                        onClick = {
                                            scope.launch {
                                                paneNavigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    item.id
                                                )
                                            }
                                        },
                                        onSwitch = {
                                            viewModel.updateFeed(
                                                it.copy(isEnabled = !it.isEnabled),
                                                true,
                                            )
                                        }
                                    )
                                }
                            }
                            item { Spacer(modifier = Modifier.height(64.dp)) }
                            return@LazyColumn
                        }

                        // A heading over nothing was tolerable when the list
                        // was only ever searched; with a category chip it is
                        // routine for one of the two sections to be empty.
                        if (state.enabledSources.isNotEmpty()) item {
                            PreferenceGroupHeading(heading = stringResource(id = R.string.enabled))
                        }
                        items(state.enabledSources, key = { it.id }) { item ->
                            SourceItem(
                                modifier = Modifier.animateItem(),
                                source = item,
                                selectionMode = selection.isNotEmpty(),
                                selected = item.id in selection,
                                onLongClick = { viewModel.toggleSelected(it.id) },
                                onExtendSelection = {
                                    viewModel.extendSelection(shownIds, it.id)
                                },
                                staleSince = staleSince,
                                pinned = item.id in state.pinned,
                                onPin = {
                                    scope.launch {
                                        if (!viewModel.togglePinned(it.id)) {
                                            snackbarHostState.showSnackbar(
                                                resources.getQuantityString(
                                                    R.plurals.source_pin_full,
                                                    MAX_PINNED_SOURCES,
                                                    MAX_PINNED_SOURCES,
                                                ),
                                                withDismissAction = true,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    scope.launch {
                                        paneNavigator.navigateTo(
                                            ListDetailPaneScaffoldRole.Detail,
                                            item.id
                                        )
                                    }
                                },
                                onSwitch = {
                                    viewModel.updateFeed(
                                        it.copy(isEnabled = false),
                                        false
                                    )
                                }
                            )
                        }
                        if (state.disabledSources.isNotEmpty()) item {
                            PreferenceGroupHeading(heading = stringResource(id = R.string.disabled))
                        }
                        items(state.disabledSources, key = { it.id }) { item ->
                            SourceItem(
                                modifier = Modifier.animateItem(),
                                source = item,
                                selectionMode = selection.isNotEmpty(),
                                selected = item.id in selection,
                                onLongClick = { viewModel.toggleSelected(it.id) },
                                onExtendSelection = {
                                    viewModel.extendSelection(shownIds, it.id)
                                },
                                staleSince = staleSince,
                                onClick = {
                                    scope.launch {
                                        paneNavigator.navigateTo(
                                            ListDetailPaneScaffoldRole.Detail,
                                            item.id
                                        )
                                    }
                                },
                                onSwitch = {
                                    viewModel.updateFeed(
                                        it.copy(isEnabled = true),
                                        true,
                                    )
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(64.dp))
                        }
                    }
                }
            }
        },
        detailPane = {
            sourceId.longValue = paneNavigator.currentDestination
                ?.takeIf { it.pane == this.paneRole }?.contentKey
                .toString().toLongOrDefault(-1L)

            sourceId.longValue.takeIf { it != -1L }?.let { id ->
                AnimatedPane {
                    SourceEditPage(id) {
                        scope.launch {
                            paneNavigator.navigateBack()
                        }
                    }
                }
            }
        }
    )
}

/** How long a source can go without a successful sync before it is flagged. */
private const val STALE_AFTER_MS = 3L * 24 * 60 * 60 * 1000

/** Which of the three tag operations a dialog is open for. */
enum class TagAction(val labelId: Int) {
    Add(R.string.sources_add_tag),
    Remove(R.string.sources_remove_tag),
    Replace(R.string.sources_replace_tags),
}

/**
 * What the app bar carries while a selection is running.
 *
 * Two icons and an overflow, which is the Material limit and also the honest
 * one: naming three tag operations, enable, disable, full text and clear
 * across the top of a phone would mean either eight unlabelled glyphs or a row
 * that scrolls. Category and Delete are the two anybody reaches for, and Delete
 * is drawn in the error colour so the destructive one is the one that looks
 * destructive.
 */
@Composable
private fun SelectionActions(
    onTag: (TagAction) -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onFullText: (Boolean) -> Unit,
    onClearArticles: () -> Unit,
) {
    IconButton(onClick = { onTag(TagAction.Add) }) {
        Icon(Phosphor.Hash, stringResource(R.string.sources_add_tag))
    }
    IconButton(onClick = onDelete) {
        Icon(
            imageVector = Phosphor.TrashSimple,
            contentDescription = stringResource(R.string.sources_delete_selected),
            tint = MaterialTheme.colorScheme.error,
        )
    }
    OverflowMenu(description = R.string.sources_more_actions) {
        DropdownMenuItem(
            leadingIcon = { Icon(Phosphor.Check, null) },
            onClick = { hideMenu(); onSelectAll() },
            text = { Text(stringResource(R.string.sources_select_all)) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { Icon(Phosphor.Hash, null) },
            onClick = { hideMenu(); onTag(TagAction.Remove) },
            text = { Text(stringResource(R.string.sources_remove_tag)) },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Phosphor.Hash, null) },
            onClick = { hideMenu(); onTag(TagAction.Replace) },
            text = { Text(stringResource(R.string.sources_replace_tags)) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { Icon(Phosphor.Power, null) },
            onClick = { hideMenu(); onEnable() },
            text = { Text(stringResource(R.string.sources_enable)) },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Phosphor.Prohibit, null) },
            onClick = { hideMenu(); onDisable() },
            text = { Text(stringResource(R.string.sources_disable)) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { Icon(Phosphor.BracketsSquare, null) },
            onClick = { hideMenu(); onFullText(true) },
            text = { Text(stringResource(R.string.sources_full_text_on)) },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Phosphor.ListDashes, null) },
            onClick = { hideMenu(); onFullText(false) },
            text = { Text(stringResource(R.string.sources_full_text_off)) },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Phosphor.ArrowCounterClockwise, null) },
            onClick = { hideMenu(); onClearArticles() },
            text = { Text(stringResource(R.string.sources_clear_articles)) },
        )
    }
}

/**
 * The categories in use, as a row that filters the list, and the place their
 * order is set.
 *
 * Single-choice: two categories at once is either "and", which is usually
 * empty, or "or", which is usually the whole list, and a chip row cannot say
 * which one it meant. Tapping the chosen chip again clears it, so the row
 * needs no All chip explaining how to get back.
 *
 * Press and hold a chip to pick it up, drag it along the row, and let go: the
 * order is kept, and both feeds' chips follow it. A quick swipe still scrolls
 * the row, since nothing moves until the hold. Screen readers get Move left
 * and Move right instead of the drag.
 */
@Composable
internal fun CategoryChips(
    tags: List<String>,
    chosen: String?,
    onPick: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val gap = 8.dp
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    // The row as it is being dragged; the stored order once it is let go.
    var working by remember(tags) { mutableStateOf(tags) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<String, Int>() }
    // A drag ends with the finger lifting over the chip it carried, which
    // reads as a tap on it. A tap that soon after a drop is that lift.
    var droppedAt by remember { mutableLongStateOf(0L) }
    val moveLeft = stringResource(R.string.category_move_left)
    val moveRight = stringResource(R.string.category_move_right)

    fun commit(order: List<String>) {
        if (order != tags) onReorder(order)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        working.forEachIndexed { index, tag ->
          // Keyed, so a chip that changes place during the drag is still the
          // same chip, and its gesture is not restarted under the finger.
          key(tag) {
            val lifted = tag == dragging
            FilterChip(
                selected = tag == chosen,
                onClick = {
                    val lift = droppedAt != 0L && SystemClock.uptimeMillis() - droppedAt <= DROP_TAP_MS
                    if (!lift) onPick(tag)
                },
                label = { Text(tag) },
                leadingIcon = if (tag == chosen) {
                    { Icon(Phosphor.Check, null) }
                } else null,
                elevation = FilterChipDefaults.filterChipElevation(
                    elevation = if (lifted) 6.dp else 0.dp,
                ),
                modifier = Modifier
                    .onSizeChanged { widths[tag] = it.width }
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer {
                        if (lifted) {
                            translationX = offset
                            scaleX = 1.05f
                            scaleY = 1.05f
                        }
                    }
                    .semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveLeft) {
                                commit(CategoryOrder.move(working, index, index - 1)); true
                            })
                            if (index < working.lastIndex) add(CustomAccessibilityAction(moveRight) {
                                commit(CategoryOrder.move(working, index, index + 1)); true
                            })
                        }
                    }
                    .pointerInput(tag) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragging = tag
                                offset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                offset += amount.x
                                // Swap with a neighbour once the chip is more
                                // than halfway over it, and carry the offset
                                // across so the chip stays under the finger.
                                val at = working.indexOf(tag)
                                val right = working.getOrNull(at + 1)
                                val left = working.getOrNull(at - 1)
                                if (right != null && offset > (widths[right] ?: 0) / 2f) {
                                    offset -= (widths[right] ?: 0) + gapPx
                                    working = CategoryOrder.move(working, at, at + 1)
                                } else if (left != null && offset < -(widths[left] ?: 0) / 2f) {
                                    offset += (widths[left] ?: 0) + gapPx
                                    working = CategoryOrder.move(working, at, at - 1)
                                }
                            },
                            onDragEnd = {
                                dragging = null
                                offset = 0f
                                droppedAt = SystemClock.uptimeMillis()
                                commit(working)
                            },
                            onDragCancel = {
                                dragging = null
                                offset = 0f
                                working = tags
                            },
                        )
                    },
            )
          }
        }
    }
}

/** How long after a drop a tap on the dropped chip is taken to be the lift. */
private const val DROP_TAP_MS = 400L

/**
 * Says that some feeds are subscribed twice, before anybody thinks to ask.
 *
 * Each one is downloaded twice on every sync. The same shape as the banner
 * below, because it is the way into what that banner explains.
 */
@Composable
private fun DuplicatesFound(count: Int, onReview: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = pluralStringResource(R.plurals.sources_duplicates_found, count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) {
                Text(stringResource(R.string.sources_duplicates_review))
            }
        }
    }
}

/**
 * Says why the list is short, while the duplicates filter is on.
 *
 * Without it the screen is indistinguishable from a search that matched
 * almost nothing, and the way out — the same menu entry, tapped again — is
 * not somewhere anyone would look.
 */
@Composable
private fun DuplicatesBanner(
    sameSite: Boolean,
    groupCount: Int,
    onClear: () -> Unit,
    /** Removes all but the oldest of each group; null where that is not safe to offer. */
    onRemoveExtra: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
            Text(
                // The two filters mean different things and the difference
                // matters: one found the same feed twice, the other made a
                // guess. Saying which is on is what stops somebody deleting a
                // Guardian section because the screen listed it.
                text = if (sameSite) {
                    pluralStringResource(
                        R.plurals.sources_same_site_showing,
                        groupCount,
                        groupCount,
                    )
                } else {
                    stringResource(R.string.sources_duplicates_showing)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onRemoveExtra != null) {
                    TextButton(onClick = onRemoveExtra) {
                        Text(stringResource(R.string.sources_duplicates_remove_extra))
                    }
                }
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.sources_show_all))
                }
            }
        }
    }
}

/**
 * Picking categories for a bulk edit.
 *
 * Existing categories are offered as chips and a new one can be typed, which is
 * the whole point: a category should be selected from what exists, so that
 * "Tech" and "tech" do not both end up in the list because two sources were
 * tagged on different days.
 */
@Composable
private fun TagPickerDialog(
    action: TagAction,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(action.labelId)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = tag in picked,
                            onClick = {
                                picked = if (tag in picked) picked - tag else picked + tag
                            },
                            label = { Text(tag) },
                        )
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.source_tags)) },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .traceFocus("bulk category dialog"),
                )
            }
        },
        confirmButton = {
            val result = picked + typed.split(",").map(String::trim).filter(String::isNotEmpty)
            TextButton(
                // Replace with nothing is a legitimate action — it clears every
                // category off the selection — so only Add and Remove need a
                // non-empty result.
                enabled = result.isNotEmpty() || action == TagAction.Replace,
                onClick = { onConfirm(result) },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
