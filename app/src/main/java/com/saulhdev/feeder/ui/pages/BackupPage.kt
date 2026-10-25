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
import com.saulhdev.feeder.ui.icons.phosphor.UploadSimple
import com.saulhdev.feeder.ui.icons.phosphor.DownloadSimple
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.Archive
import com.saulhdev.feeder.ui.icons.phosphor.Folder
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.annotation.StringRes
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

    // Restore: what to bring back is ticked first, then the folder is
    // chosen. It was three buttons, one per file, each opening a file
    // picker; people come here with a situation (a new phone), not a file
    // type (owner, 25 October).
    var restoring by remember { mutableStateOf(false) }
    var restoreParts by remember {
        mutableStateOf(setOf(BackupStore.Part.SOURCES, BackupStore.Part.SAVED))
    }

    val chooseRestoreFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val parts = restoreParts
        scope.launch { report(store.restoreFolder(uri, parts), context) { say(it) } }
    }

    // Any OPML, not only one Whisper wrote: somebody arriving from another
    // reader has an export of their own, and it is the same format.
    val importSources = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { report(store.restore(uri), context) { say(it) } }
    }

    val exportSources = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupStore.MIME_TYPE)
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { report(store.exportSources(uri), context) { say(it) } }
    }

    if (restoring) {
        RestoreDialog(
            parts = restoreParts,
            onPartsChange = { restoreParts = it },
            onDismiss = { restoring = false },
            onConfirm = {
                restoring = false
                chooseRestoreFolder.launch(null)
            },
        )
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

            item { SectionHeading(R.string.backup_heading_where) }

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
                ActionButton(
                    text = stringResource(
                        if (folder.isEmpty()) R.string.backup_choose else R.string.backup_change
                    ),
                    icon = Phosphor.Folder,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    onClick = { chooseFolder.launch(null) },
                )
            }

            if (folder.isNotEmpty() && writable) {
                item {
                    OutlinedActionButton(
                        text = stringResource(R.string.backup_now),
                        icon = Phosphor.Archive,
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

            item { SectionHeading(R.string.backup_heading_restore) }
            item { SectionText(R.string.backup_restore_what) }
            item {
                OutlinedActionButton(
                    text = stringResource(R.string.backup_restore_button),
                    icon = Phosphor.ArrowCounterClockwise,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    onClick = { restoring = true },
                )
            }

            // Not a backup at all: the same OPML file, to or from another
            // reader. Kept apart so the restore above stays about this app.
            item { SectionHeading(R.string.backup_heading_other_apps) }
            item { SectionText(R.string.backup_other_apps_body) }
            item {
                OutlinedActionButton(
                    text = stringResource(R.string.backup_import_other),
                    icon = Phosphor.DownloadSimple,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    onClick = { importSources.launch(arrayOf("*/*")) },
                )
            }
            item {
                OutlinedActionButton(
                    text = stringResource(R.string.backup_export_other),
                    icon = Phosphor.UploadSimple,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    onClick = { exportSources.launch(BackupStore.FILE_NAME) },
                )
            }

            // Android's own backup, asked separately from the folder above
            // because it is a different mechanism with a different answer.
            // Placed after it rather than before: the folder is the one this
            // app controls end to end, and is what most people should use.
            item { SectionHeading(R.string.backup_platform_heading) }
            item { SectionText(R.string.backup_platform_body) }
            item {
                SwitchPreference(pref = prefs.platformBackupDevice, index = 1, groupSize = 2)
            }
            item {
                SwitchPreference(pref = prefs.platformBackupCloud, index = 2, groupSize = 2)
            }

        }
    }
}

@Composable
private fun SectionHeading(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 24.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SectionText(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What to bring back, ticked before the folder is chosen. */
@Composable
private fun RestoreDialog(
    parts: Set<BackupStore.Part>,
    onPartsChange: (Set<BackupStore.Part>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column {
                listOf(
                    Triple(BackupStore.Part.SOURCES, R.string.backup_part_sources, null),
                    Triple(BackupStore.Part.SAVED, R.string.backup_part_saved, null),
                    // Unticked to begin with: the other two add, this one
                    // replaces how the phone is set up.
                    Triple(BackupStore.Part.SETTINGS, R.string.backup_part_settings, R.string.backup_part_settings_note),
                ).forEach { (part, label, note) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = part in parts,
                                role = Role.Checkbox,
                                onValueChange = { on -> onPartsChange(if (on) parts + part else parts - part) },
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        Checkbox(checked = part in parts, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                            if (note != null) Text(
                                stringResource(note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = parts.isNotEmpty()) {
                Text(stringResource(R.string.backup_restore_choose))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
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
                if (result.settingsName != null && !result.savedWritten) {
                    context.getString(R.string.backup_written_no_saved)
                } else if (result.settingsName != null) {
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

            is BackupStore.Result.SavedRestored ->
                if (result.count > 0) {
                    context.resources.getQuantityString(R.plurals.backup_saved_restored, result.count, result.count)
                } else {
                    context.getString(R.string.backup_saved_restored_none)
                }

            // Only what was asked for, and saved articles only when there
            // were some, so a backup made before they were included reads as
            // it always did.
            is BackupStore.Result.FolderRestored -> {
                val res = context.resources
                val said = buildList {
                    if (BackupStore.Part.SOURCES in result.parts) {
                        add(res.getQuantityString(R.plurals.backup_folder_sources, result.feeds, result.feeds))
                    }
                    if (BackupStore.Part.SETTINGS in result.parts) {
                        add(res.getQuantityString(R.plurals.backup_folder_settings, result.settings, result.settings))
                    }
                    if (BackupStore.Part.SAVED in result.parts && (result.saved > 0 || result.parts.size == 1)) {
                        add(res.getQuantityString(R.plurals.backup_folder_saved, result.saved, result.saved))
                    }
                }
                when (said.size) {
                    1 -> context.getString(R.string.backup_folder_restored_one, said[0])
                    2 -> context.getString(R.string.backup_folder_restored, said[0], said[1])
                    else -> context.getString(R.string.backup_folder_restored_saved, said[0], said[1], said[2])
                }
            }

            is BackupStore.Result.Exported ->
                context.resources.getQuantityString(R.plurals.backup_exported, result.feeds, result.feeds)

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
