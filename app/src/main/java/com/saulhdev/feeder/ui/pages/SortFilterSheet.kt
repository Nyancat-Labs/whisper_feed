package com.saulhdev.feeder.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.ChipsSwitch
import com.saulhdev.feeder.ui.components.DeSelectAll
import com.saulhdev.feeder.ui.components.ComposeSwitchView
import com.saulhdev.feeder.ui.components.ExpandableItemsBlock
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.SelectChip
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowUUpLeft
import com.saulhdev.feeder.ui.icons.phosphor.Check
import com.saulhdev.feeder.ui.icons.phosphor.CheckCircle
import com.saulhdev.feeder.ui.icons.phosphor.SortAscending
import com.saulhdev.feeder.ui.icons.phosphor.SortDescending
import com.saulhdev.feeder.utils.READ_HIDE
import com.saulhdev.feeder.utils.readVisibilityOnShow
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.MarkReadRange
import com.saulhdev.feeder.viewmodels.SortFilterViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.compose.koinInject
import com.saulhdev.feeder.data.content.asState

@OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun SortFilterSheet(
    viewModel: SortFilterViewModel = koinNeoViewModel(),
    prefs: FeedPreferences = koinInject(),
    // Both or neither. The app passes them; the launcher panel has no undo
    // for a batch of reads yet, so it does not offer one.
    markReadCounts: (suspend (nowMs: Long) -> Map<MarkReadRange, Int>)? = null,
    onMarkRead: ((MarkReadRange, nowMs: Long) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val nestedScrollConnection = rememberNestedScrollInteropConnection()
    val state by viewModel.sheetState.collectAsState()

    var sortPrefVar by prefs.sortingFilter
    var sortAscPrefVar by prefs.sortingAsc
    var sourcesPrefVar by prefs.sourcesFilter
    var tagsPrefVar by prefs.tagsFilter
    var readVisibilityPrefVar by prefs.readVisibility
    val readVisibility by prefs.readVisibility.asState()
    // Staged until Apply, like everything else in this sheet. A control that
    // acts the moment it is touched, sitting above an Apply button, leaves the
    // reader unsure which of the two did the work.
    var showReadOption by remember(readVisibility) {
        mutableStateOf(readVisibility != READ_HIDE)
    }

    var sortOption by remember(state.sortFilter.sort) {
        mutableStateOf(state.sortFilter.sort)
    }
    var sortAscOption by remember(state.sortFilter.sortAsc) {
        mutableStateOf(state.sortFilter.sortAsc)
    }
    val sourcesOption = remember(state.sortFilter.sourcesFilter) {
        mutableStateListOf(*state.sortFilter.sourcesFilter.toTypedArray())
    }

    val tagsOption = remember(state.sortFilter.tagsFilter) {
        mutableStateListOf(*state.sortFilter.tagsFilter.toTypedArray())
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                HorizontalDivider(thickness = 2.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedActionButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.action_reset),
                        icon = Phosphor.ArrowUUpLeft,
                        positive = false,
                    ) {
                        sortPrefVar = prefs.sortingFilter.defaultValue
                        sortAscPrefVar = prefs.sortingAsc.defaultValue
                        sourcesPrefVar = prefs.sourcesFilter.defaultValue
                        tagsPrefVar = prefs.tagsFilter.defaultValue
                        readVisibilityPrefVar = prefs.readVisibility.defaultValue
                        onDismiss()
                    }
                    ActionButton(
                        text = stringResource(id = R.string.action_apply),
                        icon = Phosphor.Check,
                        modifier = Modifier.weight(1f),
                        positive = true,
                        onClick = {
                            sortPrefVar = sortOption
                            sortAscPrefVar = sortAscOption
                            sourcesPrefVar = sourcesOption.toSet()
                            tagsPrefVar = tagsOption.toSet()
                            // Turning them back on restores whichever of Keep
                            // or Fade was set when this switch hid them. It
                            // used to be Keep every time, which quietly undid
                            // a Fade chosen in Settings.
                            if (showReadOption) {
                                if (readVisibility == READ_HIDE) {
                                    readVisibilityPrefVar = readVisibilityOnShow(
                                        prefs.readVisibilityBeforeHide.getValue()
                                    )
                                }
                            } else {
                                if (readVisibility != READ_HIDE) {
                                    prefs.readVisibilityBeforeHide.set(readVisibility)
                                }
                                readVisibilityPrefVar = READ_HIDE
                            }
                            onDismiss()
                        }
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(
                    bottom = paddingValues.calculateBottomPadding(),
                    start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                    end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                )
                .nestedScroll(nestedScrollConnection)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.sorting_order),
                    preExpanded = true,
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        prefs.sortingFilter.entries.forEach {
                            SelectChip(
                                text = it.value,
                                checked = it.key == sortOption,
                                alwaysShowIcon = false,
                            ) {
                                sortOption = it.key
                            }
                        }
                    }
                    ChipsSwitch(
                        firstTextId = R.string.sort_ascending,
                        firstIcon = Phosphor.SortAscending,
                        secondTextId = R.string.sort_descending,
                        secondIcon = Phosphor.SortDescending,
                        firstSelected = sortAscOption,
                        onCheckedChange = { checked ->
                            sortAscOption = checked
                        }
                    )
                }
            }

            item {
                // The way back. Hiding read articles is set in Settings, but
                // the filter that made something vanish is the one place a
                // reader looks for it — and a switch buried two screens away
                // is indistinguishable from the articles being gone for good.
                ComposeSwitchView(
                    titleId = R.string.show_read_articles,
                    isChecked = showReadOption,
                    onCheckedChange = { showReadOption = it },
                    index = 0,
                    groupSize = 1,
                )
            }

            if (markReadCounts != null && onMarkRead != null) item {
                MarkReadBlock(
                    counts = markReadCounts,
                    onMark = { range, now ->
                        onMarkRead(range, now)
                        onDismiss()
                    },
                )
            }

            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.title_sources),
                    preExpanded = sourcesOption.isNotEmpty(),
                ) {
                    DeSelectAll(state.activeSources.map { it.id.toString() }, sourcesOption)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.activeSources.sortedBy { it.title.lowercase() }.forEach {
                            val checked by remember(sourcesOption.toString()) {
                                mutableStateOf(!sourcesOption.contains(it.id.toString()))
                            }

                            SelectChip(
                                text = it.title,
                                checked = checked,
                            ) {
                                if (checked) sourcesOption.add(it.id.toString())
                                else sourcesOption.remove(it.id.toString())
                            }
                        }
                    }
                }
            }

            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.source_tags),
                    preExpanded = tagsOption.isNotEmpty(),
                ) {
                    DeSelectAll(state.activeTags.map { it }, tagsOption)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.activeTags.sortedBy { it.lowercase() }.forEach {
                            val checked by remember(tagsOption.toString()) {
                                mutableStateOf(!tagsOption.contains(it))
                            }

                            SelectChip(
                                text = it,
                                checked = checked,
                            ) {
                                if (checked) tagsOption.add(it)
                                else tagsOption.remove(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Marking read, in the sheet but not of it.
 *
 * Everything else here is staged until Apply; these act the moment they are
 * tapped, close the sheet, and are offered back in the feed. So they are
 * buttons rather than chips, under their own heading, with a line saying so —
 * a control that looks like a filter and marks four hundred articles read is
 * the one mistake this sheet must not invite.
 *
 * Each shows what it would mark, counted when the sheet opened, and the
 * marking uses that same moment so the number and the result agree.
 */
@Composable
private fun MarkReadBlock(
    counts: suspend (nowMs: Long) -> Map<MarkReadRange, Int>,
    onMark: (MarkReadRange, nowMs: Long) -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val counted by produceState<Map<MarkReadRange, Int>?>(null) { value = counts(now) }
    ExpandableItemsBlock(
        heading = stringResource(R.string.mark_read_title),
        icon = Phosphor.CheckCircle,
        preExpanded = true,
    ) {
        Text(
            text = stringResource(R.string.mark_read_explained),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MarkReadRange.entries.forEach { range ->
            val count = counted?.get(range) ?: 0
            OutlinedActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(range.label()) +
                        if (counted != null) " \u00b7 " + pluralStringResource(R.plurals.mark_read_unread, count, count)
                        else "",
                positive = true,
                enabled = count > 0,
                onClick = { onMark(range, now) },
            )
        }
    }
}

private fun MarkReadRange.label(): Int = when (this) {
    MarkReadRange.Everything -> R.string.mark_read_everything
    MarkReadRange.OlderThanHour -> R.string.mark_read_older_hour
    MarkReadRange.OlderThanDay -> R.string.mark_read_older_day
    MarkReadRange.OlderThanTwoDays -> R.string.mark_read_older_two_days
}
