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

import android.content.Context
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saulhdev.feeder.data.content.FeedPreferences
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

/**
 * The backup nobody has to remember to take.
 *
 * A manual export is not a backup: it is a thing people mean to do. The whole
 * value of this feature is that it happens without being asked, so the
 * scheduled version is the feature and the button is the reassurance.
 *
 * Daily rather than hourly. A subscription list changes a few times a month at
 * most, and writing an identical file to somebody's cloud storage every hour
 * would be rude to their storage and their battery for no gain.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    private val store: BackupStore by inject(BackupStore::class.java)

    override suspend fun doWork(): Result {
        val destination = prefs.backupFolder.getValue().takeIf { it.isNotEmpty() }
            ?: return Result.success()

        return when (val outcome = store.backUp(destination.toUri())) {
            is BackupStore.Result.Written -> {
                prefs.backupLastRun.setValue(outcome.at.toString())
                // Any earlier stoppage is over, and a warning that outlives
                // the problem teaches people to ignore warnings.
                if (prefs.backupStoppedAt.getValue() != 0L) {
                    prefs.backupStoppedAt.setValue(0L)
                }
                Result.success()
            }

            // The folder is gone, or permission was revoked. Retrying on a
            // schedule would not fix it and the reader has to choose again, so
            // this stops rather than failing daily in the background for ever.
            //
            // Recorded on the way out. Stopping quietly was the whole problem:
            // nothing said backups had ended, and the only way to find out was
            // to open the backup screen and read a line about the folder.
            BackupStore.Result.NoDestination -> {
                if (prefs.backupStoppedAt.getValue() == 0L) {
                    prefs.backupStoppedAt.setValue(System.currentTimeMillis())
                }
                Result.success()
            }

            else -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "WhisperBackup"

        /**
         * Daily, for the folder at [folder].
         *
         * No Wi-Fi rule. It used to wait for unmetered Wi-Fi, which made no
         * sense for a folder on the phone and little for cloud storage: the
         * two files are a few kilobytes, less than one article's photo. A
         * folder in cloud storage needs a connection of some kind to reach
         * it; a folder on the phone needs none at all.
         *
         * UPDATE rather than KEEP, so a schedule made under the old rule takes
         * the new one without losing its place in the day.
         */
        fun schedule(context: Context, folder: String) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (backupFolderIsOnDevice(folder)) NetworkType.NOT_REQUIRED
                            else NetworkType.CONNECTED
                        )
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

/** The document providers that are storage on the phone itself. */
private val ON_DEVICE_PROVIDERS = setOf(
    "com.android.externalstorage.documents",
    "com.android.providers.downloads.documents",
)

/**
 * Whether a chosen backup folder is on the phone rather than in cloud storage.
 *
 * Read from the provider that granted the folder. Anything not known to be
 * local — Drive, Dropbox, a provider nobody has heard of — counts as remote,
 * so an unknown case waits for a connection rather than failing without one.
 */
fun backupFolderIsOnDevice(folder: String): Boolean =
    runCatching { java.net.URI(folder).authority }.getOrNull() in ON_DEVICE_PROVIDERS
