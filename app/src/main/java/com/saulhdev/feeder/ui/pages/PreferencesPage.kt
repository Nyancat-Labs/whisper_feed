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

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.saulhdev.feeder.utils.openOverlaySettings
import com.saulhdev.feeder.utils.canGrantOverlay
import com.saulhdev.feeder.BuildConfig
import com.saulhdev.feeder.R
import java.util.Date
import java.text.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.StringSelectionPref
import com.saulhdev.feeder.manager.models.scheduleFullTextParse
import com.saulhdev.feeder.ui.components.PreferenceGroup
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.components.dialog.BaseDialog
import com.saulhdev.feeder.ui.components.dialog.StringSelectionPrefDialogUI
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import com.saulhdev.feeder.NeoApp
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.BackgroundDataHint
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.ui.icons.phosphor.Power
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.saulhdev.feeder.data.content.asState

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PreferencesPage(
    prefs: FeedPreferences = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val backupStoppedAt by prefs.backupStoppedAt.get().collectAsState(initial = 0L)
    val title = stringResource(id = R.string.title_settings)

    // Grouped by what the reader is trying to do, because one list of sixteen
    // rows called "Service" is a list nobody reads to the end of. Every group
    // below fits on a screen and can be named in one word.

    /** How much is fetched, how often, and over what. */
    val fetchingPrefs = listOf(
        prefs.syncFrequency,
        prefs.syncRange,
        prefs.itemsPerFeed,
        prefs.syncOnlyOnWifi,
        prefs.syncOnlyWhenCharging,
        prefs.fullTextForAllFeeds,
        prefs.fullTextOnMobile,
    )

    /**
     * What appears in the feed, and in what order.
     *
     * Absorbed the one-row "Filters" group and the two-row "Glance row"
     * group. A heading over a single preference is a heading that only makes
     * the list longer, and both were answering the same question this one
     * does: what shows up when you open Whisper.
     */
    val feedPrefs = listOf(
        prefs.breakingNews,
        prefs.dimSkipped,
        prefs.removeDuplicates,
        prefs.blockedWords,
        prefs.glanceEnabled,
        prefs.glancePlaceName,
        prefs.learned,
        prefs.statistics,
    )

    /** What happens while reading it. */
    val readingPrefs = listOf(
        prefs.articleOpenMode,
        prefs.readVisibility,
        prefs.markReadOnScroll,
        prefs.volumeKeyScroll,
    )

    val themePrefs = listOf(
        prefs.feedLayout,
        prefs.overlayTheme,
        prefs.pureBlack,
        prefs.appFont,
        prefs.dynamicColor,
        prefs.overlayTransparency,
    )

    /**
     * Adding and finding sources.
     *
     * The two repair tools — feeds that stopped working, and feeds still on
     * http — used to sit here. They are maintenance on a list rather than
     * preferences about it, and the moment you want either is while looking
     * at that list, so they are in Data sources' own menu now.
     */
    val sourcePrefs = listOf(
        prefs.sources,
        prefs.feedLibrary,
        prefs.importBookmarks,
    )

    /**
     * Where the reader's data comes from and goes to.
     *
     * A sync account and a backup folder were in separate groups, one of them
     * alone. They are the same question asked twice.
     */
    val dataPrefs = listOf(
        prefs.account,
        prefs.backupFolder,
    )

    // The test notice sits under the debugging switch, and only while it is on.
    val debugging by prefs.debugging.asState()

    /** Everything that explains the app rather than changing it. */
    val helpPrefs = listOfNotNull(
        prefs.showTour,
        prefs.launcherSetup,
        prefs.about,
        prefs.reportProblem,
        prefs.exportDiagnostics,
        // Last row of the last group. It was defined and in no list at all, so
        // the switch existed and could not be reached from anywhere.
        prefs.debugging,
        // Only in the debug and preview builds: it is for checking how the
        // notice looks, not something a reader of a store build needs.
        prefs.testSyncNotice.takeIf { debugging && BuildConfig.DEV_TOOLS },
    )

    // Turning the global switch on should start downloading now, not at the
    // next scheduled sync — the setting reads as an instruction, not a plan.
    val fullTextForAll by prefs.fullTextForAllFeeds.asState()
    val syncWifiOnly by prefs.syncOnlyOnWifi.asState()
    var wasFullTextForAll by remember { mutableStateOf(fullTextForAll) }
    LaunchedEffect(fullTextForAll) {
        if (fullTextForAll && !wasFullTextForAll) scheduleFullTextParse()
        wasFullTextForAll = fullTextForAll
    }

    val openDialog = remember { mutableStateOf(false) }
    var dialogPref by remember { mutableStateOf<Any?>(null) }
    val onPrefDialog = { pref: Any ->
        dialogPref = pref
        openDialog.value = true
    }

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

            item(key = R.string.title_sources) {
                PreferenceGroup(
                    stringResource(id = R.string.title_sources),
                    prefs = sourcePrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_cat_fetching) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_fetching),
                    prefs = fetchingPrefs,
                    onPrefDialog = onPrefDialog
                )
                // Under the switches it overrules. See BackgroundDataHint.
                BackgroundDataHint(
                    wifiOnly = syncWifiOnly,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = R.string.pref_cat_feed) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_feed),
                    prefs = feedPrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_cat_reading) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_reading),
                    prefs = readingPrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_cat_overlay) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_overlay),
                    prefs = themePrefs,
                    onPrefDialog = onPrefDialog
                )

                if (!Settings.canDrawOverlays(context)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (canGrantOverlay(context)) {
                                Text(text = stringResource(R.string.draw_permission_required))
                                Spacer(modifier = Modifier.height(12.dp))
                                ActionButton(
                                    text = stringResource(R.string.go_to_settings),
                                    icon = Phosphor.GearSix,
                                    onClick = { openOverlaySettings(context) },
                                )
                            } else {
                                Text(text = stringResource(R.string.overlay_not_on_go))
                            }
                        }
                    }
                }
            }
            item(key = R.string.pref_cat_data) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_data),
                    prefs = dataPrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_cat_help) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_help),
                    prefs = helpPrefs,
                    onPrefDialog = onPrefDialog
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (openDialog.value) {
            BaseDialog(openDialogCustom = openDialog) {
                when (dialogPref) {
                    is StringSelectionPref -> StringSelectionPrefDialogUI(
                        pref = dialogPref as StringSelectionPref,
                        openDialogCustom = openDialog
                    )
                }
            }
        }
    }
}
