/*
 * This file is part of Neo Launcher
 * Copyright (c) 2022   Neo Launcher Team
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

package com.saulhdev.feeder.ui.components.dialog

import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.data.content.StringSelectionPref
import com.saulhdev.feeder.utils.extensions.blockShadow
import kotlinx.coroutines.launch

/**
 * A choice from a list too long for a row of buttons.
 *
 * Material's own AlertDialog (stage 2, 25 October): the app had two dialog
 * systems, this one hand-built on a bare Dialog with its own card, and
 * Material's everywhere else. One system now, so every dialog has the same
 * shape, colours, width and button placement.
 */
@Composable
fun StringSelectionPrefDialogUI(
    pref: StringSelectionPref,
    openDialogCustom: MutableState<Boolean>
) {
    var selected by remember { mutableStateOf(pref.peekOrDefault()) }
    val entryPairs = pref.entries.toList()

    AlertDialog(
        onDismissRequest = { openDialogCustom.value = false },
        title = { Text(text = stringResource(pref.titleId)) },
        text = {
            LazyColumn(modifier = Modifier.selectableGroup()) {
                items(items = entryPairs, key = { it.first }) {
                    SingleSelectionListItem(
                        text = it.second,
                        supporting = pref.descriptions?.get(it.first)?.let { id -> stringResource(id) },
                        isSelected = selected == it.first
                    ) {
                        selected = it.first
                    }
                }
            }
        },
        confirmButton = {
            DialogPositiveButton(
                onClick = {
                    // set() writes on a scope that outlives the dialog, so
                    // closing it straight after cannot cancel the write.
                    pref.set(selected)
                    openDialogCustom.value = false
                }
            )
        },
        dismissButton = {
            DialogNegativeButton(onClick = { openDialogCustom.value = false })
        },
    )
}

@Composable
fun SingleSelectionListItem(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    /** A line under the choice saying what it is; none by default. */
    supporting: String? = null,
    endWidget: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    // Material's list of choices in a dialog (owner, 12 November): the radio
    // lines up with the title's left edge rather than sitting 22dp in, it
    // is level with the name when there is a line under it, and the whole
    // row is the one tap target, announced as a radio button.
    Row(
        // At least 48, not exactly: at 200% text a choice such as "Only on
        // Wi-Fi and while charging" takes two lines, and a fixed row cut the
        // second one off.
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        verticalAlignment = if (supporting != null) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The row is the touch target, so the radio need not reserve its own
        // 48dp: without that it draws at the row's edge, under the title.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            RadioButton(
                selected = isSelected,
                enabled = isEnabled,
                onClick = null,
                // Level with the name's first line (titleMedium, 24sp line).
                modifier = Modifier.padding(top = if (supporting != null) 12.dp else 0.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = if (supporting != null) 10.dp else 6.dp),
        ) {
            // Exactly a Settings row (BasePreference): title in titleMedium
            // and the full text colour, the line under it in bodyMedium and
            // the quieter colour. Bold names stood out from the rest of the
            // app (owner, 12 November); the space between choices keeps
            // them apart instead.
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (endWidget != null) {
            Spacer(modifier = Modifier.width(8.dp))
            endWidget()
        }
    }
}
