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
package com.saulhdev.feeder.ui.overlay

import com.saulhdev.feeder.ui.icons.phosphor.Check
import com.saulhdev.feeder.ui.icons.Phosphor
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import com.saulhdev.feeder.ui.components.categoryChipColors
import com.saulhdev.feeder.ui.components.categoryChipShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.saulhdev.feeder.R

/**
 * The category strip beneath the feed header.
 *
 * Selection maps onto the existing tag filter, so choosing a chip filters the
 * feed through the same path the filter sheet already uses. Selecting none is
 * "All": an empty tag set is what the article query treats as unfiltered.
 *
 * The "All" chip is always drawn, even with no categories to sit beside it.
 * That is partly so the row is visibly present before any source has been
 * tagged, and partly because an empty row is indistinguishable from one that
 * failed to render — which is precisely what made the first version of this
 * hard to verify on device.
 *
 * The permanent categories the design calls for (Unread, Saved, Recently
 * Added) are not here yet: articles currently carry no read state at all, so
 * they need the article-state model rather than a chip that lies.
 *
 * Unselected chips carry a filled container rather than Material's default
 * outline-over-nothing. The overlay's background opacity is user-adjustable, and
 * at a low setting an outlined chip has nothing behind it but the wallpaper —
 * the labels became unreadable on anything busy. A container costs a little of
 * the "unselected" contrast against the selected chip and buys legibility that
 * does not depend on what the user's wallpaper happens to be.
 */
@Composable
fun CategoryChipRow(
    categories: List<String>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sources default to an empty tag and the query does not exclude it, so an
    // untagged feed otherwise shows up here as a nameless chip.
    val named = categories.filter { it.isNotBlank() }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        item(key = ALL_CHIP_KEY) {
            CategoryChip(
                label = stringResource(R.string.all_categories),
                selected = selected.isEmpty(),
                // Already unfiltered, so re-tapping "All" should not churn the feed.
                onClick = { if (selected.isNotEmpty()) onSelectedChange(emptySet()) },
            )
        }

        items(named, key = { it }) { category ->
            val isSelected = category in selected
            CategoryChip(
                label = category,
                selected = isSelected,
                onClick = {
                    onSelectedChange(
                        if (isSelected) selected - category else selected + category
                    )
                },
            )
        }
    }
}

/**
 * A category chip: filled, with a tick, when it is on.
 *
 * It had an underline as well (review 11: two marks for one thing, left over
 * from when this row was a tab bar). The tick is Material's own sign of a
 * chosen filter chip, and like the underline it does not depend on telling
 * two shades apart.
 */
@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Phosphor.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        shape = categoryChipShape,
        colors = categoryChipColors(),
        border = null,
    )
}


private const val ALL_CHIP_KEY = "__all__"
