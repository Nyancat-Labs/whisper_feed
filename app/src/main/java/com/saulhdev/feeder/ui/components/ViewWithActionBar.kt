/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Saul Henriquez <henriquez.saul@gmail.com>
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

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowLeft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewWithActionBar(
    title: String,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 18.sp,
    subTitle: String = "",
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    showBackButton: Boolean = true,
    /**
     * Draws the header as a large collapsing title.
     *
     * Material You gives a top-level destination a large title that shrinks as
     * the content scrolls under it; a fixed 18sp row is what an app looks like
     * before anyone has themed it. Used for the destinations a user lands on
     * rather than passes through.
     */
    largeTitle: Boolean = false,
    actions: @Composable (RowScope.() -> Unit) = {},
    /**
     * What the navigation slot shows, when it is not going back.
     *
     * A screen holding a selection puts a close cross here instead of the back
     * arrow: the button dismisses the selection rather than the screen, and an
     * arrow promising to leave would be lying about where the tap goes.
     */
    backIcon: ImageVector = Phosphor.ArrowLeft,
    backDescription: Int = R.string.go_back,
    onBackAction: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val appBarState = rememberTopAppBarState()
    val scrollBehavior =
        if (largeTitle) TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
        else enterAlwaysScrollBehavior(appBarState)

    val titleContent: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        // In large mode the app bar supplies the type scale;
                        // forcing 18sp there would defeat the point of it.
                        Text(
                            text = title,
                            style = if (largeTitle) MaterialTheme.typography.headlineMedium
                            else MaterialTheme.typography.titleMedium,
                            fontSize = if (largeTitle) TextUnit.Unspecified else titleSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subTitle.isNotEmpty()) {
                            Text(
                                text = subTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
    }

    val navContent: @Composable () -> Unit = {
                    if (showBackButton) {
                        val backDispatcher =
                            LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                        IconButton(
                            onClick = {
                                onBackAction?.invoke()
                                    ?: backDispatcher?.onBackPressed()
                            }
                        ) {
                            Icon(
                                imageVector = backIcon,
                                contentDescription = stringResource(id = backDescription),
                            )
                        }
                    }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (largeTitle) {
                LargeTopAppBar(
                    title = { titleContent() },
                    navigationIcon = { navContent() },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                TopAppBar(
                    title = { titleContent() },
                    navigationIcon = { navContent() },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = FabPosition.Center,
        content = content,
    )
}

@Composable
fun RoundButton(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    description: String = "",
    onClick: () -> Unit,
) {
    IconButton(
        modifier = modifier.size(size = size),
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
        )
    }
}
