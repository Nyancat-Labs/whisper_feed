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
package com.saulhdev.feeder.manager.backup

import com.saulhdev.feeder.utils.SyncLog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.documentfile.provider.DocumentFile
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.models.OPMLParser
import com.saulhdev.feeder.manager.models.SourceToRoom
import com.saulhdev.feeder.manager.models.writeOutputStream
import com.saulhdev.feeder.manager.sync.requestFeedSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeping a copy of the subscription list somewhere that is not this phone.
 *
 * **Through the Storage Access Framework, not the Google Drive API**, and the
 * difference is worth the paragraph. Talking to Drive directly means an OAuth
 * client registered against this app's package and signing certificate, which
 * is a step the project cannot take for its users and which would have to be
 * repeated for the debug build, the release build, and anyone forking this.
 * Picking a folder needs none of that: Drive ships a `DocumentsProvider`, the
 * system's own picker offers it alongside everything else, and a persistable
 * permission means the app can keep writing there afterwards without asking
 * again.
 *
 * It is also simply better. Dropbox, OneDrive, Nextcloud and an SD card all
 * appear in the same picker, so this is not a Google feature that happens to
 * be spelled generically — it works with wherever the reader already keeps
 * things. And there is no Google dependency to declare, which matters for a
 * build that goes to F-Droid.
 *
 * What it writes is exactly what the existing export writes: the same OPML,
 * from the same code. This is a destination, not a second format.
 */
class BackupStore(
    private val context: Context,
    private val sources: SourcesRepository,
    private val dataStore: DataStore<Preferences>,
) {

    /** What happened, in terms the settings screen can say out loud. */
    sealed interface Result {
        /**
         * A backup that was written, and what it consists of.
         *
         * Two names rather than one, because a backup is two files and saying
         * only the OPML's name understates what left the phone. [settingsName]
         * is null when the settings file could not be written — the
         * subscriptions are still safe, and the screen should say which half
         * it got rather than claim both.
         */
        data class Written(
            val name: String,
            val settingsName: String?,
            val at: Long,
        ) : Result
        data class Restored(val feeds: Int) : Result
        data class SettingsRestored(val count: Int) : Result
        data class FolderRestored(val feeds: Int, val settings: Int) : Result
        data object NothingFound : Result
        data object NoDestination : Result
        data class Failed(val cause: Throwable?) : Result
    }

    /**
     * Writes the current subscription list to the chosen folder.
     *
     * One file, overwritten, rather than a dated series. A backup that
     * accumulates is a folder the reader has to tidy, and the second-newest
     * copy of a subscription list has never once been the one somebody wanted.
     */
    suspend fun backUp(treeUri: Uri?): Result = withContext(Dispatchers.IO) {
        if (treeUri == null) return@withContext Result.NoDestination
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.NoDestination
            if (!tree.canWrite()) return@withContext Result.NoDestination

            // Replaced rather than appended to: DocumentFile has no truncate,
            // so an existing shorter file would otherwise keep the tail of the
            // longer one it replaced and produce invalid XML. The one it
            // replaces is kept beside it; see makeRoomFor.
            makeRoomFor(DocumentBackupFolder(tree), FILE_NAME)
            val file = tree.createFile(MIME_TYPE, FILE_NAME)
                ?: return@withContext Result.Failed(null)

            val byTag = sources.getAllSources()
                .groupBy { it.tags.firstOrNull().orEmpty() }

            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                writeOutputStream(out, byTag)
            } ?: return@withContext Result.Failed(null)

            // Beside it, not inside it: the OPML is an interchange format and
            // has to stay one. See SettingsBackup.
            //
            // Caught separately from the OPML above. A settings file that
            // cannot be written is not a failed backup — the subscriptions,
            // which are the part nobody could reconstruct, are already on
            // disk — and reporting it as one would have the reader believe
            // they have no backup at all when they have most of it.
            val settingsWritten = runCatching {
                makeRoomFor(DocumentBackupFolder(tree), SettingsBackup.FILE_NAME)
                val settingsFile = tree.createFile("application/json", SettingsBackup.FILE_NAME)
                    ?: return@runCatching false
                context.contentResolver.openOutputStream(settingsFile.uri)?.use { out ->
                    out.write(SettingsBackup.export(dataStore).toByteArray())
                    true
                } ?: false
            }.getOrElse {
                Log.e(TAG, "Settings backup failed", it)
                false
            }

            Log.i(TAG, "Wrote backup to ${file.uri}")
            Result.Written(
                name = FILE_NAME,
                settingsName = SettingsBackup.FILE_NAME.takeIf { settingsWritten },
                at = System.currentTimeMillis(),
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Backup failed", t)
            Result.Failed(t)
        }
    }

    /**
     * Reads a backup back in.
     *
     * Additive, like the OPML import it reuses: a feed already subscribed is
     * left alone rather than duplicated, and nothing local is removed. A
     * restore that deleted whatever the file did not mention would be a
     * different and much more dangerous operation than the word suggests.
     */
    suspend fun restore(fileUri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val before = sources.getAllSources().size
            val parser = OPMLParser(SourceToRoom())
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parser.parseInputStream(stream)
            } ?: return@withContext Result.Failed(null)

            val after = sources.getAllSources().size
            requestFeedSync(origin = SyncLog.ORIGIN_RESTORE)
            Result.Restored(after - before)
        } catch (t: Throwable) {
            Log.e(TAG, "Restore failed", t)
            Result.Failed(t)
        }
    }

    /**
     * Puts the settings back from a settings file.
     *
     * Separate from restoring the feeds, and separately chosen, because the two
     * are different in kind. Adding somebody's subscriptions to a new phone is
     * additive and safe; overwriting every preference on a phone already set up
     * the way they like it is not, and one button doing both would eventually
     * do the second to somebody who wanted the first.
     */
    suspend fun restoreSettings(fileUri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(fileUri)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Result.Failed(null)
            Result.SettingsRestored(SettingsBackup.import(dataStore, json))
        } catch (t: Throwable) {
            Log.e(TAG, "Settings restore failed", t)
            Result.Failed(t)
        }
    }

    /**
     * Restores everything a chosen folder holds, in one action.
     *
     * For the first run only, and the difference from the settings screen is
     * deliberate rather than an inconsistency. There, restoring the settings is
     * a separate button because overwriting every preference on a phone the
     * reader has already arranged is dangerous. Here there is nothing to
     * overwrite — the app was installed minutes ago — so making somebody pick
     * two files out of the same folder would be ceremony protecting nothing.
     *
     * The folder is remembered as the backup destination while it is open.
     * Somebody restoring from their backup folder has just told the app where
     * their backups live, and asking again on the settings screen later would
     * be asking a question already answered.
     */
    suspend fun restoreFolder(treeUri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.NoDestination

            val opml = tree.findFile(FILE_NAME)
            // Any OPML in the folder, not only one this app wrote. Somebody
            // arriving from another reader has an export of their own under
            // whatever name that reader chose, and it is the same file.
                ?: tree.listFiles().firstOrNull { it.name?.endsWith(".opml") == true }
            val settings = tree.findFile(SettingsBackup.FILE_NAME)

            if (opml == null && settings == null) return@withContext Result.NothingFound

            val feeds = opml?.let {
                when (val result = restore(it.uri)) {
                    is Result.Restored -> result.feeds
                    else -> 0
                }
            } ?: 0

            val restored = settings?.let {
                when (val result = restoreSettings(it.uri)) {
                    is Result.SettingsRestored -> result.count
                    else -> 0
                }
            } ?: 0

            Result.FolderRestored(feeds, restored)
        } catch (t: Throwable) {
            Log.e(TAG, "Folder restore failed", t)
            Result.Failed(t)
        }
    }

    /**
     * Whether the folder already holds a Whisper backup.
     *
     * Asked before a newly chosen folder is written to. Choosing one used to
     * write a backup straight away, so a reader who backed up, reinstalled and
     * pointed the new install at the same folder had their backup replaced by
     * the empty app's within a second of choosing it - which is exactly the
     * moment the backup existed for.
     */
    suspend fun hasBackup(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching false
            tree.findFile(FILE_NAME) != null || tree.findFile(SettingsBackup.FILE_NAME) != null
        }.getOrDefault(false)
    }

    /** Keeps the folder writable across restarts. */
    fun remember(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    /** Whether the folder chosen earlier can still be written to. */
    fun canWrite(treeUri: Uri?): Boolean {
        if (treeUri == null) return false
        return runCatching {
            DocumentFile.fromTreeUri(context, treeUri)?.canWrite() == true
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "Backup"
        const val FILE_NAME = "whisper-subscriptions.opml"
        const val MIME_TYPE = "text/x-opml"
    }
}

/** The few things [makeRoomFor] asks of a folder, so it can be tested without one. */
internal interface BackupFolder {
    fun exists(name: String): Boolean
    fun delete(name: String)
    fun rename(from: String, to: String): Boolean
}

private class DocumentBackupFolder(private val tree: DocumentFile) : BackupFolder {
    override fun exists(name: String) = tree.findFile(name) != null
    override fun delete(name: String) {
        tree.findFile(name)?.delete()
    }
    override fun rename(from: String, to: String) =
        runCatching { tree.findFile(from)?.renameTo(to) == true }.getOrDefault(false)
}

/** "whisper-subscriptions.opml" -> "whisper-subscriptions.previous.opml". */
internal fun previousName(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot <= 0) "$name.previous" else name.substring(0, dot) + ".previous" + name.substring(dot)
}

/**
 * Clears the way for a new backup file, keeping the one it replaces.
 *
 * One copy back, not a dated series: enough that a backup written over by
 * mistake - the new install's empty one over the old phone's - can still be
 * had, without a folder that fills up. A folder that cannot rename gets the
 * old behaviour, the file deleted, since the new backup has to go somewhere.
 */
internal fun makeRoomFor(folder: BackupFolder, name: String) {
    if (!folder.exists(name)) return
    val previous = previousName(name)
    folder.delete(previous)
    if (!folder.rename(name, previous)) folder.delete(name)
}
