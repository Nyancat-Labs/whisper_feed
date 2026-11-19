/*
 * This file is part of Whisper.
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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** One entry in a [GroupedMenu]. */
class GroupedMenuEntry(
    @StringRes val textId: Int,
    val icon: ImageVector?,
    /** Drawn in the primary colour, with [trailingIcon] after it: the current choice. */
    val chosen: Boolean = false,
    val trailingIcon: ImageVector? = null,
    /** Leaves the menu open, so a change the entry makes stays in sight. */
    val keepOpen: Boolean = false,
    val action: () -> Unit,
)

/** A named group of entries; [labelId] -1 for none. */
class GroupedMenuSection(@StringRes val labelId: Int = -1, val entries: List<GroupedMenuEntry>)

/**
 * Expressive's menu in named groups, as on the article card (review 58): a
 * header button, and a popup of rounded groups with a small gap between them.
 * Sources uses it for its ⋮ menu and its Sort button (review 60).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupedMenu(
    icon: ImageVector,
    @StringRes description: Int,
    sections: List<GroupedMenuSection>,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val close = { expanded = false }
    Box {
        IconButton(
            onClick = {
                // As in OverflowMenu: nothing for Compose to hand focus back
                // to, so the search field's keyboard does not return.
                focusManager.clearFocus()
                expanded = true
            },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(description),
                modifier = Modifier.size(HeaderIconSize),
            )
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = close) {
            val shown = sections.filter { it.entries.isNotEmpty() }
            shown.forEachIndexed { g, section ->
                if (g > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(g, shown.size)) {
                    if (section.labelId != -1) {
                        Text(
                            text = stringResource(section.labelId),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(MenuDefaults.DropdownMenuGroupLabelHorizontalPadding)
                                .padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    section.entries.forEachIndexed { e, entry ->
                        DropdownMenuItem(
                            onClick = {
                                if (!entry.keepOpen) close()
                                entry.action()
                            },
                            text = {
                                Text(
                                    text = stringResource(entry.textId),
                                    color = if (entry.chosen) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            shape = MenuDefaults.itemShape(e, section.entries.size).shape,
                            leadingIcon = entry.icon?.let { icon ->
                                @Composable { Icon(icon, contentDescription = null) }
                            },
                            trailingContent = entry.trailingIcon?.let { icon ->
                                @Composable {
                                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
