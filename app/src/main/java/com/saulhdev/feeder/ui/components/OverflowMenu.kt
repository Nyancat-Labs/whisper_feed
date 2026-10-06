/*
 * This file is part of Neo Feed
 * Copyright (c) 2023   Neo Feed Team
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

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.DotsThreeVertical

@Composable
fun OverflowMenu(
    /**
     * What a screen reader calls the button.
     *
     * It announced "Settings" everywhere, on every screen that has one of
     * these — including the ones with no settings in the menu at all.
     */
    description: Int = R.string.more_options,
    block: @Composable OverflowMenuScope.() -> Unit)
{
    val showMenu = remember { mutableStateOf(false) }
    val overflowMenuScope = remember { OverflowMenuScopeImpl(showMenu) }
    val focusManager = LocalFocusManager.current

    Box{
        IconButton(
            onClick = {
                // Focus is dropped as the menu opens, and the menu is not
                // what this is for. Compose restores focus to whatever held
                // it when a popup closes; on the sources screen that is the
                // search field, so opening the menu, choosing something and
                // coming back brought the keyboard up over the result —
                // unasked for, with nothing to type into.
                //
                // Clearing it here rather than on dismissal means there is
                // nothing to restore, which is both simpler and right: a menu
                // is a change of mode, and the field somebody was typing in
                // before they opened one is no longer where they are.
                //
                // In the shared component because every screen with an
                // overflow menu has the same arrangement, and because the two
                // screens that would each need the fix are the two that would
                // each be forgotten.
                focusManager.clearFocus()
                showMenu.value = true
            }
        ) {
            Icon(
                imageVector = Phosphor.DotsThreeVertical,
                contentDescription = stringResource(id = description),
                modifier = Modifier.size(HeaderIconSize),
            )
        }
        DropdownMenu(
            expanded = showMenu.value,
            onDismissRequest = { showMenu.value = false },
        ) {
            block(overflowMenuScope)
        }
    }
}

interface OverflowMenuScope {
    fun hideMenu()
}

private class OverflowMenuScopeImpl(private val showState: MutableState<Boolean>) :
    OverflowMenuScope {
    override fun hideMenu() {
        showState.value = false
    }
}
