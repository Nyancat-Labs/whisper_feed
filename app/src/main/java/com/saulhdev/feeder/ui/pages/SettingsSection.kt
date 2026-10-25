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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.BuildConfig
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.StringSelectionPref
import com.saulhdev.feeder.data.content.asState
import com.saulhdev.feeder.manager.models.scheduleFullTextParse
import com.saulhdev.feeder.ui.components.BackgroundDataHint
import com.saulhdev.feeder.ui.components.PreferenceBuilder
import com.saulhdev.feeder.ui.components.PreferenceGroup
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.components.dialog.BaseDialog
import com.saulhdev.feeder.ui.components.dialog.StringSelectionPrefDialogUI
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.BookOpenUser
import com.saulhdev.feeder.ui.icons.phosphor.Bug
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowDown
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowUp
import com.saulhdev.feeder.ui.icons.phosphor.Hash
import com.saulhdev.feeder.ui.icons.phosphor.ListDashes
import com.saulhdev.feeder.ui.icons.phosphor.Megaphone
import com.saulhdev.feeder.ui.icons.phosphor.PaintRoller
import org.koin.compose.koinInject

/**
 * The pages Settings opens onto, in the order the top list shows them.
 *
 * Settings was one page of thirty-six rows under seven headings. The owner
 * preferred Harmonic's shape: a short top list, each row saying what is
 * inside it, each opening its own page (decided 24 October 2026, About as a
 * row of its own). [DIAGNOSTICS] is not in the top list; Help opens it.
 */
enum class SettingsSection(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val icon: ImageVector,
) {
    // Most often used first, as Android's and Material's settings guidance
    // both ask, Appearance leading by convention; the set-once pages lower
    // (owner, 25 October 2026).
    APPEARANCE(R.string.pref_cat_overlay, R.string.settings_appearance_summary, Phosphor.PaintRoller),
    SOURCES(R.string.settings_sources, R.string.settings_sources_summary, Phosphor.Hash),
    FEED(R.string.pref_cat_feed, R.string.settings_feed_summary, Phosphor.ListDashes),
    READING(R.string.pref_cat_reading, R.string.settings_reading_summary, Phosphor.BookOpenUser),
    FETCHING(R.string.pref_cat_fetching, R.string.settings_fetching_summary, Phosphor.CloudArrowDown),
    DATA(R.string.pref_cat_data, R.string.settings_data_summary, Phosphor.CloudArrowUp),
    HELP(R.string.pref_cat_help, R.string.settings_help_summary, Phosphor.Megaphone),
    DIAGNOSTICS(R.string.report_problem, R.string.settings_diagnostics_summary, Phosphor.Bug);

    companion object {
        /** The rows of the top list, About apart; see PreferencesPage. */
        val topLevel = entries.filter { it != DIAGNOSTICS }

        fun of(name: String): SettingsSection = entries.firstOrNull { it.name == name } ?: APPEARANCE
    }
}

/**
 * What each page holds. The same preferences as before, in the same order
 * within each group; only the page they live on has changed.
 */
fun settingsRows(prefs: FeedPreferences, section: SettingsSection, debugging: Boolean): List<Any> =
    when (section) {
        SettingsSection.APPEARANCE -> listOf(
            prefs.feedLayout,
            prefs.showSummaries,
            prefs.timeFormat,
            prefs.overlayTheme,
            prefs.pureBlack,
            prefs.appFont,
            prefs.dynamicColor,
            prefs.overlayTransparency,
        )
        SettingsSection.FEED -> listOf(
            prefs.breakingNews,
            prefs.dimSkipped,
            prefs.removeDuplicates,
            prefs.blockedWords,
            prefs.glanceEnabled,
            prefs.glancePlaceName,
            prefs.learned,
            prefs.statistics,
        )
        SettingsSection.READING -> listOf(
            prefs.articleOpenMode,
            prefs.readerTextSize,
            prefs.readVisibility,
            prefs.markReadOnScroll,
            prefs.volumeKeyScroll,
        )
        SettingsSection.SOURCES -> listOf(
            prefs.sources,
            prefs.feedLibrary,
            prefs.importBookmarks,
        )
        SettingsSection.FETCHING -> listOf(
            prefs.syncFrequency,
            prefs.syncRange,
            prefs.itemsPerFeed,
            prefs.syncOnlyOnWifi,
            prefs.syncOnlyWhenCharging,
            prefs.fullTextForAllFeeds,
            prefs.fullTextOnMobile,
        )
        SettingsSection.DATA -> listOf(
            prefs.account,
            prefs.backupFolder,
        )
        // The reader's rows; the diagnostics are one row away, on their own
        // page, so the three of them stop crowding the list (owner, 21 October).
        SettingsSection.HELP -> listOf(
            prefs.showTour,
            prefs.launcherSetup,
            prefs.reportProblemPage,
        )
        SettingsSection.DIAGNOSTICS -> listOfNotNull(
            prefs.reportProblem,
            prefs.exportDiagnostics,
            prefs.debugging,
            // Only in the debug and preview builds, and only while detailed
            // logs are on: for checking how the notice looks.
            prefs.testSyncNotice.takeIf { debugging && BuildConfig.DEV_TOOLS },
        )
    }

/** One page of Settings. */
@Composable
fun SettingsSectionPage(section: SettingsSection, prefs: FeedPreferences = koinInject()) {
    val debugging by prefs.debugging.asState()
    val rows = settingsRows(prefs, section, debugging)

    // Turning the global switch on should start downloading now, not at the
    // next scheduled sync: the setting reads as an instruction, not a plan.
    if (section == SettingsSection.FETCHING) {
        val fullTextForAll by prefs.fullTextForAllFeeds.asState()
        var wasFullTextForAll by remember { mutableStateOf(fullTextForAll) }
        LaunchedEffect(fullTextForAll) {
            if (fullTextForAll && !wasFullTextForAll) scheduleFullTextParse()
            wasFullTextForAll = fullTextForAll
        }
    }

    val openDialog = remember { mutableStateOf(false) }
    var dialogPref by remember { mutableStateOf<Any?>(null) }
    val onPrefDialog = { pref: Any ->
        dialogPref = pref
        openDialog.value = true
    }

    ViewWithActionBar(
        title = stringResource(section.title),
        largeTitle = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = paddingValues.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = section.name) {
                PreferenceGroup {
                    rows.forEachIndexed { i, pref ->
                        PreferenceBuilder(pref, onPrefDialog, i, rows.size)
                        if (i < rows.size - 1) Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                if (section == SettingsSection.FETCHING) {
                    // Under the switches it overrules. See BackgroundDataHint.
                    val syncWifiOnly by prefs.syncOnlyOnWifi.asState()
                    BackgroundDataHint(wifiOnly = syncWifiOnly, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (openDialog.value) {
            BaseDialog(openDialogCustom = openDialog) {
                when (dialogPref) {
                    is StringSelectionPref -> StringSelectionPrefDialogUI(
                        pref = dialogPref as StringSelectionPref,
                        openDialogCustom = openDialog,
                    )
                }
            }
        }
    }
}
