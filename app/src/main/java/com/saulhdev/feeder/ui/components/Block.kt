/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.CaretDown

/**
 * How far a block's explanatory line is set in, so it starts under the
 * heading's text rather than under its icon (review 6): the icon's 24dp and
 * the 8dp gap after it. Controls (chips, buttons) use the full width.
 */
val BlockTextIndent = 32.dp

@Composable
fun ExpandableItemsBlock(
    modifier: Modifier = Modifier,
    heading: String? = null,
    icon: ImageVector? = null,
    preExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(preExpanded) }
    // A card open or closed (review 2): closed ones sat bare on the sheet
    // beside open ones on a card, the same row drawn two ways. The colour of
    // the switch rows beside them, so the sheet reads as one set.
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val expandedPadding by animateDpAsState(
        targetValue = if (expanded) 2.dp else 0.dp,
        label = "expandedPadding"
    )

    Surface(
        modifier = Modifier
            .animateContentSize()
            .padding(vertical = expandedPadding),
        shape = MaterialTheme.shapes.large,
        onClick = { expanded = !expanded },
        color = surfaceColor
    ) {
        Column(modifier = modifier) {
            BlockHeader(heading, icon)
            AnimatedVisibility(visible = expanded) {
                Column(
                    // In line with the heading's text (review 6).
                    Modifier.padding(
                        bottom = 16.dp,
                        start = 16.dp,
                        end = 16.dp,
                    )
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun BlockHeader(
    heading: String? = null,
    icon: ImageVector? = null,
    withIcon: Boolean = true,
) {
    var spacerHeight = 0
    if (heading == null) spacerHeight += 8
    Spacer(modifier = Modifier.requiredHeight(spacerHeight.dp))
    if (heading != null) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                )
            }
            Text(
                modifier = Modifier.weight(1f),
                text = heading,
                style = MaterialTheme.typography.titleSmall,
            )
            if (withIcon) Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Phosphor.CaretDown,
                contentDescription = heading
            )
        }
    }
}