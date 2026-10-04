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
package com.saulhdev.feeder.ui.pages

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.utils.openOverlaySettings
import com.saulhdev.feeder.utils.canGrantOverlay
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.service.LauncherLink
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.ui.icons.phosphor.Info

/**
 * How to put Whisper on the launcher's left-most page.
 *
 * It exists as a screen rather than a line in the README because the people
 * who need it are holding the phone, not reading GitHub — and because the
 * steps are genuinely obscure. Lawnchair keeps a hardcoded list of packages
 * allowed to be the feed, `com.nyancatlabs.whisper` is not on it, and the way past
 * that is a hidden command typed into the app drawer's search box. Nobody
 * guesses that.
 *
 * The screen is careful about one thing: it is an offer, not a missed step.
 * Most people installing this do not have Lawnchair and are not going to; a
 * reader is what they came for, and the launcher page is a bonus.
 */
@Composable
fun LauncherPage() {
    val context = LocalContext.current
    val launcher = remember { LauncherLink.installedLauncher(context) }
    val connected by LauncherLink.bound.collectAsState()
    val canDrawOverlays = remember { Settings.canDrawOverlays(context) }

    ViewWithActionBar(
        title = stringResource(R.string.pref_launcher),
        largeTitle = true,
        showBackButton = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.launcher_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // Said only when it is true. A launcher that bound before this
            // process was restarted will bind again the next time somebody
            // swipes, and claiming "not connected" in the meantime would send
            // them back through four steps they have already done.
            if (connected || LauncherLink.everBound) {
                item { StatusCard(stringResource(R.string.launcher_connected)) }
            }

            if (launcher == null) {
                item {
                    Text(
                        text = stringResource(R.string.launcher_none_installed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexedSteps(
                    listOf(
                        R.string.launcher_step_debug,
                        R.string.launcher_step_menu,
                        R.string.launcher_step_whitelist,
                        R.string.launcher_step_pick,
                    )
                )

                item {
                    ActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.launcher_open),
                        icon = Phosphor.GearSix,
                        onClick = {
                            LauncherLink.settingsIntent(context, launcher)?.let {
                                runCatching { context.startActivity(it) }
                            }
                        },
                    )
                }
            }

            // Separate from the four steps because it is a different problem
            // with a different fix, and because it bites people who got the
            // launcher part right: the page renders, and tapping an article
            // does nothing at all.
            item {
                Text(
                    text = stringResource(R.string.launcher_permission_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            item {
                Text(
                    text = stringResource(
                        if (canDrawOverlays) R.string.launcher_permission_granted
                        else R.string.launcher_permission_body
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!canDrawOverlays && canGrantOverlay(context)) {
                item {
                    OutlinedActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.launcher_permission_open),
                        icon = Phosphor.Info,
                        onClick = { openOverlaySettings(context) },
                    )
                }
            } else if (!canDrawOverlays) {
                item {
                    Text(
                        text = stringResource(R.string.overlay_not_on_go),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The numbered steps, which are the whole point of the screen. */
private fun LazyListScope.itemsIndexedSteps(steps: List<Int>) {
    itemsIndexed(steps) { index, stringId ->
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(stringId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatusCard(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
