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
package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowLeft
import com.saulhdev.feeder.ui.icons.phosphor.X
import com.saulhdev.feeder.ui.overlay.SourceMark

/**
 * Says which source the feed has been narrowed to, and how to stop.
 *
 * Deliberately the same shape as [FeedSearchBar] — a back arrow, the subject,
 * and an X — because it is the same kind of state: a detour the reader is
 * currently on, with two ways back. Learning one teaches the other.
 *
 * What it is **not** is that bar with a source's name typed into it, which was
 * the obvious shortcut and is wrong twice over. `matchesSearch` compares
 * substrings across three fields, so "slate" also returns "tran*slate*",
 * "*slate*d for release" and every article that merely mentions Slate; and a
 * search deliberately widens the query from the five-hundred-row window to
 * every article ever stored, which is the cost the feed spent a day removing.
 *
 * So the subject is shown rather than typed: the source's own mark and its
 * name, with no cursor and nothing to edit. A bar that cannot be typed into
 * should not look like one that can.
 */
@Composable
fun SourceFilterBar(
    title: String,
    iconUrl: String?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.statusBars,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Inset here rather than at the call site, for the reason
            // FeedSearchBar gives: this replaces a TopAppBar, which insets
            // itself, and a bar drawn under the status bar puts its two
            // controls where the system takes the taps.
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(
                Phosphor.ArrowLeft,
                stringResource(R.string.action_close),
                modifier = Modifier.size(HeaderIconSize),
            )
        }

        // The source's own mark, which is what was tapped. Falling back to a
        // monogram is SourceMark's job and it already does it, so a source
        // with no icon is a letter rather than a gap.
        SourceMark(
            iconUrl = iconUrl,
            sourceName = title,
            size = 24.dp,
        )
        Spacer(Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onClear) {
            Icon(
                Phosphor.X,
                stringResource(R.string.action_clear),
                modifier = Modifier.size(HeaderIconSize),
            )
        }
    }
}
