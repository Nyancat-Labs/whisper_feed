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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowLeft
import com.saulhdev.feeder.ui.icons.phosphor.X

/**
 * The search field, in place of the header while a search is running.
 *
 * It replaces the title row rather than sitting beside it. A fourth control
 * squeezed into a header that already carries three would leave nothing room
 * enough to be tapped, and a search field is not a peer of those buttons
 * anyway — while it is up, it is what the screen is doing.
 *
 * Searching covers what has already been downloaded, which is every article in
 * the feed: no network, no account, no index, and it works on a train.
 */
@Composable
fun FeedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.statusBars,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Opening a search field and then having to tap it is a wasted step.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Inset by default rather than left to the caller. This replaces a
            // TopAppBar, which insets itself, so a call site that simply swaps
            // one for the other gets a bar drawn *under* the status bar — and
            // the two controls at its ends then sit where the system takes the
            // taps. A tester reported both as broken buttons, which is exactly
            // what they were. The launcher overlay measures its own inset and
            // passes zero here.
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            onQueryChange("")
            onClose()
        }) {
            Icon(
                Phosphor.ArrowLeft,
                stringResource(R.string.action_close),
                modifier = Modifier.size(HeaderIconSize),
            )
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search_articles)) },
            colors = TextFieldDefaults.colors(
                // The header has no container of its own, and neither should
                // this: an outlined box drawn across the top of the feed reads
                // as a second app bar.
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Phosphor.X,
                            stringResource(R.string.action_clear),
                            modifier = Modifier.size(HeaderIconSize),
                        )
                    }
                }
            },
        )
    }
}
