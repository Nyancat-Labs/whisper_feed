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

package com.saulhdev.feeder.ui.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import com.saulhdev.feeder.ui.components.ActionPreference
import com.saulhdev.feeder.ui.components.PreferenceIcon
import com.saulhdev.feeder.ui.components.BasePreference
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.saulhdev.feeder.R
import java.util.Date
import java.text.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.ui.components.PreferenceGroup
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import org.koin.compose.koinInject

@Composable
fun PreferencesPage(
    prefs: FeedPreferences = koinInject(),
) {
    val navController = LocalNavController.current
    val backupStoppedAt by prefs.backupStoppedAt.get().collectAsState(initial = 0L)
    val title = stringResource(id = R.string.title_settings)

    // Harmonic's shape, by the owner's choice (24 October 2026): a short list
    // of what Settings holds, each row saying what is inside it, each opening
    // a page of its own. It was one page of thirty-six rows. The rows, and the
    // settings on each page, are in SettingsSection.
    val sections = SettingsSection.topLevel
    val rows = sections.size + 1 // and About

    ViewWithActionBar(
        title = title,
        largeTitle = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding(),
                ),
            // The last row clear of the gesture bar; it ended underneath it
            // (design audit, 21 October).
            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Above everything, because it is the one thing on this screen
            // that is wrong rather than merely adjustable — and because the
            // backup screen already said it, to nobody, for however long it
            // took somebody to wander in there.
            if (backupStoppedAt > 0L) {
                item(key = "backup-stopped") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.large,
                        onClick = { navController.navigate(NavRoute.Backup) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.backup_stopped_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.backup_stopped_body,
                                    DateFormat.getDateInstance().format(Date(backupStoppedAt)),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item(key = "sections") {
                PreferenceGroup {
                    sections.forEachIndexed { i, section ->
                        BasePreference(
                            titleId = section.title,
                            summaryId = section.summary,
                            index = i,
                            groupSize = rows,
                            startWidget = { PreferenceIcon(icon = section.icon, contentDescription = null) },
                            onClick = { navController.navigate(NavRoute.SettingsSection(section.name)) },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    // Its own row, as in Harmonic: the credits, licences and
                    // ways to get in touch are a page's worth on their own.
                    ActionPreference(pref = prefs.about, index = sections.size, groupSize = rows)
                }
            }
        }
    }
}
