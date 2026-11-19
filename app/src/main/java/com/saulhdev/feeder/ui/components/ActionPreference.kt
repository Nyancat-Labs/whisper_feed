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

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.saulhdev.feeder.ui.components.dialog.DialogNegativeButton
import com.saulhdev.feeder.ui.components.dialog.DialogPositiveButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.saulhdev.feeder.data.content.StringPref
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.ui.navigation.NavRoute

@Composable
fun ActionPreference(
    modifier: Modifier = Modifier,
    pref: StringPref,
    index: Int = 1,
    groupSize: Int = 1,
    isEnabled: Boolean = true,
) {
    val navController = LocalNavController.current
    var asking by remember { mutableStateOf(false) }
    if (asking && pref.confirmId != null) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(pref.titleId)) },
            text = { Text(stringResource(pref.confirmId)) },
            confirmButton = {
                DialogPositiveButton(
                    text = stringResource(pref.titleId),
                    onClick = {
                        asking = false
                        pref.onClick?.invoke()
                    },
                )
            },
            dismissButton = { DialogNegativeButton(onClick = { asking = false }) },
        )
    }
    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        index = index,
        groupSize = groupSize,
        isEnabled = isEnabled,
        startWidget = {
            PreferenceIcon(
                icon = pref.icon,
                contentDescription = stringResource(id = pref.titleId),
            )
        },
        onClick = {
            if (pref.route != null) {
                navController.navigate(pref.route)
            } else if (pref.confirmId != null) {
                asking = true
            } else {
                pref.onClick?.invoke()
            }
        }
    )
}

@Composable
fun PagePreference(
    modifier: Modifier = Modifier,
    @StringRes titleId: Int,
    icon: ImageVector,
    index: Int = 1,
    groupSize: Int = 1,
    route: NavRoute,
) {
    val navController = LocalNavController.current
    BasePreference(
        modifier = modifier,
        titleId = titleId,
        startWidget = {
            Icon(imageVector = icon, contentDescription = stringResource(id = titleId))
        },
        index = index,
        groupSize = groupSize,
        onClick = { navController.navigate(route) }
    )
}