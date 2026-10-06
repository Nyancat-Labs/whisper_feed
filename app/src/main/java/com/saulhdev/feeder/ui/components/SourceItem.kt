/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
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

package com.saulhdev.feeder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.repository.FAILURES_BEFORE_BROKEN
import com.saulhdev.feeder.ui.icons.Phosphor
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import com.saulhdev.feeder.ui.icons.phosphor.Asterisk
import com.saulhdev.feeder.ui.icons.phosphor.EyeSlash
import com.saulhdev.feeder.ui.icons.phosphor.WifiHigh
import com.saulhdev.feeder.data.db.models.Feed

@Composable
fun SourceItem(
    source: Feed,
    modifier: Modifier = Modifier,
    onSwitch: (Feed) -> Unit = {},
    onClick: (Feed) -> Unit = {},
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (Feed) -> Unit = {},
    /**
     * Long-press while a selection is already running.
     *
     * Separate from [onLongClick] because the two gestures mean different
     * things once a selection exists: a tap adds or removes this one source,
     * a long-press takes the whole run back to the last one picked. That is
     * the convention every file manager and mail app uses, and it is the
     * difference between filing thirty imported feeds in two gestures and
     * doing it in thirty taps. Defaults to [onLongClick], so a caller that
     * has no range behaviour keeps the plain toggle.
     */
    onExtendSelection: ((Feed) -> Unit)? = null,
    staleSince: Long = 0L,
    /** Whether this source is one of the few kept at the top of the list. */
    pinned: Boolean = false,
    /** Null hides the control entirely, for the screens that have no list to top. */
    onPin: ((Feed) -> Unit)? = null,
) {
    val extend = onExtendSelection ?: onLongClick
    val (isEnabled, enable) = remember(source.isEnabled) {
        mutableStateOf(source.isEnabled)
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerLowest, label = "backgroundColor"
    )

    // A source that has not synced for days looks identical to one that is
    // simply quiet, so say which it is. The threshold is passed in rather than
    // computed here: "now" in a composable would be captured at composition and
    // then drift.
    val health = sourceHealth(
        lastSyncMs = source.lastSync.toEpochMilliseconds(),
        isEnabled = source.isEnabled,
        staleSince = staleSince,
        consecutiveFailures = source.consecutiveFailures,
    )
    val isStale = health != SourceHealth.Fine

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = { if (selectionMode) onLongClick(source) else onClick(source) },
                onLongClick = { if (selectionMode) extend(source) else onLongClick(source) },
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else backgroundColor,
        ),
        overlineContent = {
            Text(
                text = source.url.toString(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        headlineContent = {
            Text(text = source.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val tags = source.tags
            if (tags.isNotEmpty() || isStale) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isStale) {
                        Icon(
                            imageVector = Phosphor.WifiHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(
                                if (health == SourceHealth.NeverUpdated) {
                                    R.string.source_never_updated
                                } else {
                                    R.string.source_not_updating
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (tags.isNotEmpty()) {
                        Text(
                            text = tags.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onLongClick(source) })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onPin != null) {
                        // The same asterisk a pinned article carries, because
                        // it means the same thing — kept at the top until the
                        // reader says otherwise. One mark for one idea is
                        // worth more than two prettier ones.
                        //
                        // There is no filled asterisk to pair with the outline
                        // the way a heart has, so the pinned state is a filled
                        // container behind it rather than a heavier glyph. A
                        // tint change alone would be the only difference
                        // between the two states, which is colour carrying
                        // meaning on its own.
                        IconButton(onClick = { onPin(source) }) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pinned) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    ),
                            ) {
                                Icon(
                                    imageVector = Phosphor.Asterisk,
                                    contentDescription = stringResource(
                                        if (pinned) R.string.source_unpin
                                        else R.string.source_pin
                                    ),
                                    tint = if (pinned)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Switch(
                        checked = isEnabled,
                        colors = SwitchDefaults.colors(uncheckedBorderColor = Color.Transparent),
                        onCheckedChange = {
                            enable(!isEnabled)
                            onSwitch(source)
                        }
                    )
                }
            }
        }
    )
}



/** What the list has to say about a source's fetching, if anything. */
enum class SourceHealth { Fine, NeverUpdated, NotUpdating }

/**
 * Whether a source is fetching, and if not, which kind of not.
 *
 * The two cases read the same on screen and are not the same problem. "Not
 * updating" means it used to work and has stopped, which is usually the
 * publisher's end. "Never updated" means it has not worked once since it was
 * added, which is usually the address — a typo, or an OPML entry that was
 * already dead when it was exported.
 *
 * Telling them apart was impossible until `lastSync` stopped defaulting to the
 * moment the row was created; see [com.saulhdev.feeder.data.db.models.NEVER_SYNCED].
 *
 * A disabled source says nothing: it is not fetching because it was told not
 * to, and reporting that as a fault would put a red line against every source
 * the reader deliberately switched off.
 */
fun sourceHealth(
    lastSyncMs: Long,
    isEnabled: Boolean,
    staleSince: Long,
    consecutiveFailures: Int = 0,
): SourceHealth = when {
    !isEnabled -> SourceHealth.Fine
    lastSyncMs <= 0L -> SourceHealth.NeverUpdated
    // A run of failures is the direct evidence and is believed before the
    // clock. This list used to ask only how long ago the last *success* was,
    // and a feed that has been failing since breakfast succeeded this morning
    // — so ten feeds could be failing, the app could know the count for each,
    // the Broken feeds screen could be listing the worst of them, and the
    // screen anybody would actually look at said nothing was wrong.
    //
    // The same three as Broken feeds, so the two screens cannot disagree. One
    // timeout is noise; three in a row is a feed that has stopped rather than
    // stumbled, and that argument belongs in one place.
    consecutiveFailures >= FAILURES_BEFORE_BROKEN -> SourceHealth.NotUpdating
    // Zero means the caller has not worked out a threshold yet, which happens
    // for one frame on a cold screen. Saying nothing is better than saying
    // everything is broken.
    staleSince <= 0L -> SourceHealth.Fine
    lastSyncMs < staleSince -> SourceHealth.NotUpdating
    else -> SourceHealth.Fine
}
