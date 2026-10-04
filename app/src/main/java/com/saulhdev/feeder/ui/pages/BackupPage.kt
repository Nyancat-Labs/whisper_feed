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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import com.saulhdev.feeder.ui.icons.phosphor.Info
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.manager.backup.BackupStore
import com.saulhdev.feeder.manager.backup.BackupWorker
import com.saulhdev.feeder.manager.backup.backupFolderIsOnDevice
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.SwitchPreference
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowDown
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowUp
import com.saulhdev.feeder.ui.icons.phosphor.Nut
import com.saulhdev.feeder.ui.icons.phosphor.PaintRoller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

/**
 * Choosing somewhere to keep a copy of the subscription list.
 *
 * The screen leads with why rather than how, because "back up your feeds" only
 * lands once somebody has noticed that the list exists in one place and took
 * years to build. Everything else here is one tap.
 */
@Composable
fun BackupPage(
    prefs: FeedPreferences = koinInject(),
    store: BackupStore = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val folder by prefs.backupFolder.get().collectAsState(initial = "")
    val lastRun by prefs.backupLastRun.get().collectAsState(initial = "")
    // A snackbar, not a row in the list. The result used to render at a fixed
    // position below the restore buttons, so backing up — a button near the
    // top of the screen — put its confirmation somewhere the reader was not
    // looking and often could not see without scrolling. An action's outcome
    // belongs where the action was.
    val snackbarHostState = remember { SnackbarHostState() }
    fun say(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text, withDismissAction = true) }
    }

    val writable = remember(folder) {
        folder.isNotEmpty() && store.canWrite(folder.toUri())
    }

    // On IO: everything in here writes to a DataStore or to a document, and
    // the launcher callback arrives on the main thread.
    fun useFolder(uri: Uri) = scope.launch(Dispatchers.IO) {
        prefs.backupFolder.setValue(uri.toString())
        BackupWorker.schedule(context, uri.toString())
        // Written immediately rather than waiting for the first scheduled
        // run: somebody who has just chosen a folder wants to see a file
        // appear in it, and a day of nothing happening reads as a setting
        // that did not take.
        val outcome = store.backUp(uri)
        report(outcome, context) { say(it) }
        stamp(outcome, prefs)
    }

    // A chosen folder that already holds a backup, waiting on the reader.
    //
    // 4 October: backed up, uninstalled, installed the new build, chose the
    // same folder - and the backup written a minute earlier was replaced by
    // the empty app's, because choosing a folder wrote to it at once. A
    // folder with a backup in it is now asked about first: restore from it,
    // or replace it, which keeps the old copy beside the new one.
    var existing by remember { mutableStateOf<Uri?>(null) }

    val chooseFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        store.remember(uri)
        scope.launch {
            if (withContext(Dispatchers.IO) { store.hasBackup(uri) }) {
                existing = uri
            } else {
                useFolder(uri)
            }
        }
    }

    val chooseSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { report(store.restoreSettings(uri), context) { say(it) } }
    }

    val chooseBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { report(store.restore(uri), context) { say(it) } }
    }

    existing?.let { uri ->
        AlertDialog(
            onDismissRequest = { existing = null },
            title = { Text(stringResource(R.string.backup_exists_title)) },
            text = { Text(stringResource(R.string.backup_exists_body)) },
            confirmButton = {
                TextButton(onClick = {
                    existing = null
                    scope.launch(Dispatchers.IO) {
                        // Restored first, then remembered as the destination,
                        // so the schedule cannot write before the restore has
                        // read. The settings restored never include the
                        // folder; see SettingsBackup.NOT_PORTABLE.
                        val outcome = store.restoreFolder(uri)
                        report(outcome, context) { say(it) }
                        prefs.backupFolder.setValue(uri.toString())
                        BackupWorker.schedule(context, uri.toString())
                    }
                }) { Text(stringResource(R.string.backup_exists_restore)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { existing = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(onClick = {
                        existing = null
                        useFolder(uri)
                    }) { Text(stringResource(R.string.backup_exists_replace)) }
                }
            },
        )
    }

    ViewWithActionBar(
        title = stringResource(R.string.pref_backup),
        largeTitle = true,
        showBackButton = true,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    text = stringResource(R.string.backup_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // What a backup actually contains, said before any button rather
            // than in a footnote below the Android section — which is where
            // this used to sit, so the screen announced itself as being about
            // subscriptions and only mentioned the settings file at the very
            // bottom, after everything it could be used for.
            item {
                Text(
                    text = stringResource(R.string.backup_what),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Said before the button that does it, not after, and said in the
            // app rather than only in a privacy policy nobody opens. If this
            // is the one thing that sends a reader's data off their phone,
            // the screen where they turn it on is where they should read it.
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Phosphor.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                // Its own string rather than the screen's
                                // title, which it used to borrow. That read
                                // as a status while the title was a sentence
                                // about subscriptions; as a section name it
                                // would say nothing about what is happening.
                                text = stringResource(
                                    when {
                                        folder.isEmpty() -> R.string.backup_off
                                        backupFolderIsOnDevice(folder) -> R.string.backup_on_device
                                        else -> R.string.backup_on_cloud
                                    }
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        // Only while nothing is on: it says no folder is chosen,
                        // which stopped being true the moment one was.
                        if (folder.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.backup_only_time),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = when {
                        folder.isEmpty() -> stringResource(R.string.backup_none)
                        !writable -> stringResource(R.string.backup_lost)
                        lastRun.isEmpty() -> stringResource(R.string.backup_never)
                        else -> stringResource(
                            R.string.backup_last,
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(lastRun.toLongOrNull() ?: 0L)),
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (folder.isNotEmpty() && !writable) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.backup_heading_where),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                ActionButton(
                    text = stringResource(
                        if (folder.isEmpty()) R.string.backup_choose else R.string.backup_change
                    ),
                    icon = Phosphor.PaintRoller,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    onClick = { chooseFolder.launch(null) },
                )
            }

            if (folder.isNotEmpty() && writable) {
                item {
                    OutlinedActionButton(
                        text = stringResource(R.string.backup_now),
                        icon = Phosphor.CloudArrowUp,
                        modifier = Modifier.fillMaxWidth(),
                        positive = true,
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val outcome = store.backUp(folder.toUri())
                                report(outcome, context) { say(it) }
                                stamp(outcome, prefs)
                            }
                        },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.backup_heading_restore),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            item {
                Text(
                    text = stringResource(R.string.backup_restore_what),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                OutlinedActionButton(
                    text = stringResource(R.string.backup_restore_settings),
                    icon = Phosphor.Nut,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    // Chosen separately from the feeds on purpose: adding
                    // somebody's subscriptions to a new phone is additive and
                    // safe, and overwriting every setting on a phone already
                    // arranged the way they like it is not.
                    onClick = { chooseSettings.launch(arrayOf("application/json", "*/*")) },
                )
            }

            item {
                OutlinedActionButton(
                    text = stringResource(R.string.backup_restore),
                    icon = Phosphor.CloudArrowDown,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    // Any OPML, not only one Whisper wrote: somebody arriving
                    // from another reader has an export of their own, and this
                    // is the same file.
                    onClick = { chooseBackup.launch(arrayOf("*/*")) },
                )
            }

            // Android's own backup, asked separately from the folder above
            // because it is a different mechanism with a different answer.
            // Placed after it rather than before: the folder is the one this
            // app controls end to end, and is what most people should use.
            item {
                Text(
                    text = stringResource(R.string.backup_platform_heading),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.backup_platform_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SwitchPreference(pref = prefs.platformBackupDevice, index = 1, groupSize = 2)
            }
            item {
                SwitchPreference(pref = prefs.platformBackupCloud, index = 2, groupSize = 2)
            }

        }
    }
}

/** Turns an outcome into the one sentence the screen shows. */
private fun report(
    result: BackupStore.Result,
    context: android.content.Context,
    show: (String) -> Unit,
) {
    show(
        when (result) {
            // Both names, because a backup is two files and naming only the
            // OPML left readers of this screen believing their settings were
            // not included — the paragraph above says two files, and the
            // confirmation said one.
            is BackupStore.Result.Written ->
                if (result.settingsName != null) {
                    context.getString(R.string.backup_written, result.name, result.settingsName)
                } else {
                    context.getString(R.string.backup_written_sources_only, result.name)
                }

            is BackupStore.Result.SettingsRestored ->
                context.resources.getQuantityString(R.plurals.backup_settings_restored, result.count, result.count)

            is BackupStore.Result.Restored ->
                if (result.feeds > 0) {
                    context.resources.getQuantityString(R.plurals.backup_restored, result.feeds, result.feeds)
                } else {
                    context.getString(R.string.backup_restored_none)
                }

            is BackupStore.Result.FolderRestored -> context.getString(
                R.string.backup_folder_restored,
                context.resources.getQuantityString(R.plurals.backup_folder_sources, result.feeds, result.feeds),
                context.resources.getQuantityString(R.plurals.backup_folder_settings, result.settings, result.settings),
            )

            BackupStore.Result.NothingFound -> context.getString(R.string.backup_nothing_found)
            BackupStore.Result.NoDestination -> context.getString(R.string.backup_lost)
            is BackupStore.Result.Failed -> context.getString(R.string.backup_failed)
        }
    )
}

/**
 * Records a backup that actually happened.
 *
 * Both routes to a backup — choosing a folder, and the button — used to stamp
 * the clock unconditionally, so a failure still left "Last backed up a minute
 * ago" on screen: the one line a reader checks to find out whether their
 * backup is current, answering yes when it was no. They were two copies of
 * the same three lines, which is how they came to disagree with the scheduled
 * worker and then with each other, so there is one copy now.
 *
 * The time comes from when the file was written rather than from the clock
 * afterwards, and a successful write clears any recorded stoppage — the
 * warning on the settings screen has to end when the problem does, or it
 * teaches people to ignore warnings.
 */
private suspend fun stamp(outcome: BackupStore.Result, prefs: FeedPreferences) {
    if (outcome !is BackupStore.Result.Written) return
    prefs.backupLastRun.setValue(outcome.at.toString())
    if (prefs.backupStoppedAt.getValue() != 0L) prefs.backupStoppedAt.setValue(0L)
}
